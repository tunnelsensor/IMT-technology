package com.senne.imttech.armor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * De Armor Workbench (hoofdstuk 111, jouw wens: "maak voor de bodyarmor een eigen workbench, hier maak je alles voor de
 * armor, de vesten en op een ander tab de plates"). Rechtsklik opent zijn scherm (ArmorWorkbenchMenu), met drie tabbladen:
 * <b>Vests</b> en <b>Plates</b> (tegels met wat het kost en een knop Make, zoals Make parts van de Gunsmith Table; op Plates
 * ook een plate herstellen) en <b>Customize</b> (de tekst op je rug, zijn kleur, en het vest verven of camo). Zoals een
 * crafting table bewaart de werkbank zelf niets: wat je in zijn vakjes legt, krijg je terug als je het scherm sluit.
 */
public class ArmorWorkbenchBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ArmorWorkbenchBlock> CODEC = simpleCodec(ArmorWorkbenchBlock::new);

    public ArmorWorkbenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Het scherm van de werkbank op deze plek openen. */
    public static void open(ServerPlayer player, BlockPos pos) {
        ContainerLevelAccess access = ContainerLevelAccess.create(player.level(), pos);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ArmorWorkbenchMenu(id, inventory, access),
                Component.translatable("block.imttech.armor_workbench")));
    }
}
