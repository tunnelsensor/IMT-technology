package com.senne.imttech.client.voice;

import com.senne.imttech.ImtTech;
import com.senne.imttech.client.ImtClientConfig;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Eén llama-server (van llama.cpp) die op de achtergrond draait. Dat is een klein programma dat
 * een AI-model in het geheugen houdt en vragen beantwoordt via een adres op je eigen pc
 * (http://127.0.0.1:poort). Alleen jouw eigen pc kan erbij, niet het internet.
 *
 * I.M.T gebruikt er één: het oor (spraak naar tekst, zie ImtEar). Eerst proberen we de
 * videokaart (snel); lukt dat niet, dan de processor (trager, maar het werkt overal).
 *
 * Wat de server zelf vertelt, staat in .minecraft/imttech/voice/logs/(naam).log.
 */
final class LlamaServer {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    /** Zo lang mag opstarten duren (model laden van de harde schijf). */
    private static final long START_MILLIS = 120_000;
    private static boolean leftoversKilled;

    private final String name;
    /** Welke bestanden het model zijn (bv. "-m", pad), pas opgevraagd bij het starten. */
    private final Supplier<List<String>> modelArgs;
    private @Nullable Process process;
    private int port;
    private boolean gpuFailed;

    LlamaServer(String name, Supplier<List<String>> modelArgs) {
        this.name = name;
        this.modelArgs = modelArgs;
    }

    boolean isRunning() {
        Process p = process;
        return p != null && p.isAlive();
    }

    /** Start de server als hij nog niet draait, en wacht tot hij klaar is (kan even duren). */
    synchronized void start() throws IOException {
        if (isRunning()) {
            return;
        }
        killLeftovers();
        boolean gpu = !gpuFailed && ImtClientConfig.AI_GPU.get();
        try {
            launch(gpu);
        } catch (IOException e) {
            if (!gpu) {
                throw e;
            }
            // Videokaart lukt niet (te weinig geheugen, geen Vulkan...): dan maar de processor.
            ImtTech.LOGGER.warn("I.M.T: {} could not use the GPU, falling back to the CPU", name, e);
            gpuFailed = true;
            launch(false);
        }
    }

    private void launch(boolean gpu) throws IOException {
        Path dir = VoicePack.LLAMA.dir();
        Path logs = VoiceInstaller.root().resolve("logs");
        Files.createDirectories(logs);
        port = freePort();
        List<String> command = new ArrayList<>();
        command.add(dir.resolve("llama-server.exe").toString());
        command.addAll(modelArgs.get());
        command.addAll(List.of("--host", "127.0.0.1", "--port", String.valueOf(port),
                "-c", "4096", "-np", "1", "--jinja", "--no-webui"));
        command.addAll(gpu ? List.of("-ngl", "99") : List.of("-ngl", "0", "--device", "none"));
        Path log = logs.resolve(name + ".log");
        Process started = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        process = started;

        long deadline = System.currentTimeMillis() + START_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (!started.isAlive()) {
                throw new IOException(name + " stopped immediately (code " + started.exitValue() + "), see " + log);
            }
            if (healthy()) {
                ImtTech.LOGGER.info("I.M.T: {} ready on port {} ({})", name, port, gpu ? "GPU" : "CPU");
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        started.destroy();
        process = null;
        throw new IOException(name + " did not start in time, see " + log);
    }

    private boolean healthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Stuurt een vraag (JSON) naar de server en geeft het antwoord (JSON) terug. */
    String post(String path, String json, Duration timeout) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException(name + " gaf fout " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    synchronized void stop() {
        Process p = process;
        if (p != null) {
            p.destroy();
        }
        process = null;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Crashte het spel de vorige keer? Dan kan er nog een oude llama-server draaien die geheugen
     * inneemt. Die ruimen we één keer op, voordat we zelf iets starten.
     */
    private static synchronized void killLeftovers() {
        if (leftoversKilled) {
            return;
        }
        leftoversKilled = true;
        String exe = VoicePack.LLAMA.dir().resolve("llama-server.exe").toString();
        ProcessHandle.allProcesses()
                .filter(handle -> handle.info().command().map(exe::equalsIgnoreCase).orElse(false))
                .forEach(ProcessHandle::destroyForcibly);
    }
}
