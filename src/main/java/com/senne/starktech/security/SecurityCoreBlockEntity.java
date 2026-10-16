package com.senne.starktech.security;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.security.net.StarkNetworkData;
import com.senne.starktech.security.os.EventLog;
import com.senne.starktech.security.os.SystemDefaults;
import com.senne.starktech.security.os.SystemPassword;
import com.senne.starktech.security.os.VirtualFs;
import com.senne.starktech.security.zone.ZoneBox;
import com.senne.starktech.security.zone.ZoneData;
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
    /** Null = geen wachtwoord: dan heeft het systeem nog het oude geheugenlek van Tony's firmware. */
    private @Nullable SystemPassword password;
    private VirtualFs fs = new VirtualFs();
    private EventLog log = new EventLog();
    /** De server: keycards en (straks) de redstone-diagrammen (zie LogicStore). */
    private com.senne.starktech.logic.LogicStore logic = new com.senne.starktech.logic.LogicStore();
    /** Het datacenter: de temperatuur van de racks (zie Datacenter). */
    private com.senne.starktech.logic.Datacenter datacenter = new com.senne.starktech.logic.Datacenter();
    /** Had de kern bij de laatste telling stroom? */
    private boolean online = true;
    /** Zette deze kern (van een Stark Tower) Tony's drones al op zijn docks (hoofdstuk 25)? Dat doet hij maar één keer. */
    private boolean towerDrones;
    /** De rapporten van de laatste trainingen (hoofdstuk 26), de oudste eerst. */
    private final java.util.List<com.senne.starktech.training.TrainingReport> trainingReports = new java.util.ArrayList<>();

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

    /** Van deze speler, ook als hij nu niet online is (de showcase geeft zo een basis aan "Pepper"). */
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
     * Zorgt dat de kern een systeem heeft: een IP (uniek in de wereld, zie StarkNetworkData), een
     * SSH-poort, een naam en de eerste bestanden. Heeft hij dat al, dan gebeurt er niets (behalve
     * dat zijn IP weer in het netwerk staat, voor als dat verloren ging).
     */
    public void ensureSystem(ServerLevel level) {
        GlobalPos here = GlobalPos.of(level.dimension(), worldPosition);
        String allocated = StarkNetworkData.get(level).allocate(here, ip);
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
            // Van niemand: dit is de kern van een Stark Tower (het systeem van Tony).
            hostname = SystemDefaults.TONY_HOST;
            SystemDefaults.tonyFiles(fs, daemon);
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
    public com.senne.starktech.logic.Datacenter datacenter() {
        return datacenter;
    }

    public com.senne.starktech.logic.LogicStore logic() {
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
    public java.util.List<com.senne.starktech.training.TrainingReport> trainingReports() {
        return java.util.Collections.unmodifiableList(trainingReports);
    }

    /** Een training is voorbij: zijn rapport erbij (hooguit {@code maxReports}; de oudste gaat eruit). */
    public void addTrainingReport(com.senne.starktech.training.TrainingReport report) {
        trainingReports.add(report);
        while (trainingReports.size() > Math.max(1, Math.round(StarkConfig.Training.MAX_REPORTS))) {
            trainingReports.removeFirst();
        }
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
        StarkNetworkData.get(level).release(GlobalPos.of(level.dimension(), worldPosition));
        // Het datacenter is weg, en de backups die erop stonden ook.
        com.senne.starktech.logic.Datacenters.get(level).forget(GlobalPos.of(level.dimension(), worldPosition));
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
     * Valt er iets in te pakken? Een kern van niemand (de Stark Tower van Tony, nog niet gehackt) niet:
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
        tag.put("Backups", com.senne.starktech.logic.Datacenters.get(level)
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
        com.senne.starktech.logic.Datacenters.get(level).adopt(GlobalPos.of(level.dimension(), worldPosition),
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
        int reach = Math.round(StarkConfig.Security.ZONE_REACH);
        return new BlockPos(worldPosition.getX() - reach, Math.max(bottom, worldPosition.getY() - reach), worldPosition.getZ() - reach);
    }

    /** De grootste hoek waar de zone mag komen (maar niet boven de wereld). */
    public BlockPos zoneHigh() {
        int top = level == null ? Integer.MAX_VALUE : level.getMaxBuildHeight() - 1;
        int reach = Math.round(StarkConfig.Security.ZONE_REACH);
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
            if (fitted.size() >= StarkConfig.Security.ZONE_MAX_BOXES) {
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
        return (int) Math.ceil(zone.volume() / Math.max(0.001, StarkConfig.Power.ZONE_BLOCKS_PER_POWER));
    }

    /** Vertelt het overzicht van de wereld (ZoneData) van wie de kern is en wie er mag bouwen. */
    public void syncZone() {
        if (level instanceof ServerLevel server) {
            ZoneData.get(server).report(GlobalPos.of(server.dimension(), worldPosition), ownerId, ownerName, trusted.keySet());
        }
    }

    /**
     * Heeft de zone stroom? Verandert dat, dan staat het in het logboek en zegt E.D.I.T.H. het tegen
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
                        + com.senne.starktech.config.Numbers.text(StarkConfig.Power.SECURITY_CORE_UPKEEP + cost) + " every 2 s)");
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            com.senne.starktech.edith.EdithServer.send(owner, net.minecraft.network.chat.Component.translatable(
                            powered ? "zone.starktech.msg.online" : "zone.starktech.msg.offline", hostname),
                    powered ? com.senne.starktech.network.EdithMessagePayload.Level.INFO
                            : com.senne.starktech.network.EdithMessagePayload.Level.WARNING);
        }
    }

    /**
     * Om de twee seconden: heeft de kern stroom, en de zone? De kern kost CORE_UPKEEP, de zone
     * daarbovenop {@link #zoneUpkeep()}. Is er genoeg voor de kern maar niet voor de zone, dan werkt
     * alleen de zone niet. (De tests roepen dit ook zelf aan.)
     */
    public void powerTick(ServerLevel level) {
        int available = com.senne.starktech.energy.StarkGrid.available(level, worldPosition);
        online = available >= StarkConfig.Power.SECURITY_CORE_UPKEEP;
        int zoneCost = zoneUpkeep();
        boolean zonePowered = online && available >= StarkConfig.Power.SECURITY_CORE_UPKEEP + zoneCost;
        updateZonePower(level, zonePowered, zoneCost);
        // Wat er niet betaald kon worden, telt als tekort op het net (voor het scherm van een generator).
        if (!online) {
            com.senne.starktech.energy.StarkGrid.missed(level, worldPosition, StarkConfig.Power.SECURITY_CORE_UPKEEP + zoneCost);
        } else if (!zonePowered) {
            com.senne.starktech.energy.StarkGrid.missed(level, worldPosition, zoneCost);
        }
        if (online) {
            com.senne.starktech.energy.StarkGrid.pay(level, worldPosition,
                    StarkConfig.Power.SECURITY_CORE_UPKEEP + (zonePowered ? zoneCost : 0));
        }
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
        com.senne.starktech.logic.LogicRunner.tick(level, this);
        // Het datacenter: hoe warm de racks zijn (om de twee seconden).
        datacenter.tick(level, this);
        long now = level.getGameTime();
        if ((now + pos.hashCode()) % 40 != 0) {
            return;
        }
        // Een Stark Tower van niemand (Tony's systeem): één keer Tony's drones op de docks in de hangar (zie TowerDrones).
        if (ownerId == null && !towerDrones && SystemDefaults.TONY_HOST.equals(hostname)) {
            towerDrones = true;
            setChanged();
            com.senne.starktech.robot.TowerDrones.fill(level, this);
        }
        // Stroom voor de kern en zijn zone (zie powerTick).
        powerTick(level);
    }

    /** Het gebied dat deze kern beschermt: ZONE_REACH blokken in elke richting (zie SecurityCores.inArea). */
    public AABB area() {
        return new AABB(worldPosition).inflate(Math.round(StarkConfig.Security.ZONE_REACH));
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
        for (com.senne.starktech.training.TrainingReport report : trainingReports) {
            reports.add(report.save());
        }
        tag.put("TrainingReports", reports);
        tag.put("Logic", logic.save());
        tag.put("Datacenter", datacenter.save());
        // Toegang
        tag.putBoolean("AttackMobs", attackMobs);
        tag.putBoolean("AttackPlayers", attackPlayers);
        tag.putBoolean("DronesOn", dronesEnabled);
        tag.putBoolean("LasersOn", lasersEnabled);
        tag.putBoolean("CamerasOn", camerasEnabled);
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
        logic = com.senne.starktech.logic.LogicStore.load(tag.getCompound("Logic"));
        datacenter = com.senne.starktech.logic.Datacenter.load(tag.getCompound("Datacenter"));
        // Toegang. Een oude kern (zonder deze sleutels) valt terug op de standaardwaarden: de
        // beveiliging op monsters aan, op spelers uit, alle apparaten aan.
        attackMobs = !tag.contains("AttackMobs") || tag.getBoolean("AttackMobs");
        attackPlayers = tag.getBoolean("AttackPlayers");
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
            trainingReports.add(com.senne.starktech.training.TrainingReport.load((CompoundTag) element));
        }
    }
}
