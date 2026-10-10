package com.senne.starktech.mixin;

import com.senne.starktech.light.ArcLampLight;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gaat een Arc Lamp aan of uit, dan moet het spel honderdduizenden plekken opnieuw narekenen. Normaal
 * vraag je dat per plek ({@code checkBlock}), en elke vraag wordt dan een klusje voor de licht-thread:
 * honderdduizenden klusjes. Wij leggen de plekken in één lijst klaar (zie light/ArcLampLight), en geven die
 * hier in één keer door, net voordat het licht-rekenwerk aan een ronde begint ({@code runLightUpdates}).
 * Daarna rekent het spel ze allemaal na in die ene ronde.
 *
 * Een "shadow" geeft ons toegang tot een veld van het spel: hier de lijst plekken die het straks narekent
 * ({@code blockNodesToCheck}) en de wereld waar dit licht bij hoort ({@code chunkSource}).
 */
@Mixin(LightEngine.class)
public abstract class LightEngineMixin {
    @Shadow
    @Final
    protected LightChunkGetter chunkSource;

    @Shadow
    @Final
    private LongOpenHashSet blockNodesToCheck;

    @Inject(method = "runLightUpdates", at = @At("HEAD"))
    private void starktech$arcLampChecks(CallbackInfoReturnable<Integer> info) {
        // Alleen het licht van blokken (lampen, fakkels), niet het daglicht.
        if ((Object) this instanceof BlockLightEngine) {
            ArcLampLight.drainChecks(chunkSource.getLevel(), blockNodesToCheck);
        }
    }
}
