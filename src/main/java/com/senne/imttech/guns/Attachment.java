package com.senne.imttech.guns;

import com.senne.imttech.config.ImtConfig;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * De attachments (hoofdstuk 103): wat je op een vuurwapen kunt zetten. Je maakt ze op de Gunsmith Table (het tabblad
 * "Make parts", onder Attachments) en zet ze er alleen daar op en af (jouw keuze), door ze op het wapen te slepen. Ze
 * slijten niet.
 * <ul>
 *   <li><b>Red Dot Sight</b> (op elk vuurwapen): een open kijkertje met een rood stipje. Je richt sneller en preciezer,
 *       en je ziet je wapen gewoon nog (jouw keuze: "je moet altijd je wapen nog zien bij de red dot, zoals TACZ").</li>
 *   <li><b>Sniper Scope</b> (op de MP5, de AK-47 en de AWP): één kijker die van 1x tot 10x gaat "en alles ertussenin"
 *       (jouw keuze): met het scrollwiel terwijl je richt. Je kijkt erin, maar je ziet het wapen nog een beetje (geen
 *       zwart beeld).</li>
 * </ul>
 */
public enum Attachment {
    RED_DOT("red_dot_sight", AttachmentSlot.SIGHT),
    SNIPER_SCOPE("sniper_scope", AttachmentSlot.SIGHT);

    /** De naam van het item (en van zijn model, zie models/firearm/attachment/). */
    public final String id;
    /** Op welke plek het zit. */
    public final AttachmentSlot slot;

    Attachment(String id, AttachmentSlot slot) {
        this.id = id;
        this.slot = slot;
    }

    public static @Nullable Attachment byId(String id) {
        for (Attachment attachment : values()) {
            if (attachment.id.equals(id)) {
                return attachment;
            }
        }
        return null;
    }

    /** Past dit op dit wapen? */
    public boolean fits(FirearmType type) {
        return switch (this) {
            case RED_DOT -> true;
            // De kijker is 42 cm lang: te groot voor een pistool, en op een hagelgeweer heb je er niets aan.
            case SNIPER_SCOPE -> type == FirearmType.MP5 || type == FirearmType.AK_47 || type == FirearmType.AWP;
        };
    }

    /** Past er op deze plek van dit wapen iets? (Dan staat de plek op de Gunsmith Table.) */
    public static boolean anyFits(AttachmentSlot slot, FirearmType type) {
        for (Attachment attachment : values()) {
            if (attachment.slot == slot && attachment.fits(type)) {
                return true;
            }
        }
        return false;
    }

    /** De volle naam: "Red Dot Sight". */
    public Component text() {
        return Component.translatable("item.imttech." + id);
    }

    /** Een korte naam voor in een smalle lijst: "Red Dot". */
    public Component shortText() {
        return Component.translatable("attachment.imttech." + id + ".short");
    }

    // ================================================================ Wat het doet (config [guns])

    /** Is dit een kijker waar je door zoomt (met het scrollwiel)? */
    public boolean zooms() {
        return this == SNIPER_SCOPE;
    }

    /** Zo ver kun je ermee inzoomen (1 = niet). */
    public float maxZoom() {
        return this == SNIPER_SCOPE ? Math.max(1, ImtConfig.Guns.SCOPE_MAX_ZOOM) : 1;
    }

    /** Maal dit is de spreiding van een gericht schot (kleiner = preciezer). */
    public float aimSpread() {
        return switch (this) {
            case RED_DOT -> ImtConfig.Guns.RED_DOT_AIM_SPREAD;
            case SNIPER_SCOPE -> ImtConfig.Guns.SCOPE_AIM_SPREAD;
        };
    }

    /** Maal dit gaat het richten (het wapen naar je oog brengen): een red dot is snel, een zware kijker traag. */
    public float aimSpeed() {
        return switch (this) {
            case RED_DOT -> ImtConfig.Guns.RED_DOT_AIM_SPEED;
            case SNIPER_SCOPE -> ImtConfig.Guns.SCOPE_AIM_SPEED;
        };
    }
}
