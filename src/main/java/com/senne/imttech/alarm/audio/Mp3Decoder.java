package com.senne.imttech.alarm.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

/**
 * Een .mp3 inlezen (hoofdstuk 126), met de bibliotheek JavaMP3 van delthas (MIT-licentie, zie THIRD-PARTY-LICENSES.txt).
 *
 * Veel mp3's beginnen met een "ID3-label": de titel, de artiest en soms een plaatje van de hoes. Dat is geen geluid, en in
 * een plaatje zitten vaak bytes die toevallig op het begin van een mp3-stukje lijken; daar zou de decoder in trappen.
 * Daarom slaan we het label eerst zelf over: het zegt precies hoe lang het is.
 *
 * Twee dingen die de nalezing vond:
 * <ul>
 *   <li>JavaMP3 kent alleen mp3's van 32, 44,1 en 48 kHz (MPEG-1). Een mp3 met een lage snelheid (22,05 kHz of minder:
 *       MPEG-2 en 2.5, bijvoorbeeld een ingesproken bericht of een oud belsignaal) kan het niet lezen. Die herkennen we
 *       aan het eerste stukje, en dan zeggen we dat duidelijk ("mp3_low_rate").</li>
 *   <li>Ook een .aac-bestand kan met een ID3-label beginnen; dan dacht het spel dat het een mp3 was. Na het label kijken
 *       we wat er echt staat: AAC gaat naar Mp4Decoder.</li>
 * </ul>
 */
public final class Mp3Decoder {
    private Mp3Decoder() {}

    /** Zo ver (bytes) zoeken we na het label naar het eerste stukje. */
    private static final int SCAN = 64 * 1024;

    public static Pcm decode(Path file, double maxSeconds) throws IOException, AlarmAudioException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), SCAN + 16)) {
            skipId3(in);
            FirstFrame first = firstFrame(in);
            if (first != null && first.adts()) {
                return Mp4Decoder.decodeAdtsStream(in, maxSeconds); // een .aac met een ID3-label ervoor
            }
            return decodeStream(in, maxSeconds);
        }
    }

    /** Mp3-stukjes uit een stroom decoderen (een bestand zonder ID3-label, of een mp3-spoor uit een mp4). */
    static Pcm decodeStream(InputStream raw, double maxSeconds) throws AlarmAudioException {
        InputStream in = raw.markSupported() ? raw : new BufferedInputStream(raw, SCAN + 16);
        FirstFrame first;
        try {
            first = firstFrame(in);
        } catch (IOException broken) {
            throw new AlarmAudioException("bad_mp3", broken);
        }
        if (first != null && !first.adts() && first.version() != MPEG_1) {
            throw new AlarmAudioException("mp3_low_rate", first.rate());
        }
        fr.delthas.javamp3.Sound sound;
        try {
            sound = new fr.delthas.javamp3.Sound(in);
        } catch (IOException | RuntimeException noFrames) {
            throw new AlarmAudioException("bad_mp3", noFrames);
        }
        Pcm pcm = new Pcm(sound.getSamplingFrequency(), sound.isStereo() ? 2 : 1, maxSeconds);
        // De decoder geeft getallen van 16 bit, de kleinste byte eerst, links en rechts om en om.
        byte[] buffer = new byte[16384];
        int carry = -1;
        try {
            int read;
            while (!pcm.full() && (read = sound.read(buffer)) > 0) {
                int i = 0;
                if (carry >= 0) {
                    pcm.add16((short) (carry | buffer[0] << 8));
                    carry = -1;
                    i = 1;
                }
                for (; i + 1 < read; i += 2) {
                    pcm.add16((short) ((buffer[i] & 0xFF) | buffer[i + 1] << 8));
                }
                if (i < read) {
                    carry = buffer[i] & 0xFF; // een half getal: de rest komt bij de volgende keer
                }
            }
        } catch (IOException | RuntimeException broken) {
            // Een kapot stuk midden in het bestand: wat we al hebben, houden we (een beetje is beter dan niets).
            if (pcm.frames() == 0) {
                throw new AlarmAudioException("bad_mp3", broken);
            }
        }
        return pcm;
    }

    /** De versie in een mp3-kopje voor MPEG-1 (3), de enige die JavaMP3 kent. MPEG-2 is 2, MPEG-2.5 is 0. */
    private static final int MPEG_1 = 3;

    /** Het eerste stukje: AAC met een kopje (ADTS), of mp3 met zijn versie en snelheid. */
    record FirstFrame(boolean adts, int version, int rate) {}

    /**
     * Zoek het eerste echte stukje (hooguit {@link #SCAN} bytes ver), zonder iets uit de stroom te halen (mark en reset).
     * Null als er niets op lijkt; dan zoekt de decoder zelf verder.
     *
     * Een mp3-kopje: 11 bits op 1, de versie (2 bits; 1 bestaat niet), de laag (2 bits; 0 bestaat niet bij mp3), dan in
     * de volgende byte de bitrate (4 bits; 15 bestaat niet) en de snelheid (2 bits; 3 bestaat niet). Een ADTS-kopje
     * (AAC): 12 bits op 1 en laag 0, met een snelheid (4 bits) onder 13.
     */
    static @Nullable FirstFrame firstFrame(InputStream in) throws IOException {
        in.mark(SCAN + 4);
        byte[] head = in.readNBytes(SCAN + 4);
        in.reset();
        for (int i = 0; i + 2 < head.length; i++) {
            int b1 = head[i + 1] & 0xFF;
            int b2 = head[i + 2] & 0xFF;
            if ((head[i] & 0xFF) != 0xFF || (b1 & 0xE0) != 0xE0) {
                continue;
            }
            int version = (b1 >> 3) & 3;
            int layer = (b1 >> 1) & 3;
            if (layer == 0) {
                if ((b1 & 0xF0) == 0xF0 && ((b2 >> 2) & 0x0F) < 13) {
                    return new FirstFrame(true, 0, 0);
                }
                continue;
            }
            int rateIndex = (b2 >> 2) & 3;
            if (version == 1 || (b2 >> 4) == 15 || rateIndex == 3) {
                continue;
            }
            int rate = new int[]{44100, 48000, 32000}[rateIndex] >> (version == MPEG_1 ? 0 : version == 2 ? 1 : 2);
            return new FirstFrame(false, version, rate);
        }
        return null;
    }

    /**
     * Sla ID3v2-labels aan het begin over (soms staan er een paar achter elkaar). Een label begint met "ID3", twee bytes
     * versie, één byte vlaggen en dan de lengte in 4 bytes van elk 7 bits.
     */
    static void skipId3(InputStream in) throws IOException {
        for (int tags = 0; tags < 4; tags++) {
            in.mark(10);
            byte[] head = in.readNBytes(10);
            if (head.length < 10 || head[0] != 'I' || head[1] != 'D' || head[2] != '3') {
                in.reset();
                return;
            }
            long size = ((head[6] & 0x7F) << 21) | ((head[7] & 0x7F) << 14) | ((head[8] & 0x7F) << 7) | (head[9] & 0x7F);
            if ((head[5] & 0x10) != 0) {
                size += 10; // er staat ook een "voet" achter het label
            }
            in.skipNBytes(size);
        }
    }
}
