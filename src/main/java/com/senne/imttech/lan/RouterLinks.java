package com.senne.imttech.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Wie draadloos met welke Router verbonden is (hoofdstuk 112, jouw wens: "een router: hier kun je elk ding draadloos tot
 * op 32 blokken op connecten ... elke router kan max 10 devices en kan ook verbinden met andere routers, die horen dan ook
 * bij één van de devices").
 *
 * Eén lijst per wereld (overworld, Nether, End elk hun eigen), bewaard in de wereld zelf (data/imttech_routers.dat). Zo
 * hoeft geen enkel apparaat zelf te onthouden aan welke router het hangt: een relais, camera of sensor blijft zoals hij
 * was.
 * <ul>
 *   <li>Een <b>apparaat</b> (alles met een netwerkaansluiting dat geen router is) hangt aan hooguit één router: verbindt
 *       een andere router het, dan verhuist het.</li>
 *   <li>Twee <b>routers</b> kunnen met elkaar verbonden zijn; dat kost bij allebei één plek.</li>
 *   <li>De <b>Security Core</b> is de uitzondering: die mag aan meer routers tegelijk hangen (bij elke router één plek).
 *       Anders pakte een tweede router de kern af van de eerste, en viel alles aan de eerste zonder netwerk. (Gevonden
 *       door de nalezing.) Wie verbindt, zegt erbij of het zo'n gedeeld apparaat is (zie {@link #link}).</li>
 * </ul>
 * Dit bestand weet alleen wie met wie verbonden is. De regels (hoe ver, hoeveel, wie het mag) staan in {@link Routers};
 * het zoeken langs kabels en routers in {@link LanGrid}.
 */
public final class RouterLinks extends SavedData {
    private static final String NAME = "imttech_routers";
    /** Hoe de wereld deze lijst maakt en leest. Eén keer gemaakt: het netwerk vraagt de lijst bij elke stap van zijn zoektocht. */
    private static final SavedData.Factory<RouterLinks> FACTORY = new SavedData.Factory<>(RouterLinks::new, RouterLinks::load);

    /** Per router: met wie hij verbonden is (apparaten en andere routers), in de volgorde waarin dat gebeurde. */
    private final Map<BlockPos, LinkedHashSet<BlockPos>> links = new HashMap<>();
    /** Per apparaat (geen router): aan welke router het hangt. Bij een kern kunnen dat er meer zijn. */
    private final Map<BlockPos, LinkedHashSet<BlockPos>> routersOf = new HashMap<>();

    public static RouterLinks get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    // ================================================================ Opvragen

    /** Staat er op deze plek een router (die zich hier aanmeldde)? */
    public boolean isRouter(BlockPos pos) {
        return links.containsKey(pos);
    }

    /** Alle routers van deze wereld. */
    public Set<BlockPos> routers() {
        return Set.copyOf(links.keySet());
    }

    /** Met wie deze router verbonden is (apparaten en routers), in de volgorde van verbinden. Leeg als het geen router is. */
    public List<BlockPos> linked(BlockPos router) {
        Set<BlockPos> set = links.get(router);
        return set == null ? List.of() : List.copyOf(set);
    }

    /** Hoeveel plekken van deze router bezet zijn. */
    public int count(BlockPos router) {
        Set<BlockPos> set = links.get(router);
        return set == null ? 0 : set.size();
    }

    /**
     * De router waar dit apparaat aan hangt, of null. Hangt het aan meer routers (een kern), dan de eerste. Voor een router
     * zelf: altijd null (met wie die verbonden is, zegt {@link #linked(BlockPos)}).
     */
    public @Nullable BlockPos routerOf(BlockPos device) {
        Set<BlockPos> routers = routersOf.get(device);
        return routers == null || routers.isEmpty() ? null : routers.iterator().next();
    }

    /** Alle routers waar dit apparaat aan hangt: geen, één, of (bij een kern) meer. */
    public List<BlockPos> routersOf(BlockPos device) {
        Set<BlockPos> routers = routersOf.get(device);
        return routers == null ? List.of() : List.copyOf(routers);
    }

    /** Zijn deze twee draadloos met elkaar verbonden? */
    public boolean linked(BlockPos router, BlockPos other) {
        Set<BlockPos> set = links.get(router);
        return set != null && set.contains(other);
    }

    /**
     * De draadloze buren van deze plek: van een router alles waar hij mee verbonden is, van een apparaat zijn router (van
     * een kern al zijn routers). Een nieuwe lijst: wie hem afloopt, mag ondertussen verbindingen weghalen.
     */
    public List<BlockPos> partners(BlockPos pos) {
        Set<BlockPos> set = links.get(pos);
        if (set == null) {
            set = routersOf.get(pos);
        }
        return set == null ? List.of() : new ArrayList<>(set);
    }

    // ================================================================ Veranderen

    /** Een router meldt zich aan (net neergezet). */
    public void addRouter(BlockPos pos) {
        BlockPos router = pos.immutable();
        // Hing er op deze plek eerst een gewoon apparaat aan een router? Dat is er niet meer.
        forget(router);
        if (links.putIfAbsent(router, new LinkedHashSet<>()) == null) {
            changed();
        }
    }

    /** Verbindt een router met een gewoon apparaat of met een andere router (zie {@link #link(BlockPos, BlockPos, boolean)}). */
    public boolean link(BlockPos router, BlockPos other) {
        return link(router, other, false);
    }

    /**
     * Verbindt een router met een apparaat of met een andere router. Een gewoon apparaat dat al aan een andere router hing,
     * gaat daar eerst af (het verhuist). Met {@code shared} (de Security Core) blijft het ook aan zijn andere routers
     * hangen. Geeft false als er niets veranderde (geen router, al verbonden, of met zichzelf).
     */
    public boolean link(BlockPos routerPos, BlockPos otherPos, boolean shared) {
        BlockPos router = routerPos.immutable();
        BlockPos other = otherPos.immutable();
        Set<BlockPos> set = links.get(router);
        if (set == null || router.equals(other) || set.contains(other)) {
            return false;
        }
        Set<BlockPos> otherSet = links.get(other);
        if (otherSet != null) {
            otherSet.add(router); // twee routers: bij allebei één plek
        } else {
            if (!shared) {
                detach(other);
            }
            routersOf.computeIfAbsent(other, key -> new LinkedHashSet<>()).add(router);
        }
        set.add(other);
        changed();
        return true;
    }

    /** Haalt de verbinding tussen deze router en dit apparaat (of deze andere router) weg. */
    public boolean unlink(BlockPos router, BlockPos other) {
        Set<BlockPos> set = links.get(router);
        if (set == null || !set.remove(other)) {
            return false;
        }
        release(other, router);
        changed();
        return true;
    }

    /** De router op deze plek is weg: al zijn verbindingen ook. */
    public void removeRouter(BlockPos router) {
        Set<BlockPos> set = links.remove(router);
        if (set == null) {
            return;
        }
        for (BlockPos other : set) {
            release(other, router);
        }
        changed();
    }

    /** Het apparaat op deze plek is weg (of werd iets anders): het hangt aan geen router meer. */
    public void forget(BlockPos device) {
        if (detach(device)) {
            changed();
        }
    }

    /** Haalt een apparaat van al zijn routers af. Geeft of het ergens aan hing. */
    private boolean detach(BlockPos device) {
        Set<BlockPos> routers = routersOf.remove(device);
        if (routers == null) {
            return false;
        }
        for (BlockPos router : routers) {
            Set<BlockPos> set = links.get(router);
            if (set != null) {
                set.remove(device);
            }
        }
        return true;
    }

    /** {@code other} (een router of een apparaat) is niet meer met {@code router} verbonden: ook aan zijn kant weghalen. */
    private void release(BlockPos other, BlockPos router) {
        Set<BlockPos> otherSet = links.get(other);
        if (otherSet != null) {
            otherSet.remove(router);
            return;
        }
        Set<BlockPos> routers = routersOf.get(other);
        if (routers != null && routers.remove(router) && routers.isEmpty()) {
            routersOf.remove(other);
        }
    }

    /** Er veranderde iets: bewaren, en alle netten opnieuw zoeken. */
    private void changed() {
        setDirty();
        LanGrid.changed();
    }

    // ================================================================ Bewaren

    /**
     * Per router zijn plek en met wie hij verbonden is. Aan welke routers een apparaat hangt, bewaren we niet apart: dat
     * volgt bij het lezen uit de lijsten van de routers (een kern staat gewoon in de lijst van elk van zijn routers).
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        links.forEach((router, set) -> {
            CompoundTag item = new CompoundTag();
            item.put("Pos", NbtUtils.writeBlockPos(router));
            ListTag others = new ListTag();
            for (BlockPos other : set) {
                others.add(NbtUtils.writeBlockPos(other));
            }
            item.put("Links", others);
            list.add(item);
        });
        tag.put("Routers", list);
        return tag;
    }

    public static RouterLinks load(CompoundTag tag, HolderLookup.Provider registries) {
        RouterLinks data = new RouterLinks();
        // Eerst alle routers (zo weten we bij het lezen van de verbindingen wie een router is).
        for (Tag element : tag.getList("Routers", Tag.TAG_COMPOUND)) {
            NbtUtils.readBlockPos((CompoundTag) element, "Pos").ifPresent(pos -> data.links.put(pos, new LinkedHashSet<>()));
        }
        for (Tag element : tag.getList("Routers", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) element;
            BlockPos router = NbtUtils.readBlockPos(item, "Pos").orElse(null);
            Set<BlockPos> set = router == null ? null : data.links.get(router);
            if (set == null) {
                continue;
            }
            for (Tag link : item.getList("Links", Tag.TAG_INT_ARRAY)) {
                int[] xyz = ((net.minecraft.nbt.IntArrayTag) link).getAsIntArray();
                if (xyz.length != 3) {
                    continue;
                }
                BlockPos other = new BlockPos(xyz[0], xyz[1], xyz[2]);
                set.add(other);
                if (!data.links.containsKey(other)) {
                    data.routersOf.computeIfAbsent(other, key -> new LinkedHashSet<>()).add(router);
                }
            }
        }
        return data;
    }
}
