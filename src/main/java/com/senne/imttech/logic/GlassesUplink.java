package com.senne.imttech.logic;

import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.network.UplinkReplyPayload;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.net.CoreNetworkData;
import com.senne.imttech.security.os.EventLog;
import com.senne.imttech.security.os.SecurityOs;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Je bril verbinden met het datacenter van een security core (hoofdstuk 122, jouw wens van 15 november: "zorg dat het
 * datacenter voor je bril afhankelijk is van de security core waar je aanhangt, of owner of trusted; je moet je bril
 * connecten via het ip van je security core met password voor dat die bril aan de serverroom hangt").
 *
 * Zo gaat het:
 * <ul>
 *   <li>In het G-menu, tabblad Tech, kies je Datacenter. Daar typ je het IP van een kern (dat staat in zijn app Status) en
 *       zijn wachtwoord, net als met de Hacking Laptop of de I.M.T Phone.</li>
 *   <li>De server kijkt het na zoals bij de laptop ({@link SecurityOs#connect}): de kern moet in dezelfde dimensie staan,
 *       stroom hebben en een wachtwoord; een fout wachtwoord komt in zijn logboek, de eigenaar hoort het, en na te veel
 *       keer moet je even wachten.</li>
 *   <li>Het goede wachtwoord is niet genoeg: je moet op die kern ook de eigenaar zijn, of vertrouwd (Access). Kent iemand
 *       anders het wachtwoord, dan verbindt zijn bril niet, en hoort de eigenaar dat meteen.</li>
 *   <li>Gelukt: je bril hangt aan die kern (dat onthoudt de wereld, zie {@link Datacenters#uplink}). Alles wat de bril
 *       nadenkt of bewaart, en het brein, draait vanaf nu op zijn datacenter, waar je ook bent, zolang je daar de eigenaar
 *       bent of vertrouwd. Het logboek van de kern zegt wie er verbond.</li>
 *   <li>Loskoppelen kan altijd (de knop Disconnect in hetzelfde scherm). Een ander IP verbinden vervangt de vorige
 *       verbinding.</li>
 * </ul>
 */
public final class GlassesUplink {
    private GlassesUplink() {}

    /** Het verbindscherm van de bril stuurde een IP en een wachtwoord; een leeg IP betekent: loskoppelen. */
    public static void handle(ServerPlayer player, String ip, String password) {
        if (ip.isBlank()) {
            disconnect(player);
        } else {
            connect(player, ip, password);
        }
    }

    /** De bril van deze speler verbinden met de kern met dit IP, als het wachtwoord klopt en hij daar mag. */
    public static void connect(ServerPlayer player, String ip, String password) {
        ItemStack glasses = ImtData.getWornGlasses(player);
        if (glasses.isEmpty() || !ImtData.isAuthorized(glasses, player)) {
            reply(player, false, "Put on your own I.M.T glasses to connect them.");
            return;
        }
        // Een lege bril (hoofdstuk 96) of een bril die na een EMP opnieuw opstart (hoofdstuk 35) doet niets.
        if (ImtData.getEnergy(glasses) <= 0) {
            reply(player, false, "Your glasses have no power: charge them first.");
            return;
        }
        if (com.senne.imttech.weapons.Emp.isJammed(player)) {
            reply(player, false, "Your glasses are rebooting after an EMP: try again in a moment.");
            return;
        }
        SecurityCoreBlockEntity core = SecurityOs.connect(player, ip, password, message -> reply(player, false, message));
        if (core == null) {
            return; // SecurityOs.connect zei al waarom niet (geen systeem, geen stroom, fout wachtwoord...)
        }
        ServerLevel level = player.serverLevel();
        String name = player.getGameProfile().getName();
        if (!DatacenterGlasses.mayConnect(level, core, player)) {
            // Het wachtwoord klopte, maar deze speler is daar niemand: hij kent het wachtwoord van een ander. Dat moet de
            // eigenaar weten (net als een fout wachtwoord).
            core.logEvent(EventLog.Type.LOGIN_FAIL, name + " tried to connect I.M.T glasses (not trusted)");
            core.addMessage(name + " knows the password, but is not trusted: glasses not connected", true);
            ServerPlayer owner = core.ownerId() == null ? null : level.getServer().getPlayerList().getPlayer(core.ownerId());
            if (owner != null && owner != player) {
                ImtServer.send(owner, Component.translatable("imt.imttech.msg.uplink_refused_alert", name, core.hostname()),
                        ImtMessagePayload.Level.ALERT);
            }
            reply(player, false, "Access denied: you are not the owner of " + core.hostname() + ", and not trusted there.");
            return;
        }
        Datacenters.get(level).setUplink(player.getUUID(), new Datacenters.Uplink(core.ip(), core.hostname(), name));
        core.logEvent(EventLog.Type.LOGIN, name + " connected I.M.T glasses");
        // Wat er van deze bril op het vorige datacenter liep, telt daar niet meer; vanaf de volgende seconde telt het hier.
        DatacenterGlasses.forget(player.getUUID());
        DatacenterBrain.forget(player.getUUID());
        reply(player, true, "");
        ImtServer.send(player, Component.translatable("imt.imttech.msg.uplink_connected", core.hostname(), core.ip()),
                ImtMessagePayload.Level.INFO);
        ImtServer.reportToDatacenter(player);
    }

    /**
     * De bril van deze speler loskoppelen (dat kan altijd, ook zonder datacenter). Wat erop doorliep, stopt bij de
     * volgende seconde: je navigatie, je hacks en je spionagedrone.
     */
    public static void disconnect(ServerPlayer player) {
        Datacenters datacenters = Datacenters.get(player.server);
        Datacenters.Uplink uplink = datacenters.uplink(player.getUUID());
        if (uplink == null) {
            reply(player, false, "Your glasses are not connected to a datacenter.");
            return;
        }
        datacenters.removeUplink(player.getUUID());
        DatacenterGlasses.forget(player.getUUID());
        DatacenterBrain.forget(player.getUUID());
        // In het logboek van de kern, als die geladen is (voor een afscheid laden we geen stuk wereld).
        GlobalPos where = CoreNetworkData.get(player.server).find(uplink.ip());
        ServerLevel level = where == null ? null : player.server.getLevel(where.dimension());
        if (level != null && level.isLoaded(where.pos()) && level.getBlockEntity(where.pos()) instanceof SecurityCoreBlockEntity core) {
            core.logEvent(EventLog.Type.LOGIN, player.getGameProfile().getName() + " disconnected I.M.T glasses");
        }
        reply(player, true, "");
        ImtServer.send(player, Component.translatable("imt.imttech.msg.uplink_disconnected", uplink.host()),
                ImtMessagePayload.Level.INFO);
        ImtServer.reportToDatacenter(player);
    }

    /** Een antwoord voor het verbindscherm: gelukt (het scherm gaat dicht), of waarom niet (in het rood). */
    private static void reply(ServerPlayer player, boolean done, String message) {
        ModNetwork.sendTo(player, new UplinkReplyPayload(done, message));
    }
}
