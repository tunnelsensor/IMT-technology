package com.senne.imttech.body;

import com.senne.imttech.backpack.BackpackItem;
import com.senne.imttech.backpack.BackpackPlace;
import com.senne.imttech.backpack.BackpackZip;
import com.senne.imttech.config.ImtConfig;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Het lichaam van een speler (hoofdstuk 72, stap 4, jouw wens: "als je als speler doodgaat laat je ook een body achter
 * zoals de soldiers; je kunt deze body leeghalen, de rugzak is dan een apart deel van deze body om leeg te halen. Je kunt
 * als speler je eigen lichaam weer zoeken en looten of andere mensen kunnen je body looten").
 *
 * Het ligt op zijn buik (de rugzak bovenop), met zijn hoofd de kant op waar de speler keek. Erin zit alles wat hij had, op
 * zijn eigen plek (zodat "Alles terug" het weer zo kan neerleggen):
 * <pre>
 *  vakje  0 - 35   je inventaris (0 - 8 je werkbalk)          47 - 53  wat andere mods lieten vallen (Curios...)
 *  vakje 36 - 39   je harnas (voeten, benen, borst, hoofd)
 *  vakje 40        je andere hand
 *  vakje 41 - 45   je holster, granaatpouch, magazijnpouch, en je geweren op je rug en borst (de slings)
 *  vakje 46        hoofdstuk 100: de keycard aan je nek (was het eerste vakje van "andere mods")
 * </pre>
 * En apart, op zijn rug: <b>de rugzak</b> (met zijn MOLLE-pouches).
 * <ul>
 *   <li><b>Rechtsklik op het lichaam</b>: doorzoeken (BodyMenu): iedereen mag eruit halen (de eigenaar ook, en hij heeft
 *       een knop "Everything back").</li>
 *   <li><b>Rechtsklik op de rugzak</b>: openritsen (1,5 s), dan het scherm van de rugzak.</li>
 *   <li><b>Sneak + rechtsklik op de rugzak</b>: de hele rugzak eraf halen (3 s stilstaan, jouw keuze).</li>
 *   <li><b>Sneak + rechtsklik op het lichaam</b>: meeslepen (zoals een Raven-soldaat; nog eens = loslaten).</li>
 * </ul>
 * Het blijft liggen <b>tot het leeg is</b> (jouw keuze; {@code bodyMinutes} in de config als je wil dat het verdwijnt,
 * standaard nooit), ook als niemand in de buurt is: de wereld bewaart het. Geen coördinaten, geen markering: je zoekt het
 * zelf terug.
 *
 * Een LivingEntity zonder AI, zoals een armor stand: onkwetsbaar (behalve voor /kill en de void), duwt niets en wordt door
 * niemand aangevallen.
 */
public class PlayerBodyEntity extends LivingEntity {
    public static final int SIZE = 54;
    public static final int ARMOR = 36;
    public static final int OFFHAND = 40;
    public static final int GEAR = 41;
    /** Hoofdstuk 100: de keycard aan je nek, na de holster, de pouches en de slings. */
    public static final int NECK = 46;
    /**
     * Hoofdstuk 109: je vest (de Plate Carrier), na je nek. In een lichaam van voor hoofdstuk 109 was dit het eerste vakje
     * van "andere mods": ligt daar geen vest, dan gaat het bij "Everything back" gewoon in je inventaris.
     */
    public static final int VEST = 47;
    public static final int EXTRA = 48;

    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> OWNER_NAME =
            SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.STRING);
    /** De rugzak op zijn rug (ook in jouw spel, om hem te tekenen met zijn kleur en pouches). */
    private static final EntityDataAccessor<ItemStack> PACK =
            SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.ITEM_STACK);
    /** Hoofdstuk 109: het vest in zijn vakje, ook in jouw spel (om het te tekenen, met de tekst op zijn rug). */
    private static final EntityDataAccessor<ItemStack> SHOWN_VEST =
            SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.ITEM_STACK);

    /** Zo ver achter je sleept het lichaam mee. */
    private static final double DRAG_DISTANCE = 1.6;
    private static final double DRAG_LOST = 6.0;

    private final SimpleContainer loot = new SimpleContainer(SIZE);
    /** In jouw spel: het harnas dat hij draagt (om te tekenen; de server stuurt het als uitrusting). */
    private final Map<EquipmentSlot, ItemStack> shown = new EnumMap<>(EquipmentSlot.class);
    private @Nullable UUID holder;
    private int age;

    public PlayerBodyEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(OWNER_NAME, "");
        builder.define(PACK, ItemStack.EMPTY);
        builder.define(SHOWN_VEST, ItemStack.EMPTY);
    }

    /** Hoofdstuk 109: het vest dat hij draagt (om te tekenen), of leeg. */
    public ItemStack vest() {
        ItemStack vest = level().isClientSide ? entityData.get(SHOWN_VEST) : loot.getItem(VEST);
        return com.senne.imttech.armor.VestItem.isVest(vest) ? vest : ItemStack.EMPTY;
    }

    // ================================================================ Van wie, en wat erin zit

    public @Nullable UUID owner() {
        return entityData.get(OWNER).orElse(null);
    }

    public String ownerName() {
        return entityData.get(OWNER_NAME);
    }

    public void setOwner(UUID owner, String name) {
        entityData.set(OWNER, Optional.of(owner));
        entityData.set(OWNER_NAME, name);
    }

    public boolean isOwner(Player player) {
        return player.getUUID().equals(owner());
    }

    public SimpleContainer loot() {
        return loot;
    }

    /** De rugzak op zijn rug (niet veranderen; gebruik {@link #setPack}), of leeg. */
    public ItemStack pack() {
        return entityData.get(PACK);
    }

    public void setPack(ItemStack pack) {
        entityData.set(PACK, pack.copy());
    }

    public boolean hasPack() {
        return BackpackItem.isBackpack(pack());
    }

    /** Is er niets meer: geen spullen en geen rugzak? */
    public boolean isEmpty() {
        return loot.isEmpty() && pack().isEmpty();
    }

    // ================================================================ Waar je klikt

    /**
     * Ligt dit punt op de rugzak? Het lichaam ligt op zijn buik met zijn hoofd naar waar hij keek; de rugzak ligt op het
     * bovenste stuk van zijn rug: van zijn schouders ({@code +0,45} blok voorbij het midden, naar zijn hoofd toe) tot zijn
     * middel ({@code -0,25}), en een halve blok breed. {@code local} = het punt, ten opzichte van het midden van het lichaam.
     */
    public boolean onPack(Vec3 local) {
        if (!hasPack()) {
            return false;
        }
        float rad = getYRot() * Mth.DEG_TO_RAD;
        double along = -Mth.sin(rad) * local.x + Mth.cos(rad) * local.z;
        double side = Mth.cos(rad) * local.x + Mth.sin(rad) * local.z;
        return along > -0.25 && along < 0.45 && Math.abs(side) < 0.5;
    }

    /**
     * Mikt {@code player} op de rugzak? Minecraft zegt waar je blik de klikdoos binnenkomt ({@code hitLocal}); kijk je
     * schuin naar beneden, dan is dat de bovenkant van de doos, dichter bij je dan de rugzak die je ziet. Daarom rekenen we
     * met je kijklijn: waar die op de hoogte van de rugzak komt (0,45 boven de grond). Kijk je (bijna) recht vooruit, dan
     * de plek van Minecraft.
     */
    public boolean aimsAtPack(Player player, Vec3 hitLocal) {
        if (!hasPack()) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getViewVector(1.0f);
        double planeY = getY() + 0.45;
        if (dir.y < -0.05 && eye.y > planeY) {
            Vec3 at = eye.add(dir.scale((planeY - eye.y) / dir.y));
            return onPack(new Vec3(at.x - getX(), 0.45, at.z - getZ()));
        }
        return onPack(hitLocal);
    }

    /**
     * Rechtsklik met de plek waar je klikte (Minecraft stuurt die mee): op de rugzak openritsen of (sneak) eraf halen, op het
     * lichaam doorzoeken of (sneak) meeslepen. Ook in jouw spel zeggen we "gelukt", zodat er geen tweede klik volgt.
     */
    @Override
    public InteractionResult interactAt(Player player, Vec3 local, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level() instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (aimsAtPack(player, local)) {
            if (player.isShiftKeyDown()) {
                PlayerBodies.takePack(serverPlayer, this);
            } else {
                BackpackZip.start(serverPlayer, BackpackPlace.body(this, serverPlayer));
            }
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) {
            toggleDrag(serverPlayer);
        } else {
            BodyMenu.open(serverPlayer, this);
        }
        return InteractionResult.CONSUME;
    }

    private void toggleDrag(ServerPlayer player) {
        if (holder != null && holder.equals(player.getUUID())) {
            holder = null;
            player.displayClientMessage(Component.translatable("raven.imttech.body.let_go").withStyle(ChatFormatting.GRAY), true);
        } else {
            holder = player.getUUID();
            player.displayClientMessage(Component.translatable("raven.imttech.body.drag").withStyle(ChatFormatting.AQUA), true);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 0.7f);
        }
    }

    // ================================================================ Elke tick

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        age++;
        // Hoofdstuk 109: wat er in het vestvakje ligt, ziet iedereen op het lichaam (haalt iemand het eruit, dan is het weg).
        ItemStack vest = vest();
        if (!ItemStack.matches(entityData.get(SHOWN_VEST), vest)) {
            entityData.set(SHOWN_VEST, vest.copy());
        }
        float minutes = ImtConfig.Backpacks.BODY_MINUTES;
        if (minutes > 0 && age >= Math.round(minutes * 1200)) {
            spill(level);
            discard();
            return;
        }
        // Leeg = weg (jouw keuze: "tot hij leeg is"), met een wolkje.
        if (age > 20 && isEmpty()) {
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.3, getZ(), 12, 0.5, 0.15, 0.5, 0.02);
            discard();
            return;
        }
        if (holder != null) {
            drag(level);
        }
    }

    /** Alles op de grond (het lichaam verdwijnt na zijn tijd, of door /kill). */
    private void spill(ServerLevel level) {
        Containers.dropContents(level, blockPosition(), loot);
        if (!pack().isEmpty()) {
            Containers.dropItemStack(level, getX(), getY(), getZ(), pack().copy());
            setPack(ItemStack.EMPTY);
        }
    }

    /** Achter wie het vasthoudt aan slepen (zoals het lichaam van een Raven-soldaat). */
    private void drag(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(holder);
        if (player == null || !player.isAlive() || player.level() != level || player.isSpectator() || player.distanceTo(this) > DRAG_LOST) {
            holder = null;
            return;
        }
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 behind = player.position().add(Mth.sin(yaw) * DRAG_DISTANCE, 0, -Mth.cos(yaw) * DRAG_DISTANCE);
        Vec3 to = behind.subtract(position());
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        if (flat > 3.0 || Math.abs(to.y) > 2.5) {
            teleportTo(behind.x, behind.y, behind.z);
            setDeltaMovement(Vec3.ZERO);
        } else if (flat > 0.2) {
            double strength = Math.min(0.4, 0.3 * flat);
            Vec3 v = getDeltaMovement();
            double up = onGround() && to.y > 0.3 ? 0.42 : v.y;
            setDeltaMovement(to.x / flat * strength, up, to.z / flat * strength);
            hurtMarked = true;
        }
        // Het hoofd naar wie sleept, zoals je een lichaam aan zijn armen meetrekt.
        setYRot(player.getYRot());
        setYBodyRot(player.getYRot());
        setYHeadRot(player.getYRot());
    }

    // ================================================================ Een lichaam is geen wezen

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (level() instanceof ServerLevel level) {
                spill(level);
            }
            discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean attackable() {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    // ================================================================ Wat je ziet: het harnas

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of(getItemBySlot(EquipmentSlot.FEET), getItemBySlot(EquipmentSlot.LEGS), getItemBySlot(EquipmentSlot.CHEST),
                getItemBySlot(EquipmentSlot.HEAD));
    }

    /** Zijn harnas (zodat je het ziet en Minecraft het naar iedereen stuurt); niets in zijn handen. */
    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
            return ItemStack.EMPTY;
        }
        if (level().isClientSide) {
            return shown.getOrDefault(slot, ItemStack.EMPTY);
        }
        return loot.getItem(ARMOR + slot.getIndex());
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        if (level().isClientSide) {
            shown.put(slot, stack);
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    // ================================================================ Opslaan

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Age", age);
        UUID owner = owner();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName());
        NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        for (int i = 0; i < SIZE; i++) {
            items.set(i, loot.getItem(i));
        }
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items, registryAccess());
        if (!pack().isEmpty()) {
            tag.put("Pack", pack().save(registryAccess()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        age = tag.getInt("Age");
        if (tag.hasUUID("Owner")) {
            setOwner(tag.getUUID("Owner"), tag.getString("OwnerName"));
        }
        NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(tag, items, registryAccess());
        for (int i = 0; i < SIZE; i++) {
            loot.setItem(i, items.get(i));
        }
        setPack(tag.contains("Pack") ? ItemStack.parseOptional(registryAccess(), tag.getCompound("Pack")) : ItemStack.EMPTY);
    }
}
