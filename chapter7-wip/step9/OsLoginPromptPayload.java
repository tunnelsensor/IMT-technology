package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server naar speler: het inlogvenster (SSH) openen, met het IP en de naam van het systeem al
 * ingevuld als je op een kern klikte ({@code open} = true); of, als het al open staat, laten zien
 * waarom inloggen niet lukte ({@code open} = false, met {@code message}).
 */
public record OsLoginPromptPayload(String ip, String host, String message, boolean open) implements CustomPacketPayload {
    public static final Type<OsLoginPromptPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "os_login_prompt"));

    public static final StreamCodec<ByteBuf, OsLoginPromptPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), OsLoginPromptPayload::ip,
            ByteBufCodecs.stringUtf8(64), OsLoginPromptPayload::host,
            ByteBufCodecs.stringUtf8(256), OsLoginPromptPayload::message,
            ByteBufCodecs.BOOL, OsLoginPromptPayload::open,
            OsLoginPromptPayload::new);

    /** Een antwoord voor het inlogvenster dat al open staat. */
    public static OsLoginPromptPayload reply(String message) {
        return new OsLoginPromptPayload("", "", message, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
