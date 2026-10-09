package com.senne.starktech.mixin;

import com.senne.starktech.security.zone.Zones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Een "mixin": een klein stukje code dat in Minecraft zelf ingrijpt, op een plek waar NeoForge geen
 * event heeft (hoofdstuk 10).
 *
 * Vuur brandt een blok weg in {@code FireBlock.checkBurnOut}: daar kijkt het of een buurblok vlam
 * vat, en zo ja, dan verdwijnt dat blok (of wordt het zelf vuur). Wij kijken vlak daarvoor: ligt dat
 * blok in een actieve zone, dan gebeurt er niets. Zo steekt niemand je houten basis van buitenaf in
 * brand; alleen C4 komt door een zone.
 *
 * Mixin vindt de methode op naam; omdat NeoForge sinds 1.20.5 dezelfde (Mojang-)namen gebruikt als
 * in de code, is daar geen omzettabel ("refmap") voor nodig.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void starktech$keepZoneBlocks(Level level, BlockPos pos, int chance, RandomSource random, int age,
                                          Direction face, CallbackInfo info) {
        if (!Zones.fireMayBurn(level, pos)) {
            info.cancel();
        }
    }
}
