package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import com.senne.starktech.c4.C4Balance;
import com.senne.starktech.c4.C4Item;
import com.senne.starktech.registry.ModDataComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Bericht van speler naar server: "zet de timer van de C4 in mijn hand op zoveel seconden" (het
 * scherm C4TimerScreen). De server houdt het tussen 5 seconden en 2 minuten.
 *
 * @param offhand de C4 zit in je andere hand
 * @param seconds de nieuwe tijd
 */
public record C4TimerPayload(boolean offhand, int seconds) implements CustomPacketPayload {
    public static final Type<C4TimerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "c4_timer"));

    public static final StreamCodec<ByteBuf, C4TimerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, C4TimerPayload::offhand,
            ByteBufCodecs.VAR_INT, C4TimerPayload::seconds,
            C4TimerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Op de server: houdt hij echt een C4 vast? Dan de tijd erop zetten. */
    public static void handle(ServerPlayer player, C4TimerPayload payload) {
        ItemStack stack = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (stack.getItem() instanceof C4Item) {
            stack.set(ModDataComponents.C4_TIMER.get(), Mth.clamp(payload.seconds(), C4Balance.TIMER_MIN, C4Balance.TIMER_MAX));
        }
    }
}
