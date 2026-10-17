package com.senne.imttech;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Tot 17 oktober heette de mod "Stark Tech" (mod-id starktech), en zijn bestanden buiten de
 * wereld hadden die naam ook: de spraakmodellen, het AI-geheugen en je Claude-sleutel in de map
 * starktech naast je werelden (meer dan een gigabyte), en in config/ alle getallen van de mod
 * (met jouw eigen waarden), je instellingen, je spraakcommando's en de koppeling met Spotify.
 *
 * Start het spel voor het eerst met de nieuwe naam (imttech), dan verhuizen we die bestanden
 * naar hun nieuwe naam. Zo hoef je niets opnieuw te downloaden of in te stellen. Bestaat de
 * nieuwe naam al, dan blijft alles staan zoals het is: we overschrijven nooit iets.
 *
 * Werelden verhuizen niet mee: daarin heten de blokken nog "starktech:...", en die namen kent
 * het spel niet meer.
 */
final class LegacyFiles {
    private LegacyFiles() {}

    /** De oude mod-id. */
    private static final String OLD = "starktech";

    /** Verhuist alles met de oude naam. Moet vóór het laden van de configbestanden gebeuren. */
    static void moveToNewName() {
        Path game = FMLPaths.GAMEDIR.get();
        Path config = FMLPaths.CONFIGDIR.get();
        Path defaults = game.resolve("defaultconfigs");
        move(game.resolve(OLD), game.resolve(ImtTech.MODID));
        // Alle getallen van de mod (zie ImtConfig), met jouw eigen waarden erin.
        move(config.resolve(OLD + ".toml"), config.resolve(ImtTech.MODID + ".toml"));
        move(config.resolve(OLD + "-client.toml"), config.resolve(ImtTech.MODID + "-client.toml"));
        move(config.resolve(OLD + "-voice.json"), config.resolve(ImtTech.MODID + "-voice.json"));
        move(config.resolve(OLD + "-spotify.json"), config.resolve(ImtTech.MODID + "-spotify.json"));
        move(defaults.resolve(OLD + ".toml"), defaults.resolve(ImtTech.MODID + ".toml"));
    }

    private static void move(Path from, Path to) {
        if (!Files.exists(from) || Files.exists(to)) {
            return;
        }
        try {
            // Op dezelfde schijf is dit gewoon een andere naam geven: ook een map van een
            // gigabyte gaat in een oogwenk.
            Files.move(from, to);
            ImtTech.LOGGER.info("Renamed {} to {} (the mod used to be called Stark Tech)", from, to);
        } catch (IOException e) {
            ImtTech.LOGGER.warn("Could not rename {} to {}", from, to, e);
        }
    }
}
