package com.senne.imttech.security;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.security.net.CoreNetworkData;
import com.senne.imttech.security.os.EventLog;
import com.senne.imttech.security.os.SystemDefaults;
import com.senne.imttech.security.os.SystemPassword;
import com.senne.imttech.security.os.VirtualFs;
import com.senne.imttech.security.zone.ZoneBox;
import com.senne.imttech.security.zone.ZoneData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * De "binnenkant" van een beveiligingskern: van wie de toren is (niemand, tot iemand hem hackt).
 *
 * Tot hoofdstuk 25 hield de kern zelf vier beveiligingsdrones in de lucht (en verving hij ze gratis). Dat doet hij
 * niet meer (jouw antwoord van 14 oktober: "de core maakt geen gratis drones meer"): je bouwt zelf security drones
 * (GuardDroneEntity), en de schakelaar "Drones" op de desktop zet ze allemaal aan of uit. Oude drones uit een
 * bestaande wereld (SecurityDroneEntity) blijven werken tot ze neergeschoten worden.
 *
 * De kern heeft STROOM nodig (van de krachtcentrale van de toren): elke twee seconden betaalt
 * hij CORE_UPKEEP. Is er geen stroom, dan doen de oude drones niets meer: ze zakken naar de grond
 * en hun lampjes gaan uit (zie SecurityDroneEntity).
 *
 * Sinds 28 september is de kern ook een kleine COMPUTER (het "systeem"): hij heeft een IP-adres,
 * een SSH-poort, een naam (hostname), een wachtwoord, mappen en bestanden (VirtualFs) en een
 * logboek (EventLog). Dat wordt allemaal gemaakt bij de eerste tick ({@link #ensureSystem}), dus
 * ook een kern uit een oude wereld krijgt het vanzelf.
 *
 * Sinds 8 oktober (hoofdstuk 10) kan de kern ook een ZONE hebben: kubussen waarin alleen de eigenaar
 * en zijn vrienden mogen bouwen en breken (zie security/zone). De kubussen staan in het overzicht van
 * de wereld (ZoneData), zodat de zone ook werkt als deze chunk niet geladen is; de kern vertelt daar
 * van wie hij is, wie hij vertrouwt, en of er stroom genoeg is.
 */
public class SecurityCoreBlockEntity extends BlockEntity {
    private @Nullable UUID ownerId;
    private String ownerName = "";

    // ---------------------------------------------------------------- Het systeem
    /** Het IP-adres (10.x.y.z), leeg tot de eerste tick. */
    private String ip = "";
    private int sshPort;
    private String hostname = "";
    /** De echte naam van het beveiligingsprogramma (staat in security.conf). Leeg = nog geen systeem. */
    private String daemon = "";
    /** Het getal waar de rest uit komt (procesnummers, het logboek van de statuspagina). */
    private long systemSeed;
    /** Null = geen wachtwoord: dan heeft het systeem nog het oude geheugenlek van Biebia's firmware. */
    private @Nullable SystemPassword password;
    private VirtualFs fs = new VirtualFs();
    private EventLog log = new EventLog();
    /** De server: keycards en (straks) de redstone-diagrammen (zie LogicStore). */
    private com.senne.imttech.logic.LogicStore logic = new com.senne.imttech.logic.LogicStore();
    /** Het datacenter: de temperatuur van de racks (zie Datacenter). */
    private com.senne.imttech.logic.Datacenter datacenter = new com.senne.imttech.logic.Datacenter();
    /** Had de kern bij de laatste telling stroom? */
    private boolean online = true;
    /** Zette deze kern (van een Biebia's Industries Tower) Biebia's drones al op zijn docks (hoofdstuk 25)? Dat doet hij maar één keer. */
    private boolean towerDrones;
    /** De rapporten van de laatste trainingen (hoofdstuk 26), de oudste eerst. */
    private final java.util.List<com.senne.imttech.training.TrainingReport> trainingReports = new java.util.ArrayList<>();
    /**
     * Welke vensters er op de desktop open stonden toen hij dichtging (19 oktober, jouw notitie: "je slaat op wat er
     * vorige keer opstond, tenzij de pc wordt uitgezet met de powerknop"): per venster de app en waar het stond, het
     * onderste eerst. Leeg = hij start met alleen Status. Eén desktop voor iedereen, zoals een echte pc.
     */
    private net.minecraft.nbt.ListTag desktop = new net.minecraft.nbt.ListTag();

    // ---------------------------------------------------------------- Toegang (de desktop regelt dit)
    /**
     * Vertrouwde spelers (naast de eigenaar en zijn team): de beveiliging schiet niet op hen. Ze
     * mogen niet aan de desktop komen. UUID -> naam, zodat de lijst leesbaar blijft.
     */
    private final java.util.LinkedHashMap<UUID, String> trusted = new java.util.LinkedHashMap<>();
    /** Schiet de beveiliging op monsters? (Standaard ja.) */
    private boolean attackMobs = true;
    /** Schiet de beveiliging op andere spelers? (Standaard nee; ook dan alleen met PvP aan.) */
    private boolean attackPlayers;
    /** Staan de drones aan? Uit = je security drones gaan naar hun dock, en oude drones landen bij de kern. */
    private boolean dronesEnabled = true;
    /** Staan de laserwanden in dit gebied aan? */
    private boolean lasersEnabled = true;
    /** Staan de camera's in dit gebied aan? */
    private boolean camerasEnabled = true;
    /** Hoofdstuk 54 (jouw notitie): de apps die de eigenaar van het bureaublad haalde (Settings > Apps on this desktop). */
    private final java.util.LinkedHashSet<String> hiddenApps = new java.util.LinkedHashSet<>();

    // ---------------------------------------------------------------- Autorisatiecodes (hoofdstuk 49)
    /** Hoeveel cijfers (0 = de config), om de hoeveel minuten (0 = de config), en of de vorige code nog mag. */
    private int authLength;
    private int authMinutes;
    private boolean authOldAllowed = ImtConfig.Security.AUTH_OLD_CODES;
    private String authCode = "";
    private String authPrevious = "";
    private long authSince;

    // ---------------------------------------------------------------- Telefoons en berichten (hoofdstuk 50)
    /** Een I.M.T Phone die aan deze kern hangt: zijn nummer, van wie, zijn PIN en of de eigenaar hem blokkeerde. */
    public record Phone(UUID id, String name, String pin, boolean blocked) {}
    /** Wat I.M.T de eigenaar zei (say, alarmen, verzoeken, logins): de app Messages. */
    public record Message(long time, String text, boolean alarm) {}
    public static final int MAX_MESSAGES = 60;
    private final java.util.LinkedHashMap<UUID, Phone> phones = new java.util.LinkedHashMap<>();
    private final java.util.ArrayDeque<Message> messages = new java.util.ArrayDeque<>();

    /** Heeft de kern stroom (en werken zijn drones dus)? */
    public boolean isOnline() {
        return online;
    }

    public SecurityCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SECURITY_CORE.get(), pos, state);
    }

    public @Nullable UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    /** De toren is van deze speler (na het hacken met de laptop). */
    public void setOwner(ServerPlayer player) {
        setOwner(player.getUUID(), player.getGameProfile().getName());
    }

    /** Van deze speler, ook als hij nu niet online is (de showcase geeft zo een basis aan "Lotte"). */
    public void setOwner(UUID id, String name) {
        ownerId = id;
        ownerName = name;
        setChanged();
        if (level != null) {
            level.setBlock(worldPosition, getBlockState().setValue(SecurityCoreBlock.CLAIMED, true), Block.UPDATE_ALL);
        }
        syncZone();
    }

    /** Weer van niemand (voor de showcase, die de hack elke keer opnieuw wil laten zien). */
    public void clearOwner() {
        ownerId = null;
        ownerName = "";
        setChanged();
        if (level != null) {
            level.setBlock(worldPosition, getBlockState().setValue(SecurityCoreBlock.CLAIMED, false), Block.UPDATE_ALL);
        }
        syncZone();
    }

    // ================================================================ Aanmelden

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            SecurityCores.loaded(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            SecurityCores.unloaded(level, worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            SecurityCores.unloaded(level, worldPosition);
        }
    }

    // ================================================================ Het systeem

    /**
     * Zorgt dat de kern een systeem heeft: een IP (uniek in de wereld, zie CoreNetworkData), een
     * SSH-poort, een naam en de eerste bestanden. Heeft hij dat al, dan gebeurt er niets (behalve
     * dat zijn IP weer in het netwerk staat, voor als dat verloren ging).
     */
    public void ensureSystem(ServerLevel level) {
        GlobalPos here = GlobalPos.of(level.dimension(), worldPosition);
        String allocated = CoreNetworkData.get(level).allocate(here, ip);
        if (!allocated.equals(ip)) {
            ip = allocated;
            setChanged();
        }
        if (!daemon.isEmpty()) {
            return;
        }
        systemSeed = level.random.nextLong();
        daemon = SystemDefaults.DAEMONS[level.random.nextInt(SystemDefaults.DAEMONS.length)];
        sshPort = 1024 + level.random.nextInt(8976);
        fs = new VirtualFs();
        if (ownerId == null) {
            // Van niemand: dit is de kern van een Biebia's Industries Tower (het systeem van Biebia).
            hostname = SystemDefaults.BIEBIA_HOST;
            SystemDefaults.biebiaFiles(fs, daemon);
            // Hoofdstuk 54: het diagram "Generator room" en de groep "staff" (zie structures/TowerGuards).
            com.senne.imttech.structures.TowerGuards.installBiebiaLogic(logic);
        } else {
            hostname = SystemDefaults.hostFor(ownerName);
            SystemDefaults.playerFiles(fs, ownerName, daemon);
        }
        log.add(level.getGameTime(), EventLog.Type.SYSTEM, hostname + " online at " + ip);
        setChanged();
    }

    /** Is het systeem al gemaakt? (Pas na de eerste tick.) */
    public boolean hasSystem() {
        return !daemon.isEmpty() && !ip.isEmpty();
    }

    public String ip() {
        return ip;
    }

    public int sshPort() {
        return sshPort;
    }

    public String hostname() {
        return hostname;
    }

    public String daemon() {
        return daemon;
    }

    public long systemSeed() {
        return systemSeed;
    }

    public VirtualFs fs() {
        return fs;
    }

    public EventLog log() {
        return log;
    }

    /** Wat de server onthoudt: de keycards en de redstone-diagrammen. */
    public com.senne.imttech.logic.Datacenter datacenter() {
        return datacenter;
    }

    public com.senne.imttech.logic.LogicStore logic() {
        return logic;
    }

    /** Heeft het systeem een wachtwoord? Zonder heeft het het oude geheugenlek. */
    public boolean hasPassword() {
        return password != null;
    }

    public @Nullable SystemPassword password() {
        return password;
    }

    /** Een nieuw wachtwoord (het moet SystemPassword.isValid zijn), of null om het te wissen. */
    public void setPassword(@Nullable String newPassword, RandomSource random) {
        password = newPassword == null ? null : SystemPassword.create(newPassword, random);
        setChanged();
    }

    /** Klopt dit wachtwoord? (Zonder wachtwoord klopt niets: dan moet je eerst een instellen.) */
    public boolean checkPassword(String attempt) {
        return password != null && password.matches(attempt);
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
        setChanged();
    }

    public void setSshPort(int port) {
        this.sshPort = port;
        setChanged();
    }

    // ---------------------------------------------------------------- Toegang
    public boolean attackMobs() {
        return attackMobs;
    }

    public boolean attackPlayers() {
        return attackPlayers;
    }

    public boolean dronesEnabled() {
        return dronesEnabled;
    }

    public boolean lasersEnabled() {
        return lasersEnabled;
    }

    public boolean camerasEnabled() {
        return camerasEnabled;
    }

    public void setAttackMobs(boolean on) {
        attackMobs = on;
        setChanged();
    }

    /** Hoofdstuk 54: de apps (hun namen, zoals KEYCARDS) die niet op het bureaublad van deze kern staan. */
    public java.util.Set<String> hiddenApps() {
        return java.util.Collections.unmodifiableSet(hiddenApps);
    }

    /** Een app van het bureaublad halen ({@code hidden}) of terugzetten. */
    public void setAppHidden(String app, boolean hidden) {
        if (hidden) {
            hiddenApps.add(app);
        } else {
            hiddenApps.remove(app);
        }
        setChanged();
    }

    public void setAttackPlayers(boolean on) {
        attackPlayers = on;
        setChanged();
    }

    public void setDronesEnabled(boolean on) {
        dronesEnabled = on;
        setChanged();
    }

    public void setLasersEnabled(boolean on) {
        lasersEnabled = on;
        setChanged();
    }

    public void setCamerasEnabled(boolean on) {
        camerasEnabled = on;
        setChanged();
    }

    /**
     * Spelers die de beveiliging een tijdje opjaagt, ook als "attack players" uit staat: wie een
     * valstrik-bestand opende. UUID -> tot wanneer (speltijd). Niet bewaard: na herladen vergeten.
     */
    private final java.util.Map<UUID, Long> hunted = new java.util.HashMap<>();

    /** Laat de beveiliging deze speler opjagen tot speltijd {@code until}. */
    public void hunt(UUID player, long until) {
        hunted.put(player, until);
    }

    /** Wordt deze speler nu opgejaagd? */
    public boolean isHunted(UUID player) {
        Long until = hunted.get(player);
        if (until == null) {
            return false;
        }
        if (level == null || level.getGameTime() >= until) {
            hunted.remove(player);
            return false;
        }
        return true;
    }

    /** Is deze speler vertrouwd (door de eigenaar toegevoegd)? */
    public boolean isTrusted(UUID player) {
        return trusted.containsKey(player);
    }

    /** De namen van de vertrouwde spelers, in de volgorde waarin ze erbij kwamen. */
    public java.util.List<String> trustedNames() {
        return java.util.List.copyOf(trusted.values());
    }

    public void addTrusted(UUID id, String name) {
        trusted.put(id, name);
        setChanged();
        syncZone();
    }

    /** Haalt een vertrouwde speler weg op zijn naam (hoofdletters maken niet uit). */
    public boolean removeTrusted(String name) {
        boolean removed = trusted.values().removeIf(trustedName -> trustedName.equalsIgnoreCase(name));
        if (removed) {
            setChanged();
            syncZone();
        }
        return removed;
    }

    /**
     * Wist de toegang voor een nieuwe eigenaar (bij een overname): de vertrouwde lijst leeg, het
     * wachtwoord weg (de nieuwe eigenaar zet zelf een nieuw), en de apparaten weer aan. De mappen en
     * notities blijven staan (jouw wens, 28 september).
     */
    public void resetAccessForNewOwner() {
        trusted.clear();
        password = null;
        attackMobs = true;
        attackPlayers = false;
        dronesEnabled = true;
        lasersEnabled = true;
        camerasEnabled = true;
        setChanged();
        syncZone();
    }

    /** Een regel in het logboek (met de speltijd van nu). */
    /** De rapporten van de laatste trainingen (hoofdstuk 26), de oudste eerst. */
    public java.util.List<com.senne.imttech.training.TrainingReport> trainingReports() {
        return java.util.Collections.unmodifiableList(trainingReports);
    }

    /** Een training is voorbij: zijn rapport erbij (hooguit {@code maxReports}; de oudste gaat eruit). */
    public void addTrainingReport(com.senne.imttech.training.TrainingReport report) {
        trainingReports.add(report);
        while (trainingReports.size() > Math.max(1, Math.round(ImtConfig.Training.MAX_REPORTS))) {
            trainingReports.removeFirst();
        }
        setChanged();
    }

    /** De vensters die op de desktop open stonden (een kopie; leeg = hij start met alleen Status). */
    public net.minecraft.nbt.ListTag desktop() {
        return desktop.copy();
    }

    /** Onthoudt welke vensters er open staan (de desktop ging dicht), of vergeet ze (leeg: hij werd uitgezet). */
    public void setDesktop(net.minecraft.nbt.ListTag windows) {
        desktop = windows.copy();
        setChanged();
    }

    public void logEvent(EventLog.Type type, String text) {
        log.add(level == null ? 0 : level.getGameTime(), type, text);
        setChanged();
    }

    /** De kern is afgebroken: zijn IP is weer vrij. */
    public void releaseSystem(ServerLevel level) {
        // Eerst onthouden wat er in de wereld bij deze kern hoorde: valt hij hierna nog als item, dan
        // gaat dat mee (zie pack). Bij hakken komt de kern namelijk eerst weg, en valt het item pas daarna.
        worldSnapshot = worldData(level);
        CoreNetworkData.get(level).release(GlobalPos.of(level.dimension(), worldPosition));
        // Het datacenter is weg, en de backups die erop stonden ook.
        com.senne.imttech.logic.Datacenters.get(level).forget(GlobalPos.of(level.dimension(), worldPosition));
        // En de zone: zonder kern beschermt niets hem meer.
        ZoneData.get(level).forget(GlobalPos.of(level.dimension(), worldPosition));
    }

    // ================================================================ Weghakken en weer neerzetten (9 oktober)

    /**
     * Wat er buiten de kern in de wereld bij hem hoorde (de zone en de backups op dit datacenter),
     * vlak voordat de wereld het vergat (zie releaseSystem). Niet bewaard.
     */
    private @Nullable CompoundTag worldSnapshot;

    /**
     * Valt er iets in te pakken? Een kern van niemand (de Biebia's Industries Tower van Biebia, nog niet gehackt) niet:
     * die valt als een nieuwe kern, zoals altijd.
     */
    public boolean canPack() {
        return ownerId != null && hasSystem();
    }

    /**
     * Pakt alles van deze kern in, voor het item dat uitvalt (jouw wens, 9 oktober: "zorg dat al de
     * config bewaard wordt in de security block, zodat als je die hakt niet alles weg is"). De
     * instellingen bewaart de server (PackedCores); het item krijgt het etiket dat we teruggeven.
     * Null als er niets in te pakken is.
     */
    public @Nullable PackedCore pack(ServerLevel level) {
        if (!canPack()) {
            return null;
        }
        CompoundTag own = new CompoundTag();
        saveConfig(own);
        CompoundTag config = new CompoundTag();
        config.put("Core", own);
        config.put("World", worldSnapshot != null ? worldSnapshot : worldData(level));
        return new PackedCore(PackedCores.get(level).store(config), ownerName, hostname, ip);
    }

    /** De zone en de backups op dit datacenter: die staan niet in de kern zelf, maar in de wereld (ZoneData, Datacenters). */
    private CompoundTag worldData(ServerLevel level) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", level.dimension().location().toString());
        net.minecraft.nbt.ListTag boxes = new net.minecraft.nbt.ListTag();
        for (ZoneBox box : zoneBoxes()) {
            boxes.add(box.save());
        }
        tag.put("Zone", boxes);
        tag.put("Backups", com.senne.imttech.logic.Datacenters.get(level)
                .saveStoredAt(GlobalPos.of(level.dimension(), worldPosition)));
        return tag;
    }

    /**
     * Een ingepakte kern staat weer: alles komt terug zoals het was (zie {@link #pack}).
     *
     * <ul>
     *   <li>De eigenaar blijft de eigenaar, ook als iemand anders hem neerzet: met de kern gaan ook
     *       zijn wachtwoord en bestanden mee, dus een gestolen kern moet je nog steeds hacken.</li>
     *   <li>Het IP krijgt hij terug als het nog vrij is (keycards horen bij het IP).</li>
     *   <li>De zone krijgt hij terug voor zover die binnen ZONE_REACH van de nieuwe plek past (staat
     *       hij weer op dezelfde plek, dan is het dezelfde zone). Een kubus die nu de zone of het
     *       gebied van een andere kern raakt, valt weg.</li>
     *   <li>De backups die op dit datacenter stonden, staan er weer op.</li>
     * </ul>
     */
    public void unpack(ServerLevel level, CompoundTag config, String placedBy) {
        loadConfig(config.getCompound("Core"));
        level.setBlock(worldPosition, getBlockState().setValue(SecurityCoreBlock.CLAIMED, ownerId != null), Block.UPDATE_ALL);
        ensureSystem(level);
        syncZone();
        CompoundTag world = config.getCompound("World");
        if (world.getString("Dim").equals(level.dimension().location().toString())) {
            List<ZoneBox> boxes = new java.util.ArrayList<>();
            for (Tag element : world.getList("Zone", Tag.TAG_INT_ARRAY)) {
                ZoneBox box = ZoneBox.load(((net.minecraft.nbt.IntArrayTag) element).getAsIntArray());
                ZoneBox fitted = box == null ? null : box.clamp(zoneLow(), zoneHigh());
                if (fitted != null && !zoneBoxBlocked(fitted)) {
                    boxes.add(fitted);
                }
            }
            if (!boxes.isEmpty()) {
                setZoneBoxes(boxes);
            }
        }
        com.senne.imttech.logic.Datacenters.get(level).adopt(GlobalPos.of(level.dimension(), worldPosition),
                world.getList("Backups", Tag.TAG_COMPOUND));
        logEvent(EventLog.Type.OWNER, "Core re-installed by " + placedBy + " at "
                + worldPosition.getX() + " " + worldPosition.getY() + " " + worldPosition.getZ() + ", all settings kept");
    }

    // ================================================================ De zone (hoofdstuk 10)

    /** De zone van deze kern (uit het overzicht van de wereld, zie ZoneData), of null zonder zone. */
    public @Nullable ZoneData.Zone zone() {
        return level instanceof ServerLevel server
                ? ZoneData.get(server).zone(GlobalPos.of(server.dimension(), worldPosition)) : null;
    }

    /** De kubussen van de zone (leeg zonder zone). */
    public List<ZoneBox> zoneBoxes() {
        ZoneData.Zone zone = zone();
        return zone == null ? List.of() : zone.boxes();
    }

    /**
     * De kleinste hoek waar de zone van deze kern mag komen: de rand van zijn gebied, ZONE_REACH blokken
     * naar het westen, naar beneden en naar het noorden (maar niet onder de wereld).
     */
    public BlockPos zoneLow() {
        int bottom = level == null ? Integer.MIN_VALUE : level.getMinBuildHeight();
        int reach = Math.round(ImtConfig.Security.ZONE_REACH);
        return new BlockPos(worldPosition.getX() - reach, Math.max(bottom, worldPosition.getY() - reach), worldPosition.getZ() - reach);
    }

    /** De grootste hoek waar de zone mag komen (maar niet boven de wereld). */
    public BlockPos zoneHigh() {
        int top = level == null ? Integer.MAX_VALUE : level.getMaxBuildHeight() - 1;
        int reach = Math.round(ImtConfig.Security.ZONE_REACH);
        return new BlockPos(worldPosition.getX() + reach, Math.min(top, worldPosition.getY() + reach), worldPosition.getZ() + reach);
    }

    /**
     * Zit deze kubus een ander in de weg (de zone of het gebied van een andere kern, zie
     * SecurityCores.zoneBlocked)? Dan mag hij niet in de zone.
     */
    public boolean zoneBoxBlocked(ZoneBox box) {
        return level instanceof ServerLevel server && SecurityCores.zoneBlocked(server, worldPosition, box);
    }

    /**
     * Nieuwe kubussen voor de zone. Wat verder dan ZONE_REACH van de kern steekt, valt eraf, en meer
     * dan ZONE_MAX_BOXES kubussen kan niet. Geeft terug wat er echt in staat. (Of een kubus een ander
     * in de weg zit, kijkt wie hem toevoegt of groter maakt: zie {@link #zoneBoxBlocked}.)
     */
    public List<ZoneBox> setZoneBoxes(List<ZoneBox> wanted) {
        if (!(level instanceof ServerLevel server)) {
            return List.of();
        }
        List<ZoneBox> fitted = new java.util.ArrayList<>();
        for (ZoneBox box : wanted) {
            if (fitted.size() >= ImtConfig.Security.ZONE_MAX_BOXES) {
                break;
            }
            ZoneBox clamped = box.clamp(zoneLow(), zoneHigh());
            if (clamped != null) {
                fitted.add(clamped);
            }
        }
        syncZone();
        ZoneData.get(server).setBoxes(GlobalPos.of(server.dimension(), worldPosition), fitted);
        return fitted;
    }

    /** Zoveel stroom kost de zone om de twee seconden, bovenop de kern zelf (0 zonder kubussen). */
    public int zoneUpkeep() {
        ZoneData.Zone zone = zone();
        if (zone == null || !zone.hasBoxes()) {
            return 0;
        }
        return (int) Math.ceil(zone.volume() / Math.max(0.001, ImtConfig.Power.ZONE_BLOCKS_PER_POWER));
    }

    /** Vertelt het overzicht van de wereld (ZoneData) van wie de kern is en wie er mag bouwen. */
    public void syncZone() {
        if (level instanceof ServerLevel server) {
            ZoneData.get(server).report(GlobalPos.of(server.dimension(), worldPosition), ownerId, ownerName, trusted.keySet());
        }
    }

    /**
     * Heeft de zone stroom? Verandert dat, dan staat het in het logboek en zegt I.M.T het tegen
     * de eigenaar (één keer: bij uitvallen, en weer als het terug is).
     */
    private void updateZonePower(ServerLevel level, boolean powered, int cost) {
        syncZone();
        ZoneData data = ZoneData.get(level);
        GlobalPos here = GlobalPos.of(level.dimension(), worldPosition);
        ZoneData.Zone zone = data.zone(here);
        if (zone == null || zone.powered() == powered) {
            return;
        }
        data.setPowered(here, powered);
        if (!zone.hasBoxes() || ownerId == null) {
            return; // nog niets getekend: niemand hoeft het te weten
        }
        logEvent(EventLog.Type.ZONE, powered ? "Zone online: protected again"
                : "Zone offline: not enough power (needs "
                        + com.senne.imttech.config.Numbers.text(ImtConfig.Power.SECURITY_CORE_UPKEEP + cost) + " every 2 s)");
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            com.senne.imttech.imt.ImtServer.send(owner, net.minecraft.network.chat.Component.translatable(
                            powered ? "zone.imttech.msg.online" : "zone.imttech.msg.offline", hostname),
                    powered ? com.senne.imttech.network.ImtMessagePayload.Level.INFO
                            : com.senne.imttech.network.ImtMessagePayload.Level.WARNING);
        }
    }

    /**
     * Om de twee seconden: heeft de kern stroom, en de zone? De kern kost CORE_UPKEEP, de zone
     * daarbovenop {@link #zoneUpkeep()}. Is er genoeg voor de kern maar niet voor de zone, dan werkt
     * alleen de zone niet. (De tests roepen dit ook zelf aan.)
     */
    public void powerTick(ServerLevel level) {
        int available = com.senne.imttech.energy.PowerGrid.available(level, worldPosition);
        online = available >= ImtConfig.Power.SECURITY_CORE_UPKEEP;
        int zoneCost = zoneUpkeep();
        boolean zonePowered = online && available >= ImtConfig.Power.SECURITY_CORE_UPKEEP + zoneCost;
        updateZonePower(level, zonePowered, zoneCost);
        // Wat er niet betaald kon worden, telt als tekort op het net (voor het scherm van een generator).
        if (!online) {
            com.senne.imttech.energy.PowerGrid.missed(level, worldPosition, ImtConfig.Power.SECURITY_CORE_UPKEEP + zoneCost);
        } else if (!zonePowered) {
            com.senne.imttech.energy.PowerGrid.missed(level, worldPosition, zoneCost);
        }
        if (online) {
            com.senne.imttech.energy.PowerGrid.pay(level, worldPosition,
                    ImtConfig.Power.SECURITY_CORE_UPKEEP + (zonePowered ? zoneCost : 0));
        }
        // Hoofdstuk 38: de stroom van de lezers en panelen op zijn netwerk (ieder van zijn eigen stroomnet).
        com.senne.imttech.lan.DevicePower.payUpkeep(level, SecurityDevices.scan(level, this).inputs());
    }

    // ================================================================ Elke tick

    /** Staat deze kern (sinds hij geladen is) al in het netwerk? Niet bewaard: na laden opnieuw. */
    private boolean registered;

    public void serverTick(ServerLevel level, BlockPos pos) {
        if (!registered) {
            ensureSystem(level);
            syncZone();
            registered = true;
        }
        // De server: diagrammen die bezig zijn, timers, de rij (zie LogicRunner).
        com.senne.imttech.logic.LogicRunner.tick(level, this);
        // Het datacenter: hoe warm de racks zijn (om de twee seconden).
        datacenter.tick(level, this);
        // De autorisatiecode (hoofdstuk 49) draait elke paar minuten; de toegangsverzoeken lopen af.
        authTick(level, level.getGameTime());
        com.senne.imttech.logic.AuthRequests.tick(level, this);
        long now = level.getGameTime();
        if ((now + pos.hashCode()) % 40 != 0) {
            return;
        }
        // Een Biebia's Industries Tower van niemand (Biebia's systeem): één keer Biebia's drones op de docks in de hangar (zie TowerDrones).
        if (ownerId == null && !towerDrones && SystemDefaults.BIEBIA_HOST.equals(hostname)) {
            towerDrones = true;
            setChanged();
            com.senne.imttech.robot.TowerDrones.fill(level, this);
        }
        // Stroom voor de kern en zijn zone (zie powerTick).
        powerTick(level);
    }

    // ================================================================ Autorisatiecodes (hoofdstuk 49)

    /** Een getypte code vergeleken met de codes van de kern: geldig, en was het de vorige code? */
    public record AuthCheck(boolean valid, boolean old) {}

    /** Hoeveel cijfers de code heeft (Settings op de desktop; anders [security] authCodeLength). */
    public int authLength() {
        return authLength > 0 ? authLength : Math.max(4, Math.min(8, Math.round(ImtConfig.Security.AUTH_CODE_LENGTH)));
    }

    /** Om de hoeveel minuten er een nieuwe code komt (Settings; anders [security] authCodeMinutes). */
    public int authMinutes() {
        return authMinutes > 0 ? authMinutes : Math.max(1, Math.min(60, Math.round(ImtConfig.Security.AUTH_CODE_MINUTES)));
    }

    /** Mag de vorige code ook nog (hij komt dan wel in het logboek)? */
    public boolean authOldAllowed() {
        return authOldAllowed;
    }

    /** De code van dit moment (leeg tot de eerste tick). */
    public String authCode() {
        return authCode;
    }

    /** De code van daarvoor (leeg als er nog maar één was). */
    public String authPrevious() {
        return authPrevious;
    }

    /** Wanneer (speltijd) de code van nu begon. */
    public long authSince() {
        return authSince;
    }

    /** Zoveel seconden tot de volgende code. */
    public int authSecondsLeft(long now) {
        return (int) Math.max(0, ((long) authMinutes() * 1200 - (now - authSince) + 19) / 20);
    }

    /**
     * Instellen (Settings op de desktop): 4 tot 8 cijfers, elke 1 tot 60 minuten, en of de vorige code nog mag. Een andere
     * lengte geeft meteen een nieuwe code.
     */
    public void setAuthSettings(int length, int minutes, boolean oldAllowed) {
        int newLength = Math.max(4, Math.min(8, length));
        boolean changed = newLength != authLength();
        authLength = newLength;
        authMinutes = Math.max(1, Math.min(60, minutes));
        authOldAllowed = oldAllowed;
        if (changed || authCode.isEmpty()) {
            rotateAuthCode(level == null ? 0 : level.getGameTime());
        }
        setChanged();
    }

    /** Elke tick: de eerste code maken, en na authMinutes een nieuwe. */
    public void authTick(ServerLevel level, long now) {
        if (authCode.isEmpty() || now - authSince >= (long) authMinutes() * 1200) {
            rotateAuthCode(now);
        }
    }

    /** Een nieuwe code; de code van nu wordt de vorige. */
    public void rotateAuthCode(long now) {
        RandomSource random = level == null ? RandomSource.create() : level.random;
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < authLength(); i++) {
            code.append(random.nextInt(10));
        }
        authPrevious = authCode;
        authCode = code.toString();
        authSince = now;
        setChanged();
    }

    /** Is deze getypte code de code van nu (geldig), of de vorige (alleen geldig als dat aan staat)? */
    public AuthCheck checkAuthCode(String typed) {
        if (!authCode.isEmpty() && typed.equals(authCode)) {
            return new AuthCheck(true, false);
        }
        if (!authPrevious.isEmpty() && typed.equals(authPrevious)) {
            return new AuthCheck(authOldAllowed, true);
        }
        return new AuthCheck(false, false);
    }

    // ================================================================ Telefoons en berichten (hoofdstuk 50)

    /** De telefoons die aan deze kern hangen, in de volgorde waarin ze verbonden werden. */
    public List<Phone> phones() {
        return List.copyOf(phones.values());
    }

    public @Nullable Phone phone(UUID id) {
        return phones.get(id);
    }

    /** Een nieuwe telefoon van deze speler (nog zonder PIN). */
    public void linkPhone(UUID id, String name) {
        phones.put(id, new Phone(id, name, "", false));
        setChanged();
    }

    public void setPhonePin(UUID id, String pin) {
        Phone phone = phones.get(id);
        if (phone != null) {
            phones.put(id, new Phone(id, phone.name(), pin, phone.blocked()));
            setChanged();
        }
    }

    /** De eigenaar blokkeert (of deblokkeert) een telefoon in de app Access. */
    public void blockPhone(UUID id, boolean blocked) {
        Phone phone = phones.get(id);
        if (phone != null) {
            phones.put(id, new Phone(id, phone.name(), phone.pin(), blocked));
            setChanged();
        }
    }

    public boolean phoneBlocked(UUID id) {
        Phone phone = phones.get(id);
        return phone == null || phone.blocked();
    }

    /** Een telefoon vergeten (hij kan dan niet meer verbinden tot hij opnieuw verbonden wordt). */
    public void forgetPhone(UUID id) {
        phones.remove(id);
        setChanged();
    }

    /** Een bericht van I.M.T aan de eigenaar (de app Messages); een alarm is rood. Hooguit MAX_MESSAGES. */
    public void addMessage(String text, boolean alarm) {
        messages.addLast(new Message(level == null ? 0 : level.getGameTime(), text, alarm));
        while (messages.size() > MAX_MESSAGES) {
            messages.removeFirst();
        }
        setChanged();
    }

    /** De berichten, de oudste eerst. */
    public List<Message> messages() {
        return List.copyOf(messages);
    }

    /** Het gebied dat deze kern beschermt: ZONE_REACH blokken in elke richting (zie SecurityCores.inArea). */
    public AABB area() {
        return new AABB(worldPosition).inflate(Math.round(ImtConfig.Security.ZONE_REACH));
    }

    // ================================================================ Opslaan

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveConfig(tag);
        tag.putBoolean("TowerDrones", towerDrones);
    }

    /** Alles wat de kern onthoudt: dit gaat ook mee als je hem weghakt (zie pack). */
    private void saveConfig(CompoundTag tag) {
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putString("OwnerName", ownerName);
        // Het systeem
        tag.putString("Ip", ip);
        tag.putInt("SshPort", sshPort);
        tag.putString("Hostname", hostname);
        tag.putString("Daemon", daemon);
        tag.putLong("SystemSeed", systemSeed);
        if (password != null) {
            tag.put("Password", password.save());
        }
        tag.put("Files", fs.save());
        tag.put("Log", log.save());
        net.minecraft.nbt.ListTag reports = new net.minecraft.nbt.ListTag();
        for (com.senne.imttech.training.TrainingReport report : trainingReports) {
            reports.add(report.save());
        }
        tag.put("TrainingReports", reports);
        tag.put("Desktop", desktop.copy());
        tag.put("Logic", logic.save());
        tag.put("Datacenter", datacenter.save());
        // Toegang
        tag.putBoolean("AttackMobs", attackMobs);
        tag.putBoolean("AttackPlayers", attackPlayers);
        tag.putBoolean("DronesOn", dronesEnabled);
        tag.putBoolean("LasersOn", lasersEnabled);
        tag.putBoolean("CamerasOn", camerasEnabled);
        net.minecraft.nbt.ListTag hidden = new net.minecraft.nbt.ListTag();
        for (String app : hiddenApps) {
            hidden.add(net.minecraft.nbt.StringTag.valueOf(app));
        }
        tag.put("HiddenApps", hidden);
        // Autorisatiecodes (hoofdstuk 49)
        tag.putInt("AuthLength", authLength);
        tag.putInt("AuthMinutes", authMinutes);
        tag.putBoolean("AuthOld", authOldAllowed);
        tag.putString("AuthCode", authCode);
        tag.putString("AuthPrevious", authPrevious);
        tag.putLong("AuthSince", authSince);
        // Telefoons en berichten (hoofdstuk 50)
        net.minecraft.nbt.ListTag phoneList = new net.minecraft.nbt.ListTag();
        for (Phone phone : phones.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", phone.id());
            entry.putString("Name", phone.name());
            entry.putString("Pin", phone.pin());
            entry.putBoolean("Blocked", phone.blocked());
            phoneList.add(entry);
        }
        tag.put("Phones", phoneList);
        net.minecraft.nbt.ListTag messageList = new net.minecraft.nbt.ListTag();
        for (Message message : messages) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("T", message.time());
            entry.putString("X", message.text());
            entry.putBoolean("A", message.alarm());
            messageList.add(entry);
        }
        tag.put("Messages", messageList);
        net.minecraft.nbt.ListTag trustedList = new net.minecraft.nbt.ListTag();
        for (java.util.Map.Entry<UUID, String> entry : trusted.entrySet()) {
            CompoundTag person = new CompoundTag();
            person.putUUID("Id", entry.getKey());
            person.putString("Name", entry.getValue());
            trustedList.add(person);
        }
        tag.put("Trusted", trustedList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadConfig(tag);
        towerDrones = tag.getBoolean("TowerDrones");
    }

    /** Terug uit {@link #saveConfig}. */
    private void loadConfig(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        // Een kern uit een oude wereld heeft dit allemaal nog niet: dan blijft het leeg, en maakt
        // ensureSystem het bij de eerste tick.
        ip = tag.getString("Ip");
        sshPort = tag.getInt("SshPort");
        hostname = tag.getString("Hostname");
        daemon = tag.getString("Daemon");
        systemSeed = tag.getLong("SystemSeed");
        password = SystemPassword.load(tag.getCompound("Password"));
        fs = VirtualFs.load(tag.getCompound("Files"));
        log = EventLog.load(tag.getList("Log", Tag.TAG_COMPOUND));
        logic = com.senne.imttech.logic.LogicStore.load(tag.getCompound("Logic"));
        datacenter = com.senne.imttech.logic.Datacenter.load(tag.getCompound("Datacenter"));
        // Toegang. Een oude kern (zonder deze sleutels) valt terug op de standaardwaarden: de
        // beveiliging op monsters aan, op spelers uit, alle apparaten aan.
        attackMobs = !tag.contains("AttackMobs") || tag.getBoolean("AttackMobs");
        attackPlayers = tag.getBoolean("AttackPlayers");
        hiddenApps.clear();
        for (Tag app : tag.getList("HiddenApps", Tag.TAG_STRING)) {
            hiddenApps.add(app.getAsString());
        }
        // Autorisatiecodes (hoofdstuk 49); een oude kern krijgt zijn eerste code bij de volgende tick.
        authLength = tag.getInt("AuthLength");
        authMinutes = tag.getInt("AuthMinutes");
        authOldAllowed = tag.contains("AuthOld") ? tag.getBoolean("AuthOld") : ImtConfig.Security.AUTH_OLD_CODES;
        authCode = tag.getString("AuthCode");
        authPrevious = tag.getString("AuthPrevious");
        authSince = tag.getLong("AuthSince");
        // Telefoons en berichten (hoofdstuk 50)
        phones.clear();
        for (Tag element : tag.getList("Phones", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            if (entry.hasUUID("Id")) {
                phones.put(entry.getUUID("Id"), new Phone(entry.getUUID("Id"), entry.getString("Name"), entry.getString("Pin"),
                        entry.getBoolean("Blocked")));
            }
        }
        messages.clear();
        for (Tag element : tag.getList("Messages", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            messages.addLast(new Message(entry.getLong("T"), entry.getString("X"), entry.getBoolean("A")));
        }
        dronesEnabled = !tag.contains("DronesOn") || tag.getBoolean("DronesOn");
        lasersEnabled = !tag.contains("LasersOn") || tag.getBoolean("LasersOn");
        camerasEnabled = !tag.contains("CamerasOn") || tag.getBoolean("CamerasOn");
        trusted.clear();
        for (Tag element : tag.getList("Trusted", Tag.TAG_COMPOUND)) {
            CompoundTag person = (CompoundTag) element;
            if (person.hasUUID("Id")) {
                trusted.put(person.getUUID("Id"), person.getString("Name"));
            }
        }
        trainingReports.clear();
        for (net.minecraft.nbt.Tag element : tag.getList("TrainingReports", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            trainingReports.add(com.senne.imttech.training.TrainingReport.load((CompoundTag) element));
        }
        desktop = tag.getList("Desktop", Tag.TAG_COMPOUND);
    }
}
