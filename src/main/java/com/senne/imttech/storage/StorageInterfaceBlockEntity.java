package com.senne.imttech.storage;

import com.senne.imttech.car.RcCarEntity;
import com.senne.imttech.car.RcCars;
import com.senne.imttech.lan.LanGrid;
import com.senne.imttech.security.BaseTrust;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van een Storage Interface (hoofdstuk 113, jouw wens: "je kunt een item orderen via een storage interface
 * (GUI); deze interface activeert een robot die je hebt geprogrammeerd").
 *
 * Wat hij bijhoudt:
 * <ul>
 *   <li><b>Wat er in je opslag zit</b> ({@link #stock}): alles wat zijn Chest Sensors nu zien (live), en wat een RC Car
 *       zag toen hij in een kist keek (jouw wens: "de interface weet de dingen door de sensors of doordat de robot dit
 *       gecheckt heeft"). Zit er een sensor op een kist, dan telt alleen de sensor.</li>
 *   <li><b>De bestellingen</b> ({@link StorageOrder}): halen (fetch) en opbergen (store). Om de halve seconde kijkt hij of
 *       er een gekoppelde auto vrij is, thuis staat ({@link Home}), genoeg stroom heeft en het juiste stuk programma, en
 *       of wie bestelde hem mag besturen; dan start hij die.</li>
 *   <li><b>De gekoppelde auto's</b>: hooguit {@link #MAX_CARS}. Alleen een gekoppelde auto mag in zijn vakjes.</li>
 *   <li><b>Twee rijen vakjes</b>: IN (wat jij inlegt om op te bergen: daar haalt de auto het) en OUT (wat de auto brengt:
 *       daar pak jij het).</li>
 * </ul>
 * Hij heeft geen stroom nodig. Zijn sensors vindt hij via het netwerk: een netwerkkabel, of een Router (hoofdstuk 112).
 */
public class StorageInterfaceBlockEntity extends BlockEntity implements BaseTrust.Placed, MenuProvider {
    public static final int IN_SLOTS = 9;
    public static final int OUT_SLOTS = 9;
    /** Zoveel RC Cars kun je aan één interface koppelen. */
    public static final int MAX_CARS = 8;
    /** Zoveel bestellingen kunnen er tegelijk wachten of lopen. */
    public static final int MAX_OPEN_ORDERS = 16;
    /** Zoveel afgelopen bestellingen blijven in de lijst staan (de oudste gaat weg). */
    private static final int KEEP_ENDED = 6;
    /** Van zoveel kisten onthoudt hij wat een auto erin zag (de oudste gaat weg). */
    private static final int MAX_SEEN = 128;
    /** Zo vaak (ticks) rekent hij opnieuw uit wat er in de opslag zit, en kijkt hij naar de bestellingen. */
    private static final int REFRESH_TICKS = 10;
    /** Zoveel van één soort kun je in één keer bestellen: wat er in zijn negen afhaalvakjes past. */
    public static final int MAX_ORDER_STACKS = OUT_SLOTS;
    /**
     * Zo vol (procent) moet de cel van een auto minstens zijn om een bestelling te krijgen: een auto die bijna leeg is,
     * zou halverwege stilvallen. (Gevonden bij de nalezing: een auto met een restje stroom kreeg elke bestelling en liet ze
     * allemaal mislukken.) Op een Charging Pad is hij in een paar ticks weer zo ver.
     */
    public static final float MIN_CELL_PERCENT = 2.0f;

    private String name = "";
    private @Nullable UUID owner;
    private String ownerName = "";
    private final SimpleContainer in = new Slots(IN_SLOTS);
    private final SimpleContainer out = new Slots(OUT_SLOTS);
    private final List<UUID> cars = new ArrayList<>();
    /** De naam die een gekoppelde auto het laatst had (voor het scherm, als hij niet in de buurt is). */
    private final Map<UUID, String> carNames = new HashMap<>();
    /** Waar elke gekoppelde auto thuis staat (zie {@link Home}). */
    private final Map<UUID, Home> homes = new HashMap<>();
    /** Wat het scherm van de opslag toont (zie {@link #shown}), en voor welke stand van de opslag. */
    private @Nullable Shown shown;
    private @Nullable Stock shownFor;
    private final List<StorageOrder> orders = new ArrayList<>();
    private long nextOrder = 1;
    /** Per kist (zijn vaste plek, zie Storages.canonical): wat een auto erin zag, en wanneer. */
    private final Map<BlockPos, Seen> seen = new LinkedHashMap<>();
    /** Wat er nu in de opslag zit, en wanneer we dat uitrekenden. */
    private @Nullable Stock stock;
    private long stockTime = Long.MIN_VALUE;
    /** Telt elke verandering die het scherm moet tonen (zie StorageMenu). */
    private int revision;

    /** De vakjes: verandert er iets, dan moet het blok bewaard worden. */
    private final class Slots extends SimpleContainer {
        Slots(int size) {
            super(size);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            StorageInterfaceBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return !isRemoved() && player.distanceToSqr(worldPosition.getCenter()) <= 64.0;
        }
    }

    /** Wat een auto in één kist zag: per soort hoeveel, en op welke speltijd. */
    private record Seen(long time, Map<ItemKind, Integer> items) {}

    /**
     * De thuisplek van een gekoppelde auto: het blok waar hij stond, en de kant waar zijn neus naar wees, toen je hem
     * koppelde. Een bestelling start alleen als hij daar staat, met zijn neus die kant op en zijn arm beneden: zijn
     * programma is voor die plek geschreven. (Gevonden bij de nalezing: een bestelling die na een herstart opnieuw begon,
     * liet de auto vanaf de plek rijden waar hij toevallig stond.) Elders neergezet? Koppel hem daar opnieuw.
     */
    public record Home(BlockPos cell, Direction facing) {
        static Home of(RcCarEntity car) {
            return new Home(car.body().cell().immutable(), car.body().facing());
        }
    }

    /**
     * Wat het scherm van de opslag toont: de soorten op naam (wat in één bericht past), hoeveel soorten er niet in pasten,
     * de sensors en de kisten, en een vingerafdruk van dat alles (verandert er iets wat je ziet, dan verandert die).
     */
    public record Shown(List<StorageStatePayload.Entry> entries, int more, int sensors, int storages, long signature) {}

    /** Eén plek waar een soort ligt: de kist, hoeveel, en of een sensor het nu ziet ({@code live}) of een auto het zag. */
    public record Place(BlockPos pos, int count, boolean live) {}

    /**
     * Wat er in de opslag zit: per soort het totaal en de kisten waar het ligt, en hoeveel sensors en kisten meetellen.
     */
    public record Stock(Map<ItemKind, Integer> totals, Map<ItemKind, List<Place>> places, int sensors, int storages) {
        public int count(ItemKind kind) {
            return totals.getOrDefault(kind, 0);
        }
    }

    public StorageInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(StorageRegistry.STORAGE_INTERFACE_ENTITY.get(), pos, state);
    }

    // ================================================================ Naam en eigenaar

    /** Zijn naam ("Main storage"), of leeg: dan heet hij gewoon Storage Interface. */
    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name.length() > 24 ? name.substring(0, 24) : name;
        changed();
    }

    /** De naam voor het scherm en de meldingen. */
    public String title() {
        return name.isEmpty() ? "Storage Interface" : name;
    }

    @Override
    public @Nullable UUID owner() {
        return owner;
    }

    @Override
    public String ownerName() {
        return ownerName;
    }

    @Override
    public void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    public SimpleContainer in() {
        return in;
    }

    public SimpleContainer out() {
        return out;
    }

    /** Telt elke verandering die het scherm moet tonen. */
    public int revision() {
        return revision;
    }

    /** Er veranderde iets dat bewaard moet worden en dat het scherm moet tonen. */
    private void changed() {
        revision++;
        setChanged();
    }

    // ================================================================ Het scherm

    @Override
    public Component getDisplayName() {
        return name.isEmpty() ? Component.translatable("block.imttech.storage_interface") : Component.literal(name);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new StorageMenu(id, inventory, this);
    }

    // ================================================================ Wat er in de opslag zit

    /** Wat er nu in de opslag zit (hooguit een halve seconde oud). */
    public Stock stock(ServerLevel level) {
        long now = level.getGameTime();
        if (stock == null || now - stockTime >= REFRESH_TICKS || now < stockTime) {
            stock = count(level);
            stockTime = now;
        }
        return stock;
    }

    /**
     * Zoals {@link #stock}, maar hooguit één tick oud: voor het scherm, dat elke tick meekijkt zolang het open staat (jouw
     * regel voor schermen met getallen: ze verversen elke tick).
     */
    public Stock liveStock(ServerLevel level) {
        long now = level.getGameTime();
        if (stock == null || now != stockTime) {
            stock = count(level);
            stockTime = now;
        }
        return stock;
    }

    /** De volgende keer opnieuw uitrekenen (er is net iets genomen of gelegd). */
    private void forgetStock() {
        stock = null;
    }

    /**
     * Telt de opslag: eerst alle kisten met een Chest Sensor in hetzelfde net als deze interface (live), dan de kisten
     * die alleen een auto zag. Een kist die een auto zag maar die er niet meer staat, vergeten we.
     */
    private Stock count(ServerLevel level) {
        Map<BlockPos, Map<ItemKind, Integer>> live = new LinkedHashMap<>();
        int sensors = 0;
        for (BlockPos pos : LanGrid.networkOf(level, worldPosition)) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ChestSensorBlockEntity sensor)
                    || !(level.getBlockState(pos).getBlock() instanceof ChestSensorBlock) || !mayRead(level, sensor)) {
                continue;
            }
            sensors++;
            BlockPos target = ChestSensorBlock.target(level.getBlockState(pos), pos);
            IItemHandler slots = level.isLoaded(target) ? level.getCapability(Capabilities.ItemHandler.BLOCK, target, null) : null;
            if (slots != null) {
                live.putIfAbsent(Storages.canonical(level, target), Storages.contents(slots, Integer.MAX_VALUE));
            }
        }
        Map<ItemKind, Integer> totals = new LinkedHashMap<>();
        Map<ItemKind, List<Place>> places = new HashMap<>();
        live.forEach((pos, items) -> add(totals, places, pos, items, true));
        boolean dropped = seen.keySet().removeIf(pos -> level.isLoaded(pos)
                && level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) == null);
        if (dropped) {
            setChanged();
        }
        int remembered = 0;
        for (Map.Entry<BlockPos, Seen> entry : seen.entrySet()) {
            if (!live.containsKey(entry.getKey())) {
                add(totals, places, entry.getKey(), entry.getValue().items(), false);
                remembered++;
            }
        }
        return new Stock(totals, places, sensors, live.size() + remembered);
    }

    /**
     * Mag deze interface tonen wat deze Chest Sensor ziet? Als de sensor van dezelfde speler is, of als de eigenaar van de
     * interface hem mag instellen (hij is vertrouwd in de basis waar de sensor staat; een sensor van niemand alleen op vrije
     * grond: de regel van BaseTrust). Een netwerk houdt zich niet aan eigenaars en kabels trek je overal, dus zonder dit las
     * iemand jouw kisten door een kabel tot aan jouw netwerk te trekken. (Gevonden bij de nalezing.)
     */
    public boolean mayRead(ServerLevel level, ChestSensorBlockEntity sensor) {
        return Objects.equals(owner, sensor.owner()) || BaseTrust.wouldAllow(level, sensor, owner, ownerName);
    }

    /**
     * Wat het scherm van de opslag toont, één keer uitgerekend per stand van de opslag en gedeeld door iedereen die kijkt
     * (zie Storages.buildShown). Blijft de opslag hetzelfde, dan blijft ook dit hetzelfde.
     */
    public Shown shown(ServerLevel level) {
        Stock now = liveStock(level);
        if (shown == null || shownFor != now && !sameStock(shownFor, now)) {
            shown = Storages.buildShown(level, now);
        }
        shownFor = now;
        return shown;
    }

    private static boolean sameStock(@Nullable Stock a, Stock b) {
        return a != null && a.sensors() == b.sensors() && a.storages() == b.storages() && a.totals().equals(b.totals())
                && a.places().equals(b.places());
    }

    private static void add(Map<ItemKind, Integer> totals, Map<ItemKind, List<Place>> places, BlockPos pos,
                            Map<ItemKind, Integer> items, boolean live) {
        items.forEach((kind, count) -> {
            totals.merge(kind, count, Integer::sum);
            places.computeIfAbsent(kind, key -> new ArrayList<>()).add(new Place(pos, count, live));
        });
    }

    /**
     * Een gekoppelde auto keek in deze kist ({@code pos}: zijn vaste plek): onthoud wat erin zit. Verandert er niets, dan
     * verandert alleen de tijd (en hoeft het scherm niets te doen).
     */
    public void saw(BlockPos pos, IItemHandler slots, long time) {
        Map<ItemKind, Integer> items = Storages.contents(slots, Integer.MAX_VALUE);
        Seen old = seen.remove(pos);
        if (old == null && seen.size() >= MAX_SEEN) {
            seen.remove(seen.keySet().iterator().next()); // de kist die het langst niet bekeken is
        }
        seen.put(pos.immutable(), new Seen(time, items)); // achteraan: het laatst bekeken
        if (old == null || !old.items().equals(items)) {
            forgetStock();
            changed();
        } else {
            setChanged();
        }
    }

    /** Van hoeveel kisten hij onthoudt wat een auto zag (voor de tests). */
    public int seenStorages() {
        return seen.size();
    }

    /**
     * De dichtstbijzijnde kist (bij {@code from}) waar deze soort ligt, of null: dat is wat {@code order.x},
     * {@code order.y} en {@code order.z} in het programma van de auto zeggen.
     */
    public @Nullable Place nearest(ServerLevel level, ItemKind kind, BlockPos from) {
        Place best = null;
        for (Place place : stock(level).places().getOrDefault(kind, List.of())) {
            if (place.count() > 0 && (best == null || place.pos().distSqr(from) < best.pos().distSqr(from))) {
                best = place;
            }
        }
        return best;
    }

    // ================================================================ De gekoppelde auto's

    public List<UUID> cars() {
        return List.copyOf(cars);
    }

    public boolean linked(UUID car) {
        return cars.contains(car);
    }

    /** De naam die deze gekoppelde auto het laatst had. */
    public String carName(UUID car) {
        return carNames.getOrDefault(car, "RC Car");
    }

    /**
     * Koppelt een auto, met de plek waar hij nu staat als zijn thuis ({@link Home}). Geeft false als hij al gekoppeld is
     * of als er al {@link #MAX_CARS} zijn.
     */
    public boolean link(RcCarEntity car) {
        if (cars.contains(car.link()) || cars.size() >= MAX_CARS) {
            return false;
        }
        cars.add(car.link());
        carNames.put(car.link(), car.displayName());
        homes.put(car.link(), Home.of(car));
        changed();
        return true;
    }

    /**
     * Maakt een auto los. Een bestelling waar hij mee bezig was, wacht weer op een auto (wat hij al bracht, telt nog).
     * (Gevonden bij de nalezing: eerst mislukte die bestelling dan.)
     */
    public boolean unlink(UUID car) {
        if (!cars.remove(car)) {
            return false;
        }
        carNames.remove(car);
        homes.remove(car);
        for (StorageOrder order : orders) {
            if (order.state == StorageOrder.State.RUNNING && car.equals(order.car)) {
                order.state = StorageOrder.State.WAITING;
                order.car = null;
                order.note = "interrupted: waiting for an RC Car again";
            }
        }
        changed();
        return true;
    }

    /** De thuisplek van deze gekoppelde auto, of null. */
    public @Nullable Home home(UUID car) {
        return homes.get(car);
    }

    /** Staat deze gekoppelde auto thuis, klaar voor een bestelling: op zijn plek, zijn neus de goede kant op, zijn arm beneden? */
    public boolean atHome(RcCarEntity car) {
        Home home = homes.get(car.link());
        return home != null && car.body().cell().equals(home.cell()) && car.body().facing() == home.facing() && car.arm() == 0;
    }

    /** Heeft deze auto genoeg in zijn cel voor een bestelling (zie {@link #MIN_CELL_PERCENT})? */
    public static boolean charged(RcCarEntity car) {
        return car.hasCell() && car.cellPercent() >= MIN_CELL_PERCENT;
    }

    /**
     * De vakjes zoals een auto ze ziet (zie CarWork.chestAt), of null als deze auto hier niet gekoppeld is, of als wie hem
     * nu bestuurt (wie zijn klus startte) deze interface niet mag gebruiken: dezelfde regel als voor het scherm. (Gevonden
     * bij de nalezing: eerst was gekoppeld genoeg, ook als de auto intussen van een ander was.) Uit de IN-vakjes mag hij
     * nemen (wat iemand inlegde om op te bergen), in de OUT-vakjes mag hij leggen (wat besteld is). Wat hij in de
     * OUT-vakjes legt, telt mee voor zijn bestelling.
     */
    public @Nullable IItemHandler carSlots(RcCarEntity car) {
        if (!cars.contains(car.link()) || !(level instanceof ServerLevel serverLevel)
                || !BaseTrust.wouldAllow(serverLevel, this, car.actorId(), car.actorName())) {
            return null;
        }
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return IN_SLOTS + OUT_SLOTS;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return slot < IN_SLOTS ? in.getItem(slot) : out.getItem(slot - IN_SLOTS);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (slot < IN_SLOTS || stack.isEmpty()) {
                    return stack;
                }
                int index = slot - IN_SLOTS;
                ItemStack there = out.getItem(index);
                int limit = Math.min(out.getMaxStackSize(), stack.getMaxStackSize());
                if (!there.isEmpty() && !ItemStack.isSameItemSameComponents(there, stack)) {
                    return stack;
                }
                int fits = Math.min(stack.getCount(), limit - there.getCount());
                if (fits <= 0) {
                    return stack;
                }
                if (!simulate) {
                    if (there.isEmpty()) {
                        out.setItem(index, stack.copyWithCount(fits));
                    } else {
                        there.grow(fits);
                        out.setChanged();
                    }
                    car.brain().orderDelivered(stack, fits);
                    changed();
                }
                return stack.copyWithCount(stack.getCount() - fits);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (slot >= IN_SLOTS || amount <= 0) {
                    return ItemStack.EMPTY;
                }
                ItemStack there = in.getItem(slot);
                if (there.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                int take = Math.min(amount, there.getCount());
                if (simulate) {
                    return there.copyWithCount(take);
                }
                ItemStack taken = in.removeItem(slot, take);
                changed();
                return taken;
            }

            @Override
            public int getSlotLimit(int slot) {
                return slot < IN_SLOTS ? in.getMaxStackSize() : out.getMaxStackSize();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot >= IN_SLOTS;
            }
        };
    }

    // ================================================================ Bestellingen

    /** De bestellingen, de oudste eerst. */
    public List<StorageOrder> orders() {
        return List.copyOf(orders);
    }

    public @Nullable StorageOrder order(long id) {
        for (StorageOrder order : orders) {
            if (order.id == id) {
                return order;
            }
        }
        return null;
    }

    private int openOrders() {
        int open = 0;
        for (StorageOrder order : orders) {
            if (!order.ended()) {
                open++;
            }
        }
        return open;
    }

    /**
     * Iemand bestelt {@code count} van deze soort (halen). Geeft de bestelling, of null als het niet kan: de lijst is vol,
     * of de interface weet niet waar het ligt (dan staat de melding al in beeld).
     */
    public @Nullable StorageOrder fetch(ServerLevel level, ItemKind item, int count, @Nullable ServerPlayer by) {
        int known = stock(level).count(item);
        int wanted = Math.min(count, Math.min(known, MAX_ORDER_STACKS * item.stack(1).getMaxStackSize()));
        if (wanted <= 0) {
            tell(by, Component.translatable("message.imttech.storage.unknown", item.title()).withStyle(ChatFormatting.RED));
            return null;
        }
        return add(new StorageOrder(nextOrder++, StorageOrder.Kind.FETCH, item, wanted,
                by == null ? owner : by.getUUID(), by == null ? ownerName : by.getGameProfile().getName()), by);
    }

    /**
     * De knop Store: voor elke soort die in de IN-vakjes ligt een bestelling om hem op te bergen. Ligt er van een soort
     * al een bestelling te wachten, dan telt die het nieuwe aantal. Geeft hoeveel bestellingen er nu voor zijn.
     */
    public int store(@Nullable ServerPlayer by) {
        Map<ItemKind, Integer> lying = new LinkedHashMap<>();
        for (int slot = 0; slot < in.getContainerSize(); slot++) {
            ItemStack stack = in.getItem(slot);
            if (!stack.isEmpty()) {
                lying.merge(ItemKind.of(stack), stack.getCount(), Integer::sum);
            }
        }
        int made = 0;
        for (Map.Entry<ItemKind, Integer> entry : lying.entrySet()) {
            StorageOrder waiting = null;
            boolean running = false;
            for (StorageOrder order : orders) {
                if (order.kind == StorageOrder.Kind.STORE && order.item.equals(entry.getKey()) && !order.ended()) {
                    running |= order.state == StorageOrder.State.RUNNING;
                    waiting = order.state == StorageOrder.State.WAITING ? order : waiting;
                }
            }
            if (waiting != null) {
                // Er wacht er al een voor deze soort: die geldt nu voor alles wat er ligt.
                orders.remove(waiting);
                waiting = null;
            } else if (running) {
                continue; // een auto is er al mee bezig
            }
            if (add(new StorageOrder(nextOrder++, StorageOrder.Kind.STORE, entry.getKey(), entry.getValue(),
                    by == null ? owner : by.getUUID(), by == null ? ownerName : by.getGameProfile().getName()), by) != null) {
                made++;
            }
        }
        if (lying.isEmpty()) {
            tell(by, Component.translatable("message.imttech.storage.nothing_to_store").withStyle(ChatFormatting.GOLD));
        }
        return made;
    }

    private @Nullable StorageOrder add(StorageOrder order, @Nullable ServerPlayer by) {
        if (openOrders() >= MAX_OPEN_ORDERS) {
            tell(by, Component.translatable("message.imttech.storage.too_many", MAX_OPEN_ORDERS).withStyle(ChatFormatting.RED));
            return null;
        }
        orders.add(order);
        changed();
        return order;
    }

    /** Een wachtende bestelling weghalen (het kruisje in het scherm). Een lopende stop je op de auto zelf. */
    public boolean cancel(long id) {
        StorageOrder order = order(id);
        if (order == null || order.state == StorageOrder.State.RUNNING) {
            return false;
        }
        orders.remove(order);
        changed();
        return true;
    }

    /**
     * De auto is klaar met deze bestelling (zijn programma liep af, of iemand stopte hem): {@code moved} is hoeveel hij
     * bracht of opborg. Wie bestelde, leest hoe het afliep. Alleen de auto die er nu mee bezig is telt: een auto die er
     * eerder mee bezig was (en intussen is losgemaakt of weg was), beëindigt niet de bestelling van een ander.
     */
    public void orderEnded(long id, UUID car, int moved, String why) {
        StorageOrder order = order(id);
        if (order == null || order.state != StorageOrder.State.RUNNING || !car.equals(order.car)) {
            return;
        }
        order.moved = Math.max(order.moved, moved);
        order.state = order.moved >= order.count ? StorageOrder.State.DONE
                : order.moved > 0 ? StorageOrder.State.PARTIAL : StorageOrder.State.FAILED;
        order.note = order.state == StorageOrder.State.DONE ? "" : why;
        prune();
        forgetStock();
        changed();
        if (level instanceof ServerLevel serverLevel && order.by != null) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(order.by);
            String key = "message.imttech.storage." + order.kind.code() + (order.state == StorageOrder.State.DONE ? "_done"
                    : order.state == StorageOrder.State.PARTIAL ? "_partial" : "_failed");
            tell(player, Component.translatable(key, title(), order.moved, order.count, order.item.title())
                    .withStyle(order.state == StorageOrder.State.DONE ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        }
    }

    /**
     * De auto die deze bestelling doet, bracht of borg er net iets van op ({@code moved} in totaal): dat staat meteen in de
     * bestelling, en wordt zo ook bewaard. (Gevonden bij de nalezing: eerst gebeurde dat om de halve seconde; werd de
     * wereld vlak na de laatste levering bewaard, dan haalde de auto na een herstart alles nog een keer.)
     */
    public void progress(long id, UUID car, int moved) {
        StorageOrder order = order(id);
        if (order != null && order.state == StorageOrder.State.RUNNING && car.equals(order.car) && moved > order.moved) {
            order.moved = moved;
            changed();
        }
    }

    /** Afgelopen bestellingen: alleen de laatste paar blijven staan. */
    private void prune() {
        int ended = 0;
        for (StorageOrder order : orders) {
            if (order.ended()) {
                ended++;
            }
        }
        for (int i = 0; i < orders.size() && ended > KEEP_ENDED; ) {
            if (orders.get(i).ended()) {
                orders.remove(i);
                ended--;
            } else {
                i++;
            }
        }
    }

    private static void tell(@Nullable ServerPlayer player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    // ================================================================ Elke halve seconde

    public void serverTick(ServerLevel level) {
        if ((level.getGameTime() + worldPosition.hashCode()) % REFRESH_TICKS != 0) {
            return;
        }
        tickOrders(level);
    }

    /**
     * Wat de interface om de halve seconde doet (de tests roepen dit ook rechtstreeks aan): de lopende bestellingen
     * nakijken, de gekoppelde auto's bijhouden, en wachtende bestellingen aan een auto geven.
     */
    public void tickOrders(ServerLevel level) {
        // Een lopende bestelling waarvan de auto er niet meer mee bezig is (de wereld laadde opnieuw, de auto reed een stuk
        // wereld in dat niet geladen is, of hij is weg): die wacht weer op een auto. Wat al gebracht was, telt nog.
        boolean ended = false;
        for (StorageOrder order : orders) {
            if (order.state != StorageOrder.State.RUNNING) {
                continue;
            }
            RcCarEntity car = order.car == null ? null : RcCars.find(order.car);
            if (car != null && car.brain().orderId() == order.id && cars.contains(order.car)) {
                continue; // hij is ermee bezig (hoe ver hij is: zie refreshProgress)
            }
            if (order.moved >= order.count) {
                // Alles was al gebracht of opgeborgen: klaar, niet nog een keer.
                order.state = StorageOrder.State.DONE;
                order.note = "";
                ended = true;
            } else {
                order.state = StorageOrder.State.WAITING;
                order.car = null;
                order.note = "interrupted: waiting for an RC Car again";
            }
            changed();
        }
        if (ended) {
            prune();
        }
        // De gekoppelde auto's: een auto die net (weer) is neergezet, hoort wie zijn interface is; een auto die intussen
        // aan een andere interface hangt, gaat hier van de lijst (zijn bestelling hier wacht weer); en we houden hun namen
        // bij.
        net.minecraft.core.GlobalPos here = net.minecraft.core.GlobalPos.of(level.dimension(), worldPosition);
        for (UUID link : List.copyOf(cars)) {
            RcCarEntity car = RcCars.find(link);
            if (car == null || car.level() != level) {
                continue;
            }
            if (car.station() == null) {
                car.setStation(here);
            } else if (!car.station().equals(here)) {
                unlink(link);
                continue;
            }
            if (!homes.containsKey(link) && !car.brain().busy()) {
                homes.put(link, Home.of(car)); // gekoppeld in een wereld van vóór de thuisplek: waar hij nu staat
                changed();
            }
            if (!car.displayName().equals(carNames.get(link))) {
                carNames.put(link, car.displayName());
                changed();
            }
        }
        refreshProgress();
        for (StorageOrder order : List.copyOf(orders)) {
            if (order.state == StorageOrder.State.WAITING) {
                dispatch(level, order);
            }
        }
    }

    /**
     * Hoe ver de auto's met hun lopende bestellingen zijn. Een auto zegt dat zelf meteen (zie {@link #progress}); dit
     * kijkt om de halve seconde nog eens na.
     */
    public void refreshProgress() {
        for (StorageOrder order : orders) {
            if (order.state != StorageOrder.State.RUNNING || order.car == null) {
                continue;
            }
            RcCarEntity car = RcCars.find(order.car);
            if (car != null && car.brain().orderId() == order.id && car.brain().orderMoved() != order.moved) {
                order.moved = car.brain().orderMoved();
                changed();
            }
        }
    }

    /**
     * Zoekt een gekoppelde auto voor deze bestelling en start hem; anders zegt de bestelling waarom hij wacht. De auto moet
     * het juiste stuk programma hebben, vrij zijn, genoeg stroom hebben ({@link #MIN_CELL_PERCENT}), thuis staan
     * ({@link Home}), en wie bestelde moet hem mogen besturen (RcCars.wouldControl, elke keer opnieuw: een auto die
     * intussen van een ander is, of van iemand die niet meer bij de basis hoort, draait nooit zijn programma met jouw
     * rechten). Die laatste drie: gevonden bij de nalezing.
     */
    private void dispatch(ServerLevel level, StorageOrder order) {
        String note;
        if (cars.isEmpty()) {
            note = "no RC Car is linked (tab Cars)";
        } else {
            boolean near = false;
            boolean program = false;
            String empty = null;
            String foreign = null;
            String away = null;
            for (UUID link : cars) {
                RcCarEntity car = RcCars.find(link);
                if (car == null || car.level() != level) {
                    continue;
                }
                near = true;
                if (!car.brain().handles(order.kind.code())) {
                    continue;
                }
                program = true;
                if (car.brain().busy() || car.isJammed()) {
                    continue;
                }
                if (!charged(car)) {
                    empty = empty == null ? car.displayName() : empty;
                    continue;
                }
                if (!RcCars.wouldControl(level, order.by, order.byName, car)) {
                    foreign = foreign == null ? car.displayName() : foreign;
                    continue;
                }
                if (!atHome(car)) {
                    away = away == null ? car.displayName() : away;
                    continue;
                }
                if (car.brain().startOrder(this, order)) {
                    order.state = StorageOrder.State.RUNNING;
                    order.car = link;
                    order.carName = car.displayName();
                    order.note = "";
                    changed();
                    return;
                }
            }
            note = !near ? "its RC Car is not nearby"
                    : !program ? "no linked car has when ordered(\"" + order.kind.code() + "\"):"
                    : away != null ? away + " is not at its home spot (where you linked it, nose that way, arm down): drive it"
                            + " back, or link it again"
                    : foreign != null ? "who ordered this may not control " + foreign + " (it belongs to someone else now)"
                    : empty != null ? "the Energy Cell of " + empty + " is almost empty (a Charging Pad charges it)"
                    : "waiting for a free RC Car (it is busy)";
        }
        if (!note.equals(order.note)) {
            order.note = note;
            changed();
        }
    }

    // ================================================================ Bewaren

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Name", name);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.put("In", ContainerHelper.saveAllItems(new CompoundTag(), in.getItems(), registries));
        tag.put("Out", ContainerHelper.saveAllItems(new CompoundTag(), out.getItems(), registries));
        ListTag carList = new ListTag();
        for (UUID car : cars) {
            CompoundTag item = new CompoundTag();
            item.putUUID("Link", car);
            item.putString("Name", carName(car));
            Home home = homes.get(car);
            if (home != null) {
                item.put("Home", NbtUtils.writeBlockPos(home.cell()));
                item.putByte("Facing", (byte) home.facing().get2DDataValue());
            }
            carList.add(item);
        }
        tag.put("Cars", carList);
        ListTag orderList = new ListTag();
        for (StorageOrder order : orders) {
            orderList.add(order.save(registries));
        }
        tag.put("Orders", orderList);
        tag.putLong("NextOrder", nextOrder);
        ListTag seenList = new ListTag();
        seen.forEach((pos, saw) -> {
            CompoundTag item = new CompoundTag();
            item.put("Pos", NbtUtils.writeBlockPos(pos));
            item.putLong("Time", saw.time());
            ListTag items = new ListTag();
            saw.items().forEach((kind, count) -> {
                CompoundTag stack = new CompoundTag();
                stack.put("Item", kind.stack(1).save(registries));
                stack.putInt("Count", count);
                items.add(stack);
            });
            item.put("Items", items);
            seenList.add(item);
        });
        tag.put("Seen", seenList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = tag.getString("Name");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        in.clearContent();
        out.clearContent();
        ContainerHelper.loadAllItems(tag.getCompound("In"), in.getItems(), registries);
        ContainerHelper.loadAllItems(tag.getCompound("Out"), out.getItems(), registries);
        cars.clear();
        carNames.clear();
        homes.clear();
        for (Tag element : tag.getList("Cars", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) element;
            if (item.hasUUID("Link")) {
                UUID link = item.getUUID("Link");
                cars.add(link);
                carNames.put(link, item.getString("Name"));
                BlockPos home = NbtUtils.readBlockPos(item, "Home").orElse(null);
                if (home != null) {
                    homes.put(link, new Home(home, Direction.from2DDataValue(item.getByte("Facing"))));
                }
            }
        }
        orders.clear();
        for (Tag element : tag.getList("Orders", Tag.TAG_COMPOUND)) {
            StorageOrder order = StorageOrder.load((CompoundTag) element, registries);
            if (order != null) {
                orders.add(order);
            }
        }
        nextOrder = Math.max(1, tag.getLong("NextOrder"));
        seen.clear();
        for (Tag element : tag.getList("Seen", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) element;
            BlockPos pos = NbtUtils.readBlockPos(item, "Pos").orElse(null);
            if (pos == null) {
                continue;
            }
            Map<ItemKind, Integer> items = new LinkedHashMap<>();
            for (Tag entry : item.getList("Items", Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.parseOptional(registries, ((CompoundTag) entry).getCompound("Item"));
                if (!stack.isEmpty()) {
                    items.merge(ItemKind.of(stack), ((CompoundTag) entry).getInt("Count"), Integer::sum);
                }
            }
            seen.put(pos, new Seen(item.getLong("Time"), items));
        }
        stock = null;
        shown = null;
    }

    /** Breek je hem af, dan krijgt het item zijn naam mee (zie de loot table: copy_components). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!name.isEmpty()) {
            components.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Name");
    }
}
