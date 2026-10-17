package com.senne.imttech.client;

import net.minecraft.network.chat.Component;

/**
 * De tabbladen van het ronde I.M.T-menu (toets G, zie ImtMenuScreen).
 * Met het scrollwiel draai je van het ene tabblad naar het volgende; de cijfers 1-9 en 0
 * springen er meteen heen.
 *
 * - COMBAT:      repulsor, raketten, rayshield (wat je zelf doet)
 * - SATELLITE:   de drie satellietaanvallen, het satellietscherm en het arsenaal
 * - DRONES:      zwerm, één drone, escorte en hun vorm, stand down, markeringen wissen
 * - RECON:       drone-camera, spionagedrone, meekijken, rapport
 * - TRANSPORT:   zelf vliegen, autopiloot, vrachtdrone, iemand vervoeren
 * - TECH:        redstone-relais, muziek, hacken
 * - ILLUSION:    valse muur, nep-leger, onzichtbaar veld, stoppen
 * - INFORMATION: ertsen-scan, dreigingsscan, doelwit-analyse
 * - NAVIGATION:  navigatie, ping, comms
 * - SYSTEMS:     eigendom overdragen, stem, spraakbesturing, nachtzicht (en in het midden: energie, verbruik)
 *
 * De volgorde hier is de volgorde in het menu.
 */
public enum MenuTab {
    COMBAT("combat"),
    SATELLITE("satellite"),
    DRONES("drones"),
    RECON("recon"),
    TRANSPORT("transport"),
    TECH("tech"),
    ILLUSION("illusion"),
    INFORMATION("information"),
    NAVIGATION("navigation"),
    SYSTEMS("systems");

    private final String id;

    MenuTab(String id) {
        this.id = id;
    }

    public Component label() {
        return Component.translatable("screen.imttech.menu.tab." + id);
    }

    /** Het volgende (+1) of vorige (-1) tabblad; na het laatste komt weer het eerste. */
    public MenuTab step(int direction) {
        MenuTab[] all = values();
        return all[Math.floorMod(ordinal() + direction, all.length)];
    }
}
