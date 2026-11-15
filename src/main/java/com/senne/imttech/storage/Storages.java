package com.senne.imttech.storage;

import com.senne.imttech.car.CarWork;
import com.senne.imttech.car.RcCarEntity;
import com.senne.imttech.car.RcCars;
import com.senne.imttech.lan.LanGrid;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.security.BaseTrust;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De regels van het opslagsysteem (hoofdstuk 113, jouw wens: "een storage system via de robots"). De onderdelen:
 * <ul>
 *   <li>de <b>Storage Interface</b> ({@link StorageInterfaceBlock}): het scherm waar je bestelt en inlegt;</li>
 *   <li>de <b>Chest Sensor</b> ({@link ChestSensorBlock}): op een kist, voor een live overzicht;</li>
 *   <li>de <b>RC Car</b> (car/CarBrain): die doet het werk, met het programma dat jij schreef
 *       ({@code when ordered("fetch"):}, {@code take(order.item, order.left)}, {@code if item("x") in chest():}...);</li>
 *   <li>de <b>Charging Pad</b> ({@link ChargingPadBlock}): waar de auto zijn cel laadt.</li>
 * </ul>
 * Hier staat wat de server doet met het scherm (openen, de knoppen, wat het toont) en wie wat mag.
 *
 * <b>Wie mag wat.</b> Het scherm van een interface krijgt alleen wie hem mag instellen (wie hem neerzette, of wie
 * vertrouwd is in die basis: de regel van BaseTrust): het toont alles wat er in je kisten ligt. Een auto koppel je alleen
 * als je hem mag besturen (RcCars.mayControl). Tijdens een bestelling werkt de auto met de rechten van wie bestelde (in
 * een beschermde zone opent hij alleen kisten als die speler daar mag bouwen, zie CarWork.chestAt).
 */
public final class Storages {
    private Storages() {}

    /** Zo ver van de interface (blokken) mag een auto staan om hem te koppelen. */
    public static final double LINK_RANGE = 32.0;
    /**
     * Zoveel bytes mag de lijst van de opslag in één bericht hooguit zijn. Minecraft verbreekt de verbinding bij een bericht
     * van meer dan 1 MB, en een opslag vol gevulde shulkerdozen (elke doos is een eigen soort) wordt groot.
     */
    public static final int MAX_STOCK_BYTES = 512 * 1024;

    /** Bij het opstarten (ImtTech): de lijsten, de berichten en het plakken van een sensor op een kist aanmelden. */
    public static void register(IEventBus modEventBus) {
        StorageRegistry.register(modEventBus);
        modEventBus.addListener(Storages::registerPayloads);
        NeoForge.EVENT_BUS.addListener(Storages::onRightClickBlock);
    }

    /** Eigen berichten (net als de rugzakken en de body armor een eigen lijst, los van network/ModNetwork). */
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(StorageActionPayload.TYPE, StorageActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof StorageMenu menu
                    && menu.stillValid(player) && menu.station() != null) {
                handleAction(player, menu.station(), payload);
            }
        });
        // Een lambda, geen verwijzing naar de methode: zo laadt een server nooit code van het scherm.
        registrar.playToClient(StorageStatePayload.TYPE, StorageStatePayload.STREAM_CODEC,
                (payload, context) -> com.senne.imttech.client.storage.StorageClient.onState(payload));
    }

    /**
     * Rechtsklik op een kist met een Chest Sensor in je hand: de sensor komt erop, en de kist gaat niet open. (Zonder dit
     * moest je sneaken, zoals bij elk blok dat je op een kist zet.) We zeggen alleen "gebruik het blok niet"; het
     * neerzetten doet Minecraft daarna zelf, in jouw spel en op de server hetzelfde.
     */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().is(StorageRegistry.CHEST_SENSOR_ITEM.get())
                && event.getLevel().getBlockEntity(event.getPos()) != null
                && !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ChestSensorBlock)) {
            event.setUseBlock(TriState.FALSE);
        }
    }

    // ================================================================ Kisten

    /**
     * De vaste plek van een kist: bij een dubbele kist de kleinste van de twee helften, zodat hij maar één keer meetelt
     * (een sensor op de ene helft en een auto die in de andere kijkt, zien dezelfde kist).
     */
    public static BlockPos canonical(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            return other.compareTo(pos) < 0 ? other.immutable() : pos.immutable();
        }
        return pos.immutable();
    }

    /** Wat er in deze vakjes ligt, per soort (alleen de eerste {@code slots} vakjes). */
    public static Map<ItemKind, Integer> contents(IItemHandler handler, int slots) {
        Map<ItemKind, Integer> items = new LinkedHashMap<>();
        for (int slot = 0; slot < Math.min(slots, handler.getSlots()); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                items.merge(ItemKind.of(stack), stack.getCount(), Integer::sum);
            }
        }
        return items;
    }

    /**
     * Een auto keek in een kist (take, put, check, {@code in chest()}, {@code chest.count(...)}): de Storage Interface
     * waar hij aan hangt, onthoudt wat erin zit. De interface zelf is geen kist. Hangt de auto daar niet meer aan, dan
     * vergeet hij die interface.
     */
    public static void carLooked(ServerLevel level, RcCarEntity car, CarWork.Chest chest) {
        if (chest.station()) {
            return;
        }
        StorageInterfaceBlockEntity station = stationOfCar(level, car);
        // Alleen als wie de auto nu bestuurt die interface mag gebruiken (anders vulde de auto van een ander jouw lijst).
        if (station != null && BaseTrust.wouldAllow(level, station, car.actorId(), car.actorName())) {
            station.saw(canonical(level, chest.pos()), chest.slots(), level.getGameTime());
        }
    }

    /** De Storage Interface waar deze auto aan hangt, als die in deze wereld staat en geladen is; anders null. */
    public static @Nullable StorageInterfaceBlockEntity stationOfCar(ServerLevel level, RcCarEntity car) {
        GlobalPos where = car.station();
        if (where == null || where.dimension() != level.dimension() || !level.isLoaded(where.pos())) {
            return null;
        }
        if (level.getBlockEntity(where.pos()) instanceof StorageInterfaceBlockEntity station && station.linked(car.link())) {
            return station;
        }
        car.setStation(null); // die interface is weg, of de auto hangt er niet meer aan
        return null;
    }

    /**
     * De Storage Interface in hetzelfde net als deze Chest Sensor die hem leest (zie StorageInterfaceBlockEntity.mayRead),
     * of null.
     */
    public static @Nullable StorageInterfaceBlockEntity stationOf(ServerLevel level, BlockPos sensorPos) {
        if (!(level.getBlockEntity(sensorPos) instanceof ChestSensorBlockEntity sensor)) {
            return null;
        }
        for (BlockPos pos : LanGrid.networkOf(level, sensorPos)) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof StorageInterfaceBlockEntity station
                    && station.mayRead(level, sensor)) {
                return station;
            }
        }
        return null;
    }

    // ================================================================ Het scherm

    /** Rechtsklik op de interface: mag je hem instellen, dan gaat zijn scherm open. Anders lees je van wie hij is. */
    public static void open(ServerPlayer player, StorageInterfaceBlockEntity station) {
        if (!BaseTrust.mayEdit(player, station)) {
            String owner = BaseTrust.belongsTo(player.serverLevel(), station);
            player.displayClientMessage((owner.isEmpty() ? Component.translatable("message.imttech.storage.locked")
                    : Component.translatable("message.imttech.storage.not_yours", owner)).withStyle(ChatFormatting.RED), true);
            return;
        }
        player.openMenu(station, buffer -> buffer.writeBlockPos(station.getBlockPos()));
        player.serverLevel().playSound(null, station.getBlockPos(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, 1.5f);
    }

    /** Wat het scherm van deze interface aan deze speler toont, met de lijst van de opslag. */
    public static StorageStatePayload state(ServerPlayer player, StorageInterfaceBlockEntity station) {
        return state(player, station, true);
    }

    /** De wereld van de interface (niet die van de speler: die kan intussen ergens anders zijn). */
    private static ServerLevel levelOf(ServerPlayer player, StorageInterfaceBlockEntity station) {
        return station.getLevel() instanceof ServerLevel level ? level : player.serverLevel();
    }

    /**
     * Wat het scherm van deze interface aan deze speler toont. Zonder {@code withStock} zonder de lijst van de opslag (die
     * veranderde niet; zie StorageMenu.broadcastChanges).
     */
    public static StorageStatePayload state(ServerPlayer player, StorageInterfaceBlockEntity station, boolean withStock) {
        ServerLevel level = levelOf(player, station);
        StorageInterfaceBlockEntity.Shown shown = withStock ? station.shown(level) : null;
        List<StorageStatePayload.Order> orders = new ArrayList<>();
        for (StorageOrder order : station.orders()) {
            orders.add(new StorageStatePayload.Order(order.id, order.kind == StorageOrder.Kind.STORE, order.item.stack(1),
                    order.count, order.moved, order.state.ordinal(), order.carName, order.note));
        }
        List<StorageStatePayload.Car> cars = new ArrayList<>();
        for (UUID link : station.cars()) {
            RcCarEntity car = RcCars.find(link);
            cars.add(car == null || car.level() != level
                    ? new StorageStatePayload.Car(link, station.carName(link), true, StorageStatePayload.CAR_AWAY, -1, false, false)
                    : car(station, car, true));
        }
        for (RcCarEntity car : nearbyCars(player, station, level)) {
            if (cars.size() >= StorageStatePayload.MAX_CARS) {
                break;
            }
            cars.add(car(station, car, false));
        }
        return shown == null
                ? new StorageStatePayload(station.title(), false, 0, 0, List.of(), 0, orders, cars)
                : new StorageStatePayload(station.title(), true, shown.sensors(), shown.storages(), shown.entries(), shown.more(), orders, cars);
    }

    /**
     * Auto's in de buurt die deze speler kan koppelen: alleen die hij mag besturen (zo neemt niemand jouw auto in dienst),
     * op naam (zodat de lijst niet verspringt).
     */
    private static List<RcCarEntity> nearbyCars(ServerPlayer player, StorageInterfaceBlockEntity station, ServerLevel level) {
        List<RcCarEntity> nearby = new ArrayList<>();
        for (RcCarEntity car : level.getEntitiesOfClass(RcCarEntity.class,
                new net.minecraft.world.phys.AABB(station.getBlockPos()).inflate(LINK_RANGE), RcCarEntity::isAlive)) {
            if (!station.linked(car.link()) && RcCars.wouldControl(player, car)) {
                nearby.add(car);
            }
        }
        nearby.sort(Comparator.comparing(RcCarEntity::displayName).thenComparing(car -> car.link().toString()));
        return nearby;
    }

    /**
     * De lijst van de opslag voor het scherm, één keer per stand van de opslag (zie StorageInterfaceBlockEntity.shown): op
     * naam gesorteerd (elke naam één keer opgezocht), zodat de vakjes niet verspringen als een aantal verandert; afgekapt op
     * wat in één bericht past (de rest telt het scherm als "nog zoveel soorten"); en een vingerafdruk van alles wat het
     * scherm ervan toont, in die volgorde. (Gevonden bij de nalezing: elke kijker sorteerde de hele lijst opnieuw bij elke
     * verandering, en de vingerafdruk zag sommige veranderingen niet.)
     */
    static StorageInterfaceBlockEntity.Shown buildShown(ServerLevel level, StorageInterfaceBlockEntity.Stock stock) {
        record Named(String name, String id, StorageStatePayload.Entry entry) {}
        List<Named> named = new ArrayList<>(stock.totals().size());
        stock.totals().forEach((kind, count) -> {
            ItemStack one = kind.stack(1);
            named.add(new Named(one.getHoverName().getString(), CarWork.itemName(one),
                    new StorageStatePayload.Entry(one, count, stock.places().getOrDefault(kind, List.of()).size())));
        });
        named.sort(Comparator.comparing(Named::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Named::id));
        List<StorageStatePayload.Entry> entries = new ArrayList<>(named.size());
        for (Named entry : named) {
            entries.add(entry.entry());
        }
        int fits = fitting(entries, level.registryAccess(), MAX_STOCK_BYTES);
        int more = entries.size() - fits;
        List<StorageStatePayload.Entry> kept = List.copyOf(entries.subList(0, fits));
        long signature = 17L;
        signature = signature * 31L + stock.sensors();
        signature = signature * 31L + stock.storages();
        signature = signature * 31L + more;
        for (StorageStatePayload.Entry entry : kept) {
            signature = signature * 31L + ItemStack.hashItemAndComponents(entry.stack());
            signature = signature * 31L + entry.count();
            signature = signature * 31L + entry.places();
        }
        return new StorageInterfaceBlockEntity.Shown(kept, more, stock.sensors(), stock.storages(), signature);
    }

    /**
     * Hoeveel van deze soorten (van voren af) passen er in een bericht van {@code maxBytes}, en in de lijst van
     * StorageStatePayload? We schrijven ze op proef in een buffer en stoppen als die te groot wordt.
     */
    public static int fitting(List<StorageStatePayload.Entry> entries, net.minecraft.core.RegistryAccess registries, int maxBytes) {
        if (entries.isEmpty()) {
            return 0;
        }
        net.minecraft.network.RegistryFriendlyByteBuf probe = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), registries);
        try {
            int fits = 0;
            for (StorageStatePayload.Entry entry : entries) {
                StorageStatePayload.Entry.STREAM_CODEC.encode(probe, entry);
                if (fits >= StorageStatePayload.MAX_STOCK || probe.writerIndex() > maxBytes) {
                    break;
                }
                fits++;
            }
            return fits;
        } finally {
            probe.release();
        }
    }

    private static StorageStatePayload.Car car(StorageInterfaceBlockEntity station, RcCarEntity car, boolean linked) {
        int status = car.isJammed() ? StorageStatePayload.CAR_JAMMED
                : !StorageInterfaceBlockEntity.charged(car) ? StorageStatePayload.CAR_NO_POWER
                : car.brain().busy() ? StorageStatePayload.CAR_BUSY
                : linked && !station.atHome(car) ? StorageStatePayload.CAR_NOT_HOME : StorageStatePayload.CAR_READY;
        return new StorageStatePayload.Car(car.link(), car.displayName(), linked, status,
                car.hasCell() ? Math.round(car.cellPercent()) : -1,
                car.brain().handles(StorageOrder.Kind.FETCH.code()), car.brain().handles(StorageOrder.Kind.STORE.code()));
    }

    /**
     * Een getal dat verandert zodra het scherm iets anders moet tonen, behalve de lijst van de opslag (die heeft zijn eigen
     * vingerafdruk, zie buildShown): de bestellingen (de revisie van de interface), en alles wat het van de auto's toont.
     * Zo sturen we het bericht alleen als er iets veranderd is (zie StorageMenu).
     */
    public static long restSignature(ServerPlayer player, StorageInterfaceBlockEntity station) {
        ServerLevel level = levelOf(player, station);
        long signature = station.revision();
        for (UUID link : station.cars()) {
            RcCarEntity car = RcCars.find(link);
            signature = signature * 31L + (car == null || car.level() != level ? link.hashCode() : car(station, car, true).hashCode());
        }
        for (RcCarEntity car : nearbyCars(player, station, level)) {
            signature = signature * 31L + car(station, car, false).hashCode();
        }
        return signature;
    }

    /** Een knop uit het scherm. De speler heeft dit scherm open (dat keek wie ons aanriep al na). */
    public static void handleAction(ServerPlayer player, StorageInterfaceBlockEntity station, StorageActionPayload payload) {
        if (!(station.getLevel() instanceof ServerLevel level) || !BaseTrust.mayEdit(player, station)) {
            return;
        }
        switch (payload.op()) {
            case StorageActionPayload.FETCH -> {
                if (!payload.stack().isEmpty() && payload.number() > 0) {
                    station.fetch(level, ItemKind.of(payload.stack()), (int) Math.min(payload.number(), 100_000), player);
                }
            }
            case StorageActionPayload.STORE -> station.store(player);
            case StorageActionPayload.CANCEL -> station.cancel(payload.number());
            case StorageActionPayload.LINK -> link(player, level, station, payload.car());
            case StorageActionPayload.UNLINK -> unlink(level, station, payload.car());
            default -> { }
        }
    }

    /** Koppelt een auto: hij moet in de buurt staan, en je moet hem mogen besturen. */
    private static void link(ServerPlayer player, ServerLevel level, StorageInterfaceBlockEntity station, UUID link) {
        RcCarEntity car = RcCars.find(link);
        if (car == null || car.level() != level
                || car.distanceToSqr(station.getBlockPos().getCenter()) > (LINK_RANGE + 2) * (LINK_RANGE + 2)) {
            player.displayClientMessage(Component.translatable("message.imttech.storage.car_gone").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!RcCars.mayControl(player, car)) {
            player.displayClientMessage(Component.translatable("message.imttech.storage.car_not_yours", car.displayName())
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (station.cars().size() >= StorageInterfaceBlockEntity.MAX_CARS && !station.linked(link)) {
            player.displayClientMessage(Component.translatable("message.imttech.storage.cars_full",
                    StorageInterfaceBlockEntity.MAX_CARS).withStyle(ChatFormatting.RED), true);
            return;
        }
        // Hing hij aan een andere interface? Daar gaat hij eerst af (een auto werkt voor één interface).
        StorageInterfaceBlockEntity old = stationOfCar(level, car);
        if (old != null && old != station) {
            unlink(level, old, link); // was hij daar met een bestelling bezig, dan stopt hij (en die bestelling wacht weer)
        }
        if (station.link(car)) {
            car.setStation(GlobalPos.of(level.dimension(), station.getBlockPos()));
            level.playSound(null, station.getBlockPos(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.4f, 1.6f);
            player.displayClientMessage(Component.translatable("message.imttech.storage.car_linked", car.displayName(),
                    station.title()), true);
        }
    }

    /**
     * Maakt een auto los. Was hij met een bestelling van déze interface bezig, dan stopt hij, en die bestelling wacht weer
     * op een auto (zie StorageInterfaceBlockEntity.unlink). Een bestelling van een andere interface laat hij met rust.
     * (Gevonden bij de nalezing: eerst mislukte de bestelling, en kon losmaken ook de bestelling van een andere interface
     * stoppen.)
     */
    private static void unlink(ServerLevel level, StorageInterfaceBlockEntity station, UUID link) {
        if (!station.unlink(link)) {
            return;
        }
        RcCarEntity car = RcCars.find(link);
        if (car != null && car.level() == level) {
            if (station.getBlockPos().equals(car.brain().orderStation())) {
                car.brain().stop("it was unlinked from " + station.title());
            }
            GlobalPos where = car.station();
            if (where != null && where.dimension() == level.dimension() && where.pos().equals(station.getBlockPos())) {
                car.setStation(null);
            }
        }
        level.playSound(null, station.getBlockPos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, 1.2f);
    }

    /** Stuurt de stand naar wie het scherm open heeft (met of zonder de lijst van de opslag). */
    static void send(ServerPlayer player, StorageInterfaceBlockEntity station, boolean withStock) {
        ModNetwork.sendTo(player, state(player, station, withStock));
    }
}
