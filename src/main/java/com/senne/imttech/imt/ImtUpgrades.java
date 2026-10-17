package com.senne.imttech.imt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Welke upgrades er in een bril zitten. Upgrades installeer je in het laadstation.
 *
 * @param battery aantal batterij-upgrades (meer energie-opslag)
 * @param scanner aantal scanner-upgrades (grotere scan-straal)
 * @param drones  aantal drone-upgrades (meer drones in de zwerm en rond jou)
 */
public record ImtUpgrades(int battery, int scanner, int drones) {
    public static final ImtUpgrades NONE = new ImtUpgrades(0, 0, 0);

    // optionalFieldOf met een standaardwaarde zorgt ervoor dat oude brillen blijven werken
    // als we later nieuwe soorten upgrades toevoegen.
    public static final Codec<ImtUpgrades> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("battery", 0).forGetter(ImtUpgrades::battery),
            Codec.INT.optionalFieldOf("scanner", 0).forGetter(ImtUpgrades::scanner),
            Codec.INT.optionalFieldOf("drones", 0).forGetter(ImtUpgrades::drones)
    ).apply(instance, ImtUpgrades::new));

    public static final StreamCodec<ByteBuf, ImtUpgrades> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ImtUpgrades::battery,
            ByteBufCodecs.VAR_INT, ImtUpgrades::scanner,
            ByteBufCodecs.VAR_INT, ImtUpgrades::drones,
            ImtUpgrades::new);

    public ImtUpgrades withBattery(int level) {
        return new ImtUpgrades(level, scanner, drones);
    }

    public ImtUpgrades withScanner(int level) {
        return new ImtUpgrades(battery, level, drones);
    }

    public ImtUpgrades withDrones(int level) {
        return new ImtUpgrades(battery, scanner, level);
    }
}
