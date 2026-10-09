package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar de eigenaar van een zone en zijn vrienden: "hier zit C4 op je zone" (rode
 * markering aan) of "die C4 is weg" (uit). Zie c4/C4Alarm en client/c4/C4Alerts.
 *
 * @param pos waar de C4 zit
 * @param on  true = markering aan, false = weg
 */
public record C4AlertPayload(GlobalPos pos, boolean on) implements CustomPacketPayload {
    public static final Type<C4AlertPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "c4_alert"));

    public static final StreamCodec<ByteBuf, C4AlertPayload> STREAM_CODEC = StreamCodec.composite(
            GlobalPos.STREAM_CODEC, C4AlertPayload::pos,
            ByteBufCodecs.BOOL, C4AlertPayload::on,
            C4AlertPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
