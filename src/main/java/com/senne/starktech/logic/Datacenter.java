package com.senne.starktech.logic;

import com.senne.starktech.config.StarkConfig;
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
 * Het datacenter van een beveiligingskern: al zijn serverracks samen (jouw wens, 8 oktober: "er moet
 * ook een fan bij komen, die moet je voldoende hebben anders brandt je datacenter af of gaat je
 * hardware kapot").
 *
 * Elk rack heeft zijn eigen temperatuur (ook 8 oktober: "elke server rack maakt zijn eigen
 * temperatuur, dus elke server rack heeft minstens 1 fan nodig"). De CPU's, GPU's, RAM en SSD's
 * werken wel samen als één server (zie LogicServer), maar warm worden ze in hun eigen rack.
 *
 * Hoe het werkt (alle getallen staan in StarkConfig.Logic):
 * <ul>
 *   <li><b>Warmte</b>: elk onderdeel in een rack dat meedraait maakt warmte (watt) in DAT rack. Een
 *       CPU of GPU meer als hij hard werkt. Ook RAM en SSD's maken een beetje warmte.</li>
 *   <li><b>Koeling</b>: elke fan haalt warmte weg, maar alleen in zijn eigen rack. Een fan in het
 *       rack ernaast helpt niet.</li>
 *   <li><b>Zonder fan</b> raakt de warmte er niet uit: zolang er iets in het rack werkt, wordt het
 *       steeds warmer (NO_FAN_HEATING per watt). Dus: elk rack met onderdelen erin heeft minstens één
 *       fan nodig, ook een rack met alleen RAM.</li>
 *   <li><b>Met fans</b>: elke watt die de fans niet wegkoelen, maakt het rack DEGREES_PER_WATT warmer
 *       dan AMBIENT_TEMPERATURE. De temperatuur gaat daar langzaam naartoe (HEAT_RATE), dus je hebt
 *       tijd om fans bij te steken.</li>
 *   <li>Is één rack warmer dan WARM_TEMPERATURE, dan werkt de hele server op halve snelheid (alles
 *       werkt samen, dus het warme rack remt de rest af: je diagrammen reageren trager, het brein
 *       denkt langer) en waarschuwt E.D.I.T.H. de eigenaar.</li>
 *   <li>Vanaf HOT_TEMPERATURE slijten de onderdelen in DAT rack (niet de fans): om de twee seconden
 *       krijgt de helft ervan wat slijtage, hoe heter hoe meer. Bij MAX_WEAR is een onderdeel kapot.</li>
 *   <li>Blijft een rack FIRE_DELAY_TICKS lang boven FIRE_TEMPERATURE, dan vat het vlam (zie
 *       ServerRackBlockEntity.ignite): na BURN_TICKS is het weg, met alles erin.</li>
 *   <li>Gaat er een SSD kapot of verbrandt hij, dan zijn alle gegevens van de server weg: je
 *       diagrammen en keycards staan verspreid over al je SSD's (zoals RAID 0). Een SSD er gewoon
 *       uithalen mag wel.</li>
 * </ul>
 * Elk rack onthoudt zijn eigen temperatuur (zie ServerRackBlockEntity); de kern onthoudt die van het
 * heetste rack, zodat een heet datacenter na het laden van de wereld niet ineens koel is.
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

    /** De temperatuur van het heetste rack: is dat warm, dan werkt de hele server trager. */
    private double temperature = StarkConfig.Logic.AMBIENT_TEMPERATURE;
    private int warned = COOL;
    /**
     * De laatste meting (niet bewaard, na het laden meteen opnieuw), van alle racks samen: warmte nu,
     * warmte als alles hard werkt, koeling. En de hoogste temperatuur waar een rack naartoe gaat.
     */
    private double heat;
    private double fullHeat;
    private double cooling;
    private double target = StarkConfig.Logic.AMBIENT_TEMPERATURE;
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

    /** De temperatuur van het heetste rack. */
    public double temperature() {
        return temperature;
    }

    /** Warmte (watt) die alle racks samen nu maken. */
    public double heat() {
        return heat;
    }

    /** Warmte (watt) van alle racks samen als alle CPU's en GPU's zo hard werken als ze kunnen. */
    public double fullHeat() {
        return fullHeat;
    }

    /** Koeling (watt): alle fans samen. */
    public double cooling() {
        return cooling;
    }

    /**
     * De hoogste temperatuur waar een rack naartoe gaat met de warmte en koeling van nu (oneindig: een
     * rack zonder fan wordt steeds warmer).
     */
    public double target() {
        return target;
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
        return stats.logicPower() / stats.slowdown() * StarkConfig.Logic.LOGIC_STEPS_PER_SECOND;
    }

    private static int percent(double fraction) {
        return (int) Math.round(Math.max(0, Math.min(1, fraction)) * 100);
    }

    /**
     * Eén stap: elk rack apart zijn warmte en koeling meten en zijn temperatuur een stukje opschuiven,
     * zo nodig slijten of vlam vatten, en waarschuwen als het heetste rack warm wordt.
     */
    public void update(ServerLevel level, SecurityCoreBlockEntity core) {
        LogicServer.Stats stats = LogicServer.stats(level, core);
        report(level, core, stats);
        Backups.tick(level, core);
        heat = 0;
        fullHeat = 0;
        cooling = 0;
        target = StarkConfig.Logic.AMBIENT_TEMPERATURE;
        if (stats.racks() == 0) {
            // Geen racks: niets dat warm wordt.
            temperature = StarkConfig.Logic.AMBIENT_TEMPERATURE;
            warned = COOL;
            return;
        }
        double hottest = StarkConfig.Logic.AMBIENT_TEMPERATURE;
        BlockPos hottestPos = null;
        boolean ssdBroke = false;
        java.util.List<BlockPos> racks = new java.util.ArrayList<>(stats.cluster());
        racks.sort(null); // altijd in dezelfde volgorde
        for (BlockPos pos : racks) {
            if (!(level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack)) {
                continue;
            }
            // Alleen een rack met stroom draait mee: dan draaien zijn fans, en rekenen zijn onderdelen
            // als de server draait (de kern heeft stroom en er mist niets). Werken de CPU's en GPU's hard
            // (Logic of het brein), dan worden ze warmer.
            boolean powered = rack.status() == ServerRackBlockEntity.Status.ONLINE;
            double rackHeat = powered && stats.online() ? heat(rack, load()) : 0;
            double rackFullHeat = powered ? heat(rack, 1.0) : 0;
            double rackCooling = powered ? cooling(rack) : 0;
            rack.heatStep(rackHeat, rackFullHeat, rackCooling);
            heat += rackHeat;
            fullHeat += rackFullHeat;
            cooling += rackCooling;
            target = Math.max(target, rack.target());
            if (rack.temperature() >= StarkConfig.Logic.HOT_TEMPERATURE) {
                ssdBroke |= wear(level, core, rack, pos);
            }
            fire(level, core, rack);
            if (hottestPos == null || rack.temperature() > hottest) {
                hottest = rack.temperature();
                hottestPos = pos;
            }
        }
        if (Math.abs(hottest - temperature) > 0.05) {
            core.setChanged();
        }
        temperature = hottest;
        warn(level, core, hottestPos);
        if (ssdBroke) {
            dataLost(level, core);
        }
    }

    /** Warmte (watt) van de onderdelen in dit rack, als de CPU's en GPU's zo hard werken ({@code load} van 0 tot 1). */
    public static double heat(ServerRackBlockEntity rack, double load) {
        return heat(rack.count(ServerPartItem.Kind.CPU), rack.count(ServerPartItem.Kind.GPU),
                rack.count(ServerPartItem.Kind.RAM) + rack.count(ServerPartItem.Kind.SSD), load);
    }

    /** Warmte (watt) van zoveel CPU's, GPU's en RAM-latjes plus SSD's ({@code memory}), bij deze {@code load}. */
    public static double heat(int cpus, int gpus, int memory, double load) {
        return cpus * (StarkConfig.Logic.CPU_HEAT + load * StarkConfig.Logic.CPU_BUSY_HEAT)
                + gpus * (StarkConfig.Logic.GPU_HEAT + load * StarkConfig.Logic.GPU_BUSY_HEAT)
                + memory * StarkConfig.Logic.MEMORY_HEAT;
    }

    /** Koeling (watt) van een rack: zijn eigen fans. */
    public static double cooling(ServerRackBlockEntity rack) {
        return rack.count(ServerPartItem.Kind.FAN) * StarkConfig.Logic.FAN_COOLING;
    }

    /** Zonder fan wordt een rack nooit warmer dan dit (dan staat het allang in brand). */
    private static double ceiling() {
        return StarkConfig.Logic.FIRE_TEMPERATURE + 30.0;
    }

    /**
     * Waar de temperatuur van een rack naartoe gaat: elke watt die de fans niet wegkoelen maakt het
     * DEGREES_PER_WATT warmer. Maakt het warmte maar koelt niets (geen fan): oneindig, het wordt
     * steeds warmer.
     */
    public static double target(double heat, double cooling) {
        if (heat > 0 && cooling <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return StarkConfig.Logic.AMBIENT_TEMPERATURE + Math.max(0, heat - cooling) * StarkConfig.Logic.DEGREES_PER_WATT;
    }

    /**
     * De temperatuur van een rack na één stap (UPDATE_TICKS). Zonder fan komt er elke stap
     * NO_FAN_HEATING graden per watt bij; met fans schuift hij HEAT_RATE op naar waar hij heen gaat.
     */
    public static double step(double temperature, double heat, double cooling) {
        if (heat > 0 && cooling <= 0) {
            double warmer = temperature + heat * StarkConfig.Logic.NO_FAN_HEATING;
            return Math.max(temperature, Math.min(warmer, ceiling()));
        }
        return temperature + (target(heat, cooling) - temperature) * StarkConfig.Logic.HEAT_RATE;
    }

    /**
     * Zoveel fans moeten er nog in dit rack zodat het ook als alles zo hard mogelijk werkt onder "weer
     * koel" blijft (een paar graden onder WARM_TEMPERATURE). Maakt het rack warmte en heeft het geen
     * fan, dan minstens één. 0 = genoeg.
     */
    public static int fansNeeded(double fullHeat, double cooling) {
        if (fullHeat <= 0 || StarkConfig.Logic.FAN_COOLING <= 0) {
            return 0;
        }
        int fans = cooling <= 0 ? 1 : 0;
        double allowed = (LogicStats.coolAgainTemperature() - StarkConfig.Logic.AMBIENT_TEMPERATURE) / StarkConfig.Logic.DEGREES_PER_WATT;
        double missing = fullHeat - cooling - fans * StarkConfig.Logic.FAN_COOLING - allowed;
        if (missing > 0) {
            fans += (int) Math.ceil(missing / StarkConfig.Logic.FAN_COOLING - 1e-9);
        }
        return fans;
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

    /**
     * Dit rack is te heet: de helft van zijn onderdelen slijt een stukje (hoe heter, hoe meer). Kapot =
     * weg. Geeft true als er een SSD kapot ging.
     */
    private static boolean wear(ServerLevel level, SecurityCoreBlockEntity core, ServerRackBlockEntity rack, BlockPos pos) {
        int amount = 1 + (int) Math.max(0, (rack.temperature() - StarkConfig.Logic.HOT_TEMPERATURE) / 10.0);
        boolean ssdBroke = false;
        for (int slot = 0; slot < rack.parts().getContainerSize(); slot++) {
            ItemStack stack = rack.parts().getItem(slot);
            if (!(stack.getItem() instanceof ServerPartItem part) || part.kind() == ServerPartItem.Kind.FAN
                    || level.random.nextFloat() >= 0.5f) {
                continue;
            }
            int wear = ServerPartItem.wear(stack) + amount;
            if (wear < StarkConfig.Logic.MAX_WEAR) {
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
        return ssdBroke;
    }

    /** Is dit rack lang genoeg boven FIRE_TEMPERATURE, dan vat het vlam. */
    private static void fire(ServerLevel level, SecurityCoreBlockEntity core, ServerRackBlockEntity rack) {
        if (rack.isBurning() || rack.temperature() < StarkConfig.Logic.FIRE_TEMPERATURE) {
            rack.setFireTicks(0);
            return;
        }
        int ticks = rack.fireTicks() + UPDATE_TICKS;
        if (ticks < StarkConfig.Logic.FIRE_DELAY_TICKS) {
            rack.setFireTicks(ticks);
            return;
        }
        rack.setFireTicks(0);
        rack.ignite(level, level.getGameTime() + StarkConfig.Logic.BURN_TICKS);
        core.logEvent(EventLog.Type.ALARM, "A server rack is on fire! (" + where(rack.getBlockPos()) + ")");
        tellOwner(level, core, Component.translatable("logic.starktech.heat.fire", core.hostname(),
                Math.max(1, StarkConfig.Logic.BURN_TICKS / 20)), EdithMessagePayload.Level.ALERT);
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

    /**
     * Het heetste rack is warmer dan een grens: één keer zeggen (logboek en E.D.I.T.H.), met waar dat
     * rack staat. Weer koel: dat ook één keer.
     */
    private void warn(ServerLevel level, SecurityCoreBlockEntity core, @Nullable BlockPos hottest) {
        int now = temperature >= StarkConfig.Logic.HOT_TEMPERATURE ? HOT : temperature >= StarkConfig.Logic.WARM_TEMPERATURE ? WARM : COOL;
        String degrees = degrees(temperature);
        String where = hottest == null ? "?" : where(hottest);
        if (now > warned) {
            warned = now;
            if (now == HOT) {
                core.logEvent(EventLog.Type.HEAT, "Rack at " + where + " overheating (" + degrees + "): parts are wearing out");
                tellOwner(level, core, Component.translatable("logic.starktech.heat.hot", core.hostname(), degrees, where),
                        EdithMessagePayload.Level.ALERT);
            } else {
                core.logEvent(EventLog.Type.HEAT, "Rack at " + where + " running hot (" + degrees + "): half speed");
                tellOwner(level, core, Component.translatable("logic.starktech.heat.warm", core.hostname(), degrees, where),
                        EdithMessagePayload.Level.WARNING);
            }
            core.setChanged();
        } else if (warned > COOL && temperature < LogicStats.coolAgainTemperature()) {
            warned = COOL;
            core.logEvent(EventLog.Type.HEAT, "Datacenter cool again (" + degrees + ")");
            tellOwner(level, core, Component.translatable("logic.starktech.heat.cool", core.hostname(), degrees),
                    EdithMessagePayload.Level.INFO);
            core.setChanged();
        } else if (warned == HOT && now == WARM && temperature < StarkConfig.Logic.HOT_TEMPERATURE - 5.0) {
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

    /** Waar een rack staat, zoals in de app Monitor: "12 64 -30". */
    static String where(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
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
        datacenter.backupTarget = Datacenters.fromKey(tag.getString("BackupTo"));
        datacenter.backupMinutes = Math.max(0, tag.getInt("BackupMin"));
        datacenter.lastBackup = tag.getLong("BackupLast");
        return datacenter;
    }
}
