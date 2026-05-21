package dev.matejhozlar.climbableropes;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.matejhozlar.climbableropes.network.ClimbAnimPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = ClimbableRopes.MODID, value = Dist.CLIENT)
public final class RemoteClimbAnimations {
    private static final int LAYER_PRIORITY = 40;
    private static final int FADE_TICKS = 4;
    // A riding client refreshes every 10 ticks; drop a remote animation that has gone silent for longer.
    private static final long STALE_TICKS = 30L;

    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

    private RemoteClimbAnimations() {}

    public static void accept(ClimbAnimPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        UUID id = packet.player();
        LocalPlayer local = mc.player;
        if (local != null && local.getUUID().equals(id)) return;
        if (!(mc.level.getPlayerByUUID(id) instanceof AbstractClientPlayer player)) return;

        if (!packet.active() || !ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get()) {
            Entry existing = ENTRIES.get(id);
            if (existing != null) existing.fadeOut();
            return;
        }

        Entry entry = ENTRIES.computeIfAbsent(id, key -> new Entry());
        entry.ensureLayer(player);
        entry.speedModifier.speed =
                ClimbableRopesConfig.ANIMATION_SPEED_MULTIPLIER.get().floatValue();
        entry.tangent = new Vec3(packet.tangentX(), packet.tangentY(), packet.tangentZ());
        entry.lastUpdate = mc.level.getGameTime();

        if (!Objects.equals(packet.animation(), entry.currentAnimId)) {
            entry.layer.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE),
                    ClimbAnimationController.load(packet.animation()));
            entry.currentAnimId = packet.animation();
        }
    }

    public static boolean isCustomPoseActive(UUID player) {
        Entry entry = ENTRIES.get(player);
        return entry != null && entry.currentAnimId != null;
    }

    public static Vec3 tangentFor(UUID player) {
        Entry entry = ENTRIES.get(player);
        return entry != null && entry.currentAnimId != null ? entry.tangent : null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTRIES.clear();
            return;
        }
        long now = mc.level.getGameTime();
        for (Iterator<Map.Entry<UUID, Entry>> it = ENTRIES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Entry> e = it.next();
            Entry entry = e.getValue();
            Player current = mc.level.getPlayerByUUID(e.getKey());
            if (current != entry.player) {
                entry.detach();
                it.remove();
            } else if (entry.currentAnimId != null && now - entry.lastUpdate > STALE_TICKS) {
                entry.fadeOut();
            }
        }
    }

    private static final class Entry {
        private ModifierLayer<IAnimation> layer;
        private SpeedModifier speedModifier;
        private AbstractClientPlayer player;
        private ResourceLocation currentAnimId;
        private Vec3 tangent;
        private long lastUpdate;

        void ensureLayer(AbstractClientPlayer target) {
            if (layer != null && player == target) return;
            if (layer != null) detach();

            layer = new ModifierLayer<>();
            speedModifier = new SpeedModifier();
            layer.addModifierLast(speedModifier);
            PlayerAnimationAccess.getPlayerAnimLayer(target).addAnimLayer(LAYER_PRIORITY, layer);
            player = target;
        }

        void fadeOut() {
            if (layer != null) {
                layer.replaceAnimationWithFade(
                        AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE), null);
            }
            currentAnimId = null;
            tangent = null;
        }

        void detach() {
            if (layer != null && player != null) {
                try {
                    PlayerAnimationAccess.getPlayerAnimLayer(player).removeLayer(layer);
                } catch (IllegalArgumentException ignored) {
                }
            }
            layer = null;
            speedModifier = null;
            player = null;
            currentAnimId = null;
            tangent = null;
        }
    }
}
