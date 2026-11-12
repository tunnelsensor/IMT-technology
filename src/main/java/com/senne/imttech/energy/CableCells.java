package com.senne.imttech.energy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * De kabels in één chunk: gewoon een lijst blokplekken waar een stuk kabel ligt, en van elk stuk wie het legde.
 *
 * Een kabel is (sinds 27 september) geen blok meer maar een gegeven dat bij de chunk hoort (een
 * "attachment", net als je waypoints bij jou horen). Daardoor kan er op dezelfde plek ook een
 * blok staan: bouw je een muur over een kabel, dan loopt de kabel door de muur heen. Het spel
 * bewaart de lijst mee met de chunk en stuurt hem naar je spel, zodat je de kabels ziet.
 *
 * Sinds hoofdstuk 92 onthoudt elk stuk kabel wie het legde (de eigenaar): met de bril zie je alleen je eigen kabels door
 * een muur heen (en die in een zone waar je mag bouwen, zie CableSight). Een kabel van vóór 12 november, of uit een gebouw
 * van deze mod, is van niemand.
 *
 * Deze lijst verandert nooit: erbij zetten of weghalen geeft een nieuwe lijst terug (zie
 * PowerCables). Dat maakt het veilig om hem door te geven.
 */
public final class CableCells {
    public static final CableCells EMPTY = new CableCells(LongSets.EMPTY_SET, Long2ObjectMaps.emptyMap());

    /** De plekken van één eigenaar (om op te slaan en te versturen: zo staat elke eigenaar er maar één keer in). */
    private record Owned(UUID owner, long[] cells) {}

    /** Een rij plekken (elke blokplek als één getal, zie BlockPos.asLong). */
    private static final Codec<long[]> LONGS = Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream);

    private static final Codec<Owned> OWNED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(Owned::owner),
            LONGS.fieldOf("cells").forGetter(Owned::cells)).apply(instance, Owned::new));

    /** Opslaan sinds hoofdstuk 92: de plekken van niemand, en per eigenaar zijn plekken. */
    private static final Codec<CableCells> WITH_OWNERS = RecordCodecBuilder.create(instance -> instance.group(
            LONGS.optionalFieldOf("unowned", new long[0]).forGetter(CableCells::unowned),
            OWNED_CODEC.listOf().optionalFieldOf("owned", List.of()).forGetter(CableCells::owned))
            .apply(instance, CableCells::of));

    /** Zo stond het vóór hoofdstuk 92 opgeslagen: alleen een rij getallen. Die kabels zijn van niemand. */
    private static final Codec<CableCells> WITHOUT_OWNERS = LONGS.xmap(cells -> of(cells, List.of()), CableCells::unowned);

    /** Opslaan met de chunk. Een oude wereld (alleen plekken) lezen we ook nog. */
    public static final Codec<CableCells> CODEC = Codec.withAlternative(WITH_OWNERS, WITHOUT_OWNERS);

    /** Naar je spel sturen: hetzelfde (de plekken van niemand, dan per eigenaar zijn plekken). */
    public static final StreamCodec<ByteBuf, CableCells> STREAM_CODEC = StreamCodec.of(CableCells::write, CableCells::read);

    private final LongSet cells;
    /** Wie elk stuk kabel legde; een plek die er niet in staat, is van niemand. */
    private final Long2ObjectMap<UUID> owners;

    private CableCells(LongSet cells, Long2ObjectMap<UUID> owners) {
        this.cells = cells;
        this.owners = owners;
    }

    /** Een lijst uit de plekken van niemand en die van elke eigenaar. */
    private static CableCells of(long[] unowned, List<Owned> owned) {
        LongOpenHashSet cells = new LongOpenHashSet(unowned);
        Long2ObjectOpenHashMap<UUID> owners = new Long2ObjectOpenHashMap<>();
        for (Owned group : owned) {
            for (long cell : group.cells()) {
                cells.add(cell);
                owners.put(cell, group.owner());
            }
        }
        return cells.isEmpty() ? EMPTY : new CableCells(cells, owners);
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

    /** Wie het stuk kabel op deze plek legde, of null (van niemand, of er ligt hier geen). */
    public @Nullable UUID owner(BlockPos pos) {
        return owners.get(pos.asLong());
    }

    /** Een nieuwe lijst met deze plek erbij, van niemand. */
    public CableCells with(BlockPos pos) {
        return with(pos, null);
    }

    /** Een nieuwe lijst met deze plek erbij, gelegd door {@code owner} (null = van niemand). */
    public CableCells with(BlockPos pos, @Nullable UUID owner) {
        LongOpenHashSet copy = new LongOpenHashSet(cells);
        copy.add(pos.asLong());
        Long2ObjectOpenHashMap<UUID> ownersCopy = new Long2ObjectOpenHashMap<>(owners);
        if (owner == null) {
            ownersCopy.remove(pos.asLong());
        } else {
            ownersCopy.put(pos.asLong(), owner);
        }
        return new CableCells(copy, ownersCopy);
    }

    /** Een nieuwe lijst zonder deze plek. */
    public CableCells without(BlockPos pos) {
        LongOpenHashSet copy = new LongOpenHashSet(cells);
        copy.remove(pos.asLong());
        if (copy.isEmpty()) {
            return EMPTY;
        }
        Long2ObjectOpenHashMap<UUID> ownersCopy = new Long2ObjectOpenHashMap<>(owners);
        ownersCopy.remove(pos.asLong());
        return new CableCells(copy, ownersCopy);
    }

    /** Doe iets voor elke plek met een kabel. */
    public void forEach(Consumer<BlockPos> action) {
        cells.forEach((long packed) -> action.accept(BlockPos.of(packed)));
    }

    // ================================================================ Opslaan en versturen

    /** De plekken waarvan niemand de eigenaar is. */
    private long[] unowned() {
        LongArrayList list = new LongArrayList();
        cells.forEach((long cell) -> {
            if (!owners.containsKey(cell)) {
                list.add(cell);
            }
        });
        return list.toLongArray();
    }

    /** Per eigenaar zijn plekken. */
    private List<Owned> owned() {
        Map<UUID, LongArrayList> byOwner = new LinkedHashMap<>();
        for (Long2ObjectMap.Entry<UUID> entry : owners.long2ObjectEntrySet()) {
            byOwner.computeIfAbsent(entry.getValue(), owner -> new LongArrayList()).add(entry.getLongKey());
        }
        List<Owned> owned = new ArrayList<>(byOwner.size());
        byOwner.forEach((owner, list) -> owned.add(new Owned(owner, list.toLongArray())));
        return owned;
    }

    private static void write(ByteBuf buf, CableCells cells) {
        writeLongs(buf, cells.unowned());
        List<Owned> owned = cells.owned();
        VarInt.write(buf, owned.size());
        for (Owned group : owned) {
            UUIDUtil.STREAM_CODEC.encode(buf, group.owner());
            writeLongs(buf, group.cells());
        }
    }

    private static CableCells read(ByteBuf buf) {
        long[] unowned = readLongs(buf);
        int groups = VarInt.read(buf);
        List<Owned> owned = new ArrayList<>(Math.min(groups, 256));
        for (int i = 0; i < groups; i++) {
            UUID owner = UUIDUtil.STREAM_CODEC.decode(buf);
            owned.add(new Owned(owner, readLongs(buf)));
        }
        return of(unowned, owned);
    }

    private static void writeLongs(ByteBuf buf, long[] values) {
        VarInt.write(buf, values.length);
        for (long value : values) {
            VarLong.write(buf, value);
        }
    }

    private static long[] readLongs(ByteBuf buf) {
        int size = VarInt.read(buf);
        // Elk getal is minstens één byte: meer dan er nog in het bericht zit, kan niet.
        if (size < 0 || size > buf.readableBytes()) {
            throw new io.netty.handler.codec.DecoderException("Too many cable cells: " + size);
        }
        long[] values = new long[size];
        for (int i = 0; i < size; i++) {
            values[i] = VarLong.read(buf);
        }
        return values;
    }
}
