package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Zakkenrollen (hoofdstuk 12), beide kanten op. {@code victim} is het entity-nummer van de speler wiens
 * zak je doorzoekt, {@code op} wat er gebeurt, {@code args} de gegevens die erbij horen.
 * <ul>
 *   <li>server naar speler: {@code open} (zijn naam, hoeveel kaarten erin zitten, een getal voor waar
 *       alles ligt), {@code result} (je hebt een kaart: welke; of je bent betrapt, hij liep weg...);</li>
 *   <li>speler naar server: {@code take} (deze kaart trok je eruit), {@code caught} (de meter is vol),
 *       {@code cancel} (Esc).</li>
 * </ul>
 * Welke kaart waar ligt, weet alleen de server: je spel kent alleen de achterkanten.
 */
public record PocketPayload(int victim, String op, CompoundTag args) implements CustomPacketPayload {
    public static final int MAX_OP = 16;

    public static final Type<PocketPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "pocket"));

    public static final StreamCodec<ByteBuf, PocketPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PocketPayload::victim,
            ByteBufCodecs.stringUtf8(MAX_OP), PocketPayload::op,
            ByteBufCodecs.COMPOUND_TAG, PocketPayload::args,
            PocketPayload::new);

    public static PocketPayload of(int victim, String op) {
        return new PocketPayload(victim, op, new CompoundTag());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
