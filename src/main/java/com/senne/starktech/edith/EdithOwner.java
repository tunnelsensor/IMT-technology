package com.senne.starktech.edith;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wie de eigenaar van een E.D.I.T.H.-bril is.
 *
 * @param id              het unieke Minecraft-ID van de speler (verandert nooit, ook niet als de naam verandert)
 * @param name            de naam van de speler, om in de HUD en tooltip te tonen
 * @param transferPending true als de eigenaar de bril heeft vrijgegeven: de volgende andere speler
 *                        die hem opzet wordt dan de nieuwe eigenaar (zoals Tony die E.D.I.T.H. aan Peter geeft)
 */
public record EdithOwner(UUID id, String name, boolean transferPending) {
    /** Hoe dit wordt opgeslagen in de wereld (savegame). */
    public static final Codec<EdithOwner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(EdithOwner::id),
            Codec.STRING.fieldOf("name").forGetter(EdithOwner::name),
            Codec.BOOL.optionalFieldOf("transfer_pending", false).forGetter(EdithOwner::transferPending)
    ).apply(instance, EdithOwner::new));

    /** Hoe dit over het netwerk naar spelers wordt gestuurd. */
    public static final StreamCodec<ByteBuf, EdithOwner> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, EdithOwner::id,
            ByteBufCodecs.STRING_UTF8, EdithOwner::name,
            ByteBufCodecs.BOOL, EdithOwner::transferPending,
            EdithOwner::new);

    public EdithOwner withTransferPending(boolean pending) {
        return new EdithOwner(id, name, pending);
    }
}
