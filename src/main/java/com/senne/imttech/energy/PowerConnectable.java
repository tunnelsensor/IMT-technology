package com.senne.imttech.energy;

/**
 * Een blok waar een stroomkabel op aansluit: een leverancier (zonnepaneel, warmtegenerator) of
 * iets dat stroom gebruikt (laadstation, koepelgenerator, laserzender, holotafel).
 *
 * Het is alleen een "etiket": de kabel kijkt of zijn buurblok dit heeft, en maakt dan een
 * verbindingsstukje naar die kant.
 */
public interface PowerConnectable {
}
