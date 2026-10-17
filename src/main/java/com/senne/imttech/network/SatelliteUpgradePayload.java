package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import com.senne.imttech.satellite.SatelliteUpgrade;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "bouw het volgende niveau van dit onderdeel in mijn
 * satelliet". De server controleert alles zelf (onderdelen, drones) in SatelliteWorkshop.
 *
 * @param upgrade het onderdeel
 * @param maxAll  in plaats daarvan álles meteen op het hoogste niveau (alleen in creative)
 */
public record SatelliteUpgradePayload(SatelliteUpgrade upgrade, boolean maxAll) implements CustomPacketPayload {
    public static final Type<SatelliteUpgradePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "satellite_upgrade"));

    public static final StreamCodec<ByteBuf, SatelliteUpgradePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(SatelliteUpgrade::byId, SatelliteUpgrade::ordinal), SatelliteUpgradePayload::upgrade,
            ByteBufCodecs.BOOL, SatelliteUpgradePayload::maxAll,
            SatelliteUpgradePayload::new);

    public static SatelliteUpgradePayload buy(SatelliteUpgrade upgrade) {
        return new SatelliteUpgradePayload(upgrade, false);
    }

    public static SatelliteUpgradePayload everything() {
        return new SatelliteUpgradePayload(SatelliteUpgrade.PRECISION, true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
