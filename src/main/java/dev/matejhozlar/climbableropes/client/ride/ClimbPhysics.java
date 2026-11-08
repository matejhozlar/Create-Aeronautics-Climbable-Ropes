package dev.matejhozlar.climbableropes.client.ride;

import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.ClimbableRopesKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ClimbPhysics {
    private static final double CLIMB_SIDE_OFFSET = 0.3;
    // Rope |tangent.y| from which the climber circles the rope with the camera, and up to which they stay under it.
    private static final double STEEP_Y = 0.5;
    private static final double FLAT_Y = 0.26;
    private static final Vec3 DOWN = new Vec3(0.0, -1.0, 0.0);
    private static final double AT_BOTTOM_DIST_SQR = 1.0;
    // Below this |y| threshold the rope is treated as horizontal (look-based forward).
    private static final double NEAR_HORIZONTAL_EPS = 0.05;

    private ClimbPhysics() {}

    private static double anchorHeight(Player player) {
        return player.getBoundingBox().getYsize() + 0.5 * player.getScale();
    }

    public static Vec3 anchor(Player player) {
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
        boolean atBottom = ropePoint.distanceToSqr(bottom) < AT_BOTTOM_DIST_SQR;
        Vec3 snapPoint = atBottom ? bottom : ropePoint;
        double targetY = atBottom ? bottom.y : ropePoint.y - anchorHeight(player);

        double yawRad = Math.toRadians(player.getYRot());
        Vec3 offsetTarget = new Vec3(
                snapPoint.x + Math.sin(yawRad) * CLIMB_SIDE_OFFSET,
                targetY,
                snapPoint.z - Math.cos(yawRad) * CLIMB_SIDE_OFFSET);
        if (tryPlace(mc, player, offsetTarget)) return true;
        return tryPlace(mc, player, new Vec3(snapPoint.x, targetY, snapPoint.z));
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
        Vec3 target = ropeWorld.add(sideOffset(player, ClimbableRopesKeybinds.climbYaw(), forward));
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

    // Keeps the anchor off the rope, perpendicular to it. The anchor sits above the eyes, so a rope that leans
    // away from the player passes closer to the face than to the anchor; the offset grows to keep the rope
    // CLIMB_SIDE_OFFSET in front of the eyes. It stays perpendicular so the closest rope point does not drift.
    public static Vec3 sideOffset(Player player, double yaw, Vec3 ropeDir) {
        Vec3 up = uphill(ropeDir);
        Vec3 side = hangSide(yaw, up);
        if (side.lengthSqr() < 1.0e-8) return Vec3.ZERO;

        double steep = steepness(up);
        double eyeDrop = anchorHeight(player) - player.getEyeHeight();
        double atEyes = CLIMB_SIDE_OFFSET * up.y - eyeDrop * backOf(yaw).dot(up);
        double dist = Math.max(CLIMB_SIDE_OFFSET, Mth.lerp(steep, CLIMB_SIDE_OFFSET, atEyes));

        // Under a flatter rope only the sideways part moves the body, so circling the camera never lifts or drops it.
        Vec3 across = up.cross(DOWN);
        if (across.lengthSqr() < 1.0e-8) return side.scale(dist);
        across = across.normalize();
        return across.scale(side.dot(across)).lerp(side, steep).scale(dist);
    }

    // Flatter ropes swing the climber side, beneath, side and never over the top, even when looking straight up them.
    public static Vec3 hangSide(double yaw, Vec3 ropeDir) {
        Vec3 up = uphill(ropeDir);
        Vec3 back = backOf(yaw);
        Vec3 orbit = perpendicular(back, up);
        Vec3 beneath = perpendicular(DOWN, up);
        double steep = steepness(up);
        if (steep >= 1.0 || beneath.lengthSqr() < 1.0e-8) {
            return orbit.lengthSqr() < 1.0e-12 ? Vec3.ZERO : orbit.normalize();
        }
        beneath = beneath.normalize();
        Vec3 across = up.cross(beneath);
        double swing = Math.asin(Mth.clamp(back.dot(across), -1.0, 1.0));
        Vec3 below = beneath.scale(Math.cos(swing)).add(across.scale(Math.sin(swing)));
        if (steep > 0.0 && orbit.lengthSqr() > 1.0e-12) below = below.lerp(orbit.normalize(), steep);
        return below.lengthSqr() < 1.0e-8 ? beneath : below.normalize();
    }

    private static Vec3 uphill(Vec3 ropeDir) {
        Vec3 dir = ropeDir.normalize();
        return dir.y < 0.0 ? dir.scale(-1.0) : dir;
    }

    private static double steepness(Vec3 up) {
        return Mth.clamp((up.y - FLAT_Y) / (STEEP_Y - FLAT_Y), 0.0, 1.0);
    }

    private static Vec3 backOf(double yaw) {
        double yawRad = Math.toRadians(yaw);
        return new Vec3(Math.sin(yawRad), 0.0, -Math.cos(yawRad));
    }

    private static Vec3 perpendicular(Vec3 v, Vec3 axis) {
        return v.subtract(axis.scale(v.dot(axis)));
    }
}
