package com.senne.imttech.client.robot;

import com.senne.imttech.client.ImtClientConfig;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.robot.GuardDroneEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Het zachte "brrr" van de rotors van een security drone (hoofdstuk 25; erbij gemaakt op 19 oktober, want tot dan
 * vloog hij zonder geluid). Net als het laadzoemen van een robot (jouw wens, 7 oktober: "het mag altijd geluid maken
 * maar gewoon heel stil/subtiel"): een lus, heel stil. Gaat hij aan, dan spinnen de rotors op (het geluid begint laag
 * en wordt hoger); vliegt of stijgt hij, dan klinkt hij iets hoger en voller dan als hij stilhangt; landt hij of valt
 * hij, dan zakken ze weg. Hoe hard, stel je in in config/imttech-client.toml (droneRotorVolume: 1 = zo, 0 = uit).
 *
 * Alleen in jouw spel. Wanneer de rotors draaien, zegt de drone zelf (GuardDroneEntity.rotorsSpinning): dezelfde
 * regel als het model, dus je hoort ze precies als je ze ziet draaien.
 */
public final class DroneRotorSound {
    private DroneRotorSound() {}

    /** Zo vaak (ticks) kijken we of er drones in de buurt vliegen. */
    private static final int SCAN_INTERVAL = 10;
    /** Zover (blokken) zoeken we (verder dan 16 blokken hoor je zo'n stil geluid toch niet). */
    private static final double RANGE = 20;
    /** Hooguit zoveel drones zoemen tegelijk (de dichtste). */
    private static final int MAX_SOUNDS = 4;
    /** Hoe hard: heel stil (ongeveer zo stil als het laadzoemen van een robot). */
    private static final float VOLUME = 0.12f;
    /** Zo laag begint het: de rotors moeten nog opspinnen. */
    private static final float SPIN_UP_PITCH = 0.6f;
    /** Zo snel (blokken per tick) vliegt een drone ongeveer: 3 blokken per seconde (droneSpeed). */
    private static final float FULL_SPEED = 0.15f;
    /**
     * Zo lang (ticks) mogen de rotors even stilstaan voor het geluid wegzakt. Een drone die uit staat en naar zijn dock
     * vliegt, houdt in het midden van elk blok heel even stil; zonder deze marge hapert het geluid dan.
     */
    private static final int GRACE_TICKS = 10;

    private static final List<Rotor> ROTORS = new ArrayList<>();

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        ROTORS.removeIf(rotor -> rotor.isStopped() || !mc.getSoundManager().isActive(rotor) && rotor.started);
        if (player == null || level == null || level.getGameTime() % SCAN_INTERVAL != 0) {
            return;
        }
        if (ImtClientConfig.droneRotorVolume() <= 0) {
            clear();
            return;
        }
        List<GuardDroneEntity> drones = level.getEntitiesOfClass(GuardDroneEntity.class,
                new AABB(player.blockPosition()).inflate(RANGE), GuardDroneEntity::rotorsSpinning);
        drones.sort(Comparator.comparingDouble(player::distanceToSqr));
        for (GuardDroneEntity drone : drones) {
            if (ROTORS.size() >= MAX_SOUNDS) {
                return;
            }
            if (ROTORS.stream().noneMatch(rotor -> rotor.drone == drone)) {
                Rotor rotor = new Rotor(drone);
                ROTORS.add(rotor);
                mc.getSoundManager().play(rotor);
            }
        }
    }

    /** Uitloggen of een andere wereld: alles stil. */
    public static void clear() {
        for (Rotor rotor : ROTORS) {
            Minecraft.getInstance().getSoundManager().stop(rotor);
        }
        ROTORS.clear();
    }

    /** De rotors van één drone: het geluid vliegt met hem mee, en zakt weg als ze stoppen. */
    private static final class Rotor extends AbstractTickableSoundInstance {
        private final GuardDroneEntity drone;
        private boolean started;
        /** Hoeveel ticks zijn rotors al stilstaan. */
        private int idle;
        /** Waar hij de vorige tick was, en hoe snel hij vliegt (afgevlakt, blokken per tick). */
        private double lastX;
        private double lastY;
        private double lastZ;
        private float speed;

        Rotor(GuardDroneEntity drone) {
            super(ModSounds.DRONE_ROTOR.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.drone = drone;
            this.looping = true;
            this.delay = 0;
            this.volume = 0f;
            this.pitch = SPIN_UP_PITCH;
            this.x = this.lastX = drone.getX();
            this.y = drone.getY() + 0.3;
            this.lastY = drone.getY();
            this.z = this.lastZ = drone.getZ();
        }

        @Override
        public void tick() {
            started = true;
            double climb = drone.getY() - lastY;
            double moved = Math.sqrt(Math.pow(drone.getX() - lastX, 2) + climb * climb + Math.pow(drone.getZ() - lastZ, 2));
            lastX = drone.getX();
            lastY = drone.getY();
            lastZ = drone.getZ();
            x = drone.getX();
            y = drone.getY() + 0.3;
            z = drone.getZ();
            speed += ((float) moved - speed) * 0.2f;
            idle = !drone.isRemoved() && drone.rotorsSpinning() ? 0 : idle + 1;
            if (drone.isRemoved() || idle > GRACE_TICKS) {
                // De rotors zakken weg: zachter en lager, in ongeveer een seconde.
                volume *= 0.85f;
                pitch = Math.max(0.5f, pitch - 0.02f);
                if (volume < 0.004f) {
                    stop();
                }
                return;
            }
            // Vliegt hij, dan draaien ze harder (hoger en iets voller); stijgen kost nog meer, dalen minder.
            float pace = Math.min(speed / FULL_SPEED, 1f);
            float targetVolume = VOLUME * (float) ImtClientConfig.droneRotorVolume() * (1f + 0.25f * pace);
            float targetPitch = 0.92f + 0.08f * pace + (climb > 0.01 ? 0.05f : climb < -0.01 ? -0.03f : 0f);
            volume += (targetVolume - volume) * 0.08f;
            pitch += (targetPitch - pitch) * 0.06f;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
