package com.senne.imttech.security.os;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Wat je op een desktop mag (hoofdstuk 40, jouw wens: "configureren via de security core wat je exact op die desktop mag
 * en kan doen"). Per app een van drie standen:
 * <ul>
 *   <li><b>OFF</b>: de app staat er niet op, en de server stuurt zijn gegevens ook niet mee;</li>
 *   <li><b>VIEW</b>: alleen kijken (camera's bekijken maar niet draaien, apparaten zien maar niet schakelen);</li>
 *   <li><b>USE</b>: ook bedienen.</li>
 * </ul>
 * Op de kern zelf en met de laptop heb je alles ({@link #FULL}); een Computer heeft de rechten die de eigenaar in de app
 * Computers koos. De server kijkt bij elke opdracht van het scherm na of het mag ({@link #allows}); het scherm gebruikt
 * dezelfde regels om icoontjes en knoppen te verbergen. De namen van de apps zijn die van SecurityOsScreen.App.
 */
public final class DesktopRights {
    /** Wat je met één app mag. */
    public enum Access { OFF, VIEW, USE }

    /**
     * De apps die op een Computer kunnen, in de volgorde van de desktop. Niet: Settings (het wachtwoord van de kern),
     * Groups (dat zijn je eigen relais) en Computers zelf (dat stel je in op de kern of met de laptop).
     */
    public static final List<String> COMPUTER_APPS = List.of("STATUS", "CAMERAS", "DEVICES", "ROBOTS", "TRAINING", "ACCESS", "REQUESTS", "MESSAGES",
            "KEYCARDS", "LOGIC", "MONITOR", "BACKUP", "ZONE", "FILES", "NOTES", "LOGS");

    /** Apps zonder knoppen: die zijn alleen OFF of VIEW. */
    public static final Set<String> LOOK_ONLY = Set.of("STATUS", "MONITOR", "LOGS");

    /** Alles mag (de kern zelf, of de laptop). */
    public static final DesktopRights FULL = new DesktopRights(null);
    /** Niets mag (een nieuwe Computer die nog niet ingesteld is). */
    public static final DesktopRights NONE = new DesktopRights(Map.of());
    /** De I.M.T Phone (hoofdstuk 50): Status, Logs, Cameras, Devices, Requests en Messages, meer niet. */
    public static final DesktopRights PHONE = new DesktopRights(Map.of("STATUS", Access.VIEW, "LOGS", Access.VIEW,
            "CAMERAS", Access.USE, "DEVICES", Access.USE, "REQUESTS", Access.USE, "MESSAGES", Access.USE));

    /** Per app wat mag; null = alles. */
    private final @Nullable Map<String, Access> apps;

    private DesktopRights(@Nullable Map<String, Access> apps) {
        this.apps = apps;
    }

    /** Alles mag (geen Computer)? */
    public boolean full() {
        return apps == null;
    }

    /** Wat mag je met deze app? */
    public Access access(String app) {
        if (apps == null) {
            return Access.USE;
        }
        if (!COMPUTER_APPS.contains(app)) {
            return Access.OFF;
        }
        return apps.getOrDefault(app, Access.OFF);
    }

    /** Staat deze app op de desktop (VIEW of USE)? */
    public boolean shows(String app) {
        return access(app) != Access.OFF;
    }

    /** Mag je deze app ook bedienen (USE)? */
    public boolean mayUse(String app) {
        return access(app) == Access.USE;
    }

    /** Staat er nog geen enkele app op (een Computer die nog niet ingesteld is)? */
    public boolean empty() {
        return apps != null && apps.values().stream().noneMatch(access -> access != Access.OFF);
    }

    /** Dezelfde rechten, maar met deze app op {@code access} (een app zonder knoppen hooguit VIEW). */
    public DesktopRights with(String app, Access access) {
        if (apps == null || !COMPUTER_APPS.contains(app)) {
            return this;
        }
        Map<String, Access> copy = new LinkedHashMap<>(apps);
        copy.put(app, LOOK_ONLY.contains(app) && access == Access.USE ? Access.VIEW : access);
        return new DesktopRights(copy);
    }

    /**
     * Mag deze opdracht van het scherm (zie SecurityOs.handleAction)? Met alle rechten altijd. Anders hangt het af van de
     * app waar hij bij hoort: kijken (een bestand openen, een camerabeeld) mag met VIEW, iets veranderen alleen met USE.
     * Sluiten en de vensters onthouden mag altijd. Een opdracht die we niet kennen, mag op een Computer niet.
     */
    public boolean allows(String op, CompoundTag args) {
        if (apps == null) {
            return true;
        }
        return switch (op) {
            case "close", "desk_save", "desk_off" -> true;
            // Bestanden: Files en Notes delen ze. Een bestand lees je in Notes (dus openen kan alleen als Notes erop staat);
            // maken, bewaren, verplaatsen en weggooien is bedienen.
            case "fs_open" -> shows("NOTES");
            case "fs_save" -> mayUse("NOTES");
            case "fs_touch" -> mayUse("FILES") || mayUse("NOTES");
            case "fs_mkdir", "fs_delete", "fs_trap" -> mayUse("FILES");
            // Hoofdstuk 130: kopiëren tussen je systeem en een SD-kaart (ook slepen tussen de twee) is bedienen in Files, en
            // het gaat om de tekst van bestanden: van je systeem naar de kaart is die tekst lezen (Notes zien), van de kaart
            // naar je systeem is tekst schrijven (Notes bedienen). Gevonden door de nalezing: anders haalde een bezoeker met
            // alleen Files de notities van een computer zonder Notes op zijn eigen kaart. De programma's op de kaart staan in
            // Logic (erop zetten, installeren en weggooien is bedienen, de code van een open programma bekijken is kijken).
            case "fs_move" -> mayUse("FILES") && (onCard(args.getString("from")) == onCard(args.getString("to"))
                    || notesFor(args.getString("from")));
            case "fs_copy" -> mayUse("FILES") && notesFor(args.getString("from"));
            case "sd_copy", "sd_install", "sd_delete" -> mayUse("LOGIC");
            case "sd_view" -> shows("LOGIC");
            // De schakelaars van het hele netwerk: monsters en spelers staan in Access, de rest in Devices.
            case "flag" -> {
                String name = args.getString("name");
                yield mayUse(name.equals("mobs") || name.equals("players") ? "ACCESS" : "DEVICES");
            }
            case "cam_watch" -> shows("CAMERAS");
            case "cam_aim", "cam_rename" -> mayUse("CAMERAS");
            case "dev_toggle", "sw_port", "dev_rename" -> mayUse("DEVICES");
            case "trust_add", "trust_remove" -> mayUse("ACCESS");
            case "req_answer" -> mayUse("REQUESTS");
            case "msg_send" -> mayUse("MESSAGES");
            case "logic_save", "logic_delete", "logic_enable", "input_rename" -> mayUse("LOGIC");
            case "arrived" -> shows("LOGIC");
            case "training_stop" -> mayUse("TRAINING");
            default -> {
                if (op.startsWith("card_") || op.startsWith("cgroup_")) {
                    yield mayUse("KEYCARDS");
                }
                if (op.startsWith("zone_")) {
                    yield mayUse("ZONE");
                }
                if (op.startsWith("robot_")) {
                    yield mayUse("ROBOTS");
                }
                if (op.startsWith("backup_")) {
                    yield mayUse("BACKUP");
                }
                yield false; // ook passwd en de opdrachten van de app Computers (pc_...)
            }
        };
    }

    /** Hoofdstuk 130: staat dit pad (van Files) op een SD-kaart ({@code sd:/...}, zie sdcard/SdDesktop)? */
    private static boolean onCard(String path) {
        return path.startsWith("sd:");
    }

    /**
     * Hoofdstuk 130: mag de tekst van {@code from} over naar de andere kant? Van de kaart naar je systeem schrijft tekst
     * (Notes bedienen), van je systeem naar de kaart leest tekst (Notes zien).
     */
    private boolean notesFor(String from) {
        return onCard(from) ? mayUse("NOTES") : shows("NOTES");
    }

    /** Bij welke app hoort deze opdracht (voor de melding "View only")? Leeg als we het niet weten. */
    public static String appOf(String op, CompoundTag args) {
        if (op.equals("fs_open") || op.equals("fs_save") || op.equals("fs_copy")) {
            return "NOTES"; // fs_copy (hoofdstuk 130): Files mag het wel, maar Notes niet
        }
        if (op.startsWith("fs_")) {
            return "FILES";
        }
        if (op.equals("flag")) {
            String name = args.getString("name");
            return name.equals("mobs") || name.equals("players") ? "ACCESS" : "DEVICES";
        }
        if (op.startsWith("cam_")) {
            return "CAMERAS";
        }
        if (op.startsWith("dev_") || op.equals("sw_port")) {
            return "DEVICES";
        }
        if (op.startsWith("trust_")) {
            return "ACCESS";
        }
        if (op.startsWith("req_")) {
            return "REQUESTS";
        }
        if (op.startsWith("auth_")) {
            return "SETTINGS";
        }
        if (op.startsWith("msg_")) {
            return "MESSAGES";
        }
        if (op.startsWith("phone_")) {
            return "ACCESS";
        }
        if (op.startsWith("card_") || op.startsWith("cgroup_")) {
            return "KEYCARDS";
        }
        if (op.startsWith("logic_") || op.equals("input_rename") || op.startsWith("sd_")) {
            return "LOGIC";
        }
        if (op.startsWith("zone_")) {
            return "ZONE";
        }
        if (op.startsWith("robot_")) {
            return "ROBOTS";
        }
        if (op.startsWith("backup_")) {
            return "BACKUP";
        }
        if (op.startsWith("training_")) {
            return "TRAINING";
        }
        return "";
    }

    // ================================================================ Bewaren (en meesturen naar het scherm)

    /** Per app een getal: 0 = OFF, 1 = VIEW, 2 = USE (apps die OFF zijn, laten we weg). Alles = een lege tag met "full". */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (apps == null) {
            tag.putBoolean("full", true);
            return tag;
        }
        for (Map.Entry<String, Access> entry : apps.entrySet()) {
            if (entry.getValue() != Access.OFF) {
                tag.putByte(entry.getKey(), (byte) entry.getValue().ordinal());
            }
        }
        return tag;
    }

    public static DesktopRights load(CompoundTag tag) {
        if (tag.getBoolean("full")) {
            return FULL;
        }
        Map<String, Access> apps = new LinkedHashMap<>();
        for (String app : COMPUTER_APPS) {
            int value = tag.getByte(app);
            if (value > 0 && value < Access.values().length) {
                apps.put(app, LOOK_ONLY.contains(app) ? Access.VIEW : Access.values()[value]);
            }
        }
        return new DesktopRights(apps);
    }
}
