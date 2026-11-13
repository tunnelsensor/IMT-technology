package com.senne.imttech.arsenal;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Het drone-arsenaal van één speler: alle drones die hij ooit gebouwd heeft en die nog niet
 * zijn neergehaald.
 *
 * Het arsenaal is van jou persoonlijk en je kunt er overal bij, want je drones hangen in een
 * baan om de planeet. Er kunnen er zoveel in als je wil.
 *
 * Net als bij de waypoints verandert dit object nooit: bij elke wijziging maken we een nieuw
 * object, zodat NeoForge weet dat het opnieuw naar de speler gestuurd moet worden.
 */
public record ImtArsenal(List<ArsenalDrone> drones) {

    public static final ImtArsenal EMPTY = new ImtArsenal(List.of());

    /** Zoveel drones passen er in één pakketje naar de speler; genoeg voor elk arsenaal. */
    private static final int MAX_SYNCED = 4096;

    public static final MapCodec<ImtArsenal> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ArsenalDrone.CODEC.listOf().optionalFieldOf("drones", List.of()).forGetter(ImtArsenal::drones)
    ).apply(instance, ImtArsenal::new));

    public static final StreamCodec<ByteBuf, ImtArsenal> STREAM_CODEC = StreamCodec.composite(
            ArsenalDrone.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SYNCED)), ImtArsenal::drones,
            ImtArsenal::new);

    public ImtArsenal {
        drones = List.copyOf(drones);
    }

    public int size() {
        return drones.size();
    }

    public @Nullable ArsenalDrone find(UUID id) {
        for (ArsenalDrone drone : drones) {
            if (drone.id().equals(id)) {
                return drone;
            }
        }
        return null;
    }

    /** Hoeveel drones er klaar in een baan hangen. */
    public int availableCount() {
        return readyCount(true);
    }

    /**
     * Hoofdstuk 106: hoeveel drones er klaar zijn om op pad te gaan in een wereld met een baan ({@code orbit} true: wat
     * in een baan hangt) of zonder ({@code orbit} false, de Nether en het End: wat bij je wacht). Zie {@link Orbit}.
     */
    public int readyCount(boolean orbit) {
        int count = 0;
        for (ArsenalDrone drone : drones) {
            if (drone.isReady(orbit)) {
                count++;
            }
        }
        return count;
    }

    /** Hoeveel drones er in een baan hangen (heel of niet): in de Nether en het End kun je daar niet bij. */
    public int orbitCount() {
        int count = 0;
        for (ArsenalDrone drone : drones) {
            if (drone.state() == ArsenalDrone.State.ORBIT) {
                count++;
            }
        }
        return count;
    }

    /** Hoeveel er nu in de wereld bezig zijn. */
    public int deployedCount() {
        int count = 0;
        for (ArsenalDrone drone : drones) {
            if (drone.state() == ArsenalDrone.State.DEPLOYED) {
                count++;
            }
        }
        return count;
    }

    /**
     * De volgende drones die op pad mogen: de meest gehavende eerst, zodat een zwerm de
     * drones gebruikt die toch al beschadigd zijn en je beste drones heel blijven.
     */
    public List<ArsenalDrone> pickAvailable(int wanted) {
        return pickReady(wanted, true);
    }

    /** Hetzelfde, maar uit wat klaar is in deze wereld: uit je baan ({@code orbit} true) of van bij jou (false). */
    public List<ArsenalDrone> pickReady(int wanted, boolean orbit) {
        List<ArsenalDrone> ready = new ArrayList<>();
        for (ArsenalDrone drone : drones) {
            if (drone.isReady(orbit)) {
                ready.add(drone);
            }
        }
        ready.sort((a, b) -> Float.compare(a.health(), b.health()));
        return ready.size() <= wanted ? List.copyOf(ready) : List.copyOf(ready.subList(0, wanted));
    }

    public ImtArsenal with(ArsenalDrone drone) {
        List<ArsenalDrone> list = new ArrayList<>(drones);
        list.add(drone);
        return new ImtArsenal(list);
    }

    public ImtArsenal without(UUID id) {
        List<ArsenalDrone> list = new ArrayList<>(drones);
        list.removeIf(drone -> drone.id().equals(id));
        return list.size() == drones.size() ? this : new ImtArsenal(list);
    }

    /** Eén drone vervangen (andere schade of een andere plek). Onbekend nummer? Dan verandert er niets. */
    public ImtArsenal replace(ArsenalDrone drone) {
        List<ArsenalDrone> list = new ArrayList<>(drones);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(drone.id())) {
                list.set(i, drone);
                return new ImtArsenal(list);
            }
        }
        return this;
    }

    /**
     * Alles wat "onderweg" stond weer op klaar zetten.
     *
     * Dit doen we als je in de wereld komt: je drones verdwijnen uit de wereld zodra jij weg
     * bent, dus een drone die nog als "bezig" geboekt staat, hangt in werkelijkheid gewoon
     * weer in een baan.
     */
    public ImtArsenal groundDeployed() {
        return groundDeployed(true);
    }

    /**
     * Hetzelfde, in een wereld met ({@code orbit} true) of zonder baan (false, hoofdstuk 106). Zonder baan: wat onderweg
     * stond, was daar met je mee en wacht nu bij jou. Met een baan: ook wat bij je wachtte, hangt weer in zijn baan.
     */
    public ImtArsenal groundDeployed(boolean orbit) {
        ArsenalDrone.State home = ArsenalDrone.home(orbit);
        List<ArsenalDrone> list = new ArrayList<>(drones.size());
        boolean changed = false;
        for (ArsenalDrone drone : drones) {
            boolean move = drone.state() == ArsenalDrone.State.DEPLOYED
                    || (orbit && drone.state() == ArsenalDrone.State.STANDBY);
            if (move) {
                list.add(drone.withState(home));
                changed = true;
            } else {
                list.add(drone);
            }
        }
        return changed ? new ImtArsenal(list) : this;
    }

    /**
     * Hoofdstuk 106: je bent in een wereld met een baan: wat bij je wachtte (in de Nether of het End) is met je
     * meegekomen en hangt weer in zijn baan.
     */
    public ImtArsenal standbyToOrbit() {
        List<ArsenalDrone> list = new ArrayList<>(drones.size());
        boolean changed = false;
        for (ArsenalDrone drone : drones) {
            if (drone.state() == ArsenalDrone.State.STANDBY) {
                list.add(drone.withState(ArsenalDrone.State.ORBIT));
                changed = true;
            } else {
                list.add(drone);
            }
        }
        return changed ? new ImtArsenal(list) : this;
    }

    /**
     * Alle "staat in de hangar"-vlaggen weg. Bij het inloggen: de drone-docks die geladen zijn,
     * zetten ze binnen een halve seconde weer aan (een dock die intussen kapot is, niet).
     */
    public ImtArsenal undocked() {
        List<ArsenalDrone> list = new ArrayList<>(drones.size());
        boolean changed = false;
        for (ArsenalDrone drone : drones) {
            list.add(drone.withDocked(false));
            changed |= drone.docked();
        }
        return changed ? new ImtArsenal(list) : this;
    }
}
