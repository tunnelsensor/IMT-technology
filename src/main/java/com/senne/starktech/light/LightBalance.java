package com.senne.starktech.light;

import com.senne.starktech.edith.Tunable;

/**
 * De getallen van de Arc Lamp (jouw wens, 9 oktober: "een lamp die op stroom werkt en veel helderder kan
 * dan lichtniveau 15, ik wil er een van 100"; 10 oktober: "zorg dat je de brightness kunt instellen"). Met
 * {@link Tunable} erboven staan ze ook in config/starktech-balance.toml, onder [lights].
 */
public final class LightBalance {
    private LightBalance() {}

    /**
     * Het hoogste lichtniveau dat je een lamp kunt geven (in zijn instelscherm), en waar een nieuwe lamp
     * mee begint. Een gewone lamp in Minecraft geeft hooguit 15: op de lamp zelf is het 15, elk blok verder
     * 1 minder, dus na 15 blokken is het donker. Een Arc Lamp op niveau 100 geeft tot 85 blokken ver vol
     * licht, en dat dooft daarna in 15 blokken uit (op 100 blokken is het donker). Zie ArcLampLight.
     * Tussen 15 en 128.
     */
    @Tunable(section = "lights", comment = "The highest light level you can set an Arc Lamp to (new lamps start here). "
            + "Normal lamps stop at 15. Full light up to this minus 15 blocks away, then it fades out like normal light. "
            + "Between 15 and 128.")
    public static int ARC_LAMP_MAX_LEVEL = 100;

    /**
     * Stroom per tick van een lamp op lichtniveau 100 (2 per tick = 40 per seconde). Een lamp die je zachter
     * zet, gebruikt naar verhouding minder: op 50 de helft. Een volle generator van 20.000 houdt een lamp
     * op 100 ruim acht minuten aan: ongeveer een nacht. (Mijn invulling.)
     */
    @Tunable(section = "lights", comment = "Power per tick an Arc Lamp at light level 100 uses while it is on (through Stark "
            + "cables). A lamp set lower uses less: at level 50, half of this.")
    public static int ARC_LAMP_POWER = 2;

    /**
     * Een lamp die uit is, gaat pas aan als het net stroom heeft voor zoveel ticks branden (100 = vijf
     * seconden). Zo knippert hij niet als er net te weinig stroom binnenkomt. Brandt hij eenmaal, dan
     * blijft hij aan zolang er per tick genoeg is.
     */
    @Tunable(section = "lights", comment = "An Arc Lamp that is off only turns on when the grid has power for this many ticks "
            + "(so it doesn't flicker on a weak generator)")
    public static int ARC_LAMP_START_TICKS = 100;

    /** Het hoogste lichtniveau, binnen wat de mod aankan (15 tot 128). */
    public static int maxLevel() {
        return Math.max(15, Math.min(128, ARC_LAMP_MAX_LEVEL));
    }
}
