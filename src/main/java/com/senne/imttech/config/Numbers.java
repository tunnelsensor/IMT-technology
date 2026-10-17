package com.senne.imttech.config;

import java.util.Locale;

/**
 * Getallen uit de config netjes als tekst, voor schermen en berichten. Alles in ImtConfig mag een
 * kommagetal zijn, maar "1500.0 SE" leest raar: daarom zonder nullen die niets zeggen.
 */
public final class Numbers {
    private Numbers() {}

    /** 1500 wordt "1500", 0.3 wordt "0.3", 0.125 wordt "0.13" (hooguit twee cijfers na de komma). */
    public static String text(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
