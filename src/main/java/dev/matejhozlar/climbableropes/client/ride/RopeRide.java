package dev.matejhozlar.climbableropes.client.ride;

import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.ClimbAnimationController;
import dev.simulated_team.simulated.index.SimClickInteractions;
import dev.simulated_team.simulated.network.packets.RopeRidingPacket;
import foundry.veil.api.network.VeilPacketManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

final class RopeRide {
    private static final int RIDING_PACKET_INTERVAL = 10;

    private RopeRide() {}

    static boolean climbInterrupted(LocalPlayer player) {
        return player.getAbilities().flying
                || !player.getMainHandItem().isEmpty()
                || SimClickInteractions.HANDLE_HANDLER.isActive();
    }

    static void stopFlight(LocalPlayer player) {
        player.getAbilities().flying = false;
        player.stopFallFlying();
    }

    static void jumpOff(LocalPlayer player) {
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x, Math.max(v.y, ClimbableRopesConfig.JUMP_OFF_VELOCITY.get()), v.z);
    }

    static void notifyEmbark(Minecraft mc, UUID ropeId, ClimbAnimationController.ClimbMode mode) {
        mc.gui.setOverlayMessage(
                Component.translatable("mount.onboard", mc.options.keyShift.getTranslatedKeyMessage()),
                false);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 1f, 0.5f));
        VeilPacketManager.server().sendPacket(new RopeRidingPacket(ropeId, false));
        ClimbAnimationController.onEmbark(mode);
    }

    static void notifyDisembark(UUID ropeId) {
        VeilPacketManager.server().sendPacket(new RopeRidingPacket(ropeId, true));
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 0.75f, 0.35f));
        ClimbAnimationController.onDisembark();
    }

    static void keepAlive(UUID ropeId) {
        if (AnimationTickHolder.getTicks() % RIDING_PACKET_INTERVAL == 0) {
            VeilPacketManager.server().sendPacket(new RopeRidingPacket(ropeId, false));
        }
    }
}
