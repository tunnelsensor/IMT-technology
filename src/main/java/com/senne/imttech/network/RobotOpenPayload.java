package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "open het scherm van deze robot" (hoofdstuk 21). De server beslist of je
 * het mag (jij zette hem neer, of je bent vertrouwd in die basis; zie robot/Robots). Zijn naam, onderdelen,
 * batterij en of hij aan staat leest het scherm gewoon van de robot (die kent jouw spel al).
 *
 * @param entityId welke robot
 * @param owner    de naam van zijn eigenaar (leeg als niemand)
 * @param home     waar zijn Robot Dock staat (leeg als hij geen dock heeft)
 */
public record RobotOpenPayload(int entityId, String owner, Optional<BlockPos> home) implements CustomPacketPayload {
    public static final Type<RobotOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "robot_open"));

    public static final StreamCodec<ByteBuf, RobotOpenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RobotOpenPayload::entityId,
            ByteBufCodecs.stringUtf8(64), RobotOpenPayload::owner,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs::optional), RobotOpenPayload::home,
            RobotOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
