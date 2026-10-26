package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar de eigenaar van een zone en zijn vrienden: "hier zit een Remote Frag op je zone" (rode
 * markering aan) of "die is weg" (uit). Zie breach/BlastAlarm en client/zone/ChargeAlerts.
 *
 * @param pos waar de lading zit
 * @param on  true = markering aan, false = weg
 */
public record ChargeAlertPayload(GlobalPos pos, boolean on) implements CustomPacketPayload {
    public static final Type<ChargeAlertPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "charge_alert"));

    public static final StreamCodec<ByteBuf, ChargeAlertPayload> STREAM_CODEC = StreamCodec.composite(
            GlobalPos.STREAM_CODEC, ChargeAlertPayload::pos,
            ByteBufCodecs.BOOL, ChargeAlertPayload::on,
            ChargeAlertPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
