package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "kijk nu door deze drone" (entity-id), of NONE: "terug naar je lichaam".
 *
 * @param droneId het nummer van de drone, of NONE
 * @param control true = je bestuurt de drone zelf (verkenner); false = je kijkt alleen mee (spion)
 */
public record DroneCameraPayload(int droneId, boolean control) implements CustomPacketPayload {
    public static final Type<DroneCameraPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "drone_camera"));

    public static final int NONE = -1;

    /** "Terug naar je eigen ogen." */
    public static final DroneCameraPayload OFF = new DroneCameraPayload(NONE, false);

    public static final StreamCodec<ByteBuf, DroneCameraPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> i - 1, i -> i + 1), DroneCameraPayload::droneId,
            ByteBufCodecs.BOOL, DroneCameraPayload::control,
            DroneCameraPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
