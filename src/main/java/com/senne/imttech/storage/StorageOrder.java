package com.senne.imttech.storage;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Eén bestelling bij een Storage Interface (hoofdstuk 113, jouw wens: "je kunt een item orderen via een storage
 * interface; deze interface activeert een robot die je hebt geprogrammeerd"). Twee soorten:
 * <ul>
 *   <li><b>fetch</b>: iemand bestelde iets: een RC Car haalt het uit je kisten en brengt het naar de interface;</li>
 *   <li><b>store</b>: iemand legde iets in de interface en drukte op Store: een RC Car bergt het op.</li>
 * </ul>
 * Een bestelling wacht tot er een gekoppelde auto vrij is met het juiste stuk programma ({@code when ordered("fetch"):}
 * of {@code when ordered("store"):}), loopt terwijl die auto ermee bezig is, en is daarna klaar: helemaal (DONE), voor een
 * deel (PARTIAL: de auto bracht of borg minder dan gevraagd), of niet (FAILED).
 */
public final class StorageOrder {
    /** Wat er moet gebeuren; {@code code} is het woord in het programma van de auto: {@code when ordered("fetch"):}. */
    public enum Kind {
        FETCH("fetch"), STORE("store");

        private final String code;

        Kind(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Hoe ver de bestelling is. */
    public enum State { WAITING, RUNNING, DONE, PARTIAL, FAILED }

    public final long id;
    public final Kind kind;
    public final ItemKind item;
    public final int count;
    /** Wie bestelde: namens die speler werkt de auto (zijn rechten in een beschermde zone). */
    public final @Nullable UUID by;
    public final String byName;
    public State state = State.WAITING;
    /** Hoeveel er al gebracht (fetch) of opgeborgen (store) is. */
    public int moved;
    /** De auto die ermee bezig is (zijn vaste nummer), en zijn naam voor het scherm. */
    public @Nullable UUID car;
    public String carName = "";
    /** Waarom hij wacht of wat er misging (Engels, voor het scherm); leeg = niets bijzonders. */
    public String note = "";

    public StorageOrder(long id, Kind kind, ItemKind item, int count, @Nullable UUID by, String byName) {
        this.id = id;
        this.kind = kind;
        this.item = item;
        this.count = count;
        this.by = by;
        this.byName = byName;
    }

    /** Is hij afgelopen (klaar, voor een deel, of mislukt)? */
    public boolean ended() {
        return state == State.DONE || state == State.PARTIAL || state == State.FAILED;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Id", id);
        tag.putString("Kind", kind.name());
        tag.put("Item", item.stack(1).save(registries));
        tag.putInt("Count", count);
        if (by != null) {
            tag.putUUID("By", by);
        }
        tag.putString("ByName", byName);
        tag.putString("State", state.name());
        tag.putInt("Moved", moved);
        if (car != null) {
            tag.putUUID("Car", car);
        }
        tag.putString("CarName", carName);
        tag.putString("Note", note);
        return tag;
    }

    /** Leest een bewaarde bestelling; null als het item niet meer bestaat (een mod die weg is). */
    public static @Nullable StorageOrder load(CompoundTag tag, HolderLookup.Provider registries) {
        ItemStack stack = ItemStack.parseOptional(registries, tag.getCompound("Item"));
        if (stack.isEmpty()) {
            return null;
        }
        Kind kind = "STORE".equals(tag.getString("Kind")) ? Kind.STORE : Kind.FETCH;
        StorageOrder order = new StorageOrder(tag.getLong("Id"), kind, ItemKind.of(stack), tag.getInt("Count"),
                tag.hasUUID("By") ? tag.getUUID("By") : null, tag.getString("ByName"));
        try {
            order.state = State.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException e) {
            order.state = State.WAITING;
        }
        order.moved = tag.getInt("Moved");
        order.car = tag.hasUUID("Car") ? tag.getUUID("Car") : null;
        order.carName = tag.getString("CarName");
        order.note = tag.getString("Note");
        return order;
    }
}
