package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "zet dit anders op deze Arc Lamp" (10 oktober).
 *
 * @param pos   welke lamp
 * @param op    wat er anders moet
 * @param value de nieuwe waarde (een lichtniveau)
 */
public record LampActionPayload(BlockPos pos, Op op, int value) implements CustomPacketPayload {
    public static final Type<LampActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "lamp_action"));

    public enum Op {
        /** Hoe fel hij is: zijn lichtniveau. (De gloed, "GLOW", is weg sinds 13 oktober.) */
        BRIGHTNESS
    }

    public static final StreamCodec<ByteBuf, LampActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LampActionPayload::pos,
            ByteBufCodecs.VAR_INT.map(i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal),
            LampActionPayload::op,
            ByteBufCodecs.VAR_INT, LampActionPayload::value,
            LampActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
