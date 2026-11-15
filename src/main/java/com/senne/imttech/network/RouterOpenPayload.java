package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 112): wat het scherm van een Router toont. De server stuurt het als je de
 * router opent, na elke knop, en om de seconde zolang het scherm open staat (het scherm vraagt daar zelf om). Alleen wie
 * de router mag instellen krijgt dit bericht (zie Routers.open): wie het scherm ziet, mag dus ook verbinden en losmaken.
 *
 * @param pos     waar de router staat
 * @param name    zijn naam ("Router 1")
 * @param online  heeft hij stroom?
 * @param core    zit er een Security Core in zijn netwerk?
 * @param max     hoeveel verbindingen hij kan hebben (uit de config)
 * @param range   hoe ver hij reikt (blokken, uit de config)
 * @param open    moet het scherm opengaan (true), of alleen bijgewerkt worden als het al open staat?
 * @param linked  wat er draadloos aan hangt
 * @param nearby  netwerkblokken binnen bereik die er niet aan hangen en die deze speler mag verbinden (de dichtste eerst)
 */
public record RouterOpenPayload(BlockPos pos, String name, boolean online, boolean core, int max, int range, boolean open,
                                List<Entry> linked, List<Entry> nearby) implements CustomPacketPayload {
    /** Een verbonden apparaat: alles goed, in een stuk wereld dat niet geladen is, of te ver weg (de config veranderde). */
    public static final int OK = 0;
    public static final int FAR = 1;
    public static final int OUT_OF_RANGE = 2;
    /** Een apparaat in de buurt: los, aan een andere router, of al aan een netwerk (een kabel). */
    public static final int FREE = 3;
    public static final int OTHER_ROUTER = 4;
    public static final int WIRED = 5;
    /** Een apparaat (of de kern) in de buurt dat al in het netwerk van deze router zit (met een kabel): verbinden hoeft niet. */
    public static final int SAME_NET = 6;

    /**
     * Eén regel in het scherm.
     *
     * @param pos      waar het apparaat staat
     * @param block    de vertaalsleutel van het blok ("block.imttech.relay"), of "" als we het niet kunnen zien
     * @param name     zijn eigen naam ("Lab door"), of ""
     * @param distance hoe ver van de router (blokken)
     * @param status   een van de getallen hierboven
     */
    public record Entry(BlockPos pos, String block, String name, int distance, int status) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Entry::pos,
                ByteBufCodecs.STRING_UTF8, Entry::block,
                ByteBufCodecs.STRING_UTF8, Entry::name,
                ByteBufCodecs.VAR_INT, Entry::distance,
                ByteBufCodecs.VAR_INT, Entry::status,
                Entry::new);
    }

    public static final Type<RouterOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "router_open"));

    public static final StreamCodec<ByteBuf, RouterOpenPayload> STREAM_CODEC = new StreamCodec<>() {
        private final StreamCodec<ByteBuf, List<Entry>> entries = Entry.STREAM_CODEC.apply(ByteBufCodecs.list(128));

        @Override
        public RouterOpenPayload decode(ByteBuf buffer) {
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
            String name = ByteBufCodecs.STRING_UTF8.decode(buffer);
            int flags = buffer.readByte();
            int max = ByteBufCodecs.VAR_INT.decode(buffer);
            int range = ByteBufCodecs.VAR_INT.decode(buffer);
            List<Entry> linked = entries.decode(buffer);
            List<Entry> nearby = entries.decode(buffer);
            return new RouterOpenPayload(pos, name, (flags & 1) != 0, (flags & 2) != 0, max, range, (flags & 4) != 0, linked,
                    nearby);
        }

        @Override
        public void encode(ByteBuf buffer, RouterOpenPayload payload) {
            BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
            ByteBufCodecs.STRING_UTF8.encode(buffer, payload.name());
            buffer.writeByte((payload.online() ? 1 : 0) | (payload.core() ? 2 : 0) | (payload.open() ? 4 : 0));
            ByteBufCodecs.VAR_INT.encode(buffer, payload.max());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.range());
            entries.encode(buffer, payload.linked());
            entries.encode(buffer, payload.nearby());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
