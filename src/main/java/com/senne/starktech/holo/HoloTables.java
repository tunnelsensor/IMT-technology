package com.senne.starktech.holo;

import com.senne.starktech.network.HoloActionPayload;
import com.senne.starktech.network.HoloOpenPayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.shield.ShieldColor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De serverkant van de holotafel: het instelscherm openen en de knoppen daarvan uitvoeren.
 *
 * Alles wat het scherm vraagt wordt hier gecontroleerd. Je spel mag van alles sturen, dus we
 * kijken elke keer opnieuw of je wel bij die tafel staat en of je de bril op hebt.
 */
public final class HoloTables {
    private HoloTables() {}

    /** Hoe ver je van de tafel mag staan om hem in te stellen (in blokken). */
    private static final double REACH = 8.0;

    /** Vraagt jouw spel het instelscherm te openen. */
    public static void openSettings(ServerPlayer player, BlockPos pos) {
        if (table(player, pos) != null) {
            ModNetwork.sendTo(player, new HoloOpenPayload(pos));
        }
    }

    /** Een knop uit het instelscherm. */
    public static void handleAction(ServerPlayer player, HoloActionPayload payload) {
        HoloTableBlockEntity table = table(player, payload.pos());
        if (table == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        switch (payload.op()) {
            case MODE -> table.setMode(HoloMode.byId(payload.value()));
            case RANGE -> table.setRange(payload.value());
            case SCALE -> table.setScale(payload.value());
            case SPIN -> table.setSpin(payload.value() != 0);
            case CLEAR_BLUEPRINT -> table.clearBlueprint();
            case COLOUR -> {
                BlockState state = level.getBlockState(payload.pos());
                ShieldColor[] all = ShieldColor.values();
                ShieldColor colour = all[Math.floorMod(payload.value(), all.length)];
                level.setBlock(payload.pos(), state.setValue(HoloTableBlock.COLOR, colour), Block.UPDATE_ALL);
            }
        }
        level.playSound(null, payload.pos(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.4f, 1.6f);
    }

    /**
     * De tafel waar deze speler bij mag, of null.
     *
     * Twee dingen moeten kloppen: er staat daar echt een holotafel, en je staat er dichtbij
     * genoeg bij. (Een bril is niet nodig.)
     */
    private static @Nullable HoloTableBlockEntity table(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.getBlockState(pos).is(ModBlocks.HOLO_TABLE.get())) {
            return null;
        }
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH * REACH) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof HoloTableBlockEntity table ? table : null;
    }
}
