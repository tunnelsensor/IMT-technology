package com.senne.imttech.structures;

import com.senne.imttech.logic.InputDeviceBlockEntity;
import com.senne.imttech.logic.KeycardData;
import com.senne.imttech.logic.KeycardItem;
import com.senne.imttech.logic.LogicStore;
import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.raven.RavenSoldierEntity;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * De bewakers van een Biebia's Industries Tower en wat ze op zak hebben (hoofdstuk 54, jouw notitie: "al
 * geconfigureerde keycards die je kunt krijgen van de guards die je toevoegt, makkelijker door de security").
 *
 * - Een <b>guard</b> (de markering "raven_guard" in de lobby en voor de generatorkamer, zie tools/generate_tower_rooms.py)
 *   is een Raven-soldaat die bij zijn dood een keycard van de toren laat vallen: geschreven op de kern van de toren, in de
 *   groep "staff". De kern krijgt die groep en de kaart pas op dat moment (de kern weet pas in de wereld wat zijn IP is).
 * - Biebia's kern (een kern van niemand) krijgt bij zijn eerste tick het diagram <b>"Generator room"</b>: een staff-kaart
 *   op de lezer "Power door" zet de poort van rayshields "Power gate" acht seconden uit; een andere kaart geeft alarm.
 *   Dat is meteen een voorbeeld van hoe lezers, groepen en diagrammen samenwerken: hack de toren en kijk in de app
 *   Logic. De lezer heet al zo (zijn naam staat in het bouwbestand); de zenders krijgen hun naam hier, want een kern
 *   van niemand heeft geen eigenaar met een apparatenlijst (zie {@link #refreshBiebiaDevices}).
 */
public final class TowerGuards {
    private TowerGuards() {}

    public static final String GUARD = "raven_guard";
    public static final String STAFF = "staff";
    public static final String PROGRAM = "Generator room";
    public static final String POWER_DOOR = "Power door";
    public static final String POWER_GATE = "Power gate";
    /** Zo ver van de lezer "Power door" staan de zenders van de poort (hooguit). */
    private static final double GATE_REACH = 6.5;

    public static final String SOURCE = """
            when keycard("Power door"):
                if card.in_group("staff"):
                    accept()
                    log("Staff card at the generator room")
                    off("Power gate")
                    wait(8)
                    on("Power gate")
                else:
                    deny()
                    alarm("Unknown card at the generator room")
            """;

    /** Een bewaker met een keycard op zak, op deze plek (zoals BiebiaBunkerPiece.spawnSoldier, plus de kaart). */
    public static void spawnGuard(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        ServerLevel serverLevel = level.getLevel();
        RavenSoldierEntity soldier = ModEntities.RAVEN_SOLDIER.get().create(serverLevel);
        if (soldier == null) {
            return;
        }
        soldier.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360, 0);
        soldier.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        soldier.setPersistenceRequired();
        soldier.setKeycardGuard(true);
        level.addFreshEntityWithPassengers(soldier);
    }

    /**
     * De keycard van een bewaker die hier sterft: geschreven op de kern die deze plek beschermt, in de groep "staff".
     * Leeg als er geen kern is (de bewaker liep ver weg) of de kern vol zit.
     */
    public static ItemStack staffCard(ServerLevel level, BlockPos at) {
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, at);
        if (core == null) {
            return ItemStack.EMPTY;
        }
        core.ensureSystem(level);
        LogicStore store = core.logic();
        if (!store.hasGroup(STAFF)) {
            store.addGroup(STAFF, "");
        }
        LogicStore.Card card = store.issue("Staff pass", 2, "Biebia", STAFF);
        if (card == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(ModItems.KEYCARD.get());
        KeycardItem.write(stack, new KeycardData(core.ip(), core.hostname(), card.number(), card.colour(), card.label()));
        core.setChanged();
        return stack;
    }

    /** Het diagram en de groep van Biebia's kern (bij zijn eerste tick, zie SecurityCoreBlockEntity.ensureSystem). */
    public static void installBiebiaLogic(LogicStore store) {
        if (!store.hasGroup(STAFF)) {
            store.addGroup(STAFF, "");
        }
        if (store.program(PROGRAM) == null) {
            var graph = GraphConverter.toGraph(ScriptParser.parse(SOURCE), null);
            store.put(null, new LogicProgram(PROGRAM, true, graph, SOURCE, ProgramKind.SERVER));
        }
    }

    /**
     * Het telefoonboek van een kern van niemand: de laserzenders bij een lezer "Power door" heten "Power gate". (Een
     * kern met een eigenaar neemt de namen uit diens apparatenlijst, zie LogicDevices.refresh.)
     */
    public static void refreshBiebiaDevices(ServerLevel level, SecurityCoreBlockEntity core) {
        if (core.ownerId() != null) {
            return;
        }
        SecurityDevices.Found found = SecurityDevices.scan(level, core);
        List<BlockPos> readers = new ArrayList<>();
        for (BlockPos pos : found.inputs()) {
            if (level.getBlockEntity(pos) instanceof InputDeviceBlockEntity device && POWER_DOOR.equalsIgnoreCase(device.name())) {
                readers.add(pos);
            }
        }
        if (readers.isEmpty()) {
            return;
        }
        Map<String, List<LogicStore.Target>> fresh = new LinkedHashMap<>();
        for (BlockPos emitter : found.emitters()) {
            for (BlockPos reader : readers) {
                if (emitter.distSqr(reader) <= GATE_REACH * GATE_REACH) {
                    fresh.computeIfAbsent(POWER_GATE, key -> new ArrayList<>()).add(new LogicStore.Target(emitter.immutable(), -1));
                    break;
                }
            }
        }
        if (!fresh.isEmpty() && core.logic().setDevices(fresh)) {
            core.setChanged();
        }
    }
}
