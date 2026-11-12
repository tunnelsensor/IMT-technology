package com.senne.imttech.guns;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * De zes onderdelen van een vuurwapen (hoofdstuk 52, jouw lijst: slede, trekker, hamer, handvat, loop en veren). Een
 * Glock heeft geen hamer maar een slagpin ("striker"); die heet hier dus zo, want dat is wat er echt in zit. Elk
 * onderdeel kent zijn materialen: polymeer alleen voor het frame en de trekker van de Glock (zoals bij de echte), staal
 * en titanium voor alles.
 *
 * Hoofdstuk 103: elk nieuw wapen (de MP5, de AK-47...) heeft dezelfde zes rollen, met de namen van dat wapen: het
 * "frame" van een MP5 is zijn Receiver, de "slide" zijn Bolt Carrier. Ze zijn van staal of titanium, en een onderdeel
 * van het ene wapen past niet in het andere (zie {@link GunPartItem}: één item per onderdeel per wapen).
 */
public enum GunPart implements StringRepresentable {
    FRAME("frame", EnumSet.of(GunMaterial.POLYMER, GunMaterial.STEEL, GunMaterial.TITANIUM)),
    SLIDE("slide", EnumSet.of(GunMaterial.STEEL, GunMaterial.TITANIUM)),
    BARREL("barrel", EnumSet.of(GunMaterial.STEEL, GunMaterial.TITANIUM)),
    SPRINGS("springs", EnumSet.of(GunMaterial.STEEL, GunMaterial.TITANIUM)),
    STRIKER("striker", EnumSet.of(GunMaterial.STEEL, GunMaterial.TITANIUM)),
    TRIGGER("trigger", EnumSet.of(GunMaterial.POLYMER, GunMaterial.STEEL, GunMaterial.TITANIUM));

    public static final Codec<GunPart> CODEC = StringRepresentable.fromEnum(GunPart::values);
    public static final StreamCodec<ByteBuf, GunPart> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Enum::ordinal);

    /** Staal en titanium: de materialen van de onderdelen van de wapens van hoofdstuk 103. */
    private static final Set<GunMaterial> METALS = EnumSet.of(GunMaterial.STEEL, GunMaterial.TITANIUM);

    /** De naam in de bestanden ("slide"; het item van de Glock heet "gun_slide", dat van de MP5 "mp5_slide"). */
    public final String id;
    private final Set<GunMaterial> materials;

    GunPart(String id, Set<GunMaterial> materials) {
        this.id = id;
        this.materials = materials;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** De naam van het item van de Glock in de registry: "gun_slide". */
    public String itemId() {
        return "gun_" + id;
    }

    /** De naam van het item van dit wapen in de registry: "gun_slide" (de Glock), "mp5_slide"... */
    public String itemId(FirearmType type) {
        return type == FirearmType.GLOCK_19 ? itemId() : type.id + "_" + id;
    }

    /** De naam bij de Glock: "Slide". */
    public Component text() {
        return Component.translatable("part.imttech." + id);
    }

    /** De naam bij dit wapen: "Slide" (de Glock), "Bolt Carrier" (de MP5)... */
    public Component text(FirearmType type) {
        return type == FirearmType.GLOCK_19 ? text() : Component.translatable("part.imttech." + type.id + "." + id);
    }

    /** Kan dit onderdeel van de Glock van dat materiaal zijn? */
    public boolean accepts(GunMaterial material) {
        return materials.contains(material);
    }

    /** Kan dit onderdeel van dit wapen van dat materiaal zijn? */
    public boolean accepts(FirearmType type, GunMaterial material) {
        return materials(type).contains(material);
    }

    /** De materialen bij de Glock. */
    public Set<GunMaterial> materials() {
        return materials;
    }

    /** De materialen bij dit wapen: polymeer is alleen iets van de Glock. */
    public Set<GunMaterial> materials(FirearmType type) {
        return type == FirearmType.GLOCK_19 ? materials : METALS;
    }

    /**
     * Zit dit onderdeel onder (of in) de slede of de grendel? Dan kun je er op de Gunsmith Table pas bij als die eraf
     * is (hoofdstuk 54). Bij de Glock: de loop, de veren en de slagpin; bij de lange wapens steekt de loop gewoon naar
     * voren uit, en zitten alleen de veer en de slagpin erachter.
     */
    public boolean underSlide(FirearmType type) {
        return this == SPRINGS || this == STRIKER || (this == BARREL && !type.longGun);
    }
}
