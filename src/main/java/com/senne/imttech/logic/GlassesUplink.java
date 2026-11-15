package com.senne.imttech.logic;

import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.imt.ImtUplink;
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
 * Je bril verbinden met het datacenter van een security core (hoofdstuk 122 en 123, jouw wensen van 15 november: "je moet
 * je bril connecten via het ip van je security core met password voor dat die bril aan de serverroom hangt", en daarna:
 * "zorg dat de bril connectie niet aan de speler hangt maar echt aan de bril; als iemand het wachtwoord kent kan die ook
 * connecten zonder trusted te zijn").
 *
 * Zo gaat het:
 * <ul>
 *   <li>In het G-menu, tabblad Systems, kies je Datacenter. Daar typ je het IP van een kern (dat staat in zijn app Status) en
 *       zijn wachtwoord, net als met de Hacking Laptop of de I.M.T Phone.</li>
 *   <li>De server kijkt het na zoals bij de laptop ({@link SecurityOs#connect}): de kern moet in dezelfde dimensie staan,
 *       stroom hebben en een wachtwoord; een fout wachtwoord komt in zijn logboek, de eigenaar hoort het, en na te veel
 *       keer moet je even wachten.</li>
 *   <li>Het goede wachtwoord is genoeg: wie het kent, mag verbinden, ook zonder in Access te staan (vertrouwd zijn is voor
 *       iets anders, zoals de kabels in de zone door de muur zien). Is het niet de eigenaar die verbindt, dan hoort de
 *       eigenaar dat (zoals bij een login met de laptop).</li>
 *   <li>Gelukt: de verbinding staat op de bril zelf ({@link ImtUplink}): geef je de bril door, dan blijft hij verbonden.
 *       Alles wat de bril nadenkt of bewaart, en het brein, draait vanaf nu op het datacenter van die kern, waar je ook
 *       bent. De bril onthoudt ook welk wachtwoord de kern had: zet de eigenaar een nieuw wachtwoord (of neemt iemand de
 *       kern over, wat het wachtwoord wist), dan moet de bril opnieuw verbinden. Zo gooit de eigenaar iedereen eraf.</li>
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

    /** De bril die deze speler op heeft, als het zijn eigen bril is; anders leeg. */
    private static ItemStack ownGlasses(ServerPlayer player) {
        ItemStack glasses = ImtData.getWornGlasses(player);
        return !glasses.isEmpty() && ImtData.isAuthorized(glasses, player) ? glasses : ItemStack.EMPTY;
    }

    /** De bril van deze speler verbinden met de kern met dit IP, als het wachtwoord klopt. */
    public static void connect(ServerPlayer player, String ip, String password) {
        ItemStack glasses = ownGlasses(player);
        if (glasses.isEmpty()) {
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
        if (core.ownerId() == null) {
            // Een kern van niemand (de toren van Biebia, nog niet gehackt) is niemands datacenter.
            reply(player, false, "This system has no owner, so there is no datacenter to connect to.");
            return;
        }
        ServerLevel level = player.serverLevel();
        String name = player.getGameProfile().getName();
        String stamp = core.passwordStamp();
        ImtData.setUplink(glasses, new ImtUplink(core.ip(), core.hostname(), stamp));
        // Het datacenter weet meteen welk wachtwoord hij nu heeft (anders pas bij zijn volgende melding, om de twee seconden).
        Datacenters.get(level).updateStamp(GlobalPos.of(level.dimension(), core.getBlockPos()), stamp);
        core.logEvent(EventLog.Type.LOGIN, name + " connected I.M.T glasses");
        if (!player.getUUID().equals(core.ownerId())) {
            // Iemand anders kent het wachtwoord: dat moet de eigenaar weten (zoals bij een login met de laptop).
            core.addMessage(name + " connected I.M.T glasses", true);
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(core.ownerId());
            if (owner != null && owner != player) {
                ImtServer.send(owner, Component.translatable("imt.imttech.msg.uplink_connected_alert", name, core.hostname()),
                        ImtMessagePayload.Level.ALERT);
            }
        }
        // Wat er van deze bril op het vorige datacenter liep, telt daar niet meer; vanaf de volgende seconde telt het hier.
        DatacenterGlasses.forget(player.getUUID());
        DatacenterBrain.forget(player.getUUID());
        reply(player, true, "");
        ImtServer.send(player, Component.translatable("imt.imttech.msg.uplink_connected", core.hostname(), core.ip()),
                ImtMessagePayload.Level.INFO);
        ImtServer.reportToDatacenter(player);
    }

    /**
     * De bril van deze speler loskoppelen (dat kan altijd, ook zonder datacenter). Wat erop doorliep, stopt meteen: je
     * navigatie, je hacks en je spionagedrone.
     */
    public static void disconnect(ServerPlayer player) {
        ItemStack glasses = ownGlasses(player);
        ImtUplink uplink = glasses.isEmpty() ? null : ImtData.getUplink(glasses);
        if (uplink == null) {
            reply(player, false, "Your glasses are not connected to a datacenter.");
            return;
        }
        ImtData.setUplink(glasses, null);
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
