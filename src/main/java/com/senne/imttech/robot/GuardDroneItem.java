package com.senne.imttech.robot;

import com.senne.imttech.arsenal.DroneDockBlock;
import com.senne.imttech.arsenal.DroneDockBlockEntity;
import com.senne.imttech.bot.BotNetwork;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Een security drone in je rugzak (hoofdstuk 25): gebouwd aan de werkbank, of opgepakt. Rechtsklik op een Drone Dock
 * en hij staat erop, met zijn neus naar de open kant: dat dock is zijn thuis (daar laadt hij, en daar landt hij als
 * je hem uitzet). Op een ander blok hangt hij erboven, zonder thuis. Jij bent dan zijn eigenaar. Wat hij is
 * (printplaat, naam, programma) staat in het item (zie DroneData).
 */
public class GuardDroneItem extends Item {
    public GuardDroneItem(Properties properties) {
        super(properties);
    }

    /** Een drone-item met deze printplaat, naam, batterij en programma. */
    public static ItemStack create(DroneData data) {
        ItemStack stack = new ItemStack(ModItems.GUARD_DRONE.get());
        stack.set(ModDataComponents.DRONE_DATA.get(), data);
        return stack;
    }

    /** Wat er in dit item zit (een leeg item, van de werkbank of uit creative, is een nieuwe drone). */
    public static DroneData data(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.DRONE_DATA.get(), DroneData.DEFAULT);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        Player player = context.getPlayer();
        // Op een Drone Dock: daar is hij thuis (als het dock nog vrij is).
        DroneDockBlockEntity dock = level.getBlockEntity(clicked) instanceof DroneDockBlockEntity found ? found : null;
        if (dock != null && player instanceof ServerPlayer user && !dock.mayUse(user)) {
            player.displayClientMessage(Component.translatable("block.imttech.drone_dock.not_yours"), true);
            return InteractionResult.FAIL;
        }
        if (dock != null && dock.bot() != null && level.getEntity(dock.bot()) instanceof GuardDroneEntity other && other.isAlive()) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.imttech.drone_dock.taken"), true);
            }
            return InteractionResult.FAIL;
        }
        BlockPos pos = dock != null || level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
                ? clicked : clicked.relative(context.getClickedFace());
        GuardDroneEntity drone = ModEntities.GUARD_DRONE.get().create(level);
        if (drone == null) {
            return InteractionResult.FAIL;
        }
        // Met zijn neus naar de open kant van zijn dock (of naar wie hem neerzet).
        float yaw = dock != null ? level.getBlockState(clicked).getValue(DroneDockBlock.FACING).toYRot()
                : player == null ? 0 : player.getYRot() + 180.0f;
        drone.moveTo(pos.getX() + 0.5, pos.getY() + (dock != null ? 4.0 / 16.0 : GuardDroneEntity.HOVER), pos.getZ() + 0.5, yaw, 0);
        drone.setYaw(yaw);
        if (!level.noCollision(drone)) {
            return InteractionResult.FAIL; // geen plek
        }
        ItemStack stack = context.getItemInHand();
        drone.applyData(data(stack));
        if (player instanceof ServerPlayer serverPlayer) {
            drone.setOwner(serverPlayer);
        }
        // Een naam die bij deze eigenaar nog niet bestaat ("Drone 1"...): in je code noem je hem zo.
        BotNetwork network = BotNetwork.get(level);
        String name = data(stack).name();
        if (name.isEmpty() || network.nameTaken(drone.owner(), name, null)) {
            drone.setCustomName(Component.literal(network.freeName(drone.owner(), ProgramKind.DRONE)));
        }
        if (dock != null) {
            drone.setHome(pos);
            dock.setBot(drone.getUUID());
        }
        level.addFreshEntity(drone);
        network.update(drone);
        level.playSound(null, pos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 0.5f, 1.7f);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        stack.consume(1, player); // in creative blijft hij
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DroneData data = data(stack);
        if (!data.name().isEmpty()) {
            tooltip.add(Component.literal(data.name()).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.imttech.guard_robot.board", data.board().used())
                .withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.translatable("tooltip.imttech.guard_drone.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
