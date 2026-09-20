package com.senne.starktech.client;

import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.redstone.EdithRelays;
import com.senne.starktech.redstone.Relay;
import com.senne.starktech.redstone.RelayBlock;
import com.senne.starktech.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.senne.starktech.shield.DomeGeneratorBlock;
import com.senne.starktech.shield.LaserEmitterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Kleine hulpjes aan de speler-kant voor de redstone-relais: staat er een aan, en welke
 * relais zijn er in de buurt (voor de naamplaatjes in de HUD)?
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
        return state.getBlock() instanceof RelayBlock && state.getValue(RelayBlock.POWERED);
    }

    /** Weet jouw spel wat dit relais doet? (Anders zetten we geen lampje in het scherm.) */
    public static boolean isKnown(Relay relay) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == relay.dimension() && mc.level.hasChunkAt(relay.pos());
    }

    /** Wat voor apparaat staat hier? Bepaalt onder welk kopje het in de lijst komt. */
    public enum Kind {
        RELAY("screen.starktech.relays.kind.relay"),
        EMITTER("screen.starktech.relays.kind.emitter"),
        DOME("screen.starktech.relays.kind.dome");

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
        if (block instanceof LaserEmitterBlock) {
            return Kind.EMITTER;
        }
        if (block instanceof DomeGeneratorBlock) {
            return Kind.DOME;
        }
        return Kind.RELAY;
    }

    /** Staat het kastje er nog echt? (Iemand kan het gesloopt hebben.) */
    public static boolean exists(Relay relay) {
        Minecraft mc = Minecraft.getInstance();
        return isKnown(relay) && EdithRedstone.isSwitchable(mc.level.getBlockState(relay.pos()));
    }

    /** De relais in de buurt, voor de naamplaatjes in de HUD. */
    public static List<Relay> nearby() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of();
        }
        EdithRelays data = player.getData(ModAttachments.RELAYS);
        if (data.relays().isEmpty()) {
            return List.of();
        }
        double range = EdithBalance.RELAY_LABEL_RANGE;
        List<Relay> found = new ArrayList<>();
        for (Relay relay : data.relays()) {
            if (relay.dimension() == player.level().dimension()
                    && player.blockPosition().distSqr(relay.pos()) <= range * range
                    // Staat het kastje er niet meer? Dan ook geen naamplaatje in de lucht.
                    && exists(relay)) {
                found.add(relay);
            }
        }
        return found;
    }
}
