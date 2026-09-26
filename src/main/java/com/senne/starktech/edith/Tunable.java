package com.senne.starktech.edith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Een "etiket" voor een getal in een balance-bestand (EdithBalance, SuitBalance, ...): zet dit
 * erboven, en het getal staat vanzelf in het configbestand config/starktech-balance.toml.
 * Zie BalanceConfig.
 *
 * Voorbeeld:
 * <pre>
 *     &#64;Tunable(section = "scanner", comment = "Energy cost of an ore scan")
 *     public static int ORE_SCAN_COST = 400;
 * </pre>
 *
 * Let op: zo'n getal mag NIET "final" zijn, want de config zet er bij het laden van de wereld
 * een andere waarde in. De waarde die hier in de code staat, is de standaardwaarde.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Tunable {
    /** Onder welk kopje het in het configbestand staat, bv. "satellite". */
    String section();

    /** Uitleg in het configbestand (in het Engels, zoals alles wat de speler ziet). */
    String comment();
}
