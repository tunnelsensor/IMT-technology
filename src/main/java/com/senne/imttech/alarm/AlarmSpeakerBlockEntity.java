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
 * De "binnenkant" van een Alarm Speaker (hoofdstuk 126): wie hem neerzette, hoe hij heet, welk geluid erop staat en hoe hij
 * het laat horen.
 *
 * <ul>
 *   <li><b>Geluid</b>: {@link #soundId} is de vingerafdruk van een geüpload geluid (zie AlarmSounds), of "" voor de
 *       ingebouwde sirene. {@link #soundName} is de naam van het bestand ("sirene.mp4"), {@link #soundSeconds} hoe lang
 *       het duurt. Het geluid zelf zit NIET hierin: het staat in de map van de wereld, en je spel haalt het apart op. Zo
 *       blijft wat de server bij elk stuk wereld naar je stuurt klein.</li>
 *   <li><b>Instellingen</b>: hoe hard ({@link #volume}, 0,1 tot 1), tot hoe ver je hem hoort ({@link #range}, blokken) en
 *       hoe lang hij loeit als een script hem aanzet ({@link #alarmSeconds}, 0 = tot je hem uitzet).</li>
 *   <li><b>Nooit vanzelf</b> (hoofdstuk 128, jouw wens: "Zorg dat het alarm niet vanzelf kan afgaan, het gebeurd enkel
 *       vanuit een script als je het runt"): ook niet als zijn kern alarm slaat. Hij gaat alleen af als iets hem op zijn
 *       naam aanzet ({@code on("Alarm 1")} of {@code pulse("Alarm 1", 10)} in een diagram dat je runt; met de hand:
 *       Devices), of met Test in zijn scherm.</li>
 *   <li><b>Aan en uit</b>: het blok zelf weet of hij afgaat (AlarmSpeakerBlock.SOUNDING); dat ziet je spel meteen, en dan
 *       speelt het het geluid. Elke keer dat hij aangaat, krijgt hij een eindtijd ({@link #soundUntil}, de speltijd waarop
 *       hij vanzelf stopt; 0 = geen). Die wordt bewaard: valt de server uit terwijl het alarm loeit, dan stopt het na een
 *       herstart toch op tijd, in plaats van eeuwig door te gaan. Bij een puls is dat de tijd van de puls (zie
 *       {@link #pulsed}): de puls zelf onthoudt ImtRedstone na een herstart niet.</li>
 *   <li><b>Stroom</b>: om de twee seconden betaalt hij zelf een beetje stroom ([power] alarmSpeakerUpkeep) en kijkt hij of
 *       hij aan een kern hangt en niet door een EMP is uitgeschakeld (AlarmSpeakerBlock.ONLINE). Zonder dat blijft hij
 *       stil.</li>
 * </ul>
 */
public class AlarmSpeakerBlockEntity extends BlockEntity implements BaseTrust.Placed, com.senne.imttech.lan.DevicePower.Idle {
    /** Zo lang mag zijn naam hooguit zijn (tekens), net als een apparaat in je I.M.T-lijst. */
    public static final int MAX_NAME = 32;
    /** Zo lang mag de naam van het bestand hooguit zijn (we laten de rest weg). */
    public static final int MAX_SOUND_NAME = 48;
    /** Het bereik als je hem neerzet (blokken), en hoe dichtbij hooguit. */
    public static final int DEFAULT_RANGE = 32;
    public static final int MIN_RANGE = 8;
    /** Zo lang loeit hij als je hem neerzet (seconden), als een script hem aanzet met on(...). */
    public static final int DEFAULT_ALARM_SECONDS = 30;
    /** De keuzes voor "hoe lang" in het scherm (seconden; 0 = tot je hem uitzet). */
    public static final int[] ALARM_CHOICES = {10, 30, 60, 120, 300, 0};
    /** Zo lang gaat hij af met de knop Test (seconden). */
    public static final int TEST_SECONDS = 5;

    private String name = "";
    private @Nullable UUID owner;
    private String ownerName = "";
    private String soundId = "";
    private String soundName = "";
    private float soundSeconds;
    private float volume = 1.0f;
    private int range = DEFAULT_RANGE;
    private int alarmSeconds = DEFAULT_ALARM_SECONDS;
    /** De speltijd waarop hij vanzelf stopt (0 = niet vanzelf). */
    private long soundUntil;

    public AlarmSpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALARM_SPEAKER.get(), pos, state);
    }

    // ================================================================ Lezen

    public String name() {
        return name;
    }

    public String soundId() {
        return soundId;
    }

    public String soundName() {
        return soundName;
    }

    public float soundSeconds() {
        return soundSeconds;
    }

    public float volume() {
        return volume;
    }

    public int range() {
        return range;
    }

    public int alarmSeconds() {
        return alarmSeconds;
    }

    public long soundUntil() {
        return soundUntil;
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
     * aanhalingstekens of backslash (die passen niet in {@code on("Lab alarm")}), en hooguit {@code max} tekens.
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

    /** Het geluid op deze speaker: een upload ({@code id}), of {@code ""} voor de ingebouwde sirene. */
    public void setSound(String id, String fileName, float seconds) {
        this.soundId = AlarmSounds.validId(id) ? id : "";
        this.soundName = this.soundId.isEmpty() ? "" : clean(fileName, MAX_SOUND_NAME);
        this.soundSeconds = this.soundId.isEmpty() ? 0f : clampSeconds(seconds);
        changed();
    }

    /** Een lengte die kan: een gewoon getal tussen 0 en het hoogste wat een geluid ooit mag duren. */
    private static float clampSeconds(float seconds) {
        return Float.isFinite(seconds) ? Mth.clamp(seconds, 0f, AlarmSounds.HARD_MAX_SECONDS) : 0f;
    }

    /** Hoe hard, hoe ver en hoe lang; alles binnen de grenzen. */
    public void setSettings(float volume, int range, int alarmSeconds) {
        this.volume = Float.isFinite(volume) ? Mth.clamp(Math.round(volume * 20f) / 20f, 0.1f, 1.0f) : 1.0f;
        this.range = Mth.clamp(range, MIN_RANGE, maxRange());
        this.alarmSeconds = clampAlarmSeconds(alarmSeconds);
        changed();
    }

    /** "Hoe lang" binnen de grenzen: 0 (tot je hem uitzet) of 5 seconden tot het maximum uit de config. */
    public static int clampAlarmSeconds(int seconds) {
        if (seconds <= 0) {
            return 0;
        }
        int max = Math.max(5, Math.round(ImtConfig.Alarms.MAX_ALARM_SECONDS));
        return Mth.clamp(seconds, 5, max);
    }

    /** Bewaren, en de nieuwe stand naar de spelers in de buurt sturen (het geluid, het bereik). */
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

    // ================================================================ Afgaan en stoppen

    /** Hij gaat af voor zoveel seconden (de knop Test), of verlengt het tot dan. */
    public void soundFor(ServerLevel level, int seconds) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AlarmSpeakerBlock) || !state.getValue(AlarmSpeakerBlock.ONLINE)) {
            return;
        }
        long until = level.getGameTime() + Math.max(1, seconds) * 20L;
        if (!state.getValue(AlarmSpeakerBlock.SOUNDING)) {
            soundUntil = until;
            level.setBlock(worldPosition, state.setValue(AlarmSpeakerBlock.SOUNDING, true), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        } else if (soundUntil != 0) {
            soundUntil = Math.max(soundUntil, until);
        }
        setChanged();
    }

    /**
     * Op zijn naam aangezet (een diagram {@code on("Lab alarm")}, of met de hand in Devices): hij loeit
     * {@link #alarmSeconds} seconden (0 = tot je hem uitzet). Een puls zet hem zelf eerder uit. Dit is de enige manier
     * waarop hij afgaat, naast Test (hoofdstuk 128).
     */
    void switchedOn(ServerLevel level) {
        soundUntil = alarmSeconds == 0 ? 0 : level.getGameTime() + alarmSeconds * 20L;
        setChanged();
    }

    /**
     * Een puls zette hem aan (of hij stond al aan): hij stopt na {@code ticks} ticks, ook als dat langer of korter is dan
     * zijn eigen lengte, en ook als hij op "tot je hem uitzet" staat. Gevonden door de nalezing van hoofdstuk 128: eerst
     * kreeg een puls de eindtijd van de speaker zelf, en stopte de server midden in een puls, dan loeide hij na het laden
     * zonder script verder (bij "tot je hem uitzet" zelfs voor altijd).
     */
    void pulsed(ServerLevel level, int ticks) {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof AlarmSpeakerBlock && state.getValue(AlarmSpeakerBlock.SOUNDING)) {
            soundUntil = level.getGameTime() + Math.max(1, ticks);
            setChanged();
        }
    }

    /** Uitgezet: geen eindtijd meer. */
    void switchedOff() {
        soundUntil = 0;
        setChanged();
    }

    // ================================================================ Elke tick (op de server)

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if ((level.getGameTime() + pos.hashCode()) % 40 == 0) {
            updateOnline(level);
        }
        checkDeadline(level);
    }

    /**
     * Is de eindtijd voorbij? Dan zetten we hem uit zoals een diagram dat zou doen, zodat "when turned_off" ook werkt. Dit
     * gebeurt in zijn eigen tick, en (via AlarmSpeakers.onServerTick) ook als zijn stuk wereld wel geladen is maar niet
     * "tikt", zoals aan het eind van een lange kabel: anders bleef hij daar loeien tot er iemand in de buurt kwam.
     */
    void checkDeadline(ServerLevel level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AlarmSpeakerBlock) || !state.getValue(AlarmSpeakerBlock.SOUNDING) || soundUntil == 0
                || level.getGameTime() < soundUntil) {
            return;
        }
        soundUntil = 0;
        setChanged();
        if (!com.senne.imttech.redstone.ImtRedstone.switchAt(level, worldPosition, -1, com.senne.imttech.redstone.ImtRedstone.Op.OFF, 0)) {
            level.setBlock(worldPosition, state.setValue(AlarmSpeakerBlock.SOUNDING, false), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /**
     * Heeft hij stroom, hangt hij aan een kern (met een netwerkkabel of een router; zonder netwerk-eis: staat hij in het
     * gebied van een kern), en is hij niet uitgeschakeld door een EMP-granaat? Zet dat in het blok (ONLINE), en betaal de
     * stroom. Zonder dat stopt hij meteen met loeien.
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
        if (!online && state.getValue(AlarmSpeakerBlock.SOUNDING)) {
            next = next.setValue(AlarmSpeakerBlock.SOUNDING, false);
            soundUntil = 0;
            setChanged();
        }
        if (next != state) {
            // Alleen naar de spelers sturen, geen buren wakker maken (dit kan in een stuk wereld dat niet "tickt").
            level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /**
     * Hoofdstuk 124: draait zijn stuk wereld niet (aan het eind van een lange kabel, dat alleen geladen gehouden wordt), dan
     * kijkt zijn kern om de twee seconden of hij aan kan (zie DevicePower.refreshIdle), zodat hij een alarm toch hoort. Alleen
     * aan, nooit uit (daar ziet de kern zijn eigen stroom niet altijd); uit gaat hij in zijn eigen tick.
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
            if (!soundId.isEmpty()) {
                AlarmSounds.touch(server.getServer(), soundId);
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

    /** Wat er naar je spel gaat: alles behalve de eindtijd (die heeft je spel niet nodig). */
    private void saveShared(CompoundTag tag) {
        tag.putString("Name", name);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.putString("SoundId", soundId);
        tag.putString("SoundName", soundName);
        tag.putFloat("SoundSeconds", soundSeconds);
        tag.putFloat("Volume", volume);
        tag.putInt("Range", range);
        tag.putInt("AlarmSeconds", alarmSeconds);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveShared(tag);
        tag.putLong("SoundUntil", soundUntil);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = cleanName(tag.getString("Name"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        String id = tag.getString("SoundId");
        soundId = AlarmSounds.validId(id) ? id : "";
        soundName = soundId.isEmpty() ? "" : clean(tag.getString("SoundName"), MAX_SOUND_NAME);
        soundSeconds = soundId.isEmpty() ? 0f : clampSeconds(tag.getFloat("SoundSeconds"));
        float savedVolume = tag.contains("Volume") ? tag.getFloat("Volume") : 1.0f;
        volume = Float.isFinite(savedVolume) ? Mth.clamp(savedVolume, 0.1f, 1.0f) : 1.0f;
        range = tag.contains("Range") ? Mth.clamp(tag.getInt("Range"), MIN_RANGE, maxRange()) : DEFAULT_RANGE;
        alarmSeconds = tag.contains("AlarmSeconds") ? clampAlarmSeconds(tag.getInt("AlarmSeconds")) : DEFAULT_ALARM_SECONDS;
        soundUntil = Math.max(0L, tag.getLong("SoundUntil"));
    }

    /**
     * Breek je hem af, dan houdt het item zijn naam (CUSTOM_NAME) en zijn geluid en instellingen (AlarmSettings; zie de loot
     * table: copy_components). Zet je hem weer neer, dan heeft hij alles nog (zoals een kern zijn instellingen houdt). De
     * eigenaar gaat niet mee: wie hem neerzet, is de nieuwe eigenaar.
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!name.isEmpty()) {
            components.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        AlarmSettings settings = new AlarmSettings(soundId, soundName, soundSeconds, volume, range, alarmSeconds);
        if (!settings.equals(AlarmSettings.DEFAULT)) {
            components.set(com.senne.imttech.registry.ModDataComponents.ALARM_SETTINGS.get(), settings);
        }
    }

    /**
     * Neergezet van een item met instellingen (zie AlarmSettings): die neemt hij over, binnen de grenzen van nu. De naam
     * leest AlarmSpeakerBlock.setPlacedBy (die zet hem ook in je I.M.T-lijst); we "lezen" hem hier ook, anders bewaart
     * Minecraft hem als los onderdeel en komt de oude naam bij de volgende keer afbreken terug.
     */
    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        input.get(DataComponents.CUSTOM_NAME);
        AlarmSettings settings = input.get(com.senne.imttech.registry.ModDataComponents.ALARM_SETTINGS.get());
        if (settings != null) {
            soundId = AlarmSounds.validId(settings.soundId()) ? settings.soundId() : "";
            soundName = soundId.isEmpty() ? "" : clean(settings.soundName(), MAX_SOUND_NAME);
            soundSeconds = soundId.isEmpty() ? 0f : clampSeconds(settings.soundSeconds());
            volume = Float.isFinite(settings.volume()) ? Mth.clamp(settings.volume(), 0.1f, 1.0f) : 1.0f;
            range = Mth.clamp(settings.range(), MIN_RANGE, maxRange());
            alarmSeconds = clampAlarmSeconds(settings.alarmSeconds());
        }
    }

    /** Naam, geluid en instellingen zitten al in het item (als onderdelen), de eigenaar en de eindtijd horen er niet in. */
    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Name");
        tag.remove("Owner");
        tag.remove("OwnerName");
        tag.remove("SoundUntil");
        tag.remove("SoundId");
        tag.remove("SoundName");
        tag.remove("SoundSeconds");
        tag.remove("Volume");
        tag.remove("Range");
        tag.remove("AlarmSeconds");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveShared(tag);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
