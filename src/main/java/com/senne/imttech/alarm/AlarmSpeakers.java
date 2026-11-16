package com.senne.imttech.alarm;

import com.senne.imttech.network.AlarmActionPayload;
import com.senne.imttech.network.AlarmOpenPayload;
import com.senne.imttech.network.AlarmSavePayload;
import com.senne.imttech.network.AlarmSoundDataPayload;
import com.senne.imttech.network.AlarmSoundRequestPayload;
import com.senne.imttech.network.AlarmUploadPayload;
import com.senne.imttech.network.AlarmUploadReplyPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.redstone.DeviceNames;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.security.BaseTrust;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import com.senne.imttech.security.os.EventLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De Alarm Speakers op de server (hoofdstuk 126): wie er geladen zijn, wat er gebeurt als een kern alarm slaat, en het
 * scherm (openen, opslaan, de knoppen). Het uploaden en ophalen van geluiden staat in {@link AlarmUploads}, het bewaren in
 * {@link AlarmSounds}.
 *
 * De berichten van de speaker hebben hun eigen lijst ({@link #register}), net als de rugzakken en het opslagsysteem: zo
 * hoeft het protocolnummer van network/ModNetwork niet omhoog.
 */
public final class AlarmSpeakers {
    private AlarmSpeakers() {}

    /** Zo dichtbij moet je staan om hem in te stellen (blokken), net als bij een camera. */
    public static final double REACH = 6.0;

    /** Welke speakers er nu geladen zijn, per wereld (voor "mag deze speler dit geluid ophalen?"). */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

    // ================================================================ Aanmelden

    /** Vanuit ImtTech: de berichten en de gebeurtenissen van de speaker. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(AlarmSpeakers::registerPayloads);
        NeoForge.EVENT_BUS.addListener(AlarmUploads::onServerTick);
        NeoForge.EVENT_BUS.addListener(AlarmSpeakers::onServerTick);
        NeoForge.EVENT_BUS.addListener(AlarmUploads::onLoggedOut);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> clear());
    }

    /** De berichten. Naar de speler: met een lambda, nooit een verwijzing naar de methode, zodat een server nooit code van het scherm laadt. */
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(AlarmSavePayload.TYPE, AlarmSavePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                handleSave(player, payload);
            }
        });
        registrar.playToServer(AlarmActionPayload.TYPE, AlarmActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                handleAction(player, payload);
            }
        });
        registrar.playToServer(AlarmUploadPayload.TYPE, AlarmUploadPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                AlarmUploads.handleUpload(player, payload);
            }
        });
        registrar.playToServer(AlarmSoundRequestPayload.TYPE, AlarmSoundRequestPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                AlarmUploads.handleRequest(player, payload);
            }
        });
        registrar.playToClient(AlarmOpenPayload.TYPE, AlarmOpenPayload.STREAM_CODEC,
                (payload, context) -> com.senne.imttech.client.alarm.AlarmClient.onOpen(payload));
        registrar.playToClient(AlarmUploadReplyPayload.TYPE, AlarmUploadReplyPayload.STREAM_CODEC,
                (payload, context) -> com.senne.imttech.client.alarm.AlarmClient.onUploadReply(payload));
        registrar.playToClient(AlarmSoundDataPayload.TYPE, AlarmSoundDataPayload.STREAM_CODEC,
                (payload, context) -> com.senne.imttech.client.alarm.AlarmClient.onSoundData(payload));
    }

    // ================================================================ Welke speakers geladen zijn

    static void loaded(ServerLevel level, BlockPos pos) {
        LOADED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
    }

    static void unloaded(Level level, BlockPos pos) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    /**
     * Staat er bij deze plek (binnen het grootste bereik plus wat marge) een geladen speaker met dit geluid? Alleen dan
     * stuurt de server het naar een speler (zie AlarmUploads.handleRequest): zo kan niemand alle geluiden van de server
     * binnenhalen.
     */
    public static boolean usedNear(ServerLevel level, Vec3 at, String id) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set == null) {
            return false;
        }
        double reach = AlarmSpeakerBlockEntity.maxRange() + 48.0;
        for (BlockPos pos : set) {
            if (pos.distToCenterSqr(at) <= reach * reach && level.getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity speaker
                    && speaker.soundId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** De geluiden van alle speakers die nu geladen zijn (die ruimt AlarmSounds als laatste op). Op de server-thread. */
    static Set<String> loadedSoundIds(net.minecraft.server.MinecraftServer server) {
        Set<String> ids = new HashSet<>();
        for (Map.Entry<ResourceKey<Level>, Set<BlockPos>> entry : LOADED.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }
            for (BlockPos pos : entry.getValue()) {
                if (level.getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity speaker && !speaker.soundId().isEmpty()) {
                    ids.add(speaker.soundId());
                }
            }
        }
        return ids;
    }

    /**
     * Elke seconde: speakers in een stuk wereld dat wel geladen is maar niet "tikt" (aan het eind van een lange kabel, of
     * net buiten de simulatieafstand) doen hun eigen tick niet. Hun eindtijd houden we hier in de gaten, anders bleven ze
     * loeien tot er iemand in de buurt kwam. (Gevonden door de nalezing.)
     */
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (LOADED.isEmpty() || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (Map.Entry<ResourceKey<Level>, Set<BlockPos>> entry : LOADED.entrySet()) {
            ServerLevel level = event.getServer().getLevel(entry.getKey());
            if (level == null) {
                continue;
            }
            for (BlockPos pos : List.copyOf(entry.getValue())) {
                if (level.isLoaded(pos) && !level.shouldTickBlocksAt(pos)
                        && level.getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity speaker) {
                    speaker.checkDeadline(level);
                }
            }
        }
    }

    /** De server is uit: alles vergeten (in singleplayer blijft deze code tussen werelden in het geheugen). */
    public static void clear() {
        LOADED.clear();
        AlarmUploads.clear();
        AlarmSounds.clear();
    }

    // ================================================================ Een alarm van de kern

    /**
     * De kern sloeg alarm (SecurityCoreBlockEntity.logEvent met type ALARM): elke speaker van zijn netwerk gaat af, of
     * blijft langer afgaan. Alleen speakers die de eigenaar van de kern zelf mag instellen (van hem, van wie hij vertrouwt,
     * of in zijn basis): de speaker van een vreemde die op jouw kabel hangt, vertelt die vreemde niets.
     *
     * Dit gebeurt midden in andere dingen (een diagram, een ontploffing, een brand), dus het schrijft zelf geen alarm in het
     * logboek (dat zou zichzelf weer oproepen).
     */
    public static void coreAlarm(ServerLevel level, SecurityCoreBlockEntity core) {
        for (BlockPos pos : SecurityDevices.scan(level, core).alarms()) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity speaker)) {
                continue;
            }
            if (core.ownerId() != null && !BaseTrust.wouldAllow(level, speaker, core.ownerId(), core.ownerName())) {
                continue;
            }
            boolean wasSounding = speaker.getBlockState().getValue(AlarmSpeakerBlock.SOUNDING);
            speaker.raise(level);
            if (!wasSounding && speaker.getBlockState().getValue(AlarmSpeakerBlock.SOUNDING)) {
                com.senne.imttech.lan.CableTraffic.signal(level, pos, false); // een wit vakje van de kern naar de speaker
            }
        }
    }

    // ================================================================ Het scherm

    /**
     * De speaker op deze plek die deze speler nu mag instellen (hij staat erbij, en het is zijn speaker of hij is vertrouwd
     * in de basis; zie BaseTrust), of null. Een speaker van niemand (buiten een basis) wordt hierdoor van hem.
     */
    static @Nullable AlarmSpeakerBlockEntity editable(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH
                || !(player.serverLevel().getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity speaker)) {
            return null;
        }
        return BaseTrust.mayEdit(player, speaker) ? speaker : null;
    }

    /** Sneak + rechtsklik: mag je hem instellen, dan opent jouw spel zijn scherm; anders hoor je van wie hij is. */
    public static void open(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH
                || !(player.serverLevel().getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity speaker)) {
            return;
        }
        if (!BaseTrust.mayEdit(player, speaker)) {
            String owner = BaseTrust.belongsTo(player.serverLevel(), speaker);
            player.displayClientMessage((owner.isEmpty()
                    ? Component.translatable("security.imttech.alarm_speaker.locked")
                    : Component.translatable("security.imttech.alarm_speaker.not_yours", owner)).withStyle(ChatFormatting.RED), true);
            return;
        }
        ModNetwork.sendTo(player, openPayload(player.serverLevel(), speaker, true));
    }

    /** Het scherm bijwerken als het open staat (na een knop of een upload). */
    static void refresh(ServerPlayer player, AlarmSpeakerBlockEntity speaker) {
        if (speaker.getLevel() instanceof ServerLevel level) {
            ModNetwork.sendTo(player, openPayload(level, speaker, false));
        }
    }

    /** Wat het scherm toont. */
    public static AlarmOpenPayload openPayload(ServerLevel level, AlarmSpeakerBlockEntity speaker, boolean open) {
        BlockPos pos = speaker.getBlockPos();
        BlockState state = speaker.getBlockState();
        int status;
        if (state.getValue(AlarmSpeakerBlock.ONLINE)) {
            status = AlarmOpenPayload.ONLINE;
        } else if (com.senne.imttech.weapons.Emp.isOut(level, pos)) {
            status = AlarmOpenPayload.EMP;
        } else if (SecurityCores.deviceCore(level, pos) == null && com.senne.imttech.lan.LanGrid.required()) {
            status = AlarmOpenPayload.NO_NETWORK;
        } else {
            status = AlarmOpenPayload.NO_POWER;
        }
        int soundState = speaker.soundId().isEmpty() ? AlarmOpenPayload.DEFAULT_SOUND
                : AlarmSounds.storedNow(level.getServer(), speaker.soundId()) ? AlarmOpenPayload.CUSTOM_SOUND : AlarmOpenPayload.MISSING_SOUND;
        return new AlarmOpenPayload(pos.immutable(), speaker.name(), speaker.soundName(), speaker.soundSeconds(), soundState,
                speaker.volume(), speaker.range(), speaker.alarmSeconds(), Math.round(AlarmSounds.maxSamples() / (float) com.senne.imttech.alarm.audio.AlarmAudio.RATE),
                status, com.senne.imttech.config.ImtConfig.Alarms.UPLOADS_ALLOWED, state.getValue(AlarmSpeakerBlock.SOUNDING), open);
    }

    /**
     * Save in het scherm: de naam en hoe hard, hoe ver en hoe lang. De server kijkt opnieuw of je mag (iemand kan je net uit
     * Access gehaald hebben) en houdt alles binnen de grenzen. Een nieuwe naam komt ook in je I.M.T-lijst (en die van de
     * eigenaar), dus ook in je diagrammen; en in het logboek van zijn kern.
     */
    public static void handleSave(ServerPlayer player, AlarmSavePayload payload) {
        AlarmSpeakerBlockEntity speaker = editable(player, payload.pos());
        if (speaker == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = speaker.getBlockPos();
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
        String clean = AlarmSpeakerBlockEntity.cleanName(payload.name());
        String old = speaker.name();
        if (!clean.isEmpty() && !clean.equals(old)) {
            speaker.setName(clean);
            List<ServerPlayer> lists = new ArrayList<>();
            lists.add(player);
            ServerPlayer owner = speaker.owner() == null ? null : level.getServer().getPlayerList().getPlayer(speaker.owner());
            if (owner != null && owner != player) {
                lists.add(owner);
            }
            // De I.M.T-lijst van wie hem hernoemt, en van zijn eigenaar als die online is: wie hem erin heeft, krijgt de
            // nieuwe naam (diagrammen en je stem zoeken hem op die naam), net als bij een camera.
            for (ServerPlayer who : lists) {
                if (!DeviceNames.nameOf(who, List.of(pos)).isEmpty()) {
                    DeviceNames.rename(who, List.of(pos), pos, clean, AlarmSpeakerBlock.DEFAULT_NAME);
                    if (core != null) {
                        com.senne.imttech.logic.LogicDevices.refresh(level, core, who);
                    }
                }
            }
            if (core != null) {
                core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " renamed the alarm speaker "
                        + (old.isEmpty() ? "at " + pos.toShortString() : "\"" + old + "\"") + " to \"" + clean + "\"");
            }
        }
        speaker.setSettings(payload.volume(), payload.range(), payload.alarmSeconds());
        refresh(player, speaker);
        player.displayClientMessage(Component.translatable("security.imttech.alarm_speaker.saved", speaker.name())
                .withStyle(ChatFormatting.GREEN), true);
    }

    /** Een knop in het scherm: Test, Stop, de sirene terug, of hetzelfde geluid op alle speakers van dit netwerk. */
    public static void handleAction(ServerPlayer player, AlarmActionPayload payload) {
        AlarmSpeakerBlockEntity speaker = editable(player, payload.pos());
        if (speaker == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = speaker.getBlockPos();
        switch (payload.action()) {
            case AlarmActionPayload.TEST -> {
                speaker.updateOnline(level);
                if (!speaker.getBlockState().getValue(AlarmSpeakerBlock.ONLINE)) {
                    player.displayClientMessage(Component.translatable("security.imttech.alarm_speaker.cannot_test")
                            .withStyle(ChatFormatting.RED), true);
                } else {
                    speaker.soundFor(level, AlarmSpeakerBlockEntity.TEST_SECONDS);
                }
            }
            case AlarmActionPayload.STOP -> {
                // Zoals een diagram off("Alarm 1") zegt: dan hoort de server het ook ("when turned_off").
                if (speaker.getBlockState().getValue(AlarmSpeakerBlock.SOUNDING)) {
                    ImtRedstone.switchAt(level, pos, -1, ImtRedstone.Op.OFF, 0);
                }
            }
            case AlarmActionPayload.DEFAULT_SOUND -> {
                if (!speaker.soundId().isEmpty()) {
                    speaker.setSound("", "", 0f);
                    log(level, pos, player.getGameProfile().getName() + " put the built-in siren back on \"" + speaker.name() + "\"");
                }
            }
            case AlarmActionPayload.APPLY_ALL -> applyToAll(player, speaker);
            default -> {
            }
        }
        refresh(player, speaker);
    }

    /**
     * Hetzelfde geluid op alle speakers van dezelfde kern die deze speler mag instellen. Van op afstand, dus met
     * BaseTrust.wouldAllow: een speaker van niemand blijft van niemand.
     */
    static void applyToAll(ServerPlayer player, AlarmSpeakerBlockEntity from) {
        ServerLevel level = player.serverLevel();
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, from.getBlockPos());
        if (core == null) {
            player.displayClientMessage(Component.translatable("security.imttech.alarm_speaker.no_core_to_copy")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        int count = 0;
        for (BlockPos pos : SecurityDevices.scan(level, core).alarms()) {
            if (pos.equals(from.getBlockPos()) || !level.isLoaded(pos)
                    || !(level.getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity other) || !BaseTrust.wouldAllow(player, other)) {
                continue;
            }
            if (!other.soundId().equals(from.soundId())) {
                other.setSound(from.soundId(), from.soundName(), from.soundSeconds());
                count++;
            }
        }
        if (count > 0) {
            core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " gave " + count + " alarm speaker"
                    + (count == 1 ? "" : "s") + " the sound of \"" + from.name() + "\"");
        }
        player.displayClientMessage(Component.translatable("security.imttech.alarm_speaker.copied", count)
                .withStyle(ChatFormatting.GREEN), true);
    }

    /** Een regel in het logboek van de kern van deze speaker (als hij er een heeft). */
    static void log(ServerLevel level, BlockPos pos, String text) {
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
        if (core != null) {
            core.logEvent(EventLog.Type.DEVICE, text);
        }
    }
}
