package dev.matejhozlar.climbableropes.mixin;

import dev.matejhozlar.climbableropes.client.rig.ClimbRigs;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// HumanoidModel rather than PlayerModel so the pose lands before PlayerModel copies limbs onto the skin overlays.
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void climbableRopes$poseClimb(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                         float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if ((Object) this instanceof PlayerModel<?> model && entity instanceof AbstractClientPlayer player) {
            ClimbRigs.pose(model, player, ageInTicks);
        }
    }
}
