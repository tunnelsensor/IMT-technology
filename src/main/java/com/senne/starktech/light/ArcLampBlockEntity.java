package com.senne.starktech.light;

import com.senne.starktech.energy.StarkGrid;
import com.senne.starktech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * De binnenkant van een Arc Lamp: elke tick stroom halen, en aan of uit.
 *
 * Stroom = aan, geen stroom = uit (jouw wens). Twee kleine regels zodat hij niet knippert:
 * <ul>
 *   <li>Brandt hij, dan blijft hij aan zolang het net elke tick {@link LightBalance#ARC_LAMP_POWER} heeft.</li>
 *   <li>Is hij uit, dan gaat hij pas aan als het net stroom heeft voor even branden
 *       ({@link LightBalance#ARC_LAMP_START_TICKS} ticks). Anders zou hij aan een zwakke generator elke
 *       tick aan en uit gaan.</li>
 * </ul>
 * Zet je een lichtschakelaar uit, dan ziet hij de volgende tick geen generator meer, en gaat hij meteen uit.
 *
 * Er wordt niets bewaard: aan of uit staat in het blok zelf ({@link ArcLampBlock#LIT}).
 */
public class ArcLampBlockEntity extends BlockEntity {
    /** Heeft deze lamp zich na het laden al gemeld bij ArcLampLight? */
    private boolean announced;
    /** In je eigen spel: wat we ArcLampLight het laatst vertelden. */
    private boolean clientLit;

    public ArcLampBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARC_LAMP.get(), pos, state);
    }

    // ================================================================ Server

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean lit = state.getValue(ArcLampBlock.LIT);
        int cost = LightBalance.ARC_LAMP_POWER;
        int needed = Math.max(1, lit ? cost : cost * LightBalance.ARC_LAMP_START_TICKS);
        boolean on = StarkGrid.available(level, pos) >= needed;
        if (on && cost > 0) {
            StarkGrid.draw(level, pos, cost);
        }
        if (on != lit) {
            level.setBlock(pos, state.setValue(ArcLampBlock.LIT, on), Block.UPDATE_ALL);
            // Het klikje van een koperen lamp, iets lager: deze is groter.
            level.playSound(null, pos, on ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
                    SoundSource.BLOCKS, 0.8f, 0.7f);
        }
        if (on != lit || !announced) {
            announced = true;
            ArcLampLight.setOn(level, pos, on);
        }
    }

    // ================================================================ Je eigen spel

    public void clientTick(Level level, BlockPos pos, BlockState state) {
        boolean lit = state.getValue(ArcLampBlock.LIT);
        if (lit != clientLit) {
            clientLit = lit;
            ArcLampLight.clientLamp(level, pos, lit);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        forgetOnClient();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        forgetOnClient();
    }

    /**
     * In je eigen spel is de lamp weg (afgebroken, of zijn chunk is uit beeld). Op de server doen we hier
     * niets: daar regelt het blok het afbreken, en een lamp in een chunk die even niet geladen is, brandt
     * gewoon door.
     */
    private void forgetOnClient() {
        if (level != null && level.isClientSide && clientLit) {
            clientLit = false;
            ArcLampLight.clientLamp(level, worldPosition, false);
        }
    }
}
