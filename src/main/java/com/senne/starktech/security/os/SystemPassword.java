package com.senne.starktech.security.os;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * Het wachtwoord van een beveiligingssysteem (jouw keuze, 28 september: alleen kleine letters en
 * cijfers, hoogstens 8 tekens, zodat het in het spel te kraken is).
 *
 * We bewaren het wachtwoord zelf NIET, alleen een "hash": een vingerafdruk waaruit je het
 * wachtwoord niet terug kunt rekenen, met een willekeurig stukje ervoor (de "salt"). Inloggen =
 * de vingerafdruk van wat je typt vergelijken. Zo staat je wachtwoord niet leesbaar in de save,
 * en stuurt de server het nooit naar iemands spel.
 *
 * Voor het kraken in het spel (bruteforce, zie de shell) onthouden we wel hoe LANG het is en of
 * het alleen uit cijfers bestaat: daar hangt af hoe lang de laptop erover doet.
 */
public final class SystemPassword {
    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 8;

    private final byte[] salt;
    private final byte[] hash;
    private final int length;
    private final boolean digitsOnly;

    private SystemPassword(byte[] salt, byte[] hash, int length, boolean digitsOnly) {
        this.salt = salt;
        this.hash = hash;
        this.length = length;
        this.digitsOnly = digitsOnly;
    }

    /** Mag dit een wachtwoord zijn? 4 tot 8 tekens, alleen a-z en 0-9. */
    public static boolean isValid(String password) {
        return password.length() >= MIN_LENGTH && password.length() <= MAX_LENGTH
                && password.chars().allMatch(c -> c >= 'a' && c <= 'z' || c >= '0' && c <= '9');
    }

    /** Waarom mag dit geen wachtwoord zijn? (Engels, voor het scherm.) Null als het goed is. */
    public static @Nullable String problem(String password) {
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return "a password has " + MIN_LENGTH + " to " + MAX_LENGTH + " characters";
        }
        if (!isValid(password)) {
            return "only lowercase letters (a-z) and digits (0-9)";
        }
        return null;
    }

    /** Een nieuw wachtwoord maken (het moet {@link #isValid} zijn). */
    public static SystemPassword create(String password, RandomSource random) {
        byte[] salt = new byte[8];
        for (int i = 0; i < salt.length; i++) {
            salt[i] = (byte) random.nextInt(256);
        }
        return new SystemPassword(salt, digest(salt, password), password.length(),
                password.chars().allMatch(c -> c >= '0' && c <= '9'));
    }

    /** Klopt dit wachtwoord? */
    public boolean matches(String password) {
        return MessageDigest.isEqual(hash, digest(salt, password));
    }

    public int length() {
        return length;
    }

    public boolean digitsOnly() {
        return digitsOnly;
    }

    private static byte[] digest(byte[] salt, String password) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(salt);
            return sha.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 bestaat altijd in Java", e);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("Salt", salt);
        tag.putByteArray("Hash", hash);
        tag.putInt("Length", length);
        tag.putBoolean("Digits", digitsOnly);
        return tag;
    }

    public static @Nullable SystemPassword load(CompoundTag tag) {
        if (!tag.contains("Hash")) {
            return null;
        }
        return new SystemPassword(tag.getByteArray("Salt"), tag.getByteArray("Hash"), tag.getInt("Length"),
                tag.getBoolean("Digits"));
    }
}
