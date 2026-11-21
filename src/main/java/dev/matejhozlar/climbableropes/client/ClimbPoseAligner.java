package dev.matejhozlar.climbableropes.client;

import dev.matejhozlar.climbableropes.ClimbableRopes;
import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.rig.ClimbRigs;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@EventBusSubscriber(modid = ClimbableRopes.MODID, value = Dist.CLIENT)
public final class ClimbPoseAligner {
    private static final Set<Player> TRANSFORMED = Collections.newSetFromMap(new IdentityHashMap<>());

    private ClimbPoseAligner() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get() || ClimbRigs.isSuppressed()) return;
        if (!ClimbRigs.placeBody(event.getEntity(), event.getPartialTick(), event.getPoseStack())) return;
        TRANSFORMED.add(event.getEntity());
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        ClimbRigs.clearBends(event.getRenderer().getModel());
        if (TRANSFORMED.remove(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) TRANSFORMED.clear();
    }
}
