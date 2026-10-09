package com.senne.starktech.bypass;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Het rekenwerk achter het kraken van een codepaneel (hoofdstuk 12): de vingerafdrukken van een code,
 * hoeveel cijfers van een poging op de goede plek staan, en hoeveel volgordes er na je pogingen nog
 * kunnen. De server en je scherm gebruiken allebei deze klasse.
 */
public final class PanelOrders {
    private PanelOrders() {}

    /** Eén poging: de volgorde die je probeerde, en hoeveel cijfers op de goede plek stonden. */
    public record Try(String code, int right) {}

    /** De afdrukken van een code: per cijfer 0-9 hoe vaak het erin zit ("4711" geeft 1 twee keer). */
    public static int[] prints(String code) {
        int[] prints = new int[10];
        for (char c : code.toCharArray()) {
            if (c >= '0' && c <= '9') {
                prints[c - '0']++;
            }
        }
        return prints;
    }

    /** Hoeveel cijfers van {@code guess} op dezelfde plek staan als in {@code code}. */
    public static int rightPlaces(String guess, String code) {
        int right = 0;
        for (int i = 0; i < Math.min(guess.length(), code.length()); i++) {
            if (guess.charAt(i) == code.charAt(i)) {
                right++;
            }
        }
        return right;
    }

    /**
     * Hoeveel volgordes van deze cijfers (precies zo vaak als de afdrukken zeggen) nog kunnen: bij elke
     * poging moeten er evenveel cijfers op de goede plek staan als de kit hoorde. Bij 8 verschillende
     * cijfers zijn dat er eerst 40320; dat rekent een computer zo uit.
     */
    public static long count(int[] prints, int length, List<Try> tries) {
        long[] found = new long[1];
        walk(prints.clone(), new char[length], 0, tries, found, null);
        return found[0];
    }

    /** De eerste volgorde (van klein naar groot) die nog kan, of null (voor de rondleiding). */
    public static @Nullable String firstPossible(int[] prints, int length, List<Try> tries) {
        String[] first = new String[1];
        walk(prints.clone(), new char[length], 0, tries, new long[1], first);
        return first[0];
    }

    /** Zet de cijfers één voor één op hun plek; bij een volle rij: past hij bij alle pogingen? */
    private static boolean walk(int[] left, char[] order, int at, List<Try> tries, long[] found, @Nullable String[] first) {
        if (at == order.length) {
            String candidate = new String(order);
            for (Try attempt : tries) {
                if (rightPlaces(attempt.code(), candidate) != attempt.right()) {
                    return false;
                }
            }
            found[0]++;
            if (first != null) {
                first[0] = candidate;
                return true; // genoeg: we zochten er maar één
            }
            return false;
        }
        for (int digit = 0; digit < 10; digit++) {
            if (left[digit] == 0) {
                continue;
            }
            left[digit]--;
            order[at] = (char) ('0' + digit);
            boolean stop = walk(left, order, at + 1, tries, found, first);
            left[digit]++;
            if (stop) {
                return true;
            }
        }
        return false;
    }
}
