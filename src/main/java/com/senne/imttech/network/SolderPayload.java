package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: solderen of lossolderen op het bord van een robot (hoofdstuk 22). Je spel
 * laat het spelletje zien (de hittebalk) en zegt hoe lang je vasthield; de server beslist hoe het afloopt
 * (zie bot/Soldering) en kijkt na of je het mag (zie robot/Robots).
 *
 * @param entityId  welke robot
 * @param desolder  true = lossolderen, false = solderen
 * @param pin       welke pin (1 tot 8)
 * @param module    welke module (zijn naam, zoals "laser"; leeg bij lossolderen)
 * @param holdTicks hoe lang je de muis vasthield (ticks)
 */
public record SolderPayload(int entityId, boolean desolder, int pin, String module, int holdTicks) implements CustomPacketPayload {
    public static final Type<SolderPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "solder"));

    public static final StreamCodec<ByteBuf, SolderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SolderPayload::entityId,
            ByteBufCodecs.BOOL, SolderPayload::desolder,
            ByteBufCodecs.VAR_INT, SolderPayload::pin,
            ByteBufCodecs.stringUtf8(32), SolderPayload::module,
            ByteBufCodecs.VAR_INT, SolderPayload::holdTicks,
            SolderPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
