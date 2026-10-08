package com.senne.starktech.logic;

import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.os.EventLog;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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
 * </ul>
 * De kern bewaart dit (zie SecurityCoreBlockEntity), zodat een heet datacenter na het laden van
 * de wereld niet ineens koel is.
 */
public final class Datacenter {
    /** Om de zoveel ticks rekenen we opnieuw (net zo vaak als de racks kijken of ze stroom hebben). */
    public static final int UPDATE_TICKS = 40;

    /** Hoe ver E.D.I.T.H. al waarschuwde: 0 = koel, 1 = warm, 2 = heet. */
    private static final int COOL = 0, WARM = 1, HOT = 2;

    private double temperature = LogicBalance.AMBIENT_TEMPERATURE;
    private int warned = COOL;
    /** De laatste meting (niet bewaard, na het laden meteen opnieuw): warmte nu, warmte als alles hard werkt, koeling. */
    private double heat;
    private double fullHeat;
    private double cooling;

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

    /** Voor de tests: meteen deze temperatuur. */
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    // ================================================================ Rekenen

    /** Elke tick, vanuit de kern. Om de UPDATE_TICKS ticks rekenen we. */
    public void tick(ServerLevel level, SecurityCoreBlockEntity core) {
        if ((level.getGameTime() + core.getBlockPos().hashCode()) % UPDATE_TICKS == 0) {
            update(level, core);
        }
    }

    /** Eén stap: warmte en koeling meten, de temperatuur een stukje opschuiven, en zo nodig waarschuwen. */
    public void update(ServerLevel level, SecurityCoreBlockEntity core) {
        LogicServer.Stats stats = LogicServer.stats(level, core);
        if (stats.racks() == 0) {
            // Geen racks: niets dat warm wordt.
            heat = 0;
            fullHeat = 0;
            cooling = 0;
            temperature = LogicBalance.AMBIENT_TEMPERATURE;
            warned = COOL;
            return;
        }
        double load = 0.0; // hoe hard de CPU's en GPU's werken (0..1); komt in stap 4
        // Draait de server niet (de kern zonder stroom, of iets mist), dan rekent er niets.
        heat = stats.online() ? heat(stats, load) : 0;
        fullHeat = heat(stats, 1.0);
        cooling = cooling(stats);
        double before = temperature;
        temperature += (target(heat, cooling) - temperature) * LogicBalance.HEAT_RATE;
        if (Math.abs(temperature - before) > 0.05) {
            core.setChanged();
        }
        warn(level, core);
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
        return tag;
    }

    public static Datacenter load(CompoundTag tag) {
        Datacenter datacenter = new Datacenter();
        if (tag.contains("T")) {
            datacenter.temperature = tag.getDouble("T");
        }
        datacenter.warned = Math.max(COOL, Math.min(HOT, tag.getInt("W")));
        return datacenter;
    }
}
