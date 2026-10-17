package dev.matejhozlar.climbableropes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.matejhozlar.climbableropes.client.rig.BendablePart;
import dev.matejhozlar.climbableropes.client.rig.LimbBend;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

// Runs ahead of renderers that replace ModelPart drawing wholesale (Sodium, Embeddium) so bent limbs still bend.
@Mixin(value = ModelPart.class, priority = 900)
public abstract class ModelPartMixin implements BendablePart {
    @Shadow @Final private List<ModelPart.Cube> cubes;
    @Shadow @Final private Map<String, ModelPart> children;
    @Shadow public boolean visible;
    @Shadow public boolean skipDraw;

    @Unique private LimbBend climbableRopes$bend;

    @Shadow public abstract void translateAndRotate(PoseStack poseStack);

    @Override
    public LimbBend climbableRopes$getBend() {
        return climbableRopes$bend;
    }

    @Override
    public void climbableRopes$setBend(LimbBend bend) {
        climbableRopes$bend = bend;
    }

    @Override
    public float climbableRopes$centerX() {
        if (cubes.isEmpty()) return 0.0F;
        ModelPart.Cube cube = cubes.getFirst();
        return (cube.minX + cube.maxX) * 0.5F;
    }

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void climbableRopes$copyBend(ModelPart other, CallbackInfo ci) {
        climbableRopes$bend = ((BendablePart) (Object) other).climbableRopes$getBend();
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("HEAD"), cancellable = true)
    private void climbableRopes$renderBent(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                                           int packedOverlay, int color, CallbackInfo ci) {
        LimbBend bend = climbableRopes$bend;
        if (bend == null || bend.isStraight() || !visible) return;
        ci.cancel();
        if (cubes.isEmpty() && children.isEmpty()) return;
        poseStack.pushPose();
        translateAndRotate(poseStack);
        if (!skipDraw) {
            for (ModelPart.Cube cube : cubes) {
                bend.render(cube, poseStack.last(), buffer, packedLight, packedOverlay, color);
            }
        }
        for (ModelPart child : children.values()) {
            child.render(poseStack, buffer, packedLight, packedOverlay, color);
        }
        poseStack.popPose();
    }
}
