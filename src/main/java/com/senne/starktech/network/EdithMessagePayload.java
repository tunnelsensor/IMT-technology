package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Bericht van server naar speler: "E.D.I.T.H. zegt iets". Het verschijnt in de HUD
 * en speelt een geluidje af dat past bij het soort bericht.
 */
public record EdithMessagePayload(Component message, Level level) implements CustomPacketPayload {
    public static final Type<EdithMessagePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StarkTech.MODID, "edith_message"));

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
        /** Bericht van een andere speler via E.D.I.T.H.-comms (groen). */
        COMMS
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, EdithMessagePayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, EdithMessagePayload::message,
            ByteBufCodecs.VAR_INT.map(i -> Level.values()[Math.floorMod(i, Level.values().length)], Level::ordinal), EdithMessagePayload::level,
            EdithMessagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
