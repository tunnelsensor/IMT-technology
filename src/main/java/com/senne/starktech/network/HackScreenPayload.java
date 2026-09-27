package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server naar speler: open de terminal van de laptop op {@code pos} ({@code open} = true), of sluit
 * hem ({@code open} = false: je liep weg, ging dood, of de laptop is weg).
 *
 * Bij openen gaan het getal van de puzzel ({@code seed}) en alles wat je al typte
 * ({@code commands}) mee: je spel speelt dat na, en dan zit je weer waar je was.
 */
public record HackScreenPayload(BlockPos pos, boolean open, long seed, List<String> commands)
        implements CustomPacketPayload {
    public static final Type<HackScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "hack_screen"));

    public static final StreamCodec<ByteBuf, HackScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HackScreenPayload::pos,
            ByteBufCodecs.BOOL, HackScreenPayload::open,
            ByteBufCodecs.VAR_LONG, HackScreenPayload::seed,
            ByteBufCodecs.stringUtf8(HackCommandPayload.MAX_LENGTH).apply(ByteBufCodecs.list(512)),
            HackScreenPayload::commands,
            HackScreenPayload::new);

    /** Het scherm dichtdoen. */
    public static HackScreenPayload close(BlockPos pos) {
        return new HackScreenPayload(pos, false, 0, List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
