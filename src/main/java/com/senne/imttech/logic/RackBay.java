package com.senne.imttech.logic;

import com.senne.imttech.config.ImtConfig;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * Wat er in één vakje van een serverrack zit, zoals je het op de voorkant van het blok ziet (hoofdstuk 64).
 *
 * Jouw wens van 8 november: "ga van 3 rijen naar 4 rijen, dit geeft je in totaal 8 slots, 2 daarvan kan een CPU of GPU,
 * 2 daarvan kan SSD of RAM, 4 daarvan kunnen enkel fans", en je klikt de onderdelen er rechtstreeks in, zoals boeken in een
 * boekenkast. De voorkant (16 x 16 pixels), zoals je ervoor staat:
 * <pre>
 *   pixel  0      rand
 *          1-3    [ vakje 0 ][ vakje 1 ]   CPU of GPU
 *          5-7    [ vakje 2 ][ vakje 3 ]   RAM of SSD
 *          9-11   [ vakje 4 ][ vakje 5 ]   fan (twee ronde fannetjes per vakje)
 *          12-14  [ vakje 6 ][ vakje 7 ]   fan
 *          15     rand
 *   kolommen: pixel 2-7 (links) en 8-13 (rechts); op pixel 1 en 14 de rails
 * </pre>
 * De blokstaat van het rack heeft per vakje een eigenschap (bay_0 tot bay_7) met dit soort waarden; zo tekent Minecraft
 * het onderdeel in zijn vakje (blockstates/server_rack.json, gemaakt door tools/generate_block_models.py).
 */
public enum RackBay implements StringRepresentable {
    NONE("none", null),
    CPU("cpu", ServerPartItem.Kind.CPU),
    GPU("gpu", ServerPartItem.Kind.GPU),
    RAM("ram", ServerPartItem.Kind.RAM),
    SSD("ssd", ServerPartItem.Kind.SSD),
    FAN("fan", ServerPartItem.Kind.FAN);

    /** Waar elke rij vakjes begint en ophoudt op de voorkant (pixels van boven, het einde telt niet mee). */
    public static final int[][] ROW_PIXELS = {{1, 4}, {5, 8}, {9, 12}, {12, 15}};
    /** Waar de linker- en rechterkolom beginnen en ophouden (pixels van links zoals je ervoor staat). */
    public static final int[][] COLUMN_PIXELS = {{2, 8}, {8, 14}};

    private final String id;
    private final ServerPartItem.@Nullable Kind kind;

    RackBay(String id, ServerPartItem.@Nullable Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Het onderdeel in dit vakje, of null als het leeg is. */
    public ServerPartItem.@Nullable Kind kind() {
        return kind;
    }

    /** Het vakje met dit onderdeel erin. */
    public static RackBay of(ServerPartItem.Kind kind) {
        return switch (kind) {
            case CPU -> CPU;
            case GPU -> GPU;
            case RAM -> RAM;
            case SSD -> SSD;
            case FAN -> FAN;
        };
    }

    /** De rij van een vakje: 0 = CPU of GPU, 1 = RAM of SSD, 2 en 3 = fans. */
    public static int row(int slot) {
        return slot / 2;
    }

    /** Mag dit onderdeel in dit vakje? Bovenaan een CPU of GPU, dan RAM of een SSD, onderaan alleen fans. */
    public static boolean accepts(int slot, ServerPartItem.Kind kind) {
        if (slot < 0 || slot >= ImtConfig.Logic.RACK_SLOTS) {
            return false;
        }
        return switch (row(slot)) {
            case 0 -> kind == ServerPartItem.Kind.CPU || kind == ServerPartItem.Kind.GPU;
            case 1 -> kind == ServerPartItem.Kind.RAM || kind == ServerPartItem.Kind.SSD;
            default -> kind == ServerPartItem.Kind.FAN;
        };
    }

    /** De waarden die het vakje {@code slot} in de blokstaat kan hebben (leeg, en wat erin mag). */
    public static RackBay[] allowed(int slot) {
        return switch (row(slot)) {
            case 0 -> new RackBay[] {NONE, CPU, GPU};
            case 1 -> new RackBay[] {NONE, RAM, SSD};
            default -> new RackBay[] {NONE, FAN};
        };
    }

    /** Wat er in deze rij mag, in het Engels (voor de melding als iets niet past): "a CPU or GPU". */
    public static String rowText(int slot) {
        return switch (row(slot)) {
            case 0 -> "a CPU or GPU";
            case 1 -> "RAM or an SSD";
            default -> "a fan";
        };
    }

    /**
     * Welk vakje ligt op deze plek van de voorkant (in pixels van linksboven, zoals je ervoor staat)? Een klik op de rand
     * telt voor het dichtstbijzijnde vakje. Altijd een vakje van 0 tot 7.
     */
    public static int slotAt(double pixelX, double pixelY) {
        int column = pixelX < 8.0 ? 0 : 1;
        int row;
        if (pixelY < 4.5) {
            row = 0;
        } else if (pixelY < 8.5) {
            row = 1;
        } else if (pixelY < 12.0) {
            row = 2;
        } else {
            row = 3;
        }
        return row * 2 + column;
    }
}
