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
 * De zes onderdelen van een pistool (hoofdstuk 52, jouw lijst: slede, trekker, hamer, handvat, loop en veren). Een
 * Glock heeft geen hamer maar een slagpin ("striker"); die heet hier dus zo, want dat is wat er echt in zit. Elk
 * onderdeel kent zijn materialen: polymeer alleen voor het frame en de trekker (zoals bij de echte Glock), staal en
 * titanium voor alles.
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

    /** De naam in de bestanden ("slide"; het item heet "gun_slide"). */
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

    /** De naam van het item in de registry: "gun_slide". */
    public String itemId() {
        return "gun_" + id;
    }

    public Component text() {
        return Component.translatable("part.imttech." + id);
    }

    /** Kan dit onderdeel van dat materiaal zijn? */
    public boolean accepts(GunMaterial material) {
        return materials.contains(material);
    }

    public Set<GunMaterial> materials() {
        return materials;
    }
}
