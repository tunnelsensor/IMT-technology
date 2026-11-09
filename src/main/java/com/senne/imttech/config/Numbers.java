package com.senne.imttech.config;

import java.util.Locale;
import net.minecraft.world.entity.player.Player;

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

    /**
     * Een hoeveelheid stroom (FE, of FE per tick/seconde/...) zoals jij getallen wilt zien: "5 000", "5.000" of "5k"
     * (hoofdstuk 66, zie NumberStyle). Voor wat op jouw eigen scherm komt: schermen, tooltips, het G-menu.
     */
    public static String amount(double value) {
        return NumberStyle.local().format(value);
    }

    /** Hetzelfde voor een melding van de server aan deze speler: in de stijl die híj gekozen heeft. */
    public static String amount(Player player, double value) {
        return NumberStyle.of(player).format(value);
    }
}
