package com.senne.starktech.logic.program;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Onthoudt voorwaarden die al eens gelezen zijn. Een timer die elke seconde
 * {@code if is_on("Lamp") and is_night:} controleert, hoeft die tekst dan niet elke keer opnieuw te
 * ontleden.
 */
public final class ExprCache {
    private ExprCache() {}

    /** Zoveel voorwaarden onthouden we hooguit; daarna beginnen we opnieuw. */
    private static final int MAX = 512;
    private static final Map<String, Optional<Expr>> CACHE = new HashMap<>();

    /** De voorwaarde, gelezen voor een diagram dat met deze trigger begint; null als hij niet klopt. */
    public static @Nullable Expr get(String text, @Nullable Op trigger) {
        String key = (trigger == null ? "" : trigger.name()) + "|" + text;
        Optional<Expr> cached = CACHE.get(key);
        if (cached == null) {
            if (CACHE.size() >= MAX) {
                CACHE.clear();
            }
            try {
                cached = Optional.of(Expr.parse(text, trigger));
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

    public static void clear() {
        CACHE.clear();
    }
}
