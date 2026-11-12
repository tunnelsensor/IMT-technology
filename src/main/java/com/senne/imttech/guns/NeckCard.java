package com.senne.imttech.guns;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.imttech.logic.KeycardItem;
import com.senne.imttech.registry.ModAttachments;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * De keycard aan je nek trekken (hoofdstuk 100, jouw wens: "een nek slot zodat je je keycard ook kunt quick drawen").
 *
 * Jouw keuze: je hoeft geen lege hand te hebben. "Je hebt het dan gewoon vast over het item in je inventory en je kunt het
 * dan gebruiken, na gebruik gaat het weer naar de slot." Dus:
 * <ul>
 *   <li><b>Trekken</b> ({@link #draw}, uit het wiel of met een eigen toets): de kaart komt in je hand, en wat je vasthield
 *       verdwijnt even "onder" de kaart (we bewaren het, zie {@link Drawn}); het valt niet en schuift nergens heen.</li>
 *   <li><b>Gebruiken</b>: houd hem tegen een keycardlezer. Een halve seconde later ({@link #USED_DELAY}) hangt de kaart weer
 *       aan je nek en heb je je eigen ding weer vast ({@link #used}).</li>
 *   <li><b>Ook terug</b> ({@link #putBack}): als je naar een ander vakje van je werkbalk gaat, als je hem wegsteekt (het wiel:
 *       "put away", of nog eens trekken), als je iets anders trekt (een pistool), als je doodgaat (vóór je lichaam alles
 *       krijgt) en als je uitlogt. Raakt de kaart weg uit je hand (je gooit hem weg, legt hem in je inventaris ergens anders,
 *       of een zakkenroller pakt hem), dan krijg je je eigen ding gewoon terug in je hand en blijft je nek leeg.</li>
 * </ul>
 * Wat er onder de kaart zit, wordt opgeslagen met de speler (de attachment NECK_DRAW), zodat het ook na een crash niet weg is:
 * bij het inloggen komt alles weer op zijn plek.
 */
public final class NeckCard {
    private NeckCard() {}

    /** Zo lang na een tik tegen een lezer gaat de kaart terug (ticks): dan zie je hem nog even bij de lezer. */
    public static final int USED_DELAY = 10;

    /**
     * Een getrokken kaart: in welk vakje van je werkbalk hij zit, wat daar eerst lag ({@code under}, mag leeg zijn) en welke
     * kaart het is. {@code slot} -1 = niets getrokken.
     */
    public record Drawn(int slot, ItemStack under, ItemStack card) {
        public static final Drawn NONE = new Drawn(-1, ItemStack.EMPTY, ItemStack.EMPTY);
        public static final Codec<Drawn> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("slot").forGetter(Drawn::slot),
                ItemStack.OPTIONAL_CODEC.fieldOf("under").forGetter(Drawn::under),
                ItemStack.OPTIONAL_CODEC.fieldOf("card").forGetter(Drawn::card)).apply(i, Drawn::new));
        /** Naar je eigen spel (alleen naar jou), zodat het wiel weet dat je hem vasthebt. */
        public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Drawn> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.composite(
                        net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Drawn::slot,
                        ItemStack.OPTIONAL_STREAM_CODEC, Drawn::under,
                        ItemStack.OPTIONAL_STREAM_CODEC, Drawn::card,
                        Drawn::new);

        public boolean active() {
            return slot >= 0;
        }

        // Een attachment moet equals hebben, en een ItemStack heeft dat niet.
        @Override
        public boolean equals(Object other) {
            return other instanceof Drawn d && d.slot == slot && ItemStack.matches(d.under, under) && ItemStack.matches(d.card, card);
        }

        @Override
        public int hashCode() {
            return slot;
        }
    }

    /** Wanneer (speltijd) een gebruikte kaart terug moet. Alleen in het geheugen: na een herstart gaat hij gewoon terug. */
    private static final Map<UUID, Long> USED = new HashMap<>();

    /** Heeft deze speler zijn nekkaart nu in zijn hand? */
    public static boolean isDrawn(Player player) {
        return player.hasData(ModAttachments.NECK_DRAW) && player.getData(ModAttachments.NECK_DRAW).active();
    }

    /**
     * De kaart aan je nek in je hand, over wat je vasthield; is hij al getrokken, dan gaat hij terug (nog eens = wegsteken).
     * Geeft true als er iets gebeurde.
     */
    public static boolean draw(ServerPlayer player) {
        if (isDrawn(player)) {
            putBack(player);
            return true;
        }
        ItemStack card = Gear.neck(player);
        if (card.isEmpty()) {
            player.displayClientMessage(Component.translatable("gear.imttech.no_keycard").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (Firearms.isBusy(player) || !Gear.setNeck(player, ItemStack.EMPTY)) {
            return false;
        }
        Inventory inventory = player.getInventory();
        int slot = inventory.selected;
        ItemStack under = inventory.getItem(slot).copy();
        inventory.setItem(slot, card.copy());
        player.setData(ModAttachments.NECK_DRAW, new Drawn(slot, under, card.copy()));
        sound(player, 1.3f);
        return true;
    }

    /**
     * De kaart terug aan je nek, en wat eronder zat weer in je hand. Zit de kaart niet meer in dat vakje, dan krijg je alleen
     * je eigen ding terug (in dat vakje als het leeg is, anders in je inventaris of op de grond).
     */
    public static void putBack(ServerPlayer player) {
        putBack(player, true);
    }

    private static void putBack(ServerPlayer player, boolean sound) {
        if (!isDrawn(player)) {
            return;
        }
        Drawn drawn = player.getData(ModAttachments.NECK_DRAW);
        player.setData(ModAttachments.NECK_DRAW, Drawn.NONE);
        USED.remove(player.getUUID());
        Inventory inventory = player.getInventory();
        ItemStack inSlot = drawn.slot() < Inventory.getSelectionSize() ? inventory.getItem(drawn.slot()) : ItemStack.EMPTY;
        if (!inSlot.isEmpty() && ItemStack.isSameItemSameComponents(inSlot, drawn.card())) {
            ItemStack card = inSlot.copyWithCount(1);
            inSlot.shrink(1);
            if (!Gear.neck(player).isEmpty() || !Gear.setNeck(player, card)) {
                give(player, card);
            }
            if (inSlot.isEmpty()) {
                inventory.setItem(drawn.slot(), drawn.under().copy());
            } else {
                give(player, drawn.under().copy());
            }
        } else if (inSlot.isEmpty()) {
            inventory.setItem(drawn.slot(), drawn.under().copy());
        } else {
            give(player, drawn.under().copy());
        }
        player.inventoryMenu.broadcastChanges();
        if (sound) {
            sound(player, 1.0f);
        }
    }

    /** Hoofdstuk 100: je hield een kaart tegen een lezer. Was het je getrokken nekkaart, dan gaat hij zo terug. */
    public static void used(ServerPlayer player, ItemStack card) {
        if (!isDrawn(player)) {
            return;
        }
        Drawn drawn = player.getData(ModAttachments.NECK_DRAW);
        if (player.getInventory().selected == drawn.slot() && ItemStack.isSameItemSameComponents(card, drawn.card())) {
            USED.put(player.getUUID(), player.serverLevel().getGameTime() + USED_DELAY);
        }
    }

    /** Elke tick: is de kaart gebruikt, ben je naar een ander vakje gegaan, of is de kaart uit je hand? Dan terug. */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isDrawn(player)) {
                continue;
            }
            Drawn drawn = player.getData(ModAttachments.NECK_DRAW);
            Long at = USED.get(player.getUUID());
            boolean used = at != null && player.serverLevel().getGameTime() >= at;
            ItemStack held = player.getInventory().getItem(drawn.slot());
            boolean gone = !ItemStack.isSameItemSameComponents(held, drawn.card());
            if (used || gone || player.getInventory().selected != drawn.slot()) {
                putBack(player, !gone);
            }
        }
    }

    /** Doodgaan: eerst alles terug op zijn plek, zodat je lichaam (of de grond) de kaart en je eigen ding allebei krijgt. */
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            putBack(player, false);
        }
    }

    /** Uitloggen: alles terug, zodat er niets "onder een kaart" opgeslagen wordt. */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            putBack(player, false);
        }
    }

    /** Inloggen na een crash (toen stond de kaart nog in je hand): nu alles terug. */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            putBack(player, false);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        USED.clear();
    }

    /** In je inventaris, of op de grond als die vol is. */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void sound(ServerPlayer player, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.PLAYERS, 0.45f, pitch);
    }

    /** Is dit een kaart die aan je nek kan? (Elke keycard, ook een lege.) */
    public static boolean fits(ItemStack stack) {
        return stack.getItem() instanceof KeycardItem;
    }
}
