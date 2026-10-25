package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * De granaten die je als bal gooit (hoofdstuk 35): ze stuiteren tegen muren, de vloer en wezens, en rollen op de grond
 * verder tot ze stil liggen. Zo kun je ze om een hoek gooien, of met sneak over de vloer laten rollen. Er zijn drie
 * soorten (welke, zegt het soort wezen, zie {@link #kind}):
 * <ul>
 *   <li>de <b>EMP Grenade</b> (jouw wens: "de bol die rolt zoals bij Star Wars detonators"): gaat 3 seconden na het
 *       gooien af met een EMP-puls (geen schade aan de map);</li>
 *   <li>de <b>Frag Grenade</b>, zijn kopie als explosief (jouw wens): dezelfde bol, maar een klap zoals C4, net iets
 *       minder sterk, en geen EMP;</li>
 *   <li>de <b>Smoke Grenade</b> (jouw wens: "maak voor de bal ook smokebom en flashbang"): sist en maakt een wolk rook
 *       (zie {@link Smoke}) waar robots, turrets en drones niet doorheen kijken. Grijs, of in de kleur van de kleurstof
 *       waarmee je hem maakte. Hij blijft liggen tot de rook op is;</li>
 *   <li>de <b>Flashbang</b>: een felle flits en een knal (zie {@link Flashbang}). Alleen spelers merken iets, zoals in
 *       CS.</li>
 * </ul>
 */
public class RollingGrenade extends GrenadeEntity {
    public enum Kind { EMP, FRAG, SMOKE, FLASH }

    /** De kleur van de rook (een DyeColor), of -1 = gewoon grijs. */
    private static final EntityDataAccessor<Integer> SMOKE_COLOR =
            SynchedEntityData.defineId(RollingGrenade.class, EntityDataSerializers.INT);
    /** Sinds welke tick hij rookt (-1 = nog niet). */
    private static final EntityDataAccessor<Integer> SMOKING_SINCE =
            SynchedEntityData.defineId(RollingGrenade.class, EntityDataSerializers.INT);

    /** Zo hard trekt de zwaartekracht (blokken per tick per tick), zoals bij een sneeuwbal. */
    private static final double GRAVITY = 0.04;
    /** Hoeveel vaart hij per tick houdt als hij over de grond rolt, en in de lucht. */
    private static final double ROLLING = 0.94;
    private static final double AIR = 0.99;
    /** Langzamer dan dit stuitert hij niet meer op de vloer, maar blijft hij liggen (en rolt hij). */
    private static final double MIN_BOUNCE = 0.12;
    /** De straal van de bol (blokken), om te weten hoe ver hij draait als hij rolt. */
    public static final float RADIUS = 3 / 16f;

    // Alleen in je spel: hoe de bol nu gedraaid is (door het rollen), en hoe de vorige tick.
    public final Quaternionf spin = new Quaternionf();
    public final Quaternionf spinO = new Quaternionf();

    public RollingGrenade(EntityType<? extends RollingGrenade> type, Level level) {
        super(type, level);
    }

    /** Een nieuwe granaat van deze soort in de hand van {@code thrower}, die meteen op scherp staat. */
    public static RollingGrenade thrown(Kind kind, Level level, LivingEntity thrower) {
        RollingGrenade grenade = new RollingGrenade(type(kind), level);
        grenade.setOwner(thrower);
        grenade.setPos(thrower.getX(), thrower.getEyeY() - 0.2, thrower.getZ());
        grenade.arm(Math.round(fuseSeconds(kind) * 20));
        return grenade;
    }

    public static EntityType<RollingGrenade> type(Kind kind) {
        return switch (kind) {
            case EMP -> ModEntities.ROLLING_EMP_GRENADE.get();
            case FRAG -> ModEntities.FRAG_GRENADE.get();
            case SMOKE -> ModEntities.SMOKE_GRENADE.get();
            case FLASH -> ModEntities.FLASHBANG.get();
        };
    }

    private static float fuseSeconds(Kind kind) {
        return switch (kind) {
            case EMP, FRAG -> ImtConfig.Weapons.ROLLING_FUSE_SECONDS;
            case SMOKE -> ImtConfig.Weapons.SMOKE_FUSE_SECONDS;
            case FLASH -> ImtConfig.Weapons.FLASH_FUSE_SECONDS;
        };
    }

    /** Welke soort dit is (dat hangt af van het soort wezen). */
    public Kind kind() {
        EntityType<?> type = getType();
        return type == ModEntities.SMOKE_GRENADE.get() ? Kind.SMOKE : type == ModEntities.FLASHBANG.get() ? Kind.FLASH
                : type == ModEntities.FRAG_GRENADE.get() ? Kind.FRAG : Kind.EMP;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SMOKE_COLOR, -1);
        builder.define(SMOKING_SINCE, -1);
    }

    @Override
    public boolean beeps() {
        return kind() == Kind.EMP || kind() == Kind.FRAG;
    }

    // ================================================================ De rook

    /** De kleur van de rook, of null = grijs. */
    public @Nullable DyeColor smokeColor() {
        int id = entityData.get(SMOKE_COLOR);
        return id < 0 ? null : DyeColor.byId(id);
    }

    public void setSmokeColor(@Nullable DyeColor color) {
        entityData.set(SMOKE_COLOR, color == null ? -1 : color.getId());
    }

    /** Rookt hij nu? */
    public boolean isSmoking() {
        return entityData.get(SMOKING_SINCE) >= 0;
    }

    /** Hoe lang hij al rookt (ticks), of -1. */
    public int smokingFor() {
        int since = entityData.get(SMOKING_SINCE);
        return since < 0 ? -1 : tickCount - since;
    }

    /** Hoe lang (ticks) de rook duurt. */
    public static int smokeTicks() {
        return Math.max(1, Math.round(ImtConfig.Weapons.SMOKE_SECONDS * 20));
    }

    // ================================================================ Afgaan

    @Override
    protected void explode(ServerLevel level) {
        switch (kind()) {
            case EMP -> empPulse(level);
            case FRAG -> fragBlast(level);
            case FLASH -> {
                Vec3 at = blastCenter();
                discard();
                Flashbang.bang(level, at, getOwner());
            }
            case SMOKE -> {
                // Hij blijft liggen en rookt; als de rook op is, verdwijnt hij (zie tick).
                disarm();
                entityData.set(SMOKING_SINCE, tickCount);
                Smoke.add(level, position().add(0, 0.5, 0), smokeTicks());
                level.playSound(null, getX(), getY(), getZ(), ModSounds.SMOKE_GRENADE_HISS.get(), SoundSource.PLAYERS,
                        1.0f, 1.0f);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        int smoking = smokingFor();
        if (smoking < 0 || !isAlive()) {
            return;
        }
        if (level().isClientSide) {
            com.senne.imttech.client.weapons.SmokeView.puff(this, smoking);
        } else if (smoking >= smokeTicks()) {
            discard();
        } else if (smoking % 60 == 0 && smoking < smokeTicks() / 2) {
            // Het sissen herhalen, steeds zachter.
            level().playSound(null, getX(), getY(), getZ(), ModSounds.SMOKE_GRENADE_HISS.get(), SoundSource.PLAYERS,
                    1.0f - smoking / (float) smokeTicks(), 0.95f);
        }
    }

    // ================================================================ Vliegen en rollen

    @Override
    protected void fly() {
        Vec3 wanted = getDeltaMovement().add(0, -GRAVITY, 0);
        Vec3 before = position();
        // Eerst kijken of hij een wezen raakt (niet zijn gooier, die hij net loslaat).
        EntityHitResult hitEntity = ProjectileUtil.getEntityHitResult(level(), this, before, before.add(wanted),
                getBoundingBox().expandTowards(wanted).inflate(0.1), this::canHitEntity);
        if (hitEntity != null && wanted.length() > 0.15) {
            // Tegen iemand aan: hij kaatst terug, een stuk trager.
            wanted = wanted.scale(-0.3);
            bounceSound(wanted.length());
        }
        move(MoverType.SELF, wanted);
        Vec3 moved = position().subtract(before);
        double bounce = ImtConfig.Weapons.BOUNCE;
        double vx = wanted.x;
        double vy = wanted.y;
        double vz = wanted.z;
        boolean hit = false;
        // Is hij in een richting minder ver gekomen dan hij wilde? Dan zat daar een blok: kaats terug.
        if (Math.abs(moved.x - wanted.x) > 1.0E-4) {
            hit |= Math.abs(vx) > MIN_BOUNCE;
            vx = -vx * bounce;
        }
        if (Math.abs(moved.z - wanted.z) > 1.0E-4) {
            hit |= Math.abs(vz) > MIN_BOUNCE;
            vz = -vz * bounce;
        }
        if (Math.abs(moved.y - wanted.y) > 1.0E-4) {
            if (Math.abs(vy) > MIN_BOUNCE + GRAVITY) {
                hit = true;
                vy = -vy * bounce;
            } else {
                vy = 0; // te traag om te stuiteren: hij ligt (en rolt)
            }
        }
        if (hit) {
            bounceSound(Math.sqrt(vx * vx + vy * vy + vz * vz));
        }
        double keep = onGround() ? ROLLING : AIR;
        setDeltaMovement(vx * keep, vy * AIR, vz * keep);
        if (level().isClientSide) {
            roll(moved);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target instanceof LivingEntity && !target.isSpectator();
    }

    private void bounceSound(double speed) {
        if (!level().isClientSide && speed > 0.05) {
            level().playSound(null, getX(), getY(), getZ(), ModSounds.EMP_GRENADE_BOUNCE.get(), SoundSource.PLAYERS,
                    (float) Mth.clamp(speed * 2, 0.2, 0.8), 0.9f + random.nextFloat() * 0.2f);
        }
    }

    /** In je spel: de bol draait mee met hoe ver hij over de grond ging (om de as dwars op zijn richting). */
    private void roll(Vec3 moved) {
        spinO.set(spin);
        double distance = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
        if (distance < 1.0E-4) {
            return;
        }
        Vector3f axis = new Vector3f((float) moved.z, 0, (float) -moved.x).normalize();
        float angle = (float) (distance / RADIUS);
        spin.premul(new Quaternionf().rotationAxis(angle, axis));
    }

    // ================================================================ Opslaan

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SmokeColor", entityData.get(SMOKE_COLOR));
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SmokeColor")) {
            entityData.set(SMOKE_COLOR, tag.getInt("SmokeColor"));
        }
    }
}
