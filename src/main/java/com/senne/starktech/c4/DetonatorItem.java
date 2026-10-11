package com.senne.starktech.c4;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.registry.ModSounds;
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
 * De detonator (hoofdstuk 10: "één C4 met een afstandsbediening").
 *
 * <ul>
 *   <li>Plak je een <b>remote C4</b> terwijl je een detonator bij je hebt, dan hoort hij bij die
 *       detonator. Een remote C4 die nog nergens bij hoort, koppel je met een rechtsklik met de
 *       detonator erop.</li>
 *   <li><b>Rechtsklik</b>: al zijn ladingen binnen {@link StarkConfig.C4#DETONATOR_RANGE} blokken (in dezelfde wereld) gaan af,
 *       kort na elkaar.</li>
 *   <li><b>Sluipen + rechtsklik</b>: hoeveel ladingen er bij hem horen en hoeveel er in bereik zijn
 *       (zonder iets op te blazen).</li>
 * </ul>
 * Met de detonator in je hand zie je je ladingen als rode markering (zie client/c4/DetonatorMarkers).
 */
public class DetonatorItem extends Item {
    /** Zoveel ticks zit er tussen twee ladingen die na elkaar afgaan. */
    private static final int BETWEEN = 3;

    public DetonatorItem(Properties properties) {
        super(properties);
    }

    /** Wat deze detonator onthoudt, of null als hij nog nooit gebruikt is. */
    public static @Nullable DetonatorData data(ItemStack stack) {
        return stack.get(ModDataComponents.DETONATOR.get());
    }

    /** Wat deze detonator onthoudt; heeft hij nog geen nummer, dan krijgt hij er nu een. */
    public static DetonatorData ensure(ItemStack stack) {
        DetonatorData data = data(stack);
        if (data == null) {
            data = new DetonatorData(UUID.randomUUID(), List.of());
            stack.set(ModDataComponents.DETONATOR.get(), data);
        }
        return data;
    }

    /** De eerste detonator in de rugzak (of hand) van deze speler, of null. */
    public static @Nullable ItemStack find(Player player) {
        if (player.getOffhandItem().getItem() instanceof DetonatorItem) {
            return player.getOffhandItem();
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof DetonatorItem) {
                return stack;
            }
        }
        return null;
    }

    /**
     * Koppelt de remote C4 op {@code pos} aan deze detonator. Geeft false als de detonator vol zit
     * (hooguit {@link DetonatorData#MAX_CHARGES} ladingen).
     */
    public static boolean link(ServerLevel level, BlockPos pos, C4BlockEntity c4, ItemStack detonator) {
        DetonatorData data = ensure(detonator);
        GlobalPos here = GlobalPos.of(level.dimension(), pos.immutable());
        DetonatorData linked = data.with(here);
        if (linked == data && !data.charges().contains(here)) {
            return false;
        }
        detonator.set(ModDataComponents.DETONATOR.get(), linked);
        c4.link(linked.id());
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

    /** Wat er met een signaal gebeurt: zoveel gingen af, zoveel waren te ver, zoveel waren weg. */
    public record Fired(int fired, int outOfRange, int gone) {}

    /**
     * De knop: al zijn ladingen in bereik gaan af, de dichtstbijzijnde eerst en dan om de drie ticks
     * de volgende. Ladingen die er niet meer zijn (ontploft, onschadelijk gemaakt), vergeet hij.
     */
    public static Fired fire(ServerLevel level, ServerPlayer player, ItemStack stack) {
        level.playSound(null, player.blockPosition(), ModSounds.DETONATOR.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        DetonatorData data = data(stack);
        if (data == null || data.charges().isEmpty()) {
            player.displayClientMessage(Component.translatable("item.starktech.detonator.none").withStyle(ChatFormatting.GRAY), true);
            return new Fired(0, 0, 0);
        }
        List<GlobalPos> kept = new ArrayList<>();
        List<C4BlockEntity> ready = new ArrayList<>();
        int outOfRange = 0;
        int gone = 0;
        for (GlobalPos charge : data.charges()) {
            if (!charge.dimension().equals(level.dimension())
                    || charge.pos().distToCenterSqr(player.position()) > (double) Math.round(StarkConfig.C4.DETONATOR_RANGE) * Math.round(StarkConfig.C4.DETONATOR_RANGE)
                    || !level.isLoaded(charge.pos())) {
                outOfRange++;
                kept.add(charge);
                continue;
            }
            if (level.getBlockEntity(charge.pos()) instanceof C4BlockEntity c4 && data.id().equals(c4.detonator())) {
                ready.add(c4);
            } else {
                gone++;
            }
        }
        ready.sort((a, b) -> Double.compare(a.getBlockPos().distToCenterSqr(player.position()),
                b.getBlockPos().distToCenterSqr(player.position())));
        for (int i = 0; i < ready.size(); i++) {
            ready.get(i).detonateIn(1 + i * BETWEEN);
        }
        stack.set(ModDataComponents.DETONATOR.get(), data.withCharges(kept));
        Component message = outOfRange > 0
                ? Component.translatable("item.starktech.detonator.fired_some", ready.size(), outOfRange, Math.round(StarkConfig.C4.DETONATOR_RANGE))
                : Component.translatable("item.starktech.detonator.fired", ready.size());
        player.displayClientMessage(message.copy().withStyle(ready.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.RED), true);
        return new Fired(ready.size(), outOfRange, gone);
    }

    /** Sluipen + rechtsklik: hoeveel ladingen horen erbij, en hoeveel zijn in bereik. */
    private static void status(ServerLevel level, ServerPlayer player, ItemStack stack) {
        DetonatorData data = data(stack);
        int linked = data == null ? 0 : data.charges().size();
        int inRange = 0;
        if (data != null) {
            for (GlobalPos charge : data.charges()) {
                if (charge.dimension().equals(level.dimension())
                        && charge.pos().distToCenterSqr(player.position()) <= (double) Math.round(StarkConfig.C4.DETONATOR_RANGE) * Math.round(StarkConfig.C4.DETONATOR_RANGE)) {
                    inRange++;
                }
            }
        }
        player.displayClientMessage(Component.translatable("item.starktech.detonator.status", linked, inRange, Math.round(StarkConfig.C4.DETONATOR_RANGE))
                .withStyle(ChatFormatting.AQUA), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DetonatorData data = data(stack);
        tooltip.add(Component.translatable("tooltip.starktech.detonator.linked", data == null ? 0 : data.charges().size())
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.starktech.detonator.fire", Math.round(StarkConfig.C4.DETONATOR_RANGE)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.detonator.status").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starktech.detonator.link").withStyle(ChatFormatting.GRAY));
    }
}
