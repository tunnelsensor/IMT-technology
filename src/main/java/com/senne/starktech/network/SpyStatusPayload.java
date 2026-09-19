package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Bericht van server naar speler (twee keer per seconde): wat de spionagedrone ziet.
 * Zo kan de HUD het doelwit live volgen, ook als het aan de andere kant van de wereld is.
 *
 * @param active     is er een spionage-opdracht bezig?
 * @param targetName naam van het doelwit
 * @param pos        laatst bekende plek van het doelwit
 * @param dimension  in welke wereld het doelwit is
 * @param ticksLeft  hoeveel ticks de opdracht nog duurt
 * @param health     levenspunten van het doelwit
 * @param maxHealth  maximale levenspunten van het doelwit
 * @param logLines   hoeveel regels er al in het logboek staan
 */
public record SpyStatusPayload(boolean active, Component targetName, Optional<Vec3> pos, ResourceKey<Level> dimension,
                               int ticksLeft, float health, float maxHealth, int logLines) implements CustomPacketPayload {
    public static final Type<SpyStatusPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StarkTech.MODID, "spy_status"));

    /** "Er is geen opdracht (meer)." */
    public static final SpyStatusPayload NONE =
            new SpyStatusPayload(false, Component.empty(), Optional.empty(), Level.OVERWORLD, 0, 0, 20, 0);

    public static final StreamCodec<RegistryFriendlyByteBuf, SpyStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SpyStatusPayload::active,
            ComponentSerialization.STREAM_CODEC, SpyStatusPayload::targetName,
            ByteBufCodecs.optional(Vec3.STREAM_CODEC), SpyStatusPayload::pos,
            ResourceKey.streamCodec(Registries.DIMENSION), SpyStatusPayload::dimension,
            ByteBufCodecs.VAR_INT, SpyStatusPayload::ticksLeft,
            ByteBufCodecs.FLOAT, SpyStatusPayload::health,
            ByteBufCodecs.FLOAT, SpyStatusPayload::maxHealth,
            ByteBufCodecs.VAR_INT, SpyStatusPayload::logLines,
            SpyStatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
