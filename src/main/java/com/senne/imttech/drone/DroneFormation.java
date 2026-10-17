package com.senne.imttech.drone;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * In welke vorm je escortedrones om je heen vliegen. Je wisselt met G > Drones > Formation;
 * de keuze bewaart de bril.
 *
 * - ORBIT: rondcirkelen, zoals altijd. Zien ze een vijand, dan vliegen ze erheen.
 * - SHIELD: een strakke, snel draaiende ring op borsthoogte. Ze blijven bij je en schieten
 *   vanaf daar; wat van buiten op je af komt, raakt eerst een drone.
 * - ARROW: een V-formatie schuin boven en achter je, die meedraait met je kijkrichting, als
 *   een eskader. Ze blijven in formatie en schieten van daaruit.
 * - CIRCLE: een wijde, trage ring om je heen, als een patrouille. Zien ze een vijand, dan vliegen
 *   ze erheen.
 */
public enum DroneFormation {
    ORBIT("orbit"),
    SHIELD("shield"),
    ARROW("arrow"),
    CIRCLE("circle");

    private final String id;

    DroneFormation(String id) {
        this.id = id;
    }

    public String translationKey() {
        return "imt.imttech.formation." + id;
    }

    public DroneFormation next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static DroneFormation byId(int id) {
        DroneFormation[] all = values();
        return all[Math.floorMod(id, all.length)];
    }

    /** Blijven de drones in deze vorm staan als ze schieten (in plaats van op het doelwit af te gaan)? */
    public boolean holdsPosition() {
        return this == SHIELD || this == ARROW;
    }

    /** Hoe snel ze erheen vliegen (in de V moeten ze je bijhouden, ook als je rent). */
    public double speed() {
        return this == ARROW ? 1.4 : this == SHIELD ? 1.1 : 0.8;
    }

    /**
     * Waar drone nummer {@code slot} (van {@code count}) nu hoort te hangen.
     *
     * @param tick de tijd, voor de vormen die ronddraaien
     */
    public Vec3 spot(Player owner, int slot, int count, long tick) {
        int n = Math.max(count, slot + 1);
        double share = slot * (Math.PI * 2 / n);
        return switch (this) {
            case ORBIT -> around(owner, tick * 0.05 + slot * (Math.PI * 2 / 5), 1.8, owner.getBbHeight() + 0.5);
            case SHIELD -> around(owner, tick * 0.12 + share, 1.3, 1.0);
            case CIRCLE -> around(owner, tick * 0.015 + share, 5.0, owner.getBbHeight() + 1.0);
            case ARROW -> {
                // Twee aan twee achter elkaar, links en rechts om en om: een V met de punt naar voren.
                int row = slot / 2 + 1;
                int side = slot % 2 == 0 ? -1 : 1;
                float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
                Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
                Vec3 right = new Vec3(-forward.z, 0, forward.x);
                yield owner.position()
                        .add(forward.scale(-0.8 - row * 1.1))
                        .add(right.scale(side * row * 1.1))
                        .add(0, owner.getBbHeight() + 0.8 + row * 0.25, 0);
            }
        };
    }

    private static Vec3 around(Player owner, double angle, double radius, double height) {
        return new Vec3(owner.getX() + Math.cos(angle) * radius, owner.getY() + height,
                owner.getZ() + Math.sin(angle) * radius);
    }
}
