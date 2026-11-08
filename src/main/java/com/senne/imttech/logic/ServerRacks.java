package com.senne.imttech.logic;

import com.senne.imttech.network.InputNameOpenPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.network.RackRenamePayload;
import com.senne.imttech.security.BaseTrust;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.os.EventLog;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Een serverrack aan de kant van de server (hoofdstuk 64): onderdelen erin en eruit klikken, en het rack een naam geven.
 * Het blok zelf (ServerRackBlock) zegt welk vakje je aanklikte; hier gebeurt het.
 *
 * Jouw wens van 8 november: "je kunt elke rack een naam geven door die te sneakklikken, dan noem je die bijvoorbeeld rack1
 * en dan zie je in de monitor op rack1 met zijn stats". Wie mag dat? Dezelfde regel als bij een keycardlezer: wie het rack
 * neerzette, of wie vertrouwd is in de basis waar het staat (security/BaseTrust). Het naamscherm is dat van de lezer
 * (client/InputNameScreen), dat ziet dat het om een rack gaat.
 */
public final class ServerRacks {
    private ServerRacks() {}

    /** Zo dichtbij moet je staan (blokken) om een rack te hernoemen, net als bij een lezer. */
    private static final double REACH = 6.0;

    /** Een onderdeel uit je hand in het vakje {@code slot} (het blok keek al dat het leeg is en dat het mag). */
    public static void insert(ServerLevel level, ServerRackBlockEntity rack, int slot, ItemStack hand, Player player) {
        rack.parts().setItem(slot, hand.copyWithCount(1));
        hand.consume(1, player); // in creative blijft hij in je hand
        rack.refresh();
        level.playSound(null, rack.getBlockPos(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.45f, 1.9f);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, rack.getBlockPos());
    }

    /** Het onderdeel uit het vakje {@code slot} in je rugzak (past het niet meer, dan valt het voor je voeten). */
    public static void takeOut(ServerLevel level, ServerRackBlockEntity rack, int slot, Player player) {
        ItemStack part = rack.parts().removeItem(slot, 1);
        if (part.isEmpty()) {
            return;
        }
        rack.refresh();
        if (!player.getInventory().add(part)) {
            player.drop(part, false);
        }
        level.playSound(null, rack.getBlockPos(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.45f, 1.7f);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, rack.getBlockPos());
    }

    /** Sneak + rechtsklik met een lege hand: mag je het rack hernoemen, dan opent jouw spel het naamscherm. */
    public static void openName(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        ServerRackBlockEntity rack = rack(player, pos);
        if (rack == null) {
            return;
        }
        if (!BaseTrust.mayEdit(player, rack)) {
            String owner = BaseTrust.belongsTo(player.serverLevel(), rack);
            player.displayClientMessage(owner.isEmpty()
                    ? Component.translatable("block.imttech.server_rack.locked")
                    : Component.translatable("block.imttech.server_rack.not_yours", owner), true);
            return;
        }
        ModNetwork.sendTo(player, new InputNameOpenPayload(pos, rack.name(), rack.ownerName()));
    }

    /**
     * Save in het naamscherm: de nieuwe naam op het rack. De server kijkt opnieuw of je mag. Hangt het aan een kern, dan
     * komt het in zijn logboek.
     */
    public static void handleRename(ServerPlayer player, RackRenamePayload payload) {
        ServerRackBlockEntity rack = rack(player, payload.pos());
        if (rack == null || !BaseTrust.mayEdit(player, rack)) {
            return;
        }
        String clean = InputDeviceBlockEntity.cleanName(payload.name());
        if (clean.isEmpty()) {
            player.displayClientMessage(Component.translatable("logic.imttech.input.name_empty").withStyle(ChatFormatting.RED), true);
            return;
        }
        String old = rack.name();
        rack.setName(clean);
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(player.serverLevel(), payload.pos());
        if (core != null && !old.equals(clean)) {
            core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " named a server rack \"" + clean + "\""
                    + (old.isEmpty() ? "" : " (was \"" + old + "\")"));
        }
        player.displayClientMessage(Component.translatable("block.imttech.server_rack.renamed", clean)
                .withStyle(ChatFormatting.GREEN), true);
    }

    /** Het rack waar deze speler bij kan, of null (er staat daar geen rack, of het is te ver weg). */
    private static @Nullable ServerRackBlockEntity rack(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH
                || !(player.serverLevel().getBlockEntity(pos) instanceof ServerRackBlockEntity rack)) {
            return null;
        }
        return rack;
    }
}
