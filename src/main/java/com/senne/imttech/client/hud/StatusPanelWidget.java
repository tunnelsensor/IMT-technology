package com.senne.imttech.client.hud;

import static com.senne.imttech.client.hud.HudStyle.CYAN;
import static com.senne.imttech.client.hud.HudStyle.GREEN;
import static com.senne.imttech.client.hud.HudStyle.ORANGE;
import static com.senne.imttech.client.hud.HudStyle.PURPLE;
import static com.senne.imttech.client.hud.HudStyle.RED;
import static com.senne.imttech.client.hud.HudStyle.WHITE;
import static com.senne.imttech.client.hud.HudStyle.argb;
import static com.senne.imttech.client.hud.HudStyle.bar;

import com.senne.imttech.client.ImtClientState;
import com.senne.imttech.client.NavigationScreen;
import com.senne.imttech.client.OreType;
import com.senne.imttech.client.voice.ImtAssistant;
import com.senne.imttech.client.voice.ImtListener;
import com.senne.imttech.client.voice.ImtSpeech;
import com.senne.imttech.client.voice.VoiceFeature;
import com.senne.imttech.client.voice.VoiceInstaller;
import com.senne.imttech.imt.ImtCooldown;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtFeature;
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

        // Regel 1: I.M.T en de batterij
        Component name = c.powered ? Component.literal("I.M.T") : Component.translatable("hud.imttech.offline");
        g.drawString(c.font, name, x, y, argb(255, c.powered ? CYAN : RED), true);
        int barX = x + c.font.width(name) + 5;
        c.panelRight = barX + 70;
        float fraction = ImtData.getEnergyFraction(c.glasses);
        int barColor = fraction < 0.1f ? RED : fraction < 0.3f ? ORANGE : CYAN;
        bar(g, barX, y + 3, 40, 3, fraction, barColor);
        g.drawString(c.font, Math.round(fraction * 100) + "%", barX + 44, y, argb(210, barColor), true);
        // Praat I.M.T? Dan een klein bewegend golfje naast de batterij.
        if (ImtSpeech.isSpeaking()) {
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
        for (ImtFeature feature : ImtFeature.values()) {
            if (ImtData.isEnabled(c.glasses, feature)) {
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
        // Je praat tegen I.M.T, of ze denkt na over wat je zei
        if (ImtAssistant.isBusy()) {
            y = renderListening(c, x, y);
        }
        // Vijanden in de buurt (dreigingsscan)
        if (!ImtClientState.threats.isEmpty()) {
            int alpha = (int) (190 + 60 * Math.sin(c.now / 150.0));
            panelText(c, Component.translatable("hud.imttech.threats", ImtClientState.threats.size()), x, y, argb(alpha, RED));
            y += LINE;
        }
        // Drones in de lucht
        if (ImtClientState.swarmDrones > 0 || ImtClientState.guardDrones > 0) {
            panelText(c, Component.translatable("hud.imttech.drones", ImtClientState.swarmDrones, ImtClientState.guardDrones),
                    x, y, argb(230, CYAN));
            y += LINE;
        }
        // Vlieg je met drones? Dan de besturing even in beeld.
        if (ImtClientState.flying) {
            panelText(c, Component.translatable("hud.imttech.flying"), x, y, argb(240, CYAN));
            y += LINE;
            panelText(c, Component.translatable("hud.imttech.flying_help"), x + 6, y, argb(190, WHITE));
            y += LINE;
        }
        // Wie je hackt
        if (ImtClientState.hackedCount > 0) {
            panelText(c, Component.translatable("hud.imttech.hacked", ImtClientState.hackedCount),
                    x, y, argb(235, RED));
            y += LINE;
        }
        // Spionagedrone: wie hij volgt, hoe lang nog en hoeveel leven het doelwit heeft
        if (ImtClientState.spyActive) {
            panelText(c, Component.translatable("hud.imttech.spy", ImtClientState.spyTarget,
                    ImtClientState.spyTimeLeft()), x, y, argb(235, PURPLE));
            y += LINE;
            if (ImtClientState.spyPos != null && ImtClientState.spyDimension == c.player.level().dimension()) {
                int distance = (int) Math.round(Math.sqrt(c.player.distanceToSqr(ImtClientState.spyPos)));
                panelText(c, Component.translatable("hud.imttech.spy_at", distance,
                        Math.max(1, Math.round(ImtClientState.spyHealth / 2))), x + 6, y, argb(200, PURPLE));
            } else {
                panelText(c, Component.translatable("hud.imttech.spy_other_world",
                        NavigationScreen.dimensionName(ImtClientState.spyDimension)), x + 6, y, argb(200, PURPLE));
            }
            y += LINE;
        }
        // Gemarkeerde doelwitten
        if (!ImtClientState.markedIds.isEmpty()) {
            panelText(c, Component.translatable("hud.imttech.marked", ImtClientState.markedIds.size()), x, y, argb(240, ORANGE));
            y += LINE;
        }
        // Wachttijden die nog lopen
        y = cooldownLine(c, x, y, "hud.imttech.swarm", ImtClientState.cooldown(ImtCooldown.SWARM));
        y = cooldownLine(c, x, y, "hud.imttech.strike", ImtClientState.cooldown(ImtCooldown.STRIKE));
        y = cooldownLine(c, x, y, "hud.imttech.rockets", ImtClientState.cooldown(ImtCooldown.ROCKETS));
        y = cooldownLine(c, x, y, "hud.imttech.satellite", ImtClientState.cooldown(ImtCooldown.SATELLITE));
        y = cooldownLine(c, x, y, "hud.imttech.ore_scan", ImtClientState.cooldown(ImtCooldown.ORE_SCAN));
        // Bestemming (navigatie)
        y = renderNavigationLine(c, x, y);
        // Resultaat van de ertsen-scan, zolang de markeringen er nog zijn
        if (ImtClientState.hasOreScan()) {
            y = renderOreResults(c, x, y + 2);
        }
        // Pings van jezelf en anderen
        int shown = 0;
        for (ImtClientState.Ping ping : ImtClientState.pings) {
            if (shown++ >= 3) {
                break;
            }
            int distance = (int) Math.round(Math.sqrt(c.player.distanceToSqr(ping.pos())));
            panelText(c, Component.translatable("hud.imttech.ping_line", ping.sender(), distance), x, y, argb(220, GREEN));
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
        ImtAssistant.Stage stage = ImtAssistant.stage();
        boolean listening = stage == ImtAssistant.Stage.LISTENING;
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
            case LISTENING -> "hud.imttech.voice.listening";
            case STARTING -> "hud.imttech.voice.starting";
            case THINKING -> "hud.imttech.voice.thinking";
            default -> "hud.imttech.voice.processing";
        };
        Component label = Component.translatable(key);
        panelText(c, label, x + 8, y, argb(240, listening ? RED : CYAN));
        if (listening) {
            bar(g, x + 12 + c.font.width(label), y + 3, 30, 3, ImtListener.level(), GREEN);
        }
        return y + LINE;
    }

    /** Hoeveel ertsen van elke soort de scan vond, met een balkje dat aftelt tot de markeringen weg zijn. */
    private int renderOreResults(HudContext c, int x, int y) {
        Map<OreType, Integer> counts = new EnumMap<>(OreType.class);
        for (ImtClientState.OreMarker marker : ImtClientState.oreMarkers) {
            counts.merge(marker.type(), 1, Integer::sum);
        }
        int shown = 0;
        for (Map.Entry<OreType, Integer> entry : counts.entrySet()) {
            if (shown++ >= 6) {
                break;
            }
            c.g.fill(x, y + 2, x + 4, y + 6, argb(255, entry.getKey().color()));
            panelText(c, Component.translatable("hud.imttech.ore_count", entry.getKey().displayName(), entry.getValue()),
                    x + 7, y, argb(220, WHITE));
            y += LINE;
        }
        float left = 1.0f - (float) (c.now - ImtClientState.oreScanStartMillis)
                / (ImtClientState.oreScanEndMillis - ImtClientState.oreScanStartMillis);
        bar(c.g, x, y + 1, 70, 2, left, CYAN);
        return y + 6;
    }

    /** Regel met een wachttijd die nog loopt (oranje). Is hij klaar, dan tekenen we niets. */
    private int cooldownLine(HudContext c, int x, int y, String key, int cooldownTicks) {
        int seconds = ImtClientState.secondsLeft(cooldownTicks);
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
            line = Component.translatable("hud.imttech.nav", target.name(), distance + "m" + up);
        } else {
            line = Component.translatable("hud.imttech.nav_other_dimension", target.name(), target.dimensionName());
        }
        c.g.fill(x, y + 1, x + 4, y + 7, argb(255, target.color()));
        panelText(c, line, x + 7, y, argb(230, WHITE));
        return y + 11;
    }
}
