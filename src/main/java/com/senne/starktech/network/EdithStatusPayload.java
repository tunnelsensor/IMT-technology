package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Bericht van server naar speler (twee keer per seconde): hoe staat het met de drones,
 * de wachttijden, de gemarkeerde doelwitten en een eventuele satelliet-aanval?
 * De HUD gebruikt dit om alles te tonen.
 *
 * @param swarmDrones  aantal actieve aanvalsdrones
 * @param guardDrones  aantal actieve beschermende drones
 * @param cooldowns    ticks tot elke functie weer kan, op de volgorde van EdithCooldown
 * @param marked       nummers (entity-id's) van de gemarkeerde doelwitten
 * @param strikeTarget waar de satelliet gaat inslaan (als hij aan het opladen is)
 * @param strikeTicks  ticks tot de inslag
 * @param hackedCount  hoeveel spelers je nu hackt (fase 10)
 * @param threats      nummers van wat jou op dit moment aanvalt (zie EdithThreats)
 */
public record EdithStatusPayload(int swarmDrones, int guardDrones, List<Integer> cooldowns, List<Integer> marked,
                                 Optional<Vec3> strikeTarget, int strikeTicks, int hackedCount,
                                 List<Integer> threats)
        implements CustomPacketPayload {
    public static final Type<EdithStatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "edith_status"));

    /**
     * Zelf in- en uitpakken. (StreamCodec.composite kan in 1.21.1 maximaal zes onderdelen
     * aan elkaar plakken, en dit bericht heeft er meer.)
     */
    public static final StreamCodec<ByteBuf, EdithStatusPayload> STREAM_CODEC =
            new StreamCodec<ByteBuf, EdithStatusPayload>() {
                @Override
                public void encode(ByteBuf buffer, EdithStatusPayload payload) {
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.swarmDrones());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.guardDrones());
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)).encode(buffer, payload.cooldowns());
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)).encode(buffer, payload.marked());
                    ByteBufCodecs.optional(ModCodecs.VEC3).encode(buffer, payload.strikeTarget());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.strikeTicks());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.hackedCount());
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(32)).encode(buffer, payload.threats());
                }

                @Override
                public EdithStatusPayload decode(ByteBuf buffer) {
                    int swarm = ByteBufCodecs.VAR_INT.decode(buffer);
                    int guards = ByteBufCodecs.VAR_INT.decode(buffer);
                    List<Integer> cooldowns = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)).decode(buffer);
                    List<Integer> marked = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)).decode(buffer);
                    Optional<Vec3> strikeTarget = ByteBufCodecs.optional(ModCodecs.VEC3).decode(buffer);
                    int strikeTicks = ByteBufCodecs.VAR_INT.decode(buffer);
                    int hacked = ByteBufCodecs.VAR_INT.decode(buffer);
                    List<Integer> threats = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(32)).decode(buffer);
                    return new EdithStatusPayload(swarm, guards, cooldowns, marked, strikeTarget, strikeTicks,
                            hacked, threats);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
