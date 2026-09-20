package com.senne.starktech.client.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.senne.starktech.StarkTech;
import com.senne.starktech.client.EdithClientConfig;
import com.senne.starktech.network.EdithMessagePayload;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;

/**
 * De stem van E.D.I.T.H.: zet haar meldingen om in spraak met Piper en speelt die af.
 *
 * Piper is een los programma (piper.exe). We starten het één keer en laten het draaien: per zin
 * sturen we één regel JSON en Piper maakt er een WAV-bestand van (± 0,1 seconde). Dat bestand
 * spelen we af en gooien we daarna weg. Alles gebeurt op een eigen thread, zodat het spel niet
 * hoeft te wachten.
 *
 * De stem volgt de taal van je spel: Nederlands → Vlaamse stem, anders een Engelse stem.
 * Het volume volgt de schuif "Stem/Spraak" (Voice/Speech) in de geluidsinstellingen van Minecraft.
 */
public final class EdithSpeech {
    private EdithSpeech() {}

    private record Utterance(String text, VoicePack voice, float volume) {}

    /** Zinnen die nog uitgesproken moeten worden. Er wachten er nooit meer dan 3: de oudste valt weg. */
    private static final LinkedBlockingDeque<Utterance> QUEUE = new LinkedBlockingDeque<>();
    private static final int MAX_WAITING = 3;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final AtomicInteger FILE_NUMBER = new AtomicInteger();

    private static @Nullable Thread worker;
    private static volatile boolean speaking;
    /** De geluidslijn die nu speelt; via deze kunnen we haar midden in een zin stoppen. */
    private static volatile @Nullable SourceDataLine playing;
    /**
     * Gaat één omhoog elke keer dat je haar onderbreekt. De speel-thread vergelijkt dit getal
     * met het getal van toen hij begon; klopt het niet meer, dan stopt hij met deze zin.
     */
    private static volatile int stopGeneration;
    /** Dezelfde zin vlak na elkaar zeggen we maar één keer. */
    private static String lastText = "";
    private static long lastMillis;

    // Alleen gebruikt op de spreek-thread:
    private static @Nullable Process piper;
    private static @Nullable VoicePack piperVoice;
    private static @Nullable BufferedWriter toPiper;
    private static @Nullable BufferedReader fromPiper;

    /** Is E.D.I.T.H. nu aan het praten? (voor het golfje in de HUD) */
    public static boolean isSpeaking() {
        return speaking;
    }

    /**
     * Meteen stoppen met praten: de wachtrij leeg en de zin die nu klinkt afbreken.
     * Dit gebruiken we als je zelf begint te praten (spraaktoets): dan zwijgt zij.
     */
    public static void stopSpeaking() {
        QUEUE.clear();
        stopGeneration++;
        lastText = ""; // zo mag dezelfde zin daarna weer gezegd worden
        SourceDataLine line = playing;
        if (line != null) {
            // stop() + flush(): wat al in de geluidskaart zit, gooien we weg.
            line.stop();
            line.flush();
        }
    }

    /** De stem van E.D.I.T.H. De mod is Engels, dus ze praat Engels. */
    public static VoicePack voicePack() {
        return VoicePack.VOICE_EN;
    }

    /** Tot dit moment (ms) spreken we gewone meldingen (INFO) niet uit. */
    private static long holdInfoUntil;

    /**
     * Gewone meldingen (INFO) even niet uitspreken, wel tonen. Na een antwoord van de AI zegt ze
     * zelf al wat er gebeurt; anders hoor je alles twee keer. Waarschuwingen hoor je wel.
     */
    public static void holdInfoMessages(long millis) {
        holdInfoUntil = Util.getMillis() + millis;
    }

    /**
     * Laat E.D.I.T.H. iets zeggen (vanaf de hoofd-thread). Doet niets als haar stem uit staat,
     * niet geïnstalleerd is, of als het volume op 0 staat.
     */
    public static void say(Component message, EdithMessagePayload.Level level) {
        if (!VoiceFeature.SPEECH.isEnabled() || VoiceInstaller.state(VoiceFeature.SPEECH) != VoiceInstaller.State.INSTALLED) {
            return;
        }
        if ((level == EdithMessagePayload.Level.INFO || level == EdithMessagePayload.Level.BOOT) && Util.getMillis() < holdInfoUntil) {
            return;
        }
        String text = speakable(message.getString());
        long now = Util.getMillis();
        if (text.isEmpty() || (text.equals(lastText) && now - lastMillis < 3000)) {
            return;
        }
        lastText = text;
        lastMillis = now;
        Minecraft mc = Minecraft.getInstance();
        float volume = mc.options.getSoundSourceVolume(SoundSource.VOICE) * EdithClientConfig.VOICE_VOLUME.get().floatValue();
        if (volume <= 0.001f) {
            return;
        }
        while (QUEUE.size() >= MAX_WAITING) {
            QUEUE.pollFirst();
        }
        QUEUE.offerLast(new Utterance(text, voicePack(), volume));
        startWorker();
    }

    /** Maakt een melding klaar om uit te spreken: "E.D.I.T.H." wordt "Edith", tekens als › weg. */
    static String speakable(String text) {
        return text.replace("E.D.I.T.H.", "Edith")
                .replaceAll("§.", "")            // kleurcodes van Minecraft
                .replaceAll("[›»«·|]", ", ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static synchronized void startWorker() {
        if (worker != null && worker.isAlive()) {
            return;
        }
        worker = new Thread(EdithSpeech::work, "E.D.I.T.H. voice");
        worker.setDaemon(true);
        worker.start();
    }

    /** Stopt Piper als het spel afsluit (anders blijft piper.exe draaien). */
    public static void shutdown() {
        Process process = piper;
        if (process != null) {
            process.destroy();
        }
    }

    // ---------------------------------------------------------------- Op de spreek-thread

    private static void work() {
        while (true) {
            Utterance utterance;
            try {
                utterance = QUEUE.takeFirst();
            } catch (InterruptedException e) {
                return;
            }
            Path wav = null;
            try {
                wav = synthesize(utterance);
                speaking = true;
                play(wav, utterance.volume());
            } catch (Exception e) {
                StarkTech.LOGGER.warn("E.D.I.T.H.: speaking failed", e);
                stopPiper(); // de volgende keer opnieuw starten
            } finally {
                speaking = false;
                if (wav != null) {
                    try {
                        Files.deleteIfExists(wav);
                    } catch (IOException ignored) {
                        // niet erg: het staat in onze eigen tmp-map
                    }
                }
            }
        }
    }

    /** Laat Piper een WAV-bestand maken van de zin. */
    private static Path synthesize(Utterance utterance) throws IOException {
        if (piper == null || !piper.isAlive() || piperVoice != utterance.voice()) {
            startPiper(utterance.voice());
        }
        Path wav = tmpDir().resolve("edith-" + FILE_NUMBER.incrementAndGet() + ".wav");
        JsonObject request = new JsonObject();
        request.addProperty("text", utterance.text());
        request.addProperty("output_file", wav.toString());
        toPiper.write(GSON.toJson(request));
        toPiper.newLine();
        toPiper.flush();
        // Piper schrijft de naam van het bestand terug als het klaar is.
        if (fromPiper.readLine() == null || !Files.isRegularFile(wav)) {
            throw new IOException("Piper gaf geen geluid terug");
        }
        return wav;
    }

    private static boolean leftoversKilled;

    private static void startPiper(VoicePack voice) throws IOException {
        stopPiper();
        Path piperDir = VoicePack.PIPER.dir();
        if (!leftoversKilled) {
            // Crashte het spel de vorige keer? Dan kan er nog een oude piper.exe draaien.
            leftoversKilled = true;
            String exe = piperDir.resolve("piper.exe").toString();
            ProcessHandle.allProcesses()
                    .filter(handle -> handle.info().command().map(exe::equalsIgnoreCase).orElse(false))
                    .forEach(ProcessHandle::destroyForcibly);
        }
        Process process = new ProcessBuilder(piperDir.resolve("piper.exe").toString(),
                "--model", voice.dir().resolve("voice.onnx").toString(),
                "--json-input",
                "--output_dir", tmpDir().toString())
                .directory(piperDir.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD) // Piper praat veel in zijn log; dat hebben we niet nodig
                .start();
        piper = process;
        piperVoice = voice;
        toPiper = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        fromPiper = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    private static void stopPiper() {
        if (piper != null) {
            piper.destroy();
        }
        piper = null;
        piperVoice = null;
        toPiper = null;
        fromPiper = null;
    }

    private static Path tmpDir() throws IOException {
        Path dir = VoiceInstaller.root().resolve("tmp");
        Files.createDirectories(dir);
        return dir;
    }

    /** Speelt een WAV-bestand af, zachter of luider gemaakt met volume (0-1). */
    private static void play(Path wav, float volume) throws Exception {
        byte[] data;
        AudioFormat format;
        try (AudioInputStream in = AudioSystem.getAudioInputStream(wav.toFile())) {
            format = in.getFormat();
            data = in.readAllBytes();
        }
        // Piper maakt 16-bit geluid: elk getal (2 bytes) vermenigvuldigen we met het volume.
        for (int i = 0; i + 1 < data.length; i += 2) {
            int sample = (short) ((data[i] & 0xFF) | (data[i + 1] << 8));
            sample = Math.round(sample * volume);
            data[i] = (byte) sample;
            data[i + 1] = (byte) (sample >> 8);
        }
        SourceDataLine line = AudioSystem.getSourceDataLine(format);
        int generation = stopGeneration;
        try {
            line.open(format);
            line.start();
            playing = line;
            // In stukjes afspelen, zodat we tussendoor kunnen stoppen als je haar onderbreekt.
            int chunk = Math.max(2, format.getFrameSize() * 1024);
            for (int offset = 0; offset < data.length; offset += chunk) {
                if (generation != stopGeneration) {
                    return; // onderbroken: de rest van de zin slaan we over
                }
                line.write(data, offset, Math.min(chunk, data.length - offset));
            }
            if (generation == stopGeneration) {
                line.drain();
            }
        } finally {
            playing = null;
            line.close();
        }
    }
}
