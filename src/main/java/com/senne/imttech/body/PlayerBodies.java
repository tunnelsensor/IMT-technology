package com.senne.imttech.body;

import com.senne.imttech.backpack.BackpackRegistry;
import com.senne.imttech.backpack.BackpackZip;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Het lichaam van een speler op de server (hoofdstuk 72, stap 4): doodgaan, de rugzak eraf halen, en "Alles terug".
 * <ul>
 *   <li><b>Doodgaan</b> zonder keepInventory ({@link #onDeath}): alles wat je had gaat in een lichaam op de plek waar je
 *       viel, elk ding op zijn eigen vakje (zie PlayerBodyEntity), je rugzak apart op zijn rug. Wat een andere mod nog laat
 *       vallen (Curios...) gaat er ook in ({@link #onDrops}). Spullen met de vloek van verdwijnen verdwijnen, zoals altijd.
 *       Viel je in de void, dan ligt het lichaam waar je het laatst op de grond stond.</li>
 *   <li><b>De rugzak eraf halen</b> ({@link #takePack}): met sneak + rechtsklik op de rugzak, of (hoofdstuk 98) met een klik
 *       op het rugzakvakje in het scherm van het lichaam. 3 s stilstaan (jouw keuze, voor iedereen), dan op je rug als die
 *       leeg is, anders in je inventaris.</li>
 *   <li><b>Alles terug</b> ({@link #restore}, de knop van de eigenaar): 3 s stilstaan, dan legt het alles terug op zijn
 *       plek (je harnas aan, je holster en pouches om, je rugzak op je rug, je werkbalk zoals hij was); wat niet past
 *       (daar ligt al iets) blijft in het lichaam. De rugzak gaat sinds hoofdstuk 98 altijd mee: draag je er al een, dan
 *       komt hij in je inventaris.</li>
 * </ul>
 */
public final class PlayerBodies {
    private PlayerBodies() {}

    /** Het lichaam dat net voor deze speler gemaakt is (tot zijn LivingDropsEvent, voor wat andere mods laten vallen). */
    private static final Map<UUID, PlayerBodyEntity> PENDING = new HashMap<>();
    /** Waar elke speler het laatst op de grond stond (voor wie in de void valt). */
    private static final Map<UUID, Vec3> LAST_GROUND = new HashMap<>();

    public static boolean enabled() {
        return ImtConfig.Backpacks.PLAYER_BODIES;
    }

    /** Elke tick (uit Backpacks): onthouden waar je op de grond staat. */
    public static void track(ServerPlayer player) {
        if (player.onGround() && player.getY() >= player.level().getMinBuildHeight()) {
            LAST_GROUND.put(player.getUUID(), player.position());
        }
    }

    // ================================================================ Doodgaan

    /**
     * Doodgaan (als allerlaatste, zodat een mod die je nog redt, zoals een totem, eerst kan): een lichaam met alles erin.
     * Gear.onDeath laat dan niets meer vallen (dat gaat hier mee).
     */
    public static void onDeath(LivingDeathEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        PlayerBodyEntity body = BackpackRegistry.PLAYER_BODY.get().create(level);
        if (body == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size() && i < 36; i++) {
            body.loot().setItem(i, keep(inventory.items.get(i)));
        }
        for (int i = 0; i < inventory.armor.size() && i < 4; i++) {
            body.loot().setItem(PlayerBodyEntity.ARMOR + i, keep(inventory.armor.get(i)));
        }
        body.loot().setItem(PlayerBodyEntity.OFFHAND, keep(inventory.offhand.getFirst()));
        inventory.clearContent();
        // Je holster, pouches, slings en rugzak: ook met Curios (hoofdstuk 98; vroeger liet Curios ze dan zelf vallen, en lag je
        // rugzak als los ding tussen wat andere mods lieten vallen in plaats van op de rug van je lichaam). Gear leest en
        // schrijft de Curios-slots zelf; we halen ze leeg voordat Curios ze zou laten vallen, dus niets komt dubbel.
        GearItem.Kind[] kinds = GearItem.Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            body.loot().setItem(PlayerBodyEntity.GEAR + i, keep(Gear.get(player, kinds[i])));
            Gear.set(player, kinds[i], ItemStack.EMPTY);
        }
        for (int i = 0; i < Gear.SLINGS; i++) {
            body.loot().setItem(PlayerBodyEntity.GEAR + kinds.length + i, keep(Gear.sling(player, i)));
            Gear.setSling(player, i, ItemStack.EMPTY);
        }
        body.setPack(keep(Gear.back(player)));
        Gear.setBack(player, ItemStack.EMPTY);
        // Hoofdstuk 100: de keycard aan je nek.
        body.loot().setItem(PlayerBodyEntity.NECK, keep(Gear.neck(player)));
        Gear.setNeck(player, ItemStack.EMPTY);
        Vec3 at = player.position();
        if (at.y < level.getMinBuildHeight()) {
            at = LAST_GROUND.getOrDefault(player.getUUID(), Vec3.atBottomCenterOf(level.getSharedSpawnPos()));
        }
        body.moveTo(at.x, at.y, at.z, player.getYRot(), 0);
        body.setYBodyRot(player.getYRot());
        body.setYHeadRot(player.getYRot());
        body.setOwner(player.getUUID(), player.getGameProfile().getName());
        if (body.isEmpty()) {
            return; // niets bij je: geen lichaam (dat zou meteen weer verdwijnen)
        }
        level.addFreshEntity(body);
        PENDING.put(player.getUUID(), body);
    }

    /** Wat je bewaart: alles behalve spullen met de vloek van verdwijnen (die verdwijnen, zoals altijd). */
    private static ItemStack keep(ItemStack stack) {
        if (stack.isEmpty() || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
            return ItemStack.EMPTY;
        }
        return stack.copy();
    }

    /** Wat andere mods nog laten vallen (Curios...) gaat ook in het lichaam, in de laatste vakjes (zolang er plek is). */
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerBodyEntity body = PENDING.remove(player.getUUID());
        if (body == null || body.isRemoved()) {
            return;
        }
        var it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity drop = it.next();
            for (int slot = PlayerBodyEntity.EXTRA; slot < PlayerBodyEntity.SIZE; slot++) {
                if (body.loot().getItem(slot).isEmpty()) {
                    body.loot().setItem(slot, drop.getItem().copy());
                    it.remove();
                    break;
                }
            }
        }
    }

    // ================================================================ De rugzak eraf halen

    /** Sneak + rechtsklik op de rugzak van een lichaam: 3 s stilstaan, dan is hij van jou (op je rug, of in je inventaris). */
    public static boolean takePack(ServerPlayer player, PlayerBodyEntity body) {
        int ticks = Math.round(ImtConfig.Backpacks.BODY_TAKE_PACK_SECONDS * 20);
        return BackpackZip.startTimed(player, ticks, "body.imttech.taking_pack",
                () -> body.isAlive() && body.hasPack() && player.distanceTo(body) <= 4,
                () -> {
                    ItemStack pack = body.pack().copy();
                    body.setPack(ItemStack.EMPTY);
                    if (!Gear.back(player).isEmpty() || !Gear.setBack(player, pack)) {
                        if (!player.getInventory().add(pack)) {
                            player.drop(pack, false);
                        }
                    }
                    player.serverLevel().playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                            SoundSource.PLAYERS, 0.9f, 0.9f);
                    player.displayClientMessage(Component.translatable("body.imttech.took_pack", pack.getHoverName())
                            .withStyle(ChatFormatting.GRAY), true);
                });
    }

    /**
     * Hoofdstuk 98 (jouw melding: "als je je body leegmaakt blijft je backpack zitten"): het rugzakvakje in het scherm van een
     * lichaam. Het scherm gaat dicht en je staat 3 s stil, net als met sneak + rechtsklik op de rugzak (jouw keuze: voor
     * iedereen, ook de eigenaar). Geeft false als er geen lichaam open staat of het geen rugzak heeft.
     */
    public static boolean takePackFromScreen(ServerPlayer player) {
        if (!(player.containerMenu instanceof BodyMenu menu) || menu.body() == null || !menu.body().hasPack()) {
            return false;
        }
        PlayerBodyEntity body = menu.body();
        player.closeContainer();
        return takePack(player, body);
    }

    // ================================================================ Alles terug

    /** De knop "Everything back" (alleen de eigenaar): 3 s stilstaan, dan alles terug op zijn plek. */
    public static boolean startRestore(ServerPlayer player, PlayerBodyEntity body) {
        if (!body.isOwner(player)) {
            return false;
        }
        player.closeContainer();
        int ticks = Math.round(ImtConfig.Backpacks.BODY_RESTORE_SECONDS * 20);
        return BackpackZip.startTimed(player, ticks, "body.imttech.restoring",
                () -> body.isAlive() && player.distanceTo(body) <= 4,
                () -> restore(player, body));
    }

    /**
     * Alles terug op zijn plek: je inventaris (elk vakje), je harnas, je andere hand, je holster, pouches en slings, en je
     * rugzak op je rug. Een plek waar al iets ligt slaat hij over: dat ding blijft in het lichaam. Wat andere mods lieten
     * vallen gaat in een vrij vakje van je inventaris.
     */
    public static int restore(ServerPlayer player, PlayerBodyEntity body) {
        Inventory inventory = player.getInventory();
        int back = 0;
        for (int slot = 0; slot < PlayerBodyEntity.SIZE; slot++) {
            ItemStack stack = body.loot().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            boolean placed = false;
            if (slot < 36) {
                if (inventory.items.get(slot).isEmpty()) {
                    inventory.items.set(slot, stack.copy());
                    placed = true;
                }
            } else if (slot < PlayerBodyEntity.OFFHAND) {
                int armor = slot - PlayerBodyEntity.ARMOR;
                if (inventory.armor.get(armor).isEmpty()) {
                    inventory.armor.set(armor, stack.copy());
                    placed = true;
                }
            } else if (slot == PlayerBodyEntity.OFFHAND) {
                if (inventory.offhand.getFirst().isEmpty()) {
                    inventory.offhand.set(0, stack.copy());
                    placed = true;
                }
            } else if (slot < PlayerBodyEntity.EXTRA) {
                placed = restoreGear(player, slot - PlayerBodyEntity.GEAR, stack);
            } else {
                placed = inventory.add(stack.copy());
            }
            if (placed) {
                body.loot().setItem(slot, ItemStack.EMPTY);
                back++;
            }
        }
        // Hoofdstuk 98: de rugzak gaat altijd mee: op je rug, of (draag je al een rugzak) in je inventaris. Alleen als daar
        // ook geen plek is, blijft hij op het lichaam.
        if (body.hasPack()) {
            ItemStack pack = body.pack().copy();
            if ((Gear.back(player).isEmpty() && Gear.setBack(player, pack)) || inventory.add(pack)) {
                body.setPack(ItemStack.EMPTY);
                back++;
            }
        }
        player.inventoryMenu.broadcastChanges();
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(),
                SoundSource.PLAYERS, 1.0f, 1.0f);
        player.displayClientMessage(Component.translatable(body.isEmpty() ? "body.imttech.restored_all" : "body.imttech.restored_some", back)
                .withStyle(body.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.GOLD), true);
        return back;
    }

    /**
     * Een holster, pouch of geweer met sling terug op zijn plek (0-2 de holster en pouches, 3-4 de slings), of (hoofdstuk 100,
     * 5) de keycard aan je nek. Ligt daar in een oud lichaam iets anders (het was het eerste vakje van "andere mods"), dan
     * gaat dat in je inventaris.
     */
    private static boolean restoreGear(ServerPlayer player, int index, ItemStack stack) {
        GearItem.Kind[] kinds = GearItem.Kind.values();
        if (index == PlayerBodyEntity.NECK - PlayerBodyEntity.GEAR) {
            if (!Gear.neckAccepts(stack)) {
                return player.getInventory().add(stack.copy());
            }
            return Gear.neck(player).isEmpty() && Gear.setNeck(player, stack.copy());
        }
        if (index < kinds.length) {
            return Gear.get(player, kinds[index]).isEmpty() && Gear.set(player, kinds[index], stack.copy());
        }
        int sling = index - kinds.length;
        return sling < Gear.SLINGS && Gear.sling(player, sling).isEmpty() && Gear.setSling(player, sling, stack.copy());
    }

    /** Alles vergeten (de server stopt, of een speler gaat weg). */
    public static void clear() {
        PENDING.clear();
        LAST_GROUND.clear();
    }

    /** Hoeveel er in het lichaam ligt (stapels, plus de rugzak). */
    public static int count(PlayerBodyEntity body) {
        int count = 0;
        for (int i = 0; i < PlayerBodyEntity.SIZE; i++) {
            if (!body.loot().getItem(i).isEmpty()) {
                count++;
            }
        }
        return count + (body.hasPack() ? 1 : 0);
    }
}
