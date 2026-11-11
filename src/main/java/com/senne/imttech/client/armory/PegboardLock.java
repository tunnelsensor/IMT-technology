package com.senne.imttech.client.armory;

import com.senne.imttech.armory.Pegboard;
import com.senne.imttech.armory.PegboardBlock;
import com.senne.imttech.network.PegboardLockPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sneak + linksklik op een Pegboard, in je spel (hoofdstuk 70, jouw keuze). Kijk je (sluipend, dichtbij genoeg) naar
 * iets dat erop hangt, of naar een schaduw, dan is linksklik niet "breken" maar "vastzetten of los": we vragen het de
 * server (zie armory/PegboardBlock.handleLock), en het paneel gaat niet stuk. Op een lege plek, of zonder te sluipen,
 * breekt linksklik het paneel zoals altijd.
 *
 * Minecraft roept linksklik elke tick opnieuw aan zolang je hem inhoudt (zo breek je een blok). Wij doen het maar één
 * keer per klik ({@link #handled}), anders ging het vastzetten elke tick aan en uit. Zolang je inhoudt, gebeurt er verder
 * niets (ook niet breken).
 *
 * Dit luistert met voorrang (EventPriority.HIGH): met een pistool of lasergeweer in je hand gooien die anders elke
 * linksklik weg ("linkerklik slaat niet").
 */
public final class PegboardLock {
    private PegboardLock() {}

    /** Deze klik is al naar de server gestuurd (tot je de muisknop loslaat). */
    private static boolean handled;

    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !player.isShiftKeyDown() || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof PegboardBlock) || !Pegboard.inReach(player, hit.getLocation())) {
            return;
        }
        Direction wall = PegboardBlock.wallOf(state, hit);
        double[] cell = Pegboard.aim(player, pos, wall, hit.getLocation(), Pegboard.BACK + 1.5);
        if (Pegboard.itemAt(mc.level, pos, wall, cell[0], cell[1]) == null) {
            return; // een leeg plekje: gewoon breken
        }
        event.setCanceled(true);
        event.setSwingHand(!handled);
        if (!handled) {
            handled = true;
            PacketDistributor.sendToServer(new PegboardLockPayload(pos, wall, cell[0], cell[1]));
        }
    }

    /** Muisknop los: de volgende klik telt weer. */
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Minecraft.getInstance().options.keyAttack.isDown()) {
            handled = false;
        }
    }
}
