package com.senne.imttech.bypass;

import com.senne.imttech.config.ImtConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * De draden in een laserzender (hoofdstuk 33, jouw keuze: "draden volgen"): een kluwen. Links komen de draden
 * uit de stekkers (1, 2, 3...), rechts gaan ze naar een onderdeel van de zender: de LENS, en verder dingen als
 * ALARM, LAMP of FAN. Volg de draad die bij de LENS uitkomt terug naar zijn stekker en knip hem door: dan valt
 * de hele laserpoort uit. Knip je een andere, dan krijg je een schok en gaat de poort op slot.
 *
 * <pre>
 *   1 ===\ /=======\ /=== LAMP
 *   2 ====X===\ /===X==== LENS
 *   3 ===/ \===X===/ \=== ALARM
 *   4 ========/ \======== FAN
 * </pre>
 *
 * <b>Hoe de kluwen eruitziet.</b> Het is een "ladderspel": de kluwen bestaat uit kolommen, en in elke kolom
 * kruisen een paar draden hun buurdraad (de draad op rij 1 gaat schuin naar rij 2, en die van rij 2 schuin naar
 * rij 1: een X). Zo komt elke draad op een eigen plek rechts uit, en loopt er precies één draad naar de LENS.
 * Meer draden = meer kolommen, dus ook meer kruisingen.
 *
 * Deze klasse is alleen de puzzel (geen scherm), net als CircuitPuzzle: de server en je spel maken PRECIES
 * dezelfde kluwen uit hetzelfde getal ({@code seed}). Je spel stuurt alleen terug welke stekker je doorknipt; de
 * server rekent zelf na of dat de draad naar de LENS is.
 */
public final class WireTangle {
    /** Zo veel draden kan een kluwen hooguit hebben (net zoveel als er namen rechts zijn). */
    public static final int MAX_WIRES = 8;
    /** De kans dat een draad in een kolom zijn buurdraad kruist (als die nog vrij is). */
    private static final float CROSS_CHANCE = 0.55f;
    /** Zo vaak moet de draad naar de LENS minstens een andere draad kruisen (anders is hij te makkelijk). */
    public static final int MIN_LENS_CROSSINGS = 3;

    /** Wat er rechts staat, behalve de LENS (alleen namen: elke draad behalve die naar de LENS is fout). */
    private static final String[] DECOYS = {"ALARM", "LAMP", "FAN", "SENSOR", "GROUND", "BUZZER", "CAMERA"};
    public static final String LENS = "LENS";

    private final int wires;
    /** Per kolom: kruist de draad op rij r hier de draad op rij r + 1? */
    private final boolean[][] crossings;
    /** Per draad (de stekker links): op welke rij hij loopt aan het begin van elke kolom (en helemaal rechts). */
    private final int[][] rows;
    /** Wat er rechts staat, per rij. */
    private final String[] labels;
    private final int lensRow;
    private final int lensStart;

    private WireTangle(int wires, boolean[][] crossings, String[] labels, int lensRow) {
        this.wires = wires;
        this.crossings = crossings;
        this.labels = labels;
        this.lensRow = lensRow;
        this.rows = trace(wires, crossings);
        int start = 0;
        for (int wire = 0; wire < wires; wire++) {
            if (endRow(wire) == lensRow) {
                start = wire;
            }
        }
        this.lensStart = start;
    }

    // ================================================================ Hoeveel draden en hoeveel tijd

    /**
     * Zoveel draden heeft een laserpoort van zoveel zenders (jouw keuze: "groeit met de poort"): het aantal zenders
     * plus 3, tussen LASER_WIRES_MIN en LASER_WIRES_MAX uit de config (standaard 4 tot 8).
     */
    public static int wiresFor(int emitters) {
        int min = Math.max(2, Math.min(MAX_WIRES, Math.round(ImtConfig.Bypass.LASER_WIRES_MIN)));
        int max = Math.max(min, Math.min(MAX_WIRES, Math.round(ImtConfig.Bypass.LASER_WIRES_MAX)));
        return Math.max(min, Math.min(max, emitters + 3));
    }

    /**
     * Zoveel seconden krijg je voor een kluwen met zoveel draden: LASER_SECONDS_MIN bij de minste draden, oplopend
     * tot LASER_SECONDS_MAX bij de meeste (standaard 40 tot 60: 5 seconden per extra draad).
     */
    public static float secondsFor(int wires) {
        int min = Math.max(2, Math.min(MAX_WIRES, Math.round(ImtConfig.Bypass.LASER_WIRES_MIN)));
        int max = Math.max(min, Math.min(MAX_WIRES, Math.round(ImtConfig.Bypass.LASER_WIRES_MAX)));
        float fewest = ImtConfig.Bypass.LASER_SECONDS_MIN;
        float most = Math.max(fewest, ImtConfig.Bypass.LASER_SECONDS_MAX);
        if (max == min) {
            return fewest;
        }
        float t = Math.max(0, Math.min(1, (wires - min) / (float) (max - min)));
        return fewest + (most - fewest) * t;
    }

    /** Hetzelfde in ticks (20 per seconde). */
    public static int ticksFor(int wires) {
        return Math.round(secondsFor(wires) * 20);
    }

    // ================================================================ Maken

    /**
     * Maakt de kluwen. Hetzelfde getal en hetzelfde aantal draden geven altijd dezelfde kluwen (java.util.Random
     * rekent overal hetzelfde).
     *
     * Hoe: per kolom lopen we de rijen in een willekeurige volgorde af; een draad kruist met een vaste kans zijn
     * buurdraad eronder, als die twee in deze kolom nog vrij zijn (een draad kan in één kolom maar één keer kruisen).
     * Kruisten ze in de kolom ervoor ook al, dan is de kans kleiner: anders krijg je lange vlechten van steeds dezelfde
     * twee draden. Elke kolom krijgt minstens één kruising. Dan kiezen we waar rechts de LENS zit. Loopt de draad naar
     * de LENS (bijna) rechtdoor, of kruist hij te weinig, dan proberen we het opnieuw: zo moet je hem echt volgen.
     */
    public static WireTangle generate(long seed, int wires) {
        wires = Math.max(2, Math.min(MAX_WIRES, wires));
        Random random = new Random(seed * 131L + 17);
        int columns = wires * 2 + 2;
        List<Integer> order = new ArrayList<>();
        for (int row = 0; row < wires - 1; row++) {
            order.add(row);
        }
        WireTangle best = null;
        for (int attempt = 0; attempt < 60; attempt++) {
            boolean[][] crossings = new boolean[columns][wires - 1];
            for (int column = 0; column < columns; column++) {
                boolean any = false;
                Collections.shuffle(order, random);
                for (int row : order) {
                    boolean free = (row == 0 || !crossings[column][row - 1]) && (row == wires - 2 || !crossings[column][row + 1]);
                    float chance = column > 0 && crossings[column - 1][row] ? CROSS_CHANCE * 0.35f : CROSS_CHANCE;
                    if (free && random.nextFloat() < chance) {
                        crossings[column][row] = true;
                        any = true;
                    }
                }
                if (!any) {
                    // Een lege kolom ziet er niet uit als een kluwen: dan kruisen er toch twee.
                    int row = random.nextInt(wires - 1);
                    crossings[column][row] = true;
                }
            }
            int lensRow = random.nextInt(wires);
            List<String> decoys = new ArrayList<>(List.of(DECOYS));
            Collections.shuffle(decoys, random);
            String[] labels = new String[wires];
            for (int row = 0, next = 0; row < wires; row++) {
                labels[row] = row == lensRow ? LENS : decoys.get(next++);
            }
            WireTangle tangle = new WireTangle(wires, crossings, labels, lensRow);
            best = tangle;
            if (tangle.lensStart != lensRow && tangle.crossingsOf(tangle.lensStart) >= MIN_LENS_CROSSINGS) {
                return tangle;
            }
        }
        return best;
    }

    /** Volgt elke draad van links naar rechts: op welke rij hij aan het begin van elke kolom loopt. */
    private static int[][] trace(int wires, boolean[][] crossings) {
        int columns = crossings.length;
        int[][] rows = new int[wires][columns + 1];
        for (int wire = 0; wire < wires; wire++) {
            int row = wire;
            rows[wire][0] = row;
            for (int column = 0; column < columns; column++) {
                if (row < wires - 1 && crossings[column][row]) {
                    row++;
                } else if (row > 0 && crossings[column][row - 1]) {
                    row--;
                }
                rows[wire][column + 1] = row;
            }
        }
        return rows;
    }

    // ================================================================ Vragen

    public int wires() {
        return wires;
    }

    /** Zoveel kolommen met kruisingen heeft de kluwen. */
    public int columns() {
        return crossings.length;
    }

    /** Kruist de draad op rij {@code row} in deze kolom de draad op rij {@code row} + 1? */
    public boolean crosses(int column, int row) {
        return row >= 0 && row < wires - 1 && crossings[column][row];
    }

    /** Op welke rij draad {@code wire} (zijn stekker links) loopt aan het begin van kolom {@code column} (columns() = rechts). */
    public int rowAt(int wire, int column) {
        return rows[wire][column];
    }

    /** Waar draad {@code wire} rechts uitkomt. */
    public int endRow(int wire) {
        return rows[wire][crossings.length];
    }

    /** Hoe vaak draad {@code wire} een andere draad kruist. */
    public int crossingsOf(int wire) {
        int count = 0;
        for (int column = 0; column < crossings.length; column++) {
            if (rows[wire][column] != rows[wire][column + 1]) {
                count++;
            }
        }
        return count;
    }

    /** Wat er rechts op rij {@code row} staat (LENS, ALARM, LAMP...). */
    public String label(int row) {
        return labels[row];
    }

    /** Waar rechts de LENS zit. */
    public int lensRow() {
        return lensRow;
    }

    /** Het antwoord: de stekker van de draad die bij de LENS uitkomt. */
    public int lensStart() {
        return lensStart;
    }
}
