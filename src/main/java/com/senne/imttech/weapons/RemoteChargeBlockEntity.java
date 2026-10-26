package com.senne.imttech.weapons;

import com.senne.imttech.breach.Blast;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van een Remote EMP of Remote Frag (hoofdstuk 35): wie hem plakte, bij welke trigger hij hoort, of de
 * zone al gewaarschuwd is (alleen de Remote Frag, zie breach/BlastAlarm), en (als iemand op de trigger drukte) over
 * hoeveel ticks hij afgaat. Daarna is hij weg (eenmalig).
 */
public class RemoteChargeBlockEntity extends BlockEntity {
    private @Nullable UUID owner;
    private String ownerName = "";
    private @Nullable UUID trigger;
    /** Over hoeveel ticks hij afgaat, of -1 = hij wacht. */
    private int fuse = -1;
    /** Heeft I.M.T de eigenaar van de zone gewaarschuwd? (Dan gaat de markering weg als hij afgaat.) */
    private boolean alerted;

    public RemoteChargeBlockEntity(BlockPos pos, BlockState state) {
        super(type(state), pos, state);
    }

    /** Elke soort heeft zijn eigen soort binnenkant (zo blijven Remote EMP's van vroeger gewoon werken). */
    private static BlockEntityType<?> type(BlockState state) {
        return kind(state) == RemoteChargeBlock.Kind.FRAG ? ModBlockEntities.REMOTE_FRAG.get() : ModBlockEntities.REMOTE_EMP.get();
    }

    private static RemoteChargeBlock.Kind kind(BlockState state) {
        return state.getBlock() instanceof RemoteChargeBlock block ? block.kind() : RemoteChargeBlock.Kind.EMP;
    }

    public RemoteChargeBlock.Kind kind() {
        return kind(getBlockState());
    }

    public void placedBy(@Nullable Player player) {
        if (player != null) {
            owner = player.getUUID();
            ownerName = player.getGameProfile().getName();
            setChanged();
        }
    }

    /** Wie hem plakte (of null). */
    public @Nullable UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    /** De trigger waar hij bij hoort, of null. */
    public @Nullable UUID trigger() {
        return trigger;
    }

    public void link(UUID trigger) {
        this.trigger = trigger;
        setChanged();
    }

    public boolean alerted() {
        return alerted;
    }

    public void setAlerted() {
        alerted = true;
        setChanged();
    }

    /** Wacht hij nog niet meer (gaat hij straks af)? */
    public boolean goingOff() {
        return fuse >= 0;
    }

    /** Over {@code ticks} afgaan (de trigger laat ze kort na elkaar afgaan). Gaat hij al eerder af, dan blijft dat zo. */
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

    /**
     * Afgaan, en dan is hij weg. De Remote EMP: een EMP-puls vanaf zijn voorkant. De Remote Frag: een klap zoals een
     * Sticky Frag, met het midden op het blok waar hij tegenaan plakt (zo blaast hij dat blok eruit). Geeft hoeveel
     * blokken er kapot gingen (een EMP: geen).
     */
    public int goOff(ServerLevel level, BlockPos pos, BlockState state) {
        Direction out = RemoteChargeBlock.outward(state);
        Vec3 normal = Vec3.atLowerCornerOf(out.getNormal());
        level.removeBlock(pos, false);
        if (kind(state) == RemoteChargeBlock.Kind.FRAG) {
            return Blast.frag(level, Vec3.atCenterOf(pos).subtract(normal.scale(0.45)), pos, null, alerted, "Remote Frag");
        }
        Emp.blast(level, Vec3.atCenterOf(pos).subtract(normal.scale(0.3)), ImtConfig.Weapons.EMP_RADIUS,
                Math.max(1, Math.round(ImtConfig.Weapons.EMP_SECONDS * 20)), owner);
        return 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        if (trigger != null) {
            tag.putUUID("Trigger", trigger);
        }
        tag.putInt("Fuse", fuse);
        tag.putBoolean("Alerted", alerted);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        trigger = tag.hasUUID("Trigger") ? tag.getUUID("Trigger") : null;
        fuse = tag.contains("Fuse") ? tag.getInt("Fuse") : -1;
        alerted = tag.getBoolean("Alerted");
    }
}
