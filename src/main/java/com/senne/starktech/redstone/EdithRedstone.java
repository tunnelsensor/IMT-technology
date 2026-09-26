package com.senne.starktech.redstone;

import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.edith.StarkAdvancements;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.network.RelayActionPayload;
import com.senne.starktech.network.RelayEditPayload;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.shield.EdithSwitchable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * E.D.I.T.H. stuurt je redstone aan.
 *
 * Je zet een E.D.I.T.H.-relais (RelayBlock) naast wat je wil aansturen: een deur, een lamp,
 * een zuiger, een trechter... Je geeft het een naam ("voordeur") en een groep ("huis").
 * Daarna kan E.D.I.T.H. het aanzetten, uitzetten, omschakelen of een korte puls geven -
 * één relais, of een hele groep tegelijk.
 *
 * Het werkt overal in dezelfde wereld: staat dat stukje wereld niet ingeladen (je bent ver weg),
 * dan laadt E.D.I.T.H. het even in om te schakelen.
 */
public final class EdithRedstone {
    private EdithRedstone() {}

    /** Wat je met een relais kunt doen. */
    public enum Op {
        ON, OFF, TOGGLE, PULSE
    }

    /** Een puls die straks weer uit moet. */
    private static final class Pulse {
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        int ticksLeft;

        Pulse(ResourceKey<Level> dimension, BlockPos pos, int ticksLeft) {
            this.dimension = dimension;
            this.pos = pos;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final List<Pulse> PULSES = new ArrayList<>();

    // ================================================================ Aanmelden en instellen

    /** Net neergezet: het relais komt in de lijst van de speler, met een standaardnaam. */
    public static void register(ServerPlayer player, BlockPos pos) {
        register(player, pos, "edith.starktech.relay.default_name");
    }

    /** Zelfde, maar met een eigen standaardnaam (zoals "Turret 3" voor een torentje). */
    public static void register(ServerPlayer player, BlockPos pos, String defaultNameKey) {
        EdithRelays data = player.getData(ModAttachments.RELAYS);
        if (data.indexAt(player.level().dimension(), pos) >= 0) {
            return; // stond er al in
        }
        if (data.relays().size() >= EdithBalance.MAX_RELAYS) {
            EdithServer.send(player, Component.translatable("edith.starktech.msg.relays_full", EdithBalance.MAX_RELAYS),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        String name = Component.translatable(defaultNameKey, data.relays().size() + 1).getString();
        Relay relay = new Relay(name, Relay.DEFAULT_GROUP, player.level().dimension(), pos);
        player.setData(ModAttachments.RELAYS, data.with(relay));
        StarkAdvancements.award(player, StarkAdvancements.RELAY);
        EdithServer.send(player, Component.translatable("edith.starktech.msg.relay_added", name), EdithMessagePayload.Level.INFO);
    }

    /**
     * Ruimt relais op die er niet meer staan.
     *
     * forget() hieronder vangt het slopen zelf op, maar niet alles: wie op dat moment uitgelogd
     * was, houdt zijn relais, en kastjes die al weg waren voordat dit bestond staan er ook nog
     * in. Daarom kijken we af en toe na of het kastje er echt nog staat. Alleen in stukken
     * wereld die geladen zijn: wat we niet kunnen zien, laten we met rust.
     */
    public static void pruneGhosts(ServerPlayer player) {
        EdithRelays data = player.getData(ModAttachments.RELAYS);
        ServerLevel level = player.serverLevel();
        for (int i = data.relays().size() - 1; i >= 0; i--) {
            Relay relay = data.relays().get(i);
            if (relay.dimension() == level.dimension()
                    && level.hasChunkAt(relay.pos())
                    && !isSwitchable(level.getBlockState(relay.pos()))) {
                data = data.without(i);
            }
        }
        if (data != player.getData(ModAttachments.RELAYS)) {
            player.setData(ModAttachments.RELAYS, data);
        }
    }

    /**
     * Staat hier iets dat E.D.I.T.H. kan schakelen? Een relais, maar ook een laserzender of
     * een schildkoepel: die meld je op dezelfde manier aan.
     */
    public static boolean isSwitchable(BlockState state) {
        return state.getBlock() instanceof RelayBlock || state.getBlock() instanceof EdithSwitchable;
    }

    /**
     * Het blok is weg (gesloopt, opgeblazen, weggeduwd). Dan hoort het ook uit de lijst van
     * iedereen die het had aangemeld: anders blijft zijn naamplaatje in de lucht hangen.
     */
    public static void forget(ServerLevel level, BlockPos pos) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            EdithRelays data = player.getData(ModAttachments.RELAYS);
            int index = data.indexAt(level.dimension(), pos);
            if (index >= 0) {
                player.setData(ModAttachments.RELAYS, data.without(index));
            }
        }
    }

    /** Rechtsklikken op een relais: het scherm openen waarin je naam en groep instelt. */
    public static void openEditor(ServerPlayer player, BlockPos pos) {
        ItemStack glasses = EdithServer.authorizedGlasses(player);
        if (glasses == null) {
            EdithServer.send(player, Component.translatable("edith.starktech.msg.relay_needs_edith"),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        EdithRelays data = player.getData(ModAttachments.RELAYS);
        int index = data.indexAt(player.level().dimension(), pos);
        if (index < 0) {
            // Iemand anders heeft hem neergezet: dan meld je hem nu aan op jouw bril.
            register(player, pos);
            index = player.getData(ModAttachments.RELAYS).indexAt(player.level().dimension(), pos);
        }
        if (index >= 0) {
            ModNetwork.sendTo(player, new RelayEditPayload(index));
        }
    }

    // ================================================================ Wat de speler vraagt

    /** Een bericht van het scherm (of van een spraakcommando) uitvoeren. */
    public static void handleAction(ServerPlayer player, RelayActionPayload payload) {
        ItemStack glasses = EdithServer.authorizedGlasses(player);
        if (glasses == null) {
            return;
        }
        EdithRelays data = player.getData(ModAttachments.RELAYS);
        switch (payload.op()) {
            case RENAME -> rename(player, data, payload);
            case REMOVE -> {
                Relay relay = data.get(payload.index());
                if (relay != null) {
                    player.setData(ModAttachments.RELAYS, data.without(payload.index()));
                    EdithServer.send(player, Component.translatable("edith.starktech.msg.relay_removed", relay.name()),
                            EdithMessagePayload.Level.INFO);
                }
            }
            case SWITCH -> switchRelays(player, glasses, data, payload);
        }
    }

    private static void rename(ServerPlayer player, EdithRelays data, RelayActionPayload payload) {
        Relay relay = data.get(payload.index());
        if (relay == null) {
            return;
        }
        String name = clean(payload.name(), EdithBalance.MAX_RELAY_NAME, relay.name());
        String group = clean(payload.group(), EdithBalance.MAX_RELAY_NAME, Relay.DEFAULT_GROUP);
        player.setData(ModAttachments.RELAYS, data.replace(payload.index(), new Relay(name, group, relay.dimension(), relay.pos())));
        EdithServer.send(player, Component.translatable("edith.starktech.msg.relay_named", name, group),
                EdithMessagePayload.Level.INFO);
    }

    /**
     * Schakelt één relais (als index >= 0) of alles wat bij de naam/groep past.
     * Elke schakeling kost een beetje energie.
     */
    private static void switchRelays(ServerPlayer player, ItemStack glasses, EdithRelays data, RelayActionPayload payload) {
        List<Relay> targets = payload.index() >= 0
                ? (data.get(payload.index()) == null ? List.<Relay>of() : List.of(data.get(payload.index())))
                : data.find(payload.name());
        if (targets.isEmpty()) {
            EdithServer.send(player, Component.translatable("edith.starktech.msg.relay_unknown", payload.name()),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        if (!EdithData.tryUseEnergy(glasses, EdithBalance.RELAY_COST * targets.size())) {
            EdithServer.send(player, Component.translatable("edith.starktech.msg.not_enough_energy",
                    EdithBalance.RELAY_COST * targets.size()), EdithMessagePayload.Level.WARNING);
            return;
        }
        MinecraftServer server = player.getServer();
        int done = 0;
        int missing = 0;
        for (Relay relay : targets) {
            if (apply(server, relay, payload.relayOp())) {
                done++;
            } else {
                missing++;
            }
        }
        if (done > 0) {
            Component what = targets.size() == 1
                    ? Component.literal(targets.getFirst().name())
                    : Component.translatable("edith.starktech.relay.count", done);
            EdithServer.send(player, Component.translatable("edith.starktech.msg.relay_switched", what,
                    Component.translatable("edith.starktech.relay.op." + payload.relayOp().name().toLowerCase(java.util.Locale.ROOT))),
                    EdithMessagePayload.Level.INFO);
        }
        if (missing > 0) {
            EdithServer.send(player, Component.translatable("edith.starktech.msg.relay_missing", missing),
                    EdithMessagePayload.Level.WARNING);
        }
    }

    /** Zet één relais echt om in de wereld. Geeft false als het blok er niet meer staat. */
    private static boolean apply(MinecraftServer server, Relay relay, Op op) {
        ServerLevel level = server == null ? null : server.getLevel(relay.dimension());
        if (level == null) {
            return false;
        }
        BlockPos pos = relay.pos();
        // Ver weg? Dan laadt E.D.I.T.H. dat stukje wereld even in.
        if (!level.hasChunkAt(pos)) {
            level.getChunk(pos);
        }
        BlockState state = level.getBlockState(pos);
        // E.D.I.T.H. kan een relais schakelen, maar ook een laserzender of schildkoepel
        // (die doen er zelf nog iets bij; zie EdithSwitchable).
        if (state.getBlock() instanceof EdithSwitchable switchable) {
            boolean now = switchable.isSwitchedOn(state);
            boolean wanted = switch (op) {
                case ON, PULSE -> true;
                case OFF -> false;
                case TOGGLE -> !now;
            };
            if (now != wanted) {
                switchable.setSwitched(level, pos, state, wanted);
            }
            if (op == Op.PULSE) {
                PULSES.add(new Pulse(relay.dimension(), pos, EdithBalance.RELAY_PULSE_TICKS));
            }
            return true;
        }
        if (!(state.getBlock() instanceof RelayBlock)) {
            return false; // weggehaald of opgeblazen
        }
        boolean now = state.getValue(RelayBlock.POWERED);
        boolean wanted = switch (op) {
            case ON, PULSE -> true;
            case OFF -> false;
            case TOGGLE -> !now;
        };
        if (now != wanted) {
            level.setBlock(pos, state.setValue(RelayBlock.POWERED, wanted), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, wanted ? 0.7f : 0.55f);
        }
        if (op == Op.PULSE) {
            PULSES.add(new Pulse(relay.dimension(), pos, EdithBalance.RELAY_PULSE_TICKS));
        }
        return true;
    }

    // ================================================================ Pulsen aftellen

    public static void onServerTick(ServerTickEvent.Post event) {
        if (PULSES.isEmpty()) {
            return;
        }
        Iterator<Pulse> it = PULSES.iterator();
        while (it.hasNext()) {
            Pulse pulse = it.next();
            if (--pulse.ticksLeft > 0) {
                continue;
            }
            it.remove();
            ServerLevel level = event.getServer().getLevel(pulse.dimension);
            if (level == null) {
                continue;
            }
            if (!level.hasChunkAt(pulse.pos)) {
                level.getChunk(pulse.pos);
            }
            BlockState state = level.getBlockState(pulse.pos);
            if (state.getBlock() instanceof EdithSwitchable switchable && switchable.isSwitchedOn(state)) {
                switchable.setSwitched(level, pulse.pos, state, false);
            } else if (state.getBlock() instanceof RelayBlock && state.getValue(RelayBlock.POWERED)) {
                level.setBlock(pulse.pos, state.setValue(RelayBlock.POWERED, false), Block.UPDATE_ALL);
                level.playSound(null, pulse.pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 0.55f);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        PULSES.clear();
    }

    // ================================================================ Hulpjes

    /** Maakt een naam netjes: geen opmaakcodes of rare tekens, niet te lang, nooit leeg. */
    private static String clean(String text, int maxLength, String fallback) {
        String clean = text.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", "").trim();
        if (clean.length() > maxLength) {
            clean = clean.substring(0, maxLength).trim();
        }
        return clean.isEmpty() ? fallback : clean;
    }
}
