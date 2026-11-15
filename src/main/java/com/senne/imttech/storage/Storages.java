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
        if (station != null) {
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

    /** De Storage Interface in hetzelfde net als dit blok (een Chest Sensor), of null. */
    public static @Nullable StorageInterfaceBlockEntity stationOf(ServerLevel level, BlockPos device) {
        for (BlockPos pos : LanGrid.networkOf(level, device)) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof StorageInterfaceBlockEntity station) {
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

    /** Wat het scherm van deze interface aan deze speler toont. */
    public static StorageStatePayload state(ServerPlayer player, StorageInterfaceBlockEntity station) {
        ServerLevel level = player.serverLevel();
        StorageInterfaceBlockEntity.Stock stock = station.liveStock(level);
        List<StorageStatePayload.Entry> entries = new ArrayList<>();
        stock.totals().forEach((kind, count) -> entries.add(new StorageStatePayload.Entry(kind.stack(1), count,
                stock.places().getOrDefault(kind, List.of()).size())));
        // Op naam, zodat de vakjes niet verspringen als een aantal verandert.
        entries.sort(Comparator.comparing((StorageStatePayload.Entry entry) -> entry.stack().getHoverName().getString(),
                String.CASE_INSENSITIVE_ORDER).thenComparing(entry -> CarWork.itemName(entry.stack())));
        // Niet meer dan er in één bericht past: de rest telt het scherm als "nog zoveel soorten".
        int fits = fitting(entries, level.registryAccess(), MAX_STOCK_BYTES);
        int more = entries.size() - fits;
        entries.subList(fits, entries.size()).clear();
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
                    : car(car, true));
        }
        // Auto's in de buurt die je kunt koppelen: alleen die je mag besturen (zo neemt niemand jouw auto in dienst).
        double range = LINK_RANGE;
        for (RcCarEntity car : level.getEntitiesOfClass(RcCarEntity.class,
                new net.minecraft.world.phys.AABB(station.getBlockPos()).inflate(range), RcCarEntity::isAlive)) {
            if (cars.size() >= StorageStatePayload.MAX_CARS) {
                break;
            }
            if (!station.linked(car.link()) && RcCars.wouldControl(player, car)) {
                cars.add(car(car, false));
            }
        }
        return new StorageStatePayload(station.title(), stock.sensors(), stock.storages(), entries, more, orders, cars);
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

    private static StorageStatePayload.Car car(RcCarEntity car, boolean linked) {
        int status = car.isJammed() ? StorageStatePayload.CAR_JAMMED
                : !car.hasCell() || car.cellPercent() <= 0 ? StorageStatePayload.CAR_NO_POWER
                : car.brain().busy() ? StorageStatePayload.CAR_BUSY : StorageStatePayload.CAR_READY;
        return new StorageStatePayload.Car(car.link(), car.displayName(), linked, status,
                car.hasCell() ? Math.round(car.cellPercent()) : -1,
                car.brain().handles(StorageOrder.Kind.FETCH.code()), car.brain().handles(StorageOrder.Kind.STORE.code()));
    }

    /**
     * Een getal dat verandert zodra het scherm iets anders moet tonen: zo sturen we het bericht alleen als er iets
     * veranderd is (zie StorageMenu).
     */
    public static long signature(ServerPlayer player, StorageInterfaceBlockEntity station) {
        ServerLevel level = player.serverLevel();
        station.refreshProgress();
        long signature = station.revision() * 31L + station.liveStock(level).totals().hashCode();
        for (UUID link : station.cars()) {
            RcCarEntity car = RcCars.find(link);
            signature = signature * 31L + (car == null ? 7 : car(car, true).hashCode());
        }
        for (RcCarEntity car : level.getEntitiesOfClass(RcCarEntity.class,
                new net.minecraft.world.phys.AABB(station.getBlockPos()).inflate(LINK_RANGE), RcCarEntity::isAlive)) {
            signature = signature * 31L + car.link().hashCode();
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

    /** Maakt een auto los; was hij met een bestelling van deze interface bezig, dan stopt hij. */
    private static void unlink(ServerLevel level, StorageInterfaceBlockEntity station, UUID link) {
        if (!station.unlink(link)) {
            return;
        }
        RcCarEntity car = RcCars.find(link);
        if (car != null) {
            if (car.brain().orderId() != 0) {
                car.brain().stop();
            }
            car.setStation(null);
        }
        level.playSound(null, station.getBlockPos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, 1.2f);
    }

    /** Stuurt de stand naar wie het scherm open heeft. */
    static void send(ServerPlayer player, StorageInterfaceBlockEntity station) {
        ModNetwork.sendTo(player, state(player, station));
    }
}
