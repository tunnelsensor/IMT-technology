package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "open het scherm om dit relais een naam en groep te geven".
 *
 * @param index welk relais uit je eigen lijst (zie ImtRelays)
 */
public record RelayEditPayload(int index) implements CustomPacketPayload {
    public static final Type<RelayEditPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "relay_edit"));

    public static final StreamCodec<ByteBuf, RelayEditPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(RelayEditPayload::new, RelayEditPayload::index);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
