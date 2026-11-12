package com.senne.imttech.armory;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
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
 *
 * <b>Een hoek</b> (hoofdstuk 70, jouw wens: "een hoek van 90° van boards"): in een binnenhoek hangen twee panelen in
 * hetzelfde blok, elk tegen zijn eigen muur. Elk is een stukje van zijn eigen wand. Het eerste gaatje naast de andere
 * wand is dicht (daar zit dat paneel), en wat in de hoek uitsteekt mag niet door elkaar heen (zie {@link #fits}).
 *
 * <b>Vastzetten</b> (hoofdstuk 70): een vastgezet item laat een schaduw achter als je het pakt. Die houdt zijn plek vrij
 * voor hetzelfde soort ({@link #sameKind}); je hangt het terug door op de schaduw te klikken.
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
    /** Tot zo ver van de muur steekt een wapen uit (pixels): de voorkant van zijn kader. */
    public static final float FRONT = BACK + 3.5f;
    /**
     * In een hoek: zoveel gaatjes naast de andere wand liggen binnen {@link #FRONT} van die wand (3 x 2 = 6 pixels).
     * Daar kan een wapen tegen een wapen op de andere wand botsen.
     */
    public static final int CORNER_ZONE = 3;

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
        FirearmType firearm = FirearmItem.typeOf(stack);
        if (firearm != null) {
            // Hoofdstuk 103: een lang wapen neemt meer gaatjes in (met zijn magazijn eronder).
            return switch (firearm) {
                case GLOCK_19 -> new int[]{4, 3};
                case MP5 -> new int[]{8, 4};
            };
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
        // Hoofdstuk 72, stap 5 (jouw keuze): de rugzakken (met hun pouches) en de losse MOLLE-pouches.
        com.senne.imttech.backpack.BackpackItem.Size pack = com.senne.imttech.backpack.BackpackItem.sizeOf(stack);
        if (pack != null) {
            return switch (pack) {
                case ASSAULT -> new int[]{5, 6};
                case PATROL -> new int[]{6, 7};
                case RUCKSACK -> new int[]{7, 9};
            };
        }
        if (stack.getItem() instanceof com.senne.imttech.backpack.MolleItem) {
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

    /**
     * Past {@code held} op de schaduw van {@code shadow}? Jouw keuze (hoofdstuk 70): hetzelfde soort. Elke Glock op een
     * Glock-plek (welk materiaal ook), elk Glock-magazijn op een magazijnplek, elke Laser Rifle op een rifle-plek; de
     * rest (cellen, granaten, triggers, uitrusting) per item. Hetzelfde soort is altijd even groot.
     */
    public static boolean sameKind(ItemStack held, ItemStack shadow) {
        if (held.isEmpty() || shadow.isEmpty() || !hangable(held)) {
            return false;
        }
        GunType laser = LaserGunItem.typeOf(shadow);
        if (laser != null) {
            return laser == LaserGunItem.typeOf(held);
        }
        FirearmType firearm = FirearmItem.typeOf(shadow);
        if (firearm != null) {
            return firearm == FirearmItem.typeOf(held);
        }
        FirearmType magazine = MagazineItem.typeOf(shadow);
        if (magazine != null) {
            return magazine == MagazineItem.typeOf(held);
        }
        return held.is(shadow.getItem());
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

    /**
     * In een hoek: de kant op die het tweede paneel kijkt (null als er maar één paneel in dit blok hangt). Hangt het
     * tweede paneel tegen de muur links (zoals jij voor het eerste staat), dan kijkt het naar rechts, en andersom.
     */
    public static @Nullable Direction sideFacing(BlockState state) {
        if (!(state.getBlock() instanceof PegboardBlock)) {
            return null;
        }
        Direction facing = state.getValue(PegboardBlock.FACING);
        return switch (state.getValue(PegboardBlock.CORNER)) {
            case LEFT -> right(facing);
            case RIGHT -> right(facing).getOpposite();
            case NONE -> null;
        };
    }

    /** Hangt hier een paneel dat {@code facing} op kijkt (het eerste, of het tweede in een hoek)? */
    public static boolean isBoard(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof PegboardBlock
                && (state.getValue(PegboardBlock.FACING) == facing || sideFacing(state) == facing);
    }

    /** In een hoek: de andere wand van dit blok (null als er maar één paneel hangt, of als {@code wall} er niet bij hoort). */
    public static @Nullable Direction otherWall(BlockState state, Direction wall) {
        Direction side = sideFacing(state);
        if (side == null) {
            return null;
        }
        Direction facing = state.getValue(PegboardBlock.FACING);
        return wall == facing ? side : wall == side ? facing : null;
    }

    /**
     * Staat de muur van het paneel dat naar {@code other} kijkt rechts van {@code wall} (zoals jij voor {@code wall}
     * staat)? Een paneel hangt tegen de muur waar het van wegkijkt.
     */
    public static boolean otherOnRight(Direction wall, Direction other) {
        return other.getOpposite() == right(wall);
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

    /** Waar de muur achter de panelen van deze wand ligt, gemeten langs {@code facing} (in blokken). */
    private static double wallPlane(BlockPos pos, Direction facing) {
        return pos.getX() * facing.getStepX() + pos.getZ() * facing.getStepZ()
                - (facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 1 : 0);
    }

    /** Andersom: het punt in de wereld bij gaatje (U, V) van de wand, {@code depth} pixels voor de muur. */
    public static Vec3 pointOf(BlockPos pos, Direction facing, double cu, double cv, double depth) {
        Direction right = right(facing);
        double along = cu / CELLS;
        if (right.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            along -= 1;
        }
        double plane = wallPlane(pos, facing) + depth / 16.0;
        // "along" ligt langs de ene as (x of z), "plane" langs de andere.
        double x = right.getStepX() != 0 ? along * right.getStepX() : plane * facing.getStepX();
        double z = right.getStepZ() != 0 ? along * right.getStepZ() : plane * facing.getStepZ();
        return new Vec3(x, cv / CELLS, z);
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
        double plane = wallPlane(pos, facing) + depth / 16.0;
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

    /**
     * Ben je dichtbij genoeg om hier iets op te hangen, te pakken of vast te zetten? Jouw keuze (hoofdstuk 71): maximaal
     * 2 blokken ervan, dus het punt waar je klikt ligt hooguit {@link ImtConfig.Armory#PEGBOARD_REACH} (3) blokken van je
     * ogen.
     * De server geeft een klein beetje speling (jouw spel en de server zien je net niet op precies dezelfde plek).
     */
    public static boolean inReach(Player player, Vec3 point) {
        double reach = ImtConfig.Armory.PEGBOARD_REACH + (player.level().isClientSide ? 0 : 0.3);
        return player.getEyePosition().distanceToSqr(point) <= reach * reach;
    }

    // ================================================================ Wat er hangt

    /**
     * Een wapen op de wand: welk paneel het draagt, tegen welke muur ({@code wall}, in een hoek kan dat de tweede zijn),
     * en de rechthoek van gaatjes (U, V linksonder; breedte, hoogte). {@code shadow}: hier hangt niets, maar de plek is
     * vastgezet (hoofdstuk 70).
     */
    public record Spot(PegboardBlockEntity board, int index, Direction wall, int u, int v, int w, int h, boolean shadow) {
        public boolean contains(double cu, double cv) {
            return cu >= u && cu < u + w && cv >= v && cv < v + h;
        }

        public boolean overlaps(int ou, int ov, int ow, int oh) {
            return ou < u + w && u < ou + ow && ov < v + h && v < ov + oh;
        }

        public PegboardBlockEntity.Hung hung() {
            return board.hung().get(index);
        }
    }

    /** Het wapen (of de schaduw) dat over dit gaatje (U, V) van deze wand hangt, of null. */
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
                    if (board.wall(i) != facing) {
                        continue;
                    }
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
     * De schaduw onder dit gaatje als {@code held} erop past (hetzelfde soort), of null. Zo hang je een vastgezet item
     * terug: je klikt op zijn schaduw (jouw keuze), en het komt precies zoals het hing.
     */
    public static @Nullable Spot shadowFor(BlockGetter level, BlockPos pos, Direction facing, double cu, double cv, ItemStack held) {
        Spot spot = itemAt(level, pos, facing, cu, cv);
        return spot != null && spot.shadow() && sameKind(held, spot.hung().stack()) ? spot : null;
    }

    /**
     * Mag hier een rechthoek van gaatjes (U, V linksonder, {@code w} x {@code h}) hangen: overal een paneel, niets anders
     * in de weg (ook geen schaduw), en in een hoek niet tegen de andere wand of wat daar hangt? {@code ignore} telt niet
     * mee (het wapen dat je draait).
     */
    public static boolean fits(BlockGetter level, BlockPos pos, Direction facing, int u, int v, int w, int h, @Nullable Spot ignore) {
        return fitsBoards(level, pos, facing, u, v, w, h)
                && !overlapsAny(level, pos, facing, u, v, w, h, ignore)
                && clearOfCorners(level, pos, facing, u, v, w, h, ignore);
    }

    /** Hangt er op deze wand al iets (of een schaduw) over deze rechthoek? */
    private static boolean overlapsAny(BlockGetter level, BlockPos pos, Direction facing, int u, int v, int w, int h,
                                       @Nullable Spot ignore) {
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
                    if (board.wall(i) != facing) {
                        continue;
                    }
                    boolean same = ignore != null && ignore.board() == board && ignore.index() == i;
                    if (!same && board.spot(i).overlaps(u, v, w, h)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * De hoeken (hoofdstuk 70). Voor elk paneel van de rechthoek dat in een hoek hangt: het gaatje direct naast de andere
     * wand is dicht (daar zit het andere paneel, 1 pixel dik). De twee gaatjes daarna liggen binnen {@link #FRONT} van de
     * andere wand: daar mag iets hangen, zolang op de andere wand op dezelfde hoogte niets in zijn eigen hoekstrook hangt
     * (anders steken ze door elkaar heen). Verder van de hoek botst niets.
     */
    private static boolean clearOfCorners(BlockGetter level, BlockPos pos, Direction facing, int u, int v, int w, int h,
                                          @Nullable Spot ignore) {
        int col = column(pos, facing);
        for (int bu = Math.floorDiv(u, CELLS); bu <= Math.floorDiv(u + w - 1, CELLS); bu++) {
            for (int bv = Math.floorDiv(v, CELLS); bv <= Math.floorDiv(v + h - 1, CELLS); bv++) {
                BlockPos block = offset(pos, facing, bu - col, bv - pos.getY());
                Direction other = otherWall(level.getBlockState(block), facing);
                if (other == null) {
                    continue;
                }
                // De gaatjes van de rechthoek op dit paneel (0 tot 7 van links), en hoe dicht ze bij de andere wand komen.
                int x0 = Math.max(u, bu * CELLS) - bu * CELLS;
                int x1 = Math.min(u + w, bu * CELLS + CELLS) - 1 - bu * CELLS;
                int near = otherOnRight(facing, other) ? CELLS - 1 - x1 : x0;
                if (near == 0) {
                    return false;
                }
                if (near >= CORNER_ZONE) {
                    continue;
                }
                // De hoekstrook van de andere wand in dit blok (zijn gaatjes 1 en 2 van de hoek), op de hoogte van de rechthoek.
                int y0 = Math.max(v, bv * CELLS);
                int y1 = Math.min(v + h, bv * CELLS + CELLS);
                int otherCol = column(block, other) * CELLS;
                int zoneU = otherOnRight(other, facing) ? otherCol + CELLS - CORNER_ZONE : otherCol + 1;
                if (overlapsAny(level, block, other, zoneU, y0, CORNER_ZONE - 1, y1 - y0, ignore)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Mag er een tweede paneel bij in dit blok (een hoek, {@code joined} is de nieuwe stand)? Alleen als op het paneel
     * dat er al hangt niets in het gaatje naast de nieuwe muur hangt: daar komt het nieuwe paneel.
     */
    public static boolean cornerFree(BlockGetter level, BlockPos pos, BlockState joined) {
        Direction facing = joined.getValue(PegboardBlock.FACING);
        Direction side = sideFacing(joined);
        if (side == null) {
            return true;
        }
        int col = column(pos, facing) * CELLS;
        int u = otherOnRight(facing, side) ? col + CELLS - 1 : col;
        return !overlapsAny(level, pos, facing, u, pos.getY() * CELLS, 1, CELLS, null);
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
