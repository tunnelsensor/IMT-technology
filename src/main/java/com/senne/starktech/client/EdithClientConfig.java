package com.senne.starktech.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Instellingen die alleen voor jouw eigen spel gelden (niet voor de server).
 * Ze staan in het bestand config/starktech-client.toml.
 */
public final class EdithClientConfig {
    private EdithClientConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Op welk tabblad het G-menu opent: het tabblad dat je het laatst open had. */
    public static final ModConfigSpec.EnumValue<MenuTab> MENU_TAB = BUILDER
            .comment("Welk tabblad van het ronde E.D.I.T.H.-menu (G) het laatst open was")
            .defineEnum("menuTab", MenuTab.COMBAT);

    /** Spreekt E.D.I.T.H. haar meldingen uit? (Alleen als haar stem geïnstalleerd is.) */
    public static final ModConfigSpec.BooleanValue VOICE = BUILDER
            .comment("E.D.I.T.H. spreekt haar meldingen uit (als haar stem geïnstalleerd is via G > Systemen)")
            .define("voice", true);

    /** Luistert E.D.I.T.H. naar spraakcommando's? (Alleen als spraakbesturing geïnstalleerd is.) */
    public static final ModConfigSpec.BooleanValue VOICE_COMMANDS = BUILDER
            .comment("Spraakcommando's met de spraaktoets (als spraakbesturing geïnstalleerd is via G > Systemen)")
            .define("voiceCommands", true);

    /** Extra volumeknop voor de stem, bovenop de schuif "Stem/Spraak" van Minecraft. */
    public static final ModConfigSpec.DoubleValue VOICE_VOLUME = BUILDER
            .comment("Volume van de stem van E.D.I.T.H. (0.0 - 1.0), bovenop de schuif Stem/Spraak van Minecraft")
            .defineInRange("voiceVolume", 1.0, 0.0, 1.0);

    /** Laat de AI (oor en brein) op de videokaart rekenen: veel sneller. */
    public static final ModConfigSpec.BooleanValue AI_GPU = BUILDER
            .comment("De AI van E.D.I.T.H. rekent op je videokaart (veel sneller). Zet op false als je spel daardoor hapert.")
            .define("aiGpu", true);

    /** Staat de tekst van wat je vraagt en wat E.D.I.T.H. antwoordt in de HUD? (Ze praat altijd.) */
    public static final ModConfigSpec.BooleanValue VOICE_TEXT_IN_HUD = BUILDER
            .comment("Laat in de HUD zien wat jij zei en wat E.D.I.T.H. antwoordt (zet uit als je alleen haar stem wil)")
            .define("voiceTextInHud", true);

    /**
     * Het Client ID van JOUW gratis Spotify-app (zie het muziekscherm). Leeg = niet ingesteld.
     * Dit is geen wachtwoord; je logt zelf in op de site van Spotify.
     */
    public static final ModConfigSpec.ConfigValue<String> SPOTIFY_CLIENT_ID = BUILDER
            .comment("Het Client ID van je eigen Spotify-app (developer.spotify.com/dashboard).",
                    "Redirect URI in die app: http://127.0.0.1:8899/starktech")
            .define("spotifyClientId", "");

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** Mag de spraaktekst in de HUD? (Zolang de instellingen nog niet geladen zijn: ja.) */
    public static boolean voiceTextInHud() {
        return !SPEC.isLoaded() || VOICE_TEXT_IN_HUD.get();
    }

    /** Zet de spraaktekst in de HUD aan of uit en bewaart de keuze. */
    public static void toggleVoiceTextInHud() {
        if (SPEC.isLoaded()) {
            VOICE_TEXT_IN_HUD.set(!VOICE_TEXT_IN_HUD.get());
            SPEC.save();
        }
    }
}
