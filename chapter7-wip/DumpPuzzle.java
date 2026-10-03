package com.senne.starktech.security.os;

import com.senne.starktech.security.HackTerminal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * De geheugendump: de inlogpuzzel van een systeem ZONDER wachtwoord (de oude firmware van Tony
 * lekt geheugen bij een mislukte handshake). Acht woorden die op elkaar lijken; één is het
 * wachtwoord. Een fout woord laat zien welke letters goed staan, en streept door wat niet meer kan.
 *
 * Dit is de puzzel zoals hij altijd al was (zie HackTerminal), maar los, zodat de server hem kan
 * draaien voor een systeem zonder eigen wachtwoord. Systemen mét wachtwoord kraak je anders (raden,
 * woordenlijst, bruteforce; zie de shell).
 */
public final class DumpPuzzle {
    /** Woorden die vaak hetzelfde eindigen, zodat een fout woord je iets vertelt. */
    static final String[] WORDS = {
            "ROCKET", "POCKET", "SOCKET", "JACKET", "TARGET", "MAGNET", "PLANET", "HELMET", "TABLET", "GADGET",
            "SECRET", "BUCKET",
            "HAMMER", "HACKER", "LOCKER", "DOCKER", "BUFFER", "FILTER", "ROUTER", "SERVER", "SENDER", "RENDER",
            "MASTER", "POSTER", "TESTER", "SILVER", "COPPER", "LEADER", "READER", "HEADER", "LOADER", "CUTTER",
            "BORDER",
            "CANNON", "BEACON", "PISTON", "PROTON", "PHOTON", "CARBON", "BUTTON", "LEGION", "FUSION", "VISION",
            "MOTION", "OPTION",
            "SIGNAL", "PORTAL", "NEURAL", "VECTOR", "SENSOR", "DOCTOR", "MIRROR",
            "JARVIS", "FRIDAY", "PEPPER", "MALIBU", "SHIELD", "STRIKE", "ORBITS", "CIPHER", "SYSTEM", "MATRIX",
            "TITANS", "DRONES", "SECURE", "ACCESS", "BREACH", "KERNEL", "BINARY", "ARMORS", "VAULTS", "LAUNCH"};
    private static final int DUMP_WORDS = 8;
    private static final int CLOSEST = 12;
    private static final String JUNK = "!@#$%^&*()[]{}<>?/\\|+=-_;:'\",.";
    public static final int ATTEMPTS = 5;

    private final Random random;
    private String password = "";
    private List<String> candidates = List.of();
    private final List<String> possible = new ArrayList<>();
    private final List<String[]> rows = new ArrayList<>();
    private int attemptsLeft;

    public DumpPuzzle(long seed) {
        this.random = new Random(seed);
        newDump();
    }

    /** Een nieuwe dump (bij de start, en na te veel foute pogingen). */
    public void newDump() {
        password = WORDS[random.nextInt(WORDS.length)];
        List<String> others = new ArrayList<>(List.of(WORDS));
        others.remove(password);
        Collections.shuffle(others, random);
        others.sort(java.util.Comparator.comparingInt(word -> -HackTerminal.likeness(word, password)));
        List<String> close = new ArrayList<>(others.subList(0, CLOSEST));
        Collections.shuffle(close, random);
        List<String> words = new ArrayList<>(close.subList(0, DUMP_WORDS - 1));
        words.add(password);
        Collections.shuffle(words, random);
        candidates = List.copyOf(words);
        possible.clear();
        possible.addAll(candidates);
        attemptsLeft = ATTEMPTS;
        rows.clear();
        for (String word : candidates) {
            rows.add(new String[] {junk(random.nextInt(9)), word, ""});
        }
        for (String[] row : rows) {
            row[2] = junk(Math.max(0, 18 - row[0].length() - row[1].length()));
        }
    }

    private String junk(int length) {
        StringBuilder junk = new StringBuilder();
        for (int i = 0; i < length; i++) {
            junk.append(JUNK.charAt(random.nextInt(JUNK.length())));
        }
        return junk.toString();
    }

    /** De dump op het scherm (doorgestreepte woorden worden streepjes). */
    public void print(List<TermLine> out) {
        out.add(TermLine.header("MEMORY DUMP 0xF400-0xF4E0   attempts left: " + attemptsLeft));
        for (int row = 0; row < rows.size(); row++) {
            String[] parts = rows.get(row);
            String word = possible.contains(parts[1]) ? parts[1] : "-".repeat(parts[1].length());
            out.add(TermLine.normal(String.format("0x%04X  %s%s%s", 0xF400 + row * 16, parts[0], word, parts[2])));
        }
    }

    /** Uitkomst van een poging. */
    public enum Result { GRANTED, WRONG, NOT_IN_DUMP, CROSSED_OUT, LOCKED_OUT }

    /**
     * Probeer een woord. Zet de regels in {@code out}, en geeft terug wat er gebeurde. Bij LOCKED_OUT
     * zijn de pogingen op en komt er (na de blokkade) een nieuwe dump; roep dan {@link #newDump}.
     */
    public Result tryWord(String rawWord, List<TermLine> out) {
        String word = rawWord.toUpperCase(Locale.ROOT);
        if (word.equals(password)) {
            out.add(TermLine.good("ACCESS GRANTED."));
            return Result.GRANTED;
        }
        if (!candidates.contains(word)) {
            out.add(TermLine.error("'" + word + "' is not in the memory dump"));
            return Result.NOT_IN_DUMP;
        }
        if (!possible.contains(word)) {
            out.add(TermLine.error(word + " is already crossed out: it can't be the password."));
            return Result.CROSSED_OUT;
        }
        attemptsLeft--;
        int same = HackTerminal.likeness(word, password);
        out.add(TermLine.error("ACCESS DENIED. Likeness " + same + "/" + password.length()
                + "   attempts left: " + attemptsLeft));
        String pattern = HackTerminal.pattern(word, password);
        out.add(TermLine.hint(word + " -> " + pattern + "   " + (same == 0 ? "no letters in the right place"
                : same == 1 ? "1 letter in the right place" : same + " letters in the right place")));
        List<String> gone = new ArrayList<>();
        possible.removeIf(candidate -> {
            boolean out2 = !HackTerminal.pattern(word, candidate).equals(pattern);
            if (out2) {
                gone.add(candidate);
            }
            return out2;
        });
        gone.remove(word);
        if (!gone.isEmpty()) {
            out.add(TermLine.normal("Crossed out: " + String.join(", ", gone) + " (they don't match " + pattern + ")"));
        }
        out.add(TermLine.good("Still possible: " + String.join("  ", possible)));
        if (attemptsLeft <= 0) {
            out.add(TermLine.error("TOO MANY ATTEMPTS. Terminal locked, new memory dump coming..."));
            return Result.LOCKED_OUT;
        }
        return Result.WRONG;
    }

    public String password() {
        return password;
    }

    public List<String> candidates() {
        return candidates;
    }

    public List<String> possible() {
        return List.copyOf(possible);
    }

    public int attemptsLeft() {
        return attemptsLeft;
    }
}
