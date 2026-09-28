package com.senne.starktech.security;

import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.security.net.StarkNetworkData;
import com.senne.starktech.security.os.EventLog;
import com.senne.starktech.security.os.SystemDefaults;
import com.senne.starktech.security.os.SystemPassword;
import com.senne.starktech.security.os.VirtualFs;
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
 * De "binnenkant" van een beveiligingskern: van wie de toren is (niemand, tot iemand hem hackt),
 * en de beveiligingsdrones die de kern in de lucht houdt.
 *
 * Elke twee seconden telt de kern zijn drones. Zijn het er minder dan MAX_DRONES (eentje
 * neergeschoten), dan stuurt hij na DRONE_RESPAWN ticks een nieuwe. Zo blijft een toren die van
 * niemand is gevaarlijk; na het hacken zijn diezelfde drones jouw bewakers.
 *
 * De kern heeft STROOM nodig (van de krachtcentrale van de toren): elke twee seconden betaalt
 * hij CORE_UPKEEP. Is er geen stroom, dan stuurt hij geen drones, en de drones die er zijn doen
 * niets meer: ze zakken naar de grond en hun lampjes gaan uit (zie SecurityDroneEntity).
 *
 * Sinds 28 september is de kern ook een kleine COMPUTER (het "systeem"): hij heeft een IP-adres,
 * een SSH-poort, een naam (hostname), een wachtwoord, mappen en bestanden (VirtualFs) en een
 * logboek (EventLog). Dat wordt allemaal gemaakt bij de eerste tick ({@link #ensureSystem}), dus
 * ook een kern uit een oude wereld krijgt het vanzelf.
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
    /** Tot wanneer (speltijd) hij wacht voor hij een nieuwe drone stuurt. */
    private long nextDrone;
    /** Had de kern bij de laatste telling stroom? */
    private boolean online = true;

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
        ownerId = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
        if (level != null) {
            level.setBlock(worldPosition, getBlockState().setValue(SecurityCoreBlock.CLAIMED, true), Block.UPDATE_ALL);
        }
    }

    /** Weer van niemand (voor de showcase, die de hack elke keer opnieuw wil laten zien). */
    public void clearOwner() {
        ownerId = null;
        ownerName = "";
        setChanged();
        if (level != null) {
            level.setBlock(worldPosition, getBlockState().setValue(SecurityCoreBlock.CLAIMED, false), Block.UPDATE_ALL);
        }
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

    /** Een regel in het logboek (met de speltijd van nu). */
    public void logEvent(EventLog.Type type, String text) {
        log.add(level == null ? 0 : level.getGameTime(), type, text);
        setChanged();
    }

    /** De kern is afgebroken: zijn IP is weer vrij. */
    public void releaseSystem(ServerLevel level) {
        StarkNetworkData.get(level).release(GlobalPos.of(level.dimension(), worldPosition));
    }

    // ================================================================ Drones

    /** Staat deze kern (sinds hij geladen is) al in het netwerk? Niet bewaard: na laden opnieuw. */
    private boolean registered;

    public void serverTick(ServerLevel level, BlockPos pos) {
        if (!registered) {
            ensureSystem(level);
            registered = true;
        }
        long now = level.getGameTime();
        if ((now + pos.hashCode()) % 40 != 0) {
            return;
        }
        online = com.senne.starktech.energy.StarkGrid.available(level, pos) >= SecurityBalance.CORE_UPKEEP;
        if (!online) {
            return;
        }
        com.senne.starktech.energy.StarkGrid.draw(level, pos, SecurityBalance.CORE_UPKEEP);
        int drones = level.getEntitiesOfClass(SecurityDroneEntity.class, area(),
                drone -> pos.equals(drone.home())).size();
        if (drones >= SecurityBalance.MAX_DRONES) {
            filled = true;
            nextDrone = 0;
            return;
        }
        if (!filled) {
            // De allereerste keer: de drones komen snel achter elkaar (één per twee seconden).
            launchDrone(level, pos);
            return;
        }
        // Er is er een neergeschoten: na DRONE_RESPAWN ticks komt er een nieuwe.
        if (nextDrone == 0) {
            nextDrone = now + SecurityBalance.DRONE_RESPAWN;
        } else if (now >= nextDrone) {
            launchDrone(level, pos);
            nextDrone = 0;
        }
    }

    /** Zijn de drones al eens allemaal in de lucht geweest? Daarna gaat vervangen langzaam. */
    private boolean filled;

    /** Een nieuwe drone op een vrije plek vlak bij de kern. */
    private void launchDrone(ServerLevel level, BlockPos pos) {
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos spot = pos.offset(level.random.nextInt(13) - 6, 1 + level.random.nextInt(3),
                    level.random.nextInt(13) - 6);
            if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.above()).isAir()) {
                continue;
            }
            SecurityDroneEntity drone = ModEntities.SECURITY_DRONE.get().create(level);
            if (drone == null) {
                return;
            }
            drone.moveTo(spot.getX() + 0.5, spot.getY() + 0.3, spot.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            drone.setHome(pos);
            level.addFreshEntity(drone);
            setChanged();
            return;
        }
    }

    /** Het gebied dat deze kern beschermt. */
    public AABB area() {
        return new AABB(worldPosition.getX() - SecurityBalance.AREA_RADIUS, worldPosition.getY() - SecurityBalance.AREA_DOWN,
                worldPosition.getZ() - SecurityBalance.AREA_RADIUS, worldPosition.getX() + SecurityBalance.AREA_RADIUS + 1,
                worldPosition.getY() + SecurityBalance.AREA_UP + 1, worldPosition.getZ() + SecurityBalance.AREA_RADIUS + 1);
    }

    // ================================================================ Opslaan

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putString("OwnerName", ownerName);
        tag.putBoolean("Filled", filled);
        tag.putLong("NextDrone", nextDrone);
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        filled = tag.getBoolean("Filled");
        nextDrone = tag.getLong("NextDrone");
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
    }
}
