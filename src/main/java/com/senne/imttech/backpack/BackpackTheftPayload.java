package com.senne.imttech.backpack;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van de server naar de dief (hoofdstuk 72, stap 3): hoe vol de meter "NOTICED" is (0 tot 1), of -1 = betrapt.
 * Het scherm van de rugzak tekent hem (client/backpack/BackpackScreen).
 */
public record BackpackTheftPayload(float meter) implements CustomPacketPayload {
    public static final Type<BackpackTheftPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "backpack_theft"));

    public static final StreamCodec<ByteBuf, BackpackTheftPayload> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(BackpackTheftPayload::new, BackpackTheftPayload::meter);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
