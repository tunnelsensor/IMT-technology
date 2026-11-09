package com.senne.imttech.weapons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Ladingen die iemand verplaatste terwijl ze scherp waren (hoofdstuk 69): een Remote EMP of Remote Frag die met sneak +
 * lege hand van de muur ging en ergens anders weer opgehangen werd. Zo'n lading blijft aan de trigger van zijn eigenaar
 * (jouw keuze), maar die trigger zit in de inventaris van de eigenaar (misschien ver weg, of offline), dus kunnen we
 * hem niet meteen bijwerken. Daarom onthouden we hier per trigger: "weg van daar, nu hier". Zodra de trigger tikt (in
 * de inventaris van zijn eigenaar) of afgaat, neemt hij het over (zie TriggerItem.catchUp) en vergeten we het hier.
 *
 * Bewaard in de wereld (data/imttech_moved_charges.dat, bij de overworld), zodat het een herstart overleeft.
 */
public final class MovedCharges extends SavedData {
    private static final String NAME = "imttech_moved_charges";
    private static final SavedData.Factory<MovedCharges> FACTORY = new SavedData.Factory<>(MovedCharges::new, MovedCharges::load);

    /** Een verplaatsing: weg van {@code from} (als we dat weten), nu op {@code to}. */
    public record Move(@Nullable GlobalPos from, GlobalPos to) {}

    /** Per trigger (zijn nummer): wat er sinds de laatste keer verplaatst is. */
    private final Map<UUID, List<Move>> moves = new HashMap<>();

    public static MovedCharges get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** Een lading van trigger {@code trigger} hangt nu op {@code to} (en niet meer op {@code from}). */
    public void moved(UUID trigger, @Nullable GlobalPos from, GlobalPos to) {
        List<Move> list = moves.computeIfAbsent(trigger, id -> new ArrayList<>());
        if (list.size() >= TriggerData.MAX_CHARGES) {
            list.removeFirst(); // nooit eindeloos groeien (een trigger die niemand meer gebruikt)
        }
        list.add(new Move(from, to));
        setDirty();
    }

    /** Is er iets verplaatst voor deze trigger? (Snel: dit vraagt elke trigger elke tick.) */
    public boolean has(UUID trigger) {
        return !moves.isEmpty() && moves.containsKey(trigger);
    }

    /**
     * Wat deze trigger onthoudt, bijgewerkt: de oude plekken eruit, de nieuwe erbij. Daarna vergeten we het hier. Geeft
     * dezelfde gegevens terug als er niets verplaatst was.
     */
    public TriggerData apply(TriggerData data) {
        List<Move> list = moves.remove(data.id());
        if (list == null) {
            return data;
        }
        setDirty();
        List<GlobalPos> charges = new ArrayList<>(data.charges());
        for (Move move : list) {
            if (move.from() != null) {
                charges.remove(move.from());
            }
            charges.remove(move.to());
            if (charges.size() < TriggerData.MAX_CHARGES) {
                charges.add(move.to());
            }
        }
        return data.withCharges(charges);
    }

    // ================================================================ Opslaan

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag all = new ListTag();
        moves.forEach((trigger, list) -> {
            for (Move move : list) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Trigger", trigger);
                if (move.from() != null) {
                    GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, move.from()).result().ifPresent(from -> entry.put("From", from));
                }
                GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, move.to()).result().ifPresent(to -> entry.put("To", to));
                all.add(entry);
            }
        });
        tag.put("Moves", all);
        return tag;
    }

    private static MovedCharges load(CompoundTag tag, HolderLookup.Provider registries) {
        MovedCharges data = new MovedCharges();
        for (Tag t : tag.getList("Moves", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            if (!entry.hasUUID("Trigger") || !entry.contains("To")) {
                continue;
            }
            Optional<GlobalPos> to = GlobalPos.CODEC.parse(NbtOps.INSTANCE, entry.get("To")).result();
            if (to.isEmpty()) {
                continue;
            }
            GlobalPos from = entry.contains("From")
                    ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, entry.get("From")).result().orElse(null) : null;
            data.moves.computeIfAbsent(entry.getUUID("Trigger"), id -> new ArrayList<>()).add(new Move(from, to.get()));
        }
        return data;
    }
}
