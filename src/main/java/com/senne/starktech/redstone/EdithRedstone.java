package com.senne.starktech.redstone;

import com.senne.starktech.config.StarkConfig;
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
import org.jetbrains.annotations.Nullable;

/**
 * E.D.I.T.H. stuurt je redstone aan.
 *
 * Je zet een E.D.I.T.H.-relais (RelayBlock) naast wat je wil aansturen: een deur, een lamp,
 * een zuiger, een trechter... Je geeft het een naam ("voordeur") en eventueel een groep ("huis"), en
 * groepen kunnen in groepen zitten ("huis" in "base", zie EdithRelays).
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
        /** Welk kanaal (een kant van een stroomkast), of -1 = het hele blok. */
        final int channel;
        int ticksLeft;

        Pulse(ResourceKey<Level> dimension, BlockPos pos, int channel, int ticksLeft) {
            this.dimension = dimension;
            this.pos = pos;
            this.channel = channel;
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
        // Een relais van iemand anders kun je niet in je lijst zetten (13 oktober).
        String other = otherOwner(player, player.serverLevel(), pos);
        if (other != null) {
            tell(player, Component.translatable("edith.starktech.msg.relay_not_yours", other), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (data.relays().size() >= Math.round(StarkConfig.Relays.MAX_RELAYS)) {
            tell(player, Component.translatable("edith.starktech.msg.relays_full", Math.round(StarkConfig.Relays.MAX_RELAYS)),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        String name = Component.translatable(defaultNameKey, data.relays().size() + 1).getString();
        Relay relay = new Relay(name, "", player.level().dimension(), pos);
        player.setData(ModAttachments.RELAYS, data.with(relay));
        StarkAdvancements.award(player, StarkAdvancements.RELAY);
        tell(player, Component.translatable("edith.starktech.msg.relay_added", name), EdithMessagePayload.Level.INFO);
    }

    // ================================================================ Van wie is het? (13 oktober)

    /**
     * Mag deze speler het relais op deze plek gebruiken? Jouw wens: "zorg dat de relay die je zelf plaatst,
     * jij de enige bent die die kan bewerken".
     *
     * @return null als het mag (het relais is van jou, of het is geen relais maar bv. een koepel), anders de
     *         naam van de eigenaar. Een relais zonder eigenaar (van vóór 13 oktober) wordt hier van jou:
     *         wie het als eerste gebruikt, of het al in zijn lijst had.
     */
    public static @Nullable String otherOwner(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            level.getChunk(pos);
        }
        if (!(level.getBlockEntity(pos) instanceof RelayBlockEntity relay)) {
            return null;
        }
        if (relay.owner() == null) {
            relay.setOwner(player);
            return null;
        }
        return relay.owner().equals(player.getUUID()) ? null : relay.ownerName();
    }

    /** Van wie is het relais op deze plek? Null als het geen relais is, of nog van niemand. */
    public static @Nullable java.util.UUID relayOwner(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RelayBlockEntity relay ? relay.owner() : null;
    }

    /**
     * Alleen de relais die van deze speler zijn. Staat er een relais van iemand anders in zijn lijst (dat
     * kon vóór 13 oktober), dan gaat het eruit en zegt E.D.I.T.H. van wie het is.
     */
    private static List<Relay> keepOwn(ServerPlayer player, List<Relay> relays) {
        MinecraftServer server = player.getServer();
        List<Relay> own = new ArrayList<>();
        for (Relay relay : relays) {
            ServerLevel level = server == null ? null : server.getLevel(relay.dimension());
            String other = level == null || relay.channel() >= 0 ? null : otherOwner(player, level, relay.pos());
            if (other == null) {
                own.add(relay);
                continue;
            }
            EdithRelays data = player.getData(ModAttachments.RELAYS);
            int index = data.indexAt(relay.dimension(), relay.pos());
            if (index >= 0) {
                player.setData(ModAttachments.RELAYS, data.without(index));
            }
            tell(player, Component.translatable("edith.starktech.msg.relay_not_yours", other), EdithMessagePayload.Level.WARNING);
        }
        return own;
    }

    /**
     * Meldt één schakelaar van een blok aan (een kant van een stroomkast), met een eigen naam en
     * groep. Stond hij er al, dan gebeurt er niets.
     */
    public static void registerChannel(ServerPlayer player, BlockPos pos, int channel, String name, String group) {
        EdithRelays data = player.getData(ModAttachments.RELAYS);
        if (data.indexAt(player.level().dimension(), pos, channel) >= 0) {
            return;
        }
        if (data.relays().size() >= Math.round(StarkConfig.Relays.MAX_RELAYS)) {
            EdithServer.send(player, Component.translatable("edith.starktech.msg.relays_full", Math.round(StarkConfig.Relays.MAX_RELAYS)),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        player.setData(ModAttachments.RELAYS, data.with(new Relay(name, group, player.level().dimension(), pos, channel)));
    }

    /** Een groep voor de volgende stroomkast van deze speler: "Cabinet 1", "Cabinet 2"... */
    public static String nextCabinetGroup(ServerPlayer player) {
        EdithRelays data = player.getData(ModAttachments.RELAYS);
        String base = Component.translatable("screen.starktech.cabinet.group").getString();
        int number = 1;
        while (data.hasGroup(base + " " + number)) {
            number++;
        }
        return base + " " + number;
    }

    /**
     * Ruimt relais op die er niet meer staan, of die van iemand anders zijn.
     *
     * forget() hieronder vangt het slopen zelf op, maar niet alles: wie op dat moment uitgelogd
     * was, houdt zijn relais, en kastjes die al weg waren voordat dit bestond staan er ook nog
     * in. Daarom kijken we af en toe na of het kastje er echt nog staat. Alleen in stukken
     * wereld die geladen zijn: wat we niet kunnen zien, laten we met rust.
     *
     * Sinds 13 oktober ook: een relais van iemand anders gaat eruit (dat kon je vroeger aanmelden), en
     * een relais zonder eigenaar (van vóór 13 oktober) wordt van jou, omdat jij het in je lijst had.
     */
    public static void pruneGhosts(ServerPlayer player) {
        EdithRelays data = player.getData(ModAttachments.RELAYS);
        ServerLevel level = player.serverLevel();
        for (int i = data.relays().size() - 1; i >= 0; i--) {
            Relay relay = data.relays().get(i);
            if (relay.dimension() == level.dimension()
                    && level.hasChunkAt(relay.pos())
                    && (!isSwitchable(level.getBlockState(relay.pos()))
                            || relay.channel() < 0 && otherOwner(player, level, relay.pos()) != null)) {
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

    /**
     * Rechtsklikken op een relais: het scherm openen waarin je naam en groep instelt. Daar heb je je bril
     * niet voor nodig (13 oktober): je staat er toch naast. Alleen de eigenaar: een ander hoort van wie
     * het is.
     */
    public static void openEditor(ServerPlayer player, BlockPos pos) {
        String other = otherOwner(player, player.serverLevel(), pos);
        if (other != null) {
            tell(player, Component.translatable("edith.starktech.msg.relay_not_yours", other), EdithMessagePayload.Level.WARNING);
            return;
        }
        EdithRelays data = player.getData(ModAttachments.RELAYS);
        int index = data.indexAt(player.level().dimension(), pos);
        if (index < 0) {
            // Van jou, maar (nog) niet in je lijst (je haalde het eruit, of het is van vóór 13 oktober).
            register(player, pos);
            index = player.getData(ModAttachments.RELAYS).indexAt(player.level().dimension(), pos);
        }
        if (index >= 0) {
            ModNetwork.sendTo(player, new RelayEditPayload(index));
        }
    }

    // ================================================================ Wat de speler vraagt

    /**
     * Een bericht van het scherm (of van een spraakcommando) uitvoeren.
     *
     * Wat alleen je eigen lijst verandert (naam, groep, groepen, weghalen), mag zonder bril: je stond bij
     * het relais toen je het scherm opende (13 oktober: "relays renamen zonder bril"), of je zit aan de
     * Stark OS-desktop (14 oktober: "groepen configureer je in de desktop"). Op afstand schakelen kan alleen
     * met je bril op; zonder bril alleen het relais waar je naast staat.
     */
    public static void handleAction(ServerPlayer player, RelayActionPayload payload) {
        ItemStack glasses = EdithServer.authorizedGlasses(player);
        EdithRelays data = player.getData(ModAttachments.RELAYS);
        switch (payload.op()) {
            case RENAME -> rename(player, data, payload);
            case REMOVE -> {
                Relay relay = data.get(payload.index());
                if (relay != null) {
                    player.setData(ModAttachments.RELAYS, data.without(payload.index()));
                    tell(player, Component.translatable("edith.starktech.msg.relay_removed", relay.name()),
                            EdithMessagePayload.Level.INFO);
                }
            }
            case SWITCH -> {
                if (glasses != null) {
                    switchRelays(player, glasses, data, payload);
                } else {
                    switchNearby(player, data, payload);
                }
            }
            case INVERT -> invert(player, glasses, data.get(payload.index()));
            case GROUP_CREATE -> createGroup(player, data, payload.name(), payload.text());
            case GROUP_PARENT -> setParent(player, data, payload.name(), payload.text());
            case GROUP_RENAME -> renameGroup(player, data, payload.name(), payload.text());
            case GROUP_DELETE -> deleteGroup(player, data, payload.name());
        }
    }

    private static void rename(ServerPlayer player, EdithRelays data, RelayActionPayload payload) {
        Relay relay = data.get(payload.index());
        if (relay == null || keepOwn(player, List.of(relay)).isEmpty()) {
            return;
        }
        String name = clean(payload.name(), Math.round(StarkConfig.Relays.MAX_NAME), relay.name());
        // De groep kies je uit je groepen; een nieuwe (die er net nog niet was) mag, als je er nog plek voor hebt.
        String group = clean(payload.text(), Math.round(StarkConfig.Relays.MAX_NAME), "");
        if (!group.isEmpty() && !data.hasGroup(group) && groupsFull(player, data)) {
            group = relay.group();
        }
        group = data.spelling(group);
        player.setData(ModAttachments.RELAYS, data.replace(payload.index(), relay.withName(name).withGroup(group)));
        tell(player, group.isEmpty()
                        ? Component.translatable("edith.starktech.msg.relay_named_ungrouped", name)
                        : Component.translatable("edith.starktech.msg.relay_named", name, group),
                EdithMessagePayload.Level.INFO);
    }

    // ================================================================ Omkeren (13 oktober)

    /**
     * De knop "invert": het relais geeft voortaan redstone als het UIT staat, en niets als het aan staat.
     * Alleen de eigenaar, en alleen als hij erbij staat of zijn bril op heeft.
     */
    private static void invert(ServerPlayer player, @Nullable ItemStack glasses, @Nullable Relay relay) {
        MinecraftServer server = player.getServer();
        ServerLevel level = relay == null || server == null ? null : server.getLevel(relay.dimension());
        if (level == null || relay.channel() >= 0 || keepOwn(player, List.of(relay)).isEmpty()) {
            return;
        }
        if (glasses == null && !nearby(player, relay)) {
            tell(player, Component.translatable("edith.starktech.msg.relay_too_far"), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (!level.hasChunkAt(relay.pos())) {
            level.getChunk(relay.pos());
        }
        BlockState state = level.getBlockState(relay.pos());
        if (!(state.getBlock() instanceof RelayBlock)) {
            return; // een koepel of torentje geeft geen redstone: daar valt niets om te keren
        }
        boolean inverted = !state.getValue(RelayBlock.INVERTED);
        level.setBlock(relay.pos(), state.setValue(RelayBlock.INVERTED, inverted), Block.UPDATE_ALL);
        level.playSound(null, relay.pos(), SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.5f, inverted ? 0.55f : 0.5f);
        tell(player, Component.translatable(inverted ? "edith.starktech.msg.relay_inverted" : "edith.starktech.msg.relay_not_inverted",
                relay.name()), EdithMessagePayload.Level.INFO);
    }

    // ================================================================ Groepen in groepen (14 oktober)

    /** Heeft deze speler al zoveel groepen als mag? Dan zegt E.D.I.T.H. het. */
    private static boolean groupsFull(ServerPlayer player, EdithRelays data) {
        int max = Math.round(StarkConfig.Relays.MAX_GROUPS);
        if (data.groups().size() < max) {
            return false;
        }
        tell(player, Component.translatable("edith.starktech.msg.groups_full", max), EdithMessagePayload.Level.WARNING);
        return true;
    }

    /** Een nieuwe, lege groep (zoals "base"), in {@code parent} ("" = bovenaan). */
    private static void createGroup(ServerPlayer player, EdithRelays data, String typed, String parent) {
        String group = clean(typed, Math.round(StarkConfig.Relays.MAX_NAME), "");
        if (group.isEmpty()) {
            return;
        }
        if (data.hasGroup(group)) {
            tell(player, Component.translatable("edith.starktech.msg.group_exists", data.spelling(group)), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (groupsFull(player, data)) {
            return;
        }
        player.setData(ModAttachments.RELAYS, data.withGroup(group, parent));
        tell(player, data.hasGroup(parent)
                        ? Component.translatable("edith.starktech.msg.group_created_in", group, data.spelling(parent))
                        : Component.translatable("edith.starktech.msg.group_created", group),
                EdithMessagePayload.Level.INFO);
    }

    /**
     * "{@code group} zit voortaan in {@code parent}" ("" = bovenaan). Een groep zit maar in één groep, dus hij
     * verhuist. Een rondje ("base" in "temp" terwijl "temp" al in "base" zit) weigeren we.
     */
    private static void setParent(ServerPlayer player, EdithRelays data, String group, String parent) {
        if (!data.hasGroup(group) || !parent.isEmpty() && !data.hasGroup(parent) || data.parentOf(group).equalsIgnoreCase(parent)) {
            return;
        }
        if (data.wouldLoop(group, parent)) {
            tell(player, Component.translatable("edith.starktech.msg.group_loop", data.spelling(group), data.spelling(parent)),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        player.setData(ModAttachments.RELAYS, data.withParent(group, parent));
        tell(player, parent.isEmpty()
                        ? Component.translatable("edith.starktech.msg.group_moved_top", data.spelling(group))
                        : Component.translatable("edith.starktech.msg.group_moved", data.spelling(group), data.spelling(parent)),
                EdithMessagePayload.Level.INFO);
    }

    /** Een groep een andere naam geven. De relais en groepen erin gaan mee. */
    private static void renameGroup(ServerPlayer player, EdithRelays data, String group, String typed) {
        String fresh = clean(typed, Math.round(StarkConfig.Relays.MAX_NAME), "");
        if (!data.hasGroup(group) || fresh.isEmpty() || fresh.equals(data.spelling(group))) {
            return;
        }
        if (data.hasGroup(fresh) && !fresh.equalsIgnoreCase(group)) {
            tell(player, Component.translatable("edith.starktech.msg.group_exists", data.spelling(fresh)), EdithMessagePayload.Level.WARNING);
            return;
        }
        String old = data.spelling(group);
        player.setData(ModAttachments.RELAYS, data.renameGroup(group, fresh));
        tell(player, Component.translatable("edith.starktech.msg.group_renamed", old, fresh), EdithMessagePayload.Level.INFO);
    }

    /** Gooit een groep weg. Wat erin zat, schuift een laag omhoog; de relais zelf blijven. */
    private static void deleteGroup(ServerPlayer player, EdithRelays data, String group) {
        if (!data.hasGroup(group)) {
            return;
        }
        String parent = data.parentOf(group);
        player.setData(ModAttachments.RELAYS, data.withoutGroup(group));
        tell(player, parent.isEmpty()
                        ? Component.translatable("edith.starktech.msg.group_deleted", data.spelling(group))
                        : Component.translatable("edith.starktech.msg.group_deleted_into", data.spelling(group), data.spelling(parent)),
                EdithMessagePayload.Level.INFO);
    }

    /**
     * Schakelt één relais (als index >= 0) of alles wat bij de naam/groep past. Alleen je eigen relais.
     * Elke schakeling kost een beetje energie.
     */
    private static void switchRelays(ServerPlayer player, ItemStack glasses, EdithRelays data, RelayActionPayload payload) {
        List<Relay> found = payload.index() >= 0
                ? (data.get(payload.index()) == null ? List.<Relay>of() : List.of(data.get(payload.index())))
                : payload.index() == RelayActionPayload.GROUP_ONLY ? data.inGroup(payload.name()) : data.find(payload.name());
        List<Relay> targets = keepOwn(player, found);
        if (targets.isEmpty() && !found.isEmpty()) {
            return; // alleen relais van een ander: dat zei keepOwn al
        }
        if (targets.isEmpty()) {
            EdithServer.send(player, Component.translatable("edith.starktech.msg.relay_unknown", payload.name()),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        if (!EdithData.tryUseEnergy(glasses, StarkConfig.Relays.COST * targets.size())) {
            EdithServer.send(player, Component.translatable("edith.starktech.msg.not_enough_energy",
                    com.senne.starktech.config.Numbers.text(StarkConfig.Relays.COST * targets.size())), EdithMessagePayload.Level.WARNING);
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

    /**
     * Zonder bril: alleen het ene relais waar je naast staat (de knop "test" in zijn scherm), en gratis.
     * Alleen je eigen relais.
     */
    private static void switchNearby(ServerPlayer player, EdithRelays data, RelayActionPayload payload) {
        Relay relay = data.get(payload.index());
        MinecraftServer server = player.getServer();
        if (relay == null || server == null) {
            return;
        }
        if (!nearby(player, relay)) {
            tell(player, Component.translatable("edith.starktech.msg.relay_too_far"), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (!keepOwn(player, List.of(relay)).isEmpty()) {
            apply(server, relay, payload.relayOp());
        }
    }

    /** Zet één relais echt om in de wereld. Geeft false als het blok er niet meer staat. */
    private static boolean apply(MinecraftServer server, Relay relay, Op op) {
        ServerLevel level = server == null ? null : server.getLevel(relay.dimension());
        if (level == null) {
            return false;
        }
        return switchAt(level, relay.pos(), relay.channel(), op, Math.round(StarkConfig.Relays.PULSE_TICKS));
    }

    /**
     * Zet het apparaat op deze plek om: een relais, een laserzender, koepel of torentje (EdithSwitchable),
     * of één schakelaar van een stroomkast ({@code channel} = de kant). Bij PULSE gaat het na
     * {@code pulseTicks} vanzelf weer uit. Gebruikt door je bril en door de diagrammen van de server
     * (die kiezen zelf hoe lang een puls duurt). Geeft false als er niets (meer) staat.
     *
     * Ging het apparaat echt aan of uit, dan hoort de server dat (voor "when turned_on(...)").
     */
    public static boolean switchAt(ServerLevel level, BlockPos pos, int channel, Op op, int pulseTicks) {
        // Ver weg? Dan laadt E.D.I.T.H. dat stukje wereld even in.
        if (!level.hasChunkAt(pos)) {
            level.getChunk(pos);
        }
        Boolean before = isOnAt(level, pos, channel);
        if (before == null) {
            return false; // weggehaald of opgeblazen
        }
        boolean wanted = switch (op) {
            case ON, PULSE -> true;
            case OFF -> false;
            case TOGGLE -> !before;
        };
        BlockState state = level.getBlockState(pos);
        if (channel >= 0 && state.getBlock() instanceof com.senne.starktech.energy.PowerCabinetBlock) {
            // Eén schakelaar van een stroomkast (één kant).
            com.senne.starktech.energy.PowerCabinets.setSwitch(level, pos, net.minecraft.core.Direction.from3DDataValue(channel), wanted);
        } else if (state.getBlock() instanceof EdithSwitchable switchable) {
            // Een laserzender of schildkoepel doet er zelf nog iets bij (zie EdithSwitchable).
            if (before != wanted) {
                switchable.setSwitched(level, pos, state, wanted);
            }
        } else if (before != wanted) {
            level.setBlock(pos, state.setValue(RelayBlock.POWERED, wanted), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, wanted ? 0.7f : 0.55f);
        }
        if (op == Op.PULSE) {
            PULSES.add(new Pulse(level.dimension(), pos.immutable(), channel, Math.max(1, pulseTicks)));
        }
        changed(level, pos, channel, before);
        return true;
    }

    /**
     * Staat het apparaat op deze plek aan? Null als er niets staat dat E.D.I.T.H. kan schakelen (of
     * een stroomkast zonder dit kanaal).
     */
    public static @Nullable Boolean isOnAt(ServerLevel level, BlockPos pos, int channel) {
        BlockState state = level.getBlockState(pos);
        if (channel >= 0) {
            return state.getBlock() instanceof com.senne.starktech.energy.PowerCabinetBlock
                    ? com.senne.starktech.energy.PowerCabinetBlock.isOn(state, net.minecraft.core.Direction.from3DDataValue(channel)) : null;
        }
        if (state.getBlock() instanceof EdithSwitchable switchable) {
            return switchable.isSwitchedOn(state);
        }
        if (state.getBlock() instanceof RelayBlock) {
            return state.getValue(RelayBlock.POWERED);
        }
        return null;
    }

    /** Is het apparaat echt van stand veranderd? Dan krijgt de server van die plek het te horen. */
    private static void changed(ServerLevel level, BlockPos pos, int channel, boolean before) {
        Boolean after = isOnAt(level, pos, channel);
        if (after != null && after != before) {
            com.senne.starktech.logic.LogicRunner.switched(level, pos, channel, after);
        }
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
            Boolean before = isOnAt(level, pulse.pos, pulse.channel);
            if (pulse.channel >= 0 && state.getBlock() instanceof com.senne.starktech.energy.PowerCabinetBlock) {
                com.senne.starktech.energy.PowerCabinets.setSwitch(level, pulse.pos,
                        net.minecraft.core.Direction.from3DDataValue(pulse.channel), false);
            } else if (state.getBlock() instanceof EdithSwitchable switchable && switchable.isSwitchedOn(state)) {
                switchable.setSwitched(level, pulse.pos, state, false);
            } else if (state.getBlock() instanceof RelayBlock && state.getValue(RelayBlock.POWERED)) {
                level.setBlock(pulse.pos, state.setValue(RelayBlock.POWERED, false), Block.UPDATE_ALL);
                level.playSound(null, pulse.pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 0.55f);
            }
            if (before != null) {
                changed(level, pulse.pos, pulse.channel, before);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        PULSES.clear();
    }

    // ================================================================ Hulpjes

    /** Zo dichtbij moet je zonder bril bij een relais staan (blokken), net als bij een lamp of stroomkast. */
    private static final double REACH = 6.0;

    /** Staat de speler bij dit relais (zelfde wereld, binnen REACH blokken)? */
    private static boolean nearby(ServerPlayer player, Relay relay) {
        return player.level().dimension() == relay.dimension()
                && player.distanceToSqr(relay.pos().getCenter()) <= REACH * REACH;
    }

    /**
     * Iets zeggen tegen de speler. Met je bril op zegt E.D.I.T.H. het (in je HUD); zonder bril zie je
     * het even boven je hotbar, anders merk je niet dat het gelukt is.
     */
    private static void tell(ServerPlayer player, Component message, EdithMessagePayload.Level level) {
        if (EdithServer.authorizedGlasses(player) != null) {
            EdithServer.send(player, message, level);
        } else {
            player.displayClientMessage(message, true);
        }
    }

    /** Maakt een naam netjes: geen opmaakcodes of rare tekens, niet te lang, nooit leeg. */
    public static String clean(String text, int maxLength, String fallback) {
        String clean = text.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", "").trim();
        if (clean.length() > maxLength) {
            clean = clean.substring(0, maxLength).trim();
        }
        return clean.isEmpty() ? fallback : clean;
    }
}
