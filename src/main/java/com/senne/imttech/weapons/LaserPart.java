package com.senne.imttech.weapons;

import com.mojang.serialization.Codec;
import com.senne.imttech.guns.GunMaterial;
import io.netty.buffer.ByteBuf;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * De onderdelen van een lasergeweer (hoofdstuk 55, jouw keuze: "al de bestaande laserguns wil ik ook dat ze gecraft
 * worden in een nieuwe soort tafel op dezelfde manier als de Glock"). Zes onderdelen die slijten, elk in titanium of
 * netherite, en elk doet iets:
 * <ul>
 *   <li><b>frame</b>: de onderkant van de romp, de kolf en de greep. Hoe snel je trekt (het materiaal, en slijtage).</li>
 *   <li><b>receiver</b>: het bovenstuk met wat bij een schot beweegt (de slede, de spanhendel, de grendel). De
 *       terugslag en hoe snel je herlaadt.</li>
 *   <li><b>emitter</b>: de loop met de lens. De schade en het bereik.</li>
 *   <li><b>optics</b>: het vizier of de kijker. De spreiding.</li>
 *   <li><b>capacitor</b>: het gloeiende blokje opzij. Hoeveel lading een schot kost.</li>
 *   <li><b>trigger</b>: de trekker. Hoe snel je kunt schieten.</li>
 * </ul>
 * En twee dingen die je er ook op de tafel in steekt, maar die niet slijten en geen materiaal hebben:
 * <ul>
 *   <li><b>core</b>: een <b>Nether Star</b> (jouw keuze: "een item dat je er visueel moet insteken via de GUI", en er ook
 *       weer uit kunt halen). Zonder kern schiet het geweer niet.</li>
 *   <li><b>sling</b>: de <b>Gun Sling</b> (jouw keuze: "bij het bouwen van je gun heb je ook de optie om er een sling aan
 *       te hangen; als deze eraan hangt kun je die in de slingslot doen, anders niet"). Alleen op de rifle, de sniper en
 *       de shotgun.</li>
 * </ul>
 * Zie {@link LaserParts} (wat er in een geweer zit), {@link LaserPartItem} (een los onderdeel) en
 * {@link LaserWorkbenchBlockEntity} (de tafel).
 */
public enum LaserPart implements StringRepresentable {
    FRAME("frame"),
    RECEIVER("receiver"),
    EMITTER("emitter"),
    OPTICS("optics"),
    CAPACITOR("capacitor"),
    TRIGGER("trigger"),
    CORE("core"),
    SLING("sling");

    public static final Codec<LaserPart> CODEC = StringRepresentable.fromEnum(LaserPart::values);
    public static final StreamCodec<ByteBuf, LaserPart> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Enum::ordinal);

    /** De materialen van de onderdelen die slijten (jouw keuze: twee kwaliteiten). */
    public static final Set<GunMaterial> MATERIALS = EnumSet.of(GunMaterial.TITANIUM, GunMaterial.NETHERITE);

    /** De naam in de bestanden ("emitter"; het item heet "laser_rifle_emitter"). */
    public final String id;

    LaserPart(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public Component text() {
        return Component.translatable("laser_part.imttech." + id);
    }

    /** Slijt dit onderdeel (en heeft het een materiaal)? De kern en de sling niet. */
    public boolean wears() {
        return this != CORE && this != SLING;
    }

    /** Moet dit erin zitten om te kunnen schieten? Alles behalve de sling. */
    public boolean required() {
        return this != SLING;
    }

    /** Hoort dit onderdeel op dit geweer? Een pistool heeft geen sling. */
    public boolean fitsOn(GunType type) {
        return this != SLING || type != GunType.PISTOL;
    }

    /** De naam van het losse item van dit onderdeel voor dit geweer: "laser_rifle_emitter". */
    public String itemId(GunType type) {
        return type.id + "_" + id;
    }

    /** De zes onderdelen die slijten, in hun volgorde. */
    public static List<LaserPart> wearing() {
        return List.of(FRAME, RECEIVER, EMITTER, OPTICS, CAPACITOR, TRIGGER);
    }

    public static LaserPart byId(int id) {
        LaserPart[] all = values();
        return id < 0 || id >= all.length ? FRAME : all[id];
    }
}
