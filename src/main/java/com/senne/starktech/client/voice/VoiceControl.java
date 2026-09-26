package com.senne.starktech.client.voice;

import com.senne.starktech.client.CommsScreen;
import com.senne.starktech.client.DroneCamera;
import com.senne.starktech.client.EdithClientConfig;
import com.senne.starktech.client.EdithClientEvents;
import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.client.EdithKeys;
import com.senne.starktech.client.music.EdithMusic;
import com.senne.starktech.client.NavigationScreen;
import com.senne.starktech.edith.EdithAction;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithFeature;
import com.senne.starktech.nav.EdithWaypoints;
import com.senne.starktech.nav.Waypoint;
import com.senne.starktech.network.CommsPayload;
import com.senne.starktech.network.RelayActionPayload;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.WaypointActionPayload;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModSounds;
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

    /** De laatste zin die E.D.I.T.H. niet kende; het bewerkscherm kan hem toevoegen aan een commando. */
    private static String lastUnknown = "";

    public static String lastUnknown() {
        return lastUnknown;
    }

    // ---------------------------------------------------------------- Elke tick

    public static void tick(Minecraft mc) {
        VoiceInstaller.removeObsolete();
        boolean pressed = false;
        while (EdithKeys.PUSH_TO_TALK.consumeClick()) {
            pressed = true;
        }
        if (pressed && !EdithAssistant.isBusy()) {
            startListening();
        }
        // Toets los, een scherm geopend of de bril af? Dan stoppen met opnemen.
        if (EdithListener.isListening()
                && (!EdithKeys.PUSH_TO_TALK.isDown() || mc.screen != null || !EdithClientState.authorized)) {
            EdithListener.stop();
        }
        // Het oor alvast opstarten zodra je de bril draagt, dan reageert ze meteen de eerste keer.
        if (EdithClientState.wearing && VoiceFeature.COMMANDS.isEnabled()
                && VoiceInstaller.state(VoiceFeature.COMMANDS) == VoiceInstaller.State.INSTALLED) {
            EdithAssistant.preload();
        }
        // Het brein: opwarmen, en eventueel uit zichzelf iets zeggen (het kijkt zelf of het kan).
        EdithBrain.tick(mc);
    }

    private static void startListening() {
        if (!EdithClientState.wearing) {
            return; // zonder bril doet de toets niets
        }
        // Praat ze nog? Dan zwijgt ze nu: jij bent aan het woord.
        EdithSpeech.stopSpeaking();
        if (!EdithClientState.authorized || !EdithClientState.operational) {
            EdithClientEvents.playUi(ModSounds.EDITH_DENIED.get(), 1.0f);
            return;
        }
        switch (VoiceInstaller.state(VoiceFeature.COMMANDS)) {
            case UNSUPPORTED -> tell("edith.starktech.voice.unsupported", EdithMessagePayload.Level.WARNING);
            case NOT_INSTALLED, FAILED -> tell("edith.starktech.voice.not_installed", EdithMessagePayload.Level.WARNING);
            case INSTALLING -> tell(Component.translatable("edith.starktech.voice.installing",
                    VoiceFeature.COMMANDS.spokenName(), VoiceInstaller.percent(VoiceFeature.COMMANDS)), EdithMessagePayload.Level.INFO);
            case INSTALLED -> {
                if (!VoiceFeature.COMMANDS.isEnabled()) {
                    tell("edith.starktech.voice.disabled", EdithMessagePayload.Level.WARNING);
                } else {
                    EdithClientEvents.playUi(ModSounds.EDITH_SELECT.get(), 1.6f);
                    EdithListener.start();
                }
            }
        }
    }

    // ---------------------------------------------------------------- Wat je zei

    static void onHeardNothing() {
        tell("edith.starktech.voice.nothing_heard", EdithMessagePayload.Level.INFO);
    }

    static void onError(String reason) {
        if (reason.equals("mic")) {
            tell("edith.starktech.voice.mic_error", EdithMessagePayload.Level.ALERT);
        } else {
            tell(Component.translatable("edith.starktech.voice.ear_error", reason), EdithMessagePayload.Level.ALERT);
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
        if (!EdithClientState.authorized) {
            return; // bril afgezet terwijl ze luisterde
        }
        // Laat zien wat E.D.I.T.H. hoorde (niet uitspreken). Dit kun je uitzetten:
        // G > Systemen > Spraaktekst.
        if (EdithClientConfig.voiceTextInHud()) {
            EdithClientState.addNotification(Component.translatable("edith.starktech.voice.heard", heard),
                    EdithMessagePayload.Level.COMMS, false);
        }
        // Een brein gekozen (lokaal model of Claude)? Dan denkt dat na; anders het woordenboek.
        if (EdithBrain.handle(heard)) {
            return;
        }
        runDictionary(heard);
    }

    /** Zoekt wat je zei op in het woordenboek en voert het uit (ook de reserve van het brein). */
    static void runDictionary(String heard) {
        List<VoiceMatcher.Match> matches = VoiceCommandStore.match(heard);
        if (matches.isEmpty()) {
            lastUnknown = heard;
            tell(Component.translatable("edith.starktech.voice.unknown", heard), EdithMessagePayload.Level.WARNING);
            EdithClientEvents.playUi(ModSounds.EDITH_ALERT.get(), 1.2f);
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
            tell(text(command.reply, match.captured()), EdithMessagePayload.Level.INFO);
            // Haar eigen antwoord zegt al wat er gebeurt; de gewone meldingen van de acties
            // tonen we wel, maar spreken we niet ook nog uit.
            EdithSpeech.holdInfoMessages(2500);
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
            case LAUNCH_DRONES -> EdithClientEvents.sendAction(EdithAction.LAUNCH_DRONES);
            case SHIELD_ON -> setFeature(EdithFeature.SHIELD, true, quiet);
            case SHIELD_OFF -> setFeature(EdithFeature.SHIELD, false, quiet);
            case SHIELD_TOGGLE -> EdithClientEvents.sendAction(EdithAction.TOGGLE_SHIELD);
            case ESCORT_ON -> setFeature(EdithFeature.GUARD_DRONES, true, quiet);
            case ESCORT_OFF -> setFeature(EdithFeature.GUARD_DRONES, false, quiet);
            case ESCORT_TOGGLE -> EdithClientEvents.sendAction(EdithAction.TOGGLE_GUARD_DRONES);
            case SATELLITE_STRIKE -> EdithClientEvents.sendAction(EdithAction.SATELLITE_STRIKE);
            case SATELLITE_PRECISION -> EdithClientEvents.sendAction(EdithAction.SATELLITE_PRECISION);
            case SATELLITE_BOMBARDMENT -> EdithClientEvents.sendAction(EdithAction.SATELLITE_BOMBARDMENT);
            case SATELLITE_SWEEP -> EdithClientEvents.sendAction(EdithAction.SATELLITE_SWEEP);
            case DRONE_STRIKE -> EdithClientEvents.sendAction(EdithAction.DRONE_STRIKE);
            case ROCKETS -> EdithClientEvents.sendAction(EdithAction.ROCKETS);
            case STAND_DOWN -> EdithClientEvents.sendAction(EdithAction.STAND_DOWN);
            case SPY_DRONE -> EdithClientEvents.sendAction(EdithAction.SPY_DRONE);
            case SPY_VIEW -> EdithClientEvents.sendAction(EdithAction.SPY_VIEW);
            case SPY_REPORT -> EdithClientEvents.sendAction(EdithAction.SPY_REPORT);
            case TRANSPORT_RIDE -> EdithClientEvents.sendAction(EdithAction.TRANSPORT_RIDE);
            case TRANSPORT_AUTOPILOT -> EdithClientEvents.sendAction(EdithAction.TRANSPORT_AUTOPILOT);
            case CARGO_DRONE -> EdithClientEvents.sendAction(EdithAction.CARGO_DRONE);
            case CARRY_ENTITY -> EdithClientEvents.sendAction(EdithAction.CARRY_ENTITY);
            case MARK_TARGET -> EdithClientEvents.markLookTarget();
            case CLEAR_MARKS -> EdithClientEvents.sendAction(EdithAction.CLEAR_MARKS);
            case SCOUT_DRONE -> {
                if (!DroneCamera.isActive()) {
                    EdithClientEvents.sendAction(EdithAction.DRONE_CAMERA);
                }
            }
            case SCOUT_RETURN -> {
                if (DroneCamera.isActive()) {
                    EdithClientEvents.sendAction(EdithAction.DRONE_CAMERA);
                } else if (!quiet) {
                    tell("edith.starktech.voice.not_in_camera", EdithMessagePayload.Level.INFO);
                }
            }
            case ORE_SCAN -> EdithClientEvents.sendAction(EdithAction.ORE_SCAN);
            case THREAT_SCAN_ON -> setFeature(EdithFeature.THREAT_SCAN, true, quiet);
            case THREAT_SCAN_OFF -> setFeature(EdithFeature.THREAT_SCAN, false, quiet);
            case THREAT_SCAN_TOGGLE -> EdithClientEvents.sendAction(EdithAction.TOGGLE_THREAT_SCAN);
            case NIGHT_VISION_ON -> setFeature(EdithFeature.NIGHT_VISION, true, quiet);
            case NIGHT_VISION_OFF -> setFeature(EdithFeature.NIGHT_VISION, false, quiet);
            case NIGHT_VISION_TOGGLE -> EdithClientEvents.sendAction(EdithAction.TOGGLE_NIGHT_VISION);
            case TARGET_ANALYSIS_ON -> setFeature(EdithFeature.TARGET_ANALYSIS, true, quiet);
            case TARGET_ANALYSIS_OFF -> setFeature(EdithFeature.TARGET_ANALYSIS, false, quiet);
            case TARGET_ANALYSIS_TOGGLE -> EdithClientEvents.sendAction(EdithAction.TOGGLE_TARGET_ANALYSIS);
            case PING -> EdithClientEvents.placePing();
            // Fase 9: redstone. De waarde is de naam van een relais of van een hele groep.
            // Fase 13: muziek via Spotify (alles gebeurt in jouw eigen spel)
            case MUSIC_PLAY -> EdithMusic.play(value);
            case MUSIC_PAUSE -> EdithMusic.pause();
            case MUSIC_RESUME -> EdithMusic.resume();
            case MUSIC_NEXT -> EdithMusic.next();
            case MUSIC_PREVIOUS -> EdithMusic.previous();
            case MUSIC_WHAT -> EdithMusic.whatIsPlaying();
            case HACK_PLAYER -> EdithClientEvents.sendAction(EdithAction.HACK_PLAYER);
            case HACK_STOP -> EdithClientEvents.sendAction(EdithAction.HACK_STOP);
            case REDSTONE_ON -> switchRelay(value, EdithRedstone.Op.ON);
            case REDSTONE_OFF -> switchRelay(value, EdithRedstone.Op.OFF);
            case REDSTONE_TOGGLE -> switchRelay(value, EdithRedstone.Op.TOGGLE);
            case REDSTONE_PULSE -> switchRelay(value, EdithRedstone.Op.PULSE);
            case NAVIGATE_TO -> navigateTo(mc.player, value);
            case NAVIGATE_HOME -> navigateHome(mc.player);
            case NAVIGATE_DEATH -> navigate(EdithWaypoints.DEATH);
            case NEXT_DESTINATION -> nextDestination(mc.player);
            case STOP_NAVIGATION -> navigate(EdithWaypoints.NONE);
            case SAVE_PLACE -> savePlace(mc.player, value);
            case SEND_MESSAGE -> sendMessage(value);
            case OPEN_NAVIGATION -> EdithClientEvents.openIfAuthorized(mc, new NavigationScreen());
            case OPEN_COMMS -> EdithClientEvents.openIfAuthorized(mc, new CommsScreen());
            default -> {
                // De say_...-acties: E.D.I.T.H. beantwoordt een vraag.
                Component answer = VoiceInfo.answer(action);
                if (!answer.getString().isEmpty()) {
                    tell(answer, EdithMessagePayload.Level.INFO);
                }
            }
        }
    }

    /** Zet een functie aan of uit. Staat hij al goed, dan doen we niets (en zegt E.D.I.T.H. dat). */
    private static void setFeature(EdithFeature feature, boolean wanted, boolean quiet) {
        ItemStack glasses = EdithData.getWornGlasses(Minecraft.getInstance().player);
        boolean on = EdithData.isEnabled(glasses, feature);
        if (wanted == on) {
            if (!quiet) {
                tell(Component.translatable(on ? "edith.starktech.voice.already_on" : "edith.starktech.voice.already_off",
                        Component.translatable(feature.translationKey())), EdithMessagePayload.Level.INFO);
            }
            return;
        }
        for (EdithAction action : EdithAction.values()) {
            if (action.toggles() == feature) {
                EdithClientEvents.sendAction(action);
                return;
            }
        }
    }

    /** Zet een relais (of een hele groep) aan, uit, om, of geef het een korte puls. */
    private static void switchRelay(String nameOrGroup, EdithRedstone.Op op) {
        String clean = nameOrGroup.strip();
        if (clean.isEmpty()) {
            tell("edith.starktech.voice.which_relay", EdithMessagePayload.Level.WARNING);
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
            tell(Component.translatable("edith.starktech.voice.unknown_place", name), EdithMessagePayload.Level.WARNING);
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
        tell("edith.starktech.voice.no_home", EdithMessagePayload.Level.WARNING);
    }

    /** Navigeer naar de volgende opgeslagen plek (na de laatste komt weer de eerste). */
    private static void nextDestination(LocalPlayer player) {
        EdithWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        if (data.waypoints().isEmpty()) {
            tell("edith.starktech.voice.no_waypoints", EdithMessagePayload.Level.INFO);
            return;
        }
        navigate(data.active() >= 0 ? (data.active() + 1) % data.waypoints().size() : 0);
    }

    private static void savePlace(LocalPlayer player, String name) {
        int count = player.getData(ModAttachments.WAYPOINTS).waypoints().size();
        String clean = name.isBlank()
                ? Component.translatable("edith.starktech.voice.place_name", count + 1).getString()
                : name.strip();
        if (clean.length() > EdithBalance.MAX_WAYPOINT_NAME) {
            clean = clean.substring(0, EdithBalance.MAX_WAYPOINT_NAME);
        }
        int color = Waypoint.COLORS[count % Waypoint.COLORS.length];
        PacketDistributor.sendToServer(new WaypointActionPayload(WaypointActionPayload.Op.ADD, 0, clean, color));
    }

    /** Een bericht naar iedereen met een E.D.I.T.H.-bril (de server regelt wie het mag ontvangen). */
    private static void sendMessage(String text) {
        String clean = text.strip();
        if (clean.isEmpty()) {
            return;
        }
        if (clean.length() > EdithBalance.MAX_MESSAGE_LENGTH) {
            clean = clean.substring(0, EdithBalance.MAX_MESSAGE_LENGTH);
        }
        PacketDistributor.sendToServer(new CommsPayload("", clean));
    }

    // ---------------------------------------------------------------- G-menu, tabblad Systemen

    /** Wat je kiest in het G-menu: installeren, of aan/uit zetten als het al geïnstalleerd is. */
    public static void choose(VoiceFeature feature) {
        switch (VoiceInstaller.state(feature)) {
            case UNSUPPORTED -> tell("edith.starktech.voice.unsupported", EdithMessagePayload.Level.WARNING);
            case INSTALLING -> tell(Component.translatable("edith.starktech.voice.installing",
                    feature.spokenName(), VoiceInstaller.percent(feature)), EdithMessagePayload.Level.INFO);
            case NOT_INSTALLED, FAILED -> {
                tell(Component.translatable("edith.starktech.voice.install_started", feature.spokenName(), feature.missingSize()),
                        EdithMessagePayload.Level.INFO);
                VoiceInstaller.install(feature, error -> {
                    if (error != null) {
                        tell(Component.translatable("edith.starktech.voice.install_failed", feature.spokenName(), error),
                                EdithMessagePayload.Level.ALERT);
                        return;
                    }
                    feature.setEnabled(true);
                    tell(feature == VoiceFeature.SPEECH
                            ? Component.translatable("edith.starktech.voice.install_done.speech")
                            : Component.translatable("edith.starktech.voice.install_done.commands",
                                    EdithKeys.PUSH_TO_TALK.getTranslatedKeyMessage()), EdithMessagePayload.Level.BOOT);
                });
            }
            case INSTALLED -> {
                boolean on = !feature.isEnabled();
                feature.setEnabled(on);
                if (feature == VoiceFeature.COMMANDS && !on) {
                    EdithAssistant.shutdown(); // spraakbesturing uit: geheugen vrijmaken
                }
                tell(Component.translatable(on ? "edith.starktech.msg.feature_on" : "edith.starktech.msg.feature_off",
                        feature.menuLabel()), EdithMessagePayload.Level.INFO);
            }
        }
    }

    /** Het spel sluit af: alle achtergrondprogramma's van E.D.I.T.H. stoppen. */
    public static void shutdown() {
        EdithSpeech.shutdown();
        EdithAssistant.shutdown();
        EdithBrain.stopLocal();
    }

    // ---------------------------------------------------------------- Hulpjes

    private static void tell(String key, EdithMessagePayload.Level level) {
        tell(Component.translatable(key), level);
    }

    /**
     * Een melding van E.D.I.T.H. (in de HUD, en uitgesproken als haar stem aan staat).
     *
     * Staat "Spraaktekst" uit (G > Systemen), dan spreekt ze het alleen uit en komt het
     * niet als tekst in je HUD te staan.
     */
    static void tell(Component message, EdithMessagePayload.Level level) {
        if (EdithClientConfig.voiceTextInHud()) {
            EdithClientState.addNotification(message, level);
        } else {
            EdithSpeech.say(message, level);
        }
    }
}
