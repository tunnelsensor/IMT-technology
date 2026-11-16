package com.senne.imttech.sdcard;

import com.senne.imttech.computer.ComputerBlockEntity;
import com.senne.imttech.computer.Computers;
import com.senne.imttech.security.BaseTrust;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Een SD-kaart in een Security Core of een Computer steken, en er weer uit halen (hoofdstuk 130).
 * <ul>
 *   <li><b>Erin</b>: rechtsklik met de kaart op het blok. In een kern: de eigenaar en wie hij vertrouwt (Access, zijn
 *       team). In een computer: wie hem mag gebruiken (zie Computers.mayEnter) of hem mag instellen (BaseTrust). De kaart
 *       gaat echt uit je hand (ook in creative), zodat er nooit twee dezelfde kaarten zijn.</li>
 *   <li><b>Eruit</b>: sneak met een lege hand op het blok. Wie hem erin stak, mag hem er weer uit halen, en verder de
 *       eigenaar en wie hij vertrouwt (in een kern), of wie de computer mag instellen. Hij komt in je hand.</li>
 *   <li>Breekt het blok (weggehakt, opgeblazen), dan valt de kaart eruit. Een weggehakte kern neemt hem nooit mee in zijn
 *       ingepakte instellingen (anders had je hem twee keer).</li>
 * </ul>
 * Het blok toont de kaart met de blokstand {@code card} (een klein kaartje in een gleuf vooraan), dus zonder dat er iets
 * van wat er in het blok zit naar je spel gaat.
 */
public final class SdCardSlots {
    private SdCardSlots() {}

    // ================================================================ Wie mag

    /** Mag deze speler een kaart in deze kern steken (de eigenaar, wie hij vertrouwt, zijn team)? */
    public static boolean mayUse(ServerPlayer player, SecurityCoreBlockEntity core) {
        return core.ownerId() != null && SecurityCores.isFriend(player.serverLevel(), core, player);
    }

    /** Mag deze speler een kaart in deze computer steken (wie hem mag gebruiken, of wie hem mag instellen)? */
    public static boolean mayUse(ServerPlayer player, ComputerBlockEntity computer) {
        if (BaseTrust.wouldAllow(player, computer)) {
            return true;
        }
        SecurityCoreBlockEntity core = computer.core(player.serverLevel());
        return core != null && Computers.mayEnter(player, core, computer) == null;
    }

    /** Mag deze speler de kaart uit deze kern halen? Wie hem erin stak, en de eigenaar en wie hij vertrouwt. */
    public static boolean mayTakeOut(ServerPlayer player, SecurityCoreBlockEntity core) {
        return player.getUUID().equals(core.sdCardBy()) || mayUse(player, core);
    }

    /** Mag deze speler de kaart uit deze computer halen? Wie hem erin stak, en wie de computer mag instellen. */
    public static boolean mayTakeOut(ServerPlayer player, ComputerBlockEntity computer) {
        return player.getUUID().equals(computer.sdCardBy()) || BaseTrust.wouldAllow(player, computer);
    }

    // ================================================================ Erin en eruit

    /**
     * Rechtsklik met een kaart ({@code hand}) op het blok. {@code allowed}: mag deze speler dat hier (zie {@link #mayUse}).
     * Zit er al een kaart in, dan zeggen we hoe je die eruit haalt.
     */
    public static void insert(ServerPlayer player, BlockPos pos, SdCardHolder holder, ItemStack hand, boolean allowed) {
        if (!allowed) {
            message(player, "message.imttech.sd_card.not_allowed", ChatFormatting.RED);
            return;
        }
        if (!holder.sdCard().isEmpty()) {
            message(player, "message.imttech.sd_card.full", ChatFormatting.YELLOW);
            return;
        }
        // Echt uit je hand, ook in creative: anders had je twee kaarten met hetzelfde nummer (en dus dezelfde inhoud).
        holder.putSdCard(hand.split(1), player.getUUID());
        ServerLevel level = player.serverLevel();
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.3f, 2.0f);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        message(player, "message.imttech.sd_card.inserted", ChatFormatting.GREEN);
    }

    /** Sneak met een lege hand op het blok: de kaart eruit, in je hand. */
    public static void takeOut(ServerPlayer player, BlockPos pos, SdCardHolder holder, boolean allowed) {
        if (holder.sdCard().isEmpty()) {
            return;
        }
        if (!allowed) {
            message(player, "message.imttech.sd_card.not_yours", ChatFormatting.RED);
            return;
        }
        ItemStack card = holder.takeSdCard();
        if (card.isEmpty()) {
            return;
        }
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, card);
        } else if (!player.getInventory().add(card)) {
            player.drop(card, false);
        }
        ServerLevel level = player.serverLevel();
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.3f, 2.0f);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        message(player, "message.imttech.sd_card.taken_out", ChatFormatting.GREEN);
    }

    /** Het blok is weg (weggehakt, opgeblazen, /setblock): de kaart valt eruit. */
    public static void dropOnBreak(Level level, BlockPos pos, SdCardHolder holder) {
        ItemStack card = holder.takeSdCard();
        if (!card.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, card);
        }
    }

    private static void message(ServerPlayer player, String key, ChatFormatting colour) {
        player.displayClientMessage(Component.translatable(key).withStyle(colour), true);
    }
}
