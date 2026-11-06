package com.senne.imttech.guns;

import com.mojang.serialization.Codec;
import com.senne.imttech.config.ImtConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Waar een onderdeel van een vuurwapen van gemaakt is (hoofdstuk 52, jouw keuze: eigen materialen). Van zwak naar
 * sterk: <b>polymeer</b> (licht, alleen voor het frame en de trekker, zoals bij een echte Glock), <b>staal</b> en
 * <b>titanium</b>. Hoe beter het materiaal, hoe kleiner de kans per schot dat het onderdeel slijt (jouw wens: "in code
 * moet het percentage om kapot te gaan lager zijn bij het beter materiaal"), en een beetje betere cijfers: minder
 * storingen, iets meer schade (de loop), minder terugslag (de slede) en sneller trekken (het frame). Alle getallen
 * staan in de config onder [guns], in de volgorde polymeer, staal, titanium.
 */
public enum GunMaterial implements StringRepresentable {
    POLYMER("polymer", 0xB8B8B8),
    STEEL("steel", 0xC8D0DA),
    TITANIUM("titanium", 0x9FC4E8);

    public static final Codec<GunMaterial> CODEC = StringRepresentable.fromEnum(GunMaterial::values);
    public static final StreamCodec<ByteBuf, GunMaterial> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Enum::ordinal);

    /** De naam in de bestanden ("steel") en in de taalbestanden ("material.imttech.steel"). */
    public final String id;
    /** De kleur waarin de naam op je scherm staat. */
    public final int colour;

    GunMaterial(String id, int colour) {
        this.id = id;
        this.colour = colour;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public Component text() {
        return Component.translatable("material.imttech." + id);
    }

    /** Hoe sterk het materiaal is: 1 (polymeer), 2 (staal) of 3 (titanium). */
    public int tier() {
        return ordinal() + 1;
    }

    private float of(float[] values) {
        return values[Math.min(ordinal(), values.length - 1)];
    }

    /** Zoveel procent kans per schot dat een onderdeel van dit materiaal één punt slijt. */
    public float wearChance() {
        return of(ImtConfig.Guns.WEAR_CHANCE);
    }

    /** Hoeveel vaker (of minder vaak) een onderdeel van dit materiaal een storing geeft (1 = gewoon). */
    public float reliability() {
        return of(ImtConfig.Guns.MATERIAL_RELIABILITY);
    }

    /** De schade van een schot, maal dit (de loop). */
    public float damageFactor() {
        return of(ImtConfig.Guns.MATERIAL_DAMAGE);
    }

    /** De terugslag, maal dit (de slede). */
    public float recoilFactor() {
        return of(ImtConfig.Guns.MATERIAL_RECOIL);
    }

    /** Hoe snel je trekt en richt, maal dit (het frame; kleiner = sneller). */
    public float handlingFactor() {
        return of(ImtConfig.Guns.MATERIAL_HANDLING);
    }

    public static GunMaterial byId(String id) {
        for (GunMaterial material : values()) {
            if (material.id.equals(id)) {
                return material;
            }
        }
        return STEEL;
    }
}
