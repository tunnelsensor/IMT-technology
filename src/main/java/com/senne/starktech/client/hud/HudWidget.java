package com.senne.starktech.client.hud;

/**
 * Eén onderdeel van de HUD: het kompas, het paneel links, de markeringen op vijanden...
 *
 * EdithHud houdt een lijst van onderdelen bij en loopt die elk frame af, op volgorde:
 * wat later in de lijst staat, wordt over eerdere onderdelen heen getekend.
 *
 * Een onderdeel bepaalt zelf of het iets te tekenen heeft, met {@link #visible(HudContext)}.
 * Zo staat er in EdithHud geen lange rij ifs meer, en kun je een nieuw onderdeel toevoegen
 * door één regel aan de lijst toe te voegen.
 */
public interface HudWidget {
    /** Is er nu iets te tekenen? Standaard ja. */
    default boolean visible(HudContext c) {
        return true;
    }

    void render(HudContext c);
}
