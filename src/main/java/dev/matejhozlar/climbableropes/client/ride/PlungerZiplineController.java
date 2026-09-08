package dev.matejhozlar.climbableropes.client.ride;

import com.simibubi.create.AllTags;
import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.ClimbAnimationController;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ZiplineClientManager;
import dev.simulated_team.simulated.content.entities.launched_plunger.LaunchedPlungerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class PlungerZiplineController {
    private static final double EXIT_DOT_THRESHOLD = 0.6;
    private static final double END_PROXIMITY = 0.5;
    private static final double DAMPING = -0.6;
    private static final double ASSISTANCE = 0.04;
    private static final double SPRING = 0.3;

    private static LaunchedPlungerEntity plungerA;
    private static LaunchedPlungerEntity plungerB;
    private static int groundedTimer;

    private PlungerZiplineController() {}

    static boolean isRiding() {
        return plungerA != null;
    }

    private static boolean isRidingPair(PlungerRope.Pair pair) {
        return plungerA != null && pair.sameEnds(plungerA, plungerB);
    }

    static void reset() {
        plungerA = null;
        plungerB = null;
        groundedTimer = 0;
    }

    static boolean tryHoverEmbark(Minecraft mc, LocalPlayer player, boolean justPressed) {
        if (!justPressed) return false;
        PlungerRope.Pair pair = PlungerRope.findHoveredPair(mc, player);
        if (pair == null) return false;
        embark(pair, mc, player);
        return true;
    }

    static void tick(Minecraft mc, LocalPlayer player) {
        if (mc.isPaused()) return;

        if (!AllTags.AllItemTags.CHAIN_RIDEABLE.matches(player.getMainHandItem())) {
            disembark();
            return;
        }
        if (ZiplineClientManager.ridingRope != null) {
            disembark();
            return;
        }
        if (!PlungerRope.isPlunged(plungerA) || !PlungerRope.isPlunged(plungerB)) {
            disembark();
            return;
        }

        PlungerRope.RopeEnd endA = PlungerRope.ropeEnd(plungerA);
        PlungerRope.RopeEnd endB = PlungerRope.ropeEnd(plungerB);
        Vec3 a = endA.position();
        Vec3 b = endB.position();
        Vec3 ab = b.subtract(a);
        double abLen = ab.length();
        if (abLen < 1e-4) {
            disembark();
            return;
        }
        Vec3 dir = ab.scale(1.0 / abLen);

        Vec3 anchor = ClimbPhysics.anchor(player);
        double t = Mth.clamp(anchor.subtract(a).dot(dir), 0.0, abLen);
        Vec3 carry = RopeMotion.carry(player, endA.velocity().lerp(endB.velocity(), t / abLen));
        // Vanilla drag would erode a carry placed in deltaMovement and skew the rope-relative physics below.
        anchor = anchor.add(moveBy(mc, player, carry));
        t = Mth.clamp(anchor.subtract(a).dot(dir), 0.0, abLen);
        Vec3 ropeWorld = a.add(dir.scale(t));

        if (player.onGround()) groundedTimer++;
        else groundedTimer = 0;

        if (groundedTimer > ClimbableRopesConfig.BOTTOM_GROUNDED_DISMOUNT_TICKS.get()
                || player.isShiftKeyDown()
                || player.getAbilities().flying) {
            letGo(player, carry);
            return;
        }

        Vec3 v = player.getDeltaMovement();
        if (v.lengthSqr() > 1e-8) {
            Vec3 vn = v.normalize();
            boolean atEnd = t > abLen - END_PROXIMITY;
            boolean atStart = t < END_PROXIMITY;
            if ((atEnd && vn.dot(dir) > EXIT_DOT_THRESHOLD)
                    || (atStart && vn.dot(dir) < -EXIT_DOT_THRESHOLD)) {
                letGo(player, carry);
                return;
            }
        }

        Vec3 diff = ropeWorld.subtract(anchor);
        double maxLeash = ClimbableRopesConfig.MAX_LEASH_DISTANCE.get();
        if (diff.lengthSqr() > maxLeash * maxLeash) {
            letGo(player, carry);
            return;
        }

        Vec3 dampingForce = v.scale(DAMPING);
        dampingForce = dampingForce.subtract(dir.scale(dir.dot(dampingForce)));
        Vec3 assistanceForce = dir.scale(v.dot(dir) * ASSISTANCE);
        double diffLen = diff.lengthSqr() > 0.0 ? Mth.sqrt((float) diff.length()) : 0.0;
        Vec3 springForce = diff.scale(diffLen * SPRING);

        player.setDeltaMovement(v.add(dampingForce).add(assistanceForce).add(springForce));
        player.fallDistance = 0.0F;

        RopeRide.keepAlive(plungerA.getUUID());
    }

    private static Vec3 moveBy(Minecraft mc, LocalPlayer player, Vec3 delta) {
        if (delta.lengthSqr() < 1.0e-12) return Vec3.ZERO;
        Vec3 moved = Entity.collideBoundingBox(player, delta, player.getBoundingBox(), mc.level, List.of());
        player.setPos(player.position().add(moved));
        return moved;
    }

    private static void letGo(LocalPlayer player, Vec3 carry) {
        player.setDeltaMovement(player.getDeltaMovement().add(carry));
        disembark();
    }

    private static void embark(PlungerRope.Pair pair, Minecraft mc, LocalPlayer player) {
        if (isRidingPair(pair)) return;
        RopeRideDispatcher.leaveActiveRides();
        plungerA = pair.a();
        plungerB = pair.b();
        groundedTimer = 0;

        RopeRide.stopFlight(player);
        RopeRide.notifyEmbark(mc, plungerA.getUUID(), ClimbAnimationController.ClimbMode.PLUNGER_ZIPLINE);
    }

    static void disembark() {
        if (plungerA == null) return;
        RopeRide.notifyDisembark(plungerA.getUUID());
        reset();
    }
}
