package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import com.senne.imttech.security.zone.ZoneScan;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 116): waar jouw Autoscan je kamers zag openliggen (de pings), de weg die de
 * scan nam (een stippellijn), of je de grot erbij kunt nemen (dan zegt de ping hoe, als je de Zone Marker vasthoudt), en
 * hoe lang dat blijft staan. Een lege lijst haalt de oude pings weg (de scan lukte, of je scande opnieuw).
 */
public record ZoneScanMarksPayload(List<Entry> marks, List<BlockPos> path, boolean offer, int seconds) implements CustomPacketPayload {
    public static final Type<ZoneScanMarksPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "zone_scan_marks"));

    /** Hooguit zoveel pings en punten van de weg in één bericht. */
    public static final int MAX_MARKS = 8;
    public static final int MAX_PATH = 128;

    /** Eén ping: het blok, en wat het is (de volgorde van {@link ZoneScan.Kind}). */
    public record Entry(BlockPos pos, byte kind) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Entry::pos,
                ByteBufCodecs.BYTE, Entry::kind,
                Entry::new);

        /** Wat voor ping dit is (een onbekend getal van een andere versie: een opening naar een grot). */
        public ZoneScan.Kind kindOf() {
            ZoneScan.Kind[] kinds = ZoneScan.Kind.values();
            return kind >= 0 && kind < kinds.length ? kinds[kind] : ZoneScan.Kind.CAVE;
        }
    }

    public static final StreamCodec<ByteBuf, ZoneScanMarksPayload> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MARKS)), ZoneScanMarksPayload::marks,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PATH)), ZoneScanMarksPayload::path,
            ByteBufCodecs.BOOL, ZoneScanMarksPayload::offer,
            ByteBufCodecs.VAR_INT, ZoneScanMarksPayload::seconds,
            ZoneScanMarksPayload::new);

    /** Het bericht voor wat deze scan zag (hooguit MAX_MARKS pings en MAX_PATH punten). */
    public static ZoneScanMarksPayload of(ZoneScan.Result result, int seconds) {
        List<Entry> entries = new ArrayList<>();
        for (ZoneScan.Mark mark : result.marks()) {
            if (entries.size() < MAX_MARKS) {
                entries.add(new Entry(mark.pos().immutable(), (byte) mark.kind().ordinal()));
            }
        }
        List<BlockPos> path = result.path().subList(0, Math.min(MAX_PATH, result.path().size()));
        return new ZoneScanMarksPayload(entries, List.copyOf(path), result.offer() != null, seconds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
