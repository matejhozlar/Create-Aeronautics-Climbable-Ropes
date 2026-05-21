package dev.matejhozlar.climbableropes.network;

import dev.matejhozlar.climbableropes.ClimbableRopes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ClimbAnimPacket(UUID player, boolean active, ResourceLocation animation,
                              double tangentX, double tangentY, double tangentZ)
        implements CustomPacketPayload {

    public static final Type<ClimbAnimPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ClimbableRopes.MODID, "climb_anim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClimbAnimPacket> CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUUID(pkt.player);
                buf.writeBoolean(pkt.active);
                buf.writeResourceLocation(pkt.animation);
                buf.writeDouble(pkt.tangentX);
                buf.writeDouble(pkt.tangentY);
                buf.writeDouble(pkt.tangentZ);
            },
            buf -> new ClimbAnimPacket(
                    buf.readUUID(), buf.readBoolean(), buf.readResourceLocation(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
