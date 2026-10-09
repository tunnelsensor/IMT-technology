package com.senne.starktech.security;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * De instellingen van alle ingepakte beveiligingskernen: kernen die weggehakt (of opgeblazen) zijn en
 * nu als item in iemands rugzak, in een kist of op de grond liggen. Op het item zelf staat alleen het
 * nummer (zie {@link PackedCore}); hier staat wat erbij hoort.
 *
 * Zet je de kern weer neer, dan haalt hij zijn instellingen hier weg (zie {@link #take}): één kern,
 * één keer neerzetten. Verbrandt het item in lava of verdwijnt het na vijf minuten op de grond, dan
 * blijft het hier staan, maar niemand kan er nog bij.
 *
 * Dit is "SavedData": Minecraft bewaart het in de wereld (data/starktech_packed_cores.dat van de
 * overworld), voor alle dimensies samen.
 */
public final class PackedCores extends SavedData {
    private static final String NAME = "starktech_packed_cores";

    private final Map<UUID, CompoundTag> packed = new HashMap<>();

    public static PackedCores get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PackedCores::new, PackedCores::load), NAME);
    }

    public static PackedCores get(ServerLevel level) {
        return get(level.getServer());
    }

    /** Bewaart de instellingen van een kern die als item uitvalt. Geeft het nummer terug (voor op het item). */
    public UUID store(CompoundTag config) {
        UUID id = UUID.randomUUID();
        packed.put(id, config.copy());
        setDirty();
        return id;
    }

    /** Haalt de instellingen met dit nummer eruit (en weg). Null als ze er niet (meer) zijn. */
    public @Nullable CompoundTag take(UUID id) {
        CompoundTag config = packed.remove(id);
        if (config != null) {
            setDirty();
        }
        return config;
    }

    /** Staan er instellingen onder dit nummer? (Voor de tests.) */
    public boolean has(UUID id) {
        return packed.containsKey(id);
    }

    // ================================================================ Opslaan

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry : packed.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("Id", entry.getKey());
            item.put("Config", entry.getValue());
            list.add(item);
        }
        tag.put("Cores", list);
        return tag;
    }

    private static PackedCores load(CompoundTag tag, HolderLookup.Provider registries) {
        PackedCores data = new PackedCores();
        for (Tag element : tag.getList("Cores", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) element;
            if (item.hasUUID("Id")) {
                data.packed.put(item.getUUID("Id"), item.getCompound("Config"));
            }
        }
        return data;
    }
}
