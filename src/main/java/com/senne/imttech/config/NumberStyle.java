package com.senne.imttech.config;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.Player;

/**
 * Hoe grote getallen (stroom in FE) op je scherm staan (hoofdstuk 66, jouw wens van 9 november: "de optie of scheiding
 * met spatie 5 000 of met punt 5.000 of gebruik grootheden zoals 5k FE"). Elke speler kiest zelf: het knopje naast /tick
 * in de stroomschermen, of G > Information > Numbers. De keuze staat in config/imttech-client.toml (numberStyle).
 *
 * <p>Meldingen van de server ("Not enough energy. Required: 5 000 FE.") volgen dezelfde keuze: je spel vertelt de server
 * bij het inloggen en bij elke wissel welke stijl je hebt (NumberStylePayload), en de server onthoudt dat per speler
 * tot je uitlogt ({@link #of(Player)}).
 */
public enum NumberStyle {
    /** 5000000: zonder scheiding, zoals vroeger. */
    PLAIN,
    /** 5 000 000: een spatie tussen elke drie cijfers (een harde spatie, zodat een getal nooit over twee regels breekt). */
    SPACE,
    /** 5.000.000: een punt tussen elke drie cijfers, en dan een komma voor wat achter de komma komt (0,5). */
    DOT,
    /** 5,000,000: een komma tussen elke drie cijfers, zoals in het Engels. */
    COMMA,
    /** 5M: kort, met k (duizend), M (miljoen), G (miljard) en T (biljoen), in drie cijfers: 2.46k, 24.6k, 246k. */
    SHORT;

    /** Wat je hebt zolang je niets kiest (en wat de server gebruikt voor wie nog niets liet weten). */
    public static final NumberStyle DEFAULT = SPACE;
    /**
     * De spatie van SPACE: een harde spatie (U+00A0). Minecraft breekt een regel alleen op een gewone spatie, dus zo
     * blijft "1 728 000" bij elkaar. Ons lettertype geeft hem dezelfde breedte als een gewone spatie
     * (assets/minecraft/font/include/space.json).
     */
    public static final char HARD_SPACE = '\u00A0';
    private static final String[] UNITS = {"", "k", "M", "G", "T"};

    /** Een getal in deze stijl. Hooguit twee cijfers na de komma, zonder nullen die niets zeggen (zoals Numbers.text). */
    public String format(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        String text = switch (this) {
            case PLAIN -> Numbers.text(Math.abs(value));
            case SPACE -> grouped(Math.abs(value), HARD_SPACE, '.');
            case DOT -> grouped(Math.abs(value), '.', ',');
            case COMMA -> grouped(Math.abs(value), ',', '.');
            case SHORT -> shortened(Math.abs(value));
        };
        // Een min ervoor, maar niet voor iets dat afgerond nul is ("-0").
        return value < 0 && text.chars().anyMatch(c -> c >= '1' && c <= '9') ? "-" + text : text;
    }

    /** De volgende in het rijtje van het knopje: 5000, 5 000, 5.000, 5,000, 5k, en weer 5000. */
    public NumberStyle next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Het opschrift van het knopje en van de optie in het G-menu: 5000 in deze stijl ("5 000", "5k"...). */
    public String example() {
        return format(5000);
    }

    /** 1234567.5 met deze tekens: "1 234 567.5" of "1.234.567,5". */
    private static String grouped(double value, char group, char decimal) {
        String text = Numbers.text(value);
        int dot = text.indexOf('.');
        String whole = dot < 0 ? text : text.substring(0, dot);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < whole.length(); i++) {
            if (i > 0 && (whole.length() - i) % 3 == 0) {
                out.append(group);
            }
            out.append(whole.charAt(i));
        }
        if (dot >= 0) {
            out.append(decimal).append(text, dot + 1, text.length());
        }
        return out.toString();
    }

    /** Kort met k, M, G of T, in drie cijfers die tellen: 2456 = "2.46k", 24600 = "24.6k", 1728000 = "1.73M". */
    private static String shortened(double value) {
        if (value < 999.995) {
            return Numbers.text(value); // onder de duizend: gewoon het getal (afgerond 1000 is al "1k")
        }
        int unit = 0;
        double scaled = value;
        while (scaled >= 999.5 && unit < UNITS.length - 1) {
            scaled /= 1000;
            unit++;
        }
        int decimals = scaled < 9.995 ? 2 : scaled < 99.95 ? 1 : 0;
        String text = String.format(Locale.ROOT, "%." + decimals + "f", scaled);
        if (text.contains(".")) {
            text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return text + UNITS[unit];
    }

    /**
     * Een tekst om uit te spreken: de getallen in deze stijl terug naar gewone cijfers, anders leest de stem "1 500" als
     * "een vijfhonderd" en "1.500" als "een komma vijf". "5k" wordt "5 thousand".
     */
    public String spoken(String text) {
        return switch (this) {
            case PLAIN -> text;
            case SPACE -> text.replaceAll("(?<=\\d)\u00A0(?=\\d{3}(?!\\d))", "");
            case DOT -> text.replaceAll("(?<=\\d)\\.(?=\\d{3}(?!\\d))", "").replaceAll("(?<=\\d),(?=\\d)", ".");
            case COMMA -> text.replaceAll("(?<=\\d),(?=\\d{3}(?!\\d))", "");
            case SHORT -> text.replaceAll("(?<=\\d)k\\b", " thousand").replaceAll("(?<=\\d)M\\b", " million")
                    .replaceAll("(?<=\\d)G\\b", " billion").replaceAll("(?<=\\d)T\\b", " trillion");
        };
    }

    // ---------------------------------------------------------------- Wie welke stijl heeft

    /** Waar je eigen keuze vandaan komt (de client zet dit bij het opstarten; een server zonder scherm houdt DEFAULT). */
    private static Supplier<NumberStyle> local = () -> DEFAULT;
    /** Wat elke speler op deze server gekozen heeft (van NumberStylePayload). */
    private static final Map<UUID, NumberStyle> PLAYERS = new ConcurrentHashMap<>();

    /** Jouw eigen keuze, voor schermen en tooltips op jouw scherm. */
    public static NumberStyle local() {
        NumberStyle style = local.get();
        return style == null ? DEFAULT : style;
    }

    /** Vertelt waar jouw eigen keuze staat (alleen de client: ImtTechClient). */
    public static void useLocal(Supplier<NumberStyle> source) {
        local = source;
    }

    /** Wat deze speler gekozen heeft, voor een melding van de server aan hem (nog niets gehoord: DEFAULT). */
    public static NumberStyle of(Player player) {
        return player == null ? DEFAULT : PLAYERS.getOrDefault(player.getUUID(), DEFAULT);
    }

    /** De server hoort welke stijl deze speler gekozen heeft. */
    public static void remember(Player player, NumberStyle style) {
        PLAYERS.put(player.getUUID(), style);
    }

    /** De speler logt uit: vergeten (bij de volgende keer inloggen vertelt zijn spel het opnieuw). */
    public static void forget(UUID player) {
        PLAYERS.remove(player);
    }
}
