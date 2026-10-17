package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "doe iets met deze stroomkast".
 *
 * @param pos  waar de kast staat
 * @param op   TOGGLE = de schakelaar van één kant omzetten, LINK = aanmelden bij I.M.T
 * @param side bij TOGGLE: welke kant (het nummer van een Direction: 0 = onder, 1 = boven, ...)
 */
public record CabinetActionPayload(BlockPos pos, Op op, int side) implements CustomPacketPayload {
    public static final Type<CabinetActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "cabinet_action"));

    public enum Op {
        TOGGLE, LINK
    }

    public static final StreamCodec<ByteBuf, CabinetActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CabinetActionPayload::pos,
            ByteBufCodecs.VAR_INT.map(i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal),
            CabinetActionPayload::op,
            ByteBufCodecs.VAR_INT, CabinetActionPayload::side,
            CabinetActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
