package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
    public static final Type<SpyStatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "spy_status"));

    /** "Er is geen opdracht (meer)." */
    public static final SpyStatusPayload NONE =
            new SpyStatusPayload(false, Component.empty(), Optional.empty(), Level.OVERWORLD, 0, 0, 20, 0);

    /**
     * Zelf in- en uitpakken. (StreamCodec.composite kan in 1.21.1 maximaal zes onderdelen
     * aan elkaar plakken, en dit bericht heeft er acht.)
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, SpyStatusPayload> STREAM_CODEC =
            new StreamCodec<RegistryFriendlyByteBuf, SpyStatusPayload>() {
                @Override
                public void encode(RegistryFriendlyByteBuf buffer, SpyStatusPayload payload) {
                    buffer.writeBoolean(payload.active());
                    ComponentSerialization.STREAM_CODEC.encode(buffer, payload.targetName());
                    buffer.writeBoolean(payload.pos().isPresent());
                    payload.pos().ifPresent(pos -> ModCodecs.VEC3.encode(buffer, pos));
                    buffer.writeResourceKey(payload.dimension());
                    buffer.writeVarInt(payload.ticksLeft());
                    buffer.writeFloat(payload.health());
                    buffer.writeFloat(payload.maxHealth());
                    buffer.writeVarInt(payload.logLines());
                }

                @Override
                public SpyStatusPayload decode(RegistryFriendlyByteBuf buffer) {
                    boolean active = buffer.readBoolean();
                    Component name = ComponentSerialization.STREAM_CODEC.decode(buffer);
                    Optional<Vec3> pos = buffer.readBoolean() ? Optional.of(ModCodecs.VEC3.decode(buffer)) : Optional.empty();
                    ResourceKey<Level> dimension = buffer.readResourceKey(Registries.DIMENSION);
                    int ticksLeft = buffer.readVarInt();
                    float health = buffer.readFloat();
                    float maxHealth = buffer.readFloat();
                    int logLines = buffer.readVarInt();
                    return new SpyStatusPayload(active, name, pos, dimension, ticksLeft, health, maxHealth, logLines);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
