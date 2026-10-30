package com.senne.imttech.redstone;

import com.senne.imttech.registry.ModAttachments;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * De naam van een apparaat in je I.M.T-lijst (hoofdstuk 39, jouw wens van 29 oktober: "zorg dat elke device een eigen
 * naam krijgt die je kunt on/off/pulse/toggle net zoals een relay ... poort1 is dan de naam die je in de GUI van de
 * rayshield hebt ingesteld"): laserpoorten, laserbruggen, torentjes en camera's.
 *
 * Zo werkt het:
 * <ul>
 *   <li>Wie zo'n apparaat neerzet, krijgt het in zijn lijst ("Gate 3", "Bridge 2", "Turret 4", "Camera 1"). Een poort of
 *       brug van meer zenders staat er <b>één keer</b> in: zet je er een zender tegenaan, dan komt er geen tweede regel bij.
 *       De regel wijst naar één zender; schakelen geldt voor de hele poort (zie LaserEmitterBlock.setSwitched).</li>
 *   <li>Hak je juist die zender weg, dan schuift de regel naar een zender ernaast die er nog staat; was het de laatste,
 *       dan verdwijnt de regel.</li>
 *   <li>Een andere naam geef je in het scherm van de poort of brug (sneak + rechtsklik), of op de desktop (Rename in
 *       Devices). Dat is de naam in jouw lijst, en dus ook de naam in je diagrammen ({@code on("poort1")}), voor je stem
 *       en voor de desktop.</li>
 * </ul>
 */
public final class DeviceNames {
    private DeviceNames() {}

    /** Zo lang mag de naam van een apparaat hooguit zijn (net als die van een relais). */
    public static final int MAX_NAME = 32;

    /**
     * Zet dit apparaat in de I.M.T-lijst van de speler, met een standaardnaam ("Gate 3"), tenzij er al een blok van
     * hetzelfde apparaat ({@code members}: de zenders van een poort, of alleen het blok zelf) in zijn lijst staat.
     */
    public static void register(ServerPlayer player, List<BlockPos> members, BlockPos pos, String defaultNameKey) {
        if (index(player, members) < 0) {
            ImtRedstone.register(player, pos, defaultNameKey);
        }
    }

    /** Het blok op {@code pos} is weg: zijn regel schuift naar een blok van dezelfde soort ernaast, of verdwijnt. */
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

    /** De naam van dit apparaat in de lijst van deze speler (van welk blok ervan ook), of "" als het er niet in staat. */
    public static String nameOf(ServerPlayer player, List<BlockPos> members) {
        int index = index(player, members);
        Relay relay = index < 0 ? null : player.getData(ModAttachments.RELAYS).get(index);
        return relay == null ? "" : relay.name();
    }

    /**
     * Geeft dit apparaat een andere naam in de lijst van de speler. Staat het er nog niet in (bv. een poort uit een
     * gebouw, of je haalde hem eruit), dan komt het erin met deze naam. Geeft false als de naam leeg is of de lijst vol.
     */
    public static boolean rename(ServerPlayer player, List<BlockPos> members, BlockPos pos, String name, String defaultNameKey) {
        String clean = ImtRedstone.clean(name, MAX_NAME, "");
        if (clean.isEmpty()) {
            return false;
        }
        int index = index(player, members);
        if (index < 0) {
            ImtRedstone.register(player, pos, defaultNameKey);
            index = index(player, members);
            if (index < 0) {
                return false; // de lijst is vol (of het apparaat is van iemand anders)
            }
        }
        ImtRelays data = player.getData(ModAttachments.RELAYS);
        player.setData(ModAttachments.RELAYS, data.replace(index, data.get(index).withName(clean)));
        return true;
    }

    /** De plek in de lijst van de speler van een regel die naar een blok van dit apparaat wijst, of -1. */
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
