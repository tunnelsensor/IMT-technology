package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import com.senne.starktech.workbench.HoloPiece;
import com.senne.starktech.workbench.SuitWorkbenchBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bericht van speler naar server: "ik klik met rechts op dit stuk van het werkbank-hologram".
 * Wat er gebeurt, hangt af van wat de speler vasthoudt (zie SuitWorkbenchBlockEntity.interact).
 *
 * @param pos   waar de werkbank staat
 * @param piece welk stuk (het nummer van een HoloPiece: 0 = borst, 1 = broek, 2 = laarzen)
 */
public record WorkbenchClickPayload(BlockPos pos, int piece) implements CustomPacketPayload {
    public static final Type<WorkbenchClickPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "workbench_click"));

    public static final StreamCodec<ByteBuf, WorkbenchClickPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, WorkbenchClickPayload::pos,
            ByteBufCodecs.VAR_INT, WorkbenchClickPayload::piece,
            WorkbenchClickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Op de server: klopt het (staat de speler er dichtbij genoeg)? Dan doen. */
    public static void handle(ServerPlayer player, WorkbenchClickPayload payload) {
        HoloPiece piece = HoloPiece.byId(payload.piece());
        if (piece == null || player.isSpectator() || !player.level().isLoaded(payload.pos())
                // Het hologram is tot 2,5 blok hoger dan het blok zelf: daarom wat extra ruimte.
                || !player.canInteractWithBlock(payload.pos(), 3.0)) {
            return;
        }
        if (player.level().getBlockEntity(payload.pos()) instanceof SuitWorkbenchBlockEntity bench) {
            bench.interact(player, piece);
        }
    }
}
