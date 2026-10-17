package com.senne.imttech.nav;

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
public record ImtWaypoints(List<Waypoint> waypoints, int active) {
    public static final int NONE = -1;
    public static final int DEATH = -2;

    public static final ImtWaypoints EMPTY = new ImtWaypoints(List.of(), NONE);

    public static final MapCodec<ImtWaypoints> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Waypoint.CODEC.listOf().optionalFieldOf("waypoints", List.of()).forGetter(ImtWaypoints::waypoints),
            Codec.INT.optionalFieldOf("active", NONE).forGetter(ImtWaypoints::active)
    ).apply(instance, ImtWaypoints::new));

    public static final StreamCodec<ByteBuf, ImtWaypoints> STREAM_CODEC = StreamCodec.composite(
            Waypoint.STREAM_CODEC.apply(ByteBufCodecs.list(64)), ImtWaypoints::waypoints,
            ByteBufCodecs.VAR_INT.map(i -> i - 2, i -> i + 2), ImtWaypoints::active,
            ImtWaypoints::new);

    public ImtWaypoints {
        waypoints = List.copyOf(waypoints);
    }

    /** Het waypoint waar je naartoe navigeert, of null (ook null als je naar je sterfplek gaat). */
    public @Nullable Waypoint activeWaypoint() {
        return active >= 0 && active < waypoints.size() ? waypoints.get(active) : null;
    }

    public ImtWaypoints with(Waypoint waypoint) {
        List<Waypoint> list = new ArrayList<>(waypoints);
        list.add(waypoint);
        return new ImtWaypoints(list, active);
    }

    public ImtWaypoints without(int index) {
        if (index < 0 || index >= waypoints.size()) {
            return this;
        }
        List<Waypoint> list = new ArrayList<>(waypoints);
        list.remove(index);
        // Het actieve nummer schuift mee als er een waypoint vóór wordt verwijderd.
        int newActive = active == index ? NONE : active > index ? active - 1 : active;
        return new ImtWaypoints(list, newActive);
    }

    /** Eén waypoint vervangen (naam of kleur gewijzigd); de plek blijft waar hij was. */
    public ImtWaypoints replace(int index, Waypoint waypoint) {
        if (index < 0 || index >= waypoints.size()) {
            return this;
        }
        List<Waypoint> list = new ArrayList<>(waypoints);
        list.set(index, waypoint);
        return new ImtWaypoints(list, active);
    }

    public ImtWaypoints withActive(int newActive) {
        boolean valid = newActive == NONE || newActive == DEATH || (newActive >= 0 && newActive < waypoints.size());
        return new ImtWaypoints(waypoints, valid ? newActive : NONE);
    }
}
