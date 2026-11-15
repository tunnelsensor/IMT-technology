package com.senne.imttech.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
 * Alle datacenters van de wereld, en de backups die erop staan (8 oktober: "een manier om backups
 * te nemen naar een ander datacenter, omdat als alles afbrandt anders je hele config weg is").
 *
 * Waarom hier, en niet in de kern zelf? Je andere datacenter staat meestal ver weg, en dan is zijn
 * chunk niet geladen. Toch moet je er een backup naartoe kunnen sturen, en moet het brein van
 * I.M.T weten hoe sterk het is. Daarom vertelt elke kern hier om de twee seconden hoe hij
 * ervoor staat (een {@link Summary}); voor een kern die niet geladen is, geldt wat hij het laatst
 * zei. De backups zelf staan ook hier, bij het datacenter waar ze op staan.
 *
 * Dit is "SavedData": Minecraft bewaart het in de wereld (data/imttech_datacenters.dat van de
 * overworld), voor alle dimensies samen.
 */
public final class Datacenters extends SavedData {
    private static final String NAME = "imttech_datacenters";

    /**
     * Hoe een datacenter ervoor staat (het laatst gemeld).
     *
     * @param owner      van wie de kern is
     * @param host       de naam van de kern ("dev-lab")
     * @param ip         het IP-adres van de kern
     * @param online     draait de server?
     * @param capacity   zoveel blokjes passen op de SSD's
     * @param ownBlocks  zoveel blokjes gebruiken de eigen diagrammen
     * @param brainDelay zoveel ticks denkt het brein van I.M.T hier extra na (-1 = kan niet)
     * @param power      hoofdstuk 106: de rekenkracht voor de bril (een CPU 1, een GPU 2, maal wat het RAM voedt, warm de
     *                   helft; zie DatacenterGlasses). -1 = nog niet gemeld (een wereld van voor hoofdstuk 106)
     * @param ramMb      hoofdstuk 106: zoveel MB RAM heeft het. -1 = nog niet gemeld
     * @param passwordStamp hoofdstuk 123: welk wachtwoord de kern heeft (zie SystemPassword.stamp), "" = geen (of nog niet
     *                   gemeld). Een bril die met een ander wachtwoord verbond, moet opnieuw verbinden.
     */
    public record Summary(@Nullable UUID owner, String host, String ip, boolean online, int capacity, int ownBlocks,
                          int brainDelay, double power, double ramMb, String passwordStamp) {}

    /**
     * Eén backup: wat er op stond (de diagrammen en keycards, als LogicStore), van welk netwerk,
     * waar hij staat, en wanneer.
     */
    public record Backup(int id, GlobalPos target, GlobalPos source, String sourceIp, String sourceHost, @Nullable UUID owner,
                         long time, int size, boolean auto, CompoundTag data) {}

    private final Map<GlobalPos, Summary> summaries = new HashMap<>();
    private final List<Backup> backups = new ArrayList<>();
    private int nextId = 1;
    /**
     * Hoofdstuk 106: zoveel MB bewaart de bril van elke speler (zijn waypoints). Het staat op de SSD's van het datacenter
     * waar zijn bril op draait (zie DatacenterGlasses), en telt daar mee, ook als die speler niet online is.
     */
    private final Map<UUID, GlassesData> glassesData = new HashMap<>();

    /**
     * Wat de bril van een speler bewaart, en waar (hoofdstuk 106 en 123).
     *
     * @param mb   zoveel MB (zijn waypoints)
     * @param ip    het IP van de kern op wiens SSD's het staat: waar zijn bril de laatste keer verbonden mee was ("" = op
     *              geen enkel datacenter: zijn bril was niet verbonden)
     * @param stamp met welk wachtwoord van die kern de bril verbonden was (zie SystemPassword.stamp): heeft de kern nu een
     *              ander (een nieuw wachtwoord, een overname, of een nieuwe kern op dezelfde plek met hetzelfde IP), dan
     *              staat het daar niet meer, ook als die speler nog niet terug online kwam
     * @param name  de naam van de speler (voor de app Monitor: wiens bril aan dit datacenter hangt)
     */
    public record GlassesData(double mb, String ip, String stamp, String name) {}

    public static Datacenters get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Datacenters::new, Datacenters::load), NAME);
    }

    public static Datacenters get(ServerLevel level) {
        return get(level.getServer());
    }

    // ================================================================ Hoe ze ervoor staan

    /** Een kern meldt hoe hij ervoor staat (om de twee seconden). Alleen als er iets veranderde, wordt het bewaard. */
    public void report(GlobalPos pos, Summary summary) {
        if (!summary.equals(summaries.put(pos, summary))) {
            setDirty();
        }
    }

    /** Deze kern heeft geen eigenaar (meer): hij telt niet als iemands datacenter. De backups erop blijven. */
    public void unreport(GlobalPos pos) {
        if (summaries.remove(pos) != null) {
            setDirty();
        }
    }

    public @Nullable Summary summary(GlobalPos pos) {
        return summaries.get(pos);
    }

    /** Alle datacenters van deze speler (met een vaste volgorde: op naam, dan op plek). */
    public List<Map.Entry<GlobalPos, Summary>> ownedBy(@Nullable UUID owner) {
        List<Map.Entry<GlobalPos, Summary>> found = new ArrayList<>();
        if (owner == null) {
            return found;
        }
        for (Map.Entry<GlobalPos, Summary> entry : summaries.entrySet()) {
            if (owner.equals(entry.getValue().owner())) {
                found.add(Map.entry(entry.getKey(), entry.getValue()));
            }
        }
        found.sort((a, b) -> {
            int byName = a.getValue().host().compareToIgnoreCase(b.getValue().host());
            return byName != 0 ? byName : key(a.getKey()).compareTo(key(b.getKey()));
        });
        return found;
    }

    /** Een kern is weg (afgebroken): hij en de backups die erop stonden ook. */
    public void forget(GlobalPos pos) {
        boolean changed = summaries.remove(pos) != null;
        changed |= backups.removeIf(backup -> backup.target().equals(pos));
        if (changed) {
            setDirty();
        }
    }

    // ================================================================ Wat de bril bewaart (hoofdstuk 106)

    /** Zoveel MB bewaart de bril van deze speler (0 als hij niets bewaart). */
    public double glassesData(@Nullable UUID owner) {
        GlassesData data = owner == null ? null : glassesData.get(owner);
        return data == null ? 0.0 : data.mb();
    }

    /**
     * De bril van deze speler bewaart nu zoveel MB, op het datacenter van de kern met dit IP ("" = op geen enkel), verbonden
     * met dit wachtwoord van die kern ({@code stamp}). Alleen als er iets veranderde, wordt het bewaard.
     */
    public void setGlassesData(UUID owner, double mb, String ip, String stamp, String name) {
        GlassesData now = new GlassesData(Math.max(0.0, mb), ip, stamp, name);
        GlassesData before = glassesData.get(owner);
        if (now.mb() <= 0 && ip.isEmpty()) {
            if (glassesData.remove(owner) != null) {
                setDirty();
            }
            return;
        }
        if (now.equals(before)) {
            return;
        }
        glassesData.put(owner, now);
        setDirty();
    }

    /** Wat elke bril bewaart, en waar (voor de SSD's en de app Monitor). */
    public Map<UUID, GlassesData> glassesData() {
        return Map.copyOf(glassesData);
    }

    // ================================================================ Het wachtwoord van een kern (hoofdstuk 123)

    /**
     * Het wachtwoord van de kern op deze plek veranderde (of hij kreeg er een, of verbond net een bril): de brillen die met
     * een ander wachtwoord verbonden waren, merken het meteen, en niet pas bij zijn volgende melding (om de twee seconden).
     */
    public void updateStamp(GlobalPos pos, String stamp) {
        Summary summary = summaries.get(pos);
        if (summary != null && !summary.passwordStamp().equals(stamp)) {
            summaries.put(pos, new Summary(summary.owner(), summary.host(), summary.ip(), summary.online(), summary.capacity(),
                    summary.ownBlocks(), summary.brainDelay(), summary.power(), summary.ramMb(), stamp));
            setDirty();
        }
    }

    // ================================================================ Backups

    /** Alle backups van deze speler, de nieuwste eerst. */
    public List<Backup> backupsOf(@Nullable UUID owner) {
        List<Backup> found = new ArrayList<>();
        for (Backup backup : backups) {
            if (Objects.equals(owner, backup.owner())) {
                found.add(backup);
            }
        }
        found.sort((a, b) -> Long.compare(b.time(), a.time()) != 0 ? Long.compare(b.time(), a.time()) : Integer.compare(b.id(), a.id()));
        return found;
    }

    public @Nullable Backup backup(int id) {
        for (Backup backup : backups) {
            if (backup.id() == id) {
                return backup;
            }
        }
        return null;
    }

    /** Zoveel blokjes nemen de backups op dit datacenter in. */
    public int storedAt(GlobalPos target) {
        int total = 0;
        for (Backup backup : backups) {
            if (backup.target().equals(target)) {
                total += backup.size();
            }
        }
        return total;
    }

    /**
     * Zoveel blokjes zijn er nog vrij op dit datacenter: wat op de SSD's past, min de eigen
     * diagrammen, min de backups die er al staan. Telt de backups van {@code replacing} niet mee
     * die plaats moeten maken (de oudste als er al KEEP van dat netwerk staan).
     */
    public int free(GlobalPos target, @Nullable GlobalPos replacing, int keep) {
        Summary summary = summaries.get(target);
        if (summary == null) {
            return 0;
        }
        int used = summary.ownBlocks() + storedAt(target);
        Backup oldest = replacing == null ? null : oldestToDrop(target, replacing, keep);
        if (oldest != null) {
            used -= oldest.size();
        }
        return summary.capacity() - used;
    }

    /** De backup die weg moet als er al {@code keep} van dit netwerk op dit datacenter staan (de oudste), of null. */
    private @Nullable Backup oldestToDrop(GlobalPos target, GlobalPos source, int keep) {
        List<Backup> same = new ArrayList<>();
        for (Backup backup : backups) {
            if (backup.target().equals(target) && backup.source().equals(source)) {
                same.add(backup);
            }
        }
        if (same.size() < keep) {
            return null;
        }
        same.sort((a, b) -> Long.compare(a.time(), b.time()) != 0 ? Long.compare(a.time(), b.time()) : Integer.compare(a.id(), b.id()));
        return same.getFirst();
    }

    /** Een nieuwe backup. Staan er van dat netwerk al {@code keep} op dat datacenter, dan valt de oudste weg. */
    public Backup add(GlobalPos target, GlobalPos source, String sourceIp, String sourceHost, @Nullable UUID owner, long time,
                      int size, boolean auto, CompoundTag data, int keep) {
        Backup oldest;
        while ((oldest = oldestToDrop(target, source, keep)) != null) {
            backups.remove(oldest);
        }
        Backup backup = new Backup(nextId++, target, source, sourceIp, sourceHost, owner, time, size, auto, data);
        backups.add(backup);
        setDirty();
        return backup;
    }

    public boolean remove(int id) {
        boolean removed = backups.removeIf(backup -> backup.id() == id);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /**
     * De backups op dit datacenter, om mee te nemen als de kern weggehakt wordt (zie
     * SecurityCoreBlockEntity.pack). Ze blijven hier staan tot {@link #forget}.
     */
    public ListTag saveStoredAt(GlobalPos target) {
        ListTag list = new ListTag();
        for (Backup backup : backups) {
            if (backup.target().equals(target)) {
                list.add(saveBackup(backup));
            }
        }
        return list;
    }

    /**
     * Een weggehakte kern staat weer ergens (misschien op een andere plek): de backups die hij
     * meenam (zie {@link #saveStoredAt}) staan er weer op, met nieuwe nummers.
     */
    public void adopt(GlobalPos target, ListTag saved) {
        for (Tag element : saved) {
            Backup backup = loadBackup((CompoundTag) element);
            if (backup != null) {
                backups.add(new Backup(nextId++, target, backup.source(), backup.sourceIp(), backup.sourceHost(),
                        backup.owner(), backup.time(), backup.size(), backup.auto(), backup.data()));
            }
        }
        if (!saved.isEmpty()) {
            setDirty();
        }
    }

    /** Alle backups die op dit datacenter stonden zijn weg (een SSD verbrandde). Geeft terug hoeveel. */
    public int wipeStoredAt(GlobalPos target) {
        int before = backups.size();
        backups.removeIf(backup -> backup.target().equals(target));
        int removed = before - backups.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    // ================================================================ Namen voor de desktop

    /** Een plek als tekst ("minecraft:overworld|1234567"), zodat de desktop hem kan terugsturen. */
    public static String key(GlobalPos pos) {
        return pos.dimension().location() + "|" + pos.pos().asLong();
    }

    /** Terug van tekst naar plek (null als het niet klopt). */
    public static @Nullable GlobalPos fromKey(String key) {
        int bar = key.lastIndexOf('|');
        if (bar <= 0) {
            return null;
        }
        try {
            ResourceLocation dimension = ResourceLocation.parse(key.substring(0, bar));
            return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(Long.parseLong(key.substring(bar + 1))));
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ================================================================ Opslaan

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextId", nextId);
        ListTag list = new ListTag();
        for (Map.Entry<GlobalPos, Summary> entry : summaries.entrySet()) {
            Summary summary = entry.getValue();
            CompoundTag item = new CompoundTag();
            item.putString("Pos", key(entry.getKey()));
            if (summary.owner() != null) {
                item.putUUID("Owner", summary.owner());
            }
            item.putString("Host", summary.host());
            item.putString("Ip", summary.ip());
            item.putBoolean("Online", summary.online());
            item.putInt("Cap", summary.capacity());
            item.putInt("Own", summary.ownBlocks());
            item.putInt("Brain", summary.brainDelay());
            item.putDouble("Power", summary.power());
            item.putDouble("RamMb", summary.ramMb());
            item.putString("Stamp", summary.passwordStamp());
            list.add(item);
        }
        tag.put("Datacenters", list);
        ListTag glasses = new ListTag();
        for (Map.Entry<UUID, GlassesData> entry : glassesData.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("Owner", entry.getKey());
            item.putDouble("Mb", entry.getValue().mb());
            item.putString("Ip", entry.getValue().ip());
            item.putString("Stamp", entry.getValue().stamp());
            item.putString("Name", entry.getValue().name());
            glasses.add(item);
        }
        tag.put("Glasses", glasses);
        ListTag backupList = new ListTag();
        for (Backup backup : backups) {
            backupList.add(saveBackup(backup));
        }
        tag.put("Backups", backupList);
        return tag;
    }

    private static CompoundTag saveBackup(Backup backup) {
        CompoundTag item = new CompoundTag();
        item.putInt("Id", backup.id());
        item.putString("Target", key(backup.target()));
        item.putString("Source", key(backup.source()));
        item.putString("Ip", backup.sourceIp());
        item.putString("Host", backup.sourceHost());
        if (backup.owner() != null) {
            item.putUUID("Owner", backup.owner());
        }
        item.putLong("Time", backup.time());
        item.putInt("Size", backup.size());
        item.putBoolean("Auto", backup.auto());
        item.put("Data", backup.data());
        return item;
    }

    /** Een backup terug uit {@link #saveBackup} (null als de plekken niet kloppen). */
    private static @Nullable Backup loadBackup(CompoundTag item) {
        GlobalPos target = fromKey(item.getString("Target"));
        GlobalPos source = fromKey(item.getString("Source"));
        if (target == null || source == null) {
            return null;
        }
        return new Backup(item.getInt("Id"), target, source, item.getString("Ip"), item.getString("Host"),
                item.hasUUID("Owner") ? item.getUUID("Owner") : null, item.getLong("Time"), item.getInt("Size"),
                item.getBoolean("Auto"), item.getCompound("Data"));
    }

    public static Datacenters load(CompoundTag tag, HolderLookup.Provider registries) {
        Datacenters data = new Datacenters();
        data.nextId = Math.max(1, tag.getInt("NextId"));
        for (Tag element : tag.getList("Datacenters", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) element;
            GlobalPos pos = fromKey(item.getString("Pos"));
            if (pos != null) {
                // Hoofdstuk 106: een wereld van daarvoor heeft "Power" en "RamMb" nog niet: -1 = nog niet gemeld.
                data.summaries.put(pos, new Summary(item.hasUUID("Owner") ? item.getUUID("Owner") : null, item.getString("Host"),
                        item.getString("Ip"), item.getBoolean("Online"), item.getInt("Cap"), item.getInt("Own"), item.getInt("Brain"),
                        item.contains("Power") ? item.getDouble("Power") : -1.0,
                        item.contains("RamMb") ? item.getDouble("RamMb") : -1.0,
                        item.getString("Stamp"))); // hoofdstuk 123; daarvoor: "" (tot de kern weer meldt)
            }
        }
        // Wat elke bril bewaart, en waar (hoofdstuk 123: "Ip" en "Name"; daarvoor stond het op geen enkel datacenter tot de
        // bril weer verbonden werkt). De verbindingen per speler van hoofdstuk 122 ("Uplinks") staan sinds hoofdstuk 123 op
        // de bril zelf; die oude lijst lezen we niet meer.
        for (Tag element : tag.getList("Glasses", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) element;
            if (item.hasUUID("Owner") && (item.getDouble("Mb") > 0 || !item.getString("Ip").isEmpty())) {
                data.glassesData.put(item.getUUID("Owner"), new GlassesData(Math.max(0.0, item.getDouble("Mb")),
                        item.getString("Ip"), item.getString("Stamp"), item.getString("Name")));
            }
        }
        for (Tag element : tag.getList("Backups", Tag.TAG_COMPOUND)) {
            Backup backup = loadBackup((CompoundTag) element);
            if (backup != null) {
                data.backups.add(backup);
                data.nextId = Math.max(data.nextId, backup.id() + 1);
            }
        }
        return data;
    }
}
