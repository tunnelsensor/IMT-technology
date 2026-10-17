package com.senne.imttech.client.voice;

import java.util.Locale;
import java.util.Set;

/**
 * I.M.T verstaat en spreekt alleen Engels (jouw keuze op 27 september).
 *
 * - Het oor (Qwen3-ASR) zegt zelf in welke taal je praatte: het zet
 *   {@code language Dutch<asr_text>} voor de tekst. Is dat geen Engels, dan doet ze niets en zegt
 *   ze dat ze alleen Engels verstaat. Zegt het oor "Dutch" maar hoorde het toch vooral Engelse
 *   woorden (Engels met een accent), dan tellen we het wel als Engels.
 * - Oudere woordenboeken (versie 2) hadden nog Nederlandse synoniemen en zinnen, en ze kan
 *   Nederlandse zinnen geleerd hebben ("Learned: ..."). Die halen we eruit (zie
 *   VoiceCommandStore); je eigen Engelse zinnen blijven staan.
 */
public final class EnglishOnly {
    private EnglishOnly() {}

    /** Wat het oor hoorde: in welke taal (leeg als het dat niet zei) en de tekst. */
    public record Heard(String language, String text) {}

    private static final String TEXT_START = "<asr_text>";

    /** Leest het antwoord van het oor: "language Dutch<asr_text>Help, ik word aangevallen." */
    public static Heard parse(String raw) {
        int start = raw.indexOf(TEXT_START);
        if (start < 0) {
            return new Heard("", raw.trim());
        }
        String head = raw.substring(0, start).trim();
        String language = head.toLowerCase(Locale.ROOT).startsWith("language ") ? head.substring(9).trim() : "";
        return new Heard(language, raw.substring(start + TEXT_START.length()).trim());
    }

    /** Mag I.M.T hier iets mee? Alleen als het Engels was. */
    public static boolean accepted(Heard heard) {
        String language = heard.language().toLowerCase(Locale.ROOT);
        if (language.isEmpty() || language.equals("english") || language.equals("none")) {
            return true;
        }
        // Het oor dacht een andere taal te horen. Waren het toch vooral Engelse woorden?
        return count(heard.text(), ENGLISH) > count(heard.text(), DUTCH);
    }

    /** Lijkt deze zin Nederlands? (Meer Nederlandse dan Engelse woordjes.) */
    public static boolean looksDutch(String phrase) {
        return count(phrase, DUTCH) > count(phrase, ENGLISH);
    }

    /**
     * Haalt het Nederlands uit een opgeslagen woordenboek: de Nederlandse synoniemen en zinnen
     * die tot en met versie 2 in het standaard-woordenboek stonden, en geleerde zinnen die
     * Nederlands lijken. Geeft terug hoeveel er weg is.
     */
    public static int strip(VoiceDictionary dictionary) {
        int removed = 0;
        for (VoiceDictionary.Command command : dictionary.commands) {
            boolean learned = command.name.startsWith(VoiceLearning.PREFIX);
            int before = command.phrases.size();
            command.phrases.removeIf(phrase -> OLD_DUTCH.contains(phrase) || (learned && looksDutch(phrase)));
            removed += before - command.phrases.size();
        }
        // Een geleerd commando zonder zinnen heeft geen zin meer.
        dictionary.commands.removeIf(command -> command.name.startsWith(VoiceLearning.PREFIX) && command.phrases.isEmpty());
        for (VoiceDictionary.Synonym synonym : dictionary.synonyms) {
            int before = synonym.synonyms().size();
            synonym.synonyms().removeIf(OLD_DUTCH::contains);
            removed += before - synonym.synonyms().size();
        }
        dictionary.synonyms.removeIf(synonym -> synonym.synonyms().isEmpty());
        return removed;
    }

    private static int count(String text, Set<String> words) {
        int found = 0;
        // De naam van de bril telt niet mee: in "Bonjour I.M.T." is de "I" geen Engels woord.
        String withoutName = NAME.matcher(text).replaceAll(" ");
        for (String word : withoutName.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) {
            found += words.contains(word) ? 1 : 0;
        }
        return found;
    }

    /** De naam van de bril zoals het oor hem opschrijft: "I.M.T.", "IMT", "I M T"... */
    private static final java.util.regex.Pattern NAME =
            java.util.regex.Pattern.compile("(?i)(?<![\\p{L}])i[.\\s]*m[.\\s]*t(?![\\p{L}])\\.?");

    /** Gewone Nederlandse woordjes (om een Nederlandse zin te herkennen). */
    private static final Set<String> DUTCH = Set.of("de", "het", "een", "ik", "je", "jij", "mijn", "naar", "en",
            "wat", "hoe", "hoeveel", "zet", "aan", "uit", "op", "dat", "deze", "nog", "heb", "niet", "maar",
            "doe", "stuur", "breng", "onthoud", "sla", "waar", "welke", "wie", "er", "zijn", "laat", "mij", "ben",
            "wordt", "met", "voor", "ja", "nee", "graag", "alsjeblieft", "gaat", "regenen", "word", "aangevallen");
    /** Gewone Engelse woordjes. */
    private static final Set<String> ENGLISH = Set.of("the", "a", "an", "i", "you", "my", "to", "and", "what",
            "how", "turn", "on", "off", "that", "this", "send", "bring", "remember", "save", "where", "which", "who",
            "it", "are", "am", "of", "with", "for", "yes", "no", "please", "time", "much", "many", "after", "at",
            "going", "rain", "is", "me", "drones", "drone", "attack", "scan", "night", "vision");

    /** De Nederlandse woorden en zinnen die tot en met versie 2 in het standaard-woordenboek stonden. */
    private static final Set<String> OLD_DUTCH = Set.of(
            "aanwijzen", "aanzetten", "accu", "activeren", "afmaken", "afspelen", "analyseer", "anderen",
            "annuleren", "avond", "beest", "beesten", "bekijk", "berichtenscherm", "beschermen", "beschikbaar",
            "bewaken", "bewaking", "bewaren", "bewolkt", "biomen", "bioom", "blazen", "bombardeer", "brengen",
            "communicatie", "conditie", "coordinaat", "coordinaten", "dankje", "dankjewel", "dankuwel",
            "deactiveren", "detecteer", "deuntje", "diamant", "doel", "doelanalyse", "doelen", "doelwitanalyse",
            "doorgaan", "draaien", "dreiging", "dreigingen", "dreigings scan", "droontjes", "duister",
            "eindpunt", "gids", "goedemiddag", "goedendag", "graf", "grapje", "grondstoffen", "hart", "harten",
            "hartjes", "heks", "helpen", "hervat", "hervatten", "hongerig", "hoofdkwartier", "huisje",
            "informeer", "infrarood", "inschakelen", "inzetten", "kapot", "klok", "lading", "lanceren", "leid",
            "leven", "levenspunten", "liedjes", "locatie", "markeren", "meld", "merci", "mineralen", "mopje",
            "muziekje", "muziekjes", "nachtkijker", "nee", "neer", "netheriet", "noemen", "noteer", "nummers",
            "onthouden", "ontplof", "onweert", "oorlog", "openen", "opgeladen", "oplaadtijd", "opslaan",
            "opstarten", "opzetten", "orbitaal", "overal", "overzicht", "pauzeert", "pauzeren", "pikdonker",
            "plaatje", "plattegrond", "plekje", "positie", "precisiestraal", "punt", "redden", "regent",
            "rontgen", "ruimtelaser", "sateliet", "satelliet", "satelliet laser", "satteliet", "schat",
            "schatten", "schieten", "scouten", "skelet", "skeletten", "smaragd", "smaragden", "sneeuw",
            "spelen", "speur", "spin", "spinnen", "spionagerapport", "spionbeeld", "spioneer", "spioneren",
            "starten", "steenkool", "sterfplaats", "stierf", "stroom", "sturen", "systeem check", "taggen",
            "targetanalyse", "teamgenoten", "tegenstander", "tegenstanders", "tonen", "top", "uitschakelen",
            "uitzetten", "verdedigen", "verdergaan", "verkenner", "verkennersdrone", "verkennings drone",
            "verkenningsdrones", "verkenningsdroon", "vernietigen", "vertellen", "verwijderen",
            "vijanddetectie", "vijandenscan", "vijandig", "vijandscan", "vinden", "voedsel", "weerbericht",
            "weersvoorspelling", "wissen", "zaklamp", "zeggen", "zend", "zoeken", "zonnig");
}
