package com.senne.starktech.satellite;

import com.senne.starktech.arsenal.ArsenalDrone;
import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModSounds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De werkplaats van de satelliet: een upgrade kopen en de onderdelen omhoog laten brengen.
 *
 * Zo gaat een upgrade:
 * 1. je kiest in het satellietscherm een onderdeel;
 * 2. we kijken of je de onderdelen bij je hebt en of er genoeg drones in je baan hangen;
 * 3. de onderdelen gaan uit je rugzak, en de drones vliegen ze voor je ogen de lucht in;
 * 4. zodra ze boven zijn (een paar seconden) gaat de upgrade in, en hangen de drones weer
 *    gewoon in je arsenaal.
 *
 * Er kan per speler één levering tegelijk onderweg zijn.
 */
public final class SatelliteWorkshop {
    private SatelliteWorkshop() {}


    /** Een levering die onderweg is. */
    private record Delivery(SatelliteUpgrade upgrade, int level, int ticksLeft) {
        Delivery tick() {
            return new Delivery(upgrade, level, ticksLeft - 1);
        }
    }

    private static final Map<UUID, Delivery> DELIVERIES = new HashMap<>();

    public static SatelliteUpgrades get(ServerPlayer player) {
        return player.getData(ModAttachments.SATELLITE);
    }

    /** Is er voor deze speler een levering onderweg? Dan zegt dit welk onderdeel, anders null. */
    public static @Nullable SatelliteUpgrade inTransit(ServerPlayer player) {
        Delivery delivery = DELIVERIES.get(player.getUUID());
        return delivery == null ? null : delivery.upgrade();
    }

    /** Waarom een upgrade (nog) niet kan, of null als hij kan. */
    public static @Nullable Component whyNot(ServerPlayer player, SatelliteUpgrade upgrade) {
        int next = get(player).level(upgrade) + 1;
        if (next > SatelliteUpgrade.MAX_LEVEL) {
            return Component.translatable("satellite.starktech.msg.maxed");
        }
        if (DELIVERIES.containsKey(player.getUUID())) {
            return Component.translatable("satellite.starktech.msg.busy");
        }
        for (ItemStack needed : upgrade.costFor(next)) {
            if (count(player.getInventory(), needed) < needed.getCount()) {
                return Component.translatable("satellite.starktech.msg.missing", needed.getCount(), needed.getHoverName());
            }
        }
        int drones = SatelliteUpgrade.dronesFor(next);
        if (ArsenalServer.get(player).availableCount() < drones) {
            return Component.translatable("satellite.starktech.msg.drones", drones);
        }
        return null;
    }

    /** Een upgrade kopen (uit het satellietscherm). */
    public static void upgrade(ServerPlayer player, SatelliteUpgrade upgrade) {
        if (EdithServer.authorizedGlasses(player) == null) {
            return;
        }
        Component problem = whyNot(player, upgrade);
        if (problem != null) {
            EdithServer.send(player, problem, EdithMessagePayload.Level.WARNING);
            return;
        }
        int next = get(player).level(upgrade) + 1;
        for (ItemStack needed : upgrade.costFor(next)) {
            take(player.getInventory(), needed);
        }
        List<ArsenalDrone> carriers = ArsenalServer.checkOut(player, SatelliteUpgrade.dronesFor(next));
        launchCarriers(player, carriers);
        DELIVERIES.put(player.getUUID(), new Delivery(upgrade, next, StarkConfig.Satellite.DELIVERY_TICKS));
        EdithServer.send(player, Component.translatable("satellite.starktech.msg.sent", carriers.size(),
                upgrade.label(), next), EdithMessagePayload.Level.INFO);
    }

    /**
     * Creative: alles meteen op het hoogste niveau, zonder onderdelen en zonder drones. Handig
     * om de satelliet op volle sterkte te proberen. In survival kan dit niet.
     */
    public static void maxAll(ServerPlayer player) {
        if (EdithServer.authorizedGlasses(player) == null) {
            return;
        }
        if (!player.isCreative()) {
            EdithServer.send(player, Component.translatable("satellite.starktech.msg.creative_only"),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        SatelliteUpgrades all = get(player);
        for (SatelliteUpgrade upgrade : SatelliteUpgrade.values()) {
            all = all.with(upgrade, SatelliteUpgrade.MAX_LEVEL);
        }
        player.setData(ModAttachments.SATELLITE, all);
        DELIVERIES.remove(player.getUUID());
        EdithServer.send(player, Component.translatable("satellite.starktech.msg.maxed_all"), EdithMessagePayload.Level.INFO);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.STATION_UPGRADE.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    /** De drones stijgen op vlak voor je, met de onderdelen, en komen straks in je arsenaal terug. */
    private static void launchCarriers(ServerPlayer player, List<ArsenalDrone> carriers) {
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < carriers.size(); i++) {
            ArsenalDrone carrier = carriers.get(i);
            StarkDroneEntity drone = ModEntities.STARK_DRONE.get().create(level);
            if (drone == null) {
                ArsenalServer.returnToOrbit(player, carrier.id(), carrier.health());
                continue;
            }
            drone.setup(player, StarkDroneEntity.Mode.LAUNCH, 0, StarkConfig.Drones.LAUNCH_TICKS);
            drone.setArsenalId(carrier.id());
            drone.setHealth(carrier.health());
            // In een kring om je heen, zodat je ze allemaal ziet opstijgen.
            double angle = i * (Math.PI * 2 / carriers.size());
            drone.moveTo(player.getX() + Math.cos(angle) * 2.0, player.getEyeY(),
                    player.getZ() + Math.sin(angle) * 2.0, player.getYRot(), 0.0f);
            level.addFreshEntity(drone);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DRONE_DEPLOY.get(),
                SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /** Elke tick: leveringen die boven aankomen, worden ingebouwd. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (DELIVERIES.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Delivery>> it = DELIVERIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Delivery> entry = it.next();
            Delivery delivery = entry.getValue().tick();
            if (delivery.ticksLeft() > 0) {
                entry.setValue(delivery);
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                install(player, delivery.upgrade(), delivery.level());
            }
        }
    }

    /** De onderdelen zijn boven: de upgrade gaat in. */
    public static void install(ServerPlayer player, SatelliteUpgrade upgrade, int level) {
        player.setData(ModAttachments.SATELLITE, get(player).with(upgrade, level));
        Component message = upgrade.isMode() && level == 1
                ? Component.translatable("satellite.starktech.msg.unlocked", upgrade.label())
                : Component.translatable("satellite.starktech.msg.installed", upgrade.label(), level);
        EdithServer.send(player, message, EdithMessagePayload.Level.INFO);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.STATION_UPGRADE.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    // ================================================================ Rugzak

    /** Hoeveel van dit voorwerp je bij je hebt. */
    public static int count(Inventory inventory, ItemStack wanted) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(wanted.getItem())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void take(Inventory inventory, ItemStack wanted) {
        int left = wanted.getCount();
        for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(wanted.getItem())) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        inventory.setChanged();
    }

    // ================================================================ Opruimen

    /**
     * Uitgelogd terwijl er een levering onderweg was? Dan bouwen we hem meteen in: je hebt er
     * al voor betaald, en de drones klimmen gewoon verder.
     */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Delivery delivery = DELIVERIES.remove(event.getEntity().getUUID());
        if (delivery != null && event.getEntity() instanceof ServerPlayer player) {
            player.setData(ModAttachments.SATELLITE, get(player).with(delivery.upgrade(), delivery.level()));
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        DELIVERIES.clear();
    }
}
