package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van de server naar één speler: "een flashbang verblindt je" (hoofdstuk 35). {@code strength} is hoe erg (0 tot
 * 1), {@code ticks} hoe lang het duurt. Je spel tekent het witte scherm en speelt de piep (zie client/weapons/FlashOverlay).
 */
public record FlashPayload(float strength, int ticks) implements CustomPacketPayload {
    public static final Type<FlashPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "flash"));

    public static final StreamCodec<ByteBuf, FlashPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, FlashPayload::strength,
            ByteBufCodecs.VAR_INT, FlashPayload::ticks,
            FlashPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
