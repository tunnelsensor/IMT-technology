package com.senne.imttech.client;

import com.senne.imttech.client.voice.ImtSpeech;
import com.senne.imttech.imt.ImtCooldown;
import com.senne.imttech.network.ImtMessagePayload;
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
 * Wat I.M.T op dit moment aan de speler-kant weet: meldingen, gevonden ertsen,
 * het huidige doelwit, vijanden in de buurt... De HUD tekent dit elke frame.
 *
 * Dit bestaat alleen in jouw eigen spel (de client), niet op de server.
 */
public final class ImtClientState {
    private ImtClientState() {}

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
     * Bril op, maar de opstart-animatie wacht nog: bij het inloggen staat er eerst een laadscherm,
     * en de animatie begint pas als je de wereld echt ziet.
     */
    public static boolean bootPending;
    /**
     * De welkomstzin van de server bij het opzetten ("Welcome back, ..."). De opstart-animatie
     * zegt hem pas als je identiteit bevestigd is (zie BootOverlay), niet meteen. Bij het afzetten
     * van de bril vergeten we hem weer.
     */
    public static @Nullable Component bootGreeting;
    /** Heeft de animatie van deze opstart de zin al gezegd? */
    public static boolean bootGreetingSaid;
    /**
     * Hoofdstuk 96: je draagt je eigen bril, maar de batterij is leeg. Dan is de bril helemaal uit: geen HUD, geen
     * meldingen, geen stem, en geen enkele toets van de bril (ook G niet). Zie ImtClientEvents.onClientTick.
     */
    public static boolean batteryDead;
    /** Wanneer (ms) het uitvalbeeld begon (na haar laatste zin); 0 = geen uitvalbeeld (zie ShutdownOverlay). */
    public static long shutdownStartMillis;
    /**
     * Hoofdstuk 102: de batterij liep leeg terwijl je hem droeg, en I.M.T zegt nog haar laatste zin. Zolang staat de HUD nog
     * (met de rode rand van een lege bril); pas als de zin gedaan is, begint het uitvalbeeld. Wanneer (ms) ze begon; 0 = niet.
     */
    public static long lastWordsStartMillis;
    /** Het volgnummer van die laatste zin bij ImtSpeech (0 = je hoort haar niet, dan lees je hem even). */
    public static long lastWordsNumber;

    // ---------------------------------------------------------------- Meldingen

    public record Notification(Component text, ImtMessagePayload.Level level, long startMillis) {}

    public static final List<Notification> NOTIFICATIONS = new ArrayList<>();
    private static final int MAX_NOTIFICATIONS = 4;
    public static final long NOTIFICATION_MILLIS = 5000;

    /** Een melding van I.M.T: in de HUD, en uitgesproken als haar stem aan staat. */
    public static void addNotification(Component text, ImtMessagePayload.Level level) {
        addNotification(text, level, true);
    }

    /**
     * Idem, maar met speak = false wordt hij alleen getoond (niet uitgesproken).
     *
     * Staat "Voice text" uit (G > Systems), dan komt er GEEN tekst van I.M.T in beeld: ze
     * zegt het alleen. Behalve als haar stem niet geïnstalleerd is: dan zou je niets horen én
     * niets zien, dus dan tonen we het toch.
     */
    public static void addNotification(Component text, ImtMessagePayload.Level level, boolean speak) {
        // Hoofdstuk 96: een lege bril zegt en toont niets meer.
        if (batteryDead) {
            return;
        }
        if (ImtClientConfig.voiceTextInHud() || !ImtSpeech.available()) {
            NOTIFICATIONS.add(new Notification(text, level, Util.getMillis()));
            while (NOTIFICATIONS.size() > MAX_NOTIFICATIONS) {
                NOTIFICATIONS.removeFirst();
            }
        }
        if (speak) {
            ImtSpeech.say(text, level);
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
    /** Wachttijden (in ticks) van alle functies, op de volgorde van ImtCooldown. */
    public static int[] cooldowns = new int[ImtCooldown.COUNT];
    /** Waar de satelliet gaat inslaan, of null. */
    public static @Nullable Vec3 strikeTarget;
    /** Moment (ms) waarop de satelliet inslaat. */
    public static long strikeAtMillis;
    /** Moment (ms) waarop de laatste status binnenkwam; om cooldowns tussendoor af te tellen. */
    public static long statusMillis;

    /** Hoeveel ticks deze functie nog moet wachten (zoals de server het laatst doorgaf). */
    public static int cooldown(ImtCooldown which) {
        int index = which.ordinal();
        return index < cooldowns.length ? cooldowns[index] : 0;
    }

    /** Hoeveel seconden deze functie nog moet wachten (0 = kan nu). */
    public static int secondsLeft(ImtCooldown which) {
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
        if (!menuTabLoaded && ImtClientConfig.SPEC.isLoaded()) {
            menuTab = ImtClientConfig.MENU_TAB.get();
            menuTabLoaded = true;
        }
    }

    /** Wisselt van tabblad en onthoudt de keuze voor de volgende keer. */
    public static void setMenuTab(MenuTab tab) {
        if (tab == menuTab) {
            return;
        }
        menuTab = tab;
        if (ImtClientConfig.SPEC.isLoaded()) {
            ImtClientConfig.MENU_TAB.set(tab);
            ImtClientConfig.SPEC.save();
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

    /**
     * Het brein op het datacenter (8 oktober): hoeveel ticks het extra nadenkt (-1 = geen datacenter,
     * dan werkt er geen brein), en op welk datacenter ("creative" of "" = nergens). Komt met de status.
     */
    public static int brainDelayTicks = -1;
    public static String brainSource = "";
    /**
     * Hoofdstuk 106: hoe je bril met je datacenter verbonden is (het nummer van DatacenterGlasses.Link: 0 = geen nodig,
     * 1 = verbonden, 2 = geen datacenter, 3 = het draait niet), en hoeveel procent van zijn rekenkracht de brillen nu
     * gebruiken. Komt met de status.
     */
    public static int datacenterLink;
    public static int datacenterLoad;
    /**
     * Hoofdstuk 122: de naam en het IP van de kern waarmee je bril verbonden is ("" = met geen enkele). Sindsdien ook
     * datacenterLink 4 = niet verbonden, en (hoofdstuk 123) 5 = het wachtwoord van die kern veranderde: opnieuw verbinden.
     */
    public static String uplinkHost = "";
    public static String uplinkIp = "";

    /** Hoofdstuk 106: kan je bril nu nadenken en bewaren (hij heeft geen datacenter nodig, of het draait)? */
    public static boolean datacenterWorks() {
        return datacenterLink <= 1;
    }

    /** Nummers van wat jou op dit moment aanvalt; de server stuurt ze (zie ImtThreats). */
    public static java.util.List<Integer> threatIds = java.util.List.of();

    // ---------------------------------------------------------------- Fase 3: pings

    /** Een ping van jezelf of een andere I.M.T-gebruiker. */
    public record Ping(String sender, Vec3 pos, long startMillis, long endMillis) {}

    public static final List<Ping> pings = new ArrayList<>();

    /** Alles wissen, bv. als je de wereld verlaat. */
    public static void reset() {
        wearing = false;
        authorized = false;
        operational = false;
        batteryDead = false;
        shutdownStartMillis = 0;
        lastWordsStartMillis = 0;
        lastWordsNumber = 0;
        NOTIFICATIONS.clear();
        oreMarkers = List.of();
        target = null;
        threats = List.of();
        lookTarget = null;
        zooming = false;
        markedIds = Set.of();
        swarmDrones = 0;
        guardDrones = 0;
        cooldowns = new int[ImtCooldown.COUNT];
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
        brainDelayTicks = -1;
        brainSource = "";
        datacenterLink = 0;
        datacenterLoad = 0;
        uplinkHost = "";
        uplinkIp = "";
    }
}
