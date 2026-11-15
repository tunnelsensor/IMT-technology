package com.senne.imttech.storage;

import com.senne.imttech.ImtTech;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Bericht van server naar speler (hoofdstuk 113): wat het scherm van een Storage Interface toont. De server stuurt het
 * als je het scherm opent, en daarna telkens als er iets verandert (hooguit twee keer per seconde): wat er in de opslag
 * zit, de bestellingen, en de auto's.
 *
 * @param name      de naam van de interface
 * @param withStock zit de lijst van de opslag erin (sensors, storages, stock, more)? Zo niet, dan veranderde die niet, en
 *                  houdt het scherm de vorige (zie {@link #withStockOf}). De lijst kan groot zijn; de bestellingen en de
 *                  auto's zijn klein en gaan elke tick mee als ze veranderen. (Gevonden bij de nalezing.)
 * @param sensors  hoeveel Chest Sensors hij via zijn netwerk ziet
 * @param storages hoeveel kisten hij kent (met een sensor, of gezien door een auto)
 * @param stock    wat er in de opslag zit, per soort
 * @param more     hoeveel soorten er nog meer zijn dan in {@code stock} staan (ze pasten niet in één bericht)
 * @param orders   de bestellingen, de oudste eerst
 * @param cars     de gekoppelde auto's, en de auto's in de buurt die je kunt koppelen
 */
public record StorageStatePayload(String name, boolean withStock, int sensors, int storages, List<Entry> stock, int more,
                                  List<Order> orders, List<Car> cars) implements CustomPacketPayload {
    /** Een leeg scherm (tot het eerste bericht er is). */
    public static final StorageStatePayload EMPTY = new StorageStatePayload("", true, 0, 0, List.of(), 0, List.of(), List.of());

    /** Dit bericht, met de lijst van de opslag uit {@code old} als die er zelf niet in zit. */
    public StorageStatePayload withStockOf(StorageStatePayload old) {
        return withStock ? this : new StorageStatePayload(name, true, old.sensors(), old.storages(), old.stock(), old.more(), orders, cars);
    }

    /** Zoveel soorten, bestellingen en auto's passen er hooguit in één bericht. */
    public static final int MAX_STOCK = 2048;
    public static final int MAX_ORDERS = 64;
    public static final int MAX_CARS = 64;

    /**
     * Eén soort in de opslag.
     *
     * @param stack  één voorwerp van die soort (met alles wat erop zit)
     * @param count  hoeveel er in totaal liggen
     * @param places in hoeveel kisten
     */
    public record Entry(ItemStack stack, int count, int places) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Entry::stack,
                ByteBufCodecs.VAR_INT, Entry::count,
                ByteBufCodecs.VAR_INT, Entry::places,
                Entry::new);
    }

    /**
     * Eén bestelling.
     *
     * @param id    zijn nummer (voor de knop om hem weg te halen)
     * @param store true = opbergen, false = halen
     * @param stack één voorwerp van de soort
     * @param count hoeveel
     * @param moved hoeveel er al gebracht of opgeborgen is
     * @param state de stand (het nummer van StorageOrder.State)
     * @param car   de naam van de auto die ermee bezig is (of was), of leeg
     * @param note  waarom hij wacht of wat er misging (Engels), of leeg
     */
    public record Order(long id, boolean store, ItemStack stack, int count, int moved, int state, String car, String note) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Order> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Order decode(RegistryFriendlyByteBuf buffer) {
                return new Order(buffer.readVarLong(), buffer.readBoolean(), ItemStack.STREAM_CODEC.decode(buffer), buffer.readVarInt(),
                        buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(64), buffer.readUtf(160));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Order order) {
                buffer.writeVarLong(order.id());
                buffer.writeBoolean(order.store());
                ItemStack.STREAM_CODEC.encode(buffer, order.stack());
                buffer.writeVarInt(order.count());
                buffer.writeVarInt(order.moved());
                buffer.writeVarInt(order.state());
                buffer.writeUtf(clip(order.car(), 64), 64);
                buffer.writeUtf(clip(order.note(), 160), 160);
            }
        };

        public StorageOrder.State stateValue() {
            StorageOrder.State[] all = StorageOrder.State.values();
            return all[Math.max(0, Math.min(all.length - 1, state))];
        }
    }

    /** Hoe een auto ervoor staat. */
    public static final int CAR_READY = 0;
    public static final int CAR_BUSY = 1;
    public static final int CAR_AWAY = 2;
    public static final int CAR_NO_POWER = 3;
    public static final int CAR_JAMMED = 4;
    /** Gekoppeld, maar hij staat niet op zijn thuisplek (zie StorageInterfaceBlockEntity.Home). */
    public static final int CAR_NOT_HOME = 5;

    /**
     * Eén auto.
     *
     * @param link    zijn vaste nummer
     * @param name    zijn naam
     * @param linked  hangt hij aan deze interface? (Anders staat hij in de buurt en kun je hem koppelen.)
     * @param status  een van de getallen hierboven
     * @param battery hoe vol zijn cel is (0 tot 100), of -1 zonder cel of als hij niet in de buurt is
     * @param fetch   heeft zijn programma {@code when ordered("fetch"):}?
     * @param store   heeft zijn programma {@code when ordered("store"):}?
     */
    public record Car(UUID link, String name, boolean linked, int status, int battery, boolean fetch, boolean store) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Car> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Car decode(RegistryFriendlyByteBuf buffer) {
                UUID link = UUIDUtil.STREAM_CODEC.decode(buffer);
                String name = buffer.readUtf(64);
                int flags = buffer.readByte();
                return new Car(link, name, (flags & 1) != 0, buffer.readVarInt(), buffer.readVarInt() - 1, (flags & 2) != 0,
                        (flags & 4) != 0);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Car car) {
                UUIDUtil.STREAM_CODEC.encode(buffer, car.link());
                buffer.writeUtf(clip(car.name(), 64), 64);
                buffer.writeByte((car.linked() ? 1 : 0) | (car.fetch() ? 2 : 0) | (car.store() ? 4 : 0));
                buffer.writeVarInt(car.status());
                buffer.writeVarInt(car.battery() + 1);
            }
        };
    }

    private static String clip(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    public static final Type<StorageStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "storage_state"));

    /** Meer dan zes velden: de stukken schrijven we zelf achter elkaar. */
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageStatePayload> STREAM_CODEC = new StreamCodec<>() {
        private final StreamCodec<RegistryFriendlyByteBuf, List<Entry>> stockCodec = Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STOCK));
        private final StreamCodec<RegistryFriendlyByteBuf, List<Order>> orderCodec = Order.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ORDERS));
        private final StreamCodec<RegistryFriendlyByteBuf, List<Car>> carCodec = Car.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CARS));

        @Override
        public StorageStatePayload decode(RegistryFriendlyByteBuf buffer) {
            String name = buffer.readUtf(64);
            boolean withStock = buffer.readBoolean();
            int sensors = withStock ? buffer.readVarInt() : 0;
            int storages = withStock ? buffer.readVarInt() : 0;
            List<Entry> stock = withStock ? stockCodec.decode(buffer) : List.of();
            int more = withStock ? buffer.readVarInt() : 0;
            return new StorageStatePayload(name, withStock, sensors, storages, stock, more, orderCodec.decode(buffer), carCodec.decode(buffer));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, StorageStatePayload state) {
            buffer.writeUtf(clip(state.name(), 64), 64);
            buffer.writeBoolean(state.withStock());
            if (state.withStock()) {
                buffer.writeVarInt(state.sensors());
                buffer.writeVarInt(state.storages());
                stockCodec.encode(buffer, state.stock());
                buffer.writeVarInt(state.more());
            }
            orderCodec.encode(buffer, state.orders());
            carCodec.encode(buffer, state.cars());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
