package com.senne.imttech.alarm.audio;

/**
 * Er ging iets mis bij het inlezen van een geluidsbestand (hoofdstuk 126). {@link #key} is het stukje van de vertaalsleutel
 * ("alarm.imttech.audio." + key in en_us.json), {@link #args} wat er in de zin komt; zo kan het scherm een nette zin tonen
 * ("This file has no sound in it").
 */
public final class AlarmAudioException extends Exception {
    public final String key;
    public final transient Object[] args;

    public AlarmAudioException(String key, Object... args) {
        super(key);
        this.key = key;
        this.args = args;
    }

    public AlarmAudioException(String key, Throwable cause, Object... args) {
        super(key, cause);
        this.key = key;
        this.args = args;
    }
}
