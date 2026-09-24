package com.senne.starktech.energy;

/**
 * Een blok waar een Stark-kabel op aansluit: een leverancier (zonnepaneel, warmtegenerator) of
 * iets dat stroom gebruikt (laadstation, koepelgenerator, laserzender, holotafel).
 *
 * Het is alleen een "etiket": de kabel kijkt of zijn buurblok dit heeft, en maakt dan een
 * verbindingsstukje naar die kant.
 */
public interface PowerConnectable {
}
