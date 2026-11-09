package com.senne.imttech.howto.scenes;

import com.senne.imttech.armory.Pegboard;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.howto.HowToCue.Side;
import com.senne.imttech.howto.HowToScene;
import com.senne.imttech.howto.HowToSceneBuilder;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * De animatie van de armory (hoofdstuk 67): het Pegboard (panelen aan de muur, ophangen op het raster, pakken en
 * draaien) en de Ammo Box (één soort per kist, in en uit met klikken, een magazijn laden door rechtsklik in te houden,
 * en afgebroken houdt hij zijn inhoud). De wapens aan de wand en het etiket van de kist tekenen hun echte renderers,
 * gevoed met dezelfde gegevens als een echt blok bewaart.
 */
public final class ArmoryScenes {
    private ArmoryScenes() {}

    /** Een item zoals een block entity het bewaart (met al zijn gegevens: onderdelen, magazijn, cel). */
    private static CompoundTag stack(ItemStack stack) {
        return (CompoundTag) stack.save(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    /** Wat er aan één paneel hangt: per item zijn gaatje linksonder (x, y op dit paneel) en hoe het hangt. */
    private record Hang(ItemStack stack, int x, int y, Pegboard.Turn turn) {}

    private static CompoundTag board(List<Hang> hung) {
        ListTag list = new ListTag();
        for (Hang one : hung) {
            CompoundTag entry = new CompoundTag();
            entry.put("Item", stack(one.stack()));
            entry.putInt("X", one.x());
            entry.putInt("Y", one.y());
            entry.putInt("Turn", one.turn().ordinal());
            list.add(entry);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Hung", list);
        return tag;
    }

    private static CompoundTag box(ItemStack sort, int count) {
        ListTag list = new ListTag();
        int left = count;
        while (left > 0) {
            int n = Math.min(left, sort.getMaxStackSize());
            list.add(stack(sort.copyWithCount(n)));
            left -= n;
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Items", list);
        return tag;
    }

    private static BlockState facing(net.minecraft.world.level.block.Block block, Direction facing) {
        return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    /**
     * Het Pegboard: drie panelen aan een muur worden één wand; een rifle, een sniper over twee panelen, een Glock,
     * magazijnen en granaten erop; pakken en draaien. De Ammo Box: patronen erin (één stapel, dan alles), er een stapel
     * uit, een magazijn laden door rechtsklik in te houden, wisselen met een lasergeweer, en afbreken.
     */
    public static List<HowToScene> armory() {
        // Een wand van 2 x 2 panelen in het midden, van voren bekeken (de camera bijna recht op de zuidkant); de kist
        // rechts ervoor.
        HowToSceneBuilder s = new HowToSceneBuilder("armory", "Armory: pegboard and ammo box").plate(4, 3).height(3)
                .camera(14, 24);
        BlockPos bottomLeft = new BlockPos(1, 1, 1);
        BlockPos bottomRight = new BlockPos(2, 1, 1);
        BlockPos topLeft = new BlockPos(1, 2, 1);
        BlockPos topRight = new BlockPos(2, 2, 1);
        s.idle(6);

        s.chapter("The Pegboard");
        Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
        for (int x = 0; x < 4; x++) {
            for (int y = 1; y <= 2; y++) {
                wall.put(new BlockPos(x, y, 0), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        s.place(wall, 0);
        s.idle(10);
        Map<BlockPos, BlockState> boards = new LinkedHashMap<>();
        for (BlockPos pos : new BlockPos[]{bottomLeft, bottomRight, topLeft, topRight}) {
            boards.put(pos, facing(ModBlocks.PEGBOARD.get(), Direction.SOUTH));
        }
        s.place(boards, HowToSceneBuilder.DROP);
        s.idle(10);
        s.text(90, new Vec3(3.0, 2.5, 1.1), Side.RIGHT,
                "Put *Pegboards* on a wall. Side by side they make one big wall, with a hole every 2 pixels.");
        s.idle(95);

        s.chapter("Hang your weapons");
        ItemStack rifle = LaserGunItem.complete(GunType.RIFLE, GunMaterial.TITANIUM);
        LaserGunItem.setCell(rifle, 100);
        ItemStack sniper = LaserGunItem.complete(GunType.SNIPER, GunMaterial.NETHERITE);
        ItemStack glock = FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL);
        List<Hang> onBottomLeft = new ArrayList<>();
        List<Hang> onBottomRight = new ArrayList<>();
        List<Hang> onTopLeft = new ArrayList<>();
        List<Hang> onTopRight = new ArrayList<>();
        // De rifle (8 x 3) linksboven.
        Vec3 rifleAt = new Vec3(1.5, 2.25, 1.1);
        s.rightClick(rifleAt, rifle);
        s.idle(HowToSceneBuilder.CLICK / 2);
        onTopLeft.add(new Hang(rifle, 0, 0, Pegboard.Turn.LEFT));
        s.blockData(topLeft, board(onTopLeft));
        s.sound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.6f, 0.8f);
        s.text(80, rifleAt, Side.LEFT,
                "Right-click with a weapon: it hangs where you click, snapped to the holes. A frame shows where it will go.");
        s.idle(85);
        // De sniper (10 x 3) past niet op één paneel: hij hangt over de twee bovenste.
        Vec3 sniperAt = new Vec3(2.1, 2.7, 1.1);
        s.rightClick(sniperAt, sniper);
        s.idle(HowToSceneBuilder.CLICK / 2);
        onTopLeft.add(new Hang(sniper, 2, 4, Pegboard.Turn.LEFT));
        s.blockData(topLeft, board(onTopLeft));
        s.sound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.6f, 0.8f);
        s.text(70, sniperAt, Side.RIGHT, "A sniper is 10 holes wide: it hangs across two panels.");
        s.idle(75);
        // Onderaan een Glock en magazijnen, rechts granaten, een holster en cellen.
        onBottomLeft.add(new Hang(glock, 1, 3, Pegboard.Turn.LEFT));
        s.blockData(bottomLeft, board(onBottomLeft));
        s.sound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.6f, 0.8f);
        s.idle(8);
        for (int i = 0; i < 3; i++) {
            onBottomLeft.add(new Hang(MagazineItem.of(FirearmType.GLOCK_19, 15), 5 + i, 3, Pegboard.Turn.LEFT));
        }
        s.blockData(bottomLeft, board(onBottomLeft));
        s.sound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.6f, 0.9f);
        s.idle(8);
        onBottomRight.add(new Hang(new ItemStack(ModItems.EMP_GRENADE.get()), 1, 4, Pegboard.Turn.LEFT));
        onBottomRight.add(new Hang(new ItemStack(ModItems.FRAG_GRENADE.get()), 4, 4, Pegboard.Turn.LEFT));
        onBottomRight.add(new Hang(new ItemStack(ModItems.HOLSTER.get()), 1, 0, Pegboard.Turn.LEFT));
        s.blockData(bottomRight, board(onBottomRight));
        s.sound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.6f, 1.0f);
        s.idle(8);
        onTopRight.add(new Hang(EnergyCellItem.withCharge(100), 3, 0, Pegboard.Turn.LEFT));
        onTopRight.add(new Hang(EnergyCellItem.withCharge(100), 5, 0, Pegboard.Turn.LEFT));
        s.blockData(topRight, board(onTopRight));
        s.sound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.6f, 1.1f);
        s.text(90, new Vec3(3.0, 1.7, 1.1), Side.RIGHT,
                "Every weapon fits: pistols and laser guns, magazines and cells, grenades, holsters, pouches and slings."
                        + " They stay exactly as they were.");
        s.idle(95);

        s.chapter("Take and turn");
        Vec3 glockAt = new Vec3(1.33, 1.5, 1.15);
        s.sneakRightClick(glockAt, ItemStack.EMPTY);
        s.idle(HowToSceneBuilder.CLICK / 2);
        onBottomLeft.set(0, new Hang(glock, 2, 2, Pegboard.Turn.DOWN));
        s.blockData(bottomLeft, board(onBottomLeft));
        s.sound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 0.6f, 1.0f);
        s.text(70, glockAt, Side.LEFT, "Sneak + right-click with an empty hand turns it: muzzle left, right, down or up.");
        s.idle(75);
        s.rightClick(glockAt, ItemStack.EMPTY);
        s.idle(HowToSceneBuilder.CLICK / 2);
        onBottomLeft.remove(0);
        s.blockData(bottomLeft, board(onBottomLeft));
        s.sound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.6f, 0.8f);
        s.text(70, glockAt, Side.LEFT, "Right-click with an empty hand takes it. Anyone can use a pegboard.");
        s.idle(75);

        s.chapter("The Ammo Box");
        BlockPos ammo = new BlockPos(3, 1, 2);
        Vec3 label = HowToSceneBuilder.face(ammo, Direction.SOUTH).add(0, -0.2, -0.25);
        ItemStack round = new ItemStack(ModItems.ROUND_9MM.get());
        s.place(ammo, facing(ModBlocks.AMMO_BOX.get(), Direction.SOUTH));
        s.idle(12);
        s.rightClick(label, round.copyWithCount(64));
        s.idle(HowToSceneBuilder.CLICK / 2);
        s.blockData(ammo, box(round, 64));
        s.sound(SoundEvents.BUNDLE_INSERT, 0.6f, 1.0f);
        s.text(80, label, Side.LEFT, "An *Ammo Box* holds one sort: rounds, casings, bullets, magazines or energy cells."
                + " Right-click puts the stack in your hand in.");
        s.idle(85);
        s.sneakRightClick(label, round.copyWithCount(64));
        s.idle(HowToSceneBuilder.CLICK / 2);
        s.blockData(ammo, box(round, 240));
        s.sound(SoundEvents.BUNDLE_INSERT, 0.6f, 0.9f);
        s.text(70, label, Side.LEFT, "Sneak + right-click: all of that sort from your inventory (up to %s).",
                Math.round(ImtConfig.Armory.AMMO_BOX_LOOSE));
        s.idle(75);
        s.rightClick(label, ItemStack.EMPTY);
        s.idle(HowToSceneBuilder.CLICK / 2);
        s.blockData(ammo, box(round, 176));
        s.sound(SoundEvents.BUNDLE_REMOVE_ONE, 0.6f, 1.0f);
        s.text(70, label, Side.LEFT, "An empty hand takes a stack (into your inventory), sneak takes one.");
        s.idle(75);

        s.chapter("Loading a magazine");
        ItemStack magazine = MagazineItem.of(FirearmType.GLOCK_19, 0);
        s.holdRight(90, magazine, label, label.add(0.02, 0, 0));
        s.text(90, label, Side.LEFT, "Hold right-click with a magazine on a box of rounds: it loads round by round, as fast"
                + " as R. Sneak puts them back.");
        for (int i = 1; i <= 12; i++) {
            s.idle(7);
            s.blockData(ammo, box(round, 176 - i));
            s.sound(ModSounds.GLOCK_ROUND_LOAD.get(), 0.4f, 1.0f);
        }
        s.idle(15);
        s.text(80, label, Side.LEFT, "A laser gun on a box of cells swaps your cell for the fullest one; a Glock on a box of"
                + " magazines swaps your magazine.");
        s.idle(85);

        s.chapter("Take it with you");
        s.remove(ammo);
        s.sound(SoundEvents.METAL_BREAK, 0.6f, 1.0f);
        s.item(80, Vec3.atCenterOf(ammo).add(0, 0.2, 0), new ItemStack(ModItems.AMMO_BOX.get()), 0.8f);
        s.text(80, Vec3.atCenterOf(ammo).add(0, 0.3, 0), Side.LEFT,
                "Mine the box and everything stays inside, like a shulker box. Its tooltip says what is in it.");
        s.idle(85);
        return List.of(s.build());
    }
}
