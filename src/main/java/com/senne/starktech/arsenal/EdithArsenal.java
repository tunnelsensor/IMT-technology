package com.senne.starktech.arsenal;

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
public record EdithArsenal(List<ArsenalDrone> drones) {

    public static final EdithArsenal EMPTY = new EdithArsenal(List.of());

    /** Zoveel drones passen er in één pakketje naar de speler; genoeg voor elk arsenaal. */
    private static final int MAX_SYNCED = 4096;

    public static final MapCodec<EdithArsenal> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ArsenalDrone.CODEC.listOf().optionalFieldOf("drones", List.of()).forGetter(EdithArsenal::drones)
    ).apply(instance, EdithArsenal::new));

    public static final StreamCodec<ByteBuf, EdithArsenal> STREAM_CODEC = StreamCodec.composite(
            ArsenalDrone.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SYNCED)), EdithArsenal::drones,
            EdithArsenal::new);

    public EdithArsenal {
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
        int count = 0;
        for (ArsenalDrone drone : drones) {
            if (drone.isAvailable()) {
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
        List<ArsenalDrone> ready = new ArrayList<>();
        for (ArsenalDrone drone : drones) {
            if (drone.isAvailable()) {
                ready.add(drone);
            }
        }
        ready.sort((a, b) -> Float.compare(a.health(), b.health()));
        return ready.size() <= wanted ? List.copyOf(ready) : List.copyOf(ready.subList(0, wanted));
    }

    public EdithArsenal with(ArsenalDrone drone) {
        List<ArsenalDrone> list = new ArrayList<>(drones);
        list.add(drone);
        return new EdithArsenal(list);
    }

    public EdithArsenal without(UUID id) {
        List<ArsenalDrone> list = new ArrayList<>(drones);
        list.removeIf(drone -> drone.id().equals(id));
        return list.size() == drones.size() ? this : new EdithArsenal(list);
    }

    /** Eén drone vervangen (andere schade of een andere plek). Onbekend nummer? Dan verandert er niets. */
    public EdithArsenal replace(ArsenalDrone drone) {
        List<ArsenalDrone> list = new ArrayList<>(drones);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(drone.id())) {
                list.set(i, drone);
                return new EdithArsenal(list);
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
    public EdithArsenal groundDeployed() {
        List<ArsenalDrone> list = new ArrayList<>(drones.size());
        boolean changed = false;
        for (ArsenalDrone drone : drones) {
            if (drone.state() == ArsenalDrone.State.DEPLOYED) {
                list.add(drone.withState(ArsenalDrone.State.ORBIT));
                changed = true;
            } else {
                list.add(drone);
            }
        }
        return changed ? new EdithArsenal(list) : this;
    }
}
