package com.senne.imttech.guns;

import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.weapons.GrenadeItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Wat je draagt om snel bij je wapens te kunnen (hoofdstuk 52, stap 2): de holster, de granaatpouch en de
 * magazijnpouch, en alles wat je ermee doet.
 * <ul>
 *   <li><b>Waar ze zitten</b>: in Curios-slots als die mod geladen is ({@link #curios()}), anders in de attachment GEAR
 *       van de speler: drie vakjes die je in je inventaris naast je harnas ziet (zie client/guns/GearSlotsOverlay en
 *       {@link #click}). Die attachment wordt opgeslagen en naar iedereen gestuurd die je ziet (voor je heup).</li>
 *   <li><b>Trekken</b> ({@link #drawGun}, {@link #drawGrenade}): uit het wiel (linker Alt). Jouw keuze: wat je in je hand
 *       had valt op de grond, het pistool (of de granaat) komt in dat vakje. Trekken duurt even (DRAW).</li>
 *   <li><b>Wegsteken</b> ({@link #holsterHand}): het pistool in de holster, een granaat in de pouch, een magazijn in de
 *       magazijnpouch.</li>
 *   <li><b>De granaatpouch voert aan</b> ({@link #refillGrenade}): gooi je de laatste granaat van een soort, dan komt
 *       de volgende uit de pouch in je hand.</li>
 *   <li><b>Herladen uit de magazijnpouch</b> ({@link #reloadFromPouch}, {@link #finishPouchReload}): R toont de
 *       magazijnen in je pouch, je klikt er een; het oude magazijn komt op zijn plek in de pouch.</li>
 *   <li><b>Doodgaan</b>: zonder keepInventory vallen ze op de grond, net als de rest.</li>
 * </ul>
 */
public final class Gear {
    private Gear() {}

    /** Wat het wiel kan vragen (zie GearActionPayload). */
    public static final int ACTION_DRAW_GUN = 0;
    public static final int ACTION_DRAW_GRENADE = 1;
    public static final int ACTION_HOLSTER = 2;

    private static Boolean curiosLoaded;

    /** Zit Curios in het modpack? Dan draag je alles daar. */
    public static boolean curios() {
        if (curiosLoaded == null) {
            curiosLoaded = ModList.get().isLoaded("curios");
        }
        return curiosLoaded;
    }

    // ================================================================ Waar ze zitten

    /** Wat deze speler van deze soort draagt (een kopie), of leeg. */
    public static ItemStack get(Player player, GearItem.Kind kind) {
        if (curios()) {
            return CuriosGear.get(player, kind);
        }
        return player.getData(ModAttachments.GEAR).get(kind.ordinal());
    }

    /** Dit ding (of leeg) in de plek van deze soort zetten. Geeft false als dat niet kan (Curios zonder zo'n slot). */
    public static boolean set(Player player, GearItem.Kind kind, ItemStack stack) {
        if (curios()) {
            return CuriosGear.set(player, kind, stack);
        }
        GearContents slots = GearContents.sized(player.getData(ModAttachments.GEAR).stacks(), GearItem.Kind.values().length);
        player.setData(ModAttachments.GEAR, slots.with(kind.ordinal(), stack));
        return true;
    }

    /** De inhoud van wat je draagt (kopieën), of een lege lijst als je dat niet draagt. */
    public static List<ItemStack> contents(Player player, GearItem.Kind kind) {
        return GearItem.contents(get(player, kind));
    }

    private static void setContents(Player player, GearItem.Kind kind, List<ItemStack> contents) {
        ItemStack gear = get(player, kind);
        if (gear.isEmpty()) {
            return;
        }
        GearItem.setContents(gear, contents);
        set(player, kind, gear);
    }

    /** Past dit item nog ergens in wat je draagt? Geeft de soort, of null. */
    public static @Nullable GearItem.Kind roomFor(Player player, ItemStack stack) {
        for (GearItem.Kind kind : GearItem.Kind.values()) {
            if (kind.accepts(stack) && freeIndex(player, kind, stack) >= 0) {
                return kind;
            }
        }
        return null;
    }

    /** Het vakje waar dit item in kan (eerst bij dezelfde soort erbij, dan een leeg vakje), of -1. */
    private static int freeIndex(Player player, GearItem.Kind kind, ItemStack stack) {
        ItemStack gear = get(player, kind);
        if (gear.isEmpty()) {
            return -1;
        }
        List<ItemStack> contents = GearItem.contents(gear);
        if (stack.isStackable()) {
            for (int i = 0; i < contents.size(); i++) {
                ItemStack inside = contents.get(i);
                if (!inside.isEmpty() && ItemStack.isSameItemSameComponents(inside, stack) && inside.getCount() < inside.getMaxStackSize()) {
                    return i;
                }
            }
        }
        for (int i = 0; i < contents.size(); i++) {
            if (contents.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    // ================================================================ Trekken en wegsteken

    /** Het pistool uit je holster in je hand; wat je vasthield valt op de grond (jouw keuze). */
    public static boolean drawGun(ServerPlayer player) {
        List<ItemStack> holster = contents(player, GearItem.Kind.HOLSTER);
        ItemStack gun = holster.isEmpty() ? ItemStack.EMPTY : holster.getFirst();
        if (gun.isEmpty()) {
            tell(player, Component.translatable("gear.imttech.no_gun"));
            return false;
        }
        if (Firearms.isBusy(player)) {
            return false;
        }
        holster.set(0, ItemStack.EMPTY);
        setContents(player, GearItem.Kind.HOLSTER, holster);
        intoHand(player, gun);
        sound(player, ModSounds.HOLSTER_DRAW.get(), 0.8f);
        Firearms.start(player, Firearms.ActionKind.DRAW, player.getInventory().selected, 0, gun);
        return true;
    }

    /** Een granaat van deze soort uit je pouch in je hand (de hele stapel van dat vakje). */
    public static boolean drawGrenade(ServerPlayer player, int kindOrdinal) {
        GrenadeItem.Kind[] kinds = GrenadeItem.Kind.values();
        if (kindOrdinal < 0 || kindOrdinal >= kinds.length) {
            return false;
        }
        List<ItemStack> pouch = contents(player, GearItem.Kind.GRENADE_POUCH);
        for (int i = 0; i < pouch.size(); i++) {
            ItemStack stack = pouch.get(i);
            if (stack.getItem() instanceof GrenadeItem grenade && grenade.kind() == kinds[kindOrdinal]) {
                pouch.set(i, ItemStack.EMPTY);
                setContents(player, GearItem.Kind.GRENADE_POUCH, pouch);
                intoHand(player, stack);
                sound(player, ModSounds.HOLSTER_DRAW.get(), 0.6f);
                return true;
            }
        }
        tell(player, Component.translatable("gear.imttech.no_grenades"));
        return false;
    }

    /** Dit item in je gekozen vakje; wat daar lag, valt op de grond. */
    private static void intoHand(ServerPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        ItemStack held = inventory.getItem(inventory.selected);
        if (!held.isEmpty()) {
            player.drop(held, false);
        }
        inventory.setItem(inventory.selected, stack);
    }

    /** Wat je vasthoudt wegsteken: een pistool in de holster, een granaat in de pouch, een magazijn in de magazijnpouch. */
    public static boolean holsterHand(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        ItemStack held = inventory.getItem(inventory.selected);
        if (held.isEmpty()) {
            tell(player, Component.translatable("gear.imttech.nothing_to_holster"));
            return false;
        }
        GearItem.Kind kind = null;
        for (GearItem.Kind candidate : GearItem.Kind.values()) {
            if (candidate.accepts(held)) {
                kind = candidate;
            }
        }
        if (kind == null) {
            tell(player, Component.translatable("gear.imttech.does_not_fit"));
            return false;
        }
        if (get(player, kind).isEmpty()) {
            tell(player, Component.translatable("gear.imttech.not_wearing", kind.text()));
            return false;
        }
        int index = freeIndex(player, kind, held);
        if (index < 0) {
            tell(player, Component.translatable("gear.imttech.full", kind.text()));
            return false;
        }
        List<ItemStack> contents = contents(player, kind);
        ItemStack inside = contents.get(index);
        if (inside.isEmpty()) {
            contents.set(index, held.copy());
            inventory.setItem(inventory.selected, ItemStack.EMPTY);
        } else {
            int room = inside.getMaxStackSize() - inside.getCount();
            int moved = Math.min(room, held.getCount());
            inside.grow(moved);
            held.shrink(moved);
            contents.set(index, inside);
        }
        setContents(player, kind, contents);
        sound(player, ModSounds.HOLSTER_DRAW.get(), 0.6f);
        return true;
    }

    /** Je gooide je laatste granaat van een soort: de volgende uit de pouch komt in je hand. */
    public static void refillGrenade(ServerPlayer player, GrenadeItem item, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {
            return;
        }
        List<ItemStack> pouch = contents(player, GearItem.Kind.GRENADE_POUCH);
        for (int i = 0; i < pouch.size(); i++) {
            ItemStack stack = pouch.get(i);
            if (stack.getItem() == item) {
                pouch.set(i, ItemStack.EMPTY);
                setContents(player, GearItem.Kind.GRENADE_POUCH, pouch);
                player.getInventory().setItem(player.getInventory().selected, stack);
                return;
            }
        }
    }

    // ================================================================ De magazijnpouch

    /** Een uitgeworpen magazijn in de magazijnpouch stoppen; geeft false als dat niet kan. */
    public static boolean storeMagazine(ServerPlayer player, ItemStack magazine) {
        int index = freeIndex(player, GearItem.Kind.MAG_POUCH, magazine);
        if (index < 0) {
            return false;
        }
        List<ItemStack> pouch = contents(player, GearItem.Kind.MAG_POUCH);
        pouch.set(index, magazine);
        setContents(player, GearItem.Kind.MAG_POUCH, pouch);
        return true;
    }

    /** R met een magazijnpouch: dit magazijn ({@code index}) uit de pouch erin. */
    public static boolean reloadFromPouch(ServerPlayer player, int index) {
        ItemStack gun = player.getMainHandItem();
        FirearmType type = FirearmItem.typeOf(gun);
        if (type == null || Firearms.isBusy(player) || FirearmItem.jam(gun) != FirearmItem.JAM_NONE) {
            return false;
        }
        List<ItemStack> pouch = contents(player, GearItem.Kind.MAG_POUCH);
        if (index < 0 || index >= pouch.size() || MagazineItem.typeOf(pouch.get(index)) != type) {
            tell(player, Component.translatable("gear.imttech.no_magazine_there"));
            return false;
        }
        sound(player, ModSounds.GLOCK_MAG_OUT.get(), 0.8f);
        return Firearms.start(player, Firearms.ActionKind.RELOAD_POUCH, player.getInventory().selected, index, gun);
    }

    /** Het herladen uit de pouch is klaar: wisselen (het oude magazijn komt in het vakje van het nieuwe). */
    public static void finishPouchReload(ServerPlayer player, ItemStack gun, int index) {
        FirearmType type = FirearmItem.typeOf(gun);
        List<ItemStack> pouch = contents(player, GearItem.Kind.MAG_POUCH);
        if (type == null || index < 0 || index >= pouch.size() || MagazineItem.typeOf(pouch.get(index)) != type) {
            return;
        }
        ItemStack fresh = pouch.get(index);
        ItemStack old = FirearmItem.magazine(gun);
        FirearmItem.setMagazine(gun, fresh);
        pouch.set(index, old);
        setContents(player, GearItem.Kind.MAG_POUCH, pouch);
        sound(player, ModSounds.GLOCK_MAG_IN.get(), 0.8f);
        GunSkillHooks.reloaded(player, gun);
    }

    // ================================================================ De eigen vakjes in je inventaris (zonder Curios)

    /**
     * Een klik op een van de drie vakjes naast je harnas (zie GearClickPayload): wat je aan je muis hebt gaat erin, wat
     * erin zat komt aan je muis (of ze wisselen).
     */
    public static void click(ServerPlayer player, int index) {
        if (curios() || index < 0 || index >= GearItem.Kind.values().length) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (!(menu instanceof InventoryMenu)) {
            return;
        }
        GearItem.Kind kind = GearItem.Kind.values()[index];
        ItemStack carried = menu.getCarried();
        ItemStack worn = get(player, kind);
        if (carried.isEmpty()) {
            if (worn.isEmpty()) {
                return;
            }
            set(player, kind, ItemStack.EMPTY);
            menu.setCarried(worn);
        } else if (GearItem.kindOf(carried) == kind && carried.getCount() == 1) {
            set(player, kind, carried.copy());
            menu.setCarried(worn);
        } else {
            return;
        }
        menu.broadcastChanges();
        sound(player, ModSounds.HOLSTER_DRAW.get(), 0.5f);
    }

    /** Doodgaan zonder keepInventory: wat je droeg valt op de grond. */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || curios()) {
            return;
        }
        if (player.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        GearContents worn = player.getData(ModAttachments.GEAR);
        for (ItemStack stack : worn.stacks()) {
            if (!stack.isEmpty()) {
                player.drop(stack, true, false);
            }
        }
        player.setData(ModAttachments.GEAR, GearContents.EMPTY);
    }

    // ================================================================ Hulpjes

    private static void tell(ServerPlayer player, Component text) {
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.RED), true);
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume) {
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, volume,
                0.95f + player.getRandom().nextFloat() * 0.1f);
    }
}
