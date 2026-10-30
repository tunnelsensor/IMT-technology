package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Speler naar server (hoofdstuk 40): "log me in op de kern met dit IP, met dit wachtwoord" (het inlogscherm van de
 * Hacking Laptop, zoals SSH). De server zoekt de kern op (CoreNetworkData), kijkt het wachtwoord na en opent de desktop
 * op afstand, of stuurt terug waarom het niet lukte (OsLoginPromptPayload).
 */
public record OsLoginPayload(String ip, String password) implements CustomPacketPayload {
    public static final Type<OsLoginPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "os_login"));

    public static final StreamCodec<ByteBuf, OsLoginPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), OsLoginPayload::ip,
            ByteBufCodecs.stringUtf8(32), OsLoginPayload::password,
            OsLoginPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
