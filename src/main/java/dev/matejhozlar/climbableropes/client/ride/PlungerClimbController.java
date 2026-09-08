package dev.matejhozlar.climbableropes.client.ride;

import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.ClimbAnimationController;
import dev.simulated_team.simulated.content.entities.launched_plunger.LaunchedPlungerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

final class PlungerClimbController {
    private static final ClimbDrive drive = new ClimbDrive();
    private static LaunchedPlungerEntity backwardPlunger;
    private static LaunchedPlungerEntity forwardPlunger;

    private PlungerClimbController() {}

    static boolean isRiding() {
        return forwardPlunger != null;
    }

    private static boolean isRidingPair(PlungerRope.Pair pair) {
        return forwardPlunger != null && pair.sameEnds(forwardPlunger, backwardPlunger);
    }

    static void reset() {
        backwardPlunger = null;
        forwardPlunger = null;
        drive.reset();
    }

    static boolean tryHoverEmbark(Minecraft mc, LocalPlayer player, boolean justPressed) {
        if (!justPressed) return false;
        PlungerRope.Pair pair = PlungerRope.findHoveredPair(mc, player);
        if (pair == null) return false;
        embark(pair, mc, player);
        return true;
    }

    static void tick(Minecraft mc, LocalPlayer player) {
        if (RopeRide.climbInterrupted(player)) {
            disembark();
            return;
        }
        if (!PlungerRope.isPlunged(backwardPlunger) || !PlungerRope.isPlunged(forwardPlunger)) {
            disembark();
            return;
        }

        ClimbDrive.Input input = ClimbDrive.Input.read(mc);

        if (input.jump()) {
            RopeRide.jumpOff(player);
            disembark();
            return;
        }
        if (input.dismount()) {
            disembark();
            return;
        }

        PlungerRope.RopeEnd backEnd = PlungerRope.ropeEnd(backwardPlunger);
        PlungerRope.RopeEnd fwdEnd = PlungerRope.ropeEnd(forwardPlunger);
        Vec3 back = backEnd.position();
        Vec3 fwd = fwdEnd.position();
        Vec3 ab = fwd.subtract(back);
        double abLen = ab.length();
        if (abLen < 1e-4) {
            disembark();
            return;
        }
        Vec3 dir = ab.scale(1.0 / abLen);

        Vec3 anchor = ClimbPhysics.anchor(player);
        double t = Math.max(0.0, Math.min(abLen, anchor.subtract(back).dot(dir)));
        Vec3 ropeWorld = back.add(dir.scale(t));

        double maxLeash = ClimbableRopesConfig.MAX_LEASH_DISTANCE.get();
        if (anchor.distanceToSqr(ropeWorld) > maxLeash * maxLeash) {
            disembark();
            return;
        }

        Vec3 lowerEnd = back.y < fwd.y ? back : fwd;
        if (drive.groundedAtBottomTooLong(player.onGround(), input.up(), anchor.y, lowerEnd.y)) {
            disembark();
            return;
        }

        double remainingUp = Math.max(0.0, abLen - t);
        ClimbDrive.Step step = drive.step(input, player.onGround(), dir, remainingUp, t);

        Vec3 ropeVel = backEnd.velocity().lerp(fwdEnd.velocity(), t / abLen);
        ClimbPhysics.applyClimbVelocity(player, anchor, ropeWorld, dir, step.speedAlong(), ropeVel);
        ClimbAnimationController.onTick(dir, step.animState());
        RopeRide.keepAlive(forwardPlunger.getUUID());
    }

    private static void embark(PlungerRope.Pair pair, Minecraft mc, LocalPlayer player) {
        if (isRidingPair(pair)) return;
        Vec3 posA = PlungerRope.ropeEnd(pair.a()).position();
        Vec3 posB = PlungerRope.ropeEnd(pair.b()).position();
        Vec3 ab = posB.subtract(posA);
        double abLen = ab.length();
        if (abLen < 1e-4) return;
        Vec3 dirAB = ab.scale(1.0 / abLen);
        boolean forwardIsB = ClimbPhysics.forwardIsSecond(player, posA, posB);

        if (!snapToEmbarkPoint(mc, player, posA, posB, dirAB, abLen)) return;

        RopeRideDispatcher.leaveActiveRides();
        forwardPlunger = forwardIsB ? pair.b() : pair.a();
        backwardPlunger = forwardIsB ? pair.a() : pair.b();
        drive.reset();

        RopeRide.stopFlight(player);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        RopeRide.notifyEmbark(mc, forwardPlunger.getUUID(), ClimbAnimationController.ClimbMode.PLUNGER_ROPE);
    }

    private static boolean snapToEmbarkPoint(Minecraft mc, LocalPlayer player,
                                             Vec3 posA, Vec3 posB, Vec3 dirAB, double abLen) {
        Vec3 anchor = ClimbPhysics.anchor(player);
        double t = Math.max(0.0, Math.min(abLen, anchor.subtract(posA).dot(dirAB)));
        Vec3 ropePoint = posA.add(dirAB.scale(t));
        Vec3 lowerEnd = posA.y < posB.y ? posA : posB;
        return ClimbPhysics.snapToRope(mc, player, ropePoint, lowerEnd);
    }

    static void disembark() {
        if (forwardPlunger == null) return;
        RopeRide.notifyDisembark(forwardPlunger.getUUID());
        reset();
    }
}
