package com.senne.imttech.client.robot;

import com.senne.imttech.client.ImtClientConfig;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.bot.BotEntity;
import java.util.ArrayList;
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
 * Het zachte zoemen van een guard robot die op zijn Robot Dock laadt (hoofdstuk 21). Net als bij de
 * rayshields (jouw wens, 7 oktober: "het mag altijd geluid maken maar gewoon heel stil/subtiel"): een lus,
 * heel stil, net hoorbaar als je erbij staat. Hoe hard, stel je in in config/imttech-client.toml
 * (robotHumVolume: 1 = zo, 0 = uit). Is hij vol of stapt hij uit zijn dock, dan sterft het weg.
 *
 * Alleen in jouw spel: de server stuurt per robot mee of hij nu laadt (zie BotEntity.isCharging).
 */
public final class RobotChargeHum {
    private RobotChargeHum() {}

    /** Zo vaak (ticks) kijken we of er robots in de buurt laden. */
    private static final int SCAN_INTERVAL = 20;
    /** Zover (blokken) zoeken we. */
    private static final double RANGE = 16;
    /** Hooguit zoveel robots zoemen tegelijk. */
    private static final int MAX_HUMS = 4;
    /** Hoe hard: heel stil. */
    private static final float VOLUME = 0.08f;

    private static final List<Hum> HUMS = new ArrayList<>();

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        HUMS.removeIf(hum -> hum.isStopped() || !mc.getSoundManager().isActive(hum) && hum.started);
        if (player == null || level == null || level.getGameTime() % SCAN_INTERVAL != 0) {
            return;
        }
        if (ImtClientConfig.robotHumVolume() <= 0) {
            clear();
            return;
        }
        for (BotEntity robot : level.getEntitiesOfClass(BotEntity.class, new AABB(player.blockPosition()).inflate(RANGE),
                BotEntity::isCharging)) {
            if (HUMS.size() >= MAX_HUMS) {
                return;
            }
            if (HUMS.stream().noneMatch(hum -> hum.robot == robot)) {
                Hum hum = new Hum(robot);
                HUMS.add(hum);
                mc.getSoundManager().play(hum);
            }
        }
    }

    /** Uitloggen of een andere wereld: alles stil. */
    public static void clear() {
        for (Hum hum : HUMS) {
            Minecraft.getInstance().getSoundManager().stop(hum);
        }
        HUMS.clear();
    }

    /** Het zoemen van één robot: het loopt met hem mee, en sterft weg als hij niet meer laadt. */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final BotEntity robot;
        private boolean started;

        Hum(BotEntity robot) {
            super(ModSounds.ROBOT_CHARGE_HUM.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.robot = robot;
            this.looping = true;
            this.delay = 0;
            this.volume = 0f;
            this.x = robot.getX();
            this.y = robot.getY() + 0.5;
            this.z = robot.getZ();
        }

        @Override
        public void tick() {
            started = true;
            x = robot.getX();
            y = robot.getY() + 0.5;
            z = robot.getZ();
            if (robot.isRemoved() || !robot.isCharging()) {
                volume *= 0.6f;
                if (volume < 0.004f) {
                    stop();
                }
                return;
            }
            float target = VOLUME * (float) ImtClientConfig.robotHumVolume();
            volume += (target - volume) * 0.1f;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
