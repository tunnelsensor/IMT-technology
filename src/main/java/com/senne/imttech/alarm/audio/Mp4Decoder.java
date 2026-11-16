package com.senne.imttech.alarm.audio;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import com.senne.imttech.lib.jaad.aac.Decoder;
import com.senne.imttech.lib.jaad.aac.SampleBuffer;
import com.senne.imttech.lib.jaad.adts.ADTSDemultiplexer;

/**
 * Het geluid uit een .mp4 halen (jouw wens van 16 november: "een alarm waar je zelf een mp4 bestand naar kunt uploaden"),
 * en ook uit .m4a, .mov en losse .aac-bestanden.
 *
 * Een filmpje heeft meestal een beeldspoor en een geluidsspoor. {@link Mp4Demuxer} zoekt het eerste geluidsspoor en waar
 * elk stukje ervan in het bestand staat (het beeld laten we liggen); wij lezen die stukjes één voor één en decoderen ze:
 * AAC (bijna altijd zo: gsm's, YouTube-downloads, schermopnames) met de decoder van de bibliotheek JAAD (publiek domein, zie
 * THIRD-PARTY-LICENSES.txt), en mp3 in een mp4 (dat komt ook voor) met JavaMP3. We lezen alleen tot we genoeg seconden
 * hebben; van een groot filmpje dus alleen het begin.
 *
 * HE-AAC (AAC met "SBR", en soms "PS"; komt voor bij muziek in een klein bestand en bij sommige streams) leest JAAD niet
 * goed (gevonden door de nalezing). Daarom zetten we SBR altijd uit en lezen we alleen het gewone AAC-deel eronder, "de
 * kern": die heeft de halve snelheid, bij HE-AAC van 44,1 kHz dus 22.050 per seconde, en precies zo snel bewaart een
 * Alarm Speaker zijn geluid (AlarmAudio.RATE). We missen dus niets. Zie {@link #coreConfig}.
 */
public final class Mp4Decoder {
    private Mp4Decoder() {}

    /** Hoeveel kapotte stukjes we overslaan voordat we het opgeven. */
    private static final int MAX_BROKEN_FRAMES = 50;

    public static Pcm decode(Path file, double maxSeconds) throws IOException, AlarmAudioException {
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            Mp4Demuxer.Track track;
            try {
                track = Mp4Demuxer.audioTrack(in);
            } catch (IOException | RuntimeException broken) {
                throw new AlarmAudioException("bad_mp4", broken);
            }
            if (track.objectType() == Mp4Demuxer.MP3) {
                return Mp3Decoder.decodeStream(new SampleStream(in, track.samples()), maxSeconds);
            }
            Decoder decoder;
            try {
                decoder = new Decoder(coreConfig(track.config()));
                decoder.getConfig().setSBREnabled(false);
            } catch (IOException | RuntimeException broken) {
                throw new AlarmAudioException("unsupported_mp4_codec", broken);
            }
            Iterator<long[]> samples = track.samples();
            Frames frames = () -> {
                if (!samples.hasNext()) {
                    return null;
                }
                long[] sample = samples.next();
                return readSample(in, sample);
            };
            return decodeFrames(decoder, frames, maxSeconds);
        }
    }

    /** Eén stukje uit het bestand lezen (plaats, grootte), als het binnen het bestand ligt. Null = klaar. */
    private static byte[] readSample(RandomAccessFile in, long[] sample) throws IOException {
        long offset = sample[0];
        long size = sample[1];
        if (offset < 0 || size <= 0 || size > Mp4Demuxer.MAX_SAMPLE || offset + size > in.length()) {
            return null; // dit klopt niet (een afgebroken bestand): stoppen met wat we hebben
        }
        byte[] bytes = new byte[(int) size];
        in.seek(offset);
        in.readFully(bytes);
        return bytes;
    }

    /** Een los .aac-bestand ("ADTS": AAC-stukjes achter elkaar, elk met een eigen kopje). */
    public static Pcm decodeAdts(Path file, double maxSeconds) throws IOException, AlarmAudioException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 65536)) {
            return decodeAdtsStream(in, maxSeconds);
        }
    }

    /**
     * AAC-stukjes met een eigen kopje (ADTS) uit een stroom: een los .aac-bestand, of een .aac met een ID3-label ervoor
     * (dat slaat Mp3Decoder eerst over, en dan komt hij hier).
     */
    static Pcm decodeAdtsStream(InputStream in, double maxSeconds) throws AlarmAudioException {
        ADTSDemultiplexer adts;
        Decoder decoder;
        try {
            adts = new ADTSDemultiplexer(in);
            // Het kopje zegt bij HE-AAC al gewoon "AAC" met de snelheid van de kern: alleen SBR uitzetten.
            decoder = new Decoder(adts.getDecoderSpecificInfo());
            decoder.getConfig().setSBREnabled(false);
        } catch (IOException | RuntimeException broken) {
            throw new AlarmAudioException("bad_mp4", broken);
        }
        Frames frames = () -> {
            try {
                return adts.readNextFrame();
            } catch (EOFException end) {
                return null;
            }
        };
        return decodeFrames(decoder, frames, maxSeconds);
    }

    /** Geeft het volgende AAC-stukje, of null aan het einde. */
    private interface Frames {
        byte[] next() throws IOException;
    }

    /**
     * Stukje voor stukje decoderen tot er genoeg seconden zijn. Hoe snel en hoeveel kanalen weten we pas na het eerste
     * stukje (bij "HE-AAC" is het geluid twee keer zo snel als wat in het bestand staat).
     */
    private static Pcm decodeFrames(Decoder decoder, Frames frames, double maxSeconds) throws AlarmAudioException {
        SampleBuffer buffer = new SampleBuffer();
        buffer.setBigEndian(false);
        Pcm pcm = null;
        int broken = 0;
        Exception firstProblem = null;
        while (pcm == null || !pcm.full()) {
            byte[] frame;
            try {
                frame = frames.next();
            } catch (IOException | RuntimeException end) {
                if (firstProblem == null) {
                    firstProblem = end;
                }
                break; // het bestand houdt middenin op: wat we hebben, houden we
            }
            if (frame == null) {
                break;
            }
            try {
                decoder.decodeFrame(frame, buffer);
            } catch (IOException | RuntimeException bad) {
                if (firstProblem == null) {
                    firstProblem = bad;
                }
                if (++broken > MAX_BROKEN_FRAMES) {
                    break;
                }
                continue;
            }
            byte[] data = buffer.getData();
            // Een stukje dat te vroeg ophoudt, geeft JAAD zonder fout terug, maar leeg (snelheid 0): overslaan, net als
            // een kapot stukje. (Gevonden door de nalezing: eerst werd het eerste lege stukje de snelheid van alles.)
            if (data.length == 0 || buffer.getSampleRate() <= 0 || buffer.getChannels() <= 0) {
                if (++broken > MAX_BROKEN_FRAMES) {
                    break;
                }
                continue;
            }
            if (buffer.getBitsPerSample() != 16) {
                throw new AlarmAudioException("unsupported_mp4_codec");
            }
            if (pcm == null) {
                pcm = new Pcm(buffer.getSampleRate(), buffer.getChannels(), maxSeconds);
            } else if (buffer.getSampleRate() != pcm.rate || buffer.getChannels() != pcm.channels) {
                // Halverwege opeens een andere snelheid of een ander aantal kanalen: dat stukje past er niet bij.
                if (++broken > MAX_BROKEN_FRAMES) {
                    break;
                }
                continue;
            }
            for (int i = 0; i + 1 < data.length && !pcm.full(); i += 2) {
                pcm.add16((short) ((data[i] & 0xFF) | data[i + 1] << 8));
            }
        }
        if (pcm == null) {
            throw firstProblem == null ? new AlarmAudioException("bad_mp4") : new AlarmAudioException("bad_mp4", firstProblem);
        }
        return pcm;
    }

    /**
     * De beschrijving van een AAC-spoor ("AudioSpecificConfig", uit de esds-doos) zonder HE-AAC. Staat er soort 5 (SBR)
     * of 29 (PS) in, dan schrijven we die om naar de soort van de kern (bijna altijd 2, gewone AAC) met de snelheid van de
     * kern: JAAD zou de kern anders met de dubbele snelheid lezen (die van SBR), en dan klinkt er niets van. Al het andere
     * blijft zoals het was. (ISO/IEC 14496-3, 1.6.2.1: soort, snelheid, kanalen; bij 5 en 29 dan de snelheid van SBR en de
     * soort van de kern; daarna de rest, die we ongewijzigd overnemen.)
     *
     * Voorbeeld: HE-AAC stereo 44,1 kHz is 2B 92 08 00 (soort 5, kern 22.050, SBR 44.100, kern soort 2) en wordt
     * 13 90 00: gewone AAC, 22.050, stereo.
     */
    public static byte[] coreConfig(byte[] config) {
        try {
            BitReader in = new BitReader(config);
            int type = in.objectType();
            if (type != 5 && type != 29) {
                return config;
            }
            int rateIndex = in.read(4);
            int rate = rateIndex == 15 ? in.read(24) : 0;
            int channels = in.read(4);
            if (in.read(4) == 15) {
                in.read(24); // de snelheid van SBR, die we niet gebruiken
            }
            int core = in.objectType();
            if (core == 22) {
                in.read(4); // nog een kanaalgetal (alleen bij BSAC, dat JAAD toch niet kent)
            }
            BitWriter out = new BitWriter();
            out.objectType(core);
            out.write(rateIndex, 4);
            if (rateIndex == 15) {
                out.write(rate, 24);
            }
            out.write(channels, 4);
            while (in.left() > 0) {
                int bits = Math.min(8, in.left());
                out.write(in.read(bits), bits);
            }
            return out.bytes();
        } catch (IndexOutOfBoundsException tooShort) {
            return config; // te kort om te kloppen: dan zegt JAAD wel wat er mis is
        }
    }

    /** Bits lezen, de hoogste eerst. */
    private static final class BitReader {
        private final byte[] data;
        private int position;

        BitReader(byte[] data) {
            this.data = data;
        }

        int left() {
            return data.length * 8 - position;
        }

        int read(int bits) {
            if (bits > left()) {
                throw new IndexOutOfBoundsException("te weinig bits");
            }
            int value = 0;
            for (int i = 0; i < bits; i++, position++) {
                value = value << 1 | (data[position >> 3] >> (7 - (position & 7))) & 1;
            }
            return value;
        }

        /** Een soort ("audio object type"): 5 bits, en 31 betekent "32 plus de volgende 6 bits". */
        int objectType() {
            int type = read(5);
            return type == 31 ? 32 + read(6) : type;
        }
    }

    /** Bits schrijven, de hoogste eerst; de laatste byte wordt met nullen aangevuld. */
    private static final class BitWriter {
        private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        private int current;
        private int filled;

        void write(int value, int bits) {
            for (int i = bits - 1; i >= 0; i--) {
                current = current << 1 | (value >> i) & 1;
                if (++filled == 8) {
                    out.write(current);
                    current = 0;
                    filled = 0;
                }
            }
        }

        void objectType(int type) {
            if (type >= 32) {
                write(31, 5);
                write(type - 32, 6);
            } else {
                write(type, 5);
            }
        }

        byte[] bytes() {
            if (filled > 0) {
                out.write(current << (8 - filled));
                current = 0;
                filled = 0;
            }
            return out.toByteArray();
        }
    }

    /** De stukjes van een mp3-spoor in een mp4, achter elkaar als één stroom (zoals een gewoon mp3-bestand). */
    private static final class SampleStream extends InputStream {
        private final RandomAccessFile file;
        private final Iterator<long[]> samples;
        private byte[] current = new byte[0];
        private int index;

        SampleStream(RandomAccessFile file, Iterator<long[]> samples) {
            this.file = file;
            this.samples = samples;
        }

        private boolean fill() throws IOException {
            while (index >= current.length) {
                if (!samples.hasNext()) {
                    return false;
                }
                byte[] next = readSample(file, samples.next());
                if (next == null) {
                    return false;
                }
                current = next;
                index = 0;
            }
            return true;
        }

        @Override
        public int read() throws IOException {
            return fill() ? current[index++] & 0xFF : -1;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (!fill()) {
                return -1;
            }
            int count = Math.min(length, current.length - index);
            System.arraycopy(current, index, target, offset, count);
            index += count;
            return count;
        }
    }
}
