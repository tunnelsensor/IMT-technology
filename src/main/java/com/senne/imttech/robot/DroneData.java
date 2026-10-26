package com.senne.imttech.robot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.imttech.bot.BotBoard;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wat er in een Security Drone-item zit (hoofdstuk 25): zijn printplaat, zijn naam, hoe vol zijn batterij is, zijn
 * programma, zijn kogels en hoe heel hij is. Zo blijft alles bewaard als je een drone oppakt en ergens anders neerzet
 * (net als RobotData bij een robot, maar zonder onderdelen: een drone ziet er altijd hetzelfde uit).
 *
 * @param board   de printplaat met wat erop gesoldeerd is
 * @param name    zijn naam ("" = nog geen)
 * @param battery hoe vol zijn batterij is, van 0 tot 1 (een nieuwe drone is vol)
 * @param program zijn programma (zoals LogicProgram.save het schrijft), leeg = geen
 * @param ammo    hoeveel Robot Ammo er in zijn geweer zit
 * @param health  hoe heel hij is, van 0 tot 1 (een nieuwe drone is heel). Zo blijft zijn schade bewaard als je hem
 *                oppakt en weer neerzet: repareren kan alleen met ijzer (26 oktober)
 */
public record DroneData(BotBoard board, String name, float battery, CompoundTag program, int ammo, float health) {
    /** Een nieuwe drone: een lege printplaat, een volle batterij, en heel. */
    public static final DroneData DEFAULT = new DroneData(BotBoard.EMPTY, "", 1.0f, new CompoundTag(), 0, 1.0f);

    /** Is hij beschadigd (niet helemaal heel)? */
    public boolean damaged() {
        return health < 1.0f;
    }

    /** Hoe heel, in procenten (voor de tooltip). Beschadigd is nooit 100%, ook niet bij 99,8%. */
    public int healthPercent() {
        return damaged() ? Math.min(99, Math.round(health * 100)) : 100;
    }

    public static final Codec<DroneData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BotBoard.CODEC.optionalFieldOf("board", BotBoard.EMPTY).forGetter(DroneData::board),
            Codec.STRING.optionalFieldOf("name", "").forGetter(DroneData::name),
            Codec.FLOAT.optionalFieldOf("battery", 1.0f).forGetter(DroneData::battery),
            CompoundTag.CODEC.optionalFieldOf("program", new CompoundTag()).forGetter(DroneData::program),
            Codec.INT.optionalFieldOf("ammo", 0).forGetter(DroneData::ammo),
            // Een drone-item van vóór 26 oktober heeft dit nog niet: dan is hij heel.
            Codec.FLOAT.optionalFieldOf("health", 1.0f).forGetter(DroneData::health)
    ).apply(instance, DroneData::new));

    public static final StreamCodec<ByteBuf, DroneData> STREAM_CODEC = StreamCodec.composite(
            BotBoard.STREAM_CODEC, DroneData::board,
            ByteBufCodecs.STRING_UTF8, DroneData::name,
            ByteBufCodecs.FLOAT, DroneData::battery,
            ByteBufCodecs.COMPOUND_TAG, DroneData::program,
            ByteBufCodecs.VAR_INT, DroneData::ammo,
            ByteBufCodecs.FLOAT, DroneData::health,
            DroneData::new);
}
