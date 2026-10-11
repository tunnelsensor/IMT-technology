package com.senne.starktech.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Het "etiket" boven een getal in {@link StarkConfig}: de uitleg die in het configbestand
 * config/starktech.toml boven dat getal komt (in het Engels, zoals alles wat de speler ziet).
 *
 * Voorbeeld:
 * <pre>
 *     &#64;Tunable("Energy cost of an ore scan")
 *     public static int ORE_SCAN_COST = 400;
 *
 *     &#64;Tunable(value = "How many drones come in one raid", min = 1, max = 32)
 *     public static int SIZE = 4;
 * </pre>
 *
 * Met min en max zeg je welke waarden mogen (zonder: 0 tot een miljoen). Typt iemand in het bestand
 * iets daarbuiten, dan zet NeoForge het op de dichtstbijzijnde grens. Bij een rijtje (zoals "per
 * niveau") geldt het voor elk getal; klopt een getal niet, of het aantal, dan komt het standaardrijtje terug.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Tunable {
    /** De uitleg in het configbestand. */
    String value();

    /** Het kleinste getal dat mag. */
    double min() default 0;

    /** Het grootste getal dat mag. */
    double max() default 1_000_000;
}
