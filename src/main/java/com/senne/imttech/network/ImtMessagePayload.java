package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "I.M.T zegt iets". Het verschijnt in de HUD
 * en speelt een geluidje af dat past bij het soort bericht.
 */
public record ImtMessagePayload(Component message, Level level) implements CustomPacketPayload {
    public static final Type<ImtMessagePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "imt_message"));

    /** Soort bericht: bepaalt de kleur en het geluid. */
    public enum Level {
        /** Opstarten / welkom (blauw, opstartgeluid). */
        BOOT,
        /** Gewone mededeling (blauw). */
        INFO,
        /** Waarschuwing, bv. batterij bijna leeg (oranje). */
        WARNING,
        /** Gevaar of toegang geweigerd (rood). */
        ALERT,
        /** Bericht van een andere speler via I.M.T-comms (groen). */
        COMMS
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ImtMessagePayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, ImtMessagePayload::message,
            ByteBufCodecs.VAR_INT.map(i -> Level.values()[Math.floorMod(i, Level.values().length)], Level::ordinal), ImtMessagePayload::level,
            ImtMessagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
