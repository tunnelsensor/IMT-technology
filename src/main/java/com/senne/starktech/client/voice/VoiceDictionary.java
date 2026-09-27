package com.senne.starktech.client.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Het woordenboek van E.D.I.T.H.: welke zinnen welke acties uitvoeren, welke woorden hetzelfde
 * betekenen (synoniemen) en welke woorden ze negeert.
 *
 * Het staat in config/starktech-voice.json. Je kunt het in het spel bewerken (G > Systemen >
 * Commando's) of met een teksteditor. Het standaard-woordenboek zit in de mod:
 * assets/starktech/edith/voice_dictionary.json.
 *
 * Zinnen mogen eindigen op een invulplek tussen {}, bv. "breng me naar {plek}": wat je daar zegt,
 * wordt de waarde van de actie (hier: de naam van de plek).
 *
 * Een antwoord dat begint met @ is een vertaalsleutel (bv. "@voice.starktech.reply.hello"): dan
 * zegt E.D.I.T.H. het in de taal van je spel.
 */
public final class VoiceDictionary {
    /**
     * De versie van het standaard-woordenboek. Hoger dit getal zodra de standaard-zinnen
     * veranderen: opgeslagen woordenboeken van een oudere versie worden dan opzij gezet
     * (met een .old ernaast) en vervangen door het nieuwe. Versie 1 was nog deels Nederlands;
     * versie 2 had nog Nederlandse synoniemen (die halen we eruit, zie VoiceCommandStore).
     */
    public static final int VERSION = 3;

    /** Eén actie in een commando, met een eventuele vaste waarde. */
    public record ActionRef(VoiceAction action, String value) {}

    /** Eén commando: naam, acties, antwoord en de zinnen die het starten. */
    public static final class Command {
        public String name;
        public final List<ActionRef> actions = new ArrayList<>();
        public String reply = "";
        public final List<String> phrases = new ArrayList<>();

        public Command(String name) {
            this.name = name;
        }

        public Command copy() {
            Command copy = new Command(name);
            copy.actions.addAll(actions);
            copy.reply = reply;
            copy.phrases.addAll(phrases);
            return copy;
        }
    }

    /** Een woord en de woorden die hetzelfde betekenen. */
    public record Synonym(String word, List<String> synonyms) {}

    public final List<Command> commands = new ArrayList<>();
    public final List<Synonym> synonyms = new ArrayList<>();
    public final List<String> ignore = new ArrayList<>();
    /** De versie die in het gelezen bestand stond (zie VERSION). */
    public int version = VERSION;

    public VoiceDictionary copy() {
        VoiceDictionary copy = new VoiceDictionary();
        commands.forEach(command -> copy.commands.add(command.copy()));
        copy.synonyms.addAll(synonyms);
        copy.ignore.addAll(ignore);
        copy.version = version;
        return copy;
    }

    // ---------------------------------------------------------------- Lezen en schrijven (JSON)

    public static VoiceDictionary parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        VoiceDictionary dictionary = new VoiceDictionary();
        dictionary.version = root.has("version") && root.get("version").isJsonPrimitive()
                ? root.get("version").getAsInt() : 1;
        for (JsonElement element : array(root, "commands")) {
            JsonObject object = element.getAsJsonObject();
            Command command = new Command(string(object, "name"));
            for (JsonElement actionElement : array(object, "actions")) {
                JsonObject actionObject = actionElement.getAsJsonObject();
                VoiceAction action = VoiceAction.byId(string(actionObject, "action"));
                if (action != null) {
                    command.actions.add(new ActionRef(action, string(actionObject, "value")));
                }
            }
            command.reply = string(object, "reply");
            for (JsonElement phrase : array(object, "phrases")) {
                command.phrases.add(phrase.getAsString());
            }
            dictionary.commands.add(command);
        }
        for (JsonElement element : array(root, "synonyms")) {
            JsonObject object = element.getAsJsonObject();
            List<String> words = new ArrayList<>();
            for (JsonElement word : array(object, "synonyms")) {
                words.add(word.getAsString());
            }
            dictionary.synonyms.add(new Synonym(string(object, "word"), words));
        }
        for (JsonElement word : array(root, "ignore")) {
            dictionary.ignore.add(word.getAsString());
        }
        return dictionary;
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonArray commandArray = new JsonArray();
        for (Command command : commands) {
            JsonObject object = new JsonObject();
            object.addProperty("name", command.name);
            JsonArray actionArray = new JsonArray();
            for (ActionRef action : command.actions) {
                JsonObject actionObject = new JsonObject();
                actionObject.addProperty("action", action.action().id());
                if (!action.value().isEmpty()) {
                    actionObject.addProperty("value", action.value());
                }
                actionArray.add(actionObject);
            }
            object.add("actions", actionArray);
            if (!command.reply.isEmpty()) {
                object.addProperty("reply", command.reply);
            }
            JsonArray phraseArray = new JsonArray();
            command.phrases.forEach(phraseArray::add);
            object.add("phrases", phraseArray);
            commandArray.add(object);
        }
        root.add("commands", commandArray);
        JsonArray synonymArray = new JsonArray();
        for (Synonym synonym : synonyms) {
            JsonObject object = new JsonObject();
            object.addProperty("word", synonym.word());
            JsonArray words = new JsonArray();
            synonym.synonyms().forEach(words::add);
            object.add("synonyms", words);
            synonymArray.add(object);
        }
        root.add("synonyms", synonymArray);
        JsonArray ignoreArray = new JsonArray();
        ignore.forEach(ignoreArray::add);
        root.add("ignore", ignoreArray);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(root);
    }

    /** Het standaard-woordenboek uit de mod. */
    public static VoiceDictionary loadDefault() throws IOException {
        try (InputStream in = VoiceDictionary.class.getResourceAsStream("/assets/starktech/edith/voice_dictionary.json")) {
            if (in == null) {
                throw new IOException("voice_dictionary.json is missing from the mod");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public static VoiceDictionary load(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, toJson(), StandardCharsets.UTF_8);
        Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }
}
