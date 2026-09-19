package com.senne.starktech.client.voice;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Zoekt welk commando uit het woordenboek past bij wat je zei.
 *
 * Zo werkt het:
 * 1. Alles naar kleine letters, zonder leestekens en accenten ("Geïnstalleerd!" → "geinstalleerd").
 * 2. Synoniemen worden hetzelfde woord: "zwerm", "swarm" en "robots" worden allemaal "drones".
 * 3. Onbelangrijke woorden (de, het, alsjeblieft...) vallen weg.
 * 4. Voor elke zin in het woordenboek tellen we hoeveel woorden terugkomen in wat je zei (de
 *    volgorde maakt niet uit: "radar aan" = "activeer de radar"). Kleine verschillen tellen ook
 *    (het oor verstaat soms "marker" voor "markeer").
 * 5. De zin die het best past wint, als hij goed genoeg past.
 *
 * Zeg je twee dingen tegelijk ("scan naar erts en zet de radar aan"), dan krijg je twee commando's.
 *
 * Deze klasse gebruikt niets van Minecraft, zodat je ze los kunt testen.
 */
public final class VoiceMatcher {
    /**
     * Een gevonden commando.
     *
     * @param captured wat je zei op de plek van {..} in de zin (bv. de naam van een plek), of ""
     * @param score    hoe goed het past, van 0 tot 1
     * @param phrase   de zin uit het woordenboek die paste
     */
    public record Match(VoiceDictionary.Command command, String captured, double score, String phrase) {}

    /** Onder deze score telt een zin niet als herkend. */
    private static final double MIN_SCORE = 0.6;
    /** Bij twee commando's in één zin moet elk deel minstens zo goed passen. */
    private static final double MIN_PART_SCORE = 0.7;
    /** Woorden waarmee je twee commando's aan elkaar plakt. */
    private static final Pattern SPLIT = Pattern.compile("\\s*,\\s*|\\s+(?:en dan|and then|en daarna|daarna|en|and|then|dan)\\s+");

    /** Een woord zoals de matcher het ziet, met de plek van het oorspronkelijke woord (om iets te kunnen invullen). */
    private record Token(String word, int rawFirst, int rawLast) {}

    /**
     * Een zin uit het woordenboek, klaar om te vergelijken.
     *
     * @param slot     de invulplek aan het eind (bv. "{plek}"), of null
     */
    private record Phrase(VoiceDictionary.Command command, String text, List<String> fixed, String slot) {}

    /** Invulplekken voor vrije tekst (berichten): daar halen we niets vooraan weg. */
    private static final Set<String> TEXT_SLOTS = Set.of("{tekst}", "{text}", "{bericht}", "{message}");
    /** Woordjes die we vooraan een naam of plek weghalen: "noem het basis" → "basis". */
    private static final Set<String> NAME_FILLERS = Set.of("de", "het", "een", "the", "a", "an", "it", "hem", "him",
            "this", "dat", "that", "deze", "die", "naar", "to");

    private final Map<String, String> synonyms = new HashMap<>();
    private int longestSynonym = 1;
    private final Set<String> ignore = new HashSet<>();
    private final List<Phrase> phrases = new ArrayList<>();

    public VoiceMatcher(VoiceDictionary dictionary) {
        for (VoiceDictionary.Synonym synonym : dictionary.synonyms) {
            List<String> main = normalize(synonym.word());
            if (main.isEmpty()) {
                continue;
            }
            String canonical = String.join("_", main);
            addSynonym(main, canonical);
            for (String other : synonym.synonyms()) {
                addSynonym(normalize(other), canonical);
            }
        }
        for (String word : dictionary.ignore) {
            List<String> normalized = normalize(word);
            if (normalized.size() == 1) {
                ignore.add(normalized.getFirst());
                ignore.add(synonyms.getOrDefault(normalized.getFirst(), normalized.getFirst()));
            }
        }
        for (VoiceDictionary.Command command : dictionary.commands) {
            for (String text : command.phrases) {
                List<String> words = normalize(text);
                String slot = !words.isEmpty() && words.getLast().startsWith("{") && words.getLast().endsWith("}")
                        ? words.getLast() : null;
                if (slot != null) {
                    words = words.subList(0, words.size() - 1);
                }
                List<String> fixed = new ArrayList<>();
                for (Token token : canonical(words)) {
                    if (!ignore.contains(token.word())) {
                        fixed.add(token.word());
                    }
                }
                // Een zin met invulplek heeft minstens twee vaste woorden nodig, anders past hij op alles.
                if (fixed.size() >= (slot != null ? 2 : 1)) {
                    phrases.add(new Phrase(command, text, fixed, slot));
                }
            }
        }
    }

    private void addSynonym(List<String> words, String canonical) {
        if (!words.isEmpty()) {
            synonyms.put(String.join(" ", words), canonical);
            longestSynonym = Math.max(longestSynonym, words.size());
        }
    }

    // ---------------------------------------------------------------- Zoeken

    /** Alle commando's in wat je zei (meestal één, soms meer; leeg als niets past). */
    public List<Match> match(String heard) {
        Match whole = best(heard);
        if (whole != null && !whole.captured().isEmpty()) {
            return List.of(whole); // met een ingevulde waarde (bericht, naam...) nooit opsplitsen
        }
        String[] parts = SPLIT.split(heard.toLowerCase(Locale.ROOT).trim());
        if (parts.length >= 2) {
            List<Match> matches = new ArrayList<>();
            Set<VoiceDictionary.Command> seen = new HashSet<>();
            boolean allGood = true;
            for (String part : parts) {
                Match match = best(part);
                if (match == null || match.score() < MIN_PART_SCORE) {
                    allGood = false;
                    break;
                }
                if (seen.add(match.command())) {
                    matches.add(match);
                }
            }
            if (allGood && matches.size() >= 2) {
                return matches;
            }
        }
        return whole == null ? List.of() : List.of(whole);
    }

    /** Het ene commando dat het best past, of null. */
    public Match best(String heard) {
        List<Token> tokens = new ArrayList<>();
        for (Token token : canonical(normalize(heard))) {
            if (!ignore.contains(token.word())) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            return null;
        }
        Match best = null;
        int bestFixed = 0;
        for (Phrase phrase : phrases) {
            Match match = phrase.slot() != null ? scoreSlot(phrase, tokens, heard) : score(phrase, tokens);
            if (match == null || match.score() < MIN_SCORE) {
                continue;
            }
            boolean better = best == null
                    || match.score() > best.score() + 1e-9
                    || (Math.abs(match.score() - best.score()) < 1e-9 && phrase.fixed().size() > bestFixed);
            if (better) {
                best = match;
                bestFixed = phrase.fixed().size();
            }
        }
        return best;
    }

    /** Een gewone zin: hoeveel van zijn woorden komen terug in wat je zei (in welke volgorde ook)? */
    private Match score(Phrase phrase, List<Token> heard) {
        List<String> fixed = phrase.fixed();
        if (fixed.size() == 1 && heard.size() > 3) {
            return null; // één los sleutelwoord telt alleen in een korte zin
        }
        double matched = overlap(fixed, heard);
        double coverage = matched / fixed.size();
        if (coverage < requiredCoverage(fixed.size())) {
            return null;
        }
        double precision = matched / heard.size();
        return new Match(phrase.command(), "", 0.75 * coverage + 0.25 * precision, phrase.text());
    }

    /**
     * Een zin met een invulplek aan het eind, bv. "breng me naar {plek}". Eerst moeten de vaste
     * woorden in volgorde terugkomen; alles wat je daarna zei, wordt de ingevulde waarde.
     */
    private Match scoreSlot(Phrase phrase, List<Token> heard, String original) {
        List<String> fixed = phrase.fixed();
        int position = 0;
        double matched = 0;
        int lastRaw = -1;
        int missed = 0;
        for (String word : fixed) {
            int found = -1;
            double similarity = 0;
            for (int i = position; i < heard.size(); i++) {
                similarity = similarity(word, heard.get(i).word());
                if (similarity > 0) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                missed++;
                continue;
            }
            matched += similarity;
            lastRaw = heard.get(found).rawLast();
            position = found + 1;
        }
        if (lastRaw < 0 || missed > (fixed.size() >= 4 ? 1 : 0)) {
            return null;
        }
        String captured = capture(original, lastRaw + 1, !TEXT_SLOTS.contains(phrase.slot()));
        if (captured.isEmpty()) {
            return null;
        }
        double coverage = matched / fixed.size();
        if (coverage < requiredCoverage(fixed.size())) {
            return null;
        }
        int captureStart = lastRaw;
        long capturedWords = heard.stream().filter(token -> token.rawFirst() > captureStart).count();
        double precision = Math.min(1.0, (matched + capturedWords) / heard.size());
        return new Match(phrase.command(), captured, 0.75 * coverage + 0.25 * precision, phrase.text());
    }

    /** Hoe goed moeten de woorden van een zin terugkomen? Korte zinnen moeten helemaal passen. */
    private static double requiredCoverage(int words) {
        return words == 1 ? 0.8 : words == 2 ? 0.85 : words == 3 ? 0.66 : 0.7;
    }

    /**
     * Hoeveel woorden van de zin terugkomen in wat je zei: elk woord van de zin mag één gehoord
     * woord "gebruiken", en telt voor hoe goed het lijkt (1 = precies, 0,7-0,9 = bijna).
     */
    private double overlap(List<String> phrase, List<Token> heard) {
        boolean[] used = new boolean[heard.size()];
        double total = 0;
        for (String word : phrase) {
            int bestIndex = -1;
            double bestSimilarity = 0;
            for (int j = 0; j < heard.size(); j++) {
                double similarity = used[j] ? 0 : similarity(word, heard.get(j).word());
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestIndex = j;
                }
            }
            if (bestIndex >= 0) {
                used[bestIndex] = true;
                total += bestSimilarity;
            }
        }
        return total;
    }

    /** Hoeveel twee woorden op elkaar lijken: 1 = hetzelfde, 0 = anders. */
    static double similarity(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.contains("_") || b.contains("_")) {
            return 0; // synoniemen van meerdere woorden moeten precies kloppen
        }
        int shortest = Math.min(a.length(), b.length());
        int longest = Math.max(a.length(), b.length());
        if (shortest < 4) {
            return 0; // korte woorden zoals "aan" en "uit" moeten precies kloppen
        }
        // Samengestelde woorden: "verkenningsdrone" begint met "verkenning".
        if (shortest >= 5 && (a.startsWith(b) || b.startsWith(a))) {
            return 0.8;
        }
        double ratio = 1.0 - (double) distance(a, b) / longest;
        return ratio >= (longest >= 8 ? 0.7 : 0.75) ? ratio : 0;
    }

    /** Het aantal letters dat je moet veranderen om van a naar b te gaan (Levenshtein-afstand). */
    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    // ---------------------------------------------------------------- Woorden klaarmaken

    /** Kleine letters, geen accenten of leestekens: "Geïnstalleerd, oké!" → [geinstalleerd, oke]. */
    static List<String> normalize(String text) {
        String plain = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")      // accenten weg
                .replace("'", "")               // "it's" → "its"
                .replaceAll("[^\\p{L}\\p{N}{}]+", " ")
                .trim();
        return plain.isEmpty() ? List.of() : List.of(plain.split(" "));
    }

    /** Vervangt synoniemen door hun hoofdwoord; de langste synoniemen (meerdere woorden) eerst. */
    private List<Token> canonical(List<String> words) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < words.size()) {
            boolean replaced = false;
            for (int length = Math.min(longestSynonym, words.size() - i); length >= 1 && !replaced; length--) {
                String key = String.join(" ", words.subList(i, i + length));
                String canonical = synonyms.get(key);
                if (canonical != null) {
                    tokens.add(new Token(canonical, i, i + length - 1));
                    i += length;
                    replaced = true;
                }
            }
            if (!replaced) {
                tokens.add(new Token(words.get(i), i, i));
                i++;
            }
        }
        return tokens;
    }

    /**
     * Wat je zei vanaf woord nummer "from" (voor de invulplek), zoals je het zei (met hoofdletters),
     * zonder leesteken achteraan. Bij een naam of plek (isName) ook zonder "de/het/it" vooraan.
     */
    private static String capture(String original, int from, boolean isName) {
        List<String> pieces = new ArrayList<>();
        int index = 0;
        for (String raw : original.trim().split("\\s+")) {
            // Eén oorspronkelijk woord kan meerdere genormaliseerde woorden zijn (bv. "zuid-west").
            int count = normalize(raw).size();
            if (count == 0) {
                continue;
            }
            if (index >= from) {
                pieces.add(raw);
            }
            index += count;
        }
        while (isName && !pieces.isEmpty() && NAME_FILLERS.contains(normalize(pieces.getFirst()).stream().findFirst().orElse(""))) {
            pieces.removeFirst();
        }
        String captured = String.join(" ", pieces).trim();
        return captured.replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "");
    }
}
