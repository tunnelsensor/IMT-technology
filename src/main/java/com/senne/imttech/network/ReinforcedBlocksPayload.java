package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import com.senne.imttech.breach.BlockReinforcerItem;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 44): de verstevigde blokken in je buurt, zolang je de Block Reinforcer
 * vasthoudt. Je bril tekent er een dun randje om (jouw keuze: het blok zelf blijft er gewoon uitzien).
 */
public record ReinforcedBlocksPayload(List<BlockPos> blocks) implements CustomPacketPayload {
    public static final Type<ReinforcedBlocksPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "reinforced_blocks"));

    public static final StreamCodec<ByteBuf, ReinforcedBlocksPayload> STREAM_CODEC = BlockPos.STREAM_CODEC
            .apply(ByteBufCodecs.list(BlockReinforcerItem.SHOW_MAX))
            .map(ReinforcedBlocksPayload::new, ReinforcedBlocksPayload::blocks);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
