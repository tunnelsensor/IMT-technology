package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.arsenal.EdithArsenal;
import com.senne.starktech.nav.EdithWaypoints;
import com.senne.starktech.redstone.EdithRelays;
import com.senne.starktech.satellite.SatelliteUpgrades;
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

    /** De redstone-relais die deze speler heeft aangemeld (fase 9). */
    public static final Supplier<AttachmentType<EdithRelays>> RELAYS = ATTACHMENTS.register("relays",
            () -> AttachmentType.builder(() -> EdithRelays.EMPTY)
                    .serialize(EdithRelays.CODEC.codec())
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player && player.connection != null
                            && player.connection.hasChannel(SyncAttachmentsPayload.TYPE), EdithRelays.STREAM_CODEC)
                    .build());

    /** Hoe ver je satelliet is opgewaardeerd (per onderdeel een niveau van 0 tot 10). */
    public static final Supplier<AttachmentType<SatelliteUpgrades>> SATELLITE = ATTACHMENTS.register("satellite",
            () -> AttachmentType.builder(() -> SatelliteUpgrades.START)
                    .serialize(SatelliteUpgrades.CODEC.codec())
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player && player.connection != null
                            && player.connection.hasChannel(SyncAttachmentsPayload.TYPE), SatelliteUpgrades.STREAM_CODEC)
                    .build());

    /** Het drone-arsenaal in een baan om de planeet (fase 15). */
    public static final Supplier<AttachmentType<EdithArsenal>> ARSENAL = ATTACHMENTS.register("arsenal",
            () -> AttachmentType.builder(() -> EdithArsenal.EMPTY)
                    .serialize(EdithArsenal.CODEC.codec())
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player && player.connection != null
                            && player.connection.hasChannel(SyncAttachmentsPayload.TYPE), EdithArsenal.STREAM_CODEC)
                    .build());
}
