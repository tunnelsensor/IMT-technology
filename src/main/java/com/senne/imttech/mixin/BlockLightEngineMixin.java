package com.senne.imttech.mixin;

import com.senne.imttech.light.ArcLampLight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Het licht-rekenwerk van het spel vraagt in {@code BlockLightEngine.getEmission} aan een blok: "hoeveel
 * licht geef jij?" (een fakkel 14, gloeisteen 15, lucht 0). Op de plekken waar een brandende Arc Lamp
 * schijnt, zeggen wij 15, ook als daar lucht is: een onzichtbare lichtbron. Zo maken we van een lamp met
 * lichtniveau 100 gewoon licht van het spel (zie light/ArcLampLight voor het hele verhaal).
 *
 * Een "redirect" vervangt precies die ene vraag aan het blok door onze eigen methode. Die stelt de vraag
 * zelf nog steeds (een fakkel blijft 14), en neemt het hoogste van de twee.
 */
@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineMixin {
    @Redirect(method = "getEmission", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I"))
    private int imttech$arcLampLight(BlockState state, BlockGetter level, BlockPos pos) {
        int own = state.getLightEmission(level, pos);
        return own >= 15 ? own : Math.max(own, ArcLampLight.emission(level, pos));
    }
}
