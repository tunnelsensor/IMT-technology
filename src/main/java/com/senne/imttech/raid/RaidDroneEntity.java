package com.senne.imttech.raid;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.BeamEffectPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.registry.ModDamageTypes;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.shield.ImtDomes;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import com.senne.imttech.drone.SkyDive;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Een vijandige drone: niet van jou, en hij komt voor jou.
 *
 * Hij werkt als de drones van I.M.T - hij duikt uit de lucht, hangt in de lucht en schiet
 * lasers - maar hij hoort bij niemand en kiest zelf zijn doelwit. Zit je achter een muur, dan
 * breekt hij die af; dat is wat een raid gevaarlijk maakt. Dat laatste kun je uitzetten met de
 * gamerule {@code imttechRaidGriefing}.
 *
 * Hij wordt wél opgeslagen (in tegenstelling tot je eigen drones): een raid die begint moet je
 * ook afmaken, ook als je even uitlogt.
 *
 * Hoofdstuk 54 (jouw notitie: "als je een protected zone hebt moeten ze die eerst opblazen of emps gebruiken om je
 * defences weg te doen"): een blok van een zone breekt hij niet, daar gooit hij een <b>sticky frag</b> op (en het alarm
 * van de zone gaat af). Zit zijn doelwit in een zone, dan gooit hij ook <b>EMP-granaten</b> op de torentjes en
 * laserzenders van die kern, zodra hij er vrij zicht op heeft. En hij onthoudt wie hij kwam halen: de raid begint
 * buiten de basis (zie ImtRaids), verder weg dan hij gewoon kijkt.
 */
public class RaidDroneEntity extends Monster implements com.senne.imttech.weapons.EmpTarget {
    /** De duik uit de lucht (in een boog naar zijn plek), of null als hij niet (meer) duikt. */
    private @Nullable SkyDive dive;
    private int fireCooldown;
    /** Wanneer hij weer een blok mag stukmaken. */
    private int breakCooldown;
    /** Hoofdstuk 54: wie hij kwam halen (null als hij zomaar ergens kwam). */
    private @Nullable UUID raidTarget;
    /** Wanneer hij weer een sticky frag op een zone mag gooien, en wanneer weer een EMP op de verdediging. */
    private int breachCooldown;
    private int empCooldown;
    /** Tot zo ver blijft hij zoeken naar wie hij kwam halen (een basis kan groot zijn). */
    private static final double RAID_FOLLOW_RANGE = 600.0;

    public RaidDroneEntity(EntityType<? extends RaidDroneEntity> type, Level level) {
        super(type, level);
        this.moveControl = new RaidMoveControl(this);
        this.setNoGravity(true);
        this.xpReward = Math.round(ImtConfig.Raids.XP_REWARD);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, ImtConfig.Raids.HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.6)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_DAMAGE, ImtConfig.Raids.LASER_DAMAGE);
    }

    /** Laat hem uit de lucht in een boog naar {@code landing} duiken (zie SkyDive). */
    public void startDive(Vec3 landing) {
        this.dive = new SkyDive(position(), landing);
        this.fireCooldown = Math.max(this.fireCooldown, 20);
    }

    public boolean isDiving() {
        return dive != null;
    }

    /** Hoofdstuk 54: voor wie deze raid is. Die blijft hij zoeken, ook als hij buiten de basis begint. */
    public void setRaidTarget(ServerPlayer player) {
        this.raidTarget = player.getUUID();
    }

    public @Nullable UUID raidTargetId() {
        return raidTarget;
    }

    /** Wie hij kwam halen, als die nog in deze wereld is, niet in creative, en niet te ver weg; anders null. */
    private @Nullable Player raidTarget(ServerLevel level) {
        if (raidTarget == null) {
            return null;
        }
        Player wanted = level.getServer().getPlayerList().getPlayer(raidTarget);
        if (wanted == null || wanted.level() != level || wanted.isCreative() || wanted.isSpectator()
                || distanceToSqr(wanted) > RAID_FOLLOW_RANGE * RAID_FOLLOW_RANGE) {
            return null;
        }
        return wanted;
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        // Raak je hem, dan zie je dat ook: rook, vonken, en op het laatst brandende brokjes.
        com.senne.imttech.drone.DroneDamage.particles(this);
    }

    /** Door de rook van een smokebom (hoofdstuk 35) ziet hij niemand. */
    @Override
    public boolean hasLineOfSight(net.minecraft.world.entity.Entity entity) {
        return super.hasLineOfSight(entity)
                && !com.senne.imttech.weapons.Smoke.blocks(level(), getEyePosition(), entity.getEyePosition());
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
        if (breachCooldown > 0) {
            breachCooldown--;
        }
        if (empCooldown > 0) {
            empCooldown--;
        }
        // Uitgeschakeld door een EMP-granaat (hoofdstuk 35): naar de grond zakken en daar stil blijven liggen.
        if (com.senne.imttech.weapons.Emp.isJammed(this)) {
            setTarget(null);
            getNavigation().stop();
            setDeltaMovement(onGround() ? Vec3.ZERO : getDeltaMovement().multiply(0.7, 0.98, 0.7).add(0, -0.08, 0));
            return;
        }
        if (isDiving()) {
            dive(level);
            return;
        }

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || distanceToSqr(target) > ImtConfig.Raids.GIVE_UP_RANGE * ImtConfig.Raids.GIVE_UP_RANGE) {
            Player nearest = level.getNearestPlayer(this, ImtConfig.Raids.FIND_RANGE);
            target = nearest != null && !nearest.isCreative() && !nearest.isSpectator() ? nearest : raidTarget(level);
            setTarget(target);
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
        Vec3 spot = eye.add(0, ImtConfig.Raids.HOVER_ABOVE, 0);
        double distance = Math.sqrt(distanceToSqr(target));
        boolean canSee = getSensing().hasLineOfSight(target);
        if (!canSee) {
            moveControl.setWantedPosition(eye.x, eye.y + 1.0, eye.z, 1.0);
        } else if (distance > ImtConfig.Raids.ATTACK_RANGE) {
            moveControl.setWantedPosition(spot.x, spot.y, spot.z, 1.0);
        } else {
            // Binnen bereik: langzaam om hem heen cirkelen, zodat hij lastig te raken is.
            double angle = tickCount * 0.04;
            moveControl.setWantedPosition(
                    eye.x + Math.cos(angle) * ImtConfig.Raids.CIRCLE_RADIUS,
                    spot.y,
                    eye.z + Math.sin(angle) * ImtConfig.Raids.CIRCLE_RADIUS, 0.8);
        }
        lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, eye);

        // Hoofdstuk 54: zit zijn doelwit in een beschermde zone, dan eerst de verdediging weg: een EMP op het
        // dichtstbijzijnde torentje of de dichtstbijzijnde laserzender van die kern, om de EMP_INTERVAL ticks.
        if (empCooldown <= 0) {
            empCooldown = empDefences(level, target) ? Math.round(ImtConfig.Raids.EMP_INTERVAL) : 40;
        }

        if (canSee && distance <= ImtConfig.Raids.ATTACK_RANGE && fireCooldown <= 0) {
            fireLaser(level, target);
            fireCooldown = Math.round(ImtConfig.Raids.FIRE_INTERVAL);
        } else if (!canSee && breakCooldown <= 0) {
            // Verstopt achter een muur? Dan gaat de muur eraan.
            breakTowards(level, target);
            breakCooldown = Math.round(ImtConfig.Raids.BREAK_INTERVAL);
        }
    }

    private void fireLaser(ServerLevel level, LivingEntity target) {
        Vec3 from = position().add(0, getBbHeight() * 0.3, 0);
        Vec3 to = target.getBoundingBox().getCenter();
        // Staat zijn doelwit onder een schildkoepel? Dan stuit het schot daarop.
        Vec3 shield = ImtDomes.blockedAt(level, to, from, null, Math.round(ImtConfig.Dome.COST_LASER));
        if (shield != null) {
            playSound(ModSounds.DRONE_LASER.get(), 0.8f, 1.1f);
            ModNetwork.sendNear(level, position(), 64, new BeamEffectPayload(from, shield, BeamEffectPayload.Kind.DRONE_LASER));
            return;
        }
        DamageSource source = ModDamageTypes.droneLaser(level, this, this);
        target.hurt(source, ImtConfig.Raids.LASER_DAMAGE);
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
        for (double step = 1.0; step <= ImtConfig.Raids.BREAK_REACH; step += 0.5) {
            BlockPos pos = BlockPos.containing(from.add(direction.scale(step)));
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.liquid()) {
                continue;
            }
            if (state.getDestroySpeed(level, pos) < 0) {
                return; // onverwoestbaar (bedrock): hier houdt het op
            }
            if (com.senne.imttech.security.zone.Zones.activeAt(level, pos) != null) {
                // Een blok van een zone: daar komt alleen een frag door (hoofdstuk 10 en 35). Dus gooit hij er een
                // sticky frag op (hoofdstuk 54), om de BREACH_INTERVAL ticks; het alarm van de zone gaat af.
                if (breachCooldown <= 0) {
                    throwStickyFrag(level, pos);
                    breachCooldown = Math.round(ImtConfig.Raids.BREACH_INTERVAL);
                }
                return;
            }
            level.destroyBlock(pos, true, this);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    8, 0.3, 0.3, 0.3, 0.1);
            ModNetwork.sendNear(level, position(), 64,
                    new BeamEffectPayload(from, Vec3.atCenterOf(pos), BeamEffectPayload.Kind.DRONE_LASER));
            return;
        }
    }

    /** Een sticky frag tegen het zoneblok op {@code at} (hoofdstuk 54): die plakt en gaat dan af (zie StickyGrenade). */
    private void throwStickyFrag(ServerLevel level, BlockPos at) {
        com.senne.imttech.weapons.StickyGrenade grenade = new com.senne.imttech.weapons.StickyGrenade(level, this, true);
        grenade.setPos(getX(), getY() + getBbHeight() * 0.3, getZ());
        Vec3 d = Vec3.atCenterOf(at).subtract(grenade.position());
        grenade.shoot(d.x, d.y, d.z, 0.9f, 0.5f);
        level.addFreshEntity(grenade);
        playSound(net.minecraft.sounds.SoundEvents.SNOWBALL_THROW, 0.7f, 0.8f);
    }

    /**
     * Een EMP op de verdediging (hoofdstuk 54): het dichtstbijzijnde torentje of de dichtstbijzijnde laserzender van de
     * kern van de zone waar zijn doelwit in zit (of waar hij zelf bij staat), binnen DEVICE_REACH blokken, nog aan, in
     * het zicht, en niet zo dichtbij dat zijn eigen EMP hem raakt. Geeft true als hij er een gooide.
     */
    private boolean empDefences(ServerLevel level, LivingEntity target) {
        com.senne.imttech.security.zone.ZoneData.Zone zone =
                com.senne.imttech.security.zone.Zones.activeAt(level, target.blockPosition());
        if (zone == null) {
            zone = com.senne.imttech.security.zone.Zones.activeNear(level, blockPosition(), ImtConfig.Raids.DEVICE_REACH);
        }
        if (zone == null || !(level.getBlockEntity(zone.core().pos())
                instanceof com.senne.imttech.security.SecurityCoreBlockEntity core)) {
            return false;
        }
        com.senne.imttech.security.SecurityDevices.Found found = com.senne.imttech.security.SecurityDevices.scan(level, core);
        List<BlockPos> devices = new ArrayList<>(found.turrets());
        devices.addAll(found.emitters());
        Vec3 from = position().add(0, getBbHeight() * 0.3, 0);
        double reach = ImtConfig.Raids.DEVICE_REACH * ImtConfig.Raids.DEVICE_REACH;
        double safe = ImtConfig.Weapons.EMP_RADIUS + 2;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : devices) {
            double distance = pos.distToCenterSqr(from);
            if (distance > reach || distance < safe * safe || distance >= bestDistance
                    || com.senne.imttech.weapons.Emp.isOut(level, pos)) {
                continue;
            }
            Vec3 centre = Vec3.atCenterOf(pos);
            HitResult wall = level.clip(new ClipContext(from, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (wall.getType() != HitResult.Type.MISS && wall.getLocation().distanceToSqr(centre) > 2.25) {
                continue; // er zit iets tussen
            }
            best = pos;
            bestDistance = distance;
        }
        if (best == null) {
            return false;
        }
        com.senne.imttech.weapons.RollingGrenade grenade = com.senne.imttech.weapons.RollingGrenade.thrown(
                com.senne.imttech.weapons.RollingGrenade.Kind.EMP, level, this);
        Vec3 d = Vec3.atCenterOf(best).subtract(grenade.position());
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        grenade.shoot(d.x, d.y + flat * 0.15, d.z, (float) Mth.clamp(0.5 + flat * 0.04, 0.5, 1.2), 0.5f);
        level.addFreshEntity(grenade);
        playSound(net.minecraft.sounds.SoundEvents.SNOWBALL_THROW, 0.7f, 0.8f);
        return true;
    }

    /** Eén tick van de duik: naar de volgende plek op de boog, met een rookspoor erachter. */
    private void dive(ServerLevel level) {
        SkyDive current = dive;
        setDeltaMovement(current.next().subtract(position()));
        hasImpulse = true;
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.4, getZ(), 2, 0.05, 0.1, 0.05, 0.01);
        if (current.done() || horizontalCollision || verticalCollision) {
            dive = null;
            setDeltaMovement(0, 0, 0);
        }
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
        if (raidTarget != null) {
            tag.putUUID("RaidTarget", raidTarget);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fireCooldown = tag.getInt("Fire");
        raidTarget = tag.hasUUID("RaidTarget") ? tag.getUUID("RaidTarget") : null;
    }

    /**
     * Maakt een raiddrone op {@code start} (hoog in de lucht), die daarna in een boog naar
     * {@code landing} duikt. Zijn ze hetzelfde, dan hangt hij er meteen.
     */
    public static @Nullable RaidDroneEntity spawn(ServerLevel level, Vec3 start, Vec3 landing) {
        RaidDroneEntity drone = ModEntities.RAID_DRONE.get().create(level);
        if (drone == null) {
            return null;
        }
        drone.moveTo(start.x, start.y, start.z, level.random.nextFloat() * 360.0f, 0.0f);
        if (start.distanceToSqr(landing) > 1) {
            drone.startDive(landing);
        }
        level.addFreshEntity(drone);
        return drone;
    }

    /**
     * Vliegen zonder paden te zoeken: hij stuurt zichzelf recht op zijn doel af.
     * Dezelfde aanpak als bij de drones van I.M.T
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
            double speed = speedModifier * ImtConfig.Raids.ACCELERATION * Math.min(1.0, length / 2.0);
            drone.setDeltaMovement(drone.getDeltaMovement().add(delta.scale(speed / length)));
        }
    }

}
