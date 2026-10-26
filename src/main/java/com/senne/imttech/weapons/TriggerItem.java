package com.senne.imttech.weapons;

import com.senne.imttech.c4.DetonatorData;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * De EMP-trigger (hoofdstuk 35, jouw keuze: een eigen item, los van de detonator van C4). Zo werkt hij, net als de
 * detonator:
 * <ul>
 *   <li>plak je een <b>Remote EMP</b> terwijl je een EMP-trigger bij je hebt, dan hoort hij bij die trigger; een
 *       Remote EMP die nog nergens bij hoort, koppel je met een rechtsklik met de trigger erop;</li>
 *   <li><b>rechtsklik</b>: al zijn Remote EMP's binnen EMP_TRIGGER_RANGE blokken gaan af, kort na elkaar;</li>
 *   <li><b>sneak + rechtsklik</b>: hoeveel er bij hem horen en hoeveel er in bereik zijn.</li>
 * </ul>
 * Wat hij onthoudt (zijn nummer en waar zijn ladingen zitten) is hetzelfde soort gegevens als bij de detonator, maar
 * in een eigen component (EMP_TRIGGER), zodat de twee nooit door elkaar lopen.
 */
public class EmpTriggerItem extends Item {
    /** Zoveel ticks zit er tussen twee ladingen die na elkaar afgaan. */
    private static final int BETWEEN = 3;

    public EmpTriggerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Wat deze trigger onthoudt, of null als hij nog nooit gebruikt is. */
    public static @Nullable DetonatorData data(ItemStack stack) {
        return stack.get(ModDataComponents.EMP_TRIGGER.get());
    }

    /** Wat deze trigger onthoudt; heeft hij nog geen nummer, dan krijgt hij er nu een. */
    public static DetonatorData ensure(ItemStack stack) {
        DetonatorData data = data(stack);
        if (data == null) {
            data = new DetonatorData(UUID.randomUUID(), List.of());
            stack.set(ModDataComponents.EMP_TRIGGER.get(), data);
        }
        return data;
    }

    /** De EMP-trigger die deze speler bij zich heeft (eerst in zijn andere hand), of null. */
    public static @Nullable ItemStack find(Player player) {
        if (player.getOffhandItem().getItem() instanceof EmpTriggerItem) {
            return player.getOffhandItem();
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof EmpTriggerItem) {
                return stack;
            }
        }
        return null;
    }

    /** Koppelt de Remote EMP op {@code pos} aan deze trigger. Geeft false als de trigger vol zit. */
    public static boolean link(ServerLevel level, BlockPos pos, RemoteEmpBlockEntity emp, ItemStack trigger) {
        DetonatorData data = ensure(trigger);
        GlobalPos here = GlobalPos.of(level.dimension(), pos.immutable());
        DetonatorData linked = data.with(here);
        if (linked == data && !data.charges().contains(here)) {
            return false;
        }
        trigger.set(ModDataComponents.EMP_TRIGGER.get(), linked);
        emp.link(linked.id());
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                status(serverLevel, serverPlayer, stack);
            } else {
                fire(serverLevel, serverPlayer, stack);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static double range() {
        return Math.round(ImtConfig.Weapons.EMP_TRIGGER_RANGE);
    }

    /**
     * De knop: al zijn Remote EMP's in bereik gaan af, de dichtstbijzijnde eerst en dan om de drie ticks de volgende.
     * Wat er niet meer is (afgegaan of eraf gehaald), vergeet hij. Geeft hoeveel er afgaan.
     */
    public static int fire(ServerLevel level, ServerPlayer player, ItemStack stack) {
        level.playSound(null, player.blockPosition(), ModSounds.DETONATOR.get(), SoundSource.PLAYERS, 1.0f, 1.3f);
        DetonatorData data = data(stack);
        if (data == null || data.charges().isEmpty()) {
            player.displayClientMessage(Component.translatable("item.imttech.emp_trigger.none").withStyle(ChatFormatting.GRAY), true);
            return 0;
        }
        List<GlobalPos> kept = new ArrayList<>();
        List<RemoteEmpBlockEntity> ready = new ArrayList<>();
        int outOfRange = 0;
        for (GlobalPos charge : data.charges()) {
            if (!charge.dimension().equals(level.dimension())
                    || charge.pos().distToCenterSqr(player.position()) > range() * range()
                    || !level.isLoaded(charge.pos())) {
                outOfRange++;
                kept.add(charge);
                continue;
            }
            if (level.getBlockEntity(charge.pos()) instanceof RemoteEmpBlockEntity emp && data.id().equals(emp.trigger())) {
                ready.add(emp);
            }
        }
        ready.sort((a, b) -> Double.compare(a.getBlockPos().distToCenterSqr(player.position()),
                b.getBlockPos().distToCenterSqr(player.position())));
        for (int i = 0; i < ready.size(); i++) {
            ready.get(i).goOffIn(1 + i * BETWEEN);
        }
        stack.set(ModDataComponents.EMP_TRIGGER.get(), data.withCharges(kept));
        Component message = outOfRange > 0
                ? Component.translatable("item.imttech.emp_trigger.fired_some", ready.size(), outOfRange, (int) range())
                : Component.translatable("item.imttech.emp_trigger.fired", ready.size());
        player.displayClientMessage(message.copy().withStyle(ready.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.AQUA), true);
        return ready.size();
    }

    /** Sneak + rechtsklik: hoeveel Remote EMP's horen erbij, en hoeveel zijn in bereik. */
    private static void status(ServerLevel level, ServerPlayer player, ItemStack stack) {
        DetonatorData data = data(stack);
        int linked = data == null ? 0 : data.charges().size();
        int inRange = 0;
        if (data != null) {
            for (GlobalPos charge : data.charges()) {
                if (charge.dimension().equals(level.dimension())
                        && charge.pos().distToCenterSqr(player.position()) <= range() * range()) {
                    inRange++;
                }
            }
        }
        player.displayClientMessage(Component.translatable("item.imttech.emp_trigger.status", linked, inRange, (int) range())
                .withStyle(ChatFormatting.AQUA), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DetonatorData data = data(stack);
        tooltip.add(Component.translatable("tooltip.imttech.emp_trigger.linked", data == null ? 0 : data.charges().size())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.imttech.emp_trigger.fire", (int) range()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.emp_trigger.status").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.emp_trigger.link").withStyle(ChatFormatting.GRAY));
    }
}
