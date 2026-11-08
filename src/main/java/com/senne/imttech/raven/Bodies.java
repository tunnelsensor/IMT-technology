package com.senne.imttech.raven;

import com.senne.imttech.bot.BotEntity;
import com.senne.imttech.cuffs.Handcuffs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Lichamen (hoofdstuk 60, stap 5): wat voor een bewaker een "lichaam" is, en wie het al gemeld heeft.
 * <ul>
 *   <li>het lichaam van een gevallen soldaat ({@link RavenBodyEntity}; een dode soldaat laat er een achter, zie
 *       {@link #onLivingDrops});</li>
 *   <li>een geboeide soldaat (hoofdstuk 48; jouw antwoord 5: "een geboeide soldaat telt ook");</li>
 *   <li>een gevloerde robot of drone (de Ion Blaster, hoofdstuk 24; "een neergelegde robot telt ook");</li>
 *   <li>een soldaat die iemand vastheeft voor een sluipaanval (8 november, Takedown).</li>
 * </ul>
 * Een bewaker die er een ziet (RavenSenses.lookForBodies), loopt erheen en roept via de radio ("Man down!"): hetzelfde
 * alarm als bij een indringer, per lichaam één keer. Verstop je het lichaam (sleep het weg, sneak + rechtsklik), dan
 * ziet niemand het.
 */
public final class Bodies {
    private Bodies() {}

    /** Geboeide soldaten en gevloerde robots die al gemeld zijn (een lichaam onthoudt het zelf, zie RavenBodyEntity). */
    private static final Set<UUID> REPORTED = new HashSet<>();

    /** Is dit voor een bewaker een lichaam? */
    public static boolean isBody(Entity entity) {
        if (!entity.isAlive()) {
            return false;
        }
        if (entity instanceof RavenBodyEntity) {
            return true;
        }
        if (entity instanceof RavenSoldierEntity soldier) {
            // Geboeid, of iemand heeft hem vast voor een sluipaanval (8 november: wie dat ziet, komt kijken).
            return Handcuffs.isCuffed(soldier) || soldier.isBeingTakenDown();
        }
        return entity instanceof BotEntity bot && bot.isDown();
    }

    /** Al via de radio gemeld? */
    public static boolean reported(Entity entity) {
        return entity instanceof RavenBodyEntity body ? body.reported() : REPORTED.contains(entity.getUUID());
    }

    public static void markReported(Entity entity, boolean reported) {
        if (entity instanceof RavenBodyEntity body) {
            body.setReported(reported);
        } else if (reported) {
            REPORTED.add(entity.getUUID());
        } else {
            REPORTED.remove(entity.getUUID());
        }
    }

    /**
     * Het dichtstbijzijnde nog niet gemelde lichaam dat {@code watcher} ziet: binnen {@code range}, binnen zijn blik
     * ({@code halfAngle} graden naar links en rechts van waar zijn hoofd heen kijkt) en met niets ertussen.
     */
    public static @Nullable Entity visible(LivingEntity watcher, double range, double halfAngle) {
        return visible(watcher, range, halfAngle, Bodies::reported);
    }

    /** Hetzelfde, maar {@code known} zegt welke lichamen de kijker al kent (een robot houdt zijn eigen lijst bij). */
    public static @Nullable Entity visible(LivingEntity watcher, double range, double halfAngle, java.util.function.Predicate<Entity> known) {
        Vec3 eye = watcher.getEyePosition();
        Vec3 facing = Vec3.directionFromRotation(0, watcher.getYHeadRot());
        AABB box = watcher.getBoundingBox().inflate(range);
        List<Entity> candidates = new ArrayList<>(watcher.level().getEntities(watcher, box, Bodies::isBody));
        candidates.sort(java.util.Comparator.comparingDouble(watcher::distanceToSqr));
        for (Entity body : candidates) {
            if (known.test(body) || watcher.distanceTo(body) > range) {
                continue;
            }
            if (RavenSenses.flatAngle(facing, body.position().add(0, 0.3, 0).subtract(eye)) > halfAngle) {
                continue;
            }
            if (!watcher.hasLineOfSight(body)) {
                continue;
            }
            return body;
        }
        return null;
    }

    // ================================================================ Gebeurtenissen

    /** Een Raven-soldaat sterft: zijn buit gaat niet op de grond maar in zijn lichaam, dat blijft liggen. */
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof RavenSoldierEntity soldier) || !(soldier.level() instanceof ServerLevel level)) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            items.add(drop.getItem());
        }
        if (RavenBodyEntity.fall(level, soldier, items) != null) {
            event.setCanceled(true);
            // Het lichaam ligt er al: de valanimatie van de soldaat zelf hoeft niet meer.
            soldier.discard();
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        REPORTED.clear();
    }
}
