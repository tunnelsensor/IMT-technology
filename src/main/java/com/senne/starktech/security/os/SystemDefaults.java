package com.senne.starktech.security.os;

/**
 * Wat er op een nieuw beveiligingssysteem staat: de namen, de bestanden en hun teksten.
 *
 * Twee soorten systemen:
 * <ul>
 *   <li>Het systeem van Tony (de kern in een Stark Tower): een briefje van Tony aan Happy
 *       (README.txt) met aanwijzingen, en wat rommel die het echt laat lijken.</li>
 *   <li>Een systeem dat een speler zelf neerzette: een lege thuismap en een kort welkom.</li>
 * </ul>
 * In beide zit het systeembestand {@code /etc/security.conf}: daarin staat de echte naam van het
 * beveiligingsprogramma. De eigenaar mag het verplaatsen en hernoemen (verstoppen), niet weggooien.
 *
 * Alle teksten zijn Engels (je leest ze in het spel).
 */
public final class SystemDefaults {
    private SystemDefaults() {}

    /** De namen die het beveiligingsprogramma kan hebben (in de proceslijst staan er ook nep-versies). */
    public static final String[] DAEMONS = {"sentinel", "aegis", "warden", "argus", "vigil", "bastion", "citadel"};

    /** De hostnaam van de kern in een Stark Tower. */
    public static final String TONY_HOST = "stark-sec-core";

    /** Het systeembestand: zegt hoe het beveiligingsprogramma echt heet. */
    public static String securityConf(String daemon) {
        return "# Stark security network\ndaemon = " + daemon
                + "\nwatchdog = on\nrestart_on_kill = true\noverride_requires = pid";
    }

    /** De bestanden van Tony's systeem (de kern in een Stark Tower). */
    public static void tonyFiles(VirtualFs fs, String daemon) {
        // De README staat bovenaan in de map waar je binnenkomt. Een briefje van Tony aan Happy:
        // geen kant-en-klare commando's, wel alle aanwijzingen.
        fs.write("/README.txt", "Happy,\n"
                + "Yes, this is the maintenance shell. No, you can't break anything. Probably.\n"
                + "The tower security is a daemon. I rename it every week, because Rhodey keeps\n"
                + "killing it by name. Its current name is written in the security config\n"
                + "somewhere in /etc. You know how to read a file. You cat it.\n"
                + "Don't trust the process list blindly: a few daemons there only SOUND like\n"
                + "security. Decoys. Only the name from the config is the real one, and 'ps'\n"
                + "shows you its number.\n"
                + "If the tower ever goes rogue: override the daemon by its number. Inside, a\n"
                + "watchdog keeps putting the owner back, so shut that thing up first. Then make\n"
                + "yourself the owner. Type 'help' in there if you get lost.\n"
                + "And do NOT kill it. It just comes back angrier.\n"
                + "- Tony");
        fs.mkdirs("/home/tony");
        fs.write("/home/tony/notes.txt", "TODO: rename the security daemon (again).\n"
                + "Rhodey keeps killing it by name. New name is in /etc/security.conf.\n"
                + "Also: stop leaving the maintenance port open. - T");
        fs.putSystemFile("/etc/security.conf", securityConf(daemon));
        fs.write("/etc/hostname", TONY_HOST);
        fs.mkdirs("/var/log");
        fs.write("/var/log/boot.log", "[ ok ] turret-ctl online\n[ ok ] cam-feed online\n[ ok ] " + daemon
                + " online (watchdog)\n[warn] coffee-maker: low on beans");
        fs.mkdirs("/opt/jarvis");
        fs.write("/opt/jarvis/warning.txt", "JARVIS maintenance shell. Unauthorized access will be reported to\n"
                + "Mr. Stark, Miss Potts and Happy. Mostly Happy.");
    }

    /** De bestanden van een systeem dat een speler zelf neerzette. */
    public static void playerFiles(VirtualFs fs, String owner, String daemon) {
        String home = "/home/" + homeName(owner);
        fs.write("/README.txt", "Welcome to Stark OS.\n"
                + "This machine runs your security network: turrets, drones, laser walls\n"
                + "and cameras in this area answer to its owner.\n"
                + "Set a password first (Settings, or 'passwd' in the terminal).\n"
                + "Tip: the security config tells a hacker which daemon to take over.\n"
                + "You can't delete it, but nobody said you can't hide it.");
        fs.mkdirs(home);
        fs.putSystemFile("/etc/security.conf", securityConf(daemon));
        fs.write("/etc/hostname", hostFor(owner));
        fs.mkdirs("/var/log");
        fs.write("/var/log/boot.log", "[ ok ] turret-ctl online\n[ ok ] cam-feed online\n[ ok ] " + daemon
                + " online (watchdog)");
    }

    /** De hostnaam van een systeem van deze speler: {@code senne-sec}. */
    public static String hostFor(String owner) {
        return homeName(owner) + "-sec";
    }

    /** Een naam die als mapnaam mag (spelersnamen mogen al bijna alles; voor de zekerheid). */
    public static String homeName(String owner) {
        String name = owner.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return name.isEmpty() ? "user" : name.substring(0, Math.min(name.length(), 16));
    }
}
