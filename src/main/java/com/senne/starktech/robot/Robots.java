package com.senne.starktech.robot;

import com.senne.starktech.bot.BotBoard;
import com.senne.starktech.bot.BotLink;
import com.senne.starktech.bot.BotModule;
import com.senne.starktech.bot.BotModuleItem;
import com.senne.starktech.bot.BotNetwork;
import com.senne.starktech.bot.Soldering;
import com.senne.starktech.bot.SolderingIronItem;
import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.logic.program.ProgramKind;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.network.RobotActionPayload;
import com.senne.starktech.network.RobotOpenPayload;
import com.senne.starktech.network.SolderPayload;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.security.BaseTrust;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Het robotscherm, aan de kant van de server (hoofdstuk 21): het scherm openen, en de knoppen uitvoeren. Het
 * scherm zelf staat in client/robot/RobotScreen.
 *
 * Openen: sneak + rechtsklik met een lege hand op de robot. Dat mag wie hem neerzette, en wie vertrouwd is in
 * de basis waar hij staat (dezelfde regel als bij de laserzender, zie BaseTrust). Een robot van niemand (uit
 * een spawn-ei) wordt van wie hem als eerste opent, behalve in iemands basis.
 */
public final class Robots {
    private Robots() {}

    /** Zo dichtbij moet je staan (blokken). */
    private static final double REACH = 8.0;

    /** Sneak + rechtsklik op de robot: mag je hem instellen, dan opent jouw spel het scherm. */
    public static void open(ServerPlayer player, GuardRobotEntity robot) {
        if (!mayEdit(player, robot)) {
            String owner = BaseTrust.belongsTo(player.serverLevel(), robot);
            player.displayClientMessage(owner.isEmpty() ? Component.translatable("message.starktech.robot.locked")
                    : Component.translatable("message.starktech.robot.not_yours", owner), true);
            return;
        }
        ModNetwork.sendTo(player, new RobotOpenPayload(robot.getId(), robot.ownerName(), Optional.ofNullable(robot.home())));
    }

    /** Een knop uit het scherm. */
    public static void handleAction(ServerPlayer player, RobotActionPayload payload) {
        GuardRobotEntity robot = robot(player, payload.entityId());
        if (robot == null || !mayEdit(player, robot)) {
            return;
        }
        switch (payload.op()) {
            case TURN_ON -> {
                if (!robot.setActive(true)) {
                    player.displayClientMessage(Component.translatable("message.starktech.robot.short_circuit"), true);
                }
            }
            case TURN_OFF -> robot.setActive(false);
            case RENAME -> rename(player, robot, payload.text());
            case PICK_UP -> pickUp(player, robot);
        }
        BotNetwork.get(player.serverLevel()).update(robot);
    }

    /** Mag deze speler de robot instellen? (Zie de uitleg bovenaan.) */
    public static boolean mayEdit(ServerPlayer player, GuardRobotEntity robot) {
        return BaseTrust.mayEdit(player, robot);
    }

    /** Een nieuwe naam: niet leeg, niet te lang, en geen andere robot van dezelfde eigenaar heet al zo. */
    public static boolean rename(ServerPlayer player, GuardRobotEntity robot, String typed) {
        String name = BotNetwork.cleanName(typed);
        if (name == null) {
            player.displayClientMessage(Component.translatable("message.starktech.robot.bad_name", BotNetwork.MAX_NAME), true);
            return false;
        }
        if (BotNetwork.get(player.serverLevel()).nameTaken(robot.owner(), name, robot.getUUID())) {
            player.displayClientMessage(Component.translatable("message.starktech.robot.name_taken", name), true);
            return false;
        }
        robot.setCustomName(Component.literal(name));
        return true;
    }

    /** Oppakken: de robot wordt een item in je rugzak (met alles erop en eraan), en zijn dock is weer vrij. */
    public static void pickUp(ServerPlayer player, GuardRobotEntity robot) {
        ServerLevel level = player.serverLevel();
        if (robot.home() != null && level.getBlockEntity(robot.home()) instanceof RobotDockBlockEntity dock
                && robot.getUUID().equals(dock.robot())) {
            dock.setRobot(null);
        }
        ItemStack stack = GuardRobotItem.create(robot.toData());
        level.playSound(null, robot.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 0.6f, 0.8f);
        robot.discard();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.displayClientMessage(Component.translatable("message.starktech.robot.picked_up", stack.getHoverName()), true);
    }

    // ================================================================ Solderen (hoofdstuk 22)

    /**
     * Solderen of lossolderen op het bord van een robot (uit het bordscherm). Alleen als je hem mag instellen,
     * hij uit staat en je een Soldering Iron bij je hebt (die slijt een beetje). Solderen: de module komt uit je
     * rugzak; hoe het afloopt, beslist Soldering (te kort = het pakt niet, te lang = vaak een brug, en dat zeggen
     * we je niet). Lossolderen: de module komt terug (ook een verbrande), en de bruggen aan die pin gaan weg.
     */
    public static void handleSolder(ServerPlayer player, SolderPayload payload) {
        GuardRobotEntity robot = robot(player, payload.entityId());
        int pin = payload.pin();
        if (robot == null || !mayEdit(player, robot) || pin < 1 || pin > BotBoard.PINS) {
            return;
        }
        String name = robot.hasCustomName() ? robot.getCustomName().getString() : "the robot";
        if (robot.isActive()) {
            player.displayClientMessage(Component.translatable("message.starktech.solder.robot_on", name), true);
            return;
        }
        ItemStack iron = SolderingIronItem.find(player);
        if (iron == null) {
            player.displayClientMessage(Component.translatable("message.starktech.solder.no_iron"), true);
            return;
        }
        BotBoard board = robot.board();
        ServerLevel level = player.serverLevel();
        if (payload.desolder()) {
            boolean bridged = board.bridge(pin - 1) || board.bridge(pin);
            if (board.pin(pin).isEmpty() && !bridged) {
                player.displayClientMessage(Component.translatable("message.starktech.solder.nothing", pin), true);
                return;
            }
            BotModule module = board.module(pin);
            robot.setBoard(board.desolder(pin));
            if (module != null) {
                give(player, new ItemStack(ModItems.robotModule(module)));
            }
            iron.hurtAndBreak(1, level, player, item -> {});
            level.playSound(null, robot.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.6f);
            player.displayClientMessage(Component.translatable("message.starktech.solder.desoldered", pin), true);
            return;
        }
        BotModule module = BotModule.byId(payload.module());
        if (module == null || module == BotModule.BURNT) {
            return;
        }
        if (!board.pin(pin).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.starktech.solder.pin_taken", pin), true);
            return;
        }
        ItemStack stack = moduleIn(player, module);
        boolean creative = player.getAbilities().instabuild;
        if (stack == null && !creative) {
            player.displayClientMessage(Component.translatable("message.starktech.solder.no_module", module.title()), true);
            return;
        }
        Soldering.Result result = Soldering.solder(board, pin, module, payload.holdTicks(), robot.getRandom());
        iron.hurtAndBreak(1, level, player, item -> {});
        if (result.joint() == Soldering.Joint.COLD) {
            level.playSound(null, robot.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3f, 0.7f);
            player.displayClientMessage(Component.translatable("message.starktech.solder.cold", pin), true);
            return;
        }
        robot.setBoard(result.board());
        if (stack != null && !creative) {
            stack.shrink(1);
        }
        // Goed of met een brug: je hoort en leest hetzelfde. Een brug zie je alleen als je goed kijkt.
        level.playSound(null, robot.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.3f);
        player.displayClientMessage(Component.translatable("message.starktech.solder.done", pin, module.title()), true);
    }

    /** Een stapel van deze module in de rugzak van de speler, of null. */
    private static @Nullable ItemStack moduleIn(ServerPlayer player, BotModule module) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof BotModuleItem item && item.module() == module) {
                return stack;
            }
        }
        return null;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ================================================================ De app Robots op de desktop

    /**
     * De robots in het gebied van deze kern, voor de app Robots: naam, toestand, aan of uit, batterij, waar hij
     * staat, of hij een dock heeft, of hij nu geladen is (anders kan de desktop hem niet bedienen), en (hoofdstuk
     * 23) welk programma hij draait, of zijn kopie ouder is dan die op de server, of hij verbinding heeft, en welk
     * programma op hem wacht.
     */
    public static ListTag snapshot(ServerLevel level, SecurityCoreBlockEntity core) {
        ListTag list = new ListTag();
        BotNetwork network = BotNetwork.get(level);
        for (BotNetwork.Entry entry : network.atCore(GlobalPos.of(level.dimension(), core.getBlockPos()))) {
            GuardRobotEntity robot = level.getEntity(entry.id()) instanceof GuardRobotEntity found && found.isAlive() ? found : null;
            CompoundTag row = new CompoundTag();
            row.putString("id", entry.id().toString());
            row.putString("n", entry.name());
            row.putString("st", entry.state().title().toLowerCase(Locale.ROOT));
            row.putBoolean("on", robot != null ? robot.isActive() : entry.state() == RobotState.ACTIVE);
            row.putInt("bat", Math.round(entry.battery() * 100));
            row.putInt("x", entry.pos().pos().getX());
            row.putInt("y", entry.pos().pos().getY());
            row.putInt("z", entry.pos().pos().getZ());
            row.putBoolean("dock", entry.home() != null);
            row.putBoolean("loaded", robot != null);
            LogicProgram program = robot != null ? robot.program() : null;
            String name = robot != null ? program == null ? "" : program.name() : entry.program();
            LogicProgram newest = name.isEmpty() ? null : core.logic().program(name);
            row.putString("prog", name);
            row.putBoolean("old", program != null && newest != null && !newest.source().equals(program.source()));
            row.putBoolean("link", robot != null && BotLink.connected(robot));
            CompoundTag waiting = network.pending(entry.id());
            if (waiting != null) {
                row.putString("wait", waiting.isEmpty() ? "-" : waiting.getString("Name"));
            }
            list.add(row);
        }
        return list;
    }

    /** De robotprogramma's op de server van deze kern (voor de knop Program in de app Robots). */
    public static ListTag programNames(SecurityCoreBlockEntity core) {
        ListTag names = new ListTag();
        for (LogicProgram program : core.logic().programs()) {
            if (program.kind() == ProgramKind.ROBOT) {
                names.add(net.minecraft.nbt.StringTag.valueOf(program.name()));
            }
        }
        return names;
    }

    /**
     * Een knop uit de app Robots: aan/uit ("robot_switch"), een andere naam ("robot_rename") of (hoofdstuk 23) een
     * programma ("robot_program"). Alleen voor robots in het gebied van deze kern; aan/uit en hernoemen alleen als
     * ze nu geladen zijn. Geeft de melding voor de taakbalk terug.
     */
    public static Desktop desktop(ServerPlayer player, SecurityCoreBlockEntity core, String op, CompoundTag args) {
        ServerLevel level = player.serverLevel();
        UUID id;
        try {
            id = UUID.fromString(args.getString("id"));
        } catch (IllegalArgumentException e) {
            return new Desktop("No such robot", true);
        }
        BotNetwork network = BotNetwork.get(level);
        BotNetwork.Entry entry = network.get(id);
        if (entry == null || !GlobalPos.of(level.dimension(), core.getBlockPos()).equals(entry.core())) {
            return new Desktop("That robot is not in this network's area", true);
        }
        if (op.equals("robot_program")) {
            return upload(level, core, network, entry, args.getString("program"));
        }
        if (!(level.getEntity(id) instanceof GuardRobotEntity robot) || !robot.isAlive()) {
            return new Desktop(entry.name() + " is too far away (its chunk is not loaded)", true);
        }
        String name = robot.hasCustomName() ? robot.getCustomName().getString() : entry.name();
        Desktop result;
        if (op.equals("robot_switch")) {
            boolean on = args.getBoolean("on");
            result = robot.setActive(on) ? new Desktop(name + (on ? " switched on" : " switched off"), false)
                    : new Desktop("Short circuit in " + name + "! Check its circuit board", true);
        } else {
            result = rename(player, robot, args.getString("name"))
                    ? new Desktop("Renamed to " + robot.getCustomName().getString(), false)
                    : new Desktop("That name is not allowed or already taken", true);
        }
        network.update(robot);
        return result;
    }

    /**
     * Een programma naar een robot (hoofdstuk 23): de kern stuurt een kopie via de verbinding, en de robot bewaart
     * die zelf (ook als je hem oppakt). Hij begint dan meteen opnieuw bij when started (als hij aan staat). Een
     * robot die niet geladen is, krijgt het als hij terug is en verbinding heeft. {@code name} leeg = geen
     * programma meer.
     */
    private static Desktop upload(ServerLevel level, SecurityCoreBlockEntity core, BotNetwork network, BotNetwork.Entry entry,
                                  String name) {
        LogicProgram program = null;
        if (!name.isEmpty()) {
            LogicProgram found = core.logic().program(name);
            if (found == null) {
                return new Desktop("There is no program called " + name, true);
            }
            if (found.kind() != ProgramKind.ROBOT) {
                return new Desktop(name + " is a server diagram, not a robot program", true);
            }
            program = LogicProgram.load(found.save()); // een kopie: de robot houdt hem, ook als je hem later verandert
        }
        String what = program == null ? "no program" : program.name();
        if (!(level.getEntity(entry.id()) instanceof GuardRobotEntity robot) || !robot.isAlive()) {
            network.setPending(entry.id(), program);
            return new Desktop(entry.name() + " is far away: it gets " + what + " as soon as it is back", false);
        }
        String robotName = robot.hasCustomName() ? robot.getCustomName().getString() : entry.name();
        if (!BotLink.connected(robot)) {
            return new Desktop(robotName + " has no connection: it must stand in this network's area, and the server must run",
                    true);
        }
        robot.setProgram(program);
        network.update(robot);
        return new Desktop(program == null ? robotName + " has no program now" : "Uploaded " + what + " to " + robotName, false);
    }

    /** Wat de desktop in de taakbalk zegt na een knop van de app Robots. */
    public record Desktop(String text, boolean error) {}

    /** De robot met dit nummer, als hij dichtbij genoeg is; anders null. */
    private static @Nullable GuardRobotEntity robot(ServerPlayer player, int entityId) {
        if (player.serverLevel().getEntity(entityId) instanceof GuardRobotEntity robot && robot.isAlive()
                && player.distanceToSqr(robot) <= REACH * REACH) {
            return robot;
        }
        return null;
    }
}
