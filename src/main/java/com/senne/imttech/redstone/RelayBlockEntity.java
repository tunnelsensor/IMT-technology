package com.senne.imttech.redstone;

import com.senne.imttech.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Wat een I.M.T-relais onthoudt: van wie het is (13 oktober: "zorg dat de relay die je zelf plaatst,
 * jij de enige bent die die kan bewerken").
 *
 * Wie het neerzet, is de eigenaar. Alleen de eigenaar kan het in zijn redstone-lijst zetten, een naam en
 * tags geven, omkeren en schakelen (zie ImtRedstone.otherOwner). Een relais van vóór 13 oktober heeft
 * nog geen eigenaar: dat wordt van wie het als eerste gebruikt (meestal wie het al in zijn lijst had).
 *
 * Omdat het relais nu een blok-entiteit heeft, kan een zuiger het niet meer wegduwen (zoals een kist). Zo
 * kan niemand het verschuiven en de eigenaar kwijtraken.
 *
 * Hoofdstuk 112: het onthoudt ook of het een netwerk nodig heeft ({@link #networked}). Dat is zo voor elk relais dat een
 * speler neerzet sinds de Router er is. Een relais van daarvoor (of uit een gebouw van de mod) heeft dat gegeven niet, en
 * werkt zoals vroeger: jouw regel bij hoofdstuk 106 was "wat er al is, blijft; nieuwe dingen moeten dit toepassen".
 */
public class RelayBlockEntity extends BlockEntity {
    private @Nullable UUID owner;
    private String ownerName = "";
    /** Heeft dit relais een netwerk nodig (neergezet sinds hoofdstuk 112)? */
    private boolean networked;
    /**
     * Hing het aan een netwerk, de laatste keer dat we dat zeker wisten? Schakel je het van ver, dan is het stuk wereld
     * rond zijn kabel of zijn router vaak niet geladen, en kunnen we het op dat moment niet nakijken: dan telt dit.
     */
    private boolean linked;

    public RelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RELAY.get(), pos, state);
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    /** Heeft dit relais een netwerk nodig om van op afstand te schakelen (neergezet sinds hoofdstuk 112)? */
    public boolean networked() {
        return networked;
    }

    public void setNetworked(boolean networked) {
        this.networked = networked;
        setChanged();
    }

    /** Hing het aan een netwerk, de laatste keer dat we dat zeker wisten? */
    public boolean linked() {
        return linked;
    }

    /** We weten het nu zeker: het hangt wel of niet aan een netwerk. */
    public void rememberLinked(boolean linked) {
        if (this.linked != linked) {
            this.linked = linked;
            setChanged();
        }
    }

    /**
     * Om de vijf seconden (alleen een relais dat een netwerk nodig heeft): kijk of het nog aan een netwerk hangt, en
     * onthoud dat. Zo klopt wat het weet ook als jij er daarna ver vandaan staat.
     */
    public void serverTick(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        if ((level.getGameTime() + pos.hashCode()) % 100 == 0) {
            ImtRedstone.offline(level, pos);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        if (networked) {
            tag.putBoolean("Networked", true);
        }
        if (linked) {
            tag.putBoolean("Linked", true);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        networked = tag.getBoolean("Networked"); // een relais van vóór hoofdstuk 112 heeft dit niet: dan false
        linked = tag.getBoolean("Linked");
    }
}
