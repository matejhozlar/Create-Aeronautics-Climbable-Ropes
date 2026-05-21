package dev.matejhozlar.climbableropes.mixin;

import com.simibubi.create.foundation.render.PlayerSkyhookRenderer;
import dev.matejhozlar.climbableropes.ClimbAnimationController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerSkyhookRenderer.class)
public class PlayerSkyhookRendererMixin {

    @Inject(method = "beforeSetupAnim", at = @At("HEAD"), cancellable = true, remap = false)
    private static void climbableRopes$cancelBefore(Player player, HumanoidModel<?> model, CallbackInfo ci) {
        if (!climbableRopes$shouldSuppress(player)) return;
        model.head.resetPose();
        model.hat.resetPose();
        model.body.resetPose();
        model.leftArm.resetPose();
        model.rightArm.resetPose();
        model.leftLeg.resetPose();
        model.rightLeg.resetPose();
        ci.cancel();
    }

    @Inject(method = "afterSetupAnim", at = @At("HEAD"), cancellable = true, remap = false)
    private static void climbableRopes$cancelAfter(Player player, HumanoidModel<?> model, CallbackInfo ci) {
        if (climbableRopes$shouldSuppress(player)) ci.cancel();
    }

    private static boolean climbableRopes$shouldSuppress(Player player) {
        Player local = Minecraft.getInstance().player;
        if (local == null || !player.getUUID().equals(local.getUUID())) return false;
        return ClimbAnimationController.isCustomPoseActive();
    }
}
