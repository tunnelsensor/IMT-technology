package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Over herladen (hoofdstuk 35), twee kanten op. Van jouw spel naar de server: {@code start} = "ik herlaad" (R). Van de
 * server naar jouw spel: {@code start} = false betekent "dat gaat niet" (geen geladen cel, of je wisselde van item):
 * dan stopt de animatie.
 */
public record GunReloadPayload(boolean start) implements CustomPacketPayload {
    public static final Type<GunReloadPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "gun_reload"));

    public static final StreamCodec<ByteBuf, GunReloadPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(GunReloadPayload::new, GunReloadPayload::start);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
