package com.senne.imttech.structures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Welke zalen een Biebia's Industries-bunker krijgt, en in welke volgorde (hoofdstuk 51, 4 november). Jouw antwoorden:
 * de ingang en de kluis liggen vast, daartussen 3 tot 6 zalen in een willekeurige keuze en volgorde.
 *
 * De stukken staan in data/imttech/structure/bunker/ (zie tools/generate_bunker_rooms.py): vooraan altijd "entrance",
 * achteraan altijd "vault". Daartussen {@link #MIN_HALLS} tot {@link #MAX_HALLS} zalen uit {@link #HALLS}, elke soort
 * hooguit één keer; "reactor" zit er altijd bij, want die geeft de laserwanden en de torentjes hun stroom.
 */
public final class BunkerLayout {
    private BunkerLayout() {}

    public static final String ENTRANCE = "entrance";
    public static final String VAULT = "vault";
    public static final String REACTOR = "reactor";
    /** De zalen waaruit gekozen wordt (behalve de reactor, die er altijd in zit). */
    public static final List<String> HALLS = List.of("hall", "armory", "lab", "cells", "garage", "mess", "comms");
    public static final int MIN_HALLS = 3;
    public static final int MAX_HALLS = 6;

    /** Hoe diep de stukken zijn (langs de as van de bunker). */
    public static final int END_DEPTH = 15;
    public static final int HALL_DEPTH = 14;

    /** Een willekeurige bunker met {@code halls} zalen: ["entrance", de zalen (reactor erbij), "vault"]. */
    public static List<String> random(Random random, int halls) {
        int count = Math.max(MIN_HALLS, Math.min(MAX_HALLS, halls));
        List<String> pool = new ArrayList<>(HALLS);
        Collections.shuffle(pool, random);
        List<String> middle = new ArrayList<>(pool.subList(0, count - 1));
        middle.add(random.nextInt(count), REACTOR);
        List<String> layout = new ArrayList<>();
        layout.add(ENTRANCE);
        layout.addAll(middle);
        layout.add(VAULT);
        return layout;
    }

    /** Een willekeurige bunker met een willekeurig aantal zalen. */
    public static List<String> random(Random random) {
        return random(random, MIN_HALLS + random.nextInt(MAX_HALLS - MIN_HALLS + 1));
    }

    /** Hoeveel blokken achter de ingang stuk {@code index} begint. */
    public static int offsetOf(List<String> layout, int index) {
        int offset = 0;
        for (int i = 0; i < index; i++) {
            offset += depthOf(layout.get(i));
        }
        return offset;
    }

    public static int depthOf(String piece) {
        return ENTRANCE.equals(piece) || VAULT.equals(piece) ? END_DEPTH : HALL_DEPTH;
    }

    /** Hoe diep de hele bunker is. */
    public static int depth(List<String> layout) {
        return offsetOf(layout, layout.size());
    }

    /** Klopt deze indeling met de regels hierboven? (Voor de tests.) */
    public static boolean valid(List<String> layout) {
        if (layout.size() < MIN_HALLS + 2 || layout.size() > MAX_HALLS + 2
                || !ENTRANCE.equals(layout.getFirst()) || !VAULT.equals(layout.getLast())) {
            return false;
        }
        List<String> middle = layout.subList(1, layout.size() - 1);
        return middle.stream().filter(REACTOR::equals).count() == 1
                && middle.stream().distinct().count() == middle.size()
                && middle.stream().allMatch(hall -> REACTOR.equals(hall) || HALLS.contains(hall));
    }
}
