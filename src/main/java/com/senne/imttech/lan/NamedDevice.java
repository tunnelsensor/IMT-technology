package com.senne.imttech.lan;

/**
 * Een apparaat op het netwerk met een eigen naam die in het blok zelf staat (hoofdstuk 38): een keycardlezer, een
 * codepaneel, een switch. In de app Devices op de desktop geef je het een andere naam (zie SecurityOs, de knop Rename).
 * Een naam wordt altijd netjes gemaakt met InputDeviceBlockEntity.cleanName (hooguit 24 tekens).
 */
public interface NamedDevice {
    String name();

    void setName(String name);
}
