package com.senne.imttech.shield;

import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.redstone.ImtRelays;
import com.senne.imttech.redstone.Relay;
import com.senne.imttech.registry.ModAttachments;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * De naam van een laserpoort of laserbrug in je I.M.T-lijst (hoofdstuk 39, jouw wens van 29 oktober: "zorg dat elke
 * device een eigen naam krijgt die je kunt on/off/pulse/toggle net zoals een relay ... poort1 is dan de naam die je in
 * de GUI van de rayshield hebt ingesteld").
 *
 * Zo werkt het:
 * <ul>
 *   <li>Wie een poort neerzet, krijgt <b>één regel per poort</b> in zijn lijst ("Gate 3", "Bridge 2"): zet hij er een
 *       zender tegenaan, dan komt er geen tweede regel bij. De regel wijst naar één zender van de poort; schakelen geldt
 *       voor de hele poort (zie LaserEmitterBlock.setSwitched).</li>
 *   <li>Hak je juist die zender weg, dan schuift de regel naar een zender ernaast die er nog staat; was het de laatste,
 *       dan verdwijnt de regel.</li>
 *   <li>In het instelscherm van de poort (sneak + rechtsklik) geef je hem een andere naam. Dat is de naam in jouw lijst,
 *       en dus ook de naam in je diagrammen ({@code on("poort1")}), voor je stem en voor de desktop.</li>
 * </ul>
 */
public final class LaserGates {
    private LaserGates() {}

    /** Zo lang mag de naam van een poort hooguit zijn (net als die van een relais). */
    public static final int MAX_NAME = 32;

    /**
     * Zet deze poort in de I.M.T-lijst van de speler, met een standaardnaam ("Gate 3"), tenzij er al een zender van deze
     * poort in zijn lijst staat.
     */
    public static void register(ServerPlayer player, List<BlockPos> members, BlockPos pos, String defaultNameKey) {
        if (index(player, members) < 0) {
            ImtRedstone.register(player, pos, defaultNameKey);
        }
    }

    /** De zender op {@code pos} is weg: zijn regel schuift naar een zender van dezelfde poort ernaast, of verdwijnt. */
    public static void removed(ServerLevel level, BlockPos pos, Block block) {
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (level.getBlockState(next).is(block)) {
                ImtRedstone.move(level, pos, next);
                return;
            }
        }
        ImtRedstone.forget(level, pos);
    }

    /** De naam van deze poort in de lijst van deze speler (van welke zender ook), of "" als hij er niet in staat. */
    public static String nameOf(ServerPlayer player, List<BlockPos> members) {
        int index = index(player, members);
        Relay relay = index < 0 ? null : player.getData(ModAttachments.RELAYS).get(index);
        return relay == null ? "" : relay.name();
    }

    /**
     * Geeft deze poort een andere naam in de lijst van de speler. Staat hij er nog niet in (bv. een poort uit een gebouw,
     * of je haalde hem eruit), dan komt hij erin met deze naam. Geeft false als de naam leeg is of de lijst vol.
     */
    public static boolean rename(ServerPlayer player, List<BlockPos> members, BlockPos pos, String name) {
        String clean = ImtRedstone.clean(name, MAX_NAME, "");
        if (clean.isEmpty()) {
            return false;
        }
        int index = index(player, members);
        if (index < 0) {
            ImtRedstone.register(player, pos, "imt.imttech.laser_gate.default_name");
            index = index(player, members);
            if (index < 0) {
                return false; // de lijst is vol
            }
        }
        ImtRelays data = player.getData(ModAttachments.RELAYS);
        player.setData(ModAttachments.RELAYS, data.replace(index, data.get(index).withName(clean)));
        return true;
    }

    /** De plek in de lijst van de speler van een regel die naar een zender van deze poort wijst, of -1. */
    private static int index(ServerPlayer player, List<BlockPos> members) {
        ImtRelays data = player.getData(ModAttachments.RELAYS);
        for (BlockPos member : members) {
            int index = data.indexAt(player.level().dimension(), member);
            if (index >= 0) {
                return index;
            }
        }
        return -1;
    }
}
