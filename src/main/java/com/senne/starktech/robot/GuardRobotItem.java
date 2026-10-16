package com.senne.starktech.robot;

import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModItems;
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
 * en hij staat erbovenop, met zijn gezicht naar jou; jij bent dan zijn eigenaar. Wat hij is (onderdelen,
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
        BlockPos pos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
                ? clicked : clicked.relative(context.getClickedFace());
        GuardRobotEntity robot = ModEntities.GUARD_ROBOT.get().create(level);
        if (robot == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        // Met zijn gezicht naar wie hem neerzet.
        float yaw = player == null ? 0 : player.getYRot() + 180.0f;
        robot.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
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
        level.addFreshEntity(robot);
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
            tooltip.add(Component.translatable("tooltip.starktech.guard_robot.part", slot.title(), parts.get(slot).title())
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.starktech.guard_robot.board", data.board().used())
                .withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.translatable("tooltip.starktech.guard_robot.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
