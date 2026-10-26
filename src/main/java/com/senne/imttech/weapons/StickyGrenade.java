package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * De halve bol die plakt (hoofdstuk 35, jouw wens: "de halve sticky one"), in twee soorten (welke, zegt het soort
 * wezen): de Sticky EMP Grenade (een EMP-puls) en zijn kopie, de Sticky Frag Grenade (een klap met
 * breekpunten, zie breach/Blast). Een sticky smokebom of flashbang vind jij niet logisch, dus die zijn er niet. Een halve bol op een kleefkussen: hij plakt
 * vast op het eerste wat hij raakt (een muur, de grond, een plafond, een robot, een drone of een speler) en gaat 2
 * seconden later af (STICKY_FUSE_SECONDS). Plakt hij op een wezen, dan gaat hij mee met dat wezen.
 *
 * Waar hij zit, gaat ook naar je spel: {@code STUCK_TO} (het wezen, -1 = een blok of nog nergens), {@code OFFSET} (waar
 * op dat wezen, gedraaid zoals het wezen nu kijkt; of op een blok: vanaf de hoek van {@code ANCHOR}) en {@code NORMAL}
 * (de kant waar zijn bolle kant naartoe wijst; 0,0,0 = hij vliegt nog). Je spel zet hem daar zelf neer: anders zakt hij
 * in je spel nog een stukje verder (je spel laat hem vallen tot het hoort dat hij vastzit).
 */
public class StickyGrenade extends GrenadeEntity {
    private static final EntityDataAccessor<Integer> STUCK_TO =
            SynchedEntityData.defineId(StickyGrenade.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> OFFSET =
            SynchedEntityData.defineId(StickyGrenade.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> NORMAL =
            SynchedEntityData.defineId(StickyGrenade.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<BlockPos> ANCHOR =
            SynchedEntityData.defineId(StickyGrenade.class, EntityDataSerializers.BLOCK_POS);

    private static final double GRAVITY = 0.03;
    /** Plakt hij na zo lang (ticks) nog nergens, dan gaat hij toch af. */
    private static final int MAX_FLIGHT = 200;
    /** Het blok waar hij op plakt (alleen op de server), of null. */
    private @Nullable BlockPos stuckBlock;

    public StickyGrenade(EntityType<? extends StickyGrenade> type, Level level) {
        super(type, level);
    }

    /**
     * Een nieuwe granaat in de hand van {@code thrower}: de EMP-versie, of ({@code frag}) de frag-versie. Hij gaat pas
     * op scherp als hij vastplakt.
     */
    public StickyGrenade(Level level, LivingEntity thrower, boolean frag) {
        this(frag ? ModEntities.STICKY_FRAG_GRENADE.get() : ModEntities.STICKY_EMP_GRENADE.get(), level);
        setOwner(thrower);
        setPos(thrower.getX(), thrower.getEyeY() - 0.2, thrower.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STUCK_TO, -1);
        builder.define(OFFSET, new Vector3f());
        builder.define(NORMAL, new Vector3f());
        builder.define(ANCHOR, BlockPos.ZERO);
    }

    /** Zit hij vast (op een blok of een wezen)? */
    public boolean isStuck() {
        return entityData.get(NORMAL).lengthSquared() > 0.5f;
    }

    /** Naar welke kant zijn bolle kant wijst (weg van wat hij raakte). Alleen als hij vastzit. */
    public Vec3 normal() {
        Vector3f n = entityData.get(NORMAL);
        Entity host = host();
        Vec3 normal = new Vec3(n.x, n.y, n.z);
        return host == null ? normal : normal.yRot(-host.getYRot() * Mth.DEG_TO_RAD);
    }

    /** Het wezen waar hij op plakt, of null. */
    public @Nullable Entity host() {
        int id = entityData.get(STUCK_TO);
        return id < 0 ? null : level().getEntity(id);
    }

    @Override
    protected void fly() {
        if (isStuck()) {
            stayStuck();
            return;
        }
        Vec3 velocity = getDeltaMovement().add(0, -GRAVITY, 0).scale(0.99);
        setDeltaMovement(velocity);
        if (!level().isClientSide) {
            // Raakt hij onderweg een wezen? Dan plakt hij daarop.
            Vec3 from = getBoundingBox().getCenter();
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level(), this, from, from.add(velocity),
                    getBoundingBox().expandTowards(velocity).inflate(0.3), this::canHitEntity);
            if (entityHit != null) {
                stickTo(entityHit.getEntity(), entityHit.getLocation());
                return;
            }
        }
        Vec3 before = position();
        move(MoverType.SELF, velocity);
        if (!level().isClientSide) {
            // Kwam hij in een richting minder ver dan hij wilde? Dan botste hij daar tegen een blok: daar plakt hij.
            // (Niet kijken waar zijn middelpunt heen vliegt: zijn rand raakt de muur eerder, en dan gleed hij langs de
            // muur naar beneden en plakte hij op de vloer.)
            Direction face = clippedFace(velocity, position().subtract(before));
            if (face != null) {
                stickTo(face);
                return;
            }
            if (tickCount > MAX_FLIGHT && !isArmed()) {
                arm(1);
            }
        }
    }

    /**
     * De kant van het blok waar hij tegenaan botste (die wijst naar hem toe), of null. Bij meer dan één (een hoek): de
     * richting waarin hij het hardst ging.
     */
    private static @Nullable Direction clippedFace(Vec3 wanted, Vec3 moved) {
        Direction best = null;
        double fastest = 0;
        for (Direction.Axis axis : Direction.Axis.values()) {
            double want = wanted.get(axis);
            if (Math.abs(moved.get(axis) - want) > 1.0E-4 && Math.abs(want) > fastest) {
                fastest = Math.abs(want);
                best = Direction.fromAxisAndDirection(axis,
                        want > 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
            }
        }
        return best;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof GrenadeEntity) && !target.isSpectator();
    }

    /**
     * Plakt vast tegen de kant van een blok waar hij net tegenaan botste: zijn kussen komt op de plek waar zijn rand die
     * kant raakt, de bolle kant naar buiten ({@code face} wijst van het blok af).
     */
    private void stickTo(Direction face) {
        AABB box = getBoundingBox();
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        double half = face.getAxis().choose(box.getXsize(), box.getYsize(), box.getZsize()) / 2;
        Vec3 contact = box.getCenter().subtract(normal.scale(half));
        Vec3 at = contact.add(normal.scale(0.02));
        stuckBlock = BlockPos.containing(contact.subtract(normal.scale(0.05)));
        setPos(at.x, at.y, at.z);
        BlockPos anchor = BlockPos.containing(at);
        entityData.set(ANCHOR, anchor);
        entityData.set(OFFSET, at.subtract(Vec3.atLowerCornerOf(anchor)).toVector3f());
        entityData.set(NORMAL, new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ()));
        stuck();
    }

    /** Plakt vast op een wezen, op de plek waar hij het raakte. */
    private void stickTo(Entity host, Vec3 at) {
        Vec3 offset = at.subtract(host.position());
        // De kant waar hij zit: van het midden van het wezen naar de plek (alleen opzij, zoals een sticker op een romp).
        Vec3 out = new Vec3(offset.x, 0, offset.z);
        out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : out.normalize();
        // Opslaan zoals het wezen nu kijkt, zodat hij meedraait als het wezen zich omdraait.
        float yaw = host.getYRot() * Mth.DEG_TO_RAD;
        Vec3 localOffset = offset.yRot(yaw);
        Vec3 localNormal = out.yRot(yaw);
        entityData.set(STUCK_TO, host.getId());
        entityData.set(OFFSET, new Vector3f((float) localOffset.x, (float) localOffset.y, (float) localOffset.z));
        entityData.set(NORMAL, new Vector3f((float) localNormal.x, (float) localNormal.y, (float) localNormal.z));
        setPos(at.x, at.y, at.z);
        stuck();
    }

    private void stuck() {
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        level().playSound(null, getX(), getY(), getZ(), ModSounds.EMP_GRENADE_STICK.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        if (!isArmed()) {
            arm(Math.round(ImtConfig.Weapons.STICKY_FUSE_SECONDS * 20));
        }
    }

    /** Vastgeplakt: mee met het wezen, of blijven zitten. Is wat hem vasthield weg, dan valt hij. */
    private void stayStuck() {
        setDeltaMovement(Vec3.ZERO);
        int id = entityData.get(STUCK_TO);
        if (id >= 0) {
            Entity host = host();
            if (host == null || !host.isAlive()) {
                if (!level().isClientSide) {
                    loosen();
                }
                return;
            }
            Vector3f o = entityData.get(OFFSET);
            Vec3 at = host.position().add(new Vec3(o.x, o.y, o.z).yRot(-host.getYRot() * Mth.DEG_TO_RAD));
            setPos(at.x, at.y, at.z);
            return;
        }
        Vector3f o = entityData.get(OFFSET);
        Vec3 at = Vec3.atLowerCornerOf(entityData.get(ANCHOR)).add(o.x, o.y, o.z);
        setPos(at.x, at.y, at.z);
        if (!level().isClientSide && stuckBlock != null && level().getBlockState(stuckBlock).isAir()) {
            loosen(); // het blok is weg
        }
    }

    /** Losgekomen: hij valt (en plakt opnieuw op wat hij dan raakt). De lont loopt gewoon door. */
    private void loosen() {
        entityData.set(STUCK_TO, -1);
        entityData.set(NORMAL, new Vector3f());
        stuckBlock = null;
        setNoGravity(false);
    }

    @Override
    protected Vec3 blastCenter() {
        return isStuck() ? position().add(normal().scale(0.15)) : super.blastCenter();
    }

    /** Is dit de frag-versie (een klap in plaats van een EMP)? */
    public boolean isFrag() {
        return getType() == ModEntities.STICKY_FRAG_GRENADE.get();
    }

    /** De lont is op: een EMP-puls, of bij de frag-versie een klap met breekpunten (zie GrenadeEntity). */
    @Override
    protected void explode(ServerLevel level) {
        if (isFrag()) {
            fragBlast(level);
        } else {
            empPulse(level);
        }
    }

    /** Vastgeplakt: de klap zit precies tegen het blok of wezen (zoals een Remote Frag tegen zijn muur). */
    @Override
    protected Vec3 explosionCenter() {
        return isStuck() ? position() : super.explosionCenter();
    }
}
