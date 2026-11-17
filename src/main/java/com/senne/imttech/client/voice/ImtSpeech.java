package com.senne.imttech.client.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.senne.imttech.ImtTech;
import com.senne.imttech.client.ImtClientConfig;
import com.senne.imttech.network.ImtMessagePayload;
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
import java.util.concurrent.atomic.AtomicLong;
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
 * De stem van I.M.T: zet haar meldingen om in spraak met Piper en speelt die af.
 *
 * Piper is een los programma (piper.exe). We starten het één keer en laten het draaien: per zin
 * sturen we één regel JSON en Piper maakt er een WAV-bestand van (± 0,1 seconde). Dat bestand
 * spelen we af en gooien we daarna weg. Alles gebeurt op een eigen thread, zodat het spel niet
 * hoeft te wachten.
 *
 * Ze praat alleen Engels (de Engelse stem "Lessac"), wat de taal van je spel ook is.
 *
 * Hoofdstuk 136: een Speaker kan ook iets zeggen met haar stem ({@code playtext("Alarm armed", "all")}). Dan maakt
 * Piper de zin net zo, maar spelen we hem niet hier af: {@link #synthesize} geeft de monsters terug, en de speaker laat
 * ze horen uit zijn eigen plek (client/alarm/AlarmSpeakerSounds). Zo'n opdracht wacht in dezelfde rij, maar valt er nooit
 * uit door de grens van drie zinnen of doordat jij begint te praten.
 * Het volume volgt de schuif "Stem/Spraak" (Voice/Speech) in de geluidsinstellingen van Minecraft.
 */
public final class ImtSpeech {
    private ImtSpeech() {}

    /**
     * @param number het volgnummer van de zin (zie say en isFinished); 0 voor een zin voor een speaker
     * @param pcm    hoofdstuk 136: niet afspelen maar de monsters teruggeven (voor een speaker), anders null
     */
    private record Utterance(String text, VoicePack voice, float volume, long number,
                             java.util.function.@Nullable Consumer<short[]> pcm) {}

    /** Zinnen die nog uitgesproken moeten worden. Er wachten er nooit meer dan 3: de oudste valt weg. */
    private static final LinkedBlockingDeque<Utterance> QUEUE = new LinkedBlockingDeque<>();
    private static final int MAX_WAITING = 3;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final AtomicInteger FILE_NUMBER = new AtomicInteger();
    /** Elke zin in de wachtrij krijgt een volgnummer: 1, 2, 3... */
    private static final AtomicLong NUMBERS = new AtomicLong();
    /** Alle zinnen tot en met dit volgnummer zijn klaar: uitgesproken, afgebroken of weggevallen. */
    private static final AtomicLong FINISHED = new AtomicLong();

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

    /** Is I.M.T nu aan het praten? (voor het golfje in de HUD) */
    public static boolean isSpeaking() {
        return speaking;
    }

    /**
     * Is de zin met dit volgnummer (van say) klaar? Uitgesproken, afgebroken of weggevallen; 0 (niet gezegd) is altijd klaar.
     * Zo wacht de bril met uitvallen tot haar laatste zin gedaan is (hoofdstuk 102).
     */
    public static boolean isFinished(long number) {
        return FINISHED.get() >= number;
    }

    /**
     * Meteen stoppen met praten: de wachtrij leeg en de zin die nu klinkt afbreken.
     * Dit gebruiken we als je zelf begint te praten (spraaktoets): dan zwijgt zij.
     */
    public static void stopSpeaking() {
        QUEUE.removeIf(utterance -> utterance.pcm() == null); // zinnen voor een speaker blijven staan
        FINISHED.accumulateAndGet(NUMBERS.get(), Math::max); // alles wat wachtte of klonk, is nu klaar
        stopGeneration++;
        lastText = ""; // zo mag dezelfde zin daarna weer gezegd worden
        SourceDataLine line = playing;
        if (line != null) {
            // stop() + flush(): wat al in de geluidskaart zit, gooien we weg.
            line.stop();
            line.flush();
        }
    }

    /** De stem van I.M.T. De mod is Engels, dus ze praat Engels. */
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
     * Laat I.M.T iets zeggen (vanaf de hoofd-thread). Doet niets als haar stem uit staat,
     * niet geïnstalleerd is, of als het volume op 0 staat.
     *
     * @return het volgnummer van de zin (zie isFinished), of 0 als ze hem niet zegt
     */
    public static long say(Component message, ImtMessagePayload.Level level) {
        // Hoofdstuk 96: een lege bril zwijgt. (Haar laatste zin zegt ImtClientEvents net voordat de bril "dood" telt.)
        if (!available() || com.senne.imttech.client.ImtClientState.batteryDead) {
            return 0;
        }
        if ((level == ImtMessagePayload.Level.INFO || level == ImtMessagePayload.Level.BOOT) && Util.getMillis() < holdInfoUntil) {
            return 0;
        }
        String text = speakable(message.getString());
        long now = Util.getMillis();
        if (text.isEmpty() || (text.equals(lastText) && now - lastMillis < 3000)) {
            return 0;
        }
        lastText = text;
        lastMillis = now;
        Minecraft mc = Minecraft.getInstance();
        float volume = mc.options.getSoundSourceVolume(SoundSource.VOICE) * ImtClientConfig.VOICE_VOLUME.get().floatValue();
        if (volume <= 0.001f) {
            return 0;
        }
        while (QUEUE.stream().filter(utterance -> utterance.pcm() == null).count() >= MAX_WAITING) {
            // De oudste gewone zin valt weg (hij telt als klaar zodra een latere zin klaar is); zinnen voor een speaker niet.
            for (Utterance waiting : QUEUE) {
                if (waiting.pcm() == null) {
                    QUEUE.remove(waiting);
                    break;
                }
            }
        }
        long number = NUMBERS.incrementAndGet();
        QUEUE.offerLast(new Utterance(text, voicePack(), volume, number, null));
        startWorker();
        return number;
    }

    /**
     * Hoofdstuk 136: maak deze zin met de stem van I.M.T, maar speel hem niet af. {@code done} krijgt de monsters (mono,
     * 16 bit, alarm/audio/AlarmAudio.RATE per seconde) op de draad van het spel, of null als het niet lukte. Doet niets (en
     * geeft false) als haar stem niet aan staat of niet geïnstalleerd is.
     */
    public static boolean synthesize(String text, java.util.function.Consumer<short[]> done) {
        String clean = speakable(text);
        if (!available() || clean.isEmpty()) {
            return false;
        }
        // Vooraan in de rij: een speaker wacht niet op wat I.M.T jou nog wil zeggen (gevonden door de nalezing).
        QUEUE.offerFirst(new Utterance(clean, voicePack(), 1f, 0, done));
        startWorker();
        return true;
    }

    /** Kan I.M.T praten? (Haar stem staat aan en is geïnstalleerd.) */
    public static boolean available() {
        return VoiceFeature.SPEECH.isEnabled() && VoiceInstaller.state(VoiceFeature.SPEECH) == VoiceInstaller.State.INSTALLED;
    }

    /**
     * Maakt een melding klaar om uit te spreken: "I.M.T" wordt "I M T" (dan spelt de stem de drie
     * letters, en een punt erachter blijft het einde van de zin), tekens als › weg. Grote getallen zoals jij
     * ze laat zien ("1 500", "1.500", "5k", hoofdstuk 66) worden weer gewone cijfers, die leest de stem goed.
     */
    static String speakable(String text) {
        return com.senne.imttech.config.NumberStyle.local().spoken(text).replace("I.M.T", "I M T")
                .replaceAll("§.", "")            // kleurcodes van Minecraft
                .replaceAll("[›»«·|]", ", ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static synchronized void startWorker() {
        if (worker != null && worker.isAlive()) {
            return;
        }
        worker = new Thread(ImtSpeech::work, "I.M.T voice");
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
                if (utterance.pcm() != null) {
                    // Hoofdstuk 136: voor een speaker. Niet hier afspelen; de monsters gaan terug naar het spel.
                    short[] samples = readMono(wav);
                    var done = utterance.pcm();
                    Minecraft.getInstance().execute(() -> done.accept(samples));
                } else {
                    speaking = true;
                    play(wav, utterance.volume());
                }
            } catch (Exception e) {
                ImtTech.LOGGER.warn("I.M.T: speaking failed", e);
                stopPiper(); // de volgende keer opnieuw starten
                if (utterance.pcm() != null) {
                    var done = utterance.pcm();
                    Minecraft.getInstance().execute(() -> done.accept(null));
                }
            } finally {
                speaking = false;
                FINISHED.accumulateAndGet(utterance.number(), Math::max);
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
        Path wav = tmpDir().resolve("imt-" + FILE_NUMBER.incrementAndGet() + ".wav");
        JsonObject request = new JsonObject();
        request.addProperty("text", utterance.text());
        request.addProperty("output_file", wav.toString());
        toPiper.write(GSON.toJson(request));
        toPiper.newLine();
        toPiper.flush();
        // Piper schrijft de naam van het bestand terug als het klaar is.
        if (fromPiper.readLine() == null || !Files.isRegularFile(wav)) {
            throw new IOException("Piper returned no audio");
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

    /**
     * Hoofdstuk 136: een WAV-bestand van Piper als monsters (mono, 16 bit), omgerekend naar AlarmAudio.RATE als Piper een
     * andere snelheid gebruikte (de stem Lessac maakt al 22 050 per seconde, dan hoeft er niets).
     */
    private static short[] readMono(Path wav) throws Exception {
        AudioFormat format;
        byte[] data;
        try (AudioInputStream in = AudioSystem.getAudioInputStream(wav.toFile())) {
            format = in.getFormat();
            data = in.readAllBytes();
        }
        int channels = Math.max(1, format.getChannels());
        boolean big = format.isBigEndian();
        int frames = data.length / (2 * channels);
        float[] mono = new float[frames];
        for (int frame = 0; frame < frames; frame++) {
            float sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                int at = (frame * channels + channel) * 2;
                int value = big ? (short) ((data[at] << 8) | (data[at + 1] & 0xFF)) : (short) ((data[at] & 0xFF) | (data[at + 1] << 8));
                sum += value;
            }
            mono[frame] = sum / channels / 32768f;
        }
        int rate = Math.round(format.getSampleRate());
        int target = com.senne.imttech.alarm.audio.AlarmAudio.RATE;
        float[] samples = rate == target || rate <= 0 ? mono : com.senne.imttech.alarm.audio.AlarmAudio.resample(mono, rate, target);
        short[] out = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(samples[i] * 32767f)));
        }
        return out;
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
