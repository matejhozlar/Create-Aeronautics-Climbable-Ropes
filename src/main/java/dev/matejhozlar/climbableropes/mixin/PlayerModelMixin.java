package dev.matejhozlar.climbableropes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.matejhozlar.climbableropes.client.rig.LimbBend;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {

    @Inject(method = "translateToHand", at = @At("TAIL"))
    private void climbableRopes$followForearm(HumanoidArm side, PoseStack poseStack, CallbackInfo ci) {
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        LimbBend.applyToHand(side == HumanoidArm.LEFT ? model.leftArm : model.rightArm, poseStack);
    }
}
