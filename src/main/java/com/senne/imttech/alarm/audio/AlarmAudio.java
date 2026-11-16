package com.senne.imttech.alarm.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Van jouw geluidsbestand naar een alarmgeluid (hoofdstuk 126, jouw wens van 16 november: "Voeg een nieuwe blok toe
 * namelijk een alarm waar je zelf een mp4 bestand naar kunt uploaden zodat je custom alarmen kunt hebben").
 *
 * Wat we doen, in je eigen spel (zodat er geen groot bestand naar de server hoeft):
 * <ol>
 *   <li>Aan de eerste bytes zien we wat voor bestand het is (niet aan de naam: een .mp4 die eigenlijk een .m4a is, werkt
 *       ook): mp4/m4a/mov en losse aac (JAAD), mp3 (JavaMP3), wav (zelf) en ogg (Minecraft's eigen ogg-lezer, alleen in
 *       je spel; zie {@link #setOggDecoder}).</li>
 *   <li>We lezen alleen het begin: hooguit {@code maxSeconds} seconden geluid, plus wat stilte vooraan.</li>
 *   <li>Alles naar één kanaal (mono: zo komt het geluid uit de speaker, en hoor je waar hij hangt), stilte vooraan eraf
 *       (een filmpje begint vaak stil), en omgerekend naar {@link #RATE} monsters per seconde.</li>
 *   <li>Even hard gemaakt (de hardste piek net onder het maximum, hooguit vier keer harder), met een heel korte fade aan
 *       begin en einde: zo tikt het niet als het alarm opnieuw begint.</li>
 * </ol>
 * Daarna maakt {@link Adpcm} het klein voor de upload.
 *
 * Gewone Java zonder Minecraft (op ogg na): de game tests lezen er echte testbestanden mee in.
 */
public final class AlarmAudio {
    private AlarmAudio() {}

    /** Zoveel monsters per seconde heeft elk alarmgeluid: genoeg voor een sirene, en de helft van een cd. */
    public static final int RATE = 22050;
    /** Zoveel seconden stilte vooraan slaan we hooguit over. */
    public static final double MAX_LEADING_SILENCE = 10.0;
    /** Korter dan dit (seconden) is geen alarm. */
    public static final double MIN_SECONDS = 0.1;

    /** Onder dit volume (van 1) telt het als stilte (ongeveer -40 dB). */
    private static final float SILENCE = 0.01f;
    /** Zo hard maken we de hardste piek (net onder het maximum, zodat het niet kraakt). */
    private static final float TARGET_PEAK = 0.89f;
    /** Hooguit zoveel keer harder (anders wordt ruis een orkaan). */
    private static final float MAX_GAIN = 4f;

    /** Een lezer voor één soort bestand. */
    public interface Decoder {
        Pcm decode(Path file, double maxSeconds) throws IOException, AlarmAudioException;
    }

    /** De ogg-lezer: die van Minecraft, en die bestaat alleen in je spel (de client zet hem bij het opstarten). */
    private static volatile @Nullable Decoder oggDecoder;

    public static void setOggDecoder(@Nullable Decoder decoder) {
        oggDecoder = decoder;
    }

    /** Wat voor bestand het is, aan de eerste bytes. */
    public enum Format {
        MP4, AAC, MP3, WAV, OGG,
        /** Bestanden die we herkennen maar niet kunnen lezen: dan zeggen we dat netjes. */
        FLAC, WEBM, MIDI, UNKNOWN
    }

    /** Wat voor bestand is dit? Kijkt naar de eerste (hooguit 64) bytes. */
    public static Format detect(byte[] head, int length) {
        if (length >= 12 && is(head, 4, "ftyp")) {
            return Format.MP4; // mp4, m4a, mov en 3gp beginnen allemaal met een "ftyp"-doos
        }
        if (length >= 8 && (is(head, 4, "moov") || is(head, 4, "mdat") || is(head, 4, "wide") || is(head, 4, "free"))) {
            return Format.MP4; // een oud QuickTime-filmpje zonder ftyp
        }
        if (length >= 12 && is(head, 0, "RIFF") && is(head, 8, "WAVE")) {
            return Format.WAV;
        }
        if (length >= 4 && is(head, 0, "OggS")) {
            return Format.OGG;
        }
        if (length >= 3 && is(head, 0, "ID3")) {
            return Format.MP3;
        }
        if (length >= 4 && is(head, 0, "fLaC")) {
            return Format.FLAC;
        }
        if (length >= 4 && (head[0] & 0xFF) == 0x1A && (head[1] & 0xFF) == 0x45 && (head[2] & 0xFF) == 0xDF && (head[3] & 0xFF) == 0xA3) {
            return Format.WEBM; // webm en mkv
        }
        if (length >= 4 && is(head, 0, "MThd")) {
            return Format.MIDI;
        }
        if (length >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0) {
            // Het begin van een stukje mp3 of aac: 11 bits op 1. Bij aac (ADTS) is de "laag" 00, bij mp3 niet.
            return ((head[1] >> 1) & 0x03) == 0 ? Format.AAC : Format.MP3;
        }
        return Format.UNKNOWN;
    }

    private static boolean is(byte[] head, int offset, String text) {
        for (int i = 0; i < text.length(); i++) {
            if (head[offset + i] != (byte) text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lees een geluidsbestand in en maak er een alarmgeluid van: mono, {@link #RATE} per seconde, hooguit
     * {@code maxSeconds} seconden. Gaat het mis, dan zegt de fout wat er mis is (zie AlarmAudioException).
     */
    public static short[] read(Path file, double maxSeconds) throws AlarmAudioException {
        byte[] head = new byte[64];
        int length;
        try (InputStream in = Files.newInputStream(file)) {
            length = in.readNBytes(head, 0, head.length);
        } catch (NoSuchFileException missing) {
            throw new AlarmAudioException("no_file");
        } catch (IOException unreadable) {
            throw new AlarmAudioException("unreadable", unreadable);
        }
        if (length == 0) {
            throw new AlarmAudioException("empty_file");
        }
        Format format = detect(head, length);
        double readSeconds = maxSeconds + MAX_LEADING_SILENCE;
        Pcm pcm;
        try {
            pcm = switch (format) {
                case MP4 -> Mp4Decoder.decode(file, readSeconds);
                case AAC -> Mp4Decoder.decodeAdts(file, readSeconds);
                case MP3 -> Mp3Decoder.decode(file, readSeconds);
                case WAV -> WavDecoder.decode(file, readSeconds);
                case OGG -> {
                    Decoder ogg = oggDecoder;
                    if (ogg == null) {
                        throw new AlarmAudioException("unsupported", "ogg");
                    }
                    yield ogg.decode(file, readSeconds);
                }
                default -> throw new AlarmAudioException("unsupported", format.name().toLowerCase(Locale.ROOT));
            };
        } catch (IOException broken) {
            throw new AlarmAudioException("unreadable", broken);
        } catch (RuntimeException | OutOfMemoryError broken) {
            // Een bibliotheek die over een raar bestand struikelt, mag het spel niet laten vallen.
            throw new AlarmAudioException("unreadable", broken);
        }
        return process(pcm, maxSeconds);
    }

    /** Van ruw geluid naar een alarmgeluid (stap 3 en 4 hierboven). */
    public static short[] process(Pcm pcm, double maxSeconds) throws AlarmAudioException {
        float[] mono = pcm.mono();
        int start = firstSound(mono, pcm.rate);
        if (start < 0) {
            throw new AlarmAudioException("silent");
        }
        // Een klein beetje van vlak voor het geluid laten we staan (30 ms), zodat de aanzet niet afgeknipt wordt.
        start = Math.max(0, start - pcm.rate * 3 / 100);
        float[] trimmed = java.util.Arrays.copyOfRange(mono, start, mono.length);
        float[] resampled = resample(trimmed, pcm.rate, RATE);
        int max = (int) Math.min(resampled.length, Math.round(maxSeconds * RATE));
        if (max < MIN_SECONDS * RATE) {
            throw new AlarmAudioException("too_short");
        }
        float[] clip = java.util.Arrays.copyOf(resampled, max);
        fade(clip, RATE * 5 / 1000, RATE * 30 / 1000);
        return normalize(clip);
    }

    /** Waar het geluid begint (het eerste monster boven de stilte), hooguit {@link #MAX_LEADING_SILENCE} ver; -1 = alles stil. */
    static int firstSound(float[] mono, int rate) {
        int limit = (int) Math.min(mono.length, (long) (MAX_LEADING_SILENCE * rate));
        for (int i = 0; i < mono.length; i++) {
            if (Math.abs(mono[i]) > SILENCE) {
                return Math.min(i, limit);
            }
        }
        return -1;
    }

    /** Een zachte aanzet (fadeIn monsters) en een zacht einde (fadeOut monsters). */
    private static void fade(float[] clip, int fadeIn, int fadeOut) {
        int in = Math.min(fadeIn, clip.length / 4);
        for (int i = 0; i < in; i++) {
            clip[i] *= (float) i / in;
        }
        int out = Math.min(fadeOut, clip.length / 4);
        for (int i = 0; i < out; i++) {
            clip[clip.length - 1 - i] *= (float) i / out;
        }
    }

    /** Even hard maken en naar 16 bit. */
    private static short[] normalize(float[] clip) throws AlarmAudioException {
        float peak = 0f;
        for (float value : clip) {
            peak = Math.max(peak, Math.abs(value));
        }
        if (peak < SILENCE / 5) {
            throw new AlarmAudioException("silent");
        }
        float gain = Math.min(MAX_GAIN, TARGET_PEAK / peak);
        short[] out = new short[clip.length];
        for (int i = 0; i < clip.length; i++) {
            float value = Math.max(-1f, Math.min(1f, clip[i] * gain));
            out[i] = (short) Math.round(value * 32767f);
        }
        return out;
    }

    // ================================================================ Omrekenen naar een andere snelheid

    /** Zoveel keer per monster rekenen we de filtervorm vooraf uit (fijner = nauwkeuriger). */
    private static final int KERNEL_STEPS = 256;
    /** Zoveel nuldoorgangen van het filter aan elke kant (meer = scherper, maar trager). */
    private static final int ZERO_CROSSINGS = 12;

    /**
     * Omrekenen van {@code fromRate} naar {@code toRate} monsters per seconde, met een "windowed sinc"-filter: elk nieuw
     * monster is een gewogen gemiddelde van de oude monsters eromheen. Bij omlaag rekenen (44100 naar 22050) haalt hetzelfde
     * filter eerst de tonen weg die de nieuwe snelheid niet meer kan dragen; anders hoor je ze als vreemde fluittonen.
     */
    public static float[] resample(float[] in, int fromRate, int toRate) {
        if (fromRate == toRate || in.length == 0) {
            return in.clone();
        }
        double ratio = (double) toRate / fromRate;
        int outLength = (int) Math.floor(in.length * ratio);
        float[] out = new float[outLength];
        // De afsnijtoon, als deel van de oude snelheid: net onder de helft van de laagste van de twee snelheden.
        double cutoff = 0.5 * Math.min(1.0, ratio) * 0.95;
        double halfWidth = ZERO_CROSSINGS / (2 * cutoff); // zo ver reikt het filter (in oude monsters)
        float[] kernel = kernel(cutoff, halfWidth);
        int reach = (int) Math.ceil(halfWidth);
        for (int i = 0; i < outLength; i++) {
            double center = i / ratio;
            int first = Math.max(0, (int) Math.floor(center) - reach + 1);
            int last = Math.min(in.length - 1, (int) Math.floor(center) + reach);
            double sum = 0;
            double weights = 0;
            for (int k = first; k <= last; k++) {
                double position = Math.abs(center - k) * KERNEL_STEPS;
                int index = (int) position;
                if (index + 1 >= kernel.length) {
                    continue;
                }
                double fraction = position - index;
                double weight = kernel[index] + (kernel[index + 1] - kernel[index]) * fraction;
                sum += in[k] * weight;
                weights += weight;
            }
            // Delen door de som van de gewichten: zo blijft het even hard, ook aan het begin en einde.
            out[i] = weights > 1e-9 ? (float) (sum / weights) : 0f;
        }
        return out;
    }

    /** De filtervorm (sinc maal een Blackman-venster), vooraf uitgerekend in stapjes van 1/{@link #KERNEL_STEPS} monster. */
    private static float[] kernel(double cutoff, double halfWidth) {
        int length = (int) Math.ceil(halfWidth * KERNEL_STEPS) + 2;
        float[] kernel = new float[length];
        for (int j = 0; j < length; j++) {
            double t = (double) j / KERNEL_STEPS;
            if (t >= halfWidth) {
                kernel[j] = 0f;
                continue;
            }
            double x = 2 * cutoff * t;
            double sinc = x == 0 ? 1.0 : Math.sin(Math.PI * x) / (Math.PI * x);
            double w = t / halfWidth; // 0 in het midden, 1 aan de rand
            double window = 0.42 + 0.5 * Math.cos(Math.PI * w) + 0.08 * Math.cos(2 * Math.PI * w);
            kernel[j] = (float) (2 * cutoff * sinc * window);
        }
        return kernel;
    }
}
