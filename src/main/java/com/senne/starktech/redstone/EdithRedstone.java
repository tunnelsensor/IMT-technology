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
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
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
        if (data.relays().size() >= Math.round(StarkConfig.Relays.MAX_RELAYS)) {
            EdithServer.send(player, Component.translatable("edith.starktech.msg.relays_full", Math.round(StarkConfig.Relays.MAX_RELAYS)),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        String name = Component.translatable(defaultNameKey, data.relays().size() + 1).getString();
        Relay relay = new Relay(name, List.of(), player.level().dimension(), pos);
        player.setData(ModAttachments.RELAYS, data.with(relay));
        StarkAdvancements.award(player, StarkAdvancements.RELAY);
        EdithServer.send(player, Component.translatable("edith.starktech.msg.relay_added", name), EdithMessagePayload.Level.INFO);
    }

    /**
     * Meldt één schakelaar van een blok aan (een kant van een stroomkast), met een eigen naam en
     * tag. Stond hij er al, dan gebeurt er niets.
     */
    public static void registerChannel(ServerPlayer player, BlockPos pos, int channel, String name, String tag) {
        EdithRelays data = player.getData(ModAttachments.RELAYS);
        if (data.indexAt(player.level().dimension(), pos, channel) >= 0) {
            return;
        }
        if (data.relays().size() >= Math.round(StarkConfig.Relays.MAX_RELAYS)) {
            EdithServer.send(player, Component.translatable("edith.starktech.msg.relays_full", Math.round(StarkConfig.Relays.MAX_RELAYS)),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        player.setData(ModAttachments.RELAYS, data.with(new Relay(name, List.of(tag), player.level().dimension(), pos, channel)));
    }

    /** Een tag voor de volgende stroomkast van deze speler: "Cabinet 1", "Cabinet 2"... */
    public static String nextCabinetGroup(ServerPlayer player) {
        EdithRelays data = player.getData(ModAttachments.RELAYS);
        String base = Component.translatable("screen.starktech.cabinet.group").getString();
        int number = 1;
        while (data.hasTag(base + " " + number)) {
            number++;
        }
        return base + " " + number;
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

    /**
     * Rechtsklikken op een relais: het scherm openen waarin je naam en tags instelt. Daar heb je je bril
     * niet voor nodig (13 oktober): je staat er toch naast.
     */
    public static void openEditor(ServerPlayer player, BlockPos pos) {
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

    /**
     * Een bericht van het scherm (of van een spraakcommando) uitvoeren.
     *
     * Wat alleen je eigen lijst verandert (naam, tags, weghalen), mag zonder bril: je stond bij het
     * relais toen je het scherm opende (13 oktober: "relays renamen zonder bril"). Op afstand schakelen
     * kan alleen met je bril op; zonder bril alleen het relais waar je naast staat.
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
            case TAG_CREATE -> createTag(player, data, payload.name());
            case TAG_PARENT -> setParent(player, data, payload.name(), payload.text(), true);
            case TAG_UNPARENT -> setParent(player, data, payload.name(), payload.text(), false);
            case TAG_DELETE -> deleteTag(player, data, payload.name());
        }
    }

    private static void rename(ServerPlayer player, EdithRelays data, RelayActionPayload payload) {
        Relay relay = data.get(payload.index());
        if (relay == null) {
            return;
        }
        String name = clean(payload.name(), Math.round(StarkConfig.Relays.MAX_NAME), relay.name());
        List<String> tags = parseTags(payload.text());
        player.setData(ModAttachments.RELAYS, data.replace(payload.index(), relay.withName(name).withTags(tags)));
        tell(player, tags.isEmpty()
                        ? Component.translatable("edith.starktech.msg.relay_named_untagged", name)
                        : Component.translatable("edith.starktech.msg.relay_named", name, String.join(", ", tags)),
                EdithMessagePayload.Level.INFO);
    }

    /**
     * Maakt van wat je typte ("security room, doors") een lijst tags: netjes, elke tag één keer
     * (hoofdletters maken niet uit), hooguit MAX_TAGS.
     */
    public static List<String> parseTags(String text) {
        List<String> tags = new ArrayList<>();
        int max = Math.round(StarkConfig.Relays.MAX_TAGS);
        for (String part : text.split(",")) {
            String tag = clean(part, Math.round(StarkConfig.Relays.MAX_NAME), "");
            if (!tag.isEmpty() && tags.size() < max && tags.stream().noneMatch(own -> own.equalsIgnoreCase(tag))) {
                tags.add(tag);
            }
        }
        return tags;
    }

    // ================================================================ Omkeren (13 oktober)

    /**
     * De knop "invert": het relais geeft voortaan redstone als het UIT staat, en niets als het aan staat.
     * Dat staat in het blok zelf (iedereen merkt het), dus mag het alleen als je erbij staat of je bril
     * op hebt, en niet in de basis van een ander.
     */
    private static void invert(ServerPlayer player, @Nullable ItemStack glasses, @Nullable Relay relay) {
        MinecraftServer server = player.getServer();
        ServerLevel level = relay == null || server == null ? null : server.getLevel(relay.dimension());
        if (level == null || relay.channel() >= 0) {
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
        if (protectedFrom(player, level, relay.pos())) {
            return;
        }
        boolean inverted = !state.getValue(RelayBlock.INVERTED);
        level.setBlock(relay.pos(), state.setValue(RelayBlock.INVERTED, inverted), Block.UPDATE_ALL);
        level.playSound(null, relay.pos(), SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.5f, inverted ? 0.55f : 0.5f);
        tell(player, Component.translatable(inverted ? "edith.starktech.msg.relay_inverted" : "edith.starktech.msg.relay_not_inverted",
                relay.name()), EdithMessagePayload.Level.INFO);
    }

    // ================================================================ Tags in tags (13 oktober)

    /** Een nieuwe, lege tag (zoals "base") om andere tags in te zetten. */
    private static void createTag(ServerPlayer player, EdithRelays data, String typed) {
        String tag = clean(typed, Math.round(StarkConfig.Relays.MAX_NAME), "");
        if (tag.isEmpty() || data.hasTag(tag)) {
            return;
        }
        player.setData(ModAttachments.RELAYS, data.withTag(tag));
        tell(player, Component.translatable("edith.starktech.msg.tag_created", tag), EdithMessagePayload.Level.INFO);
    }

    /** "{@code tag} zit (niet meer) in {@code parent}". Een rondje (base in base) weigeren we. */
    private static void setParent(ServerPlayer player, EdithRelays data, String tag, String parent, boolean add) {
        if (!data.hasTag(tag) || !data.hasTag(parent)) {
            return;
        }
        if (!add) {
            player.setData(ModAttachments.RELAYS, data.withoutParent(tag, parent));
            return;
        }
        if (data.wouldLoop(tag, parent)) {
            tell(player, Component.translatable("edith.starktech.msg.tag_loop", tag, parent), EdithMessagePayload.Level.WARNING);
            return;
        }
        if (data.parentsOf(tag).size() >= Math.round(StarkConfig.Relays.MAX_TAGS)) {
            tell(player, Component.translatable("edith.starktech.msg.tag_full", tag, Math.round(StarkConfig.Relays.MAX_TAGS)),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        player.setData(ModAttachments.RELAYS, data.withParent(tag, parent));
    }

    /** Gooit een tag weg (van alle relais en uit alle tags). De relais zelf blijven. */
    private static void deleteTag(ServerPlayer player, EdithRelays data, String tag) {
        if (!data.hasTag(tag)) {
            return;
        }
        player.setData(ModAttachments.RELAYS, data.withoutTag(tag));
        tell(player, Component.translatable("edith.starktech.msg.tag_deleted", tag), EdithMessagePayload.Level.INFO);
    }

    /**
     * Schakelt één relais (als index >= 0) of alles wat bij de naam/groep past.
     * Elke schakeling kost een beetje energie.
     */
    private static void switchRelays(ServerPlayer player, ItemStack glasses, EdithRelays data, RelayActionPayload payload) {
        List<Relay> targets = payload.index() >= 0
                ? (data.get(payload.index()) == null ? List.<Relay>of() : List.of(data.get(payload.index())))
                : payload.index() == RelayActionPayload.TAG_ONLY ? data.inTag(payload.name()) : data.find(payload.name());
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
     * Niet in de basis van een ander.
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
        ServerLevel level = server.getLevel(relay.dimension());
        if (level != null && !protectedFrom(player, level, relay.pos())) {
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
     * Staat dit op een plek in de basis van iemand anders (het gebied van een beveiligingskern waar jij
     * geen eigenaar of vriend bent)? Dan mag je het niet omkeren of zonder bril schakelen. Creative mag alles.
     */
    private static boolean protectedFrom(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player.isCreative()) {
            return false;
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        if (core == null || core.ownerId() == null || SecurityCores.isFriend(level, core, player)) {
            return false;
        }
        tell(player, Component.translatable("edith.starktech.msg.relay_protected", core.ownerName()), EdithMessagePayload.Level.WARNING);
        return true;
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
