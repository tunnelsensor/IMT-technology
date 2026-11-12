package com.senne.imttech.security;

import com.senne.imttech.network.CameraRenamePayload;
import com.senne.imttech.network.InputNameOpenPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.redstone.DeviceNames;
import com.senne.imttech.security.os.EventLog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Een bewakingscamera hernoemen in de wereld, aan de kant van de server (hoofdstuk 93, jouw wens van 12 november: "Kun je
 * ook zorgen dat je cam kunt renamen door shift klik zoals bij een keycardreader"). Sneak + rechtsklik met een lege hand op
 * de camera opent het naamscherm (hetzelfde scherm als bij de lezer, client/InputNameScreen); Save zet de naam erop.
 *
 * Wie mag? Dezelfde regel als bij een keycardlezer (security/BaseTrust): wie de camera neerzette, of wie vertrouwd is in
 * de basis waar hij hangt (de eigenaar van de kern, zijn team en zijn vertrouwde spelers). Een camera van vóór vandaag
 * heeft nog geen eigenaar: die wordt van wie hem als eerste hernoemt, behalve in iemands basis of in een gebouw van deze
 * mod (de toren).
 *
 * Eén naam overal (zoals Rename op de desktop): de nieuwe naam staat bij zijn beeld (de app Cameras, de schermmuur), en ook
 * in de I.M.T-lijst van wie hem hernoemt en van zijn eigenaar (als die online is), dus ook in je diagrammen
 * ({@code off("Lobby")}) en voor je stem.
 */
public final class CameraNames {
    private CameraNames() {}

    /** Zo dichtbij moet je staan (blokken), net als bij een lezer. */
    private static final double REACH = 6.0;

    /** Sneak + rechtsklik: mag je hem hernoemen, dan opent jouw spel het naamscherm; anders hoor je van wie hij is. */
    public static void open(ServerPlayer player, BlockPos pos) {
        CameraBlockEntity camera = camera(player, pos);
        if (camera == null) {
            return;
        }
        if (!BaseTrust.mayEdit(player, camera)) {
            String owner = BaseTrust.belongsTo(player.serverLevel(), camera);
            player.displayClientMessage((owner.isEmpty()
                    ? Component.translatable("security.imttech.camera.locked")
                    : Component.translatable("security.imttech.camera.not_yours", owner)).withStyle(ChatFormatting.RED), true);
            return;
        }
        ModNetwork.sendTo(player, new InputNameOpenPayload(pos.immutable(), camera.name(), camera.ownerName()));
    }

    /**
     * Save in het naamscherm: de nieuwe naam op de camera. De server kijkt opnieuw of je mag (iemand kan je net uit Access
     * gehaald hebben). Hangt hij aan een kern, dan komt het in zijn logboek.
     */
    public static void handleRename(ServerPlayer player, CameraRenamePayload payload) {
        CameraBlockEntity camera = camera(player, payload.pos());
        if (camera == null || !BaseTrust.mayEdit(player, camera)) {
            return;
        }
        String clean = clean(payload.name());
        if (clean.isEmpty()) {
            player.displayClientMessage(Component.translatable("logic.imttech.input.name_empty").withStyle(ChatFormatting.RED), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = camera.getBlockPos();
        String old = camera.name();
        camera.setName(clean);
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
        // De I.M.T-lijst van wie hem hernoemt, en van zijn eigenaar als die online is: wie hem erin heeft, krijgt de nieuwe
        // naam (diagrammen en je stem zoeken hem op die naam).
        List<ServerPlayer> lists = new ArrayList<>();
        lists.add(player);
        ServerPlayer owner = camera.owner() == null ? null : level.getServer().getPlayerList().getPlayer(camera.owner());
        if (owner != null && owner != player) {
            lists.add(owner);
        }
        for (ServerPlayer who : lists) {
            if (!DeviceNames.nameOf(who, List.of(pos)).isEmpty()) {
                DeviceNames.rename(who, List.of(pos), pos, clean, CameraBlock.DEFAULT_NAME);
                if (core != null) {
                    com.senne.imttech.logic.LogicDevices.refresh(level, core, who);
                }
            }
        }
        if (core != null && !old.equals(clean)) {
            core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " renamed the camera "
                    + (old.isEmpty() ? "at " + pos.toShortString() : "\"" + old + "\"") + " to \"" + clean + "\"");
        }
        player.displayClientMessage(Component.translatable("security.imttech.camera.renamed", clean)
                .withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * Een naam zoals we hem bewaren: netjes (zie CameraBlockEntity.cleanName), en zonder aanhalingstekens of backslash, want
     * die passen niet in een diagram ({@code off("Lobby")}).
     */
    public static String clean(String name) {
        return CameraBlockEntity.cleanName(name.replace("\"", "").replace("\\", ""));
    }

    /** De camera waar deze speler bij kan, of null (er hangt daar geen camera, of hij is te ver weg). */
    private static @Nullable CameraBlockEntity camera(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH
                || !(player.serverLevel().getBlockEntity(pos) instanceof CameraBlockEntity camera)) {
            return null;
        }
        return camera;
    }
}
