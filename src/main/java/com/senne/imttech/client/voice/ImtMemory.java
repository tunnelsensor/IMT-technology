package com.senne.imttech.client.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.senne.imttech.ImtTech;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Wat I.M.T onthoudt, voor het brein:
 *
 * 1. Blijvende feitjes over jou ("The player's base is called Thuis."), in
 *    .minecraft/imttech/ai/memory.json. Die blijven, ook na het afsluiten van het spel. Je kunt
 *    ze ook met de hand bewerken of wissen.
 * 2. De laatste paar zinnen van het gesprek (alleen zolang het spel draait, en na drie minuten
 *    stilte vergeten): zo snapt ze "ja, doe maar" na haar vraag "bedoelde je ...?".
 */
public final class ImtMemory {
    private ImtMemory() {}

    public static final int MAX_FACTS = 40;
    private static final int MAX_TURNS = 4;
    private static final long TURN_MILLIS = 180_000;

    /** Eén beurt in het gesprek: wat je zei, en haar antwoord (het JSON-object van het brein). */
    public record Turn(String said, String answer, long millis) {}

    private static List<String> facts;
    private static final Deque<Turn> TURNS = new ArrayDeque<>();

    public static Path file() {
        return FMLPaths.GAMEDIR.get().resolve("imttech").resolve("ai").resolve("memory.json");
    }

    // ================================================================ Feitjes

    public static synchronized List<String> facts() {
        if (facts == null) {
            facts = load();
        }
        return List.copyOf(facts);
    }

    /** Onthoud dit (en bewaar het meteen). Staat het er al, dan niets. */
    public static synchronized boolean remember(String fact) {
        String clean = fact.trim();
        if (clean.isEmpty() || facts().stream().anyMatch(known -> known.equalsIgnoreCase(clean))) {
            return false;
        }
        facts.add(clean);
        while (facts.size() > MAX_FACTS) {
            facts.removeFirst();
        }
        save();
        return true;
    }

    /** Vergeet de feitjes die hierop lijken (de een staat in de ander). Geeft terug of er iets weg is. */
    public static synchronized boolean forget(String what) {
        String needle = what.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return false;
        }
        facts();
        boolean removed = facts.removeIf(fact -> {
            String known = fact.toLowerCase(Locale.ROOT);
            return known.contains(needle) || needle.contains(known);
        });
        if (removed) {
            save();
        }
        return removed;
    }

    /** Alles vergeten (de knop in het scherm AI Brain). */
    public static synchronized void forgetAll() {
        facts();
        facts.clear();
        save();
        TURNS.clear();
    }

    private static List<String> load() {
        List<String> loaded = new ArrayList<>();
        Path file = file();
        if (!Files.exists(file)) {
            return loaded;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("facts") && root.get("facts").isJsonArray()) {
                for (JsonElement fact : root.getAsJsonArray("facts")) {
                    if (fact.isJsonPrimitive() && !fact.getAsString().isBlank()) {
                        loaded.add(fact.getAsString().trim());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            ImtTech.LOGGER.warn("I.M.T: could not read {}", file, e);
        }
        return loaded;
    }

    private static void save() {
        JsonArray array = new JsonArray();
        facts.forEach(array::add);
        JsonObject root = new JsonObject();
        root.add("facts", array);
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            ImtTech.LOGGER.warn("I.M.T: could not save {}", file(), e);
        }
    }

    // ================================================================ Het gesprek

    /** De laatste beurten, oudste eerst (na drie minuten stilte begint het gesprek opnieuw). */
    public static synchronized List<Turn> recentTurns() {
        long now = System.currentTimeMillis();
        if (!TURNS.isEmpty() && now - TURNS.peekLast().millis() > TURN_MILLIS) {
            TURNS.clear();
        }
        return List.copyOf(TURNS);
    }

    public static synchronized void addTurn(String said, String answer) {
        TURNS.addLast(new Turn(said, answer, System.currentTimeMillis()));
        while (TURNS.size() > MAX_TURNS) {
            TURNS.removeFirst();
        }
    }

    public static synchronized void clearConversation() {
        TURNS.clear();
    }
}
