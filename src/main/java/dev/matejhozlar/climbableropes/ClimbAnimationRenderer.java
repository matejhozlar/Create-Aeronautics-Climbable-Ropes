package dev.matejhozlar.climbableropes;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Quaternionf;

@EventBusSubscriber(modid = ClimbableRopes.MODID, value = Dist.CLIENT)
public final class ClimbAnimationRenderer {
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private static final double ALIGN_SMOOTHING = 0.2;

    private static Vec3 alignedUp = WORLD_UP;
    private static boolean transformed;

    private ClimbAnimationRenderer() {}

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        transformed = false;
        if (!ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get()) return;
        if (event.getEntity() != Minecraft.getInstance().player) return;

        Vec3 target = WORLD_UP;
        Vec3 tangent = ClimbAnimationController.currentRopeTangent();
        if (tangent != null && tangent.lengthSqr() > 1.0e-8) {
            Vec3 dir = tangent.normalize();
            target = dir.y < 0.0 ? dir.scale(-1.0) : dir;
        }

        alignedUp = lerp(alignedUp, target, ALIGN_SMOOTHING);
        double len = alignedUp.length();
        if (len < 1.0e-6) return;
        alignedUp = alignedUp.scale(1.0 / len);
        if (alignedUp.distanceToSqr(WORLD_UP) < 1.0e-6) return;

        double pivotY = event.getEntity().getBoundingBox().getYsize()
                + 0.5 * event.getEntity().getScale();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0, pivotY, 0.0);
        pose.mulPose(new Quaternionf().rotateTo(
                0.0f, 1.0f, 0.0f,
                (float) alignedUp.x, (float) alignedUp.y, (float) alignedUp.z));
        pose.translate(0.0, -pivotY, 0.0);
        transformed = true;
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (transformed) {
            event.getPoseStack().popPose();
            transformed = false;
        }
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double t) {
        return new Vec3(
                from.x + (to.x - from.x) * t,
                from.y + (to.y - from.y) * t,
                from.z + (to.z - from.z) * t);
    }
}
