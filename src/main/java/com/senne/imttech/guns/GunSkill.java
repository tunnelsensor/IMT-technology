package com.senne.imttech.guns;

import net.minecraft.network.chat.Component;

/**
 * De skills van de vuurwapens (hoofdstuk 52, stap 3, jouw wens: "je magazijn switch is ook een skill die je beter en
 * beter leert"). Elke skill maakt iets sneller of nauwkeuriger, per niveau een vast percentage. Je leert ze met
 * skillpunten (jouw keuze: punten verdelen, "zo komt er iets meer tactiek in"), en elke skill bestaat twee keer:
 * <b>globaal</b> (voor elk vuurwapen) en <b>per wapen</b> (alleen de Glock 19), die bij elkaar optellen.
 */
public enum GunSkill {
    /** Herladen: het magazijn eruit en erin, de slede, en een patroon in de kamer. */
    RELOAD("reload"),
    /** Terugslag: hoe ver een schot je zicht omhoog duwt. */
    RECOIL("recoil"),
    /** Richten: de spreiding van je schoten. */
    AIM("aim"),
    /** Trekken uit de holster. */
    QUICKDRAW("quickdraw"),
    /** Storingen verhelpen: de tap-rack. */
    CLEARING("clearing"),
    /** Magazijnen vullen: patronen erin duwen. */
    MAG_HANDLING("mag_handling");

    public final String id;

    GunSkill(String id) {
        this.id = id;
    }

    public Component text() {
        return Component.translatable("skill.imttech." + id);
    }

    public Component description() {
        return Component.translatable("skill.imttech." + id + ".what");
    }

    public static GunSkill byId(int id) {
        GunSkill[] all = values();
        return id < 0 || id >= all.length ? RELOAD : all[id];
    }
}
