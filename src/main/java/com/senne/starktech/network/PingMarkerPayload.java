package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Bericht van server naar spelers met een E.D.I.T.H.-bril: "iemand heeft hier een ping geplaatst".
 */
public record PingMarkerPayload(String sender, Vec3 pos) implements CustomPacketPayload {
    public static final Type<PingMarkerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "ping_marker"));

    /**
     * Geen echte ping maar de markering van het beginpunt van de schuivende laser (alleen voor
     * jezelf). Die krijgt geen "Ping from ..."-melding: E.D.I.T.H. zegt zelf al wat het is.
     */
    public static final String SWEEP_START = "Sweep start";

    public static final StreamCodec<ByteBuf, PingMarkerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), PingMarkerPayload::sender,
            ModCodecs.VEC3, PingMarkerPayload::pos,
            PingMarkerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
