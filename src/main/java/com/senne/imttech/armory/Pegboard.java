package com.senne.imttech.armory;

import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.GrenadeItem;
import com.senne.imttech.weapons.GunSlingItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import com.senne.imttech.weapons.RemoteChargeItem;
import com.senne.imttech.weapons.TriggerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De rekensommen van het Pegboard (hoofdstuk 67, jouw keuze: "een paneel aan de muur; je hangt een wapen op de plek
 * waar je klikt, en het klikt vast op een raster. Panelen naast elkaar worden één grote wand").
 *
 * <b>Het raster</b>: elk paneel heeft 8 x 8 gaatjes (om de 2 pixels). Een wapen neemt een rechthoek van gaatjes in
 * ({@link #size}: een sniper 10 breed en 3 hoog, een Glock 4 x 3, een granaat 2 x 2...). Het hoort bij het paneel waar
 * zijn gaatje linksonder in zit, maar mag over de panelen rechts en boven hangen, als daar ook een paneel hangt (zo past
 * een sniper over twee panelen).
 *
 * <b>De wand</b>: alle panelen die in hetzelfde vlak dezelfde kant op kijken. We tellen gaatjes over de hele wand: U
 * naar rechts (zoals jij ervoor staat) en V naar boven. Een paneel op blok (u, v) van de wand heeft de gaatjes
 * 8u..8u+7 en 8v..8v+7.
 */
public final class Pegboard {
    private Pegboard() {}

    /** Gaatjes per paneel in elke richting (om de 2 pixels). */
    public static final int CELLS = 8;
    /** Zo ver (in panelen) kan een wapen naar rechts of boven buiten zijn eigen paneel hangen: de sniper is 10 gaatjes. */
    public static final int REACH = 2;
    /** Hoe dik het paneel is (pixels); de wapens hangen er een klein beetje voor, aan hun haakjes. */
    public static final float THICKNESS = 1.0f;
    /** De achterkant van een wapen hangt zo ver van de muur (pixels). */
    public static final float BACK = 1.7f;

    /** Hoe een wapen hangt: de loop naar links (de linkerkant naar jou), naar rechts, naar beneden of naar boven. */
    public enum Turn {
        LEFT, RIGHT, DOWN, UP;

        public boolean vertical() {
            return this == DOWN || this == UP;
        }

        /** Sneak + rechtsklik: de volgende stand. */
        public Turn next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Turn byId(int id) {
            return values()[Math.floorMod(id, values().length)];
        }
    }

    // ================================================================ Wat er op mag, en hoe groot

    /**
     * Hoeveel gaatjes dit item breed en hoog is als het met de loop naar links hangt (gedraaid wisselen ze), of null als
     * het niet op het Pegboard mag. Jouw keuze: alle wapens, magazijnen en cellen, granaten, en holster, pouches en sling.
     * De Ion Blaster telt als wapen (mijn invulling).
     */
    public static int @Nullable [] size(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        GunType laser = LaserGunItem.typeOf(stack);
        if (laser != null) {
            return switch (laser) {
                case PISTOL -> new int[]{4, 4};
                case RIFLE -> new int[]{8, 3};
                case SNIPER -> new int[]{10, 3};
                case SHOTGUN -> new int[]{7, 3};
            };
        }
        if (FirearmItem.typeOf(stack) != null) {
            return new int[]{4, 3};
        }
        if (MagazineItem.typeOf(stack) != null || stack.is(ModItems.ENERGY_CELL.get())) {
            return new int[]{1, 3};
        }
        // Hoofdstuk 69: geen scherpe (opgeraapte) granaat of lading: aan de wand zou zijn lont stilstaan.
        if (GrenadeItem.isLive(stack) || RemoteChargeItem.isLive(stack)) {
            return null;
        }
        if (stack.getItem() instanceof GrenadeItem || stack.getItem() instanceof RemoteChargeItem) {
            return new int[]{2, 2};
        }
        if (stack.getItem() instanceof TriggerItem) {
            return new int[]{1, 4};
        }
        if (stack.getItem() instanceof GearItem gear) {
            return gear.kind() == GearItem.Kind.HOLSTER ? new int[]{2, 3} : new int[]{2, 2};
        }
        if (stack.getItem() instanceof GunSlingItem) {
            return new int[]{3, 3};
        }
        if (stack.is(ModItems.ION_BLASTER.get())) {
            return new int[]{4, 4};
        }
        return null;
    }

    public static boolean hangable(ItemStack stack) {
        return size(stack) != null;
    }

    /** Breedte en hoogte in gaatjes zoals het hangt (gedraaid: omgewisseld). */
    public static int[] size(ItemStack stack, Turn turn) {
        int[] size = size(stack);
        if (size == null) {
            return new int[]{1, 1};
        }
        return turn.vertical() ? new int[]{size[1], size[0]} : size;
    }

    // ================================================================ De wand

    /** Naar rechts, zoals jij voor een paneel staat dat naar {@code facing} kijkt. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** De kolom van dit blok op de wand (in panelen). */
    public static int column(BlockPos pos, Direction facing) {
        Direction right = right(facing);
        return pos.getX() * right.getStepX() + pos.getZ() * right.getStepZ();
    }

    /** Het blok op de wand {@code du} panelen rechts en {@code dv} boven {@code from}. */
    public static BlockPos offset(BlockPos from, Direction facing, int du, int dv) {
        return from.relative(right(facing), du).above(dv);
    }

    /** Hangt hier een paneel dat dezelfde kant op kijkt? */
    public static boolean isBoard(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof PegboardBlock && state.getValue(PegboardBlock.FACING) == facing;
    }

    /**
     * Waar dit punt (in de wereld) op de wand ligt, in gaatjes: {U, V}. Het blok {@code pos} geeft het vlak; het punt
     * mag er een beetje voor liggen.
     */
    public static double[] cellOf(Vec3 point, BlockPos pos, Direction facing) {
        Direction right = right(facing);
        double along = point.x * right.getStepX() + point.z * right.getStepZ();
        if (right.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            along += 1;
        }
        return new double[]{along * CELLS, point.y * CELLS};
    }

    /**
     * Waar je naar kijkt op de wand: waar je kijklijn het vlak kruist op {@code depth} pixels voor de muur (daar hangen de
     * wapens; het paneel zelf ligt er net achter, dus zonder dit klik je schuin van opzij een beetje naast). Kijk je
     * van de wand weg, dan het punt waar je het paneel raakte.
     */
    public static double[] aim(Player player, BlockPos pos, Direction facing, Vec3 hit, double depth) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 normal = new Vec3(facing.getStepX(), 0, facing.getStepZ());
        double wall = pos.getX() * normal.x + pos.getZ() * normal.z
                - (facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 1 : 0);
        double plane = wall + depth / 16.0;
        double toward = look.dot(normal);
        if (toward < -1.0e-4) {
            double t = (plane - eye.dot(normal)) / toward;
            if (t > 0) {
                Vec3 point = eye.add(look.scale(t));
                if (point.distanceTo(hit) < 1.0) {
                    return cellOf(point, pos, facing);
                }
            }
        }
        return cellOf(hit, pos, facing);
    }

    // ================================================================ Wat er hangt

    /** Een wapen op de wand: welk paneel het draagt, en de rechthoek van gaatjes (U, V linksonder; breedte, hoogte). */
    public record Spot(PegboardBlockEntity board, int index, int u, int v, int w, int h) {
        public boolean contains(double cu, double cv) {
            return cu >= u && cu < u + w && cv >= v && cv < v + h;
        }

        public boolean overlaps(int ou, int ov, int ow, int oh) {
            return ou < u + w && u < ou + ow && ov < v + h && v < ov + oh;
        }
    }

    /** Het wapen dat over dit gaatje (U, V) hangt, of null. */
    public static @Nullable Spot itemAt(BlockGetter level, BlockPos pos, Direction facing, double cu, double cv) {
        int bu = Math.floorDiv((int) Math.floor(cu), CELLS);
        int bv = Math.floorDiv((int) Math.floor(cv), CELLS);
        BlockPos base = offset(pos, facing, bu - column(pos, facing), bv - pos.getY());
        Spot found = null;
        for (int du = -REACH; du <= 0; du++) {
            for (int dv = -REACH; dv <= 0; dv++) {
                BlockPos anchor = offset(base, facing, du, dv);
                if (!isBoard(level, anchor, facing) || !(level.getBlockEntity(anchor) instanceof PegboardBlockEntity board)) {
                    continue;
                }
                for (int i = 0; i < board.hung().size(); i++) {
                    Spot spot = board.spot(i);
                    if (spot.contains(cu, cv)) {
                        found = spot;
                    }
                }
            }
        }
        return found;
    }

    /**
     * Mag hier een rechthoek van gaatjes (U, V linksonder, {@code w} x {@code h}) hangen: overal een paneel, en niets
     * anders in de weg? {@code ignore} telt niet mee (het wapen dat je draait).
     */
    public static boolean fits(BlockGetter level, BlockPos pos, Direction facing, int u, int v, int w, int h, @Nullable Spot ignore) {
        if (!fitsBoards(level, pos, facing, u, v, w, h)) {
            return false;
        }
        int col = column(pos, facing);
        int firstU = Math.floorDiv(u, CELLS);
        int lastU = Math.floorDiv(u + w - 1, CELLS);
        int firstV = Math.floorDiv(v, CELLS);
        int lastV = Math.floorDiv(v + h - 1, CELLS);
        for (int bu = firstU - REACH; bu <= lastU; bu++) {
            for (int bv = firstV - REACH; bv <= lastV; bv++) {
                BlockPos anchor = offset(pos, facing, bu - col, bv - pos.getY());
                if (!isBoard(level, anchor, facing) || !(level.getBlockEntity(anchor) instanceof PegboardBlockEntity board)) {
                    continue;
                }
                for (int i = 0; i < board.hung().size(); i++) {
                    Spot spot = board.spot(i);
                    boolean same = ignore != null && ignore.board() == board && ignore.index() == i;
                    if (!same && spot.overlaps(u, v, w, h)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Hangt er achter elk gaatje van deze rechthoek een paneel (dat dezelfde kant op kijkt)? */
    public static boolean fitsBoards(BlockGetter level, BlockPos pos, Direction facing, int u, int v, int w, int h) {
        int col = column(pos, facing);
        for (int bu = Math.floorDiv(u, CELLS); bu <= Math.floorDiv(u + w - 1, CELLS); bu++) {
            for (int bv = Math.floorDiv(v, CELLS); bv <= Math.floorDiv(v + h - 1, CELLS); bv++) {
                if (!isBoard(level, offset(pos, facing, bu - col, bv - pos.getY()), facing)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Waar een wapen van {@code w} x {@code h} gaatjes komt als je op gaatje (U, V) klikt: met zijn midden op de klik,
     * en past dat niet, het dichtstbijzijnde plekje tot 2 gaatjes ernaast. Geeft {U, V} linksonder, of null.
     */
    public static int @Nullable [] place(BlockGetter level, BlockPos pos, Direction facing, double cu, double cv, int w, int h,
                                         @Nullable Spot ignore) {
        int u0 = (int) Math.round(cu - w / 2.0);
        int v0 = (int) Math.round(cv - h / 2.0);
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int du = -2; du <= 2; du++) {
            for (int dv = -2; dv <= 2; dv++) {
                int distance = du * du + dv * dv;
                if (distance >= bestDistance || !fits(level, pos, facing, u0 + du, v0 + dv, w, h, ignore)) {
                    continue;
                }
                best = new int[]{u0 + du, v0 + dv};
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Het blok van de wand waar gaatje (U, V) in zit. */
    public static BlockPos boardAt(BlockPos pos, Direction facing, int u, int v) {
        return offset(pos, facing, Math.floorDiv(u, CELLS) - column(pos, facing), Math.floorDiv(v, CELLS) - pos.getY());
    }
}
