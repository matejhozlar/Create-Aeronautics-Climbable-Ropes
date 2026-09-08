package dev.matejhozlar.climbableropes.client.ride;

import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.ClimbAnimationController;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopePoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ZiplineClientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

final class StrandClimbController {
    // How far past a strand endpoint the player may drift before being dismounted. The snap spring
    // cannot hold a grounded player on a near-horizontal rope, so they can walk off the end.
    private static final double END_OVERSHOOT_LIMIT = 0.4;

    private static final ClimbDrive drive = new ClimbDrive();
    private static UUID climbingRope = null;
    private static boolean forwardIsLast = true;
    private static double prevEndOvershoot = 0.0;

    private StrandClimbController() {}

    static boolean isRiding() {
        return climbingRope != null;
    }

    static void reset() {
        climbingRope = null;
        forwardIsLast = true;
        prevEndOvershoot = 0.0;
        drive.reset();
    }

    static boolean tryHoverEmbark(Minecraft mc, LocalPlayer player, boolean justPressed) {
        UUID hovered = findVerticalHover(mc, player);
        if (hovered == null) return false;
        ZiplineClientManager.hoveringRope = hovered;
        if (justPressed) embark(hovered, mc, player);
        return true;
    }

    private static UUID findVerticalHover(Minecraft mc, LocalPlayer player) {
        ClientLevelRopeManager mgr = ClientLevelRopeManager.getOrCreate(mc.level);
        HoverHit hit = raycastAnyRope(mgr, HoverRay.from(mc, player));
        if (hit == null) return null;

        double minVerticalDot = Math.cos(Math.toRadians(ClimbableRopesConfig.MAX_CLIMB_ANGLE_FROM_VERTICAL.get()));
        return Math.abs(hit.tangent().y) >= minVerticalDot ? hit.strand() : null;
    }

    private record HoverHit(UUID strand, Vec3 tangent) {}

    private static HoverHit raycastAnyRope(ClientLevelRopeManager mgr, HoverRay ray) {
        UUID best = null;
        Vec3 bestTangent = null;
        double bestDistSqr = ray.blockDistSqr();
        double radius = ClimbableRopesConfig.ROPE_HOVER_RADIUS.get();
        double radiusSqr = radius * radius;
        for (ClientRopeStrand strand : mgr.getAllStrands()) {
            var points = strand.getPoints();
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3 a = JOMLConversion.toMojang(points.get(i).position());
                Vec3 b = JOMLConversion.toMojang(points.get(i + 1).position());
                HoverRay.Hit hit = ray.hit(a, b);
                if (hit == null || hit.lateralSqr() > radiusSqr || hit.depthSqr() > bestDistSqr) continue;
                bestDistSqr = hit.depthSqr();
                best = strand.getUuid();
                bestTangent = hit.segmentDir();
            }
        }
        return best == null ? null : new HoverHit(best, bestTangent);
    }

    private static void embark(UUID rope, Minecraft mc, LocalPlayer player) {
        if (rope.equals(climbingRope)) return;
        RopeRideDispatcher.leaveActiveRides();
        climbingRope = rope;
        prevEndOvershoot = 0.0;
        drive.reset();
        forwardIsLast = computeForwardIsLast(mc, player, rope);

        RopeRide.stopFlight(player);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;

        snapToEmbarkPoint(mc, player, rope);
        RopeRide.notifyEmbark(mc, rope, ClimbAnimationController.ClimbMode.HANGING_STRAND);
    }

    private static void snapToEmbarkPoint(Minecraft mc, LocalPlayer player, UUID rope) {
        ClientLevelRopeManager mgr = ClientLevelRopeManager.getOrCreate(mc.level);
        ClientRopeStrand strand = mgr.getStrand(rope);
        if (strand == null) return;

        Vec3 clickPoint = JOMLConversion.toMojang(
                ZiplineClientManager.getClosestPointOnStrand(strand, player).position());
        Vec3 first = JOMLConversion.toMojang(strand.getPoints().getFirst().position());
        Vec3 last = JOMLConversion.toMojang(strand.getPoints().getLast().position());
        Vec3 bottom = first.y < last.y ? first : last;
        ClimbPhysics.snapToRope(mc, player, clickPoint, bottom);
    }

    static void disembark() {
        if (climbingRope == null) return;
        RopeRide.notifyDisembark(climbingRope);
        reset();
    }

    private static boolean computeForwardIsLast(Minecraft mc, LocalPlayer player, UUID rope) {
        ClientLevelRopeManager mgr = ClientLevelRopeManager.getOrCreate(mc.level);
        ClientRopeStrand strand = mgr.getStrand(rope);
        if (strand == null || strand.getPoints().size() < 2) return true;
        Vec3 first = JOMLConversion.toMojang(strand.getPoints().getFirst().position());
        Vec3 last = JOMLConversion.toMojang(strand.getPoints().getLast().position());
        return ClimbPhysics.forwardIsSecond(player, first, last);
    }

    static void tick(Minecraft mc, LocalPlayer player) {
        if (RopeRide.climbInterrupted(player)) {
            disembark();
            return;
        }

        ClientLevelRopeManager mgr = ClientLevelRopeManager.getOrCreate(mc.level);
        ClientRopeStrand strand = mgr.getStrand(climbingRope);
        if (strand == null || strand.getPoints().size() < 2) {
            disembark();
            return;
        }

        ClimbDrive.Input input = ClimbDrive.Input.read(mc);

        Vec3 firstPoint = JOMLConversion.toMojang(strand.getPoints().getFirst().position());
        Vec3 lastPoint = JOMLConversion.toMojang(strand.getPoints().getLast().position());
        boolean topIsLast = lastPoint.y >= firstPoint.y;
        Vec3 bottomPoint = topIsLast ? firstPoint : lastPoint;
        Vec3 topPoint = topIsLast ? lastPoint : firstPoint;

        Vec3 anchor = ClimbPhysics.anchor(player);
        StrandQuery sq = findClosestSegment(strand, anchor);
        double maxLeash = ClimbableRopesConfig.MAX_LEASH_DISTANCE.get();
        if (sq.distSqr > maxLeash * maxLeash) {
            disembark();
            return;
        }
        Vec3 ropeWorld = sq.position;
        Vec3 tangent = sq.tangent;
        Vec3 forwardAlongStrand = forwardIsLast ? tangent : tangent.scale(-1.0);

        double sTotal = totalArcLength(strand);
        double sFromIndex0 = sq.arcLengthFromStart;
        double arcRemainingForward = Math.max(0.0, forwardIsLast ? sTotal - sFromIndex0 : sFromIndex0);
        double arcRemainingBackward = Math.max(0.0, sTotal - arcRemainingForward);
        double arcRemainingToTop = Math.max(0.0, topIsLast ? sTotal - sFromIndex0 : sFromIndex0);

        if (input.jump()) {
            if (ClimbableRopesConfig.ALLOW_BLOCK_MANTLE.get()) {
                boolean atTop = arcRemainingToTop <= 0.1
                    || (player.verticalCollision && !player.onGround() && anchor.y >= topPoint.y - 0.5);
                if (atTop && trySnapAboveCeiling(mc, player, topPoint)) return;
            }
            RopeRide.jumpOff(player);
            disembark();
            return;
        }
        if (input.dismount()) {
            disembark();
            return;
        }

        if (drive.groundedAtBottomTooLong(player.onGround(), input.up(), anchor.y, bottomPoint.y)) {
            disembark();
            return;
        }

        ClimbDrive.Step step = drive.step(input, player.onGround(), forwardAlongStrand,
                arcRemainingForward, arcRemainingBackward);

        boolean overshootGrowing = sq.endOvershoot >= prevEndOvershoot;
        prevEndOvershoot = sq.endOvershoot;
        if (step.speedAlong() == 0.0 && overshootGrowing && sq.endOvershoot > END_OVERSHOOT_LIMIT) {
            disembark();
            return;
        }

        ClimbPhysics.applyClimbVelocity(player, anchor, ropeWorld, forwardAlongStrand, step.speedAlong(), sq.velocity);

        // The endpoint-to-endpoint chord, not a local segment, keeps the verticality gate from flickering.
        Vec3 ropeChord = forwardIsLast
                ? lastPoint.subtract(firstPoint)
                : firstPoint.subtract(lastPoint);
        Vec3 animForward = ropeChord.lengthSqr() > 1.0e-8 ? ropeChord.normalize() : forwardAlongStrand;
        ClimbAnimationController.onTick(animForward, step.animState());

        RopeRide.keepAlive(climbingRope);
    }

    private static StrandQuery findClosestSegment(ClientRopeStrand strand, Vec3 target) {
        var points = strand.getPoints();
        int segCount = points.size() - 1;
        double minDistSq = Double.MAX_VALUE;
        Vec3 minPoint = Vec3.ZERO;
        Vec3 minTangent = new Vec3(0.0, 1.0, 0.0);
        double minArc = 0.0;
        double minRaw = 0.0;
        double minSegLen = 0.0;
        int minSegIndex = -1;
        double minSegFrac = 0.0;
        double cumulative = 0.0;
        for (int i = 0; i < segCount; i++) {
            Vec3 a = JOMLConversion.toMojang(points.get(i).position());
            Vec3 b = JOMLConversion.toMojang(points.get(i + 1).position());
            Vec3 ab = b.subtract(a);
            double abLen = ab.length();
            if (abLen >= 1e-6) {
                Vec3 dir = ab.scale(1.0 / abLen);
                double raw = target.subtract(a).dot(dir);
                double along = Math.max(0.0, Math.min(abLen, raw));
                Vec3 onSeg = a.add(dir.scale(along));
                double distSq = onSeg.distanceToSqr(target);
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    minPoint = onSeg;
                    minTangent = dir;
                    minArc = cumulative + along;
                    minRaw = raw;
                    minSegLen = abLen;
                    minSegIndex = i;
                    minSegFrac = along / abLen;
                }
            }
            cumulative += abLen;
        }
        double endOvershoot = 0.0;
        if (minSegIndex == 0 && minRaw < 0.0) {
            endOvershoot = -minRaw;
        } else if (minSegIndex == segCount - 1 && minRaw > minSegLen) {
            endOvershoot = minRaw - minSegLen;
        }
        Vec3 velocity = Vec3.ZERO;
        if (minSegIndex >= 0) {
            velocity = pointVelocity(points.get(minSegIndex))
                    .lerp(pointVelocity(points.get(minSegIndex + 1)), minSegFrac);
        }
        return new StrandQuery(minPoint, minTangent, minArc, minDistSq, endOvershoot, velocity);
    }

    private static Vec3 pointVelocity(ClientRopePoint point) {
        return JOMLConversion.toMojang(point.position()).subtract(JOMLConversion.toMojang(point.previousPosition()));
    }

    private static double totalArcLength(ClientRopeStrand strand) {
        var points = strand.getPoints();
        double s = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 a = JOMLConversion.toMojang(points.get(i).position());
            Vec3 b = JOMLConversion.toMojang(points.get(i + 1).position());
            s += a.distanceTo(b);
        }
        return s;
    }

    private record StrandQuery(Vec3 position, Vec3 tangent, double arcLengthFromStart, double distSqr,
                               double endOvershoot, Vec3 velocity) {}

    private static boolean trySnapAboveCeiling(Minecraft mc, LocalPlayer player, Vec3 topPoint) {
        Vec3 playerPos = player.position();
        Vec3[] candidates = {
                new Vec3(topPoint.x, topPoint.y + 0.05, topPoint.z),
                new Vec3(playerPos.x, topPoint.y + 0.05, playerPos.z),
                new Vec3(topPoint.x, topPoint.y + 1.0, topPoint.z),
        };
        for (Vec3 candidate : candidates) {
            if (ClimbPhysics.tryPlace(mc, player, candidate)) {
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0.0F;
                disembark();
                return true;
            }
        }
        return false;
    }
}
