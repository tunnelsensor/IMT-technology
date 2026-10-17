package com.senne.imttech.robot;

import com.senne.imttech.bot.BotNetwork;
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
 * Een guard robot in je rugzak (hoofdstuk 21): uit de Robot Assembler, of opgepakt. Rechtsklik op een blok
 * en hij staat erbovenop, met zijn gezicht naar jou; jij bent dan zijn eigenaar. Op een vrij Robot Dock is
 * dat dock zijn thuis: daar laadt hij, en daar loopt hij heen als je hem uitzet. Wat hij is (onderdelen,
 * printplaat, naam) staat in het item (zie RobotData).
 */
public class GuardRobotItem extends Item {
    public GuardRobotItem(Properties properties) {
        super(properties);
    }

    /** Een robot-item met deze onderdelen, printplaat en naam. */
    public static ItemStack create(RobotData data) {
        ItemStack stack = new ItemStack(ModItems.GUARD_ROBOT.get());
        stack.set(ModDataComponents.ROBOT_DATA.get(), data);
        return stack;
    }

    /** Wat er in dit item zit (een leeg item, bv. uit creative, wordt een Battle-robot). */
    public static RobotData data(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ROBOT_DATA.get(), RobotData.DEFAULT);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        Player player = context.getPlayer();
        // Op een Robot Dock: daar is hij thuis (als het dock nog vrij is), met zijn gezicht naar de open kant.
        RobotDockBlockEntity dock = level.getBlockEntity(clicked) instanceof RobotDockBlockEntity found ? found : null;
        if (dock != null && dock.robot() != null && level.getEntity(dock.robot()) instanceof GuardRobotEntity other && other.isAlive()) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.imttech.robot_dock.taken"), true);
            }
            return InteractionResult.FAIL;
        }
        BlockPos pos = dock != null || level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
                ? clicked : clicked.relative(context.getClickedFace());
        GuardRobotEntity robot = ModEntities.GUARD_ROBOT.get().create(level);
        if (robot == null) {
            return InteractionResult.FAIL;
        }
        // Met zijn gezicht naar wie hem neerzet (of naar de open kant van zijn dock).
        float yaw = dock != null ? level.getBlockState(clicked).getValue(RobotDockBlock.FACING).toYRot()
                : player == null ? 0 : player.getYRot() + 180.0f;
        robot.moveTo(pos.getX() + 0.5, pos.getY() + (dock != null ? 3.0 / 16.0 : 0.0), pos.getZ() + 0.5, yaw, 0);
        robot.setYHeadRot(yaw);
        robot.yBodyRot = yaw;
        if (!level.noCollision(robot)) {
            return InteractionResult.FAIL; // geen plek
        }
        ItemStack stack = context.getItemInHand();
        robot.applyData(data(stack));
        if (player instanceof ServerPlayer serverPlayer) {
            robot.setOwner(serverPlayer);
        }
        // Een naam die bij deze eigenaar nog niet bestaat ("Guard 1"...): in je code noem je hem zo.
        BotNetwork network = BotNetwork.get(level);
        String name = data(stack).name();
        if (name.isEmpty() || network.nameTaken(robot.owner(), name, null)) {
            robot.setCustomName(Component.literal(network.freeName(robot.owner())));
        }
        if (dock != null) {
            robot.setHome(pos);
            dock.setRobot(robot.getUUID());
        }
        level.addFreshEntity(robot);
        network.update(robot);
        level.playSound(null, pos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 0.6f, 1.4f);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        stack.consume(1, player); // in creative blijft hij
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RobotData data = data(stack);
        if (!data.name().isEmpty()) {
            tooltip.add(Component.literal(data.name()).withStyle(ChatFormatting.AQUA));
        }
        RobotParts parts = data.robotParts();
        for (RobotSlot slot : RobotSlot.values()) {
            tooltip.add(Component.translatable("tooltip.imttech.guard_robot.part", slot.title(), parts.get(slot).title())
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.imttech.guard_robot.board", data.board().used())
                .withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.translatable("tooltip.imttech.guard_robot.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
