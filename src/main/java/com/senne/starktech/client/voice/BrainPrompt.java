package com.senne.starktech.client.voice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.senne.starktech.client.EdithClientState;
import com.senne.starktech.nav.Waypoint;
import com.senne.starktech.redstone.Relay;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.suit.StarkSuitItem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Wat het brein te lezen krijgt, voor Claude en het lokale model precies hetzelfde:
 *
 * 1. De instructies (assets/starktech/edith/brain_prompt.txt), met daarin de lijst van alle
 *    acties van de bril. Die tekst verandert nooit tijdens het spelen: Claude kan hem dan
 *    onthouden ("prompt caching"), wat sneller en goedkoper is.
 * 2. Per zin: de toestand van jou en je bril (STATUS), wat ze over je onthouden heeft
 *    (REMEMBER) en wat je zei.
 * 3. Het JSON-schema van het antwoord (zie BrainDecision): het model kan niets anders teruggeven.
 *
 * De instructies en de acties zijn in het Engels (daar zijn de modellen het best in); ze
 * antwoordt wel in de taal waarin jij praat.
 */
public final class BrainPrompt {
    private BrainPrompt() {}

    /** Wat er bij de acties met een waarde in die waarde hoort. */
    private static final Map<VoiceAction, String> VALUES = Map.of(
            VoiceAction.NAVIGATE_TO, "value: exact waypoint name from STATUS",
            VoiceAction.SAVE_PLACE, "value: a name for this spot",
            VoiceAction.SEND_MESSAGE, "value: the message for your team",
            VoiceAction.MUSIC_PLAY, "value: song and/or artist",
            VoiceAction.REDSTONE_ON, "value: relay or group name from STATUS",
            VoiceAction.REDSTONE_OFF, "value: relay or group name from STATUS",
            VoiceAction.REDSTONE_TOGGLE, "value: relay or group name from STATUS",
            VoiceAction.REDSTONE_PULSE, "value: relay or group name from STATUS");

    private static @Nullable String system;

    // ================================================================ De instructies

    /** De instructies met de lijst van acties (één keer opgebouwd, daarna altijd dezelfde tekst). */
    public static synchronized String system() {
        if (system == null) {
            JsonObject lang = JsonParser.parseString(resource("/assets/starktech/lang/en_us.json")).getAsJsonObject();
            StringBuilder actions = new StringBuilder();
            for (VoiceAction action : VoiceAction.values()) {
                String key = action.translationKey();
                String name = lang.has(key) ? lang.get(key).getAsString() : action.id();
                String description = lang.has(key + ".desc") ? lang.get(key + ".desc").getAsString() : "";
                // In het woordenboek staat "(or {place} in the phrase)": dat gaat over zinnen, niet over het brein.
                description = description.replaceAll("\\s*\\(or \\{[^}]*} in the phrase\\)", "")
                        .replaceAll("^Value = [^.]*\\.?\\s*", "");
                actions.append("- ").append(action.id()).append(": ").append(name);
                if (!description.isBlank()) {
                    actions.append(" - ").append(description);
                }
                if (VALUES.containsKey(action)) {
                    actions.append(" (").append(VALUES.get(action)).append(")");
                }
                actions.append('\n');
            }
            system = resource("/assets/starktech/edith/brain_prompt.txt").replace("{actions}", actions.toString().trim());
        }
        return system;
    }

    private static String resource(String path) {
        try (InputStream in = BrainPrompt.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + path, e);
        }
    }

    // ================================================================ Het schema van het antwoord

    /**
     * Het JSON-schema van het antwoord (zie BrainDecision), als gewone Java-maps en -lijsten:
     * zo gaat het zowel naar Claude (via de SDK) als naar llama.cpp (als JSON-tekst).
     */
    public static Map<String, Object> schema() {
        List<String> ids = new ArrayList<>();
        for (VoiceAction action : VoiceAction.values()) {
            ids.add(action.id());
        }
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("type", "object");
        call.put("properties", Map.of(
                "action", Map.of("type", "string", "enum", ids),
                "value", Map.of("type", "string")));
        call.put("required", List.of("action", "value"));
        call.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("reply", Map.of("type", "string"));
        properties.put("actions", Map.of("type", "array", "items", call));
        properties.put("remember", Map.of("type", "string"));
        properties.put("forget", Map.of("type", "string"));
        properties.put("unsure", Map.of("type", "boolean"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("reply", "actions", "remember", "forget", "unsure"));
        schema.put("additionalProperties", false);
        return schema;
    }

    // ================================================================ Per zin

    /** De toestand van jou en je bril, als tekst (op de hoofd-thread: hij leest het spel). */
    public static String status(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return "STATUS\n(no player)";
        }
        StringBuilder text = new StringBuilder("STATUS\n");
        line(text, "Player", player.getGameProfile().getName());
        // De antwoorden die ze ook in het woordenboek geeft (in de taal van je spel).
        line(text, "Health", VoiceInfo.answer(VoiceAction.SAY_HEALTH).getString());
        line(text, "Glasses", VoiceInfo.answer(VoiceAction.SAY_STATUS).getString());
        line(text, "Battery", VoiceInfo.answer(VoiceAction.SAY_BATTERY).getString());
        line(text, "Ready", VoiceInfo.answer(VoiceAction.SAY_COOLDOWNS).getString());
        line(text, "Time", VoiceInfo.answer(VoiceAction.SAY_TIME).getString());
        line(text, "Weather", VoiceInfo.answer(VoiceAction.SAY_WEATHER).getString());
        line(text, "Position", VoiceInfo.answer(VoiceAction.SAY_POSITION).getString());
        line(text, "Enemies", VoiceInfo.answer(VoiceAction.SAY_ENEMIES).getString());
        line(text, "Looking at", VoiceInfo.answer(VoiceAction.SAY_TARGET).getString());
        line(text, "Route", VoiceInfo.answer(VoiceAction.SAY_DESTINATION).getString());
        line(text, "Suit", StarkSuitItem.fullSet(player) ? "full Stark suit" + (player.onGround() ? "" : ", in the air")
                : StarkSuitItem.wornPieces(player) + " of 3 suit pieces");
        List<String> waypoints = new ArrayList<>();
        for (Waypoint waypoint : player.getData(ModAttachments.WAYPOINTS).waypoints()) {
            waypoints.add(waypoint.name());
        }
        line(text, "Waypoints", waypoints.isEmpty() ? "none" : String.join(", ", waypoints));
        com.senne.starktech.redstone.EdithRelays relayData = player.getData(ModAttachments.RELAYS);
        Set<String> relays = new LinkedHashSet<>();
        for (Relay relay : relayData.relays()) {
            relays.add(relay.tags().isEmpty() ? relay.name() : relay.name() + " (tags " + relay.tagText() + ")");
        }
        line(text, "Relays", relays.isEmpty() ? "none" : String.join(", ", relays));
        // Tags in tags: "security room is inside base". Een tag schakelen pakt ook alles eronder.
        List<String> nesting = new ArrayList<>();
        for (String tag : relayData.tags()) {
            if (!relayData.parentsOf(tag).isEmpty()) {
                nesting.add(tag + " is inside " + String.join(" and ", relayData.parentsOf(tag)));
            }
        }
        line(text, "Relay tags", nesting.isEmpty() ? "" : String.join("; ", nesting));
        if (!EdithClientState.operational) {
            line(text, "Note", "the glasses are low on power or still starting");
        }
        return text.toString().trim();
    }

    private static void line(StringBuilder text, String label, String value) {
        if (!value.isBlank()) {
            text.append(label).append(": ").append(value.replace('\n', ' ')).append('\n');
        }
    }

    /** Het bericht voor deze zin: STATUS, wat ze onthouden heeft, en wat je zei. */
    public static String turn(String status, List<String> memory, String heard) {
        StringBuilder text = new StringBuilder(status).append("\n\nREMEMBER\n");
        if (memory.isEmpty()) {
            text.append("(nothing yet)\n");
        }
        for (String fact : memory) {
            text.append("- ").append(fact).append('\n');
        }
        return text.append("\nPLAYER SAID: \"").append(heard).append('"').toString();
    }

    /** Het bericht voor iets dat ze uit zichzelf mag zeggen (zie EdithInitiative). */
    public static String event(String status, List<String> memory, String event) {
        StringBuilder text = new StringBuilder(turn(status, memory, ""));
        int said = text.lastIndexOf("PLAYER SAID");
        text.setLength(said);
        return text.append("EVENT (the player said nothing): ").append(event)
                .append("\nYou may speak up on your own: one short, useful remark and no actions.")
                .append(" If it is not worth saying anything, leave reply empty.").toString();
    }

    static String toJson(Map<String, Object> map) {
        return new com.google.gson.Gson().toJson(map);
    }
}
