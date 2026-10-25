package com.senne.imttech.weapons;

import com.senne.imttech.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Wat de twee EMP-granaten (hoofdstuk 35) delen: een lont, piepjes die steeds sneller gaan, een knipperend lampje, en
 * de ontploffing (een EMP-puls, zie {@link Emp}). Hoe ze vliegen verschilt: de bol stuitert en rolt
 * ({@link RollingEmpGrenade}), de halve bol plakt vast ({@link StickyEmpGrenade}).
 *
 * De lont telt in ticks van de granaat zelf ({@code tickCount}): {@code FUSE_END} is de tick waarop hij afgaat, -1 =
 * hij is nog niet op scherp gezet (een sticky die nog vliegt). Die gaat ook naar je spel, voor het knipperen.
 */
public abstract class EmpGrenadeEntity extends Projectile {
    private static final EntityDataAccessor<Integer> FUSE_END =
            SynchedEntityData.defineId(EmpGrenadeEntity.class, EntityDataSerializers.INT);

    protected EmpGrenadeEntity(EntityType<? extends EmpGrenadeEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FUSE_END, -1);
    }

    // ================================================================ De lont

    /** Zet hem op scherp: over {@code ticks} gaat hij af. */
    public void arm(int ticks) {
        entityData.set(FUSE_END, tickCount + Math.max(1, ticks));
    }

    public boolean isArmed() {
        return entityData.get(FUSE_END) >= 0;
    }

    /** Hoeveel ticks nog tot hij afgaat (of een groot getal als hij nog niet op scherp staat). */
    public int ticksLeft() {
        return isArmed() ? entityData.get(FUSE_END) - tickCount : Integer.MAX_VALUE;
    }

    /** Om de hoeveel ticks hij piept: eerst rustig, op het laatst heel snel. */
    private static int beepInterval(int left) {
        return left > 30 ? 10 : left > 14 ? 5 : 2;
    }

    /** Is het lampje nu aan? (In je spel, voor het tekenen.) Het flitst kort op bij elk piepje. */
    public boolean lightOn() {
        if (!isArmed()) {
            return tickCount % 20 < 2; // nog niet op scherp: heel af en toe een knipoogje
        }
        int left = ticksLeft();
        int interval = beepInterval(left);
        return left % interval < Math.max(1, interval / 2);
    }

    @Override
    public void tick() {
        super.tick();
        fly();
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        if (!isArmed()) {
            return;
        }
        int left = ticksLeft();
        if (left <= 0) {
            explode(level);
            return;
        }
        if (left % beepInterval(left) == 0) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.EMP_GRENADE_BEEP.get(), SoundSource.PLAYERS, 0.7f,
                    left <= 14 ? 1.25f : 1.0f);
        }
    }

    /** Eén tick vliegen, stuiteren of vastzitten (op de server én in je spel, zodat het soepel loopt). */
    protected abstract void fly();

    /** Waar de puls vandaan komt (het midden van de granaat). */
    protected Vec3 blastCenter() {
        return getBoundingBox().getCenter();
    }

    /** Hij gaat af: een EMP-puls, en dan is hij weg. */
    protected void explode(ServerLevel level) {
        Emp.blast(level, blastCenter(), getOwner());
        discard();
    }

    // ================================================================ Opslaan

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FuseLeft", isArmed() ? ticksLeft() : -1);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int left = tag.getInt("FuseLeft");
        if (left >= 0) {
            arm(left);
        }
    }
}
