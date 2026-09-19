package com.senne.starktech.client.voice;

import com.senne.starktech.StarkTech;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Bewaart het woordenboek van E.D.I.T.H. (zie VoiceDictionary) en de matcher die ermee zoekt.
 *
 * Het woordenboek staat in config/starktech-voice.json. Bestaat dat bestand nog niet, dan nemen
 * we het standaard-woordenboek uit de mod. Het scherm "Spraakcommando's" slaat je wijzigingen hier
 * op; ze werken meteen.
 */
public final class VoiceCommandStore {
    private VoiceCommandStore() {}

    private static VoiceDictionary dictionary;
    private static VoiceMatcher matcher;

    /** Het bestand met jouw woordenboek. */
    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("starktech-voice.json");
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
                use(VoiceDictionary.load(file));
                return;
            }
        } catch (IOException | RuntimeException e) {
            // Kapot bestand (bv. een typfout met de hand)? Dan het standaard-woordenboek, en het
            // kapotte bestand bewaren we als .kapot zodat je werk niet verloren gaat.
            StarkTech.LOGGER.warn("E.D.I.T.H.: could not read {}, using the default dictionary", file, e);
            try {
                Files.move(file, file.resolveSibling("starktech-voice.json.kapot"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // dan maar niet
            }
        }
        try {
            use(VoiceDictionary.loadDefault());
        } catch (IOException e) {
            StarkTech.LOGGER.error("E.D.I.T.H.: default voice dictionary missing", e);
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
