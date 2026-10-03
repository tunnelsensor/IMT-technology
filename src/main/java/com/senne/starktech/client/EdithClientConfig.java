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

    /**
     * Staat de tekst van E.D.I.T.H. in de HUD (wat ze zegt, en wat jij tegen haar zei)? Uit = ze
     * praat alleen, er verschijnt geen tekst.
     */
    public static final ModConfigSpec.BooleanValue VOICE_TEXT_IN_HUD = BUILDER
            .comment("Show E.D.I.T.H.'s messages as text in the HUD (turn off if you only want to hear her voice)")
            .define("voiceTextInHud", true);

    /**
     * Zie je verborgen Stark-kabels (in een muur of vloer) blauw door de muur heen als je de bril
     * draagt? Aan/uit via G > Information > Cable X-ray (of een eigen toets).
     */
    public static final ModConfigSpec.BooleanValue CABLE_XRAY = BUILDER
            .comment("With the E.D.I.T.H. glasses on, see hidden Stark cables glow through walls (G > Information > Cable X-ray)")
            .define("cableXray", true);

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

    // ---------------------------------------------------------------- Bewakingscamera's (Stark OS)
    /** Zo vaak per seconde wordt elk camerabeeld op de bewakingsmuur ververst (het grote beeld 2x zo vaak). */
    public static final ModConfigSpec.IntValue CAMERA_FPS = BUILDER
            .comment("Security camera wall: how often each camera image is refreshed per second (the big view twice as often)")
            .defineInRange("cameraFeedFps", 5, 1, 30);

    /** Zakt je spel onder de 60 fps, dan worden de beelden minder vaak ververst, maar nooit minder dan dit. */
    public static final ModConfigSpec.IntValue CAMERA_MIN_FPS = BUILDER
            .comment("Security camera wall: the slowest refresh per second when your game runs below 60 fps")
            .defineInRange("cameraFeedMinFps", 2, 1, 30);

    /** Zoveel milliseconden per frame mogen alle camerabeelden samen kosten (er komt altijd minstens één beeld). */
    public static final ModConfigSpec.DoubleValue CAMERA_BUDGET_MS = BUILDER
            .comment("Security camera wall: milliseconds per frame all camera images together may take")
            .defineInRange("cameraFeedBudgetMs", 3.0, 0.5, 20.0);

    /** Zover (blokken) kijkt een camera; daarachter vervaagt het beeld. */
    public static final ModConfigSpec.IntValue CAMERA_DISTANCE = BUILDER
            .comment("Security camera wall: how far a camera sees (blocks)")
            .defineInRange("cameraFeedDistance", 48, 16, 96);

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

    /** Staat de kabel-xray aan? (Zolang de instellingen nog niet geladen zijn: ja.) */
    public static boolean cableXray() {
        return !SPEC.isLoaded() || CABLE_XRAY.get();
    }

    /** Zet de kabel-xray aan of uit, bewaart de keuze, en geeft terug of hij nu aan staat. */
    public static boolean toggleCableXray() {
        if (SPEC.isLoaded()) {
            CABLE_XRAY.set(!CABLE_XRAY.get());
            SPEC.save();
        }
        return cableXray();
    }
}
