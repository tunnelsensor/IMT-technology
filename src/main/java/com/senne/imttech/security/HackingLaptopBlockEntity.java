package com.senne.imttech.security;

import com.senne.imttech.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Wat de laptop op de beveiligingskern onthoudt: van wie hij is, het getal waar zijn puzzel uit
 * komt ({@code seed}), en elk commando dat je al typte. Met die twee speel je de terminal precies
 * na (HackTerminal.replay): zo ben je je voortgang niet kwijt als je het scherm sluit of even
 * wegloopt, en kijkt de server zelf na of de hack echt gelukt is.
 */
public class HackingLaptopBlockEntity extends BlockEntity {
    /** Meer commando's onthouden we niet (niemand heeft er zoveel nodig; zo blijft de save klein). */
    public static final int MAX_COMMANDS = 400;

    private @Nullable UUID owner;
    private String ownerName = "";
    private long seed;
    private long startedAt;
    private final List<String> commands = new ArrayList<>();

    public HackingLaptopBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HACKING_LAPTOP.get(), pos, state);
    }

    /** Een nieuwe hack: deze speler, een nieuwe puzzel, nog niets getypt. */
    public void start(ServerPlayer player, long seed, long gameTime) {
        this.owner = player.getUUID();
        this.ownerName = player.getGameProfile().getName();
        this.seed = seed;
        this.startedAt = gameTime;
        commands.clear();
        setChanged();
    }

    public boolean isOwner(ServerPlayer player) {
        return player.getUUID().equals(owner);
    }

    public String ownerName() {
        return ownerName;
    }

    public long seed() {
        return seed;
    }

    public long startedAt() {
        return startedAt;
    }

    public List<String> commands() {
        return commands;
    }

    /** Een getypt commando onthouden. Geeft false als de laptop vol zit. */
    public boolean add(String command) {
        if (commands.size() >= MAX_COMMANDS) {
            return false;
        }
        commands.add(command);
        setChanged();
        return true;
    }

    /** De terminal zoals hij nu is (alles opnieuw afgespeeld). */
    public HackTerminal terminal() {
        return HackTerminal.replay(seed, ownerName, commands);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.putLong("Seed", seed);
        tag.putLong("StartedAt", startedAt);
        ListTag list = new ListTag();
        for (String command : commands) {
            list.add(StringTag.valueOf(command));
        }
        tag.put("Commands", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        seed = tag.getLong("Seed");
        startedAt = tag.getLong("StartedAt");
        commands.clear();
        for (Tag entry : tag.getList("Commands", Tag.TAG_STRING)) {
            commands.add(entry.getAsString());
        }
    }
}
