package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server naar speler (hoofdstuk 40): de Computer achter deze monitor heeft een code. Open het codescherm ({@code open} =
 * true), of zeg in het scherm dat al open staat waarom het niet lukte ({@code open} = false, met {@code message}). Lukt
 * het, dan stuurt de server gewoon de desktop (OsOpenPayload).
 */
public record ComputerCodePromptPayload(BlockPos monitor, String name, String message, boolean open)
        implements CustomPacketPayload {
    public static final Type<ComputerCodePromptPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "computer_code_prompt"));

    public static final StreamCodec<ByteBuf, ComputerCodePromptPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ComputerCodePromptPayload::monitor,
            ByteBufCodecs.stringUtf8(64), ComputerCodePromptPayload::name,
            ByteBufCodecs.stringUtf8(256), ComputerCodePromptPayload::message,
            ByteBufCodecs.BOOL, ComputerCodePromptPayload::open,
            ComputerCodePromptPayload::new);

    /** Een antwoord voor het codescherm dat al open staat. */
    public static ComputerCodePromptPayload reply(BlockPos monitor, String message) {
        return new ComputerCodePromptPayload(monitor, "", message, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
