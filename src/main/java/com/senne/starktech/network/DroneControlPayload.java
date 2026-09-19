package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server, elke tick tijdens het besturen van de verkenningsdrone:
 * welke kant wil je op en waar kijk je naar?
 *
 * @param forward  vooruit (1) of achteruit (-1)
 * @param strafe   links (1) of rechts (-1)
 * @param vertical omhoog (1) of omlaag (-1)
 * @param boost    snel vliegen (sprinttoets)
 * @param yaw      kijkrichting links/rechts (graden)
 * @param pitch    kijkrichting omhoog/omlaag (graden)
 */
public record DroneControlPayload(float forward, float strafe, float vertical, boolean boost, float yaw, float pitch)
        implements CustomPacketPayload {
    public static final Type<DroneControlPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "drone_control"));

    public static final DroneControlPayload IDLE = new DroneControlPayload(0, 0, 0, false, 0, 0);

    public static final StreamCodec<ByteBuf, DroneControlPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, DroneControlPayload::forward,
            ByteBufCodecs.FLOAT, DroneControlPayload::strafe,
            ByteBufCodecs.FLOAT, DroneControlPayload::vertical,
            ByteBufCodecs.BOOL, DroneControlPayload::boost,
            ByteBufCodecs.FLOAT, DroneControlPayload::yaw,
            ByteBufCodecs.FLOAT, DroneControlPayload::pitch,
            DroneControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
