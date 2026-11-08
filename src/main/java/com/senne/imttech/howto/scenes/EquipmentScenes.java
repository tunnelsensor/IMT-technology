package com.senne.imttech.howto.scenes;

import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** De animaties van het tabblad Equipment (hoofdstuk 63). Als eerste de bril, waar de hele mod om draait. */
public final class EquipmentScenes {
    private EquipmentScenes() {}

    /**
     * De bril: op je hoofd (alleen te vinden in de kluis van een Biebia's Industries Tower), van wie hem als eerste opzet,
     * de HUD en het G-menu als plaatje, en laden in het laadstation.
     */
    public static List<HowToScene> glasses() {
        HowToSceneBuilder s = new HowToSceneBuilder("glasses", "The I.M.T glasses").plate(5, 5).height(3);
        ItemStack glasses = new ItemStack(ModItems.IMT_GLASSES.get());
        Vec3 spot = new Vec3(1.4, 1.0, 2.8);
        Vec3 head = spot.add(0, 1.75, 0);
        s.idle(6);
        s.chapter("On your head");
        // Je kijkt naar de camera (die staat zuidwestelijk): yaw 40.
        s.player("you", 24, 0, 40.0f, Map.of(), spot);
        s.idle(24);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 40.0f, Map.of(EquipmentSlot.HEAD, glasses), spot);
        s.sound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.5f, 1.0f);
        s.text(90, head, Side.RIGHT,
                "The *I.M.T glasses* go on your head (the helmet slot). You can't craft them: the only pair in your world lies"
                        + " in the vault of a *Biebia's Industries Tower*.");
        s.idle(95);

        s.chapter("Yours alone");
        s.text(80, head, Side.RIGHT,
                "Whoever puts them on first becomes their owner; anyone else gets *Access denied*. You can hand them over"
                        + " (G > Systems).");
        s.idle(85);

        s.chapter("Your HUD");
        s.picture(130, "glasses_hud", 426, 240,
                s.arrow(0.1f, 0.055f, "Energy: everything the glasses do costs some"),
                s.arrow(0.045f, 0.11f, "*Target*: what you look at"),
                s.arrow(0.5f, 0.07f, "A compass"));
        s.idle(135);

        s.chapter("The G menu");
        s.picture(130, "glasses_menu", 426, 240,
                s.arrow(0.5f, 0.47f, "Tap [G]: ten tabs, scroll or 1 to 0 to switch"),
                s.arrow(0.5f, 0.07f, "Point at an option and click (or hold [G] and let go)"));
        s.idle(135);

        s.chapter("Energy");
        BlockPos station = new BlockPos(3, 1, 0);
        BlockPos panel = new BlockPos(4, 1, 0);
        s.place(station, PowerScenes.station(Direction.SOUTH));
        s.idle(5);
        s.place(panel, ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        s.idle(10);
        Vec3 front = HowToSceneBuilder.face(station, Direction.SOUTH);
        s.rightClick(front, glasses);
        s.idle(14);
        s.meter(90, HowToSceneBuilder.top(station).add(0, 0.5, 0), "GLASSES", 0.2f, 1.0f, HowToSceneBuilder.CYAN);
        s.text(85, front, Side.RIGHT,
                "Charge them in a *Charging Station*. Upgrades (battery, scanner, drones) go in at the *Suit Workbench*.");
        s.idle(95);
        return List.of(s.build());
    }
}
