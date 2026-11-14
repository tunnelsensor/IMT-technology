package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import com.senne.imttech.logic.program.LogicProgram;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "open het scherm van deze RC Car" (hoofdstuk 108), of zijn code-editor. De server
 * besliste al of je het mag (jij zette hem neer, of je bent vertrouwd in die basis; zie car/RcCars). Wat hij nu doet,
 * zijn functies en zijn batterij leest het scherm gewoon van de auto (die kent jouw spel al).
 *
 * @param entityId welke auto
 * @param owner    de naam van zijn eigenaar (leeg als niemand)
 * @param remote   true = via de RC Controller (dan kun je hem niet oppakken: daarvoor moet je erbij staan)
 * @param editor   true = open de code-editor (met {@code source}), false = zijn gewone scherm
 * @param source   de code van zijn programma (alleen bij de editor; anders leeg)
 */
public record RcCarOpenPayload(int entityId, String owner, boolean remote, boolean editor, String source)
        implements CustomPacketPayload {
    public static final Type<RcCarOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "rc_car_open"));

    public static final StreamCodec<ByteBuf, RcCarOpenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RcCarOpenPayload::entityId,
            ByteBufCodecs.stringUtf8(64), RcCarOpenPayload::owner,
            ByteBufCodecs.BOOL, RcCarOpenPayload::remote,
            ByteBufCodecs.BOOL, RcCarOpenPayload::editor,
            ByteBufCodecs.stringUtf8(LogicProgram.MAX_SOURCE), RcCarOpenPayload::source,
            RcCarOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
