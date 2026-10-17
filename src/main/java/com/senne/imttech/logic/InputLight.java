package com.senne.imttech.logic;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * Het lampje op een keycardlezer of codepaneel.
 * <ul>
 *   <li>OFF: wacht op een kaart of een code;</li>
 *   <li>BUSY (oranje): gelezen, de server denkt na;</li>
 *   <li>OK (groen): het diagram zei {@code accept()};</li>
 *   <li>DENIED (rood): het diagram zei {@code deny()}, of de server antwoordt niet.</li>
 * </ul>
 */
public enum InputLight implements StringRepresentable {
    OFF, BUSY, OK, DENIED;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
