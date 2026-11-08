package com.senne.imttech.howto.scenes;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.station.HeatGeneratorBlock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
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
        String capacity = String.format(Locale.ROOT, "%,d", Math.round(ImtConfig.Power.BATTERY_CELL_CAPACITY));
        String rate = String.format(Locale.ROOT, "%,d", Math.round(ImtConfig.Power.BATTERY_CHARGE_RATE));

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
}
