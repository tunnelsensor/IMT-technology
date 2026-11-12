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
 * Sinds hoofdstuk 85 zitten de drie soorten niet meer aan elkaar vast (zie {@link #joins}), en sluiten een netwerkkabel en
 * een PoE-kabel alleen op netwerkblokken aan (zie {@link #connectsTo}).
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
     * Zitten twee kabels van deze soorten aan elkaar vast als ze tegen elkaar aan liggen? Alleen dezelfde soort. Hoofdstuk 85
     * (jouw wens: "zorg ook dat power cable, netwerk en poe niet connecten aan elkaar"): een PoE-kabel zit dus niet meer vast
     * aan een stroom- of netwerkkabel. Van de ene soort naar de andere gaat het alleen via een blok: één stroomkabel op de
     * Security Core voedt zijn PoE-kabels (zie PowerGrid).
     */
    public boolean joins(CableKind other) {
        return this == other;
    }

    /**
     * Sluit deze soort kabel aan op dit blok (hoofdstuk 85, jouw wens: "elke blok die in een netwerk kan, kan een power,
     * netwerk en poe kabel hebben. Iets dat niet in een netwerk kan, kan enkel een powercable")? Een stroomkabel op elk blok
     * dat stroom gebruikt of maakt; een netwerkkabel en een PoE-kabel alleen op een netwerkblok (zie NetworkConnectable).
     * Zo krijgt een Crusher of een generator nooit stroom via PoE. Het tekenen van de kabels (CableRenderer) en de
     * zoektochten van het stroomnet (PowerGrid) en het netwerk (LanGrid) houden zich hieraan.
     */
    public boolean connectsTo(net.minecraft.world.level.block.state.BlockState state, boolean energySource) {
        if (state.getBlock() instanceof com.senne.imttech.lan.NetworkConnectable) {
            return true;
        }
        return this == POWER && (energySource || state.getBlock() instanceof PowerConnectable);
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
