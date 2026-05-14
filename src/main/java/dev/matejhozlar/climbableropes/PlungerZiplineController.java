package dev.matejhozlar.climbableropes;

import com.simibubi.create.AllTags;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ZiplineClientManager;
import dev.simulated_team.simulated.content.entities.launched_plunger.LaunchedPlungerEntity;
import dev.simulated_team.simulated.network.packets.RopeRidingPacket;
import foundry.veil.api.network.VeilPacketManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

final class PlungerZiplineController {
    private static final int GROUNDED_DISMOUNT_TICKS = 5;
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

    private static boolean isRidingPair(PlungerClimbController.Pair pair) {
        if (plungerA == null) return false;
        int a = pair.a().getId();
        int b = pair.b().getId();
        int x = plungerA.getId();
        int y = plungerB.getId();
        return (a == x && b == y) || (a == y && b == x);
    }

    static void reset() {
        plungerA = null;
        plungerB = null;
        groundedTimer = 0;
    }

    static void tryHoverEmbark(Minecraft mc, LocalPlayer player, boolean justPressed) {
        if (!justPressed) return;
        PlungerClimbController.Pair pair = PlungerClimbController.findHoveredPair(mc, player);
        if (pair == null) return;
        embark(pair, mc, player);
    }

    static void ridingTick(Minecraft mc, LocalPlayer player) {
        if (mc.isPaused()) return;

        if (!AllTags.AllItemTags.CHAIN_RIDEABLE.matches(player.getMainHandItem())) {
            disembark();
            return;
        }
        if (ZiplineClientManager.ridingRope != null) {
            disembark();
            return;
        }
        if (plungerA == null || plungerA.isRemoved() || !plungerA.isPlunged()
                || plungerB == null || plungerB.isRemoved() || !plungerB.isPlunged()) {
            disembark();
            return;
        }

        if (player.onGround()) groundedTimer++;
        else groundedTimer = 0;

        if (groundedTimer > GROUNDED_DISMOUNT_TICKS
                || player.isShiftKeyDown()
                || player.getAbilities().flying) {
            disembark();
            return;
        }

        Vec3 a = PlungerClimbController.ropeEndWorld(plungerA);
        Vec3 b = PlungerClimbController.ropeEndWorld(plungerB);
        Vec3 ab = b.subtract(a);
        double abLen = ab.length();
        if (abLen < 1e-4) {
            disembark();
            return;
        }
        Vec3 dir = ab.scale(1.0 / abLen);

        Vec3 anchor = anchor(player);
        double t = Mth.clamp(anchor.subtract(a).dot(dir), 0.0, abLen);
        Vec3 ropeWorld = a.add(dir.scale(t));

        Vec3 v = player.getDeltaMovement();
        if (v.lengthSqr() > 1e-8) {
            Vec3 vn = v.normalize();
            boolean atEnd = t > abLen - END_PROXIMITY;
            boolean atStart = t < END_PROXIMITY;
            if ((atEnd && vn.dot(dir) > EXIT_DOT_THRESHOLD)
                    || (atStart && vn.dot(dir) < -EXIT_DOT_THRESHOLD)) {
                disembark();
                return;
            }
        }

        Vec3 diff = ropeWorld.subtract(anchor);
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
        if (diff.lengthSqr() > reach * reach) {
            disembark();
            return;
        }

        Vec3 dampingForce = v.scale(DAMPING);
        dampingForce = dampingForce.subtract(dir.scale(dir.dot(dampingForce)));
        Vec3 assistanceForce = dir.scale(v.dot(dir) * ASSISTANCE);
        double diffLen = diff.lengthSqr() > 0.0 ? Mth.sqrt((float) diff.length()) : 0.0;
        Vec3 springForce = diff.scale(diffLen * SPRING);

        player.setDeltaMovement(v.add(dampingForce).add(assistanceForce).add(springForce));
        player.fallDistance = 0.0F;

        if (AnimationTickHolder.getTicks() % 10 == 0) {
            VeilPacketManager.server().sendPacket(new RopeRidingPacket(plungerA.getUUID(), false));
        }
    }

    private static void embark(PlungerClimbController.Pair pair, Minecraft mc, LocalPlayer player) {
        if (isRidingPair(pair)) return;
        ClimbController.leaveActiveRides();
        plungerA = pair.a();
        plungerB = pair.b();
        groundedTimer = 0;

        player.getAbilities().flying = false;
        player.stopFallFlying();

        mc.gui.setOverlayMessage(
                Component.translatable("mount.onboard", mc.options.keyShift.getTranslatedKeyMessage()),
                false);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 1f, 0.5f));

        VeilPacketManager.server().sendPacket(new RopeRidingPacket(plungerA.getUUID(), false));
    }

    static void disembark() {
        if (plungerA == null) return;
        VeilPacketManager.server().sendPacket(new RopeRidingPacket(plungerA.getUUID(), true));
        reset();
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 0.75f, 0.35f));
    }

    private static Vec3 anchor(LocalPlayer player) {
        double chainYOffset = 0.5 * player.getScale();
        return player.position().add(0.0, player.getBoundingBox().getYsize() + chainYOffset, 0.0);
    }
}
