package com.senne.imttech.howto.scenes;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.lan.NetworkSwitchBlock;
import com.senne.imttech.logic.InputLight;
import com.senne.imttech.security.CameraBlock;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.station.HeatGeneratorBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * De animaties van het tabblad Power (hoofdstuk 63). Als eerste de Power Battery, jouw voorbeeld: "casings om een
 * multiblock batterij te maken".
 */
public final class PowerScenes {
    private PowerScenes() {}

    /** De doos van de batterij: 5 x 4 x 5, met de westkant op x = 3 en de zuidkant (met het raam) op z = 6. */
    public static final BlockPos BOX_MIN = new BlockPos(3, 1, 2);
    public static final BlockPos BOX_MAX = new BlockPos(7, 4, 6);
    /** De poort in het midden van de westwand, waar de kabel aankomt. */
    public static final BlockPos PORT = new BlockPos(3, 2, 4);
    /** Het blok in het dak dat er even uit gaat ("houd de doos dicht"). */
    public static final BlockPos ROOF_HOLE = new BlockPos(5, 4, 4);

    /**
     * Een Power Battery bouwen, laden en aflezen (LEESMIJ, "De Power Battery"). Zes hoofdstukjes: de doos van casing (met
     * een raam van glas), de cellen erin, de poort, een kabel van de generatoren, het scherm, en een gat in de wand.
     */
    public static List<HowToScene> battery() {
        return List.of(batteryScript().build());
    }

    /** Alleen voor de test: alle blokken zoals ze aan het eind van de animatie van de batterij staan. */
    public static Map<BlockPos, BlockState> batteryEnd() {
        return batteryScript().world();
    }

    private static HowToSceneBuilder batteryScript() {
        BlockState casing = ModBlocks.BATTERY_CASING.get().defaultBlockState();
        BlockState glass = ModBlocks.BATTERY_GLASS.get().defaultBlockState();
        BlockState cell = ModBlocks.BATTERY_CELL.get().defaultBlockState();
        BlockState port = ModBlocks.BATTERY_PORT.get().defaultBlockState();
        int max = ImtConfig.Power.BATTERY_MAX_SIZE;
        // Zoals jij grote getallen wilt zien (hoofdstuk 66).
        String capacity = com.senne.imttech.config.Numbers.amount(Math.round(ImtConfig.Power.BATTERY_CELL_CAPACITY));
        String rate = com.senne.imttech.config.Numbers.amount(Math.round(ImtConfig.Power.BATTERY_CHARGE_RATE));

        HowToSceneBuilder s = new HowToSceneBuilder("power_battery", "Building a Power Battery").plate(9, 9);
        s.idle(12);

        // --- 1. De doos: de vloer, dan twee lagen wand (met een raam van glas in de zuidwand).
        s.chapter("A box of Battery Casing");
        s.place(HowToSceneBuilder.box(BOX_MIN, new BlockPos(7, 1, 6), pos -> casing), HowToSceneBuilder.DROP);
        s.idle(8);
        for (int y = 2; y <= 3; y++) {
            final int layer = y;
            s.place(HowToSceneBuilder.box(new BlockPos(3, layer, 2), new BlockPos(7, layer, 6), pos -> {
                boolean wall = pos.getX() == 3 || pos.getX() == 7 || pos.getZ() == 2 || pos.getZ() == 6;
                if (!wall) {
                    return null;
                }
                return pos.getZ() == 6 && pos.getX() >= 4 && pos.getX() <= 6 ? glass : casing;
            }), HowToSceneBuilder.DROP);
            s.idle(8);
        }
        s.text(75, HowToSceneBuilder.top(new BlockPos(5, 3, 2)), Side.RIGHT,
                "Build a closed box of *Battery Casing*. Anything from 3 x 3 x 3 up to %s x %s x %s blocks works.", max, max, max);
        s.idle(80);
        s.outline(75, new BlockPos(4, 2, 6), new BlockPos(6, 3, 6), HowToSceneBuilder.CYAN);
        s.text(75, new Vec3(5.5, 2.0, 7.0), Side.LEFT,
                "Wall blocks may also be *Battery Glass*, so you can look inside. Glass next to glass becomes one big window.");
        s.idle(80);

        // --- 2. De cellen erin, dan het dak erop.
        s.chapter("Battery Cells inside");
        s.layers(HowToSceneBuilder.box(new BlockPos(4, 2, 3), new BlockPos(6, 3, 5), pos -> cell), 8);
        s.text(80, HowToSceneBuilder.top(new BlockPos(5, 3, 4)), Side.RIGHT,
                "Fill the inside with *Battery Cells*: each one stores %s FE. Empty spots (air) are fine too.", capacity);
        s.idle(85);
        s.place(HowToSceneBuilder.box(new BlockPos(3, 4, 2), BOX_MAX, pos -> casing), HowToSceneBuilder.DROP);
        s.idle(14);

        // --- 3. Een poort in de westwand.
        s.chapter("A Battery Port");
        Vec3 portFace = HowToSceneBuilder.face(PORT, Direction.WEST);
        s.rightClick(portFace, new ItemStack(ModItems.BATTERY_PORT.get()));
        s.idle(10);
        s.replace(PORT, port);
        s.outline(60, PORT, PORT, HowToSceneBuilder.CYAN);
        s.text(75, portFace, Side.LEFT,
                "Put at least one *Battery Port* in the wall: that is where cables connect. More ports share the same power.");
        s.idle(80);

        // --- 4. Generatoren en een kabel naar de poort.
        s.chapter("Charging it");
        s.place(new BlockPos(0, 1, 3), ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        s.idle(5);
        s.place(new BlockPos(0, 1, 5), ModBlocks.HEAT_GENERATOR.get().defaultBlockState()
                .setValue(HeatGeneratorBlock.FACING, Direction.WEST).setValue(HeatGeneratorBlock.LIT, true));
        s.idle(14);
        // De kabel ligt op de vloer (net boven de plaat), langs de wand van de doos omhoog, en de poort in (zie CableGeometry).
        Vec3[] main = {new Vec3(1.0, 1.05, 5.5), new Vec3(1.5, 1.05, 5.5), new Vec3(1.5, 1.05, 4.5),
                new Vec3(2.95, 1.05, 4.5), new Vec3(2.95, 2.5, 4.5), new Vec3(3.0, 2.5, 4.5)};
        s.holdRight(36, new ItemStack(ModItems.CABLE.get()), main);
        s.cable(CableKind.POWER, 36, main);
        s.idle(36);
        s.cable(CableKind.POWER, 10, new Vec3(1.0, 1.05, 3.5), new Vec3(1.5, 1.05, 3.5), new Vec3(1.5, 1.05, 4.5));
        s.idle(12);
        s.meter(95, new Vec3(5.5, 5.2, 4.5), "STORED", 0.0f, 0.4f, HowToSceneBuilder.ORANGE);
        s.text(90, new Vec3(2.95, 1.6, 4.5), Side.RIGHT,
                "Hold [Right Button] with a *Power Cable* to draw it from your generators to the port. The battery charges"
                        + " with what they have to spare, up to %s FE per tick.", rate);
        s.idle(100);

        // --- 5. Het scherm: een klik op de poort, en het plaatje van het echte scherm.
        s.chapter("The battery screen");
        s.text(40, portFace, Side.LEFT, "Right-click any block of the battery to open its screen.");
        s.rightClick(portFace, ItemStack.EMPTY);
        s.idle(30);
        s.picture(140, "battery", 300, 228,
                s.arrow(0.55f, 0.125f, "STORED: how full the battery is"),
                s.arrow(0.14f, 0.26f, "What your whole grid makes, uses and misses"),
                s.arrow(0.77f, 0.37f, "The last 5 minutes"),
                s.arrow(0.20f, 0.61f, "Your generators and devices"));
        s.idle(145);

        // --- 6. Een gat in de wand: geen batterij meer tot hij weer dicht is.
        s.chapter("Keep it closed");
        s.remove(ROOF_HOLE);
        s.outline(80, ROOF_HOLE, ROOF_HOLE, HowToSceneBuilder.RED);
        s.text(80, HowToSceneBuilder.top(ROOF_HOLE), Side.RIGHT,
                "Break a wall block and it stops being a battery until you close it again. The power waits in the port.");
        s.idle(85);
        s.place(ROOF_HOLE, casing);
        s.idle(8);
        s.outline(35, BOX_MIN, BOX_MAX, HowToSceneBuilder.GREEN);
        s.idle(35);
        return s;
    }

    // ================================================================ Stroom maken

    /** Gele streepjes: stroom op weg naar een apparaat (zoals G > Information > Power flow). */
    private static final int POWER_YELLOW = 0xFFD84A;

    static BlockState station(Direction facing) {
        return ModBlocks.CHARGING_STATION.get().defaultBlockState()
                .setValue(com.senne.imttech.station.ChargingStationBlock.FACING, facing);
    }

    static BlockState heat(Direction facing) {
        return ModBlocks.HEAT_GENERATOR.get().defaultBlockState().setValue(HeatGeneratorBlock.FACING, facing)
                .setValue(HeatGeneratorBlock.LIT, true);
    }

    /**
     * Stroom maken: het zonnepaneel (alleen overdag, onder open hemel), de warmtegenerator op brandstof of op warmte van
     * magma, lava of vuur, en dat een apparaat dat ertegen staat geen kabel nodig heeft.
     */
    public static List<HowToScene> makingPower() {
        String solar = com.senne.imttech.config.Numbers.amount(Math.round(ImtConfig.Power.SOLAR_RATE));
        String fuel = com.senne.imttech.config.Numbers.amount(Math.round(ImtConfig.Power.HEAT_RATE_FUEL));
        HowToSceneBuilder s = new HowToSceneBuilder("making_power", "Making power").plate(7, 5);
        s.idle(8);
        s.chapter("Solar panels");
        BlockPos panelA = new BlockPos(1, 1, 1);
        BlockPos panelB = new BlockPos(2, 1, 1);
        s.place(panelA, ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        s.idle(6);
        s.place(panelB, ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        s.idle(8);
        s.text(80, HowToSceneBuilder.top(panelB), Side.RIGHT,
                "A *Solar Panel* makes %s FE per tick in full sun: only by day, under open sky.", solar);
        s.idle(85);

        s.chapter("Heat generators");
        BlockPos burner = new BlockPos(4, 1, 1);
        s.place(burner, ModBlocks.HEAT_GENERATOR.get().defaultBlockState().setValue(HeatGeneratorBlock.FACING, Direction.SOUTH));
        s.idle(10);
        Vec3 grill = HowToSceneBuilder.face(burner, Direction.SOUTH);
        s.rightClick(grill, new ItemStack(Items.COAL));
        s.idle(14);
        s.change(burner, heat(Direction.SOUTH));
        s.text(80, grill, Side.RIGHT, "A *Heat Generator* burns fuel (right-click it): %s FE per tick. Its grill glows while"
                + " it works.", fuel);
        s.idle(85);
        BlockPos warm = new BlockPos(5, 1, 3);
        s.place(warm, ModBlocks.HEAT_GENERATOR.get().defaultBlockState().setValue(HeatGeneratorBlock.FACING, Direction.SOUTH));
        s.idle(8);
        Map<BlockPos, BlockState> magma = new LinkedHashMap<>();
        magma.put(new BlockPos(4, 1, 3), Blocks.MAGMA_BLOCK.defaultBlockState());
        magma.put(new BlockPos(6, 1, 3), Blocks.MAGMA_BLOCK.defaultBlockState());
        magma.put(new BlockPos(5, 1, 2), Blocks.MAGMA_BLOCK.defaultBlockState());
        s.place(magma, HowToSceneBuilder.DROP);
        s.idle(12);
        s.change(warm, heat(Direction.SOUTH));
        s.text(85, HowToSceneBuilder.face(warm, Direction.SOUTH), Side.RIGHT,
                "Or no fuel at all: it takes free heat from *lava*, *magma* or *fire* around it. The more, the better.");
        s.idle(90);

        s.chapter("Using it");
        BlockPos user = new BlockPos(3, 1, 1);
        s.place(user, station(Direction.SOUTH));
        s.idle(8);
        s.outline(80, user, user, HowToSceneBuilder.CYAN);
        s.text(85, HowToSceneBuilder.face(user, Direction.SOUTH), Side.RIGHT,
                "A machine right against a generator needs no cable. Further away: draw a *Power Cable* to it.");
        s.idle(90);
        return List.of(s.build());
    }

    // ================================================================ Stroomkabels

    /**
     * Stroomkabels: je tekent ze (rechtermuisknop ingedrukt), stroom loopt erover als gele streepjes, je bouwt eroverheen
     * om ze weg te werken, en sneak + rechtsklik raapt ze op (een schaar knipt ze door).
     */
    public static List<HowToScene> cables() {
        HowToSceneBuilder s = new HowToSceneBuilder("power_cables", "Power cables").plate(8, 5);
        BlockPos panel = new BlockPos(0, 1, 2);
        BlockPos user = new BlockPos(6, 1, 2);
        Vec3[] path = {new Vec3(1.0, 1.05, 2.5), new Vec3(3.5, 1.05, 2.5), new Vec3(3.5, 1.05, 3.5), new Vec3(5.5, 1.05, 3.5),
                new Vec3(5.5, 1.05, 2.5), new Vec3(6.0, 1.05, 2.5)};
        s.idle(8);
        s.chapter("Draw it");
        s.place(panel, ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        s.idle(5);
        s.place(user, station(Direction.SOUTH));
        s.idle(12);
        s.holdRight(50, new ItemStack(ModItems.CABLE.get()), path);
        s.cable(CableKind.POWER, 50, path);
        s.text(95, new Vec3(3.5, 1.05, 3.5), Side.RIGHT,
                "Cables are not blocks: you *draw* them. Hold [Right Button] with a *Power Cable* and look where it should go.");
        s.idle(100);

        s.chapter("Power flows");
        for (int i = 0; i < 3; i++) {
            s.signal(30, POWER_YELLOW, path);
            s.idle(12);
        }
        s.text(80, new Vec3(5.5, 1.05, 3.5), Side.RIGHT,
                "Yellow stripes show power on its way to a machine (G > Information > Power flow).");
        for (int i = 0; i < 4; i++) {
            s.signal(30, POWER_YELLOW, path);
            s.idle(12);
        }
        s.idle(30);

        s.chapter("Hide it");
        Map<BlockPos, BlockState> floor = new LinkedHashMap<>();
        for (BlockPos pos : new BlockPos[] {new BlockPos(1, 1, 2), new BlockPos(2, 1, 2), new BlockPos(3, 1, 2),
                new BlockPos(3, 1, 3), new BlockPos(4, 1, 3), new BlockPos(5, 1, 3), new BlockPos(5, 1, 2)}) {
            floor.put(pos, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
        }
        s.place(floor, HowToSceneBuilder.DROP);
        s.idle(16);
        s.text(90, new Vec3(3.5, 1.5, 3.5), Side.RIGHT,
                "Build over a cable to hide it: it runs on inside. With your glasses on you see hidden cables through walls.");
        s.idle(95);

        s.chapter("Pick it up");
        s.clear(floor.keySet().toArray(new BlockPos[0]));
        s.idle(8);
        s.sneakRightClick(new Vec3(3.5, 1.1, 3.0), new ItemStack(ModItems.CABLE.get()));
        s.text(80, new Vec3(3.5, 1.05, 3.0), Side.RIGHT,
                "Sneak + right-click a cable with a cable in your hand to pick it up. *Shears* cut it in two.");
        s.idle(85);
        return List.of(s.build());
    }

    // ================================================================ Netwerkkabels en PoE

    /**
     * Netwerkkabels: wit van de kern naar een apparaat; PoE (groen) brengt ook stroom; signalen lopen over de kabel; en de
     * switch, waar kabels samenkomen en je elke poort aan of uit zet.
     */
    public static List<HowToScene> network() {
        HowToSceneBuilder s = new HowToSceneBuilder("network_cables", "Network cables and PoE").plate(7, 6);
        BlockPos core = SecurityScenes.CORE;
        BlockPos gen = SecurityScenes.CORE_GEN;
        BlockPos reader = SecurityScenes.CORE_READER;
        BlockPos camera = SecurityScenes.CORE_CAMERA;
        // De switch komt op de hoek van de witte kabel (die loopt er recht doorheen).
        BlockPos sw = new BlockPos(3, 1, 3);
        Vec3[] white = SecurityScenes.CORE_TO_READER;
        Vec3[] whiteBack = {white[4], white[3], white[2], white[1], white[0]};
        Vec3[] green = SecurityScenes.CORE_TO_CAMERA;
        s.idle(8);
        s.chapter("Network cable");
        s.place(core, SecurityScenes.core(Direction.SOUTH, true));
        s.idle(4);
        s.place(gen, heat(Direction.WEST));
        s.idle(6);
        s.place(SecurityScenes.coreProps(), HowToSceneBuilder.DROP);
        s.idle(6);
        s.place(reader, SecurityScenes.reader(Direction.WEST, InputLight.OFF));
        s.idle(4);
        s.place(camera, SecurityScenes.camera(Direction.SOUTH).setValue(CameraBlock.ONLINE, false));
        s.idle(10);
        s.holdRight(30, new ItemStack(ModItems.NETWORK_CABLE.get()), white);
        s.cable(CableKind.NETWORK, 30, white);
        s.idle(32);
        s.text(90, new Vec3(3.5, 1.05, 3.0), Side.RIGHT,
                "A *Network Cable* (white) links a security device to the *Security Core*. Small devices like a reader also"
                        + " need a little power.");
        s.idle(95);

        s.chapter("PoE cable");
        s.holdRight(26, new ItemStack(ModItems.POE_CABLE.get()), green);
        s.cable(CableKind.POE, 26, green);
        s.idle(28);
        s.change(camera, SecurityScenes.camera(Direction.SOUTH));
        s.text(85, new Vec3(2.5, 1.05, 1.0), Side.LEFT,
                "A *PoE Cable* (green) carries network and power at once: the core feeds it from its own power.");
        s.idle(90);

        s.chapter("Signals");
        s.signal(16, NETWORK_WHITE, whiteBack);
        s.idle(18);
        s.signal(16, NETWORK_WHITE, white);
        s.text(80, new Vec3(4.2, 1.05, 3.5), Side.RIGHT,
                "A card, a code or a button sends a signal over the cable to the core, and the answer comes back.");
        s.idle(20);
        s.signal(16, NETWORK_WHITE, whiteBack);
        s.idle(18);
        s.signal(16, NETWORK_WHITE, white);
        s.idle(45);

        s.chapter("Network Switch");
        s.place(sw, ModBlocks.NETWORK_SWITCH.get().defaultBlockState().setValue(NetworkSwitchBlock.FACING, Direction.WEST)
                .setValue(NetworkSwitchBlock.ONLINE, true));
        s.idle(8);
        s.outline(80, sw, sw, HowToSceneBuilder.CYAN);
        s.text(85, HowToSceneBuilder.face(sw, Direction.WEST), Side.LEFT,
                "A *Network Switch* joins cables from all six sides. Behind its little door you turn each port on or off.");
        s.idle(90);
        return List.of(s.build());
    }

    /** Een helder vakje over een netwerkkabel (wit), zoals G > Information > Cable signals. */
    private static final int NETWORK_WHITE = 0xF2F6FA;

    // ================================================================ De stroomkast

    static BlockState cabinet(boolean west, boolean east) {
        return ModBlocks.POWER_CABINET.get().defaultBlockState()
                .setValue(com.senne.imttech.energy.PowerCabinetBlock.FACING, Direction.SOUTH)
                .setValue(com.senne.imttech.energy.PowerCabinetBlock.SWITCHES.get(Direction.WEST), west)
                .setValue(com.senne.imttech.energy.PowerCabinetBlock.SWITCHES.get(Direction.EAST), east);
    }

    static BlockState arcLamp(boolean lit) {
        return ModBlocks.ARC_LAMP.get().defaultBlockState().setValue(com.senne.imttech.light.ArcLampBlock.LIT, lit);
    }

    /**
     * De stroomkast (LEESMIJ, "De stroomkast"): kabels van alle kanten komen er samen, achter het deurtje een schakelaar per
     * kant (als plaatje), een kant uit (het torentje laat zijn loop hangen, de lamp gaat uit), en schakelen op afstand.
     */
    public static List<HowToScene> cabinet() {
        HowToSceneBuilder s = new HowToSceneBuilder("power_cabinet", "The power cabinet").plate(7, 5).height(4);
        // De ballonnen hangen boven de blokken: het torentje, de kast en de lamp staan over de hele breedte, dus een
        // ballon op de hoogte van de blokken stond altijd voor een van de drie.
        // Alles staat vooraan op de plaat (z = 3): dan hangen de ballonnen erboven in de lucht.
        Vec3 above = new Vec3(3.5, 3.7, 3.5);
        BlockPos box = new BlockPos(3, 1, 3);
        BlockPos gen = new BlockPos(3, 1, 2);
        BlockPos turret = new BlockPos(0, 1, 3);
        BlockPos lamp = new BlockPos(6, 1, 3);
        Vec3[] toTurret = {new Vec3(3.0, 1.05, 3.5), new Vec3(1.0, 1.05, 3.5)};
        Vec3[] toLamp = {new Vec3(4.0, 1.05, 3.5), new Vec3(6.0, 1.05, 3.5)};
        net.minecraft.world.level.block.Block laserTurret = ModBlocks.SECURITY_TURRET.get();
        s.idle(6);
        s.chapter("Where cables meet");
        s.place(box, cabinet(true, true));
        s.idle(6);
        s.place(gen, heat(Direction.SOUTH));
        s.idle(6);
        s.place(turret, DefenceScenes.turret(laserTurret, false, true));
        s.idle(4);
        s.place(lamp, arcLamp(false));
        s.idle(10);
        s.cable(CableKind.POWER, 14, toTurret);
        s.cable(CableKind.POWER, 14, toLamp);
        s.idle(16);
        s.change(turret, DefenceScenes.turret(laserTurret, true, true));
        s.change(lamp, arcLamp(true));
        s.text(100, above, Side.RIGHT,
                "A *Power Cabinet* is where cables from all sides meet: here the generator at the back, a turret on one side"
                        + " and a lamp on the other. All sides that are on hang together.");
        for (int i = 0; i < 3; i++) {
            s.signal(30, POWER_YELLOW, toTurret);
            s.signal(30, POWER_YELLOW, toLamp);
            s.idle(30);
        }
        s.idle(15);

        s.chapter("A switch per side");
        s.rightClick(HowToSceneBuilder.face(box, Direction.SOUTH), ItemStack.EMPTY);
        s.idle(12);
        s.text(60, above, Side.RIGHT,
                "Right-click opens its little door. Anyone standing in front of it can switch.");
        s.idle(65);
        s.picture(150, "cabinet_screen", 426, 240,
                s.arrow(0.303f, 0.228f, "Front, back, left, right, top and bottom"),
                s.arrow(0.244f, 0.503f, "Red light: that side is off"),
                s.arrow(0.526f, 0.41f, "*wired*: something hangs on that side"),
                s.arrow(0.698f, 0.503f, "The switch"),
                s.arrow(0.382f, 0.833f, "*Link to I.M.T* (only the owner)"));
        s.idle(155);

        s.chapter("A side off");
        s.change(box, cabinet(false, true));
        s.sound(net.minecraft.sounds.SoundEvents.LEVER_CLICK, 0.4f, 0.8f);
        s.change(turret, DefenceScenes.turret(laserTurret, false, true));
        s.outline(60, turret, turret, HowToSceneBuilder.RED);
        s.text(100, HowToSceneBuilder.top(turret).add(0, 1.7, 0), Side.RIGHT,
                "Switch a side off, and what hangs there gets no more power from the cabinet: the turret lets its barrel"
                        + " drop. The lamp on the other side stays on.");
        for (int i = 0; i < 3; i++) {
            s.signal(30, POWER_YELLOW, toLamp);
            s.idle(30);
        }
        s.idle(15);

        s.chapter("From anywhere");
        s.change(box, cabinet(true, false));
        s.sound(net.minecraft.sounds.SoundEvents.LEVER_CLICK, 0.4f, 0.8f);
        s.change(turret, DefenceScenes.turret(laserTurret, true, true));
        s.change(lamp, arcLamp(false));
        s.text(115, above, Side.RIGHT,
                "*Link to I.M.T* puts the six switches in your redstone list ([']): switch them from anywhere, or say \"turn"
                        + " off cabinet 1 left\". Towers and bunkers have one in a well-guarded spot.");
        s.idle(125);
        return List.of(s.build());
    }
}
