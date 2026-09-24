package com.senne.starktech.client;

import com.senne.starktech.client.voice.EdithSpeech;
import com.senne.starktech.edith.EdithCooldown;
import com.senne.starktech.network.EdithMessagePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Wat E.D.I.T.H. op dit moment aan de speler-kant weet: meldingen, gevonden ertsen,
 * het huidige doelwit, vijanden in de buurt... De HUD tekent dit elke frame.
 *
 * Dit bestaat alleen in jouw eigen spel (de client), niet op de server.
 */
public final class EdithClientState {
    private EdithClientState() {}

    // ---------------------------------------------------------------- Status van de bril

    /** Draagt de speler de bril? */
    public static boolean wearing;
    /** Is de speler de eigenaar? */
    public static boolean authorized;
    /** Draagt, is eigenaar, en er zit energie in. */
    public static boolean operational;
    /** Moment (in milliseconden) waarop de bril werd opgezet, voor de opstart-animatie. */
    public static long bootStartMillis;
    /**
     * De welkomstzin van de server bij het opzetten ("Welcome back, ..."). De opstart-animatie
     * zegt hem pas als je identiteit bevestigd is (zie BootOverlay), niet meteen.
     */
    public static @Nullable Component bootGreeting;
    /** Wanneer die zin binnenkwam. */
    public static long bootGreetingMillis;
    /** Heeft de animatie van deze opstart de zin al gezegd? */
    public static boolean bootGreetingSaid;

    // ---------------------------------------------------------------- Meldingen

    public record Notification(Component text, EdithMessagePayload.Level level, long startMillis) {}

    public static final List<Notification> NOTIFICATIONS = new ArrayList<>();
    private static final int MAX_NOTIFICATIONS = 4;
    public static final long NOTIFICATION_MILLIS = 5000;

    /** Een melding van E.D.I.T.H.: in de HUD, en uitgesproken als haar stem aan staat. */
    public static void addNotification(Component text, EdithMessagePayload.Level level) {
        addNotification(text, level, true);
    }

    /** Idem, maar met speak = false wordt hij alleen getoond (niet uitgesproken). */
    public static void addNotification(Component text, EdithMessagePayload.Level level, boolean speak) {
        NOTIFICATIONS.add(new Notification(text, level, Util.getMillis()));
        while (NOTIFICATIONS.size() > MAX_NOTIFICATIONS) {
            NOTIFICATIONS.removeFirst();
        }
        if (speak) {
            EdithSpeech.say(text, level);
        }
    }

    // ---------------------------------------------------------------- Ertsen-scan

    public record OreMarker(BlockPos pos, OreType type) {}

    public static List<OreMarker> oreMarkers = List.of();
    public static long oreScanEndMillis;
    public static long oreScanStartMillis;

    public static boolean hasOreScan() {
        return !oreMarkers.isEmpty() && Util.getMillis() < oreScanEndMillis;
    }

    // ---------------------------------------------------------------- Doelwit en vijanden

    /** Het wezen waar je naar kijkt (voor de doelwit-analyse), of null. */
    public static @Nullable LivingEntity target;
    /** Vijanden in de buurt (voor "vijanden door muren"). */
    /** Wat jou nu aanvalt: wezens, maar ook pijlen die onderweg zijn. */
    public static List<Entity> threats = List.of();
    public static int threatRadius;

    /** Het wezen waar je naar kijkt (voor markeren), ook als de doelwit-analyse uit staat. */
    public static @Nullable LivingEntity lookTarget;

    // ---------------------------------------------------------------- Zoom

    public static boolean zooming;
    /**
     * Het beeldveld (FOV) van de laatste frame. Dat onthouden we omdat WorldProjection het nodig
     * heeft om te berekenen waar iets op je scherm staat - ook als je inzoomt.
     */
    public static double fieldOfView = 70.0;

    // ---------------------------------------------------------------- Fase 2: status van de server

    /** Nummers (entity-id's) van de gemarkeerde doelwitten. */
    public static Set<Integer> markedIds = Set.of();
    public static int swarmDrones;
    public static int guardDrones;
    /** Wachttijden (in ticks) van alle functies, op de volgorde van EdithCooldown. */
    public static int[] cooldowns = new int[EdithCooldown.COUNT];
    /** Waar de satelliet gaat inslaan, of null. */
    public static @Nullable Vec3 strikeTarget;
    /** Moment (ms) waarop de satelliet inslaat. */
    public static long strikeAtMillis;
    /** Moment (ms) waarop de laatste status binnenkwam; om cooldowns tussendoor af te tellen. */
    public static long statusMillis;

    /** Hoeveel ticks deze functie nog moet wachten (zoals de server het laatst doorgaf). */
    public static int cooldown(EdithCooldown which) {
        int index = which.ordinal();
        return index < cooldowns.length ? cooldowns[index] : 0;
    }

    /** Hoeveel seconden deze functie nog moet wachten (0 = kan nu). */
    public static int secondsLeft(EdithCooldown which) {
        return secondsLeft(cooldown(which));
    }

    /** Hoeveel seconden een cooldown nog duurt, rekening houdend met de tijd sinds de laatste status. */
    public static int secondsLeft(int cooldownTicks) {
        long elapsedTicks = (Util.getMillis() - statusMillis) / 50;
        return (int) Math.max(0, (cooldownTicks - elapsedTicks + 19) / 20);
    }

    // ---------------------------------------------------------------- Tabblad van het G-menu

    /** Welk tabblad het ronde G-menu toont. Het menu opent weer op het tabblad van de vorige keer. */
    public static MenuTab menuTab = MenuTab.COMBAT;
    private static boolean menuTabLoaded;

    /** Leest het opgeslagen tabblad in (één keer, zodra de instellingen geladen zijn). */
    public static void loadMenuTab() {
        if (!menuTabLoaded && EdithClientConfig.SPEC.isLoaded()) {
            menuTab = EdithClientConfig.MENU_TAB.get();
            menuTabLoaded = true;
        }
    }

    /** Wisselt van tabblad en onthoudt de keuze voor de volgende keer. */
    public static void setMenuTab(MenuTab tab) {
        if (tab == menuTab) {
            return;
        }
        menuTab = tab;
        if (EdithClientConfig.SPEC.isLoaded()) {
            EdithClientConfig.MENU_TAB.set(tab);
            EdithClientConfig.SPEC.save();
        }
    }

    // ---------------------------------------------------------------- Fase 6: spionagedrone

    /** Is er een spionage-opdracht bezig? */
    public static boolean spyActive;
    public static Component spyTarget = Component.empty();
    /** Laatst bekende plek van het doelwit (ook van heel ver weg). */
    public static @Nullable Vec3 spyPos;
    public static ResourceKey<Level> spyDimension = Level.OVERWORLD;
    public static int spyTicksLeft;
    public static float spyHealth;
    public static float spyMaxHealth = 20.0f;
    public static int spyLogLines;
    /** Het laatst ontvangen rapport, zodat je het later nog kunt openen. */
    public static Component spyReportTarget = Component.empty();
    public static List<Component> spyReport = List.of();

    /** Hoeveel tijd de spionage-opdracht nog heeft, als "m:ss". */
    public static String spyTimeLeft() {
        int seconds = spyTicksLeft / 20;
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    // ---------------------------------------------------------------- Fase 10: hack player

    /**
     * Een onderschept privébericht dat nu boven het hoofd van een speler staat.
     *
     * @param entityId  welke speler
     * @param text      wat er gezegd werd
     * @param outgoing  true = hij stuurde het, false = hij kreeg het
     * @param endMillis tot wanneer het blijft staan
     */
    public record HackBubble(int entityId, Component text, boolean outgoing, long endMillis) {}

    public static final List<HackBubble> hackBubbles = new ArrayList<>();
    /** Hoeveel spelers je nu hackt (voor het paneel links). */
    public static int hackedCount;

    /** Zet een nieuw ballonnetje boven een speler (maximaal 3 per speler). */
    public static void addHackBubble(int entityId, Component text, boolean outgoing, long millis) {
        long now = Util.getMillis();
        hackBubbles.removeIf(bubble -> now > bubble.endMillis());
        long sameEntity = hackBubbles.stream().filter(bubble -> bubble.entityId() == entityId).count();
        if (sameEntity >= 3) {
            hackBubbles.stream().filter(bubble -> bubble.entityId() == entityId).findFirst().ifPresent(hackBubbles::remove);
        }
        hackBubbles.add(new HackBubble(entityId, text, outgoing, now + millis));
    }

    // ---------------------------------------------------------------- Fase 7: transport

    /** Hang je op dit moment onder een transportdrone? */
    public static boolean flying;
    /** Nummers van wat jou op dit moment aanvalt; de server stuurt ze (zie EdithThreats). */
    public static java.util.List<Integer> threatIds = java.util.List.of();

    // ---------------------------------------------------------------- Fase 3: pings

    /** Een ping van jezelf of een andere E.D.I.T.H.-gebruiker. */
    public record Ping(String sender, Vec3 pos, long startMillis, long endMillis) {}

    public static final List<Ping> pings = new ArrayList<>();

    /** Alles wissen, bv. als je de wereld verlaat. */
    public static void reset() {
        wearing = false;
        authorized = false;
        operational = false;
        NOTIFICATIONS.clear();
        oreMarkers = List.of();
        target = null;
        threats = List.of();
        lookTarget = null;
        zooming = false;
        markedIds = Set.of();
        swarmDrones = 0;
        guardDrones = 0;
        cooldowns = new int[EdithCooldown.COUNT];
        strikeTarget = null;
        pings.clear();
        spyActive = false;
        spyTarget = Component.empty();
        spyPos = null;
        spyTicksLeft = 0;
        spyLogLines = 0;
        spyReport = List.of();
        flying = false;
        threatIds = java.util.List.of();
        hackBubbles.clear();
        hackedCount = 0;
    }
}
