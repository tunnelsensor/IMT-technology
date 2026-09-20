package com.senne.starktech.station;

import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.registry.ModBlockEntities;
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
        return EdithBalance.SOLAR_CAPACITY;
    }

    @Override
    public boolean usesFuel() {
        return false;
    }

    @Override
    protected int produce(ServerLevel level, BlockPos pos) {
        if (!level.canSeeSky(pos.above())) {
            return 0; // er zit iets boven hem
        }
        if (!level.isDay()) {
            return 0;
        }
        // Hoe hoger de zon staat, hoe meer hij oplevert (middags het meest).
        float angle = level.getSunAngle(1.0f);
        float height = Math.max(0.0f, net.minecraft.util.Mth.cos(angle));
        float weather = level.isThundering() ? 0.2f : level.isRaining() ? 0.4f : 1.0f;
        return Math.round(EdithBalance.SOLAR_RATE * height * weather);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // Alleen de bril; brandstof doet een zonnepaneel niets.
        return slot == SLOT_GLASSES && com.senne.starktech.edith.EdithData.isGlasses(stack);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.starktech.solar_panel");
    }
}
