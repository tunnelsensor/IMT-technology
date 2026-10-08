package com.senne.starktech.client;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.client.hud.PowerReadout;
import com.mojang.blaze3d.platform.InputConstants;
import com.senne.starktech.client.voice.VoiceCommandsScreen;
import com.senne.starktech.client.voice.VoiceControl;
import com.senne.starktech.client.voice.VoiceFeature;
import com.senne.starktech.client.voice.VoiceInstaller;
import com.senne.starktech.edith.EdithAction;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.suit.SuitBalance;
import com.senne.starktech.edith.EdithCooldown;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithFeature;
import com.senne.starktech.edith.EdithOwner;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.satellite.SatelliteMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;

/**
 * Het ronde E.D.I.T.H.-menu (toets G), in het midden van je scherm.
 *
 * Het menu is opgedeeld in tabbladen (zie MenuTab: Combat, Satellite & Arsenal, Drones, ...). Met het
 * scrollwiel draai je naar het volgende tabblad; in het midden van de cirkel zie je op welk
 * tabblad je zit. Zolang het menu open is, verdwijnt de HUD links (en het kompas en de
 * meldingen), zodat niets de cirkel in de weg zit (zie EdithHud).
 *
 * Bediening:
 * - G ingedrukt houden, scrollen naar het juiste tabblad, muis naar een optie, G loslaten
 * - of: kort op G tikken (het menu blijft open), scrollen en op een optie klikken
 * - pijltjes links/rechts of de cijfers 1-9 en 0 wisselen ook van tabblad
 * - sluiten: Esc, nog eens G, of in het midden klikken
 */
public class EdithMenuScreen extends Screen {
    private static final int BOX_W = 88;
    private static final int BOX_H = 26;
    /** Binnen deze afstand van het midden is niets geselecteerd. */
    private static final int DEAD_ZONE = 18;
    /** Zo lang (ms) moet je G vasthouden voordat loslaten meteen kiest. Korter = tikken = menu blijft open. */
    private static final long HOLD_MILLIS = 250;
    /** Hoe lang (ms) het draai-effect duurt als je van tabblad wisselt. */
    private static final long TURN_MILLIS = 180;
    private static final int GREY = 0x8A9BA0;

    /**
     * Eén optie in de cirkel.
     *
     * @param label  de naam
     * @param status wat eronder staat (AAN/UIT, kosten, wachttijd...)
     * @param choose wat er gebeurt als je hem kiest (nadat het menu gesloten is)
     */
    private record Option(Component label, Function<ItemStack, Status> status, Runnable choose) {}

    private record Status(Component text, int color) {}

    // ---------------------------------------------------------------- De opties per tabblad (met de klok mee vanaf boven)

    /** Tabblad Combat: wat je zelf doet, met het harnas en het schild. */
    private static final List<Option> COMBAT = List.of(
            // Aan/uit: vuren doe je daarna met de aanvalsknop.
            new Option(Component.translatable(EdithAction.REPULSOR.translationKey()),
                    glasses -> RepulsorControl.isArmed()
                            ? new Status(Component.translatable("screen.starktech.on"), HudStyle.GREEN)
                            : new Status(Component.translatable("screen.starktech.cost", SuitBalance.REPULSOR_COST), GREY),
                    RepulsorControl::toggle),
            action(EdithAction.ROCKETS),
            action(EdithAction.TOGGLE_SHIELD));

    /** Tabblad Satellite & Arsenal: de drie aanvallen apart, en de schermen van satelliet en arsenaal. */
    private static final List<Option> SATELLITE = List.of(
            action(EdithAction.STRIKE_PRECISION),
            action(EdithAction.STRIKE_BOMBARDMENT),
            action(EdithAction.STRIKE_SWEEP),
            // Het satellietscherm: upgraden en zien wat hij kan.
            open("screen.starktech.menu.satellite", HudStyle.ORANGE, SatelliteScreen::new),
            open("screen.starktech.menu.arsenal", HudStyle.CYAN, ArsenalScreen::new));

    /** Tabblad Drones: aanvallen en beschermen. */
    private static final List<Option> DRONES = List.of(
            action(EdithAction.LAUNCH_DRONES),
            action(EdithAction.DRONE_STRIKE),
            action(EdithAction.TOGGLE_GUARD_DRONES),
            action(EdithAction.DRONE_FORMATION),
            action(EdithAction.STAND_DOWN),
            action(EdithAction.CLEAR_MARKS));

    /** Tabblad Recon drones: kijken zonder dat iemand het merkt. */
    private static final List<Option> RECON = List.of(
            action(EdithAction.DRONE_CAMERA),
            action(EdithAction.SPY_DRONE),
            action(EdithAction.SPY_VIEW),
            action(EdithAction.SPY_REPORT));

    /** Tabblad Illusie: wat de cloakingdrones projecteren. */
    private static final List<Option> ILLUSION = List.of(
            action(EdithAction.ILLUSION_WALL),
            action(EdithAction.ILLUSION_ARMY),
            action(EdithAction.ILLUSION_CLOAK),
            action(EdithAction.ILLUSION_STOP));

    /** Tabblad Tech: dingen in je wereld aansturen. */
    private static final List<Option> TECH = List.of(
            open("screen.starktech.menu.redstone", HudStyle.ORANGE, RelayListScreen::new),
            open("screen.starktech.menu.music", HudStyle.GREEN, MusicScreen::new),
            action(EdithAction.HACK_PLAYER),
            action(EdithAction.HACK_STOP));

    /** Tabblad Transport: laat je vervoeren of stuur spullen weg. */
    private static final List<Option> TRANSPORT = List.of(
            action(EdithAction.TRANSPORT_RIDE),
            action(EdithAction.TRANSPORT_AUTOPILOT),
            action(EdithAction.CARGO_DRONE),
            action(EdithAction.CARRY_ENTITY));

    /** Tabblad Information: wat de bril je laat zien. */
    private static final List<Option> INFORMATION = List.of(
            action(EdithAction.ORE_SCAN),
            action(EdithAction.TOGGLE_THREAT_SCAN),
            action(EdithAction.TOGGLE_TARGET_ANALYSIS),
            // Verborgen kabels door muren heen zien. Dit is een instelling van jouw eigen spel
            // (net als "Voice text"), dus er gaat niets naar de server.
            new Option(Component.translatable("screen.starktech.menu.cable_xray"),
                    glasses -> EdithClientConfig.cableXray()
                            ? new Status(Component.translatable("screen.starktech.on"), HudStyle.GREEN)
                            : new Status(Component.translatable("screen.starktech.off"), GREY),
                    EdithClientEvents::toggleCableXray),
            // De rand van je zones (en die van wie jou vertrouwt). Ook een instelling van je eigen spel.
            new Option(Component.translatable("screen.starktech.menu.zone_border"),
                    glasses -> EdithClientConfig.zoneBorder()
                            ? new Status(Component.translatable("screen.starktech.on"), HudStyle.GREEN)
                            : new Status(Component.translatable("screen.starktech.off"), GREY),
                    EdithClientEvents::toggleZoneBorder));

    private static final List<Option> NAVIGATION = List.of(
            open("screen.starktech.menu.navigation", HudStyle.CYAN, NavigationScreen::new),
            new Option(Component.translatable("screen.starktech.menu.ping"),
                    glasses -> new Status(Component.translatable("screen.starktech.menu.here"), HudStyle.GREEN), EdithClientEvents::placePing),
            open("screen.starktech.menu.comms", HudStyle.GREEN, CommsScreen::new));

    private static final List<Option> SYSTEMS = List.of(
            action(EdithAction.AUTHORIZE_TRANSFER),
            voice(VoiceFeature.SPEECH),
            voice(VoiceFeature.COMMANDS),
            new Option(Component.translatable("screen.starktech.menu.brain"), glasses -> brainStatus(),
                    () -> Minecraft.getInstance().setScreen(new BrainScreen())),
            new Option(Component.translatable("screen.starktech.menu.voice_edit"),
                    glasses -> new Status(Component.translatable("screen.starktech.menu.edit"), HudStyle.CYAN),
                    () -> Minecraft.getInstance().setScreen(new VoiceCommandsScreen(null))),
            // Staat de tekst van wat jij vraagt en wat E.D.I.T.H. antwoordt in de HUD?
            new Option(Component.translatable("screen.starktech.menu.voice_text"),
                    glasses -> EdithClientConfig.voiceTextInHud()
                            ? new Status(Component.translatable("screen.starktech.on"), HudStyle.GREEN)
                            : new Status(Component.translatable("screen.starktech.off"), GREY),
                    EdithClientConfig::toggleVoiceTextInHud),
            action(EdithAction.TOGGLE_NIGHT_VISION));

    private static List<Option> optionsOf(MenuTab tab) {
        return switch (tab) {
            case COMBAT -> COMBAT;
            case SATELLITE -> SATELLITE;
            case DRONES -> DRONES;
            case RECON -> RECON;
            case TRANSPORT -> TRANSPORT;
            case TECH -> TECH;
            case ILLUSION -> ILLUSION;
            case INFORMATION -> INFORMATION;
            case NAVIGATION -> NAVIGATION;
            case SYSTEMS -> SYSTEMS;
        };
    }

    /** Een optie die een E.D.I.T.H.-actie naar de server stuurt. */
    private static Option action(EdithAction action) {
        return new Option(Component.translatable(action.translationKey()), glasses -> statusOf(action, glasses),
                () -> EdithClientEvents.sendAction(action));
    }

    /** Een optie die een ander E.D.I.T.H.-scherm opent (navigatie, comms). */
    private static Option open(String key, int color, Supplier<Screen> screen) {
        Status status = new Status(Component.translatable("screen.starktech.menu.open"), color);
        return new Option(Component.translatable(key), glasses -> status,
                () -> EdithClientEvents.openIfAuthorized(Minecraft.getInstance(), screen.get()));
    }

    /** Stem of spraakbesturing: eerst installeren (downloaden), daarna aan/uit zetten. */
    private static Option voice(VoiceFeature feature) {
        return new Option(feature.menuLabel(), glasses -> voiceStatus(feature), () -> VoiceControl.choose(feature));
    }

    /** Welk brein ze gebruikt (of hoe ver de download van het lokale brein is). */
    private static Status brainStatus() {
        if (VoiceInstaller.state(VoiceFeature.BRAIN) == VoiceInstaller.State.INSTALLING) {
            return new Status(Component.literal(VoiceInstaller.percent(VoiceFeature.BRAIN) + "%"), HudStyle.ORANGE);
        }
        com.senne.starktech.client.voice.BrainMode mode = com.senne.starktech.client.voice.EdithBrain.mode();
        return new Status(Component.translatable(mode.translationKey()),
                mode == com.senne.starktech.client.voice.BrainMode.DICTIONARY ? GREY : HudStyle.CYAN);
    }

    private static Status voiceStatus(VoiceFeature feature) {
        return switch (VoiceInstaller.state(feature)) {
            case UNSUPPORTED -> new Status(Component.translatable("screen.starktech.voice.unsupported"), GREY);
            case NOT_INSTALLED -> new Status(Component.translatable("screen.starktech.voice.download", feature.missingSize()), HudStyle.CYAN);
            case INSTALLING -> new Status(Component.literal(VoiceInstaller.percent(feature) + "%"), HudStyle.ORANGE);
            case FAILED -> new Status(Component.translatable("screen.starktech.voice.retry"), HudStyle.RED);
            case INSTALLED -> feature.isEnabled()
                    ? new Status(Component.translatable("screen.starktech.on"), HudStyle.GREEN)
                    : new Status(Component.translatable("screen.starktech.off"), GREY);
        };
    }

    // ---------------------------------------------------------------- Toestand van het menu

    private final long openedMillis = Util.getMillis();
    /** Is G sinds het openen al eens losgelaten? Pas daarna sluit een nieuwe druk op G het menu. */
    private boolean menuKeyReleased;
    /** De optie waar de muis naar wijst, of -1. */
    private int hovered = -1;
    /** Moment (ms) van de laatste tabbladwissel en de richting (+1 of -1), voor het draai-effect. */
    private long tabChangedMillis;
    private int tabDirection;
    /** Touchpads scrollen in kleine stapjes; die tellen we op tot een hele stap. */
    private double scrollBuffer;

    public EdithMenuScreen() {
        super(Component.translatable("screen.starktech.menu"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- Tekenen

    /** Geen wazige achtergrond: alleen een lichte verduistering, zodat je de wereld blijft zien. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);
        int cx = width / 2;
        int cy = height / 2;
        MenuTab tab = EdithClientState.menuTab;
        List<Option> options = optionsOf(tab);
        hovered = optionAt(mouseX, mouseY);
        ItemStack glasses = glasses();

        // Draai-effect bij het wisselen: de opties draaien vanuit de scrollrichting op hun plek en worden zichtbaar.
        float t = Math.min(1.0f, (Util.getMillis() - tabChangedMillis) / (float) TURN_MILLIS);
        float turn = (1.0f - t) * (1.0f - t) * -0.6f * tabDirection;
        int fade = (int) (60 + 195 * t);

        renderCenter(g, cx, cy, tab, t);

        int rx = radiusX();
        int ry = radiusY();
        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            double angle = angleOf(i, options.size()) + turn;
            int bx = cx + (int) (Math.cos(angle) * rx);
            int by = cy + (int) (Math.sin(angle) * ry);
            boolean isHovered = i == hovered;

            // Stippellijn van het midden naar de optie
            for (double d = 0.35; d < 0.75; d += 0.05) {
                int px = cx + (int) (Math.cos(angle) * rx * d);
                int py = cy + (int) (Math.sin(angle) * ry * d);
                g.fill(px, py, px + 1, py + 1, HudStyle.argb(isHovered ? fade : fade * 110 / 255, HudStyle.CYAN));
            }

            int x0 = bx - BOX_W / 2;
            int y0 = by - BOX_H / 2;
            g.fill(x0, y0, x0 + BOX_W, y0 + BOX_H, HudStyle.argb((isHovered ? 150 : 90) * fade / 255, isHovered ? 0x0B4A5E : 0x001018));
            HudStyle.brackets(g, x0, y0, x0 + BOX_W, y0 + BOX_H, 5, HudStyle.argb((isHovered ? 255 : 150) * fade / 255, HudStyle.CYAN));

            g.drawCenteredString(font, option.label(), bx, y0 + 4, HudStyle.argb(fade, HudStyle.WHITE));
            Status status = option.status().apply(glasses);
            g.drawCenteredString(font, status.text(), bx, y0 + 15, HudStyle.argb(fade * 230 / 255, status.color()));
        }
    }

    /**
     * Het midden van de cirkel: op welk tabblad je zit, met een puntje per tabblad eronder.
     * Op het tabblad Systemen staat hier ook de info over de bril.
     */
    private void renderCenter(GuiGraphics g, int cx, int cy, MenuTab tab, float t) {
        // Het kader groeit mee met een lange naam zoals "SATELLITE & ARSENAL".
        int half = Math.max(48, font.width(tab.label()) / 2 + 16);
        HudStyle.brackets(g, cx - half, cy - 20, cx + half, cy + 20, 6, HudStyle.argb(220, HudStyle.CYAN));
        g.drawCenteredString(font, "E.D.I.T.H.", cx, cy - 15, HudStyle.argb(200, HudStyle.CYAN));

        // Naam van het tabblad, schuift even mee in de scrollrichting.
        int slide = (int) ((1.0f - t) * 10 * tabDirection);
        g.drawCenteredString(font, tab.label(), cx + slide, cy - 4, HudStyle.argb(255, HudStyle.WHITE));
        g.drawString(font, "‹", cx - half + 4, cy - 4, HudStyle.argb(150, HudStyle.CYAN), false);
        g.drawString(font, "›", cx + half - 8, cy - 4, HudStyle.argb(150, HudStyle.CYAN), false);

        // Een puntje per tabblad; het huidige is gevuld.
        MenuTab[] tabs = MenuTab.values();
        int dotsX = cx - (tabs.length * 7 - 2) / 2;
        for (int i = 0; i < tabs.length; i++) {
            int dx = dotsX + i * 7;
            g.fill(dx, cy + 9, dx + 5, cy + 14, HudStyle.argb(tabs[i] == tab ? 255 : 70, HudStyle.CYAN));
        }

        int y = cy + 26;
        if (tab == MenuTab.SYSTEMS) {
            for (Component line : systemInfo(glasses())) {
                g.drawCenteredString(font, line, cx, y, HudStyle.argb(220, HudStyle.WHITE));
                y += 11;
            }
            y += 4;
        }
        g.drawCenteredString(font, Component.translatable("screen.starktech.menu.hint"), cx, y, HudStyle.argb(140, HudStyle.WHITE));
    }

    /** Info over de bril voor het tabblad Systemen: energie, verbruik en eigenaar. */
    private static List<Component> systemInfo(ItemStack glasses) {
        List<Component> lines = new ArrayList<>();
        if (glasses.isEmpty()) {
            return lines;
        }
        int energy = EdithData.getEnergy(glasses);
        int drain = PowerReadout.drainPerSecond(glasses);
        lines.add(Component.translatable("hud.starktech.sys.energy", energy, EdithData.getMaxEnergy(glasses)));
        lines.add(Component.translatable("hud.starktech.sys.drain", drain, PowerReadout.timeLeft(energy, drain)));
        EdithOwner owner = EdithData.getOwner(glasses);
        if (owner != null) {
            lines.add(Component.translatable("hud.starktech.sys.owner", owner.name()));
        }
        return lines;
    }

    /** Wat er onder een actie staat: AAN/UIT, de kosten, een wachttijd of de overdracht-status. */
    private static Status statusOf(EdithAction action, ItemStack glasses) {
        EdithFeature feature = action.toggles();
        if (feature != null) {
            boolean on = !glasses.isEmpty() && EdithData.isEnabled(glasses, feature);
            return new Status(Component.translatable(on ? "screen.starktech.on" : "screen.starktech.off"), on ? HudStyle.GREEN : GREY);
        }
        return switch (action) {
            case ORE_SCAN -> costOrCooldown(EdithBalance.ORE_SCAN_COST, EdithClientState.cooldown(EdithCooldown.ORE_SCAN));
            case LAUNCH_DRONES -> costOrCooldown(EdithBalance.SWARM_COST, EdithClientState.cooldown(EdithCooldown.SWARM));
            case DRONE_STRIKE -> costOrCooldown(EdithBalance.STRIKE_DRONE_COST, EdithClientState.cooldown(EdithCooldown.STRIKE));
            case ROCKETS -> costOrCooldown(SuitBalance.ROCKET_COST, EdithClientState.cooldown(EdithCooldown.ROCKETS));
            case SATELLITE_STRIKE -> satelliteStatus(SatelliteMode.PRECISION);
            case STRIKE_PRECISION -> satelliteStatus(SatelliteMode.PRECISION);
            case STRIKE_BOMBARDMENT -> satelliteStatus(SatelliteMode.BOMBARDMENT);
            case STRIKE_SWEEP -> satelliteStatus(SatelliteMode.SWEEP);
            case DRONE_FORMATION -> new Status(Component.translatable(
                    EdithData.getFormation(glasses).translationKey()), HudStyle.CYAN);
            case CLEAR_MARKS -> new Status(Component.translatable("screen.starktech.marked_count", EdithClientState.markedIds.size()),
                    EdithClientState.markedIds.isEmpty() ? GREY : HudStyle.ORANGE);
            case TRANSPORT_RIDE, TRANSPORT_AUTOPILOT -> EdithClientState.flying
                    ? new Status(Component.translatable("screen.starktech.transport.land"), HudStyle.ORANGE)
                    : costOrCooldown(EdithBalance.TRANSPORT_COST, EdithClientState.cooldown(EdithCooldown.TRANSPORT));
            case CARGO_DRONE, CARRY_ENTITY ->
                    costOrCooldown(EdithBalance.TRANSPORT_COST, EdithClientState.cooldown(EdithCooldown.TRANSPORT));
            case ILLUSION_WALL, ILLUSION_ARMY, ILLUSION_CLOAK ->
                    new Status(Component.translatable("screen.starktech.cost", EdithBalance.ILLUSION_COST), HudStyle.CYAN);
            case ILLUSION_STOP -> com.senne.starktech.client.illusion.IllusionRenderer.count() > 0
                    ? new Status(Component.translatable("screen.starktech.illusion.count",
                            com.senne.starktech.client.illusion.IllusionRenderer.count()), HudStyle.ORANGE)
                    : new Status(Component.translatable("screen.starktech.none"), GREY);
            case HACK_PLAYER -> EdithClientState.hackedCount > 0
                    ? new Status(Component.translatable("screen.starktech.hack.count", EdithClientState.hackedCount), HudStyle.RED)
                    : new Status(Component.translatable("screen.starktech.hack.look"), HudStyle.CYAN);
            case HACK_STOP -> EdithClientState.hackedCount > 0
                    ? new Status(Component.translatable("screen.starktech.hack.stop"), HudStyle.ORANGE)
                    : new Status(Component.translatable("screen.starktech.none"), GREY);
            case SPY_DRONE -> EdithClientState.spyActive
                    ? new Status(Component.translatable("screen.starktech.spy.recall"), HudStyle.PURPLE)
                    : costOrCooldown(EdithBalance.SPY_COST, EdithClientState.cooldown(EdithCooldown.SPY));
            case SPY_VIEW -> {
                if (DroneCamera.isWatchingOnly()) {
                    yield new Status(Component.translatable("screen.starktech.menu.return"), HudStyle.ORANGE);
                }
                yield EdithClientState.spyActive
                        ? new Status(Component.translatable("screen.starktech.menu.watch"), HudStyle.PURPLE)
                        : new Status(Component.translatable("screen.starktech.none"), GREY);
            }
            case SPY_REPORT -> EdithClientState.spyLogLines > 0 || !EdithClientState.spyReport.isEmpty()
                    ? new Status(Component.translatable("screen.starktech.spy.lines",
                            Math.max(EdithClientState.spyLogLines, EdithClientState.spyReport.size())), HudStyle.PURPLE)
                    : new Status(Component.translatable("screen.starktech.none"), GREY);
            case STAND_DOWN -> {
                int drones = EdithClientState.swarmDrones + EdithClientState.guardDrones;
                yield drones > 0 || EdithClientState.strikeTarget != null
                        ? new Status(Component.translatable("screen.starktech.stand_down.count", drones), HudStyle.ORANGE)
                        : new Status(Component.translatable("screen.starktech.stand_down.calm"), GREY);
            }
            case DRONE_CAMERA -> DroneCamera.isActive()
                    ? new Status(Component.translatable("screen.starktech.menu.return"), HudStyle.ORANGE)
                    : new Status(Component.translatable("screen.starktech.cost", EdithBalance.SCOUT_COST), HudStyle.CYAN);
            case AUTHORIZE_TRANSFER -> {
                EdithOwner owner = glasses.isEmpty() ? null : EdithData.getOwner(glasses);
                boolean pending = owner != null && owner.transferPending();
                yield new Status(Component.translatable(pending ? "screen.starktech.transfer.pending" : "screen.starktech.transfer.locked"),
                        pending ? HudStyle.ORANGE : HudStyle.GREEN);
            }
            default -> new Status(Component.empty(), HudStyle.WHITE);
        };
    }

    /**
     * Onder een van de drie satellietaanvallen: LOCKED als je die aanval nog niet vrijspeelde;
     * bij de schuivende laser met een beginpunt: dat je nu het eind moet kiezen; anders de
     * kosten of de wachttijd.
     */
    private static Status satelliteStatus(SatelliteMode mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.getData(com.senne.starktech.registry.ModAttachments.SATELLITE).unlocked(mode)) {
            return new Status(Component.translatable("screen.starktech.locked"), GREY);
        }
        if (mode == SatelliteMode.SWEEP && EdithClientState.pings.stream()
                .anyMatch(ping -> ping.sender().equals(com.senne.starktech.network.PingMarkerPayload.SWEEP_START))) {
            return new Status(Component.translatable("screen.starktech.sweep.pick_end"), HudStyle.ORANGE);
        }
        return costOrCooldown(mode.cost(), EdithClientState.cooldown(EdithCooldown.SATELLITE));
    }

    /** Kosten als de functie klaar is, anders de resterende wachttijd. */
    private static Status costOrCooldown(int cost, int cooldownTicks) {
        int seconds = EdithClientState.secondsLeft(cooldownTicks);
        return seconds > 0
                ? new Status(Component.literal(seconds + "s"), HudStyle.ORANGE)
                : new Status(Component.translatable("screen.starktech.cost", cost), HudStyle.CYAN);
    }

    // ---------------------------------------------------------------- Waar wijst de muis?

    /** De opties liggen op een ovaal; die past zich aan de schermgrootte aan. */
    private int radiusX() {
        return Math.min(170, width / 2 - BOX_W / 2 - 4);
    }

    private int radiusY() {
        return Math.min(105, height / 2 - BOX_H / 2 - 4);
    }

    /** De hoek van optie i (van count): optie 0 staat bovenaan, de rest met de klok mee. */
    private static double angleOf(int index, int count) {
        return -Math.PI / 2 + index * (2 * Math.PI / count);
    }

    /** Welke optie ligt in de richting van de muis? -1 als de muis in het midden staat. */
    private int optionAt(double mouseX, double mouseY) {
        int count = optionsOf(EdithClientState.menuTab).size();
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
            return -1;
        }
        double sector = 2 * Math.PI / count;
        // Hoek van de muis op het ovaal, gedraaid zodat optie 0 (boven) rond 0 ligt.
        double angle = Math.atan2(dy / radiusY(), dx / radiusX()) + Math.PI / 2 + sector / 2;
        angle = Mth.positiveModulo(angle, 2 * Math.PI);
        return (int) (angle / sector) % count;
    }

    // ---------------------------------------------------------------- Kiezen

    /** Naar het volgende (+1) of vorige (-1) tabblad. */
    private void turn(int direction) {
        selectTab(EdithClientState.menuTab.step(direction), direction);
    }

    private void selectTab(MenuTab tab, int direction) {
        if (tab == EdithClientState.menuTab) {
            return;
        }
        EdithClientState.setMenuTab(tab);
        tabChangedMillis = Util.getMillis();
        tabDirection = direction;
        EdithClientEvents.playUi(ModSounds.EDITH_SELECT.get(), 1.8f);
    }

    /** Sluit het menu en voert optie index uit (bij -1 alleen sluiten). */
    private void choose(int index) {
        onClose();
        List<Option> options = optionsOf(EdithClientState.menuTab);
        if (index >= 0 && index < options.size()) {
            EdithClientEvents.playUi(ModSounds.EDITH_SELECT.get(), 1.4f);
            options.get(index).choose().run();
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scrollBuffer += scrollY;
        while (scrollBuffer >= 1) {
            scrollBuffer -= 1;
            turn(-1); // omhoog scrollen = vorig tabblad
        }
        while (scrollBuffer <= -1) {
            scrollBuffer += 1;
            turn(1); // omlaag scrollen = volgend tabblad
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (EdithKeys.MENU.matches(keyCode, scanCode)) {
            // Zolang je G nog vasthoudt van het openen, komen er "herhaal"-drukken binnen; die negeren we.
            if (menuKeyReleased) {
                onClose();
            }
            return true;
        }
        int key = keyCode;
        MenuTab[] tabs = MenuTab.values();
        if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT) {
            turn(key == InputConstants.KEY_RIGHT ? 1 : -1);
            return true;
        }
        // 1 tot 9 zijn de eerste negen tabbladen, 0 het tiende.
        int index = key == InputConstants.KEY_0 ? 9 : key - InputConstants.KEY_1;
        if (key >= InputConstants.KEY_0 && key <= InputConstants.KEY_9 && index < tabs.length) {
            MenuTab tab = tabs[index];
            selectTab(tab, tab.ordinal() > EdithClientState.menuTab.ordinal() ? 1 : -1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers); // Esc sluit het menu
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (EdithKeys.MENU.matches(keyCode, scanCode)) {
            boolean firstRelease = !menuKeyReleased;
            menuKeyReleased = true;
            // G vastgehouden en losgelaten: kies wat onder de muis staat (of sluit als de muis in het midden staat).
            // Alleen kort getikt? Dan blijft het menu open en kun je klikken.
            if (firstRelease && Util.getMillis() - openedMillis >= HOLD_MILLIS) {
                choose(hovered);
            }
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        choose(optionAt(mouseX, mouseY));
        return true;
    }

    @Override
    public void tick() {
        // Bril afgezet terwijl het menu open is? Dan sluiten.
        if (!EdithClientState.authorized) {
            onClose();
            return;
        }
        // Heel kort op G getikt (al los voordat het menu open was)? Dan telt dat ook als losgelaten.
        if (!menuKeyReleased && !isMenuKeyDown()) {
            menuKeyReleased = true;
        }
    }

    /** Wordt de G-toets (of waar je hem op gezet hebt) nu echt ingedrukt? */
    private static boolean isMenuKeyDown() {
        InputConstants.Key key = EdithKeys.MENU.getKey();
        // In 1.21.1 moet je erbij zeggen welk venster je bedoelt.
        return key.getType() == InputConstants.Type.KEYSYM
                && InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue());
    }

    private static ItemStack glasses() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? ItemStack.EMPTY : EdithData.getWornGlasses(mc.player);
    }
}
