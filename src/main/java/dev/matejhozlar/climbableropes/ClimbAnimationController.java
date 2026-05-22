package dev.matejhozlar.climbableropes;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.matejhozlar.climbableropes.network.ClimbAnimUpdatePacket;
import dev.matejhozlar.climbableropes.network.ClimbableRopesNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Objects;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class ClimbAnimationController {
    public enum ClimbMode { HANGING_STRAND, PLUNGER_ROPE, PLUNGER_ZIPLINE }
    public enum ClimbState { IDLE, CLIMB_UP, DESCEND, SLIDE }

    private static final int LAYER_PRIORITY = 40;
    private static final int FADE_TICKS = 4;
    // Resend the current state periodically so newly-tracking observers pick it up and the rope tangent stays fresh.
    private static final int SYNC_REFRESH_INTERVAL = 10;
    // cos(45deg): ropes flatter than this fall back to Create's hanging pose.
    private static final double VERTICAL_TANGENT_Y = 0.7071;
    private static final ResourceLocation ANIM_CLIMB_UP =
            ResourceLocation.fromNamespaceAndPath(ClimbableRopes.MODID, "climb_up");
    private static final ResourceLocation ANIM_DESCEND =
            ResourceLocation.fromNamespaceAndPath(ClimbableRopes.MODID, "descend");
    private static final ResourceLocation ANIM_SLIDE =
            ResourceLocation.fromNamespaceAndPath(ClimbableRopes.MODID, "slide");
    private static final ResourceLocation ANIM_IDLE =
            ResourceLocation.fromNamespaceAndPath(ClimbableRopes.MODID, "idle");

    private static ModifierLayer<IAnimation> layer;
    private static SpeedModifier speedModifier;
    private static UUID registeredFor;
    private static ClimbMode currentMode;
    private static ResourceLocation currentAnimId;
    private static Vec3 ropeTangent;
    private static int syncRefreshCounter;
    // True between embark and the first climb tick, before the rope angle is known. Suppresses
    // Create's hanging pose during that gap so it does not flash before our animation is applied.
    private static boolean embarkPending;

    private ClimbAnimationController() {}

    public static void onEmbark(ClimbMode mode) {
        if (!ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get()) {
            removeLayer();
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ensureLayer(player);
        currentMode = mode;
        currentAnimId = null;
        ropeTangent = null;
        embarkPending = mode != ClimbMode.PLUNGER_ZIPLINE;
    }

    public static void onTick(Vec3 tangent, ClimbState state) {
        if (!ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get()) {
            if (currentAnimId != null) sendSync(null, null);
            removeLayer();
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || layer == null) return;

        embarkPending = false;
        ropeTangent = tangent;
        if (speedModifier != null) {
            speedModifier.speed = ClimbableRopesConfig.ANIMATION_SPEED_MULTIPLIER.get().floatValue();
        }

        ResourceLocation desired = desiredAnimation(state, tangent);
        if (!Objects.equals(desired, currentAnimId)) {
            applyAnimation(desired, currentAnimId != null);
            currentAnimId = desired;
            sendSync(desired, tangent);
            syncRefreshCounter = 0;
        } else if (desired != null && ++syncRefreshCounter >= SYNC_REFRESH_INTERVAL) {
            sendSync(desired, tangent);
            syncRefreshCounter = 0;
        }
    }

    public static void onDisembark() {
        if (layer != null) {
            layer.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE), null);
        }
        if (currentAnimId != null) sendSync(null, null);
        currentMode = null;
        currentAnimId = null;
        ropeTangent = null;
        syncRefreshCounter = 0;
        embarkPending = false;
    }

    public static boolean isCustomPoseActive() {
        return layer != null && (currentAnimId != null || embarkPending);
    }

    public static Vec3 currentRopeTangent() {
        return currentAnimId != null ? ropeTangent : null;
    }

    private static ResourceLocation desiredAnimation(ClimbState state, Vec3 tangent) {
        if (currentMode == ClimbMode.PLUNGER_ZIPLINE || state == null) return null;
        if (!isVerticalish(tangent)) return null;
        return switch (state) {
            case CLIMB_UP -> ANIM_CLIMB_UP;
            case DESCEND -> ANIM_DESCEND;
            case SLIDE -> ANIM_SLIDE;
            case IDLE -> ANIM_IDLE;
        };
    }

    private static boolean isVerticalish(Vec3 tangent) {
        if (tangent == null) return false;
        double len = tangent.length();
        return len > 1.0e-6 && Math.abs(tangent.y) / len >= VERTICAL_TANGENT_Y;
    }

    private static void removeLayer() {
        if (layer == null) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            try {
                PlayerAnimationAccess.getPlayerAnimLayer(player).removeLayer(layer);
            } catch (IllegalArgumentException ignored) {
            }
        }
        layer = null;
        speedModifier = null;
        registeredFor = null;
        currentMode = null;
        currentAnimId = null;
        ropeTangent = null;
        embarkPending = false;
    }

    private static void ensureLayer(LocalPlayer player) {
        UUID id = player.getUUID();
        if (layer != null && id.equals(registeredFor)) return;
        if (layer != null) removeLayer();

        layer = new ModifierLayer<>();
        speedModifier = new SpeedModifier();
        speedModifier.speed = ClimbableRopesConfig.ANIMATION_SPEED_MULTIPLIER.get().floatValue();
        layer.addModifierLast(speedModifier);

        PlayerAnimationAccess.getPlayerAnimLayer(player).addAnimLayer(LAYER_PRIORITY, layer);
        registeredFor = id;
    }

    private static void applyAnimation(ResourceLocation id, boolean fade) {
        IAnimation anim = load(id);
        if (fade) {
            layer.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE), anim);
        } else {
            layer.setAnimation(anim);
        }
    }

    static IAnimation load(ResourceLocation id) {
        if (id == null) return null;
        var playable = PlayerAnimationRegistry.getAnimation(id);
        if (!(playable instanceof KeyframeAnimation kf)) return null;
        return new KeyframeAnimationPlayer(kf);
    }

    private static void sendSync(ResourceLocation animation, Vec3 tangent) {
        if (animation == null) {
            ClimbableRopesNetwork.sendToServer(
                    new ClimbAnimUpdatePacket(false, ANIM_IDLE, 0.0, 0.0, 0.0));
        } else {
            ClimbableRopesNetwork.sendToServer(new ClimbAnimUpdatePacket(
                    true, animation, tangent.x, tangent.y, tangent.z));
        }
    }
}
