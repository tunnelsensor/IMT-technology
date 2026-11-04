package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 47): alles wat het scherm van een Power Battery laat zien. {@code open} =
 * het scherm openen (rechtsklik op de batterij); anders zijn het nieuwe cijfers voor een scherm dat al open staat.
 *
 * @param pos         de poort die de baas is (daar vraagt het scherm nieuwe cijfers)
 * @param formed      klopt de doos?
 * @param problem     zo niet: waarom (in het Engels)
 * @param size        "5 x 4 x 5"
 * @param cells       hoeveel cellen
 * @param energy      wat erin zit (FE)
 * @param capacity    wat erin past
 * @param made        wat het hele net per tick maakt
 * @param used        wat apparaten per tick gebruiken
 * @param missed      wat apparaten per tick tekortkomen
 * @param generators  per soort generator: naam, hoeveel, en wat ze samen maken
 * @param devices     per soort apparaat: naam, hoeveel, en wat ze samen gebruiken (de grootste eerst)
 * @param historyMade de laatste 5 minuten, een punt per 5 seconden (honderdsten per tick), oud naar nieuw
 * @param historyUsed idem, het verbruik
 */
public record BatteryStatsPayload(BlockPos pos, boolean open, boolean formed, String problem, String size, int cells,
                                  long energy, long capacity, float made, float used, float missed,
                                  List<Entry> generators, List<Entry> devices, int[] historyMade, int[] historyUsed)
        implements CustomPacketPayload {
    public static final Type<BatteryStatsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "battery_stats"));

    /** Eén regel in een lijst: een soort generator of apparaat. */
    public record Entry(String name, int count, float perTick) {}

    public static final StreamCodec<FriendlyByteBuf, BatteryStatsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                buf.writeBoolean(payload.open);
                buf.writeBoolean(payload.formed);
                buf.writeUtf(payload.problem, 400);
                buf.writeUtf(payload.size, 40);
                buf.writeVarInt(payload.cells);
                buf.writeVarLong(payload.energy);
                buf.writeVarLong(payload.capacity);
                buf.writeFloat(payload.made);
                buf.writeFloat(payload.used);
                buf.writeFloat(payload.missed);
                writeEntries(buf, payload.generators);
                writeEntries(buf, payload.devices);
                buf.writeVarIntArray(payload.historyMade);
                buf.writeVarIntArray(payload.historyUsed);
            },
            buf -> new BatteryStatsPayload(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(400), buf.readUtf(40),
                    buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    readEntries(buf), readEntries(buf), buf.readVarIntArray(), buf.readVarIntArray()));

    private static void writeEntries(FriendlyByteBuf buf, List<Entry> entries) {
        buf.writeVarInt(Math.min(entries.size(), 32));
        for (int i = 0; i < Math.min(entries.size(), 32); i++) {
            Entry entry = entries.get(i);
            buf.writeUtf(entry.name(), 80);
            buf.writeVarInt(entry.count());
            buf.writeFloat(entry.perTick());
        }
    }

    private static List<Entry> readEntries(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 32);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readUtf(80), buf.readVarInt(), buf.readFloat()));
        }
        return entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
