package com.senne.starktech.arsenal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.starktech.config.StarkConfig;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * Eén drone uit je arsenaal.
 *
 * Een drone is van jou persoonlijk en blijft bestaan, ook als je hem niet gebruikt: hij hangt
 * dan in een baan om de planeet te wachten. Zijn schade blijft bewaard, dus een drone die
 * gehavend terugkomt, gaat gehavend weer omhoog tot je hem repareert.
 *
 * @param id     het vaste nummer van deze drone; hiermee vindt de drone in de wereld zichzelf terug
 * @param health hoeveel levenspunten hij nog heeft
 * @param state  waar hij nu is (zie {@link State})
 * @param docked stond hij vroeger op een drone-dock in je hangar? Sinds hoofdstuk 25 niet meer gebruikt (drones blijven in
 *               hun baan); het blijft bestaan zodat oude werelden nog laden, en gaat bij het inloggen weg.
 *               Klaar voor gebruik staat hij dan daar, en niet in een baan om de planeet.
 */
public record ArsenalDrone(UUID id, float health, State state, boolean docked) {

    /** Waar een drone zich bevindt. */
    public enum State {
        /** In een baan om de planeet: klaar voor gebruik. */
        ORBIT,
        /** Nu in de wereld, bezig met een opdracht. */
        DEPLOYED,
        /** Teruggeroepen en aan de grond: je kunt hem repareren of weer omhoog sturen. */
        GROUNDED;

        public static State byId(int id) {
            State[] all = values();
            return id >= 0 && id < all.length ? all[id] : ORBIT;
        }

        public String translationKey() {
            return "arsenal.starktech.state." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final Codec<ArsenalDrone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ArsenalDrone::id),
            Codec.FLOAT.optionalFieldOf("health", (float) StarkConfig.Drones.HEALTH).forGetter(ArsenalDrone::health),
            Codec.INT.optionalFieldOf("state", State.ORBIT.ordinal()).xmap(State::byId, Enum::ordinal).forGetter(ArsenalDrone::state),
            Codec.BOOL.optionalFieldOf("docked", false).forGetter(ArsenalDrone::docked)
    ).apply(instance, ArsenalDrone::new));

    public static final StreamCodec<ByteBuf, ArsenalDrone> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ArsenalDrone::id,
            ByteBufCodecs.FLOAT, ArsenalDrone::health,
            ByteBufCodecs.VAR_INT.map(State::byId, Enum::ordinal), ArsenalDrone::state,
            ByteBufCodecs.BOOL, ArsenalDrone::docked,
            ArsenalDrone::new);

    /** Een drone zonder vaste plek in een hangar. */
    public ArsenalDrone(UUID id, float health, State state) {
        this(id, health, state, false);
    }

    /** Een gloednieuwe drone, helemaal heel. */
    public static ArsenalDrone fresh(UUID id) {
        return new ArsenalDrone(id, (float) StarkConfig.Drones.HEALTH, State.ORBIT);
    }

    public ArsenalDrone {
        health = Mth.clamp(health, 0.0f, (float) StarkConfig.Drones.HEALTH);
    }

    /** Hoe heel hij is, van 0.0 (schroot) tot 1.0 (nieuw). */
    public float healthFraction() {
        return health / (float) StarkConfig.Drones.HEALTH;
    }

    public boolean isFullyRepaired() {
        return health >= (float) StarkConfig.Drones.HEALTH;
    }

    /** Klaar om op pad te gaan: hij hangt in een baan en is niet tot schroot geschoten. */
    public boolean isAvailable() {
        return state == State.ORBIT && health > 0.0f;
    }

    public ArsenalDrone withState(State newState) {
        return new ArsenalDrone(id, health, newState, docked);
    }

    public ArsenalDrone withHealth(float newHealth) {
        return new ArsenalDrone(id, newHealth, state, docked);
    }

    public ArsenalDrone withDocked(boolean newDocked) {
        return new ArsenalDrone(id, health, state, newDocked);
    }

    /** Waar hij is, voor het arsenaalscherm: "In hangar" in plaats van "In orbit" als hij op een dock staat. */
    public String whereKey() {
        return docked && state == State.ORBIT ? "arsenal.starktech.state.docked" : state.translationKey();
    }
}
