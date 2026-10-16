package com.senne.starktech.robot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.starktech.bot.BotBoard;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wat er in een Guard Robot-item zit (hoofdstuk 21): zijn onderdelen, zijn printplaat en zijn naam. Zo
 * blijft alles bewaard als je een robot oppakt en ergens anders neerzet.
 *
 * @param parts de vier onderdelen, ingepakt (zie RobotParts.pack)
 * @param board de printplaat met wat erop gesoldeerd is
 * @param name  zijn naam ("" = nog geen)
 */
public record RobotData(int parts, BotBoard board, String name) {
    /** Een robot zonder bijzondere keuze: helemaal Battle, met een lege printplaat. */
    public static final RobotData DEFAULT = new RobotData(RobotParts.DEFAULT.pack(), BotBoard.EMPTY, "");

    public RobotData(RobotParts parts, BotBoard board, String name) {
        this(parts.pack(), board, name);
    }

    public RobotParts robotParts() {
        return RobotParts.unpack(parts);
    }

    public static final Codec<RobotData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("parts").forGetter(RobotData::parts),
            BotBoard.CODEC.optionalFieldOf("board", BotBoard.EMPTY).forGetter(RobotData::board),
            Codec.STRING.optionalFieldOf("name", "").forGetter(RobotData::name)
    ).apply(instance, RobotData::new));

    public static final StreamCodec<ByteBuf, RobotData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RobotData::parts,
            BotBoard.STREAM_CODEC, RobotData::board,
            ByteBufCodecs.STRING_UTF8, RobotData::name,
            RobotData::new);
}
