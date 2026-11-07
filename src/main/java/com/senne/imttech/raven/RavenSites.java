package com.senne.imttech.raven;

import com.senne.imttech.config.ImtConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De bunkers en torens van Raven als <b>sites</b> (hoofdstuk 60, stap 4, jouw antwoord 4): wie je als eerste ziet, roept
 * via de radio, en dan weet de hele bunker het en komt er een team binnen.
 *
 * Een site is een ingang: de markering {@code raven_entry} in de ingang van de bunker en de lobby van de toren wordt
 * bij het bouwen een onzichtbare {@link Marker} met de tag {@link #ENTRY_TAG} (die mag een wereldgenerator veilig
 * neerzetten, en hij blijft bij zijn stuk wereld). Alle soldaten binnen {@code [guards] siteRange} van die ingang horen
 * erbij. Wat er met een site gebeurt (het alarm, hoeveel teams er al kwamen, het team dat onderweg is) staat hier, per
 * wereld, in SavedData op de plek van de ingang.
 * <ul>
 *   <li><b>Een heel radiogesprek</b> ({@link #raiseAlarm}): alle soldaten van de site krijgen je als doelwit en je laatste
 *       plek, ze zijn {@code memoryMinutes} lang extra scherp, het alarm loopt {@code alarmSeconds} na het laatste contact
 *       af, en na {@code backupDelaySeconds} komt een team van {@code backupSize} soldaten door de ingang; hooguit
 *       {@code backupWaves} teams per alarm.</li>
 *   <li><b>Een half gesprek</b> ({@link #halfCall}): zijn maten worden achterdochtig en lopen langzaam naar hem toe, en jij
 *       hoort door de coms "BB-2358, come in".</li>
 *   <li><b>"Stand down"</b>: het team loopt terug naar de ingang en is daar weg (8 november: minder bewakers, en een
 *       bunker wordt na een alarm niet voorgoed voller).</li>
 *   <li>Zonder ingang in de buurt (een soldaat uit een spawn egg, een oude wereld) werkt het met de soldaten binnen
 *       {@link #LOOSE_RANGE} blokken, en komt er geen team.</li>
 * </ul>
 */
public final class RavenSites extends SavedData {
    private static final String NAME = "imttech_raven_sites";
    /** De tag van de onzichtbare markering op de ingang. */
    public static final String ENTRY_TAG = "raven_entry";
    /** Zonder site: zo ver reikt een radiogesprek (blokken). */
    public static final double LOOSE_RANGE = 48.0;

    /** Eén site: wat er speelt rond deze ingang. */
    public static final class Site {
        final BlockPos entry;
        /** Tot deze speltijd loopt het alarm (0 = geen). */
        long alarmUntil;
        /** Hoeveel teams er in dit alarm al kwamen. */
        int waves;
        /** Op deze speltijd komt het volgende team binnen (0 = geen onderweg). */
        long backupAt;
        /** Wie, en waar hij het laatst gezien is. */
        @Nullable UUID target;
        @Nullable BlockPos lastSeen;

        Site(BlockPos entry) {
            this.entry = entry;
        }

        public BlockPos entry() {
            return entry;
        }

        public boolean alarmed(long now) {
            return alarmUntil > now;
        }

        public int waves() {
            return waves;
        }

        public boolean backupPending() {
            return backupAt != 0;
        }
    }

    private final Map<BlockPos, Site> sites = new HashMap<>();

    public RavenSites() {}

    public static RavenSites get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(RavenSites::new, RavenSites::load), NAME);
    }

    /** De sites van deze wereld als ze er al zijn, anders null (zonder er een leeg bestand voor te maken). */
    public static @Nullable RavenSites existing(ServerLevel level) {
        return level.getDataStorage().get(new SavedData.Factory<>(RavenSites::new, RavenSites::load), NAME);
    }

    // ================================================================ Ingangen

    /** De markering {@code raven_entry} uit een kamerbestand: een onzichtbare Marker met de tag, op deze plek. */
    public static void placeEntry(ServerLevelAccessor level, BlockPos pos) {
        Marker marker = EntityType.MARKER.create(level.getLevel());
        if (marker == null) {
            return;
        }
        marker.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        marker.addTag(ENTRY_TAG);
        level.addFreshEntityWithPassengers(marker);
    }

    /** De dichtstbijzijnde ingang binnen {@code siteRange} van deze plek, of null. */
    public static @Nullable BlockPos findEntry(ServerLevel level, BlockPos near) {
        double range = ImtConfig.Guards.SITE_RANGE;
        AABB box = new AABB(near).inflate(range, Math.min(range, 96), range);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Marker marker : level.getEntitiesOfClass(Marker.class, box, m -> m.getTags().contains(ENTRY_TAG))) {
            double distance = marker.distanceToSqr(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
            if (distance < bestDistance && distance <= range * range) {
                bestDistance = distance;
                best = marker.blockPosition();
            }
        }
        return best;
    }

    /** De site van deze ingang (en maak hem als hij nog niet bestaat). */
    public Site site(BlockPos entry) {
        return sites.computeIfAbsent(entry.immutable(), Site::new);
    }

    /** De site van deze soldaat, of null als er geen ingang in de buurt is. */
    public @Nullable Site siteOf(ServerLevel level, RavenSoldierEntity soldier) {
        BlockPos entry = soldier.senses().siteEntry(level);
        return entry == null ? null : site(entry);
    }

    /** De soldaten die bij deze ingang horen (binnen {@code siteRange}). */
    public static List<RavenSoldierEntity> soldiersOf(ServerLevel level, BlockPos entry) {
        double range = ImtConfig.Guards.SITE_RANGE;
        return level.getEntitiesOfClass(RavenSoldierEntity.class, new AABB(entry).inflate(range, Math.min(range, 96), range), Entity::isAlive);
    }

    /** Zonder site: de soldaten rond deze soldaat. */
    private static List<RavenSoldierEntity> soldiersAround(RavenSoldierEntity soldier) {
        return soldier.level().getEntitiesOfClass(RavenSoldierEntity.class, soldier.getBoundingBox().inflate(LOOSE_RANGE), Entity::isAlive);
    }

    // ================================================================ Roepen

    /**
     * Moet deze soldaat, die je net zag, eerst via de radio roepen? Ja als er nog niemand van zijn site (of zijn maten
     * in de buurt) met je vecht, en zijn site nog een team kan sturen of nog geen alarm heeft. Anders doet hij meteen mee.
     */
    public static boolean shouldCall(ServerLevel level, RavenSoldierEntity soldier) {
        Site site = get(level).siteOf(level, soldier);
        List<RavenSoldierEntity> mates = site != null ? soldiersOf(level, site.entry) : soldiersAround(soldier);
        for (RavenSoldierEntity mate : mates) {
            if (mate != soldier && mate.getTarget() != null) {
                return false;
            }
        }
        long now = level.getGameTime();
        return site == null || !site.alarmed(now) || site.waves + (site.backupPending() ? 1 : 0) < Math.round(ImtConfig.Guards.BACKUP_WAVES);
    }

    /**
     * Een heel radiogesprek: alarm voor de site (of de maten in de buurt). Iedereen krijgt {@code target} en de laatste
     * plek, wordt extra scherp, en als het kan komt er een team.
     */
    public static void raiseAlarm(ServerLevel level, RavenSoldierEntity caller, @Nullable LivingEntity target, Vec3 lastSeen, boolean body) {
        long now = level.getGameTime();
        long memory = now + Math.round(ImtConfig.Guards.MEMORY_MINUTES * 1200);
        RavenSites sites = get(level);
        Site site = sites.siteOf(level, caller);
        List<RavenSoldierEntity> mates = site != null ? soldiersOf(level, site.entry) : soldiersAround(caller);
        for (RavenSoldierEntity mate : mates) {
            if (mate != caller) {
                mate.senses().alarmed(level, target, lastSeen, memory);
            }
        }
        caller.senses().remember(memory);
        RavenRadio.contact(level, caller, body);
        if (site == null) {
            return;
        }
        boolean fresh = !site.alarmed(now);
        site.alarmUntil = now + Math.round(ImtConfig.Guards.ALARM_SECONDS * 20);
        site.target = target == null ? null : target.getUUID();
        site.lastSeen = BlockPos.containing(lastSeen);
        if (fresh) {
            site.waves = 0;
        }
        if (!site.backupPending() && site.waves < Math.round(ImtConfig.Guards.BACKUP_WAVES)) {
            site.backupAt = now + Math.max(1, Math.round(ImtConfig.Guards.BACKUP_DELAY_SECONDS * 20));
            RavenRadio.backupOnTheWay(level, caller);
        }
        sites.setDirty();
    }

    /** Een half gesprek: hij viel (of werd geboeid, of geraakt) voor hij klaar was. Zijn maten komen langzaam kijken. */
    public static void halfCall(ServerLevel level, RavenSoldierEntity caller) {
        Site site = get(level).siteOf(level, caller);
        List<RavenSoldierEntity> mates = site != null ? soldiersOf(level, site.entry) : soldiersAround(caller);
        Vec3 where = caller.position();
        for (RavenSoldierEntity mate : mates) {
            if (mate != caller) {
                mate.senses().noticed(where, true);
            }
        }
        RavenRadio.comeIn(level, caller);
    }

    /** Contact (hij ziet je nog): het alarm van zijn site loopt weer {@code alarmSeconds} door. */
    public static void touch(ServerLevel level, RavenSoldierEntity soldier, Vec3 seenAt) {
        RavenSites sites = existing(level);
        if (sites == null) {
            return;
        }
        BlockPos entry = soldier.senses().siteEntry(level);
        if (entry == null) {
            return;
        }
        Site site = sites.sites.get(entry);
        if (site != null && site.alarmed(level.getGameTime())) {
            site.alarmUntil = level.getGameTime() + Math.round(ImtConfig.Guards.ALARM_SECONDS * 20);
            site.lastSeen = BlockPos.containing(seenAt);
            sites.setDirty();
        }
    }

    // ================================================================ Elke tick: de teams

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            RavenSites sites = existing(level);
            if (sites != null && !sites.sites.isEmpty()) {
                sites.tick(level);
            }
        }
    }

    private void tick(ServerLevel level) {
        long now = level.getGameTime();
        for (Site site : sites.values()) {
            if (site.backupAt != 0 && now >= site.backupAt) {
                site.backupAt = 0;
                setDirty();
                if (level.isLoaded(site.entry)) {
                    spawnTeam(level, site);
                    site.waves++;
                }
            }
            if (site.alarmUntil != 0 && now >= site.alarmUntil) {
                site.alarmUntil = 0;
                site.target = null;
                setDirty();
                RavenRadio.standDown(level, site.entry);
                // 8 november: de versterking gaat weer naar buiten (anders werd de bunker na elk alarm voller).
                for (RavenSoldierEntity soldier : soldiersOf(level, site.entry)) {
                    soldier.leave();
                }
            }
        }
    }

    /** Het team komt binnen: {@code backupSize} soldaten bij de ingang, die naar je laatste plek rennen. */
    private static void spawnTeam(ServerLevel level, Site site) {
        LivingEntity target = site.target == null ? null : level.getEntity(site.target) instanceof LivingEntity living && living.isAlive() ? living : null;
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            target = null;
        }
        Vec3 lastSeen = site.lastSeen == null ? Vec3.atBottomCenterOf(site.entry) : Vec3.atBottomCenterOf(site.lastSeen);
        long memory = level.getGameTime() + Math.round(ImtConfig.Guards.MEMORY_MINUTES * 1200);
        int size = Math.max(1, Math.round(ImtConfig.Guards.BACKUP_SIZE));
        List<BlockPos> spots = new ArrayList<>();
        for (int tried = 0; tried < size * 6 && spots.size() < size; tried++) {
            BlockPos around = site.entry.offset(level.random.nextInt(5) - 2, 0, level.random.nextInt(5) - 2);
            BlockPos stand = RavenCombatGoal.standingSpot(level, around);
            if (stand != null && !spots.contains(stand)) {
                spots.add(stand);
            }
        }
        while (spots.size() < size) {
            spots.add(site.entry);
        }
        RavenRadio.teamEntering(level, site.entry);
        // 8 november: zolang het alarm loopt, lopen ze heen en weer tussen de ingang en je laatste plek (niet stilstaan);
        // na "stand down" gaan ze naar buiten (RavenSoldierEntity.leave).
        BlockPos watch = RavenCombatGoal.standingSpot(level, BlockPos.containing(lastSeen));
        List<BlockPos> route = watch == null || watch.closerThan(site.entry, 3) ? List.of() : List.of(site.entry, watch);
        for (BlockPos spot : spots) {
            RavenSoldierEntity soldier = com.senne.imttech.registry.ModEntities.RAVEN_SOLDIER.get().create(level);
            if (soldier == null) {
                continue;
            }
            soldier.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            soldier.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.REINFORCEMENT, null);
            soldier.setPersistenceRequired();
            soldier.setPost(site.entry);
            soldier.setRoute(route);
            soldier.markBackup();
            level.addFreshEntity(soldier);
            soldier.senses().alarmed(level, target, lastSeen, memory);
        }
    }

    // ================================================================ Opslaan

    public static RavenSites load(CompoundTag tag, HolderLookup.Provider registries) {
        RavenSites sites = new RavenSites();
        ListTag list = tag.getList("Sites", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Site site = new Site(BlockPos.of(entry.getLong("Entry")));
            site.alarmUntil = entry.getLong("AlarmUntil");
            site.waves = entry.getInt("Waves");
            site.backupAt = entry.getLong("BackupAt");
            site.target = entry.hasUUID("Target") ? entry.getUUID("Target") : null;
            site.lastSeen = entry.contains("LastSeen") ? BlockPos.of(entry.getLong("LastSeen")) : null;
            sites.sites.put(site.entry, site);
        }
        return sites;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Site site : sites.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Entry", site.entry.asLong());
            entry.putLong("AlarmUntil", site.alarmUntil);
            entry.putInt("Waves", site.waves);
            entry.putLong("BackupAt", site.backupAt);
            if (site.target != null) {
                entry.putUUID("Target", site.target);
            }
            if (site.lastSeen != null) {
                entry.putLong("LastSeen", site.lastSeen.asLong());
            }
            list.add(entry);
        }
        tag.put("Sites", list);
        return tag;
    }
}
