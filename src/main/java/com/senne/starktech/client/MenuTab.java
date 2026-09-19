package com.senne.starktech.client;

import net.minecraft.network.chat.Component;

/**
 * De tabbladen van het ronde E.D.I.T.H.-menu (toets G, zie EdithMenuScreen).
 * Met het scrollwiel draai je van het ene tabblad naar het volgende.
 *
 * - COMBAT:     doelwit-analyse, dreigingsscan, drones, satelliet, stand down, markeringen wissen
 * - DRONES:     drone-camera, spionagedrone, meekijken, rapport
 * - TRANSPORT:  zelf vliegen, autopiloot, vrachtdrone, iemand vervoeren
 * - SCANNER:    ertsen-scan en nachtzicht
 * - NAVIGATION: navigatie, ping, comms
 * - SYSTEMS:    eigendom overdragen, stem, spraakbesturing en spraakcommando's bewerken (en in het midden: energie, verbruik)
 */
public enum MenuTab {
    COMBAT("combat"),
    /** Fase 6: alles met drones (camera, spion, rapport). */
    DRONES("drones"),
    /** Fase 7: vervoer met drones (zelf vliegen, autopiloot, vracht, iemand oppakken). */
    TRANSPORT("transport"),
    SCANNER("scanner"),
    NAVIGATION("navigation"),
    SYSTEMS("systems");

    private final String id;

    MenuTab(String id) {
        this.id = id;
    }

    public Component label() {
        return Component.translatable("screen.starktech.menu.tab." + id);
    }

    /** Het volgende (+1) of vorige (-1) tabblad; na het laatste komt weer het eerste. */
    public MenuTab step(int direction) {
        MenuTab[] all = values();
        return all[Math.floorMod(ordinal() + direction, all.length)];
    }
}
