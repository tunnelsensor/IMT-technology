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
            .comment("Which tab of the round E.D.I.T.H. menu (G) was open last")
            .defineEnum("menuTab", MenuTab.COMBAT);

    /** Spreekt E.D.I.T.H. haar meldingen uit? (Alleen als haar stem geïnstalleerd is.) */
    public static final ModConfigSpec.BooleanValue VOICE = BUILDER
            .comment("E.D.I.T.H. speaks her messages out loud (if her voice is installed via G > Systems)")
            .define("voice", true);

    /** Luistert E.D.I.T.H. naar spraakcommando's? (Alleen als spraakbesturing geïnstalleerd is.) */
    public static final ModConfigSpec.BooleanValue VOICE_COMMANDS = BUILDER
            .comment("Voice commands with the push-to-talk key (if voice control is installed via G > Systems)")
            .define("voiceCommands", true);

    /** Extra volumeknop voor de stem, bovenop de schuif "Stem/Spraak" van Minecraft. */
    public static final ModConfigSpec.DoubleValue VOICE_VOLUME = BUILDER
            .comment("Volume of E.D.I.T.H.'s voice (0.0 - 1.0), on top of Minecraft's Voice/Speech slider")
            .defineInRange("voiceVolume", 1.0, 0.0, 1.0);

    /** Laat de AI (oor en brein) op de videokaart rekenen: veel sneller. */
    public static final ModConfigSpec.BooleanValue AI_GPU = BUILDER
            .comment("Run E.D.I.T.H.'s AI on your graphics card (much faster). Set to false if it makes your game stutter.")
            .define("aiGpu", true);

    /** Staat de tekst van wat je vraagt en wat E.D.I.T.H. antwoordt in de HUD? (Ze praat altijd.) */
    public static final ModConfigSpec.BooleanValue VOICE_TEXT_IN_HUD = BUILDER
            .comment("Show in the HUD what you said and what E.D.I.T.H. replies (turn off if you only want her voice)")
            .define("voiceTextInHud", true);

    /**
     * Het Client ID van JOUW gratis Spotify-app (zie het muziekscherm). Leeg = niet ingesteld.
     * Dit is geen wachtwoord; je logt zelf in op de site van Spotify.
     */
    public static final ModConfigSpec.ConfigValue<String> SPOTIFY_CLIENT_ID = BUILDER
            .comment("The Client ID of your own Spotify app (developer.spotify.com/dashboard).",
                    "Redirect URI in that app: http://127.0.0.1:8899/starktech")
            .define("spotifyClientId", "");

    /** Hoe E.D.I.T.H. begrijpt wat je zegt (G > Systemen > AI Brain). */
    public static final ModConfigSpec.EnumValue<com.senne.starktech.client.voice.BrainMode> BRAIN = BUILDER
            .comment("How E.D.I.T.H. understands what you say: DICTIONARY (fixed phrases), LOCAL (a language model on this PC)"
                    + " or CLAUDE (the Claude API with your own key). Choose it in G > Systems > AI Brain.")
            .defineEnum("brain", com.senne.starktech.client.voice.BrainMode.DICTIONARY);

    /** Welk lokaal model (auto = de mod kiest bij je pc, zie BrainModel). */
    public static final ModConfigSpec.ConfigValue<String> BRAIN_MODEL = BUILDER
            .comment("Local brain size: auto (chosen for your PC the first time), small (2B), medium (4B) or large (9B)")
            .define("brainModel", "auto");

    /** Welk Claude-model het brein gebruikt. */
    public static final ModConfigSpec.ConfigValue<String> CLAUDE_MODEL = BUILDER
            .comment("The Claude model for the CLAUDE brain (it must support structured outputs and effort)")
            .define("claudeModel", "claude-opus-5");

    /** Mag ze uit zichzelf praten (zie EdithInitiative)? */
    public static final ModConfigSpec.BooleanValue PROACTIVE = BUILDER
            .comment("Let E.D.I.T.H. speak up on her own at important moments (needs the LOCAL or CLAUDE brain)")
            .define("speaksOnHerOwn", false);

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
