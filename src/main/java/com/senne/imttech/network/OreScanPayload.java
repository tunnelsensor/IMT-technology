package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "de ertsen-scan is goedgekeurd (en betaald), scan maar
 * in deze straal". De speler zijn eigen spel zoekt dan de ertsen op en toont ze in de HUD.
 */
public record OreScanPayload(int radius) implements CustomPacketPayload {
    public static final Type<OreScanPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "ore_scan"));

    public static final StreamCodec<ByteBuf, OreScanPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(OreScanPayload::new, OreScanPayload::radius);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
