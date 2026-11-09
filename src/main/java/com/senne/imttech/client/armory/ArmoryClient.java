package com.senne.imttech.client.armory;

import com.senne.imttech.armory.AmmoBoxBlockEntity;
import com.senne.imttech.armory.AmmoBoxes;
import com.senne.imttech.armory.Pegboard;
import com.senne.imttech.armory.PegboardBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Jouw spel en de armory (hoofdstuk 67). Met een pistool of lasergeweer in je hand is rechtsklik "richten", en dan
 * gebruikt Minecraft geen blokken (zie FirearmControl en GunControl). Maar een geweer ophangen aan een Pegboard, of
 * een lasergeweer op een kist cellen wisselen, is juist een rechtsklik op een blok: dan richt je niet, en mag de klik
 * door.
 */
public final class ArmoryClient {
    private ArmoryClient() {}

    /** Doet een rechtsklik nu iets met een Pegboard of Ammo Box (binnen bereik, met wat je vasthoudt)? */
    public static boolean usesArmory() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        var entity = mc.level.getBlockEntity(hit.getBlockPos());
        if (entity instanceof PegboardBlockEntity) {
            return Pegboard.hangable(held);
        }
        if (entity instanceof AmmoBoxBlockEntity box) {
            return AmmoBoxes.useFor(player, held, box) != AmmoBoxes.Use.NONE;
        }
        return false;
    }

    /** Zoveel patronen zitten in de Ammo Box waar je naar kijkt (binnen bereik), of 0 (voor de HUD van een magazijn). */
    public static int roundsInBox() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !(mc.level.getBlockEntity(hit.getBlockPos()) instanceof AmmoBoxBlockEntity box)) {
            return 0;
        }
        return box.sort().is(com.senne.imttech.registry.ModItems.ROUND_9MM.get()) ? box.count() : 0;
    }
}
