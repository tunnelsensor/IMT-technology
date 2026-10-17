package com.senne.imttech.client.bypass;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Wat de schermen van de Bypass Kit (hoofdstuk 12) gemeen hebben: bij welk apparaat ze horen, wat ze
 * doen als de server zegt hoe het afliep, en dichtgaan als de server de hack stopt (je liep weg).
 */
public interface BypassScreen {
    /** De lezer of het paneel dat je hackt. */
    BlockPos device();

    /** De server zegt hoe het afliep ({@code result}, en bij een paneel ook de {@code code}). */
    void onResult(CompoundTag args);

    /** De server stopte de hack (je liep weg, of het apparaat is weg): het scherm gaat dicht. */
    void closeFromServer();
}
