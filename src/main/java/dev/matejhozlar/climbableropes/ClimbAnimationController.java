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
    }

    public static void onTick(Vec3 tangent, ClimbState state) {
        if (!ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get()) {
            removeLayer();
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || layer == null) return;

        ropeTangent = tangent;
        if (speedModifier != null) {
            speedModifier.speed = ClimbableRopesConfig.ANIMATION_SPEED_MULTIPLIER.get().floatValue();
        }

        ResourceLocation desired = desiredAnimation(state, tangent);
        if (!Objects.equals(desired, currentAnimId)) {
            applyAnimation(desired, currentAnimId != null);
            currentAnimId = desired;
        }
    }

    public static void onDisembark() {
        if (layer != null) {
            layer.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE), null);
        }
        currentMode = null;
        currentAnimId = null;
        ropeTangent = null;
    }

    public static boolean isCustomPoseActive() {
        return layer != null && currentAnimId != null;
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

    private static IAnimation load(ResourceLocation id) {
        if (id == null) return null;
        var playable = PlayerAnimationRegistry.getAnimation(id);
        if (!(playable instanceof KeyframeAnimation kf)) return null;
        return new KeyframeAnimationPlayer(kf);
    }
}
