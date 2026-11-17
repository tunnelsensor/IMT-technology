package com.senne.imttech.alarm;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.lan.LanGrid;
import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.security.BaseTrust;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De "binnenkant" van een Speaker (hoofdstuk 126, sinds hoofdstuk 136 "Speaker" in plaats van "Alarm Speaker"): wie hem
 * neerzette, hoe hij heet, hoe hard en hoe ver hij klinkt, en wat hij nu speelt.
 *
 * <ul>
 *   <li><b>Instellingen</b>: hoe hard ({@link #volume}, 0,1 tot 1) en tot hoe ver je hem hoort ({@link #range}, blokken).
 *       Een eigen geluid heeft hij niet meer (hoofdstuk 136, jouw wens: "ik wil dat je niet op de speaker een geluid kiest
 *       maar in je securitycore bij files"): de geluiden staan in de map /sounds van de kern.</li>
 *   <li><b>Wat hij speelt</b>: een diagram zegt {@code playsound("siren", "Speaker 1")} (een geluid uit /sounds, of
 *       "default": de ingebouwde sirene) of {@code playtext("Alarm armed", "all")} (een zin met de stem van I.M.T). Dat
 *       staat hier: wat ({@link #playKind}, {@link #playId}, {@link #speechText}), vanaf welke speltijd en tot wanneer, en
 *       een volgnummer zodat je spel een nieuw geluid ook herkent als het hetzelfde is. Het blok zegt ook zelf dat hij
 *       speelt (AlarmSpeakerBlock.SOUNDING: het rode licht draait).</li>
 *   <li><b>Nooit vanzelf</b> (hoofdstuk 128): alleen een script laat hem iets spelen (of Test in zijn scherm). Wat hij
 *       speelt, bewaren we niet: na het laden van de wereld is hij stil, ook als hij midden in een geluid zat.</li>
 *   <li><b>Stroom</b>: om de twee seconden betaalt hij zelf een beetje stroom ([power] alarmSpeakerUpkeep) en kijkt hij of
 *       hij aan een kern hangt en niet door een EMP is uitgeschakeld (AlarmSpeakerBlock.ONLINE). Zonder dat speelt hij
 *       niets.</li>
 *   <li><b>Een geluid van vroeger</b>: had hij in hoofdstuk 126 tot 135 een eigen geüpload geluid, dan zet hij dat één keer
 *       in /sounds van zijn kern (zie {@link #legacySoundId} en AlarmSpeakers.moveLegacySound), zodat je het niet kwijt bent.</li>
 * </ul>
 */
public class AlarmSpeakerBlockEntity extends BlockEntity implements BaseTrust.Placed, com.senne.imttech.lan.DevicePower.Idle {
    /** Zo lang mag zijn naam hooguit zijn (tekens), net als een apparaat in je I.M.T-lijst. */
    public static final int MAX_NAME = 32;
    /** Zo lang mag de naam van een bestand hooguit zijn (we laten de rest weg). */
    public static final int MAX_SOUND_NAME = 48;
    /** Het bereik als je hem neerzet (blokken), en hoe dichtbij hooguit. */
    public static final int DEFAULT_RANGE = 32;
    public static final int MIN_RANGE = 8;
    /**
     * Een nieuwe zin komt hooguit zo vaak (ticks) per speaker: een lus die elke tick iets anders laat zeggen, zou anders de
     * chat volspammen en de stem van I.M.T laten achterlopen. (Gevonden door de nalezing.)
     */
    static final int SPEECH_GAP = 20;
    /** Zo lang speelt de knop Test de ingebouwde sirene (seconden). */
    public static final int TEST_SECONDS = 5;

    /** Wat hij speelt: niets, een geluid uit /sounds, of de ingebouwde sirene. */
    public static final int PLAY_NONE = 0;
    public static final int PLAY_SOUND = 1;
    public static final int PLAY_DEFAULT = 2;

    private String name = "";
    private @Nullable UUID owner;
    private String ownerName = "";
    private float volume = 1.0f;
    private int range = DEFAULT_RANGE;

    /** Een eigen geluid van vóór hoofdstuk 136 dat nog naar /sounds van de kern moet ("" = niets meer te doen). */
    private String legacySoundId = "";
    private String legacySoundName = "";
    private float legacySeconds;

    // Wat hij nu speelt (niet bewaard; wel naar je spel gestuurd).
    private int playKind = PLAY_NONE;
    private String playId = "";
    private int playToken;
    private long playStart;
    private long playEnd;
    private String speechText = "";
    private int speechToken;
    private long speechStart;
    private long speechEnd;

    public AlarmSpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALARM_SPEAKER.get(), pos, state);
    }

    // ================================================================ Lezen

    public String name() {
        return name;
    }

    public float volume() {
        return volume;
    }

    public int range() {
        return range;
    }

    /** Wat hij speelt: PLAY_NONE, PLAY_SOUND of PLAY_DEFAULT. */
    public int playKind() {
        return playKind;
    }

    /** De vingerafdruk van het geluid dat hij speelt (bij PLAY_SOUND), anders "". */
    public String playId() {
        return playId;
    }

    /** Gaat één omhoog bij elk nieuw geluid, zodat je spel het ook opnieuw begint als het hetzelfde geluid is. */
    public int playToken() {
        return playToken;
    }

    public long playStart() {
        return playStart;
    }

    public long playEnd() {
        return playEnd;
    }

    /** De zin die hij nu zegt, of "". */
    public String speechText() {
        return speechText;
    }

    public int speechToken() {
        return speechToken;
    }

    public long speechStart() {
        return speechStart;
    }

    public long speechEnd() {
        return speechEnd;
    }

    /** Speelt hij nu een geluid (op deze speltijd)? */
    public boolean playingAt(long time) {
        return playKind != PLAY_NONE && time < playEnd;
    }

    /** Zegt hij nu een zin (op deze speltijd)? */
    public boolean speakingAt(long time) {
        return !speechText.isEmpty() && time < speechEnd;
    }

    /** Een geluid van vóór hoofdstuk 136 dat nog naar /sounds moet, of "". */
    String legacySoundId() {
        return legacySoundId;
    }

    String legacySoundName() {
        return legacySoundName;
    }

    float legacySeconds() {
        return legacySeconds;
    }

    /** Dat geluid staat nu in /sounds (of kan niet meer): niet nog eens proberen. */
    void forgetLegacySound() {
        legacySoundId = "";
        legacySoundName = "";
        legacySeconds = 0f;
        setChanged();
    }

    /** Het grootste bereik dat nu mag (uit de config). */
    public static int maxRange() {
        return Math.max(MIN_RANGE, Math.round(ImtConfig.Alarms.MAX_RANGE));
    }

    // ================================================================ Veranderen (alleen op de server)

    /** Een nieuwe naam (netjes gemaakt: zonder onzichtbare tekens, aanhalingstekens of backslash). */
    public void setName(String name) {
        this.name = cleanName(name);
        changed();
    }

    /**
     * Een naam zoals we hem bewaren: zonder onzichtbare tekens en zonder het teken waarmee Minecraft tekst kleurt, zonder
     * aanhalingstekens of backslash (die passen niet in {@code playsound("siren", "Lab speaker")}), en hooguit {@code max}
     * tekens.
     */
    public static String clean(String text, int max) {
        StringBuilder clean = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= ' ' && c != 127 && c != '§' && c != '"' && c != '\\') {
                clean.append(c);
            }
        }
        String result = clean.toString().strip();
        return result.length() > max ? result.substring(0, max).strip() : result;
    }

    public static String cleanName(String name) {
        return clean(name, MAX_NAME);
    }

    /** Hoe hard en hoe ver; alles binnen de grenzen. */
    public void setSettings(float volume, int range) {
        this.volume = Float.isFinite(volume) ? Mth.clamp(Math.round(volume * 20f) / 20f, 0.1f, 1.0f) : 1.0f;
        this.range = Mth.clamp(range, MIN_RANGE, maxRange());
        changed();
    }

    /** Bewaren, en de nieuwe stand naar de spelers in de buurt sturen. */
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ================================================================ Wie hem neerzette

    @Override
    public @Nullable UUID owner() {
        return owner;
    }

    @Override
    public String ownerName() {
        return ownerName;
    }

    @Override
    public void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    // ================================================================ Spelen (alleen op de server)

    /** Wat er gebeurde met een playsound of playtext op deze speaker. */
    public enum Played {
        /** Hij speelt het nu. */
        STARTED,
        /** Hij speelde dat al (hetzelfde geluid of dezelfde zin): niets veranderd. */
        ALREADY,
        /** Geen stroom, geen kern of een EMP: hij speelt niets. */
        OFFLINE
    }

    /**
     * Speel een geluid: {@code kind} PLAY_SOUND (met de vingerafdruk {@code id}) of PLAY_DEFAULT, {@code ticks} lang.
     * Speelt hij dat geluid nog, dan gebeurt er niets (zo hapert een lus zonder wait niet); een ander geluid vervangt het
     * oude. Het script wacht niet: dit gebeurt meteen.
     */
    public Played play(ServerLevel level, int kind, String id, int ticks) {
        if (!isOnline()) {
            return Played.OFFLINE;
        }
        long now = level.getGameTime();
        if (playingAt(now) && playKind == kind && playId.equals(id)) {
            return Played.ALREADY;
        }
        playKind = kind;
        playId = kind == PLAY_SOUND ? id : "";
        playToken++;
        playStart = now;
        playEnd = now + Math.max(1, ticks);
        refreshSounding(level);
        changed();
        return Played.STARTED;
    }

    /**
     * Zeg een zin met de stem van I.M.T, ongeveer {@code ticks} lang (de stem zelf maakt elk spel apart; hoe lang hij precies
     * duurt, weet de server niet). Zegt hij die zin nog, dan gebeurt er niets; een andere zin vervangt de oude. Zolang hij
     * praat, is het geluid dat hij speelt stil (dat doet je spel).
     */
    public Played speak(ServerLevel level, String text, int ticks) {
        if (!isOnline()) {
            return Played.OFFLINE;
        }
        long now = level.getGameTime();
        if (speakingAt(now) && speechText.equals(text) || speechToken > 0 && now - speechStart < SPEECH_GAP) {
            return Played.ALREADY; // dezelfde zin loopt nog, of de vorige zin is pas net begonnen
        }
        speechText = text;
        speechToken++;
        speechStart = now;
        speechEnd = now + Math.max(1, ticks);
        refreshSounding(level);
        changed();
        return Played.STARTED;
    }

    /** Alles stoppen (geen stroom meer, weggehaald). */
    private void stopAll() {
        if (playKind == PLAY_NONE && speechText.isEmpty()) {
            return;
        }
        playKind = PLAY_NONE;
        playId = "";
        playEnd = 0;
        speechText = "";
        speechEnd = 0;
        changed();
    }

    private boolean isOnline() {
        BlockState state = getBlockState();
        return state.getBlock() instanceof AlarmSpeakerBlock && state.getValue(AlarmSpeakerBlock.ONLINE);
    }

    /** Het blok zegt of hij speelt (het rode licht): aan zolang er een geluid of een zin bezig is. */
    private void refreshSounding(ServerLevel level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AlarmSpeakerBlock)) {
            return;
        }
        long now = level.getGameTime();
        boolean busy = playingAt(now) || speakingAt(now);
        if (state.getValue(AlarmSpeakerBlock.SOUNDING) != busy) {
            // Alleen naar de spelers sturen, geen buren wakker maken.
            level.setBlock(worldPosition, state.setValue(AlarmSpeakerBlock.SOUNDING, busy), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    // ================================================================ Elke tick (op de server)

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if ((level.getGameTime() + pos.hashCode()) % 40 == 0) {
            updateOnline(level);
            AlarmSpeakers.moveLegacySound(level, this);
        }
        expire(level);
    }

    /**
     * Is het geluid of de zin voorbij? Dan weg ermee, en het rode licht uit. Dit gebeurt in zijn eigen tick, en (via
     * AlarmSpeakers.onServerTick) ook als zijn stuk wereld wel geladen is maar niet "tikt", zoals aan het eind van een
     * lange kabel.
     */
    void expire(ServerLevel level) {
        long now = level.getGameTime();
        boolean changedNow = false;
        if (playKind != PLAY_NONE && now >= playEnd) {
            playKind = PLAY_NONE;
            playId = "";
            changedNow = true;
        }
        if (!speechText.isEmpty() && now >= speechEnd) {
            speechText = "";
            changedNow = true;
        }
        if (changedNow) {
            changed();
        }
        refreshSounding(level);
    }

    /**
     * Heeft hij stroom, hangt hij aan een kern (met een netwerkkabel of een router; zonder netwerk-eis: staat hij in het
     * gebied van een kern), en is hij niet uitgeschakeld door een EMP-granaat? Zet dat in het blok (ONLINE), en betaal de
     * stroom. Zonder dat stopt hij meteen met spelen.
     */
    public void updateOnline(ServerLevel level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AlarmSpeakerBlock block)) {
            return;
        }
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, worldPosition);
        boolean network = core != null || !LanGrid.required();
        float cost = block.upkeep();
        boolean online = network && !com.senne.imttech.weapons.Emp.isOut(level, worldPosition)
                && (!LanGrid.required() || cost <= 0 || PowerGrid.pay(level, worldPosition, cost));
        BlockState next = state.setValue(AlarmSpeakerBlock.ONLINE, online);
        if (!online) {
            stopAll();
            next = next.setValue(AlarmSpeakerBlock.SOUNDING, false);
        }
        if (next != state) {
            // Alleen naar de spelers sturen, geen buren wakker maken (dit kan in een stuk wereld dat niet "tickt").
            level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /**
     * Hoofdstuk 124: draait zijn stuk wereld niet (aan het eind van een lange kabel, dat alleen geladen gehouden wordt), dan
     * kijkt zijn kern om de twee seconden of hij aan kan (zie DevicePower.refreshIdle), zodat een script hem toch iets kan
     * laten spelen. Alleen aan, nooit uit; uit gaat hij in zijn eigen tick.
     */
    @Override
    public void refreshIdle(ServerLevel level) {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof AlarmSpeakerBlock && !state.getValue(AlarmSpeakerBlock.ONLINE)) {
            updateOnline(level);
        }
    }

    // ================================================================ Opslaan en naar je spel

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            AlarmSpeakers.loaded(server, worldPosition);
            // Wat hij speelde, bewaren we niet: na het laden is hij stil (hoofdstuk 128: nooit vanzelf). Het blok zelf
            // onthoudt SOUNDING wel; dat zetten we de volgende tick uit (in onLoad mag de wereld nog niet veranderen).
            server.getServer().execute(() -> {
                if (!isRemoved()) {
                    refreshSounding(server);
                }
            });
            if (!legacySoundId.isEmpty()) {
                AlarmSounds.touch(server.getServer(), legacySoundId);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            AlarmSpeakers.unloaded(level, worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            AlarmSpeakers.unloaded(level, worldPosition);
        }
    }

    /** Naam, eigenaar en instellingen: wat bewaard wordt én naar je spel gaat. */
    private void saveShared(CompoundTag tag) {
        tag.putString("Name", name);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.putFloat("Volume", volume);
        tag.putInt("Range", range);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveShared(tag);
        if (!legacySoundId.isEmpty()) {
            tag.putString("SoundId", legacySoundId);
            tag.putString("SoundName", legacySoundName);
            tag.putFloat("SoundSeconds", legacySeconds);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = cleanName(tag.getString("Name"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        float savedVolume = tag.contains("Volume") ? tag.getFloat("Volume") : 1.0f;
        volume = Float.isFinite(savedVolume) ? Mth.clamp(savedVolume, 0.1f, 1.0f) : 1.0f;
        range = tag.contains("Range") ? Mth.clamp(tag.getInt("Range"), MIN_RANGE, maxRange()) : DEFAULT_RANGE;
        // Een geluid van vóór hoofdstuk 136 (alleen in wat bewaard was, niet in wat naar je spel gaat).
        String id = tag.getString("SoundId");
        if (AlarmSounds.validId(id)) {
            legacySoundId = id;
            legacySoundName = clean(tag.getString("SoundName"), MAX_SOUND_NAME);
            float seconds = tag.getFloat("SoundSeconds");
            legacySeconds = Float.isFinite(seconds) ? Mth.clamp(seconds, 0f, AlarmSounds.HARD_MAX_SECONDS) : 0f;
        }
        // Wat hij speelt (alleen in wat naar je spel gaat; zie getUpdateTag).
        if (tag.contains("PlayToken")) {
            playKind = Mth.clamp(tag.getInt("PlayKind"), PLAY_NONE, PLAY_DEFAULT);
            String play = tag.getString("PlayId");
            playId = AlarmSounds.validId(play) ? play : "";
            playToken = tag.getInt("PlayToken");
            playStart = tag.getLong("PlayStart");
            playEnd = tag.getLong("PlayEnd");
            speechText = clean(tag.getString("Speech"), com.senne.imttech.logic.program.Op.MAX_TEXT);
            speechToken = tag.getInt("SpeechToken");
            speechStart = tag.getLong("SpeechStart");
            speechEnd = tag.getLong("SpeechEnd");
        }
    }

    /**
     * Breek je hem af, dan houdt het item zijn naam (CUSTOM_NAME) en zijn instellingen (AlarmSettings; zie de loot table:
     * copy_components). Zet je hem weer neer, dan heeft hij alles nog. De eigenaar gaat niet mee: wie hem neerzet, is de
     * nieuwe eigenaar.
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!name.isEmpty()) {
            components.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        AlarmSettings settings = new AlarmSettings(legacySoundId, legacySoundName, legacySeconds, volume, range,
                AlarmSettings.DEFAULT.alarmSeconds());
        if (!settings.equals(AlarmSettings.DEFAULT)) {
            components.set(com.senne.imttech.registry.ModDataComponents.ALARM_SETTINGS.get(), settings);
        }
    }

    /**
     * Neergezet van een item met instellingen (zie AlarmSettings): die neemt hij over, binnen de grenzen van nu. Een geluid
     * op een oud item (van vóór hoofdstuk 136) gaat naar /sounds van zijn kern. De naam leest AlarmSpeakerBlock.setPlacedBy;
     * we "lezen" hem hier ook, anders bewaart Minecraft hem als los onderdeel en komt de oude naam later terug.
     */
    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        input.get(DataComponents.CUSTOM_NAME);
        AlarmSettings settings = input.get(com.senne.imttech.registry.ModDataComponents.ALARM_SETTINGS.get());
        if (settings != null) {
            volume = Float.isFinite(settings.volume()) ? Mth.clamp(settings.volume(), 0.1f, 1.0f) : 1.0f;
            range = Mth.clamp(settings.range(), MIN_RANGE, maxRange());
            if (AlarmSounds.validId(settings.soundId())) {
                legacySoundId = settings.soundId();
                legacySoundName = clean(settings.soundName(), MAX_SOUND_NAME);
                legacySeconds = Float.isFinite(settings.soundSeconds())
                        ? Mth.clamp(settings.soundSeconds(), 0f, AlarmSounds.HARD_MAX_SECONDS) : 0f;
            }
        }
    }

    /** Naam en instellingen zitten al in het item (als onderdelen); de eigenaar hoort er niet in. */
    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Name");
        tag.remove("Owner");
        tag.remove("OwnerName");
        tag.remove("SoundId");
        tag.remove("SoundName");
        tag.remove("SoundSeconds");
        tag.remove("Volume");
        tag.remove("Range");
    }

    /** Wat er naar je spel gaat: naam en instellingen, en wat hij nu speelt (dat laatste wordt nooit bewaard). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveShared(tag);
        tag.putInt("PlayKind", playKind);
        tag.putString("PlayId", playId);
        tag.putInt("PlayToken", playToken);
        tag.putLong("PlayStart", playStart);
        tag.putLong("PlayEnd", playEnd);
        tag.putString("Speech", speechText);
        tag.putInt("SpeechToken", speechToken);
        tag.putLong("SpeechStart", speechStart);
        tag.putLong("SpeechEnd", speechEnd);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
