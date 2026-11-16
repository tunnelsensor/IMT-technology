package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server (hoofdstuk 126): "stuur me het alarmgeluid met deze naam". Je spel vraagt het zodra je in
 * de buurt komt van een speaker met een eigen geluid dat je nog niet hebt, zodat het klaar is als het alarm afgaat. De
 * server antwoordt met een of meer AlarmSoundDataPayloads (zie AlarmUploads.handleRequest).
 *
 * @param id de vingerafdruk van het geluid (40 tekens 0-9 en a-f, zie AlarmSounds)
 */
public record AlarmSoundRequestPayload(String id) implements CustomPacketPayload {
    public static final Type<AlarmSoundRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "alarm_sound_request"));

    public static final StreamCodec<ByteBuf, AlarmSoundRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), AlarmSoundRequestPayload::id,
            AlarmSoundRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
