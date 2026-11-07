package com.senne.imttech.raven;

/**
 * Iets dat spelers in de gaten houdt zoals een bewaker in Hitman (hoofdstuk 60): een Raven-soldaat, en sinds stap 6
 * ook een robot of drone. Je spel leest dit (het staat in de gesynchroniseerde gegevens van het wezen) om het oogje op
 * je bril te tekenen: wie heeft jou bijna door, en hoe ver is zijn meter?
 */
public interface Watcher {
    /** Hoe alert hij is. */
    RavenAlert alertState();

    /** Zijn meter (0-100): hoe ver hij is met jou in de gaten krijgen. */
    int suspicion();

    /** Het nummer (entity id) van de speler die zijn meter vult, of -1. */
    int suspectId();
}
