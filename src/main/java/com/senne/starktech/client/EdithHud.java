package com.senne.starktech.client;

import com.senne.starktech.client.voice.EdithAssistant;
import com.senne.starktech.client.voice.EdithListener;
import com.senne.starktech.client.voice.EdithSpeech;
import com.senne.starktech.client.voice.VoiceFeature;
import com.senne.starktech.client.voice.VoiceInstaller;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithCooldown;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithFeature;
import com.senne.starktech.edith.EdithOwner;
import com.senne.starktech.nav.EdithWaypoints;
import com.senne.starktech.nav.Waypoint;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.registry.ModAttachments;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * De HUD van E.D.I.T.H.: alles wat je "in de bril" ziet. Wordt elke frame getekend.
 *
 * Tekenen gebeurt met simpele rechthoekjes (fill) en tekst, dus er zijn geen
 * plaatjes nodig. Kleuren zijn geschreven als 0xAARRGGBB (A = doorzichtigheid).
 */
public class EdithHud implements GuiLayer {
    // ---------------------------------------------------------------- Kleuren (zonder alpha)
    static final int CYAN = 0x3FD8FF;
    static final int WHITE = 0xE8FBFF;
    static final int ORANGE = 0xFFA93F;
    static final int RED = 0xFF4545;
    static final int GREEN = 0x5CFF9D;
    /** Paars: alles van de spionagedrone. */
    static final int PURPLE = 0xD02BFF;

    /** Hoe lang de opstart-animatie duurt (ms). */
    private static final long BOOT_MILLIS = 1600;

    private final WorldProjection projection = new WorldProjection();

    static int argb(int alpha, int rgb) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.gui.hud.isHidden() || !EdithClientState.wearing) {
            return;
        }
        ItemStack glasses = EdithData.getWornGlasses(player);
        if (glasses.isEmpty()) {
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        long now = Util.getMillis();

        if (!EdithClientState.authorized) {
            renderAccessDenied(g, font, glasses, w, h, now);
            renderNotifications(g, font, w, now);
            return;
        }

        long bootAge = now - EdithClientState.bootStartMillis;
        if (bootAge < BOOT_MILLIS) {
            renderBoot(g, font, w, h, bootAge);
            return;
        }

        projection.begin(w, h);
        boolean powered = EdithData.getEnergy(glasses) > 0;
        // Is het ronde G-menu open? Dan verdwijnt alles wat plat op je scherm staat (het paneel
        // links, het kompas, meldingen...), zodat niets de cirkel in het midden in de weg zit.
        // Markeringen in de wereld (vijanden, ertsen, pings...) blijven wel zichtbaar.
        boolean menuOpen = mc.gui.screen() instanceof EdithMenuScreen;

        renderFrame(g, w, h, powered ? CYAN : RED, 110);
        if (powered) {
            // In de wereld: alles wat aan staat, altijd. Staat iets uit (of is er niets), dan tekenen
            // deze methodes gewoon niets.
            renderThreatMarkers(g, font, player);
            renderMarkedTargets(g, font, mc);
            renderTarget(g, font, player, w, h, !menuOpen);
            if (EdithClientState.hasOreScan()) {
                renderOreMarkers(g, font, player, now);
            }
            renderNavigationMarker(g, font, player, now);
            renderPings(g, font, now);
            renderSpyTarget(g, font, player, now);
            renderSatelliteStrike(g, font, w, h, now, !menuOpen);
            if (!menuOpen) {
                renderCompass(g, font, player, w);
                if (DroneCamera.isActive()) {
                    renderDroneFeed(g, font, player, w, h, now);
                } else if (EdithClientState.zooming) {
                    renderZoom(g, font, player, w, h);
                }
            }
        } else if (!menuOpen) {
            Component noPower = Component.translatable("hud.starktech.no_power");
            int alpha = (int) (150 + 100 * Math.sin(now / 200.0));
            g.centeredText(font, noPower, w / 2, h / 2 - 30, argb(alpha, RED));
        }
        if (!menuOpen) {
            renderStatusPanel(g, font, player, glasses, powered, now);
            renderNotifications(g, font, w, now);
        }
    }

    // ================================================================ Onderdelen

    /** Hoekbeugels rond het hele scherm. */
    private void renderFrame(GuiGraphicsExtractor g, int w, int h, int color, int alpha) {
        int m = 4;
        int len = 22;
        brackets(g, m, m, w - m, h - m, len, argb(alpha, color));
    }

    // ---------------------------------------------------------------- Paneel links

    private static final int PANEL_X = 10;
    /** Aantal pixels tussen twee regels tekst. */
    private static final int LINE = 10;
    /** Rechterrand van het paneel links; meldingen van E.D.I.T.H. schuiven daarnaast. */
    private int panelRight;

    /** Tekent een regel in het paneel links en onthoudt hoe breed het paneel is. */
    private void panelText(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
        g.text(font, text, x, y, color, true);
        panelRight = Math.max(panelRight, x + font.width(text));
    }

    /**
     * Linksboven: je batterij en ALLES wat nu actief is, altijd (geen tabbladen).
     * Wat uit staat of niet bezig is, zie je hier niet; staat er niets aan, dan zie je alleen de batterij.
     * (Zolang het G-menu open is, is dit paneel even weg.)
     */
    private void renderStatusPanel(GuiGraphicsExtractor g, Font font, Player player, ItemStack glasses, boolean powered, long now) {
        int x = PANEL_X;
        int y = 10;

        // Regel 1: E.D.I.T.H. en de batterij
        Component name = powered ? Component.literal("E.D.I.T.H.") : Component.translatable("hud.starktech.offline");
        g.text(font, name, x, y, argb(255, powered ? CYAN : RED), true);
        int barX = x + font.width(name) + 5;
        panelRight = barX + 70;
        float fraction = EdithData.getEnergyFraction(glasses);
        int barColor = fraction < 0.1f ? RED : fraction < 0.3f ? ORANGE : CYAN;
        bar(g, barX, y + 3, 40, 3, fraction, barColor);
        g.text(font, Math.round(fraction * 100) + "%", barX + 44, y, argb(210, barColor), true);
        // Praat E.D.I.T.H.? Dan een klein bewegend golfje naast de batterij.
        if (EdithSpeech.isSpeaking()) {
            int waveX = barX + 70;
            for (int i = 0; i < 5; i++) {
                int wave = 2 + (int) (3 * (1 + Math.sin(now / 70.0 + i * 1.3)));
                g.fill(waveX + i * 3, y + 4 - wave / 2, waveX + i * 3 + 2, y + 4 + wave / 2 + 1, argb(230, CYAN));
            }
            panelRight = Math.max(panelRight, waveX + 15);
        }
        y += 13;
        if (!powered) {
            return;
        }

        // Regel 2: de functies die aan staan, als labeltjes (bv. NZ, DREIGING, DOEL, ESCORTE)
        int tagX = x;
        for (EdithFeature feature : EdithFeature.values()) {
            if (EdithData.isEnabled(glasses, feature)) {
                Component label = Component.translatable(feature.translationKey() + ".short");
                int tw = font.width(label);
                g.fill(tagX - 1, y - 1, tagX + tw + 2, y + 9, argb(70, CYAN));
                g.text(font, label, tagX + 1, y, argb(255, WHITE), false);
                tagX += tw + 6;
                panelRight = Math.max(panelRight, tagX);
            }
        }
        if (tagX != x) {
            y += LINE + 3;
        }

        // Daaronder: alleen wat er nu gebeurt.
        // Je praat tegen E.D.I.T.H., of ze denkt na over wat je zei
        if (EdithAssistant.isBusy()) {
            y = renderListening(g, font, x, y, now);
        }
        // Vijanden in de buurt (dreigingsscan)
        if (!EdithClientState.threats.isEmpty()) {
            int alpha = (int) (190 + 60 * Math.sin(now / 150.0));
            panelText(g, font, Component.translatable("hud.starktech.threats", EdithClientState.threats.size()), x, y, argb(alpha, RED));
            y += LINE;
        }
        // Drones in de lucht
        if (EdithClientState.swarmDrones > 0 || EdithClientState.guardDrones > 0) {
            panelText(g, font, Component.translatable("hud.starktech.drones", EdithClientState.swarmDrones, EdithClientState.guardDrones),
                    x, y, argb(230, CYAN));
            y += LINE;
        }
        // Vlieg je met drones? Dan de besturing even in beeld.
        if (EdithClientState.flying) {
            panelText(g, font, Component.translatable("hud.starktech.flying"), x, y, argb(240, CYAN));
            y += LINE;
            panelText(g, font, Component.translatable("hud.starktech.flying_help"), x + 6, y, argb(190, WHITE));
            y += LINE;
        }
        // Spionagedrone: wie hij volgt, hoe lang nog en hoeveel leven het doelwit heeft
        if (EdithClientState.spyActive) {
            panelText(g, font, Component.translatable("hud.starktech.spy", EdithClientState.spyTarget,
                    EdithClientState.spyTimeLeft()), x, y, argb(235, PURPLE));
            y += LINE;
            if (EdithClientState.spyPos != null && EdithClientState.spyDimension == player.level().dimension()) {
                int distance = (int) Math.round(Math.sqrt(player.distanceToSqr(EdithClientState.spyPos)));
                panelText(g, font, Component.translatable("hud.starktech.spy_at", distance,
                        Math.max(1, Math.round(EdithClientState.spyHealth / 2))), x + 6, y, argb(200, PURPLE));
            } else {
                panelText(g, font, Component.translatable("hud.starktech.spy_other_world",
                        NavigationScreen.dimensionName(EdithClientState.spyDimension)), x + 6, y, argb(200, PURPLE));
            }
            y += LINE;
        }
        // Gemarkeerde doelwitten
        if (!EdithClientState.markedIds.isEmpty()) {
            panelText(g, font, Component.translatable("hud.starktech.marked", EdithClientState.markedIds.size()), x, y, argb(240, ORANGE));
            y += LINE;
        }
        // Wachttijden die nog lopen
        y = cooldownLine(g, font, x, y, "hud.starktech.swarm", EdithClientState.cooldown(EdithCooldown.SWARM));
        y = cooldownLine(g, font, x, y, "hud.starktech.strike", EdithClientState.cooldown(EdithCooldown.STRIKE));
        y = cooldownLine(g, font, x, y, "hud.starktech.satellite", EdithClientState.cooldown(EdithCooldown.SATELLITE));
        y = cooldownLine(g, font, x, y, "hud.starktech.ore_scan", EdithClientState.cooldown(EdithCooldown.ORE_SCAN));
        // Bestemming (navigatie)
        y = renderNavigationLine(g, font, player, x, y);
        // Resultaat van de ertsen-scan, zolang de markeringen er nog zijn
        if (EdithClientState.hasOreScan()) {
            y = renderOreResults(g, font, x, y + 2, now);
        }
        // Pings van jezelf en anderen
        int shown = 0;
        for (EdithClientState.Ping ping : EdithClientState.pings) {
            if (shown++ >= 3) {
                break;
            }
            int distance = (int) Math.round(Math.sqrt(player.distanceToSqr(ping.pos())));
            panelText(g, font, Component.translatable("hud.starktech.ping_line", ping.sender(), distance), x, y, argb(220, GREEN));
            y += LINE;
        }
        // Stem of spraakbesturing aan het downloaden
        for (VoiceFeature feature : VoiceFeature.values()) {
            if (VoiceInstaller.state(feature) == VoiceInstaller.State.INSTALLING) {
                int percent = VoiceInstaller.percent(feature);
                panelText(g, font, feature.downloadingLine(percent), x, y, argb(220, CYAN));
                bar(g, x, y + 9, 70, 2, percent / 100.0f, CYAN);
                y += LINE + 3;
            }
        }
    }

    /** "● LUISTEREN" (met een balkje voor hoe luid je praat), of "VERWERKEN..." als het oor uitschrijft. */
    private int renderListening(GuiGraphicsExtractor g, Font font, int x, int y, long now) {
        EdithAssistant.Stage stage = EdithAssistant.stage();
        boolean listening = stage == EdithAssistant.Stage.LISTENING;
        if (listening && (now / 400) % 2 == 0) {
            g.fill(x, y + 2, x + 5, y + 7, argb(255, RED));
        } else if (!listening) {
            // Drie puntjes die om de beurt oplichten: ze is bezig.
            for (int i = 0; i < 3; i++) {
                boolean lit = (now / 200) % 3 == i;
                g.fill(x + i * 2, y + 4, x + i * 2 + 1, y + 5, argb(lit ? 255 : 90, CYAN));
            }
        }
        String key = switch (stage) {
            case LISTENING -> "hud.starktech.voice.listening";
            case STARTING -> "hud.starktech.voice.starting";
            default -> "hud.starktech.voice.processing";
        };
        Component label = Component.translatable(key);
        panelText(g, font, label, x + 8, y, argb(240, listening ? RED : CYAN));
        if (listening) {
            bar(g, x + 12 + font.width(label), y + 3, 30, 3, EdithListener.level(), GREEN);
        }
        return y + LINE;
    }

    /** Hoeveel ertsen van elke soort de scan vond, met een balkje dat aftelt tot de markeringen weg zijn. */
    private int renderOreResults(GuiGraphicsExtractor g, Font font, int x, int y, long now) {
        Map<OreType, Integer> counts = new EnumMap<>(OreType.class);
        for (EdithClientState.OreMarker marker : EdithClientState.oreMarkers) {
            counts.merge(marker.type(), 1, Integer::sum);
        }
        int shown = 0;
        for (Map.Entry<OreType, Integer> entry : counts.entrySet()) {
            if (shown++ >= 6) {
                break;
            }
            g.fill(x, y + 2, x + 4, y + 6, argb(255, entry.getKey().color()));
            panelText(g, font, Component.translatable("hud.starktech.ore_count", entry.getKey().displayName(), entry.getValue()),
                    x + 7, y, argb(220, WHITE));
            y += LINE;
        }
        float left = 1.0f - (float) (now - EdithClientState.oreScanStartMillis)
                / (EdithClientState.oreScanEndMillis - EdithClientState.oreScanStartMillis);
        bar(g, x, y + 1, 70, 2, left, CYAN);
        return y + 6;
    }

    /** Hoeveel energie (SE) de bril nu per seconde verbruikt: dezelfde berekening als op de server. */
    public static int drainPerSecond(ItemStack glasses) {
        int drain = EdithBalance.DRAIN_HUD;
        for (EdithFeature feature : EdithFeature.values()) {
            if (EdithData.isEnabled(glasses, feature)) {
                drain += feature.drainPerSecond();
            }
        }
        if (DroneCamera.isActive()) {
            drain += EdithBalance.DRAIN_SCOUT;
        }
        return drain;
    }

    /** Hoe lang de batterij nog meegaat bij dit verbruik, bv. "1u 20m" of "4m 10s". */
    public static Component timeLeft(int energy, int drainPerSecond) {
        int seconds = energy / Math.max(1, drainPerSecond);
        return seconds >= 3600
                ? Component.translatable("hud.starktech.sys.time_hours", seconds / 3600, seconds % 3600 / 60)
                : Component.translatable("hud.starktech.sys.time_minutes", seconds / 60, seconds % 60);
    }

    /** Regel met een wachttijd die nog loopt (oranje). Is hij klaar, dan tekenen we niets. */
    private int cooldownLine(GuiGraphicsExtractor g, Font font, int x, int y, String key, int cooldownTicks) {
        int seconds = EdithClientState.secondsLeft(cooldownTicks);
        if (seconds <= 0) {
            return y;
        }
        panelText(g, font, Component.translatable(key, seconds + "s"), x, y, argb(210, ORANGE));
        return y + LINE;
    }

    /** Oranje beugels rond gemarkeerde doelwitten (ook door muren). */
    private void renderMarkedTargets(GuiGraphicsExtractor g, Font font, Minecraft mc) {
        if (mc.level == null) {
            return;
        }
        int number = 0;
        for (int id : EdithClientState.markedIds) {
            number++;
            if (!(mc.level.getEntity(id) instanceof LivingEntity entity) || !entity.isAlive()) {
                continue;
            }
            WorldProjection.ScreenRect rect = projection.projectBox(entity.getBoundingBox());
            if (rect == null) {
                continue;
            }
            int pad = 5;
            int x0 = (int) rect.minX() - pad;
            int y0 = (int) rect.minY() - pad;
            int x1 = (int) rect.maxX() + pad;
            int y1 = (int) rect.maxY() + pad;
            brackets(g, x0, y0, x1, y1, 4, argb(240, ORANGE));
            smallText(g, font, Component.translatable("hud.starktech.marked_label", number).getString(), (x0 + x1) / 2, y0 - 8, argb(240, ORANGE));
        }
    }

    /** Rood richtkruis en aftelling voor een satelliet-aanval die aan het opladen is. */
    private void renderSatelliteStrike(GuiGraphicsExtractor g, Font font, int w, int h, long now, boolean underCrosshair) {
        Vec3 target = EdithClientState.strikeTarget;
        if (target == null || now > EdithClientState.strikeAtMillis + 500) {
            return;
        }
        float secondsLeft = Math.max(0, (EdithClientState.strikeAtMillis - now) / 1000.0f);
        int pulse = (int) (170 + 85 * Math.sin(now / 90.0));
        Component countdown = Component.translatable("hud.starktech.impact", String.format("%.1f", secondsLeft));

        WorldProjection.ScreenPoint p = projection.project(target);
        if (p != null && projection.isOnScreen(p, 0)) {
            int x = (int) p.x();
            int y = (int) p.y();
            int size = 10 + (int) (6 * Math.sin(now / 120.0));
            brackets(g, x - size, y - size, x + size, y + size, 5, argb(pulse, RED));
            g.fill(x - 1, y - 1, x + 1, y + 1, argb(255, RED));
            g.centeredText(font, countdown, x, y + size + 4, argb(255, RED));
        }
        // Ook onder het vizier, voor als het doel niet in beeld is (niet als het G-menu open is).
        if (underCrosshair) {
            g.centeredText(font, countdown, w / 2, h / 2 + 24, argb(pulse, RED));
            // In welke stand de satelliet staat (precisie, bombardement of schuivende laser).
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ItemStack glasses = EdithData.getWornGlasses(mc.player);
                if (!glasses.isEmpty()) {
                    g.centeredText(font, Component.translatable(EdithData.getSatelliteMode(glasses).translationKey()),
                            w / 2, h / 2 + 34, argb(pulse, ORANGE));
                }
            }
        }
    }

    /**
     * Markering op het doelwit van de spionagedrone: een paarse ruit met naam en afstand.
     * Die zie je door muren heen en van elke afstand (de drone stuurt de plek door).
     */
    private void renderSpyTarget(GuiGraphicsExtractor g, Font font, Player player, long now) {
        Vec3 pos = EdithClientState.spyPos;
        if (!EdithClientState.spyActive || pos == null || EdithClientState.spyDimension != player.level().dimension()) {
            return;
        }
        WorldProjection.ScreenPoint point = projection.project(pos.add(0, 2.2, 0));
        if (point == null) {
            return;
        }
        int px = (int) point.x();
        int py = (int) point.y();
        int alpha = (int) (170 + 60 * Math.sin(now / 300.0));
        // Een klein ruitje (vier streepjes rond het midden).
        g.fill(px - 1, py - 5, px + 2, py - 4, argb(alpha, PURPLE));
        g.fill(px - 1, py + 4, px + 2, py + 5, argb(alpha, PURPLE));
        g.fill(px - 5, py - 1, px - 4, py + 2, argb(alpha, PURPLE));
        g.fill(px + 4, py - 1, px + 5, py + 2, argb(alpha, PURPLE));
        int distance = (int) Math.round(Math.sqrt(player.distanceToSqr(pos)));
        smallText(g, font, EdithClientState.spyTarget.getString() + "  " + distance + "m", px, py - 16, argb(230, PURPLE));
    }

    /** Kompas bovenaan in het midden. */
    private void renderCompass(GuiGraphicsExtractor g, Font font, Player player, int w) {
        int width = 150;
        int cx = w / 2;
        int y = 8;
        float pixelsPerDegree = 1.5f;
        // In Minecraft is yaw 0 = zuid. Wij willen 0 = noord (zoals een echt kompas).
        float bearing = Mth.positiveModulo(player.getYRot() + 180.0f, 360.0f);

        g.fill(cx - width / 2, y + 10, cx + width / 2, y + 11, argb(120, CYAN));
        String[] labels = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        for (int deg = 0; deg < 360; deg += 15) {
            float diff = Mth.wrapDegrees(deg - bearing);
            float px = cx + diff * pixelsPerDegree;
            if (Math.abs(diff) * pixelsPerDegree > width / 2f) {
                continue;
            }
            int alpha = (int) (255 * (1.0f - Math.abs(diff) * pixelsPerDegree / (width / 2f)));
            if (deg % 45 == 0) {
                String label = labels[deg / 45];
                int color = label.equals("N") ? RED : WHITE;
                g.centeredText(font, label, (int) px, y, argb(alpha, color));
                g.fill((int) px, y + 8, (int) px + 1, y + 12, argb(alpha, CYAN));
            } else {
                g.fill((int) px, y + 9, (int) px + 1, y + 12, argb(alpha / 2, CYAN));
            }
        }
        // Andere waypoints in deze wereld: kleine streepjes in hun eigen kleur.
        Vec3 from = cameraPosition();
        EdithWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        for (Waypoint waypoint : data.waypoints()) {
            if (waypoint.dimension() == player.level().dimension() && waypoint != data.activeWaypoint()) {
                compassMarker(g, font, cx, y, width, bearing, bearingTo(from, Vec3.atCenterOf(waypoint.pos())), waypoint.color(), null, false);
            }
        }
        // Pings: groen.
        for (EdithClientState.Ping ping : EdithClientState.pings) {
            compassMarker(g, font, cx, y, width, bearing, bearingTo(from, ping.pos()), GREEN, null, true);
        }
        // Het doelwit van de spionagedrone: paars.
        if (EdithClientState.spyActive && EdithClientState.spyPos != null
                && EdithClientState.spyDimension == player.level().dimension()) {
            compassMarker(g, font, cx, y, width, bearing, bearingTo(from, EdithClientState.spyPos), PURPLE,
                    EdithClientState.spyTarget, true);
        }
        // De bestemming: groot, met pijltje aan de rand als hij buiten het kompas valt.
        NavTarget target = navTarget(player);
        if (target != null && target.sameDimension()) {
            compassMarker(g, font, cx, y, width, bearing, bearingTo(from, target.pos()), target.color(), target.name(), true);
        }

        // Middenstreepje en graden
        g.fill(cx - 1, y + 11, cx + 1, y + 16, argb(255, CYAN));
        g.centeredText(font, Math.round(bearing) % 360 + "°", cx, y + 17, argb(200, WHITE));
    }

    /** Een markering op het kompas. Valt hij erbuiten, dan een pijltje aan de rand (alleen als important). */
    private void compassMarker(GuiGraphicsExtractor g, Font font, int cx, int y, int width, float bearing, float targetBearing,
                               int color, Component label, boolean important) {
        float diff = Mth.wrapDegrees(targetBearing - bearing);
        float px = cx + diff * 1.5f;
        float half = width / 2f;
        if (Math.abs(px - cx) <= half) {
            int x = (int) px;
            if (important) {
                g.fill(x - 2, y + 9, x + 3, y + 14, argb(255, color));
                if (label != null) {
                    smallText(g, font, label.getString(), x, y - 7, argb(230, color));
                }
            } else {
                g.fill(x, y + 9, x + 1, y + 14, argb(200, color));
            }
        } else if (important) {
            // Pijltje aan de linker- of rechterkant van het kompas.
            int x = diff < 0 ? (int) (cx - half - 6) : (int) (cx + half + 2);
            g.text(font, diff < 0 ? "<" : ">", x, y + 6, argb(255, color), true);
        }
    }

    /** Kompasrichting (0 = noord, 90 = oost) van een punt naar een ander punt. */
    private static float bearingTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        return Mth.positiveModulo(yaw + 180.0f, 360.0f);
    }

    /** Waar de camera is (je ogen, of je verkenningsdrone). */
    private static Vec3 cameraPosition() {
        return Minecraft.getInstance().gameRenderer.mainCamera().position();
    }

    // ================================================================ Fase 3: navigatie, pings, drone-camera

    /** De huidige bestemming, klaar om te tekenen. */
    private record NavTarget(Component name, Vec3 pos, int color, boolean sameDimension, Component dimensionName) {}

    private static NavTarget navTarget(Player player) {
        EdithWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        if (data.active() == EdithWaypoints.DEATH) {
            Optional<GlobalPos> death = player.getLastDeathLocation();
            if (death.isEmpty()) {
                return null;
            }
            return new NavTarget(Component.translatable("edith.starktech.nav.death"), Vec3.atCenterOf(death.get().pos()), RED,
                    death.get().dimension() == player.level().dimension(), NavigationScreen.dimensionName(death.get().dimension()));
        }
        Waypoint waypoint = data.activeWaypoint();
        if (waypoint == null) {
            return null;
        }
        return new NavTarget(Component.literal(waypoint.name()), Vec3.atCenterOf(waypoint.pos()), waypoint.color(),
                waypoint.dimension() == player.level().dimension(), NavigationScreen.dimensionName(waypoint.dimension()));
    }

    /** Regel in het statuspaneel: waar navigeer je naartoe, hoe ver, hoger of lager? */
    private int renderNavigationLine(GuiGraphicsExtractor g, Font font, Player player, int x, int y) {
        NavTarget target = navTarget(player);
        if (target == null) {
            return y;
        }
        Component line;
        if (target.sameDimension()) {
            int distance = (int) Math.round(Math.sqrt(player.distanceToSqr(target.pos())));
            int height = (int) Math.round(target.pos().y - player.getY());
            String up = height > 2 ? " +" + height : height < -2 ? " " + height : "";
            line = Component.translatable("hud.starktech.nav", target.name(), distance + "m" + up);
        } else {
            line = Component.translatable("hud.starktech.nav_other_dimension", target.name(), target.dimensionName());
        }
        g.fill(x, y + 1, x + 4, y + 7, argb(255, target.color()));
        panelText(g, font, line, x + 7, y, argb(230, WHITE));
        return y + 11;
    }

    /** Ruitje met naam en afstand op de bestemming in de wereld. */
    private void renderNavigationMarker(GuiGraphicsExtractor g, Font font, Player player, long now) {
        NavTarget target = navTarget(player);
        if (target == null || !target.sameDimension()) {
            return;
        }
        WorldProjection.ScreenPoint p = projection.project(target.pos().add(0, 1.0, 0));
        if (p == null || !projection.isOnScreen(p, 0)) {
            return;
        }
        int x = (int) p.x();
        int y = (int) p.y();
        int pulse = (int) (200 + 55 * Math.sin(now / 250.0));
        g.pose().pushMatrix();
        g.pose().translate(p.x(), p.y());
        g.pose().rotate((float) (Math.PI / 4));
        g.fill(-4, -4, 4, 4, argb(pulse, target.color()));
        g.fill(-2, -2, 2, 2, argb(160, 0x000000));
        g.pose().popMatrix();
        int distance = (int) Math.round(Math.sqrt(cameraPosition().distanceToSqr(target.pos())));
        smallText(g, font, target.name().getString(), x, y - 14, argb(255, target.color()));
        smallText(g, font, distance + "m", x, y + 7, argb(220, WHITE));
    }

    /** Pings van jezelf en anderen: groene beugels met naam en afstand. */
    private void renderPings(GuiGraphicsExtractor g, Font font, long now) {
        for (EdithClientState.Ping ping : EdithClientState.pings) {
            WorldProjection.ScreenPoint p = projection.project(ping.pos());
            if (p == null || !projection.isOnScreen(p, 0)) {
                continue;
            }
            long left = ping.endMillis() - now;
            int alpha = left < 1500 ? (int) (255 * left / 1500.0) : 255;
            int x = (int) p.x();
            int y = (int) p.y();
            // Een uitdijende ring bij het begin, daarna een rustig pulserend kader.
            long age = now - ping.startMillis();
            int size = age < 600 ? 4 + (int) (age / 30) : 6 + (int) (2 * Math.sin(now / 150.0));
            brackets(g, x - size, y - size, x + size, y + size, 3, argb(alpha, GREEN));
            g.fill(x - 1, y - 1, x + 1, y + 1, argb(alpha, GREEN));
            int distance = (int) Math.round(Math.sqrt(cameraPosition().distanceToSqr(ping.pos())));
            smallText(g, font, ping.sender() + " · " + distance + "m", x, y - size - 8, argb(alpha, GREEN));
        }
    }

    /**
     * Het "camerabeeld" als je door de verkenningsdrone kijkt: scanlijnen, een knipperende
     * REC-stip, signaalsterkte, de levens van de drone en de afstand tot je lichaam.
     */
    private void renderDroneFeed(GuiGraphicsExtractor g, Font font, Player player, int w, int h, long now) {
        StarkDroneEntity drone = DroneCamera.drone();
        if (drone == null) {
            return;
        }
        // Scanlijnen
        for (int y = 0; y < h; y += 3) {
            g.fill(0, y, w, y + 1, argb(18, 0x000000));
        }
        brackets(g, 20, 20, w - 20, h - 20, 40, argb(220, WHITE));

        // Vizier
        int cx = w / 2;
        int cy = h / 2;
        g.fill(cx - 12, cy, cx - 4, cy + 1, argb(220, WHITE));
        g.fill(cx + 5, cy, cx + 13, cy + 1, argb(220, WHITE));
        g.fill(cx, cy - 12, cx + 1, cy - 4, argb(220, WHITE));
        g.fill(cx, cy + 5, cx + 1, cy + 13, argb(220, WHITE));

        // REC + titel (rechtsboven, onder het kompas)
        int x = w - 150;
        int y = 30;
        if ((now / 500) % 2 == 0) {
            g.fill(x, y + 1, x + 6, y + 7, argb(255, RED));
        }
        g.text(font, Component.translatable("hud.starktech.drone_feed"), x + 10, y, argb(255, WHITE), true);
        y += 12;

        // Signaalsterkte: hoe verder van je lichaam, hoe zwakker.
        double distance = Math.sqrt(drone.distanceToSqr(player));
        float signal = 1.0f - (float) Math.min(1.0, distance / EdithBalance.SCOUT_RANGE);
        int bars = 1 + Math.round(signal * 4);
        for (int i = 0; i < 5; i++) {
            int barHeight = 3 + i * 2;
            int color = i < bars ? (signal < 0.25f ? RED : signal < 0.5f ? ORANGE : GREEN) : 0x404850;
            g.fill(x + i * 4, y + 12 - barHeight, x + i * 4 + 3, y + 12, argb(230, color));
        }
        g.text(font, Component.translatable("hud.starktech.drone_distance", Math.round(distance)), x + 24, y + 2, argb(220, WHITE), true);
        y += 16;

        // Levens van de drone
        float health = drone.getHealth() / Math.max(1.0f, drone.getMaxHealth());
        g.text(font, Component.translatable("hud.starktech.drone_hull"), x, y, argb(220, WHITE), true);
        bar(g, x + 40, y + 2, 60, 4, health, health < 0.3f ? RED : GREEN);
        y += 11;
        g.text(font, Component.translatable("hud.starktech.drone_altitude", Math.round(drone.getY())), x, y, argb(200, WHITE), true);

        // Uitleg onderaan
        g.centeredText(font, Component.translatable("hud.starktech.drone_controls"), cx, h - 34, argb(200, WHITE));
    }

    /** Doelwit-analyse: beugels om het wezen waar je naar kijkt + een info-paneel rechts. */
    private void renderTarget(GuiGraphicsExtractor g, Font font, Player player, int w, int h, boolean showPanel) {
        LivingEntity target = EdithClientState.target;
        if (target == null || !target.isAlive()) {
            return;
        }
        ThreatLevel threat = ThreatLevel.of(target);

        // Beugels rond het doelwit in de wereld.
        WorldProjection.ScreenRect rect = projection.projectBox(target.getBoundingBox());
        if (rect != null) {
            int pad = 3;
            brackets(g, (int) rect.minX() - pad, (int) rect.minY() - pad, (int) rect.maxX() + pad, (int) rect.maxY() + pad, 5, argb(230, threat.color));
        }

        // Info-paneel rechts (niet als het G-menu open is: dan zit het in de weg)
        if (!showPanel) {
            return;
        }
        int panelW = 128;
        int x = w - panelW - 10;
        int y = h / 2 - 50;
        g.fill(x - 4, y - 4, x + panelW, y + 70, argb(60, 0x001018));
        brackets(g, x - 4, y - 4, x + panelW, y + 70, 6, argb(160, CYAN));

        g.text(font, Component.translatable("hud.starktech.target"), x, y, argb(255, CYAN), true);
        y += 11;
        g.text(font, target.getDisplayName(), x, y, argb(255, WHITE), true);
        y += 11;

        float health = target.getHealth();
        float maxHealth = Math.max(1.0f, target.getMaxHealth());
        bar(g, x, y + 2, 70, 4, health / maxHealth, health / maxHealth < 0.3f ? RED : GREEN);
        g.text(font, String.format("%.0f/%.0f", health, maxHealth), x + 75, y, argb(220, WHITE), true);
        y += 11;

        g.text(font, Component.translatable("hud.starktech.armor", target.getArmorValue()), x, y, argb(220, WHITE), true);
        y += 10;
        g.text(font, Component.translatable("hud.starktech.distance", String.format("%.1f", player.distanceTo(target))), x, y, argb(220, WHITE), true);
        y += 10;
        g.text(font, Component.translatable("hud.starktech.threat_level", threat.label()), x, y, argb(255, threat.color), true);
        y += 10;

        ItemStack held = target.getMainHandItem();
        if (!held.isEmpty()) {
            g.text(font, Component.translatable("hud.starktech.holding", held.getHoverName()), x, y, argb(200, WHITE), true);
        }
    }

    /** Rode markeringen boven vijanden (ook door muren heen). */
    private void renderThreatMarkers(GuiGraphicsExtractor g, Font font, Player player) {
        List<LivingEntity> threats = EdithClientState.threats;
        int drawn = 0;
        for (LivingEntity enemy : threats) {
            if (drawn >= 16) {
                break;
            }
            Vec3 top = new Vec3(enemy.getX(), enemy.getBoundingBox().maxY + 0.4, enemy.getZ());
            WorldProjection.ScreenPoint p = projection.project(top);
            if (p == null || !projection.isOnScreen(p, 0)) {
                continue;
            }
            int x = (int) p.x();
            int y = (int) p.y();
            // Een klein pijltje dat naar beneden wijst.
            g.fill(x - 3, y - 4, x + 4, y - 3, argb(230, RED));
            g.fill(x - 2, y - 3, x + 3, y - 2, argb(230, RED));
            g.fill(x - 1, y - 2, x + 2, y - 1, argb(230, RED));
            g.fill(x, y - 1, x + 1, y, argb(230, RED));
            smallText(g, font, Math.round(player.distanceTo(enemy)) + "m", x, y - 11, argb(220, RED));
            drawn++;
        }
    }

    /** Gekleurde ruitjes op de ertsen die de scan heeft gevonden. */
    private void renderOreMarkers(GuiGraphicsExtractor g, Font font, Player player, long now) {
        List<EdithClientState.OreMarker> markers = EdithClientState.oreMarkers;
        // Laatste seconde: langzaam vervagen.
        long left = EdithClientState.oreScanEndMillis - now;
        int baseAlpha = left < 1000 ? (int) (255 * left / 1000.0) : 255;
        int labelled = 0;
        for (EdithClientState.OreMarker marker : markers) {
            WorldProjection.ScreenPoint p = projection.project(Vec3.atCenterOf(marker.pos()));
            if (p == null || !projection.isOnScreen(p, 0)) {
                continue;
            }
            int color = marker.type().color();
            g.pose().pushMatrix();
            g.pose().translate(p.x(), p.y());
            g.pose().rotate((float) (Math.PI / 4));
            g.fill(-3, -3, 3, 3, argb(baseAlpha * 3 / 4, color));
            g.fill(-2, -2, 2, 2, argb(baseAlpha / 3, 0x000000));
            g.pose().popMatrix();

            // Naam + afstand bij de dichtstbijzijnde paar ertsen.
            if (labelled < 6) {
                double dist = Math.sqrt(marker.pos().distToCenterSqr(player.position()));
                String label = marker.type().displayName().getString() + " " + Math.round(dist) + "m";
                smallText(g, font, label, (int) p.x(), (int) p.y() + 5, argb(baseAlpha, color));
                labelled++;
            }
        }
    }

    /** Extra's tijdens inzoomen: donkere randen en een afstandsmeter. */
    private void renderZoom(GuiGraphicsExtractor g, Font font, Player player, int w, int h) {
        int border = Math.min(w, h) / 8;
        int dark = argb(110, 0x000814);
        g.fill(0, 0, w, border, dark);
        g.fill(0, h - border, w, h, dark);
        g.fill(0, border, border, h - border, dark);
        g.fill(w - border, border, w, h - border, dark);
        brackets(g, border, border, w - border, h - border, 30, argb(200, CYAN));

        int cx = w / 2;
        int cy = h / 2;
        g.fill(cx - 20, cy, cx - 6, cy + 1, argb(200, CYAN));
        g.fill(cx + 7, cy, cx + 21, cy + 1, argb(200, CYAN));
        g.fill(cx, cy - 20, cx + 1, cy - 6, argb(200, CYAN));
        g.fill(cx, cy + 7, cx + 1, cy + 21, argb(200, CYAN));

        // Afstandsmeter: hoe ver is het blok waar je naar kijkt?
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(256));
        HitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        String range = hit.getType() == HitResult.Type.MISS ? "---" : String.format("%.1fm", hit.getLocation().distanceTo(eye));
        g.text(font, Component.translatable("hud.starktech.zoom", String.format("%.0f", com.senne.starktech.edith.EdithBalance.ZOOM_FACTOR)), cx + 26, cy + 8, argb(230, CYAN), true);
        g.text(font, Component.translatable("hud.starktech.range", range), cx + 26, cy + 18, argb(230, WHITE), true);
    }

    /** Meldingen van E.D.I.T.H., met een typmachine-effect. */
    private void renderNotifications(GuiGraphicsExtractor g, Font font, int w, long now) {
        EdithClientState.NOTIFICATIONS.removeIf(n -> now - n.startMillis() > EdithClientState.NOTIFICATION_MILLIS);
        int y = 36;
        for (EdithClientState.Notification n : EdithClientState.NOTIFICATIONS) {
            long age = now - n.startMillis();
            int alpha = age > EdithClientState.NOTIFICATION_MILLIS - 1000
                    ? (int) (255 * (EdithClientState.NOTIFICATION_MILLIS - age) / 1000.0)
                    : 255;
            if (alpha < 8) {
                continue;
            }
            String full = n.text().getString();
            int chars = (int) Math.min(full.length(), age / 18);
            String shown = full.substring(0, chars);
            int color = colorFor(n.level());
            String prefix = "E.D.I.T.H. › ";
            int totalWidth = font.width(prefix + full);
            int x = w / 2 - totalWidth / 2;
            // Zou de melding over het paneel links vallen? Schuif hem er dan naast (als dat past).
            int besidePanel = panelRight + 8;
            if (x < besidePanel && besidePanel + totalWidth + 10 <= w) {
                x = besidePanel;
            }
            g.fill(x - 4, y - 2, x + totalWidth + 4, y + 10, argb(alpha / 3, 0x001018));
            g.fill(x - 4, y - 2, x - 3, y + 10, argb(alpha, color));
            g.text(font, prefix, x, y, argb(alpha * 2 / 3, color), true);
            g.text(font, shown, x + font.width(prefix), y, argb(alpha, WHITE), true);
            y += 14;
        }
    }

    /** Opstart-animatie als je de bril opzet. */
    private void renderBoot(GuiGraphicsExtractor g, Font font, int w, int h, long age) {
        float t = (float) age / BOOT_MILLIS;
        // Een scanlijn die van boven naar beneden gaat.
        int lineY = (int) (h * t);
        g.fill(0, lineY, w, lineY + 1, argb(160, CYAN));
        g.fill(0, lineY - 6, w, lineY, argb(30, CYAN));
        // Beugels die van het midden naar buiten schuiven.
        int grow = (int) (Math.min(1.0f, t * 1.5f) * (Math.min(w, h) / 2 - 4));
        brackets(g, w / 2 - grow * w / h, h / 2 - grow, w / 2 + grow * w / h, h / 2 + grow, 22, argb(200, CYAN));

        g.centeredText(font, Component.literal("E.D.I.T.H."), w / 2, h / 2 - 20, argb(255, CYAN));
        g.centeredText(font, Component.translatable("hud.starktech.booting"), w / 2, h / 2 - 8, argb(220, WHITE));
        bar(g, w / 2 - 50, h / 2 + 6, 100, 3, t, CYAN);
    }

    /** Wat je ziet als je een bril van iemand anders opzet. */
    private void renderAccessDenied(GuiGraphicsExtractor g, Font font, ItemStack glasses, int w, int h, long now) {
        int alpha = (int) (170 + 80 * Math.sin(now / 180.0));
        renderFrame(g, w, h, RED, alpha);
        g.fill(0, h / 3 - 14, w, h / 3 + 26, argb(90, 0x200000));
        g.centeredText(font, Component.translatable("hud.starktech.access_denied"), w / 2, h / 3 - 6, argb(255, RED));
        EdithOwner owner = EdithData.getOwner(glasses);
        if (owner != null) {
            g.centeredText(font, Component.translatable("hud.starktech.registered_to", owner.name()), w / 2, h / 3 + 8, argb(220, WHITE));
        }
    }

    // ================================================================ Tekenhulpjes

    /** Vier L-vormige hoekjes (zoals een camera-zoeker). */
    static void brackets(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int len, int color) {
        len = Math.min(len, Math.min((x1 - x0) / 2, (y1 - y0) / 2));
        // linksboven
        g.fill(x0, y0, x0 + len, y0 + 1, color);
        g.fill(x0, y0, x0 + 1, y0 + len, color);
        // rechtsboven
        g.fill(x1 - len, y0, x1, y0 + 1, color);
        g.fill(x1 - 1, y0, x1, y0 + len, color);
        // linksonder
        g.fill(x0, y1 - 1, x0 + len, y1, color);
        g.fill(x0, y1 - len, x0 + 1, y1, color);
        // rechtsonder
        g.fill(x1 - len, y1 - 1, x1, y1, color);
        g.fill(x1 - 1, y1 - len, x1, y1, color);
    }

    /** Een balkje dat voor een deel gevuld is (fraction 0.0 - 1.0). */
    static void bar(GuiGraphicsExtractor g, int x, int y, int width, int height, float fraction, int rgb) {
        fraction = Mth.clamp(fraction, 0.0f, 1.0f);
        g.fill(x, y, x + width, y + height, argb(60, rgb));
        g.fill(x, y, x + Math.round(width * fraction), y + height, argb(230, rgb));
    }

    /** Kleine, gecentreerde tekst (75% van normaal). */
    private static void smallText(GuiGraphicsExtractor g, Font font, String text, int centerX, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(centerX, y);
        g.pose().scale(0.75f, 0.75f);
        g.text(font, text, -font.width(text) / 2, 0, color, true);
        g.pose().popMatrix();
    }

    // ================================================================ Dreigingsniveau

    /** Hoe gevaarlijk is een wezen? Een simpele schatting op basis van soort en levenspunten. */
    enum ThreatLevel {
        NONE("none", GREEN),
        NEUTRAL("neutral", 0xFFE14A),
        LOW("low", ORANGE),
        MEDIUM("medium", 0xFF7A3F),
        HIGH("high", RED),
        EXTREME("extreme", 0xD02BFF),
        UNKNOWN("unknown", WHITE);

        private final String id;
        final int color;

        ThreatLevel(String id, int color) {
            this.id = id;
            this.color = color;
        }

        Component label() {
            return Component.translatable("hud.starktech.threat." + id);
        }

        static ThreatLevel of(LivingEntity entity) {
            if (entity instanceof Player) {
                return UNKNOWN;
            }
            if (entity instanceof Enemy) {
                float max = entity.getMaxHealth();
                if (max >= 100) {
                    return EXTREME;
                }
                if (max >= 40) {
                    return HIGH;
                }
                return max >= 20 ? MEDIUM : LOW;
            }
            return entity instanceof NeutralMob ? NEUTRAL : NONE;
        }
    }

    /** Kleur die bij een soort melding hoort. */
    static int colorFor(EdithMessagePayload.Level level) {
        return switch (level) {
            case BOOT, INFO -> CYAN;
            case WARNING -> ORANGE;
            case ALERT -> RED;
            case COMMS -> GREEN;
        };
    }
}
