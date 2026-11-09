package com.senne.imttech.weapons;

import com.senne.imttech.guns.Gear;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Granaten oprapen en teruggooien (hoofdstuk 69, jouw wens: "iemand gooit een EMP, maar jij gooit die terug").
 * <ul>
 *   <li><b>Oprapen</b>: rechtsklik op een granaat die op scherp staat (een bal die rolt, een sticky op een muur, of
 *       een sticky op een speler, een robot, een drone of jezelf), wat je ook vasthoudt. Jouw keuze: de granaat komt in
 *       je hand, en wat je vasthield valt op de grond, <i>tenzij</i> het in je holster, een sling-vakje of een pouch past:
 *       dan gaat het daarheen (net als bij trekken uit het wiel, zie Gear.intoHand).</li>
 *   <li><b>De lont loopt door</b> (jouw keuze): je hebt nog de rest van zijn tijd. Hij piept in je hand en zegt onderaan
 *       je scherm hoeveel seconden er nog zijn (zie GrenadeItem). Rechtsklik gooit hem terug, Q laat hem vallen; wacht je
 *       te lang, dan gaat hij bij jou af.</li>
 * </ul>
 * Een sticky die op jou plakt kun je niet aankijken: daarvoor trekt je eerste rechtsklik hem van je af (zie
 * client/weapons/GrenadeGrab).
 */
public final class GrenadePickup {
    private GrenadePickup() {}

    /** Raapt deze granaat op, in de hand van {@code player}. Geeft false als dat niet (meer) kan. */
    public static boolean pickUp(ServerPlayer player, GrenadeEntity grenade) {
        if (!grenade.canBePickedUp()) {
            return false;
        }
        int left = grenade.ticksLeft();
        ItemStack stack = GrenadeItem.live(grenade.asItem(), player.level(), left);
        grenade.discard();
        Gear.intoHand(player, stack);
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS, 0.6f, 0.8f);
        return true;
    }

    /**
     * Een scherpe granaat aan je muis (terwijl je een scherm open hebt) telt ook af: Minecraft laat dat ding niet ticken,
     * dus kijken we het hier na. Is zijn tijd op, dan gaat hij bij jou af.
     */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (GrenadeItem.isLive(carried) && GrenadeItem.ticksLeft(carried, player.level()) <= 0) {
            GrenadeItem.goOffAt(player.serverLevel(), carried, player);
            player.containerMenu.setCarried(ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();
        }
    }
}
