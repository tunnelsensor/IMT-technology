package com.senne.starktech.edith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Wie valt jou aan?
 *
 * De oude dreigingsscan markeerde alles wat vijandig was en in de buurt liep - ook de zombie
 * drie bomen verderop die je niet eens gezien had. Dat is veel rood in beeld en weinig
 * informatie. Nu markeren we alleen wat zich echt met jou bemoeit:
 *
 * - wezens die JOU als doelwit hebben (een zombie die op je afkomt, een skelet dat richt);
 * - alles wat je de laatste seconden geraakt heeft, ook als het zich intussen omdraaide;
 * - pijlen en vuurballen die onderweg zijn naar jou toe.
 *
 * Dit moet op de server gebeuren: welk doelwit een wezen heeft, weet jouw eigen spel niet.
 */
public final class EdithThreats {
    private EdithThreats() {}

    /** Zo lang blijft iemand die je raakte als dreiging gemarkeerd (in ticks). */
    private static final int ATTACKER_MEMORY_TICKS = 160;

    /** Per speler: wie hem raakte, en wanneer. */
    private static final Map<UUID, Map<Integer, Long>> RECENT_ATTACKERS = new HashMap<>();

    /** Iemand deed jou pijn: die onthouden we even, ook als hij zich omdraait. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (source == null || source == player) {
            return;
        }
        RECENT_ATTACKERS.computeIfAbsent(player.getUUID(), id -> new HashMap<>())
                .put(source.getId(), player.level().getGameTime());
    }

    /**
     * De dreigingen voor deze speler, dichtstbij eerst.
     *
     * @param radius hoe ver we kijken (uit de bril: de scanner-upgrade maakt dit groter)
     */
    public static List<LivingEntity> of(ServerPlayer player, int radius) {
        long now = player.level().getGameTime();
        Map<Integer, Long> attackers = RECENT_ATTACKERS.get(player.getUUID());
        if (attackers != null) {
            attackers.entrySet().removeIf(entry -> now - entry.getValue() > ATTACKER_MEMORY_TICKS);
            if (attackers.isEmpty()) {
                RECENT_ATTACKERS.remove(player.getUUID());
                attackers = null;
            }
        }

        AABB area = player.getBoundingBox().inflate(radius);
        List<LivingEntity> found = new ArrayList<>();
        for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, area,
                other -> other != player && other.isAlive())) {
            if (living.distanceToSqr(player) > (double) radius * radius) {
                continue;
            }
            boolean huntingYou = living instanceof Mob mob && mob.getTarget() == player;
            boolean hitYou = attackers != null && attackers.containsKey(living.getId());
            if (huntingYou || hitYou) {
                found.add(living);
            }
        }
        found.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
        return found;
    }

    /**
     * Pijlen, vuurballen en ander gooiwerk dat jouw kant op komt.
     * We kijken of het projectiel naar je toe beweegt, niet alleen of het in de buurt is.
     */
    public static List<Integer> incomingProjectiles(ServerPlayer player, int radius) {
        AABB area = player.getBoundingBox().inflate(radius);
        List<Integer> ids = new ArrayList<>();
        for (Projectile projectile : player.level().getEntitiesOfClass(Projectile.class, area, Entity::isAlive)) {
            if (projectile.getOwner() == player) {
                continue; // je eigen pijl telt niet
            }
            Vec3 motion = projectile.getDeltaMovement();
            if (motion.lengthSqr() < 0.01) {
                continue; // ligt stil (bv. een pijl in de grond)
            }
            Vec3 toPlayer = player.position().add(0, player.getBbHeight() / 2, 0).subtract(projectile.position());
            // Komt hij onze kant op? (hoek tussen vliegrichting en de lijn naar jou)
            if (motion.normalize().dot(toPlayer.normalize()) > 0.85) {
                ids.add(projectile.getId());
            }
        }
        return ids;
    }

    public static void forget(ServerPlayer player) {
        RECENT_ATTACKERS.remove(player.getUUID());
    }

    public static void clear() {
        RECENT_ATTACKERS.clear();
    }
}
