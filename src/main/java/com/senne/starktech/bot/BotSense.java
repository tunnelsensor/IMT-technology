package com.senne.starktech.bot;

import com.senne.starktech.robot.GuardRobotEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Wat een robot ziet (hoofdstuk 24): de zintuigen achter de modules Scan en Distance.
 * <ul>
 *   <li><b>Scan</b>: de spelers binnen het bereik en de kijkhoek van zijn hoofd (zie RobotParts.scanRange en
 *       scanAngle; een Trainer-hoofd kijkt rondom), met niets ertussen: niet door muren. Spelers in creative of
 *       die alleen toekijken (spectator) ziet hij niet, net als monsters en torentjes.</li>
 *   <li><b>Distance</b>: hoeveel blokken het is tot wat recht voor hem staat (een blok, een speler, een dier),
 *       op heuphoogte. Een muur in het blok vlak voor hem is 1; niets binnen {@link #MAX_DISTANCE} blokken is
 *       {@link #NOTHING}.</li>
 * </ul>
 */
public final class BotSense {
    private BotSense() {}

    /** Zo ver kijkt de afstandssensor. */
    public static final int MAX_DISTANCE = 16;
    /** Wat de afstandssensor zegt als er niets is. */
    public static final double NOTHING = 99;

    /** De spelers die deze robot nu ziet, de dichtste eerst. */
    public static List<Player> seen(GuardRobotEntity robot) {
        double range = robot.parts().scanRange();
        double half = robot.parts().scanAngle() / 2.0;
        Vec3 eye = robot.getEyePosition();
        Vec3 look = Vec3.directionFromRotation(0, robot.getYHeadRot());
        List<Player> seen = new ArrayList<>();
        for (Player player : robot.level().players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }
            Vec3 to = player.getBoundingBox().getCenter().subtract(eye);
            if (to.lengthSqr() > range * range) {
                continue;
            }
            if (half < 180 && angle(look, to) > half) {
                continue;
            }
            if (!robot.hasLineOfSight(player)) {
                continue;
            }
            seen.add(player);
        }
        seen.sort(Comparator.comparingDouble(player -> player.distanceToSqr(robot)));
        return seen;
    }

    /** De hoek (graden) tussen twee richtingen, plat gemeten (omhoog of omlaag kijken telt niet). */
    private static double angle(Vec3 a, Vec3 b) {
        double la = Math.sqrt(a.x * a.x + a.z * a.z);
        double lb = Math.sqrt(b.x * b.x + b.z * b.z);
        if (la < 1.0e-6 || lb < 1.0e-6) {
            return 0;
        }
        double cos = (a.x * b.x + a.z * b.z) / (la * lb);
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }

    /** Hoeveel blokken tot wat recht voor hem staat (1 = het blok vlak voor hem), of {@link #NOTHING}. */
    public static double distance(GuardRobotEntity robot) {
        Vec3 from = robot.position().add(0, 0.9, 0);
        Vec3 way = Vec3.directionFromRotation(0, robot.getYRot());
        Vec3 to = from.add(way.scale(MAX_DISTANCE));
        double nearest = Double.MAX_VALUE;
        HitResult block = robot.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, robot));
        if (block.getType() != HitResult.Type.MISS) {
            nearest = block.getLocation().distanceTo(from);
        }
        AABB path = new AABB(from, to).inflate(0.5);
        for (Entity entity : robot.level().getEntities(robot, path, entity -> entity instanceof LivingEntity && entity.isAlive()
                && !(entity instanceof Player player && player.isSpectator()))) {
            var hit = entity.getBoundingBox().inflate(0.1).clip(from, to);
            if (hit.isPresent()) {
                nearest = Math.min(nearest, hit.get().distanceTo(from));
            }
        }
        if (nearest == Double.MAX_VALUE) {
            return NOTHING;
        }
        // Van het midden van zijn blok tot de voorkant van wat er staat: een muur vlak voor hem is 0,5 ver, en dat is 1 blok.
        return Math.max(1, Math.floor(nearest + 0.5));
    }
}
