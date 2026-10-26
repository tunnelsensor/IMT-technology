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
 * Wat alle granaten (hoofdstuk 35) delen: een lont, en wat er gebeurt als die op is ({@link #explode}). De EMP- en
 * frag-granaten piepen ook (steeds sneller) en hebben een knipperend lampje; de smokebom en de flashbang niet (die doen
 * dat in het echt ook niet). Hoe ze vliegen verschilt: de bol stuitert en rolt ({@link RollingGrenade}), de halve bol
 * plakt vast ({@link StickyGrenade}).
 *
 * De lont telt in ticks van de granaat zelf ({@code tickCount}): {@code FUSE_END} is de tick waarop hij afgaat, -1 =
 * hij is (nog) niet op scherp gezet (een sticky die nog vliegt, of een smokebom die al rookt). Die gaat ook naar je
 * spel, voor het knipperen.
 */
public abstract class GrenadeEntity extends Projectile {
    private static final EntityDataAccessor<Integer> FUSE_END =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.INT);

    protected GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
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

    /** Haalt hem van scherp (hij gaat niet meer af). */
    protected void disarm() {
        entityData.set(FUSE_END, -1);
    }

    public boolean isArmed() {
        return entityData.get(FUSE_END) >= 0;
    }

    /** Hoeveel ticks nog tot hij afgaat (of een groot getal als hij niet op scherp staat). */
    public int ticksLeft() {
        return isArmed() ? entityData.get(FUSE_END) - tickCount : Integer.MAX_VALUE;
    }

    /** Piept hij, en heeft hij een lampje dat knippert? (De EMP- en frag-granaten.) */
    public boolean beeps() {
        return true;
    }

    /** Om de hoeveel ticks hij piept: eerst rustig, op het laatst heel snel. */
    private static int beepInterval(int left) {
        return left > 30 ? 10 : left > 14 ? 5 : 2;
    }

    /** Is het lampje nu aan? (In je spel, voor het tekenen.) Het flitst kort op bij elk piepje. */
    public boolean lightOn() {
        if (!beeps()) {
            return false;
        }
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
        if (!(level() instanceof ServerLevel level) || !isAlive() || !isArmed()) {
            return;
        }
        int left = ticksLeft();
        if (left <= 0) {
            explode(level);
            return;
        }
        if (beeps() && left % beepInterval(left) == 0) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.EMP_GRENADE_BEEP.get(), SoundSource.PLAYERS, 0.7f,
                    left <= 14 ? 1.25f : 1.0f);
        }
    }

    /** Eén tick vliegen, stuiteren of vastzitten (op de server én in je spel, zodat het soepel loopt). */
    protected abstract void fly();

    /** De lont is op: wat hij dan doet (een EMP-puls, rook, een flits). */
    protected abstract void explode(ServerLevel level);

    /** Waar het effect vandaan komt (het midden van de granaat). */
    protected Vec3 blastCenter() {
        return getBoundingBox().getCenter();
    }

    /** Waar de klap voor blokken zit (tegen het blok waar hij op ligt of plakt). */
    protected Vec3 explosionCenter() {
        return getBoundingBox().getCenter();
    }

    /** Een EMP-granaat gaat af: alleen een EMP-puls (jouw keuze: de EMP doet zelf geen schade aan de map). Daarna is hij weg. */
    protected void empPulse(ServerLevel level) {
        Vec3 pulse = blastCenter();
        discard();
        Emp.blast(level, pulse, getOwner());
    }

    /**
     * Een frag-granaat gaat af (jouw wens: "een extra bom die geen EMP is maar wel schade aan de map doet", "zoals de C4
     * maar net iets minder sterk"): een klap met breekpunten, met de sterktes uit [weapons] (zie breach/Blast). Daarna is
     * hij weg.
     */
    protected void fragBlast(ServerLevel level) {
        Vec3 bang = explosionCenter();
        discard();
        com.senne.imttech.breach.Blast.frag(level, bang, net.minecraft.core.BlockPos.containing(bang), this, false, "Frag grenade");
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
