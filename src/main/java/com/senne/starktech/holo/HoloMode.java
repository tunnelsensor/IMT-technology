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
    ARSENAL("arsenal");

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

    public Component label() {
        return Component.translatable("holo.starktech.mode." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
