package com.senne.imttech.howto.scenes;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.logic.LogicStats;
import com.senne.imttech.logic.RackBay;
import com.senne.imttech.logic.ServerRackBlock;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * De animaties van het tabblad Datacenter (hoofdstuk 63): serverracks, en warmte met fans (LEESMIJ "Het datacenter").
 * Sinds hoofdstuk 64 klik je de onderdelen in de vakjes op de voorkant (geen scherm meer), geef je een rack een naam, en
 * zie je zijn cijfers in de app Monitor.
 */
public final class DatacenterScenes {
    private DatacenterScenes() {}

    private static final BlockPos CORE = new BlockPos(1, 1, 2);
    private static final BlockPos GEN = new BlockPos(0, 1, 2);
    private static final BlockPos PANEL = new BlockPos(4, 1, 2);

    static BlockState rack(boolean online) {
        return ModBlocks.SERVER_RACK.get().defaultBlockState().setValue(ServerRackBlock.FACING, Direction.SOUTH)
                .setValue(ServerRackBlock.ONLINE, online);
    }

    /** Een rack met deze onderdelen in zijn vakjes (van vakje 0 af; null = leeg). */
    static BlockState rack(boolean online, RackBay... bays) {
        BlockState state = rack(online);
        for (int slot = 0; slot < bays.length && slot < ServerRackBlock.BAYS.size(); slot++) {
            if (bays[slot] != null) {
                state = state.setValue(ServerRackBlock.BAYS.get(slot), bays[slot]);
            }
        }
        return state;
    }

    /** Een vol rack: twee CPU's, RAM en een SSD, en vier fans. */
    private static BlockState fullRack(boolean online) {
        return rack(online, RackBay.CPU, RackBay.CPU, RackBay.RAM, RackBay.SSD, RackBay.FAN, RackBay.FAN, RackBay.FAN, RackBay.FAN);
    }

    /**
     * Waar het vakje {@code slot} op de voorkant van een rack ligt dat naar het zuiden kijkt (zie RackBay: links de even
     * vakjes, rechts de oneven; bovenaan CPU/GPU, dan RAM/SSD, onderaan twee rijen fans).
     */
    static Vec3 bay(BlockPos rack, int slot) {
        double across = (slot % 2 == 0 ? 5.0 : 11.0) / 16.0;
        int[] pixels = RackBay.ROW_PIXELS[RackBay.row(slot)];
        double fromTop = (pixels[0] + pixels[1]) / 2.0 / 16.0;
        return new Vec3(rack.getX() + across, rack.getY() + 1.0 - fromTop, rack.getZ() + 1.02);
    }

    /** Vier racks: twee naast de kern en twee erbovenop (racks naast of op elkaar zijn één server). */
    private static Map<BlockPos, BlockState> racks(boolean online) {
        Map<BlockPos, BlockState> racks = new LinkedHashMap<>();
        racks.put(new BlockPos(2, 1, 2), rack(online));
        racks.put(new BlockPos(3, 1, 2), rack(online));
        racks.put(new BlockPos(2, 2, 2), rack(online));
        racks.put(new BlockPos(3, 2, 2), rack(online));
        return racks;
    }

    private static void base(HowToSceneBuilder s) {
        s.place(CORE, SecurityScenes.core(Direction.SOUTH, true));
        s.idle(4);
        s.place(GEN, SecurityScenes.heat(Direction.WEST));
        s.idle(4);
        s.place(PANEL, ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        s.idle(6);
    }

    /**
     * Serverracks: samen één server tegen je kern, onderdelen in de vakjes klikken (CPU, GPU, RAM, SSD, fans), hoe groot een
     * datacenter moet zijn (30 en 120 CPU's, 64 GB en 3 TB per 10), een naam en de app Monitor, en dan "online".
     */
    public static List<HowToScene> racks() {
        HowToSceneBuilder s = new HowToSceneBuilder("server_racks", "Server racks").plate(6, 5);
        s.idle(8);
        s.chapter("One server");
        base(s);
        s.layers(racks(false), 8);
        s.text(90, HowToSceneBuilder.top(new BlockPos(3, 2, 2)), Side.RIGHT,
                "*Server Racks* next to or on top of each other are one server: your *datacenter*. One rack against your"
                        + " *Security Core* (or a network cable) links it. Your diagrams and I.M.T's brain run on it.");
        s.idle(95);

        s.chapter("Its bays");
        BlockPos first = new BlockPos(2, 1, 2);
        RackBay[] bays = new RackBay[ImtConfig.Logic.RACK_SLOTS];
        Object[][] clicks = {
                {0, ModItems.SERVER_CPU.get(), RackBay.CPU}, {1, ModItems.SERVER_GPU.get(), RackBay.GPU},
                {2, ModItems.SERVER_RAM.get(), RackBay.RAM}, {3, ModItems.SERVER_SSD.get(), RackBay.SSD},
                {4, ModItems.SERVER_FAN.get(), RackBay.FAN}, {5, ModItems.SERVER_FAN.get(), RackBay.FAN}};
        s.text(150, HowToSceneBuilder.face(first, Direction.SOUTH), Side.RIGHT,
                "Click parts straight into the *bays* on the front, like books in a bookshelf: on top a *CPU* or *GPU*, then"
                        + " *RAM* or an *SSD*, at the bottom up to four *Server Fans*. Click a full bay to take its part out.");
        for (Object[] click : clicks) {
            int slot = (Integer) click[0];
            s.rightClick(bay(first, slot), new ItemStack((Item) click[1]));
            s.idle(8);
            bays[slot] = (RackBay) click[2];
            s.change(first, rack(false, bays));
            s.idle(14);
        }
        s.idle(20);

        s.chapter("Bigger is faster");
        Map<BlockPos, BlockState> wall = HowToSceneBuilder.box(new BlockPos(0, 1, 4), new BlockPos(5, 3, 4), pos -> fullRack(true));
        s.layers(wall, 10);
        s.meter(90, HowToSceneBuilder.top(new BlockPos(5, 3, 4)).add(0, 0.6, 0), "DIAGRAMS", 1.0f, 0.0f,
                HowToSceneBuilder.GREEN, Math.round(ImtConfig.Logic.LOGIC_MAX_DELAY_TICKS / 20.0f), " s");
        s.text(140, HowToSceneBuilder.top(new BlockPos(3, 3, 4)), Side.RIGHT,
                "Every CPU makes your server a bit faster. From *%s CPUs* (or %s GPUs) your diagrams react at once; I.M.T"
                        + " needs *%s CPUs* (or %s GPUs). Every 10 CPUs or GPUs need *%s RAM* and *%s SSD*, or everything runs"
                        + " slower.",
                number(ImtConfig.Logic.LOGIC_INSTANT_POWER), number(ImtConfig.Logic.LOGIC_INSTANT_POWER / ImtConfig.Logic.GPU_LOGIC_POWER),
                number(ImtConfig.Logic.BRAIN_INSTANT_POWER), number(ImtConfig.Logic.BRAIN_INSTANT_POWER / ImtConfig.Logic.GPU_BRAIN_POWER),
                LogicStats.bytes(LogicStats.ramNeededMb(10)), LogicStats.bytes(LogicStats.ssdNeededMb(10)));
        s.idle(145);
        s.remove(wall.keySet().toArray(new BlockPos[0]));
        s.idle(10);

        s.chapter("Its name");
        s.sneakRightClick(HowToSceneBuilder.face(first, Direction.SOUTH), ItemStack.EMPTY);
        s.idle(10);
        s.text(80, HowToSceneBuilder.face(first, Direction.SOUTH), Side.RIGHT,
                "[Sneak] + right-click with an empty hand: give the rack a *name*, like *rack1*.");
        s.idle(85);
        s.picture(140, "monitor_rack", 426, 240,
                s.arrow(0.17f, 0.46f, "Your racks by *name*: click one"),
                s.arrow(0.66f, 0.56f, "Its own stats: bays, heat, fans"),
                s.arrow(0.53f, 0.26f, "*RAM* and *SSD*: what you have and need"));
        s.idle(145);

        s.chapter("Online");
        Map<BlockPos, BlockState> online = racks(true);
        online.put(first, rack(true, bays));
        online.forEach(s::change);
        s.outline(80, new BlockPos(2, 1, 2), new BlockPos(3, 2, 2), HowToSceneBuilder.GREEN);
        s.text(90, HowToSceneBuilder.top(new BlockPos(3, 2, 2)), Side.RIGHT,
                "With power, computing (a CPU or GPU), RAM and an SSD its lights blink and its fans spin. Now your readers,"
                        + " panels and diagrams work.");
        s.idle(95);
        return List.of(s.build());
    }

    /** Warmte: zonder fan wordt een rack steeds warmer, een fan koelt alleen zijn eigen rack, en te warm brandt het af. */
    public static List<HowToScene> heat() {
        HowToSceneBuilder s = new HowToSceneBuilder("datacenter_heat", "Heat and fans").plate(6, 5);
        BlockPos hot = new BlockPos(2, 1, 2);
        Vec3 above = HowToSceneBuilder.top(new BlockPos(2, 2, 2)).add(0, 0.5, 0);
        s.idle(8);
        s.chapter("It gets warm");
        base(s);
        Map<BlockPos, BlockState> racks = new LinkedHashMap<>();
        racks.put(hot, rack(true, RackBay.GPU, RackBay.GPU, RackBay.RAM, RackBay.SSD));
        racks.put(new BlockPos(3, 1, 2), fullRack(true));
        racks.put(new BlockPos(2, 2, 2), fullRack(true));
        racks.put(new BlockPos(3, 2, 2), fullRack(true));
        s.place(racks, HowToSceneBuilder.DROP);
        s.idle(10);
        s.meter(110, above, "RACK TEMP", 0.25f, 0.85f, HowToSceneBuilder.RED, 120, "°");
        s.text(95, HowToSceneBuilder.face(hot, Direction.SOUTH), Side.RIGHT,
                "Every part makes heat in its own rack. Without a *Server Fan* the heat can't get out: the rack keeps getting"
                        + " warmer.");
        s.idle(115);

        s.chapter("Fans");
        s.rightClick(bay(hot, 4), new ItemStack(ModItems.SERVER_FAN.get()));
        s.idle(8);
        s.change(hot, rack(true, RackBay.GPU, RackBay.GPU, RackBay.RAM, RackBay.SSD, RackBay.FAN));
        s.idle(8);
        s.rightClick(bay(hot, 5), new ItemStack(ModItems.SERVER_FAN.get()));
        s.idle(8);
        s.change(hot, rack(true, RackBay.GPU, RackBay.GPU, RackBay.RAM, RackBay.SSD, RackBay.FAN, RackBay.FAN));
        s.meter(100, above, "RACK TEMP", 0.85f, 0.25f, HowToSceneBuilder.GREEN, 120, "°");
        s.text(90, HowToSceneBuilder.face(hot, Direction.SOUTH), Side.RIGHT,
                "A fan only cools its own rack: every rack with something in it needs at least one, and a rack holds four.");
        s.idle(105);

        s.chapter("Too hot");
        BlockPos burning = new BlockPos(3, 3, 2);
        s.place(burning, Blocks.FIRE.defaultBlockState());
        s.outline(90, new BlockPos(3, 1, 2), new BlockPos(3, 2, 2), HowToSceneBuilder.RED);
        s.text(100, HowToSceneBuilder.top(burning), Side.RIGHT,
                "Over %s degrees the whole server runs at half speed. Over %s parts wear out. Over %s for %s seconds the"
                        + " rack burns, with everything in it.",
                number(ImtConfig.Logic.WARM_TEMPERATURE), number(ImtConfig.Logic.HOT_TEMPERATURE),
                number(ImtConfig.Logic.FIRE_TEMPERATURE), number(ImtConfig.Logic.FIRE_DELAY_TICKS / 20.0));
        s.idle(105);
        s.clear(burning);
        s.idle(10);
        return List.of(s.build());
    }

    /** 30.0 wordt "30", 2.5 blijft "2.5". */
    private static String number(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }
}
