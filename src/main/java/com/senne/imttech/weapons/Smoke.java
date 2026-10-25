package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * De rookwolken van de Smoke Grenade (hoofdstuk 35). Jouw keuze: de rook blokkeert het zicht van "alles wat kijkt":
 * robots, turrets en drones (en de Raven-soldaten) zien niemand in of achter de rook. Wat jij ziet, zijn de
 * rookdeeltjes in je spel (zie client/weapons/SmokeView); hier houdt de server bij waar de wolken hangen, zodat
 * machines er niet doorheen kijken (zie {@link #blocks}).
 *
 * Een wolk groeit in 2 seconden tot zijn volle grootte (SMOKE_RADIUS), blijft dan hangen, en trekt in de laatste 3
 * seconden weg.
 */
public final class Smoke {
    private Smoke() {}

    /** Zo lang (ticks) groeit een wolk, en zo lang trekt hij weg aan het eind. */
    public static final int GROW_TICKS = 40;
    public static final int FADE_TICKS = 60;

    /** Eén wolk: waar, en van wanneer tot wanneer (speltijd). */
    public record Cloud(Vec3 center, long start, long end) {
        /** Hoe groot hij nu is (blokken). */
        public double radius(long now) {
            return Smoke.radius(now - start, end - start);
        }
    }

    private static final Map<ResourceKey<Level>, List<Cloud>> CLOUDS = new HashMap<>();

    /** Hoe groot een wolk is die {@code age} ticks oud is en {@code duration} ticks duurt (ook voor je spel). */
    public static double radius(long age, long duration) {
        double max = ImtConfig.Weapons.SMOKE_RADIUS;
        if (age < 0 || age >= duration) {
            return 0;
        }
        double grow = Math.min(1, age / (double) GROW_TICKS);
        double fade = Math.min(1, (duration - age) / (double) FADE_TICKS);
        return max * Math.sqrt(grow) * fade;
    }

    /** Een nieuwe wolk rond {@code center}, die {@code ticks} lang blijft. */
    public static Cloud add(ServerLevel level, Vec3 center, int ticks) {
        long now = level.getGameTime();
        Cloud cloud = new Cloud(center, now, now + ticks);
        CLOUDS.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(cloud);
        return cloud;
    }

    /**
     * Kijkt een lijn van {@code from} naar {@code to} door de rook? Dat is zo als hij in totaal meer dan SMOKE_SEE_THROUGH
     * blokken door wolken gaat: van heel dichtbij zie je elkaar in de rook nog net. Alleen op de server (in je spel altijd
     * false: daar doen de deeltjes het werk).
     */
    public static boolean blocks(Level level, Vec3 from, Vec3 to) {
        List<Cloud> clouds = CLOUDS.get(level.dimension());
        if (clouds == null || clouds.isEmpty() || level.isClientSide) {
            return false;
        }
        long now = level.getGameTime();
        double through = 0;
        for (Cloud cloud : clouds) {
            through += inside(cloud.center(), cloud.radius(now), from, to);
        }
        return through > ImtConfig.Weapons.SMOKE_SEE_THROUGH;
    }

    /** Hoeveel blokken van de lijn {@code from}-{@code to} binnen de bol (midden {@code c}, straal {@code r}) liggen. */
    public static double inside(Vec3 c, double r, Vec3 from, Vec3 to) {
        if (r <= 0) {
            return 0;
        }
        Vec3 d = to.subtract(from);
        Vec3 f = from.subtract(c);
        double a = d.dot(d);
        if (a < 1.0E-9) {
            return 0;
        }
        double b = 2 * f.dot(d);
        double k = f.dot(f) - r * r;
        double disc = b * b - 4 * a * k;
        if (disc <= 0) {
            return 0;
        }
        double root = Math.sqrt(disc);
        double t1 = Math.max(0, (-b - root) / (2 * a));
        double t2 = Math.min(1, (-b + root) / (2 * a));
        return t2 > t1 ? (t2 - t1) * Math.sqrt(a) : 0;
    }

    /** Wolken die op zijn vergeten. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (CLOUDS.isEmpty()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            List<Cloud> clouds = CLOUDS.get(level.dimension());
            if (clouds == null) {
                continue;
            }
            long now = level.getGameTime();
            for (Iterator<Cloud> it = clouds.iterator(); it.hasNext(); ) {
                if (it.next().end() <= now) {
                    it.remove();
                }
            }
            if (clouds.isEmpty()) {
                CLOUDS.remove(level.dimension());
            }
        }
    }

    /** Bij het afsluiten van de wereld: alles vergeten. */
    public static void clear() {
        CLOUDS.clear();
    }
}
