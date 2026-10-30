package com.senne.imttech.computer;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.lan.DevicePower;
import com.senne.imttech.network.ComputerCodePromptPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import com.senne.imttech.security.os.DesktopRights;
import com.senne.imttech.security.os.EventLog;
import com.senne.imttech.security.os.SecurityOs;
import com.senne.imttech.security.os.SystemPassword;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Alles rond de Computer en de Monitor (hoofdstuk 40) dat niet in één blok past: welke computer bij een monitor hoort,
 * wat rechtsklikken doet, een vrije naam voor een nieuwe computer, en de app Computers op de kern (de lijst in de
 * momentopname en de knoppen, zie {@link #snapshot} en {@link #desktop}).
 */
public final class Computers {
    private Computers() {}

    /** De computer waar deze monitor tegenaan staat (de eerste die we vinden, aan welke kant ook), of null. */
    public static @Nullable BlockPos computerNextTo(BlockGetter level, BlockPos monitor) {
        for (Direction direction : Direction.values()) {
            BlockPos next = monitor.relative(direction);
            if (level.getBlockState(next).getBlock() instanceof ComputerBlock) {
                return next;
            }
        }
        return null;
    }

    /** Licht het scherm van een monitor op deze plek op? Ja als hij tegen een computer staat die werkt. */
    public static boolean lit(BlockGetter level, BlockPos monitor) {
        BlockPos computer = computerNextTo(level, monitor);
        if (computer == null) {
            return false;
        }
        BlockState state = level.getBlockState(computer);
        return state.getBlock() instanceof ComputerBlock && state.getValue(ComputerBlock.ON);
    }

    /**
     * Waarom werkt de computer op deze plek niet? Null als hij werkt. (Engels, voor de actiebalk.)
     */
    public static @Nullable Component problem(ServerLevel level, BlockPos computer) {
        if (!(level.getBlockEntity(computer) instanceof ComputerBlockEntity entity)) {
            return Component.translatable("computer.imttech.msg.no_computer");
        }
        SecurityCoreBlockEntity core = entity.core(level);
        if (core == null) {
            return Component.translatable("computer.imttech.msg.no_network");
        }
        if (!core.isOnline()) {
            return Component.translatable("computer.imttech.msg.core_offline", core.hostname());
        }
        if (com.senne.imttech.weapons.Emp.isOut(level, computer)) {
            return Component.translatable("computer.imttech.msg.emp");
        }
        if (!DevicePower.hasPower(level, computer)) {
            return Component.translatable("computer.imttech.msg.no_power");
        }
        return null;
    }

    /** Rechtsklik op de kast: zijn naam, en aan welke kern hij hangt (of waarom hij niet werkt). */
    public static void status(ServerPlayer player, BlockPos computer) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(computer) instanceof ComputerBlockEntity entity)) {
            return;
        }
        Component problem = problem(level, computer);
        if (problem != null) {
            player.displayClientMessage(Component.literal(entity.name() + ": ").append(problem).withStyle(ChatFormatting.RED), true);
            return;
        }
        SecurityCoreBlockEntity core = entity.core(level);
        player.displayClientMessage(Component.translatable("computer.imttech.msg.status", entity.name(),
                core == null ? "?" : core.hostname(), core == null ? "?" : core.ip()).withStyle(ChatFormatting.AQUA), true);
    }

    // ================================================================ De pc gebruiken (een rechtsklik op de monitor)

    /** Hoeveel foute codes na elkaar deze speler al typte (tot [security] passwordAttempts, net als bij de laptop). */
    private static final Map<UUID, Integer> WRONG = new HashMap<>();
    /** Na te veel foute codes: tot wanneer (speltijd) deze speler moet wachten. */
    private static final Map<UUID, Long> WAIT_UNTIL = new HashMap<>();

    /**
     * Mag deze speler op deze computer? De eigenaar van de kern altijd. Verder wat de kern koos (jouw wens: "wie hem mag
     * gebruiken"): iedereen, de vertrouwde spelers (de eigenaar, zijn team en de lijst in Access), of wie een geldige
     * keycard van dit netwerk uit de gekozen groep bij zich heeft (ergens in zijn inventaris). Geeft de reden als het
     * niet mag, of null.
     */
    public static @Nullable Component mayEnter(ServerPlayer player, SecurityCoreBlockEntity core, ComputerBlockEntity computer) {
        if (player.getUUID().equals(core.ownerId())) {
            return null;
        }
        return switch (computer.who()) {
            case EVERYONE -> null;
            case TRUSTED -> SecurityCores.isFriend(player.serverLevel(), core, player) ? null
                    : Component.translatable("computer.imttech.msg.trusted_only", computer.name());
            case GROUP -> hasCard(player, core, computer.group()) ? null
                    : Component.translatable("computer.imttech.msg.group_only", computer.name(), computer.group());
        };
    }

    /** Heeft deze speler een geldige keycard van dit netwerk bij zich die in deze groep zit (zelf, of via een groep erin)? */
    private static boolean hasCard(ServerPlayer player, SecurityCoreBlockEntity core, String group) {
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            com.senne.imttech.logic.KeycardData card = com.senne.imttech.logic.KeycardItem.data(inventory.getItem(i));
            if (core.logic().isValid(core.ip(), card) && core.logic().inGroup(card.number(), group)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mag deze speler (nog) op de desktop van deze computer? Hij moet werken, ingesteld zijn (minstens één app) en jij moet
     * erop mogen. Geeft de reden als het niet (meer) mag, of null. Om de seconde gevraagd zolang de desktop open staat.
     */
    public static @Nullable Component stillAllowed(ServerPlayer player, SecurityCoreBlockEntity core, BlockPos computer) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(computer) instanceof ComputerBlockEntity entity) || entity.core(level) != core) {
            return Component.translatable("computer.imttech.msg.no_network");
        }
        Component problem = problem(level, computer);
        if (problem != null) {
            return problem;
        }
        if (entity.rights().empty()) {
            return Component.translatable("computer.imttech.msg.not_set_up");
        }
        return mayEnter(player, core, entity);
    }

    /**
     * Rechtsklik op een monitor: de desktop van zijn computer, met wat de kern daar toestaat. Werkt de computer niet, is hij
     * nog niet ingesteld of mag jij er niet op, dan zegt de actiebalk waarom. Heeft hij een code (en ben jij niet de
     * eigenaar), dan eerst het codescherm.
     */
    public static void use(ServerPlayer player, BlockPos monitor) {
        ServerLevel level = player.serverLevel();
        BlockPos computer = computerNextTo(level, monitor);
        if (computer == null) {
            player.displayClientMessage(Component.translatable("computer.imttech.msg.no_computer")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!(level.getBlockEntity(computer) instanceof ComputerBlockEntity entity)) {
            return;
        }
        SecurityCoreBlockEntity core = entity.core(level);
        Component problem = core == null ? problem(level, computer) : stillAllowed(player, core, computer);
        if (problem != null || core == null) {
            player.displayClientMessage((problem == null ? Component.translatable("computer.imttech.msg.no_network") : problem)
                    .copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        if (entity.hasCode() && !player.getUUID().equals(core.ownerId())) {
            ModNetwork.sendTo(player, new ComputerCodePromptPayload(monitor.immutable(), entity.name(), "", true));
            return;
        }
        open(player, core, entity, monitor);
    }

    /**
     * De code van het codescherm. Alles wordt opnieuw nagekeken (je kon intussen weglopen, of de computer veranderde). Een
     * foute code staat in het logboek van de kern; na [security] passwordAttempts foute codes moet je even wachten.
     */
    public static void enterCode(ServerPlayer player, BlockPos monitor, String code) {
        ServerLevel level = player.serverLevel();
        BlockPos computer = computerNextTo(level, monitor);
        if (computer == null || player.distanceToSqr(monitor.getCenter()) > SecurityOs.REACH * SecurityOs.REACH
                || !(level.getBlockEntity(computer) instanceof ComputerBlockEntity entity)) {
            ModNetwork.sendTo(player, ComputerCodePromptPayload.reply(monitor, "You are too far from the computer."));
            return;
        }
        SecurityCoreBlockEntity core = entity.core(level);
        Component problem = core == null ? Component.translatable("computer.imttech.msg.no_network")
                : stillAllowed(player, core, computer);
        if (problem != null) {
            ModNetwork.sendTo(player, ComputerCodePromptPayload.reply(monitor, problem.getString()));
            return;
        }
        long now = level.getGameTime();
        Long waitUntil = WAIT_UNTIL.get(player.getUUID());
        if (waitUntil != null && now < waitUntil) {
            ModNetwork.sendTo(player, ComputerCodePromptPayload.reply(monitor,
                    "Too many wrong codes: wait " + (int) Math.ceil((waitUntil - now) / 20.0) + " s."));
            return;
        }
        if (!entity.checkCode(code)) {
            int wrong = WRONG.merge(player.getUUID(), 1, Integer::sum);
            core.logEvent(EventLog.Type.LOGIN_FAIL, "Wrong code on " + entity.name() + " from " + player.getGameProfile().getName());
            String text = "Wrong code, try again.";
            if (wrong >= Math.max(1, Math.round(ImtConfig.Security.PASSWORD_ATTEMPTS))) {
                WRONG.remove(player.getUUID());
                long ticks = Math.max(20, Math.round(ImtConfig.Security.PASSWORD_LOCKOUT_MILLIS / 50.0));
                WAIT_UNTIL.put(player.getUUID(), now + ticks);
                text = "Wrong code. Too many tries: wait " + (int) Math.ceil(ticks / 20.0) + " s.";
            }
            ModNetwork.sendTo(player, ComputerCodePromptPayload.reply(monitor, text));
            return;
        }
        WRONG.remove(player.getUUID());
        WAIT_UNTIL.remove(player.getUUID());
        open(player, core, entity, monitor);
    }

    /** De desktop van deze computer echt openen, en in het logboek: wie er waar inlogde (zie de app Logs, Show: entries). */
    private static void open(ServerPlayer player, SecurityCoreBlockEntity core, ComputerBlockEntity computer, BlockPos monitor) {
        if (SecurityOs.openComputer(player, core, computer.getBlockPos(), monitor)) {
            core.logEvent(EventLog.Type.LOGIN, player.getGameProfile().getName() + " logged in at " + computer.name());
            player.serverLevel().playSound(null, monitor, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.3f, 2.0f);
        }
    }

    /** Een computer is weg (afgebroken): wie hem open had, krijgt hem dicht. */
    public static void gone(ServerLevel level, BlockPos computer) {
        SecurityOs.closeComputer(level, computer);
    }

    /** Bij het afsluiten van de wereld: de foute codes vergeten. */
    public static void clear() {
        WRONG.clear();
        WAIT_UNTIL.clear();
    }

    // ================================================================ De app Computers op de kern

    /** Het antwoord op een knop in de app Computers: de melding rechts in de taakbalk. */
    public record Result(String text, boolean error) {}

    /**
     * De computers op het netwerk van deze kern, voor de app Computers: per computer zijn plek, naam, of hij werkt (en zo
     * niet, waarom), wie hem mag gebruiken, of hij een code heeft (nooit de code zelf) en per app wat mag.
     */
    public static ListTag snapshot(ServerLevel level, SecurityCoreBlockEntity core) {
        ListTag list = new ListTag();
        for (BlockPos pos : SecurityDevices.scan(level, core).computers()) {
            if (!(level.getBlockEntity(pos) instanceof ComputerBlockEntity entity)) {
                continue;
            }
            CompoundTag computer = new CompoundTag();
            computer.putInt("x", pos.getX());
            computer.putInt("y", pos.getY());
            computer.putInt("z", pos.getZ());
            computer.putString("n", entity.name());
            Component problem = problem(level, pos);
            computer.putString("p", problem == null ? "" : problem.getString());
            computer.putString("w", entity.who().name());
            computer.putString("g", entity.group());
            computer.putBoolean("c", entity.hasCode());
            computer.put("a", entity.rights().save());
            list.add(computer);
        }
        return list;
    }

    /**
     * Een knop in de app Computers (alleen op de kern zelf of met de laptop: een Computer heeft deze app nooit):
     * <ul>
     *   <li>{@code pc_rename}: een andere naam;</li>
     *   <li>{@code pc_who}: wie hem mag gebruiken (EVERYONE, TRUSTED, of GROUP met een keycard-groep van deze kern);</li>
     *   <li>{@code pc_code}: een nieuwe code (4-8 tekens, a-z en 0-9, zoals een wachtwoord), of leeg = geen code;</li>
     *   <li>{@code pc_app}: één app op Off (0), View (1) of Use (2).</li>
     * </ul>
     * De computer moet op het netwerk van deze kern hangen. Alles komt in het logboek (de code zelf nooit).
     */
    public static Result desktop(ServerPlayer player, SecurityCoreBlockEntity core, String op, CompoundTag args) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = new BlockPos(args.getInt("x"), args.getInt("y"), args.getInt("z"));
        if (!SecurityDevices.scan(level, core).computers().contains(pos)
                || !(level.getBlockEntity(pos) instanceof ComputerBlockEntity computer)) {
            return new Result("That computer is not on this network", true);
        }
        String who = player.getGameProfile().getName();
        String name = computer.name();
        switch (op) {
            case "pc_rename" -> {
                String clean = com.senne.imttech.logic.InputDeviceBlockEntity.cleanName(args.getString("name"));
                if (clean.isEmpty()) {
                    return new Result("A computer needs a name", true);
                }
                computer.setName(clean);
                core.logEvent(EventLog.Type.DEVICE, who + " renamed computer " + name + " to " + clean);
                return new Result("Renamed to " + clean, false);
            }
            case "pc_who" -> {
                ComputerBlockEntity.Who choice;
                try {
                    choice = ComputerBlockEntity.Who.valueOf(args.getString("who"));
                } catch (IllegalArgumentException e) {
                    return new Result("Unknown choice", true);
                }
                String group = "";
                if (choice == ComputerBlockEntity.Who.GROUP) {
                    if (core.logic().groups().isEmpty()) {
                        return new Result("Make a keycard group first (in the Keycards app)", true);
                    }
                    group = core.logic().spelling(args.getString("group"));
                    if (group == null) {
                        group = core.logic().groups().getFirst();
                    }
                }
                computer.setWho(choice, group);
                String text = switch (choice) {
                    case EVERYONE -> "everyone";
                    case TRUSTED -> "trusted players";
                    case GROUP -> "keycard group " + group;
                };
                core.logEvent(EventLog.Type.DEVICE, who + " set who may use " + name + ": " + text);
                return new Result(name + ": " + text, false);
            }
            case "pc_code" -> {
                String code = args.getString("code");
                if (!code.isEmpty()) {
                    String problem = SystemPassword.problem(code);
                    if (problem != null) {
                        return new Result("Code rejected: " + problem, true);
                    }
                }
                computer.setCode(code, level.random);
                core.logEvent(EventLog.Type.DEVICE, who + (code.isEmpty() ? " removed the code of " : " set a code on ") + name);
                return new Result(code.isEmpty() ? name + ": no code" : name + ": code set", false);
            }
            case "pc_app" -> {
                String app = args.getString("app");
                int value = args.getInt("access");
                if (!DesktopRights.COMPUTER_APPS.contains(app) || value < 0 || value >= DesktopRights.Access.values().length) {
                    return new Result("That app cannot go on a computer", true);
                }
                DesktopRights rights = computer.rights().with(app, DesktopRights.Access.values()[value]);
                computer.setRights(rights);
                String label = app.charAt(0) + app.substring(1).toLowerCase(java.util.Locale.ROOT);
                String access = rights.access(app).name().toLowerCase(java.util.Locale.ROOT);
                core.logEvent(EventLog.Type.DEVICE, who + " set " + label + " on " + name + " to " + access);
                return new Result(name + ": " + label + " " + access, false);
            }
            default -> {
                return new Result("", false);
            }
        }
    }

    /** Een naam die nog geen andere computer van deze kern heeft: "Computer 1", "Computer 2", ... */
    public static String freeName(ServerLevel level, BlockPos pos) {
        Set<String> used = new HashSet<>();
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
        if (core != null) {
            for (BlockPos other : SecurityDevices.scan(level, core).computers()) {
                if (!other.equals(pos) && level.getBlockEntity(other) instanceof ComputerBlockEntity entity) {
                    used.add(entity.name());
                }
            }
        }
        int number = 1;
        while (used.contains("Computer " + number)) {
            number++;
        }
        return "Computer " + number;
    }
}
