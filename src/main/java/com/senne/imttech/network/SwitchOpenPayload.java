package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 38): "open het deurtje van deze Network Switch". De server beslist of het mag
 * (dichtbij genoeg) en geeft de naam van de switch mee.
 */
public record SwitchOpenPayload(BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<SwitchOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "switch_open"));

    public static final StreamCodec<ByteBuf, SwitchOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SwitchOpenPayload::pos,
            ByteBufCodecs.STRING_UTF8, SwitchOpenPayload::name,
            SwitchOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
