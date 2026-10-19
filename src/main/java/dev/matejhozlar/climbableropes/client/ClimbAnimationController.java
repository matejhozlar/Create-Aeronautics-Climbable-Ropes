package dev.matejhozlar.climbableropes.client;

import dev.matejhozlar.climbableropes.ClimbableRopes;
import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.network.ClimbAnimUpdatePacket;
import dev.matejhozlar.climbableropes.network.ClimbableRopesNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClimbAnimationController {
    public enum ClimbMode { HANGING_STRAND, PLUNGER_ROPE, PLUNGER_ZIPLINE }

    public enum ClimbState {
        IDLE("idle"), CLIMB_UP("climb_up"), DESCEND("descend"), SLIDE("slide");

        // The sync packet carries these ids, which keeps it wire-compatible with clients on older versions.
        private final ResourceLocation id;

        ClimbState(String path) {
            this.id = ResourceLocation.fromNamespaceAndPath(ClimbableRopes.MODID, path);
        }

        public ResourceLocation id() {
            return id;
        }

        public static ClimbState byId(ResourceLocation id) {
            for (ClimbState state : values()) {
                if (state.id.equals(id)) return state;
            }
            return null;
        }
    }

    // Resend the current state periodically so newly-tracking observers pick it up and the rope tangent stays fresh.
    private static final int SYNC_REFRESH_INTERVAL = 10;

    private static ClimbMode currentMode;
    private static ClimbState currentState;
    private static Vec3 ropeTangent;
    private static Vec3 ropePoint;
    private static int syncRefreshCounter;
    // True between embark and the first climb tick, before the rope angle is known. Suppresses
    // Create's hanging pose during that gap so it does not flash before our pose is applied.
    private static boolean embarkPending;

    private ClimbAnimationController() {}

    public static void onEmbark(ClimbMode mode) {
        if (!ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get()) {
            clear();
            return;
        }
        if (Minecraft.getInstance().player == null) return;
        currentMode = mode;
        currentState = null;
        ropeTangent = null;
        ropePoint = null;
        embarkPending = mode != ClimbMode.PLUNGER_ZIPLINE;
    }

    public static void onTick(Vec3 tangent, Vec3 point, ClimbState state) {
        if (!ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get()) {
            if (currentState != null) sendSync(null, null);
            clear();
            return;
        }
        if (currentMode == null || Minecraft.getInstance().player == null) return;

        embarkPending = false;
        ropeTangent = tangent;
        ropePoint = point;

        ClimbState desired = desiredState(state, tangent);
        if (desired != currentState) {
            currentState = desired;
            sendSync(desired, tangent);
            syncRefreshCounter = 0;
        } else if (desired != null && ++syncRefreshCounter >= SYNC_REFRESH_INTERVAL) {
            sendSync(desired, tangent);
            syncRefreshCounter = 0;
        }
    }

    public static void onDisembark() {
        if (currentState != null) sendSync(null, null);
        clear();
    }

    public static boolean isCustomPoseActive() {
        return currentState != null || embarkPending;
    }

    public static boolean isLocalPlayerAttached() {
        return currentMode != null;
    }

    public static boolean isCustomPoseActive(Player player) {
        LocalPlayer local = Minecraft.getInstance().player;
        if (local != null && player.getUUID().equals(local.getUUID())) return isCustomPoseActive();
        return RemoteClimbAnimations.isCustomPoseActive(player.getUUID());
    }

    public static ClimbState currentState() {
        return currentState;
    }

    public static Vec3 currentRopeTangent() {
        return currentState != null ? ropeTangent : null;
    }

    public static Vec3 currentRopePoint() {
        return currentState != null ? ropePoint : null;
    }

    private static ClimbState desiredState(ClimbState state, Vec3 tangent) {
        if (currentMode == ClimbMode.PLUNGER_ZIPLINE || state == null) return null;
        return tangent != null && tangent.lengthSqr() > 1.0e-12 ? state : null;
    }

    public static void reset() {
        clear();
    }

    private static void clear() {
        currentMode = null;
        currentState = null;
        ropeTangent = null;
        ropePoint = null;
        syncRefreshCounter = 0;
        embarkPending = false;
    }

    private static void sendSync(ClimbState state, Vec3 tangent) {
        if (state == null) {
            ClimbableRopesNetwork.sendToServer(
                    new ClimbAnimUpdatePacket(false, ClimbState.IDLE.id(), 0.0, 0.0, 0.0));
        } else {
            ClimbableRopesNetwork.sendToServer(new ClimbAnimUpdatePacket(
                    true, state.id(), tangent.x, tangent.y, tangent.z));
        }
    }
}
