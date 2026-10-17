package dev.matejhozlar.climbableropes.mixin;

import dev.matejhozlar.climbableropes.client.rig.ClimbRigs;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// First-person arms reuse the third-person model and its setupAnim; keep the climb pose out of them.
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void climbableRopes$beginHand(CallbackInfo ci) {
        ClimbRigs.pushSuppression();
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void climbableRopes$endHand(CallbackInfo ci) {
        ClimbRigs.popSuppression();
    }
}
