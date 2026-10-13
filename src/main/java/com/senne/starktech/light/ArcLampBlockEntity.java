package com.senne.starktech.light;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.energy.StarkGrid;
import com.senne.starktech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * De binnenkant van een Arc Lamp: zijn instellingen, en elke tick stroom halen en aan of uit.
 *
 * <h2>Instelling (10 oktober: "zorg dat je de brightness kunt instellen")</h2>
 * <b>Helderheid</b>: zijn lichtniveau, van 1 tot het maximum uit de config (100). Tot 15 is het een
 * gewone lamp; daarboven reikt zijn licht verder (zie ArcLampLight). Een nieuwe lamp staat op het
 * maximum. Je stelt het in met het scherm van de lamp (zie ArcLamps en ArcLampScreen). De lamp onthoudt
 * het, en je eigen spel krijgt het mee (voor het scherm). (Er was ook een gloed om de lamp; die is weg
 * sinds 13 oktober.)
 *
 * <h2>Stroom</h2>
 * Stroom = aan, geen stroom = uit (jouw wens). Een lamp op niveau 100 gebruikt
 * {@link StarkConfig.Power#ARC_LAMP_POWER} per tick; zachter gebruikt naar verhouding minder (op 50 de helft).
 * Omdat dat geen hele getallen hoeven te zijn, houden we bij wat hij nog "schuldig" is en betalen we
 * telkens de hele stukjes. Twee kleine regels zodat hij niet knippert:
 * <ul>
 *   <li>Brandt hij, dan blijft hij aan zolang het net elke tick genoeg heeft.</li>
 *   <li>Is hij uit, dan gaat hij pas aan als het net stroom heeft voor even branden
 *       ({@link StarkConfig.Power#ARC_LAMP_START_TICKS} ticks). Anders zou hij aan een zwakke generator elke
 *       tick aan en uit gaan.</li>
 * </ul>
 * Zet je een lichtschakelaar uit, dan ziet hij de volgende tick geen generator meer, en gaat hij meteen uit.
 */
public class ArcLampBlockEntity extends BlockEntity {
    /** Zijn lichtniveau (1 tot het maximum uit de config). */
    private int brightness = Math.round(StarkConfig.Lights.ARC_LAMP_MAX_LEVEL);
    /** Stroom die hij nog moet betalen (minder dan 1; zie {@link #powerPerTick}). */
    private double owed;
    /** In je eigen spel: wat we ArcLampLight het laatst vertelden. */
    private boolean clientLit;

    public ArcLampBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARC_LAMP.get(), pos, state);
    }

    // ================================================================ Instellingen

    /** Zijn lichtniveau (nooit meer dan de config nu toestaat). */
    public int brightness() {
        return Mth.clamp(brightness, 1, Math.round(StarkConfig.Lights.ARC_LAMP_MAX_LEVEL));
    }

    /** Een ander lichtniveau (uit het scherm): 1 tot het maximum. */
    public void setBrightness(int value) {
        int clamped = Mth.clamp(value, 1, Math.round(StarkConfig.Lights.ARC_LAMP_MAX_LEVEL));
        if (clamped != brightness) {
            brightness = clamped;
            changed();
        }
    }

    /** Hoeveel stroom per tick een lamp op dit lichtniveau gebruikt (2 op niveau 100, 1 op 50). */
    public static double powerPerTick(int brightness) {
        return StarkConfig.Power.ARC_LAMP_POWER * brightness / 100.0;
    }

    /** Opslaan, en je eigen spel (en dat van iedereen die de lamp ziet) het nieuwe laten weten. */
    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ================================================================ Server

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean lit = state.getValue(ArcLampBlock.LIT);
        int lightLevel = brightness();
        double perTick = powerPerTick(lightLevel);
        int keepOn = Math.max(1, (int) Math.ceil(perTick));
        int start = Math.max(1, (int) Math.ceil(perTick * StarkConfig.Power.ARC_LAMP_START_TICKS));
        boolean on = StarkGrid.available(level, pos) >= (lit ? keepOn : start);
        if (!on) {
            StarkGrid.missed(level, pos, perTick); // hij wil branden maar kan niet: tekort op het net
        }
        if (on) {
            owed += perTick;
            int pay = (int) owed;
            if (pay > 0) {
                StarkGrid.draw(level, pos, pay);
                owed -= pay;
            }
        }
        // Het blok zelf: aan of uit, en zijn eigen licht (zijn lichtniveau, hooguit 15).
        BlockState next = state.setValue(ArcLampBlock.LIT, on).setValue(ArcLampBlock.LIGHT, Math.min(15, lightLevel));
        if (next != state) {
            level.setBlock(pos, next, Block.UPDATE_ALL);
            if (on != lit) {
                // Het klikje van een koperen lamp, iets lager: deze is groter.
                level.playSound(null, pos, on ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
                        SoundSource.BLOCKS, 0.8f, 0.7f);
            }
        }
        // Zijn licht verder weg (boven de 15): ArcLampLight. Doet alleen iets als er iets veranderde.
        ArcLampLight.update(level, pos, on, ArcLampLight.reachOf(lightLevel));
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

    // ================================================================ Bewaren en doorsturen

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Brightness", brightness);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Een lamp van vóór 10 oktober heeft dit nog niet: die blijft op het maximum. (Een lamp van vóór
        // 13 oktober heeft ook nog "Glow": die gloed bestaat niet meer, dus dat slaan we over.)
        if (tag.contains("Brightness")) {
            brightness = tag.getInt("Brightness");
        }
    }

    /** Wat je eigen spel van de lamp krijgt: zijn helderheid (voor het scherm). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
