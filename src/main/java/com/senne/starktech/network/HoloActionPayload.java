package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "zet dit anders op mijn holotafel".
 *
 * @param pos   welke tafel
 * @param op    wat er anders moet
 * @param value de nieuwe waarde (een nummer uit een rijtje, of 0/1 bij aan/uit)
 */
public record HoloActionPayload(BlockPos pos, Op op, int value) implements CustomPacketPayload {
    public static final Type<HoloActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "holo_action"));

    public enum Op {
        /** Wat hij laat zien (kaart, bouwtekening, arsenaal). */
        MODE,
        /** De kleur van het hologram. */
        COLOUR,
        /** Hoe ver de kaart reikt. */
        RANGE,
        /** Hoe groot het hologram is. */
        SCALE,
        /** Draait hij rond? */
        SPIN,
        /** Het voorwerp van tafel halen. */
        CLEAR_BLUEPRINT
    }

    public static final StreamCodec<ByteBuf, HoloActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HoloActionPayload::pos,
            ByteBufCodecs.VAR_INT.map(i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal),
            HoloActionPayload::op,
            ByteBufCodecs.VAR_INT, HoloActionPayload::value,
            HoloActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
