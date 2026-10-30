package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Speler naar server (hoofdstuk 40): "dit is de code van de Computer achter deze monitor". De server kijkt alles opnieuw
 * na (werkt hij, mag jij erop, klopt de code) en opent de desktop, of zegt waarom niet (ComputerCodePromptPayload).
 */
public record ComputerCodePayload(BlockPos monitor, String code) implements CustomPacketPayload {
    public static final Type<ComputerCodePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "computer_code"));

    public static final StreamCodec<ByteBuf, ComputerCodePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ComputerCodePayload::monitor,
            ByteBufCodecs.stringUtf8(32), ComputerCodePayload::code,
            ComputerCodePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
