package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server (hoofdstuk 126): een knop in het scherm van een Speaker (zie AlarmSpeakers.handleAction).
 * Sinds hoofdstuk 136 is er alleen nog Test: wat hij speelt, zegt een diagram (playsound, playtext).
 *
 * @param pos    welke speaker
 * @param action {@link #TEST}
 */
public record AlarmActionPayload(BlockPos pos, int action) implements CustomPacketPayload {
    /** Test: hij speelt een paar seconden de ingebouwde sirene (zodat je hoort hoe ver hij reikt). */
    public static final int TEST = 0;

    public static final Type<AlarmActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "alarm_action"));

    public static final StreamCodec<ByteBuf, AlarmActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AlarmActionPayload::pos,
            ByteBufCodecs.VAR_INT, AlarmActionPayload::action,
            AlarmActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
