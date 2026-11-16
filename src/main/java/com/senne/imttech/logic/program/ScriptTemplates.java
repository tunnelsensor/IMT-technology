package com.senne.imttech.logic.program;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Je eigen templates voor de app Logic (hoofdstuk 125, jouw wens van 16 november: "zorg dat al de standaard scripts die
 * in een script komen als je een nieuw script maakt weg zijn want dat is onhandig dat je altijd standaard dingen moet
 * weghalen, je kunt wel templates maken en opslaan die je dan kunt inladen bij een nieuw leeg aangemaakt script").
 * <p>
 * Een nieuw programma begint dus leeg. Code die je vaak gebruikt, bewaar je als <b>template</b>: een naam met code. Die
 * laad je later in een leeg programma. Een template hoort bij een soort programma (een diagram op de server, een robot-,
 * drone- of RC program): een robottemplate zie je alleen bij robotprogramma's. Een global script is een serverdiagram,
 * dus zijn templates staan bij de server (met een G erbij).
 * <p>
 * De templates staan op <b>jouw pc</b>, in één bestand ({@code imttech/script_templates.json} in je Minecraft-map). Je
 * hebt ze dus in elke wereld en op elke server, en niemand anders ziet ze. Dit stuk kent Minecraft zelf niet: de app
 * Logic geeft het bestand, de tests een tijdelijk bestand.
 */
public final class ScriptTemplates {
    /** Zo lang mag de naam van een template zijn (even lang als de naam van een diagram). */
    public static final int MAX_NAME = LogicProgram.MAX_NAME;
    /** Zoveel tekens code mag een template hebben (zoveel past in de editor). */
    public static final int MAX_CODE = LogicProgram.MAX_SOURCE;
    /** Zoveel templates samen (alle soorten). */
    public static final int MAX_TEMPLATES = 100;

    /** Eén template: een naam, voor welke soort programma, en de code. */
    public record Template(String name, ProgramKind kind, String code) {
        /** Is het een global script (de eerste regel code is {@code global})? */
        public boolean global() {
            return startsGlobal(code);
        }

        /** Hoeveel regels code het heeft (voor de uitleg in de app). */
        public int lines() {
            return code.isEmpty() ? 0 : (int) code.stripTrailing().lines().count();
        }
    }

    /** Wat er gebeurde bij {@link #save}. */
    public enum Result {
        /** Een nieuwe template. */
        SAVED,
        /** Er was al een template met die naam (voor dezelfde soort): die heeft nu deze code. */
        REPLACED,
        /** Geen naam. */
        NO_NAME,
        /** Geen code (leeg, of alleen de regel global). */
        NO_CODE,
        /** Meer dan {@link #MAX_CODE} tekens. */
        TOO_LONG,
        /** Al {@link #MAX_TEMPLATES} templates. */
        FULL,
        /** Het bestand kon niet geschreven worden; er is niets veranderd. */
        NOT_WRITTEN
    }

    private final Path file;
    private final List<Template> templates = new ArrayList<>();
    /**
     * Kon het bestand niet (helemaal) gelezen worden: kapot, met de hand fout aangepast, of met een template die we
     * oversloegen (een soort die deze versie niet kent, een tweede met dezelfde naam...)? Dan maken we bij het eerste
     * bewaren eerst een kopie ({@code script_templates.json.broken}), zodat bewaren niets zomaar wist.
     */
    private boolean backupFirst;

    /** De templates in dit bestand (bestaat het nog niet, dan zijn er nog geen). */
    public ScriptTemplates(Path file) {
        this.file = file;
        read();
    }

    /** Het bestand waar ze in staan. */
    public Path file() {
        return file;
    }

    /** De templates voor deze soort programma, op alfabet. */
    public List<Template> of(ProgramKind kind) {
        List<Template> list = new ArrayList<>();
        for (Template template : templates) {
            if (template.kind() == kind) {
                list.add(template);
            }
        }
        list.sort(Comparator.comparing(template -> template.name().toLowerCase(Locale.ROOT)));
        return list;
    }

    /** Hoeveel templates er in totaal zijn (alle soorten). */
    public int size() {
        return templates.size();
    }

    /** De template met deze naam voor deze soort (hoofdletters maken niet uit), of null. */
    public @Nullable Template find(ProgramKind kind, String name) {
        String clean = cleanName(name);
        for (Template template : templates) {
            if (template.kind() == kind && template.name().equalsIgnoreCase(clean)) {
                return template;
            }
        }
        return null;
    }

    /**
     * Bewaart deze code als template met deze naam. Is er al een met die naam voor dezelfde soort, dan krijgt die de
     * nieuwe code (de app vraagt dat eerst). Het bestand wordt meteen geschreven.
     */
    public Result save(String name, ProgramKind kind, String code) {
        String clean = cleanName(name);
        if (clean.isEmpty()) {
            return Result.NO_NAME;
        }
        String text = cleanCode(code);
        if (blank(text)) {
            return Result.NO_CODE;
        }
        if (text.length() - 1 > MAX_CODE) { // zonder het regeleinde dat cleanCode erbij zet: 8000 tekens mag
            return Result.TOO_LONG;
        }
        List<Template> before = new ArrayList<>(templates);
        Template old = find(kind, clean);
        if (old == null && templates.size() >= MAX_TEMPLATES) {
            return Result.FULL;
        }
        Template template = new Template(clean, kind, text);
        if (old != null) {
            templates.set(templates.indexOf(old), template);
        } else {
            templates.add(template);
        }
        if (!write()) {
            templates.clear();
            templates.addAll(before);
            return Result.NOT_WRITTEN;
        }
        return old != null ? Result.REPLACED : Result.SAVED;
    }

    /** Gooit de template met deze naam voor deze soort weg. Geeft false als die er niet was of het bestand niet lukte. */
    public boolean delete(ProgramKind kind, String name) {
        Template old = find(kind, name);
        if (old == null) {
            return false;
        }
        int index = templates.indexOf(old);
        templates.remove(index);
        if (!write()) {
            templates.add(index, old);
            return false;
        }
        return true;
    }

    // ================================================================ Regels voor code

    /**
     * Staat er nog geen code? Leeg, alleen witregels, of alleen de regel {@code global} (zo begint een nieuw global script).
     * Dan laadt de app een template meteen; anders vraagt hij eerst of je je code wil vervangen. Commentaar telt als code
     * (dat typte je zelf).
     */
    public static boolean blank(String code) {
        boolean globalSeen = false;
        for (String line : code.replace("\r", "").split("\n", -1)) {
            String text = line.strip();
            if (text.isEmpty()) {
                continue;
            }
            if (!globalSeen && text.equals("global")) {
                globalSeen = true;
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * Is de eerste regel code (na witregels en commentaar) {@code global}? Dan is het een global script (hoofdstuk 43). Net
     * als de taal mag er commentaar achter staan: {@code global  # shared}.
     */
    public static boolean startsGlobal(String code) {
        for (String line : code.replace("\r", "").split("\n", -1)) {
            int hash = line.indexOf('#');
            String text = (hash >= 0 ? line.substring(0, hash) : line).strip();
            if (text.isEmpty()) {
                continue;
            }
            return text.equals("global");
        }
        return false;
    }

    /** Een naam zonder spaties eromheen en zonder vreemde tekens, hooguit {@link #MAX_NAME} tekens. */
    static String cleanName(String name) {
        StringBuilder clean = new StringBuilder();
        for (char c : name.strip().toCharArray()) {
            if (!Character.isISOControl(c)) {
                clean.append(c);
            }
        }
        String text = clean.toString().strip();
        return text.length() > MAX_NAME ? text.substring(0, MAX_NAME).strip() : text;
    }

    /** Code zoals de editor hem kent: \n tussen de regels, geen tabs, en precies één regeleinde op het einde. */
    static String cleanCode(String code) {
        String text = code.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ").stripTrailing();
        return text.isEmpty() ? "" : text + "\n";
    }

    // ================================================================ Het bestand

    private void read() {
        templates.clear();
        backupFirst = false;
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1); // Kladblok zet soms een BOM vooraan; Gson kent die niet
            }
            JsonElement root = JsonParser.parseString(text);
            JsonArray list = root.getAsJsonObject().getAsJsonArray("templates");
            if (list == null) {
                backupFirst = !text.isBlank();
                return;
            }
            for (JsonElement element : list) {
                Template template = parse(element);
                if (template == null || find(template.kind(), template.name()) != null || templates.size() >= MAX_TEMPLATES) {
                    backupFirst = true; // overgeslagen: bij het eerste bewaren eerst een kopie van het oude bestand
                    continue;
                }
                templates.add(template);
            }
        } catch (IOException | RuntimeException e) {
            // Kapot of onleesbaar: geen templates, en bij het eerste bewaren eerst een kopie van het oude bestand.
            templates.clear();
            backupFirst = true;
        }
    }

    /** Eén template uit het bestand, of null als er iets niet klopt (die slaan we over, de rest blijft). */
    private static @Nullable Template parse(JsonElement element) {
        try {
            JsonObject object = element.getAsJsonObject();
            String name = cleanName(object.get("name").getAsString());
            String code = cleanCode(object.get("code").getAsString());
            // Een soort die deze versie niet kent (IllegalArgumentException), of iets anders dan tekst: overslaan.
            ProgramKind kind = ProgramKind.valueOf(object.get("kind").getAsString().strip().toUpperCase(Locale.ROOT));
            if (name.isEmpty() || blank(code) || code.length() - 1 > MAX_CODE) {
                return null;
            }
            return new Template(name, kind, code);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Schrijft alle templates naar het bestand (eerst naar een tijdelijk bestand, dan erover). */
    private boolean write() {
        try {
            Path folder = file.toAbsolutePath().getParent();
            if (folder != null) {
                Files.createDirectories(folder);
            }
            if (backupFirst && Files.isRegularFile(file)) {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            }
            JsonObject root = new JsonObject();
            root.addProperty("info", "Your I.M.T Logic templates (kind: SERVER, ROBOT, DRONE or CAR). Made in the Logic app.");
            JsonArray list = new JsonArray();
            for (Template template : templates) {
                JsonObject object = new JsonObject();
                object.addProperty("name", template.name());
                object.addProperty("kind", template.kind().name());
                object.addProperty("code", template.code());
                list.add(object);
            }
            root.add("templates", list);
            String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.deleteIfExists(temp); // geen half bestand laten liggen
                throw e;
            }
            backupFirst = false;
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
