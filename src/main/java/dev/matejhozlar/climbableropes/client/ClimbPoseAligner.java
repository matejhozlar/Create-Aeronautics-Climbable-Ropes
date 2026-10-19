package dev.matejhozlar.climbableropes.client;

import dev.matejhozlar.climbableropes.ClimbableRopes;
import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.rig.ClimbRigs;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = ClimbableRopes.MODID, value = Dist.CLIENT)
public final class ClimbPoseAligner {
    private static final Set<UUID> TRANSFORMED = new HashSet<>();

    private ClimbPoseAligner() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get() || ClimbRigs.isSuppressed()) return;
        if (!ClimbRigs.placeBody(event.getEntity(), event.getPartialTick(), event.getPoseStack())) return;
        TRANSFORMED.add(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        ClimbRigs.clearBends(event.getRenderer().getModel());
        if (TRANSFORMED.remove(event.getEntity().getUUID())) {
            event.getPoseStack().popPose();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) TRANSFORMED.clear();
    }
}
