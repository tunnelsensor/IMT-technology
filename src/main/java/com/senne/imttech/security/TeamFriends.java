package com.senne.imttech.security;

import com.senne.imttech.config.ImtConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

/**
 * Telt het scoreboard-team van Minecraft ({@code /team}) als vriend van de eigenaar van een basis?
 *
 * Hoofdstuk 110 (14 november). Jouw melding: "mijn base is geraid: de dief kon alles hakken ook al was hij in mijn
 * zone, hij vond mijn base door zijn bril en zone borders aan te zetten". De server stuurt de rand alleen naar wie in
 * de zone mag bouwen, dus voor de mod was de dief een "vriend". Vroeger telde daarvoor ook het scoreboard-team mee. Op
 * een server met rangen ([Donator], [Support]) of een mod voor de spelerslijst zitten spelers vaak allemaal in zo'n
 * team, en dan is iedereen een teamgenoot van iedereen: hij ziet je rand, mag in je zone hakken, en je torentjes,
 * koepel en kern laten hem met rust.
 *
 * Daarom telt een team standaard niet meer (mijn invulling): vrienden voeg je toe in de app Access van je kern. Wie
 * {@code /team} wel bewust zo gebruikt, zet {@code [rules] teamsAreFriends = true} in {@code config/imttech.toml}.
 */
public final class TeamFriends {
    private TeamFriends() {}

    /** Zit {@code other} in het team van de eigenaar (of een bevriend team), en telt dat (zie de config)? */
    public static boolean allied(ServerLevel level, String ownerName, @Nullable Player other) {
        return other != null && allied(level, ownerName, other.getScoreboardName());
    }

    /** Hetzelfde op naam (ook voor wie nu offline is). */
    public static boolean allied(ServerLevel level, String ownerName, @Nullable String otherName) {
        if (!ImtConfig.Rules.TEAMS_ARE_FRIENDS || ownerName == null || ownerName.isEmpty()
                || otherName == null || otherName.isEmpty()) {
            return false;
        }
        PlayerTeam ownerTeam = level.getScoreboard().getPlayersTeam(ownerName);
        return ownerTeam != null && ownerTeam.isAlliedTo(level.getScoreboard().getPlayersTeam(otherName));
    }
}
