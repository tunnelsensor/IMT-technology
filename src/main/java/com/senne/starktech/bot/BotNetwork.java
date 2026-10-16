package com.senne.starktech.bot;

import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.robot.GuardRobotEntity;
import com.senne.starktech.robot.RobotState;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Alle guard robots van de wereld (hoofdstuk 21; later ook de drones), ook die in een stuk wereld staan dat
 * nu niet geladen is: hun naam, eigenaar, waar ze het laatst waren, hun dock, hoe het met ze gaat en in het
 * gebied van welke beveiligingskern ze staan. De app Robots op de desktop leest hieruit, en hier wacht een
 * nieuw programma (hoofdstuk 23) voor een robot die niet geladen is, tot hij terug is en verbinding heeft.
 *
 * Een robot meldt zich zelf: als hij in de wereld komt, om de seconde, en als hij kapot gaat of opgepakt wordt
 * (dan gaat hij eruit). Elke robot krijgt een naam die bij zijn eigenaar nog niet bestaat: "Guard 1",
 * "Guard 2"... In je code noem je hem straks bij die naam, daarom mag een eigenaar een naam maar één keer
 * hebben.
 *
 * Dit is "SavedData": Minecraft bewaart het in de wereld (data/starktech_bots.dat van de overworld).
 */
public final class BotNetwork extends SavedData {
    private static final String NAME = "starktech_bots";
    /** Zo lang mag een naam zijn. */
    public static final int MAX_NAME = 24;

    /**
     * Wat we van één robot weten.
     *
     * @param core    de beveiligingskern in wiens gebied hij stond (of null)
     * @param program de naam van het programma dat hij draait ("" = geen)
     * @param seen    wanneer hij zich het laatst meldde (speltijd in ticks)
     */
    public record Entry(UUID id, String name, @Nullable UUID owner, String ownerName, GlobalPos pos,
                        @Nullable BlockPos home, @Nullable GlobalPos core, int parts, RobotState state,
                        float battery, String program, long seen) {}

    private final Map<UUID, Entry> robots = new LinkedHashMap<>();
    /**
     * Programma's die wachten op een robot die niet geladen is (zoals LogicProgram.save ze schrijft; leeg = "geen
     * programma meer"). Hij krijgt het als hij terug is en verbinding heeft.
     */
    private final Map<UUID, CompoundTag> pending = new LinkedHashMap<>();

    public static BotNetwork get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BotNetwork::new, BotNetwork::load), NAME);
    }

    public static BotNetwork get(ServerLevel level) {
        return get(level.getServer());
    }

    // ================================================================ Bijhouden

    /** Een robot meldt zich: zet hem erin of werk hem bij (en geef hem zijn wachtende programma, als hij verbinding heeft). */
    public void update(GuardRobotEntity robot) {
        if (!(robot.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag waiting = pending.get(robot.getUUID());
        if (waiting != null && BotLink.connected(robot)) {
            robot.setProgram(waiting.isEmpty() ? null : LogicProgram.load(waiting));
            pending.remove(robot.getUUID());
            setDirty();
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, robot.blockPosition());
        Entry entry = new Entry(robot.getUUID(), robot.hasCustomName() ? robot.getCustomName().getString() : "",
                robot.owner(), robot.ownerName(), GlobalPos.of(level.dimension(), robot.blockPosition()), robot.home(),
                core == null ? null : GlobalPos.of(level.dimension(), core.getBlockPos()), robot.parts().pack(),
                robot.state(), robot.batteryFraction(), robot.program() == null ? "" : robot.program().name(), level.getGameTime());
        Entry old = robots.put(robot.getUUID(), entry);
        // Alleen "veranderd" als er echt iets anders is (de tijd alleen telt niet), zodat we niet elke seconde opslaan.
        if (old == null || !same(old, entry)) {
            setDirty();
        }
    }

    private static boolean same(Entry a, Entry b) {
        return a.name().equals(b.name()) && Objects.equals(a.owner(), b.owner()) && a.pos().equals(b.pos())
                && Objects.equals(a.home(), b.home()) && Objects.equals(a.core(), b.core()) && a.parts() == b.parts()
                && a.state() == b.state() && Math.abs(a.battery() - b.battery()) < 0.01f && a.program().equals(b.program());
    }

    /** Een robot is weg (kapot of opgepakt). */
    public void remove(UUID id) {
        if (robots.remove(id) != null | pending.remove(id) != null) {
            setDirty();
        }
    }

    // ================================================================ Wachtende programma's (hoofdstuk 23)

    /** Dit programma (of null = geen programma meer) wacht op deze robot tot hij terug is en verbinding heeft. */
    public void setPending(UUID id, @Nullable LogicProgram program) {
        pending.put(id, program == null ? new CompoundTag() : program.save());
        setDirty();
    }

    /** Wat er op deze robot wacht (leeg = "geen programma meer"), of null als er niets wacht. */
    public @Nullable CompoundTag pending(UUID id) {
        return pending.get(id);
    }

    public @Nullable Entry get(UUID id) {
        return robots.get(id);
    }

    /** Alle robots, in de volgorde waarin ze erbij kwamen. */
    public List<Entry> all() {
        return List.copyOf(robots.values());
    }

    /** De robots van deze eigenaar. */
    public List<Entry> ownedBy(@Nullable UUID owner) {
        List<Entry> list = new ArrayList<>();
        for (Entry entry : robots.values()) {
            if (Objects.equals(entry.owner(), owner)) {
                list.add(entry);
            }
        }
        return list;
    }

    /** De robots in het gebied van deze kern (zoals ze zich het laatst meldden). */
    public List<Entry> atCore(GlobalPos core) {
        List<Entry> list = new ArrayList<>();
        for (Entry entry : robots.values()) {
            if (core.equals(entry.core())) {
                list.add(entry);
            }
        }
        return list;
    }

    // ================================================================ Namen

    /** Heeft een andere robot van deze eigenaar al deze naam (hoofdletters tellen niet)? */
    public boolean nameTaken(@Nullable UUID owner, String name, @Nullable UUID except) {
        for (Entry entry : robots.values()) {
            if (Objects.equals(entry.owner(), owner) && !entry.id().equals(except)
                    && entry.name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** De eerste vrije naam voor een nieuwe robot van deze eigenaar: "Guard 1", "Guard 2"... */
    public String freeName(@Nullable UUID owner) {
        for (int i = 1; ; i++) {
            String name = "Guard " + i;
            if (!nameTaken(owner, name, null)) {
                return name;
            }
        }
    }

    /** Een naam die je zelf typt: zonder spaties eromheen, niet leeg, niet te lang. Null = niet goed. */
    public static @Nullable String cleanName(String typed) {
        String name = typed.strip();
        if (name.isEmpty() || name.length() > MAX_NAME || name.chars().anyMatch(c -> c < ' ' || c == '"')) {
            return null;
        }
        return name;
    }

    // ================================================================ Opslaan

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : robots.values()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("Id", entry.id());
            item.putString("Name", entry.name());
            if (entry.owner() != null) {
                item.putUUID("Owner", entry.owner());
            }
            item.putString("OwnerName", entry.ownerName());
            putPos(item, "Pos", entry.pos());
            if (entry.home() != null) {
                item.putLong("Home", entry.home().asLong());
            }
            if (entry.core() != null) {
                putPos(item, "Core", entry.core());
            }
            item.putInt("Parts", entry.parts());
            item.putString("State", entry.state().name());
            item.putFloat("Battery", entry.battery());
            item.putString("Program", entry.program());
            item.putLong("Seen", entry.seen());
            list.add(item);
        }
        tag.put("Robots", list);
        ListTag waiting = new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry : pending.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("Id", entry.getKey());
            item.put("Program", entry.getValue());
            waiting.add(item);
        }
        tag.put("Pending", waiting);
        return tag;
    }

    /** Leest wat {@link #save} schreef (public voor de tests). */
    public static BotNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        BotNetwork data = new BotNetwork();
        for (Tag element : tag.getList("Robots", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) element;
            RobotState state;
            try {
                state = RobotState.valueOf(item.getString("State"));
            } catch (IllegalArgumentException e) {
                state = RobotState.STANDBY;
            }
            UUID id = item.getUUID("Id");
            data.robots.put(id, new Entry(id, item.getString("Name"), item.hasUUID("Owner") ? item.getUUID("Owner") : null,
                    item.getString("OwnerName"), getPos(item, "Pos"),
                    item.contains("Home") ? BlockPos.of(item.getLong("Home")) : null,
                    item.contains("Core") ? getPos(item, "Core") : null, item.getInt("Parts"), state,
                    item.getFloat("Battery"), item.getString("Program"), item.getLong("Seen")));
        }
        for (Tag element : tag.getList("Pending", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) element;
            if (item.hasUUID("Id")) {
                data.pending.put(item.getUUID("Id"), item.getCompound("Program"));
            }
        }
        return data;
    }

    private static void putPos(CompoundTag tag, String key, GlobalPos pos) {
        tag.putString(key + "Dim", pos.dimension().location().toString());
        tag.putLong(key, pos.pos().asLong());
    }

    private static GlobalPos getPos(CompoundTag tag, String key) {
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString(key + "Dim"));
        ResourceKey<Level> level = dim == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, dim);
        return GlobalPos.of(level, BlockPos.of(tag.getLong(key)));
    }
}
