package com.senne.imttech.alarm.audio;

import java.util.Arrays;

/**
 * Geluid zoals het uit een bestand komt, voordat we het omzetten (hoofdstuk 126): getallen tussen -1 en 1, met de
 * kanalen om en om (links, rechts, links, rechts...), op de snelheid van het bestand ({@link #rate} monsters per seconde).
 *
 * Een decoder vult hem met {@link #add}; zodra er genoeg seconden in zitten (meer dan we ooit gebruiken), zegt
 * {@link #full()} dat hij kan stoppen. Zo lezen we van een filmpje van een uur alleen het begin.
 */
public final class Pcm {
    public final int rate;
    public final int channels;
    private final long maxValues;
    private float[] data = new float[8192];
    private int size;

    /** Een lege buffer voor zoveel seconden (hooguit), bij deze snelheid en dit aantal kanalen. */
    public Pcm(int rate, int channels, double maxSeconds) throws AlarmAudioException {
        if (rate < 1000 || rate > 384_000) {
            throw new AlarmAudioException("bad_rate", rate);
        }
        if (channels < 1 || channels > 8) {
            throw new AlarmAudioException("bad_channels", channels);
        }
        this.rate = rate;
        this.channels = channels;
        this.maxValues = (long) Math.ceil(maxSeconds * rate) * channels;
    }

    /** Eén monster erbij (een kanaal). Geeft false als hij vol zit: dan kan de decoder stoppen. */
    public boolean add(float value) {
        if (size >= maxValues) {
            return false;
        }
        if (size == data.length) {
            data = Arrays.copyOf(data, (int) Math.min(maxValues, (long) data.length * 2));
        }
        data[size++] = Float.isFinite(value) ? Math.max(-1f, Math.min(1f, value)) : 0f;
        return true;
    }

    /** Een 16-bit-monster erbij (zoals in een wav of uit de mp3-decoder). */
    public boolean add16(int value) {
        return add(value / 32768f);
    }

    public boolean full() {
        return size >= maxValues;
    }

    /** Hoeveel monsters per kanaal er nu in zitten. */
    public int frames() {
        return size / channels;
    }

    /** Alles naar één kanaal (het gemiddelde van de kanalen), op dezelfde snelheid. */
    public float[] mono() {
        int frames = frames();
        float[] mono = new float[frames];
        for (int f = 0; f < frames; f++) {
            float sum = 0f;
            for (int c = 0; c < channels; c++) {
                sum += data[f * channels + c];
            }
            mono[f] = sum / channels;
        }
        return mono;
    }
}
