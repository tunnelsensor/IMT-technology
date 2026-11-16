package com.senne.imttech.alarm.audio;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.jetbrains.annotations.Nullable;

/**
 * Het geluidsspoor uit een mp4 halen (hoofdstuk 126): waar elk stukje geluid in het bestand staat, en hoe het gedecodeerd
 * moet worden. Alleen het uitpakken; het decoderen doet Mp4Decoder (AAC met JAAD, mp3 met JavaMP3).
 *
 * Waarom zelf? Een mp4 is een "doos met doosjes", en echte bestanden (van een gsm, ffmpeg, een downloadsite) hebben er
 * allerlei doosjes in die een algemene lezer moet begrijpen, zoals {@code udta/meta} met de titel: daar struikelde de
 * mp4-lezer van JAAD over, waardoor bijna elk echt filmpje mislukte. Wij kijken alleen naar wat we nodig hebben, en slaan
 * al het andere over:
 * <pre>
 *   moov > trak > mdia > hdlr                ("soun" = een geluidsspoor)
 *                      > minf > stbl > stsd  (mp4a > esds: welk soort geluid, en de instellingen van de decoder)
 *                                  > stsz    (hoe groot elk stukje is)
 *                                  > stsc    (hoeveel stukjes er per blok staan)
 *                                  > stco / co64 (waar elk blok in het bestand begint)
 * </pre>
 * Ook "gefragmenteerde" mp4's (zoals een m4a van YouTube): daar staan de stukjes niet in de moov, maar in losse
 * {@code moof}-doosjes (traf > tfhd + trun) tussen het geluid door.
 *
 * Elk getal komt uit een vreemd bestand: alles wordt nagekeken (geen negatieve groottes, niets buiten het bestand, hooguit
 * {@link #MAX_BOX} bytes in het geheugen), zodat een kapot bestand een nette fout geeft en nooit het spel laat vallen.
 */
final class Mp4Demuxer {
    private Mp4Demuxer() {}

    /** Zo groot mag een doos die we in het geheugen lezen (de moov of een moof) hooguit zijn. */
    private static final long MAX_BOX = 64L * 1024 * 1024;
    /** Zo groot mag één stukje geluid hooguit zijn (een AAC-stukje is hooguit een paar kilobyte). */
    static final int MAX_SAMPLE = 1 << 20;
    /** Hooguit zoveel stukjes noteren we uit de tabel (AAC: ruim een half uur; we lezen er hooguit ruim twee minuten van). */
    private static final long MAX_LISTED = 100_000;

    /** Het soort geluid volgens de esds (objectTypeIndication). */
    static final int AAC = 0x40;
    static final int MP3 = 0x6B;
    static final int MP3_MPEG2 = 0x69;

    /** Het geluidsspoor: het soort, de instellingen voor de decoder, en de stukjes. */
    record Track(int objectType, byte[] config, Iterator<long[]> samples) {}

    /** Zoek het eerste geluidsspoor dat we kunnen lezen. */
    static Track audioTrack(RandomAccessFile file) throws IOException, AlarmAudioException {
        long length = file.length();
        long at = 0;
        ByteBuffer moov = null;
        List<Long> moofs = new ArrayList<>();
        while (at + 8 <= length) {
            file.seek(at);
            long size = Integer.toUnsignedLong(file.readInt());
            int type = file.readInt();
            int header = 8;
            if (size == 1) {
                size = file.readLong();
                header = 16;
            } else if (size == 0) {
                size = length - at;
            }
            if (size < header || at + size > length) {
                break; // kapot of afgebroken einde: wat we al hebben, gebruiken we
            }
            if (type == fourcc("moov") && moov == null) {
                moov = read(file, at + header, size - header);
            } else if (type == fourcc("moof")) {
                moofs.add(at);
            }
            at += size;
        }
        if (moov == null) {
            throw new AlarmAudioException("bad_mp4");
        }
        return fromMoov(file, moov, moofs);
    }

    // ================================================================ De moov

    private static Track fromMoov(RandomAccessFile file, ByteBuffer moov, List<Long> moofs) throws IOException, AlarmAudioException {
        boolean sawAudio = false;
        for (ByteBuffer trak : children(moov, "trak")) {
            ByteBuffer mdia = child(trak, "mdia");
            ByteBuffer hdlr = mdia == null ? null : child(mdia, "hdlr");
            if (hdlr == null || hdlr.remaining() < 12 || hdlr.getInt(hdlr.position() + 8) != fourcc("soun")) {
                continue;
            }
            sawAudio = true;
            ByteBuffer stbl = path(mdia, "minf", "stbl");
            ByteBuffer stsd = stbl == null ? null : child(stbl, "stsd");
            Codec codec = stsd == null ? null : codec(stsd);
            if (codec == null) {
                continue; // een geluidsspoor dat geen AAC of mp3 is (AC-3, Opus, PCM...)
            }
            int trackId = trackId(trak);
            List<long[]> table = sampleTable(stbl);
            if (!table.isEmpty()) {
                return new Track(codec.objectType, codec.config, table.iterator());
            }
            if (!moofs.isEmpty()) {
                return new Track(codec.objectType, codec.config, new Fragments(file, moofs, trackId, defaultSize(moov, trackId)));
            }
        }
        throw new AlarmAudioException(sawAudio ? "unsupported_mp4_codec" : "no_audio_track");
    }

    /** Het soort geluid en de instellingen van de decoder, uit de eerste mp4a in de stsd. */
    private record Codec(int objectType, byte[] config) {}

    private static @Nullable Codec codec(ByteBuffer stsd) {
        ByteBuffer box = stsd.duplicate();
        if (box.remaining() < 8) {
            return null;
        }
        box.position(box.position() + 8); // version + flags, en hoeveel beschrijvingen er zijn
        while (box.remaining() >= 8) {
            int start = box.position();
            long size = Integer.toUnsignedLong(box.getInt());
            int type = box.getInt();
            if (size < 8 || size > box.limit() - start) {
                return null;
            }
            int end = (int) (start + size);
            if (type == fourcc("mp4a")) {
                // Een AudioSampleEntry: 6 + 2 + 8 bytes, en dan kanalen, bits, ... samen 28 bytes na de kop. Een
                // QuickTime-bestand (versie 1 of 2) heeft er nog 16 of 36 bytes bij.
                if (end - box.position() < 28) {
                    return null;
                }
                int version = box.getShort(box.position() + 8) & 0xFFFF;
                int skip = 28 + (version == 1 ? 16 : version == 2 ? 36 : 0);
                ByteBuffer inner = box.duplicate();
                inner.position(Math.min(end, box.position() + skip)).limit(end);
                ByteBuffer esds = find(inner, "esds", 3);
                if (esds != null) {
                    Codec codec = esds(esds);
                    if (codec != null) {
                        return codec;
                    }
                }
            }
            box.position(end);
        }
        return null;
    }

    /**
     * De esds: een rij "descriptors" (een nummer, een lengte van 1 tot 4 bytes, de inhoud). Het ES-blok (3) bevat het
     * DecoderConfig-blok (4): het soort geluid (1 byte) en daarin het DecoderSpecificInfo-blok (5): de instellingen.
     */
    private static @Nullable Codec esds(ByteBuffer esds) {
        ByteBuffer data = esds.duplicate();
        if (data.remaining() < 4) {
            return null;
        }
        data.position(data.position() + 4); // version + flags
        if (descriptor(data, 0x03) < 0) {
            return null;
        }
        if (data.remaining() < 3) {
            return null;
        }
        data.getShort(); // ES_ID
        int flags = data.get() & 0xFF;
        if ((flags & 0x80) != 0) {
            skip(data, 2);
        }
        if ((flags & 0x40) != 0) {
            skip(data, data.hasRemaining() ? data.get() & 0xFF : 0);
        }
        if ((flags & 0x20) != 0) {
            skip(data, 2);
        }
        if (descriptor(data, 0x04) < 0 || data.remaining() < 13) {
            return null;
        }
        int objectType = data.get() & 0xFF;
        skip(data, 12); // streamType, bufferSizeDB, maxBitrate, avgBitrate
        byte[] config = new byte[0];
        int length = descriptor(data, 0x05);
        if (length >= 0) {
            if (length > data.remaining() || length > 64) {
                return null;
            }
            config = new byte[length];
            data.get(config);
        }
        if (objectType == 0x40 || objectType == 0x66 || objectType == 0x67 || objectType == 0x68) {
            return config.length >= 2 ? new Codec(AAC, config) : null;
        }
        if (objectType == MP3 || objectType == MP3_MPEG2) {
            return new Codec(MP3, config);
        }
        return null;
    }

    /** Lees de kop van een descriptor met dit nummer: geeft zijn lengte, of -1 als er iets anders staat. */
    private static int descriptor(ByteBuffer data, int tag) {
        if (!data.hasRemaining() || (data.get() & 0xFF) != tag) {
            return -1;
        }
        int length = 0;
        for (int i = 0; i < 4 && data.hasRemaining(); i++) {
            int b = data.get() & 0xFF;
            length = (length << 7) | (b & 0x7F);
            if ((b & 0x80) == 0) {
                break;
            }
        }
        return length;
    }

    private static void skip(ByteBuffer data, int bytes) {
        data.position(Math.min(data.limit(), data.position() + Math.max(0, bytes)));
    }

    /** Het nummer van een spoor (uit de tkhd), voor de moof-doosjes. */
    private static int trackId(ByteBuffer trak) {
        ByteBuffer tkhd = child(trak, "tkhd");
        if (tkhd == null || tkhd.remaining() < 24) {
            return -1;
        }
        int version = tkhd.get(tkhd.position()) & 0xFF;
        int at = tkhd.position() + 4 + (version == 1 ? 16 : 8);
        return at + 4 <= tkhd.limit() ? tkhd.getInt(at) : -1;
    }

    /** De standaardgrootte van een stukje in een gefragmenteerd bestand (mvex > trex), of 0. */
    private static long defaultSize(ByteBuffer moov, int trackId) {
        ByteBuffer mvex = child(moov, "mvex");
        if (mvex == null) {
            return 0;
        }
        for (ByteBuffer trex : children(mvex, "trex")) {
            if (trex.remaining() >= 24 && trex.getInt(trex.position() + 4) == trackId) {
                return Integer.toUnsignedLong(trex.getInt(trex.position() + 16));
            }
        }
        return 0;
    }

    /** Waar elk stukje staat (plaats in het bestand, grootte), uit stsz, stsc en stco/co64. Leeg = gefragmenteerd. */
    private static List<long[]> sampleTable(ByteBuffer stbl) throws AlarmAudioException {
        List<long[]> samples = new ArrayList<>();
        ByteBuffer stsz = child(stbl, "stsz");
        ByteBuffer stsc = child(stbl, "stsc");
        ByteBuffer stco = child(stbl, "stco");
        ByteBuffer co64 = child(stbl, "co64");
        if (stsz == null || stsc == null || stco == null && co64 == null) {
            return samples;
        }
        // Grootte van elk stukje.
        if (stsz.remaining() < 12) {
            throw new AlarmAudioException("bad_mp4");
        }
        int base = stsz.position();
        long fixed = Integer.toUnsignedLong(stsz.getInt(base + 4));
        long count = Integer.toUnsignedLong(stsz.getInt(base + 8));
        if (count == 0) {
            return samples;
        }
        if (fixed == 0 && count > (stsz.remaining() - 12) / 4 || count > 50_000_000L) {
            throw new AlarmAudioException("bad_mp4");
        }
        // Waar elk blok begint.
        ByteBuffer offsets = stco != null ? stco : co64;
        int width = stco != null ? 4 : 8;
        int ob = offsets.position();
        if (offsets.remaining() < 8) {
            throw new AlarmAudioException("bad_mp4");
        }
        long chunks = Integer.toUnsignedLong(offsets.getInt(ob + 4));
        if (chunks > (offsets.remaining() - 8) / width) {
            throw new AlarmAudioException("bad_mp4");
        }
        // Hoeveel stukjes per blok (in "loopjes": vanaf blok X telkens Y stukjes).
        int sb = stsc.position();
        if (stsc.remaining() < 8) {
            throw new AlarmAudioException("bad_mp4");
        }
        long runs = Integer.toUnsignedLong(stsc.getInt(sb + 4));
        if (runs == 0 || runs > (stsc.remaining() - 8) / 12) {
            throw new AlarmAudioException("bad_mp4");
        }
        // De loopjes staan op volgorde van "vanaf blok": we lopen er één keer doorheen, samen met de blokken. En we
        // noteren hooguit MAX_LISTED stukjes: dat is ruim meer dan de paar minuten die we ooit lezen.
        long limit = Math.min(count, MAX_LISTED);
        int run = 0;
        long perChunk = 0;
        int sample = 0;
        for (long chunk = 1; chunk <= chunks && sample < limit; chunk++) {
            while (run < runs && Integer.toUnsignedLong(stsc.getInt(sb + 8 + run * 12)) <= chunk) {
                perChunk = Integer.toUnsignedLong(stsc.getInt(sb + 8 + run * 12 + 4));
                run++;
            }
            long offset = width == 4 ? Integer.toUnsignedLong(offsets.getInt(ob + 8 + (int) (chunk - 1) * 4))
                    : offsets.getLong(ob + 8 + (int) (chunk - 1) * 8);
            for (long s = 0; s < perChunk && sample < limit; s++, sample++) {
                long size = fixed != 0 ? fixed : Integer.toUnsignedLong(stsz.getInt(base + 12 + sample * 4));
                samples.add(new long[]{offset, size});
                offset += size;
            }
        }
        return samples;
    }

    // ================================================================ Gefragmenteerd (moof)

    /** De stukjes van een gefragmenteerd bestand, moof voor moof gelezen (pas als ze nodig zijn). */
    private static final class Fragments implements Iterator<long[]> {
        private final RandomAccessFile file;
        private final List<Long> moofs;
        private final int trackId;
        private final long trexSize;
        private int nextMoof;
        private final List<long[]> pending = new ArrayList<>();
        private int index;

        Fragments(RandomAccessFile file, List<Long> moofs, int trackId, long trexSize) {
            this.file = file;
            this.moofs = moofs;
            this.trackId = trackId;
            this.trexSize = trexSize;
        }

        @Override
        public boolean hasNext() {
            while (index >= pending.size() && nextMoof < moofs.size()) {
                pending.clear();
                index = 0;
                try {
                    readMoof(moofs.get(nextMoof++));
                } catch (IOException | RuntimeException broken) {
                    nextMoof = moofs.size(); // kapot: stoppen met wat we hebben
                }
            }
            return index < pending.size();
        }

        @Override
        public long[] next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return pending.get(index++);
        }

        private void readMoof(long start) throws IOException {
            file.seek(start);
            long size = Integer.toUnsignedLong(file.readInt());
            file.readInt();
            int header = 8;
            if (size == 1) {
                size = file.readLong();
                header = 16;
            }
            ByteBuffer moof = read(file, start + header, size - header);
            for (ByteBuffer traf : children(moof, "traf")) {
                ByteBuffer tfhd = child(traf, "tfhd");
                if (tfhd == null || tfhd.remaining() < 8) {
                    continue;
                }
                int at = tfhd.position();
                int flags = tfhd.getInt(at) & 0xFFFFFF;
                int id = tfhd.getInt(at + 4);
                if (trackId >= 0 && id != trackId) {
                    continue;
                }
                at += 8;
                long base = start; // standaard: vanaf het begin van de moof
                if ((flags & 0x1) != 0) {
                    base = tfhd.getLong(at);
                    at += 8;
                }
                if ((flags & 0x2) != 0) {
                    at += 4;
                }
                if ((flags & 0x8) != 0) {
                    at += 4;
                }
                long defaultSize = trexSize;
                if ((flags & 0x10) != 0) {
                    defaultSize = Integer.toUnsignedLong(tfhd.getInt(at));
                }
                long dataEnd = base;
                for (ByteBuffer trun : children(traf, "trun")) {
                    dataEnd = trun(trun, base, dataEnd, defaultSize);
                }
            }
        }

        /** Eén trun: hoeveel stukjes, waar ze beginnen, en per stukje (misschien) zijn grootte. Geeft waar zijn data eindigt. */
        private long trun(ByteBuffer trun, long base, long previousEnd, long defaultSize) {
            int at = trun.position();
            int end = trun.limit();
            if (end - at < 8) {
                return previousEnd;
            }
            int flags = trun.getInt(at) & 0xFFFFFF;
            long count = Integer.toUnsignedLong(trun.getInt(at + 4));
            at += 8;
            long offset = previousEnd;
            if ((flags & 0x1) != 0) {
                offset = base + trun.getInt(at);
                at += 4;
            }
            if ((flags & 0x4) != 0) {
                at += 4;
            }
            int perSample = ((flags & 0x100) != 0 ? 4 : 0) + ((flags & 0x200) != 0 ? 4 : 0) + ((flags & 0x400) != 0 ? 4 : 0)
                    + ((flags & 0x800) != 0 ? 4 : 0);
            if (count > 10_000_000L || perSample > 0 && count > (end - at) / perSample) {
                return previousEnd;
            }
            for (long i = 0; i < count; i++) {
                int field = at;
                if ((flags & 0x100) != 0) {
                    field += 4;
                }
                long size = defaultSize;
                if ((flags & 0x200) != 0) {
                    size = Integer.toUnsignedLong(trun.getInt(field));
                }
                at += perSample;
                pending.add(new long[]{offset, size});
                offset += size;
            }
            return offset;
        }
    }

    // ================================================================ Doosjes lezen

    /** Lees zoveel bytes vanaf deze plek in het geheugen (hooguit MAX_BOX). */
    private static ByteBuffer read(RandomAccessFile file, long at, long size) throws IOException {
        if (size < 0 || size > MAX_BOX || at < 0 || at + size > file.length()) {
            throw new EOFException("box out of range");
        }
        byte[] bytes = new byte[(int) size];
        file.seek(at);
        file.readFully(bytes);
        return ByteBuffer.wrap(bytes);
    }

    /** De inhoud (zonder kop) van alle kinderen met dit type, in volgorde. */
    static List<ByteBuffer> children(ByteBuffer parent, String type) {
        List<ByteBuffer> found = new ArrayList<>();
        int wanted = fourcc(type);
        int at = parent.position();
        int end = parent.limit();
        while (end - at >= 8) {
            long size = Integer.toUnsignedLong(parent.getInt(at));
            int kind = parent.getInt(at + 4);
            int header = 8;
            if (size == 1) {
                if (end - at < 16) {
                    break;
                }
                size = parent.getLong(at + 8);
                header = 16;
            } else if (size == 0) {
                size = end - at;
            }
            if (size < header || size > end - at) {
                break; // kapot: niet verder
            }
            if (kind == wanted) {
                ByteBuffer body = parent.duplicate();
                body.position(at + header).limit((int) (at + size));
                found.add(body.slice());
            }
            at += (int) size;
        }
        return found;
    }

    static @Nullable ByteBuffer child(ByteBuffer parent, String type) {
        List<ByteBuffer> found = children(parent, type);
        return found.isEmpty() ? null : found.get(0);
    }

    private static @Nullable ByteBuffer path(ByteBuffer parent, String... types) {
        ByteBuffer at = parent;
        for (String type : types) {
            at = child(at, type);
            if (at == null) {
                return null;
            }
        }
        return at;
    }

    /** Zoek een doos van dit type, ook een paar lagen diep (QuickTime verstopt de esds soms in een "wave"-doos). */
    private static @Nullable ByteBuffer find(ByteBuffer parent, String type, int depth) {
        ByteBuffer direct = child(parent, type);
        if (direct != null || depth <= 0) {
            return direct;
        }
        for (ByteBuffer wave : children(parent, "wave")) {
            ByteBuffer inside = find(wave, type, depth - 1);
            if (inside != null) {
                return inside;
            }
        }
        return null;
    }

    static int fourcc(String type) {
        return type.charAt(0) << 24 | type.charAt(1) << 16 | type.charAt(2) << 8 | type.charAt(3);
    }
}
