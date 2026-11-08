package com.senne.imttech.howto.scenes;

import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
}
