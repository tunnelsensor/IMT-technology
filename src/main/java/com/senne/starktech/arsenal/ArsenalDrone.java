package com.senne.starktech.arsenal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
 */
public record ArsenalDrone(UUID id, float health, State state) {

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
            Codec.FLOAT.optionalFieldOf("health", (float) ArsenalBalance.DRONE_MAX_HEALTH).forGetter(ArsenalDrone::health),
            Codec.INT.optionalFieldOf("state", State.ORBIT.ordinal()).xmap(State::byId, Enum::ordinal).forGetter(ArsenalDrone::state)
    ).apply(instance, ArsenalDrone::new));

    public static final StreamCodec<ByteBuf, ArsenalDrone> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ArsenalDrone::id,
            ByteBufCodecs.FLOAT, ArsenalDrone::health,
            ByteBufCodecs.VAR_INT.map(State::byId, Enum::ordinal), ArsenalDrone::state,
            ArsenalDrone::new);

    /** Een gloednieuwe drone, helemaal heel. */
    public static ArsenalDrone fresh(UUID id) {
        return new ArsenalDrone(id, (float) ArsenalBalance.DRONE_MAX_HEALTH, State.ORBIT);
    }

    public ArsenalDrone {
        health = Mth.clamp(health, 0.0f, (float) ArsenalBalance.DRONE_MAX_HEALTH);
    }

    /** Hoe heel hij is, van 0.0 (schroot) tot 1.0 (nieuw). */
    public float healthFraction() {
        return health / (float) ArsenalBalance.DRONE_MAX_HEALTH;
    }

    public boolean isFullyRepaired() {
        return health >= (float) ArsenalBalance.DRONE_MAX_HEALTH;
    }

    /** Klaar om op pad te gaan: hij hangt in een baan en is niet tot schroot geschoten. */
    public boolean isAvailable() {
        return state == State.ORBIT && health > 0.0f;
    }

    public ArsenalDrone withState(State newState) {
        return new ArsenalDrone(id, health, newState);
    }

    public ArsenalDrone withHealth(float newHealth) {
        return new ArsenalDrone(id, newHealth, state);
    }
}
