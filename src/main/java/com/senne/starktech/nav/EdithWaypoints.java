package com.senne.starktech.nav;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Alle waypoints van één speler, plus waar hij op dit moment naartoe navigeert.
 * Wordt bij de speler opgeslagen (zie ModAttachments) en blijft bewaard als je doodgaat.
 *
 * Dit object verandert nooit; bij een wijziging maken we een nieuw object. Zo weet
 * NeoForge dat het opnieuw naar de speler gestuurd moet worden.
 *
 * @param waypoints de opgeslagen plekken
 * @param active    waar je naartoe navigeert: een nummer uit de lijst, NONE, of DEATH (laatste sterfplek)
 */
public record EdithWaypoints(List<Waypoint> waypoints, int active) {
    public static final int NONE = -1;
    public static final int DEATH = -2;

    public static final EdithWaypoints EMPTY = new EdithWaypoints(List.of(), NONE);

    public static final MapCodec<EdithWaypoints> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Waypoint.CODEC.listOf().optionalFieldOf("waypoints", List.of()).forGetter(EdithWaypoints::waypoints),
            Codec.INT.optionalFieldOf("active", NONE).forGetter(EdithWaypoints::active)
    ).apply(instance, EdithWaypoints::new));

    public static final StreamCodec<ByteBuf, EdithWaypoints> STREAM_CODEC = StreamCodec.composite(
            Waypoint.STREAM_CODEC.apply(ByteBufCodecs.list(64)), EdithWaypoints::waypoints,
            ByteBufCodecs.VAR_INT.map(i -> i - 2, i -> i + 2), EdithWaypoints::active,
            EdithWaypoints::new);

    public EdithWaypoints {
        waypoints = List.copyOf(waypoints);
    }

    /** Het waypoint waar je naartoe navigeert, of null (ook null als je naar je sterfplek gaat). */
    public @Nullable Waypoint activeWaypoint() {
        return active >= 0 && active < waypoints.size() ? waypoints.get(active) : null;
    }

    public EdithWaypoints with(Waypoint waypoint) {
        List<Waypoint> list = new ArrayList<>(waypoints);
        list.add(waypoint);
        return new EdithWaypoints(list, active);
    }

    public EdithWaypoints without(int index) {
        if (index < 0 || index >= waypoints.size()) {
            return this;
        }
        List<Waypoint> list = new ArrayList<>(waypoints);
        list.remove(index);
        // Het actieve nummer schuift mee als er een waypoint vóór wordt verwijderd.
        int newActive = active == index ? NONE : active > index ? active - 1 : active;
        return new EdithWaypoints(list, newActive);
    }

    /** Eén waypoint vervangen (naam of kleur gewijzigd); de plek blijft waar hij was. */
    public EdithWaypoints replace(int index, Waypoint waypoint) {
        if (index < 0 || index >= waypoints.size()) {
            return this;
        }
        List<Waypoint> list = new ArrayList<>(waypoints);
        list.set(index, waypoint);
        return new EdithWaypoints(list, active);
    }

    public EdithWaypoints withActive(int newActive) {
        boolean valid = newActive == NONE || newActive == DEATH || (newActive >= 0 && newActive < waypoints.size());
        return new EdithWaypoints(waypoints, valid ? newActive : NONE);
    }
}
