package dev.matejhozlar.climbableropes;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Optional;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class ClimbAnimationController {
    public enum ClimbMode { HANGING_STRAND, PLUNGER_ROPE, PLUNGER_ZIPLINE }
    public enum ClimbState { IDLE, CLIMB_UP, DESCEND, SLIDE }

    private static final int LAYER_PRIORITY = 40;
    private static final int FADE_TICKS = 4;
    private static final double MAX_BODY_TILT_RAD = Math.toRadians(60.0);
    private static final ResourceLocation ANIM_CLIMB_UP =
            ResourceLocation.fromNamespaceAndPath(ClimbableRopes.MODID, "climb_up");
    private static final ResourceLocation ANIM_DESCEND =
            ResourceLocation.fromNamespaceAndPath(ClimbableRopes.MODID, "descend");

    private static ModifierLayer<IAnimation> layer;
    private static SpeedModifier speedModifier;
    private static UUID registeredFor;
    private static ClimbMode currentMode;
    private static ClimbState currentState;
    private static float bodyPitchRad;

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
        bodyPitchRad = 0f;
        ClimbState initial = ClimbState.IDLE;
        setAnimation(initial, false);
        currentState = initial;
    }

    public static void onTick(Vec3 ropeTangent, ClimbState state) {
        if (!ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get()) {
            removeLayer();
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || layer == null) return;
        if (ropeTangent != null) {
            bodyPitchRad = computeBodyTilt(ropeTangent, player);
        }
        if (speedModifier != null) {
            speedModifier.speed = ClimbableRopesConfig.ANIMATION_SPEED_MULTIPLIER.get().floatValue();
        }
        if (state != currentState) {
            setAnimation(state, true);
            currentState = state;
        }
    }

    private static float computeBodyTilt(Vec3 ropeTangent, LocalPlayer player) {
        double len = ropeTangent.length();
        if (len < 1.0e-6) return 0f;
        double tx = ropeTangent.x / len;
        double ty = ropeTangent.y / len;
        double tz = ropeTangent.z / len;
        double horizontal = Math.sqrt(Math.max(0.0, tx * tx + tz * tz));
        if (horizontal < 1.0e-4) return 0f;
        double angleFromVertical = Math.atan2(horizontal, Math.abs(ty));
        double yawRad = Math.toRadians(player.getYRot());
        double forwardDot = (-Math.sin(yawRad) * tx + Math.cos(yawRad) * tz) / horizontal;
        double signed = angleFromVertical * Math.signum(forwardDot);
        return (float) Math.max(-MAX_BODY_TILT_RAD, Math.min(MAX_BODY_TILT_RAD, signed));
    }

    public static void onDisembark() {
        if (layer != null) {
            layer.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE), null);
        }
        currentMode = null;
        currentState = null;
        bodyPitchRad = 0f;
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
        currentState = null;
        bodyPitchRad = 0f;
    }

    private static void ensureLayer(LocalPlayer player) {
        UUID id = player.getUUID();
        if (layer != null && id.equals(registeredFor)) return;
        if (layer != null) removeLayer();

        layer = new ModifierLayer<>();
        speedModifier = new SpeedModifier();
        speedModifier.speed = ClimbableRopesConfig.ANIMATION_SPEED_MULTIPLIER.get().floatValue();
        layer.addModifierLast(speedModifier);
        layer.addModifierLast(new AdjustmentModifier(name ->
                "body".equals(name)
                        ? Optional.of(new AdjustmentModifier.PartModifier(
                                new Vec3f(bodyPitchRad, 0f, 0f),
                                new Vec3f(0f, 0f, 0f),
                                new Vec3f(0f, 0f, 0f)))
                        : Optional.empty()));

        PlayerAnimationAccess.getPlayerAnimLayer(player).addAnimLayer(LAYER_PRIORITY, layer);
        registeredFor = id;
    }

    private static void setAnimation(ClimbState state, boolean fade) {
        IAnimation anim = animationFor(state);
        if (fade) {
            layer.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE), anim);
        } else {
            layer.setAnimation(anim);
        }
    }

    private static IAnimation animationFor(ClimbState state) {
        if (state == null || state == ClimbState.IDLE) return null;
        if (currentMode == ClimbMode.PLUNGER_ZIPLINE) return null;
        ResourceLocation id = switch (state) {
            case CLIMB_UP -> ANIM_CLIMB_UP;
            case DESCEND, SLIDE -> ANIM_DESCEND;
            default -> null;
        };
        if (id == null) return null;
        var playable = PlayerAnimationRegistry.getAnimation(id);
        if (!(playable instanceof KeyframeAnimation kf)) return null;
        return new KeyframeAnimationPlayer(kf);
    }
}
