package com.senne.imttech.backpack;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van de server naar jouw spel (hoofdstuk 72): je bent een rugzak aan het openritsen, en dat duurt {@code ticks}
 * ticks (0 = gestopt of klaar). Jouw spel tekent dan een balkje onder je vizier (client/backpack/BackpackClient).
 */
public record BackpackZipPayload(int ticks) implements CustomPacketPayload {
    public static final Type<BackpackZipPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "backpack_zip"));

    public static final StreamCodec<ByteBuf, BackpackZipPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(BackpackZipPayload::new, BackpackZipPayload::ticks);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
