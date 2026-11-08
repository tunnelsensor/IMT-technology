package com.senne.imttech.howto.scenes;

import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.logic.ServerRackBlock;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** De animaties van het tabblad Datacenter (hoofdstuk 63): serverracks, en warmte met fans (LEESMIJ "Het datacenter"). */
public final class DatacenterScenes {
    private DatacenterScenes() {}

    private static final BlockPos CORE = new BlockPos(1, 1, 2);
    private static final BlockPos GEN = new BlockPos(0, 1, 2);
    private static final BlockPos PANEL = new BlockPos(4, 1, 2);

    static BlockState rack(boolean online) {
        return ModBlocks.SERVER_RACK.get().defaultBlockState().setValue(ServerRackBlock.FACING, Direction.SOUTH)
                .setValue(ServerRackBlock.ONLINE, online);
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

    /** Serverracks: samen één server tegen je kern, de zes vakjes (CPU, GPU, RAM, SSD, fans) en dan "online". */
    public static List<HowToScene> racks() {
        HowToSceneBuilder s = new HowToSceneBuilder("server_racks", "Server racks").plate(6, 5);
        s.idle(8);
        s.chapter("One server");
        base(s);
        s.layers(racks(false), 8);
        s.text(90, HowToSceneBuilder.top(new BlockPos(3, 2, 2)), Side.RIGHT,
                "*Server Racks* next to or on top of each other are one server: your *datacenter*. One rack against your"
                        + " *Security Core* (or a network cable) links it. Your diagrams run on it.");
        s.idle(95);

        s.chapter("Its parts");
        Vec3 front = HowToSceneBuilder.face(new BlockPos(2, 1, 2), Direction.SOUTH);
        s.rightClick(front, ItemStack.EMPTY);
        s.idle(24);
        s.picture(130, "server_rack", 176, 190,
                s.arrow(0.12f, 0.11f, "*CPU* or *GPU*: how fast your server reacts"),
                s.arrow(0.25f, 0.11f, "A *Server Fan* keeps the rack cool"),
                s.arrow(0.12f, 0.21f, "*RAM*: diagrams at once"),
                s.arrow(0.12f, 0.31f, "*SSD*: room for blocks"));
        s.idle(135);

        s.chapter("Online");
        racks(true).forEach(s::change);
        s.outline(80, new BlockPos(2, 1, 2), new BlockPos(3, 2, 2), HowToSceneBuilder.GREEN);
        s.text(90, HowToSceneBuilder.top(new BlockPos(3, 2, 2)), Side.RIGHT,
                "With power, computing (a CPU or GPU), RAM and an SSD its lights blink. Now your readers, panels and"
                        + " diagrams work.");
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
        s.place(racks(true), HowToSceneBuilder.DROP);
        s.idle(10);
        s.meter(110, above, "RACK TEMP", 0.25f, 0.85f, HowToSceneBuilder.RED, 120, "\u00b0");
        s.text(95, HowToSceneBuilder.face(hot, Direction.SOUTH), Side.RIGHT,
                "Every part makes heat in its own rack. Without a *Server Fan* the heat can't get out: the rack keeps getting"
                        + " warmer.");
        s.idle(115);

        s.chapter("Fans");
        s.rightClick(HowToSceneBuilder.face(hot, Direction.SOUTH), new ItemStack(ModItems.SERVER_FAN.get()));
        s.idle(16);
        s.meter(100, above, "RACK TEMP", 0.85f, 0.25f, HowToSceneBuilder.GREEN, 120, "\u00b0");
        s.text(90, HowToSceneBuilder.face(hot, Direction.SOUTH), Side.RIGHT,
                "A fan only cools its own rack: every rack with something in it needs at least one.");
        s.idle(105);

        s.chapter("Too hot");
        BlockPos burning = new BlockPos(3, 3, 2);
        s.place(burning, Blocks.FIRE.defaultBlockState());
        s.outline(90, new BlockPos(3, 1, 2), new BlockPos(3, 2, 2), HowToSceneBuilder.RED);
        s.text(100, HowToSceneBuilder.top(burning), Side.RIGHT,
                "Over 70 degrees the whole server runs at half speed. Over 90 parts wear out. Over 110 for 10 seconds the"
                        + " rack burns, with everything in it.");
        s.idle(105);
        s.clear(burning);
        s.idle(10);
        return List.of(s.build());
    }
}
