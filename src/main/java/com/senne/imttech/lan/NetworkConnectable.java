package com.senne.imttech.lan;

/**
 * Een blok waar een netwerkkabel op aansluit (hoofdstuk 38): de Security Core (de desktop) en de beveiligingsapparaten
 * die op zijn netwerk horen (lezer, paneel, camera, torentje, laserzender, brug, serverrek, en straks de switch, de
 * netwerkhendel en de netwerkknop).
 *
 * Net als PowerConnectable is het alleen een "etiket": de kabel kijkt of zijn buurblok dit heeft, en tekent dan een
 * verbindingsstukje naar die kant. Een PoE-kabel sluit op allebei de soorten aan.
 */
public interface NetworkConnectable {
}
