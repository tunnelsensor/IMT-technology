package com.senne.imttech.robot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.imttech.bot.BotBoard;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

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
 * @param health  hoe heel hij is, van 0 tot 1 (een nieuwe robot is heel). Zo blijft zijn schade bewaard als je hem
 *                oppakt en weer neerzet: repareren kan alleen met ijzer (26 oktober)
 */
public record RobotData(int parts, BotBoard board, String name, float battery, CompoundTag program, int ammo, float health) {
    /** Een robot zonder bijzondere keuze: helemaal Battle, met een lege printplaat en een volle batterij. */
    public static final RobotData DEFAULT = new RobotData(RobotParts.DEFAULT.pack(), BotBoard.EMPTY, "", 1.0f);

    /** Een nieuwe robot: met een volle batterij. */
    public RobotData(RobotParts parts, BotBoard board, String name) {
        this(parts.pack(), board, name, 1.0f);
    }

    /** Zonder programma, en heel. */
    public RobotData(int parts, BotBoard board, String name, float battery) {
        this(parts, board, name, battery, new CompoundTag(), 0, 1.0f);
    }

    public RobotParts robotParts() {
        return RobotParts.unpack(parts);
    }

    /** Is hij beschadigd (niet helemaal heel)? */
    public boolean damaged() {
        return health < 1.0f;
    }

    /** Hoe heel, in procenten (voor de tooltip). Beschadigd is nooit 100%, ook niet bij 99,8%. */
    public int healthPercent() {
        return damaged() ? Math.min(99, Math.round(health * 100)) : 100;
    }

    public static final Codec<RobotData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("parts").forGetter(RobotData::parts),
            BotBoard.CODEC.optionalFieldOf("board", BotBoard.EMPTY).forGetter(RobotData::board),
            Codec.STRING.optionalFieldOf("name", "").forGetter(RobotData::name),
            Codec.FLOAT.optionalFieldOf("battery", 1.0f).forGetter(RobotData::battery),
            CompoundTag.CODEC.optionalFieldOf("program", new CompoundTag()).forGetter(RobotData::program),
            Codec.INT.optionalFieldOf("ammo", 0).forGetter(RobotData::ammo),
            // Een robot-item van vóór 26 oktober heeft dit nog niet: dan is hij heel.
            Codec.FLOAT.optionalFieldOf("health", 1.0f).forGetter(RobotData::health)
    ).apply(instance, RobotData::new));

    /**
     * Zeven velden: dat kan StreamCodec.composite van Minecraft niet (hooguit zes), die van NeoForge wel. Hoofdstuk 130:
     * een programma op slot gaat zonder code naar je spel (LogicProgram.NETWORK_TAG); de robot zelf draait het op de server.
     */
    public static final StreamCodec<ByteBuf, RobotData> STREAM_CODEC = NeoForgeStreamCodecs.composite(
            ByteBufCodecs.VAR_INT, RobotData::parts,
            BotBoard.STREAM_CODEC, RobotData::board,
            ByteBufCodecs.STRING_UTF8, RobotData::name,
            ByteBufCodecs.FLOAT, RobotData::battery,
            com.senne.imttech.logic.program.LogicProgram.NETWORK_TAG, RobotData::program,
            ByteBufCodecs.VAR_INT, RobotData::ammo,
            ByteBufCodecs.FLOAT, RobotData::health,
            RobotData::new);
}
