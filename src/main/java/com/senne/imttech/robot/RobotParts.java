package com.senne.imttech.robot;

import com.senne.imttech.config.ImtConfig;
import net.minecraft.util.RandomSource;

/**
 * Waaruit een guard robot bestaat (hoofdstuk 21): een stijl per onderdeel. Je mag ze mengen: een hoofd
 * van een Commando op een lijf van een Super Battle kan gewoon.
 *
 * Elk onderdeel doet een beetje iets anders (de getallen staan in de config onder {@code [robots]}, één
 * per stijl): het lijf geeft leven, pantser en batterij, de benen de snelheid, de armen hoe snel hij
 * schiet, en het hoofd hoe ver en hoe breed hij kijkt.
 */
public record RobotParts(RobotStyle head, RobotStyle body, RobotStyle arms, RobotStyle legs) {
    /** Een robot zonder bijzondere keuze: helemaal Battle. */
    public static final RobotParts DEFAULT = new RobotParts(RobotStyle.BATTLE, RobotStyle.BATTLE, RobotStyle.BATTLE, RobotStyle.BATTLE);

    /** De stijl van dit onderdeel. */
    public RobotStyle get(RobotSlot slot) {
        return switch (slot) {
            case HEAD -> head;
            case BODY -> body;
            case ARMS -> arms;
            case LEGS -> legs;
        };
    }

    /** Dezelfde robot, met dit onderdeel in een andere stijl. */
    public RobotParts with(RobotSlot slot, RobotStyle style) {
        return new RobotParts(slot == RobotSlot.HEAD ? style : head, slot == RobotSlot.BODY ? style : body,
                slot == RobotSlot.ARMS ? style : arms, slot == RobotSlot.LEGS ? style : legs);
    }

    /** Alle vier in één getal (2 bits per onderdeel): zo bewaren we ze en sturen we ze naar je spel. */
    public int pack() {
        return head.ordinal() | body.ordinal() << 2 | arms.ordinal() << 4 | legs.ordinal() << 6;
    }

    /** Het omgekeerde van {@link #pack}. */
    public static RobotParts unpack(int bits) {
        return new RobotParts(RobotStyle.byIndex(bits & 3), RobotStyle.byIndex(bits >> 2 & 3),
                RobotStyle.byIndex(bits >> 4 & 3), RobotStyle.byIndex(bits >> 6 & 3));
    }

    /** Vier willekeurige onderdelen (een robot uit een spawn-ei in creative). */
    public static RobotParts random(RandomSource random) {
        return unpack(random.nextInt(256));
    }

    // ================================================================ Wat de onderdelen doen

    /** Levenspunten: van het lijf. */
    public double health() {
        return value(ImtConfig.Robots.BODY_HEALTH, body);
    }

    /** Pantser: van het lijf. */
    public double armor() {
        return value(ImtConfig.Robots.BODY_ARMOR, body);
    }

    /** Hoeveel energie zijn batterij hoogstens heeft: van het lijf. */
    public double battery() {
        return value(ImtConfig.Robots.BODY_BATTERY, body);
    }

    /** Hoe snel hij loopt: van de benen. */
    public double speed() {
        return value(ImtConfig.Robots.LEGS_SPEED, legs);
    }

    /** Ticks tussen twee schoten: van de armen. */
    public double fireInterval() {
        return value(ImtConfig.Robots.ARMS_FIRE_INTERVAL, arms);
    }

    /** Hoe ver hij ziet (blokken): van het hoofd. */
    public double scanRange() {
        return value(ImtConfig.Robots.HEAD_SCAN_RANGE, head);
    }

    /** Hoe breed hij kijkt (graden; 360 = rondom): van het hoofd. */
    public double scanAngle() {
        return value(ImtConfig.Robots.HEAD_SCAN_ANGLE, head);
    }

    /** Het getal voor deze stijl uit een lijst van de config (te kort? dan het laatste getal). */
    private static double value(float[] list, RobotStyle style) {
        return list.length == 0 ? 0 : list[Math.min(style.ordinal(), list.length - 1)];
    }
}
