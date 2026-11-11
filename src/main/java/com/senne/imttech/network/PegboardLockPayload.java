package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "sneak + linksklik op dit Pegboard" (hoofdstuk 70): zet vast of los wat hier hangt,
 * of veeg een schaduw weg. Zie armory/PegboardBlock.handleLock (die kijkt zelf na of je dichtbij genoeg staat).
 *
 * @param pos  het blok van het paneel
 * @param wall tegen welke muur (in een hoek hangen er twee in één blok): de kant op die dat paneel kijkt
 * @param u    waar je keek, in gaatjes over de wand (naar rechts)
 * @param v    waar je keek, in gaatjes over de wand (naar boven)
 */
public record PegboardLockPayload(BlockPos pos, Direction wall, double u, double v) implements CustomPacketPayload {
    public static final Type<PegboardLockPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "pegboard_lock"));

    public static final StreamCodec<ByteBuf, PegboardLockPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PegboardLockPayload::pos,
            Direction.STREAM_CODEC, PegboardLockPayload::wall,
            ByteBufCodecs.DOUBLE, PegboardLockPayload::u,
            ByteBufCodecs.DOUBLE, PegboardLockPayload::v,
            PegboardLockPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
