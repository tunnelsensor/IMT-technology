package com.senne.imttech.client.raven;

import com.senne.imttech.client.CameraShake;
import com.senne.imttech.client.ImtMenuScreen;
import com.senne.imttech.client.WorldProjection;
import com.senne.imttech.client.hud.DetectionWidget;
import com.senne.imttech.client.hud.HudContext;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.raven.RavenSoldierEntity;
import com.senne.imttech.raven.Takedown;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Wat je van de bewakers ziet, <b>ook zonder bril</b> (8 november, jouw opmerking: "je ziet dit enkel met je bril, ik wil
 * dat je dit ook zonder bril ziet en dat de iconen iets kleiner zijn").
 * <ul>
 *   <li>het oogje dat volloopt als een bewaker je in de gaten krijgt, en het "?" of "!" boven zijn hoofd
 *       (DetectionWidget; vroeger alleen op de HUD van je bril);</li>
 *   <li>"Takedown" onder je vizier als je met een zwaard achter een bewaker staat die je niet ziet (Takedown);</li>
 *   <li>tijdens de sluipaanval zelf een balkje op die plek dat volloopt, en het beeld schudt een beetje mee met zijn
 *       worsteling (CameraShake), met een tik als hij neergaat.</li>
 * </ul>
 * Een eigen laag boven het vizier, los van de bril: hij tekent ook als je bril uit staat, leeg is of door een EMP ruist.
 */
public final class GuardLayer implements LayeredDraw.Layer {
    /** Het balkje van de sluipaanval: zo breed (pixels), en zo ver onder het midden (onder het laadbalkje van de aanval). */
    private static final int BAR_WIDTH = 41;
    private static final int BELOW_CROSSHAIR = 18;

    private final WorldProjection projection = new WorldProjection();
    private final DetectionWidget detection = new DetectionWidget();
    /** De soldaat waarvoor het beeld al begon te schudden, en waarvoor de tik aan het eind al kwam (-1 = geen). */
    private int shookFor = -1;
    private int bumpedFor = -1;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        projection.begin(w, h, partialTick);
        // Zonder bril is er geen bril: de widgets die dit tekenen kijken er niet naar.
        HudContext c = new HudContext(mc, g, mc.font, player, ItemStack.EMPTY, w, h, Util.getMillis(), true,
                mc.screen instanceof ImtMenuScreen, projection);
        if (detection.visible(c)) {
            detection.render(c);
        }
        RavenSoldierEntity held = heldGuard(mc, player);
        if (held != null) {
            takedownBar(mc, g, held, w, h, partialTick);
        } else {
            shookFor = -1;
            bumpedFor = -1;
            takedownHint(mc, g, player, w, h);
        }
    }

    /** De soldaat die jij nu vasthoudt voor een sluipaanval, of null. */
    private static @Nullable RavenSoldierEntity heldGuard(Minecraft mc, Player player) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof RavenSoldierEntity soldier && soldier.isBeingTakenDown() && soldier.takedownBy() == player.getId()) {
                return soldier;
            }
        }
        return null;
    }

    /** "Takedown [Left Button]" onder je vizier, zolang een sluipaanval kan. */
    private static void takedownHint(Minecraft mc, GuiGraphics g, Player player, int w, int h) {
        if (player.isSpectator() || mc.screen != null || !(mc.crosshairPickEntity instanceof RavenSoldierEntity guard)
                || !Takedown.possible(player, guard, guard.alertState())) {
            return;
        }
        Component text = Component.translatable("raven.imttech.takedown.hint", mc.options.keyAttack.getTranslatedKeyMessage());
        int width = mc.font.width(text);
        // Onder het laadbalkje van de aanval dat Minecraft onder je vizier tekent (dat staat tot ongeveer 13 pixels lager).
        g.drawString(mc.font, text, w / 2 - width / 2, h / 2 + BELOW_CROSSHAIR, HudStyle.argb(255, HudStyle.RED), true);
    }

    /** Tijdens de sluipaanval: het balkje dat volloopt, en het schudden van het beeld. */
    private void takedownBar(Minecraft mc, GuiGraphics g, RavenSoldierEntity held, int w, int h, float partialTick) {
        float progress = held.takedownProgress(held.tickCount + partialTick);
        if (shookFor != held.getId()) {
            shookFor = held.getId();
            // Zijn worsteling: zacht schudden, dat wegzakt als hij slap wordt (iets meer dan de helft van de tijd).
            CameraShake.shake(0.7f, Math.round(held.takedownLength() * 50 * 0.6f));
        }
        if (progress >= 0.96f && bumpedFor != held.getId()) {
            bumpedFor = held.getId();
            CameraShake.shake(1.2f, 220); // hij gaat neer
        }
        if (mc.screen != null) {
            return;
        }
        int x = w / 2 - BAR_WIDTH / 2;
        int y = h / 2 + BELOW_CROSSHAIR + 2;
        g.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + 3, HudStyle.argb(170, 0x101418));
        g.fill(x, y, x + Math.round(BAR_WIDTH * progress), y + 2, HudStyle.argb(235, HudStyle.RED));
    }
}
