package com.senne.imttech.alarm;

import com.senne.imttech.alarm.audio.AlarmAudio;
import com.senne.imttech.network.AlarmActionPayload;
import com.senne.imttech.network.AlarmOpenPayload;
import com.senne.imttech.network.AlarmPreviewPayload;
import com.senne.imttech.network.AlarmSavePayload;
import com.senne.imttech.network.AlarmSoundDataPayload;
import com.senne.imttech.network.AlarmSoundRequestPayload;
import com.senne.imttech.network.AlarmUploadPayload;
import com.senne.imttech.network.AlarmUploadReplyPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.redstone.DeviceNames;
import com.senne.imttech.security.BaseTrust;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import com.senne.imttech.security.os.EventLog;
import com.senne.imttech.security.os.VirtualFs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
 * De Speakers op de server (hoofdstuk 126, sinds hoofdstuk 136 "Speaker"): wie er geladen zijn, wat een diagram ze laat
 * spelen ({@link #playSound}, {@link #playText}), en hun scherm (openen, opslaan, Test).
 *
 * Hoofdstuk 136 (jouw wensen): een speaker heeft geen eigen geluid meer. De geluiden staan in de map /sounds in Files van de
 * kern (zie VirtualFs.SOUNDS); een diagram zegt welk geluid op welke speaker: {@code playsound("siren", "Speaker 1")} of
 * {@code playsound("siren", "all")}, en {@code playtext("Alarm armed", "all")} laat hem iets zeggen met de stem van I.M.T.
 * Het script wacht daar niet op. Vanzelf speelt een speaker nooit iets (hoofdstuk 128).
 *
 * Het uploaden en ophalen van geluiden staat in {@link AlarmUploads}, het bewaren in {@link AlarmSounds}. De berichten van
 * de speaker hebben hun eigen lijst ({@link #register}, versie {@link #PAYLOAD_VERSION}), net als de rugzakken en het
 * opslagsysteem: zo hoeft het protocolnummer van network/ModNetwork niet omhoog.
 */
public final class AlarmSpeakers {
    private AlarmSpeakers() {}

    /** De versie van de berichten van de speaker (2 sinds hoofdstuk 136: andere schermgegevens, uploaden naar Files). */
    public static final String PAYLOAD_VERSION = "2";
    /** Zo dichtbij moet je staan om hem in te stellen (blokken), net als bij een camera. */
    public static final double REACH = 6.0;
    /** {@code playsound(..., "all")} of {@code playtext(..., "all")}: alle speakers van deze kern. */
    public static final String ALL = "all";
    /** {@code playsound("default", ...)}: de ingebouwde sirene. */
    public static final String DEFAULT_SOUND = "default";
    /** Zo lang duurt de ingebouwde sirene één keer (ticks): alarm_siren.ogg is een lus van twee seconden. */
    public static final int DEFAULT_TICKS = 40;

    /** Welke speakers er nu geladen zijn, per wereld. */
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
        var registrar = event.registrar(PAYLOAD_VERSION);
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
        registrar.playToClient(AlarmPreviewPayload.TYPE, AlarmPreviewPayload.STREAM_CODEC,
                (payload, context) -> com.senne.imttech.client.alarm.AlarmClient.onPreview(payload));
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
     * Speelt er bij deze plek (binnen het grootste bereik plus wat marge) een geladen speaker dit geluid, of staat het in
     * /sounds van de kern van zo'n speaker? Alleen dan stuurt de server het naar een speler die erom vraagt (zie
     * AlarmUploads.handleRequest): zo kan niemand alle geluiden van de server binnenhalen. (Gevonden door de nalezing: eerst
     * telde alleen een geluid dat nog speelde, en een kort geluid was vaak al voorbij als de vraag aankwam.)
     */
    public static boolean usedNear(ServerLevel level, Vec3 at, String id) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set == null) {
            return false;
        }
        double reach = AlarmSpeakerBlockEntity.maxRange() + 48.0;
        long now = level.getGameTime();
        for (BlockPos pos : set) {
            if (pos.distToCenterSqr(at) > reach * reach || !(level.getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity speaker)) {
                continue;
            }
            if (speaker.playKind() == AlarmSpeakerBlockEntity.PLAY_SOUND && speaker.playId().equals(id) && now < speaker.playEnd()) {
                return true;
            }
            SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
            if (core != null && core.fs().soundIds().contains(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * De geluiden die AlarmSounds als laatste opruimt: die in de Files van een geladen kern, en wat een geladen speaker nu
     * speelt of nog naar /sounds moet verhuizen. Op de server-thread.
     */
    static Set<String> loadedSoundIds(net.minecraft.server.MinecraftServer server) {
        Set<String> ids = new HashSet<>();
        for (Map.Entry<ResourceKey<Level>, Set<BlockPos>> entry : LOADED.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }
            for (BlockPos pos : entry.getValue()) {
                if (level.getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity speaker) {
                    if (!speaker.playId().isEmpty()) {
                        ids.add(speaker.playId());
                    }
                    if (!speaker.legacySoundId().isEmpty()) {
                        ids.add(speaker.legacySoundId());
                    }
                    SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
                    if (core != null) {
                        ids.addAll(core.fs().soundIds());
                    }
                }
            }
        }
        return ids;
    }

    /**
     * Elke seconde: speakers in een stuk wereld dat wel geladen is maar niet "tikt" (aan het eind van een lange kabel, of
     * net buiten de simulatieafstand) doen hun eigen tick niet. Of hun geluid voorbij is, kijken we hier na, anders bleef
     * hun rode licht draaien tot er iemand in de buurt kwam.
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
                    speaker.expire(level);
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

    // ================================================================ Spelen vanuit een diagram

    /** Hoe een playsound of playtext afliep (voor het logboek van de kern). */
    public enum Result {
        /** Minstens één speaker speelt het (of speelde het al). */
        OK,
        /** Er is geen speaker met die naam aan deze kern (bij "all": helemaal geen speaker). */
        NO_SPEAKER,
        /** Er staat geen geluid met die naam in /sounds. */
        NO_SOUND,
        /** De speakers hebben geen stroom, geen kern of zijn uitgeschakeld door een EMP. */
        OFFLINE
    }

    /**
     * {@code playsound(sound, target)}: speel het geluid {@code sound} uit /sounds van deze kern (of "default", de ingebouwde
     * sirene) op de speaker {@code target}, of op alle speakers van deze kern ("all"). Eén keer, en het script wacht niet.
     */
    public static Result playSound(ServerLevel level, SecurityCoreBlockEntity core, String sound, String target) {
        int kind;
        String id;
        int ticks;
        if (sound.strip().equalsIgnoreCase(DEFAULT_SOUND)) {
            kind = AlarmSpeakerBlockEntity.PLAY_DEFAULT;
            id = "";
            ticks = DEFAULT_TICKS;
        } else {
            VirtualFs.Node node = core.fs().sound(sound);
            if (node == null) {
                return Result.NO_SOUND;
            }
            kind = AlarmSpeakerBlockEntity.PLAY_SOUND;
            id = node.soundId();
            ticks = Math.max(1, Math.round(node.soundSamples() * 20f / AlarmAudio.RATE));
            AlarmSounds.touch(level.getServer(), id);
        }
        List<AlarmSpeakerBlockEntity> speakers = targets(level, core, target);
        if (speakers.isEmpty()) {
            return Result.NO_SPEAKER;
        }
        boolean any = false;
        for (AlarmSpeakerBlockEntity speaker : speakers) {
            if (speaker.play(level, kind, id, ticks) != AlarmSpeakerBlockEntity.Played.OFFLINE) {
                any = true;
            }
        }
        return any ? Result.OK : Result.OFFLINE;
    }

    /**
     * {@code playtext(text, target)}: de speaker {@code target} (of alle speakers van deze kern: "all") zegt {@code text}
     * met de stem van I.M.T. Die stem maakt het spel van elke speler in de buurt zelf; wie de stem niet heeft, krijgt de zin
     * in de chat. Het script wacht niet.
     */
    public static Result playText(ServerLevel level, SecurityCoreBlockEntity core, String text, String target) {
        String clean = AlarmSpeakerBlockEntity.clean(text, com.senne.imttech.logic.program.Op.MAX_TEXT);
        if (clean.isEmpty()) {
            return Result.OK;
        }
        List<AlarmSpeakerBlockEntity> speakers = targets(level, core, target);
        if (speakers.isEmpty()) {
            return Result.NO_SPEAKER;
        }
        int ticks = speechTicks(clean);
        boolean any = false;
        for (AlarmSpeakerBlockEntity speaker : speakers) {
            if (speaker.speak(level, clean, ticks) != AlarmSpeakerBlockEntity.Played.OFFLINE) {
                any = true;
            }
        }
        return any ? Result.OK : Result.OFFLINE;
    }

    /**
     * Hoe lang een zin ongeveer duurt (ticks): de stem van I.M.T zegt zo'n 14 tekens per seconde, plus een halve seconde.
     * Zo lang is het geluid van die speaker stil en draait zijn rode licht.
     */
    static int speechTicks(String text) {
        return Math.min(20 * 12, 10 + Math.round(text.length() * 20f / 14f));
    }

    /**
     * De speakers van deze kern met deze naam (hoofdletters maken niet uit), of allemaal bij "all" of een lege naam. Alleen
     * speakers die de eigenaar van de kern mag instellen (van hem, van wie hij vertrouwt, of in zijn basis): de speaker van
     * een vreemde die op jouw kabel hangt, speelt jouw geluiden niet.
     */
    static List<AlarmSpeakerBlockEntity> targets(ServerLevel level, SecurityCoreBlockEntity core, String target) {
        String wanted = target == null ? "" : target.strip();
        boolean all = wanted.isEmpty() || wanted.equalsIgnoreCase(ALL);
        List<AlarmSpeakerBlockEntity> found = new ArrayList<>();
        for (BlockPos pos : SecurityDevices.scan(level, core).alarms()) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity speaker)) {
                continue;
            }
            // In het eigen gebied van deze kern mag het altijd (ook tijdens een drill van de eigenaar, waarin hij even geen
            // rechten heeft: gevonden door de nalezing); daarbuiten alleen een speaker die de eigenaar mag instellen.
            SecurityCoreBlockEntity area = SecurityCores.coreAt(level, pos);
            boolean ownBase = area != null && area.getBlockPos().equals(core.getBlockPos());
            if (core.ownerId() != null && !ownBase && !BaseTrust.wouldAllow(level, speaker, core.ownerId(), core.ownerName())) {
                continue;
            }
            if (all || speaker.name().equalsIgnoreCase(wanted)) {
                found.add(speaker);
            }
        }
        return found;
    }

    /** Is er aan deze kern een speaker met deze naam? (Voor een duidelijke melding als iemand on("Speaker 1") schrijft.) */
    public static boolean hasSpeaker(ServerLevel level, SecurityCoreBlockEntity core, String name) {
        return !name.isBlank() && !name.strip().equalsIgnoreCase(ALL) && !targets(level, core, name).isEmpty();
    }

    // ================================================================ Een geluid van vroeger naar /sounds

    /**
     * Had deze speaker van vóór hoofdstuk 136 een eigen geüpload geluid? Dan komt dat één keer in /sounds van zijn kern
     * (onder de naam van het bestand, zonder extensie), zodat je het met playsound kunt spelen. Zonder kern wacht hij tot hij
     * er een heeft.
     */
    public static void moveLegacySound(ServerLevel level, AlarmSpeakerBlockEntity speaker) {
        String id = speaker.legacySoundId();
        if (id.isEmpty()) {
            return;
        }
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, speaker.getBlockPos());
        if (core == null) {
            return;
        }
        if (!AlarmSounds.storedNow(level.getServer(), id)) {
            speaker.forgetLegacySound(); // het geluid is er niet meer (opgeruimd): niets te verhuizen
            return;
        }
        // Een kern zonder systeem (net neergezet) maakt eerst zijn bestanden; anders zou dat het geluid later weer wissen.
        core.ensureSystem(level);
        if (!core.fs().soundIds().contains(id)) {
            String name = fileName(speaker.legacySoundName());
            float seconds = speaker.legacySeconds();
            int samples = Math.round(seconds * AlarmAudio.RATE);
            String path = core.fs().putSound(VirtualFs.SOUNDS, name, id, samples, seconds);
            if (path == null) {
                return; // de schijf is vol: later opnieuw proberen
            }
            core.setChanged();
            core.logEvent(EventLog.Type.DEVICE, "The sound of \"" + speaker.name() + "\" moved to " + path
                    + ": play it with playsound(\"" + VirtualFs.nameOf(path) + "\", \"" + speaker.name() + "\")");
        }
        speaker.forgetLegacySound();
    }

    /**
     * Een bestandsnaam voor /sounds: zonder extensie, alleen letters, cijfers, punt, streepje en liggend streepje (zoals Files
     * wil), hooguit 32 tekens; "sound" als er niets overblijft.
     */
    public static String fileName(String original) {
        String base = original == null ? "" : original.strip();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        StringBuilder clean = new StringBuilder();
        for (char c : base.toCharArray()) {
            if (Character.isLetterOrDigit(c) && c < 128 || c == '-' || c == '_' || c == '.') {
                clean.append(Character.toLowerCase(c));
            } else if (c == ' ' && clean.length() > 0 && clean.charAt(clean.length() - 1) != '_') {
                clean.append('_');
            }
        }
        String name = clean.toString();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.length() > VirtualFs.MAX_NAME) {
            name = name.substring(0, VirtualFs.MAX_NAME);
        }
        return name.isEmpty() || !VirtualFs.validName(name) ? "sound" : name.toLowerCase(Locale.ROOT);
    }

    // ================================================================ Een geluid uit Files beluisteren

    /**
     * Dubbelklik op een geluid in Files (zie SecurityOs.openFile): het geluid gaat naar het spel van deze speler, dat het
     * alleen voor hem afspeelt. Eerst een bericht dat het eraan komt (AlarmPreviewPayload), dan de stukjes.
     */
    public static void preview(ServerPlayer player, VirtualFs.Node node) {
        if (!node.isSound()) {
            return;
        }
        String id = node.soundId();
        // Dezelfde rem als bij ophalen (zes geluiden per tien seconden, hetzelfde geluid hooguit eens per 30 seconden):
        // nog eens beluisteren heeft alleen het kleine "speel het"-bericht nodig, je spel heeft het geluid nog. (Gevonden
        // door de nalezing: eerst stuurde elke dubbelklik het hele geluid opnieuw.)
        if (!AlarmUploads.allowRequest(player.getUUID(), id, player.serverLevel().getGameTime())) {
            ModNetwork.sendTo(player, new AlarmPreviewPayload(id, node.soundSamples()));
            return;
        }
        AlarmSounds.load(player.server, id, stored -> {
            if (player.hasDisconnected()) {
                return;
            }
            if (stored == null) {
                player.displayClientMessage(Component.translatable("security.imttech.alarm_speaker.sound_gone", node.name())
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            ModNetwork.sendTo(player, new AlarmPreviewPayload(id, stored.samples()));
            for (AlarmSoundDataPayload part : AlarmUploads.dataPayloads(stored)) {
                ModNetwork.sendTo(player, part);
            }
        });
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

    /** Het scherm bijwerken als het open staat (na een knop). */
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
        return new AlarmOpenPayload(pos.immutable(), speaker.name(), speaker.volume(), speaker.range(), status,
                state.getValue(AlarmSpeakerBlock.SOUNDING), open);
    }

    /**
     * Save in het scherm: de naam en hoe hard en hoe ver. De server kijkt opnieuw of je mag (iemand kan je net uit Access
     * gehaald hebben) en houdt alles binnen de grenzen. Een nieuwe naam komt ook in je I.M.T-lijst (en die van de eigenaar),
     * en in het logboek van zijn kern.
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
            // De I.M.T-lijst van wie hem hernoemt, en van zijn eigenaar als die online is: zo botsen namen niet.
            for (ServerPlayer who : lists) {
                if (!DeviceNames.nameOf(who, List.of(pos)).isEmpty()) {
                    DeviceNames.rename(who, List.of(pos), pos, clean, AlarmSpeakerBlock.DEFAULT_NAME);
                    if (core != null) {
                        com.senne.imttech.logic.LogicDevices.refresh(level, core, who);
                    }
                }
            }
            if (core != null) {
                core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " renamed the speaker "
                        + (old.isEmpty() ? "at " + pos.toShortString() : "\"" + old + "\"") + " to \"" + clean + "\"");
            }
        }
        speaker.setSettings(payload.volume(), payload.range());
        refresh(player, speaker);
        player.displayClientMessage(Component.translatable("security.imttech.alarm_speaker.saved", speaker.name())
                .withStyle(ChatFormatting.GREEN), true);
    }

    /** Een knop in het scherm: Test (de ingebouwde sirene, een paar seconden). */
    public static void handleAction(ServerPlayer player, AlarmActionPayload payload) {
        AlarmSpeakerBlockEntity speaker = editable(player, payload.pos());
        if (speaker == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (payload.action() == AlarmActionPayload.TEST) {
            speaker.updateOnline(level);
            if (speaker.play(level, AlarmSpeakerBlockEntity.PLAY_DEFAULT, "", AlarmSpeakerBlockEntity.TEST_SECONDS * 20)
                    == AlarmSpeakerBlockEntity.Played.OFFLINE) {
                player.displayClientMessage(Component.translatable("security.imttech.alarm_speaker.cannot_test")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
        refresh(player, speaker);
    }

    /** Een regel in het logboek van de kern van deze speaker (als hij er een heeft). */
    static void log(ServerLevel level, BlockPos pos, String text) {
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
        if (core != null) {
            core.logEvent(EventLog.Type.DEVICE, text);
        }
    }
}
