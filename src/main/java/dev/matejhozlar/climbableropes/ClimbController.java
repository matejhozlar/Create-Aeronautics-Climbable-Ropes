package dev.matejhozlar.climbableropes;

import com.simibubi.create.AllTags;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ZiplineClientManager;
import dev.simulated_team.simulated.index.SimClickInteractions;
import dev.simulated_team.simulated.network.packets.RopeRidingPacket;
import foundry.veil.api.network.VeilPacketManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.UUID;

@EventBusSubscriber(modid = ClimbableRopes.MODID, value = Dist.CLIENT)
public final class ClimbController {
    private static final double CLIMB_SIDE_OFFSET = 0.3;
    private static final double AT_BOTTOM_DIST_SQR = 1.0;
    private static final double VERTICAL_BIAS = 0.5;

    private static UUID climbingRope = null;
    private static boolean forwardIsLast = true;
    private static int bottomGroundedTimer = 0;
    private static boolean prevUseDown = false;
    private static double slideVelocity = 0.0;

    private ClimbController() {}

    public static boolean isLocalOnRope() {
        return climbingRope != null
                || PlungerClimbController.isClimbing()
                || PlungerZiplineController.isRiding();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            climbingRope = null;
            forwardIsLast = true;
            bottomGroundedTimer = 0;
            prevUseDown = false;
            slideVelocity = 0.0;
            PlungerClimbController.reset();
            PlungerZiplineController.reset();
            return;
        }
        if (mc.isPaused()) return;

        ClimbableRopesKeybinds.update(player);

        boolean useDown = mc.options.keyUse.isDown();
        boolean justPressed = useDown && !prevUseDown;
        prevUseDown = useDown;

        if (climbingRope != null || PlungerClimbController.isClimbing() || PlungerZiplineController.isRiding()) {
            if (justPressed) tryHoverEmbark(mc, player, true);
            tickActiveRide(mc, player);
            return;
        }

        if (ZiplineClientManager.ridingRope != null) return;

        tryHoverEmbark(mc, player, justPressed);
    }

    private static void tickActiveRide(Minecraft mc, LocalPlayer player) {
        if (climbingRope != null) tickClimb(mc, player);
        else if (PlungerClimbController.isClimbing()) PlungerClimbController.tickClimb(mc, player);
        else if (PlungerZiplineController.isRiding()) PlungerZiplineController.ridingTick(mc, player);
    }

    private static void tryHoverEmbark(Minecraft mc, LocalPlayer player, boolean justPressed) {
        if (AllTags.AllItemTags.CHAIN_RIDEABLE.matches(player.getMainHandItem())) {
            if (!player.isShiftKeyDown() && ClimbableRopesConfig.ALLOW_PLUNGER_ZIPLINE.get()) {
                PlungerZiplineController.tryHoverEmbark(mc, player, justPressed);
            }
            return;
        }

        if (!player.getMainHandItem().isEmpty()) return;
        if (player.isShiftKeyDown()) return;

        if (ClimbableRopesConfig.ALLOW_VERTICAL_ROPE_CLIMBING.get()) {
            UUID hovered = findVerticalHover(mc, player);
            if (hovered != null) {
                ZiplineClientManager.hoveringRope = hovered;
                if (justPressed) embark(hovered, mc, player);
                return;
            }
        }

        if (ClimbableRopesConfig.ALLOW_PLUNGER_CLIMBING.get()) {
            PlungerClimbController.tryHoverEmbark(mc, player, justPressed);
        }
    }

    private static UUID findVerticalHover(Minecraft mc, LocalPlayer player) {
        ClientLevelRopeManager mgr = ClientLevelRopeManager.getOrCreate(mc.level);
        double maxRange = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
        HitResult hitResult = mc.hitResult;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        double bestDistSqr = hitResult == null
                ? maxRange * maxRange
                : Sable.HELPER.projectOutOfSubLevel(mc.level, hitResult.getLocation())
                        .distanceToSqr(eye);

        UUID found = raycastAnyRope(mgr, eye, look, maxRange, bestDistSqr);
        if (found == null) return null;

        ClientRopeStrand strand = mgr.getStrand(found);
        if (strand == null) return null;

        ZiplineClientManager.ClosestQuery query =
                ZiplineClientManager.getClosestPointOnStrand(strand, player);
        double minVerticalDot = Math.cos(Math.toRadians(ClimbableRopesConfig.MAX_CLIMB_ANGLE_FROM_VERTICAL.get()));
        return Math.abs(query.normal().y) >= minVerticalDot ? found : null;
    }

    private static UUID raycastAnyRope(ClientLevelRopeManager mgr, Vec3 eye, Vec3 look,
                                       double maxRange, double bestDistSqr) {
        UUID best = null;
        double radius = ClimbableRopesConfig.ROPE_HOVER_RADIUS.get();
        double radiusSqr = radius * radius;
        for (ClientRopeStrand strand : mgr.getAllStrands()) {
            var points = strand.getPoints();
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3 a = JOMLConversion.toMojang(points.get(i).position());
                Vec3 b = JOMLConversion.toMojang(points.get(i + 1).position());
                Vec3 segDir = b.subtract(a);
                double segLen = segDir.length();
                if (segLen < 1e-6) continue;
                Vec3 segUnit = segDir.scale(1.0 / segLen);

                double dotLD = look.dot(segUnit);
                double denom = 1.0 - dotLD * dotLD;
                Vec3 r = eye.subtract(a);
                double rSeg = r.dot(segUnit);
                double rLook = r.dot(look);

                double s, t;
                if (denom < 1e-9) {
                    s = 0.0;
                    t = rSeg;
                } else {
                    s = (dotLD * rSeg - rLook) / denom;
                    t = (rSeg - dotLD * rLook) / denom;
                }
                s = Math.max(0.0, Math.min(maxRange, s));
                t = Math.max(0.0, Math.min(segLen, t));

                Vec3 onRay = eye.add(look.scale(s));
                Vec3 onSeg = a.add(segUnit.scale(t));
                if (onRay.distanceToSqr(onSeg) > radiusSqr) continue;
                double hitDistSqr = s * s;
                if (hitDistSqr > bestDistSqr) continue;
                bestDistSqr = hitDistSqr;
                best = strand.getUuid();
            }
        }
        return best;
    }

    private static void embark(UUID rope, Minecraft mc, LocalPlayer player) {
        if (rope.equals(climbingRope)) return;
        leaveActiveRides();
        climbingRope = rope;
        bottomGroundedTimer = 0;
        slideVelocity = 0.0;
        forwardIsLast = computeForwardIsLast(mc, player, rope);

        player.getAbilities().flying = false;
        player.stopFallFlying();
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;

        snapToEmbarkPoint(mc, player, rope);

        mc.gui.setOverlayMessage(
                Component.translatable("mount.onboard", mc.options.keyShift.getTranslatedKeyMessage()),
                false);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 1f, 0.5f));

        VeilPacketManager.server().sendPacket(new RopeRidingPacket(rope, false));
        ClimbAnimationController.onEmbark(ClimbAnimationController.ClimbMode.HANGING_STRAND);
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
        boolean atBottom = clickPoint.distanceToSqr(bottom) < AT_BOTTOM_DIST_SQR;

        double targetY;
        Vec3 ropePoint;
        if (atBottom) {
            ropePoint = bottom;
            targetY = bottom.y;
        } else {
            ropePoint = clickPoint;
            double chainYOffset = 0.5 * player.getScale();
            targetY = clickPoint.y - (player.getBoundingBox().getYsize() + chainYOffset);
        }

        double yawRad = Math.toRadians(player.getYRot());
        Vec3 offsetTarget = new Vec3(
                ropePoint.x + Math.sin(yawRad) * CLIMB_SIDE_OFFSET,
                targetY,
                ropePoint.z - Math.cos(yawRad) * CLIMB_SIDE_OFFSET);

        AABB offsetAabb = player.getBoundingBox().move(offsetTarget.subtract(player.position()));
        if (mc.level.noCollision(player, offsetAabb)) {
            player.setPos(offsetTarget);
            return;
        }
        Vec3 centerTarget = new Vec3(ropePoint.x, targetY, ropePoint.z);
        AABB centerAabb = player.getBoundingBox().move(centerTarget.subtract(player.position()));
        if (mc.level.noCollision(player, centerAabb)) {
            player.setPos(centerTarget);
        }
    }

    private static void disembark() {
        if (climbingRope == null) return;
        VeilPacketManager.server().sendPacket(new RopeRidingPacket(climbingRope, true));
        climbingRope = null;
        forwardIsLast = true;
        bottomGroundedTimer = 0;
        slideVelocity = 0.0;

        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 0.75f, 0.35f));
        ClimbAnimationController.onDisembark();
    }

    static void leaveActiveRides() {
        disembark();
        PlungerClimbController.disembark();
        PlungerZiplineController.disembark();
    }

    private static boolean computeForwardIsLast(Minecraft mc, LocalPlayer player, UUID rope) {
        ClientLevelRopeManager mgr = ClientLevelRopeManager.getOrCreate(mc.level);
        ClientRopeStrand strand = mgr.getStrand(rope);
        if (strand == null || strand.getPoints().size() < 2) return true;
        Vec3 first = JOMLConversion.toMojang(strand.getPoints().getFirst().position());
        Vec3 last = JOMLConversion.toMojang(strand.getPoints().getLast().position());
        Vec3 chord = last.subtract(first);
        double chordLen = chord.length();
        if (chordLen < 1.0E-4) return true;
        Vec3 chordDir = chord.scale(1.0 / chordLen);
        if (Math.abs(chordDir.y) > VERTICAL_BIAS) return last.y > first.y;
        return player.getLookAngle().dot(chordDir) >= 0;
    }

    private static void tickClimb(Minecraft mc, LocalPlayer player) {
        if (player.getAbilities().flying || !player.getMainHandItem().isEmpty() || SimClickInteractions.HANDLE_HANDLER.isActive()) {
            disembark();
            return;
        }

        ClientLevelRopeManager mgr = ClientLevelRopeManager.getOrCreate(mc.level);
        ClientRopeStrand strand = mgr.getStrand(climbingRope);
        if (strand == null || strand.getPoints().size() < 2) {
            disembark();
            return;
        }

        boolean climbUp = mc.options.keyUp.isDown();
        boolean climbDown = mc.options.keyDown.isDown();
        boolean sprint = mc.options.keySprint.isDown();
        boolean dismount = mc.options.keyShift.isDown();
        boolean jumpOff = mc.options.keyJump.isDown();

        Vec3 firstPoint = JOMLConversion.toMojang(strand.getPoints().getFirst().position());
        Vec3 lastPoint = JOMLConversion.toMojang(strand.getPoints().getLast().position());
        boolean topIsLast = lastPoint.y >= firstPoint.y;
        Vec3 bottomPoint = topIsLast ? firstPoint : lastPoint;
        Vec3 topPoint = topIsLast ? lastPoint : firstPoint;

        Vec3 anchor = anchor(player);
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

        if (jumpOff) {
            if (ClimbableRopesConfig.ALLOW_BLOCK_MANTLE.get()) {
                boolean atTop = arcRemainingToTop <= 0.1
                    || (player.verticalCollision && !player.onGround() && anchor.y >= topPoint.y - 0.5);
                if (atTop && trySnapAboveCeiling(mc, player, topPoint)) return;
            }
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, Math.max(v.y, ClimbableRopesConfig.JUMP_OFF_VELOCITY.get()), v.z);
            disembark();
            return;
        }
        if (dismount) {
            disembark();
            return;
        }

        if (player.onGround() && !climbUp
                && anchor.y < bottomPoint.y + ClimbableRopesConfig.BOTTOM_DISMOUNT_OFFSET.get()) {
            if (++bottomGroundedTimer > ClimbableRopesConfig.BOTTOM_GROUNDED_DISMOUNT_TICKS.get()) {
                disembark();
                return;
            }
        } else {
            bottomGroundedTimer = 0;
        }

        if (climbUp && arcRemainingForward <= 0.0) climbUp = false;

        double climbSpeed = ClimbableRopesConfig.CLIMB_SPEED.get();
        double descendSpeed = ClimbableRopesConfig.DESCEND_SPEED.get();
        double slideSpeed = ClimbableRopesConfig.SLIDE_SPEED.get();
        double slideAccel = ClimbableRopesConfig.SLIDE_ACCELERATION.get();
        double slideDecel = ClimbableRopesConfig.SLIDE_DECELERATION.get();

        double verticalComponent = Math.abs(forwardAlongStrand.y);
        boolean slideEffective = climbDown && sprint && slideSpeed * verticalComponent > descendSpeed;
        if (climbUp) {
            slideVelocity = 0.0;
        } else if (slideEffective) {
            if (slideVelocity < descendSpeed) slideVelocity = descendSpeed;
            slideVelocity = Math.min(slideSpeed * verticalComponent, slideVelocity + slideAccel * verticalComponent);
        } else if (slideVelocity > 0) {
            slideVelocity = Math.max(0.0, slideVelocity - slideDecel);
        }

        double speedAlong;
        if (climbUp) speedAlong = Math.min(climbSpeed, arcRemainingForward);
        else if (slideVelocity > descendSpeed) speedAlong = -slideVelocity;
        else if (climbDown) speedAlong = -descendSpeed;
        else if (slideVelocity > 0) speedAlong = -slideVelocity;
        else speedAlong = 0.0;

        if (speedAlong < 0 && arcRemainingBackward <= 1.0E-3) {
            speedAlong = 0.0;
            slideVelocity = 0.0;
        }

        Vec3 climbVel = forwardAlongStrand.scale(speedAlong);

        Vec3 target = ropeWorld.add(sideOffset(ClimbableRopesKeybinds.climbYaw(), forwardAlongStrand));
        double dx = target.x - anchor.x;
        double dy = target.y - anchor.y;
        double dz = target.z - anchor.z;
        double snapPull = ClimbableRopesConfig.SNAP_PULL.get();
        double snapVelCap = ClimbableRopesConfig.SNAP_VELOCITY_CAP.get();
        double xVel = dx * snapPull;
        double yVel = dy * snapPull;
        double zVel = dz * snapPull;
        double snapMag = Math.sqrt(xVel * xVel + yVel * yVel + zVel * zVel);
        if (snapMag > snapVelCap) {
            double scale = snapVelCap / snapMag;
            xVel *= scale;
            yVel *= scale;
            zVel *= scale;
        }

        player.setDeltaMovement(climbVel.x + xVel, climbVel.y + yVel, climbVel.z + zVel);
        player.fallDistance = 0.0F;

        ClimbAnimationController.ClimbState animState;
        if (climbUp) animState = ClimbAnimationController.ClimbState.CLIMB_UP;
        else if (slideVelocity > descendSpeed) animState = ClimbAnimationController.ClimbState.SLIDE;
        else if (climbDown || slideVelocity > 0.0) animState = ClimbAnimationController.ClimbState.DESCEND;
        else animState = ClimbAnimationController.ClimbState.IDLE;
        ClimbAnimationController.onTick(forwardAlongStrand, animState);

        if (AnimationTickHolder.getTicks() % 10 == 0) {
            VeilPacketManager.server().sendPacket(new RopeRidingPacket(climbingRope, false));
        }
    }

    private static Vec3 anchor(LocalPlayer player) {
        double chainYOffset = 0.5 * player.getScale();
        return player.position().add(0.0, player.getBoundingBox().getYsize() + chainYOffset, 0.0);
    }

    private static Vec3 sideOffset(double yaw, Vec3 ropeDir) {
        double yawRad = Math.toRadians(yaw);
        Vec3 forward = new Vec3(Math.sin(yawRad), 0.0, -Math.cos(yawRad));
        Vec3 perp = forward.subtract(ropeDir.scale(forward.dot(ropeDir)));
        double len = perp.length();
        if (len < 1e-6) return Vec3.ZERO;
        // Fade the side offset out as the rope flattens so the player hangs below near-horizontal ropes, not beside them.
        double verticality = Math.abs(ropeDir.y);
        return perp.scale(CLIMB_SIDE_OFFSET * verticality / len);
    }

    private static StrandQuery findClosestSegment(ClientRopeStrand strand, Vec3 target) {
        var points = strand.getPoints();
        double minDistSq = Double.MAX_VALUE;
        Vec3 minPoint = Vec3.ZERO;
        Vec3 minTangent = new Vec3(0.0, 1.0, 0.0);
        double minArc = 0.0;
        double cumulative = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 a = JOMLConversion.toMojang(points.get(i).position());
            Vec3 b = JOMLConversion.toMojang(points.get(i + 1).position());
            Vec3 ab = b.subtract(a);
            double abLen = ab.length();
            if (abLen >= 1e-6) {
                Vec3 dir = ab.scale(1.0 / abLen);
                double along = Math.max(0.0, Math.min(abLen, target.subtract(a).dot(dir)));
                Vec3 onSeg = a.add(dir.scale(along));
                double distSq = onSeg.distanceToSqr(target);
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    minPoint = onSeg;
                    minTangent = dir;
                    minArc = cumulative + along;
                }
            }
            cumulative += abLen;
        }
        return new StrandQuery(minPoint, minTangent, minArc, minDistSq);
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

    private record StrandQuery(Vec3 position, Vec3 tangent, double arcLengthFromStart, double distSqr) {}

    private static boolean trySnapAboveCeiling(Minecraft mc, LocalPlayer player, Vec3 topPoint) {
        Vec3 playerPos = player.position();
        Vec3[] candidates = {
                new Vec3(topPoint.x, topPoint.y + 0.05, topPoint.z),
                new Vec3(playerPos.x, topPoint.y + 0.05, playerPos.z),
                new Vec3(topPoint.x, topPoint.y + 1.0, topPoint.z),
        };
        for (Vec3 candidate : candidates) {
            AABB aabb = player.getBoundingBox().move(candidate.subtract(playerPos));
            if (mc.level.noCollision(player, aabb)) {
                player.setPos(candidate);
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0.0F;
                disembark();
                return true;
            }
        }
        return false;
    }
}
