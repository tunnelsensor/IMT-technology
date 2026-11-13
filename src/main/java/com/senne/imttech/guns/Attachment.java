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
 *   <li><b>9mm Suppressor</b> (Glock, MP5), <b>Rifle Suppressor</b> (AK-47, AWP) en <b>Shotgun Suppressor</b> (Remington
 *       870): een demper op de loop. Het schot is veel stiller (bewakers horen het pas van dichtbij, andere spelers
 *       ook), er komt geen vlam uit de loop, en de kogel doet een tikje minder schade.</li>
 *   <li><b>Vertical Grip</b> (MP5, AK-47): een greep onder de handbeschermer: minder terugslag.</li>
 *   <li><b>Angled Grip</b> (MP5, AK-47): een schuine greep: je richt sneller en trekt het wapen sneller.</li>
 *   <li><b>Bipod</b> (AWP): een tweepoot. Sneak en sta stil: hij klapt uit en je hebt bijna geen terugslag meer.</li>
 *   <li><b>Tube Extension</b> (Remington 870): een verlengstuk op de buis: twee patronen meer.</li>
 * </ul>
 */
public enum Attachment {
    RED_DOT("red_dot_sight", AttachmentSlot.SIGHT),
    SNIPER_SCOPE("sniper_scope", AttachmentSlot.SIGHT),
    SUPPRESSOR_9MM("suppressor_9mm", AttachmentSlot.MUZZLE),
    SUPPRESSOR_RIFLE("suppressor_rifle", AttachmentSlot.MUZZLE),
    SUPPRESSOR_SHOTGUN("suppressor_shotgun", AttachmentSlot.MUZZLE),
    VERTICAL_GRIP("vertical_grip", AttachmentSlot.UNDERBARREL),
    ANGLED_GRIP("angled_grip", AttachmentSlot.UNDERBARREL),
    BIPOD("bipod", AttachmentSlot.UNDERBARREL),
    TUBE_EXTENSION("tube_extension", AttachmentSlot.TUBE);

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
            // Een demper past op één soort kaliber.
            case SUPPRESSOR_9MM -> type.caliber == Caliber.MM9;
            case SUPPRESSOR_RIFLE -> type.caliber == Caliber.R762 || type.caliber == Caliber.L338;
            case SUPPRESSOR_SHOTGUN -> type.caliber == Caliber.G12;
            // Een grip hoort onder een handbeschermer waar je linkerhand ligt; de AWP krijgt daar zijn tweepoot.
            case VERTICAL_GRIP, ANGLED_GRIP -> type == FirearmType.MP5 || type == FirearmType.AK_47;
            case BIPOD -> type == FirearmType.AWP;
            case TUBE_EXTENSION -> type.tube();
        };
    }

    /**
     * Past dit op dit lasergeweer (hoofdstuk 103, stap 7; jouw keuze: "red dot, sniperkijker en grips; geen suppressor of
     * extended mag")? De Red Dot Sight op alle vier; de Sniper Scope alleen op de Laser Sniper (voor de andere is hij
     * langer dan het geweer); de grips onder de Laser Rifle en de Laser Sniper (het pistool heeft geen handbeschermer, en
     * onder de loop van de shotgun zit zijn pomp).
     */
    public boolean fits(com.senne.imttech.weapons.GunType laser) {
        return switch (this) {
            case RED_DOT -> true;
            case SNIPER_SCOPE -> laser == com.senne.imttech.weapons.GunType.SNIPER;
            case VERTICAL_GRIP, ANGLED_GRIP -> laser == com.senne.imttech.weapons.GunType.RIFLE
                    || laser == com.senne.imttech.weapons.GunType.SNIPER;
            default -> false;
        };
    }

    /** Past er op deze plek van dit lasergeweer iets? (Dan staat de plek op de Laser Workbench.) */
    public static boolean anyFits(AttachmentSlot slot, com.senne.imttech.weapons.GunType laser) {
        for (Attachment attachment : values()) {
            if (attachment.slot == slot && attachment.fits(laser)) {
                return true;
            }
        }
        return false;
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

    /** Is dit een demper? Dan klinkt het schot anders (veel stiller) en komt er geen vlam uit de loop. */
    public boolean suppresses() {
        return this == SUPPRESSOR_9MM || this == SUPPRESSOR_RIFLE || this == SUPPRESSOR_SHOTGUN;
    }

    /** Maal dit is de spreiding van een gericht schot (kleiner = preciezer). */
    public float aimSpread() {
        return switch (this) {
            case RED_DOT -> ImtConfig.Guns.RED_DOT_AIM_SPREAD;
            case SNIPER_SCOPE -> ImtConfig.Guns.SCOPE_AIM_SPREAD;
            default -> 1;
        };
    }

    /** Maal dit gaat het richten (het wapen naar je oog brengen): een red dot of een schuine greep is snel, een zware kijker traag. */
    public float aimSpeed() {
        return switch (this) {
            case RED_DOT -> ImtConfig.Guns.RED_DOT_AIM_SPEED;
            case SNIPER_SCOPE -> ImtConfig.Guns.SCOPE_AIM_SPEED;
            case ANGLED_GRIP -> ImtConfig.Guns.ANGLED_GRIP_AIM_SPEED;
            default -> 1;
        };
    }

    /** Maal dit gaat het trekken van het wapen (van je sling of uit de holster). */
    public float drawSpeed() {
        return this == ANGLED_GRIP ? ImtConfig.Guns.ANGLED_GRIP_DRAW_SPEED : 1;
    }

    /** Maal dit is de schade van een kogel (een demper remt hem een tikje af). */
    public float damage() {
        return suppresses() ? ImtConfig.Guns.SUPPRESSOR_DAMAGE : 1;
    }

    /** Maal dit is hoe ver bewakers het schot horen (een demper: veel minder ver). */
    public float noise() {
        return suppresses() ? ImtConfig.Guns.SUPPRESSOR_NOISE : 1;
    }

    /**
     * Maal dit is de terugslag. {@code braced}: je sneakt en staat stil (dan staat een tweepoot op zijn pootjes; anders
     * doet hij niets).
     */
    public float recoil(boolean braced) {
        return switch (this) {
            case VERTICAL_GRIP -> ImtConfig.Guns.VERTICAL_GRIP_RECOIL;
            case BIPOD -> braced ? ImtConfig.Guns.BIPOD_RECOIL : 1;
            default -> 1;
        };
    }
}
