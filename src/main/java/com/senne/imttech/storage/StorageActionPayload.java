package com.senne.imttech.storage;

import com.senne.imttech.ImtTech;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Bericht van speler naar server (hoofdstuk 113): een knop in het scherm van een Storage Interface. De server kijkt zelf
 * welke interface je open hebt (je menu): er staat geen plek in het bericht.
 *
 * @param op     wat (een van de getallen hieronder)
 * @param stack  bij FETCH: één voorwerp van de soort die je bestelt; anders leeg
 * @param number bij FETCH: hoeveel; bij CANCEL: het nummer van de bestelling
 * @param car    bij LINK en UNLINK: het vaste nummer van de auto
 */
public record StorageActionPayload(int op, ItemStack stack, long number, UUID car) implements CustomPacketPayload {
    /** Bestel zoveel van deze soort (halen). */
    public static final int FETCH = 0;
    /** De knop Store: berg op wat er in de IN-vakjes ligt. */
    public static final int STORE = 1;
    /** Haal een wachtende of afgelopen bestelling weg. */
    public static final int CANCEL = 2;
    /** Koppel een auto, of maak hem los. */
    public static final int LINK = 3;
    public static final int UNLINK = 4;

    private static final UUID NOBODY = new UUID(0, 0);

    public static StorageActionPayload fetch(ItemStack stack, int count) {
        return new StorageActionPayload(FETCH, stack.copyWithCount(1), count, NOBODY);
    }

    public static StorageActionPayload store() {
        return new StorageActionPayload(STORE, ItemStack.EMPTY, 0, NOBODY);
    }

    public static StorageActionPayload cancel(long order) {
        return new StorageActionPayload(CANCEL, ItemStack.EMPTY, order, NOBODY);
    }

    public static StorageActionPayload link(UUID car, boolean link) {
        return new StorageActionPayload(link ? LINK : UNLINK, ItemStack.EMPTY, 0, car);
    }

    public static final Type<StorageActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "storage_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageActionPayload::op,
            ItemStack.OPTIONAL_STREAM_CODEC, StorageActionPayload::stack,
            ByteBufCodecs.VAR_LONG, StorageActionPayload::number,
            UUIDUtil.STREAM_CODEC, StorageActionPayload::car,
            StorageActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
