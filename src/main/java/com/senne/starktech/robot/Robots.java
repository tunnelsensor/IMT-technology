package com.senne.starktech.robot;

import com.senne.starktech.bot.BotNetwork;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.network.RobotActionPayload;
import com.senne.starktech.network.RobotOpenPayload;
import com.senne.starktech.security.BaseTrust;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Het robotscherm, aan de kant van de server (hoofdstuk 21): het scherm openen, en de knoppen uitvoeren. Het
 * scherm zelf staat in client/robot/RobotScreen.
 *
 * Openen: sneak + rechtsklik met een lege hand op de robot. Dat mag wie hem neerzette, en wie vertrouwd is in
 * de basis waar hij staat (dezelfde regel als bij de laserzender, zie BaseTrust). Een robot van niemand (uit
 * een spawn-ei) wordt van wie hem als eerste opent, behalve in iemands basis.
 */
public final class Robots {
    private Robots() {}

    /** Zo dichtbij moet je staan (blokken). */
    private static final double REACH = 8.0;

    /** Sneak + rechtsklik op de robot: mag je hem instellen, dan opent jouw spel het scherm. */
    public static void open(ServerPlayer player, GuardRobotEntity robot) {
        if (!mayEdit(player, robot)) {
            String owner = BaseTrust.belongsTo(player.serverLevel(), robot);
            player.displayClientMessage(owner.isEmpty() ? Component.translatable("message.starktech.robot.locked")
                    : Component.translatable("message.starktech.robot.not_yours", owner), true);
            return;
        }
        ModNetwork.sendTo(player, new RobotOpenPayload(robot.getId(), robot.ownerName(), Optional.ofNullable(robot.home())));
    }

    /** Een knop uit het scherm. */
    public static void handleAction(ServerPlayer player, RobotActionPayload payload) {
        GuardRobotEntity robot = robot(player, payload.entityId());
        if (robot == null || !mayEdit(player, robot)) {
            return;
        }
        switch (payload.op()) {
            case TURN_ON -> robot.setActive(true);
            case TURN_OFF -> robot.setActive(false);
            case RENAME -> rename(player, robot, payload.text());
            case PICK_UP -> pickUp(player, robot);
        }
        BotNetwork.get(player.serverLevel()).update(robot);
    }

    /** Mag deze speler de robot instellen? (Zie de uitleg bovenaan.) */
    public static boolean mayEdit(ServerPlayer player, GuardRobotEntity robot) {
        return BaseTrust.mayEdit(player, robot);
    }

    /** Een nieuwe naam: niet leeg, niet te lang, en geen andere robot van dezelfde eigenaar heet al zo. */
    public static boolean rename(ServerPlayer player, GuardRobotEntity robot, String typed) {
        String name = BotNetwork.cleanName(typed);
        if (name == null) {
            player.displayClientMessage(Component.translatable("message.starktech.robot.bad_name", BotNetwork.MAX_NAME), true);
            return false;
        }
        if (BotNetwork.get(player.serverLevel()).nameTaken(robot.owner(), name, robot.getUUID())) {
            player.displayClientMessage(Component.translatable("message.starktech.robot.name_taken", name), true);
            return false;
        }
        robot.setCustomName(Component.literal(name));
        return true;
    }

    /** Oppakken: de robot wordt een item in je rugzak (met alles erop en eraan), en zijn dock is weer vrij. */
    public static void pickUp(ServerPlayer player, GuardRobotEntity robot) {
        ServerLevel level = player.serverLevel();
        if (robot.home() != null && level.getBlockEntity(robot.home()) instanceof RobotDockBlockEntity dock
                && robot.getUUID().equals(dock.robot())) {
            dock.setRobot(null);
        }
        ItemStack stack = GuardRobotItem.create(robot.toData());
        level.playSound(null, robot.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 0.6f, 0.8f);
        robot.discard();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.displayClientMessage(Component.translatable("message.starktech.robot.picked_up", stack.getHoverName()), true);
    }

    /** De robot met dit nummer, als hij dichtbij genoeg is; anders null. */
    private static @Nullable GuardRobotEntity robot(ServerPlayer player, int entityId) {
        if (player.serverLevel().getEntity(entityId) instanceof GuardRobotEntity robot && robot.isAlive()
                && player.distanceToSqr(robot) <= REACH * REACH) {
            return robot;
        }
        return null;
    }
}
