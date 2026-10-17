package com.senne.imttech.logic;

import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.redstone.ImtRelays;
import com.senne.imttech.redstone.Relay;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * De apparaten die een diagram bij naam kan noemen.
 *
 * <b>Uitgangen</b> (wat een diagram schakelt: {@code on("Vault door")}): de relais, laserzenders,
 * koepels, torentjes en schakelaars van de stroomkast die de EIGENAAR van de kern in zijn
 * redstone-lijst heeft (de namen die hij ze met zijn bril gaf), voor zover ze in het gebied van de
 * kern staan. Die lijst hoort bij de speler; daarom neemt de kern hem over in zijn eigen
 * "telefoonboek" (LogicStore) zolang de eigenaar online is. Zo werken je diagrammen ook als jij er
 * niet bent. Ook je groepen staan erin (14 oktober): {@code on("base")} zet alles in "base" aan, ook wat
 * in groepen eronder zit ("security room").
 *
 * <b>Ingangen</b> (wat een diagram start: {@code when keycard("Lab door"):}): de keycardlezers en
 * codepanelen in het gebied, met de naam die in het blok zelf staat.
 */
public final class LogicDevices {
    private LogicDevices() {}

    /**
     * Neemt de namen over uit de redstone-lijst van de eigenaar (alleen apparaten in het gebied van
     * deze kern, in deze wereld). Een apparaat dat de nieuwe eigenaar (na een overname) niet in zijn
     * lijst heeft, maar dat er nog wel staat, houdt zijn oude naam: zo blijven je diagrammen werken.
     */
    public static void refresh(ServerLevel level, SecurityCoreBlockEntity core, ServerPlayer owner) {
        if (!owner.getUUID().equals(core.ownerId())) {
            return;
        }
        Map<String, List<LogicStore.Target>> fresh = new LinkedHashMap<>();
        ImtRelays relays = owner.getData(ModAttachments.RELAYS);
        for (Relay relay : relays.relays()) {
            if (relay.dimension() == level.dimension() && SecurityCores.ownedBy(level, core, relay.pos())) {
                add(fresh, relay.name(), relay);
            }
        }
        // Groepen: alles wat erin zit (ook via groepen eronder), voor zover het in het gebied staat.
        for (String group : relays.groups()) {
            for (Relay relay : relays.inGroup(group)) {
                if (relay.dimension() == level.dimension() && SecurityCores.ownedBy(level, core, relay.pos())) {
                    add(fresh, group, relay);
                }
            }
        }
        // Oude namen van apparaten die er nog staan maar niet in deze lijst zitten, blijven.
        for (String name : core.logic().deviceNames()) {
            if (fresh.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name))) {
                continue;
            }
            for (LogicStore.Target target : core.logic().devices(name)) {
                boolean listed = fresh.values().stream().anyMatch(list -> list.contains(target));
                if (!listed && level.isLoaded(target.pos()) && ImtRedstone.isOnAt(level, target.pos(), target.channel()) != null) {
                    fresh.computeIfAbsent(name, key -> new ArrayList<>()).add(target);
                }
            }
        }
        if (core.logic().setDevices(fresh)) {
            core.setChanged();
        }
    }

    /**
     * Zet een apparaat onder deze naam in het telefoonboek. Hoofdletters maken niet uit: heet een relais
     * "Base" en heb je ook een groep "base", dan komen ze samen onder één naam (anders zou de ene de andere
     * wegdrukken).
     */
    private static void add(Map<String, List<LogicStore.Target>> book, String name, Relay relay) {
        String key = book.keySet().stream().filter(own -> own.equalsIgnoreCase(name)).findFirst().orElse(name);
        List<LogicStore.Target> list = book.computeIfAbsent(key, k -> new ArrayList<>());
        LogicStore.Target target = new LogicStore.Target(relay.pos().immutable(), relay.channel());
        if (!list.contains(target)) {
            list.add(target);
        }
    }

    /** Ververst het telefoonboek als de eigenaar van deze kern online is (in dezelfde wereld). */
    public static void refreshIfOwnerOnline(ServerLevel level, SecurityCoreBlockEntity core) {
        if (core.ownerId() == null || level.getServer() == null) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(core.ownerId());
        if (owner != null) {
            refresh(level, core, owner);
        }
    }

    /**
     * Schakelt alle apparaten met deze naam. Geeft terug hoeveel het er waren (0 = geen apparaat met
     * die naam, of ze staan er niet meer).
     */
    public static int switchByName(ServerLevel level, SecurityCoreBlockEntity core, String name, ImtRedstone.Op op, int pulseTicks) {
        int done = 0;
        for (LogicStore.Target target : core.logic().devices(name)) {
            if (ImtRedstone.switchAt(level, target.pos(), target.channel(), op, pulseTicks)) {
                done++;
            }
        }
        return done;
    }

    /** Staat (minstens één van) het apparaat met deze naam aan? */
    public static boolean isOn(ServerLevel level, SecurityCoreBlockEntity core, String name) {
        for (LogicStore.Target target : core.logic().devices(name)) {
            if (level.isLoaded(target.pos()) && Boolean.TRUE.equals(ImtRedstone.isOnAt(level, target.pos(), target.channel()))) {
                return true;
            }
        }
        return false;
    }

    /** Een keycardlezer of codepaneel in het gebied, met zijn naam en soort. */
    public record Input(String name, boolean panel, BlockPos pos) {}

    /** Alle keycardlezers en codepanelen in het gebied van deze kern. */
    public static List<Input> inputs(ServerLevel level, SecurityCoreBlockEntity core) {
        List<Input> inputs = new ArrayList<>();
        for (BlockPos pos : SecurityDevices.scan(level, core).inputs()) {
            if (level.getBlockEntity(pos) instanceof InputDeviceBlockEntity device) {
                inputs.add(new Input(device.name(), level.getBlockState(pos).getBlock() instanceof CodePanelBlock, pos));
            }
        }
        return inputs;
    }
}
