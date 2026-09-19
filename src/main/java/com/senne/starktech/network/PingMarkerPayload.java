package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Bericht van server naar spelers met een E.D.I.T.H.-bril: "iemand heeft hier een ping geplaatst".
 */
public record PingMarkerPayload(String sender, Vec3 pos) implements CustomPacketPayload {
    public static final Type<PingMarkerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StarkTech.MODID, "ping_marker"));

    public static final StreamCodec<ByteBuf, PingMarkerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), PingMarkerPayload::sender,
            Vec3.STREAM_CODEC, PingMarkerPayload::pos,
            PingMarkerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
