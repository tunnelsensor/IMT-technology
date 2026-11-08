package com.senne.imttech.howto.scenes;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.holo.HoloMode;
import com.senne.imttech.holo.HoloTableBlock;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.light.LightSwitchBlock;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.repair.CrackMachineBlock;
import com.senne.imttech.repair.FromBlockItem;
import com.senne.imttech.shield.ShieldColor;
import com.senne.imttech.workbench.HoloPiece;
import com.senne.imttech.workbench.SuitWorkbenchBlock;
import com.senne.imttech.workbench.SuitWorkbenchBlockEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

/**
 * De laatste animaties van het tabblad Interior (hoofdstuk 63, jouw vraag van 8 november: "werk de overschot ook nog uit"):
 * de holotafel, de harnas-werkbank met zijn upgrades, barsten herstellen, het landingsplatform en de Arc Lamp met de
 * lichtschakelaar. Hoe het echt werkt staat in LEESMIJ ("Holotafel", "De harnas-werkbank", "Barsten herstellen",
 * "Landingsplatform", "Licht: de Arc Lamp en de lichtschakelaar").
 */
public final class InteriorItemScenes {
    private InteriorItemScenes() {}

    /** Een voorwerp zoals een block entity het bewaart ({id, count}): zo hoeft een animatie geen registers te kennen. */
    static CompoundTag item(Item item) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.ITEM.getKey(item).toString());
        tag.putInt("count", 1);
        return tag;
    }

    // ================================================================ De holotafel

    static BlockState holoTable(ShieldColor colour) {
        return ModBlocks.HOLO_TABLE.get().defaultBlockState().setValue(HoloTableBlock.COLOR, colour);
    }

    /** Wat de tafel laat zien: de stand, of hij stroom heeft, en (bij een recept) het voorwerp. */
    static CompoundTag holo(HoloMode mode, boolean powered, Item blueprint) {
        CompoundTag tag = new CompoundTag();
        tag.putByte("Mode", (byte) mode.ordinal());
        tag.putBoolean("Powered", powered);
        tag.putBoolean("Spin", true);
        if (blueprint != null) {
            tag.put("Blueprint", item(blueprint));
        }
        return tag;
    }

    /**
     * De holotafel: zonder stroom een rood ringetje, met stroom een hologram; een voorwerp erop toont zijn recept; sneak +
     * rechtsklik is de volgende kleur; het instelscherm (als plaatje); en de kaart, de satelliet en de ertsen (plaatjes uit
     * de echte wereld, want die standen kijken naar de wereld om de tafel heen).
     */
    public static List<HowToScene> holoTable() {
        HowToSceneBuilder s = new HowToSceneBuilder("holo_table", "Holo table").plate(4, 4).height(3);
        // Het midden van de plaat ligt op het scherm in het midden: zo staat de ballon rechts naast het hologram.
        BlockPos table = new BlockPos(1, 1, 2);
        BlockPos gen = new BlockPos(1, 1, 1);
        Vec3 front = HowToSceneBuilder.face(table, Direction.SOUTH);
        Item station = ModItems.CHARGING_STATION.get();
        s.idle(6);
        s.chapter("A hologram");
        s.place(table, holoTable(ShieldColor.CYAN));
        s.blockData(table, holo(HoloMode.BLUEPRINT, false, station));
        s.idle(30);
        s.text(70, HowToSceneBuilder.top(table), Side.RIGHT,
                "A *Holo Table* without power: only a red ring blinks.");
        s.idle(75);
        s.place(gen, PowerScenes.heat(Direction.SOUTH));
        s.idle(10);
        s.blockData(table, holo(HoloMode.BLUEPRINT, true, station));
        s.sound(com.senne.imttech.registry.ModSounds.IMT_BOOT.get(), 0.3f, 1.2f);
        s.text(90, HowToSceneBuilder.top(table).add(0, 1.2, 0), Side.RIGHT,
                "With power (a generator against it, or a cable) it projects a slowly turning hologram. You don't need glasses"
                        + " for it.");
        s.idle(95);

        s.chapter("Show a recipe");
        Item bench = ModItems.SUIT_WORKBENCH.get();
        s.rightClick(front, new ItemStack(bench));
        s.idle(12);
        s.blockData(table, holo(HoloMode.BLUEPRINT, true, bench));
        s.text(110, HowToSceneBuilder.top(table).add(0, 1.2, 0), Side.RIGHT,
                "Right-click it with an item: it shows that item's *recipe*, a floating grid with the result above. If a part"
                        + " may come from a whole group, the options take turns. No recipe (like the glasses): a red cross.");
        s.idle(115);

        s.chapter("Colours");
        ShieldColor[] colours = {ShieldColor.GREEN, ShieldColor.YELLOW, ShieldColor.RED, ShieldColor.PURPLE, ShieldColor.BLUE,
                ShieldColor.CYAN};
        s.text(100, HowToSceneBuilder.top(table).add(0, 1.2, 0), Side.RIGHT,
                "Sneak + right-click: the next colour at once, without a screen (the same six as the rayshields).");
        for (ShieldColor colour : colours) {
            s.sneakRightClick(front, ItemStack.EMPTY);
            s.idle(10);
            s.change(table, holoTable(colour));
            s.sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.2f, 1.4f);
            s.idle(8);
        }
        s.idle(5);

        s.chapter("Settings");
        s.rightClick(front, ItemStack.EMPTY);
        s.idle(14);
        s.picture(150, "holo_screen", 426, 240,
                s.arrow(0.6f, 0.17f, "What it shows: Map, Blueprint, Arsenal, Satellite or Ores"),
                s.arrow(0.56f, 0.37f, "The colour"),
                s.arrow(0.478f, 0.47f, "How far the map reaches: 24 to 160 blocks"),
                s.arrow(0.478f, 0.57f, "How big the hologram is (and below: whether it turns)"),
                s.arrow(0.76f, 0.77f, "*Take off*: the item back off the table"));
        s.idle(155);

        s.chapter("Map");
        s.text(70, HowToSceneBuilder.top(table).add(0, 1.2, 0), Side.RIGHT,
                "*Map*: a relief map of the ground around the table, the higher the brighter.");
        s.idle(75);
        s.picture(130, "holo_map", 426, 240,
                s.arrow(0.5f, 0.55f, "Players white, enemies red, animals green"),
                s.arrow(0.445f, 0.5f, "Your waypoints as posts (with your glasses on)"));
        s.idle(135);

        s.chapter("Satellite and ores");
        s.text(80, HowToSceneBuilder.top(table).add(0, 1.2, 0), Side.RIGHT,
                "*Arsenal* (your drones, green to red by damage) and *Satellite* need your glasses; without them you see a red"
                        + " cross.");
        s.idle(85);
        s.picture(110, "holo_satellite", 426, 240,
                s.arrow(0.5f, 0.35f, "Your satellite with all its upgrades"));
        s.idle(115);
        s.picture(120, "holo_ores", 426, 240,
                s.arrow(0.5f, 0.49f, "*Ores*: the ores under the table, up to 48 deep"));
        s.idle(125);
        return List.of(s.build());
    }

    // ================================================================ De harnas-werkbank

    static BlockState workbench() {
        return ModBlocks.SUIT_WORKBENCH.get().defaultBlockState().setValue(SuitWorkbenchBlock.FACING, Direction.SOUTH);
    }

    /** De werkbank met stroom: welk stuk gekozen is, wat erop ligt, en wat hij aan het doen is. */
    static CompoundTag bench(HoloPiece selected, Map<HoloPiece, Item> laid, int job, HoloPiece jobPiece, int progress,
                             Item module) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Selected", selected.ordinal());
        tag.putBoolean("Powered", true);
        tag.putInt("Job", job);
        tag.putInt("JobPiece", jobPiece.ordinal());
        tag.putInt("Progress", progress);
        for (Map.Entry<HoloPiece, Item> entry : laid.entrySet()) {
            tag.put("Laid_" + entry.getKey().name(), item(entry.getValue()));
        }
        if (module != null) {
            tag.put("Module", item(module));
        }
        return tag;
    }

    static CompoundTag bench(HoloPiece selected, Map<HoloPiece, Item> laid) {
        return bench(selected, laid, SuitWorkbenchBlockEntity.JOB_NONE, HoloPiece.CHEST, 0, null);
    }

    /**
     * De harnas-werkbank: geen scherm maar een hologram van één stuk; een klik op de werkbank kiest het volgende stuk; een
     * klik op het hologram bouwt het (het bouwt zich plaatje voor plaatje op en komt dan bij jou), een stuk neerleggen, een
     * upgrade inbouwen, en welke upgrades er zijn.
     */
    public static List<HowToScene> suitWorkbench() {
        HowToSceneBuilder s = new HowToSceneBuilder("suit_workbench", "Suit workbench and upgrades").plate(5, 5).height(3);
        BlockPos table = new BlockPos(2, 1, 2);
        BlockPos gen = new BlockPos(2, 1, 1);
        Vec3 front = HowToSceneBuilder.face(table, Direction.SOUTH);
        Vec3 holo = HowToSceneBuilder.top(table).add(0, 0.9, 0);
        Vec3 you = new Vec3(2.5, 1.0, 4.1);
        ItemStack glasses = new ItemStack(ModItems.IMT_GLASSES.get());
        Item chest = ModItems.TECH_CHESTPLATE.get();
        Item boots = ModItems.TECH_BOOTS.get();
        Item thruster = ModItems.THRUSTER_UPGRADE.get();
        s.idle(6);
        s.chapter("No screen, a hologram");
        s.place(table, workbench());
        s.idle(6);
        s.place(gen, PowerScenes.heat(Direction.SOUTH));
        s.idle(4);
        s.blockData(table, bench(HoloPiece.CHEST, Map.of()));
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(EquipmentSlot.HEAD, glasses), you);
        s.idle(10);
        s.text(100, holo, Side.RIGHT,
                "The *Suit Workbench* (on power: a generator against it or a cable) has no screen. Above it floats one piece of"
                        + " the suit as a blue *hologram*, slowly turning.");
        s.idle(105);

        s.chapter("Choose a piece");
        s.text(100, holo, Side.RIGHT,
                "Right-click the workbench itself: the next piece (chestplate, leggings, boots, glasses). Sneak + right-click:"
                        + " the previous one.");
        for (HoloPiece piece : new HoloPiece[] {HoloPiece.LEGS, HoloPiece.BOOTS, HoloPiece.GLASSES, HoloPiece.CHEST}) {
            s.rightClick(front, ItemStack.EMPTY);
            s.idle(12);
            s.blockData(table, bench(piece, Map.of()));
            s.sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.2f, 1.2f);
            s.idle(14);
        }
        s.idle(10);

        s.chapter("Build it");
        s.rightClick(holo, ItemStack.EMPTY);
        s.idle(10);
        int build = Math.round(ImtConfig.Workbench.BUILD_TICKS);
        int steps = 16;
        s.text(110, holo, Side.RIGHT,
                "Click the hologram with an empty hand: it *builds* that piece. A card by your crosshair shows what it costs and"
                        + " what you carry; the materials come from your inventory. Plate by plate it builds up.");
        for (int i = 0; i <= steps; i++) {
            s.blockData(table, bench(HoloPiece.CHEST, Map.of(), SuitWorkbenchBlockEntity.JOB_BUILD, HoloPiece.CHEST,
                    build * i / steps, null));
            s.idle(7);
        }
        s.blockData(table, bench(HoloPiece.CHEST, Map.of()));
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 180.0f,
                Map.of(EquipmentSlot.HEAD, glasses, EquipmentSlot.CHEST, new ItemStack(chest)), you);
        s.sound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.6f, 1.0f);
        s.text(80, you.add(0, 2.0, 0), Side.RIGHT,
                "Done? It flies to you and goes on straight away (or into your bag).");
        s.idle(85);

        s.chapter("Lay a piece on it");
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(EquipmentSlot.HEAD, glasses,
                EquipmentSlot.CHEST, new ItemStack(chest), EquipmentSlot.MAINHAND, new ItemStack(boots)), you);
        s.idle(8);
        s.rightClick(holo, new ItemStack(boots));
        s.idle(10);
        s.blockData(table, bench(HoloPiece.BOOTS, Map.of(HoloPiece.BOOTS, boots)));
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(EquipmentSlot.HEAD, glasses,
                EquipmentSlot.CHEST, new ItemStack(chest)), you);
        s.text(100, holo, Side.RIGHT,
                "Hold a suit piece and click the hologram: it lies there for real. Click it with an empty hand to take it"
                        + " back (it flies to you).");
        s.idle(105);

        s.chapter("Upgrade it");
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(EquipmentSlot.HEAD, glasses,
                EquipmentSlot.CHEST, new ItemStack(chest), EquipmentSlot.MAINHAND, new ItemStack(thruster)), you);
        s.idle(8);
        s.rightClick(holo, new ItemStack(thruster));
        s.idle(10);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(EquipmentSlot.HEAD, glasses,
                EquipmentSlot.CHEST, new ItemStack(chest)), you);
        int install = Math.round(ImtConfig.Workbench.INSTALL_TICKS);
        s.text(100, holo, Side.RIGHT,
                "Hold an *upgrade* and click the piece on it: the card says if it fits. It circles the piece and shrinks into"
                        + " it while a ring fills.");
        for (int i = 0; i <= steps; i++) {
            s.blockData(table, bench(HoloPiece.BOOTS, Map.of(HoloPiece.BOOTS, boots), SuitWorkbenchBlockEntity.JOB_INSTALL,
                    HoloPiece.BOOTS, install * i / steps, thruster));
            s.idle(6);
        }
        s.blockData(table, bench(HoloPiece.BOOTS, Map.of(HoloPiece.BOOTS, boots)));
        s.idle(15);

        s.chapter("Suit upgrades");
        // Twee rijtjes links achter de werkbank (vóór de werkbank stond jij ervoor, en rechts staat de ballon).
        Vec3 row = new Vec3(0.6, 2.9, 1.4);
        WeaponScenes.row(s, 146, row, 0.62f, 0.55f, List.of(new ItemStack(thruster),
                new ItemStack(ModItems.ARMOR_PLATING.get()), new ItemStack(ModItems.LASER_CANNON_CAPACITOR.get())));
        WeaponScenes.row(s, 137, row.add(0, -0.7, 0), 0.62f, 0.55f, List.of(new ItemStack(ModItems.ROCKET_PACK.get()),
                new ItemStack(ModItems.ROCKET_MAGAZINE.get()), new ItemStack(ModItems.ROCKET_WARHEAD.get())));
        s.text(125, holo, Side.RIGHT,
                "*Thruster Upgrade* in the boots (up to %s): faster and cheaper flying. *Armor Plating* in every piece (%s"
                        + " each). *Laser Cannon Capacitor* in the chestplate (%s). The *rocket pack* with *Rocket Magazines* and"
                        + " *Warheads* (%s each).", HowToSceneBuilder.number(ImtConfig.Workbench.MAX_THRUSTERS),
                HowToSceneBuilder.number(ImtConfig.Workbench.MAX_PLATING), HowToSceneBuilder.number(ImtConfig.Workbench.MAX_CAPACITORS),
                HowToSceneBuilder.number(ImtConfig.Workbench.MAX_MAGAZINES));
        s.idle(130);

        s.chapter("Glasses upgrades");
        s.blockData(table, bench(HoloPiece.GLASSES, Map.of(HoloPiece.GLASSES, ModItems.IMT_GLASSES.get())));
        List<ItemStack> eyes = List.of(new ItemStack(ModItems.BATTERY_UPGRADE.get()), new ItemStack(ModItems.SCANNER_UPGRADE.get()),
                new ItemStack(ModItems.DRONE_UPGRADE.get()));
        WeaponScenes.row(s, 110, row.add(0, -0.35, 0), 0.62f, 0.55f, eyes);
        s.text(95, holo, Side.RIGHT,
                "Your *glasses* are the fourth piece: lay them on the hologram and build in a *Battery*, *Scanner* or *Drone"
                        + " Upgrade*. Building glasses is not possible: they lie in the tower's vault.");
        s.idle(105);
        return List.of(s.build());
    }

    // ================================================================ Barsten herstellen

    static BlockState machine(net.minecraft.world.level.block.Block block, boolean working) {
        return block.defaultBlockState().setValue(CrackMachineBlock.FACING, Direction.WEST).setValue(CrackMachineBlock.WORKING,
                working);
    }

    /**
     * Barsten herstellen: barsten blijven, Repair Paste vult ze (het blok wordt zwakker), of vervangen: wat je weghakt wordt
     * een gebarsten blok, de Crusher maakt er poeder van en de Compactor een nieuw blok (zijn scherm als plaatje).
     */
    public static List<HowToScene> repairingCracks() {
        HowToSceneBuilder s = new HowToSceneBuilder("repairing_cracks", "Repairing cracks").plate(7, 5).height(3);
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockPos low = new BlockPos(1, 1, 1);
        BlockPos high = new BlockPos(2, 2, 1);
        BlockPos gen = new BlockPos(5, 1, 2);
        BlockPos crusher = new BlockPos(5, 1, 1);
        BlockPos compactor = new BlockPos(5, 1, 3);
        Vec3 you = new Vec3(1.8, 1.0, 3.3);
        ItemStack paste = new ItemStack(ModItems.REPAIR_PASTE.get());
        s.idle(6);
        s.chapter("Cracks stay");
        s.place(HowToSceneBuilder.box(new BlockPos(0, 1, 1), new BlockPos(3, 2, 1), pos -> bricks), HowToSceneBuilder.DROP);
        s.idle(12);
        s.crack(low, 6);
        s.crack(high, 4);
        s.sound(SoundEvents.GENERIC_EXPLODE.value(), 0.2f, 1.4f);
        s.idle(6);
        s.text(110, HowToSceneBuilder.face(high, Direction.SOUTH), Side.RIGHT,
                "What a blast doesn't break gets *cracks*: from a frag, and in a zone also from TNT and the satellite. They never"
                        + " heal by themselves, and the next blast adds to them.");
        s.idle(115);

        s.chapter("Fill them");
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(EquipmentSlot.MAINHAND, paste), you);
        s.idle(8);
        s.rightClick(HowToSceneBuilder.face(low, Direction.SOUTH), paste);
        s.idle(12);
        s.crack(low, -1);
        s.sound(SoundEvents.MUD_PLACE, 0.5f, 1.0f);
        float weaker = ImtConfig.Repair.PATCH_WEAKER;
        s.meter(110, HowToSceneBuilder.top(low).add(0, 1.2, 0), "STRENGTH", 1.0f, 1.0f - weaker, HowToSceneBuilder.ORANGE);
        s.text(115, HowToSceneBuilder.face(low, Direction.SOUTH), Side.RIGHT,
                "Right-click with *Repair Paste* (2 clay, sand and gravel = 4): the cracks are gone, but the block is %s%% weaker,"
                        + " every time again, never below %s%%. Not in someone else's zone.",
                HowToSceneBuilder.number(weaker * 100), HowToSceneBuilder.number(ImtConfig.Repair.PATCH_WEAKEST * 100));
        s.idle(120);

        s.chapter("Replace it");
        Vec3 highFace = HowToSceneBuilder.face(high, Direction.SOUTH);
        s.player("you", HowToSceneBuilder.UNTIL_END, 0, 180.0f, Map.of(EquipmentSlot.MAINHAND,
                new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE)), you);
        s.idle(6);
        s.click(20, List.of(highFace), com.senne.imttech.howto.HowToCue.Button.LEFT, true, false,
                new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        for (int stage = 5; stage <= 9; stage++) {
            s.crack(high, stage);
            s.idle(4);
        }
        s.crack(high, -1);
        s.clear(high);
        s.sound(SoundEvents.STONE_BREAK, 0.6f, 1.0f);
        ItemStack cracked = FromBlockItem.of(ModItems.CRACKED_BLOCK.get(), Blocks.STONE_BRICKS, 1);
        s.item(50, HowToSceneBuilder.center(high), cracked, 0.6f);
        s.idle(20);
        s.place(high, bricks);
        s.sound(SoundEvents.STONE_PLACE, 0.6f, 1.0f);
        s.text(100, highFace, Side.RIGHT,
                "Or mine the block and put in a new one: that is as strong as ever. What you mine becomes a *cracked block*"
                        + " (\"Cracked Stone Bricks\").");
        s.idle(105);

        s.chapter("Crusher and Compactor");
        s.place(gen, PowerScenes.heat(Direction.WEST));
        s.idle(4);
        s.place(crusher, machine(ModBlocks.CRUSHER.get(), false));
        s.idle(4);
        s.place(compactor, machine(ModBlocks.COMPACTOR.get(), false));
        s.idle(10);
        int machine = Math.round(ImtConfig.Repair.MACHINE_TICKS);
        Vec3 aboveCrusher = HowToSceneBuilder.top(crusher).add(0, 0.45, 0);
        Vec3 aboveCompactor = HowToSceneBuilder.top(compactor).add(0, 0.45, 0);
        s.item(machine / 2, aboveCrusher, cracked, 0.5f);
        s.change(crusher, machine(ModBlocks.CRUSHER.get(), true));
        s.text(120, HowToSceneBuilder.top(crusher).add(0, 0.6, 0), Side.LEFT,
                "On mains power the *Crusher* turns a cracked block into *powder* in the colour of the block, and the"
                        + " *Compactor* presses that powder into a brand-new block, without cracks. %s seconds a block each.",
                HowToSceneBuilder.number(machine / 20.0));
        s.idle(machine / 2);
        s.change(crusher, machine(ModBlocks.CRUSHER.get(), false));
        ItemStack powder = FromBlockItem.of(ModItems.BLOCK_POWDER.get(), Blocks.STONE_BRICKS, 1);
        s.item(machine / 2, aboveCompactor, powder, 0.5f);
        s.change(compactor, machine(ModBlocks.COMPACTOR.get(), true));
        s.idle(machine / 2);
        s.change(compactor, machine(ModBlocks.COMPACTOR.get(), false));
        s.item(60, aboveCompactor, new ItemStack(Blocks.STONE_BRICKS), 0.5f);
        s.sound(SoundEvents.ANVIL_LAND, 0.2f, 1.6f);
        s.idle(50);

        s.chapter("The screen");
        s.rightClick(HowToSceneBuilder.face(crusher, Direction.WEST), ItemStack.EMPTY);
        s.idle(14);
        s.picture(120, "crusher_screen", 426, 240,
                s.arrow(0.442f, 0.333f, "In: a cracked block (hoppers from above or the side)"),
                s.arrow(0.511f, 0.33f, "The arrow fills while it works"),
                s.arrow(0.584f, 0.333f, "Out: powder (hoppers from below)"));
        s.idle(125);
        return List.of(s.build());
    }

    // ================================================================ Het landingsplatform

    /**
     * Het landingsplatform: van jou, je HUD wijst ernaar, landen met de stuwraketten ("Welcome home"), je bril laadt en je
     * harnas wordt gerepareerd zolang je erop staat, en zijn eigen batterij (zijn scherm als plaatje).
     */
    public static List<HowToScene> landingPad() {
        HowToSceneBuilder s = new HowToSceneBuilder("landing_pad", "Landing pad").plate(5, 5).height(4);
        BlockPos pad = new BlockPos(2, 1, 2);
        BlockPos gen = new BlockPos(2, 1, 0);
        Map<EquipmentSlot, ItemStack> suit = SuitScenes.suit(false);
        Vec3 landed = new Vec3(2.5, 1.06, 2.5);
        s.idle(6);
        s.chapter("Your landing pad");
        s.place(pad, ModBlocks.LANDING_PAD.get().defaultBlockState());
        s.idle(6);
        s.place(gen, PowerScenes.heat(Direction.SOUTH));
        s.idle(6);
        s.cable(CableKind.POWER, 16, new Vec3(2.5, 1.05, 1.0), new Vec3(2.5, 1.05, 2.0));
        s.idle(14);
        s.outline(110, new BlockPos(0, 1, 0), new BlockPos(4, 1, 4), HowToSceneBuilder.CYAN);
        s.text(105, HowToSceneBuilder.top(pad), Side.RIGHT,
                "A *Landing Pad* is yours when you place it (the one on a Biebia's Industries Tower belongs to whoever hacked the"
                        + " tower). While you fly, your HUD points to the nearest one.");
        s.idle(110);

        s.chapter("Welcome home");
        Vec3[] approach = {new Vec3(-1.2, 4.6, 4.8), new Vec3(0.6, 3.8, 3.8), new Vec3(1.9, 2.8, 3.0), new Vec3(2.5, 1.9, 2.5),
                landed};
        int fly = 70;
        s.person("you", SuitScenes.flying(1.0f), fly, fly, 160.0f, suit, List.of(), approach);
        s.idle(fly);
        s.person("you", new CompoundTag(), HowToSceneBuilder.UNTIL_END, 0, 160.0f, suit, List.of(), landed);
        s.sound(com.senne.imttech.registry.ModSounds.IMT_CONFIRM.get(), 0.5f, 1.0f);
        s.text(90, landed.add(0, 2.1, 0), Side.RIGHT,
                "Land on it with your thrusters on, and I.M.T says \"Welcome home\".");
        s.idle(95);

        s.chapter("Charging and repairs");
        s.meter(110, landed.add(0, 2.4, 0), "GLASSES", 0.3f, 1.0f, HowToSceneBuilder.CYAN);
        s.text(110, landed.add(0, 1.2, 0), Side.RIGHT,
                "While you stand on it (5 x 5 blocks), your glasses charge and your suit is repaired, with power from the grid.");
        s.idle(115);

        s.chapter("Its own battery");
        s.rightClick(HowToSceneBuilder.top(pad), ItemStack.EMPTY);
        s.idle(12);
        s.text(110, HowToSceneBuilder.top(pad), Side.RIGHT,
                "Right-click: its own *battery*. It fills slowly with what the grid has spare (%s a second) and charges your"
                        + " glasses from it first (%s a second), then straight from the grid (%s a second).",
                HowToSceneBuilder.number(ImtConfig.Power.PAD_BATTERY_FILL), HowToSceneBuilder.number(ImtConfig.Power.PAD_BATTERY_CHARGE),
                HowToSceneBuilder.number(ImtConfig.Power.PAD_CHARGE_PER_SECOND));
        s.idle(115);
        s.picture(130, "pad_screen", 426, 240,
                s.arrow(0.42f, 0.29f, "The battery of the pad"),
                s.arrow(0.61f, 0.33f, "What the grid has, how fast it fills, how fast it charges you"),
                s.arrow(0.414f, 0.382f, "*Cells*: up to %s Battery Upgrades, each %s bigger",
                        com.senne.imttech.suit.LandingPadBlockEntity.MAX_CELLS,
                        HowToSceneBuilder.number(ImtConfig.Power.PAD_BATTERY_PER_CELL)));
        s.idle(135);
        return List.of(s.build());
    }

    // ================================================================ De Arc Lamp en de lichtschakelaar

    static BlockState lightSwitch(boolean on) {
        return ModBlocks.LIGHT_SWITCH.get().defaultBlockState().setValue(LightSwitchBlock.FACE, AttachFace.FLOOR)
                .setValue(LightSwitchBlock.FACING, Direction.EAST).setValue(LightSwitchBlock.ON, on);
    }

    /**
     * De Arc Lamp: lichtniveau 100 (een plaatje 's nachts), elke kamer zijn eigen lamp, alleen op netstroom, het instelscherm
     * (als plaatje), en de lichtschakelaar op de kabel.
     */
    public static List<HowToScene> arcLamp() {
        HowToSceneBuilder s = new HowToSceneBuilder("arc_lamp", "Arc Lamp and light switch").plate(7, 4).height(3);
        BlockPos gen = new BlockPos(0, 1, 2);
        BlockPos post = new BlockPos(6, 1, 2);
        BlockPos lamp = new BlockPos(6, 2, 2);
        BlockPos sw = new BlockPos(3, 1, 2);
        Vec3[] cable = {new Vec3(1.0, 1.05, 2.5), new Vec3(6.0, 1.05, 2.5)};
        int level = Math.round(ImtConfig.Lights.ARC_LAMP_MAX_LEVEL);
        s.idle(6);
        s.chapter("Light level %s".formatted(level));
        s.place(gen, PowerScenes.heat(Direction.EAST));
        s.idle(4);
        s.place(post, Blocks.IRON_BLOCK.defaultBlockState());
        s.idle(4);
        s.place(lamp, PowerScenes.arcLamp(false));
        s.idle(8);
        s.cable(CableKind.POWER, 20, cable);
        s.idle(22);
        s.change(lamp, PowerScenes.arcLamp(true));
        s.sound(SoundEvents.BEACON_ACTIVATE, 0.3f, 1.6f);
        s.text(80, HowToSceneBuilder.top(lamp), Side.LEFT,
                "The *Arc Lamp* has light level *%s*: full light up to %s blocks, then softly darker up to %s. No monsters spawn"
                        + " in it.", level, level - 15, level);
        s.idle(85);
        s.picture(110, "lamp_night", 426, 240,
                s.arrow(0.5f, 0.37f, "One lamp on a pole, at midnight: the whole field is light"));
        s.idle(115);

        s.chapter("Each room its own lamp");
        s.text(115, HowToSceneBuilder.top(lamp), Side.LEFT,
                "Its light goes in *straight lines*: not through walls or round corners, and a closed door or trapdoor stops it."
                        + " Glass and water let it through. An open door lets a beam through, but the next room isn't lit.");
        s.idle(120);

        s.chapter("Mains power only");
        s.clear(gen);
        s.change(lamp, PowerScenes.arcLamp(false));
        s.idle(20);
        s.text(90, HowToSceneBuilder.top(lamp), Side.LEFT,
                "It runs only on *mains power*: power = on, no power = off. It has no button of its own.");
        s.idle(50);
        s.place(gen, PowerScenes.heat(Direction.EAST));
        s.idle(6);
        s.change(lamp, PowerScenes.arcLamp(true));
        s.idle(45);

        s.chapter("Brightness");
        s.rightClick(HowToSceneBuilder.face(lamp, Direction.SOUTH), ItemStack.EMPTY);
        s.idle(12);
        s.picture(140, "lamp_screen", 426, 240,
                s.arrow(0.588f, 0.336f, "*Brightness*: 1 to %s (up to 15 it's a normal lamp)", level),
                s.arrow(0.582f, 0.44f, "What that means, and how much power it uses"),
                s.arrow(0.817f, 0.24f, "*ON*, or *NO POWER*"));
        s.idle(145);

        s.chapter("The light switch");
        s.place(sw, lightSwitch(true));
        s.idle(10);
        s.text(90, HowToSceneBuilder.top(sw), Side.LEFT,
                "A *Light Switch* goes *on* a cable: on a wall, the floor or the ceiling, where the cable runs.");
        s.idle(60);
        s.rightClick(HowToSceneBuilder.top(sw).subtract(0, 0.4, 0), ItemStack.EMPTY);
        s.idle(10);
        s.change(sw, lightSwitch(false));
        s.change(lamp, PowerScenes.arcLamp(false));
        s.sound(SoundEvents.LEVER_CLICK, 0.4f, 1.0f);
        s.text(100, HowToSceneBuilder.top(sw), Side.LEFT,
                "Right-click: *off* (red light). Behind it the power stops, for your lamps and everything else. *On* (blue) lets"
                        + " it through as if it were cable.");
        s.idle(60);
        s.rightClick(HowToSceneBuilder.top(sw).subtract(0, 0.4, 0), ItemStack.EMPTY);
        s.idle(10);
        s.change(sw, lightSwitch(true));
        s.change(lamp, PowerScenes.arcLamp(true));
        s.sound(SoundEvents.LEVER_CLICK, 0.4f, 1.2f);
        s.idle(45);

        s.chapter("From anywhere");
        s.text(110, HowToSceneBuilder.top(sw), Side.LEFT,
                "It must sit *in* the cable: next to it, power just runs past. Whoever places it finds it in the I.M.T redstone"
                        + " list (['], \"Light switch 1\"): switch your lights from anywhere.");
        s.idle(115);
        return List.of(s.build());
    }
}
