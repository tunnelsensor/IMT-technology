package com.senne.imttech.client.voice;

import com.senne.imttech.ImtTech;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * Downloadt en installeert de onderdelen voor de stem en de spraakbesturing (zie VoicePack).
 *
 * Alles gebeurt op een aparte achtergrond-thread, zodat het spel niet hapert. Een onderdeel
 * telt pas als geïnstalleerd als elk bestand de juiste SHA-256-vingerafdruk heeft en alles
 * uitgepakt is. Gaat er iets mis (geen internet, verkeerd bestand...), dan wordt het half
 * geïnstalleerde onderdeel weggegooid en kun je het opnieuw proberen.
 */
public final class VoiceInstaller {
    private VoiceInstaller() {}

    public enum State {
        /** Werkt niet op dit besturingssysteem. */
        UNSUPPORTED,
        NOT_INSTALLED,
        INSTALLING,
        INSTALLED,
        /** De laatste poging is mislukt. */
        FAILED
    }

    /** Hoe ver een installatie is. */
    private static final class Job {
        final long total;
        final AtomicLong done = new AtomicLong();
        volatile boolean finished;
        volatile @Nullable String error;

        Job(long total) {
            this.total = Math.max(1, total);
        }
    }

    private static final Map<VoiceFeature, Job> JOBS = new ConcurrentHashMap<>();

    /** Eén download tegelijk, op de achtergrond. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "I.M.T downloads");
        thread.setDaemon(true);
        return thread;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL) // GitHub en HuggingFace sturen je door naar hun opslag
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    /** Voorlopig alleen Windows 64-bit: daar is alles getest. */
    public static boolean isSupported() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return os.startsWith("windows") && (arch.equals("amd64") || arch.equals("x86_64"));
    }

    /** De map met alle stem-bestanden: .minecraft/imttech/voice (tijdens het testen: run/imttech/voice). */
    public static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("imttech").resolve("voice");
    }

    /** Mappen van onderdelen die de mod niet meer gebruikt (Vosk, en het AI-brein). */
    private static final String[] OBSOLETE = {"vosk", "vosk_model", "brain"};
    private static boolean obsoleteRemoved;

    /** Ruimt oude, niet meer gebruikte onderdelen op (één keer per keer dat het spel start). */
    public static void removeObsolete() {
        if (obsoleteRemoved) {
            return;
        }
        obsoleteRemoved = true;
        WORKER.execute(() -> {
            for (String name : OBSOLETE) {
                try {
                    deleteRecursively(root().resolve(name));
                } catch (IOException e) {
                    ImtTech.LOGGER.warn("I.M.T: could not remove old voice files {}", name, e);
                }
            }
        });
    }

    public static State state(VoiceFeature feature) {
        if (!isSupported()) {
            return State.UNSUPPORTED;
        }
        Job job = JOBS.get(feature);
        if (job != null && !job.finished) {
            return State.INSTALLING;
        }
        if (feature.isInstalled()) {
            return State.INSTALLED;
        }
        return job != null && job.error != null ? State.FAILED : State.NOT_INSTALLED;
    }

    /** Hoeveel procent van de download binnen is (0-100). */
    public static int percent(VoiceFeature feature) {
        Job job = JOBS.get(feature);
        return job == null ? 0 : (int) Math.min(100, job.done.get() * 100 / job.total);
    }

    /**
     * Start het downloaden van wat er nog ontbreekt voor deze functie. Als het klaar is, wordt
     * whenDone aangeroepen op de hoofd-thread van Minecraft: met null als het gelukt is, anders
     * met de foutmelding.
     */
    public static void install(VoiceFeature feature, Consumer<@Nullable String> whenDone) {
        if (!isSupported() || state(feature) == State.INSTALLING) {
            return;
        }
        List<VoicePack> missing = feature.packs().stream().filter(pack -> !pack.isInstalled()).toList();
        Job job = new Job(missing.stream().mapToLong(VoicePack::size).sum());
        JOBS.put(feature, job);
        WORKER.execute(() -> {
            String error = null;
            try {
                for (VoicePack pack : missing) {
                    installPack(pack, job);
                }
            } catch (Exception e) {
                ImtTech.LOGGER.warn("I.M.T: installing {} failed", feature, e);
                error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
            job.error = error;
            job.finished = true;
            String result = error;
            Minecraft.getInstance().execute(() -> whenDone.accept(result));
        });
    }

    // ---------------------------------------------------------------- Het echte werk (achtergrond-thread)

    private static void installPack(VoicePack pack, Job job) throws IOException {
        Path dir = pack.dir();
        deleteRecursively(dir); // restjes van een eerdere, mislukte poging
        Files.createDirectories(dir);
        try {
            for (VoicePack.Download download : pack.downloads()) {
                Path part = dir.resolve("download.part");
                String sha256 = download(download, part, job);
                if (!sha256.equalsIgnoreCase(download.sha256())) {
                    throw new IOException("wrong fingerprint (SHA-256) for " + download.url());
                }
                switch (download.kind()) {
                    case FILE -> Files.move(part, dir.resolve(download.fileName()), StandardCopyOption.REPLACE_EXISTING);
                    case ZIP_IN_FOLDER, FLAT_ZIP -> {
                        unzip(part, dir, download.kind() == VoicePack.Kind.ZIP_IN_FOLDER);
                        Files.delete(part);
                    }
                }
            }
            Files.writeString(pack.marker(), "I.M.T " + pack.name() + " ok\n");
        } catch (IOException | RuntimeException e) {
            deleteRecursively(dir);
            throw e;
        } finally {
            pack.forgetInstalledState();
        }
    }

    /** Downloadt één bestand en geeft de SHA-256-vingerafdruk terug. */
    private static String download(VoicePack.Download download, Path target, Job job) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(download.url()))
                .header("User-Agent", "ImtTech-Minecraft-mod")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("onderbroken", e);
        }
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode() + " voor " + download.url());
        }
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        long received = 0;
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                sha256.update(buffer, 0, read);
                job.done.addAndGet(read);
                received += read;
                if (received > download.size()) {
                    throw new IOException("file is larger than expected: " + download.url());
                }
            }
        }
        return HexFormat.of().formatHex(sha256.digest());
    }

    /**
     * Pakt een zip uit in dir. Met skipTopFolder laten we de bovenste map in de zip (bv. "piper/") weg.
     * Een bestand dat buiten dir zou belanden ("zip slip") weigeren we.
     */
    private static void unzip(Path zipFile, Path dir, boolean skipTopFolder) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                int slash = name.indexOf('/');
                String relative = !skipTopFolder ? name : slash < 0 ? "" : name.substring(slash + 1);
                if (relative.isEmpty()) {
                    continue;
                }
                Path target = dir.resolve(relative).normalize();
                if (!target.startsWith(dir)) {
                    throw new IOException("ongeldig pad in zip: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Verwijdert een map met alles erin, maar alleen binnen onze eigen stem-map. */
    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir) || !dir.normalize().startsWith(root().normalize())) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
