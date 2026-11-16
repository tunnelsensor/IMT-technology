package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server (hoofdstuk 126): een knop in het scherm van een Alarm Speaker (zie AlarmSpeakers.handleAction).
 *
 * @param pos    welke speaker
 * @param action {@link #TEST}, {@link #STOP}, {@link #DEFAULT_SOUND} of {@link #APPLY_ALL}
 */
public record AlarmActionPayload(BlockPos pos, int action) implements CustomPacketPayload {
    /** Test: hij gaat een paar seconden echt af (zodat je hoort hoe ver hij reikt). */
    public static final int TEST = 0;
    /** Stop: meteen stil. */
    public static final int STOP = 1;
    /** Weer de ingebouwde sirene. */
    public static final int DEFAULT_SOUND = 2;
    /** Hetzelfde geluid op alle speakers van dit netwerk die jij mag instellen. */
    public static final int APPLY_ALL = 3;

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
