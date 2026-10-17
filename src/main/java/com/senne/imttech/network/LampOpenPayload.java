package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "open het instelscherm van deze Arc Lamp" (10 oktober).
 *
 * De server beslist of je het mag openen (je staat dichtbij genoeg, zie light/ArcLamps), en jouw spel
 * opent het dan pas. Zo staat er geen schermcode in de gedeelde code (net als bij de holotafel).
 */
public record LampOpenPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<LampOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "lamp_open"));

    public static final StreamCodec<ByteBuf, LampOpenPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(LampOpenPayload::new, LampOpenPayload::pos);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
