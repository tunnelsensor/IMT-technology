package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Bericht van speler naar server: "doe iets met mijn waypoints".
 *
 * @param op    wat er moet gebeuren
 * @param index welk waypoint (bij REMOVE en NAVIGATE; bij NAVIGATE ook -1 = stoppen, -2 = sterfplek)
 * @param name  naam van het nieuwe waypoint (bij ADD)
 * @param color kleur van het nieuwe waypoint (bij ADD)
 */
public record WaypointActionPayload(Op op, int index, String name, int color) implements CustomPacketPayload {
    public static final Type<WaypointActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StarkTech.MODID, "waypoint_action"));

    public enum Op {
        /** Nieuw waypoint op je huidige plek. */
        ADD,
        REMOVE,
        NAVIGATE
    }

    public static final StreamCodec<ByteBuf, WaypointActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal), WaypointActionPayload::op,
            ByteBufCodecs.VAR_INT.map(i -> i - 2, i -> i + 2), WaypointActionPayload::index,
            ByteBufCodecs.stringUtf8(64), WaypointActionPayload::name,
            ByteBufCodecs.INT, WaypointActionPayload::color,
            WaypointActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
