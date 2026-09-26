package com.senne.starktech.client.voice;

/**
 * Hoe E.D.I.T.H. begrijpt wat je tegen haar zegt (G > Systemen > AI Brain).
 *
 * - DICTIONARY: het woordenboek met vaste zinnen (snel, gratis, maar alleen wat erin staat).
 * - LOCAL: een taalmodel op je eigen pc (gratis, offline; de mod kiest de grootte bij je pc).
 * - CLAUDE: Claude van Anthropic via internet (het slimst; kost een klein beetje per zin, met
 *   je eigen API-sleutel).
 *
 * Bij LOCAL en CLAUDE blijft het woordenboek de reserve: lukt het denken niet (geen internet,
 * model start niet), dan zoekt ze je zin daarin op.
 */
public enum BrainMode {
    DICTIONARY,
    LOCAL,
    CLAUDE;

    public String translationKey() {
        return "screen.starktech.brain.mode." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
