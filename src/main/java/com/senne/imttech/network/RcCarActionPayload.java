package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import com.senne.imttech.logic.program.LogicProgram;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: een knop uit het scherm van een RC Car (hoofdstuk 108), ook via de RC Controller. De
 * server kijkt na of je deze auto mag instellen en of je dichtbij genoeg bent (of een gekoppelde controller hebt), zie
 * car/RcCars.
 *
 * @param entityId welke auto
 * @param op       wat er moet gebeuren
 * @param text     de naam van de functie (RUN), de stap (STEP: "forward", "back", "left", "right", "mine",
 *                 "mine_up", "mine_down"), de nieuwe naam (RENAME) of de code (SAVE); anders leeg
 */
public record RcCarActionPayload(int entityId, Op op, String text) implements CustomPacketPayload {
    public static final Type<RcCarActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "rc_car_action"));

    public enum Op {
        /** Start: het hoofdprogramma (when started). */
        START,
        /** Een functie (een def zonder parameters): haar naam staat in text. */
        RUN,
        /** Stop: wat hij deed, houdt op. */
        STOP,
        /** Eén stap met de hand (de pijltjes): welke staat in text. */
        STEP,
        /** Een andere naam. */
        RENAME,
        /** Oppakken: hij wordt weer een RC Car-item in je rugzak. */
        PICK_UP,
        /** De energiecel eruit (terug in je rugzak). */
        CELL_OUT,
        /** Geef me de code: de server stuurt haar terug en dan opent de editor. */
        CODE,
        /** De code uit de editor bewaren (de server kijkt haar nog eens na). */
        SAVE
    }

    public static final StreamCodec<ByteBuf, RcCarActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RcCarActionPayload::entityId,
            ByteBufCodecs.VAR_INT.map(i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal),
            RcCarActionPayload::op,
            ByteBufCodecs.stringUtf8(LogicProgram.MAX_SOURCE), RcCarActionPayload::text,
            RcCarActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
