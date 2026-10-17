package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "zet dit anders aan mijn schildkoepel".
 *
 * @param pos   welke koepelgenerator
 * @param op    wat er anders moet
 * @param value de nieuwe waarde (0/1 bij aan/uit, anders een nummer)
 */
public record DomeActionPayload(BlockPos pos, Op op, int value) implements CustomPacketPayload {
    public static final Type<DomeActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "dome_action"));

    public enum Op {
        /** Aan of uit. */
        ENABLE,
        /** De kleur (nummer uit ShieldColor). */
        COLOUR,
        /** Het bereik (nummer uit ImtConfig.Dome.RADII). */
        RADIUS,
        /** De sterkte (1 tot 5). */
        STRENGTH
    }

    public static final StreamCodec<ByteBuf, DomeActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DomeActionPayload::pos,
            ByteBufCodecs.VAR_INT.map(i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal),
            DomeActionPayload::op,
            ByteBufCodecs.VAR_INT, DomeActionPayload::value,
            DomeActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
