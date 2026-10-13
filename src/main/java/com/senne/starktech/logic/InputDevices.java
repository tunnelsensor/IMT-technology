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
 * Een keycardlezer hernoemen in de wereld, aan de kant van de server (13 oktober): het naamscherm openen, en
 * de naam die je erin typt op de lezer zetten. Het scherm zelf staat in client/InputNameScreen.
 *
 * Jouw wens: "rename keycard reader door sneak rechts te klikken zonder item in hand, dit kan enkel de owner
 * doen of trusted players in zone".
 *
 * Wie mag (zie {@link #mayRename}; dezelfde regel als bij de laserzender, security/BaseTrust)?
 * <ul>
 *   <li>Wie de lezer neerzette.</li>
 *   <li>Wie vertrouwd is in de basis waar hij hangt: de eigenaar van de beveiligingskern, zijn team en zijn
 *       vertrouwde spelers (Access), in de zone en in de rest van het gebied van de kern.</li>
 *   <li>Een lezer van vóór 13 oktober heeft nog geen eigenaar: die wordt van wie hem als eerste opent, behalve
 *       in iemands basis (dan alleen de vertrouwde spelers daar).</li>
 * </ul>
 * Op de desktop (de apps Devices en Logic) hernoemt de eigenaar van de kern hem ook nog, zoals altijd.
 */
public final class InputDevices {
    private InputDevices() {}

    /** Zo dichtbij moet je staan (blokken), net als bij de laserzender. */
    private static final double REACH = 6.0;

    /** Sneak + rechtsklik op de lezer: mag je hem hernoemen, dan opent jouw spel het naamscherm. */
    public static void open(ServerPlayer player, BlockPos pos) {
        InputDeviceBlockEntity device = device(player, pos);
        if (device == null) {
            return;
        }
        if (!mayRename(player, device)) {
            player.displayClientMessage(refusal(player.serverLevel(), device), true);
            return;
        }
        ModNetwork.sendTo(player, new InputNameOpenPayload(pos, device.name(), device.ownerName()));
    }

    /**
     * Save in het naamscherm: de nieuwe naam op de lezer zetten. De server kijkt opnieuw of je mag (iemand kan
     * je net uit Access gehaald hebben). Hangt de lezer in het gebied van een kern, dan komt het in zijn
     * logboek, net als een Rename in de app Devices.
     */
    public static void handleRename(ServerPlayer player, InputRenamePayload payload) {
        InputDeviceBlockEntity device = device(player, payload.pos());
        if (device == null || !mayRename(player, device)) {
            return;
        }
        String clean = InputDeviceBlockEntity.cleanName(payload.name());
        if (clean.isEmpty()) {
            player.displayClientMessage(Component.translatable("logic.starktech.reader.name_empty")
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
        player.displayClientMessage(Component.translatable("logic.starktech.reader.renamed", clean)
                .withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * Mag deze speler de lezer hernoemen? Is de lezer nog van niemand en hangt hij niet in iemands basis, dan
     * wordt hij meteen van deze speler (zie de uitleg bovenaan).
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

    /** De lezer waar deze speler bij kan, of null (er hangt daar geen lezer, of hij staat te ver weg). */
    private static @Nullable InputDeviceBlockEntity device(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH
                || !(player.serverLevel().getBlockEntity(pos) instanceof InputDeviceBlockEntity device)) {
            return null;
        }
        return device;
    }
}
