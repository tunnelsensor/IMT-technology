package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "doe iets met deze drone uit mijn arsenaal".
 *
 * @param op    terugroepen, repareren of weer lanceren
 * @param drone welke drone (zijn vaste nummer uit het arsenaal)
 */
public record ArsenalActionPayload(Op op, UUID drone) implements CustomPacketPayload {
    public static final Type<ArsenalActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "arsenal_action"));

    public enum Op {
        /** Uit zijn baan naar de grond halen, zodat je eraan kunt sleutelen. */
        RECALL,
        /** Weer de ruimte in sturen. */
        LAUNCH
    }

    public static final StreamCodec<ByteBuf, ArsenalActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal),
            ArsenalActionPayload::op,
            UUIDUtil.STREAM_CODEC, ArsenalActionPayload::drone,
            ArsenalActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
