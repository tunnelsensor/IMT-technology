package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van een Remote EMP (hoofdstuk 35): wie hem plakte, bij welke EMP-trigger hij hoort, en (als iemand op
 * de trigger drukte) over hoeveel ticks hij afgaat. Hij gaat af met een gewone EMP-puls (jouw keuze: zoals een granaat,
 * alles binnen 5 blokken) en is daarna weg (eenmalig, zoals C4).
 */
public class RemoteEmpBlockEntity extends BlockEntity {
    private @Nullable UUID owner;
    private @Nullable UUID trigger;
    /** Over hoeveel ticks hij afgaat, of -1 = hij wacht. */
    private int fuse = -1;

    public RemoteEmpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REMOTE_EMP.get(), pos, state);
    }

    public void placedBy(@Nullable Player player) {
        owner = player == null ? null : player.getUUID();
        setChanged();
    }

    public @Nullable UUID owner() {
        return owner;
    }

    /** De EMP-trigger waar hij bij hoort, of null. */
    public @Nullable UUID trigger() {
        return trigger;
    }

    public void link(UUID trigger) {
        this.trigger = trigger;
        setChanged();
    }

    /** Over {@code ticks} afgaan (de trigger laat ze kort na elkaar afgaan). */
    public void goOffIn(int ticks) {
        if (fuse < 0 || ticks < fuse) {
            fuse = Math.max(1, ticks);
            setChanged();
        }
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (fuse < 0) {
            return;
        }
        if (--fuse <= 0) {
            goOff(level, pos, state);
        }
    }

    /** Afgaan: een EMP-puls vanaf de voorkant van de lading, en dan is hij weg. */
    public void goOff(ServerLevel level, BlockPos pos, BlockState state) {
        Direction out = RemoteEmpBlock.outward(state);
        Vec3 center = Vec3.atCenterOf(pos).subtract(Vec3.atLowerCornerOf(out.getNormal()).scale(0.3));
        level.removeBlock(pos, false);
        Emp.blast(level, center, ImtConfig.Weapons.EMP_RADIUS, Math.max(1, Math.round(ImtConfig.Weapons.EMP_SECONDS * 20)), owner);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        if (trigger != null) {
            tag.putUUID("Trigger", trigger);
        }
        tag.putInt("Fuse", fuse);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        trigger = tag.hasUUID("Trigger") ? tag.getUUID("Trigger") : null;
        fuse = tag.contains("Fuse") ? tag.getInt("Fuse") : -1;
    }
}
