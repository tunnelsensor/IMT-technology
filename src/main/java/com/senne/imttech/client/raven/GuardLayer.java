package com.senne.imttech.client.raven;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Wat je van de bewakers ziet, <b>ook zonder bril</b> (8 november, jouw opmerking: "je ziet dit enkel met je bril, ik wil
 * dat je dit ook zonder bril ziet en dat de iconen iets kleiner zijn").
 * <ul>
 *   <li>het oogje dat volloopt als een bewaker je in de gaten krijgt, en het "?" of "!" boven zijn hoofd
 *       (DetectionWidget; vroeger alleen op de HUD van je bril);</li>
 *   <li>"Takedown" onder je vizier als je met een zwaard achter een bewaker staat die je niet ziet (Takedown).</li>
 * </ul>
 * Een eigen laag boven het vizier, los van de bril: hij tekent ook als je bril uit staat, leeg is of door een EMP ruist.
 */
public final class GuardLayer implements LayeredDraw.Layer {
    private final WorldProjection projection = new WorldProjection();
    private final DetectionWidget detection = new DetectionWidget();

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        projection.begin(w, h, deltaTracker.getGameTimeDeltaPartialTick(false));
        // Zonder bril is er geen bril: de widgets die dit tekenen kijken er niet naar.
        HudContext c = new HudContext(mc, g, mc.font, player, ItemStack.EMPTY, w, h, Util.getMillis(), true,
                mc.screen instanceof ImtMenuScreen, projection);
        if (detection.visible(c)) {
            detection.render(c);
        }
        takedownHint(mc, g, player, w, h);
    }

    /** "Takedown [Left Button]" net onder je vizier, zolang een sluipaanval kan. */
    private static void takedownHint(Minecraft mc, GuiGraphics g, Player player, int w, int h) {
        if (player.isSpectator() || mc.screen != null || !(mc.crosshairPickEntity instanceof RavenSoldierEntity guard)
                || !Takedown.possible(player, guard, guard.alertState())) {
            return;
        }
        Component text = Component.translatable("raven.imttech.takedown.hint", mc.options.keyAttack.getTranslatedKeyMessage());
        int width = mc.font.width(text);
        // Onder het laadbalkje van de aanval dat Minecraft onder je vizier tekent (dat staat tot ongeveer 13 pixels lager).
        g.drawString(mc.font, text, w / 2 - width / 2, h / 2 + 18, HudStyle.argb(255, HudStyle.RED), true);
    }
}
