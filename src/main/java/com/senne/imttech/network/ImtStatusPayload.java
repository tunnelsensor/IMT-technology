package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
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
 * @param cooldowns    ticks tot elke functie weer kan, op de volgorde van ImtCooldown
 * @param marked       nummers (entity-id's) van de gemarkeerde doelwitten
 * @param strikeTarget waar de satelliet gaat inslaan (als hij aan het opladen is)
 * @param strikeTicks  ticks tot de inslag
 * @param hackedCount  hoeveel spelers je nu hackt (fase 10)
 * @param threats      nummers van wat jou op dit moment aanvalt (zie ImtThreats)
 * @param brainDelay   hoeveel ticks het brein extra nadenkt op je datacenter (-1 = geen datacenter: geen brein)
 * @param brainSource  op welk datacenter het brein draait ("creative" of "" = nergens)
 */
public record ImtStatusPayload(int swarmDrones, int guardDrones, List<Integer> cooldowns, List<Integer> marked,
                                 Optional<Vec3> strikeTarget, int strikeTicks, int hackedCount,
                                 List<Integer> threats, int brainDelay, String brainSource)
        implements CustomPacketPayload {
    public static final Type<ImtStatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "imt_status"));

    /**
     * Zelf in- en uitpakken. (StreamCodec.composite kan in 1.21.1 maximaal zes onderdelen
     * aan elkaar plakken, en dit bericht heeft er meer.)
     */
    public static final StreamCodec<ByteBuf, ImtStatusPayload> STREAM_CODEC =
            new StreamCodec<ByteBuf, ImtStatusPayload>() {
                @Override
                public void encode(ByteBuf buffer, ImtStatusPayload payload) {
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.swarmDrones());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.guardDrones());
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)).encode(buffer, payload.cooldowns());
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)).encode(buffer, payload.marked());
                    ByteBufCodecs.optional(ModCodecs.VEC3).encode(buffer, payload.strikeTarget());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.strikeTicks());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.hackedCount());
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(32)).encode(buffer, payload.threats());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.brainDelay() + 1); // +1: -1 wordt 0 (klein over het netwerk)
                    ByteBufCodecs.stringUtf8(64).encode(buffer, payload.brainSource());
                }

                @Override
                public ImtStatusPayload decode(ByteBuf buffer) {
                    int swarm = ByteBufCodecs.VAR_INT.decode(buffer);
                    int guards = ByteBufCodecs.VAR_INT.decode(buffer);
                    List<Integer> cooldowns = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)).decode(buffer);
                    List<Integer> marked = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)).decode(buffer);
                    Optional<Vec3> strikeTarget = ByteBufCodecs.optional(ModCodecs.VEC3).decode(buffer);
                    int strikeTicks = ByteBufCodecs.VAR_INT.decode(buffer);
                    int hacked = ByteBufCodecs.VAR_INT.decode(buffer);
                    List<Integer> threats = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(32)).decode(buffer);
                    int brainDelay = ByteBufCodecs.VAR_INT.decode(buffer) - 1;
                    String brainSource = ByteBufCodecs.stringUtf8(64).decode(buffer);
                    return new ImtStatusPayload(swarm, guards, cooldowns, marked, strikeTarget, strikeTicks,
                            hacked, threats, brainDelay, brainSource);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
