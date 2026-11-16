package com.senne.imttech.logic;

import com.senne.imttech.logic.program.LogicProgram;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * De programma's op slot die in een item zitten (hoofdstuk 130, gevonden door de nalezing). Een robot, drone of RC Car die
 * je oppakt, neemt zijn programma mee in zijn item. Maar wat op een item staat, kan ook naar het spel van anderen gaan:
 * niet alleen als iemand het vasthoudt, ook als je het item op een holotafel legt, in een rugzak die je neerzet, of in een
 * versierde pot. Een programma op slot ("je kunt de code niet zien anders zou je die kunnen overnemen") mag daarom nooit
 * op een item staan.
 * <p>
 * Daarom staat hier, op de server, het hele programma op slot, onder een vingerafdruk van zijn inhoud (SHA-256). Op het
 * item staat alleen zijn naam, soort, van wie, en die vingerafdruk ({@code Ref}); uit een vingerafdruk kun je de code niet
 * terug halen. Zet je de robot of auto weer neer, dan haalt de server het programma hier op. Hetzelfde programma heeft
 * altijd dezelfde vingerafdruk, dus vaak oppakken of een kopie in creative maakt dit niet groter. Een item uit een andere
 * wereld vindt zijn programma hier niet: dan heeft de robot of auto geen programma.
 * <p>
 * Dit is "SavedData": Minecraft bewaart het in de wereld (data/imttech_locked_programs.dat van de overworld).
 */
public final class LockedPrograms extends SavedData {
    private static final String NAME = "imttech_locked_programs";
    /** De sleutel op een opgeslagen programma (of het item van een RC Car) die naar de echte inhoud hier wijst. */
    public static final String REF = "Ref";

    private final Map<String, CompoundTag> programs = new HashMap<>();

    public static LockedPrograms get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LockedPrograms::new, LockedPrograms::load), NAME);
    }

    /** Bewaart dit (een opgeslagen programma, of de code van een auto) en geeft zijn vingerafdruk. */
    public String store(CompoundTag content) {
        String ref = fingerprint(content);
        if (!programs.containsKey(ref)) {
            programs.put(ref, content.copy());
            setDirty();
        }
        return ref;
    }

    /** Wat er onder deze vingerafdruk staat (een kopie), of null. */
    public @Nullable CompoundTag find(String ref) {
        CompoundTag content = programs.get(ref);
        return content == null ? null : content.copy();
    }

    // ================================================================ Voor de items van robots en drones

    /**
     * Wat er van een opgeslagen programma ({@link LogicProgram#save()}) op het item van een robot of drone komt: een open
     * programma zoals het is; een programma op slot zonder blokjes en code, met zijn vingerafdruk ({@link #REF}).
     */
    public static CompoundTag forItem(@Nullable ServerLevel level, CompoundTag saved) {
        if (!LogicProgram.isLocked(saved) || level == null) {
            return LogicProgram.isLocked(saved) ? LogicProgram.redact(saved) : saved;
        }
        if (saved.contains(REF)) {
            return LogicProgram.redact(saved); // al een verwijzing (redact laat Ref staan)
        }
        CompoundTag safe = LogicProgram.redact(saved);
        safe.putString(REF, get(level.getServer()).store(saved));
        return safe;
    }

    /**
     * Terug van een item: een open programma zoals het is; een programma op slot zoals het hier staat. Staat het hier niet
     * (een item uit een andere wereld), dan een lege tag: geen programma.
     */
    public static CompoundTag fromItem(@Nullable ServerLevel level, CompoundTag onItem) {
        if (!LogicProgram.isLocked(onItem)) {
            return onItem;
        }
        String ref = onItem.getString(REF);
        CompoundTag full = level == null || ref.isEmpty() ? null : get(level.getServer()).find(ref);
        return full != null ? full : new CompoundTag();
    }

    // ================================================================ Voor het item van een RC Car

    /** De code van een RC program op slot bewaren; geeft de vingerafdruk (voor op het item). */
    public static String storeCode(ServerLevel level, String code) {
        CompoundTag content = new CompoundTag();
        content.putString("Source", code);
        return get(level.getServer()).store(content);
    }

    /** De code van een RC program op slot met deze vingerafdruk, of null. */
    public static @Nullable String code(@Nullable ServerLevel level, String ref) {
        CompoundTag content = level == null || ref.isEmpty() ? null : get(level.getServer()).find(ref);
        return content == null || !content.contains("Source", Tag.TAG_STRING) ? null : content.getString("Source");
    }

    /** Een vingerafdruk van de inhoud: dezelfde inhoud geeft altijd dezelfde, en eruit terug rekenen kan niet. */
    static String fingerprint(CompoundTag content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // De tekstvorm van een tag zet zijn sleutels op alfabet: dezelfde inhoud geeft dezelfde tekst.
            return HexFormat.of().formatHex(digest.digest(content.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 bestaat niet", e);
        }
    }

    // ================================================================ Opslaan

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        programs.forEach(all::put);
        tag.put("Programs", all);
        return tag;
    }

    private static LockedPrograms load(CompoundTag tag, HolderLookup.Provider registries) {
        LockedPrograms data = new LockedPrograms();
        CompoundTag all = tag.getCompound("Programs");
        for (String ref : all.getAllKeys()) {
            data.programs.put(ref, all.getCompound(ref));
        }
        return data;
    }
}
