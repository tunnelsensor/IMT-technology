package com.senne.imttech.energy;

import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModItems;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * De soorten getekende kabels (hoofdstuk 38, jouw wens van 29 oktober). Elke soort ligt als "cellen" op blokplekken
 * (zie Cables), in een eigen lijst per chunk. Zo kunnen een stroomkabel en een netwerkkabel ook op dezelfde plek liggen:
 * dan liggen ze netjes boven elkaar (zie CableGeometry).
 * <ul>
 *   <li><b>POWER</b>: de stroomkabel (grijs). Brengt stroom van generatoren naar apparaten (zie PowerGrid).</li>
 *   <li><b>NETWORK</b>: de netwerkkabel (wit). Brengt een beveiligingsapparaat op het netwerk van de desktop van een
 *       Security Core.</li>
 *   <li><b>POE</b>: "Power over Ethernet" (groen): stroom en netwerk in één kabel. De desktop voedt hem met zijn eigen
 *       stroom.</li>
 * </ul>
 */
public enum CableKind {
    POWER("imttech_cable"),
    NETWORK("imttech_net_cable"),
    POE("imttech_poe_cable");

    private final String marker;

    CableKind(String marker) {
        this.marker = marker;
    }

    /**
     * Het labeltje op een onzichtbare marker in een gebouw (zie cable() in tools/generate_structure.py): hier hoort een
     * kabel van deze soort (zie PowerCables.onEntityJoin).
     */
    public String marker() {
        return marker;
    }

    /** De lijst per chunk waarin deze soort ligt. De stroomkabel houdt zijn oude naam ("cables"), voor oude werelden. */
    public AttachmentType<CableCells> attachment() {
        return switch (this) {
            case POWER -> ModAttachments.CABLES.get();
            case NETWORK -> ModAttachments.NET_CABLES.get();
            case POE -> ModAttachments.POE_CABLES.get();
        };
    }

    /** Geeft deze kabel stroom door? Een stroomkabel en een PoE-kabel wel, een netwerkkabel niet. */
    public boolean carriesPower() {
        return this != NETWORK;
    }

    /** Geeft deze kabel netwerk door? Een netwerkkabel en een PoE-kabel wel, een stroomkabel niet. */
    public boolean carriesNetwork() {
        return this != POWER;
    }

    /**
     * Zitten twee kabels van deze soorten aan elkaar vast als ze tegen elkaar aan liggen? Dezelfde soort altijd. Een
     * PoE-kabel ook aan een stroomkabel (dan gaat de stroom erdoor) en aan een netwerkkabel (dan gaat het netwerk
     * erdoor). Een stroomkabel en een netwerkkabel niet: die hebben niets met elkaar te maken.
     */
    public boolean joins(CableKind other) {
        return this == other || this == POE || other == POE;
    }

    /** De rol van deze soort: wat je terugkrijgt als je een stukje opraapt of doorknipt. */
    public Item item() {
        return switch (this) {
            case POWER -> ModItems.CABLE.get();
            case NETWORK -> ModItems.NETWORK_CABLE.get();
            case POE -> ModItems.POE_CABLE.get();
        };
    }
}
