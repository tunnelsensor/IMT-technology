package com.senne.imttech.alarm.audio;

/**
 * Hoe we een alarmgeluid klein maken om het te uploaden, te bewaren en naar de spelers te sturen (hoofdstuk 126): "IMA
 * ADPCM". Elk geluidsmonster (een getal van 16 bit) wordt 4 bits: het onthoudt niet het getal zelf, maar hoeveel het
 * verschilt van het vorige, in grove stapjes die vanzelf groter worden als het geluid hard verandert. Zo is een alarm van
 * 30 seconden ongeveer 330 kB in plaats van 1,3 MB. Het klinkt een beetje ruisig, maar voor een sirene hoor je dat
 * nauwelijks. Oude spellen en telefoons deden het net zo.
 *
 * Het geluid zit in blokken van {@link #BLOCK_BYTES} bytes. Elk blok begint met 4 bytes (het eerste monster zelf, en hoe
 * groot de stapjes op dat moment zijn); daarna komen twee monsters per byte (eerst de onderste 4 bits). Zo begint elk blok
 * weer "schoon": één kapotte byte verpest hooguit één blok (een twintigste seconde), niet de rest van het geluid. Dit is
 * dezelfde indeling als in een .wav-bestand met IMA ADPCM, voor één kanaal.
 *
 * Dit is gewone Java zonder Minecraft: de server, je spel en de game tests gebruiken het allemaal.
 */
public final class Adpcm {
    private Adpcm() {}

    /** Zoveel bytes per blok. */
    public static final int BLOCK_BYTES = 512;
    /** Zoveel monsters per blok: één in de kop, en twee per byte in de rest. */
    public static final int BLOCK_SAMPLES = 1 + (BLOCK_BYTES - 4) * 2;

    /** Hoeveel de stapgrootte verandert na elke code (de standaardtabel van IMA ADPCM). */
    private static final int[] INDEX_TABLE = {-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8};

    /** De 89 stapgroottes van IMA ADPCM. */
    private static final int[] STEP_TABLE = {
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
            50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
            337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
            2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
            15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767};

    /** Hoeveel bytes {@code samples} monsters worden (de laatste blok is korter als hij niet vol is). */
    public static int encodedLength(int samples) {
        if (samples <= 0) {
            return 0;
        }
        int full = samples / BLOCK_SAMPLES;
        int rest = samples % BLOCK_SAMPLES;
        int bytes = full * BLOCK_BYTES;
        if (rest > 0) {
            bytes += 4 + rest / 2; // de kop + (rest - 1) monsters, twee per byte (naar boven afgerond)
        }
        return bytes;
    }

    /** Maak het geluid klein. */
    public static byte[] encode(short[] samples) {
        byte[] out = new byte[encodedLength(samples.length)];
        int write = 0;
        for (int start = 0; start < samples.length; start += BLOCK_SAMPLES) {
            int count = Math.min(BLOCK_SAMPLES, samples.length - start);
            int predictor = samples[start];
            int index = bestStartIndex(samples, start, count);
            out[write++] = (byte) predictor;
            out[write++] = (byte) (predictor >> 8);
            out[write++] = (byte) index;
            out[write++] = 0;
            // Twee monsters per byte: eerst de onderste 4 bits.
            for (int i = 1; i < count; i += 2) {
                int low = encodeOne(samples[start + i], predictor, index);
                predictor = nextPredictor(predictor, index, low);
                index = nextIndex(index, low);
                int high = 0;
                if (i + 1 < count) {
                    high = encodeOne(samples[start + i + 1], predictor, index);
                    predictor = nextPredictor(predictor, index, high);
                    index = nextIndex(index, high);
                }
                out[write++] = (byte) (low | (high << 4));
            }
        }
        return out;
    }

    /** Maak het geluid weer groot: {@code samples} monsters uit {@code data}. Te weinig bytes = de rest is stil. */
    public static short[] decode(byte[] data, int samples) {
        short[] out = new short[Math.max(0, samples)];
        int read = 0;
        for (int start = 0; start < out.length; start += BLOCK_SAMPLES) {
            int count = Math.min(BLOCK_SAMPLES, out.length - start);
            if (read + 4 > data.length) {
                break; // het bestand is korter dan het zegt: de rest blijft stil
            }
            int predictor = (short) ((data[read] & 0xFF) | (data[read + 1] << 8));
            int index = Math.min(88, data[read + 2] & 0xFF);
            read += 4;
            out[start] = (short) predictor;
            for (int i = 1; i < count; i += 2) {
                if (read >= data.length) {
                    return out;
                }
                int packed = data[read++] & 0xFF;
                predictor = nextPredictor(predictor, index, packed & 0x0F);
                index = nextIndex(index, packed & 0x0F);
                out[start + i] = (short) predictor;
                if (i + 1 < count) {
                    predictor = nextPredictor(predictor, index, packed >> 4);
                    index = nextIndex(index, packed >> 4);
                    out[start + i + 1] = (short) predictor;
                }
            }
        }
        return out;
    }

    /**
     * Met welke stapgrootte een blok het beste begint: ongeveer zo groot als het verschil tussen de eerste monsters, zodat
     * een hard geluid niet eerst een paar milliseconden "inloopt".
     */
    private static int bestStartIndex(short[] samples, int start, int count) {
        int diff = 0;
        for (int i = 1; i < Math.min(count, 9); i++) {
            diff = Math.max(diff, Math.abs(samples[start + i] - samples[start + i - 1]));
        }
        int index = 0;
        while (index < 88 && STEP_TABLE[index] < diff / 2) {
            index++;
        }
        return index;
    }

    /** De 4-bit-code die het monster het dichtst benadert, gegeven wat de decoder nu weet. */
    private static int encodeOne(int sample, int predictor, int index) {
        int step = STEP_TABLE[index];
        int diff = sample - predictor;
        int code = 0;
        if (diff < 0) {
            code = 8;
            diff = -diff;
        }
        // Zoals de standaard: drie bits, elk "past er nog een half/kwart/achtste stap bij".
        int mask = 4;
        int tempStep = step;
        for (int bit = 0; bit < 3; bit++) {
            if (diff >= tempStep) {
                code |= mask;
                diff -= tempStep;
            }
            tempStep >>= 1;
            mask >>= 1;
        }
        return code;
    }

    /** Het volgende monster, zoals de decoder het uit de code haalt (de encoder rekent precies hetzelfde na). */
    private static int nextPredictor(int predictor, int index, int code) {
        int step = STEP_TABLE[index];
        int diff = step >> 3;
        if ((code & 4) != 0) {
            diff += step;
        }
        if ((code & 2) != 0) {
            diff += step >> 1;
        }
        if ((code & 1) != 0) {
            diff += step >> 2;
        }
        int next = (code & 8) != 0 ? predictor - diff : predictor + diff;
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, next));
    }

    private static int nextIndex(int index, int code) {
        return Math.max(0, Math.min(88, index + INDEX_TABLE[code & 0x0F]));
    }
}
