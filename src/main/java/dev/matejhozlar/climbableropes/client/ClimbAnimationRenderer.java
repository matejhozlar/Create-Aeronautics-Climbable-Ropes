package dev.matejhozlar.climbableropes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.matejhozlar.climbableropes.ClimbableRopes;
import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = ClimbableRopes.MODID, value = Dist.CLIENT)
public final class ClimbAnimationRenderer {
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private static final double ALIGN_SMOOTHING = 0.2;

    private static final Map<UUID, Vec3> ALIGNED_UP = new HashMap<>();
    private static final Set<UUID> TRANSFORMED = new HashSet<>();

    private ClimbAnimationRenderer() {}

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get()) return;

        Player entity = event.getEntity();
        UUID id = entity.getUUID();

        Vec3 target = WORLD_UP;
        Vec3 tangent = tangentFor(entity);
        if (tangent != null && tangent.lengthSqr() > 1.0e-8) {
            Vec3 dir = tangent.normalize();
            target = dir.y < 0.0 ? dir.scale(-1.0) : dir;
        }

        Vec3 aligned = lerp(ALIGNED_UP.getOrDefault(id, WORLD_UP), target, ALIGN_SMOOTHING);
        double len = aligned.length();
        if (len < 1.0e-6) {
            ALIGNED_UP.remove(id);
            return;
        }
        aligned = aligned.scale(1.0 / len);
        if (aligned.distanceToSqr(WORLD_UP) < 1.0e-6) {
            ALIGNED_UP.remove(id);
            return;
        }
        ALIGNED_UP.put(id, aligned);

        double pivotY = entity.getBoundingBox().getYsize() + 0.5 * entity.getScale();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0, pivotY, 0.0);
        pose.mulPose(new Quaternionf().rotateTo(
                0.0f, 1.0f, 0.0f,
                (float) aligned.x, (float) aligned.y, (float) aligned.z));
        pose.translate(0.0, -pivotY, 0.0);
        TRANSFORMED.add(id);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (TRANSFORMED.remove(event.getEntity().getUUID())) {
            event.getPoseStack().popPose();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ALIGNED_UP.clear();
            TRANSFORMED.clear();
            return;
        }
        ALIGNED_UP.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
        TRANSFORMED.removeIf(id -> mc.level.getPlayerByUUID(id) == null);
    }

    private static Vec3 tangentFor(Player entity) {
        LocalPlayer local = Minecraft.getInstance().player;
        if (local != null && entity == local) {
            return ClimbAnimationController.currentRopeTangent();
        }
        return RemoteClimbAnimations.tangentFor(entity.getUUID());
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double t) {
        return new Vec3(
                from.x + (to.x - from.x) * t,
                from.y + (to.y - from.y) * t,
                from.z + (to.z - from.z) * t);
    }
}
