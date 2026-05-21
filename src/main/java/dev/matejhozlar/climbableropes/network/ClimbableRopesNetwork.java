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
        MANAGER.registerServerbound(ClimbAnimPacket.TYPE, ClimbAnimPacket.CODEC,
                ClimbableRopesNetwork::handleServerbound);
        MANAGER.registerClientbound(ClimbAnimPacket.TYPE, ClimbAnimPacket.CODEC,
                ClimbableRopesNetwork::handleClientbound);
    }

    public static void sendToServer(ClimbAnimPacket packet) {
        VeilPacketManager.server().sendPacket(packet);
    }

    private static void handleServerbound(ClimbAnimPacket packet, ServerPacketContext ctx) {
        ServerPlayer sender = ctx.player();
        ClimbAnimPacket relayed = new ClimbAnimPacket(
                sender.getUUID(), packet.active(), packet.animation(),
                packet.tangentX(), packet.tangentY(), packet.tangentZ());
        VeilPacketManager.tracking(sender).sendPacket(relayed);
    }

    private static void handleClientbound(ClimbAnimPacket packet, ClientPacketContext ctx) {
        RemoteClimbAnimations.accept(packet);
    }
}
