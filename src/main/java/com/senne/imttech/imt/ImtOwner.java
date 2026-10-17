package com.senne.imttech.imt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wie de eigenaar van een I.M.T-bril is.
 *
 * @param id              het unieke Minecraft-ID van de speler (verandert nooit, ook niet als de naam verandert)
 * @param name            de naam van de speler, om in de HUD en tooltip te tonen
 * @param transferPending true als de eigenaar de bril heeft vrijgegeven: de volgende andere speler
 *                        die hem opzet wordt dan de nieuwe eigenaar (zoals Biebia die I.M.T aan Peter geeft)
 */
public record ImtOwner(UUID id, String name, boolean transferPending) {
    /** Hoe dit wordt opgeslagen in de wereld (savegame). */
    public static final Codec<ImtOwner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ImtOwner::id),
            Codec.STRING.fieldOf("name").forGetter(ImtOwner::name),
            Codec.BOOL.optionalFieldOf("transfer_pending", false).forGetter(ImtOwner::transferPending)
    ).apply(instance, ImtOwner::new));

    /** Hoe dit over het netwerk naar spelers wordt gestuurd. */
    public static final StreamCodec<ByteBuf, ImtOwner> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ImtOwner::id,
            ByteBufCodecs.STRING_UTF8, ImtOwner::name,
            ByteBufCodecs.BOOL, ImtOwner::transferPending,
            ImtOwner::new);

    public ImtOwner withTransferPending(boolean pending) {
        return new ImtOwner(id, name, pending);
    }
}
