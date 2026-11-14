package com.senne.imttech.car;

import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Een RC Car in je rugzak (hoofdstuk 108): gemaakt aan de werkbank, of opgepakt. Rechtsklik op een blok en hij staat
 * daar, met zijn neus de kant op waar jij naar kijkt: dat is waar "vooruit" heen gaat. Jij bent dan zijn eigenaar. Wat
 * hij is (naam, programma, energiecel, kofferbak, boorkop) staat in het item (zie RcCarData), dus oppakken en weer
 * neerzetten verandert niets aan hem. Een nieuwe auto (van de werkbank) heeft geen programma en de ijzeren boorkop.
 *
 * Heb je een RC Controller bij je die nog met geen auto gekoppeld is, dan koppelt die meteen met deze.
 */
public class RcCarItem extends Item {
    public RcCarItem(Properties properties) {
        super(properties);
    }

    /** Een auto-item met deze naam, dit programma, deze cel en deze kofferbak. */
    public static ItemStack create(RcCarData data) {
        ItemStack stack = new ItemStack(ModItems.RC_CAR.get());
        stack.set(ModDataComponents.RC_CAR_DATA.get(), data);
        return stack;
    }

    /**
     * Een auto is zelf een doos (zijn kofferbak): hij past niet in een rugzak, een shulkerdoos of een bundel, net zoals
     * een shulkerdoos niet in een shulkerdoos past.
     */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    /** Wat er in dit item zit (een leeg item, van de werkbank of uit creative, is een nieuwe auto). */
    public static RcCarData data(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.RC_CAR_DATA.get(), RcCarData.DEFAULT);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        Player player = context.getPlayer();
        BlockPos pos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
                ? clicked : clicked.relative(context.getClickedFace());
        RcCarEntity car = ModEntities.RC_CAR.get().create(level);
        if (car == null) {
            return InteractionResult.FAIL;
        }
        // Zijn neus de kant op waar jij naar kijkt: forward() rijdt dan van je weg.
        Direction facing = player == null ? Direction.NORTH : player.getDirection();
        float yaw = facing.toYRot();
        car.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        car.setYHeadRot(yaw);
        car.setYBodyRot(yaw);
        if (!level.noCollision(car)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.imttech.rc_car.no_room"), true);
            }
            return InteractionResult.FAIL; // geen plek
        }
        ItemStack stack = context.getItemInHand();
        car.applyData(data(stack));
        car.body().setFacing(facing);
        // Rijdt er al een auto met dit nummer (het item was een kopie)? Dan krijgt deze een nieuw nummer.
        if (RcCars.find(car.link()) != null) {
            car.newLink();
        }
        if (player instanceof ServerPlayer serverPlayer) {
            car.setOwner(serverPlayer);
        }
        level.addFreshEntity(car);
        RcCars.register(car);
        level.playSound(null, pos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 0.4f, 1.9f);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        if (player instanceof ServerPlayer serverPlayer) {
            RcCars.linkFreeController(serverPlayer, car);
        }
        stack.consume(1, player); // in creative blijft hij
        if (!stack.isEmpty()) {
            // In creative blijft het item in je hand. Dat item vergeet zijn nummer: zo krijgt de volgende auto eruit een
            // nieuw nummer, en houdt de auto die er nu staat het zijne (je RC Controller blijft dus werken).
            RcCarData data = data(stack);
            stack.set(ModDataComponents.RC_CAR_DATA.get(), new RcCarData(data.name(), data.source(), java.util.Optional.empty(),
                    data.cell(), data.trunk(), data.drill()));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RcCarData data = data(stack);
        if (!data.name().isEmpty()) {
            tooltip.add(Component.literal(data.name()).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(data.hasCell()
                ? Component.translatable("tooltip.imttech.rc_car.cell", Math.round(data.cell())).withStyle(ChatFormatting.DARK_GREEN)
                : Component.translatable("tooltip.imttech.rc_car.no_cell").withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.imttech.rc_car.drill", data.drill().displayName(),
                Math.round(data.drill().speed() * 100)).withStyle(ChatFormatting.GRAY));
        int items = data.items();
        if (items > 0) {
            tooltip.add(Component.translatable("tooltip.imttech.rc_car.trunk", items).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.imttech.rc_car.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
