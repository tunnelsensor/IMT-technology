package com.senne.imttech.client.zone;

import com.senne.imttech.network.ZoneMarkerClickPayload;
import com.senne.imttech.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * De linksklik met de Zone Marker (hoofdstuk 10). Gewoon linksklikken zou het blok beginnen te
 * breken; daarom vangen we de klik hier af en sturen we alleen "linksklik op dit blok" naar de server
 * (die zet hoek 1, of haalt met sluipen een kubus weg, zie ZoneMarkerItem).
 *
 * Zolang je de knop ingedrukt houdt, krijgt de server maar één klik: pas als je hem loslaat, telt
 * een volgende klik weer (anders zou het weghalen van een kubus meteen ook de tweede klik zijn).
 */
public final class ZoneMarkerControl {
    private ZoneMarkerControl() {}

    /** Heb je de aanvalsknop losgelaten sinds de vorige klik? */
    private static boolean released = true;

    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Minecraft.getInstance().options.keyAttack.isDown()) {
            released = true;
        }
    }

    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || !mc.player.getMainHandItem().is(ModItems.ZONE_MARKER.get())) {
            return;
        }
        // Met de marker hak en sla je nooit: de klik is voor de marker.
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!released || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        released = false;
        PacketDistributor.sendToServer(new ZoneMarkerClickPayload(hit.getBlockPos()));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }
}
