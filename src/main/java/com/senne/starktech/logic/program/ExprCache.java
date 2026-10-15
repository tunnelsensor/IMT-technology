package com.senne.starktech.logic.program;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Onthoudt voorwaarden en waarden die al eens gelezen zijn. Een timer die elke seconde
 * {@code if is_on("Lamp") and is_night:} controleert, hoeft die tekst dan niet elke keer opnieuw te
 * ontleden.
 *
 * Hier lezen we soepel (zie {@link Expr#parseLenient}): een naam die we niet kennen is een variabele.
 * Wat een diagram mag, heeft de controle (ScriptChecker) al gezegd toen je het bewaarde.
 */
public final class ExprCache {
    private ExprCache() {}

    /** Zoveel voorwaarden onthouden we hooguit; daarna beginnen we opnieuw. */
    private static final int MAX = 512;
    private static final Map<String, Optional<Expr>> CACHE = new HashMap<>();

    /** De voorwaarde (een if), gelezen voor een diagram dat met deze trigger begint; null als hij niet klopt. */
    public static @Nullable Expr get(String text, @Nullable Op trigger) {
        return cached("?" + (trigger == null ? "" : trigger.name()) + "|" + text, text, true);
    }

    /** Een waarde (rechts van {@code naam =}), of null als hij niet klopt. */
    public static @Nullable Expr value(String text) {
        return cached("=" + text, text, false);
    }

    private static @Nullable Expr cached(String key, String text, boolean condition) {
        Optional<Expr> cached = CACHE.get(key);
        if (cached == null) {
            if (CACHE.size() >= MAX) {
                CACHE.clear();
            }
            try {
                cached = Optional.of(Expr.parseLenient(text, condition));
            } catch (LogicException e) {
                cached = Optional.empty();
            }
            CACHE.put(key, cached);
        }
        return cached.orElse(null);
    }

    /** Is de voorwaarde waar, met deze gegevens? */
    public static boolean test(Expr expr, Expr.Facts facts) {
        return Expr.test(expr, facts);
    }

    /** Wat deze waarde nu is (een Double, een String of een Boolean), met deze gegevens. */
    public static Object eval(Expr expr, Expr.Facts facts) {
        return Expr.value(expr, facts);
    }

    public static void clear() {
        CACHE.clear();
    }
}
