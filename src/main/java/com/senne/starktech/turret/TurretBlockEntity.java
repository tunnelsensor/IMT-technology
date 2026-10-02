package com.senne.starktech.turret;

import com.senne.starktech.energy.StarkGrid;
import com.senne.starktech.network.BeamEffectPayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.registry.ModDamageTypes;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.shield.DomeSettings;
import com.senne.starktech.shield.EdithDomes;
import com.senne.starktech.suit.MiniRocketEntity;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * De "binnenkant" van een torentje: van wie het is, hoeveel raketten erin zitten, en op wie
 * het nu richt.
 *
 * Op de SERVER zoekt hij om de halve seconde het dichtstbijzijnde doelwit dat hij kan zien
 * (zie TurretTargeting), houdt dat vast tot het neer is of uit zicht, en schiet hij: een laser (kost stroom van het net) en, als er munitie
 * in zit, af en toe een raket. Het Hydra-torentje heeft geen stroom nodig en geen raketten.
 *
 * In JOUW SPEL draait de kop naar dat doelwit (zie clientTick en TurretRenderer). De server
 * vertelt alleen WIE het doelwit is; het draaien zelf rekent je spel uit, zo loopt het soepel.
 */
public class TurretBlockEntity extends BlockEntity {
    private @Nullable UUID ownerId;
    private String ownerName = "";
    private int laserCooldown;
    private int rocketCooldown;
    /** Het wezen waar hij op richt (het nummer in de wereld), of -1. Gaat mee naar je spel. */
    private int targetId = -1;

    /** De raketten. Een trechter kan ze er ook in doen (zie StarkTech: de capability). */
    private final ItemStackHandler ammo = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ModItems.ROCKET_AMMO.get()) && kind().usesAmmo();
        }

        @Override
        public int getSlotLimit(int slot) {
            return TurretBalance.MAX_AMMO;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    // Alleen in je eigen spel: hoe de kop nu staat (in graden), en hoe hij de vorige tick stond.
    public float yaw;
    public float yawO;
    public float pitch;
    public float pitchO;

    public TurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TURRET.get(), pos, state);
    }

    public TurretKind kind() {
        return getBlockState().getBlock() instanceof TurretBlock turret ? turret.kind() : TurretKind.STARK;
    }

    public boolean isHydra() {
        return kind() == TurretKind.HYDRA;
    }

    public ItemStackHandler ammo() {
        return ammo;
    }

    public int ammoCount() {
        return ammo.getStackInSlot(0).getCount();
    }

    public @Nullable UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(ServerPlayer player) {
        ownerId = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    /** Staat hij aan? (Niet uitgezet met redstone of door E.D.I.T.H., en er is stroom.) */
    public static boolean isActive(BlockState state) {
        return state.getValue(TurretBlock.ENABLED) && !state.getValue(TurretBlock.POWERED)
                && state.getValue(TurretBlock.ONLINE);
    }

    /** Waar de loop zit: midden in de kop. */
    public Vec3 muzzle() {
        return Vec3.atCenterOf(worldPosition).add(0, 3 / 16.0, 0);
    }

    // ================================================================ Server

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        // Eén keer per seconde: is er stroom op het net? Dat zetten we in het blok.
        if ((level.getGameTime() + pos.hashCode()) % 20 == 0) {
            boolean online = StarkGrid.available(level, pos) >= TurretBalance.LASER_COST;
            if (online != state.getValue(TurretBlock.ONLINE)) {
                state = state.setValue(TurretBlock.ONLINE, online);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }
        }
        if (laserCooldown > 0) {
            laserCooldown--;
        }
        if (rocketCooldown > 0) {
            rocketCooldown--;
        }
        LivingEntity target = null;
        if (isActive(state)) {
            // Hij blijft op hetzelfde doelwit tot het neer is of uit zicht; anders verdeelt hij zijn
            // schoten over iedereen en draait de kop steeds heen en weer.
            target = level.getEntity(targetId) instanceof LivingEntity current && stillValid(level, current)
                    ? current : null;
            // Niemand? Om de halve seconde rondkijken (niet elke tick: dat is zwaar voor de server).
            if (target == null && (level.getGameTime() + pos.hashCode()) % TurretBalance.SCAN_INTERVAL == 0) {
                target = findTarget(level);
            }
        }
        setTarget(target);
        if (target == null) {
            return;
        }
        double distance = target.getBoundingBox().getCenter().distanceTo(muzzle());
        if (laserCooldown == 0 && distance <= laserRange()) {
            fireLaser(level, target);
        }
        if (rocketCooldown == 0 && ammoCount() > 0 && distance <= TurretBalance.ROCKET_RANGE) {
            fireRocket(level, target);
        }
    }

    private double laserRange() {
        return kind().range();
    }

    /** Zo ver kijkt hij: met raketten erin wat verder dan de laser reikt. */
    private double scanRange() {
        return ammoCount() > 0 ? Math.max(laserRange(), TurretBalance.ROCKET_RANGE) : laserRange();
    }

    private @Nullable LivingEntity findTarget(ServerLevel level) {
        Vec3 muzzle = muzzle();
        double range = scanRange();
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(muzzle, muzzle).inflate(range),
                        entity -> stillValid(level, entity))
                .stream()
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(muzzle)))
                .orElse(null);
    }

    /** Leeft het nog, is het een doelwit, dichtbij genoeg, en kan de loop het zien? */
    private boolean stillValid(ServerLevel level, LivingEntity entity) {
        Vec3 muzzle = muzzle();
        Vec3 aim = entity.getBoundingBox().getCenter();
        double range = scanRange();
        return aim.distanceToSqr(muzzle) <= range * range
                && TurretTargeting.isTarget(level, kind(), ownerId, ownerName, entity, worldPosition)
                && canSee(level, aim);
    }

    /** Zit er geen blok tussen de loop en deze plek? */
    private boolean canSee(ServerLevel level, Vec3 aim) {
        Vec3 muzzle = muzzle();
        // Een stukje buiten het eigen blok beginnen, anders "raken" we het torentje zelf.
        Vec3 from = muzzle.add(aim.subtract(muzzle).normalize().scale(0.8));
        HitResult hit = level.clip(new ClipContext(from, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void fireLaser(ServerLevel level, LivingEntity target) {
        TurretKind kind = kind();
        // Elk schot kost stroom van het net; geen stroom = geen laser.
        if (StarkGrid.available(level, worldPosition) < TurretBalance.LASER_COST) {
            return;
        }
        StarkGrid.draw(level, worldPosition, TurretBalance.LASER_COST);
        laserCooldown = kind.laserInterval();

        Vec3 muzzle = muzzle();
        Vec3 to = target.getBoundingBox().getCenter();
        Vec3 from = muzzle.add(to.subtract(muzzle).normalize().scale(0.7));
        BeamEffectPayload.Kind beam = kind.beam();
        level.playSound(null, worldPosition, ModSounds.DRONE_LASER.get(), SoundSource.BLOCKS, 0.8f, kind.pitch());
        // Een rayshield-koepel om het doelwit houdt de straal tegen (de eigen koepel niet).
        Vec3 shield = EdithDomes.blockedAt(level, to, muzzle, ownerId, DomeSettings.COST_LASER);
        if (shield != null) {
            ModNetwork.sendNear(level, muzzle, 64, new BeamEffectPayload(from, shield, beam));
            return;
        }
        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        target.hurt(ModDamageTypes.turretLaser(level, owner),
                kind.laserDamage());
        ModNetwork.sendNear(level, muzzle, 64, new BeamEffectPayload(from, to, beam));
    }

    private void fireRocket(ServerLevel level, LivingEntity target) {
        // Ook de lanceerbuis werkt op stroom (een beetje per raket).
        if (StarkGrid.available(level, worldPosition) < TurretBalance.ROCKET_POWER) {
            return;
        }
        MiniRocketEntity rocket = ModEntities.MINI_ROCKET.get().create(level);
        if (rocket == null) {
            return;
        }
        StarkGrid.draw(level, worldPosition, TurretBalance.ROCKET_POWER);
        rocketCooldown = TurretBalance.ROCKET_INTERVAL;
        ammo.extractItem(0, 1, false);
        Vec3 muzzle = muzzle();
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(muzzle).normalize();
        // Iets omhoog wegschieten: dan draait hij in een boogje naar zijn doel.
        Vec3 direction = toTarget.add(0, 0.5, 0);
        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        rocket.launch(owner, muzzle.add(toTarget.scale(0.8)).add(0, 0.2, 0), direction, target, null);
        rocket.onlyHitEnemies();
        level.addFreshEntity(rocket);
        level.playSound(null, worldPosition, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 0.9f, 1.2f);
    }

    /** Ander doelwit? Dan vertellen we het de spelers in de buurt (voor het draaien van de kop). */
    /** Het wezen waar hij nu op richt, of null (voor de Devices-app op de desktop). */
    public @Nullable Entity target(Level level) {
        return targetId < 0 ? null : level.getEntity(targetId);
    }

    private void setTarget(@Nullable LivingEntity target) {
        int id = target == null ? -1 : target.getId();
        if (id != targetId) {
            targetId = id;
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    // ================================================================ Jouw spel

    /** Elke tick in je eigen spel: de kop een stukje naar het doelwit draaien. */
    public void clientTick(Level level, BlockState state) {
        yawO = yaw;
        pitchO = pitch;
        Entity target = targetId >= 0 ? level.getEntity(targetId) : null;
        float wantYaw = yaw;
        float wantPitch = 0;
        if (!isActive(state)) {
            wantPitch = -30; // uit: de loop hangt omlaag
        } else if (target != null) {
            Vec3 aim = target.getBoundingBox().getCenter().subtract(muzzle());
            wantYaw = (float) (Mth.atan2(aim.x, aim.z) * Mth.RAD_TO_DEG);
            wantPitch = (float) (Mth.atan2(aim.y, aim.horizontalDistance()) * Mth.RAD_TO_DEG);
        } else {
            wantYaw = yaw + TurretBalance.IDLE_TURN_SPEED; // niemand te zien: rustig rondkijken
        }
        yaw = Mth.approachDegrees(yaw, wantYaw, TurretBalance.TURN_SPEED);
        pitch = Mth.approach(pitch, wantPitch, TurretBalance.TURN_SPEED / 2);
    }

    // ================================================================ Opslaan en doorsturen

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putString("OwnerName", ownerName);
        tag.put("Ammo", ammo.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        if (tag.contains("Ammo")) {
            ammo.deserializeNBT(registries, tag.getCompound("Ammo"));
        }
        if (tag.contains("Target")) {
            targetId = tag.getInt("Target");
        }
    }

    /** Wat je spel te horen krijgt: alles wat we opslaan, plus het doelwit. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        tag.putInt("Target", targetId);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
