package com.senne.imttech.sdcard;

import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Een blok waar je een SD-kaart in kunt steken (hoofdstuk 130): de Security Core en de Computer (de kast). Het blok
 * bewaart de kaart als voorwerp (met zijn nummer, zie {@link SdCardData}); wat erop staat, staat op de server
 * ({@link SdCards}).
 */
public interface SdCardHolder {
    /** De kaart die erin zit, of {@link ItemStack#EMPTY}. */
    ItemStack sdCard();

    /** Wie hem erin stak (die mag hem er ook weer uit halen), of null. */
    @Nullable UUID sdCardBy();

    /** Een kaart erin (het blok toont hem: zie de blokstand {@code card}). */
    void putSdCard(ItemStack card, @Nullable UUID by);

    /** De kaart eruit (leeg als er geen in zat). */
    ItemStack takeSdCard();

    /** Het voorwerp van de kaart veranderde (zijn samenvatting): opnieuw bewaren. */
    void sdCardChanged();
}
