package com.senne.imttech.light;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Chunks waar nog licht van een Arc Lamp kan hangen dat er niet meer hoort ("schuld").
 *
 * Gaat een lamp uit, dan wist ArcLampLight zijn licht meteen in alles wat geladen is. Maar zijn licht
 * reikt 100 blokken ver: een deel daarvan kan in chunks liggen die op dat moment niet geladen zijn (je
 * bent ver weg), en het spel bewaart licht mee met een chunk. Zonder dit lijstje zou zo'n chunk bij het
 * volgende laden nog oplichten, voor altijd.
 *
 * Daarom onthouden we hier (in de wereld, data/imttech_arc_lamp_light.dat, per dimensie) welke chunks
 * nog nagekeken moeten worden. Laadt zo'n chunk weer, dan kijkt ArcLampLight even na of er licht van 15
 * zonder lichtbron in hangt, en laat het spel dat opnieuw uitrekenen. Ook als het spel stopt vlak nadat
 * je de lamp uitzette (voordat het licht klaar was), ruimt dit het bij de volgende keer op.
 */
public final class LightDebt extends SavedData {
    private static final String NAME = "imttech_arc_lamp_light";
    private static final SavedData.Factory<LightDebt> FACTORY = new SavedData.Factory<>(LightDebt::new, LightDebt::load);

    /** De chunks (als getal, zie ChunkPos.toLong) die nog nagekeken moeten worden. */
    private final LongOpenHashSet chunks = new LongOpenHashSet();
    /**
     * Sinds welke tick een chunk van het lijstje geladen is (alleen in het geheugen, niet bewaard): pas
     * als hij even geladen is, staat zijn licht klaar om na te kijken.
     */
    final Long2LongOpenHashMap loadedSince = new Long2LongOpenHashMap();

    /** Het lijstje van deze dimensie (elke dimensie heeft zijn eigen). */
    public static LightDebt get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public void add(long chunk) {
        if (chunks.add(chunk)) {
            setDirty();
        }
    }

    public void remove(long chunk) {
        if (chunks.remove(chunk)) {
            setDirty();
        }
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    /** Alle chunks op het lijstje (alleen kijken, niet aanpassen). */
    public LongSet chunks() {
        return chunks;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Chunks", new LongArrayTag(chunks.toLongArray()));
        return tag;
    }

    private static LightDebt load(CompoundTag tag, HolderLookup.Provider registries) {
        LightDebt debt = new LightDebt();
        for (long chunk : tag.getLongArray("Chunks")) {
            debt.chunks.add(chunk);
        }
        return debt;
    }
}
