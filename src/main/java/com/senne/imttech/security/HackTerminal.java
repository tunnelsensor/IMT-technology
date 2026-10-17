package com.senne.imttech.security;

import com.senne.imttech.config.ImtConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * De puzzel achter de hack van een Bibie's Industries Tower: een nep-terminal waar je commando's in typt
 * (jouw wens, 27 september: typen, zoeken naar de open poort, inbreken, het beveiligingsprogramma
 * vinden en overnemen, met verschillende soorten puzzels).
 *
 * Vier stappen:
 * <ol>
 *   <li><b>RECON</b>: {@code scan} laat de poorten zien, maar ze staan allemaal op "filtered". In het
 *       firewall-logboek ({@code logs}) staan twee poorten die toegelaten zijn; één daarvan is later
 *       weer dichtgezet. Met {@code connect <poort>} op de goede kom je binnen.</li>
 *   <li><b>LOGIN</b>: een geheugendump vol tekens met woorden ertussen. Eén woord is het wachtwoord.
 *       Typ {@code login <woord>} (of alleen het woord). Is het fout, dan laat de terminal zien
 *       welke letters op dezelfde plek staan als in het wachtwoord ({@code ROCKET -> __CKET}),
 *       streept hij de woorden door die daardoor niet meer kunnen, en zegt hij welke er nog over
 *       zijn. De woorden lijken expres op elkaar, anders leer je niets van een fout woord. Vijf
 *       pogingen, daarna even geblokkeerd en een nieuwe dump.</li>
 *   <li><b>SHELL</b>: in de map waar je binnenkomt staat een {@code README.txt} (de terminal zegt
 *       dat je die moet lezen). Daarin staat stap voor stap wat je doet: de echte naam van het
 *       beveiligingsprogramma staat in {@code /etc/security.conf} (er draaien er een paar met zo'n
 *       naam), en met {@code ps} vind je zijn procesnummer. {@code cat} vindt een bestand ook
 *       zonder het hele pad ({@code cat security.conf} werkt overal).</li>
 *   <li><b>OVERRIDE</b>: {@code override <nummer>} opent de console van het beveiligingsprogramma.
 *       {@code status} laat zijn instellingen zien. Eerst {@code disable watchdog} (anders zet de
 *       waakhond de eigenaar meteen terug), dan {@code set owner <jouw naam>}, en de toren is van
 *       jou. {@code whoami} zegt hoe je heet.</li>
 * </ol>
 *
 * Deze klasse is alleen de logica (geen scherm), zodat de tests hem kunnen uitspelen. Alles wat
 * de terminal zegt, is Engels (je ziet het in het spel).
 *
 * Alles hangt af van het getal {@code seed} (de poorten, de woorden, de namen): met hetzelfde getal
 * en dezelfde commando's kom je precies op hetzelfde uit. Daarom onthoudt de laptop alleen dat getal
 * en wat je typte ({@link #replay}): zo ben je je voortgang niet kwijt als je het scherm sluit, en
 * kan de server nakijken of je de hack echt uitspeelde.
 */
public final class HackTerminal {
    public enum Stage { RECON, LOGIN, SHELL, OVERRIDE, DONE }

    /** Soort regel: bepaalt de kleur op het scherm. */
    public enum Kind { NORMAL, INPUT, ERROR, HEADER, HINT, GOOD }

    public record Line(String text, Kind kind) {}

    /**
     * De woorden voor de geheugendump, allemaal 6 letters. Veel eindigen hetzelfde (-ET, -ER, -ON,
     * ...): zo lijken de woorden in een dump op elkaar en vertelt elk fout woord je iets.
     */
    private static final String[] WORDS = {
            "ROCKET", "POCKET", "SOCKET", "JACKET", "TARGET", "MAGNET", "PLANET", "HELMET", "TABLET", "GADGET",
            "SECRET", "BUCKET",
            "HAMMER", "HACKER", "LOCKER", "DOCKER", "BUFFER", "FILTER", "ROUTER", "SERVER", "SENDER", "RENDER",
            "MASTER", "POSTER", "TESTER", "SILVER", "COPPER", "LEADER", "READER", "HEADER", "LOADER", "CUTTER",
            "BORDER",
            "CANNON", "BEACON", "PISTON", "PROTON", "PHOTON", "CARBON", "BUTTON", "LEGION", "FUSION", "VISION",
            "MOTION", "OPTION",
            "SIGNAL", "PORTAL", "NEURAL", "VECTOR", "SENSOR", "DOCTOR", "MIRROR",
            "JARVIS", "FRIDAY", "PEPPER", "MALIBU", "SHIELD", "STRIKE", "ORBITS", "CIPHER", "SYSTEM", "MATRIX",
            "TITANS", "DRONES", "SECURE", "ACCESS", "BREACH", "KERNEL", "BINARY", "ARMORS", "VAULTS", "LAUNCH"};
    /** Zoveel woorden staan er in een dump (het wachtwoord meegeteld). */
    private static final int DUMP_WORDS = 8;
    /** Uit zoveel woorden die het meest op het wachtwoord lijken, kiest hij de andere woorden. */
    private static final int CLOSEST = 12;
    private static final String[] GUARDS = {"sentinel", "aegis", "warden", "argus", "vigil", "bastion", "citadel"};
    private static final String[] NAMED_PORTS = {"jarvis-sync", "friday-link", "karen-api", "holo-stream",
            "vault-mon", "suit-telemetry"};
    private static final String JUNK = "!@#$%^&*()[]{}<>?/\\|+=-_;:'\",.";
    /** Zoveel poorten scant hij per seconde. */
    private static final int SCAN_RATE = 1500;

    private final Random random;
    private final List<Line> output = new ArrayList<>();
    private Stage stage = Stage.RECON;

    // RECON
    private final Map<Integer, String> ports = new LinkedHashMap<>();
    private final int openPort;
    private final int revokedPort;
    private long scanUntil;
    private int scanFrom;
    private int scanTo;

    // LOGIN
    private List<String> candidates = List.of();
    /** De woorden die nog het wachtwoord kunnen zijn (de rest is doorgestreept). */
    private final List<String> possible = new ArrayList<>();
    /** De regels van de dump (elke regel: rommel, een woord, rommel), zodat hij niet verspringt. */
    private final List<String[]> dumpRows = new ArrayList<>();
    private String password = "";
    private int attemptsLeft;
    private long lockedUntil;

    // OVERRIDE: de console van het beveiligingsprogramma
    /** Hoe de speler heet: dat moet de nieuwe eigenaar worden. */
    private final String playerName;
    private boolean watchdog = true;
    private String owner = "bibie-industries";

    // SHELL
    private final String guard;
    private final Map<String, Integer> processes = new LinkedHashMap<>();
    private final Map<String, String> files = new LinkedHashMap<>();
    private String cwd = "/";

    /** @param playerName hoe de speler heet (die moet in de laatste stap de eigenaar worden) */
    public HackTerminal(long seed, String playerName) {
        this.random = new Random(seed);
        this.playerName = playerName;
        // De poorten: bekende diensten (allemaal gefilterd) en een paar met een eigen naam.
        for (int port : new int[] {22, 80, 443, 3306, 5432, 8080, 8443}) {
            ports.put(port, switch (port) {
                case 22 -> "ssh";
                case 80 -> "http";
                case 443 -> "https";
                case 3306 -> "mysql";
                case 5432 -> "postgres";
                case 8080 -> "http-proxy";
                default -> "https-alt";
            });
        }
        List<String> names = new ArrayList<>(List.of(NAMED_PORTS));
        Collections.shuffle(names, random);
        List<Integer> high = new ArrayList<>();
        while (high.size() < 4) {
            int port = 1024 + random.nextInt(8976);
            if (!ports.containsKey(port) && !high.contains(port)) {
                high.add(port);
            }
        }
        for (int i = 0; i < high.size(); i++) {
            ports.put(high.get(i), names.get(i));
        }
        openPort = high.get(random.nextInt(high.size()));
        int other;
        do {
            other = high.get(random.nextInt(high.size()));
        } while (other == openPort);
        revokedPort = other;

        // Het beveiligingsprogramma en de andere processen.
        guard = GUARDS[random.nextInt(GUARDS.length)];
        List<String> procs = new ArrayList<>(List.of("init", "jarvis-backup", "holo-render", "elevator-ctl", "hvac",
                "coffee-maker", "turret-ctl", "cam-feed"));
        for (String decoy : GUARDS) {
            if (!decoy.equals(guard) && procs.size() < 12 && random.nextInt(2) == 0) {
                procs.add(decoy);
            }
        }
        procs.add(guard);
        Collections.shuffle(procs, random);
        for (String proc : procs) {
            int pid;
            do {
                pid = 100 + random.nextInt(9900);
            } while (processes.containsValue(pid));
            processes.put(proc, pid);
        }
        // De README staat bovenaan in de map waar je binnenkomt: stap voor stap wat je moet doen.
        // Een briefje van Bibie aan Happy: geen kant-en-klare commando's, wel alle aanwijzingen.
        files.put("/README.txt", "Happy,\n"
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
                + "- Bibie");
        files.put("/home/bibie/notes.txt", "TODO: rename the security daemon (again).\n"
                + "Rhodey keeps killing it by name. New name is in /etc/security.conf.\n"
                + "Also: stop leaving the maintenance port open. - T");
        files.put("/etc/security.conf", "# Bibie's Industries tower security\ndaemon = " + guard
                + "\nwatchdog = on\nrestart_on_kill = true\noverride_requires = pid");
        files.put("/etc/hostname", "bibie-sec-core");
        files.put("/var/log/boot.log", "[ ok ] turret-ctl online\n[ ok ] cam-feed online\n[ ok ] " + guard
                + " online (watchdog)\n[warn] coffee-maker: low on beans");
        files.put("/opt/jarvis/warning.txt", "JARVIS maintenance shell. Unauthorized access will be reported to\n"
                + "Bibie, Miss Potts and Happy. Mostly Happy.");
        header("BIBIE'S INDUSTRIES // TOWER SECURITY NODE 10.4.0.1");
        hint("Type 'help' for commands. Tab completes, arrow up repeats.");
    }

    // ================================================================ Opvragen

    public Stage stage() {
        return stage;
    }

    public List<Line> output() {
        return output;
    }

    /** Wat er voor je invoer staat. */
    public String prompt() {
        return switch (stage) {
            case RECON -> "kali@laptop:~$ ";
            case LOGIN -> "password: ";
            case SHELL -> "root@bibie-sec-core:" + cwd + "# ";
            case OVERRIDE, DONE -> guard + "> ";
        };
    }

    /** Is hij nog bezig (een scan of geblokkeerd)? Dan kun je even niets typen. */
    public boolean busy(long now) {
        return now < scanUntil || now < lockedUntil;
    }

    /** Hoe ver een lopende scan is (0 tot 1), of -1 als er geen scan loopt. */
    public double scanProgress(long now) {
        if (scanUntil == 0) {
            return -1;
        }
        long total = Math.max(1, (long) (scanTo - scanFrom + 1) * 1000 / SCAN_RATE);
        return Math.min(1.0, 1.0 - (scanUntil - now) / (double) total);
    }

    /** Elke tick: klaar met scannen of met geblokkeerd zijn? Dan de uitkomst tonen. */
    public void tick(long now) {
        if (scanUntil != 0 && now >= scanUntil) {
            scanUntil = 0;
            finishScan();
        }
        if (lockedUntil != 0 && now >= lockedUntil) {
            lockedUntil = 0;
            newDump();
        }
    }

    /** Het wachtwoord (voor de tests en de showcase). */
    public String password() {
        return password;
    }

    public int openPort() {
        return openPort;
    }

    public String guard() {
        return guard;
    }

    public int guardPid() {
        return processes.get(guard);
    }

    public List<String> candidates() {
        return candidates;
    }

    /** De woorden die na je foute pogingen nog het wachtwoord kunnen zijn. */
    public List<String> possible() {
        return possible;
    }

    /**
     * Speelt de commando's opnieuw af op een nieuwe terminal met hetzelfde getal: je komt precies
     * uit waar je was. Een scan of een blokkade na te veel foute wachtwoorden is dan meteen voorbij.
     */
    public static HackTerminal replay(long seed, String playerName, List<String> commands) {
        HackTerminal terminal = new HackTerminal(seed, playerName);
        long now = 0;
        for (String command : commands) {
            terminal.run(command, now);
            // Ruim na de langste scan (65535 poorten) en de blokkade: alles is dan klaar.
            now += 100_000;
            terminal.tick(now);
            now += 100_000;
        }
        return terminal;
    }

    /** Een regel "je gaat verder waar je was" (als het scherm opnieuw opengaat). */
    public void restored() {
        hint("Session restored from the laptop. You are where you left off.");
    }

    /** Klaar (of door de server afgebroken): er kan niets meer getypt worden. */
    public void overrideDone() {
        stage = Stage.DONE;
    }

    // ================================================================ Een commando uitvoeren

    public void run(String rawInput, long now) {
        String input = rawInput.strip();
        if (busy(now) || stage == Stage.DONE) {
            return;
        }
        output.add(new Line(prompt() + input, Kind.INPUT));
        if (input.isEmpty()) {
            return;
        }
        String[] parts = input.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String argument = parts.length > 1 ? parts[1].strip() : "";
        if (command.equals("clear")) {
            output.clear();
            return;
        }
        if (command.equals("help")) {
            help();
            return;
        }
        switch (stage) {
            case RECON -> recon(command, argument, now);
            case LOGIN -> login(command, argument, now);
            case SHELL -> shell(command, argument);
            case OVERRIDE -> console(command, argument);
            default -> { }
        }
    }

    private void help() {
        switch (stage) {
            case RECON -> {
                normal("scan [from-to]   scan the ports of the security node (default 1-9999)");
                normal("logs             read the firewall log");
                normal("connect <port>   connect to a port");
                hint("Every port looks filtered. The firewall log knows which one is really open.");
            }
            case LOGIN -> {
                normal("login <word>     try a password from the memory dump (or just type the word)");
                normal("dump             show the memory dump again");
                hint("A wrong word shows which of its letters are in the same place as in the password,");
                hint("like ROCKET -> __CKET. Words that can't be the password get crossed out.");
            }
            case SHELL -> {
                normal("ls [dir]         list files       cd <dir>   change directory");
                normal("cat <file>       read a file      ps         list processes");
                normal("override <pid>   take over a process");
                hint("Not sure what to do? Read the README: cat README.txt");
            }
            case OVERRIDE -> {
                normal("status             show the settings of " + guard);
                normal("disable <setting>  switch a setting off (enable switches it on again)");
                normal("set owner <name>   give " + guard + " a new owner");
                normal("whoami             your name");
                hint("Take it over: disable watchdog, then set owner <your name>.");
            }
            default -> { }
        }
        normal("clear            clear the screen");
    }

    // ---------------------------------------------------------------- RECON

    private void recon(String command, String argument, long now) {
        switch (command) {
            case "scan", "nmap" -> {
                int from = 1;
                int to = 9999;
                if (!argument.isEmpty()) {
                    String[] range = argument.replace(" ", "").split("-");
                    try {
                        from = Integer.parseInt(range[0]);
                        to = range.length > 1 ? Integer.parseInt(range[1]) : from;
                    } catch (NumberFormatException e) {
                        error("usage: scan 1-9999");
                        return;
                    }
                }
                from = Math.max(1, Math.min(from, 65535));
                to = Math.max(from, Math.min(to, 65535));
                scanFrom = from;
                scanTo = to;
                scanUntil = now + Math.max(300, (long) (to - from + 1) * 1000 / SCAN_RATE);
                normal("Scanning 10.4.0.1 ports " + from + "-" + to + " ...");
            }
            case "logs", "cat" -> {
                header("/var/log/firewall.log");
                List<String> lines = new ArrayList<>();
                String[] ips = {"10.4.0.77", "10.4.0.23", "192.168.1.4", "10.4.0.12", "172.16.0.9"};
                for (int port : ports.keySet()) {
                    if (port != openPort && port != revokedPort && random.nextInt(3) > 0) {
                        lines.add("DENY   tcp " + ips[random.nextInt(ips.length)] + " -> :" + port);
                    }
                }
                lines.add("ALLOW  tcp 10.4.0.12 -> :" + revokedPort + "  (maintenance window)");
                lines.add("ALLOW  tcp 10.4.0.12 -> :" + openPort + "  (maintenance window)");
                Collections.shuffle(lines, random);
                lines.add("RULE   ALLOW :" + revokedPort + " removed by admin");
                lines.add("DENY   tcp 10.4.0.77 -> :22");
                int minute = 12;
                for (String line : lines) {
                    normal(String.format("08:%02d:%02d  %s", minute, random.nextInt(60), line));
                    minute += 1 + random.nextInt(3);
                }
            }
            case "connect", "ssh", "nc", "telnet" -> {
                int port;
                try {
                    port = Integer.parseInt(argument);
                } catch (NumberFormatException e) {
                    error("usage: connect <port>");
                    return;
                }
                if (port != openPort) {
                    error("connect to 10.4.0.1:" + port + ": connection refused (filtered)");
                    return;
                }
                good("Connected to 10.4.0.1:" + port + " (" + ports.get(port) + ")");
                header("JARVIS MAINTENANCE SHELL - AUTHENTICATION REQUIRED");
                stage = Stage.LOGIN;
                newDump();
            }
            default -> error(command + ": command not found (try 'help')");
        }
    }

    private void finishScan() {
        normal("PORT     STATE     SERVICE");
        int shown = 0;
        for (Map.Entry<Integer, String> port : ports.entrySet()) {
            if (port.getKey() >= scanFrom && port.getKey() <= scanTo) {
                normal(String.format("%-8s %-9s %s", port.getKey() + "/tcp", "filtered", port.getValue()));
                shown++;
            }
        }
        if (shown == 0) {
            normal("(no ports found in this range)");
        }
        hint("All filtered. Somebody opened one for maintenance... check the logs.");
    }

    // ---------------------------------------------------------------- LOGIN

    /**
     * Een nieuwe geheugendump met acht woorden, waarvan één het wachtwoord is. De andere zeven
     * kiest hij uit de woorden die het meest op het wachtwoord lijken (dezelfde letters op
     * dezelfde plek), zodat een fout woord je echt iets vertelt.
     */
    private void newDump() {
        password = WORDS[random.nextInt(WORDS.length)];
        List<String> others = new ArrayList<>(List.of(WORDS));
        others.remove(password);
        Collections.shuffle(others, random);
        // Meest gelijkend eerst (bij gelijkspel blijft de willekeurige volgorde staan).
        others.sort(java.util.Comparator.comparingInt(word -> -likeness(word, password)));
        List<String> close = new ArrayList<>(others.subList(0, CLOSEST));
        Collections.shuffle(close, random);
        List<String> words = new ArrayList<>(close.subList(0, DUMP_WORDS - 1));
        words.add(password);
        Collections.shuffle(words, random);
        candidates = List.copyOf(words);
        possible.clear();
        possible.addAll(candidates);
        attemptsLeft = Math.round(ImtConfig.Security.PASSWORD_ATTEMPTS);
        dumpRows.clear();
        for (String word : candidates) {
            dumpRows.add(new String[] {junk(random.nextInt(9)), word, ""});
        }
        for (String[] row : dumpRows) {
            row[2] = junk(Math.max(0, 18 - row[0].length() - row[1].length()));
        }
        dump();
        hint("One of these words is the password. Type one to try it.");
        hint("A wrong word shows which letters are in the right place.");
    }

    private String junk(int length) {
        StringBuilder junk = new StringBuilder();
        for (int i = 0; i < length; i++) {
            junk.append(JUNK.charAt(random.nextInt(JUNK.length())));
        }
        return junk.toString();
    }

    /** De dump laten zien; doorgestreepte woorden worden streepjes. */
    private void dump() {
        header("MEMORY DUMP 0xF400-0xF4E0   attempts left: " + attemptsLeft);
        for (int row = 0; row < dumpRows.size(); row++) {
            String[] parts = dumpRows.get(row);
            String word = possible.contains(parts[1]) ? parts[1] : "-".repeat(parts[1].length());
            normal(String.format("0x%04X  %s%s%s", 0xF400 + row * 16, parts[0], word, parts[2]));
        }
    }

    private void login(String command, String argument, long now) {
        if (command.equals("dump")) {
            dump();
            return;
        }
        // "login woord", of gewoon het woord zelf.
        String word = (command.equals("login") ? argument : command).toUpperCase(Locale.ROOT);
        if (word.isEmpty()) {
            error("usage: login <word>");
            return;
        }
        if (word.equals(password)) {
            good("ACCESS GRANTED. Welcome back, Bibie.");
            stage = Stage.SHELL;
            hint("You are in. Bibie left a note in README.txt (read a file with: cat <file>)");
            return;
        }
        if (!candidates.contains(word)) {
            error("'" + word + "' is not in the memory dump");
            return;
        }
        if (!possible.contains(word)) {
            error(word + " is already crossed out: it can't be the password.");
            return;
        }
        attemptsLeft--;
        int same = likeness(word, password);
        error("ACCESS DENIED. Likeness " + same + "/" + password.length() + "   attempts left: " + attemptsLeft);
        String pattern = pattern(word, password);
        hint(word + " -> " + pattern + "   " + (same == 0 ? "no letters in the right place"
                : same == 1 ? "1 letter in the right place" : same + " letters in the right place"));
        // Alles wat niet hetzelfde patroon geeft, kan het wachtwoord niet zijn.
        List<String> out = new ArrayList<>();
        possible.removeIf(candidate -> {
            boolean gone = !pattern(word, candidate).equals(pattern);
            if (gone) {
                out.add(candidate);
            }
            return gone;
        });
        out.remove(word);
        if (!out.isEmpty()) {
            normal("Crossed out: " + String.join(", ", out) + " (they don't match " + pattern + ")");
        }
        good("Still possible: " + String.join("  ", possible));
        if (attemptsLeft <= 0) {
            error("TOO MANY ATTEMPTS. Terminal locked, new memory dump coming...");
            lockedUntil = now + Math.round(ImtConfig.Security.PASSWORD_LOCKOUT_MILLIS);
        }
    }

    /**
     * Welke letters van {@code guess} op dezelfde plek staan in {@code password}; de andere
     * worden een streepje. ROCKET tegen POCKET geeft "_OCKET".
     */
    public static String pattern(String guess, String password) {
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < guess.length(); i++) {
            pattern.append(i < password.length() && guess.charAt(i) == password.charAt(i) ? guess.charAt(i) : '_');
        }
        return pattern.toString();
    }

    /** Hoeveel letters staan op dezelfde plek in beide woorden? */
    public static int likeness(String a, String b) {
        int same = 0;
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
            if (a.charAt(i) == b.charAt(i)) {
                same++;
            }
        }
        return same;
    }

    // ---------------------------------------------------------------- SHELL

    private void shell(String command, String argument) {
        switch (command) {
            case "ls" -> {
                String dir = argument.isEmpty() ? cwd : resolve(argument);
                List<String> entries = children(dir);
                if (entries.isEmpty()) {
                    error("ls: " + dir + ": no such directory");
                } else {
                    normal(String.join("   ", entries));
                }
            }
            case "cd" -> {
                String dir = argument.isEmpty() ? "/" : resolve(argument);
                if (!dir.equals("/") && children(dir).isEmpty()) {
                    error("cd: " + dir + ": no such directory");
                } else {
                    cwd = dir;
                }
            }
            case "cat", "less", "more" -> {
                String file = find(argument);
                String content = file == null ? null : files.get(file);
                if (content == null) {
                    error("cat: " + argument + ": no such file (try 'ls' to see the files here)");
                } else {
                    for (String line : content.split("\n")) {
                        normal(line);
                    }
                }
            }
            case "ps", "top" -> {
                normal("  PID  COMMAND");
                for (Map.Entry<String, Integer> proc : processes.entrySet()) {
                    normal(String.format("%5d  %s", proc.getValue(), proc.getKey()));
                }
            }
            case "kill" -> error("kill: watchdog restarted the process. You need to OVERRIDE it, not kill it.");
            case "override" -> {
                int pid;
                try {
                    pid = Integer.parseInt(argument);
                } catch (NumberFormatException e) {
                    // Een naam in plaats van een nummer? Dan zeggen waar het nummer staat.
                    if (processes.containsKey(argument.toLowerCase(Locale.ROOT))) {
                        error("override needs the number (PID) of '" + argument.toLowerCase(Locale.ROOT)
                                + "', not its name. Type 'ps' to see the numbers.");
                    } else {
                        error("usage: override <pid>   (the numbers are in 'ps')");
                    }
                    return;
                }
                if (pid != processes.get(guard)) {
                    String name = processes.entrySet().stream().filter(entry -> entry.getValue() == pid)
                            .map(Map.Entry::getKey).findFirst().orElse(null);
                    error(name == null ? "override: no process " + pid + " (the numbers are in 'ps')"
                            : "override: '" + name + "' is not the security daemon. Its real name is in"
                            + " security.conf.");
                    return;
                }
                header("ATTACHED TO '" + guard.toUpperCase(Locale.ROOT) + "' (PID " + pid + ") - DAEMON CONSOLE");
                stage = Stage.OVERRIDE;
                status();
                hint("You're inside " + guard + ". Type 'help' to see what you can change.");
            }
            case "pwd" -> normal(cwd);
            case "whoami" -> normal("root (probably not Bibie)");
            default -> error(command + ": command not found (try 'help')");
        }
    }

    /**
     * Het bestand bij wat je typt: eerst gewoon (vanaf de map waar je bent), en anders zoeken op
     * alleen de naam, zodat {@code cat security.conf} ook werkt als je niet in /etc staat.
     */
    private String find(String argument) {
        String path = resolve(argument);
        if (files.containsKey(path)) {
            return path;
        }
        String name = argument.contains("/") ? argument.substring(argument.lastIndexOf('/') + 1) : argument;
        List<String> matches = files.keySet().stream()
                .filter(file -> file.substring(file.lastIndexOf('/') + 1).equalsIgnoreCase(name)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    // ---------------------------------------------------------------- OVERRIDE

    /**
     * De console van het beveiligingsprogramma. Twee dingen om te doen: de waakhond uitzetten
     * (anders zet die de eigenaar meteen terug) en jezelf eigenaar maken.
     */
    private void console(String command, String argument) {
        String arg = argument.toLowerCase(Locale.ROOT);
        switch (command) {
            case "status", "show", "config" -> status();
            case "whoami" -> normal(playerName);
            case "disable", "enable" -> {
                boolean on = command.equals("enable");
                if (arg.isEmpty()) {
                    error("usage: " + command + " <setting>   (try: " + command + " watchdog)");
                } else if (arg.equals("watchdog")) {
                    watchdog = on;
                    if (on) {
                        normal("watchdog = on");
                    } else {
                        good("watchdog = off. It won't reset the owner any more.");
                    }
                } else if (arg.equals("owner") || arg.equals("turrets") || arg.equals("drones")) {
                    error(command + ": '" + arg + "' can't be switched off. Try: set owner <your name>");
                } else {
                    error(command + ": no setting '" + arg + "' (see 'status')");
                }
            }
            case "set" -> {
                // "set owner naam" of "set owner=naam"
                String[] parts = argument.replace("=", " ").strip().split("\\s+", 2);
                if (parts.length < 2 || !parts[0].equalsIgnoreCase("owner")) {
                    error("usage: set owner <name>");
                    return;
                }
                String name = parts[1].strip();
                if (watchdog) {
                    error("WATCHDOG: owner change reverted (owner = " + owner + ").");
                    hint("Switch the watchdog off first: disable watchdog");
                    return;
                }
                if (!name.equalsIgnoreCase(playerName)) {
                    error("set owner: no user '" + name + "' on this network. Type 'whoami' for your name.");
                    return;
                }
                owner = playerName;
                good("owner = " + owner);
                good("SECURITY DAEMON '" + guard.toUpperCase(Locale.ROOT) + "' NOW TAKES ORDERS FROM "
                        + playerName.toUpperCase(Locale.ROOT) + ".");
                stage = Stage.DONE;
            }
            case "kill" -> error("kill: you are INSIDE the daemon now. Take it over instead (see 'help').");
            default -> error(command + ": unknown command (try 'help')");
        }
    }

    /** De instellingen van het beveiligingsprogramma. */
    private void status() {
        normal("owner     = " + owner);
        if (watchdog) {
            output.add(new Line("watchdog  = on   (puts the owner back if anyone changes it)", Kind.ERROR));
        } else {
            normal("watchdog  = off");
        }
        normal("turrets   = fire at intruders");
        normal("drones    = hunt intruders");
    }

    /** Maakt van "../etc" of "notes.txt" een volledig pad. */
    private String resolve(String path) {
        String full = path.startsWith("/") ? path : (cwd.equals("/") ? "" : cwd) + "/" + path;
        List<String> parts = new ArrayList<>();
        for (String part : full.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.removeLast();
                }
            } else {
                parts.add(part);
            }
        }
        return "/" + String.join("/", parts);
    }

    /** Wat er direct in deze map zit (mappen eindigen op /). */
    private List<String> children(String dir) {
        String prefix = dir.equals("/") ? "/" : dir + "/";
        List<String> result = new ArrayList<>();
        for (String file : files.keySet()) {
            if (file.startsWith(prefix)) {
                String rest = file.substring(prefix.length());
                String entry = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) + "/" : rest;
                if (!result.contains(entry)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    // ================================================================ Aanvullen met Tab

    /** Vult het laatste woord van de invoer aan (commando, poort, woord, bestand). */
    public String complete(String input) {
        String[] parts = input.split(" ", -1);
        String last = parts[parts.length - 1];
        List<String> options = new ArrayList<>();
        if (parts.length == 1) {
            options.addAll(switch (stage) {
                case RECON -> List.of("scan", "logs", "connect", "help", "clear");
                case LOGIN -> List.of("login", "dump", "help", "clear");
                case SHELL -> List.of("ls", "cd", "cat", "ps", "override", "help", "clear", "kill", "pwd");
                case OVERRIDE -> List.of("status", "disable", "enable", "set", "whoami", "help", "clear");
                default -> List.of();
            });
        } else if (stage == Stage.LOGIN) {
            options.addAll(possible);
        } else if (stage == Stage.OVERRIDE) {
            // disable/enable -> watchdog; set -> owner; set owner -> jouw naam.
            if (parts.length == 2 && (parts[0].equals("disable") || parts[0].equals("enable"))) {
                options.add("watchdog");
            } else if (parts.length == 2 && parts[0].equals("set")) {
                options.add("owner");
            } else if (parts.length == 3 && parts[0].equals("set") && parts[1].equals("owner")) {
                options.add(playerName);
            }
        } else if (stage == Stage.SHELL && (parts[0].equals("cat") || parts[0].equals("cd") || parts[0].equals("ls"))) {
            int slash = last.lastIndexOf('/');
            String dirPart = slash >= 0 ? last.substring(0, slash + 1) : "";
            String dir = dirPart.isEmpty() ? cwd : resolve(dirPart);
            for (String child : children(dir)) {
                options.add(dirPart + child);
            }
            if (parts[0].equals("cat") && dirPart.isEmpty()) {
                // cat vindt een bestand ook op alleen zijn naam (zie find), dus Tab ook.
                for (String file : files.keySet()) {
                    String name = file.substring(file.lastIndexOf('/') + 1);
                    if (!options.contains(name)) {
                        options.add(name);
                    }
                }
            }
        }
        List<String> matches = options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(last.toLowerCase(Locale.ROOT))).toList();
        if (matches.size() != 1) {
            return input;
        }
        parts[parts.length - 1] = matches.getFirst();
        String completed = String.join(" ", parts);
        return completed.endsWith("/") || parts.length > 1 ? completed : completed + " ";
    }

    // ================================================================ Hulpjes

    private void normal(String text) {
        output.add(new Line(text, Kind.NORMAL));
    }

    private void error(String text) {
        output.add(new Line(text, Kind.ERROR));
    }

    private void header(String text) {
        output.add(new Line(text, Kind.HEADER));
    }

    private void hint(String text) {
        output.add(new Line(text, Kind.HINT));
    }

    private void good(String text) {
        output.add(new Line(text, Kind.GOOD));
    }
}
