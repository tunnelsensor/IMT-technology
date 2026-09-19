package com.senne.starktech.client.voice;

import com.senne.starktech.client.DroneCamera;
import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.edith.EdithCooldown;
import com.senne.starktech.client.EdithHud;
import com.senne.starktech.client.NavigationScreen;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithFeature;
import com.senne.starktech.nav.EdithWaypoints;
import com.senne.starktech.nav.Waypoint;
import com.senne.starktech.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * De antwoorden op vragen aan E.D.I.T.H. ("hoe laat is het?", "hoeveel batterij?"...), in de taal
 * van je spel. Wordt gebruikt door de "say_..."-acties (zie VoiceAction en VoiceControl).
 *
 * Alles hier draait op de hoofd-thread van Minecraft (daar mag je de wereld lezen).
 */
final class VoiceInfo {
    private VoiceInfo() {}

    private static final String KEY = "voice.starktech.info.";
    /** Zo ver kijkt E.D.I.T.H. rond naar vijanden. */
    private static final double ENEMY_RANGE = 32;

    static Component answer(VoiceAction action) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return Component.empty();
        }
        return switch (action) {
            case SAY_TIME -> time(level);
            case SAY_WEATHER -> weather(level, player.blockPosition());
            case SAY_BATTERY -> battery(player);
            case SAY_HEALTH -> health(player);
            case SAY_ENEMIES -> enemies(level, player);
            case SAY_POSITION -> position(level, player);
            case SAY_TARGET -> target(mc, player);
            case SAY_DESTINATION -> destination(player);
            case SAY_COOLDOWNS -> cooldowns();
            case SAY_STATUS -> status(player);
            default -> Component.empty();
        };
    }

    /** "Het is 19:40, 's avonds." Een Minecraft-dag begint om 6 uur 's ochtends (tick 0). */
    private static Component time(ClientLevel level) {
        long ticks = level.getOverworldClockTime() % 24000;
        int hours = (int) ((ticks / 1000 + 6) % 24);
        int minutes = (int) (ticks % 1000 * 60 / 1000);
        String part = hours >= 5 && hours < 12 ? "morning" : hours >= 12 && hours < 18 ? "afternoon"
                : hours >= 18 && hours < 21 ? "evening" : "night";
        Component answer = Component.translatable(KEY + "time", String.format("%02d:%02d", hours, minutes),
                Component.translatable(KEY + "part." + part));
        // 's Avonds en 's nachts een waarschuwing: vanaf 19 uur komen de monsters.
        if (hours >= 18 || hours < 5) {
            answer = answer.copy().append(" ").append(Component.translatable(KEY + "time.monsters"));
        }
        return answer;
    }

    private static Component weather(ClientLevel level, BlockPos pos) {
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) {
            return Component.translatable(KEY + "weather.none");
        }
        if (!level.isRaining()) {
            return Component.translatable(KEY + "weather.clear");
        }
        Biome.Precipitation precipitation = level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel());
        String kind = switch (precipitation) {
            case RAIN -> level.isThundering() ? "thunder" : "rain";
            case SNOW -> "snow";
            case NONE -> "dry";
        };
        return Component.translatable(KEY + "weather." + kind);
    }

    private static Component battery(LocalPlayer player) {
        ItemStack glasses = EdithData.getWornGlasses(player);
        int energy = EdithData.getEnergy(glasses);
        return Component.translatable(KEY + "battery", Math.round(EdithData.getEnergyFraction(glasses) * 100),
                EdithHud.timeLeft(energy, EdithHud.drainPerSecond(glasses)));
    }

    private static Component health(LocalPlayer player) {
        int food = player.getFoodData().getFoodLevel();
        Component answer = Component.translatable(KEY + "health", Math.round(player.getHealth()), Math.round(player.getMaxHealth()), food);
        if (food <= 14) {
            answer = answer.copy().append(" ").append(Component.translatable(KEY + "health.eat"));
        }
        return answer;
    }

    private static Component enemies(ClientLevel level, LocalPlayer player) {
        List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ENEMY_RANGE),
                entity -> entity instanceof Enemy && entity.isAlive() && entity.distanceTo(player) <= ENEMY_RANGE);
        if (enemies.isEmpty()) {
            return Component.translatable(KEY + "enemies.none");
        }
        LivingEntity closest = enemies.stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(player))).orElseThrow();
        return Component.translatable(KEY + "enemies", enemies.size(), closest.getDisplayName(), Math.round(closest.distanceTo(player)));
    }

    private static Component position(ClientLevel level, LocalPlayer player) {
        Component biome = level.getBiome(player.blockPosition()).unwrapKey()
                .map(key -> (Component) Component.translatable(key.identifier().toLanguageKey("biome")))
                .orElse(Component.literal("?"));
        return Component.translatable(KEY + "position", NavigationScreen.dimensionName(level.dimension()), biome,
                player.getBlockX(), player.getBlockY(), player.getBlockZ());
    }

    private static Component target(Minecraft mc, LocalPlayer player) {
        LivingEntity target = EdithClientState.lookTarget;
        if (target != null && target.isAlive()) {
            return Component.translatable(KEY + "target", target.getDisplayName(), Math.round(target.distanceTo(player)),
                    Math.round(target.getHealth()), Math.round(target.getMaxHealth()));
        }
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && mc.level != null) {
            return Component.translatable(KEY + "target.block", mc.level.getBlockState(block.getBlockPos()).getBlock().getName());
        }
        return Component.translatable(KEY + "target.none");
    }

    private static Component destination(LocalPlayer player) {
        EdithWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        Vec3 position;
        Component name;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        if (data.active() == EdithWaypoints.DEATH) {
            Optional<GlobalPos> death = player.getLastDeathLocation();
            if (death.isEmpty()) {
                return Component.translatable(KEY + "destination.none");
            }
            position = Vec3.atCenterOf(death.get().pos());
            name = Component.translatable("edith.starktech.nav.death");
            dimension = death.get().dimension();
        } else {
            Waypoint waypoint = data.activeWaypoint();
            if (waypoint == null) {
                return Component.translatable(KEY + "destination.none");
            }
            position = Vec3.atCenterOf(waypoint.pos());
            name = Component.literal(waypoint.name());
            dimension = waypoint.dimension();
        }
        if (dimension != player.level().dimension()) {
            return Component.translatable(KEY + "destination.other", name, NavigationScreen.dimensionName(dimension));
        }
        return Component.translatable(KEY + "destination", Math.round(Math.sqrt(player.distanceToSqr(position))), name);
    }

    private static Component cooldowns() {
        return Component.translatable(KEY + "cooldowns", cooldown(EdithClientState.cooldown(EdithCooldown.SWARM)),
                cooldown(EdithClientState.cooldown(EdithCooldown.SATELLITE)),
                cooldown(EdithClientState.cooldown(EdithCooldown.ORE_SCAN)));
    }

    private static Component cooldown(int ticks) {
        int seconds = EdithClientState.secondsLeft(ticks);
        return seconds > 0 ? Component.translatable(KEY + "seconds", seconds) : Component.translatable(KEY + "ready");
    }

    /** Batterij, wat er aan staat, en hoeveel drones er vliegen. */
    private static Component status(LocalPlayer player) {
        ItemStack glasses = EdithData.getWornGlasses(player);
        List<String> on = new ArrayList<>();
        for (EdithFeature feature : EdithFeature.values()) {
            if (EdithData.isEnabled(glasses, feature)) {
                on.add(Component.translatable(feature.translationKey()).getString());
            }
        }
        if (DroneCamera.isActive()) {
            on.add(Component.translatable("edith.starktech.action.drone_camera").getString());
        }
        Component systems = on.isEmpty()
                ? Component.translatable(KEY + "status.nothing")
                : Component.translatable(KEY + "status.active", String.join(", ", on));
        Component answer = Component.translatable(KEY + "status", Math.round(EdithData.getEnergyFraction(glasses) * 100), systems);
        int drones = EdithClientState.swarmDrones + EdithClientState.guardDrones;
        if (drones > 0) {
            answer = answer.copy().append(" ").append(Component.translatable(KEY + "status.drones", drones));
        }
        return answer;
    }
}
