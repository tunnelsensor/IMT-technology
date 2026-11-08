package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.battery.BatteryStructure;
import com.senne.imttech.bot.BotBoard;
import com.senne.imttech.bot.BotEntity;
import com.senne.imttech.howto.HowToCue;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToTopic;
import com.senne.imttech.howto.HowToTopics;
import com.senne.imttech.howto.scenes.PowerScenes;
import com.senne.imttech.registry.ModCreativeTabs;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.robot.GuardRobotEntity;
import com.senne.imttech.robot.RobotState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Tests voor "How it works" (hoofdstuk 63): elk item heeft een onderwerp (of gaat bewust alleen in recepten), de
 * draaiboeken kloppen, wat de animatie van de Power Battery laat bouwen is echt een batterij, en de robots in de
 * animaties laden zoals echte robots.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class HowToGameTests {
    private HowToGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * Elk item van de mod staat bij een onderwerp van How it works (klaar of nog in het plan), of in de lijst van items
     * die alleen in recepten gaan; niet allebei. Elk onderwerp heeft een uniek id, een bestaand tabblad en items van de mod.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void every_item_has_a_how_it_works(GameTestHelper helper) {
        Set<String> tabs = new HashSet<>();
        ModCreativeTabs.TABS.getEntries().forEach(tab -> tabs.add(tab.getId().getPath()));
        Set<String> ids = new HashSet<>();
        Set<Item> covered = new HashSet<>();
        for (HowToTopic topic : HowToTopics.ALL) {
            check(ids.add(topic.id()), "Het onderwerp " + topic.id() + " staat er twee keer in");
            check(tabs.contains(topic.tab()), "Het onderwerp " + topic.id() + " hoort bij een tabblad dat niet bestaat: " + topic.tab());
            List<Item> items = topic.items().get();
            check(!items.isEmpty(), "Het onderwerp " + topic.id() + " heeft geen items");
            for (Item item : items) {
                check(BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ImtTech.MODID),
                        "Het onderwerp " + topic.id() + " heeft een item dat niet van I.M.T is: " + BuiltInRegistries.ITEM.getKey(item));
            }
            covered.addAll(items);
        }
        Set<Item> craftingOnly = HowToTopics.craftingOnly();
        List<String> missing = new ArrayList<>();
        for (DeferredHolder<Item, ? extends Item> holder : ModItems.ITEMS.getEntries()) {
            Item item = holder.get();
            boolean inTopic = covered.contains(item);
            boolean recipe = craftingOnly.contains(item);
            check(!(inTopic && recipe), holder.getId() + " staat bij een onderwerp én bij de items die alleen in recepten gaan");
            if (!inTopic && !recipe) {
                missing.add(holder.getId().getPath());
            }
        }
        check(missing.isEmpty(), "Deze items hebben geen onderwerp in HowToTopics (of horen bij craftingOnly): " + missing);
        helper.succeed();
    }

    /**
     * Elk draaiboek dat klaar is, klopt: een uniek id, hoofdstukjes op volgorde vanaf tick 0, cues op volgorde en binnen de
     * lengte, teksten met Engelse tekst, blokken op of boven de grondplaat, en een lengte tussen 10 seconden en anderhalve
     * minuut. Sinds de wapens ook: zwevende voorwerpen zijn niet leeg, een bol heeft een maat, rook heeft plek om te
     * groeien, barsten lopen van 0 tot 9, en een speler draagt hooguit vijf stuks uitrusting.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void how_it_works_scenes_are_sound(GameTestHelper helper) {
        Set<String> ids = new HashSet<>();
        int ready = 0;
        for (HowToTopic topic : HowToTopics.ALL) {
            if (!topic.ready()) {
                continue;
            }
            ready++;
            List<HowToScene> scenes = topic.buildScenes();
            check(!scenes.isEmpty(), "Het onderwerp " + topic.id() + " is klaar maar heeft geen animaties");
            for (HowToScene scene : scenes) {
                String name = topic.id() + "/" + scene.id();
                check(ids.add(scene.id()), "De animatie " + scene.id() + " bestaat twee keer");
                check(scene.length() >= 200 && scene.length() <= 1800, name + " duurt " + scene.length() / 20 + " seconden");
                check(!scene.chapters().isEmpty() && scene.chapters().getFirst().tick() == 0, name + " begint niet met een hoofdstukje");
                for (int i = 1; i < scene.chapters().size(); i++) {
                    check(scene.chapters().get(i).tick() > scene.chapters().get(i - 1).tick(), name + ": de hoofdstukjes staan niet op volgorde");
                }
                int previous = 0;
                for (HowToCue cue : scene.cues()) {
                    check(cue.tick() >= previous, name + ": de cues staan niet op volgorde");
                    previous = cue.tick();
                    check(cue.tick() < scene.length(), name + ": een cue begint na het eind");
                    switch (cue) {
                        case HowToCue.Text text -> {
                            String english = text.text().getString();
                            check(!english.isBlank() && !english.startsWith("howto."), name + ": een tekst zonder Engelse tekst");
                            check(text.duration() >= 30, name + ": een tekst staat te kort in beeld (" + english + ")");
                        }
                        case HowToCue.Place place -> {
                            for (BlockPos pos : place.blocks().keySet()) {
                                check(pos.getY() >= 0 && pos.getX() >= -2 && pos.getZ() >= -2 && pos.getX() < scene.sizeX() + 2
                                        && pos.getZ() < scene.sizeZ() + 2, name + ": een blok ver naast de grondplaat: " + pos);
                            }
                        }
                        case HowToCue.Click click -> check(!click.path().isEmpty(), name + ": een klik zonder plek");
                        case HowToCue.Cable cable -> check(cable.points().size() >= 2, name + ": een kabel van minder dan twee punten");
                        case HowToCue.Picture picture -> check(!picture.arrows().isEmpty() && picture.width() > 0 && picture.height() > 0,
                                name + ": een plaatje zonder pijltjes of maat");
                        case HowToCue.Item item -> check(!item.stack().isEmpty() && item.scale() > 0, name + ": een leeg zwevend voorwerp");
                        case HowToCue.Pulse pulse -> check(pulse.radius() > 0 && pulse.duration() > 0, name + ": een bol zonder maat");
                        case HowToCue.Smoke smoke -> check(!smoke.cells().isEmpty(),
                                name + ": rook zonder plek (begint de wolk in een blok?)");
                        case HowToCue.Crack crack -> check(crack.stage() >= -1 && crack.stage() <= 9, name + ": barsten " + crack.stage());
                        case HowToCue.Actor actor -> check(actor.worn().size() <= 5, name + ": te veel uitrusting voor " + actor.key());
                        default -> {
                        }
                    }
                }
            }
        }
        check(ready > 0, "Er is nog geen enkel onderwerp klaar");
        helper.succeed();
    }

    /**
     * De robots en drones in de animaties zijn echte robots (hoofdstuk 63, de robots): elk wezen met gegevens laadt ze zoals
     * het spel een robot laadt, en dan klopt wat je ziet: zijn onderdelen, zijn printplaat, en of hij aan staat, uit staat
     * of neerligt. Zo merken we het als het opslaan van een robot ooit andere namen krijgt (dan stond de robot in de
     * animatie anders stilletjes uit, zonder modules).
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void robot_animations_load_like_real_robots(GameTestHelper helper) {
        int robots = 0;
        for (HowToTopic topic : HowToTopics.ALL) {
            if (!topic.ready()) {
                continue;
            }
            for (HowToScene scene : topic.buildScenes()) {
                for (HowToCue cue : scene.cues()) {
                    if (!(cue instanceof HowToCue.Actor actor) || actor.type() == null || actor.data().isEmpty()) {
                        continue;
                    }
                    String name = scene.id() + "/" + actor.key();
                    Entity entity = actor.type().create(helper.getLevel());
                    check(entity != null, name + ": het wezen bestaat niet");
                    entity.load(actor.data());
                    if (entity instanceof BotEntity bot) {
                        robots++;
                        BotBoard board = BotBoard.CODEC.parse(NbtOps.INSTANCE, actor.data().get("Board")).result().orElse(null);
                        check(board != null && bot.board().equals(board), name + ": de printplaat laadt niet: " + bot.board());
                        RobotState state = actor.data().contains("DownTicks") ? RobotState.DOWN
                                : actor.data().getBoolean("Active") ? RobotState.ACTIVE : RobotState.STANDBY;
                        check(bot.state() == state, name + ": zou " + state + " moeten zijn, niet " + bot.state());
                        if (bot instanceof GuardRobotEntity robot) {
                            check(robot.parts().pack() == actor.data().getInt("Parts"), name + ": de onderdelen laden niet: " + robot.parts());
                        }
                    }
                    entity.discard();
                }
            }
        }
        check(robots > 0, "Geen enkele animatie heeft een robot of drone");
        helper.succeed();
    }

    /**
     * De animatie van de Power Battery bouwt een echte batterij: zet alle blokken van het eind (zonder de grondplaat) in
     * de testwereld, en de zoektocht van de batterij vindt een doos van 5 x 4 x 5 met 18 cellen en één poort.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void battery_animation_builds_a_real_battery(GameTestHelper helper) {
        BlockPos offset = new BlockPos(4, 0, 4);
        for (Map.Entry<BlockPos, BlockState> entry : PowerScenes.batteryEnd().entrySet()) {
            if (entry.getKey().getY() > 0) {
                helper.setBlock(entry.getKey().offset(offset), entry.getValue());
            }
        }
        BlockPos port = helper.absolutePos(PowerScenes.PORT.offset(offset));
        BatteryStructure.Result result = BatteryStructure.find(helper.getLevel(), port);
        check(result.formed(), "De doos uit de animatie is geen batterij: " + result.problem());
        BatteryStructure.Structure structure = result.structure();
        check(structure.sizeX() == 5 && structure.sizeY() == 4 && structure.sizeZ() == 5, "De doos zou 5 x 4 x 5 moeten zijn: " + structure.size());
        check(structure.cells() == 18, "Er zouden 18 cellen in moeten zitten, niet " + structure.cells());
        check(structure.ports().size() == 1 && structure.ports().getFirst().equals(port), "Er zou één poort moeten zijn: " + structure.ports());
        helper.succeed();
    }
}
