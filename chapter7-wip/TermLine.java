package com.senne.starktech.security.os;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Eén regel op de terminal (of in een venster van de desktop): de tekst plus wat voor soort het is
 * (dat bepaalt de kleur). De server maakt deze regels en stuurt ze naar je spel; je spel tekent ze
 * alleen maar. Zo staat het echte wachtwoord of de mappenstructuur nooit in het spel van een hacker.
 */
public record TermLine(String text, Kind kind) {
    /** Soort regel: bepaalt de kleur op het scherm. */
    public enum Kind { NORMAL, INPUT, ERROR, HEADER, HINT, GOOD }

    /** Langer maken we een regel niet (zo blijft een netwerkbericht klein). */
    public static final int MAX_TEXT = 160;

    public static final StreamCodec<ByteBuf, TermLine> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_TEXT), TermLine::text,
            ByteBufCodecs.idMapper(id -> Kind.values()[id], Kind::ordinal), TermLine::kind,
            TermLine::new);

    public static TermLine normal(String text) {
        return new TermLine(text, Kind.NORMAL);
    }

    public static TermLine input(String text) {
        return new TermLine(text, Kind.INPUT);
    }

    public static TermLine error(String text) {
        return new TermLine(text, Kind.ERROR);
    }

    public static TermLine header(String text) {
        return new TermLine(text, Kind.HEADER);
    }

    public static TermLine hint(String text) {
        return new TermLine(text, Kind.HINT);
    }

    public static TermLine good(String text) {
        return new TermLine(text, Kind.GOOD);
    }
}
