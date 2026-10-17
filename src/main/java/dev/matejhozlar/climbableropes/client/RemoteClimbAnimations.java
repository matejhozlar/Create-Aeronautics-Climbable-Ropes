package dev.matejhozlar.climbableropes.client;

import dev.matejhozlar.climbableropes.ClimbableRopes;
import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.ClimbAnimationController.ClimbState;
import dev.matejhozlar.climbableropes.network.ClimbAnimSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = ClimbableRopes.MODID, value = Dist.CLIENT)
public final class RemoteClimbAnimations {
    // A riding client refreshes every 10 ticks; drop a remote pose that has gone silent for longer.
    private static final long STALE_TICKS = 30L;

    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

    private RemoteClimbAnimations() {}

    public static void accept(ClimbAnimSyncPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        UUID id = packet.player();
        LocalPlayer local = mc.player;
        if (local != null && local.getUUID().equals(id)) return;
        if (mc.level.getPlayerByUUID(id) == null) return;

        ClimbState state = packet.active() ? ClimbState.byId(packet.animation()) : null;
        if (state == null || !ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get()) {
            ENTRIES.remove(id);
            return;
        }

        Entry entry = ENTRIES.computeIfAbsent(id, key -> new Entry());
        entry.state = state;
        entry.tangent = new Vec3(packet.tangentX(), packet.tangentY(), packet.tangentZ());
        entry.lastUpdate = mc.level.getGameTime();
    }

    public static boolean isCustomPoseActive(UUID player) {
        return ENTRIES.containsKey(player);
    }

    public static ClimbState stateFor(UUID player) {
        Entry entry = ENTRIES.get(player);
        return entry != null ? entry.state : null;
    }

    public static Vec3 tangentFor(UUID player) {
        Entry entry = ENTRIES.get(player);
        return entry != null ? entry.tangent : null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTRIES.clear();
            return;
        }
        long now = mc.level.getGameTime();
        ENTRIES.entrySet().removeIf(e -> mc.level.getPlayerByUUID(e.getKey()) == null
                || now - e.getValue().lastUpdate > STALE_TICKS);
    }

    private static final class Entry {
        private ClimbState state;
        private Vec3 tangent;
        private long lastUpdate;
    }
}
