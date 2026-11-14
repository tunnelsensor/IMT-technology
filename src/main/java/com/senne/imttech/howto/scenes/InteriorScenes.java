package com.senne.imttech.howto.scenes;

import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** De animaties van het tabblad Interior (hoofdstuk 63). Eerst het laadstation: de bril laden doe je in elke wereld. */
public final class InteriorScenes {
    private InteriorScenes() {}

    /**
     * Het laadstation: de bril laden uit het net, een energiecel laden, en dat het alleen neemt wat de generatoren over
     * hebben (ze houden de helft achter voor de rest van het net, zie LEESMIJ "Stroom en kabels").
     */
    public static List<HowToScene> chargingStation() {
        HowToSceneBuilder s = new HowToSceneBuilder("charging_station", "The charging station").plate(5, 5);
        BlockPos station = new BlockPos(2, 1, 2);
        BlockPos panel = new BlockPos(1, 1, 2);
        Vec3 front = HowToSceneBuilder.face(station, Direction.SOUTH);
        Vec3 above = HowToSceneBuilder.top(station).add(0, 0.5, 0);
        s.idle(8);
        s.chapter("Charge your glasses");
        s.place(station, PowerScenes.station(Direction.SOUTH));
        s.idle(6);
        s.place(panel, ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        s.idle(12);
        s.rightClick(front, new ItemStack(ModItems.IMT_GLASSES.get()));
        s.idle(16);
        s.meter(110, above, "GLASSES", 0.15f, 1.0f, HowToSceneBuilder.CYAN);
        s.text(90, front, Side.RIGHT,
                "Put your *I.M.T glasses* in the *Charging Station* (right-click it): it charges them from your grid.");
        s.idle(115);

        s.chapter("Energy cells");
        s.rightClick(front, new ItemStack(ModItems.ENERGY_CELL.get()));
        s.idle(16);
        s.meter(100, above, "ENERGY CELL", 0.0f, 1.0f, HowToSceneBuilder.ORANGE);
        s.text(85, front, Side.RIGHT, "It charges *Energy Cells* for your laser guns too.");
        s.idle(105);

        s.chapter("Only spare power");
        s.outline(80, panel, panel, HowToSceneBuilder.ORANGE);
        s.text(85, HowToSceneBuilder.top(panel), Side.LEFT,
                "It only takes what your generators have to spare: they always keep half for the rest of your grid.");
        s.idle(90);
        return List.of(s.build());
    }

    static BlockState relay(boolean powered) {
        return ModBlocks.RELAY.get().defaultBlockState().setValue(com.senne.imttech.redstone.RelayBlock.POWERED, powered);
    }

    static BlockState lamp(boolean lit) {
        return Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, lit);
    }

    /**
     * Relais: een naam geven (rechtsklik, zonder bril), van overal schakelen (de lijst onder ['], je stem, een diagram),
     * groepen, en de relay emitter die het omgekeerde doet (redstone erin, jouw relais schakelen mee).
     */
    public static List<HowToScene> relays() {
        HowToSceneBuilder s = new HowToSceneBuilder("relays", "Redstone relays").plate(7, 5);
        BlockPos relayA = new BlockPos(1, 1, 1);
        BlockPos lampA = new BlockPos(2, 1, 1);
        BlockPos relayB = new BlockPos(1, 1, 3);
        BlockPos lampB = new BlockPos(2, 1, 3);
        BlockPos emitter = new BlockPos(5, 1, 2);
        BlockPos lever = new BlockPos(5, 2, 2);
        s.idle(8);
        s.chapter("A relay");
        s.place(relayA, relay(false));
        s.idle(6);
        s.place(lampA, lamp(false));
        s.idle(10);
        s.rightClick(HowToSceneBuilder.face(relayA, Direction.SOUTH), ItemStack.EMPTY);
        s.text(90, HowToSceneBuilder.top(relayA), Side.RIGHT,
                "Put an *I.M.T Relay* next to a lamp, door, piston or hopper, and right-click it to give it a name (no glasses"
                        + " needed).");
        s.idle(95);

        s.chapter("Switch it from anywhere");
        for (int i = 0; i < 2; i++) {
            s.change(relayA, relay(true));
            s.change(lampA, lamp(true));
            s.idle(24);
            s.change(relayA, relay(false));
            s.change(lampA, lamp(false));
            s.idle(16);
        }
        s.change(relayA, relay(true));
        s.change(lampA, lamp(true));
        s.text(95, HowToSceneBuilder.top(lampA), Side.RIGHT,
                "I.M.T switches it from anywhere in the world: with your glasses (['] list), your voice (turn on the lamp),"
                        + " or a diagram: on(\"Lamp\").");
        s.idle(100);

        s.chapter("Groups");
        s.change(relayA, relay(false));
        s.change(lampA, lamp(false));
        s.place(relayB, relay(false));
        s.idle(4);
        s.place(lampB, lamp(false));
        s.idle(12);
        s.change(relayA, relay(true));
        s.change(lampA, lamp(true));
        s.change(relayB, relay(true));
        s.change(lampB, lamp(true));
        s.outline(80, relayA, lampB, HowToSceneBuilder.CYAN);
        s.text(90, HowToSceneBuilder.top(lampB), Side.RIGHT,
                "Put relays in groups (the *Groups* app on the desktop): switch the group, and everything in it switches"
                        + " along.");
        s.idle(95);

        s.chapter("Relay Emitter");
        s.change(relayA, relay(false));
        s.change(lampA, lamp(false));
        s.change(relayB, relay(false));
        s.change(lampB, lamp(false));
        s.place(emitter, ModBlocks.RELAY_EMITTER.get().defaultBlockState());
        s.idle(4);
        s.place(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, Direction.WEST));
        s.idle(10);
        s.rightClick(HowToSceneBuilder.top(lever).subtract(0, 0.35, 0), ItemStack.EMPTY);
        s.idle(8);
        s.change(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, Direction.WEST).setValue(LeverBlock.POWERED, true));
        s.sound(net.minecraft.sounds.SoundEvents.LEVER_CLICK, 0.4f, 1.0f);
        s.change(emitter, ModBlocks.RELAY_EMITTER.get().defaultBlockState()
                .setValue(com.senne.imttech.redstone.RelayEmitterBlock.POWERED, true));
        s.idle(6);
        s.change(relayA, relay(true));
        s.change(lampA, lamp(true));
        s.change(relayB, relay(true));
        s.change(lampB, lamp(true));
        s.text(95, HowToSceneBuilder.top(emitter), Side.LEFT,
                "A *Relay Emitter* works the other way around: give it redstone and it switches your relays (follow, toggle"
                        + " or pulse). Free, and it works while you are offline.");
        s.idle(100);

        // Hoofdstuk 112: een nieuw relais heeft een netwerk nodig (een Router, of een netwerkkabel).
        s.chapter("On the network");
        BlockPos router = new BlockPos(3, 1, 0);
        s.place(router, com.senne.imttech.howto.scenes.PowerScenes.routerBlock(true));
        s.idle(8);
        s.signal(12, HowToSceneBuilder.CYAN, new Vec3(1.5, 1.3, 1.5), new Vec3(3.5, 1.7, 0.3));
        s.signal(12, HowToSceneBuilder.CYAN, new Vec3(1.5, 1.3, 3.5), new Vec3(3.5, 1.7, 0.3));
        s.idle(14);
        s.outline(90, router, router, HowToSceneBuilder.CYAN);
        s.text(110, HowToSceneBuilder.top(router), Side.RIGHT,
                "A relay you place now only listens from a distance when it is on the network: put a *Router* nearby, or lay a"
                        + " network cable to it. No power needed. Its own Pulse button always works.");
        s.idle(115);
        return List.of(s.build());
    }
}
