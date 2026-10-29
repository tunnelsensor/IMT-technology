package com.senne.imttech.registry;

import com.senne.imttech.ImtTech;
import com.senne.imttech.arsenal.ImtArsenal;
import com.senne.imttech.nav.ImtWaypoints;
import com.senne.imttech.redstone.ImtRelays;
import com.senne.imttech.satellite.SatelliteUpgrades;
import java.util.function.Supplier;
import net.minecraft.network.codec.ByteBufCodecs;
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
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ImtTech.MODID);

    public static final Supplier<AttachmentType<ImtWaypoints>> WAYPOINTS = ATTACHMENTS.register("waypoints",
            () -> AttachmentType.builder(() -> ImtWaypoints.EMPTY)
                    .serialize(ImtWaypoints.CODEC.codec())
                    .copyOnDeath()
                    // Alleen de speler zelf krijgt zijn waypoints te zien. De extra controle op
                    // "hasChannel" is voor nep-spelers (bv. in de automatische tests of van andere
                    // mods): die hebben geen echte verbinding en kunnen niets ontvangen.
                    .sync((holder, player) -> holder == player && player.connection != null
                            && player.connection.hasChannel(SyncAttachmentsPayload.TYPE), ImtWaypoints.STREAM_CODEC)
                    .build());

    /**
     * De getekende kabels in een chunk (zie PowerCables). Opgeslagen met de chunk, en naar
     * iedereen gestuurd die de chunk ziet (om ze te tekenen). Een lege lijst bewaren we niet.
     */
    public static final Supplier<AttachmentType<com.senne.imttech.energy.CableCells>> CABLES = ATTACHMENTS.register("cables",
            ModAttachments::cableCells);

    /** De getekende netwerkkabels in een chunk (hoofdstuk 38, zie CableKind.NETWORK). Net als de stroomkabels. */
    public static final Supplier<AttachmentType<com.senne.imttech.energy.CableCells>> NET_CABLES = ATTACHMENTS.register("net_cables",
            ModAttachments::cableCells);

    /** De getekende PoE-kabels in een chunk (hoofdstuk 38, zie CableKind.POE). Net als de stroomkabels. */
    public static final Supplier<AttachmentType<com.senne.imttech.energy.CableCells>> POE_CABLES = ATTACHMENTS.register("poe_cables",
            ModAttachments::cableCells);

    /** Een lijst kabels per chunk: opgeslagen met de chunk (leeg niet), en naar iedereen die de chunk ziet. */
    private static AttachmentType<com.senne.imttech.energy.CableCells> cableCells() {
        return AttachmentType.builder(() -> com.senne.imttech.energy.CableCells.EMPTY)
                .serialize(com.senne.imttech.energy.CableCells.CODEC, cells -> !cells.isEmpty())
                .sync((holder, player) -> player.connection != null
                        && player.connection.hasChannel(SyncAttachmentsPayload.TYPE), com.senne.imttech.energy.CableCells.STREAM_CODEC)
                .build();
    }

    /** De redstone-relais die deze speler heeft aangemeld (fase 9). */
    public static final Supplier<AttachmentType<ImtRelays>> RELAYS = ATTACHMENTS.register("relays",
            () -> AttachmentType.builder(() -> ImtRelays.EMPTY)
                    .serialize(ImtRelays.CODEC.codec())
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player && player.connection != null
                            && player.connection.hasChannel(SyncAttachmentsPayload.TYPE), ImtRelays.STREAM_CODEC)
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
    public static final Supplier<AttachmentType<ImtArsenal>> ARSENAL = ATTACHMENTS.register("arsenal",
            () -> AttachmentType.builder(() -> ImtArsenal.EMPTY)
                    .serialize(ImtArsenal.CODEC.codec())
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player && player.connection != null
                            && player.connection.hasChannel(SyncAttachmentsPayload.TYPE), ImtArsenal.STREAM_CODEC)
                    .build());

    /** Waar jouw landingsplatforms liggen (zie LandingPads): de HUD wijst je de weg ernaartoe. */
    public static final Supplier<AttachmentType<com.senne.imttech.suit.LandingPads>> LANDING_PADS = ATTACHMENTS.register("landing_pads",
            () -> AttachmentType.builder(() -> com.senne.imttech.suit.LandingPads.EMPTY)
                    .serialize(com.senne.imttech.suit.LandingPads.CODEC.codec())
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player && player.connection != null
                            && player.connection.hasChannel(SyncAttachmentsPayload.TYPE),
                            com.senne.imttech.suit.LandingPads.STREAM_CODEC)
                    .build());

    /**
     * Tot wanneer (speltijd) dit wezen uitgeschakeld is door een EMP-granaat (hoofdstuk 35); 0 = niet. Op een drone,
     * een robot of een speler. Niet opgeslagen (het duurt maar even), wel naar iedereen gestuurd die het wezen ziet:
     * zo gaan de lampjes van een drone uit en ziet een speler ruis in zijn bril.
     */
    public static final Supplier<AttachmentType<Long>> EMP_UNTIL = ATTACHMENTS.register("emp_until",
            () -> AttachmentType.builder(() -> 0L)
                    .sync((holder, player) -> player.connection != null
                            && player.connection.hasChannel(SyncAttachmentsPayload.TYPE), ByteBufCodecs.VAR_LONG)
                    .build());
}
