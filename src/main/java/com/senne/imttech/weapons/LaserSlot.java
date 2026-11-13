package com.senne.imttech.weapons;

import com.senne.imttech.guns.Attachment;
import com.senne.imttech.guns.AttachmentSlot;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * De plekken op de Laser Workbench (hoofdstuk 103, stap 7): de acht dingen die al in een lasergeweer zaten (de zes
 * onderdelen, de kern en de sling: {@link LaserPart}), en twee plekken voor een attachment: de kijker en onder de loop.
 * Zoals GunSlot bij de Gunsmith Table. De eerste acht staan in dezelfde volgorde als LaserPart: hun nummer (dat de
 * knoppen van de tafel naar de server sturen) is hetzelfde gebleven.
 */
public enum LaserSlot {
    FRAME(LaserPart.FRAME),
    RECEIVER(LaserPart.RECEIVER),
    EMITTER(LaserPart.EMITTER),
    OPTICS(LaserPart.OPTICS),
    CAPACITOR(LaserPart.CAPACITOR),
    TRIGGER(LaserPart.TRIGGER),
    CORE(LaserPart.CORE),
    SLING(LaserPart.SLING),
    /** De kijker: een Red Dot Sight, of (de Laser Sniper) de Sniper Scope. Hij komt in de plaats van het eigen vizier. */
    SIGHT(AttachmentSlot.SIGHT),
    /** Onder de loop: een Vertical Grip of een Angled Grip (de Laser Rifle en de Laser Sniper). */
    UNDERBARREL(AttachmentSlot.UNDERBARREL);

    /** Het onderdeel op deze plek, of null (een attachment). */
    public final @Nullable LaserPart part;
    /** De plek voor een attachment, of null (een onderdeel, de kern of de sling). */
    public final @Nullable AttachmentSlot attachment;

    LaserSlot(LaserPart part) {
        this.part = part;
        this.attachment = null;
    }

    LaserSlot(AttachmentSlot attachment) {
        this.part = null;
        this.attachment = attachment;
    }

    /** De plek van dit onderdeel. */
    public static LaserSlot of(LaserPart part) {
        return values()[part.ordinal()];
    }

    /** De plek op de tafel voor deze attachment-plek, of null als lasergeweren die niet hebben (de loop, de buis). */
    public static @Nullable LaserSlot of(AttachmentSlot slot) {
        for (LaserSlot each : values()) {
            if (each.attachment == slot) {
                return each;
            }
        }
        return null;
    }

    public static @Nullable LaserSlot byId(int id) {
        LaserSlot[] all = values();
        return id < 0 || id >= all.length ? null : all[id];
    }

    /** Hoort deze plek op dit geweer? Een pistool heeft geen sling; een attachment-plek alleen als er daar iets op past. */
    public boolean fitsOn(GunType type) {
        return attachment != null ? Attachment.anyFits(attachment, type) : part.fitsOn(type);
    }

    /** De naam van deze plek: "Emitter", "Sling", "Sight". */
    public Component text() {
        return attachment != null ? attachment.text() : part.text();
    }
}
