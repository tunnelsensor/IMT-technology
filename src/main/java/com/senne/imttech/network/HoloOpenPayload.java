package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "open het instelscherm van deze holotafel".
 *
 * De server beslist of je het scherm mag openen (bril op, tafel binnen handbereik), en jouw
 * spel opent het dan pas. Zo staat er geen schermcode in de gedeelde code, wat op een echte
 * server problemen zou geven.
 */
public record HoloOpenPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<HoloOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "holo_open"));

    public static final StreamCodec<ByteBuf, HoloOpenPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(HoloOpenPayload::new, HoloOpenPayload::pos);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
