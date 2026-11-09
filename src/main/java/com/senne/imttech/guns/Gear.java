package com.senne.imttech.guns;

import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.weapons.GrenadeItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Wat je draagt om snel bij je wapens te kunnen (hoofdstuk 52, stap 2): de holster, de granaatpouch en de
 * magazijnpouch, en sinds hoofdstuk 55 de twee <b>sling-vakjes</b> (een lasergeweer met een sling op je rug en een
 * voor je borst), en alles wat je ermee doet.
 * <ul>
 *   <li><b>Waar ze zitten</b>: in Curios-slots als die mod geladen is ({@link #curios()}), anders in de attachment GEAR
 *       van de speler: de drie vakjes die je in je inventaris naast je harnas ziet, plus de twee sling-vakjes onder het
 *       craftraster (zie client/guns/GearSlotsOverlay en {@link #click}). Die attachment wordt opgeslagen en naar
 *       iedereen gestuurd die je ziet (voor je heup en je rug). In een sling-vakje zit het geweer zelf (met zijn sling
 *       eraan), niet een holster: jouw keuze, "als de sling eraan hangt kun je die in de slingslot doen, anders
 *       niet".</li>
 *   <li><b>Trekken</b> ({@link #drawGun}, {@link #drawSling}, {@link #drawGrenade}): uit het wiel (linker Alt). Jouw keuze:
 *       wat je in je hand had valt op de grond, <i>tenzij</i> het zelf in je holster, een sling-vakje of een pouch past:
 *       dan gaat het daarheen ("heb je de glock vast en pak je je laserrifle, dan holster je de glock"). Het pistool
 *       (of het geweer, of de granaat) komt in dat vakje. Trekken duurt even (DRAW; van je sling langer).</li>
 *   <li><b>Wegsteken</b> ({@link #holsterHand}): een pistool in de holster, een geweer met sling in een sling-vakje,
 *       een granaat in de pouch, een magazijn of een cel in de magazijnpouch.</li>
 *   <li><b>De granaatpouch voert aan</b> ({@link #refillGrenade}): gooi je de laatste granaat van een soort, dan komt
 *       de volgende uit de pouch in je hand.</li>
 *   <li><b>Herladen uit de magazijnpouch</b> ({@link #reloadFromPouch}, {@link #finishPouchReload}): R toont de
 *       magazijnen in je pouch, je klikt er een; het oude magazijn komt op zijn plek in de pouch. (Voor de cellen van
 *       een lasergeweer: zie weapons/LaserGuns.startReload.)</li>
 *   <li><b>Doodgaan</b>: zonder keepInventory vallen ze op de grond, net als de rest.</li>
 * </ul>
 */
public final class Gear {
    private Gear() {}

    /** Wat het wiel kan vragen (zie GearActionPayload). */
    public static final int ACTION_DRAW_GUN = 0;
    public static final int ACTION_DRAW_GRENADE = 1;
    public static final int ACTION_HOLSTER = 2;
    /** Hoofdstuk 54: een pouch (of de holster) die je draagt openen, om er dingen in te stoppen of uit te halen. */
    public static final int ACTION_OPEN = 3;
    /** Hoofdstuk 55: het geweer uit dit sling-vakje (0 = je rug, 1 = je borst) trekken. */
    public static final int ACTION_DRAW_SLING = 4;

    /** Zoveel sling-vakjes draag je (jouw keuze: twee, een op je rug en een voor je borst). */
    public static final int SLINGS = 2;
    /** Zoveel vakjes heeft de attachment: de drie van GearItem.Kind, en dan de slings. */
    public static final int SLOTS = GearItem.Kind.values().length + SLINGS;

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
        setSlot(player, kind.ordinal(), stack);
        return true;
    }

    private static void setSlot(Player player, int index, ItemStack stack) {
        GearContents slots = GearContents.sized(player.getData(ModAttachments.GEAR).stacks(), SLOTS);
        player.setData(ModAttachments.GEAR, slots.with(index, stack));
    }

    /** Het geweer in dit sling-vakje (0 = je rug, 1 = je borst; een kopie), of leeg. */
    public static ItemStack sling(Player player, int index) {
        if (index < 0 || index >= SLINGS) {
            return ItemStack.EMPTY;
        }
        if (curios()) {
            return CuriosGear.sling(player, index);
        }
        return player.getData(ModAttachments.GEAR).get(GearItem.Kind.values().length + index);
    }

    /** Dit geweer (of leeg) in dit sling-vakje. Geeft false als dat niet kan (Curios zonder zo'n slot). */
    public static boolean setSling(Player player, int index, ItemStack stack) {
        if (index < 0 || index >= SLINGS) {
            return false;
        }
        if (curios()) {
            return CuriosGear.setSling(player, index, stack);
        }
        setSlot(player, GearItem.Kind.values().length + index, stack);
        return true;
    }

    /** Past dit in een sling-vakje: een laser rifle, sniper of shotgun met een sling eraan? */
    public static boolean slingAccepts(ItemStack stack) {
        GunType type = LaserGunItem.typeOf(stack);
        return type != null && type != GunType.PISTOL && LaserGunItem.hasSling(stack);
    }

    /** Het eerste lege sling-vakje, of -1. */
    public static int freeSling(Player player) {
        for (int i = 0; i < SLINGS; i++) {
            if (sling(player, i).isEmpty() && (!curios() || CuriosGear.hasSlingSlot(player, i))) {
                return i;
            }
        }
        return -1;
    }

    /** De inhoud van wat je draagt (kopieën), of een lege lijst als je dat niet draagt. */
    public static List<ItemStack> contents(Player player, GearItem.Kind kind) {
        return GearItem.contents(get(player, kind));
    }

    public static void setContents(Player player, GearItem.Kind kind, List<ItemStack> contents) {
        ItemStack gear = get(player, kind);
        if (gear.isEmpty()) {
            return;
        }
        GearItem.setContents(gear, contents);
        set(player, kind, gear);
    }

    /** Past dit item nog ergens in wat je draagt (een holster of pouch)? Geeft de soort, of null. */
    public static @Nullable GearItem.Kind roomFor(Player player, ItemStack stack) {
        for (GearItem.Kind kind : GearItem.Kind.values()) {
            if (kind.accepts(stack) && freeIndex(player, kind, stack) >= 0) {
                return kind;
            }
        }
        return null;
    }

    /** Kun je dit wegsteken: in een sling-vakje, of in een holster of pouch? */
    public static boolean canStow(Player player, ItemStack stack) {
        return (slingAccepts(stack) && freeSling(player) >= 0) || roomFor(player, stack) != null;
    }

    /** Waar het heen zou gaan, voor op het wiel ("into the Holster", "onto your sling"), of null. */
    public static @Nullable Component stowTarget(Player player, ItemStack stack) {
        if (slingAccepts(stack) && freeSling(player) >= 0) {
            return Component.translatable("screen.imttech.wheel.onto_sling");
        }
        GearItem.Kind kind = roomFor(player, stack);
        return kind == null ? null : Component.translatable("screen.imttech.wheel.into", kind.text());
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

    /** Het pistool uit je holster in je hand; wat je vasthield gaat weg (in zijn eigen plek, of op de grond). */
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
        Firearms.start(player, Firearms.ActionKind.DRAW, player.getInventory().selected, Firearms.DRAW_FROM_HOLSTER, gun);
        return true;
    }

    /**
     * Het geweer uit dit sling-vakje (hoofdstuk 55) in je hand; wat je vasthield gaat weg (het pistool in de holster, een
     * ander geweer met sling in dit vakje, dat net leegkwam). Van je sling trekken duurt langer dan uit de holster.
     */
    public static boolean drawSling(ServerPlayer player, int index) {
        ItemStack gun = sling(player, index);
        if (gun.isEmpty()) {
            tell(player, Component.translatable("gear.imttech.no_sling_gun"));
            return false;
        }
        if (Firearms.isBusy(player)) {
            return false;
        }
        setSling(player, index, ItemStack.EMPTY);
        intoHand(player, gun);
        sound(player, ModSounds.HOLSTER_DRAW.get(), 0.8f);
        Firearms.start(player, Firearms.ActionKind.DRAW, player.getInventory().selected, Firearms.DRAW_FROM_SLING, gun);
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

    /**
     * Dit item in je gekozen vakje. Wat daar lag gaat in zijn eigen plek als dat kan (jouw keuze: "tenzij het een ander
     * gun is dat je kunt holsteren of wegdraaien met de sling"; mijn invulling: ook een granaat, magazijn of cel gaat
     * terug in zijn pouch), anders valt het op de grond. Hoofdstuk 69: ook een granaat die je opraapt, komt zo in je
     * hand (zie weapons/GrenadePickup).
     */
    public static void intoHand(ServerPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        ItemStack held = inventory.getItem(inventory.selected);
        if (!held.isEmpty() && !stow(player, held.copy())) {
            player.drop(held, false);
        }
        inventory.setItem(inventory.selected, stack);
    }

    /**
     * Dit item wegsteken in wat je draagt: een geweer met sling in een leeg sling-vakje, een pistool in de holster, een
     * granaat in de pouch, een magazijn of cel in de magazijnpouch. Geeft true als het (helemaal) weg is.
     */
    private static boolean stow(ServerPlayer player, ItemStack stack) {
        if (slingAccepts(stack)) {
            int free = freeSling(player);
            return free >= 0 && setSling(player, free, stack);
        }
        GearItem.Kind kind = roomFor(player, stack);
        if (kind == null) {
            return false;
        }
        int index = freeIndex(player, kind, stack);
        List<ItemStack> contents = contents(player, kind);
        ItemStack inside = contents.get(index);
        if (inside.isEmpty()) {
            contents.set(index, stack);
        } else {
            int room = inside.getMaxStackSize() - inside.getCount();
            if (room < stack.getCount()) {
                return false; // past niet helemaal: dan liever op de grond dan half kwijt
            }
            inside.grow(stack.getCount());
            contents.set(index, inside);
        }
        setContents(player, kind, contents);
        return true;
    }

    /**
     * Wat je vasthoudt wegsteken: een pistool in de holster, een geweer met sling in een sling-vakje, een granaat in de
     * pouch, een magazijn of een cel in de magazijnpouch.
     */
    public static boolean holsterHand(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        ItemStack held = inventory.getItem(inventory.selected);
        if (held.isEmpty()) {
            tell(player, Component.translatable("gear.imttech.nothing_to_holster"));
            return false;
        }
        if (slingAccepts(held)) {
            int free = freeSling(player);
            if (free < 0) {
                tell(player, Component.translatable("gear.imttech.sling_full"));
                return false;
            }
            if (!setSling(player, free, held.copy())) {
                return false;
            }
            inventory.setItem(inventory.selected, ItemStack.EMPTY);
            sound(player, ModSounds.HOLSTER_DRAW.get(), 0.6f);
            return true;
        }
        GearItem.Kind kind = null;
        for (GearItem.Kind candidate : GearItem.Kind.values()) {
            if (candidate.accepts(held)) {
                kind = candidate;
            }
        }
        if (kind == null) {
            tell(player, Component.translatable(LaserGunItem.typeOf(held) != null && LaserGunItem.typeOf(held) != GunType.PISTOL
                    ? "gear.imttech.needs_sling" : "gear.imttech.does_not_fit"));
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

    // ================================================================ Een pouch openen (hoofdstuk 54)

    /**
     * Een holster of pouch die je draagt openen (uit het wiel of met een eigen toets, jouw notitie: "nu kun je geen nieuwe
     * volle magazijnen in de pouch steken en kun je ook geen lege er uit halen"): een scherm met zijn vakjes en je
     * inventaris (zie GearPouchMenu).
     */
    public static boolean openPouch(ServerPlayer player, int kindOrdinal) {
        GearItem.Kind[] kinds = GearItem.Kind.values();
        if (kindOrdinal < 0 || kindOrdinal >= kinds.length) {
            return false;
        }
        GearItem.Kind kind = kinds[kindOrdinal];
        ItemStack worn = get(player, kind);
        if (worn.isEmpty()) {
            tell(player, Component.translatable("gear.imttech.not_wearing", kind.text()));
            return false;
        }
        open(player, kind, GearPouchMenu.WORN, worn.getHoverName());
        return true;
    }

    /** Rechtsklik met een holster of pouch in je hand: die openen (om hem te vullen voor je hem aantrekt). */
    public static void openHeld(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        GearItem.Kind kind = GearItem.kindOf(held);
        if (kind == null) {
            return;
        }
        open(player, kind, hand == InteractionHand.MAIN_HAND ? GearPouchMenu.MAIN_HAND : GearPouchMenu.OFF_HAND, held.getHoverName());
    }

    private static void open(ServerPlayer player, GearItem.Kind kind, int where, Component title) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new GearPouchMenu(id, inventory, kind, where), title), buf -> {
            buf.writeVarInt(kind.ordinal());
            buf.writeVarInt(where);
        });
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
     * Een klik op een van de eigen vakjes (zie GearClickPayload): de drie naast je harnas (holster, granaatpouch,
     * magazijnpouch; {@code index} 0-2) of de twee sling-vakjes onder het craftraster (3 en 4). Wat je aan je muis hebt
     * gaat erin, wat erin zat komt aan je muis (of ze wisselen). In een sling-vakje past alleen een geweer met een sling.
     */
    public static void click(ServerPlayer player, int index) {
        if (curios() || index < 0 || index >= SLOTS) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (!(menu instanceof InventoryMenu)) {
            return;
        }
        ItemStack carried = menu.getCarried();
        boolean sling = index >= GearItem.Kind.values().length;
        ItemStack worn = sling ? sling(player, index - GearItem.Kind.values().length) : get(player, GearItem.Kind.values()[index]);
        boolean fits = sling ? slingAccepts(carried) : GearItem.kindOf(carried) == GearItem.Kind.values()[index];
        if (carried.isEmpty()) {
            if (worn.isEmpty()) {
                return;
            }
            setSlot(player, index, ItemStack.EMPTY);
            menu.setCarried(worn);
        } else if (fits && carried.getCount() == 1) {
            setSlot(player, index, carried.copy());
            menu.setCarried(worn);
        } else {
            if (sling && LaserGunItem.typeOf(carried) != null) {
                tell(player, Component.translatable("gear.imttech.needs_sling"));
            }
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
