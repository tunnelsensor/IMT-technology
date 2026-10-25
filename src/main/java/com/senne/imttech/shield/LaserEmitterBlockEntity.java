package com.senne.imttech.shield;

import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.security.BaseTrust;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Wat een laserzender onthoudt: van wie hij is (13 oktober: "enkel de persoon die het heeft geplaatst (of
 * trusted players in zone) kunnen hier op sneak klikken en dan een GUI openen").
 *
 * Wie hem neerzet, is de eigenaar. De kleur, het omkeren en liggend/staand zitten in het blok zelf (zie
 * LaserEmitterBlock), want die moet iedereen zien; alleen de eigenaar staat hier.
 *
 * Een zender van vóór 13 oktober, of een die in een gebouw stond (de Biebia's Industries Tower, de Biebia's Industries-bunker), heeft
 * nog geen eigenaar. Wie dan mag, staat in security/BaseTrust (dezelfde regel als bij de keycardlezer).
 *
 * Sinds hoofdstuk 33 onthoudt hij ook of zijn draden <b>doorgeknipt</b> zijn (met de Bypass Kit: dan blijft de hele
 * poort uit tot iemand hem herstelt), en tot wanneer hij <b>op slot</b> zit na een verkeerde knip. Of hij doorgeknipt
 * is, krijgt ook jouw spel te horen (zie getUpdateTag): dan vonkt hij, en toont zijn scherm de knop Repair wires.
 *
 * Omdat de zender nu een blok-entiteit heeft, kan een zuiger hem niet meer wegduwen (zoals een kist).
 */
public class LaserEmitterBlockEntity extends BlockEntity implements BaseTrust.Placed {
    private @Nullable UUID owner;
    private String ownerName = "";
    /** De draden zijn doorgeknipt (hoofdstuk 33): de hele poort blijft uit tot iemand ze herstelt. */
    private boolean cut;
    /** Tot wanneer (speltijd) niemand de draden van deze poort mag knippen, na een verkeerde knip. 0 = niet op slot. */
    private long lockedUntil;

    public LaserEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_EMITTER.get(), pos, state);
    }

    @Override
    public @Nullable UUID owner() {
        return owner;
    }

    @Override
    public String ownerName() {
        return ownerName;
    }

    @Override
    public void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    /** Zijn de draden van deze zender doorgeknipt? */
    public boolean isCut() {
        return cut;
    }

    /** Doorgeknipt of hersteld. Jouw spel hoort het meteen (de vonken en het scherm). */
    public void setCut(boolean cut) {
        if (this.cut == cut) {
            return;
        }
        this.cut = cut;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Tot wanneer (speltijd) deze zender op slot zit; 0 = niet. */
    public long lockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(long gameTime) {
        lockedUntil = gameTime;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        if (cut) {
            tag.putBoolean("Cut", true);
        }
        if (lockedUntil > 0) {
            tag.putLong("LockedUntil", lockedUntil);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        cut = tag.getBoolean("Cut");
        lockedUntil = tag.getLong("LockedUntil");
    }

    // ---------------------------------------------------------------- Wat jouw spel weet

    /** Jouw spel hoeft alleen te weten of de draden doorgeknipt zijn (niet van wie hij is of tot wanneer hij op slot zit). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Cut", cut);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        cut = tag.getBoolean("Cut");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }
}
