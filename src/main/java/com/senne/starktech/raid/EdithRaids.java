package com.senne.starktech.raid;

import com.senne.starktech.StarkTech;
import com.senne.starktech.drone.EdithDrones;
import com.senne.starktech.edith.EdithConfig;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.EdithMessagePayload;
import java.util.HashMap;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Wie er achter de raiddrones zit, zegt niemand. Wat je merkt is dat ze komen.
 *
 * Twee dingen halen ze binnen:
 *
 * 1. **Vanzelf.** Draag je de E.D.I.T.H.-bril, dan ben je een doelwit. Om de zoveel tijd komt
 *    er een golf; hoe vaak precies staat in de serverconfig.
 * 2. **Omdat je erom vroeg.** Sloop je in een Stark Tower, dan trek je aandacht. Genoeg
 *    aandacht en ze komen meteen, zonder te wachten op de klok.
 *
 * Alles wordt per speler bijgehouden en niet opgeslagen: na een herstart begint de klok
 * opnieuw. Dat is expres - je wil niet inloggen en meteen een raid over je heen krijgen.
 */
public final class EdithRaids {
    private EdithRaids() {}

    /** De Stark Tower, om te herkennen dat je in hun gebouw staat te slopen. */
    private static final ResourceKey<Structure> STARK_TOWER = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "stark_tower"));

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
        int min = EdithConfig.raidMinMinutes();
        int max = Math.max(min, EdithConfig.raidMaxMinutes());
        int minutes = min + level.random.nextInt(max - min + 1);
        return (long) minutes * 60L * 20L;
    }

    // ================================================================ Elke paar seconden

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!EdithConfig.raidsEnabled()) {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        if (now % CHECK_INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            // Zonder bril ben je niemand voor ze.
            ItemStack glasses = EdithData.getWornGlasses(player);
            if (glasses.isEmpty() || !EdithData.isAuthorized(glasses, player)) {
                continue;
            }
            Watch watch = watch(player, now);
            coolDown(watch, now);
            if (now >= watch.nextRaidAt) {
                startRaid(player, EdithConfig.raidSize());
                watch.nextRaidAt = now + randomDelay(player.serverLevel());
            }
        }
    }

    /** Aandacht zakt vanzelf weg als je je een tijdje gedraagt. */
    private static void coolDown(Watch watch, long now) {
        long minutes = (now - watch.lastCooled) / (60L * 20L);
        if (minutes <= 0) {
            return;
        }
        watch.heat = Math.max(0, watch.heat - (int) minutes * RaidBalance.HEAT_COOLDOWN_PER_MINUTE);
        watch.lastCooled = now;
    }

    // ================================================================ Slopen in een Stark Tower

    /**
     * Haal je een blok weg in een Stark Tower, dan valt dat op. Doe je het vaak genoeg, dan
     * komen ze kijken.
     */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!EdithConfig.raidsEnabled() || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isCreative() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Er is geen opzoeking op naam, dus vragen we het per structuur: is dit de Stark Tower?
        if (!level.structureManager()
                .getStructureWithPieceAt(event.getPos(), holder -> holder.is(STARK_TOWER)).isValid()) {
            return;
        }
        long now = level.getGameTime();
        Watch watch = watch(player, now);
        coolDown(watch, now);
        watch.heat += RaidBalance.HEAT_PER_TOWER_BLOCK;
        if (watch.heat >= RaidBalance.HEAT_FOR_RAID) {
            watch.heat = 0;
            watch.nextRaidAt = now + randomDelay(level);
            startRaid(player, EdithConfig.raidSize());
        } else if (watch.heat == RaidBalance.HEAT_PER_TOWER_BLOCK) {
            // Eén keer waarschuwen, bij het eerste blok.
            EdithServer.send(player, Component.translatable("raid.starktech.msg.noticed"),
                    EdithMessagePayload.Level.WARNING);
        }
    }

    // ================================================================ De golf zelf

    /** Stuurt een golf raiddrones op deze speler af. Geeft terug hoeveel er echt kwamen. */
    public static int startRaid(ServerPlayer player, int count) {
        ServerLevel level = player.serverLevel();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            // Verspreid rond de speler. Ze duiken naar een plek iets boven hem, en beginnen zo
            // hoog als er vrije lucht is boven die plek: onder een dak of in een grot dus lager.
            // (Eerst keken we naar de hoogte van de grond onder elke drone apart; stond je naast
            // een gebouw, dan begon een drone boven dat gebouw, of dwars door een plafond heen.)
            double angle = i * (Math.PI * 2 / Math.max(1, count));
            double x = player.getX() + Math.cos(angle) * RaidBalance.SPREAD;
            double z = player.getZ() + Math.sin(angle) * RaidBalance.SPREAD;
            double targetY = player.getY() + RaidBalance.HOVER_ABOVE;
            double from = EdithDrones.freeHeightAbove(level, x, targetY, z, RaidBalance.ARRIVAL_HEIGHT);
            if (RaidDroneEntity.spawn(level, new Vec3(x, from, z), targetY) != null) {
                spawned++;
            }
        }
        if (spawned > 0) {
            EdithServer.send(player, Component.translatable("raid.starktech.msg.incoming", spawned),
                    EdithMessagePayload.Level.ALERT);
            level.playSound(null, BlockPos.containing(player.position()),
                    com.senne.starktech.registry.ModSounds.EDITH_ALERT.get(), SoundSource.PLAYERS, 1.0f, 0.6f);
        }
        return spawned;
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
