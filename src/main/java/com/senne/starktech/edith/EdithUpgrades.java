package com.senne.starktech.edith;

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
public record EdithUpgrades(int battery, int scanner, int drones) {
    public static final EdithUpgrades NONE = new EdithUpgrades(0, 0, 0);

    // optionalFieldOf met een standaardwaarde zorgt ervoor dat oude brillen blijven werken
    // als we later nieuwe soorten upgrades toevoegen.
    public static final Codec<EdithUpgrades> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("battery", 0).forGetter(EdithUpgrades::battery),
            Codec.INT.optionalFieldOf("scanner", 0).forGetter(EdithUpgrades::scanner),
            Codec.INT.optionalFieldOf("drones", 0).forGetter(EdithUpgrades::drones)
    ).apply(instance, EdithUpgrades::new));

    public static final StreamCodec<ByteBuf, EdithUpgrades> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EdithUpgrades::battery,
            ByteBufCodecs.VAR_INT, EdithUpgrades::scanner,
            ByteBufCodecs.VAR_INT, EdithUpgrades::drones,
            EdithUpgrades::new);

    public EdithUpgrades withBattery(int level) {
        return new EdithUpgrades(level, scanner, drones);
    }

    public EdithUpgrades withScanner(int level) {
        return new EdithUpgrades(battery, level, drones);
    }

    public EdithUpgrades withDrones(int level) {
        return new EdithUpgrades(battery, scanner, level);
    }
}
