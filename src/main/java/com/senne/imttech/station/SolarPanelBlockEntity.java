package com.senne.imttech.station;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Het zonnepaneel: laadt je bril op met daglicht.
 *
 * Hij moet de lucht kunnen zien - onder een dak of diep in een grot doet hij niets. 's Nachts
 * ook niet, en bij regen of onweer levert hij minder. Wat hij overdag opwekt slaat hij op, dus
 * je kunt 's ochtends je bril erin leggen en hem 's avonds vol ophalen.
 */
public class SolarPanelBlockEntity extends ChargerBlockEntity {
    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL.get(), pos, state);
    }

    @Override
    public int capacity() {
        return (int) ImtConfig.Power.SOLAR_CAPACITY;
    }

    @Override
    public boolean usesFuel() {
        return false;
    }

    @Override
    protected double produce(ServerLevel level, BlockPos pos) {
        // Hoeveel zon er op hem valt (niets onder een dak of 's nachts, het meest 's middags): zie SolarMath.
        return ImtConfig.Power.SOLAR_RATE * SolarMath.sunlight(level, pos.above());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // Alleen de bril; brandstof doet een zonnepaneel niets.
        return slot == SLOT_GLASSES && com.senne.imttech.imt.ImtData.isGlasses(stack);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.imttech.solar_panel");
    }
}
