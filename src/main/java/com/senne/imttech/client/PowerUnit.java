package com.senne.imttech.client;

import com.senne.imttech.config.NumberStyle;
import com.senne.imttech.config.Numbers;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * In welke tijd de schermen stroom laten zien: per tick, per seconde, per minuut of per uur (jouw wens,
 * 11 oktober). De config rekent altijd in ticks; dit is alleen hoe je het leest. De knop "/tick" in de
 * stroomschermen (generator, koepel, Arc Lamp) wisselt het, en de keuze geldt meteen voor alle schermen
 * en het G-menu. Hij blijft bewaard in config/imttech-client.toml (powerUnit). Het knopje ernaast ({@link #styleButton})
 * kiest hoe grote getallen eruitzien: 5000, 5 000, 5.000, 5,000 of 5k (hoofdstuk 66).
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
        return ImtClientConfig.powerUnit();
    }

    /** Hoeveel ticks er in deze eenheid zitten (een seconde: 20). */
    public int ticks() {
        return ticks;
    }

    /** De volgende in het rijtje: tick, seconde, minuut, uur, en weer tick. */
    public PowerUnit next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /**
     * Een hoeveelheid per tick als tekst in deze eenheid: 0.3 per tick wordt "6 per second", en 24 per tick per uur
     * "1 728 000 per hour" (of "1.728.000", "1.73M": zoals je getallen wilt zien, hoofdstuk 66).
     */
    public Component rate(double perTick) {
        return Component.translatable("power.imttech.per_" + id, Numbers.amount(perTick * ticks));
    }

    /** Het opschrift van de knop: "/tick", "/sec", "/min" of "/hour". */
    public Component label() {
        return Component.translatable("power.imttech.unit." + id);
    }

    /** De knop die de eenheid wisselt. Het scherm tekent zijn getallen elke frame opnieuw, dus meer hoeft niet. */
    public static Button button(int x, int y, int width, int height) {
        return Button.builder(current().label(), button -> {
                    ImtClientConfig.setPowerUnit(current().next());
                    button.setMessage(current().label());
                })
                .bounds(x, y, width, height)
                .tooltip(Tooltip.create(Component.translatable("power.imttech.unit.tooltip")))
                .build();
    }

    /**
     * Het knopje naast /tick dat wisselt hoe grote getallen eruitzien (hoofdstuk 66): 5000, 5 000, 5.000, 5,000 of 5k.
     * Op het knopje staat 5000 in de stijl die je nu hebt. De keuze geldt meteen voor alle schermen en het G-menu, en de
     * server hoort het (voor zijn meldingen).
     */
    public static Button styleButton(int x, int y, int width, int height) {
        return Button.builder(Component.literal(NumberStyle.local().example()), button -> {
                    ImtClientConfig.setNumberStyle(NumberStyle.local().next());
                    button.setMessage(Component.literal(NumberStyle.local().example()));
                })
                .bounds(x, y, width, height)
                .tooltip(Tooltip.create(Component.translatable("power.imttech.style.tooltip")))
                .build();
    }
}
