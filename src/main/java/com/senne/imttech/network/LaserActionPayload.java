package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "zet dit anders op deze laserzender" (13 oktober). Het geldt voor de
 * hele laserpoort (alle zenders die eraan vastzitten), zie shield/LaserEmitters.
 *
 * @param pos   welke zender
 * @param op    wat er anders moet
 * @param value de nieuwe waarde: een kleur (de volgorde van ShieldColor), of 0/1 voor uit/aan
 */
public record LaserActionPayload(BlockPos pos, Op op, int value) implements CustomPacketPayload {
    public static final Type<LaserActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "laser_action"));

    public enum Op {
        /** De kleur van het veld. */
        COLOR,
        /** Omkeren: redstone aan = laser uit. */
        INVERT,
        /** Het veld een kwartslag draaien: liggend in plaats van staand. */
        TURN
    }

    public static final StreamCodec<ByteBuf, LaserActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LaserActionPayload::pos,
            ByteBufCodecs.VAR_INT.map(i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal),
            LaserActionPayload::op,
            ByteBufCodecs.VAR_INT, LaserActionPayload::value,
            LaserActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
