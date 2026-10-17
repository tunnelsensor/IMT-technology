package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server, tijdens het richten van een illusie: "zet hem hier neer"
 * (linkermuisknop) of "laat maar" (rechtermuisknop).
 *
 * @param place true = neerzetten, false = annuleren
 */
public record IllusionPlacePayload(boolean place) implements CustomPacketPayload {
    public static final Type<IllusionPlacePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "illusion_place"));

    public static final StreamCodec<ByteBuf, IllusionPlacePayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(IllusionPlacePayload::new, IllusionPlacePayload::place);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
