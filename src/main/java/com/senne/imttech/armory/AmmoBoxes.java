package com.senne.imttech.armory;

import com.senne.imttech.guns.Caliber;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.Firearms;
import com.senne.imttech.guns.GunSkillHooks;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.LaserGunItem;
import com.senne.imttech.weapons.LaserGuns;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Wat er gebeurt als je op een Ammo Box klikt (hoofdstuk 67), zonder scherm. Jouw keuzes:
 * <ul>
 *   <li><b>Rechtsklik met spullen</b>: de stapel in je hand erin. <b>Sneak + rechtsklik</b>: alles van die soort uit je
 *       inventaris erin.</li>
 *   <li><b>Rechtsklik met een lege hand</b>: een stapel eruit (of één magazijn of cel: de volste).
 *       <b>Sneak + rechtsklik met een lege hand</b>: er één uit. Het gaat in je inventaris, niet in je hand, zodat je
 *       kunt blijven klikken (anders stopte de volgende klik het er meteen weer in).</li>
 *   <li><b>Rechtsklik ingedrukt houden met een magazijn op een doos patronen</b>: patroon na patroon erin, net zo snel als
 *       met R (dezelfde handeling van {@link Firearms}, dus ook dezelfde animatie en ervaring). Met sneak gaan ze er
 *       patroon na patroon uit, terug in de doos (mijn invulling).</li>
 *   <li><b>Een lasergeweer op een doos cellen</b>: je cel gaat erin en de volste cel komt in je geweer, net zo lang als
 *       herladen uit je pouch. <b>Een Glock op een doos magazijnen</b> doet hetzelfde met je magazijn (mijn invulling,
 *       zoals de cellen).</li>
 * </ul>
 * Minecraft herhaalt een rechtsklik op een blok vanzelf elke 4 ticks zolang je hem inhoudt; zo wordt "inhouden"
 * vanzelf "patroon na patroon".
 */
public final class AmmoBoxes {
    private AmmoBoxes() {}

    /** Wat een klik met dit item op deze doos doet (hetzelfde in jouw spel en op de server). */
    public enum Use {
        /** De stapel in je hand erin. */
        INSERT,
        /** Alles van deze soort uit je inventaris erin (sneak). */
        INSERT_ALL,
        /** Eén patroon uit de doos in het magazijn in je hand. */
        LOAD,
        /** Eén patroon uit het magazijn in je hand terug in de doos (sneak). */
        UNLOAD,
        /** Je magazijn wisselen voor het volste uit de doos. */
        MAG_SWAP,
        /** Je cel wisselen voor de volste uit de doos. */
        CELL_SWAP,
        /** Het past niet bij wat erin ligt (een melding). */
        WRONG,
        /** Niets met de doos te maken: het item doet wat het altijd doet (een blok neerzetten, richten...). */
        NONE
    }

    /** Uit welke doos elke speler nu patronen laadt of wisselt. */
    private static final Map<UUID, BlockPos> SOURCES = new HashMap<>();

    /** Wat een rechtsklik met {@code held} op deze doos doet. */
    public static Use useFor(Player player, ItemStack held, AmmoBoxBlockEntity box) {
        if (held.isEmpty()) {
            return Use.NONE;
        }
        ItemStack sort = box.sort();
        boolean sneak = player.isSecondaryUseActive();
        // Hoofdstuk 103: een magazijn laadt alleen uit een doos met de patronen van zijn eigen wapen (een magazijn van de
        // AK-47 op een doos 9mm krijgt de melding van WRONG hieronder).
        FirearmType magazine = MagazineItem.typeOf(held);
        if (magazine != null && sort.is(magazine.caliber.round())) {
            return sneak ? Use.UNLOAD : Use.LOAD;
        }
        FirearmType gun = FirearmItem.typeOf(held);
        if (gun != null) {
            return MagazineItem.typeOf(sort) == gun ? Use.MAG_SWAP : Use.NONE;
        }
        if (LaserGunItem.typeOf(held) != null) {
            return sort.is(ModItems.ENERGY_CELL.get()) ? Use.CELL_SWAP : Use.NONE;
        }
        if (!AmmoBoxBlockEntity.accepts(held)) {
            return Use.NONE;
        }
        if (!box.fits(held)) {
            return Use.WRONG;
        }
        return sneak ? Use.INSERT_ALL : Use.INSERT;
    }

    /** Rechtsklik met een item (op de server): doen wat {@link #useFor} zegt. */
    public static void use(ServerPlayer player, BlockPos pos, AmmoBoxBlockEntity box, ItemStack held) {
        switch (useFor(player, held, box)) {
            case INSERT -> insert(player, box, held);
            case INSERT_ALL -> insertAll(player, box, held);
            case LOAD -> startLoad(player, pos, box);
            case UNLOAD -> startUnload(player, pos, box);
            case MAG_SWAP -> startMagSwap(player, pos, box);
            case CELL_SWAP -> startCellSwap(player, pos, box);
            case WRONG -> tell(player, Component.translatable("armory.imttech.box.wrong_sort", box.sort().getHoverName()));
            case NONE -> {
            }
        }
    }

    // ================================================================ Erin en eruit

    private static void insert(ServerPlayer player, AmmoBoxBlockEntity box, ItemStack held) {
        if (box.room(held) <= 0) {
            tellFull(player, box);
            return;
        }
        ItemStack kind = held.copyWithCount(1);
        int moved = box.insert(held, held.getCount());
        sound(player, box, SoundEvents.BUNDLE_INSERT);
        report(player, box, kind, moved);
    }

    private static void insertAll(ServerPlayer player, AmmoBoxBlockEntity box, ItemStack held) {
        if (box.room(held) <= 0) {
            tellFull(player, box);
            return;
        }
        ItemStack kind = held.copyWithCount(1);
        Inventory inventory = player.getInventory();
        int moved = box.insert(held, held.getCount());
        for (int slot = 0; slot < inventory.getContainerSize() && box.room(kind) > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(kind.getItem()) && slot != inventory.selected) {
                moved += box.insert(stack, stack.getCount());
            }
        }
        inventory.setChanged();
        sound(player, box, SoundEvents.BUNDLE_INSERT);
        report(player, box, kind, moved);
    }

    /** Rechtsklik met een lege hand: een stapel eruit, of met sneak één. */
    public static void take(ServerPlayer player, AmmoBoxBlockEntity box, boolean one) {
        if (box.isEmpty()) {
            tell(player, Component.translatable("armory.imttech.box.empty"));
            return;
        }
        ItemStack kind = box.sort();
        ItemStack taken = box.take(one ? 1 : kind.getMaxStackSize());
        if (taken.isEmpty()) {
            return;
        }
        int count = taken.getCount();
        giveBesideHand(player, taken);
        sound(player, box, SoundEvents.BUNDLE_REMOVE_ONE);
        player.displayClientMessage(Component.translatable("armory.imttech.box.took", count, kind.getHoverName(),
                box.count(), AmmoBoxBlockEntity.capacity(kind)).withStyle(ChatFormatting.GOLD), true);
    }

    /**
     * In je inventaris, maar niet in je hand (die is leeg: anders stopte de volgende rechtsklik het er weer in). Eerst bij
     * een stapel die er al is, dan in het eerste lege vakje; past het nergens, dan toch in je hand, en anders op de grond.
     */
    static void giveBesideHand(ServerPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size() && !stack.isEmpty(); slot++) {
            ItemStack there = inventory.items.get(slot);
            if (slot != inventory.selected && ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
                int add = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
                there.grow(add);
                stack.shrink(add);
            }
        }
        for (int slot = 0; slot < inventory.items.size() && !stack.isEmpty(); slot++) {
            if (slot != inventory.selected && inventory.items.get(slot).isEmpty()) {
                inventory.items.set(slot, stack.copy());
                stack.setCount(0);
            }
        }
        if (!stack.isEmpty()) {
            Firearms.give(player, stack);
        }
        inventory.setChanged();
    }

    private static void report(ServerPlayer player, AmmoBoxBlockEntity box, ItemStack kind, int moved) {
        player.displayClientMessage(Component.translatable("armory.imttech.box.put", moved, kind.getHoverName(), box.count(),
                AmmoBoxBlockEntity.capacity(kind)).withStyle(ChatFormatting.GOLD), true);
    }

    private static void tellFull(ServerPlayer player, AmmoBoxBlockEntity box) {
        tell(player, Component.translatable("armory.imttech.box.full", AmmoBoxBlockEntity.capacity(box.sort())));
    }

    // ================================================================ Een magazijn laden (rechtsklik inhouden)

    /** Eén patroon uit de doos in het magazijn in je hand (de handeling LOAD_ROUND van de Glock, met de doos als bron). */
    private static void startLoad(ServerPlayer player, BlockPos pos, AmmoBoxBlockEntity box) {
        ItemStack magazine = player.getMainHandItem();
        if (Firearms.isBusy(player) || LaserGuns.isReloading(player)) {
            return; // de vorige patroon gaat er nog in; inhouden = straks de volgende
        }
        if (MagazineItem.isFull(magazine)) {
            tell(player, Component.translatable("gun.imttech.magazine_full"));
            return;
        }
        if (box.isEmpty()) {
            tell(player, Component.translatable("armory.imttech.box.empty"));
            return;
        }
        SOURCES.put(player.getUUID(), pos.immutable());
        Firearms.start(player, Firearms.ActionKind.LOAD_ROUND, player.getInventory().selected, Firearms.FROM_BOX, null);
    }

    /** Sneak: één patroon uit het magazijn in je hand terug in de doos. */
    private static void startUnload(ServerPlayer player, BlockPos pos, AmmoBoxBlockEntity box) {
        ItemStack magazine = player.getMainHandItem();
        if (Firearms.isBusy(player) || LaserGuns.isReloading(player)) {
            return;
        }
        if (MagazineItem.rounds(magazine) <= 0) {
            tell(player, Component.translatable("gun.imttech.magazine_empty"));
            return;
        }
        FirearmType type = MagazineItem.typeOf(magazine);
        if (type == null || box.room(new ItemStack(type.caliber.round())) <= 0) {
            tellFull(player, box);
            return;
        }
        SOURCES.put(player.getUUID(), pos.immutable());
        Firearms.start(player, Firearms.ActionKind.UNLOAD_ROUND, player.getInventory().selected, Firearms.FROM_BOX, null);
    }

    /** De doos waaruit deze speler laadt, als hij er nog bij kan (anders null). */
    private static @Nullable AmmoBoxBlockEntity source(ServerPlayer player) {
        BlockPos pos = SOURCES.get(player.getUUID());
        if (pos == null || !player.canInteractWithBlock(pos, 1.0)) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof AmmoBoxBlockEntity box ? box : null;
    }

    /**
     * De handeling is klaar: één patroon van dit kaliber uit de doos (geeft false als hij leeg is, er een ander kaliber
     * in ligt, of je er niet meer bij kunt).
     */
    public static boolean takeRound(ServerPlayer player, Caliber caliber) {
        AmmoBoxBlockEntity box = source(player);
        if (box == null || !box.sort().is(caliber.round())) {
            return false;
        }
        return !box.take(1).isEmpty();
    }

    /** De handeling is klaar: één patroon terug in de doos (geeft false als hij vol is; dan gaat hij in je inventaris). */
    public static boolean putRound(ServerPlayer player, Caliber caliber) {
        AmmoBoxBlockEntity box = source(player);
        return box != null && box.insert(new ItemStack(caliber.round()), 1) == 1;
    }

    // ================================================================ Wisselen: een magazijn of een cel

    /** Een Glock op een doos magazijnen: het magazijn eruit, het volste uit de doos erin (zoals herladen uit je pouch). */
    private static void startMagSwap(ServerPlayer player, BlockPos pos, AmmoBoxBlockEntity box) {
        ItemStack gun = player.getMainHandItem();
        if (Firearms.isBusy(player) || LaserGuns.isReloading(player)) {
            return;
        }
        if (FirearmItem.jam(gun) != FirearmItem.JAM_NONE) {
            tell(player, Component.translatable("gun.imttech.jammed"));
            return;
        }
        int current = FirearmItem.hasMagazine(gun) ? FirearmItem.roundsInMagazine(gun) : -1;
        if (box.fullest() <= current) {
            tell(player, Component.translatable("armory.imttech.box.no_fuller_magazine"));
            return;
        }
        SOURCES.put(player.getUUID(), pos.immutable());
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.GLOCK_MAG_OUT.get(),
                SoundSource.PLAYERS, 0.8f, 1.0f);
        Firearms.start(player, Firearms.ActionKind.RELOAD_POUCH, player.getInventory().selected, Firearms.BOX_SLOT, gun);
    }

    /** Het wisselen is klaar: het volste magazijn uit de doos in de Glock, het oude in de doos. */
    public static void finishMagSwap(ServerPlayer player, ItemStack gun) {
        AmmoBoxBlockEntity box = source(player);
        FirearmType type = FirearmItem.typeOf(gun);
        if (box == null || type == null || MagazineItem.typeOf(box.sort()) != type) {
            return;
        }
        int current = FirearmItem.hasMagazine(gun) ? FirearmItem.roundsInMagazine(gun) : -1;
        if (box.fullest() <= current) {
            return;
        }
        ItemStack fresh = box.takeFullest();
        ItemStack old = FirearmItem.magazine(gun);
        FirearmItem.setMagazine(gun, fresh);
        if (!old.isEmpty() && box.insert(old, 1) == 0) {
            giveBesideHand(player, old);
        }
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.GLOCK_MAG_IN.get(),
                SoundSource.PLAYERS, 0.8f, 1.0f);
        GunSkillHooks.reloaded(player, gun);
    }

    /** Een lasergeweer op een doos cellen: je cel erin, de volste uit de doos in je geweer. */
    private static void startCellSwap(ServerPlayer player, BlockPos pos, AmmoBoxBlockEntity box) {
        ItemStack gun = player.getMainHandItem();
        if (Firearms.isBusy(player) || LaserGuns.isReloading(player)) {
            return;
        }
        if (box.fullest() <= LaserGunItem.cell(gun) + 0.001f) {
            tell(player, Component.translatable("armory.imttech.box.no_fuller_cell"));
            return;
        }
        SOURCES.put(player.getUUID(), pos.immutable());
        LaserGuns.startReloadFromBox(player);
    }

    /** Het wisselen is klaar: de volste cel uit de doos in het geweer, de oude in de doos. */
    public static void finishCellSwap(ServerPlayer player, ItemStack gun) {
        AmmoBoxBlockEntity box = source(player);
        if (box == null || !box.sort().is(ModItems.ENERGY_CELL.get()) || LaserGunItem.typeOf(gun) == null) {
            return;
        }
        float old = LaserGunItem.cell(gun);
        if (box.fullest() <= old + 0.001f) {
            return;
        }
        float fresh = EnergyCellItem.charge(box.takeFullest());
        if (old >= 0) {
            ItemStack cell = EnergyCellItem.withCharge(old);
            if (box.insert(cell, 1) == 0) {
                giveBesideHand(player, cell);
            }
        }
        LaserGunItem.setCell(gun, fresh);
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.LASER_GUN_CELL_IN.get(),
                SoundSource.PLAYERS, 0.8f, 1.0f);
        GunSkillHooks.reloaded(player, gun);
    }

    // ================================================================ Klikken met sneak

    /**
     * Minecraft slaat het blok over als je sneakt met iets in je hand (dan zet je bijvoorbeeld een blok ernaast). Voor
     * een Ammo Box willen we sneak + rechtsklik juist zelf (alles erin, er één uit, een patroon terug), dus zeggen we
     * hier: gebruik het blok toch, als wat je vasthoudt iets met de doos te maken heeft (of je hand leeg is). Ook voor
     * het Pegboard: sneak + rechtsklik met een lege hand draait een wapen, ook als je in je andere hand iets hebt.
     * Gebeurt in jouw spel en op de server.
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isSecondaryUseActive()) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        var level = event.getLevel();
        var pos = event.getPos();
        if (level.getBlockEntity(pos) instanceof AmmoBoxBlockEntity box) {
            if (held.isEmpty() || useFor(player, held, box) != Use.NONE) {
                event.setUseBlock(TriState.TRUE);
            }
        } else if (level.getBlockEntity(pos) instanceof PegboardBlockEntity && held.isEmpty()) {
            event.setUseBlock(TriState.TRUE);
        }
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SOURCES.remove(event.getEntity().getUUID());
    }

    public static void clear() {
        SOURCES.clear();
    }

    // ================================================================ Kleine hulpjes

    private static void sound(ServerPlayer player, AmmoBoxBlockEntity box, SoundEvent sound) {
        BlockPos pos = box.getBlockPos();
        player.serverLevel().playSound(null, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, sound, SoundSource.BLOCKS,
                0.8f, 0.9f + player.getRandom().nextFloat() * 0.2f);
    }

    private static void tell(ServerPlayer player, Component text) {
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.RED), true);
    }
}
