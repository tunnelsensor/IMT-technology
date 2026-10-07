package com.senne.starktech.logic.program;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Alle blokjes die je in een diagram kunt gebruiken, met hun naam in de code (Python-achtig, jouw
 * diagram-taal van 7 oktober). Er zijn drie soorten:
 * <ul>
 *   <li><b>triggers</b> ("when ..."): waar een diagram mee begint;</li>
 *   <li><b>if</b>: een keuze met een "yes"- en een "no"-uitgang;</li>
 *   <li><b>acties</b>: wat de server doet (een relais aan, wachten, een alarm...).</li>
 * </ul>
 * Elk blokje zegt welke gegevens het nodig heeft: een naam (tekst tussen aanhalingstekens) en/of
 * een getal (seconden).
 */
public enum Op {
    // ---------------------------------------------------------------- Triggers
    KEYCARD(Kind.TRIGGER, "keycard", Arg.DEVICE, Arg.NONE, "A keycard is held against this reader"),
    CODEPANEL(Kind.TRIGGER, "codepanel", Arg.DEVICE, Arg.NONE, "Someone types a code on this panel"),
    TURNED_ON(Kind.TRIGGER, "turned_on", Arg.DEVICE, Arg.NONE, "This relay (or laser, dome, turret) is switched on"),
    TURNED_OFF(Kind.TRIGGER, "turned_off", Arg.DEVICE, Arg.NONE, "This relay (or laser, dome, turret) is switched off"),
    EVERY(Kind.TRIGGER, "every", Arg.NONE, Arg.SECONDS, "Every so many seconds"),

    // ---------------------------------------------------------------- Keuze
    IF(Kind.IF, "if", Arg.NONE, Arg.NONE, "Check something: go on at yes or no"),

    // ---------------------------------------------------------------- Acties
    ON(Kind.ACTION, "on", Arg.DEVICE, Arg.NONE, "Switch a relay (or laser, dome, turret) on"),
    OFF(Kind.ACTION, "off", Arg.DEVICE, Arg.NONE, "Switch it off"),
    TOGGLE(Kind.ACTION, "toggle", Arg.DEVICE, Arg.NONE, "Switch it on if it is off, off if it is on"),
    PULSE(Kind.ACTION, "pulse", Arg.DEVICE, Arg.OPTIONAL_SECONDS, "Switch it on for a few seconds"),
    WAIT(Kind.ACTION, "wait", Arg.NONE, Arg.SECONDS, "Wait a few seconds before going on"),
    ACCEPT(Kind.ACTION, "accept", Arg.NONE, Arg.NONE, "Green light and a beep on the reader or panel"),
    DENY(Kind.ACTION, "deny", Arg.NONE, Arg.NONE, "Red light and a buzz on the reader or panel"),
    SAY(Kind.ACTION, "say", Arg.TEXT, Arg.NONE, "E.D.I.T.H. tells the owner something"),
    ALARM(Kind.ACTION, "alarm", Arg.TEXT, Arg.NONE, "Alarm: E.D.I.T.H. warns the owner and the security hunts the player"),
    LOG(Kind.ACTION, "log", Arg.TEXT, Arg.NONE, "Write a line in the security log");

    /** Wat voor blokje. */
    public enum Kind { TRIGGER, IF, ACTION }

    /** Wat voor gegevens. DEVICE en TEXT zijn tekst, SECONDS een getal. */
    public enum Arg { NONE, DEVICE, TEXT, SECONDS, OPTIONAL_SECONDS }

    /** Zo lang mag een tekst (say, alarm, log) of een naam hooguit zijn. */
    public static final int MAX_TEXT = 80;

    private final Kind kind;
    private final String code;
    private final Arg text;
    private final Arg number;
    private final String help;

    Op(Kind kind, String code, Arg text, Arg number, String help) {
        this.kind = kind;
        this.code = code;
        this.text = text;
        this.number = number;
        this.help = help;
    }

    public Kind kind() {
        return kind;
    }

    /** De naam in de code: {@code keycard}, {@code pulse}... */
    public String code() {
        return code;
    }

    /** Wat voor tekst dit blokje nodig heeft (een apparaat, een bericht), of NONE. */
    public Arg textArg() {
        return text;
    }

    /** Of dit blokje een getal (seconden) heeft: SECONDS (moet), OPTIONAL_SECONDS (mag) of NONE. */
    public Arg numberArg() {
        return number;
    }

    /** Uitleg in het Engels (in het blokjes-palet en bij fouten). */
    public String help() {
        return help;
    }

    public boolean isTrigger() {
        return kind == Kind.TRIGGER;
    }

    /** Het blokje met deze naam in de code, of null. */
    public static @Nullable Op byCode(String code) {
        for (Op op : values()) {
            if (op.code.equals(code)) {
                return op;
            }
        }
        return null;
    }

    /** Het blokje met deze naam zoals we hem opslaan (de enum-naam), of null. */
    public static @Nullable Op byName(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Het kleinste en grootste getal (seconden) dat dit blokje aanneemt. */
    public double minSeconds() {
        return this == EVERY ? 1 : 0.05;
    }

    public double maxSeconds() {
        return switch (this) {
            case EVERY -> 3600;
            case WAIT -> 600;
            default -> 60;
        };
    }

    /** Als het getal weggelaten mag worden (pulse), is dit de standaard. */
    public double defaultSeconds() {
        return 1;
    }

    /** Werken {@code accept()} en {@code deny()} hier? Alleen na een keycardlezer of codepaneel. */
    public boolean hasSourceDevice() {
        return this == KEYCARD || this == CODEPANEL;
    }
}
