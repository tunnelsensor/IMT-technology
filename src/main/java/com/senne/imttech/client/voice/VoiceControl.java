package com.senne.imttech.client.voice;

import com.senne.imttech.client.CommsScreen;
import com.senne.imttech.client.DroneCamera;
import com.senne.imttech.client.ImtClientEvents;
import com.senne.imttech.client.ImtClientState;
import com.senne.imttech.client.ImtKeys;
import com.senne.imttech.client.music.ImtMusic;
import com.senne.imttech.client.NavigationScreen;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtAction;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtFeature;
import com.senne.imttech.nav.ImtWaypoints;
import com.senne.imttech.nav.Waypoint;
import com.senne.imttech.network.CommsPayload;
import com.senne.imttech.network.RelayActionPayload;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.network.WaypointActionPayload;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModSounds;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Verbindt de stem-functies met de rest van de mod:
 * - de spraaktoets (standaard Z): ingedrukt houden = praten, loslaten = uitvoeren
 * - uitvoeren wat je zei, met het woordenboek (zie VoiceCommandStore en VoiceMatcher)
 * - installeren en aan/uit zetten via het G-menu (tabblad Systemen)
 */
public final class VoiceControl {
    private VoiceControl() {}

    /** De laatste zin die I.M.T niet kende; het bewerkscherm kan hem toevoegen aan een commando. */
    private static String lastUnknown = "";

    public static String lastUnknown() {
        return lastUnknown;
    }

    // ---------------------------------------------------------------- Elke tick

    public static void tick(Minecraft mc) {
        VoiceInstaller.removeObsolete();
        boolean pressed = false;
        while (ImtKeys.PUSH_TO_TALK.consumeClick()) {
            pressed = true;
        }
        if (pressed && !ImtAssistant.isBusy()) {
            startListening();
        }
        // Toets los, een scherm geopend of de bril af? Dan stoppen met opnemen.
        if (ImtListener.isListening()
                && (!ImtKeys.PUSH_TO_TALK.isDown() || mc.screen != null || !ImtClientState.authorized)) {
            ImtListener.stop();
        }
        // Het oor alvast opstarten zodra je de bril draagt, dan reageert ze meteen de eerste keer.
        if (ImtClientState.wearing && VoiceFeature.COMMANDS.isEnabled()
                && VoiceInstaller.state(VoiceFeature.COMMANDS) == VoiceInstaller.State.INSTALLED) {
            ImtAssistant.preload();
        }
        // Het brein: opwarmen, en eventueel uit zichzelf iets zeggen (het kijkt zelf of het kan).
        ImtBrain.tick(mc);
    }

    private static void startListening() {
        if (!ImtClientState.wearing) {
            return; // zonder bril doet de toets niets
        }
        // Praat ze nog? Dan zwijgt ze nu: jij bent aan het woord.
        ImtSpeech.stopSpeaking();
        if (!ImtClientState.authorized || !ImtClientState.operational) {
            ImtClientEvents.playUi(ModSounds.IMT_DENIED.get(), 1.0f);
            return;
        }
        // Zonder datacenter is er geen brein: dan hoeft ze ook niet te luisteren (8 oktober).
        if (!ImtBrain.hasDatacenter()) {
            noDatacenter();
            return;
        }
        switch (VoiceInstaller.state(VoiceFeature.COMMANDS)) {
            case UNSUPPORTED -> tell("imt.imttech.voice.unsupported", ImtMessagePayload.Level.WARNING);
            case NOT_INSTALLED, FAILED -> tell("imt.imttech.voice.not_installed", ImtMessagePayload.Level.WARNING);
            case INSTALLING -> tell(Component.translatable("imt.imttech.voice.installing",
                    VoiceFeature.COMMANDS.spokenName(), VoiceInstaller.percent(VoiceFeature.COMMANDS)), ImtMessagePayload.Level.INFO);
            case INSTALLED -> {
                if (!VoiceFeature.COMMANDS.isEnabled()) {
                    tell("imt.imttech.voice.disabled", ImtMessagePayload.Level.WARNING);
                } else {
                    ImtClientEvents.playUi(ModSounds.IMT_SELECT.get(), 1.6f);
                    ImtListener.start();
                }
            }
        }
    }

    // ---------------------------------------------------------------- Wat je zei

    static void onHeardNothing() {
        tell("imt.imttech.voice.nothing_heard", ImtMessagePayload.Level.INFO);
    }

    /** Je praatte geen Engels: dat verstaat ze niet (zie EnglishOnly). */
    static void onNotEnglish() {
        tell("imt.imttech.voice.english_only", ImtMessagePayload.Level.WARNING);
    }

    static void onError(String reason) {
        if (reason.equals("mic")) {
            tell("imt.imttech.voice.mic_error", ImtMessagePayload.Level.ALERT);
        } else {
            tell(Component.translatable("imt.imttech.voice.ear_error", reason), ImtMessagePayload.Level.ALERT);
        }
    }

    /**
     * Doet alsof het oor deze zin hoorde (hoofd-thread). Voor de ontwikkeltests: zo kunnen die
     * het brein proberen zonder microfoon.
     */
    public static void hear(String text) {
        onHeard(text);
    }

    /** Het oor hoorde iets (hoofd-thread): zoek het op in het woordenboek en voer het uit. */
    static void onHeard(String heard) {
        if (!ImtClientState.authorized) {
            return; // bril afgezet terwijl ze luisterde
        }
        // Laat zien wat I.M.T hoorde (niet uitspreken). Staat "Voice text" uit (G > Systems),
        // dan laat addNotification het zelf weg.
        ImtClientState.addNotification(Component.translatable("imt.imttech.voice.heard", heard),
                ImtMessagePayload.Level.COMMS, false);
        // Zonder datacenter werkt er geen enkel brein, ook het woordenboek niet (8 oktober).
        if (!ImtBrain.hasDatacenter()) {
            noDatacenter();
            return;
        }
        // Een brein gekozen (lokaal model of Claude)? Dan denkt dat na; anders het woordenboek. Is
        // je datacenter nog niet sterk, dan duurt het woordenboek ook even (dictionaryLater).
        if (ImtBrain.handle(heard)) {
            return;
        }
        if (ImtBrain.dictionaryLater(heard)) {
            return;
        }
        runDictionary(heard);
    }

    /** Ze kan niet denken: er is (nog) geen datacenter. */
    private static void noDatacenter() {
        tell("imt.imttech.brain.no_datacenter", ImtMessagePayload.Level.WARNING);
        ImtClientEvents.playUi(ModSounds.IMT_DENIED.get(), 1.0f);
    }

    /** Zoekt wat je zei op in het woordenboek en voert het uit (ook de reserve van het brein). */
    static void runDictionary(String heard) {
        List<VoiceMatcher.Match> matches = VoiceCommandStore.match(heard);
        if (matches.isEmpty()) {
            lastUnknown = heard;
            tell(Component.translatable("imt.imttech.voice.unknown", heard), ImtMessagePayload.Level.WARNING);
            ImtClientEvents.playUi(ModSounds.IMT_ALERT.get(), 1.2f);
            return;
        }
        // Meerdere acties tegelijk? Dan geen "staat al aan"-meldingen voor elk apart.
        boolean quiet = matches.size() > 1 || matches.stream().anyMatch(match -> match.command().actions.size() > 1);
        for (VoiceMatcher.Match match : matches) {
            run(match, quiet);
        }
    }

    /** Voert één gevonden commando uit: eerst het antwoord, dan de acties. */
    static void run(VoiceMatcher.Match match, boolean quiet) {
        VoiceDictionary.Command command = match.command();
        if (!command.reply.isBlank()) {
            tell(text(command.reply, match.captured()), ImtMessagePayload.Level.INFO);
            // Haar eigen antwoord zegt al wat er gebeurt; de gewone meldingen van de acties
            // tonen we wel, maar spreken we niet ook nog uit.
            ImtSpeech.holdInfoMessages(2500);
        }
        for (VoiceDictionary.ActionRef ref : command.actions) {
            String value = ref.value().isEmpty() ? match.captured() : text(ref.value(), match.captured()).getString();
            execute(ref.action(), value, quiet);
        }
    }

    /**
     * Tekst uit het woordenboek: begint hij met @, dan is het een vertaalsleutel (in de taal van
     * je spel). Een invulplek zoals {plek} wordt vervangen door wat je zei.
     */
    private static Component text(String text, String captured) {
        String filled = text.replaceAll("\\{[^}]*}", captured);
        return filled.startsWith("@") ? Component.translatable(filled.substring(1)) : Component.literal(filled);
    }

    /** Eén actie uitvoeren. De server controleert zelf nog of het mag (energie, wachttijd...). */
    static void execute(VoiceAction action, String value, boolean quiet) {
        Minecraft mc = Minecraft.getInstance();
        switch (action) {
            case LAUNCH_DRONES -> ImtClientEvents.sendAction(ImtAction.LAUNCH_DRONES);
            case SHIELD_ON -> setFeature(ImtFeature.SHIELD, true, quiet);
            case SHIELD_OFF -> setFeature(ImtFeature.SHIELD, false, quiet);
            case SHIELD_TOGGLE -> ImtClientEvents.sendAction(ImtAction.TOGGLE_SHIELD);
            case ESCORT_ON -> setFeature(ImtFeature.GUARD_DRONES, true, quiet);
            case ESCORT_OFF -> setFeature(ImtFeature.GUARD_DRONES, false, quiet);
            case ESCORT_TOGGLE -> ImtClientEvents.sendAction(ImtAction.TOGGLE_GUARD_DRONES);
            case SATELLITE_STRIKE -> ImtClientEvents.sendAction(ImtAction.SATELLITE_STRIKE);
            // Geen standen meer: de aanval noemen = meteen vuren (net als K, P en C).
            case SATELLITE_PRECISION -> ImtClientEvents.sendAction(ImtAction.STRIKE_PRECISION);
            case SATELLITE_BOMBARDMENT -> ImtClientEvents.sendAction(ImtAction.STRIKE_BOMBARDMENT);
            case SATELLITE_SWEEP -> ImtClientEvents.sendAction(ImtAction.STRIKE_SWEEP);
            case DRONE_STRIKE -> ImtClientEvents.sendAction(ImtAction.DRONE_STRIKE);
            case ROCKETS -> ImtClientEvents.sendAction(ImtAction.ROCKETS);
            case STAND_DOWN -> ImtClientEvents.sendAction(ImtAction.STAND_DOWN);
            case SPY_DRONE -> ImtClientEvents.sendAction(ImtAction.SPY_DRONE);
            case SPY_VIEW -> ImtClientEvents.sendAction(ImtAction.SPY_VIEW);
            case SPY_REPORT -> ImtClientEvents.sendAction(ImtAction.SPY_REPORT);
            case TRANSPORT_RIDE -> ImtClientEvents.sendAction(ImtAction.TRANSPORT_RIDE);
            case TRANSPORT_AUTOPILOT -> ImtClientEvents.sendAction(ImtAction.TRANSPORT_AUTOPILOT);
            case CARGO_DRONE -> ImtClientEvents.sendAction(ImtAction.CARGO_DRONE);
            case CARRY_ENTITY -> ImtClientEvents.sendAction(ImtAction.CARRY_ENTITY);
            case MARK_TARGET -> ImtClientEvents.markLookTarget();
            case CLEAR_MARKS -> ImtClientEvents.sendAction(ImtAction.CLEAR_MARKS);
            case SCOUT_DRONE -> {
                if (!DroneCamera.isActive()) {
                    ImtClientEvents.sendAction(ImtAction.DRONE_CAMERA);
                }
            }
            case SCOUT_RETURN -> {
                if (DroneCamera.isActive()) {
                    ImtClientEvents.sendAction(ImtAction.DRONE_CAMERA);
                } else if (!quiet) {
                    tell("imt.imttech.voice.not_in_camera", ImtMessagePayload.Level.INFO);
                }
            }
            case ORE_SCAN -> ImtClientEvents.sendAction(ImtAction.ORE_SCAN);
            case THREAT_SCAN_ON -> setFeature(ImtFeature.THREAT_SCAN, true, quiet);
            case THREAT_SCAN_OFF -> setFeature(ImtFeature.THREAT_SCAN, false, quiet);
            case THREAT_SCAN_TOGGLE -> ImtClientEvents.sendAction(ImtAction.TOGGLE_THREAT_SCAN);
            case NIGHT_VISION_ON -> setFeature(ImtFeature.NIGHT_VISION, true, quiet);
            case NIGHT_VISION_OFF -> setFeature(ImtFeature.NIGHT_VISION, false, quiet);
            case NIGHT_VISION_TOGGLE -> ImtClientEvents.sendAction(ImtAction.TOGGLE_NIGHT_VISION);
            case TARGET_ANALYSIS_ON -> setFeature(ImtFeature.TARGET_ANALYSIS, true, quiet);
            case TARGET_ANALYSIS_OFF -> setFeature(ImtFeature.TARGET_ANALYSIS, false, quiet);
            case TARGET_ANALYSIS_TOGGLE -> ImtClientEvents.sendAction(ImtAction.TOGGLE_TARGET_ANALYSIS);
            case PING -> ImtClientEvents.placePing();
            // Fase 9: redstone. De waarde is de naam van een relais of van een hele groep.
            // Fase 13: muziek via Spotify (alles gebeurt in jouw eigen spel)
            case MUSIC_PLAY -> ImtMusic.play(value);
            case MUSIC_PAUSE -> ImtMusic.pause();
            case MUSIC_RESUME -> ImtMusic.resume();
            case MUSIC_NEXT -> ImtMusic.next();
            case MUSIC_PREVIOUS -> ImtMusic.previous();
            case MUSIC_WHAT -> ImtMusic.whatIsPlaying();
            case HACK_PLAYER -> ImtClientEvents.sendAction(ImtAction.HACK_PLAYER);
            case HACK_STOP -> ImtClientEvents.sendAction(ImtAction.HACK_STOP);
            case REDSTONE_ON -> switchRelay(value, ImtRedstone.Op.ON);
            case REDSTONE_OFF -> switchRelay(value, ImtRedstone.Op.OFF);
            case REDSTONE_TOGGLE -> switchRelay(value, ImtRedstone.Op.TOGGLE);
            case REDSTONE_PULSE -> switchRelay(value, ImtRedstone.Op.PULSE);
            case NAVIGATE_TO -> navigateTo(mc.player, value);
            case NAVIGATE_HOME -> navigateHome(mc.player);
            case NAVIGATE_DEATH -> navigate(ImtWaypoints.DEATH);
            case NEXT_DESTINATION -> nextDestination(mc.player);
            case STOP_NAVIGATION -> navigate(ImtWaypoints.NONE);
            case SAVE_PLACE -> savePlace(mc.player, value);
            case SEND_MESSAGE -> sendMessage(value);
            case OPEN_NAVIGATION -> ImtClientEvents.openIfAuthorized(mc, new NavigationScreen());
            case OPEN_COMMS -> ImtClientEvents.openIfAuthorized(mc, new CommsScreen());
            default -> {
                // De say_...-acties: I.M.T beantwoordt een vraag.
                Component answer = VoiceInfo.answer(action);
                if (!answer.getString().isEmpty()) {
                    tell(answer, ImtMessagePayload.Level.INFO);
                }
            }
        }
    }

    /** Zet een functie aan of uit. Staat hij al goed, dan doen we niets (en zegt I.M.T dat). */
    private static void setFeature(ImtFeature feature, boolean wanted, boolean quiet) {
        ItemStack glasses = ImtData.getWornGlasses(Minecraft.getInstance().player);
        boolean on = ImtData.isEnabled(glasses, feature);
        if (wanted == on) {
            if (!quiet) {
                tell(Component.translatable(on ? "imt.imttech.voice.already_on" : "imt.imttech.voice.already_off",
                        Component.translatable(feature.translationKey())), ImtMessagePayload.Level.INFO);
            }
            return;
        }
        for (ImtAction action : ImtAction.values()) {
            if (action.toggles() == feature) {
                ImtClientEvents.sendAction(action);
                return;
            }
        }
    }

    /** Zet een relais (of een hele groep) aan, uit, om, of geef het een korte puls. */
    private static void switchRelay(String nameOrGroup, ImtRedstone.Op op) {
        String clean = nameOrGroup.strip();
        if (clean.isEmpty()) {
            tell("imt.imttech.voice.which_relay", ImtMessagePayload.Level.WARNING);
            return;
        }
        PacketDistributor.sendToServer(RelayActionPayload.switchNamed(clean, op));
    }

    /** NONE = navigatie stoppen, DEATH = naar je laatste sterfplek, anders een nummer uit je lijst. */
    private static void navigate(int index) {
        PacketDistributor.sendToServer(new WaypointActionPayload(WaypointActionPayload.Op.NAVIGATE, index, "", 0));
    }

    /** Zoekt de opgeslagen plek met (ongeveer) deze naam. Geeft het nummer terug, of -1. */
    private static int findPlace(LocalPlayer player, String name) {
        List<Waypoint> waypoints = player.getData(ModAttachments.WAYPOINTS).waypoints();
        String wanted = name.toLowerCase(Locale.ROOT).strip();
        if (wanted.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < waypoints.size(); i++) {
            if (waypoints.get(i).name().toLowerCase(Locale.ROOT).equals(wanted)) {
                return i;
            }
        }
        for (int i = 0; i < waypoints.size(); i++) {
            String saved = waypoints.get(i).name().toLowerCase(Locale.ROOT);
            if (saved.contains(wanted) || wanted.contains(saved)) {
                return i;
            }
        }
        // Bijna hetzelfde (het oor verstond het net anders)?
        for (int i = 0; i < waypoints.size(); i++) {
            if (VoiceMatcher.similarity(waypoints.get(i).name().toLowerCase(Locale.ROOT), wanted) > 0) {
                return i;
            }
        }
        return -1;
    }

    private static void navigateTo(LocalPlayer player, String name) {
        int found = findPlace(player, name);
        if (found < 0) {
            tell(Component.translatable("imt.imttech.voice.unknown_place", name), ImtMessagePayload.Level.WARNING);
        } else {
            navigate(found);
        }
    }

    /** Naar huis: de eerste opgeslagen plek die thuis, home, basis... heet. */
    private static void navigateHome(LocalPlayer player) {
        for (String home : new String[] {"thuis", "home", "huis", "basis", "base", "kamp", "camp"}) {
            int found = findPlace(player, home);
            if (found >= 0) {
                navigate(found);
                return;
            }
        }
        tell("imt.imttech.voice.no_home", ImtMessagePayload.Level.WARNING);
    }

    /** Navigeer naar de volgende opgeslagen plek (na de laatste komt weer de eerste). */
    private static void nextDestination(LocalPlayer player) {
        ImtWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        if (data.waypoints().isEmpty()) {
            tell("imt.imttech.voice.no_waypoints", ImtMessagePayload.Level.INFO);
            return;
        }
        navigate(data.active() >= 0 ? (data.active() + 1) % data.waypoints().size() : 0);
    }

    private static void savePlace(LocalPlayer player, String name) {
        int count = player.getData(ModAttachments.WAYPOINTS).waypoints().size();
        String clean = name.isBlank()
                ? Component.translatable("imt.imttech.voice.place_name", count + 1).getString()
                : name.strip();
        if (clean.length() > Math.round(ImtConfig.Navigation.MAX_WAYPOINT_NAME)) {
            clean = clean.substring(0, Math.round(ImtConfig.Navigation.MAX_WAYPOINT_NAME));
        }
        int color = Waypoint.COLORS[count % Waypoint.COLORS.length];
        PacketDistributor.sendToServer(new WaypointActionPayload(WaypointActionPayload.Op.ADD, 0, clean, color));
    }

    /** Een bericht naar iedereen met een I.M.T-bril (de server regelt wie het mag ontvangen). */
    private static void sendMessage(String text) {
        String clean = text.strip();
        if (clean.isEmpty()) {
            return;
        }
        if (clean.length() > Math.round(ImtConfig.Comms.MAX_MESSAGE_LENGTH)) {
            clean = clean.substring(0, Math.round(ImtConfig.Comms.MAX_MESSAGE_LENGTH));
        }
        PacketDistributor.sendToServer(new CommsPayload("", clean));
    }

    // ---------------------------------------------------------------- G-menu, tabblad Systemen

    /** Wat je kiest in het G-menu: installeren, of aan/uit zetten als het al geïnstalleerd is. */
    public static void choose(VoiceFeature feature) {
        switch (VoiceInstaller.state(feature)) {
            case UNSUPPORTED -> tell("imt.imttech.voice.unsupported", ImtMessagePayload.Level.WARNING);
            case INSTALLING -> tell(Component.translatable("imt.imttech.voice.installing",
                    feature.spokenName(), VoiceInstaller.percent(feature)), ImtMessagePayload.Level.INFO);
            case NOT_INSTALLED, FAILED -> {
                tell(Component.translatable("imt.imttech.voice.install_started", feature.spokenName(), feature.missingSize()),
                        ImtMessagePayload.Level.INFO);
                VoiceInstaller.install(feature, error -> {
                    if (error != null) {
                        tell(Component.translatable("imt.imttech.voice.install_failed", feature.spokenName(), error),
                                ImtMessagePayload.Level.ALERT);
                        return;
                    }
                    feature.setEnabled(true);
                    tell(feature == VoiceFeature.SPEECH
                            ? Component.translatable("imt.imttech.voice.install_done.speech")
                            : Component.translatable("imt.imttech.voice.install_done.commands",
                                    ImtKeys.PUSH_TO_TALK.getTranslatedKeyMessage()), ImtMessagePayload.Level.BOOT);
                });
            }
            case INSTALLED -> {
                boolean on = !feature.isEnabled();
                feature.setEnabled(on);
                if (feature == VoiceFeature.COMMANDS && !on) {
                    ImtAssistant.shutdown(); // spraakbesturing uit: geheugen vrijmaken
                }
                tell(Component.translatable(on ? "imt.imttech.msg.feature_on" : "imt.imttech.msg.feature_off",
                        feature.menuLabel()), ImtMessagePayload.Level.INFO);
            }
        }
    }

    /** Het spel sluit af: alle achtergrondprogramma's van I.M.T stoppen. */
    public static void shutdown() {
        ImtSpeech.shutdown();
        ImtAssistant.shutdown();
        ImtBrain.stopLocal();
    }

    // ---------------------------------------------------------------- Hulpjes

    private static void tell(String key, ImtMessagePayload.Level level) {
        tell(Component.translatable(key), level);
    }

    /**
     * Een melding van I.M.T (in de HUD, en uitgesproken als haar stem aan staat).
     * Staat "Voice text" uit (G > Systems), dan zegt ze het alleen (zie addNotification).
     */
    static void tell(Component message, ImtMessagePayload.Level level) {
        ImtClientState.addNotification(message, level);
    }
}
