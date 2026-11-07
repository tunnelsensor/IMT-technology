package com.senne.imttech.raven;

import com.senne.imttech.config.ImtConfig;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Het lichaam van een gevallen Raven-soldaat (hoofdstuk 60, stap 5, jouw antwoord 5: "een dode soldaat laat een lichaam
 * liggen; zijn buit zit in de body"). Vroeger verdween een dode soldaat na zijn valanimatie en lag zijn buit op de
 * grond; nu ligt hij er zelf, plat op zijn rug (RavenBodyRenderer), met alles wat hij zou laten vallen (zijn
 * Raven-onderdeel, soms zijn wapen, de staff-keycard van een bewaker) <b>in</b> zich:
 * <ul>
 *   <li><b>rechtsklik</b>: "Search the body", negen vakjes, haal eruit wat je wil;</li>
 *   <li><b>sneak + rechtsklik</b>: je pakt het lichaam vast en sleept het achter je aan (nog eens: loslaten), om het te
 *       verstoppen voor de andere bewakers, want wie een lichaam ziet, roept versterking (Bodies, RavenSenses);</li>
 *   <li>na {@code [guards] bodyMinutes} (5) verdwijnt het, en wat er nog in zat valt op de grond.</li>
 * </ul>
 * Het is een LivingEntity zonder AI (zoals een armor stand): zo tekenen we hem met het mensmodel van de soldaat en kan
 * je hem vastpakken, maar hij is onkwetsbaar (behalve voor /kill), duwt niets opzij en wordt door niemand aangevallen.
 */
public class RavenBodyEntity extends LivingEntity {
    /** Zo ver achter je sleept het lichaam mee. */
    private static final double DRAG_DISTANCE = 1.4;
    /** Verder dan dit laat je het los (je rende weg, of viel). */
    private static final double DRAG_LOST = 6.0;

    private final SimpleContainer loot = new SimpleContainer(9);
    /** Wie het lichaam meesleept, of null. */
    private @Nullable UUID holder;
    /** Hoe lang het er al ligt (ticks). */
    private int age;
    /** Al via de radio gemeld (dan roept niet elke bewaker het opnieuw). */
    private boolean reported;

    public RavenBodyEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 1.0);
    }

    /** Een soldaat valt: zijn lichaam op zijn plek, met deze buit erin. */
    public static @Nullable RavenBodyEntity fall(ServerLevel level, LivingEntity dead, Collection<ItemStack> items) {
        RavenBodyEntity body = com.senne.imttech.registry.ModEntities.RAVEN_BODY.get().create(level);
        if (body == null) {
            return null;
        }
        body.moveTo(dead.getX(), dead.getY(), dead.getZ(), dead.getYRot(), 0);
        body.setYBodyRot(dead.getYRot());
        body.setYHeadRot(dead.getYRot());
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                body.loot.addItem(stack.copy());
            }
        }
        level.addFreshEntity(body);
        return body;
    }

    /** Wat er in het lichaam zit (de buit van de soldaat). */
    public SimpleContainer loot() {
        return loot;
    }

    public boolean reported() {
        return reported;
    }

    public void setReported(boolean reported) {
        this.reported = reported;
    }

    public @Nullable UUID holder() {
        return holder;
    }

    // ================================================================ Elke tick

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        age++;
        if (age >= Math.round(ImtConfig.Guards.BODY_MINUTES * 1200)) {
            // Weg: wat erin zat valt op de grond.
            Containers.dropContents(level, blockPosition(), loot);
            discard();
            return;
        }
        if (holder != null) {
            drag(level);
        }
    }

    /** Achter wie het vasthoudt aan slepen (zoals een geboeide voor je uit loopt, zie Handcuffs.hold). */
    private void drag(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(holder);
        if (player == null || !player.isAlive() || player.level() != level || player.isSpectator()
                || player.distanceTo(this) > DRAG_LOST) {
            if (player != null && player.level() == level) {
                player.displayClientMessage(Component.translatable("raven.imttech.body.let_go").withStyle(ChatFormatting.GRAY), true);
            }
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
        setYRot(player.getYRot());
        setYBodyRot(player.getYRot());
        setYHeadRot(player.getYRot());
    }

    // ================================================================ Rechtsklik

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            // Vastpakken of loslaten.
            if (holder != null && holder.equals(player.getUUID())) {
                holder = null;
                serverPlayer.displayClientMessage(Component.translatable("raven.imttech.body.let_go").withStyle(ChatFormatting.GRAY), true);
            } else {
                holder = player.getUUID();
                serverPlayer.displayClientMessage(Component.translatable("raven.imttech.body.drag").withStyle(ChatFormatting.AQUA), true);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 0.7f);
            }
            return InteractionResult.CONSUME;
        }
        serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestMenu(MenuType.GENERIC_9x1, id, inventory, loot, 1),
                Component.translatable("raven.imttech.body.search")));
        return InteractionResult.CONSUME;
    }

    // ================================================================ Een lichaam is geen wezen

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (level() instanceof ServerLevel level) {
                Containers.dropContents(level, blockPosition(), loot);
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
        // Een lichaam duwt niets opzij.
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
        // Niets.
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        // Het lichaam draagt niets (zijn buit zit in loot).
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
        tag.putBoolean("Reported", reported);
        NonNullList<ItemStack> items = NonNullList.withSize(loot.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, loot.getItem(i));
        }
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items, registryAccess());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        age = tag.getInt("Age");
        reported = tag.getBoolean("Reported");
        NonNullList<ItemStack> items = NonNullList.withSize(loot.getContainerSize(), ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(tag, items, registryAccess());
        for (int i = 0; i < items.size(); i++) {
            loot.setItem(i, items.get(i));
        }
    }
}
