package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van jouw spel naar de server: je klikte op een van de drie eigen vakjes naast je harnas (hoofdstuk 52, stap 2,
 * zonder Curios): 0 = de holster, 1 = de granaatpouch, 2 = de magazijnpouch (zie Gear.click).
 */
public record GearClickPayload(int slot) implements CustomPacketPayload {
    public static final Type<GearClickPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "gear_click"));

    public static final StreamCodec<ByteBuf, GearClickPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(GearClickPayload::new, GearClickPayload::slot);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
