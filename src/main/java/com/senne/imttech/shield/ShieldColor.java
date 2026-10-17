package com.senne.imttech.shield;

import net.minecraft.util.StringRepresentable;

/**
 * De kleur van een rayshield (het energieveld tussen twee zenders).
 *
 * De kleur kies je in het scherm van de zender (sneak + rechtsklik); de wand neemt hem over.
 * Elke kleur heeft zijn eigen (bewegende) texture: zie assets/.../block/laser_wall_*.png.
 */
public enum ShieldColor implements StringRepresentable {
    BLUE("blue", 0x3C8CFF),
    CYAN("cyan", 0x3CDCF0),
    GREEN("green", 0x46E678),
    YELLOW("yellow", 0xF5DC46),
    RED("red", 0xEB463C),
    PURPLE("purple", 0xBE5AF5);

    private final String name;
    /** Voor de deeltjes en het lichtje op de zender. */
    private final int rgb;

    ShieldColor(String name, int rgb) {
        this.name = name;
        this.rgb = rgb;
    }

    public int rgb() {
        return rgb;
    }

    /** De volgende kleur in de rij (zo bladert de holotafel erdoorheen). */
    public ShieldColor next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
