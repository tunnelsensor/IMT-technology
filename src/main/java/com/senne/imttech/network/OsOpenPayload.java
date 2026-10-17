package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server naar speler: open de Security OS-desktop van de kern op {@code pos} ({@code open} = true), of
 * sluit hem ({@code open} = false). De server beslist of openen mag (je bent de eigenaar, of je kent
 * op afstand het wachtwoord). {@code remote} = true toont de balk "REMOTE SESSION" (via SSH op
 * afstand ingelogd).
 */
public record OsOpenPayload(BlockPos pos, boolean open, boolean remote) implements CustomPacketPayload {
    public static final Type<OsOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "os_open"));

    public static final StreamCodec<ByteBuf, OsOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OsOpenPayload::pos,
            ByteBufCodecs.BOOL, OsOpenPayload::open,
            ByteBufCodecs.BOOL, OsOpenPayload::remote,
            OsOpenPayload::new);

    /** Het scherm dichtdoen (bij de kern op {@code pos}). */
    public static OsOpenPayload close(BlockPos pos) {
        return new OsOpenPayload(pos, false, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
