package com.senne.starktech.config;

import com.senne.starktech.StarkTech;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Maakt van {@link StarkConfig} het configbestand config/starktech.toml, en zet de waarden uit dat
 * bestand terug in de getallen van StarkConfig. Hier hoef je zelf niets aan te doen: een getal toevoegen
 * doe je in StarkConfig.
 *
 * Hoe het werkt:
 * 1. Bij het opstarten lopen we alle hoofdstukken van StarkConfig langs (Rules, Glasses, ...). Elk
 *    hoofdstuk wordt een kopje in het bestand, zoals [satellite], met de uitleg van {@link Section}.
 * 2. Elk getal zonder "final" wordt een regel onder dat kopje, met de uitleg van {@link Tunable} en de
 *    waarde uit de code als standaard. SWARM_COST wordt swarmCost.
 * 3. Laadt er een wereld (of slaat iemand het bestand op terwijl het spel draait), dan zetten we de
 *    waarden uit het bestand in de getallen. De rest van de mod merkt er niets van: die leest gewoon
 *    StarkConfig.Drones.SWARM_COST, zoals altijd.
 * 4. Gaat de wereld dicht, dan zetten we alles terug op de standaard, zodat de volgende wereld schoon begint.
 *
 * Het is een SERVER-config: op een server stuurt NeoForge het bestand naar elke speler die binnenkomt,
 * zodat het menu en de HUD dezelfde kosten laten zien als de server rekent.
 */
public final class StarkConfigLoader {
    private StarkConfigLoader() {}

    /** De naam van het bestand in de map config (en, voor één wereld, in serverconfig). */
    public static final String FILE_NAME = "starktech.toml";

    /**
     * De hoofdstukken, in de volgorde waarin ze in het bestand komen. Vergeet je er hier een, dan zegt
     * het spel dat bij het opstarten (zie {@link #build}).
     */
    private static final List<Class<?>> SECTIONS = List.of(
            StarkConfig.Rules.class,
            StarkConfig.Glasses.class,
            StarkConfig.Scanner.class,
            StarkConfig.Power.class,
            StarkConfig.Drones.class,
            StarkConfig.Satellite.class,
            StarkConfig.Recon.class,
            StarkConfig.Transport.class,
            StarkConfig.Illusions.class,
            StarkConfig.Shields.class,
            StarkConfig.Dome.class,
            StarkConfig.Hack.class,
            StarkConfig.Relays.class,
            StarkConfig.Comms.class,
            StarkConfig.Navigation.class,
            StarkConfig.Holo.class,
            StarkConfig.Suit.class,
            StarkConfig.Workbench.class,
            StarkConfig.Turrets.class,
            StarkConfig.Security.class,
            StarkConfig.Logic.class,
            StarkConfig.C4.class,
            StarkConfig.Repair.class,
            StarkConfig.Bypass.class,
            StarkConfig.Lights.class,
            StarkConfig.Robots.class,
            StarkConfig.Hydra.class,
            StarkConfig.Raids.class);

    /** Eén instelbaar getal: waar het staat, de regel in het bestand, en de standaardwaarde. */
    private record Entry(Field field, ModConfigSpec.ConfigValue<?> value, Object fallback) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();
    public static final ModConfigSpec SPEC = build();

    private static ModConfigSpec build() {
        for (Class<?> nested : StarkConfig.class.getDeclaredClasses()) {
            if (!SECTIONS.contains(nested)) {
                throw new IllegalStateException("StarkConfig." + nested.getSimpleName()
                        + " staat niet in StarkConfigLoader.SECTIONS");
            }
        }
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        for (Class<?> section : SECTIONS) {
            Section about = section.getAnnotation(Section.class);
            if (about == null) {
                throw new IllegalStateException("StarkConfig." + section.getSimpleName() + " heeft geen @Section");
            }
            builder.comment(about.value()).push(camelCase(section.getSimpleName()));
            for (Field field : section.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || field.isSynthetic()) {
                    continue; // "final" = vast, niet in het bestand
                }
                Tunable tunable = field.getAnnotation(Tunable.class);
                if (tunable == null) {
                    throw new IllegalStateException("StarkConfig." + section.getSimpleName() + "." + field.getName()
                            + " is instelbaar (geen final), maar heeft geen @Tunable met uitleg");
                }
                ENTRIES.add(define(builder, camelCase(field.getName()), field, tunable));
            }
            builder.pop();
        }
        return builder.build();
    }

    /** Een regel in het bestand, met het juiste soort getal en de waarde uit de code als standaard. */
    private static Entry define(ModConfigSpec.Builder builder, String key, Field field, Tunable tunable) {
        double min = tunable.min();
        double max = tunable.max();
        try {
            Class<?> type = field.getType();
            builder.comment(tunable.value());
            if (type == int.class) {
                int value = field.getInt(null);
                return new Entry(field, builder.defineInRange(key, value, (int) min, (int) max), value);
            }
            if (type == long.class) {
                long value = field.getLong(null);
                return new Entry(field, builder.defineInRange(key, value, (long) min, (long) max), value);
            }
            if (type == double.class || type == float.class) {
                // Een float (zoals 4.0f) bewaren we als double: 0.1f wordt dan 0.10000000149, dus eerst via tekst.
                double value = type == float.class ? Double.parseDouble(Float.toString(field.getFloat(null))) : field.getDouble(null);
                return new Entry(field, builder.defineInRange(key, value, min, max), value);
            }
            if (type == boolean.class) {
                boolean value = field.getBoolean(null);
                builder.comment(" Default: " + value);
                return new Entry(field, builder.define(key, value), value);
            }
            if (type.isArray()) {
                return defineList(builder, key, field, type.getComponentType(), min, max);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Kan " + field + " niet lezen", e);
        }
        throw new IllegalStateException(field + ": @Tunable kan alleen bij int, long, double, float, boolean"
                + " of een rijtje int[], double[] of float[]");
    }

    /** Een rijtje (één getal per niveau): het aantal getallen ligt vast, elk getal moet tussen min en max. */
    private static Entry defineList(ModConfigSpec.Builder builder, String key, Field field, Class<?> part,
                                    double min, double max) throws IllegalAccessException {
        Object array = field.get(null);
        boolean whole = part == int.class;
        List<Number> fallback = new ArrayList<>();
        if (part == int.class) {
            for (int v : (int[]) array) fallback.add(v);
        } else if (part == double.class) {
            for (double v : (double[]) array) fallback.add(v);
        } else if (part == float.class) {
            for (float v : (float[]) array) fallback.add(Double.parseDouble(Float.toString(v)));
        } else {
            throw new IllegalStateException(field + ": een rijtje kan alleen int[], double[] of float[] zijn");
        }
        int size = fallback.size();
        List<Number> standard = List.copyOf(fallback);
        Predicate<Object> valid = o -> o instanceof Number n && n.doubleValue() >= min && n.doubleValue() <= max;
        builder.comment(" Default: " + standard,
                " Range: " + size + " numbers, each " + number(min, whole) + " ~ " + number(max, whole));
        ModConfigSpec.ConfigValue<?> value = builder.defineList(List.of(key), () -> standard,
                () -> whole ? (Number) 0 : (Number) 0.0, valid, ModConfigSpec.Range.of(size, size));
        return new Entry(field, value, copy(array));
    }

    private static String number(double value, boolean whole) {
        return whole ? Long.toString((long) value) : Double.toString(value);
    }

    /** SWARM_COST wordt swarmCost, en Satellite wordt satellite. */
    private static String camelCase(String name) {
        if (!name.contains("_") && !name.equals(name.toUpperCase(Locale.ROOT))) {
            return Character.toLowerCase(name.charAt(0)) + name.substring(1); // een klasse: Satellite
        }
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
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

    /** Het bestand is (opnieuw) geladen, of de wereld gaat dicht: zet de waarden in de getallen van StarkConfig. */
    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        boolean unloading = event instanceof ModConfigEvent.Unloading;
        for (Entry entry : ENTRIES) {
            // Wereld dicht: terug naar de standaard, zodat een volgende wereld schoon begint.
            apply(entry, unloading ? entry.fallback() : entry.value().get());
        }
        StarkTech.LOGGER.info("[config] {} getallen uit {} {}", ENTRIES.size(), FILE_NAME,
                unloading ? "terug naar standaard" : "geladen");
    }

    /** Zet de waarden uit het bestand nog eens in de getallen (voor de tests). */
    public static void reapply() {
        for (Entry entry : ENTRIES) {
            apply(entry, entry.value().get());
        }
    }

    /** Hoeveel getallen er in het bestand staan (voor de tests). */
    public static int size() {
        return ENTRIES.size();
    }

    private static void apply(Entry entry, Object value) {
        Field field = entry.field();
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
            } else if (type.isArray()) {
                field.set(null, toArray(entry, value));
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Kan " + field + " niet aanpassen", e);
        }
    }

    /** Een rijtje uit het bestand als int[], double[] of float[]. Klopt het aantal niet, dan de standaard. */
    private static Object toArray(Entry entry, Object value) {
        int size = java.lang.reflect.Array.getLength(entry.fallback());
        if (!(value instanceof List<?> list) || list.size() != size || !list.stream().allMatch(o -> o instanceof Number)) {
            if (value instanceof List<?>) {
                StarkTech.LOGGER.warn("[config] {} moet {} getallen hebben; de standaard wordt gebruikt",
                        entry.field().getName(), size);
            }
            return copy(entry.fallback());
        }
        Class<?> part = entry.field().getType().getComponentType();
        if (part == int.class) {
            int[] out = new int[size];
            for (int i = 0; i < size; i++) out[i] = ((Number) list.get(i)).intValue();
            return out;
        }
        if (part == float.class) {
            float[] out = new float[size];
            for (int i = 0; i < size; i++) out[i] = ((Number) list.get(i)).floatValue();
            return out;
        }
        double[] out = new double[size];
        for (int i = 0; i < size; i++) out[i] = ((Number) list.get(i)).doubleValue();
        return out;
    }

    /** Een kopie van een rijtje, zodat niemand per ongeluk de standaard aanpast. */
    private static Object copy(Object array) {
        if (array instanceof int[] a) return Arrays.copyOf(a, a.length);
        if (array instanceof float[] a) return Arrays.copyOf(a, a.length);
        return Arrays.copyOf((double[]) array, ((double[]) array).length);
    }
}
