package com.senne.starktech.client.hud;

import static com.senne.starktech.client.hud.HudStyle.CYAN;
import static com.senne.starktech.client.hud.HudStyle.GREEN;
import static com.senne.starktech.client.hud.HudStyle.ORANGE;
import static com.senne.starktech.client.hud.HudStyle.PURPLE;
import static com.senne.starktech.client.hud.HudStyle.RED;
import static com.senne.starktech.client.hud.HudStyle.WHITE;
import static com.senne.starktech.client.hud.HudStyle.argb;
import static com.senne.starktech.client.hud.HudStyle.bar;

import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.client.NavigationScreen;
import com.senne.starktech.client.OreType;
import com.senne.starktech.client.voice.EdithAssistant;
import com.senne.starktech.client.voice.EdithListener;
import com.senne.starktech.client.voice.EdithSpeech;
import com.senne.starktech.client.voice.VoiceFeature;
import com.senne.starktech.client.voice.VoiceInstaller;
import com.senne.starktech.edith.EdithCooldown;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithFeature;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Het paneel linksboven: je batterij en ALLES wat nu actief is, altijd (geen tabbladen).
 *
 * Wat uit staat of niet bezig is, zie je hier niet; staat er niets aan, dan zie je alleen de
 * batterij. Zolang het ronde G-menu open is, is dit paneel even weg.
 */
public final class StatusPanelWidget implements HudWidget {
    private static final int PANEL_X = 10;
    /** Aantal pixels tussen twee regels tekst. */
    private static final int LINE = 10;

    @Override
    public boolean visible(HudContext c) {
        return !c.menuOpen;
    }

    @Override
    public void render(HudContext c) {
        GuiGraphics g = c.g;
        int x = PANEL_X;
        int y = 10;

        // Regel 1: E.D.I.T.H. en de batterij
        Component name = c.powered ? Component.literal("E.D.I.T.H.") : Component.translatable("hud.starktech.offline");
        g.drawString(c.font, name, x, y, argb(255, c.powered ? CYAN : RED), true);
        int barX = x + c.font.width(name) + 5;
        c.panelRight = barX + 70;
        float fraction = EdithData.getEnergyFraction(c.glasses);
        int barColor = fraction < 0.1f ? RED : fraction < 0.3f ? ORANGE : CYAN;
        bar(g, barX, y + 3, 40, 3, fraction, barColor);
        g.drawString(c.font, Math.round(fraction * 100) + "%", barX + 44, y, argb(210, barColor), true);
        // Praat E.D.I.T.H.? Dan een klein bewegend golfje naast de batterij.
        if (EdithSpeech.isSpeaking()) {
            int waveX = barX + 70;
            for (int i = 0; i < 5; i++) {
                int wave = 2 + (int) (3 * (1 + Math.sin(c.now / 70.0 + i * 1.3)));
                g.fill(waveX + i * 3, y + 4 - wave / 2, waveX + i * 3 + 2, y + 4 + wave / 2 + 1, argb(230, CYAN));
            }
            c.claimPanelWidth(waveX + 15);
        }
        y += 13;
        if (!c.powered) {
            return;
        }

        // Regel 2: de functies die aan staan, als labeltjes (bv. NZ, DREIGING, DOEL, ESCORTE)
        int tagX = x;
        for (EdithFeature feature : EdithFeature.values()) {
            if (EdithData.isEnabled(c.glasses, feature)) {
                Component label = Component.translatable(feature.translationKey() + ".short");
                int tw = c.font.width(label);
                g.fill(tagX - 1, y - 1, tagX + tw + 2, y + 9, argb(70, CYAN));
                g.drawString(c.font, label, tagX + 1, y, argb(255, WHITE), false);
                tagX += tw + 6;
                c.claimPanelWidth(tagX);
            }
        }
        if (tagX != x) {
            y += LINE + 3;
        }

        // Daaronder: alleen wat er nu gebeurt.
        // Je praat tegen E.D.I.T.H., of ze denkt na over wat je zei
        if (EdithAssistant.isBusy()) {
            y = renderListening(c, x, y);
        }
        // Vijanden in de buurt (dreigingsscan)
        if (!EdithClientState.threats.isEmpty()) {
            int alpha = (int) (190 + 60 * Math.sin(c.now / 150.0));
            panelText(c, Component.translatable("hud.starktech.threats", EdithClientState.threats.size()), x, y, argb(alpha, RED));
            y += LINE;
        }
        // Drones in de lucht
        if (EdithClientState.swarmDrones > 0 || EdithClientState.guardDrones > 0) {
            panelText(c, Component.translatable("hud.starktech.drones", EdithClientState.swarmDrones, EdithClientState.guardDrones),
                    x, y, argb(230, CYAN));
            y += LINE;
        }
        // Vlieg je met drones? Dan de besturing even in beeld.
        if (EdithClientState.flying) {
            panelText(c, Component.translatable("hud.starktech.flying"), x, y, argb(240, CYAN));
            y += LINE;
            panelText(c, Component.translatable("hud.starktech.flying_help"), x + 6, y, argb(190, WHITE));
            y += LINE;
        }
        // Wie je hackt
        if (EdithClientState.hackedCount > 0) {
            panelText(c, Component.translatable("hud.starktech.hacked", EdithClientState.hackedCount),
                    x, y, argb(235, RED));
            y += LINE;
        }
        // Spionagedrone: wie hij volgt, hoe lang nog en hoeveel leven het doelwit heeft
        if (EdithClientState.spyActive) {
            panelText(c, Component.translatable("hud.starktech.spy", EdithClientState.spyTarget,
                    EdithClientState.spyTimeLeft()), x, y, argb(235, PURPLE));
            y += LINE;
            if (EdithClientState.spyPos != null && EdithClientState.spyDimension == c.player.level().dimension()) {
                int distance = (int) Math.round(Math.sqrt(c.player.distanceToSqr(EdithClientState.spyPos)));
                panelText(c, Component.translatable("hud.starktech.spy_at", distance,
                        Math.max(1, Math.round(EdithClientState.spyHealth / 2))), x + 6, y, argb(200, PURPLE));
            } else {
                panelText(c, Component.translatable("hud.starktech.spy_other_world",
                        NavigationScreen.dimensionName(EdithClientState.spyDimension)), x + 6, y, argb(200, PURPLE));
            }
            y += LINE;
        }
        // Gemarkeerde doelwitten
        if (!EdithClientState.markedIds.isEmpty()) {
            panelText(c, Component.translatable("hud.starktech.marked", EdithClientState.markedIds.size()), x, y, argb(240, ORANGE));
            y += LINE;
        }
        // Wachttijden die nog lopen
        y = cooldownLine(c, x, y, "hud.starktech.swarm", EdithClientState.cooldown(EdithCooldown.SWARM));
        y = cooldownLine(c, x, y, "hud.starktech.strike", EdithClientState.cooldown(EdithCooldown.STRIKE));
        y = cooldownLine(c, x, y, "hud.starktech.rockets", EdithClientState.cooldown(EdithCooldown.ROCKETS));
        y = cooldownLine(c, x, y, "hud.starktech.satellite", EdithClientState.cooldown(EdithCooldown.SATELLITE));
        y = cooldownLine(c, x, y, "hud.starktech.ore_scan", EdithClientState.cooldown(EdithCooldown.ORE_SCAN));
        // Bestemming (navigatie)
        y = renderNavigationLine(c, x, y);
        // Resultaat van de ertsen-scan, zolang de markeringen er nog zijn
        if (EdithClientState.hasOreScan()) {
            y = renderOreResults(c, x, y + 2);
        }
        // Pings van jezelf en anderen
        int shown = 0;
        for (EdithClientState.Ping ping : EdithClientState.pings) {
            if (shown++ >= 3) {
                break;
            }
            int distance = (int) Math.round(Math.sqrt(c.player.distanceToSqr(ping.pos())));
            panelText(c, Component.translatable("hud.starktech.ping_line", ping.sender(), distance), x, y, argb(220, GREEN));
            y += LINE;
        }
        // Stem of spraakbesturing aan het downloaden
        for (VoiceFeature feature : VoiceFeature.values()) {
            if (VoiceInstaller.state(feature) == VoiceInstaller.State.INSTALLING) {
                int percent = VoiceInstaller.percent(feature);
                panelText(c, feature.downloadingLine(percent), x, y, argb(220, CYAN));
                bar(g, x, y + 9, 70, 2, percent / 100.0f, CYAN);
                y += LINE + 3;
            }
        }
    }

    /** Tekent een regel in het paneel en onthoudt hoe breed het paneel daardoor wordt. */
    private void panelText(HudContext c, Component text, int x, int y, int color) {
        c.g.drawString(c.font, text, x, y, color, true);
        c.claimPanelWidth(x + c.font.width(text));
    }

    /** "● LUISTEREN" (met een balkje voor hoe luid je praat), of "VERWERKEN..." als het oor uitschrijft. */
    private int renderListening(HudContext c, int x, int y) {
        GuiGraphics g = c.g;
        EdithAssistant.Stage stage = EdithAssistant.stage();
        boolean listening = stage == EdithAssistant.Stage.LISTENING;
        if (listening && (c.now / 400) % 2 == 0) {
            g.fill(x, y + 2, x + 5, y + 7, argb(255, RED));
        } else if (!listening) {
            // Drie puntjes die om de beurt oplichten: ze is bezig.
            for (int i = 0; i < 3; i++) {
                boolean lit = (c.now / 200) % 3 == i;
                g.fill(x + i * 2, y + 4, x + i * 2 + 1, y + 5, argb(lit ? 255 : 90, CYAN));
            }
        }
        String key = switch (stage) {
            case LISTENING -> "hud.starktech.voice.listening";
            case STARTING -> "hud.starktech.voice.starting";
            default -> "hud.starktech.voice.processing";
        };
        Component label = Component.translatable(key);
        panelText(c, label, x + 8, y, argb(240, listening ? RED : CYAN));
        if (listening) {
            bar(g, x + 12 + c.font.width(label), y + 3, 30, 3, EdithListener.level(), GREEN);
        }
        return y + LINE;
    }

    /** Hoeveel ertsen van elke soort de scan vond, met een balkje dat aftelt tot de markeringen weg zijn. */
    private int renderOreResults(HudContext c, int x, int y) {
        Map<OreType, Integer> counts = new EnumMap<>(OreType.class);
        for (EdithClientState.OreMarker marker : EdithClientState.oreMarkers) {
            counts.merge(marker.type(), 1, Integer::sum);
        }
        int shown = 0;
        for (Map.Entry<OreType, Integer> entry : counts.entrySet()) {
            if (shown++ >= 6) {
                break;
            }
            c.g.fill(x, y + 2, x + 4, y + 6, argb(255, entry.getKey().color()));
            panelText(c, Component.translatable("hud.starktech.ore_count", entry.getKey().displayName(), entry.getValue()),
                    x + 7, y, argb(220, WHITE));
            y += LINE;
        }
        float left = 1.0f - (float) (c.now - EdithClientState.oreScanStartMillis)
                / (EdithClientState.oreScanEndMillis - EdithClientState.oreScanStartMillis);
        bar(c.g, x, y + 1, 70, 2, left, CYAN);
        return y + 6;
    }

    /** Regel met een wachttijd die nog loopt (oranje). Is hij klaar, dan tekenen we niets. */
    private int cooldownLine(HudContext c, int x, int y, String key, int cooldownTicks) {
        int seconds = EdithClientState.secondsLeft(cooldownTicks);
        if (seconds <= 0) {
            return y;
        }
        panelText(c, Component.translatable(key, seconds + "s"), x, y, argb(210, ORANGE));
        return y + LINE;
    }

    /** Regel in het paneel: waar navigeer je naartoe, hoe ver, hoger of lager? */
    private int renderNavigationLine(HudContext c, int x, int y) {
        NavTarget target = NavTarget.of(c.player);
        if (target == null) {
            return y;
        }
        Component line;
        if (target.sameDimension()) {
            int distance = (int) Math.round(Math.sqrt(c.player.distanceToSqr(target.pos())));
            int height = (int) Math.round(target.pos().y - c.player.getY());
            String up = height > 2 ? " +" + height : height < -2 ? " " + height : "";
            line = Component.translatable("hud.starktech.nav", target.name(), distance + "m" + up);
        } else {
            line = Component.translatable("hud.starktech.nav_other_dimension", target.name(), target.dimensionName());
        }
        c.g.fill(x, y + 1, x + 4, y + 7, argb(255, target.color()));
        panelText(c, line, x + 7, y, argb(230, WHITE));
        return y + 11;
    }
}
