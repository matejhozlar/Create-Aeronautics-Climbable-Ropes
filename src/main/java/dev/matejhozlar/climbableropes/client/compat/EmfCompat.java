package dev.matejhozlar.climbableropes.client.compat;

import com.mojang.logging.LogUtils;
import dev.matejhozlar.climbableropes.client.ClimbAnimationController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import traben.entity_model_features.EMFAnimationApi;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// Must only be loaded when Entity Model Features is present.
@OnlyIn(Dist.CLIENT)
public final class EmfCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> PAUSED = new HashSet<>();
    private static boolean disabled;

    private EmfCompat() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EmfCompat::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (disabled || mc.level == null) return;
        try {
            for (Player player : mc.level.players()) {
                if (ClimbAnimationController.isCustomPoseActive(player)) {
                    EMFAnimationApi.pauseAllCustomAnimationsForEntity(EMFAnimationApi.emfEntityOf(player));
                    PAUSED.add(player.getUUID());
                } else if (PAUSED.remove(player.getUUID())) {
                    EMFAnimationApi.resumeAllCustomAnimationsForEntity(EMFAnimationApi.emfEntityOf(player));
                }
            }
        } catch (LinkageError e) {
            disabled = true;
            LOGGER.warn("Entity Model Features animation API is incompatible, custom player models may override climb animations", e);
        }
    }
}
