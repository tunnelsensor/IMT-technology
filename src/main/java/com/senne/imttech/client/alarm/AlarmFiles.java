package com.senne.imttech.client.alarm;

import com.senne.imttech.alarm.audio.AlarmAudio;
import com.senne.imttech.alarm.audio.AlarmAudioException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Een geluidsbestand kiezen en inlezen voor een Alarm Speaker (hoofdstuk 126), in je spel.
 *
 * <ul>
 *   <li><b>Choose file</b> opent het gewone "bestand openen"-venster van je computer (TinyFileDialogs, dat Minecraft zelf
 *       ook meelevert). Zo'n venster wacht tot je kiest; daarom draait het op een eigen draad, zodat je spel niet vastloopt.
 *       In volledig scherm kan het venster achter Minecraft verschijnen: sleep het bestand dan gewoon op het scherm.</li>
 *   <li><b>Slepen</b>: sleep een bestand uit je Verkenner op het venster van Minecraft terwijl het scherm van de speaker open
 *       staat (AlarmSpeakerScreen.onFilesDrop).</li>
 *   <li><b>Folder</b> opent de map {@code imttech/alarms} in je Minecraft-map, als handige plek voor je geluiden.</li>
 * </ul>
 * Het inlezen zelf (AlarmAudio: mp4, m4a, mp3, wav, ogg) gebeurt op een tweede eigen draad; het antwoord komt terug op de
 * draad van het spel. (Gevonden door de nalezing: met één draad voor allebei wachtte een gesleept bestand tot je het
 * venster sloot, en opende een tweede klik op Choose file na het eerste venster nog een venster.)
 */
public final class AlarmFiles {
    private AlarmFiles() {}

    /** De soorten bestanden in het "bestand openen"-venster. (Wat het echt is, ziet AlarmAudio aan de eerste bytes.) */
    public static final String[] EXTENSIONS = {"mp4", "m4a", "mov", "aac", "mp3", "wav", "ogg"};

    /** De draad voor het venster (dat wacht tot je kiest), die stopt als het spel stopt. */
    private static final ExecutorService DIALOG = daemon("I.M.T alarm file dialog");
    /** De draad voor het inlezen, één bestand tegelijk. */
    private static final ExecutorService DECODER = daemon("I.M.T alarm file");
    /** Staat het venster nu open? Dan opent Choose file geen tweede. */
    private static volatile boolean dialogOpen;

    private static ExecutorService daemon(String name) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Staat het "bestand openen"-venster nu open (misschien achter Minecraft)? */
    public static boolean dialogOpen() {
        return dialogOpen;
    }

    /** De map waar het venster begint: de map van je laatste bestand, of imttech/alarms. */
    private static @Nullable Path lastFolder;

    /** Wat er uit een bestand kwam: het geluid, of wat er mis was. */
    public record Result(String fileName, @Nullable short[] samples, @Nullable AlarmAudioException error) {
        public boolean ok() {
            return samples != null;
        }
    }

    /** De map imttech/alarms in je Minecraft-map (bestaat hij nog niet, dan maken we hem). */
    public static Path folder() {
        Path folder = FMLPaths.GAMEDIR.get().resolve("imttech").resolve("alarms");
        try {
            Files.createDirectories(folder);
        } catch (IOException ignored) {
            // Dan opent het venster gewoon ergens anders.
        }
        return folder;
    }

    /** Open die map in je Verkenner (of Finder). */
    public static void openFolder() {
        Util.getPlatform().openPath(folder());
    }

    /**
     * Het "bestand openen"-venster. {@code done} krijgt het gekozen bestand (of null als je annuleerde), op de draad van het
     * spel. Staat er al een venster open, dan gebeurt er niets (geeft false).
     */
    public static boolean browse(Consumer<@Nullable Path> done) {
        if (dialogOpen) {
            return false;
        }
        dialogOpen = true;
        Path start = lastFolder != null && Files.isDirectory(lastFolder) ? lastFolder : folder();
        DIALOG.execute(() -> {
            String chosen = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(EXTENSIONS.length);
                for (String extension : EXTENSIONS) {
                    filters.put(stack.UTF8("*." + extension));
                }
                filters.flip();
                chosen = TinyFileDialogs.tinyfd_openFileDialog("Choose an alarm sound", start.toAbsolutePath() + File.separator,
                        filters, "Sound files (mp4, m4a, mp3, wav, ogg)", false);
            } catch (Throwable broken) {
                // Geen venster (bijvoorbeeld op Linux zonder zenity): dan blijft slepen over.
                com.senne.imttech.ImtTech.LOGGER.warn("[alarm] Geen bestandsvenster: {}", broken.toString());
            } finally {
                dialogOpen = false;
            }
            Path path = chosen == null || chosen.isBlank() ? null : Path.of(chosen);
            Minecraft.getInstance().execute(() -> done.accept(path));
        });
        return true;
    }

    /** Lees een bestand in (hooguit {@code maxSeconds} seconden). {@code done} krijgt het resultaat op de draad van het spel. */
    public static void read(Path file, double maxSeconds, Consumer<Result> done) {
        String name = file.getFileName() == null ? "sound" : file.getFileName().toString();
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            lastFolder = parent;
        }
        DECODER.execute(() -> {
            Result result;
            try {
                result = new Result(name, AlarmAudio.read(file, maxSeconds), null);
            } catch (AlarmAudioException problem) {
                result = new Result(name, null, problem);
            } catch (Throwable broken) {
                result = new Result(name, null, new AlarmAudioException("unreadable", broken));
            }
            Result finished = result;
            Minecraft.getInstance().execute(() -> done.accept(finished));
        });
    }

    /** Lijkt dit op een geluidsbestand dat we kunnen lezen (aan de naam)? Voor bestanden die je op het scherm sleept. */
    public static boolean looksLikeSound(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) {
            if (name.endsWith("." + extension)) {
                return true;
            }
        }
        return false;
    }
}
