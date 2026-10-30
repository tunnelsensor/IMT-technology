package com.senne.imttech.redstone;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van een relay emitter: van wie hij is, welke relais hij schakelt en hoe.
 *
 * <h2>Werkt ook als je offline bent</h2>
 * Welke relais "base" zijn, staat in de lijst van de eigenaar (ImtRelays), en die hoort bij de speler:
 * is hij offline, dan kan niemand erin kijken. Daarom onthoudt de emitter zelf waar die relais staan
 * ({@link #targets}), net als het "telefoonboek" van een server (LogicDevices). Die kopie werken we bij
 * zolang de eigenaar online is: als je het scherm opslaat, elke 5 seconden, en vlak voor het schakelen.
 *
 * <h2>In een gebouw (hoofdstuk 39)</h2>
 * Een emitter die een gebouw neerzet (de val in de kluis van de Biebia's Industries Tower: een drukplaat met vier
 * laserzenders eromheen) heeft geen eigenaar en dus geen lijst. Zijn gebouw zet er "LinkAround" in: dan zoekt hij de
 * eerste keer dat hij tikt (of schakelt) zelf de laserzenders binnen zoveel blokken om hem heen, en die schakelt hij
 * voortaan. Zo werkt de val zonder redstone in de zenders, ook als het gebouw gedraaid is neergezet.
 */
public class RelayEmitterBlockEntity extends BlockEntity {
    /** Wat hij doet als de redstone verandert. */
    public enum Mode {
        /** Redstone aan = relais aan, redstone uit = relais uit. */
        FOLLOW,
        /** Elke keer dat de redstone aangaat: omschakelen. */
        TOGGLE,
        /** Elke keer dat de redstone aangaat: een korte puls. */
        PULSE;

        /** De vertaalsleutel voor het scherm: "screen.imttech.relay_emitter.mode.follow". */
        public String key() {
            return "screen.imttech.relay_emitter.mode." + name().toLowerCase(Locale.ROOT);
        }

        public static Mode of(int ordinal) {
            return values()[Math.floorMod(ordinal, values().length)];
        }
    }

    /** Eén apparaat dat hij schakelt: waar het staat, en welk kanaal (-1 = het hele blok). */
    public record Target(ResourceKey<Level> dimension, BlockPos pos, int channel) {
        static Target of(Relay relay) {
            return new Target(relay.dimension(), relay.pos().immutable(), relay.channel());
        }
    }

    /** Om de hoeveel ticks hij zijn kopie bijwerkt als de eigenaar online is (100 = 5 seconden). */
    private static final int REFRESH_TICKS = 100;

    private @Nullable UUID owner;
    private String ownerName = "";
    /** De naam van een relais, of een groep. */
    private String target = "";
    private Mode mode = Mode.FOLLOW;
    /** Waar de relais stonden toen we het laatst in de lijst van de eigenaar keken. */
    private List<Target> targets = List.of();
    /** Uit een gebouw (hoofdstuk 39): zoek de laserzenders binnen zoveel blokken om hem heen (0 = niet, of al gedaan). */
    private int linkAround;

    public RelayEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RELAY_EMITTER.get(), pos, state);
    }

    // ================================================================ Instellingen

    public @Nullable UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public String target() {
        return target;
    }

    public Mode mode() {
        return mode;
    }

    public List<Target> targets() {
        return targets;
    }

    public void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    /** Nieuwe instellingen uit het scherm (van de eigenaar, die dus online is). */
    public void configure(ServerPlayer player, String newTarget, Mode newMode) {
        target = newTarget;
        mode = newMode;
        refresh(player);
        setChanged();
    }

    // ================================================================ De kopie bijwerken

    /** Kijkt in de lijst van de eigenaar welke relais nu bij zijn naam of groep horen. */
    public void refresh(ServerPlayer player) {
        List<Target> fresh = new ArrayList<>();
        for (Relay relay : player.getData(ModAttachments.RELAYS).named(target)) {
            fresh.add(Target.of(relay));
        }
        if (!fresh.equals(targets)) {
            targets = List.copyOf(fresh);
            setChanged();
        }
    }

    /** De eigenaar, als hij nu online is. */
    private @Nullable ServerPlayer onlineOwner(ServerLevel level) {
        MinecraftServer server = level.getServer();
        return owner == null || server == null ? null : server.getPlayerList().getPlayer(owner);
    }

    /** Een emitter uit een gebouw: de laserzenders om hem heen worden zijn doelen (één keer, zie de uitleg bovenaan). */
    private void linkIfNeeded(ServerLevel level) {
        if (linkAround <= 0) {
            return;
        }
        List<Target> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-linkAround, -linkAround, -linkAround),
                worldPosition.offset(linkAround, linkAround, linkAround))) {
            if (level.getBlockState(pos).getBlock() instanceof com.senne.imttech.shield.LaserEmitterBlock) {
                found.add(new Target(level.dimension(), pos.immutable(), -1));
            }
        }
        targets = List.copyOf(found);
        linkAround = 0;
        setChanged();
    }

    public void serverTick(ServerLevel level) {
        linkIfNeeded(level);
        // Niet alle emitters in dezelfde tick: zijn plek bepaalt wanneer hij aan de beurt is.
        if (Math.floorMod(level.getGameTime() + worldPosition.hashCode(), REFRESH_TICKS) != 0) {
            return;
        }
        ServerPlayer player = onlineOwner(level);
        if (player != null) {
            refresh(player);
        }
    }

    // ================================================================ Schakelen

    /** Wat er moet gebeuren nu de redstone aan (true) of uit (false) ging; null = niets. */
    public static ImtRedstone.@Nullable Op opFor(Mode mode, boolean powered) {
        return switch (mode) {
            case FOLLOW -> powered ? ImtRedstone.Op.ON : ImtRedstone.Op.OFF;
            case TOGGLE -> powered ? ImtRedstone.Op.TOGGLE : null;
            case PULSE -> powered ? ImtRedstone.Op.PULSE : null;
        };
    }

    /** De redstone ging aan of uit (zie RelayEmitterBlock.tick): schakel de relais. Gratis, ook zonder bril. */
    public void signalChanged(ServerLevel level, boolean powered) {
        linkIfNeeded(level);
        ImtRedstone.Op op = opFor(mode, powered);
        if (op == null) {
            return;
        }
        ServerPlayer player = onlineOwner(level);
        if (player != null) {
            refresh(player);
        }
        for (Target target : targets) {
            ServerLevel targetLevel = level.getServer().getLevel(target.dimension());
            if (targetLevel == null) {
                continue;
            }
            if (!targetLevel.hasChunkAt(target.pos())) {
                targetLevel.getChunk(target.pos());
            }
            // Nooit het relais van iemand anders (13 oktober), ook niet uit een oude kopie.
            UUID relayOwner = ImtRedstone.relayOwner(targetLevel, target.pos());
            if (relayOwner != null && !relayOwner.equals(owner)) {
                continue;
            }
            ImtRedstone.switchAt(targetLevel, target.pos(), target.channel(), op, Math.round(ImtConfig.Relays.PULSE_TICKS));
        }
    }

    // ================================================================ Bewaren

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.putString("Target", target);
        tag.putString("Mode", mode.name());
        ListTag list = new ListTag();
        for (Target entry : targets) {
            CompoundTag one = new CompoundTag();
            one.putString("D", entry.dimension().location().toString());
            one.putInt("X", entry.pos().getX());
            one.putInt("Y", entry.pos().getY());
            one.putInt("Z", entry.pos().getZ());
            one.putInt("C", entry.channel());
            list.add(one);
        }
        tag.put("Targets", list);
        if (linkAround > 0) {
            tag.putInt("LinkAround", linkAround);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        target = tag.getString("Target");
        try {
            mode = Mode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException e) {
            mode = Mode.FOLLOW;
        }
        List<Target> list = new ArrayList<>();
        for (Tag entry : tag.getList("Targets", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) entry;
            ResourceLocation dimension = ResourceLocation.tryParse(one.getString("D"));
            if (dimension != null) {
                list.add(new Target(ResourceKey.create(Registries.DIMENSION, dimension),
                        new BlockPos(one.getInt("X"), one.getInt("Y"), one.getInt("Z")), one.getInt("C")));
            }
        }
        targets = List.copyOf(list);
        linkAround = tag.getInt("LinkAround");
    }
}
