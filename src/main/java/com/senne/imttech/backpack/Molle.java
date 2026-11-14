package com.senne.imttech.backpack;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.CasingEntity;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;

/**
 * De MOLLE-pouches op de rugzak die je draagt (hoofdstuk 72, stap 2): erbij zonder rits.
 * <ul>
 *   <li><b>Magazijnen en granaten</b> ({@link #magazines}, {@link #grenades}): je heup-pouch en de MOLLE-pouches samen,
 *       in die volgorde. R (het keuzescherm, MagPickerScreen) en het wiel tonen ze allemaal; trekken en aanvoeren pakken
 *       eerst uit je heup-pouch (jouw keuze: "als je heup-pouch leeg is, pakt R (of de granaatpouch) hieruit").</li>
 *   <li><b>Openen</b> via het wiel ({@link #openWornPouch}), zonder rits; in je hand met rechtsklik ({@link #openHeld}).</li>
 *   <li><b>De dump pouch</b> raapt elke paar ticks de hulzen rond je voeten op ({@link #tickDump}).</li>
 *   <li><b>De IFAK</b>: in het wiel tik je een genees-item aan en je gebruikt het meteen ({@link #useIfak}); daarna even
 *       wachten ({@code ifakCooldown}).</li>
 * </ul>
 * Alles wat aan een pouch op je rug verandert gaat via {@link #setWorn}: staat de rugzak open (bij jou, of bij een dief),
 * dan via die open inhoud (BackpackInventory), anders rechtstreeks in de rugzak.
 */
public final class Molle {
    private Molle() {}

    /** Wanneer elke speler zijn IFAK weer mag gebruiken (speltijd). */
    private static final Map<UUID, Long> IFAK_READY = new HashMap<>();

    // ================================================================ De pouches op je rug

    /** De MOLLE-pouches op de rugzak die je draagt (kopieën; leeg = geen pouch op die plek), of een lege lijst. */
    public static List<ItemStack> worn(Player player) {
        ItemStack pack = Gear.back(player);
        BackpackInventory open = BackpackInventory.findOpen(pack);
        if (open != null) {
            List<ItemStack> pouches = new ArrayList<>();
            for (int i = 0; i < open.size().molle; i++) {
                pouches.add(open.getItem(open.molleSlot(i)).copy());
            }
            return pouches;
        }
        return BackpackItem.molle(pack);
    }

    /** Deze pouch (of leeg) op MOLLE-plek {@code index} van de rugzak die je draagt. */
    public static void setWorn(Player player, int index, ItemStack pouch) {
        ItemStack pack = Gear.back(player);
        BackpackItem.Size size = BackpackItem.sizeOf(pack);
        if (size == null || index < 0 || index >= size.molle) {
            return;
        }
        BackpackInventory open = BackpackInventory.findOpen(pack);
        if (open != null) {
            open.setItem(open.molleSlot(index), pouch);
            return;
        }
        List<ItemStack> pouches = BackpackItem.molle(pack);
        pouches.set(index, pouch);
        BackpackItem.setMolle(pack, pouches);
        Gear.setBack(player, pack);
    }

    // ================================================================ Magazijnen en granaten

    /**
     * Je heup-pouch (als je die draagt) en dan de MOLLE-pouches van deze soort, als één rijtje vakjes (kopieën). Voor
     * magazijnen zitten sinds hoofdstuk 109 de drie vakjes van je vest ertussen (jouw wens: "deze komen bij de ander 6 mags
     * van een magpouch"): eerst je magazijnpouch, dan je vest, dan de MOLLE-pouches op je rugzak.
     */
    private static List<ItemStack> combined(Player player, GearItem.Kind hip, MolleItem.Kind molle) {
        List<ItemStack> all = new ArrayList<>(Gear.contents(player, hip));
        if (molle == MolleItem.Kind.MAG) {
            all.addAll(com.senne.imttech.armor.Vests.magazines(player));
        }
        for (ItemStack pouch : worn(player)) {
            if (MolleItem.kindOf(pouch) == molle) {
                all.addAll(MolleItem.contents(pouch));
            }
        }
        return all;
    }

    /** Het rijtje terugzetten: het eerste stuk in je heup-pouch, de rest in de MOLLE-pouches van deze soort. */
    private static void setCombined(Player player, GearItem.Kind hip, MolleItem.Kind molle, List<ItemStack> all) {
        int at = 0;
        if (!Gear.get(player, hip).isEmpty()) {
            int size = hip.size();
            Gear.setContents(player, hip, new ArrayList<>(all.subList(0, Math.min(size, all.size()))));
            at = size;
        }
        // Hoofdstuk 109: de drie magazijnvakjes van je vest (als je er een draagt), na je magazijnpouch.
        if (molle == MolleItem.Kind.MAG && com.senne.imttech.armor.VestItem.isVest(Gear.vest(player))) {
            List<ItemStack> vest = new ArrayList<>();
            for (int j = 0; j < com.senne.imttech.armor.VestData.MAGS; j++) {
                vest.add(at + j < all.size() ? all.get(at + j) : ItemStack.EMPTY);
            }
            com.senne.imttech.armor.Vests.setMagazines(player, vest);
            at += com.senne.imttech.armor.VestData.MAGS;
        }
        List<ItemStack> pouches = worn(player);
        for (int i = 0; i < pouches.size(); i++) {
            ItemStack pouch = pouches.get(i);
            if (MolleItem.kindOf(pouch) != molle) {
                continue;
            }
            int size = molle.size();
            List<ItemStack> part = new ArrayList<>();
            for (int j = 0; j < size; j++) {
                part.add(at + j < all.size() ? all.get(at + j) : ItemStack.EMPTY);
            }
            at += size;
            if (!sameStacks(part, MolleItem.contents(pouch))) {
                ItemStack changed = pouch.copy();
                MolleItem.setContents(changed, part);
                setWorn(player, i, changed);
            }
        }
    }

    private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** De magazijnen en cellen in je magazijnpouch en je MOLLE-magazijnpouches (een rijtje, in die volgorde). */
    public static List<ItemStack> magazines(Player player) {
        return combined(player, GearItem.Kind.MAG_POUCH, MolleItem.Kind.MAG);
    }

    public static void setMagazines(Player player, List<ItemStack> all) {
        setCombined(player, GearItem.Kind.MAG_POUCH, MolleItem.Kind.MAG, all);
    }

    /** De granaten in je granaatpouch en je MOLLE-granaatpouches (een rijtje, in die volgorde). */
    public static List<ItemStack> grenades(Player player) {
        return combined(player, GearItem.Kind.GRENADE_POUCH, MolleItem.Kind.GRENADE);
    }

    public static void setGrenades(Player player, List<ItemStack> all) {
        setCombined(player, GearItem.Kind.GRENADE_POUCH, MolleItem.Kind.GRENADE, all);
    }

    /** De MOLLE-pouch waar dit item nog in past (een magazijn, cel of granaat), of null. */
    public static MolleItem.Kind roomFor(Player player, ItemStack stack) {
        for (ItemStack pouch : worn(player)) {
            MolleItem.Kind kind = MolleItem.kindOf(pouch);
            if (kind != null && (kind == MolleItem.Kind.MAG || kind == MolleItem.Kind.GRENADE) && kind.accepts(stack)
                    && freeIndex(MolleItem.contents(pouch), stack) >= 0) {
                return kind;
            }
        }
        return null;
    }

    /**
     * Dit item (helemaal) in een MOLLE-magazijn- of granaatpouch stoppen. Geeft false als het niet past. Sinds hoofdstuk 109
     * gaat een magazijn of cel eerst in een leeg vakje van je vest (dat komt na je magazijnpouch, voor de MOLLE-pouches).
     */
    public static boolean store(Player player, ItemStack stack) {
        if (com.senne.imttech.armor.Vests.store(player, stack)) {
            return true;
        }
        List<ItemStack> pouches = worn(player);
        for (int i = 0; i < pouches.size(); i++) {
            ItemStack pouch = pouches.get(i);
            MolleItem.Kind kind = MolleItem.kindOf(pouch);
            if (kind == null || (kind != MolleItem.Kind.MAG && kind != MolleItem.Kind.GRENADE) || !kind.accepts(stack)) {
                continue;
            }
            List<ItemStack> contents = MolleItem.contents(pouch);
            int index = freeIndex(contents, stack);
            if (index < 0) {
                continue;
            }
            ItemStack inside = contents.get(index);
            if (inside.isEmpty()) {
                contents.set(index, stack.copy());
            } else {
                if (inside.getMaxStackSize() - inside.getCount() < stack.getCount()) {
                    continue;
                }
                inside.grow(stack.getCount());
                contents.set(index, inside);
            }
            ItemStack changed = pouch.copy();
            MolleItem.setContents(changed, contents);
            setWorn(player, i, changed);
            return true;
        }
        return false;
    }

    /** Het vakje waar dit bij kan (eerst bij dezelfde soort, dan een leeg vakje), of -1. */
    private static int freeIndex(List<ItemStack> contents, ItemStack stack) {
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

    // ================================================================ Openen (zonder rits)

    /** De MOLLE-pouch op plek {@code index} van je rugzak openen (uit het wiel): meteen, zonder rits. */
    public static boolean openWornPouch(ServerPlayer player, int index) {
        List<ItemStack> pouches = worn(player);
        if (index < 0 || index >= pouches.size() || MolleItem.kindOf(pouches.get(index)) == null) {
            player.displayClientMessage(Component.translatable("molle.imttech.no_pouch").withStyle(ChatFormatting.RED), true);
            return false;
        }
        ItemStack pouch = pouches.get(index);
        if (pouch.get(BackpackRegistry.PACK_ID.get()) == null) {
            pouch = pouch.copy();
            pouch.set(BackpackRegistry.PACK_ID.get(), UUID.randomUUID());
            setWorn(player, index, pouch);
        }
        open(player, MolleItem.kindOf(pouch), MolleMenu.WORN, index, pouch.getHoverName());
        return true;
    }

    /** Rechtsklik met een MOLLE-pouch in je hand: openen (om hem te vullen voor je hem aan een rugzak hangt). */
    public static void openHeld(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        MolleItem.Kind kind = MolleItem.kindOf(held);
        if (kind != null) {
            open(player, kind, hand == InteractionHand.MAIN_HAND ? MolleMenu.MAIN_HAND : MolleMenu.OFF_HAND, -1, held.getHoverName());
        }
    }

    private static void open(ServerPlayer player, MolleItem.Kind kind, int where, int index, Component title) {
        SimpleMenuProvider provider = new SimpleMenuProvider((id, inventory, p) -> new MolleMenu(id, inventory, kind, where, index), title);
        if (player.connection != null && player.connection.hasChannel(AdvancedOpenScreenPayload.TYPE)) {
            player.openMenu(provider, buf -> {
                buf.writeVarInt(kind.ordinal());
                buf.writeVarInt(where);
                buf.writeVarInt(index);
            });
        } else {
            player.openMenu(provider);
        }
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.HOLSTER_DRAW.get(),
                SoundSource.PLAYERS, 0.5f, 1.2f);
    }

    // ================================================================ De dump pouch

    /**
     * Elke paar ticks: de hulzen rond je voeten in je dump pouch (als die plek heeft). Hoofdstuk 103: elk kaliber is zijn
     * eigen huls (9mm, 7.62x39mm...), dus een pouch die vol zit met de ene soort kan nog plek hebben voor de andere;
     * draag je er twee, dan gaat wat niet in de eerste past in de tweede.
     */
    public static void tickDump(ServerPlayer player) {
        if (player.containerMenu instanceof MolleMenu || !player.isAlive() || player.isSpectator()) {
            return; // met een pouch open niet: dat scherm zou het straks weer overschrijven
        }
        List<ItemStack> pouches = worn(player);
        boolean any = false;
        for (ItemStack pouch : pouches) {
            any |= MolleItem.kindOf(pouch) == MolleItem.Kind.DUMP;
        }
        if (!any) {
            return;
        }
        double radius = ImtConfig.Backpacks.DUMP_POUCH_RADIUS;
        List<CasingEntity> casings = player.serverLevel().getEntitiesOfClass(CasingEntity.class,
                new AABB(player.position(), player.position()).inflate(radius, 2, radius),
                casing -> casing.position().distanceTo(player.position()) <= radius + 0.5);
        if (casings.isEmpty()) {
            return;
        }
        boolean sound = false;
        for (int dump = 0; dump < pouches.size(); dump++) {
            if (MolleItem.kindOf(pouches.get(dump)) != MolleItem.Kind.DUMP) {
                continue;
            }
            ItemStack pouch = pouches.get(dump).copy();
            List<ItemStack> contents = MolleItem.contents(pouch);
            int picked = 0;
            for (CasingEntity casing : casings) {
                if (casing.isRemoved()) {
                    continue; // al in een vorige pouch
                }
                ItemStack one = new ItemStack(casing.caliber().casing());
                int index = freeIndex(contents, one);
                if (index < 0) {
                    continue; // geen plek voor deze soort; een huls van een ander kaliber past misschien nog wel
                }
                ItemStack inside = contents.get(index);
                if (inside.isEmpty()) {
                    contents.set(index, one);
                } else {
                    inside.grow(1);
                }
                casing.discard();
                picked++;
            }
            if (picked > 0) {
                MolleItem.setContents(pouch, contents);
                setWorn(player, dump, pouch);
                sound = true;
            }
        }
        if (sound) {
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CASING_TINK.get(),
                    SoundSource.PLAYERS, 0.35f, 1.3f);
        }
    }

    // ================================================================ De IFAK

    /**
     * Uit het wiel: het genees-item in vakje {@code slot} van de IFAK op plek {@code index} meteen gebruiken (een gouden
     * appel eet je, een drankje drink je; de lege fles komt in je inventaris). Daarna {@code ifakCooldown} wachten.
     */
    public static boolean useIfak(ServerPlayer player, int index, int slot) {
        long now = player.serverLevel().getGameTime();
        Long ready = IFAK_READY.get(player.getUUID());
        if (ready != null && now < ready) {
            return false;
        }
        List<ItemStack> pouches = worn(player);
        if (index < 0 || index >= pouches.size() || MolleItem.kindOf(pouches.get(index)) != MolleItem.Kind.IFAK) {
            return false;
        }
        ItemStack pouch = pouches.get(index).copy();
        List<ItemStack> contents = MolleItem.contents(pouch);
        if (slot < 0 || slot >= contents.size() || !MolleItem.healing(contents.get(slot))) {
            player.displayClientMessage(Component.translatable("molle.imttech.ifak_empty").withStyle(ChatFormatting.RED), true);
            return false;
        }
        ItemStack one = contents.get(slot).split(1);
        Component name = one.getHoverName();
        ItemStack before = one.copy();
        MolleItem.setContents(pouch, contents);
        setWorn(player, index, pouch);
        ItemStack left = one.finishUsingItem(player.serverLevel(), player);
        // Wat overblijft (de lege fles van een drankje) in je inventaris; niet het item zelf (in creative blijft dat over).
        if (!left.isEmpty() && !ItemStack.isSameItem(left, before)) {
            if (!player.getInventory().add(left)) {
                player.drop(left, false);
            }
        }
        IFAK_READY.put(player.getUUID(), now + Math.round(ImtConfig.Backpacks.IFAK_COOLDOWN * 20));
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.PLAYERS, 0.6f, 1.4f);
        player.displayClientMessage(Component.translatable("molle.imttech.ifak_used", name).withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    /** Alles vergeten (de server stopt). */
    public static void clear() {
        IFAK_READY.clear();
    }
}
