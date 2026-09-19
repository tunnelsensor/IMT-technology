package com.senne.starktech.client.voice;

import com.senne.starktech.StarkTech;
import java.io.ByteArrayOutputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * De microfoon van E.D.I.T.H.: neemt op zolang je de spraaktoets ingedrukt houdt, en geeft het
 * geluid daarna aan EdithAssistant (oor → brein → actie).
 *
 * De microfoon staat alleen aan terwijl je de toets ingedrukt houdt.
 */
public final class EdithListener {
    private EdithListener() {}

    /** 16000 metingen per seconde, 16 bit, mono: dat verwacht het oor. */
    private static final float SAMPLE_RATE = 16000f;
    /** 0,1 seconde geluid per stukje. */
    private static final int CHUNK_BYTES = 3200;
    /** Na het loslaten nog even doorluisteren, anders mis je vaak het laatste woord. */
    private static final int TAIL_CHUNKS = 3;
    /** Veiligheid: nooit langer dan 15 seconden opnemen. */
    private static final long MAX_MILLIS = 15_000;

    /** Toets ingedrukt: we nemen op. */
    private static volatile boolean listening;
    /** Hoe luid het nu is (0-1), voor het balkje in de HUD. */
    private static volatile float level;

    public static boolean isListening() {
        return listening;
    }

    public static float level() {
        return level;
    }

    /** Begin met opnemen (hoofd-thread). */
    public static void start() {
        if (EdithAssistant.isBusy()) {
            return;
        }
        listening = true;
        level = 0;
        EdithAssistant.setStage(EdithAssistant.Stage.LISTENING);
        Thread thread = new Thread(EdithListener::record, "E.D.I.T.H. listening");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stop met opnemen (toets losgelaten). */
    public static void stop() {
        listening = false;
    }

    private static void record() {
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        TargetDataLine line = null;
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            line = AudioSystem.getTargetDataLine(format);
            line.open(format, CHUNK_BYTES * 8);
            line.start();
            byte[] chunk = new byte[CHUNK_BYTES];
            long start = System.currentTimeMillis();
            int tail = TAIL_CHUNKS;
            while (tail > 0 && System.currentTimeMillis() - start < MAX_MILLIS) {
                if (!listening) {
                    tail--;
                }
                int read = line.read(chunk, 0, chunk.length);
                if (read > 0) {
                    audio.write(chunk, 0, read);
                    level = loudness(chunk, read);
                }
            }
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            StarkTech.LOGGER.warn("E.D.I.T.H.: microphone unavailable", e);
            EdithAssistant.failed("mic");
            return;
        } finally {
            if (line != null) {
                line.close();
            }
            listening = false;
            level = 0;
        }
        // Verder op deze thread: oor → brein → actie.
        EdithAssistant.process(audio.toByteArray());
    }

    /** Hoe luid een stukje geluid is: 0 = stil, 1 = heel luid. */
    private static float loudness(byte[] data, int length) {
        double sum = 0;
        int samples = length / 2;
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (data[i] & 0xFF) | (data[i + 1] << 8); // 16 bit, "little endian"
            sum += (double) sample * sample;
        }
        double rms = Math.sqrt(sum / Math.max(1, samples)) / 32768.0;
        return (float) Math.min(1.0, rms * 6.0);
    }
}
