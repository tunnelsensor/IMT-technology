package com.senne.imttech.client.weapons;

import com.senne.imttech.weapons.GrenadeEntity;
import com.senne.imttech.weapons.StickyGrenade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Granaten oprapen, in je spel (hoofdstuk 69). Rechtsklik op een granaat die op scherp staat, wat je ook vasthoudt: ook
 * met een geweer in je hand (dat zou anders richten). Daarom kijken we hier als eerste (EventPriority.HIGHEST) naar de
 * rechtermuisknop: kijk je naar zo'n granaat, dan vragen we de server hem op te rapen (zie weapons/GrenadePickup), en
 * gebeurt er verder niets met die klik.
 *
 * Een sticky die op jou plakt, kun je niet aankijken. Jouw keuze was "ook van wezens", dus ook van jezelf: zolang er een
 * scherpe sticky op je zit, trekt je eerste rechtsklik hem van je af.
 */
public final class GrenadeGrab {
    private GrenadeGrab() {}

    /** De rechtermuisknop: een granaat oprapen als er een is. */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GrenadeEntity grenade = target();
        if (player == null || mc.gameMode == null || grenade == null) {
            return;
        }
        // Zoals Minecraft het zelf doet bij een rechtsklik op een wezen: de server hoort het, en hier gebeurt hetzelfde.
        mc.gameMode.interact(player, grenade, InteractionHand.MAIN_HAND);
        event.setCanceled(true);
        event.setSwingHand(true);
    }

    /** Pakt een rechtsklik nu een granaat op? (Dan richt een geweer niet.) */
    public static boolean wouldGrab() {
        return target() != null;
    }

    /** De granaat die een rechtsklik nu opraapt: die waar je naar kijkt, of anders een sticky op jezelf. */
    private static @Nullable GrenadeEntity target() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator() || mc.getCameraEntity() != player) {
            return null;
        }
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof GrenadeEntity grenade
                && grenade.canBePickedUp()) {
            return grenade;
        }
        for (StickyGrenade sticky : mc.level.getEntitiesOfClass(StickyGrenade.class, player.getBoundingBox().inflate(1.0),
                sticky -> sticky.canBePickedUp() && sticky.host() == player)) {
            return sticky;
        }
        return null;
    }
}
