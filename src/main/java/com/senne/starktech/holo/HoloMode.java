package com.senne.starktech.holo;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Wat de holotafel boven zich projecteert. Rechtsklik met een lege hand bladert erdoorheen.
 */
public enum HoloMode implements StringRepresentable {
    /** Een reliëfkaart van de omgeving, met je waypoints, spelers en vijanden erop. */
    MAP("map"),
    /** Het recept van het voorwerp dat je in de tafel legt. */
    BLUEPRINT("blueprint"),
    /** Je drones in hun baan, met hoe heel ze nog zijn. */
    ARSENAL("arsenal"),
    /** Je satelliet, groot, met alle onderdelen van zijn upgrades en een ring met hun niveaus. */
    SATELLITE("satellite"),
    /** De ertsen in de grond onder de tafel, als gekleurde blokjes in een doorzichtig blok aarde. */
    ORES("ores");

    private final String name;

    HoloMode(String name) {
        this.name = name;
    }

    public HoloMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static HoloMode byId(int id) {
        HoloMode[] all = values();
        return all[Math.floorMod(id, all.length)];
    }

    /**
     * Hoort deze stand bij E.D.I.T.H. (jouw drones, jouw satelliet)? Dan zie je hem alleen met je
     * eigen bril op. De kaart, het recept en de ertsen laat de tafel zelf zien.
     */
    public boolean needsGlasses() {
        return this == ARSENAL || this == SATELLITE;
    }

    public Component label() {
        return Component.translatable("holo.starktech.mode." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
