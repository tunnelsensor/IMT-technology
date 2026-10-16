package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import com.senne.starktech.bot.BotNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: een knop uit het robotscherm (hoofdstuk 21). De server kijkt na of je deze
 * robot mag instellen (zie robot/Robots).
 *
 * @param entityId welke robot
 * @param op       wat er moet gebeuren
 * @param text     de nieuwe naam (bij RENAME), anders leeg
 */
public record RobotActionPayload(int entityId, Op op, String text) implements CustomPacketPayload {
    public static final Type<RobotActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "robot_action"));

    public enum Op {
        /** Aanzetten: hij stapt uit zijn dock. */
        TURN_ON,
        /** Uitzetten: hij loopt naar zijn dock en laadt daar. */
        TURN_OFF,
        /** Een andere naam. */
        RENAME,
        /** Oppakken: hij wordt weer een Guard Robot-item in je rugzak. */
        PICK_UP
    }

    public static final StreamCodec<ByteBuf, RobotActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RobotActionPayload::entityId,
            ByteBufCodecs.VAR_INT.map(i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal),
            RobotActionPayload::op,
            ByteBufCodecs.stringUtf8(BotNetwork.MAX_NAME * 4), RobotActionPayload::text,
            RobotActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
