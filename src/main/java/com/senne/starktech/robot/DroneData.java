package com.senne.starktech.robot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.starktech.bot.BotBoard;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wat er in een Security Drone-item zit (hoofdstuk 25): zijn printplaat, zijn naam, hoe vol zijn batterij is, zijn
 * programma en zijn kogels. Zo blijft alles bewaard als je een drone oppakt en ergens anders neerzet (net als
 * RobotData bij een robot, maar zonder onderdelen: een drone ziet er altijd hetzelfde uit).
 *
 * @param board   de printplaat met wat erop gesoldeerd is
 * @param name    zijn naam ("" = nog geen)
 * @param battery hoe vol zijn batterij is, van 0 tot 1 (een nieuwe drone is vol)
 * @param program zijn programma (zoals LogicProgram.save het schrijft), leeg = geen
 * @param ammo    hoeveel Robot Ammo er in zijn geweer zit
 */
public record DroneData(BotBoard board, String name, float battery, CompoundTag program, int ammo) {
    /** Een nieuwe drone: een lege printplaat en een volle batterij. */
    public static final DroneData DEFAULT = new DroneData(BotBoard.EMPTY, "", 1.0f, new CompoundTag(), 0);

    public static final Codec<DroneData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BotBoard.CODEC.optionalFieldOf("board", BotBoard.EMPTY).forGetter(DroneData::board),
            Codec.STRING.optionalFieldOf("name", "").forGetter(DroneData::name),
            Codec.FLOAT.optionalFieldOf("battery", 1.0f).forGetter(DroneData::battery),
            CompoundTag.CODEC.optionalFieldOf("program", new CompoundTag()).forGetter(DroneData::program),
            Codec.INT.optionalFieldOf("ammo", 0).forGetter(DroneData::ammo)
    ).apply(instance, DroneData::new));

    public static final StreamCodec<ByteBuf, DroneData> STREAM_CODEC = StreamCodec.composite(
            BotBoard.STREAM_CODEC, DroneData::board,
            ByteBufCodecs.STRING_UTF8, DroneData::name,
            ByteBufCodecs.FLOAT, DroneData::battery,
            ByteBufCodecs.COMPOUND_TAG, DroneData::program,
            ByteBufCodecs.VAR_INT, DroneData::ammo,
            DroneData::new);
}
