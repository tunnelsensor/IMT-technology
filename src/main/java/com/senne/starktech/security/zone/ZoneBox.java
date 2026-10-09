package com.senne.starktech.security.zone;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Eén kubus van een zone: alle blokken van {@code min} tot en met {@code max} (beide hoeken horen
 * erbij). Een zone bestaat uit een of meer van die kubussen (jouw keuze, 8 oktober: "meerdere
 * kubussen"), zo maak je een L-vorm of een toren die hoger is dan de rest.
 *
 * Hoe je de twee hoeken opgeeft maakt niet uit: de kubus zet zelf de kleinste x, y en z in
 * {@code min} en de grootste in {@code max}.
 */
public record ZoneBox(BlockPos min, BlockPos max) {
    /** Zo gaat een kubus over het netwerk (naar de client, die de rand tekent). */
    public static final StreamCodec<ByteBuf, ZoneBox> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ZoneBox::min,
            BlockPos.STREAM_CODEC, ZoneBox::max,
            ZoneBox::new);

    public ZoneBox {
        BlockPos low = new BlockPos(Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ()));
        BlockPos high = new BlockPos(Math.max(min.getX(), max.getX()), Math.max(min.getY(), max.getY()),
                Math.max(min.getZ(), max.getZ()));
        min = low;
        max = high;
    }

    /** Een kubus tussen twee hoeken (in welke volgorde ook). */
    public static ZoneBox of(BlockPos a, BlockPos b) {
        return new ZoneBox(a, b);
    }

    /** Hoort dit blok bij de kubus? */
    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public int sizeX() {
        return max.getX() - min.getX() + 1;
    }

    public int sizeY() {
        return max.getY() - min.getY() + 1;
    }

    public int sizeZ() {
        return max.getZ() - min.getZ() + 1;
    }

    /** Hoeveel blokken erin passen. */
    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    /** "12 × 6 × 20": breedte (x), hoogte (y) en diepte (z), zoals in de app en bij de Zone Marker. */
    public String size() {
        return sizeX() + " × " + sizeY() + " × " + sizeZ();
    }

    /** Het stuk van deze kubus dat tussen {@code low} en {@code high} valt, of null als er niets overblijft. */
    public @Nullable ZoneBox clamp(BlockPos low, BlockPos high) {
        int x1 = Math.max(min.getX(), low.getX());
        int y1 = Math.max(min.getY(), low.getY());
        int z1 = Math.max(min.getZ(), low.getZ());
        int x2 = Math.min(max.getX(), high.getX());
        int y2 = Math.min(max.getY(), high.getY());
        int z2 = Math.min(max.getZ(), high.getZ());
        if (x1 > x2 || y1 > y2 || z1 > z2) {
            return null;
        }
        return new ZoneBox(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2));
    }

    /** Het stuk dat deze kubus en {@code other} samen hebben, of null als ze elkaar niet raken. */
    public @Nullable ZoneBox overlap(ZoneBox other) {
        return clamp(other.min, other.max);
    }

    /** Ligt {@code other} helemaal in deze kubus? */
    public boolean contains(ZoneBox other) {
        return contains(other.min) && contains(other.max);
    }

    /**
     * Schuift één kant van de kubus {@code blocks} blokken naar buiten (negatief = naar binnen). De
     * kant {@link Direction#EAST} is de kant met de grootste x, {@link Direction#WEST} de kleinste,
     * enzovoort. Wordt de kubus zo kleiner dan één blok, dan geeft hij null.
     */
    public @Nullable ZoneBox grow(Direction side, int blocks) {
        BlockPos low = min;
        BlockPos high = max;
        if (side.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            high = high.relative(side, blocks);
        } else {
            low = low.relative(side, blocks);
        }
        if (low.getX() > high.getX() || low.getY() > high.getY() || low.getZ() > high.getZ()) {
            return null;
        }
        return new ZoneBox(low, high);
    }

    // ================================================================ Opslaan

    /** Zes getallen: x, y, z van de kleinste hoek, dan van de grootste. */
    public IntArrayTag save() {
        return new IntArrayTag(new int[] {min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()});
    }

    /** Terug uit de zes getallen van {@link #save()} (null als het er geen zes zijn). */
    public static @Nullable ZoneBox load(int[] numbers) {
        if (numbers.length != 6) {
            return null;
        }
        return new ZoneBox(new BlockPos(numbers[0], numbers[1], numbers[2]), new BlockPos(numbers[3], numbers[4], numbers[5]));
    }
}
