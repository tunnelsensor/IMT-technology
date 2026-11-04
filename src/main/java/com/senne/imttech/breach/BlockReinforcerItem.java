package com.senne.imttech.breach;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.zone.Zones;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * De Block Reinforcer (hoofdstuk 44, jouw wens: "block reinforcer zodat je base mooi is en ook sterk voor c4").
 *
 * Rechtsklik op een blok: het is verstevigd en telt voortaan als een <b>sterk</b> blok tegen frags (zo sterk als
 * obsidiaan, jouw keuze), hoe mooi of zwak het zelf ook is: kwarts, hout, glas. Het kost één Reinforcement Plate
 * (ijzer en obsidiaan) uit je rugzak. Sluipen + rechtsklik verstevigt de hele muur van dat blok in één keer
 * (alle aaneengesloten blokken van dezelfde soort, tot {@link ImtConfig.Breach#REINFORCER_WALL}), zolang je platen
 * hebt. Alleen waar je mag bouwen (in de zone van een ander niet).
 *
 * Het blok blijft er precies zo uitzien (jouw keuze). Met je bril op en dit gereedschap in je hand zie je een dun
 * randje om elk verstevigd blok (zie client/repair/ReinforcedRenderer). Komt er een ander blok te staan, dan is de
 * versteviging weg, net als barsten; barsten en plamuurbeurten blijven gewoon meetellen.
 */
public class BlockReinforcerItem extends Item {
    /** Zo ver om je heen stuurt de server de verstevigde blokken naar je bril, en hooguit zoveel. */
    public static final int SHOW_RADIUS = 24;
    public static final int SHOW_MAX = 512;

    public BlockReinforcerItem(Properties properties) {
        super(properties);
    }

    /** Houdt deze speler de Block Reinforcer vast (in een van zijn handen)? */
    public static boolean holds(Player player) {
        return player.getMainHandItem().is(ModItems.BLOCK_REINFORCER.get()) || player.getOffhandItem().is(ModItems.BLOCK_REINFORCER.get());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.CONSUME; // de server weet wat verstevigd is en beslist
        }
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        BlockState state = level.getBlockState(pos);
        if (!canReinforce(level, pos, state)) {
            player.displayClientMessage(Component.translatable("item.imttech.block_reinforcer.cannot"), true);
            return InteractionResult.FAIL;
        }
        if (Zones.denies(level, pos, player)) {
            Zones.tellProtected(player);
            return InteractionResult.FAIL;
        }
        boolean creative = player.getAbilities().instabuild;
        int plates = creative ? Integer.MAX_VALUE : countPlates(player);
        if (plates <= 0) {
            player.displayClientMessage(Component.translatable("item.imttech.block_reinforcer.no_plates"), true);
            return InteractionResult.FAIL;
        }
        // Eén blok, of (sluipen) de hele muur van dezelfde soort.
        List<BlockPos> targets = player.isShiftKeyDown() ? wall(level, pos, state.getBlock(), player)
                : Cracks.isReinforced(level, pos) ? List.of() : List.of(pos);
        if (targets.isEmpty()) {
            player.displayClientMessage(Component.translatable("item.imttech.block_reinforcer.already"), true);
            return InteractionResult.FAIL;
        }
        int done = 0;
        for (BlockPos target : targets) {
            if (done >= plates) {
                break;
            }
            Cracks.reinforce(level, target);
            done++;
        }
        if (!creative) {
            takePlates(player, done);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4f, 1.6f);
        Vec3 at = context.getClickLocation();
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.REINFORCEMENT_PLATE.get())),
                at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.05);
        int left = creative ? -1 : plates - done;
        if (done == 1 && targets.size() == 1) {
            player.displayClientMessage(Component.translatable("item.imttech.block_reinforcer.one"), true);
        } else if (done < targets.size()) {
            player.displayClientMessage(Component.translatable("item.imttech.block_reinforcer.out", done, targets.size()), true);
        } else {
            player.displayClientMessage(Component.translatable(left < 0 ? "item.imttech.block_reinforcer.many_creative"
                    : "item.imttech.block_reinforcer.many", done, left), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** Kan dit blok verstevigd worden: geen lucht, geen vloeistof, en te breken (geen bedrock)? */
    public static boolean canReinforce(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && !(state.getBlock() instanceof LiquidBlock) && state.getDestroySpeed(level, pos) >= 0;
    }

    /**
     * De muur: alle blokken van dezelfde soort die (via buren) aan {@code start} vastzitten en nog niet verstevigd
     * zijn, hooguit {@link ImtConfig.Breach#REINFORCER_WALL}, en alleen waar de speler mag bouwen.
     */
    private static List<BlockPos> wall(ServerLevel level, BlockPos start, Block block, Player player) {
        int max = Math.max(1, Math.round(ImtConfig.Breach.REINFORCER_WALL));
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(start);
        seen.add(start);
        while (!todo.isEmpty() && found.size() < max) {
            BlockPos pos = todo.poll();
            if (!Cracks.isReinforced(level, pos) && !Zones.denies(level, pos, player)) {
                found.add(pos);
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.size() < max * 8 && seen.add(next) && level.getBlockState(next).getBlock() == block) {
                    todo.add(next);
                }
            }
        }
        return found;
    }

    /** Hoeveel Reinforcement Plates de speler bij zich heeft. */
    public static int countPlates(Player player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.REINFORCEMENT_PLATE.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Haalt zoveel platen uit de rugzak van de speler. */
    private static void takePlates(Player player, int amount) {
        int left = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.REINFORCEMENT_PLATE.get())) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.block_reinforcer.use", Math.round(ImtConfig.Breach.REINFORCER_WALL))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.block_reinforcer.see").withStyle(ChatFormatting.GRAY));
    }
}
