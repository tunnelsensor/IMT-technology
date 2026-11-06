package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van jouw spel naar de server: een keuze uit het wiel (hoofdstuk 52, stap 2). {@code action} = Gear.ACTION_...
 * (het pistool trekken, een granaat van soort {@code arg} trekken, of wegsteken wat je vasthoudt).
 */
public record GearActionPayload(int action, int arg) implements CustomPacketPayload {
    public static final Type<GearActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "gear_action"));

    public static final StreamCodec<ByteBuf, GearActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GearActionPayload::action,
            ByteBufCodecs.VAR_INT, GearActionPayload::arg,
            GearActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
