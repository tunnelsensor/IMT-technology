package com.senne.imttech.guns;

import net.minecraft.network.chat.Component;

/**
 * De plekken op een vuurwapen waar een attachment kan zitten (hoofdstuk 103, de grote update; jouw wens: "een attachment
 * systeem, de attachments maak je net zoals de gun parts in de Gunsmith Table"). Op elke plek zit er hoogstens één.
 * <ul>
 *   <li><b>SIGHT</b>: bovenop het wapen: de Red Dot Sight of de Sniper Scope.</li>
 * </ul>
 * De andere plekken (de loop voor een suppressor, onder de loop voor een grip of een tweepoot, en de buis van de
 * Remington 870) komen in de volgende stap. Het nummer van een plek gaat niet over het netwerk (alleen zijn naam, in het
 * data component), dus er mogen er overal tussen komen.
 */
public enum AttachmentSlot {
    /** Bovenop: een kijker. */
    SIGHT("sight");

    /** De naam in de bestanden. */
    public final String id;

    AttachmentSlot(String id) {
        this.id = id;
    }

    /**
     * Het onderdeel van het wapen waar een attachment op deze plek op staat. Een kijker staat bij een lang wapen op de
     * behuizing (het frame), bij een pistool op de slede. Zolang er een attachment op staat, krijg je dat onderdeel op de
     * Gunsmith Table niet uit het wapen; en zonder dat onderdeel kun je het attachment er niet op zetten.
     */
    public GunPart carrier(FirearmType type) {
        return switch (this) {
            case SIGHT -> type.longGun ? GunPart.FRAME : GunPart.SLIDE;
        };
    }

    /** De naam van de plek voor op het scherm: "Sight". */
    public Component text() {
        return Component.translatable("attachment_slot.imttech." + id);
    }
}
