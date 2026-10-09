package com.senne.starktech.mixin;

import com.senne.starktech.c4.Cracks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Barsten horen bij één blok (hoofdstuk 11). Komt er op die plek een ander blok te staan, hoe dan ook
 * (gehakt, opgeblazen, weggeduwd door een zuiger, vervangen met /setblock...), dan heeft dat nieuwe
 * blok geen barsten en is het nooit geplamuurd.
 *
 * Elk blok dat in een geladen stuk wereld verandert, komt langs {@code LevelChunk.setBlockState}. Die
 * geeft het oude blok terug (of null als er niets veranderde); wij kijken vlak daarna of het een ander
 * soort blok werd. NeoForge heeft daar geen event voor dat altijd komt, vandaar deze mixin (net als
 * FireBlockMixin).
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void starktech$forgetCracks(BlockPos pos, BlockState state, boolean isMoving,
                                        CallbackInfoReturnable<BlockState> info) {
        BlockState old = info.getReturnValue();
        if (old != null && !old.is(state.getBlock())
                && ((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level) {
            Cracks.blockChanged(level, pos);
        }
    }
}
