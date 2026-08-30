package dev.matejhozlar.climbableropes.client.compat;

import com.mojang.logging.LogUtils;
import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.ClimbAnimationController;
import dev.matejhozlar.climbableropes.mixin.PlayerSkyhookRendererAccessor;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ZiplineClientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
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
    // Never cleared on level change: EMF's paused set is process-wide and keyed by UUID,
    // so a player can only be un-paused once they are seen again.
    private static final Set<UUID> PAUSED = new HashSet<>();
    private static boolean disabled;

    private EmfCompat() {}

    public static void register() {
        // Must run after the climb tick handlers so the pause lands in the same tick the pose changes.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EmfCompat::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (disabled || mc.level == null) return;
        try {
            // Create's skyhook hanging pose (flat ropes, ziplines, chain conveyors) is
            // overwritten by EMF flight animations just like our climb clips, so pause
            // for hanging players even when no clip of ours is active. Gated on the
            // animation toggle so opting out returns EMF to stock behavior.
            boolean pauseHanging = ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get();
            Set<UUID> hanging = pauseHanging
                    ? PlayerSkyhookRendererAccessor.getHangingPlayers()
                    : Set.of();
            // hangingPlayers is fed by a server broadcast, so it lags embark by a round
            // trip; client-side riding state covers the local player from the first tick.
            boolean localOnRope = pauseHanging
                    && (ClimbAnimationController.isLocalPlayerAttached()
                            || ZiplineClientManager.ridingRope != null);
            for (Player player : mc.level.players()) {
                if (ClimbAnimationController.isCustomPoseActive(player)
                        || hanging.contains(player.getUUID())
                        || (localOnRope && player == mc.player)) {
                    EMFAnimationApi.pauseAllCustomAnimationsForEntity(EMFAnimationApi.emfEntityOf(player));
                    PAUSED.add(player.getUUID());
                } else if (PAUSED.remove(player.getUUID())) {
                    EMFAnimationApi.resumeAllCustomAnimationsForEntity(EMFAnimationApi.emfEntityOf(player));
                }
            }
        } catch (LinkageError | RuntimeException e) {
            disabled = true;
            LOGGER.warn("Entity Model Features animation API is incompatible, custom player models may override climb animations", e);
        }
    }
}
