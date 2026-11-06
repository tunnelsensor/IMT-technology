package com.senne.imttech.guns;

import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Een magazijn (hoofdstuk 52, jouw wens: "het magazijn kogel per kogel laden"). Hoeveel patronen erin zitten, staat
 * in het data component MAG_ROUNDS; magazijnen stapelen daarom niet (elk heeft zijn eigen inhoud, net als een
 * energiecel). Vullen: houd het magazijn vast en druk <b>R</b>: elke druk duwt er één patroon uit je inventaris in (zie
 * Firearms.loadRound). In een pistool: <b>rechtsklik</b> met het magazijn in je hand, dan gaat het in het pistool in
 * je werkbalk dat er geen heeft (jouw keuze voor wie geen magazijnpouch heeft: R op het pistool gooit het oude eruit,
 * dan scrollen naar het magazijn, rechtsklikken, en terug naar het pistool).
 */
public class MagazineItem extends Item {
    private final FirearmType type;

    public MagazineItem(Properties properties, FirearmType type) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    public FirearmType type() {
        return type;
    }

    /** Voor welk pistool dit magazijn is, of null als het geen magazijn is. */
    public static FirearmType typeOf(ItemStack stack) {
        return stack.getItem() instanceof MagazineItem item ? item.type : null;
    }

    /** Zoveel patronen zitten erin. */
    public static int rounds(ItemStack stack) {
        Integer rounds = stack.get(ModDataComponents.MAG_ROUNDS.get());
        return rounds == null ? 0 : Math.max(0, rounds);
    }

    public static void setRounds(ItemStack stack, int rounds) {
        FirearmType type = typeOf(stack);
        int capacity = type == null ? 0 : type.capacity();
        stack.set(ModDataComponents.MAG_ROUNDS.get(), Math.max(0, Math.min(capacity, rounds)));
    }

    /** Een magazijn met zoveel patronen. */
    public static ItemStack of(FirearmType type, int rounds) {
        ItemStack stack = new ItemStack(ModItems.magazine(type));
        setRounds(stack, rounds);
        return stack;
    }

    public static boolean isFull(ItemStack stack) {
        FirearmType type = typeOf(stack);
        return type != null && rounds(stack) >= type.capacity();
    }

    /** Rechtsklik: in het pistool in je werkbalk dat er geen heeft (de server doet het, met een korte animatie). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            Firearms.insertMagazine(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13 * rounds(stack) / (float) Math.max(1, type.capacity()));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return rounds(stack) == 0 ? 0xFF3030 : 0xD6AC58;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.magazine.rounds", rounds(stack), type.capacity())
                .withStyle(rounds(stack) == 0 ? ChatFormatting.RED : ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.imttech.magazine").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.magazine.keys").withStyle(ChatFormatting.DARK_GRAY));
    }
}
