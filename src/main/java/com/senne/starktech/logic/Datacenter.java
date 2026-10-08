package com.senne.starktech.logic;

import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.os.EventLog;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Het datacenter van een beveiligingskern: al zijn serverracks samen, als één ruimte met één
 * temperatuur (jouw wens, 8 oktober: "er moet ook een fan bij komen, die moet je voldoende hebben
 * anders brandt je datacenter af of gaat je hardware kapot").
 *
 * Hoe het werkt (alle getallen staan in LogicBalance):
 * <ul>
 *   <li><b>Warmte</b>: elk onderdeel in een rack dat meedraait maakt warmte (watt). Een CPU of GPU
 *       meer als hij hard werkt.</li>
 *   <li><b>Koeling</b>: elke fan haalt warmte weg, en elk rack zelf een beetje (lucht).</li>
 *   <li><b>Temperatuur</b>: elke watt die niet weggekoeld wordt, maakt het DEGREES_PER_WATT warmer
 *       dan AMBIENT_TEMPERATURE. De temperatuur gaat daar langzaam naartoe (HEAT_RATE), dus je hebt
 *       tijd om fans bij te steken.</li>
 *   <li>Vanaf WARM_TEMPERATURE werkt alles op halve snelheid (je diagrammen reageren trager, het
 *       brein denkt langer) en waarschuwt E.D.I.T.H. de eigenaar.</li>
 *   <li>Vanaf HOT_TEMPERATURE slijten de onderdelen (niet de fans): om de twee seconden krijgt de
 *       helft ervan wat slijtage, hoe heter hoe meer. Bij MAX_WEAR is een onderdeel kapot.</li>
 *   <li>Blijft het FIRE_DELAY_TICKS lang boven FIRE_TEMPERATURE, dan vat het heetste rack vlam (zie
 *       ServerRackBlockEntity.ignite): na BURN_TICKS is het weg, met alles erin.</li>
 *   <li>Gaat er een SSD kapot of verbrandt hij, dan zijn alle gegevens van de server weg: je
 *       diagrammen en keycards staan verspreid over al je SSD's (zoals RAID 0). Een SSD er gewoon
 *       uithalen mag wel.</li>
 * </ul>
 * De kern bewaart dit (zie SecurityCoreBlockEntity), zodat een heet datacenter na het laden van
 * de wereld niet ineens koel is.
 */
public final class Datacenter {
    /** Om de zoveel ticks rekenen we opnieuw (net zo vaak als de racks kijken of ze stroom hebben). */
    public static final int UPDATE_TICKS = 40;
    /** Om de zoveel ticks meten we hoe hard alles werkt (voor de app Monitor). */
    public static final int SAMPLE_TICKS = 20;
    /** Zoveel metingen onthouden we: de laatste minuut. */
    public static final int HISTORY = 60;

    /** Hoe ver E.D.I.T.H. al waarschuwde: 0 = koel, 1 = warm, 2 = heet. */
    private static final int COOL = 0, WARM = 1, HOT = 2;

    private double temperature = LogicBalance.AMBIENT_TEMPERATURE;
    private int warned = COOL;
    /** Hoe lang (ticks) het al aan één stuk boven FIRE_TEMPERATURE is. */
    private int fireTicks;
    /** De laatste meting (niet bewaard, na het laden meteen opnieuw): warmte nu, warmte als alles hard werkt, koeling. */
    private double heat;
    private double fullHeat;
    private double cooling;
    /**
     * Hoe hard alles werkt (0..1), de laatste meting: de CPU's voor Logic, het brein van E.D.I.T.H.
     * en het RAM (diagrammen tegelijk). Niet bewaard.
     */
    private double cpuLoad;
    private double brainLoad;
    private double ramLoad;
    /** De laatste minuut, één meting per seconde (de oudste eerst): {cpu %, brein %, ram %, temperatuur}. */
    private final java.util.ArrayDeque<int[]> history = new java.util.ArrayDeque<>();
    /** Backups (zie Backups): naar welk datacenter, om de hoeveel minuten (0 = niet vanzelf), en wanneer de laatste. */
    private @Nullable GlobalPos backupTarget;
    private int backupMinutes;
    private long lastBackup;
    /** Waarom de laatste automatische backup niet lukte ("" = hij lukte); zo zeggen we het maar één keer. */
    private String autoError = "";
    /** Lukte een automatische backup niet, dan proberen we het pas om deze tijd weer (niet bewaard). */
    private long retryAt;

    public double temperature() {
        return temperature;
    }

    /** Warmte (watt) die het datacenter nu maakt. */
    public double heat() {
        return heat;
    }

    /** Warmte (watt) als alle CPU's en GPU's zo hard werken als ze kunnen. */
    public double fullHeat() {
        return fullHeat;
    }

    /** Koeling (watt): fans en de racks zelf. */
    public double cooling() {
        return cooling;
    }

    /** Waar de temperatuur naartoe gaat met de warmte en koeling van nu. */
    public double target() {
        return target(heat, cooling);
    }

    /** Hoe hard de CPU's werken voor Logic (0..1), de laatste meting. */
    public double cpuLoad() {
        return cpuLoad;
    }

    /** Hoe hard het brein van E.D.I.T.H. hier werkt (0..1), de laatste meting. */
    public double brainLoad() {
        return brainLoad;
    }

    /** Hoe vol het RAM is (0..1): diagrammen die bezig zijn of wachten, tegen wat er tegelijk kan. */
    public double ramLoad() {
        return ramLoad;
    }

    /**
     * Hoe hard de CPU's en GPU's werken (0..1) voor de warmte: het drukste van Logic en het brein
     * (ze doen het allebei op dezelfde chips).
     */
    public double load() {
        return Math.min(1.0, Math.max(cpuLoad, brainLoad));
    }

    /** De laatste minuut: per meting {cpu %, brein %, ram %, temperatuur}, de oudste eerst. */
    public java.util.List<int[]> history() {
        return java.util.List.copyOf(history);
    }

    public @Nullable GlobalPos backupTarget() {
        return backupTarget;
    }

    public void setBackupTarget(@Nullable GlobalPos target) {
        backupTarget = target;
        autoError = "";
        retryAt = 0;
    }

    public int backupMinutes() {
        return backupMinutes;
    }

    public void setBackupMinutes(int minutes) {
        backupMinutes = Math.max(0, minutes);
        autoError = "";
        retryAt = 0;
    }

    /** Wanneer (speltijd) de laatste backup was; 0 = nog nooit. */
    public long lastBackup() {
        return lastBackup;
    }

    void backedUp(long time) {
        lastBackup = time;
    }

    String autoError() {
        return autoError;
    }

    void setAutoError(String error) {
        autoError = error;
    }

    long retryAt() {
        return retryAt;
    }

    void setRetryAt(long time) {
        retryAt = time;
    }

    /** Voor de tests: meteen deze temperatuur. */
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    // ================================================================ Rekenen

    /** Elke tick, vanuit de kern. Om de SAMPLE_TICKS ticks meten we, om de UPDATE_TICKS ticks rekenen we. */
    public void tick(ServerLevel level, SecurityCoreBlockEntity core) {
        long phase = level.getGameTime() + core.getBlockPos().hashCode();
        if (phase % SAMPLE_TICKS == 0) {
            sample(level, core);
        }
        if (phase % UPDATE_TICKS == 0) {
            update(level, core);
        }
    }

    /**
     * Eén meting (elke seconde): hoeveel werk de CPU's deden tegen wat ze aankunnen, hoe vol het RAM
     * is, en de temperatuur. Gaat in de geschiedenis voor de app Monitor.
     */
    public void sample(ServerLevel level, SecurityCoreBlockEntity core) {
        LogicServer.Stats stats = LogicServer.stats(level, core);
        int steps = LogicRunner.takeSteps(level, core);
        double perSecond = steps * 20.0 / SAMPLE_TICKS;
        double capacity = stats.online() ? logicCapacity(stats) : 0;
        cpuLoad = capacity > 0 ? Math.min(1.0, perSecond / capacity) : 0;
        int[] load = LogicRunner.load(level, core);
        ramLoad = stats.online() && stats.maxRuns() > 0 ? Math.min(1.0, (load[0] + load[1]) / (double) stats.maxRuns()) : 0;
        // Denkt het brein van E.D.I.T.H. hier nu na (voor iemand)? Dan werken de chips op vol.
        brainLoad = DatacenterBrain.thinkingOn(level, GlobalPos.of(level.dimension(), core.getBlockPos())).isEmpty() ? 0.0 : 1.0;
        history.addLast(new int[] {percent(cpuLoad), percent(brainLoad), percent(ramLoad), (int) Math.round(temperature)});
        while (history.size() > HISTORY) {
            history.removeFirst();
        }
    }

    /** Hoeveel blokjes per seconde de CPU's en GPU's van deze server aankunnen (warm: minder). */
    public static double logicCapacity(LogicServer.Stats stats) {
        return stats.logicPower() / stats.slowdown() * LogicBalance.LOGIC_STEPS_PER_SECOND;
    }

    private static int percent(double fraction) {
        return (int) Math.round(Math.max(0, Math.min(1, fraction)) * 100);
    }

    /** Eén stap: warmte en koeling meten, de temperatuur een stukje opschuiven, en zo nodig waarschuwen. */
    public void update(ServerLevel level, SecurityCoreBlockEntity core) {
        LogicServer.Stats stats = LogicServer.stats(level, core);
        report(level, core, stats);
        Backups.tick(level, core);
        if (stats.racks() == 0) {
            // Geen racks: niets dat warm wordt.
            heat = 0;
            fullHeat = 0;
            cooling = 0;
            temperature = LogicBalance.AMBIENT_TEMPERATURE;
            warned = COOL;
            fireTicks = 0;
            return;
        }
        // Draait de server niet (de kern zonder stroom, of iets mist), dan rekent er niets. Werken de
        // CPU's en GPU's hard (Logic of het brein), dan worden ze warmer.
        heat = stats.online() ? heat(stats, load()) : 0;
        fullHeat = heat(stats, 1.0);
        cooling = cooling(stats);
        double before = temperature;
        temperature += (target(heat, cooling) - temperature) * LogicBalance.HEAT_RATE;
        if (Math.abs(temperature - before) > 0.05) {
            core.setChanged();
        }
        warn(level, core);
        if (temperature >= LogicBalance.HOT_TEMPERATURE) {
            wear(level, core, stats);
        }
        fire(level, core, stats);
    }

    /** Warmte (watt) van deze onderdelen, als de CPU's en GPU's zo hard werken ({@code load} van 0 tot 1). */
    public static double heat(LogicServer.Stats stats, double load) {
        return stats.cpus() * (LogicBalance.CPU_HEAT + load * LogicBalance.CPU_BUSY_HEAT)
                + stats.gpus() * (LogicBalance.GPU_HEAT + load * LogicBalance.GPU_BUSY_HEAT)
                + (stats.ram() + stats.ssd()) * LogicBalance.MEMORY_HEAT;
    }

    /** Koeling (watt): de fans in racks die meedraaien, en elk rack van de server zelf. */
    public static double cooling(LogicServer.Stats stats) {
        return stats.fans() * LogicBalance.FAN_COOLING + stats.racks() * LogicBalance.RACK_COOLING;
    }

    /** De temperatuur waar het naartoe gaat: elke watt te veel maakt het DEGREES_PER_WATT warmer. */
    public static double target(double heat, double cooling) {
        return LogicBalance.AMBIENT_TEMPERATURE + Math.max(0, heat - cooling) * LogicBalance.DEGREES_PER_WATT;
    }

    /**
     * Zoveel fans moeten er nog bij zodat het ook als alles zo hard mogelijk werkt onder "weer koel"
     * blijft (een paar graden onder WARM_TEMPERATURE). 0 = genoeg.
     */
    public static int fansNeeded(double fullHeat, double cooling) {
        double allowed = (LogicBalance.coolAgainTemperature() - LogicBalance.AMBIENT_TEMPERATURE) / LogicBalance.DEGREES_PER_WATT;
        double missing = fullHeat - cooling - allowed;
        if (missing <= 0 || LogicBalance.FAN_COOLING <= 0) {
            return 0;
        }
        return (int) Math.ceil(missing / LogicBalance.FAN_COOLING - 1e-9);
    }

    /**
     * Vertelt de wereld (Datacenters) hoe dit datacenter ervoor staat: zo kun je er een backup
     * naartoe sturen en weet het brein van E.D.I.T.H. hoe sterk het is, ook als zijn chunk straks niet
     * geladen is. Een kern zonder eigenaar is niemands datacenter.
     */
    private static void report(ServerLevel level, SecurityCoreBlockEntity core, LogicServer.Stats stats) {
        Datacenters datacenters = Datacenters.get(level);
        GlobalPos here = GlobalPos.of(level.dimension(), core.getBlockPos());
        if (core.ownerId() == null) {
            datacenters.unreport(here);
            return;
        }
        datacenters.report(here, new Datacenters.Summary(core.ownerId(), core.hostname(), core.ip(), stats.online(),
                stats.capacity(), core.logic().blocks(), stats.brainDelayTicks()));
    }

    // ================================================================ Slijten en brand

    /** Te heet: de helft van de onderdelen slijt een stukje (hoe heter, hoe meer). Kapot = weg. */
    private void wear(ServerLevel level, SecurityCoreBlockEntity core, LogicServer.Stats stats) {
        int amount = 1 + (int) Math.max(0, (temperature - LogicBalance.HOT_TEMPERATURE) / 10.0);
        boolean ssdBroke = false;
        for (BlockPos pos : stats.cluster()) {
            if (!(level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack)) {
                continue;
            }
            for (int slot = 0; slot < rack.parts().getContainerSize(); slot++) {
                ItemStack stack = rack.parts().getItem(slot);
                if (!(stack.getItem() instanceof ServerPartItem part) || part.kind() == ServerPartItem.Kind.FAN
                        || level.random.nextFloat() >= 0.5f) {
                    continue;
                }
                int wear = ServerPartItem.wear(stack) + amount;
                if (wear < LogicBalance.MAX_WEAR) {
                    ServerPartItem.setWear(stack, wear);
                    rack.setChanged();
                    continue;
                }
                // Kapot: rook en een knak, en weg.
                rack.parts().setItem(slot, ItemStack.EMPTY);
                level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.02);
                level.playSound(null, pos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
                core.logEvent(EventLog.Type.HEAT, "A " + part.kind().name() + " burned out (too hot)");
                tellOwner(level, core, Component.translatable("logic.starktech.heat.broke", part.kind().name(), core.hostname()),
                        EdithMessagePayload.Level.WARNING);
                ssdBroke |= part.kind() == ServerPartItem.Kind.SSD;
            }
        }
        if (ssdBroke) {
            dataLost(level, core);
        }
    }

    /** Lang genoeg boven FIRE_TEMPERATURE: het heetste rack vat vlam. */
    private void fire(ServerLevel level, SecurityCoreBlockEntity core, LogicServer.Stats stats) {
        if (temperature < LogicBalance.FIRE_TEMPERATURE) {
            fireTicks = 0;
            return;
        }
        fireTicks += UPDATE_TICKS;
        if (fireTicks < LogicBalance.FIRE_DELAY_TICKS) {
            return;
        }
        fireTicks = 0;
        ServerRackBlockEntity rack = hottestRack(level, stats);
        if (rack == null) {
            return;
        }
        rack.ignite(level, level.getGameTime() + LogicBalance.BURN_TICKS);
        core.logEvent(EventLog.Type.ALARM, "A server rack is on fire!");
        tellOwner(level, core, Component.translatable("logic.starktech.heat.fire", core.hostname(),
                Math.max(1, LogicBalance.BURN_TICKS / 20)), EdithMessagePayload.Level.ALERT);
    }

    /**
     * Het rack dat de meeste warmte maakt (als alles hard werkt) en nog niet brandt; bij gelijke
     * warmte het laagste. Null als geen enkel rack warmte maakt.
     */
    static @Nullable ServerRackBlockEntity hottestRack(ServerLevel level, LogicServer.Stats stats) {
        ServerRackBlockEntity best = null;
        double bestHeat = 0;
        for (BlockPos pos : stats.cluster()) {
            if (!(level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) || rack.isBurning()) {
                continue;
            }
            double heat = rack.count(ServerPartItem.Kind.CPU) * (LogicBalance.CPU_HEAT + LogicBalance.CPU_BUSY_HEAT)
                    + rack.count(ServerPartItem.Kind.GPU) * (LogicBalance.GPU_HEAT + LogicBalance.GPU_BUSY_HEAT)
                    + (rack.count(ServerPartItem.Kind.RAM) + rack.count(ServerPartItem.Kind.SSD)) * LogicBalance.MEMORY_HEAT;
            if (heat > bestHeat || heat == bestHeat && heat > 0 && best != null && pos.compareTo(best.getBlockPos()) < 0) {
                best = rack;
                bestHeat = heat;
            }
        }
        return best;
    }

    /** Een rack is afgebrand (ServerRackBlockEntity.burnDown). Zat er een SSD in, dan zijn de gegevens weg. */
    public void rackBurned(ServerLevel level, SecurityCoreBlockEntity core, boolean hadSsd) {
        core.logEvent(EventLog.Type.ALARM, "A server rack burned down");
        tellOwner(level, core, Component.translatable("logic.starktech.heat.burned", core.hostname()), EdithMessagePayload.Level.ALERT);
        LogicServer.forgetAll();
        if (hadSsd) {
            dataLost(level, core);
        }
    }

    /**
     * Een SSD is kapot of verbrand: alle diagrammen en keycards van deze server zijn weg, en ook de
     * backups van andere netwerken die hier stonden.
     */
    private static void dataLost(ServerLevel level, SecurityCoreBlockEntity core) {
        core.logic().wipe();
        LogicRunner.stop(level, core.getBlockPos());
        core.setChanged();
        core.logEvent(EventLog.Type.ALARM, "An SSD burned: all diagrams and keycards on this server are gone");
        int backups = Datacenters.get(level).wipeStoredAt(GlobalPos.of(level.dimension(), core.getBlockPos()));
        if (backups > 0) {
            core.logEvent(EventLog.Type.ALARM, backups + " backup" + (backups == 1 ? "" : "s") + " stored here burned too");
        }
        tellOwner(level, core, Component.translatable("logic.starktech.heat.data_lost", core.hostname()), EdithMessagePayload.Level.ALERT);
    }

    // ================================================================ Waarschuwen

    /** Warmer dan een grens: één keer zeggen (logboek en E.D.I.T.H.). Weer koel: dat ook één keer. */
    private void warn(ServerLevel level, SecurityCoreBlockEntity core) {
        int now = temperature >= LogicBalance.HOT_TEMPERATURE ? HOT : temperature >= LogicBalance.WARM_TEMPERATURE ? WARM : COOL;
        String degrees = degrees(temperature);
        if (now > warned) {
            warned = now;
            if (now == HOT) {
                core.logEvent(EventLog.Type.HEAT, "Datacenter overheating (" + degrees + "): parts are wearing out");
                tellOwner(level, core, Component.translatable("logic.starktech.heat.hot", core.hostname(), degrees),
                        EdithMessagePayload.Level.ALERT);
            } else {
                core.logEvent(EventLog.Type.HEAT, "Datacenter running hot (" + degrees + "): half speed");
                tellOwner(level, core, Component.translatable("logic.starktech.heat.warm", core.hostname(), degrees),
                        EdithMessagePayload.Level.WARNING);
            }
            core.setChanged();
        } else if (warned > COOL && temperature < LogicBalance.coolAgainTemperature()) {
            warned = COOL;
            core.logEvent(EventLog.Type.HEAT, "Datacenter cool again (" + degrees + ")");
            tellOwner(level, core, Component.translatable("logic.starktech.heat.cool", core.hostname(), degrees),
                    EdithMessagePayload.Level.INFO);
            core.setChanged();
        } else if (warned == HOT && now == WARM && temperature < LogicBalance.HOT_TEMPERATURE - 5.0) {
            warned = WARM; // van heet terug naar warm: geen bericht, maar wordt het weer heet, dan wel
        }
    }

    private static void tellOwner(ServerLevel level, SecurityCoreBlockEntity core, Component message, EdithMessagePayload.Level type) {
        if (core.ownerId() == null || level.getServer() == null) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(core.ownerId());
        if (owner != null) {
            EdithServer.send(owner, message, type);
        }
    }

    /** 72.4 wordt "72°". */
    public static String degrees(double temperature) {
        return String.format(Locale.ROOT, "%d°", Math.round(temperature));
    }

    // ================================================================ Opslaan

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("T", temperature);
        tag.putInt("W", warned);
        tag.putInt("F", fireTicks);
        if (backupTarget != null) {
            tag.putString("BackupTo", Datacenters.key(backupTarget));
        }
        tag.putInt("BackupMin", backupMinutes);
        tag.putLong("BackupLast", lastBackup);
        return tag;
    }

    public static Datacenter load(CompoundTag tag) {
        Datacenter datacenter = new Datacenter();
        if (tag.contains("T")) {
            datacenter.temperature = tag.getDouble("T");
        }
        datacenter.warned = Math.max(COOL, Math.min(HOT, tag.getInt("W")));
        datacenter.fireTicks = Math.max(0, tag.getInt("F"));
        datacenter.backupTarget = Datacenters.fromKey(tag.getString("BackupTo"));
        datacenter.backupMinutes = Math.max(0, tag.getInt("BackupMin"));
        datacenter.lastBackup = tag.getLong("BackupLast");
        return datacenter;
    }
}
