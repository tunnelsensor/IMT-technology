package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Over herladen (hoofdstuk 35), twee kanten op. Van jouw spel naar de server: {@code start} = "ik herlaad" (R), en
 * {@code value} = uit welk vakje van je magazijnpouch de cel komt (hoofdstuk 55; -1 = de volste cel uit je inventaris).
 * Van de server naar jouw spel: {@code start} = true met {@code value} = zo lang duurt het (ticks; de receiver en je
 * skills tellen mee, zo loopt de animatie even lang), of {@code start} = false: "dat gaat niet" (geen geladen cel, of
 * je wisselde van item): dan stopt de animatie.
 */
public record GunReloadPayload(boolean start, int value) implements CustomPacketPayload {
    public static final Type<GunReloadPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "gun_reload"));

    public static final StreamCodec<ByteBuf, GunReloadPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GunReloadPayload::start,
            ByteBufCodecs.VAR_INT, GunReloadPayload::value,
            GunReloadPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
