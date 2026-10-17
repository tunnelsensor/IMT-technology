package com.senne.imttech.c4;

import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.registry.ModSounds;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van een lading C4: wanneer hij afgaat, wie hem plakte, en (voor de remote C4) bij
 * welke detonator hij hoort.
 *
 * Een C4 (Timer) gaat lopen zodra je hem plakt: {@link #explodeAt} is het moment (speltijd) waarop hij
 * ontploft. Hij piept elke seconde, de laatste vijf seconden twee keer zo vaak, en de laatste twee
 * seconden snel achter elkaar. Je spel krijgt {@link #explodeAt} mee (zie getUpdateTag), zodat het
 * schermpje kan aftellen (zie client/c4/C4Renderer).
 *
 * Een remote C4 heeft geen tijd ({@link #explodeAt} = -1): die wacht op zijn detonator.
 */
public class C4BlockEntity extends BlockEntity {
    /** Wanneer hij ontploft (speltijd), of -1: hij wacht (een remote C4 zonder signaal). */
    private long explodeAt = -1;
    /** Hoe lang de timer in totaal liep (ticks), voor het schermpje. */
    private int fuse;
    /** Wie hem plakte (krijgt het te horen als hij onschadelijk gemaakt wordt). */
    private @Nullable UUID placer;
    private String placerName = "";
    /** Bij welke detonator hij hoort (alleen een remote C4). */
    private @Nullable UUID detonator;
    /** Heeft I.M.T de eigenaar van de zone al gewaarschuwd? (Zie hoofdstuk 10, stap 7.) */
    private boolean alerted;

    public C4BlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.C4.get(), pos, state);
    }

    public C4Block.Kind kind() {
        return getBlockState().getBlock() instanceof C4Block block ? block.kind() : C4Block.Kind.TIMED;
    }

    public long explodeAt() {
        return explodeAt;
    }

    public int fuse() {
        return fuse;
    }

    public @Nullable UUID placer() {
        return placer;
    }

    public String placerName() {
        return placerName;
    }

    public @Nullable UUID detonator() {
        return detonator;
    }

    public boolean alerted() {
        return alerted;
    }

    public void setAlerted() {
        alerted = true;
        setChanged();
    }

    /** Wie hem plakte. */
    public void placedBy(@Nullable Player player) {
        if (player != null) {
            placer = player.getUUID();
            placerName = player.getGameProfile().getName();
            setChanged();
        }
    }

    /** Hoort bij deze detonator (een remote C4). */
    public void link(@Nullable UUID detonatorId) {
        detonator = detonatorId;
        setChanged();
        sync();
    }

    /**
     * Gaat over {@code ticks} ticks af. Liep hij al en gaat hij eerder af, dan blijft dat zo (zo maakt
     * een kettingreactie een timer nooit langer).
     */
    public void detonateIn(int ticks) {
        if (level == null) {
            return;
        }
        long at = level.getGameTime() + Math.max(1, ticks);
        if (explodeAt >= 0 && explodeAt <= at) {
            return;
        }
        explodeAt = at;
        fuse = Math.max(1, ticks);
        setChanged();
        sync();
    }

    /** Zoveel ticks nog tot hij afgaat (-1 als hij wacht). */
    public long remaining(long now) {
        return explodeAt < 0 ? -1 : Math.max(0, explodeAt - now);
    }

    // ================================================================ Elke tick

    void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (explodeAt < 0) {
            return;
        }
        long left = explodeAt - level.getGameTime();
        if (left <= 0) {
            C4Blast.detonate(level, pos, state);
            return;
        }
        // Piepen: elke seconde, de laatste 5 seconden elke halve, de laatste 2 seconden snel.
        int every = left > 100 ? 20 : left > 40 ? 10 : 4;
        if (left % every == 0 && fuse > 10) {
            float pitch = left > 100 ? 1.0f : left > 40 ? 1.15f : 1.35f;
            level.playSound(null, pos, ModSounds.C4_BEEP.get(), SoundSource.BLOCKS, 0.9f, pitch);
        }
    }

    // ================================================================ Naar je spel

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("ExplodeAt", explodeAt);
        tag.putInt("Fuse", fuse);
        tag.putBoolean("Linked", detonator != null);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Alleen voor je spel: hoort hij bij een detonator? (Het lampje van een remote C4.) */
    private boolean linkedOnClient;

    public boolean linked() {
        return detonator != null || linkedOnClient;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        explodeAt = tag.getLong("ExplodeAt");
        fuse = tag.getInt("Fuse");
        linkedOnClient = tag.getBoolean("Linked");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    // ================================================================ Opslaan

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("ExplodeAt", explodeAt);
        tag.putInt("Fuse", fuse);
        if (placer != null) {
            tag.putUUID("Placer", placer);
        }
        tag.putString("PlacerName", placerName);
        if (detonator != null) {
            tag.putUUID("Detonator", detonator);
        }
        tag.putBoolean("Alerted", alerted);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        explodeAt = tag.contains("ExplodeAt") ? tag.getLong("ExplodeAt") : -1;
        fuse = tag.getInt("Fuse");
        placer = tag.hasUUID("Placer") ? tag.getUUID("Placer") : null;
        placerName = tag.getString("PlacerName");
        detonator = tag.hasUUID("Detonator") ? tag.getUUID("Detonator") : null;
        alerted = tag.getBoolean("Alerted");
    }
}
