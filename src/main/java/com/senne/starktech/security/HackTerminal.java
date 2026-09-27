package com.senne.starktech.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * De puzzel achter de hack van een Stark Tower: een nep-terminal waar je commando's in typt
 * (jouw wens, 27 september: typen, zoeken naar de open poort, inbreken, het beveiligingsprogramma
 * vinden en overnemen, met verschillende soorten puzzels).
 *
 * Vier stappen:
 * <ol>
 *   <li><b>RECON</b>: {@code scan} laat de poorten zien, maar ze staan allemaal op "filtered". In het
 *       firewall-logboek ({@code logs}) staan twee poorten die toegelaten zijn; één daarvan is later
 *       weer dichtgezet. Met {@code connect <poort>} op de goede kom je binnen.</li>
 *   <li><b>LOGIN</b>: een geheugendump vol tekens met woorden ertussen. Eén woord is het wachtwoord.
 *       Typ {@code login <woord>}; is het fout, dan zegt de terminal hoeveel letters op de goede
 *       plek staan ("likeness"). Vier pogingen, daarna even geblokkeerd en een nieuwe dump.</li>
 *   <li><b>SHELL</b>: zoek in de bestanden ({@code ls}, {@code cd}, {@code cat}) hoe het
 *       beveiligingsprogramma heet (er draaien er een paar met zo'n naam), en met {@code ps} zijn
 *       procesnummer.</li>
 *   <li><b>OVERRIDE</b>: {@code override <nummer>}. Dan nog één laag met de timingbalk (die tekent
 *       het scherm zelf; zie HackScreen), en de toren is van jou.</li>
 * </ol>
 *
 * Deze klasse is alleen de logica (geen scherm), zodat de tests hem kunnen uitspelen. Alles wat
 * de terminal zegt, is Engels (je ziet het in het spel).
 */
public final class HackTerminal {
    public enum Stage { RECON, LOGIN, SHELL, OVERRIDE, DONE }

    /** Soort regel: bepaalt de kleur op het scherm. */
    public enum Kind { NORMAL, INPUT, ERROR, HEADER, HINT, GOOD }

    public record Line(String text, Kind kind) {}

    private static final String[] WORDS = {"SHIELD", "PEPPER", "FRIDAY", "JARVIS", "HAMMER", "MALIBU", "STRIKE",
            "ORBITS", "VECTOR", "CIPHER", "SYSTEM", "ROCKET", "MATRIX", "NEURAL", "SIGNAL", "FUSION", "TITANS",
            "LEGION", "DRONES", "SECURE", "ACCESS", "BREACH", "KERNEL", "BINARY", "REPULS", "ARMORS", "VAULTS",
            "SENSOR", "LAUNCH", "CANNON", "TARGET", "BEACON"};
    private static final String[] GUARDS = {"sentinel", "aegis", "warden", "argus", "vigil", "bastion", "citadel"};
    private static final String[] NAMED_PORTS = {"jarvis-sync", "friday-link", "karen-api", "holo-stream",
            "vault-mon", "suit-telemetry"};
    private static final String JUNK = "!@#$%^&*()[]{}<>?/\\|+=-_;:'\",.";
    /** Zo vaak mag je een fout wachtwoord proberen, en zo lang ben je daarna geblokkeerd (ms). */
    public static final int ATTEMPTS = 4;
    public static final long LOCKOUT_MILLIS = 4000;
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
    private String password = "";
    private int attemptsLeft;
    private long lockedUntil;

    // SHELL
    private final String guard;
    private final Map<String, Integer> processes = new LinkedHashMap<>();
    private final Map<String, String> files = new LinkedHashMap<>();
    private String cwd = "/";

    public HackTerminal(long seed) {
        this.random = new Random(seed);
        // De poorten: bekende diensten (allemaal gefilterd) en een paar met een Stark-naam.
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
        files.put("/home/tony/notes.txt", "TODO: rename the security daemon (again).\n"
                + "Rhodey keeps killing it by name. New name is in /etc/security.conf.\n"
                + "Also: stop leaving the maintenance port open. - T");
        files.put("/etc/security.conf", "# Stark Industries tower security\ndaemon = " + guard
                + "\nwatchdog = on\nrestart_on_kill = true\noverride_requires = pid");
        files.put("/etc/hostname", "stark-sec-core");
        files.put("/var/log/boot.log", "[ ok ] turret-ctl online\n[ ok ] cam-feed online\n[ ok ] " + guard
                + " online (watchdog)\n[warn] coffee-maker: low on beans");
        files.put("/opt/jarvis/readme.txt", "JARVIS maintenance shell. Unauthorized access will be reported to\n"
                + "Mr. Stark, Miss Potts and Happy. Mostly Happy.");
        header("STARK INDUSTRIES // TOWER SECURITY NODE 10.4.0.1");
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
            case SHELL, OVERRIDE, DONE -> "root@stark-sec-core:" + cwd + "# ";
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

    /** De laatste timingbalk is gehaald: de toren is van jou. */
    public void overrideDone() {
        stage = Stage.DONE;
        good("SECURITY DAEMON '" + guard.toUpperCase(Locale.ROOT) + "' NOW TAKES ORDERS FROM YOU.");
    }

    // ================================================================ Een commando uitvoeren

    public void run(String rawInput, long now) {
        String input = rawInput.strip();
        if (busy(now) || stage == Stage.OVERRIDE || stage == Stage.DONE) {
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
                normal("login <word>     try a password from the memory dump");
                normal("dump             show the memory dump again");
                hint("A wrong word tells you how many letters are right AND in the right place.");
            }
            case SHELL -> {
                normal("ls [dir]         list files       cd <dir>   change directory");
                normal("cat <file>       read a file      ps         list processes");
                normal("override <pid>   take over a process");
                hint("Several daemons sound like security. Find the real name in the config.");
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

    /** Een nieuwe geheugendump met acht woorden, waarvan één het wachtwoord is. */
    private void newDump() {
        List<String> pool = new ArrayList<>(List.of(WORDS));
        Collections.shuffle(pool, random);
        candidates = List.copyOf(pool.subList(0, 8));
        password = candidates.get(random.nextInt(candidates.size()));
        attemptsLeft = ATTEMPTS;
        dump();
    }

    private void dump() {
        header("MEMORY DUMP 0xF400-0xF4E0   attempts left: " + attemptsLeft);
        List<String> words = new ArrayList<>(candidates);
        Collections.shuffle(words, random);
        for (int row = 0; row < words.size(); row++) {
            StringBuilder junk = new StringBuilder();
            int before = random.nextInt(9);
            for (int i = 0; i < before; i++) {
                junk.append(JUNK.charAt(random.nextInt(JUNK.length())));
            }
            junk.append(words.get(row));
            while (junk.length() < 18) {
                junk.append(JUNK.charAt(random.nextInt(JUNK.length())));
            }
            normal(String.format("0x%04X  %s", 0xF400 + row * 16, junk));
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
            good("ACCESS GRANTED. Welcome back, Mr. Stark.");
            stage = Stage.SHELL;
            hint("You are in. Find the security daemon and override it.");
            return;
        }
        if (!candidates.contains(word)) {
            error("'" + word + "' is not in the memory dump");
            return;
        }
        attemptsLeft--;
        error("ACCESS DENIED. Likeness " + likeness(word, password) + "/" + password.length()
                + "   attempts left: " + attemptsLeft);
        if (attemptsLeft <= 0) {
            error("TOO MANY ATTEMPTS. Terminal locked, new memory dump coming...");
            lockedUntil = now + LOCKOUT_MILLIS;
        }
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
                String file = resolve(argument);
                String content = files.get(file);
                if (content == null) {
                    error("cat: " + argument + ": no such file");
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
                    error("usage: override <pid>");
                    return;
                }
                if (pid != processes.get(guard)) {
                    String name = processes.entrySet().stream().filter(entry -> entry.getValue() == pid)
                            .map(Map.Entry::getKey).findFirst().orElse(null);
                    error(name == null ? "override: no process " + pid
                            : "override: '" + name + "' is not the security daemon");
                    return;
                }
                header("OVERRIDING '" + guard.toUpperCase(Locale.ROOT) + "' - SYNCHRONIZE THE HANDSHAKE");
                hint("SPACE or click when the cursor is in the green zone.");
                stage = Stage.OVERRIDE;
            }
            case "pwd" -> normal(cwd);
            case "whoami" -> normal("root (probably not Tony)");
            default -> error(command + ": command not found (try 'help')");
        }
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
                default -> List.of();
            });
        } else if (stage == Stage.LOGIN) {
            options.addAll(candidates);
        } else if (stage == Stage.SHELL && (parts[0].equals("cat") || parts[0].equals("cd") || parts[0].equals("ls"))) {
            int slash = last.lastIndexOf('/');
            String dirPart = slash >= 0 ? last.substring(0, slash + 1) : "";
            String dir = dirPart.isEmpty() ? cwd : resolve(dirPart);
            for (String child : children(dir)) {
                options.add(dirPart + child);
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
