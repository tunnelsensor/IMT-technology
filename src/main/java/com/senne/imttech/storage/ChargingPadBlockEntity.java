package com.senne.imttech.storage;

import com.senne.imttech.car.RcCarEntity;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.EnergyCarry;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.weapons.EnergyCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * De binnenkant van een Charging Pad (zie ChargingPadBlock): elke tick kijkt hij of er een RC Car op staat met een
 * energiecel die niet vol is, haalt hij wat stroom van het net ({@code [rcCar] padRate} per tick) en stopt die in de
 * cel. Hij onthoudt niets: wat hij van het net haalt, zit meteen in de cel.
 */
public class ChargingPadBlockEntity extends BlockEntity {
    /** Zo lang (ticks) blijven zijn strepen nog gloeien nadat hij voor het laatst laadde (anders knipperen ze). */
    private static final int GLOW_TICKS = 10;

    /** Maakt van een kommagetal per tick hele stroom (zie EnergyCarry). */
    private final EnergyCarry carry = new EnergyCarry();
    private int glow;

    public ChargingPadBlockEntity(BlockPos pos, BlockState state) {
        super(StorageRegistry.CHARGING_PAD_ENTITY.get(), pos, state);
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean charged = false;
        // De auto staat ín het blok van de plaat (de plaat is maar twee pixels dik).
        for (RcCarEntity car : level.getEntitiesOfClass(RcCarEntity.class, new AABB(pos), RcCarEntity::isAlive)) {
            if (!car.hasCell()) {
                continue;
            }
            double missing = EnergyCellItem.missingEnergy(car.cell());
            if (missing < 1.0) {
                continue;
            }
            int wanted = (int) Math.min(missing, carry.take(ImtConfig.RcCar.PAD_RATE));
            if (wanted > 0) {
                int got = PowerGrid.draw(level, pos, wanted);
                if (got > 0) {
                    EnergyCellItem.addEnergy(car.cell(), got);
                    charged = true;
                }
            } else {
                charged = glow > 0; // een kleine stroom: niet elke tick een hele eenheid, maar hij laadt wel
            }
            break; // één auto tegelijk
        }
        glow = charged ? GLOW_TICKS : Math.max(0, glow - 1);
        boolean lit = glow > 0;
        if (state.getValue(ChargingPadBlock.CHARGING) != lit) {
            level.setBlock(pos, state.setValue(ChargingPadBlock.CHARGING, lit), Block.UPDATE_CLIENTS);
        }
    }
}
