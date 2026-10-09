package com.senne.starktech.security.zone;

import com.senne.starktech.logic.Datacenters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

/**
 * Alle zones van de wereld (hoofdstuk 10 van het plan, 8 oktober: "binnen deze zone kunnen enkel
 * players met toestemming plaatsen/breken").
 *
 * Een zone hoort bij een beveiligingskern: de kubussen die de eigenaar tekende, van wie de kern is,
 * wie hij vertrouwt, en of de kern genoeg stroom heeft. Waarom staat dat hier, en niet alleen in de
 * kern? Iemand kan aan de rand van je zone staan terwijl de chunk van de kern niet geladen is; ook
 * dan moet de zone werken. Daarom vertelt de kern het hier, en geldt voor een kern die niet geladen
 * is wat hij het laatst zei (net als bij {@link Datacenters}).
 *
 * Dit is "SavedData": Minecraft bewaart het in de wereld (data/starktech_zones.dat van de
 * overworld), voor alle dimensies samen.
 */
public final class ZoneData extends SavedData {
    private static final String NAME = "starktech_zones";

    /** De zone van één kern. */
    public static final class Zone {
        private final GlobalPos core;
        private @Nullable UUID owner;
        private String ownerName = "";
        private final Set<UUID> trusted = new LinkedHashSet<>();
        /** Had de kern de laatste keer genoeg stroom voor zichzelf en de zone? */
        private boolean powered = true;
        private List<ZoneBox> boxes = List.of();
        /** De kleinste doos om alle kubussen heen (om snel "hier zeker niet" te zeggen), of null zonder kubussen. */
        private @Nullable ZoneBox bounds;
        /** Hoeveel blokken de zone telt (kubussen die over elkaar liggen tellen één keer). */
        private long volume;

        private Zone(GlobalPos core) {
            this.core = core;
        }

        public GlobalPos core() {
            return core;
        }

        public @Nullable UUID owner() {
            return owner;
        }

        public String ownerName() {
            return ownerName;
        }

        public boolean powered() {
            return powered;
        }

        /** De kubussen, in de volgorde waarin ze erbij kwamen (de app nummert ze zo: 1, 2, 3...). */
        public List<ZoneBox> boxes() {
            return boxes;
        }

        public boolean hasBoxes() {
            return !boxes.isEmpty();
        }

        public long volume() {
            return volume;
        }

        /**
         * Beschermt de zone nu? Dat kan alleen als de kern van iemand is, er kubussen zijn, en de kern
         * genoeg stroom heeft. (De Stark Tower is van niemand tot je hem hackt: die heeft dus nooit een zone.)
         */
        public boolean active() {
            return owner != null && powered && hasBoxes();
        }

        /**
         * Hoort dit blok bij de zone? De kern zelf hoort er altijd bij (zodra er een kubus is): anders
         * hakte je gewoon de kern weg, en was de zone weg.
         */
        public boolean contains(BlockPos pos) {
            if (bounds == null) {
                return false;
            }
            if (pos.equals(core.pos())) {
                return true;
            }
            if (!bounds.contains(pos)) {
                return false;
            }
            for (ZoneBox box : boxes) {
                if (box.contains(pos)) {
                    return true;
                }
            }
            return false;
        }

        /** Raakt een van de kubussen (of de kern) dit stuk ruimte? */
        public boolean intersects(net.minecraft.world.phys.AABB area) {
            if (bounds == null) {
                return false;
            }
            if (area.contains(core.pos().getCenter())) {
                return true;
            }
            for (ZoneBox box : boxes) {
                if (area.intersects(box.min().getX(), box.min().getY(), box.min().getZ(),
                        box.max().getX() + 1, box.max().getY() + 1, box.max().getZ() + 1)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Mag deze speler hier bouwen? Dezelfde vrienden als bij de torentjes (zie
         * SecurityCores.isFriend): de eigenaar, zijn team en wie hij vertrouwt (de app Access).
         */
        public boolean isBuilder(ServerLevel level, Player player) {
            return isBuilder(level, player.getUUID(), player.getScoreboardName());
        }

        /** Hetzelfde, op nummer en naam (ook voor wie nu offline is, zoals wie een C4 plakte). */
        public boolean isBuilder(ServerLevel level, @Nullable UUID id, String name) {
            if (owner == null || id == null) {
                return false;
            }
            if (owner.equals(id) || trusted.contains(id)) {
                return true;
            }
            PlayerTeam ownerTeam = level.getScoreboard().getPlayersTeam(ownerName);
            return ownerTeam != null && !name.isEmpty() && ownerTeam.isAlliedTo(level.getScoreboard().getPlayersTeam(name));
        }

        /** Alleen de kubussen veranderen (en wat we daaruit uitrekenen). */
        private void setBoxes(List<ZoneBox> newBoxes) {
            boxes = List.copyOf(newBoxes);
            volume = unionVolume(boxes);
            if (boxes.isEmpty()) {
                bounds = null;
                return;
            }
            BlockPos low = boxes.getFirst().min();
            BlockPos high = boxes.getFirst().max();
            for (ZoneBox box : boxes) {
                low = new BlockPos(Math.min(low.getX(), box.min().getX()), Math.min(low.getY(), box.min().getY()),
                        Math.min(low.getZ(), box.min().getZ()));
                high = new BlockPos(Math.max(high.getX(), box.max().getX()), Math.max(high.getY(), box.max().getY()),
                        Math.max(high.getZ(), box.max().getZ()));
            }
            bounds = ZoneBox.of(low, high);
        }
    }

    private final Map<GlobalPos, Zone> zones = new LinkedHashMap<>();
    /**
     * Wat er met de zones van een eigenaar gebeurde terwijl hij offline was (hoofdstuk 10, stap 7):
     * hoeveel C4-klappen en hoeveel blokken. Bij het inloggen vertelt E.D.I.T.H. het, dan is het weg.
     */
    private final Map<UUID, int[]> awayReports = new HashMap<>();
    /** Per dimensie de zones met kubussen (opnieuw opgebouwd na elke verandering). Niet bewaard. */
    private @Nullable Map<ResourceKey<Level>, List<Zone>> byLevel;
    /** Telt elke verandering; de client krijgt de zones opnieuw als dit getal veranderde. Niet bewaard. */
    private int changes;

    public static ZoneData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ZoneData::new, ZoneData::load), NAME);
    }

    public static ZoneData get(ServerLevel level) {
        return get(level.getServer());
    }

    // ================================================================ Opzoeken

    /** De zone van deze kern, of null als hij er (nog) geen heeft. */
    public @Nullable Zone zone(GlobalPos core) {
        return zones.get(core);
    }

    /** De zone waar dit blok bij hoort (actief of niet), of null. Zones liggen nooit over elkaar heen. */
    public @Nullable Zone at(ResourceKey<Level> dimension, BlockPos pos) {
        for (Zone zone : in(dimension)) {
            if (zone.contains(pos)) {
                return zone;
            }
        }
        return null;
    }

    /** Alle zones met kubussen in deze dimensie. */
    public List<Zone> in(ResourceKey<Level> dimension) {
        if (byLevel == null) {
            byLevel = new HashMap<>();
            for (Zone zone : zones.values()) {
                if (zone.hasBoxes()) {
                    byLevel.computeIfAbsent(zone.core().dimension(), key -> new ArrayList<>()).add(zone);
                }
            }
        }
        return byLevel.getOrDefault(dimension, List.of());
    }

    /** Telt elke verandering aan een zone (zie {@link #changes}). */
    public int changes() {
        return changes;
    }

    // ================================================================ Veranderen

    private Zone zoneFor(GlobalPos core) {
        return zones.computeIfAbsent(core, Zone::new);
    }

    private void changed() {
        byLevel = null;
        changes++;
        setDirty();
    }

    /** De kern vertelt van wie hij is en wie er mag bouwen. Alleen als er iets veranderde, wordt het bewaard. */
    public void report(GlobalPos core, @Nullable UUID owner, String ownerName, Collection<UUID> trusted) {
        Zone zone = zones.get(core);
        if (zone == null) {
            if (owner == null) {
                return; // een kern van niemand zonder zone: niets te onthouden
            }
            zone = zoneFor(core);
        }
        if (Objects.equals(zone.owner, owner) && zone.ownerName.equals(ownerName) && zone.trusted.equals(Set.copyOf(trusted))) {
            return;
        }
        zone.owner = owner;
        zone.ownerName = ownerName;
        zone.trusted.clear();
        zone.trusted.addAll(trusted);
        changed();
    }

    /** Heeft de kern genoeg stroom voor de zone? */
    public void setPowered(GlobalPos core, boolean powered) {
        Zone zone = zoneFor(core);
        if (zone.powered != powered) {
            zone.powered = powered;
            changed();
        }
    }

    /** Nieuwe kubussen voor de zone van deze kern (de kern heeft ze al passend gemaakt). */
    public void setBoxes(GlobalPos core, List<ZoneBox> boxes) {
        Zone zone = zoneFor(core);
        if (!zone.boxes.equals(boxes)) {
            zone.setBoxes(boxes);
            changed();
        }
    }

    /** De kern is weg (afgebroken): zijn zone ook. */
    public void forget(GlobalPos core) {
        if (zones.remove(core) != null) {
            changed();
        }
    }

    // ================================================================ Terwijl je weg was

    /** Een C4-klap op een zone van deze eigenaar, terwijl hij offline is. */
    public void addAwayReport(UUID owner, int blocks) {
        int[] report = awayReports.computeIfAbsent(owner, key -> new int[2]);
        report[0]++;
        report[1] += blocks;
        setDirty();
    }

    /** Het verslag van deze eigenaar ({klappen, blokken}), en meteen weg; null als er niets was. */
    public int @Nullable [] takeAwayReport(UUID owner) {
        int[] report = awayReports.remove(owner);
        if (report != null) {
            setDirty();
        }
        return report;
    }

    // ================================================================ Rekenen

    /**
     * Hoeveel blokken liggen er in deze kubussen samen? Kubussen mogen over elkaar liggen; wat dubbel
     * ligt, telt één keer. We knippen de ruimte op langs alle randen van de kubussen in stukken
     * (hooguit 31 x 31 x 31), en tellen elk stuk dat in een kubus ligt.
     */
    static long unionVolume(List<ZoneBox> boxes) {
        if (boxes.isEmpty()) {
            return 0;
        }
        if (boxes.size() == 1) {
            return boxes.getFirst().volume();
        }
        TreeSet<Integer> xs = new TreeSet<>();
        TreeSet<Integer> ys = new TreeSet<>();
        TreeSet<Integer> zs = new TreeSet<>();
        for (ZoneBox box : boxes) {
            xs.add(box.min().getX());
            xs.add(box.max().getX() + 1);
            ys.add(box.min().getY());
            ys.add(box.max().getY() + 1);
            zs.add(box.min().getZ());
            zs.add(box.max().getZ() + 1);
        }
        Integer[] x = xs.toArray(new Integer[0]);
        Integer[] y = ys.toArray(new Integer[0]);
        Integer[] z = zs.toArray(new Integer[0]);
        long total = 0;
        for (int i = 0; i + 1 < x.length; i++) {
            for (int j = 0; j + 1 < y.length; j++) {
                for (int k = 0; k + 1 < z.length; k++) {
                    BlockPos probe = new BlockPos(x[i], y[j], z[k]);
                    for (ZoneBox box : boxes) {
                        if (box.contains(probe)) {
                            total += (long) (x[i + 1] - x[i]) * (y[j + 1] - y[j]) * (z[k + 1] - z[k]);
                            break;
                        }
                    }
                }
            }
        }
        return total;
    }

    // ================================================================ Opslaan

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Zone zone : zones.values()) {
            CompoundTag item = new CompoundTag();
            item.putString("Core", Datacenters.key(zone.core));
            if (zone.owner != null) {
                item.putUUID("Owner", zone.owner);
            }
            item.putString("OwnerName", zone.ownerName);
            ListTag trustedList = new ListTag();
            for (UUID id : zone.trusted) {
                trustedList.add(NbtUtils.createUUID(id));
            }
            item.put("Trusted", trustedList);
            item.putBoolean("Powered", zone.powered);
            ListTag boxList = new ListTag();
            for (ZoneBox box : zone.boxes) {
                boxList.add(box.save());
            }
            item.put("Boxes", boxList);
            list.add(item);
        }
        tag.put("Zones", list);
        ListTag reports = new ListTag();
        for (Map.Entry<UUID, int[]> entry : awayReports.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("Owner", entry.getKey());
            item.putInt("Blasts", entry.getValue()[0]);
            item.putInt("Blocks", entry.getValue()[1]);
            reports.add(item);
        }
        tag.put("AwayReports", reports);
        return tag;
    }

    public static ZoneData load(CompoundTag tag, HolderLookup.Provider registries) {
        ZoneData data = new ZoneData();
        for (Tag element : tag.getList("Zones", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) element;
            GlobalPos core = Datacenters.fromKey(item.getString("Core"));
            if (core == null) {
                continue;
            }
            Zone zone = data.zoneFor(core);
            zone.owner = item.hasUUID("Owner") ? item.getUUID("Owner") : null;
            zone.ownerName = item.getString("OwnerName");
            for (Tag id : item.getList("Trusted", Tag.TAG_INT_ARRAY)) {
                zone.trusted.add(NbtUtils.loadUUID(id));
            }
            zone.powered = !item.contains("Powered") || item.getBoolean("Powered");
            List<ZoneBox> boxes = new ArrayList<>();
            for (Tag box : item.getList("Boxes", Tag.TAG_INT_ARRAY)) {
                ZoneBox loaded = ZoneBox.load(((IntArrayTag) box).getAsIntArray());
                if (loaded != null) {
                    boxes.add(loaded);
                }
            }
            zone.setBoxes(boxes);
        }
        for (Tag element : tag.getList("AwayReports", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) element;
            if (item.hasUUID("Owner")) {
                data.awayReports.put(item.getUUID("Owner"), new int[] {item.getInt("Blasts"), item.getInt("Blocks")});
            }
        }
        return data;
    }

    /** Voor de tests: wat er in een opgeslagen versie van deze zones staat. */
    public static List<ZoneBox> boxesAfterReload(ZoneData data, GlobalPos core, HolderLookup.Provider registries) {
        ZoneData reloaded = load(data.save(new CompoundTag(), registries), registries);
        Zone zone = reloaded.zone(core);
        return zone == null ? Collections.emptyList() : zone.boxes();
    }
}
