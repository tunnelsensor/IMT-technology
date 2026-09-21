package com.senne.starktech.raid;

import com.senne.starktech.network.BeamEffectPayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.registry.ModDamageTypes;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Een vijandige drone: niet van jou, en hij komt voor jou.
 *
 * Hij werkt als de drones van E.D.I.T.H. - hij duikt uit de lucht, hangt in de lucht en schiet
 * lasers - maar hij hoort bij niemand en kiest zelf zijn doelwit. Zit je achter een muur, dan
 * breekt hij die af; dat is wat een raid gevaarlijk maakt. Dat laatste kun je uitzetten met de
 * gamerule {@code starktechRaidGriefing}.
 *
 * Hij wordt wél opgeslagen (in tegenstelling tot je eigen drones): een raid die begint moet je
 * ook afmaken, ook als je even uitlogt.
 */
public class RaidDroneEntity extends Monster {
    /** Zolang dit niet NaN is, duikt hij nog naar beneden. */
    private double diveTargetY = Double.NaN;
    private int fireCooldown;
    /** Wanneer hij weer een blok mag stukmaken. */
    private int breakCooldown;

    public RaidDroneEntity(EntityType<? extends RaidDroneEntity> type, Level level) {
        super(type, level);
        this.moveControl = new RaidMoveControl(this);
        this.setNoGravity(true);
        this.xpReward = RaidBalance.XP_REWARD;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, RaidBalance.HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.6)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_DAMAGE, RaidBalance.LASER_DAMAGE);
    }

    /** Laat hem uit de lucht naar beneden duiken, tot vlak boven deze hoogte. */
    public void startDive(double targetY) {
        this.diveTargetY = targetY;
        this.fireCooldown = Math.max(this.fireCooldown, 20);
    }

    public boolean isDiving() {
        return !Double.isNaN(diveTargetY);
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (fireCooldown > 0) {
            fireCooldown--;
        }
        if (breakCooldown > 0) {
            breakCooldown--;
        }
        if (isDiving()) {
            dive(level);
            return;
        }

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || distanceToSqr(target) > RaidBalance.GIVE_UP_RANGE * RaidBalance.GIVE_UP_RANGE) {
            target = level.getNearestPlayer(this, RaidBalance.FIND_RANGE);
            setTarget(target instanceof Player player && !player.isCreative() && !player.isSpectator() ? target : null);
        }
        if (target == null) {
            // Niemand in de buurt: rustig blijven hangen op ooghoogte.
            moveControl.setWantedPosition(getX(), getY(), getZ(), 0.2);
            return;
        }
        chase(level, target);
    }

    /**
     * Blijft op schietafstand hangen en vuurt zodra hij vrij zicht heeft.
     *
     * Kan hij je niet zien, dan komt hij recht op je af en breekt hij zich een weg naar binnen.
     * (Eerst bleef hij ook dan op zes blokken om je heen cirkelen, terwijl hij maar zes blokken
     * ver kan slopen: wie zich verstopte, werd nooit gevonden.)
     */
    private void chase(ServerLevel level, LivingEntity target) {
        Vec3 eye = target.getEyePosition();
        // Iets boven en naast zijn doelwit blijven hangen, niet er bovenop.
        Vec3 spot = eye.add(0, RaidBalance.HOVER_ABOVE, 0);
        double distance = Math.sqrt(distanceToSqr(target));
        boolean canSee = getSensing().hasLineOfSight(target);
        if (!canSee) {
            moveControl.setWantedPosition(eye.x, eye.y + 1.0, eye.z, 1.0);
        } else if (distance > RaidBalance.ATTACK_RANGE) {
            moveControl.setWantedPosition(spot.x, spot.y, spot.z, 1.0);
        } else {
            // Binnen bereik: langzaam om hem heen cirkelen, zodat hij lastig te raken is.
            double angle = tickCount * 0.04;
            moveControl.setWantedPosition(
                    eye.x + Math.cos(angle) * RaidBalance.CIRCLE_RADIUS,
                    spot.y,
                    eye.z + Math.sin(angle) * RaidBalance.CIRCLE_RADIUS, 0.8);
        }
        lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, eye);

        if (canSee && distance <= RaidBalance.ATTACK_RANGE && fireCooldown <= 0) {
            fireLaser(level, target);
            fireCooldown = RaidBalance.FIRE_INTERVAL;
        } else if (!canSee && breakCooldown <= 0) {
            // Verstopt achter een muur? Dan gaat de muur eraan.
            breakTowards(level, target);
            breakCooldown = RaidBalance.BREAK_INTERVAL;
        }
    }

    private void fireLaser(ServerLevel level, LivingEntity target) {
        Vec3 from = position().add(0, getBbHeight() * 0.3, 0);
        Vec3 to = target.getBoundingBox().getCenter();
        DamageSource source = ModDamageTypes.droneLaser(level, this, this);
        target.hurt(source, RaidBalance.LASER_DAMAGE);
        playSound(ModSounds.DRONE_LASER.get(), 0.8f, 1.1f + random.nextFloat() * 0.2f);
        ModNetwork.sendNear(level, position(), 64, new BeamEffectPayload(from, to, BeamEffectPayload.Kind.DRONE_LASER));
    }

    /**
     * Maakt één blok kapot op de lijn naar zijn doelwit.
     *
     * Eén blok per keer, en alleen iets wat je ook zelf zou kunnen weghalen: bedrock en andere
     * onverwoestbare blokken laat hij staan. Staat de gamerule uit, dan gebeurt er niets.
     */
    private void breakTowards(ServerLevel level, LivingEntity target) {
        if (!level.getGameRules().getBoolean(ModGameRules.RAID_GRIEFING)) {
            return;
        }
        Vec3 from = position();
        Vec3 direction = target.getEyePosition().subtract(from).normalize();
        for (double step = 1.0; step <= RaidBalance.BREAK_REACH; step += 0.5) {
            BlockPos pos = BlockPos.containing(from.add(direction.scale(step)));
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.liquid()) {
                continue;
            }
            if (state.getDestroySpeed(level, pos) < 0) {
                return; // onverwoestbaar (bedrock): hier houdt het op
            }
            level.destroyBlock(pos, true, this);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    8, 0.3, 0.3, 0.3, 0.1);
            ModNetwork.sendNear(level, position(), 64,
                    new BeamEffectPayload(from, Vec3.atCenterOf(pos), BeamEffectPayload.Kind.DRONE_LASER));
            return;
        }
    }

    /** Valt met hoge snelheid naar beneden en vangt vlak boven zijn doel op. */
    private void dive(ServerLevel level) {
        double left = getY() - diveTargetY;
        if (left <= 3.0 || verticalCollision) {
            diveTargetY = Double.NaN;
            setDeltaMovement(0, 0, 0);
            return;
        }
        setDeltaMovement(0, -Math.min(RaidBalance.DIVE_SPEED, left), 0);
        hasImpulse = true;
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.4, getZ(), 2, 0.05, 0.1, 0.05, 0.01);
    }

    // ---------------------------------------------------------------- Gedrag als wezen

    /** Raiddrones doen elkaar geen pijn. */
    @Override
    public boolean hurt(DamageSource source, float damage) {
        if (source.getEntity() instanceof RaidDroneEntity) {
            return false;
        }
        return super.hurt(source, damage);
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.DRONE_HURT.get();
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return ModSounds.DRONE_DESTROYED.get();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false; // een raid verdwijnt niet vanzelf
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Fire", fireCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fireCooldown = tag.getInt("Fire");
    }

    /** Maakt een raiddrone hoog boven een plek, klaar om te duiken. */
    public static @Nullable RaidDroneEntity spawn(ServerLevel level, Vec3 above, double targetY) {
        RaidDroneEntity drone = ModEntities.RAID_DRONE.get().create(level);
        if (drone == null) {
            return null;
        }
        drone.moveTo(above.x, above.y, above.z, level.random.nextFloat() * 360.0f, 0.0f);
        drone.startDive(targetY);
        level.addFreshEntity(drone);
        return drone;
    }

    /**
     * Vliegen zonder paden te zoeken: hij stuurt zichzelf recht op zijn doel af.
     * Dezelfde aanpak als bij de drones van E.D.I.T.H.
     */
    private static class RaidMoveControl extends net.minecraft.world.entity.ai.control.MoveControl {
        private final RaidDroneEntity drone;

        RaidMoveControl(RaidDroneEntity drone) {
            super(drone);
            this.drone = drone;
        }

        @Override
        public void tick() {
            if (operation != Operation.MOVE_TO) {
                return;
            }
            operation = Operation.WAIT;
            Vec3 delta = new Vec3(wantedX - drone.getX(), wantedY - drone.getY(), wantedZ - drone.getZ());
            double length = delta.length();
            if (length < 0.3) {
                drone.setDeltaMovement(drone.getDeltaMovement().scale(0.5));
                return;
            }
            // Alleen de snelheid bijsturen; het verplaatsen doet de drone zelf al.
            double speed = speedModifier * RaidBalance.ACCELERATION * Math.min(1.0, length / 2.0);
            drone.setDeltaMovement(drone.getDeltaMovement().add(delta.scale(speed / length)));
        }
    }

}
