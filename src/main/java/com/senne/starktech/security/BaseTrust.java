package com.senne.starktech.security;

import com.senne.starktech.StarkTech;
import com.senne.starktech.security.zone.ZoneData;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Wie mag een apparaat in een basis instellen? Eén regel voor de laserzender (zijn instelscherm) en de
 * keycardlezer en het codepaneel (hun naam), allemaal van 13 oktober: "enkel de persoon die het heeft geplaatst (of trusted
 * players in zone)" en "dit kan enkel de owner doen of trusted players in zone".
 * <ul>
 *   <li>Wie het apparaat neerzette.</li>
 *   <li>Wie vertrouwd is in de basis waar het staat: de eigenaar van de beveiligingskern, zijn team en de
 *       spelers die hij vertrouwt (Access op de desktop). Dat geldt in zijn zone én in de rest van het
 *       gebied van de kern (daar lopen die spelers ook gewoon door een laserwand).</li>
 *   <li>Een apparaat van niemand (neergezet vóór 13 oktober) wordt van wie het als eerste opent, net als
 *       een oud relais. Behalve in iemands basis (dan alleen de vertrouwde spelers daar) en in een gebouw
 *       van deze mod (de Stark Tower, de Hydra-bunker): dat kun je niet zomaar overnemen.</li>
 * </ul>
 * Geen uitzondering voor creative.
 */
public final class BaseTrust {
    private BaseTrust() {}

    /** Een apparaat dat onthoudt wie het neerzette (de blok-entiteit van de laserzender, lezer of het paneel). */
    public interface Placed {
        /** Wie het neerzette, of null (van vóór 13 oktober, of uit een gebouw). */
        @Nullable UUID owner();

        /** De naam van wie het neerzette (leeg als niemand). */
        String ownerName();

        void setOwner(ServerPlayer player);

        BlockPos getBlockPos();
    }

    /**
     * Mag deze speler het apparaat instellen? Is het nog van niemand en staat het niet in iemands basis of in
     * een gebouw, dan wordt het meteen van deze speler (zie de uitleg bovenaan).
     */
    public static boolean mayEdit(ServerPlayer player, Placed device) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = device.getBlockPos();
        if (player.getUUID().equals(device.owner()) || trustedHere(level, pos, player)) {
            return true;
        }
        if (device.owner() == null && baseOwnerAt(level, pos) == null && !inBuilding(level, pos)) {
            device.setOwner(player);
            return true;
        }
        return false;
    }

    /**
     * Van wie het apparaat is, voor de melding als je het niet mag instellen: wie het neerzette, anders de
     * eigenaar van de basis waar het staat. Leeg als het van niemand is (dan zit het op slot).
     */
    public static String belongsTo(ServerLevel level, Placed device) {
        if (device.owner() != null) {
            return device.ownerName();
        }
        String base = baseOwnerAt(level, device.getBlockPos());
        return base == null ? "" : base;
    }

    /** Is deze speler vertrouwd in de basis waar deze plek in ligt (zijn zone, of het gebied van zijn kern)? */
    public static boolean trustedHere(ServerLevel level, BlockPos pos, Player player) {
        ZoneData.Zone zone = ZoneData.get(level).at(level.dimension(), pos);
        if (zone != null && zone.isBuilder(level, player)) {
            return true;
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        return core != null && SecurityCores.isFriend(level, core, player);
    }

    /** De naam van de basis waar deze plek in ligt ("" als die van niemand is), of null als hij nergens in ligt. */
    public static @Nullable String baseOwnerAt(ServerLevel level, BlockPos pos) {
        ZoneData.Zone zone = ZoneData.get(level).at(level.dimension(), pos);
        if (zone != null) {
            return zone.ownerName();
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        if (core != null) {
            return core.ownerId() == null ? "" : core.ownerName();
        }
        return null;
    }

    /** Ligt deze plek in een gebouw van deze mod (de Stark Tower, de Hydra-bunker...)? */
    public static boolean inBuilding(ServerLevel level, BlockPos pos) {
        return level.structureManager().getStructureWithPieceAt(pos, holder -> holder.unwrapKey()
                .map(key -> key.location().getNamespace().equals(StarkTech.MODID)).orElse(false)).isValid();
    }
}
