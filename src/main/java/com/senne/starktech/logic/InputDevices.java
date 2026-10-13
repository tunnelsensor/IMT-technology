package com.senne.starktech.logic;

import com.senne.starktech.network.InputNameOpenPayload;
import com.senne.starktech.network.InputRenamePayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.security.BaseTrust;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import com.senne.starktech.security.os.EventLog;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Een keycardlezer of codepaneel hernoemen in de wereld, aan de kant van de server (13 oktober): het naamscherm
 * openen, en de naam die je erin typt op het apparaat zetten. Het scherm zelf staat in client/InputNameScreen.
 *
 * Jouw wensen: "rename keycard reader door sneak rechts te klikken zonder item in hand, dit kan enkel de owner
 * doen of trusted players in zone", en daarna "zorg ook dat dit bij code panels werkt".
 *
 * Wie mag (zie {@link #mayRename}; dezelfde regel als bij de laserzender, security/BaseTrust)?
 * <ul>
 *   <li>Wie de lezer of het paneel neerzette.</li>
 *   <li>Wie vertrouwd is in de basis waar hij hangt: de eigenaar van de beveiligingskern, zijn team en zijn
 *       vertrouwde spelers (Access), in de zone en in de rest van het gebied van de kern.</li>
 *   <li>Een lezer of paneel van vóór 13 oktober heeft nog geen eigenaar: die wordt van wie hem als eerste opent,
 *       behalve in iemands basis (dan alleen de vertrouwde spelers daar).</li>
 * </ul>
 * Mag je het niet, dan zegt een lezer van wie hij is ({@link #open}); een codepaneel opent dan gewoon zijn
 * toetsen ({@link #tryOpen}), want daar moet iedereen een code op kunnen typen, ook als hij sluipt.
 *
 * Op de desktop (de apps Devices en Logic) hernoemt de eigenaar van de kern ze ook nog, zoals altijd.
 */
public final class InputDevices {
    private InputDevices() {}

    /** Zo dichtbij moet je staan (blokken), net als bij de laserzender. */
    private static final double REACH = 6.0;

    /**
     * Sneak + rechtsklik op een keycardlezer: mag je hem hernoemen, dan opent jouw spel het naamscherm; anders
     * hoor je van wie hij is.
     */
    public static void open(ServerPlayer player, BlockPos pos) {
        if (tryOpen(player, pos)) {
            return;
        }
        InputDeviceBlockEntity device = device(player, pos);
        if (device != null) {
            player.displayClientMessage(refusal(player.serverLevel(), device), true);
        }
    }

    /**
     * Mag deze speler het apparaat hernoemen, dan opent jouw spel het naamscherm en geeft dit true. Anders false,
     * zonder melding (een codepaneel opent dan gewoon zijn toetsen).
     */
    public static boolean tryOpen(ServerPlayer player, BlockPos pos) {
        InputDeviceBlockEntity device = device(player, pos);
        if (device == null || !mayRename(player, device)) {
            return false;
        }
        ModNetwork.sendTo(player, new InputNameOpenPayload(pos, device.name(), device.ownerName()));
        return true;
    }

    /**
     * Save in het naamscherm: de nieuwe naam op de lezer of het paneel zetten. De server kijkt opnieuw of je mag
     * (iemand kan je net uit Access gehaald hebben). Hangt hij in het gebied van een kern, dan komt het in zijn
     * logboek, net als een Rename in de app Devices.
     */
    public static void handleRename(ServerPlayer player, InputRenamePayload payload) {
        InputDeviceBlockEntity device = device(player, payload.pos());
        if (device == null || !mayRename(player, device)) {
            return;
        }
        String clean = InputDeviceBlockEntity.cleanName(payload.name());
        if (clean.isEmpty()) {
            player.displayClientMessage(Component.translatable("logic.starktech.input.name_empty")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        String old = device.name();
        device.setName(clean);
        SecurityCoreBlockEntity core = SecurityCores.coreAt(player.serverLevel(), payload.pos());
        if (core != null && !old.equals(clean)) {
            core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " renamed \"" + old + "\" to \""
                    + clean + "\"");
        }
        player.displayClientMessage(Component.translatable("logic.starktech.input.renamed", clean)
                .withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * Mag deze speler de lezer (of het paneel) hernoemen? Is hij nog van niemand en hangt hij niet in iemands
     * basis, dan wordt hij meteen van deze speler (zie de uitleg bovenaan).
     */
    public static boolean mayRename(ServerPlayer player, InputDeviceBlockEntity device) {
        return BaseTrust.mayEdit(player, device);
    }

    /** Wat je te horen krijgt als je de lezer niet mag hernoemen. */
    private static Component refusal(ServerLevel level, InputDeviceBlockEntity device) {
        String owner = BaseTrust.belongsTo(level, device);
        return owner.isEmpty()
                ? Component.translatable("logic.starktech.reader.locked")
                : Component.translatable("logic.starktech.reader.not_yours", owner);
    }

    /** De lezer of het paneel waar deze speler bij kan, of null (er hangt daar niets, of het is te ver weg). */
    private static @Nullable InputDeviceBlockEntity device(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH
                || !(player.serverLevel().getBlockEntity(pos) instanceof InputDeviceBlockEntity device)) {
            return null;
        }
        return device;
    }
}
