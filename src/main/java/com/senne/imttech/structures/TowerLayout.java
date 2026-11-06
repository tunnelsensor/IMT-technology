package com.senne.imttech.structures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Welke kamers een Biebia's Industries Tower krijgt, en in welke volgorde (hoofdstuk 51, 4 november). Jouw wens: "elke
 * toren is anders"; jouw antwoorden: de lobby, de kluis en het dak liggen vast, de rest is een willekeurige keuze,
 * volgorde en aantal (5 tot 8 verdiepingen), en er mochten een stuk of zes nieuwe kamers bij.
 *
 * De stukken staan in data/imttech/structure/tower/ (zie tools/generate_tower_rooms.py): onderaan altijd "lobby",
 * bovenaan altijd "top" (de lasergang, de kluis, het penthouse en het dak). Daartussen {@link #MIN_FLOORS} tot
 * {@link #MAX_FLOORS} tussenverdiepingen uit {@link #ROOMS}, elke soort hooguit één keer; "security" (de kern) zit er
 * altijd bij, want zonder kern is er geen beveiliging te hacken, en sinds hoofdstuk 54 ook "power" (de generatorkamer,
 * die de hele toren voedt: jouw notitie "zorg dat er altijd 1 generator room sowieso is").
 */
public final class TowerLayout {
    private TowerLayout() {}

    public static final String LOBBY = "lobby";
    public static final String TOP = "top";
    public static final String SECURITY = "security";
    public static final String POWER = "power";
    /** De tussenverdiepingen waaruit gekozen wordt (behalve security en power, die er altijd in zitten). */
    public static final List<String> ROOMS = List.of("lab", "archive", "hangar", "living", "gym", "greenhouse", "cinema",
            "pool", "museum", "server_farm");
    public static final int MIN_FLOORS = 5;
    public static final int MAX_FLOORS = 8;

    /** Hoe hoog de stukken zijn, en hoeveel hoger het volgende stuk begint (de vloerplaten overlappen één rij). */
    public static final int LOBBY_HEIGHT = 13;
    public static final int FLOOR_STEP = 11;
    public static final int MIDDLE_HEIGHT = 12;
    public static final int TOP_HEIGHT = 44;

    /** Een willekeurige toren: ["lobby", 5 tot 8 kamers (security en power erbij), "top"]. */
    public static List<String> random(Random random) {
        int floors = MIN_FLOORS + random.nextInt(MAX_FLOORS - MIN_FLOORS + 1);
        List<String> pool = new ArrayList<>(ROOMS);
        Collections.shuffle(pool, random);
        List<String> middle = new ArrayList<>(pool.subList(0, floors - 2));
        middle.add(random.nextInt(floors - 1), SECURITY);
        middle.add(random.nextInt(floors), POWER);
        List<String> layout = new ArrayList<>();
        layout.add(LOBBY);
        layout.addAll(middle);
        layout.add(TOP);
        return layout;
    }

    /** Hoeveel blokken boven de voet van de toren stuk {@code index} van deze indeling begint. */
    public static int baseOf(int index) {
        return index == 0 ? 0 : FLOOR_STEP * index;
    }

    /** Hoe hoog de hele toren is (van het plein tot de antenne). */
    public static int height(List<String> layout) {
        return baseOf(layout.size() - 1) + TOP_HEIGHT;
    }

    /** Klopt deze indeling met de regels hierboven? (Voor de tests.) */
    public static boolean valid(List<String> layout) {
        if (layout.size() < MIN_FLOORS + 2 || layout.size() > MAX_FLOORS + 2
                || !LOBBY.equals(layout.getFirst()) || !TOP.equals(layout.getLast())) {
            return false;
        }
        List<String> middle = layout.subList(1, layout.size() - 1);
        return middle.stream().filter(SECURITY::equals).count() == 1
                && middle.stream().filter(POWER::equals).count() == 1
                && middle.stream().distinct().count() == middle.size()
                && middle.stream().allMatch(room -> SECURITY.equals(room) || POWER.equals(room) || ROOMS.contains(room));
    }
}
