package com.senne.imttech.sdcard;

import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.security.os.VirtualFs;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Wat er op alle SD-kaarten van de wereld staat (hoofdstuk 130): per kaart (zijn vaste nummer) de programma's en de
 * bestanden. Op het item zelf staat alleen dat nummer en een samenvatting (zie {@link SdCardData}): zo gaat de code van een
 * programma op slot nooit naar iemands spel, en blijft een kist vol kaarten klein.
 * <p>
 * Net als bij een ingepakte kern ({@code PackedCores}): verbrandt een kaart in lava of verdwijnt hij op de grond, dan blijft
 * wat erop stond hier staan, maar niemand kan er nog bij. Gooi je alles van een kaart weg, dan verdwijnt hij hier en is
 * het item weer een lege kaart.
 * <p>
 * Dit is "SavedData": Minecraft bewaart het in de wereld (data/imttech_sd_cards.dat van de overworld).
 */
public final class SdCards extends SavedData {
    private static final String NAME = "imttech_sd_cards";
    /** Zoveel programma's leest hij hooguit van één kaart (de grootste kaart heeft er 64; dit is alleen een vangnet). */
    private static final int MAX_LOADED_PROGRAMS = 256;

    /** Wat er op één kaart staat. */
    public static final class Card {
        private final UUID id;
        private final List<LogicProgram> programs = new ArrayList<>();
        private VirtualFs files = new VirtualFs();
        /**
         * Gaat één omhoog bij elke verandering. Niet bewaard: alleen zodat een open desktop weet dat hij de lijst van de
         * kaart opnieuw moet krijgen (zie SecurityOs).
         */
        private int revision;

        private Card(UUID id) {
            this.id = id;
        }

        public UUID id() {
            return id;
        }

        /** De programma's, in de volgorde waarin ze erop kwamen. */
        public List<LogicProgram> programs() {
            return Collections.unmodifiableList(programs);
        }

        /** Het programma met deze naam (hoofdletters maken niet uit), of null. */
        public @Nullable LogicProgram program(String name) {
            for (LogicProgram program : programs) {
                if (program.name().equalsIgnoreCase(name.strip())) {
                    return program;
                }
            }
            return null;
        }

        /** Zet een programma erop: een programma met dezelfde naam wordt vervangen (op zijn plek), anders komt het onderaan. */
        void put(LogicProgram program) {
            for (int i = 0; i < programs.size(); i++) {
                if (programs.get(i).name().equalsIgnoreCase(program.name())) {
                    programs.set(i, program);
                    return;
                }
            }
            programs.add(program);
        }

        boolean remove(String name) {
            return programs.removeIf(program -> program.name().equalsIgnoreCase(name.strip()));
        }

        /** De bestanden en mappen op de kaart (dezelfde soort mappen als op een kern, maar zonder security.conf). */
        public VirtualFs files() {
            return files;
        }

        /** Hoeveel bestanden erop staan (mappen tellen niet). */
        public int fileCount() {
            return files.allFiles().size();
        }

        /** Hoeveel mappen erop staan. */
        public int folderCount() {
            return files.count() - fileCount();
        }

        /** Hoeveel programma's op slot zijn. */
        public int lockedCount() {
            return (int) programs.stream().filter(LogicProgram::locked).count();
        }

        /** Staat er niets meer op? */
        public boolean empty() {
            return programs.isEmpty() && files.count() == 0;
        }

        public int revision() {
            return revision;
        }

        /** De samenvatting voor op het item. */
        public SdCardData summary() {
            return new SdCardData(id, programs.size(), fileCount(), lockedCount());
        }
    }

    private final Map<UUID, Card> cards = new HashMap<>();

    public static SdCards get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SdCards::new, SdCards::load), NAME);
    }

    public static SdCards get(ServerLevel level) {
        return get(level.getServer());
    }

    /** Wat er op de kaart met dit nummer staat, of null (nooit beschreven, of alles weggegooid). */
    public @Nullable Card card(UUID id) {
        return cards.get(id);
    }

    /** Een nieuwe, lege kaart met een nieuw nummer (de eerste keer dat er iets op een lege kaart komt). */
    public Card create() {
        Card card = new Card(UUID.randomUUID());
        cards.put(card.id, card);
        setDirty();
        return card;
    }

    /** Er veranderde iets op deze kaart: bewaren, en open desktops krijgen de nieuwe lijst. */
    public void changed(Card card) {
        card.revision++;
        setDirty();
    }

    /** Alles van deze kaart weg (hij is leeg). */
    public void forget(UUID id) {
        if (cards.remove(id) != null) {
            setDirty();
        }
    }

    // ================================================================ Opslaan

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Card card : cards.values()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("Id", card.id);
            ListTag programs = new ListTag();
            for (LogicProgram program : card.programs) {
                programs.add(program.save());
            }
            item.put("Programs", programs);
            item.put("Files", card.files.save());
            list.add(item);
        }
        tag.put("Cards", list);
        return tag;
    }

    private static SdCards load(CompoundTag tag, HolderLookup.Provider registries) {
        SdCards data = new SdCards();
        for (Tag element : tag.getList("Cards", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) element;
            if (!item.hasUUID("Id")) {
                continue;
            }
            Card card = new Card(item.getUUID("Id"));
            for (Tag programTag : item.getList("Programs", Tag.TAG_COMPOUND)) {
                if (card.programs.size() >= MAX_LOADED_PROGRAMS) {
                    break;
                }
                LogicProgram program = LogicProgram.load((CompoundTag) programTag);
                if (!program.name().isBlank() && card.program(program.name()) == null) {
                    card.programs.add(program);
                }
            }
            card.files = VirtualFs.load(item.getCompound("Files"));
            data.cards.put(card.id, card);
        }
        return data;
    }

    /** Voor de tests: alles opnieuw inlezen zoals na een herstart (bewaren en terug). */
    public static SdCards reload(SdCards data, HolderLookup.Provider registries) {
        return load(data.save(new CompoundTag(), registries), registries);
    }
}
