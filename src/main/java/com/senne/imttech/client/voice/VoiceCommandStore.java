package com.senne.imttech.client.voice;

import com.senne.imttech.ImtTech;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Bewaart het woordenboek van I.M.T (zie VoiceDictionary) en de matcher die ermee zoekt.
 *
 * Het woordenboek staat in config/imttech-voice.json. Bestaat dat bestand nog niet, dan nemen
 * we het standaard-woordenboek uit de mod. Het scherm "Spraakcommando's" slaat je wijzigingen hier
 * op; ze werken meteen.
 */
public final class VoiceCommandStore {
    private VoiceCommandStore() {}

    private static VoiceDictionary dictionary;
    private static VoiceMatcher matcher;

    /** Het bestand met jouw woordenboek. */
    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("imttech-voice.json");
    }

    /** Het huidige woordenboek (laadt het de eerste keer). */
    public static synchronized VoiceDictionary dictionary() {
        if (dictionary == null) {
            load();
        }
        return dictionary;
    }

    /** Zoekt welke commando's passen bij wat je zei. */
    public static synchronized List<VoiceMatcher.Match> match(String heard) {
        dictionary();
        return matcher.match(heard);
    }

    private static void load() {
        Path file = file();
        try {
            if (Files.exists(file)) {
                VoiceDictionary saved = VoiceDictionary.load(file);
                if (saved.version >= VoiceDictionary.VERSION) {
                    use(saved);
                    return;
                }
                if (saved.version == 2 || saved.version == 3) {
                    if (saved.version == 2) {
                        // Versie 2 had nog Nederlandse synoniemen en zinnen. I.M.T is nu alleen
                        // Engels: die halen we eruit, je eigen (Engelse) werk blijft staan.
                        int removed = EnglishOnly.strip(saved);
                        ImtTech.LOGGER.info("I.M.T: removed {} Dutch words and phrases from your voice dictionary", removed);
                    }
                    // Versie 2 en 3 kenden de bril nog onder zijn oude naam (17 oktober): je eigen
                    // zinnen blijven, alleen de naam erin verandert.
                    int renamed = saved.renameAssistant(VoiceDictionary.loadDefault());
                    saved.save(file);
                    ImtTech.LOGGER.info("I.M.T: updated {} phrases and words in your voice dictionary to the new name", renamed);
                    use(saved);
                    return;
                }
                // Een woordenboek van een oudere versie (versie 1 was nog deels Nederlands).
                // We zetten het opzij, zodat je eigen zinnen niet verloren gaan, en gaan verder
                // met het nieuwe standaard-woordenboek.
                Path backup = file.resolveSibling("imttech-voice-v" + saved.version + ".json.old");
                Files.move(file, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                ImtTech.LOGGER.info("I.M.T: voice dictionary version {} replaced by version {}; the old one is kept as {}",
                        saved.version, VoiceDictionary.VERSION, backup.getFileName());
            }
        } catch (IOException | RuntimeException e) {
            // Kapot bestand (bv. een typfout met de hand)? Dan het standaard-woordenboek, en het
            // kapotte bestand bewaren we als .broken zodat je werk niet verloren gaat.
            ImtTech.LOGGER.warn("I.M.T: could not read {}, using the default dictionary", file, e);
            try {
                Files.move(file, file.resolveSibling("imttech-voice.json.broken"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // dan maar niet
            }
        }
        try {
            use(VoiceDictionary.loadDefault());
        } catch (IOException e) {
            ImtTech.LOGGER.error("I.M.T: default voice dictionary missing", e);
            use(new VoiceDictionary());
        }
    }

    private static void use(VoiceDictionary newDictionary) {
        dictionary = newDictionary;
        matcher = new VoiceMatcher(newDictionary);
    }

    /** Slaat een (bewerkt) woordenboek op en gebruikt het meteen. */
    public static synchronized void save(VoiceDictionary newDictionary) throws IOException {
        newDictionary.save(file());
        use(newDictionary.copy());
    }

    /** Terug naar het standaard-woordenboek uit de mod (je eigen zinnen verdwijnen). */
    public static synchronized VoiceDictionary resetToDefault() throws IOException {
        VoiceDictionary fresh = VoiceDictionary.loadDefault();
        save(fresh);
        return fresh.copy();
    }
}
