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
 */
public record EdithStatusPayload(int swarmDrones, int guardDrones, List<Integer> cooldowns, List<Integer> marked,
                                 Optional<Vec3> strikeTarget, int strikeTicks) implements CustomPacketPayload {
    public static final Type<EdithStatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "edith_status"));

    public static final StreamCodec<ByteBuf, EdithStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EdithStatusPayload::swarmDrones,
            ByteBufCodecs.VAR_INT, EdithStatusPayload::guardDrones,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)), EdithStatusPayload::cooldowns,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)), EdithStatusPayload::marked,
            ByteBufCodecs.optional(ModCodecs.VEC3), EdithStatusPayload::strikeTarget,
            ByteBufCodecs.VAR_INT, EdithStatusPayload::strikeTicks,
            EdithStatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
