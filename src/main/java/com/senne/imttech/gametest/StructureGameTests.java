package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.raven.BiebiaBunkerPiece;
import com.senne.imttech.structures.BunkerLayout;
import com.senne.imttech.structures.TowerLayout;
import com.senne.imttech.structures.TowerPiece;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de toren en de bunker uit kamers (hoofdstuk 51, 4 november): de indeling volgt de regels (vaste uiteinden,
 * een willekeurig aantal en volgorde, de kern en de reactor altijd één keer), en elk stuk bestaat als bestand, met de
 * goede maat en de dingen die erin horen (de liftkoker op dezelfde plek, de kern, de kluiskist, de laserwanden).
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class StructureGameTests {
    private StructureGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    private static StructureTemplate template(GameTestHelper helper, ResourceLocation id) {
        return helper.getLevel().getStructureManager().get(id).orElseThrow(() -> new GameTestAssertException("Het bestand " + id + " ontbreekt"));
    }

    private static boolean contains(StructureTemplate template, Block block) {
        return !template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).isEmpty();
    }

    private static boolean has(StructureTemplate template, Block block, BlockPos at) {
        return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).stream().anyMatch(info -> info.pos().equals(at));
    }

    /**
     * Tweehonderd torens: altijd lobby onder en top boven, 5 tot 8 tussenverdiepingen, de beveiliging precies één keer,
     * geen kamer twee keer, en over al die torens komt elke kamer en elk aantal voor. Elk stuk is een bestand van de
     * goede maat; de liftkoker (de ladder) staat in elke tussenverdieping op dezelfde plek, de kern zit in security, de
     * docks in de hangar, de kluiskist en het landingsplatform in de top.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "rooms")
    public static void tower_rooms_follow_the_rules_and_exist(GameTestHelper helper) {
        Random random = new Random(1234);
        Set<String> seenRooms = new HashSet<>();
        Set<Integer> seenCounts = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            List<String> layout = TowerLayout.random(random);
            check(TowerLayout.valid(layout), "Een toren die niet klopt: " + layout);
            seenRooms.addAll(layout);
            seenCounts.add(layout.size() - 2);
        }
        check(seenRooms.containsAll(TowerLayout.ROOMS) && seenCounts.size() == TowerLayout.MAX_FLOORS - TowerLayout.MIN_FLOORS + 1,
                "Over 200 torens zouden alle kamers en alle aantallen voorbij moeten komen: " + seenRooms + " " + seenCounts);
        check(!TowerLayout.valid(List.of("lobby", "lab", "top")), "Een toren met één kamer zou niet mogen");
        check(!TowerLayout.valid(List.of("lobby", "lab", "archive", "hangar", "living", "power", "top")), "Zonder security zou niet mogen");
        check(!TowerLayout.valid(List.of("lobby", "lab", "archive", "hangar", "living", "security", "top")), "Zonder generatorkamer zou niet mogen (hoofdstuk 54)");
        List<String> all = new ArrayList<>(TowerLayout.ROOMS);
        all.add(TowerLayout.SECURITY);
        all.add(TowerLayout.POWER);
        BlockPos ladder = new BlockPos(20, 5, 30);
        for (String room : all) {
            StructureTemplate piece = template(helper, TowerPiece.template(room));
            check(piece.getSize().equals(new Vec3i(41, TowerLayout.MIDDLE_HEIGHT, 41)), room + " heeft niet de maat van een verdieping: " + piece.getSize());
            check(has(piece, Blocks.LADDER, ladder), room + " mist de liftkoker op " + ladder);
        }
        StructureTemplate lobby = template(helper, TowerPiece.template(TowerLayout.LOBBY));
        check(lobby.getSize().equals(new Vec3i(41, TowerLayout.LOBBY_HEIGHT, 55)), "De lobby heeft niet de goede maat: " + lobby.getSize());
        check(has(lobby, Blocks.LADDER, new BlockPos(20, 5, 30)), "De lobby mist de liftkoker");
        StructureTemplate top = template(helper, TowerPiece.template(TowerLayout.TOP));
        check(top.getSize().equals(new Vec3i(41, TowerLayout.TOP_HEIGHT, 41)), "De top heeft niet de goede maat: " + top.getSize());
        check(contains(top, Blocks.CHEST) && contains(top, com.senne.imttech.registry.ModBlocks.LANDING_PAD.get())
                && contains(top, com.senne.imttech.registry.ModBlocks.LASER_EMITTER.get()), "De top mist de kluis, het platform of de lasers");
        StructureTemplate security = template(helper, TowerPiece.template(TowerLayout.SECURITY));
        check(contains(security, com.senne.imttech.registry.ModBlocks.SECURITY_CORE.get()), "Security mist de kern");
        // Hoofdstuk 54: de generatorkamer heeft de stroomkast, de generatoren, de poort van rayshields, de lezer en de serverkast.
        StructureTemplate power = template(helper, TowerPiece.template(TowerLayout.POWER));
        check(contains(power, com.senne.imttech.registry.ModBlocks.POWER_CABINET.get())
                && contains(power, com.senne.imttech.registry.ModBlocks.HEAT_GENERATOR.get())
                && contains(power, com.senne.imttech.registry.ModBlocks.LASER_EMITTER.get())
                && contains(power, com.senne.imttech.registry.ModBlocks.KEYCARD_READER.get())
                && contains(power, com.senne.imttech.registry.ModBlocks.SERVER_RACK.get()), "De generatorkamer mist de kast, de generatoren, de poort, de lezer of het rek");
        check(contains(template(helper, TowerPiece.template("hangar")), com.senne.imttech.registry.ModBlocks.DRONE_DOCK.get()),
                "De hangar mist zijn drone-docks");
        check(TowerLayout.height(List.of("lobby", "a", "b", "c", "d", "e", "top")) == 110, "Vijf verdiepingen zouden de klassieke 110 hoog geven");
        helper.succeed();
    }

    /**
     * Tweehonderd bunkers: altijd de ingang vooraan en de kluis achteraan, 3 tot 6 zalen, de reactor precies één keer, geen
     * zaal twee keer. Elk stuk is een bestand van de goede maat (35 breed, 8 hoog; 15 of 14 diep), met de doorgang van de
     * hoofdgang open aan de kant waar de volgende zaal komt; de reactor heeft zijn stroomkast en generatoren, de kluis
     * haar laserwanden en kisten, en elke zaal minstens één soldaat.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "rooms")
    public static void bunker_halls_follow_the_rules_and_exist(GameTestHelper helper) {
        Random random = new Random(42);
        Set<String> seen = new HashSet<>();
        Set<Integer> counts = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            List<String> layout = BunkerLayout.random(random);
            check(BunkerLayout.valid(layout), "Een bunker die niet klopt: " + layout);
            seen.addAll(layout);
            counts.add(layout.size() - 2);
        }
        check(seen.containsAll(BunkerLayout.HALLS) && counts.size() == BunkerLayout.MAX_HALLS - BunkerLayout.MIN_HALLS + 1,
                "Over 200 bunkers zouden alle zalen en alle aantallen voorbij moeten komen: " + seen + " " + counts);
        check(BunkerLayout.random(random, 3).size() == 5 && BunkerLayout.random(random, 6).size() == 8, "Het aantal zalen zou te kiezen moeten zijn");
        check(BunkerLayout.depth(List.of("entrance", "a", "b", "c", "vault")) == 15 + 3 * 14 + 15, "De diepte zou de som van de stukken moeten zijn");
        List<String> all = new ArrayList<>(BunkerLayout.HALLS);
        all.add(BunkerLayout.REACTOR);
        for (String hall : all) {
            StructureTemplate piece = template(helper, BiebiaBunkerPiece.template(hall));
            check(piece.getSize().equals(new Vec3i(35, 8, BunkerLayout.HALL_DEPTH)), hall + " heeft niet de maat van een zaal: " + piece.getSize());
            check(has(piece, Blocks.AIR, new BlockPos(17, 2, 0)) && has(piece, Blocks.AIR, new BlockPos(17, 2, 13)),
                    hall + " zou aan beide kanten open moeten zijn voor de hoofdgang");
            check(contains(piece, Blocks.STRUCTURE_BLOCK), hall + " heeft geen soldaat");
        }
        StructureTemplate entrance = template(helper, BiebiaBunkerPiece.template(BunkerLayout.ENTRANCE));
        check(entrance.getSize().equals(new Vec3i(35, 8, BunkerLayout.END_DEPTH)) && has(entrance, Blocks.AIR, new BlockPos(17, 2, 14)),
                "De ingang heeft niet de goede maat, of is achteraan dicht");
        StructureTemplate reactor = template(helper, BiebiaBunkerPiece.template(BunkerLayout.REACTOR));
        check(contains(reactor, com.senne.imttech.registry.ModBlocks.POWER_CABINET.get())
                && contains(reactor, com.senne.imttech.registry.ModBlocks.HEAT_GENERATOR.get()), "De reactor mist zijn stroomkast of generatoren");
        StructureTemplate vault = template(helper, BiebiaBunkerPiece.template(BunkerLayout.VAULT));
        check(vault.getSize().equals(new Vec3i(35, 8, BunkerLayout.END_DEPTH)) && has(vault, Blocks.AIR, new BlockPos(17, 2, 0))
                && contains(vault, com.senne.imttech.registry.ModBlocks.LASER_EMITTER.get()) && contains(vault, Blocks.CHEST),
                "De kluis heeft niet de goede maat, is vooraan dicht, of mist de lasers of de kisten");
        helper.succeed();
    }
}
