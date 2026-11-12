package com.senne.imttech.guns;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * De plekken van een vuurwapen op de Gunsmith Table (hoofdstuk 103): de zes onderdelen ({@link GunPart}, in dezelfde
 * volgorde), en daarna wat je er verder aan hangt: de <b>sling</b> (alleen op een lang wapen; dan past het in een
 * sling-vakje, zoals bij de lasergeweren). Het nummer van een plek gaat als "knop" naar de server (zie PartsTable en
 * GunsmithMenu): nieuwe plekken komen dus ONDERAAN.
 */
public enum GunSlot {
    FRAME(GunPart.FRAME),
    SLIDE(GunPart.SLIDE),
    BARREL(GunPart.BARREL),
    SPRINGS(GunPart.SPRINGS),
    STRIKER(GunPart.STRIKER),
    TRIGGER(GunPart.TRIGGER),
    /** De Gun Sling: geen onderdeel dat slijt, het hangt eraan of niet. */
    SLING(null);

    /** Het onderdeel dat op deze plek zit, of null (de sling). */
    public final @Nullable GunPart part;

    GunSlot(@Nullable GunPart part) {
        this.part = part;
    }

    /** De plek van dit onderdeel. */
    public static GunSlot of(GunPart part) {
        return values()[part.ordinal()];
    }

    public static @Nullable GunSlot byId(int id) {
        GunSlot[] all = values();
        return id < 0 || id >= all.length ? null : all[id];
    }

    /** Hoort deze plek op dit wapen? Een pistool heeft geen sling. */
    public boolean fitsOn(FirearmType type) {
        return this != SLING || type.longGun;
    }

    /** De naam van deze plek bij dit wapen: "Bolt Carrier", "Sling". */
    public Component text(FirearmType type) {
        return part == null ? Component.translatable("part.imttech.sling") : part.text(type);
    }
}
