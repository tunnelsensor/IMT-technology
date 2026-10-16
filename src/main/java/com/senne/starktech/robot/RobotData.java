package com.senne.starktech.robot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.starktech.bot.BotBoard;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wat er in een Guard Robot-item zit (hoofdstuk 21): zijn onderdelen, zijn printplaat, zijn naam, hoe vol
 * zijn batterij is, en (hoofdstuk 23) zijn programma. Zo blijft alles bewaard als je een robot oppakt en
 * ergens anders neerzet.
 *
 * @param parts   de vier onderdelen, ingepakt (zie RobotParts.pack)
 * @param board   de printplaat met wat erop gesoldeerd is
 * @param name    zijn naam ("" = nog geen)
 * @param battery hoe vol zijn batterij is, van 0 tot 1 (een nieuwe robot is vol)
 * @param program zijn programma (zoals LogicProgram.save het schrijft), leeg = geen
 * @param ammo    hoeveel Robot Ammo er in zijn geweer zit (hoofdstuk 24)
 */
public record RobotData(int parts, BotBoard board, String name, float battery, CompoundTag program, int ammo) {
    /** Een robot zonder bijzondere keuze: helemaal Battle, met een lege printplaat en een volle batterij. */
    public static final RobotData DEFAULT = new RobotData(RobotParts.DEFAULT.pack(), BotBoard.EMPTY, "", 1.0f);

    /** Een nieuwe robot: met een volle batterij. */
    public RobotData(RobotParts parts, BotBoard board, String name) {
        this(parts.pack(), board, name, 1.0f);
    }

    /** Zonder programma. */
    public RobotData(int parts, BotBoard board, String name, float battery) {
        this(parts, board, name, battery, new CompoundTag(), 0);
    }

    public RobotParts robotParts() {
        return RobotParts.unpack(parts);
    }

    public static final Codec<RobotData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("parts").forGetter(RobotData::parts),
            BotBoard.CODEC.optionalFieldOf("board", BotBoard.EMPTY).forGetter(RobotData::board),
            Codec.STRING.optionalFieldOf("name", "").forGetter(RobotData::name),
            Codec.FLOAT.optionalFieldOf("battery", 1.0f).forGetter(RobotData::battery),
            CompoundTag.CODEC.optionalFieldOf("program", new CompoundTag()).forGetter(RobotData::program),
            Codec.INT.optionalFieldOf("ammo", 0).forGetter(RobotData::ammo)
    ).apply(instance, RobotData::new));

    public static final StreamCodec<ByteBuf, RobotData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RobotData::parts,
            BotBoard.STREAM_CODEC, RobotData::board,
            ByteBufCodecs.STRING_UTF8, RobotData::name,
            ByteBufCodecs.FLOAT, RobotData::battery,
            ByteBufCodecs.COMPOUND_TAG, RobotData::program,
            ByteBufCodecs.VAR_INT, RobotData::ammo,
            RobotData::new);
}
