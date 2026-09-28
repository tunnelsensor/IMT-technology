package com.senne.starktech.security.net;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Het "netwerk" van de wereld: welk IP-adres bij welke beveiligingskern hoort.
 *
 * Elke kern krijgt een eigen adres (10.x.y.z). Dat onthouden we hier, en niet alleen op de kern
 * zelf: met SSH log je in vanaf de andere kant van de wereld, en dan is de chunk met de kern
 * misschien niet geladen. Hier vinden we hem toch terug.
 *
 * Dit is "SavedData": Minecraft bewaart het in de wereld (data/starktech_network.dat van de
 * overworld), en het geldt voor alle dimensies samen.
 */
public final class StarkNetworkData extends SavedData {
    private static final String NAME = "starktech_network";

    private final Map<String, GlobalPos> byIp = new HashMap<>();
    private final Map<GlobalPos, String> byPos = new HashMap<>();

    /** Het netwerk van deze server (altijd via de overworld, zodat er maar één is). */
    public static StarkNetworkData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StarkNetworkData::new, StarkNetworkData::load), NAME);
    }

    public static StarkNetworkData get(ServerLevel level) {
        return get(level.getServer());
    }

    /**
     * Het IP van de kern op deze plek. Heeft hij er nog geen, dan krijgt hij er een dat nog niemand
     * heeft: bij voorkeur {@code preferred} (het IP dat de kern zelf al onthield), anders een nieuw
     * dat van de plek afhangt, zodat het er "echt" uitziet.
     */
    public String allocate(GlobalPos pos, @Nullable String preferred) {
        String existing = byPos.get(pos);
        if (existing != null) {
            return existing;
        }
        String ip = preferred;
        int hash = pos.pos().hashCode() * 31 + pos.dimension().location().hashCode();
        while (ip == null || ip.isEmpty() || byIp.containsKey(ip)) {
            ip = "10." + ((hash >>> 16) & 0xFF) + "." + ((hash >>> 8) & 0xFF) + "." + (1 + Math.floorMod(hash, 254));
            hash = hash * 1103515245 + 12345; // bezet? Dan een ander getal proberen.
        }
        byIp.put(ip, pos);
        byPos.put(pos, ip);
        setDirty();
        return ip;
    }

    /** De kern met dit IP, of null. */
    public @Nullable GlobalPos find(String ip) {
        return byIp.get(ip);
    }

    /** De kern is weg (afgebroken): zijn IP is weer vrij. */
    public void release(GlobalPos pos) {
        String ip = byPos.remove(pos);
        if (ip != null) {
            byIp.remove(ip);
            setDirty();
        }
    }

    public Map<String, GlobalPos> all() {
        return Map.copyOf(byIp);
    }

    // ================================================================ Opslaan

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, GlobalPos> entry : byIp.entrySet()) {
            CompoundTag node = new CompoundTag();
            node.putString("Ip", entry.getKey());
            node.putString("Dim", entry.getValue().dimension().location().toString());
            node.putLong("Pos", entry.getValue().pos().asLong());
            list.add(node);
        }
        tag.put("Nodes", list);
        return tag;
    }

    private static StarkNetworkData load(CompoundTag tag, HolderLookup.Provider registries) {
        StarkNetworkData data = new StarkNetworkData();
        for (Tag element : tag.getList("Nodes", Tag.TAG_COMPOUND)) {
            CompoundTag node = (CompoundTag) element;
            ResourceLocation dim = ResourceLocation.tryParse(node.getString("Dim"));
            if (dim == null) {
                continue;
            }
            GlobalPos pos = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(node.getLong("Pos")));
            data.byIp.put(node.getString("Ip"), pos);
            data.byPos.put(pos, node.getString("Ip"));
        }
        return data;
    }
}
