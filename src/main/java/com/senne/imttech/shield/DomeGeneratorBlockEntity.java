package com.senne.imttech.shield;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerGrid;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van de koepelgenerator: zijn instellingen (kleur, bereik, sterkte, aan/uit),
 * van wie hij is, hoeveel schildkracht hij nog heeft, en of de koepel op dit moment staat.
 *
 * Elke tick op de server (zie serverTick):
 * - elke halve seconde stroom betalen uit het net (DomeSettings.upkeep); geen stroom = geen koepel;
 * - staat hij, dan laadt de schildkracht bij en houdt hij projectielen en monsters tegen
 *   (zie ImtDomes);
 * - is de schildkracht op, dan valt hij DOWN_TICKS uit en komt daarna vanzelf terug.
 *
 * Wat het scherm en de tekening (DomeRenderer) moeten weten, sturen we naar de spelers in de
 * buurt: de instellingen, of hij staat, sinds wanneer (voor de animatie) en zijn schildkracht.
 */
public class DomeGeneratorBlockEntity extends BlockEntity {
    /** Waarom de koepel (niet) staat; het scherm laat het zien. */
    public enum Status {
        /** Hij staat. */
        UP,
        /** Uitgezet in het scherm (of door I.M.T). */
        OFF,
        /** Geen (of te weinig) stroom. */
        NO_POWER,
        /** Schildkracht op: even uitgevallen. */
        DEPLETED
    }

    private @Nullable UUID ownerId;
    private String ownerName = "";
    private ShieldColor colour = ShieldColor.CYAN;
    private int radiusIndex = Math.round(ImtConfig.Dome.DEFAULT_SIZE);
    private int strength = Math.round(ImtConfig.Dome.DEFAULT_STRENGTH);
    private boolean enabled = true;

    private boolean up;
    private Status status = Status.OFF;
    /** Het moment (game-tijd) dat hij opging of wegviel: daar rekent de animatie mee. */
    private long changedAt;
    private int shield;
    /** Hoeveel leveranciers er aan zijn net hangen (voor het scherm). */
    private int sources;

    // Alleen op de server:
    private boolean powered;
    private long downUntil;
    private int lastSyncedShield = -1;

    public DomeGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DOME_GENERATOR.get(), pos, state);
    }

    // ---------------------------------------------------------------- Wat iedereen mag lezen

    public @Nullable UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public ShieldColor colour() {
        return colour;
    }

    public int radiusIndex() {
        return radiusIndex;
    }

    public int radius() {
        return DomeSettings.radius(radiusIndex);
    }

    public int strength() {
        return strength;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean isUp() {
        return up;
    }

    public Status status() {
        return status;
    }

    public long changedAt() {
        return changedAt;
    }

    public int shield() {
        return shield;
    }

    public int maxShield() {
        return DomeSettings.maxShield(strength);
    }

    public int sources() {
        return sources;
    }

    /** Het midden van de koepel: het midden van de generator. */
    public Vec3 center() {
        return Vec3.atCenterOf(getBlockPos());
    }

    /** Ligt dit punt binnen de koepel (de bovenste helft van een bol om de generator)? */
    public boolean contains(Vec3 point) {
        Vec3 center = center();
        return point.y >= center.y - 1.5 && point.distanceToSqr(center) <= (double) radius() * radius();
    }

    // ---------------------------------------------------------------- Instellen (vanaf het scherm)

    public void setOwner(ServerPlayer player) {
        ownerId = player.getUUID();
        ownerName = player.getGameProfile().getName();
        sync();
    }

    public void setColour(ShieldColor newColour) {
        colour = newColour;
        sync();
    }

    public void setRadius(int index) {
        radiusIndex = DomeSettings.clampRadius(index);
        sync();
    }

    public void setStrength(int value) {
        strength = DomeSettings.clampStrength(value);
        shield = Math.min(shield, maxShield());
        sync();
    }

    public void setEnabled(boolean on) {
        enabled = on;
        sync();
    }

    // ---------------------------------------------------------------- Elke tick (server)

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        long now = level.getGameTime();
        // Elke halve seconde stroom betalen (zolang hij aan moet).
        if (enabled && Math.floorMod(now + pos.hashCode(), 10) == 0) {
            double cost = DomeSettings.upkeep(radiusIndex, strength) * 10;
            powered = PowerGrid.pay(level, pos, cost);
            sources = PowerGrid.sources(level, pos).size();
        }
        boolean depleted = now < downUntil;
        boolean wanted = enabled && powered && !depleted;
        if (wanted != up) {
            setUp(level, wanted, !enabled ? Status.OFF : depleted ? Status.DEPLETED : !powered ? Status.NO_POWER : Status.UP);
        } else if (!up) {
            // Staat hij niet, laat dan zien waarom (dat kan veranderen zonder dat hij opgaat).
            Status why = !enabled ? Status.OFF : depleted ? Status.DEPLETED : Status.NO_POWER;
            if (why != status) {
                status = why;
                sync();
            }
        }
        if (up) {
            shield = Math.min(maxShield(), shield + DomeSettings.recharge(strength));
            ImtDomes.defend(level, this, now);
        }
        // De schildkracht af en toe doorsturen voor het scherm (niet elke tick: dat is veel verkeer).
        if (now % 10 == 0 && shield != lastSyncedShield) {
            lastSyncedShield = shield;
            sync();
        }
    }

    /** De koepel gaat op of valt weg. */
    private void setUp(ServerLevel level, boolean nowUp, Status why) {
        up = nowUp;
        status = why;
        changedAt = level.getGameTime();
        BlockPos pos = getBlockPos();
        if (nowUp) {
            // Terug na uitvallen (of voor het eerst): met een deel van zijn schildkracht.
            if (shield <= 0) {
                shield = Math.round(maxShield() * ImtConfig.Dome.COMEBACK_SHIELD);
            }
            // Een koepel is groot: hetzelfde "vwoem" als een laserwand, maar dieper en verder te horen.
            level.playSound(null, pos, ModSounds.RAYSHIELD_ON.get(), SoundSource.BLOCKS, 2.0f, 0.7f);
        } else {
            level.playSound(null, pos, ModSounds.RAYSHIELD_OFF.get(), SoundSource.BLOCKS, 2.0f, 0.7f);
        }
        ImtDomes.setActive(level, pos, nowUp);
        BlockState state = getBlockState();
        if (state.hasProperty(DomeGeneratorBlock.POWERED) && state.getValue(DomeGeneratorBlock.POWERED) != nowUp) {
            level.setBlock(pos, state.setValue(DomeGeneratorBlock.POWERED, nowUp), Block.UPDATE_ALL);
        }
        sync();
    }

    /**
     * Een treffer tegenhouden kost schildkracht. Is die op, dan valt de koepel even uit (de
     * treffer zelf houdt hij nog wel tegen).
     */
    public void absorb(ServerLevel level, int cost) {
        shield -= cost;
        if (shield <= 0) {
            shield = 0;
            downUntil = level.getGameTime() + Math.round(ImtConfig.Dome.DOWN_TICKS);
            setUp(level, false, Status.DEPLETED);
        }
    }

    /** Na het laden van de wereld: stond hij, meld hem dan weer aan als actieve koepel. */
    @Override
    public void onLoad() {
        super.onLoad();
        // Stond hij, dan had hij stroom: zo valt hij niet even weg tot de eerste betaling.
        powered = up;
        if (level instanceof ServerLevel serverLevel && up) {
            ImtDomes.setActive(serverLevel, getBlockPos(), true);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            ImtDomes.setActive(serverLevel, getBlockPos(), false);
        }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---------------------------------------------------------------- Opslaan en versturen

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putString("OwnerName", ownerName);
        tag.putByte("Colour", (byte) colour.ordinal());
        tag.putByte("Radius", (byte) radiusIndex);
        tag.putByte("Strength", (byte) strength);
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("Up", up);
        tag.putByte("Status", (byte) status.ordinal());
        tag.putLong("ChangedAt", changedAt);
        tag.putInt("Shield", shield);
        tag.putByte("Sources", (byte) Math.min(sources, 127));
        tag.putLong("DownUntil", downUntil);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        ShieldColor[] colours = ShieldColor.values();
        colour = tag.contains("Colour") ? colours[Math.floorMod(tag.getByte("Colour"), colours.length)] : ShieldColor.CYAN;
        radiusIndex = tag.contains("Radius") ? DomeSettings.clampRadius(tag.getByte("Radius")) : Math.round(ImtConfig.Dome.DEFAULT_SIZE);
        strength = tag.contains("Strength") ? DomeSettings.clampStrength(tag.getByte("Strength")) : Math.round(ImtConfig.Dome.DEFAULT_STRENGTH);
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        up = tag.getBoolean("Up");
        Status[] statuses = Status.values();
        status = statuses[Math.floorMod(tag.getByte("Status"), statuses.length)];
        changedAt = tag.getLong("ChangedAt");
        shield = tag.getInt("Shield");
        sources = tag.getByte("Sources");
        downUntil = tag.getLong("DownUntil");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
