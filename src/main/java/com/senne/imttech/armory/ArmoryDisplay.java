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
 *
 * Hoofdstuk 70: links staat een zijmuur (naar het oosten, 3 blokken diep) met ook 3 x 2 panelen; de linkerkolom van de
 * wand is een hoek (twee panelen in een blok). Op de zijmuur een vastgezette rifle (met zijn grijze omtrek eromheen) en de
 * schaduw van een shotgun die iemand pakte. Hoofdstuk 71: de granaten daar zijn allemaal vastgezet, en de flashbang, de
 * frag en de EMP zijn weg: hun schaduwen hebben elk hun eigen kleur.
 */
public final class ArmoryDisplay {
    private ArmoryDisplay() {}

    private static final Direction FACING = Direction.NORTH;

    /** De zijmuur links (hoofdstuk 70): zijn panelen kijken naar het westen. */
    private static final Direction SIDE = Direction.WEST;

    /**
     * Bouwt de wand met {@code leftBottom} als paneel linksonder (de muur erachter moet er al staan: ten zuiden ervan,
     * en de zijmuur ten oosten van de linkerkolom, 3 blokken naar het noorden), en de kisten vanaf {@code firstBox} naar
     * rechts (naar het westen).
     */
    public static void build(ServerLevel level, BlockPos leftBottom, BlockPos firstBox) {
        for (int du = 0; du < 5; du++) {
            for (int dv = 0; dv < 2; dv++) {
                // De linkerkolom is een hoek: daar hangt ook een paneel tegen de zijmuur.
                PegboardBlock.Corner corner = du == 0 ? PegboardBlock.Corner.LEFT : PegboardBlock.Corner.NONE;
                level.setBlockAndUpdate(Pegboard.offset(leftBottom, FACING, du, dv), ModBlocks.PEGBOARD.get().defaultBlockState()
                        .setValue(PegboardBlock.FACING, FACING).setValue(PegboardBlock.CORNER, corner));
            }
        }
        // De rest van de zijmuur: nog twee kolommen naar het noorden.
        BlockPos sideLeftBottom = leftBottom.north(2);
        for (int dz = 1; dz <= 2; dz++) {
            for (int dv = 0; dv < 2; dv++) {
                level.setBlockAndUpdate(leftBottom.north(dz).above(dv), ModBlocks.PEGBOARD.get().defaultBlockState()
                        .setValue(PegboardBlock.FACING, SIDE));
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
        // De Remote EMP op dezelfde rij als de sticky's (zijn bol hangt even hoog), de Remote Frag erboven met een gaatje
        // ertussen (hun antenne steekt boven hun rechthoek uit), en daarboven de Ion Blaster.
        hang(level, leftBottom, 29, 2, new ItemStack(ModItems.REMOTE_EMP.get()), left);
        hang(level, leftBottom, 29, 5, new ItemStack(ModItems.REMOTE_FRAG.get()), left);
        hang(level, leftBottom, 27, 7, new ItemStack(ModItems.ION_BLASTER.get()), left);

        // De zijmuur (hoofdstuk 70). Gaatjes 21 tot 23 liggen in de hoek: daar hangt niets (op de wand hangt daar al wat).
        hangSide(level, sideLeftBottom, 1, 11, laser(GunType.RIFLE), left);
        hangSide(level, sideLeftBottom, 11, 11, laser(GunType.SHOTGUN), left);
        lock(level, sideLeftBottom, 1, 11);
        lock(level, sideLeftBottom, 11, 11);
        takeAt(level, sideLeftBottom, 11, 11); // iemand pakte de shotgun: zijn schaduw blijft
        hangSide(level, sideLeftBottom, 1, 7, FirearmItem.loaded(FirearmType.GLOCK_19, GunMaterial.TITANIUM), left);
        hangSide(level, sideLeftBottom, 6, 6, laser(GunType.PISTOL), left);
        for (int i = 0; i < 3; i++) {
            hangSide(level, sideLeftBottom, 11 + i, 7, MagazineItem.of(FirearmType.GLOCK_19, 15), left);
        }
        hangSide(level, sideLeftBottom, 15, 7, EnergyCellItem.withCharge(100), left);
        hangSide(level, sideLeftBottom, 16, 7, EnergyCellItem.withCharge(100), left);
        Item[] sideGrenades = {ModItems.FLASHBANG.get(), ModItems.SMOKE_GRENADE.get(), ModItems.FRAG_GRENADE.get(),
                ModItems.STICKY_EMP_GRENADE.get(), ModItems.EMP_GRENADE.get()};
        for (int i = 0; i < sideGrenades.length; i++) {
            hangSide(level, sideLeftBottom, 1 + 3 * i, 2, new ItemStack(sideGrenades[i]), left);
            lock(level, sideLeftBottom, 1 + 3 * i, 2);
        }
        // Hoofdstuk 71: de flashbang, de frag en de EMP zijn gepakt; hun schaduwen hebben elk hun eigen kleur.
        takeAt(level, sideLeftBottom, 1, 2);
        takeAt(level, sideLeftBottom, 7, 2);
        takeAt(level, sideLeftBottom, 13, 2);
        hangSide(level, sideLeftBottom, 16, 1, new ItemStack(ModItems.HOLSTER.get()), left);

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
        hangOn(level, leftBottom, FACING, u, v, stack, turn);
    }

    /** Hetzelfde op de zijmuur (hoofdstuk 70), die bij {@code sideLeftBottom} begint. */
    private static void hangSide(ServerLevel level, BlockPos sideLeftBottom, int u, int v, ItemStack stack, Pegboard.Turn turn) {
        hangOn(level, sideLeftBottom, SIDE, u, v, stack, turn);
    }

    private static void hangOn(ServerLevel level, BlockPos leftBottom, Direction wall, int u, int v, ItemStack stack, Pegboard.Turn turn) {
        int[] size = Pegboard.size(stack, turn);
        double cu = Pegboard.column(leftBottom, wall) * Pegboard.CELLS + u + size[0] / 2.0;
        double cv = leftBottom.getY() * Pegboard.CELLS + v + size[1] / 2.0;
        if (!PegboardBlock.hangAt(null, level, leftBottom, wall, cu, cv, stack, turn)) {
            ImtTech.LOGGER.warn("[armory] no room for {} at {}, {}", stack.getHoverName().getString(), u, v);
        }
    }

    /** Het gaatje (u, v) van de zijmuur als gaatje op de hele wand: {U, V}, net binnen het item dat daar linksonder begint. */
    private static double[] sideCell(BlockPos sideLeftBottom, int u, int v) {
        return new double[]{Pegboard.column(sideLeftBottom, SIDE) * Pegboard.CELLS + u + 0.5,
                sideLeftBottom.getY() * Pegboard.CELLS + v + 0.5};
    }

    /** Zet het item dat op de zijmuur bij (u, v) begint vast. */
    private static void lock(ServerLevel level, BlockPos sideLeftBottom, int u, int v) {
        double[] cell = sideCell(sideLeftBottom, u, v);
        PegboardBlock.toggleLock(null, level, sideLeftBottom, SIDE, cell[0], cell[1]);
    }

    /** Pakt het item dat op de zijmuur bij (u, v) begint (het verdwijnt; vastgezet blijft zijn schaduw). */
    private static void takeAt(ServerLevel level, BlockPos sideLeftBottom, int u, int v) {
        double[] cell = sideCell(sideLeftBottom, u, v);
        Pegboard.Spot spot = Pegboard.itemAt(level, sideLeftBottom, SIDE, cell[0], cell[1]);
        if (spot != null) {
            spot.board().take(spot.index());
        }
    }
}
