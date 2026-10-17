package com.senne.imttech.client.music;

import java.util.List;
import java.util.Locale;

/**
 * Maakt van wat je zei een zoekopdracht voor Spotify.
 *
 * Zeg je "play thunderstruck by acdc", dan zoeken we op track:"thunderstruck" artist:"acdc".
 * Met die twee filters zoekt Spotify gericht naar dat ene nummer, in plaats van de eerste de
 * beste karaoke- of coverversie te pakken die toevallig dezelfde woorden in de titel heeft.
 *
 * Noem je alleen een artiest ("play something by queen"), dan zoeken we op artiest. En zeg je
 * gewoon een titel, dan laten we Spotify het zelf uitzoeken.
 */
public final class MusicQuery {
    private MusicQuery() {}

    /**
     * Woorden waarmee je een zin begint, maar die niets zeggen over wát je wil horen.
     * De langste eerst, want "the song" moet voor "song" weggehaald worden.
     */
    private static final List<String> FILLERS = List.of(
            "the song", "a song", "the track", "a track", "the tune", "a tune",
            "some music", "a bit of", "anything", "something", "some",
            "song", "track", "tune", "music");

    /** Hoe je de artiest aankondigt: "thunderstruck BY acdc". */
    private static final List<String> BY = List.of("by", "from");

    /** De zoekopdracht voor Spotify, of leeg als er niets bruikbaars overblijft. */
    public static String build(String spoken) {
        String text = stripFillers(spoken);
        if (text.isEmpty()) {
            return "";
        }
        String[] words = text.split("\\s+");
        int at = indexOfBy(words);
        if (at < 0) {
            return text; // gewoon een titel: Spotify zoekt het zelf wel uit
        }
        String title = stripFillers(join(words, 0, at));
        String artist = stripFillers(join(words, at + 1, words.length));
        if (artist.isEmpty()) {
            return title;
        }
        if (title.isEmpty()) {
            return "artist:" + quote(artist); // "speel iets van queen"
        }
        return "track:" + quote(title) + " artist:" + quote(artist);
    }

    /**
     * Wat je zei zonder de filters, voor als het zoeken mét filters niets oplevert.
     * ("man of steel" is een titel, geen nummer van ene Steel.)
     */
    public static String plain(String spoken) {
        String[] words = stripFillers(spoken).split("\\s+");
        StringBuilder text = new StringBuilder();
        for (String word : words) {
            if (!BY.contains(word.toLowerCase(Locale.ROOT))) {
                text.append(text.isEmpty() ? "" : " ").append(word);
            }
        }
        return text.toString().strip();
    }

    /** Waar staat "by" (of "from") als los woord? -1 als het er niet in staat. */
    private static int indexOfBy(String[] words) {
        for (int i = 0; i < words.length; i++) {
            if (BY.contains(words[i].toLowerCase(Locale.ROOT))) {
                return i;
            }
        }
        return -1;
    }

    private static String join(String[] words, int from, int to) {
        return String.join(" ", List.of(words).subList(from, to));
    }

    /** Haalt vulwoorden vooraan weg, net zo lang tot er een echte titel of naam staat. */
    private static String stripFillers(String text) {
        String result = text.strip();
        for (boolean changed = true; changed; ) {
            changed = false;
            String lower = result.toLowerCase(Locale.ROOT);
            for (String filler : FILLERS) {
                if (lower.equals(filler)) {
                    return "";
                }
                if (lower.startsWith(filler + " ")) {
                    result = result.substring(filler.length()).strip();
                    changed = true;
                    break;
                }
            }
        }
        return result;
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "") + '"';
    }
}
