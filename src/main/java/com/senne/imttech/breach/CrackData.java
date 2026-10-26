package com.senne.imttech.c4;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * De barsten in de wereld (hoofdstuk 11, 9 oktober: barsten herstellen). Sinds dit hoofdstuk genezen
 * barsten niet meer vanzelf: ze blijven tot je ze opvult met Repair Paste of het blok vervangt. Daarom
 * staan ze hier, in de wereld (data/imttech_cracks.dat van de overworld), en niet meer alleen in het
 * geheugen van de server.
 *
 * Per blok: hoeveel breekpunten het al kreeg, welk blok het was, en hoe vaak het geplamuurd is (elke
 * keer wat zwakker, zie {@link Cracks#factor}). Komt er een ander blok te staan, dan vergeten we het
 * meteen (zie mixin/LevelChunkMixin). Het rekenwerk staat in {@link Cracks}; dit is alleen het geheugen.
 */
public final class CrackData extends SavedData {
    private static final String NAME = "imttech_cracks";
    private static final SavedData.Factory<CrackData> FACTORY = new SavedData.Factory<>(CrackData::new, CrackData::load);

    /**
     * Wat er met één blok gebeurd is.
     *
     * @param damage  de breekpunten die het al kreeg (0 = geen barsten, bijvoorbeeld net geplamuurd)
     * @param block   welk blok het was
     * @param patches hoe vaak het geplamuurd is
     */
    public record Crack(float damage, Block block, int patches) {}

    /** Per wereld (overworld, nether...): plek (als getal, zie BlockPos.asLong) -> wat er gebeurd is. */
    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Crack>> cracks = new HashMap<>();

    public static CrackData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static CrackData get(ServerLevel level) {
        return get(level.getServer());
    }

    /** Wat er met het blok op deze plek gebeurd is, als het nog hetzelfde blok is; anders null. */
    public @Nullable Crack at(ServerLevel level, BlockPos pos) {
        Long2ObjectOpenHashMap<Crack> here = cracks.get(level.dimension());
        Crack crack = here == null ? null : here.get(pos.asLong());
        return crack == null || crack.block() != level.getBlockState(pos).getBlock() ? null : crack;
    }

    public void put(ServerLevel level, BlockPos pos, Crack crack) {
        cracks.computeIfAbsent(level.dimension(), key -> new Long2ObjectOpenHashMap<>()).put(pos.asLong(), crack);
        setDirty();
    }

    /** Alles over deze plek vergeten. Geeft terug wat er stond (of null). */
    public @Nullable Crack remove(ServerLevel level, BlockPos pos) {
        Long2ObjectOpenHashMap<Crack> here = cracks.get(level.dimension());
        Crack removed = here == null ? null : here.remove(pos.asLong());
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    /** Alle plekken in deze wereld (niet aanpassen: alleen kijken). */
    public Long2ObjectMap<Crack> in(ResourceKey<Level> dimension) {
        Long2ObjectOpenHashMap<Crack> here = cracks.get(dimension);
        return here == null ? Long2ObjectMaps.emptyMap() : here;
    }

    /** Is er ergens in deze wereld iets? (Snel, want LevelChunkMixin vraagt het bij elk blok dat verandert.) */
    public boolean any(ResourceKey<Level> dimension) {
        Long2ObjectOpenHashMap<Crack> here = cracks.get(dimension);
        return here != null && !here.isEmpty();
    }

    // ================================================================ Opslaan

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<ResourceKey<Level>, Long2ObjectOpenHashMap<Crack>> perLevel : cracks.entrySet()) {
            for (Long2ObjectMap.Entry<Crack> entry : perLevel.getValue().long2ObjectEntrySet()) {
                CompoundTag item = new CompoundTag();
                item.putString("Dimension", perLevel.getKey().location().toString());
                item.putLong("Pos", entry.getLongKey());
                item.putString("Block", BuiltInRegistries.BLOCK.getKey(entry.getValue().block()).toString());
                item.putFloat("Damage", entry.getValue().damage());
                item.putInt("Patches", entry.getValue().patches());
                list.add(item);
            }
        }
        tag.put("Cracks", list);
        return tag;
    }

    public static CrackData load(CompoundTag tag, HolderLookup.Provider registries) {
        CrackData data = new CrackData();
        for (Tag element : tag.getList("Cracks", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) element;
            ResourceLocation dimension = ResourceLocation.tryParse(item.getString("Dimension"));
            ResourceLocation id = ResourceLocation.tryParse(item.getString("Block"));
            Block block = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id);
            if (dimension != null && block != Blocks.AIR) {
                data.cracks.computeIfAbsent(ResourceKey.create(Registries.DIMENSION, dimension), key -> new Long2ObjectOpenHashMap<>())
                        .put(item.getLong("Pos"), new Crack(item.getFloat("Damage"), block, item.getInt("Patches")));
            }
        }
        return data;
    }

    /** Voor de tests: wat er na opslaan en weer inladen op deze plek staat. */
    public static @Nullable Crack afterReload(CrackData data, ResourceKey<Level> dimension, BlockPos pos,
                                              HolderLookup.Provider registries) {
        Long2ObjectOpenHashMap<Crack> here = load(data.save(new CompoundTag(), registries), registries).cracks.get(dimension);
        return here == null ? null : here.get(pos.asLong());
    }
}
