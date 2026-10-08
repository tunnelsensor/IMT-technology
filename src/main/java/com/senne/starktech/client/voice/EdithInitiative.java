package com.senne.starktech.client.voice;

import com.senne.starktech.client.EdithClientConfig;
import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.client.hud.NavTarget;
import com.senne.starktech.edith.EdithData;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * E.D.I.T.H. zegt uit zichzelf iets (G > Systemen > AI Brain > "Speaks on her own"; alleen met
 * een brein, niet met het woordenboek).
 *
 * Ze let op een paar momenten, telkens op het moment dat het verandert (niet de hele tijd):
 * - je leven zakt onder de drie hartjes;
 * - de batterij van je bril zakt onder de 15%;
 * - er komen twee of meer vijanden in de buurt (binnen 16 blokken);
 * - de nacht valt;
 * - je bent aangekomen op je bestemming.
 *
 * Dan vraagt ze het brein om één korte, nuttige opmerking (het brein mag ook besluiten niets
 * te zeggen). Hooguit één keer per minuut, en niet vlak nadat jij iets zei.
 */
final class EdithInitiative {
    private EdithInitiative() {}

    private static final long PAUSE_MILLIS = 60_000;
    private static final float LOW_HEALTH = 6.0f;
    private static final float LOW_BATTERY = 0.15f;
    private static final double ENEMY_RANGE = 16;

    private static boolean lowHealth;
    private static boolean lowBattery;
    private static boolean enemies;
    private static boolean night;
    private static boolean arrived;
    private static boolean primed;
    private static long lastRemark;

    static boolean enabled() {
        return EdithClientConfig.SPEC.isLoaded() && EdithClientConfig.PROACTIVE.get();
    }

    static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || player.tickCount % 20 != 0) {
            return;
        }
        if (!EdithClientState.wearing || !EdithClientState.authorized) {
            primed = false;
            return;
        }
        boolean nowLowHealth = player.getHealth() <= LOW_HEALTH;
        ItemStack glasses = EdithData.getWornGlasses(player);
        boolean nowLowBattery = !glasses.isEmpty() && EdithData.getEnergyFraction(glasses) <= LOW_BATTERY;
        long hostile = player.level().getEntities(player, player.getBoundingBox().inflate(ENEMY_RANGE),
                entity -> entity instanceof Enemy && entity.isAlive()).size();
        boolean nowEnemies = hostile >= 2;
        long time = player.level().getDayTime() % 24000;
        boolean nowNight = player.level().dimension() == Level.OVERWORLD && time >= 12_600 && time < 23_000;
        NavTarget target = NavTarget.of(player);
        boolean nowArrived = target != null && target.sameDimension() && target.pos().distanceTo(player.position()) < 6;

        String event = null;
        if (primed) {
            if (nowLowHealth && !lowHealth) {
                event = "The player's health just dropped below three hearts.";
            } else if (nowLowBattery && !lowBattery) {
                event = "The glasses battery just dropped below 15%.";
            } else if (nowEnemies && !enemies) {
                event = hostile + " hostile creatures came within 16 blocks.";
            } else if (nowArrived && !arrived) {
                event = "The player just arrived at their navigation destination.";
            } else if (nowNight && !night) {
                event = "Night is falling.";
            }
        }
        lowHealth = nowLowHealth;
        lowBattery = nowLowBattery;
        enemies = nowEnemies;
        night = nowNight;
        arrived = nowArrived;
        primed = true; // de eerste keer alleen kijken: geen opmerking over wat er al was bij het opzetten

        long now = System.currentTimeMillis();
        List<EdithMemory.Turn> turns = EdithMemory.recentTurns();
        boolean justTalked = !turns.isEmpty() && now - turns.getLast().millis() < 20_000;
        if (event != null && enabled() && !justTalked && now - lastRemark > PAUSE_MILLIS
                && EdithBrain.ready(EdithBrain.mode()) && EdithBrain.hasDatacenter() && !EdithListener.isListening()) {
            lastRemark = now;
            EdithBrain.remark(event);
        }
    }
}
