package com.senne.imttech.weapons;

/**
 * Hoe ver een laserbout van een lasergeweer gevlogen is (hoofdstuk 35): een felle streep licht met een kop en een staart,
 * allebei gemeten in blokken vanaf de loop. De kop stopt bij de inslag, de staart vliegt door tot hij daar ook is: dan is
 * de bout helemaal in de inslag verdwenen (31 oktober, jouw notitie: "zorg dat de afgeschoten bullets verdwijnen en niet
 * zoals pijlen in de grond blijven zitten").
 *
 * Er zit hier bewust geen tekencode in: zo kan een automatische test hem nakijken (op de server, waar geen beeld is). Het
 * tekenen staat in client/weapons/GunEffects.
 */
public record BoltFlight(double tail, double head) {
    /** Zo snel vliegt een bout (blokken per tick)... */
    public static final double SPEED = 9;
    /** ...en zo lang is hij (blokken). */
    public static final double LENGTH = 2.2;

    /** Waar de bout is, {@code age} ticks na het schot, op een weg van {@code length} blokken tot de inslag. */
    public static BoltFlight at(double age, double length) {
        double travelled = Math.max(0, age) * SPEED;
        return new BoltFlight(Math.max(0, travelled - LENGTH), Math.min(length, travelled));
    }

    /** Is de bout helemaal verdwenen op een weg van {@code length} blokken? */
    public boolean gone(double length) {
        return tail >= length;
    }
}
