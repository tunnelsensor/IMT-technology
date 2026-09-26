package com.senne.starktech.client.voice;

import java.util.Locale;
import java.util.Set;

/**
 * In welke taal zei je iets: Dutch of English? Het brein antwoordt in die taal.
 *
 * We tellen gewone woordjes van elke taal; staat het gelijk, dan de taal van je spel. (Een klein
 * taalmodel volgt anders snel de taal van zijn vorige antwoord in plaats van die van jouw zin.)
 */
public final class BrainLanguage {
    private BrainLanguage() {}

    private static final Set<String> DUTCH = Set.of("de", "het", "een", "ik", "je", "jij", "mijn", "naar", "en", "is",
            "wat", "hoe", "hoeveel", "zet", "aan", "uit", "op", "dat", "deze", "die", "nog", "heb", "niet", "maar",
            "doe", "stuur", "breng", "onthoud", "sla", "waar", "welke", "wie", "er", "zijn", "laat", "mij", "ben",
            "wordt", "van", "met", "voor", "ja", "nee", "graag", "alsjeblieft", "gaat", "regenen");
    private static final Set<String> ENGLISH = Set.of("the", "a", "an", "i", "you", "my", "to", "and", "what",
            "how", "turn", "on", "off", "that", "this", "send", "bring", "remember", "save", "where", "which", "who",
            "it", "are", "am", "of", "with", "for", "yes", "no", "please", "time", "much", "many", "after", "at",
            "going", "rain");

    /** "Dutch" of "English". */
    public static String of(String heard, String gameLanguage) {
        int dutch = 0;
        int english = 0;
        for (String word : heard.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) {
            dutch += DUTCH.contains(word) ? 1 : 0;
            english += ENGLISH.contains(word) ? 1 : 0;
        }
        if (dutch != english) {
            return dutch > english ? "Dutch" : "English";
        }
        return gameLanguage.toLowerCase(Locale.ROOT).startsWith("nl") ? "Dutch" : "English";
    }
}
