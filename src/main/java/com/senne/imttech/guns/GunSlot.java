package com.senne.imttech.guns;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * De plekken van een vuurwapen op de Gunsmith Table (hoofdstuk 103): de zes onderdelen ({@link GunPart}, in dezelfde
 * volgorde), en daarna wat je er verder aan hangt: de <b>sling</b> (alleen op een lang wapen; dan past het in een
 * sling-vakje, zoals bij de lasergeweren) en de <b>attachments</b> (stap 5: de kijker). Het nummer van een plek gaat als
 * "knop" naar de server (zie PartsTable en GunsmithMenu): nieuwe plekken komen dus ONDERAAN.
 */
public enum GunSlot {
    FRAME(GunPart.FRAME),
    SLIDE(GunPart.SLIDE),
    BARREL(GunPart.BARREL),
    SPRINGS(GunPart.SPRINGS),
    STRIKER(GunPart.STRIKER),
    TRIGGER(GunPart.TRIGGER),
    /** De Gun Sling: geen onderdeel dat slijt, het hangt eraan of niet. */
    SLING((GunPart) null),
    /** De kijker (hoofdstuk 103, stap 5): een Red Dot Sight of een Sniper Scope. */
    SIGHT(AttachmentSlot.SIGHT);

    /** Het onderdeel dat op deze plek zit, of null (de sling of een attachment). */
    public final @Nullable GunPart part;
    /** De plek voor een attachment, of null (een onderdeel of de sling). */
    public final @Nullable AttachmentSlot attachment;

    GunSlot(@Nullable GunPart part) {
        this.part = part;
        this.attachment = null;
    }

    GunSlot(AttachmentSlot attachment) {
        this.part = null;
        this.attachment = attachment;
    }

    /** De plek van deze attachment-plek op de tafel. */
    public static GunSlot of(AttachmentSlot slot) {
        for (GunSlot each : values()) {
            if (each.attachment == slot) {
                return each;
            }
        }
        throw new IllegalArgumentException("Geen plek op de tafel voor " + slot);
    }

    /** De plek van dit onderdeel. */
    public static GunSlot of(GunPart part) {
        return values()[part.ordinal()];
    }

    public static @Nullable GunSlot byId(int id) {
        GunSlot[] all = values();
        return id < 0 || id >= all.length ? null : all[id];
    }

    /** Hoort deze plek op dit wapen? Een pistool heeft geen sling; een attachment-plek alleen als er daar iets op past. */
    public boolean fitsOn(FirearmType type) {
        if (attachment != null) {
            return Attachment.anyFits(attachment, type);
        }
        return this != SLING || type.longGun;
    }

    /** De naam van deze plek bij dit wapen: "Bolt Carrier", "Sling", "Sight". */
    public Component text(FirearmType type) {
        if (attachment != null) {
            return attachment.text();
        }
        return part == null ? Component.translatable("part.imttech.sling") : part.text(type);
    }
}
