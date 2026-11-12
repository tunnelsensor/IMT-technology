package com.senne.imttech.backpack;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van jouw spel naar de server (hoofdstuk 72): iets met een rugzak. {@code action} is een van de ACTION_...
 * hieronder: de rugzak op je rug openritsen (de rugzaktoets), of de rugzak die open is sorteren (de sorteerknop).
 */
public record BackpackActionPayload(int action, int arg) implements CustomPacketPayload {
    /** De rugzak op je rug openritsen. */
    public static final int OPEN_WORN = 0;
    /** De open rugzak sorteren. */
    public static final int SORT = 1;
    /** Stap 4: "Everything back" bij je eigen lichaam (het scherm ervan is open). */
    public static final int RESTORE_BODY = 2;
    /** Hoofdstuk 98: het rugzakvakje in het scherm van een lichaam: de rugzak eraf halen (3 s stilstaan). */
    public static final int TAKE_BODY_PACK = 3;

    public static final Type<BackpackActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "backpack_action"));

    public static final StreamCodec<ByteBuf, BackpackActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BackpackActionPayload::action,
            ByteBufCodecs.VAR_INT, BackpackActionPayload::arg,
            BackpackActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
