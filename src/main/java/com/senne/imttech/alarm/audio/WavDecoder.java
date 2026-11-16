package com.senne.imttech.alarm.audio;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Een .wav-bestand inlezen (hoofdstuk 126). We lezen het zelf in in plaats van met Java's eigen javax.sound: dat is maar
 * een paar regels, en zo hangt het niet af van welke "geluidslezers" andere bibliotheken in Java inpluggen.
 *
 * Een wav is een rij "stukken" (chunks): {@code fmt } zegt hoe het geluid is opgeslagen (hoeveel kanalen, hoe snel,
 * hoeveel bits), {@code data} is het geluid zelf. Alles in "little endian" (de kleinste byte eerst). We kunnen gewone
 * getallen van 8, 16, 24 of 32 bit en kommagetallen (float) van 32 of 64 bit; samengeperst geluid in een wav (ADPCM, mp3)
 * niet.
 */
public final class WavDecoder {
    private WavDecoder() {}

    private static final int PCM = 1;
    private static final int FLOAT = 3;
    private static final int EXTENSIBLE = 0xFFFE;

    public static Pcm decode(Path file, double maxSeconds) throws IOException, AlarmAudioException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 65536)) {
            Reader reader = new Reader(in);
            if (reader.int32() != tag("RIFF")) {
                throw new AlarmAudioException("bad_wav");
            }
            reader.int32(); // de lengte van het hele bestand: die hebben we niet nodig
            if (reader.int32() != tag("WAVE")) {
                throw new AlarmAudioException("bad_wav");
            }
            int format = -1;
            int channels = 0;
            int rate = 0;
            int bits = 0;
            while (true) {
                int id;
                long size;
                try {
                    id = reader.int32();
                    size = reader.int32() & 0xFFFFFFFFL;
                } catch (EOFException end) {
                    throw new AlarmAudioException("bad_wav");
                }
                if (id == tag("fmt ")) {
                    format = reader.int16();
                    channels = reader.int16();
                    rate = reader.int32();
                    reader.int32(); // bytes per seconde
                    reader.int16(); // bytes per monster (alle kanalen samen)
                    bits = reader.int16();
                    long used = 16;
                    if (format == EXTENSIBLE && size >= 40) {
                        reader.int16(); // hoe lang dit extra stuk is
                        reader.int16(); // hoeveel bits er echt gebruikt worden
                        reader.int32(); // welke luidspreker welk kanaal is
                        format = reader.int16(); // het echte formaat: de eerste twee bytes van de "GUID"
                        used = 26;
                    }
                    reader.skip(size - used + (size & 1));
                } else if (id == tag("data")) {
                    if (format != PCM && format != FLOAT) {
                        throw new AlarmAudioException("unsupported_wav", format);
                    }
                    boolean okBits = format == PCM ? bits == 8 || bits == 16 || bits == 24 || bits == 32 : bits == 32 || bits == 64;
                    if (!okBits) {
                        throw new AlarmAudioException("unsupported_wav", format);
                    }
                    Pcm pcm = new Pcm(rate, channels, maxSeconds);
                    // Een wav die "live" opgenomen is, zegt soms 0 of 0xFFFFFFFF als lengte: dan lezen we tot het einde.
                    long values = size == 0 || size == 0xFFFFFFFFL ? Long.MAX_VALUE : size / (bits / 8);
                    for (long i = 0; i < values && !pcm.full(); i++) {
                        float value;
                        try {
                            value = sample(reader, format, bits);
                        } catch (EOFException end) {
                            break; // het bestand is korter dan het zegt: wat we hebben, houden we
                        }
                        pcm.add(value);
                    }
                    return pcm;
                } else {
                    reader.skip(size + (size & 1)); // een stuk dat we niet nodig hebben (LIST, bext...)
                }
            }
        }
    }

    /** Eén monster lezen, als getal tussen -1 en 1. */
    private static float sample(Reader reader, int format, int bits) throws IOException {
        if (format == FLOAT) {
            return bits == 32 ? Float.intBitsToFloat(reader.int32()) : (float) Double.longBitsToDouble(reader.int64());
        }
        return switch (bits) {
            case 8 -> (reader.byte8() - 128) / 128f; // 8 bit is "zonder teken": 128 is stil
            case 16 -> (short) reader.int16() / 32768f;
            case 24 -> {
                int low = reader.byte8();
                int mid = reader.byte8();
                int high = (byte) reader.byte8();
                yield ((high << 16) | (mid << 8) | low) / 8388608f;
            }
            default -> reader.int32() / 2147483648f;
        };
    }

    /** Vier letters als getal, zoals ze in het bestand staan ("RIFF"). */
    private static int tag(String text) {
        return text.charAt(0) | text.charAt(1) << 8 | text.charAt(2) << 16 | text.charAt(3) << 24;
    }

    /** Leest getallen met de kleinste byte eerst. */
    private static final class Reader {
        private final InputStream in;

        Reader(InputStream in) {
            this.in = in;
        }

        int byte8() throws IOException {
            int value = in.read();
            if (value < 0) {
                throw new EOFException();
            }
            return value;
        }

        int int16() throws IOException {
            return byte8() | byte8() << 8;
        }

        int int32() throws IOException {
            return byte8() | byte8() << 8 | byte8() << 16 | byte8() << 24;
        }

        long int64() throws IOException {
            return (int32() & 0xFFFFFFFFL) | (long) int32() << 32;
        }

        void skip(long bytes) throws IOException {
            long left = bytes;
            while (left > 0) {
                long skipped = in.skip(left);
                if (skipped <= 0) {
                    if (in.read() < 0) {
                        throw new EOFException();
                    }
                    skipped = 1;
                }
                left -= skipped;
            }
        }
    }
}
