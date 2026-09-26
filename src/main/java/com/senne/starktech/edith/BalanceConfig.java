package com.senne.starktech.edith;

import com.senne.starktech.StarkTech;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Het configbestand met alle getallen van de mod: .minecraft/config/starktech-balance.toml (op
 * een server: de map config van de server). Dat geldt voor al je werelden. Moet één wereld
 * anders? Kopieer het bestand naar &lt;wereld&gt;/serverconfig/ en pas het daar aan: die kopie
 * gaat voor (zo werkt NeoForge).
 *
 * Hoe het werkt: we lopen alle balance-bestanden langs en zoeken de getallen met een
 * {@link Tunable}-etiket. Voor elk daarvan komt er een regel in het configbestand, met de
 * waarde uit de code als standaard. Laadt de wereld (of verandert iemand het bestand terwijl
 * het spel draait), dan zetten we de waarden uit het bestand in die getallen. De rest van de
 * mod merkt er niets van: die leest gewoon EdithBalance.SWARM_COST, zoals altijd.
 *
 * Op een server stuurt NeoForge dit bestand naar elke speler die binnenkomt. Zo laten het menu
 * en de HUD dezelfde kosten zien als de server rekent.
 *
 * Een nieuw getal instelbaar maken? Zet er {@code @Tunable(section = ..., comment = ...)} boven
 * en haal "final" weg. Meer hoeft niet.
 */
public final class BalanceConfig {
    private BalanceConfig() {}

    /** De bestanden waarin we naar etiketten zoeken. */
    private static final List<Class<?>> SOURCES = List.of(
            EdithBalance.class,
            com.senne.starktech.suit.SuitBalance.class,
            com.senne.starktech.turret.TurretBalance.class,
            com.senne.starktech.hydra.HydraBalance.class,
            com.senne.starktech.raid.RaidBalance.class,
            com.senne.starktech.arsenal.ArsenalBalance.class,
            com.senne.starktech.shield.DomeSettings.class,
            com.senne.starktech.security.SecurityBalance.class);

    /** Eén instelbaar getal: waar het staat, de regel in het bestand, en de standaardwaarde. */
    private record Entry(Field field, ModConfigSpec.ConfigValue<?> value, Object fallback) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();
    public static final ModConfigSpec SPEC = build();

    private static ModConfigSpec build() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        for (Class<?> source : SOURCES) {
            for (Field field : source.getDeclaredFields()) {
                Tunable tunable = field.getAnnotation(Tunable.class);
                if (tunable == null) {
                    continue;
                }
                if (!Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    throw new IllegalStateException(source.getSimpleName() + "." + field.getName()
                            + " heeft @Tunable, maar moet dan static en niet final zijn");
                }
                String path = tunable.section() + "." + camelCase(field.getName());
                builder.comment(tunable.comment());
                ENTRIES.add(define(builder, path, field));
            }
        }
        return builder.build();
    }

    /** Een regel in het bestand, met het juiste soort getal en de waarde uit de code als standaard. */
    private static Entry define(ModConfigSpec.Builder builder, String path, Field field) {
        try {
            Class<?> type = field.getType();
            if (type == int.class) {
                int value = field.getInt(null);
                return new Entry(field, builder.defineInRange(path, value, 0, Integer.MAX_VALUE), value);
            }
            if (type == long.class) {
                long value = field.getLong(null);
                return new Entry(field, builder.defineInRange(path, value, 0L, Long.MAX_VALUE), value);
            }
            if (type == double.class) {
                double value = field.getDouble(null);
                return new Entry(field, builder.defineInRange(path, value, 0.0, 1.0E9), value);
            }
            if (type == float.class) {
                double value = field.getFloat(null);
                return new Entry(field, builder.defineInRange(path, value, 0.0, 1.0E9), value);
            }
            if (type == boolean.class) {
                boolean value = field.getBoolean(null);
                return new Entry(field, builder.define(path, value), value);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Kan " + field + " niet lezen", e);
        }
        throw new IllegalStateException(field + ": @Tunable kan alleen bij int, long, double, float of boolean");
    }

    /** SWARM_COST wordt swarmCost. */
    private static String camelCase(String constant) {
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (char c : constant.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return out.toString();
    }

    // ================================================================ Laden

    /** Het bestand is (opnieuw) geladen: zet de waarden in de getallen van de mod. */
    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        boolean unloading = event instanceof ModConfigEvent.Unloading;
        for (Entry entry : ENTRIES) {
            // Wereld dicht: terug naar de standaard, zodat een volgende wereld schoon begint.
            apply(entry.field(), unloading ? entry.fallback() : entry.value().get());
        }
        StarkTech.LOGGER.info("[balance] {} getallen {}", ENTRIES.size(), unloading ? "terug naar standaard" : "geladen");
    }

    private static void apply(Field field, Object value) {
        try {
            Class<?> type = field.getType();
            if (type == int.class) {
                field.setInt(null, ((Number) value).intValue());
            } else if (type == long.class) {
                field.setLong(null, ((Number) value).longValue());
            } else if (type == double.class) {
                field.setDouble(null, ((Number) value).doubleValue());
            } else if (type == float.class) {
                field.setFloat(null, ((Number) value).floatValue());
            } else if (type == boolean.class) {
                field.setBoolean(null, (Boolean) value);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Kan " + field + " niet aanpassen", e);
        }
    }

    /** Zet de waarden uit het bestand nog eens in de getallen (voor de test). */
    public static void reapply() {
        for (Entry entry : ENTRIES) {
            apply(entry.field(), entry.value().get());
        }
    }

    /** Hoeveel getallen er in het bestand staan (voor de test). */
    public static int size() {
        return ENTRIES.size();
    }
}
