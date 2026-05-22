package dev.matejhozlar.climbableropes.network;

import dev.matejhozlar.climbableropes.ClimbableRopes;
import dev.matejhozlar.climbableropes.RemoteClimbAnimations;
import foundry.veil.api.network.VeilPacketManager;
import foundry.veil.api.network.handler.ClientPacketContext;
import foundry.veil.api.network.handler.ServerPacketContext;
import net.minecraft.server.level.ServerPlayer;

public final class ClimbableRopesNetwork {
    private static final VeilPacketManager MANAGER =
            VeilPacketManager.create(ClimbableRopes.MODID, "1");

    private ClimbableRopesNetwork() {}

    public static void init() {
        MANAGER.registerServerbound(ClimbAnimUpdatePacket.TYPE, ClimbAnimUpdatePacket.CODEC,
                ClimbableRopesNetwork::handleUpdate);
        MANAGER.registerClientbound(ClimbAnimSyncPacket.TYPE, ClimbAnimSyncPacket.CODEC,
                ClimbableRopesNetwork::handleSync);
    }

    public static void sendToServer(ClimbAnimUpdatePacket packet) {
        VeilPacketManager.server().sendPacket(packet);
    }

    private static void handleUpdate(ClimbAnimUpdatePacket packet, ServerPacketContext ctx) {
        // A client could forge the id or tangent; relay only this mod's animations with a finite
        // tangent (an Infinity tangent corrupts the pose rotation on other clients).
        if (!ClimbableRopes.MODID.equals(packet.animation().getNamespace())) return;
        if (!Double.isFinite(packet.tangentX()) || !Double.isFinite(packet.tangentY())
                || !Double.isFinite(packet.tangentZ())) return;
        ServerPlayer sender = ctx.player();
        ClimbAnimSyncPacket relayed = new ClimbAnimSyncPacket(
                sender.getUUID(), packet.active(), packet.animation(),
                packet.tangentX(), packet.tangentY(), packet.tangentZ());
        VeilPacketManager.tracking(sender).sendPacket(relayed);
    }

    private static void handleSync(ClimbAnimSyncPacket packet, ClientPacketContext ctx) {
        RemoteClimbAnimations.accept(packet);
    }
}
