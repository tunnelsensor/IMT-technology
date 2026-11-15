package com.senne.imttech.raid;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.drone.ImtDrones;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.security.zone.ZoneBox;
import com.senne.imttech.security.zone.ZoneData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Wie er achter de raiddrones zit, zegt niemand. Wat je merkt is dat ze komen.
 *
 * Twee dingen halen ze binnen:
 *
 * 1. **Vanzelf.** Draag je de I.M.T-bril, dan ben je een doelwit. Om de zoveel tijd komt
 *    er een golf; hoe vaak precies staat in de serverconfig.
 * 2. **Omdat je erom vroeg.** Sloop je in een Biebia's Industries Tower, dan trek je aandacht. Genoeg
 *    aandacht en ze komen meteen, zonder te wachten op de klok.
 * 3. **Omdat je bij het wrak komt.** Een neergestorte Biebia's Industries-satelliet zendt nog; zij luisteren
 *    mee. Kom je er met de bril, dan duiken ze meteen naar beneden (hooguit eens per
 *    CRASH_SITE_COOLDOWN).
 *
 * Alles wordt per speler bijgehouden en niet opgeslagen: na een herstart begint de klok
 * opnieuw. Dat is expres - je wil niet inloggen en meteen een raid over je heen krijgen.
 *
 * Sinds hoofdstuk 54 (jouw notitie) begint een raid altijd <b>buiten een basis</b>: een basis is een beveiligingskern
 * met zijn zone (of, zonder zone, BASE_SIZE blokken rondom de kern), en de drones komen minstens BASE_MARGIN blokken
 * daarbuiten aan. Zit je in een beschermde zone, dan moeten ze eerst door je verdediging heen: zie RaidDroneEntity.
 *
 * Sinds hoofdstuk 75 (playtest) is een raid voorbij als je doodgaat: de drones die voor jou kwamen (en elke andere die op
 * jou schoot) vliegen weg, zodat je je spullen kunt gaan halen. Zie {@link #onPlayerDeath}.
 */
public final class ImtRaids {
    private ImtRaids() {}

    /** De Biebia's Industries Tower, om te herkennen dat je in hun gebouw staat te slopen. */
    private static final ResourceKey<Structure> BIEBIA_TOWER = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "biebia_tower"));
    /** Het wrak van een Biebia's Industries-satelliet (zie tools/generate_crash_site.py). */
    private static final ResourceKey<Structure> CRASHED_SATELLITE = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "crashed_satellite"));

    /** Hoe vaak we kijken of er iets moet gebeuren (in ticks). 100 ticks = 5 seconden. */
    private static final int CHECK_INTERVAL = 100;

    /** Wat we per speler onthouden. */
    private static final class Watch {
        /** Speltijd waarop de volgende golf mag komen. */
        long nextRaidAt;
        /** Hoeveel aandacht je getrokken hebt door te slopen waar dat niet mag. */
        int heat;
        /** Wanneer we voor het laatst aandacht hebben afgebouwd. */
        long lastCooled;
        /** Speltijd waarop het wrak van een satelliet ze weer mag roepen. */
        long crashSiteReadyAt;
    }

    private static final Map<UUID, Watch> WATCHED = new HashMap<>();

    private static Watch watch(ServerPlayer player, long now) {
        return WATCHED.computeIfAbsent(player.getUUID(), id -> {
            Watch fresh = new Watch();
            fresh.nextRaidAt = now + randomDelay(player.serverLevel());
            fresh.lastCooled = now;
            return fresh;
        });
    }

    /** Een willekeurige tijd tot de volgende golf, binnen wat de config toestaat. */
    private static long randomDelay(ServerLevel level) {
        float min = ImtConfig.Raids.MIN_MINUTES;
        float max = Math.max(min, ImtConfig.Raids.MAX_MINUTES);
        float minutes = min + level.random.nextFloat() * (max - min);
        return Math.max(20L, Math.round(minutes * 60.0 * 20.0));
    }

    // ================================================================ Elke paar seconden

    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        if (now % CHECK_INTERVAL != 0) {
            return;
        }
        if (!ModGameRules.raidsOn(event.getServer().overworld())) {
            // Hoofdstuk 75: uit (config of /gamerule imttechRaidDrones false). Alles vergeten, zodat de klok opnieuw begint
            // als ze weer aan gaan: anders kwam er meteen een golf die al lang "te laat" was.
            WATCHED.clear();
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            // Zonder bril ben je niemand voor ze.
            ItemStack glasses = ImtData.getWornGlasses(player);
            if (glasses.isEmpty() || !ImtData.isAuthorized(glasses, player)) {
                continue;
            }
            Watch watch = watch(player, now);
            coolDown(watch, now);
            if (now >= watch.nextRaidAt) {
                startRaid(player, Math.round(ImtConfig.Raids.SIZE));
                watch.nextRaidAt = now + randomDelay(player.serverLevel());
            } else if (now >= watch.crashSiteReadyAt && atCrashSite(player)) {
                watch.crashSiteReadyAt = now + Math.round(ImtConfig.Raids.CRASH_SITE_COOLDOWN);
                // De gewone klok schuift ook op, anders komt er vlak daarna nog een golf.
                watch.nextRaidAt = Math.max(watch.nextRaidAt, now + randomDelay(player.serverLevel()));
                ImtServer.send(player, Component.translatable("raid.imttech.msg.crash_site"),
                        ImtMessagePayload.Level.WARNING);
                startRaid(player, Math.round(ImtConfig.Raids.SIZE));
            }
        }
    }

    /** Staat deze speler in het wrak van een neergestorte satelliet (de krater en de voor)? */
    private static boolean atCrashSite(ServerPlayer player) {
        return player.serverLevel().structureManager()
                .getStructureWithPieceAt(player.blockPosition(), holder -> holder.is(CRASHED_SATELLITE)).isValid();
    }

    /** Aandacht zakt vanzelf weg als je je een tijdje gedraagt. */
    private static void coolDown(Watch watch, long now) {
        long minutes = (now - watch.lastCooled) / (60L * 20L);
        if (minutes <= 0) {
            return;
        }
        watch.heat = Math.max(0, watch.heat - (int) minutes * Math.round(ImtConfig.Raids.HEAT_COOLDOWN_PER_MINUTE));
        watch.lastCooled = now;
    }

    // ================================================================ Slopen in een Biebia's Industries Tower

    /**
     * Haal je een blok weg in een Biebia's Industries Tower, dan valt dat op. Doe je het vaak genoeg, dan
     * komen ze kijken.
     */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            towerBlockBroken(player, level, event.getPos());
        }
    }

    /**
     * Deze speler haalde dit blok weg, zelf of (hoofdstuk 108) met zijn RC Car: ligt het in een Biebia's Industries
     * Tower, dan valt dat op, en doet hij het vaak genoeg, dan komen ze kijken. {@code level} is de wereld van het blok:
     * een auto kan in een andere wereld rijden dan waar zijn baas nu is.
     */
    public static void towerBlockBroken(ServerPlayer player, ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (player.isCreative() || !ModGameRules.raidsOn(level)) {
            return;
        }
        // Er is geen opzoeking op naam, dus vragen we het per structuur: is dit de Biebia's Industries Tower?
        if (!level.structureManager()
                .getStructureWithPieceAt(pos, holder -> holder.is(BIEBIA_TOWER)).isValid()) {
            return;
        }
        long now = level.getGameTime();
        Watch watch = watch(player, now);
        coolDown(watch, now);
        watch.heat += Math.round(ImtConfig.Raids.HEAT_PER_TOWER_BLOCK);
        if (watch.heat >= Math.round(ImtConfig.Raids.HEAT_FOR_RAID)) {
            watch.heat = 0;
            watch.nextRaidAt = now + randomDelay(level);
            startRaid(player, Math.round(ImtConfig.Raids.SIZE));
        } else if (watch.heat == Math.round(ImtConfig.Raids.HEAT_PER_TOWER_BLOCK)) {
            // Eén keer waarschuwen, bij het eerste blok.
            ImtServer.send(player, Component.translatable("raid.imttech.msg.noticed"),
                    ImtMessagePayload.Level.WARNING);
        }
    }

    // ================================================================ De golf zelf

    /** Stuurt een golf raiddrones op deze speler af. Geeft terug hoeveel er echt kwamen. */
    public static int startRaid(ServerPlayer player, int count) {
        ServerLevel level = player.serverLevel();
        if (!level.getGameRules().getBoolean(ModGameRules.RAID_DRONES)) {
            return 0; // hoofdstuk 75: /gamerule imttechRaidDrones false
        }
        List<ZoneBox> bases = basesNear(level, player.blockPosition());
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            // Verspreid rond de speler. Ze duiken in een boog naar een plek iets boven hem (elk
            // uit een andere richting), en beginnen zo hoog als er vrije lucht is: onder een dak
            // of in een grot dus lager (zie ImtDrones.arcStart). Maar nooit in een basis (hoofdstuk 54): dan schuift
            // de plek in dezelfde richting naar buiten, tot buiten de zone en de rand eromheen.
            double angle = i * (Math.PI * 2 / Math.max(1, count));
            Vec3 landing = outsideBases(level, bases, player.position(), angle, ImtConfig.Raids.SPREAD)
                    .add(0, ImtConfig.Raids.HOVER_ABOVE, 0);
            Vec3 start = ImtDrones.arcStart(level, landing, angle, Math.round(ImtConfig.Raids.ARRIVAL_HEIGHT));
            RaidDroneEntity drone = RaidDroneEntity.spawn(level, start, landing);
            if (drone != null) {
                drone.setRaidTarget(player);
                spawned++;
            }
        }
        if (spawned > 0) {
            ImtServer.send(player, Component.translatable("raid.imttech.msg.incoming", spawned),
                    ImtMessagePayload.Level.ALERT);
            level.playSound(null, BlockPos.containing(player.position()),
                    com.senne.imttech.registry.ModSounds.IMT_ALERT.get(), SoundSource.PLAYERS, 1.0f, 0.6f);
        }
        return spawned;
    }

    // ================================================================ Je gaat dood (hoofdstuk 75)

    /**
     * Hoofdstuk 75 (playtest: "als je niks meer hebt is je spul terug krijgen moeilijk. Zorg dat ze na je 1x te killen
     * verdwijnen"): ga je dood, dan vliegt elke raiddrone weg die voor jou kwam (in elke dimensie) of die op dat moment
     * op jou schoot. Een drone die voor een ander kwam en jou niet op de korrel had, blijft voor die ander.
     *
     * Als (bijna) allerlaatste geluisterd, net als het lichaam (PlayerBodies.onDeath): redt een andere mod je nog (en
     * zegt die dat je niet doodgaat), dan komt het hier niet aan en blijven ze gewoon.
     */
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (callOff(player) > 0) {
            // In de chat (die zie je ook op het doodscherm); de melding van de bril zou je niet zien, die ligt in je lichaam.
            player.sendSystemMessage(Component.translatable("raid.imttech.msg.pulling_out")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }

    /**
     * De raid op deze speler is voorbij: elke raiddrone die voor hem kwam (in elke dimensie) of die nu op hem schiet,
     * vliegt weg (RaidDroneEntity.leave), weg van waar hij staat. Geeft terug hoeveel er vertrokken. Ook voor de showcase.
     */
    public static int callOff(ServerPlayer player) {
        UUID id = player.getUUID();
        int leaving = 0;
        for (ServerLevel level : player.server.getAllLevels()) {
            boolean here = level == player.serverLevel();
            for (RaidDroneEntity drone : level.getEntities(com.senne.imttech.registry.ModEntities.RAID_DRONE.get(),
                    drone -> drone.isAlive() && !drone.isLeaving()
                            && (id.equals(drone.raidTargetId()) || here && drone.getTarget() == player))) {
                drone.leave(here ? player.position() : drone.position());
                leaving++;
            }
        }
        return leaving;
    }

    // ================================================================ Buiten de basis (hoofdstuk 54)

    /** Zo ver rondom de speler kijken we naar kernen, bovenop ZONE_RANGE (zo ver kan een zone van haar kern liggen). */
    private static final int NEARBY = 64;
    /** Hooguit zo ver schuift de aankomst naar buiten, als een basis (of een rij basissen) in de weg ligt. */
    private static final double MAX_BASE_WALK = 512;

    /**
     * De basissen in de buurt: elke beveiligingskern telt (jouw woorden: "een base wordt enkel een base genoemd als het
     * een security core heeft"), ook die van Biebia's. Met haar zone als ze er een heeft, anders BASE_SIZE blokken
     * rondom de kern.
     */
    static List<ZoneBox> basesNear(ServerLevel level, BlockPos around) {
        List<ZoneBox> bases = new ArrayList<>();
        int size = Math.max(1, (int) Math.round(ImtConfig.Raids.BASE_SIZE));
        int nearby = Math.round(ImtConfig.Security.ZONE_RANGE) + NEARBY;
        for (ZoneData.Zone zone : List.copyOf(ZoneData.get(level).in(level.dimension()))) {
            BlockPos core = zone.core().pos();
            if (Math.abs(core.getX() - around.getX()) > nearby || Math.abs(core.getZ() - around.getZ()) > nearby) {
                continue;
            }
            List<ZoneBox> boxes = zone.boxesInReach();
            if (boxes.isEmpty()) {
                bases.add(ZoneBox.of(core.offset(-size, -size, -size), core.offset(size, size, size)));
            } else {
                bases.addAll(boxes);
            }
        }
        return bases;
    }

    /**
     * De plek op {@code distance} blokken van {@code from} in richting {@code angle}; ligt die in (of vlak bij) een basis,
     * dan schuift hij in dezelfde richting naar buiten. Buiten de basis kan de grond hoger liggen (een basis op een berg):
     * dan niet in de helling, maar erboven.
     */
    static Vec3 outsideBases(ServerLevel level, List<ZoneBox> bases, Vec3 from, double angle, double distance) {
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        Vec3 at = from.add(dx * distance, 0, dz * distance);
        if (!insideAny(bases, at)) {
            return at;
        }
        for (double d = distance + 1; d <= distance + MAX_BASE_WALK; d += 1.0) {
            at = from.add(dx * d, 0, dz * d);
            if (!insideAny(bases, at)) {
                break;
            }
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z));
        return new Vec3(at.x, Math.max(at.y, surface + 1), at.z);
    }

    /** Ligt deze plek in een basis, of binnen BASE_MARGIN ervan? Alleen in het platte vlak: een basis telt ook van bovenaf. */
    private static boolean insideAny(List<ZoneBox> bases, Vec3 at) {
        double margin = ImtConfig.Raids.BASE_MARGIN;
        for (ZoneBox box : bases) {
            if (at.x >= box.min().getX() - margin && at.x <= box.max().getX() + 1 + margin
                    && at.z >= box.min().getZ() - margin && at.z <= box.max().getZ() + 1 + margin) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ Opruimen

    public static void onServerStopped(ServerStoppedEvent event) {
        WATCHED.clear();
    }

    /** Alleen voor de tests: begin met een schone lei. */
    public static void clear() {
        WATCHED.clear();
    }
}
