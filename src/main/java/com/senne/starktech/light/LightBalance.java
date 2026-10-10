package com.senne.starktech.light;

import com.senne.starktech.edith.Tunable;

/**
 * De getallen van de Arc Lamp (jouw wens, 9 oktober: "een lamp die op stroom werkt en veel helderder kan
 * dan lichtniveau 15, ik wil er een van 100"). Met {@link Tunable} erboven staan ze ook in
 * config/starktech-balance.toml, onder [lights].
 */
public final class LightBalance {
    private LightBalance() {}

    /**
     * Het lichtniveau van de lamp. Een gewone lamp in Minecraft geeft hooguit 15: op de lamp zelf is het
     * 15, elk blok verder 1 minder, dus na 15 blokken is het donker. Deze lamp rekent met dit getal: tot
     * {@code ARC_LAMP_LEVEL - 15} blokken ver is het vol licht (85), en daarna dooft het in 15 blokken uit
     * (op 100 blokken is het donker). Zie ArcLampLight. Tussen 15 en 128.
     */
    @Tunable(section = "lights", comment = "Light level of the Arc Lamp (normal lamps stop at 15). Full light up to "
            + "this minus 15 blocks away, then it fades out like normal light. Between 15 and 128.")
    public static int ARC_LAMP_LEVEL = 100;

    /**
     * Stroom per tick zolang de lamp brandt (2 per tick = 40 per seconde). Een volle generator van
     * 20.000 houdt hem dus ruim acht minuten aan: ongeveer een nacht. (Mijn invulling.)
     */
    @Tunable(section = "lights", comment = "Power the Arc Lamp uses per tick while it is on (through Stark cables)")
    public static int ARC_LAMP_POWER = 2;

    /**
     * Een lamp die uit is, gaat pas aan als het net stroom heeft voor zoveel ticks branden (100 = vijf
     * seconden). Zo knippert hij niet als er net te weinig stroom binnenkomt. Brandt hij eenmaal, dan
     * blijft hij aan zolang er per tick genoeg is.
     */
    @Tunable(section = "lights", comment = "An Arc Lamp that is off only turns on when the grid has power for this many ticks "
            + "(so it doesn't flicker on a weak generator)")
    public static int ARC_LAMP_START_TICKS = 100;
}
