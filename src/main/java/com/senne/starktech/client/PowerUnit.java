package com.senne.starktech.client;

import com.senne.starktech.config.Numbers;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * In welke tijd de schermen stroom laten zien: per tick, per seconde, per minuut of per uur (jouw wens,
 * 11 oktober). De config rekent altijd in ticks; dit is alleen hoe je het leest. De knop "/tick" in de
 * stroomschermen (generator, koepel, Arc Lamp) wisselt het, en de keuze geldt meteen voor alle schermen
 * en het G-menu. Hij blijft bewaard in config/starktech-client.toml (powerUnit).
 */
public enum PowerUnit {
    TICK(1, "tick"),
    SECOND(20, "second"),
    MINUTE(20 * 60, "minute"),
    HOUR(20 * 60 * 60, "hour");

    private final int ticks;
    private final String id;

    PowerUnit(int ticks, String id) {
        this.ticks = ticks;
        this.id = id;
    }

    /** De eenheid die je nu gekozen hebt. */
    public static PowerUnit current() {
        return EdithClientConfig.powerUnit();
    }

    /** Hoeveel ticks er in deze eenheid zitten (een seconde: 20). */
    public int ticks() {
        return ticks;
    }

    /** De volgende in het rijtje: tick, seconde, minuut, uur, en weer tick. */
    public PowerUnit next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Een hoeveelheid per tick als tekst in deze eenheid: 0.3 per tick wordt "6 per second". */
    public Component rate(double perTick) {
        return Component.translatable("power.starktech.per_" + id, Numbers.text(perTick * ticks));
    }

    /** Het opschrift van de knop: "/tick", "/sec", "/min" of "/hour". */
    public Component label() {
        return Component.translatable("power.starktech.unit." + id);
    }

    /** De knop die de eenheid wisselt. Het scherm tekent zijn getallen elke frame opnieuw, dus meer hoeft niet. */
    public static Button button(int x, int y, int width, int height) {
        return Button.builder(current().label(), button -> {
                    EdithClientConfig.setPowerUnit(current().next());
                    button.setMessage(current().label());
                })
                .bounds(x, y, width, height)
                .tooltip(Tooltip.create(Component.translatable("power.starktech.unit.tooltip")))
                .build();
    }
}
