package dev.matejhozlar.climbableropes.client.ride;

import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.ClimbableRopesKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class ClimbPhysics {
    private static final double CLIMB_SIDE_OFFSET = 0.3;
    private static final double AT_BOTTOM_DIST_SQR = 1.0;
    // Below this |y| threshold the rope is treated as horizontal (look-based forward).
    private static final double NEAR_HORIZONTAL_EPS = 0.05;

    private ClimbPhysics() {}

    static double anchorHeight(LocalPlayer player) {
        return player.getBoundingBox().getYsize() + 0.5 * player.getScale();
    }

    static Vec3 anchor(LocalPlayer player) {
        return player.position().add(0.0, anchorHeight(player), 0.0);
    }

    static boolean forwardIsSecond(LocalPlayer player, Vec3 first, Vec3 second) {
        Vec3 chord = second.subtract(first);
        double chordLen = chord.length();
        if (chordLen < 1.0E-4) return true;
        Vec3 chordDir = chord.scale(1.0 / chordLen);
        if (Math.abs(chordDir.y) > NEAR_HORIZONTAL_EPS) return second.y > first.y;
        return player.getLookAngle().dot(chordDir) >= 0;
    }

    static boolean snapToRope(Minecraft mc, LocalPlayer player, Vec3 ropePoint, Vec3 bottom) {
        double targetY;
        if (ropePoint.distanceToSqr(bottom) < AT_BOTTOM_DIST_SQR) {
            ropePoint = bottom;
            targetY = bottom.y;
        } else {
            targetY = ropePoint.y - anchorHeight(player);
        }

        double yawRad = Math.toRadians(player.getYRot());
        Vec3 offsetTarget = new Vec3(
                ropePoint.x + Math.sin(yawRad) * CLIMB_SIDE_OFFSET,
                targetY,
                ropePoint.z - Math.cos(yawRad) * CLIMB_SIDE_OFFSET);
        if (tryPlace(mc, player, offsetTarget)) return true;
        return tryPlace(mc, player, new Vec3(ropePoint.x, targetY, ropePoint.z));
    }

    static boolean tryPlace(Minecraft mc, LocalPlayer player, Vec3 target) {
        AABB aabb = player.getBoundingBox().move(target.subtract(player.position()));
        if (!mc.level.noCollision(player, aabb)) return false;
        player.setPos(target);
        return true;
    }

    static void applyClimbVelocity(LocalPlayer player, Vec3 anchor, Vec3 ropeWorld, Vec3 forward,
                                   double speedAlong, Vec3 ropeVelocity) {
        Vec3 climbVel = forward.scale(speedAlong);
        Vec3 target = ropeWorld.add(sideOffset(ClimbableRopesKeybinds.climbYaw(), forward));
        double snapPull = ClimbableRopesConfig.SNAP_PULL.get();
        double snapVelCap = ClimbableRopesConfig.SNAP_VELOCITY_CAP.get();
        double xVel = (target.x - anchor.x) * snapPull;
        double yVel = (target.y - anchor.y) * snapPull;
        double zVel = (target.z - anchor.z) * snapPull;
        double horizMag = Math.sqrt(xVel * xVel + zVel * zVel);
        if (horizMag > snapVelCap) {
            double scale = snapVelCap / horizMag;
            xVel *= scale;
            zVel *= scale;
        }
        yVel = Math.max(-snapVelCap, Math.min(snapVelCap, yVel));

        Vec3 carry = RopeMotion.carry(player, ropeVelocity);
        player.setDeltaMovement(carry.x + climbVel.x + xVel, carry.y + climbVel.y + yVel, carry.z + climbVel.z + zVel);
        player.fallDistance = 0.0F;
    }

    private static Vec3 sideOffset(double yaw, Vec3 ropeDir) {
        double yawRad = Math.toRadians(yaw);
        Vec3 forward = new Vec3(Math.sin(yawRad), 0.0, -Math.cos(yawRad));
        Vec3 perp = forward.subtract(ropeDir.scale(forward.dot(ropeDir)));
        double len = perp.length();
        if (len < 1e-6) return Vec3.ZERO;
        return perp.scale(CLIMB_SIDE_OFFSET / len);
    }
}
