package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.nav.EdithWaypoints;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * "Attachments" zijn gegevens die we aan een speler vastmaken, zoals zijn waypoints.
 * Ze worden opgeslagen in de wereld en blijven bewaard als de speler doodgaat.
 */
public final class ModAttachments {
    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, StarkTech.MODID);

    public static final Supplier<AttachmentType<EdithWaypoints>> WAYPOINTS = ATTACHMENTS.register("waypoints",
            () -> AttachmentType.builder(() -> EdithWaypoints.EMPTY)
                    .serialize(EdithWaypoints.CODEC.codec())
                    .copyOnDeath()
                    // Alleen de speler zelf krijgt zijn waypoints te zien. De extra controle op
                    // "hasChannel" is voor nep-spelers (bv. in de automatische tests of van andere
                    // mods): die hebben geen echte verbinding en kunnen niets ontvangen.
                    .sync((holder, player) -> holder == player && player.connection != null
                            && player.connection.hasChannel(SyncAttachmentsPayload.TYPE), EdithWaypoints.STREAM_CODEC)
                    .build());
}
