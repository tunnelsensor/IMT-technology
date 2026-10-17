package com.senne.imttech.security.os;

import com.senne.imttech.security.SecurityBalance;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * De terminal van de hacklaptop, maar dan op de SERVER (jouw wens, 28 september: de hack
 * logischer, en het echte wachtwoord/de mappen van iemand mogen niet in het spel van een hacker
 * belanden). De laptop in je spel tekent alleen de regels die deze klasse maakt.
 *
 * De stappen volgen nu logisch op elkaar:
 * <ol>
 *   <li><b>LOCAL</b> (de laptop zelf): {@code netscan} toont de systemen in de buurt met hun IP,
 *       {@code curl <ip>} toont hun statuspagina (met het firewall-logboek dat de open poort
 *       verraadt), en {@code ssh admin@<ip> -p <poort>} verbindt.</li>
 *   <li><b>LOGIN</b>: een systeem zónder wachtwoord (de oude firmware van Biebia) lekt een
 *       geheugendump; een systeem mét wachtwoord vraagt gewoon het wachtwoord.</li>
 *   <li><b>SHELL</b>: zoek in de ECHTE mappen van de eigenaar ({@code ls}, {@code cd}, {@code cat},
 *       {@code find}, {@code grep}) naar de naam van het beveiligingsprogramma, en {@code ps} voor
 *       zijn nummer.</li>
 *   <li><b>CONSOLE</b>: {@code override <nummer>}, {@code disable watchdog}, {@code set owner <naam>}.</li>
 * </ol>
 *
 * Hacken (binnenkomen zonder het wachtwoord te weten) mag alleen binnen het beschermde gebied
 * (waar de torentjes op je schieten). Inloggen mét het wachtwoord mag overal (zie de manager).
 */
public final class SystemShell {
    public enum Phase { LOCAL, LOGIN, SHELL, CONSOLE, DONE }

    /** Voor de balk bovenaan het scherm: welke van de vier stappen je bezig bent. */
    public enum Step { RECON, BREACH, SEARCH, OVERRIDE }

    private static final int MAX_OUTPUT = 300;

    private final List<TermLine> output = new ArrayList<>();
    private Phase phase = Phase.LOCAL;
    private @Nullable BlockPos core;
    private String cwd = "/";
    private boolean authed;
    private boolean watchdogOff;
    private long busyUntil;

    // Inloggen
    private @Nullable DumpPuzzle dump;
    private int loginAttempts;

    // De processen van het systeem waar je op zit (echt + nep), uit de systemSeed.
    private final Map<String, Integer> processes = new LinkedHashMap<>();
    private String daemon = "";

    public SystemShell() {
        output.add(TermLine.header("PORTABLE TERMINAL"));
        output.add(TermLine.hint("Type 'help' for commands. 'netscan' finds systems near you."));
    }

    // ================================================================ Opvragen

    public List<TermLine> output() {
        return output;
    }

    public Phase phase() {
        return phase;
    }

    public @Nullable BlockPos connectedCore() {
        return core;
    }

    public boolean authed() {
        return authed;
    }

    public boolean busy(long gameTime) {
        return gameTime < busyUntil;
    }

    public long busyUntil() {
        return busyUntil;
    }

    /** De prompt voor je invoerregel. */
    public String prompt(ServerLevel level) {
        return switch (phase) {
            case LOCAL -> "kali@laptop:~$ ";
            case LOGIN -> "password: ";
            case SHELL -> "root@" + host(level) + ":" + cwd + "# ";
            case CONSOLE, DONE -> daemon + "> ";
        };
    }

    /** Welke van de vier stappen (voor de balk bovenaan). */
    public Step step() {
        return switch (phase) {
            case LOCAL -> Step.RECON;
            case LOGIN -> Step.BREACH;
            case SHELL -> Step.SEARCH;
            case CONSOLE, DONE -> Step.OVERRIDE;
        };
    }

    private String host(ServerLevel level) {
        SecurityCoreBlockEntity be = coreEntity(level);
        return be == null ? "system" : be.hostname();
    }

    private @Nullable SecurityCoreBlockEntity coreEntity(ServerLevel level) {
        return core != null && level.getBlockEntity(core) instanceof SecurityCoreBlockEntity be ? be : null;
    }

    // ================================================================ Een commando

    /** De speler typte iets. Geeft true als er iets gebeurde dat de wereld verandert (voor de manager). */
    public void run(ServerPlayer player, ServerLevel level, String rawInput) {
        String input = rawInput.strip();
        long now = level.getGameTime();
        if (busy(now) || phase == Phase.DONE) {
            return;
        }
        output.add(TermLine.input(prompt(level) + input));
        cap();
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
            cap();
            return;
        }
        switch (phase) {
            case LOCAL -> local(player, level, command, argument);
            case LOGIN -> login(player, level, command, argument);
            case SHELL -> shell(player, level, command, argument);
            case CONSOLE -> console(player, level, command, argument);
            default -> { }
        }
        cap();
    }

    private void help() {
        switch (phase) {
            case LOCAL -> {
                normal("netscan          find security systems near you");
                normal("curl <ip>        read a system's status page");
                normal("ssh admin@<ip> -p <port>   connect to a system");
                hint("Start with netscan. The status page tells you which port is open.");
            }
            case LOGIN -> {
                if (dump != null) {
                    normal("<word>           try a password from the memory dump");
                    normal("dump             show the memory dump again");
                    hint("Old firmware leaked its memory. One of those words is the password.");
                } else {
                    normal("<password>       try the password");
                    hint("This system has a real password. Guess it, or find another way in.");
                }
            }
            case SHELL -> {
                normal("ls [dir]   cd <dir>   cat <file>   pwd");
                normal("find <name>      search for a file by name");
                normal("grep -r <text>   search inside every file");
                normal("ps               list processes    override <pid>   take one over");
                hint("The security config names the real daemon. It may be hidden. Search for it.");
            }
            case CONSOLE -> {
                normal("status             show the settings of " + daemon);
                normal("disable watchdog   stop it resetting the owner");
                normal("set owner <name>   give " + daemon + " a new owner");
                normal("whoami             your name");
                hint("Take it over: disable watchdog, then set owner <your name>.");
            }
            default -> { }
        }
        normal("clear            clear the screen");
    }

    // ---------------------------------------------------------------- LOCAL: netscan / curl / ssh

    private void local(ServerPlayer player, ServerLevel level, String command, String argument) {
        switch (command) {
            case "netscan", "nmap", "scan" -> netscan(player, level);
            case "curl", "wget" -> curl(level, argument);
            case "ssh", "connect", "telnet", "nc" -> ssh(player, level, argument);
            default -> error(command + ": command not found (try 'help')");
        }
    }

    private void netscan(ServerPlayer player, ServerLevel level) {
        List<SecurityCoreBlockEntity> found = SecurityCores.scannable(level, player.blockPosition());
        if (found.isEmpty()) {
            normal("No systems responded. A security node only answers on its own local network.");
            hint("Get closer to a security core (a Biebia's Industries Tower, or someone's base).");
            return;
        }
        header("HOSTS UP  (local network)");
        normal(String.format("%-14s %-18s %s", "IP", "HOSTNAME", "OWNER"));
        for (SecurityCoreBlockEntity be : found) {
            String owner = be.ownerName().isEmpty() ? "unclaimed" : be.ownerName();
            normal(String.format("%-14s %-18s %s", be.ip(), be.hostname(), owner));
        }
        hint("Read a status page: curl <ip>");
    }

    private void curl(ServerLevel level, String ip) {
        SecurityCoreBlockEntity be = coreByIp(level, ip);
        if (be == null) {
            error("curl: could not connect to " + (ip.isEmpty() ? "(no ip)" : ip) + " (is it on this network?)");
            return;
        }
        header("http://" + be.ip() + "/status  --  " + be.hostname());
        normal("Security Node. Firewall active. See /var/log/firewall.log below.");
        Random random = new Random(be.systemSeed() ^ 0x0C0FFEEL);
        List<String> lines = new ArrayList<>();
        String[] ips = {"10.4.0.77", "10.4.0.23", "192.168.1.4", "10.4.0.12", "172.16.0.9"};
        for (int i = 0; i < 4; i++) {
            int junkPort = 1024 + random.nextInt(60000);
            if (junkPort != be.sshPort()) {
                lines.add("DENY   tcp " + ips[random.nextInt(ips.length)] + " -> :" + junkPort);
            }
        }
        // De open poort (de SSH-poort). Bij Biebia's systeem ook een tweede die weer dicht ging.
        boolean biebia = be.ownerName().isEmpty();
        int revoked = 1024 + random.nextInt(60000);
        if (biebia) {
            lines.add("ALLOW  tcp 10.4.0.12 -> :" + revoked + "  (maintenance window)");
        }
        lines.add("ALLOW  tcp 10.4.0.12 -> :" + be.sshPort() + "  (maintenance window)");
        Collections.shuffle(lines, random);
        if (biebia) {
            lines.add("RULE   ALLOW :" + revoked + " removed by admin");
        }
        int minute = 12;
        for (String line : lines) {
            normal(String.format("08:%02d:%02d  %s", minute, random.nextInt(60), line));
            minute += 1 + random.nextInt(3);
        }
        hint("An ALLOW that wasn't removed is a way in: ssh admin@" + be.ip() + " -p <port>");
    }

    private void ssh(ServerPlayer player, ServerLevel level, String argument) {
        // ssh admin@10.1.2.3 -p 4410   (ook: ssh 10.1.2.3 4410)
        String ip = "";
        int port = -1;
        String[] tokens = argument.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.equals("-p") && i + 1 < tokens.length) {
                port = parseInt(tokens[++i]);
            } else if (token.contains("@")) {
                ip = token.substring(token.indexOf('@') + 1);
            } else if (token.matches("\\d+")) {
                port = parseInt(token);
            } else if (token.matches("[0-9.]+")) {
                ip = token;
            }
        }
        if (ip.isEmpty()) {
            error("usage: ssh admin@<ip> -p <port>");
            return;
        }
        SecurityCoreBlockEntity be = coreByIp(level, ip);
        if (be == null) {
            error("ssh: connect to host " + ip + ": no route to host");
            return;
        }
        if (port != be.sshPort()) {
            error("ssh: connect to " + ip + ":" + (port < 0 ? "22" : port) + ": connection refused (filtered)");
            hint("The open port is on the status page: curl " + ip);
            return;
        }
        connect(player, level, be);
    }

    // ================================================================ Verbinden

    /** Verbindt met een systeem (na een goede ssh). De manager doet het alarm; hier de terminal. */
    private void connect(ServerPlayer player, ServerLevel level, SecurityCoreBlockEntity be) {
        core = be.getBlockPos().immutable();
        daemon = be.daemon();
        buildProcesses(be);
        good("Connected to " + be.ip() + ":" + be.sshPort());
        boolean friend = SecurityCores.isFriend(level, be, player);
        HackProgress saved = be.hackProgress(player.getUUID());
        if (friend) {
            // Van jou: meteen binnen.
            authed = true;
            phase = Phase.SHELL;
            cwd = "/";
            header("Welcome back. You are the owner of " + be.hostname() + ".");
            hint("Type 'help', or 'exit' to disconnect.");
        } else if (saved != null && saved.authed()) {
            // Je was hier al binnen: verder waar je was.
            authed = true;
            phase = saved.attached() ? Phase.CONSOLE : Phase.SHELL;
            cwd = saved.cwd();
            watchdogOff = saved.watchdogOff();
            header("Session restored. You were already inside.");
            if (phase == Phase.CONSOLE) {
                printStatus(be);
            }
        } else {
            beginLogin(be);
        }
    }

    private void beginLogin(SecurityCoreBlockEntity be) {
        phase = Phase.LOGIN;
        header("BITECH MAINTENANCE SHELL - AUTHENTICATION REQUIRED");
        if (be.hasPassword()) {
            dump = null;
            normal("Password required.");
            hint("This system has a real password. You'll have to crack it.");
        } else {
            dump = new DumpPuzzle(be.systemSeed());
            normal("Old firmware: a failed handshake dumped some memory.");
            dump.print(output);
            hint("One of these words is the password. Type one to try it.");
        }
    }

    // ---------------------------------------------------------------- LOGIN

    private void login(ServerPlayer player, ServerLevel level, String command, String argument) {
        SecurityCoreBlockEntity be = coreEntity(level);
        if (be == null) {
            disconnected();
            return;
        }
        if (command.equals("exit") || command.equals("disconnect")) {
            disconnect();
            return;
        }
        if (dump != null) {
            if (command.equals("dump")) {
                dump.print(output);
                return;
            }
            String word = command.equals("login") ? argument : command;
            if (word.isEmpty()) {
                error("type one of the words from the dump");
                return;
            }
            DumpPuzzle.Result result = dump.tryWord(word, output);
            if (result == DumpPuzzle.Result.GRANTED) {
                enterShell(be);
            } else if (result == DumpPuzzle.Result.LOCKED_OUT) {
                busyUntil = level.getGameTime() + 80; // 4 seconden
                dump.newDump();
            }
        } else {
            String attempt = command.equals("login") ? argument : command;
            if (attempt.isEmpty()) {
                error("type the password");
                return;
            }
            if (be.checkPassword(attempt)) {
                enterShell(be);
            } else {
                loginAttempts++;
                be.logEvent(EventLog.Type.LOGIN_FAIL, player.getGameProfile().getName() + " failed to log in");
                error("ACCESS DENIED. Wrong password. (attempt " + loginAttempts + ")");
                busyUntil = level.getGameTime() + 40; // 2 seconden wachten na een fout
            }
        }
    }

    private void enterShell(SecurityCoreBlockEntity be) {
        authed = true;
        phase = Phase.SHELL;
        cwd = "/";
        good("Welcome to " + be.hostname() + ".");
        hint("Look around with ls. Biebia left a README.txt.");
    }

    // ---------------------------------------------------------------- SHELL

    private void shell(ServerPlayer player, ServerLevel level, String command, String argument) {
        SecurityCoreBlockEntity be = coreEntity(level);
        if (be == null) {
            disconnected();
            return;
        }
        VirtualFs fs = be.fs();
        switch (command) {
            case "exit", "disconnect", "logout" -> disconnect();
            case "pwd" -> normal(cwd);
            case "whoami" -> normal("root (you)");
            case "ls" -> {
                String dir = argument.isEmpty() ? cwd : VirtualFs.resolve(cwd, argument);
                if (!fs.isDir(dir)) {
                    error("ls: " + dir + ": no such directory");
                } else {
                    List<String> entries = new ArrayList<>();
                    for (VirtualFs.Node node : fs.list(dir)) {
                        entries.add(node.isDir() ? node.name() + "/" : node.name());
                    }
                    normal(entries.isEmpty() ? "(empty)" : String.join("   ", entries));
                }
            }
            case "cd" -> {
                String dir = argument.isEmpty() ? "/" : VirtualFs.resolve(cwd, argument);
                if (!fs.isDir(dir)) {
                    error("cd: " + argument + ": no such directory");
                } else {
                    cwd = dir;
                }
            }
            case "cat", "less", "more" -> readFile(player, level, be, argument);
            case "find" -> find(fs, argument);
            case "grep" -> grep(player, level, be, argument);
            case "ps", "top" -> {
                normal("  PID  COMMAND");
                for (Map.Entry<String, Integer> proc : processes.entrySet()) {
                    normal(String.format("%5d  %s", proc.getValue(), proc.getKey()));
                }
            }
            case "kill" -> error("kill: watchdog restarted it. You need to OVERRIDE it by its PID.");
            case "override" -> override(be, argument);
            default -> error(command + ": command not found (try 'help')");
        }
    }

    private void readFile(ServerPlayer player, ServerLevel level, SecurityCoreBlockEntity be, String argument) {
        String path = resolveFile(be.fs(), argument);
        VirtualFs.Node node = path == null ? null : be.fs().get(path);
        if (node == null || node.isDir()) {
            error("cat: " + argument + ": no such file (try 'ls')");
            return;
        }
        if (node.isTrap() && !authedOwner(level, be, player)) {
            triggerTrap(player, level, be, node.name());
            return;
        }
        for (String line : node.content().split("\n")) {
            normal(line);
        }
    }

    private void find(VirtualFs fs, String name) {
        if (name.isEmpty()) {
            error("usage: find <name>");
            return;
        }
        List<String> hits = new ArrayList<>();
        for (String path : fs.allFiles().keySet()) {
            if (VirtualFs.nameOf(path).toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
                hits.add(path);
            }
        }
        if (hits.isEmpty()) {
            normal("(nothing found)");
        } else {
            hits.forEach(this::normal);
        }
    }

    private void grep(ServerPlayer player, ServerLevel level, SecurityCoreBlockEntity be, String argument) {
        // grep -r <text>  (of gewoon grep <text>)
        String text = argument.startsWith("-r") ? argument.substring(2).strip() : argument;
        if (text.isEmpty()) {
            error("usage: grep -r <text>");
            return;
        }
        boolean any = false;
        for (Map.Entry<String, VirtualFs.Node> file : be.fs().allFiles().entrySet()) {
            VirtualFs.Node node = file.getValue();
            // grep leest elk bestand, dus ook een valstrik.
            if (node.isTrap() && !authedOwner(level, be, player)) {
                triggerTrap(player, level, be, node.name());
                return;
            }
            for (String line : node.content().split("\n")) {
                if (line.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                    normal(file.getKey() + ": " + line);
                    any = true;
                }
            }
        }
        if (!any) {
            normal("(no matches)");
        }
    }

    private void override(SecurityCoreBlockEntity be, String argument) {
        int pid = parseInt(argument);
        if (pid < 0) {
            if (processes.containsKey(argument.toLowerCase(Locale.ROOT))) {
                error("override needs the PID (number) of '" + argument.toLowerCase(Locale.ROOT)
                        + "', not its name. Type 'ps'.");
            } else {
                error("usage: override <pid>   (the numbers are in 'ps')");
            }
            return;
        }
        if (pid != processes.getOrDefault(daemon, -1)) {
            String name = processes.entrySet().stream().filter(e -> e.getValue() == pid)
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            error(name == null ? "override: no process " + pid + " (see 'ps')"
                    : "override: '" + name + "' is not the security daemon. Its real name is in the security config.");
            return;
        }
        phase = Phase.CONSOLE;
        header("ATTACHED TO '" + daemon.toUpperCase(Locale.ROOT) + "' (PID " + pid + ") - DAEMON CONSOLE");
        printStatus(be);
        hint("You're inside " + daemon + ". Type 'help'.");
    }

    // ---------------------------------------------------------------- CONSOLE

    private void console(ServerPlayer player, ServerLevel level, String command, String argument) {
        SecurityCoreBlockEntity be = coreEntity(level);
        if (be == null) {
            disconnected();
            return;
        }
        String arg = argument.toLowerCase(Locale.ROOT);
        switch (command) {
            case "status", "show", "config" -> printStatus(be);
            case "whoami" -> normal(player.getGameProfile().getName());
            case "exit", "disconnect" -> disconnect();
            case "disable", "enable" -> {
                boolean on = command.equals("enable");
                if (arg.equals("watchdog")) {
                    watchdogOff = !on;
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
                String[] parts = argument.replace("=", " ").strip().split("\\s+", 2);
                if (parts.length < 2 || !parts[0].equalsIgnoreCase("owner")) {
                    error("usage: set owner <name>");
                    return;
                }
                String name = parts[1].strip();
                if (!watchdogOff) {
                    error("WATCHDOG: owner change reverted (owner = " + ownerLabel(be) + ").");
                    hint("Switch the watchdog off first: disable watchdog");
                    return;
                }
                if (!name.equalsIgnoreCase(player.getGameProfile().getName())) {
                    error("set owner: no user '" + name + "' here. Type 'whoami' for your name.");
                    return;
                }
                good("owner = " + name);
                good("SECURITY DAEMON '" + daemon.toUpperCase(Locale.ROOT) + "' NOW TAKES ORDERS FROM "
                        + name.toUpperCase(Locale.ROOT) + ".");
                phase = Phase.DONE;
            }
            case "kill" -> error("kill: you are INSIDE the daemon now. Take it over instead.");
            default -> error(command + ": unknown command (try 'help')");
        }
    }

    private void printStatus(SecurityCoreBlockEntity be) {
        normal("owner     = " + ownerLabel(be));
        if (watchdogOff) {
            normal("watchdog  = off");
        } else {
            output.add(TermLine.error("watchdog  = on   (puts the owner back if anyone changes it)"));
        }
        normal("turrets   = fire at intruders");
        normal("drones    = hunt intruders");
    }

    private String ownerLabel(SecurityCoreBlockEntity be) {
        return be.ownerName().isEmpty() ? "biebia-industries" : be.ownerName();
    }

    // ================================================================ Hulp

    /** Is deze speler de eigenaar (of vriend) van dit systeem? Dan gaan valstrikken niet af. */
    private boolean authedOwner(ServerLevel level, SecurityCoreBlockEntity be, ServerPlayer player) {
        return SecurityCores.isFriend(level, be, player);
    }

    /** Een valstrik ging af: dat handelt de manager af (alarm + eruit gooien). We markeren het. */
    private boolean trapTripped;
    private String trapName = "";

    private void triggerTrap(ServerPlayer player, ServerLevel level, SecurityCoreBlockEntity be, String name) {
        trapTripped = true;
        trapName = name;
        error("!! TRAP: " + name + " triggered an alarm. Connection dropped.");
    }

    public boolean trapTripped() {
        return trapTripped;
    }

    public String trapName() {
        return trapName;
    }

    public void clearTrap() {
        trapTripped = false;
    }

    /** De processen van dit systeem: het echte beveiligingsprogramma, plus nep-versies. */
    private void buildProcesses(SecurityCoreBlockEntity be) {
        processes.clear();
        Random random = new Random(be.systemSeed() ^ 0xDEADBEEFL);
        List<String> procs = new ArrayList<>(List.of("init", "bitech-backup", "holo-render", "elevator-ctl",
                "hvac", "coffee-maker", "turret-ctl", "cam-feed"));
        for (String decoy : SystemDefaults.DAEMONS) {
            if (!decoy.equals(be.daemon()) && procs.size() < 12 && random.nextInt(2) == 0) {
                procs.add(decoy);
            }
        }
        procs.add(be.daemon());
        Collections.shuffle(procs, random);
        for (String proc : procs) {
            int pid;
            do {
                pid = 100 + random.nextInt(9900);
            } while (processes.containsValue(pid));
            processes.put(proc, pid);
        }
    }

    /** Waar dit bestandsnaam-argument naar wijst (ook op alleen de naam, zoals cat security.conf). */
    private @Nullable String resolveFile(VirtualFs fs, String argument) {
        if (argument.isEmpty()) {
            return null;
        }
        String path = VirtualFs.resolve(cwd, argument);
        if (fs.isFile(path)) {
            return path;
        }
        String name = VirtualFs.nameOf(argument);
        List<String> matches = fs.findByName(name);
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private void disconnect() {
        normal("Connection to " + (daemon.isEmpty() ? "system" : daemon) + " closed.");
        core = null;
        daemon = "";
        cwd = "/";
        authed = false;
        watchdogOff = false;
        dump = null;
        phase = Phase.LOCAL;
    }

    private void disconnected() {
        error("Connection lost (the system went offline).");
        disconnect();
    }

    // ================================================================ Voortgang bewaren

    /** De voortgang om op de kern te bewaren (zodat re-ssh en herstart je niet terugzetten). */
    public @Nullable HackProgress progressFor(long startedAt) {
        if (core == null || !authed) {
            return null;
        }
        return new HackProgress(true, cwd, watchdogOff, phase == Phase.CONSOLE, startedAt);
    }

    // ================================================================ Tab aanvullen

    public String complete(ServerLevel level, String input) {
        String[] parts = input.split(" ", -1);
        String last = parts[parts.length - 1];
        List<String> options = new ArrayList<>();
        SecurityCoreBlockEntity be = coreEntity(level);
        if (parts.length == 1) {
            options.addAll(switch (phase) {
                case LOCAL -> List.of("netscan", "curl", "ssh", "help", "clear");
                case LOGIN -> dump != null ? List.of("dump", "help", "clear", "exit") : List.of("help", "clear", "exit");
                case SHELL -> List.of("ls", "cd", "cat", "find", "grep", "ps", "override", "pwd", "help", "clear", "exit");
                case CONSOLE -> List.of("status", "disable", "enable", "set", "whoami", "help", "clear", "exit");
                default -> List.of();
            });
            if (phase == Phase.LOGIN && dump != null) {
                options.addAll(dump.possible());
            }
        } else if (phase == Phase.CONSOLE) {
            if (parts.length == 2 && (parts[0].equals("disable") || parts[0].equals("enable"))) {
                options.add("watchdog");
            } else if (parts.length == 2 && parts[0].equals("set")) {
                options.add("owner");
            }
        } else if (phase == Phase.SHELL && be != null
                && (parts[0].equals("cat") || parts[0].equals("cd") || parts[0].equals("ls") || parts[0].equals("find"))) {
            for (VirtualFs.Node node : be.fs().list(cwd)) {
                options.add(node.isDir() ? node.name() + "/" : node.name());
            }
            if (parts[0].equals("cat") || parts[0].equals("find")) {
                for (String path : be.fs().allFiles().keySet()) {
                    String name = VirtualFs.nameOf(path);
                    if (!options.contains(name)) {
                        options.add(name);
                    }
                }
            }
        }
        List<String> matches = options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last.toLowerCase(Locale.ROOT))).toList();
        if (matches.size() != 1) {
            return input;
        }
        parts[parts.length - 1] = matches.getFirst();
        String completed = String.join(" ", parts);
        return completed.endsWith("/") || parts.length > 1 ? completed : completed + " ";
    }

    // ================================================================ Kleine hulpjes

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private @Nullable SecurityCoreBlockEntity coreByIp(ServerLevel level, String ip) {
        for (SecurityCoreBlockEntity be : SecurityCores.scannable(level, null)) {
            if (be.ip().equals(ip)) {
                return be;
            }
        }
        return null;
    }

    private void cap() {
        while (output.size() > MAX_OUTPUT) {
            output.removeFirst();
        }
    }

    private void normal(String text) {
        output.add(TermLine.normal(text));
    }

    private void error(String text) {
        output.add(TermLine.error(text));
    }

    private void header(String text) {
        output.add(TermLine.header(text));
    }

    private void good(String text) {
        output.add(TermLine.good(text));
    }

    private void hint(String text) {
        output.add(TermLine.hint(text));
    }

    /** Wat er per hacker op de kern wordt bewaard, zodat re-ssh en herstart je niet terugzetten. */
    public record HackProgress(boolean authed, String cwd, boolean watchdogOff, boolean attached, long startedAt) {}
}
