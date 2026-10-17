package com.senne.imttech.shield;

import com.senne.imttech.network.LaserActionPayload;
import com.senne.imttech.network.LaserOpenPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.security.BaseTrust;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Het instelscherm van de laserzender, aan de kant van de server (13 oktober): het scherm openen, en wat
 * je erin kiest op de zenders zetten. Het scherm zelf staat in client/shield/LaserEmitterScreen.
 *
 * Jouw wens: "zorg dat je gewoon op ze kunt klikken zonder dat er iets gebeurt, dit zorgt ervoor dat het
 * makkelijker is om cables te leggen. Enkel de persoon die het heeft geplaatst (of trusted players in zone)
 * kunnen hier op sneak klikken en dan een GUI openen."
 *
 * Wie mag het scherm openen (zie {@link #mayEdit}; dezelfde regel als bij de keycardlezer, security/BaseTrust)?
 * <ul>
 *   <li>Wie de zender neerzette.</li>
 *   <li>Wie in deze basis vertrouwd is: de eigenaar van de beveiligingskern, zijn team en de spelers die hij
 *       vertrouwt (Access op de desktop). Dat geldt in zijn zone én in de rest van het gebied van de kern,
 *       net als bij de laserwand zelf (daar lopen die spelers gewoon doorheen).</li>
 *   <li>Een zender van niemand (van vóór 13 oktober) wordt van wie hem als eerste opent, net als een oud
 *       relais. Behalve in iemands basis (dan alleen de vertrouwde spelers daar) en in een gebouw van deze
 *       mod (de Biebia's Industries Tower, de Biebia's Industries-bunker): die lasers kun je niet zomaar overnemen en uitzetten.</li>
 * </ul>
 *
 * Wat je kiest, geldt voor de hele laserpoort: alle zenders die eraan vastzitten (die gaan ook samen aan
 * en uit, zie LaserEmitterBlock.group). Een zender van iemand anders in dezelfde poort blijft zoals hij is.
 */
public final class LaserEmitters {
    private LaserEmitters() {}

    /** Zo dichtbij moet je staan (blokken), net als bij een lamp of stroomkast. */
    private static final double REACH = 6.0;

    /** Sneak + rechtsklik op de zender: mag je hem instellen, dan opent jouw spel het scherm. */
    public static void open(ServerPlayer player, BlockPos pos) {
        LaserEmitterBlockEntity emitter = emitter(player, pos);
        if (emitter == null) {
            return;
        }
        if (!mayEdit(player, emitter)) {
            player.displayClientMessage(refusal(player.serverLevel(), emitter), true);
            return;
        }
        ModNetwork.sendTo(player, new LaserOpenPayload(pos, emitter.ownerName()));
    }

    /** Een knop uit het scherm: de kleur, omkeren of liggend/staand, voor de hele poort. */
    public static void handleAction(ServerPlayer player, LaserActionPayload payload) {
        LaserEmitterBlockEntity clicked = emitter(player, payload.pos());
        if (clicked == null || !mayEdit(player, clicked)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<BlockPos> gate = new ArrayList<>();
        for (BlockPos member : LaserEmitterBlock.group(level, payload.pos())) {
            if (level.getBlockEntity(member) instanceof LaserEmitterBlockEntity other && mayEdit(player, other)) {
                gate.add(member);
            }
        }
        switch (payload.op()) {
            case COLOR -> LaserEmitterBlock.applyColour(level, gate,
                    ShieldColor.values()[Math.floorMod(payload.value(), ShieldColor.values().length)]);
            case INVERT -> LaserEmitterBlock.applyInverted(level, gate, payload.value() != 0);
            case TURN -> LaserEmitterBlock.applyTurned(level, gate, payload.value() != 0);
        }
    }

    /**
     * Mag deze speler de zender instellen? Is de zender nog van niemand en ligt hij niet in iemands basis
     * of in een gebouw, dan wordt hij meteen van deze speler (zie de uitleg bovenaan).
     */
    public static boolean mayEdit(ServerPlayer player, LaserEmitterBlockEntity emitter) {
        return BaseTrust.mayEdit(player, emitter);
    }

    /** Wat je te horen krijgt als je de zender niet mag instellen. */
    private static Component refusal(ServerLevel level, LaserEmitterBlockEntity emitter) {
        String owner = BaseTrust.belongsTo(level, emitter);
        return owner.isEmpty()
                ? Component.translatable("imt.imttech.msg.laser_locked")
                : Component.translatable("imt.imttech.msg.laser_not_yours", owner);
    }

    /** De zender waar deze speler bij kan, of null (er staat daar geen zender, of hij staat te ver weg). */
    private static @Nullable LaserEmitterBlockEntity emitter(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH
                || !(player.serverLevel().getBlockEntity(pos) instanceof LaserEmitterBlockEntity emitter)) {
            return null;
        }
        return emitter;
    }
}
