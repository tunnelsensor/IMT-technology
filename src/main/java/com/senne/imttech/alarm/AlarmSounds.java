package com.senne.imttech.alarm;

import com.senne.imttech.alarm.audio.Adpcm;
import com.senne.imttech.alarm.audio.AlarmAudio;
import com.senne.imttech.config.ImtConfig;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waar de server de geüploade alarmgeluiden bewaart (hoofdstuk 126): in de map van de wereld, in
 * {@code imttech_alarm_sounds/}, één bestand per geluid. (Zo gaan ze mee als je de wereld kopieert of een back-up maakt.)
 *
 * <ul>
 *   <li>De naam van een bestand is zijn "vingerafdruk" (SHA-1, 40 tekens 0-9 en a-f), uitgerekend door de server zelf uit
 *       wat er binnenkwam. Uploadt iemand hetzelfde geluid nog eens (of zet je het op tien speakers), dan is het nog steeds
 *       één bestand. En omdat een naam alleen uit die tekens bestaat, kan hij nooit naar een andere map wijzen.</li>
 *   <li>In een bestand: "IMTA", versie 1, de snelheid (AlarmAudio.RATE), het aantal monsters, en dan het geluid in
 *       {@link Adpcm} (ongeveer 11 kB per seconde). Nooit het mp4- of mp3-bestand zelf: dat leest alleen het spel van wie
 *       uploadt, zodat geen enkele server of andere speler een vreemd bestand hoeft te openen.</li>
 *   <li>De laatste wijzigingstijd van een bestand is "wanneer het voor het laatst gebruikt werd" (bij een upload, als een
 *       speaker het krijgt, en als een speaker met dit geluid geladen wordt). Staan er meer dan
 *       {@code [alarms] maxStoredSounds} geluiden, of samen meer dan {@code maxStorageMb}, dan gaat het langst ongebruikte
 *       weg. Een speaker met een geluid dat weg is, laat gewoon de ingebouwde sirene horen.</li>
 *   <li>Lezen, schrijven en opruimen gebeurt naast de server, op één eigen draad na elkaar (zodat twee uploads van
 *       hetzelfde geluid, of een upload en het opruimen, elkaar nooit in de weg zitten), zodat een trage schijf het spel
 *       niet laat haperen; het antwoord komt terug op de server-thread.</li>
 * </ul>
 */
public final class AlarmSounds {
    private AlarmSounds() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("imttech/alarm");

    /** De map in de wereld. (NeoForge maakt deze constructor openbaar.) */
    private static final LevelResource FOLDER = new LevelResource("imttech_alarm_sounds");
    /** "IMTA" aan het begin van elk bestand. */
    private static final int MAGIC = 0x494D5441;
    private static final int VERSION = 1;
    /** Zo groot is de kop van een bestand (magic, versie, snelheid, monsters). */
    private static final int HEADER = 4 + 1 + 4 + 4;
    /** Een geldige naam: de vingerafdruk. */
    private static final Pattern ID = Pattern.compile("[0-9a-f]{40}");
    /** Zoveel geluiden houden we in het geheugen om ze snel naar spelers te sturen. */
    private static final int CACHE = 16;
    /** Langer dan dit (seconden) is nooit een geldig geluid, wat de config ook zegt (de grens van [alarms] maxSoundSeconds). */
    public static final int HARD_MAX_SECONDS = 120;

    /** Een bewaard geluid: hoeveel monsters, en de ADPCM-bytes. */
    public record Stored(String id, int samples, byte[] adpcm) {
        public float seconds() {
            return samples / (float) AlarmAudio.RATE;
        }
    }

    private static final Map<String, Stored> CACHED = new LinkedHashMap<>(CACHE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Stored> eldest) {
            return size() > CACHE;
        }
    };
    /** Wanneer we de tijd van een geluid het laatst bijwerkten (hooguit eens per uur per geluid). Alleen op de server-thread. */
    private static final Map<String, Long> TOUCHED = new HashMap<>();
    private static final long TOUCH_EVERY_MS = 3_600_000L;

    /**
     * Eén draad voor al het werk in de map (schrijven, lezen, bijwerken, opruimen), na elkaar. Gevonden door de nalezing:
     * met meer draden tegelijk konden twee uploads van hetzelfde geluid hetzelfde tijdelijke bestand gebruiken, en kon het
     * opruimen een geluid weggooien dat net opnieuw geüpload was.
     */
    private static final ExecutorService DISK = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "I.M.T alarm sounds");
        thread.setDaemon(true);
        return thread;
    });

    /** Is dit een geldige naam van een geluid? */
    public static boolean validId(@Nullable String id) {
        return id != null && ID.matcher(id).matches();
    }

    /** De vingerafdruk van een geluid: SHA-1 over het aantal monsters en de bytes. */
    public static String idOf(int samples, byte[] adpcm) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(new byte[]{(byte) (samples >>> 24), (byte) (samples >>> 16), (byte) (samples >>> 8), (byte) samples});
            digest.update(adpcm);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Java zonder SHA-1", impossible);
        }
    }

    /** Hoeveel monsters een geluid hooguit mag hebben, volgens de config van nu. */
    public static int maxSamples() {
        float seconds = Math.max(1f, Math.min(HARD_MAX_SECONDS, ImtConfig.Alarms.MAX_SOUND_SECONDS));
        return Math.round(seconds * AlarmAudio.RATE);
    }

    /** De map met de geluiden van deze wereld. */
    static Path folder(MinecraftServer server) {
        return server.getWorldPath(FOLDER).toAbsolutePath().normalize();
    }

    private static @Nullable Path file(MinecraftServer server, String id) {
        if (!validId(id)) {
            return null;
        }
        Path folder = folder(server);
        Path file = folder.resolve(id + ".imta").normalize();
        return file.startsWith(folder) ? file : null;
    }

    // ================================================================ Bewaren

    /**
     * Bewaar een geluid (als het er nog niet is). Klaar = {@code done} krijgt de naam, op de server-thread; ging het mis,
     * dan null. De server controleert hier dat het aantal bytes klopt bij het aantal monsters; wat er in die bytes staat, is
     * altijd "geluid" (elke byte is geldige ADPCM), dus daar hoeft niets aan nagekeken te worden.
     */
    public static void store(MinecraftServer server, int samples, byte[] adpcm, java.util.function.Consumer<@Nullable String> done) {
        if (samples <= 0 || samples > AlarmAudio.RATE * HARD_MAX_SECONDS || adpcm.length != Adpcm.encodedLength(samples)) {
            done.accept(null);
            return;
        }
        String id = idOf(samples, adpcm);
        Path target = file(server, id);
        if (target == null) {
            done.accept(null);
            return;
        }
        synchronized (CACHED) {
            CACHED.put(id, new Stored(id, samples, adpcm));
        }
        CompletableFuture.runAsync(() -> write(target, samples, adpcm), DISK)
                .whenComplete((ok, error) -> server.execute(() -> {
                    // Mislukte het schrijven, maar staat het bestand er toch (een eerdere upload van hetzelfde geluid)? Goed genoeg.
                    if (error != null && !Files.isRegularFile(target)) {
                        LOGGER.warn("Kon alarmgeluid {} niet bewaren: {}", id, error.toString());
                        synchronized (CACHED) {
                            CACHED.remove(id);
                        }
                        done.accept(null);
                        return;
                    }
                    TOUCHED.put(id, System.currentTimeMillis());
                    done.accept(id);
                    // Welke geluiden geladen speakers nu hebben, weet alleen de server-thread: die gaan bij het opruimen als laatste weg.
                    Set<String> inUse = AlarmSpeakers.loadedSoundIds(server);
                    Path folder = folder(server);
                    DISK.execute(() -> cleanUp(folder, id, inUse));
                }));
    }

    /** Wegschrijven (op een andere draad). Bestaat het al, dan alleen de tijd bijwerken. */
    private static void write(Path target, int samples, byte[] adpcm) {
        try {
            if (Files.isRegularFile(target)) {
                Files.setLastModifiedTime(target, FileTime.fromMillis(System.currentTimeMillis()));
                return;
            }
            Files.createDirectories(target.getParent());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER + adpcm.length);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeByte(VERSION);
                out.writeInt(AlarmAudio.RATE);
                out.writeInt(samples);
                out.write(adpcm);
            }
            // Eerst naar een tijdelijk bestand en dan in één keer op zijn plek: valt de server halverwege uit, dan staat er
            // nooit een half geluid.
            // Elke keer een eigen tijdelijke naam (<naam>.imta<getal>.tmp), zodat er nooit twee schrijvers op één bestand zitten.
            Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            try {
                Files.write(temp, bytes.toByteArray());
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException noAtomic) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp); // ging het mis, dan geen los tijdelijk bestand (na het verplaatsen is het er al niet meer)
            }
        } catch (IOException broken) {
            throw new java.io.UncheckedIOException(broken);
        }
    }

    // ================================================================ Ophalen

    /**
     * Haal een geluid op. {@code done} krijgt het op de server-thread (meteen als het in het geheugen zit), of null als het
     * niet (meer) bestaat of kapot is.
     */
    public static void load(MinecraftServer server, String id, java.util.function.Consumer<@Nullable Stored> done) {
        Path path = file(server, id);
        if (path == null) {
            done.accept(null);
            return;
        }
        Stored cached;
        synchronized (CACHED) {
            cached = CACHED.get(id);
        }
        if (cached != null) {
            done.accept(cached);
            return;
        }
        CompletableFuture.supplyAsync(() -> read(path, id), DISK)
                .whenComplete((stored, error) -> server.execute(() -> {
                    if (stored != null) {
                        synchronized (CACHED) {
                            CACHED.put(id, stored);
                        }
                    }
                    done.accept(error == null ? stored : null);
                }));
    }

    /** Lees één bestand (op een andere draad). Null als het er niet is, of niet klopt. */
    private static @Nullable Stored read(Path path, String id) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (InputStream raw = Files.newInputStream(path); DataInputStream in = new DataInputStream(raw)) {
            if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION || in.readInt() != AlarmAudio.RATE) {
                return null;
            }
            int samples = in.readInt();
            if (samples <= 0 || samples > AlarmAudio.RATE * HARD_MAX_SECONDS) {
                return null;
            }
            byte[] adpcm = in.readNBytes(Adpcm.encodedLength(samples));
            if (adpcm.length != Adpcm.encodedLength(samples) || !id.equals(idOf(samples, adpcm))) {
                return null; // kapot of veranderd: niet gebruiken
            }
            return new Stored(id, samples, adpcm);
        } catch (IOException broken) {
            LOGGER.warn("Kon alarmgeluid {} niet lezen: {}", id, broken.toString());
            return null;
        }
    }

    /** Heeft de server dit geluid nu (in het geheugen of als bestand)? Eén blik op de schijf: goed genoeg voor het scherm. */
    public static boolean storedNow(MinecraftServer server, String id) {
        Path path = file(server, id);
        if (path == null) {
            return false;
        }
        synchronized (CACHED) {
            if (CACHED.containsKey(id)) {
                return true;
            }
        }
        return Files.isRegularFile(path);
    }

    /**
     * Een speaker met dit geluid is geladen, of iemand haalde het op: werk zijn tijd bij, zodat het het langst bewaard
     * blijft. Hooguit eens per uur per geluid (de schijf hoeft niet bij elk stukje wereld dat laadt). Op de server-thread.
     */
    public static void touch(MinecraftServer server, String id) {
        Path path = file(server, id);
        long now = System.currentTimeMillis();
        Long last = TOUCHED.get(id);
        if (path == null || last != null && now - last < TOUCH_EVERY_MS) {
            return;
        }
        TOUCHED.put(id, now);
        CompletableFuture.runAsync(() -> {
            try {
                if (Files.isRegularFile(path)) {
                    Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
                }
            } catch (IOException ignored) {
                // Niet erg: dan telt het als iets ouder.
            }
        }, DISK);
    }

    // ================================================================ Opruimen

    /**
     * Te veel geluiden, of samen te groot? Dan gaan de langst ongebruikte weg: eerst geluiden die geen geladen speaker
     * heeft ({@code inUse}), pas daarna de rest, en nooit {@code keep} (het geluid dat net binnenkwam). Ook oude tijdelijke
     * bestanden (van een server die halverwege uitviel) ruimen we op. Op de schijfdraad.
     */
    static void cleanUp(Path folder, String keep, Set<String> inUse) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        record Entry(Path path, String id, long size, long used) {}
        List<Entry> entries = new ArrayList<>();
        long total = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
            for (Path path : files) {
                String name = path.getFileName().toString();
                if (name.endsWith(".tmp")) {
                    if (Files.getLastModifiedTime(path).toMillis() < System.currentTimeMillis() - 3_600_000L) {
                        Files.deleteIfExists(path);
                    }
                    continue;
                }
                if (!name.endsWith(".imta") || !validId(name.substring(0, name.length() - 5))) {
                    continue;
                }
                long size = Files.size(path);
                total += size;
                entries.add(new Entry(path, name.substring(0, name.length() - 5), size, Files.getLastModifiedTime(path).toMillis()));
            }
        } catch (IOException broken) {
            LOGGER.warn("Kon de alarmgeluiden niet opruimen: {}", broken.toString());
            return;
        }
        long maxBytes = (long) Math.max(1f, ImtConfig.Alarms.MAX_STORAGE_MB) * 1024L * 1024L;
        int maxCount = Math.max(1, Math.round(ImtConfig.Alarms.MAX_STORED_SOUNDS));
        // Eerst wat geen geladen speaker gebruikt (het oudste eerst), dan wat wel in gebruik is (ook het oudste eerst).
        entries.sort((a, b) -> {
            boolean aUsed = inUse.contains(a.id());
            boolean bUsed = inUse.contains(b.id());
            return aUsed != bUsed ? (aUsed ? 1 : -1) : Long.compare(a.used(), b.used());
        });
        int count = entries.size();
        for (Entry entry : entries) {
            if (count <= maxCount && total <= maxBytes) {
                break;
            }
            if (entry.id().equals(keep)) {
                continue;
            }
            try {
                Files.deleteIfExists(entry.path());
                synchronized (CACHED) {
                    CACHED.remove(entry.id());
                }
                count--;
                total -= entry.size();
            } catch (IOException ignored) {
                // Dan proberen we het de volgende keer weer.
            }
        }
    }

    /** De server stopt: vergeet wat in het geheugen zat (een volgende wereld heeft andere geluiden). */
    public static void clear() {
        synchronized (CACHED) {
            CACHED.clear();
        }
        TOUCHED.clear();
    }
}
