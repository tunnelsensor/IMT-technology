package com.senne.imttech.client;

import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.redstone.Relay;
import com.senne.imttech.redstone.RelayBlock;
import net.minecraft.client.Minecraft;
import com.senne.imttech.shield.DomeGeneratorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Kleine hulpjes aan de speler-kant voor de redstone-relais: staat er een aan, wat voor
 * apparaat is het, en staat het er nog?
 */
public final class RelayLabels {
    private RelayLabels() {}

    /** Staat dit relais aan? (We kijken in de wereld zoals jouw spel die kent.) */
    public static boolean isOn(Relay relay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != relay.dimension() || !mc.level.hasChunkAt(relay.pos())) {
            return false; // te ver weg om te weten
        }
        BlockState state = mc.level.getBlockState(relay.pos());
        if (relay.channel() >= 0 && state.getBlock() instanceof com.senne.imttech.energy.PowerCabinetBlock) {
            return com.senne.imttech.energy.PowerCabinetBlock.isOn(state,
                    net.minecraft.core.Direction.from3DDataValue(relay.channel()));
        }
        return state.getBlock() instanceof RelayBlock && state.getValue(RelayBlock.POWERED);
    }

    /** Weet jouw spel wat dit relais doet? (Anders zetten we geen lampje in het scherm.) */
    public static boolean isKnown(Relay relay) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == relay.dimension() && mc.level.hasChunkAt(relay.pos());
    }

    /** Wat voor apparaat staat hier? Bepaalt onder welk kopje het in de lijst komt. */
    public enum Kind {
        RELAY("screen.imttech.relays.kind.relay"),
        DOME("screen.imttech.relays.kind.dome"),
        TURRET("screen.imttech.relays.kind.turret"),
        CABINET("screen.imttech.relays.kind.cabinet");

        public final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    /** Onder welk kopje hoort dit apparaat? */
    public static Kind kindOf(Relay relay) {
        Minecraft mc = Minecraft.getInstance();
        if (!isKnown(relay)) {
            return Kind.RELAY; // te ver weg om te weten; dan maar bij de relais
        }
        Block block = mc.level.getBlockState(relay.pos()).getBlock();
        if (block instanceof DomeGeneratorBlock) {
            return Kind.DOME;
        }
        if (block instanceof com.senne.imttech.turret.TurretBlock) {
            return Kind.TURRET;
        }
        if (block instanceof com.senne.imttech.energy.PowerCabinetBlock) {
            return Kind.CABINET;
        }
        return Kind.RELAY;
    }

    /** Staat het kastje er nog echt? (Iemand kan het gesloopt hebben.) */
    public static boolean exists(Relay relay) {
        Minecraft mc = Minecraft.getInstance();
        return isKnown(relay) && ImtRedstone.isSwitchable(mc.level.getBlockState(relay.pos()));
    }
}
