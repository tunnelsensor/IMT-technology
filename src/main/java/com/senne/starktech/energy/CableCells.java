package com.senne.starktech.energy;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * De kabels in één chunk: gewoon een lijst blokplekken waar een stuk kabel ligt.
 *
 * Een kabel is (sinds 27 september) geen blok meer maar een gegeven dat bij de chunk hoort (een
 * "attachment", net als je waypoints bij jou horen). Daardoor kan er op dezelfde plek ook een
 * blok staan: bouw je een muur over een kabel, dan loopt de kabel door de muur heen. Het spel
 * bewaart de lijst mee met de chunk en stuurt hem naar je spel, zodat je de kabels ziet.
 *
 * Deze lijst verandert nooit: erbij zetten of weghalen geeft een nieuwe lijst terug (zie
 * StarkCables). Dat maakt het veilig om hem door te geven.
 */
public final class CableCells {
    public static final CableCells EMPTY = new CableCells(LongSets.EMPTY_SET);

    /** Opslaan: een rij getallen (elke blokplek als één getal, zie BlockPos.asLong). */
    public static final Codec<CableCells> CODEC = Codec.LONG_STREAM.xmap(
            stream -> new CableCells(new LongOpenHashSet(stream.toArray())),
            cells -> cells.cells.longStream());

    /** Naar je spel sturen: hetzelfde, als lijst. */
    public static final StreamCodec<ByteBuf, CableCells> STREAM_CODEC = ByteBufCodecs.VAR_LONG
            .apply(ByteBufCodecs.list())
            .map(list -> {
                LongOpenHashSet set = new LongOpenHashSet();
                list.forEach(set::add);
                return new CableCells(set);
            }, cells -> new java.util.ArrayList<>(cells.cells));

    private final LongSet cells;

    private CableCells(LongSet cells) {
        this.cells = cells;
    }

    public boolean contains(BlockPos pos) {
        return cells.contains(pos.asLong());
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    public int size() {
        return cells.size();
    }

    /** Een nieuwe lijst met deze plek erbij. */
    public CableCells with(BlockPos pos) {
        LongOpenHashSet copy = new LongOpenHashSet(cells);
        copy.add(pos.asLong());
        return new CableCells(copy);
    }

    /** Een nieuwe lijst zonder deze plek. */
    public CableCells without(BlockPos pos) {
        LongOpenHashSet copy = new LongOpenHashSet(cells);
        copy.remove(pos.asLong());
        return copy.isEmpty() ? EMPTY : new CableCells(copy);
    }

    /** Doe iets voor elke plek met een kabel. */
    public void forEach(Consumer<BlockPos> action) {
        cells.forEach((long packed) -> action.accept(BlockPos.of(packed)));
    }
}
