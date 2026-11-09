package com.senne.imttech.armory;

import com.senne.imttech.ImtTech;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * Een volle armory om te laten zien (hoofdstuk 67): voor de rondleiding met screenshots (-Ponly=armory) en de
 * showcase. Een wand van 5 x 2 Pegboards (met hun voorkant naar het noorden) vol wapens, magazijnen, cellen, granaten
 * en uitrusting, en vier Ammo Boxes ervoor: patronen, hulzen, magazijnen en cellen. Het paneel helemaal rechts blijft vrij, om
 * er zelf iets op te hangen.
 */
public final class ArmoryDisplay {
    private ArmoryDisplay() {}

    private static final Direction FACING = Direction.NORTH;

    /**
     * Bouwt de wand met {@code leftBottom} als paneel linksonder (de muur erachter moet er al staan: ten zuiden ervan),
     * en de kisten vanaf {@code firstBox} naar rechts (naar het westen).
     */
    public static void build(ServerLevel level, BlockPos leftBottom, BlockPos firstBox) {
        for (int du = 0; du < 5; du++) {
            for (int dv = 0; dv < 2; dv++) {
                level.setBlockAndUpdate(Pegboard.offset(leftBottom, FACING, du, dv),
                        ModBlocks.PEGBOARD.get().defaultBlockState().setValue(PegboardBlock.FACING, FACING));
            }
        }
        Pegboard.Turn left = Pegboard.Turn.LEFT;
        // Bovenaan de lange geweren.
        hang(level, leftBottom, 1, 11, laser(GunType.SNIPER), left);
        hang(level, leftBottom, 12, 11, laser(GunType.RIFLE), left);
        hang(level, leftBottom, 22, 11, laser(GunType.SHOTGUN), Pegboard.Turn.RIGHT);
        // In het midden de pistolen, magazijnen en cellen.
        hang(level, leftBottom, 1, 7, FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.STEEL), left);
        hang(level, leftBottom, 6, 6, laser(GunType.PISTOL), left);
        hang(level, leftBottom, 11, 6, FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.TITANIUM), Pegboard.Turn.DOWN);
        for (int i = 0; i < 3; i++) {
            hang(level, leftBottom, 15 + i, 7, MagazineItem.of(FirearmType.GLOCK_19, 15), left);
            hang(level, leftBottom, 19 + i, 7, EnergyCellItem.withCharge(100), left);
        }
        // Onderaan de granaten, de uitrusting en de triggers.
        Item[] grenades = {ModItems.EMP_GRENADE.get(), ModItems.FRAG_GRENADE.get(), ModItems.SMOKE_GRENADE.get(),
                ModItems.FLASHBANG.get(), ModItems.STICKY_EMP_GRENADE.get(), ModItems.STICKY_FRAG_GRENADE.get()};
        for (int i = 0; i < grenades.length; i++) {
            hang(level, leftBottom, 1 + 2 * i, 2, new ItemStack(grenades[i]), left);
        }
        hang(level, leftBottom, 14, 1, new ItemStack(ModItems.HOLSTER.get()), left);
        hang(level, leftBottom, 17, 2, new ItemStack(ModItems.MAG_POUCH.get()), left);
        hang(level, leftBottom, 20, 2, new ItemStack(ModItems.GRENADE_POUCH.get()), left);
        hang(level, leftBottom, 23, 1, new ItemStack(ModItems.GUN_SLING.get()), left);
        hang(level, leftBottom, 27, 1, new ItemStack(ModItems.EMP_TRIGGER.get()), left);
        hang(level, leftBottom, 28, 1, new ItemStack(ModItems.FRAG_TRIGGER.get()), left);
        // De Remote EMP en Remote Frag boven elkaar, met een gaatje ertussen (hun antenne vult hun rechthoek tot boven).
        hang(level, leftBottom, 29, 1, new ItemStack(ModItems.REMOTE_EMP.get()), left);
        hang(level, leftBottom, 29, 4, new ItemStack(ModItems.REMOTE_FRAG.get()), left);
        hang(level, leftBottom, 27, 6, new ItemStack(ModItems.ION_BLASTER.get()), left);

        ItemStack[][] contents = {
                {new ItemStack(ModItems.ROUND_9MM.get(), 64), new ItemStack(ModItems.ROUND_9MM.get(), 64),
                        new ItemStack(ModItems.ROUND_9MM.get(), 64), new ItemStack(ModItems.ROUND_9MM.get(), 48)},
                {new ItemStack(ModItems.CASING_9MM.get(), 57)},
                {MagazineItem.of(FirearmType.GLOCK_19, 15), MagazineItem.of(FirearmType.GLOCK_19, 15),
                        MagazineItem.of(FirearmType.GLOCK_19, 8), MagazineItem.of(FirearmType.GLOCK_19, 0),
                        MagazineItem.of(FirearmType.GLOCK_19, 12)},
                {EnergyCellItem.withCharge(100), EnergyCellItem.withCharge(64), EnergyCellItem.withCharge(20),
                        EnergyCellItem.withCharge(100)}};
        for (int i = 0; i < contents.length; i++) {
            BlockPos at = firstBox.west(i);
            level.setBlockAndUpdate(at, ModBlocks.AMMO_BOX.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, FACING));
            if (level.getBlockEntity(at) instanceof AmmoBoxBlockEntity box) {
                box.clear();
                for (ItemStack stack : contents[i]) {
                    box.insert(stack.copy(), stack.getCount());
                }
            }
        }
    }

    private static ItemStack laser(GunType type) {
        ItemStack gun = LaserGunItem.complete(type, GunMaterial.TITANIUM);
        LaserGunItem.setCell(gun, 100);
        return gun;
    }

    /** Hangt een item met zijn gaatje linksonder op ({@code u}, {@code v}) van de wand die bij {@code leftBottom} begint. */
    private static void hang(ServerLevel level, BlockPos leftBottom, int u, int v, ItemStack stack, Pegboard.Turn turn) {
        int[] size = Pegboard.size(stack, turn);
        double cu = Pegboard.column(leftBottom, FACING) * Pegboard.CELLS + u + size[0] / 2.0;
        double cv = leftBottom.getY() * Pegboard.CELLS + v + size[1] / 2.0;
        if (!PegboardBlock.hangAt(null, level, leftBottom, FACING, cu, cv, stack, turn)) {
            ImtTech.LOGGER.warn("[armory] no room for {} at {}, {}", stack.getHoverName().getString(), u, v);
        }
    }
}
