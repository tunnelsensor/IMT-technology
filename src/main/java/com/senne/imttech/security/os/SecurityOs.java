package com.senne.imttech.security.os;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.network.OsActionPayload;
import com.senne.imttech.network.OsLoginPromptPayload;
import com.senne.imttech.network.OsOpenPayload;
import com.senne.imttech.network.OsSnapshotPayload;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDroneEntity;
import com.senne.imttech.security.net.CoreNetworkData;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De Security OS-desktop op een beveiligingskern (jouw wens, 28 september): een echte desktop met
 * vensters waar de EIGENAAR zijn beveiliging bekijkt en bedient. Hij opent door met een lege hand
 * op de kern te rechtsklikken.
 *
 * Alles draait op de server; het scherm van de speler tekent alleen wat de server stuurt (een
 * momentopname in een CompoundTag, zie {@link #snapshot}). Zo staat het echte wachtwoord of de
 * mappenstructuur nooit in het spel van iemand anders, en kan de server elke opdracht controleren.
 *
 * Deze klasse houdt bij wie welke desktop open heeft (de sessies), ververst de momentopname terwijl
 * het scherm openstaat, en sluit hem als je wegloopt, doodgaat of uitlogt. Hacken (binnenkomen
 * zonder eigenaar te zijn) gaat NIET via de desktop maar via de laptop; de desktop is voor wie de
 * beveiliging al bezit (of straks het wachtwoord kent, via SSH).
 *
 * Sinds stap 5 ook de mappen en bestanden (de apps Files en Notes) en de VALSTRIKKEN: een bestand
 * dat de eigenaar als valstrik markeerde. Opent iemand anders dan de eigenaar het, dan gaat het
 * alarm af, wordt hij eruit gegooid en een tijdje buitengesloten, en jagen de drones op hem.
 *
 * Sinds hoofdstuk 40 (30 oktober) kun je ook van ver inloggen, met de Hacking Laptop: IP + wachtwoord, zoals SSH (zie
 * {@link #login}). Zo'n sessie "op afstand" houdt het stukje wereld met de kern geladen, en sluit als je de laptop niet
 * meer bij je hebt of de kern geen stroom meer heeft.
 */
public final class SecurityOs {
    private SecurityOs() {}

    /** Zo dicht bij de kern moet je blijven om zijn desktop open te houden (blokken). */
    public static final double REACH = 8.0;
    /** Zo vaak (ticks) stuurt de server een nieuwe momentopname naar wie ingelogd is. */
    private static final int SNAPSHOT_INTERVAL = 10;

    /**
     * Eén open desktop: op welke kern, of het via SSH op afstand is, en wat er nog naar het scherm
     * moet (een geopend bestand, een melding). Ook welke versie van de mappen het scherm al heeft,
     * zodat de server de lijst alleen opnieuw stuurt als er iets veranderde.
     */
    public static final class Session {
        private final BlockPos core;
        private final boolean remote;
        /** Het bestand dat net geopend is en nog (één keer) naar het scherm moet. */
        private @Nullable String fileToSend;
        /** Een melding die nog naar het scherm moet (het actiebalk-bericht zie je niet achter de desktop). */
        private @Nullable String notice;
        private boolean noticeError;
        /** Welke mappen (en welke versie ervan) het scherm al kreeg. */
        private @Nullable VirtualFs sentFs;
        private int sentRevision = -1;
        /** Welke diagrammen (en welke versie ervan) het scherm al kreeg. */
        private @Nullable com.senne.imttech.logic.LogicStore sentLogic;
        private int sentLogicRevision = -1;
        /** Kreeg het scherm al welke vensters er de vorige keer open stonden (19 oktober)? Dat gaat er maar één keer in. */
        private boolean sentDesktop;
        /** Hoofdstuk 130: welke stand van de SD-kaart het scherm al kreeg (zie SdDesktop.key); null = nog niets. */
        private @Nullable String sentSdKey;
        /** Hoofdstuk 130: de code van een open programma op de SD-kaart, om te bekijken (één keer), of null. */
        private @Nullable CompoundTag sdCodeToSend;

        /** Wie deze desktop open heeft (de app Zone toont waar hij staat). */
        private final UUID viewer;
        /** In welke dimensie de kern staat (hoofdstuk 40: op afstand moet je in dezelfde blijven). */
        private final ResourceKey<Level> dimension;
        /** Hoofdstuk 40: de Computer waarop deze desktop draait, of null (de kern zelf, of de laptop). */
        private final @Nullable BlockPos computer;
        /** Waar je bij moet blijven (binnen REACH): de kern, of de monitor waarop je klikte. */
        private final BlockPos anchor;
        /** Hoofdstuk 50: de I.M.T Phone waarmee deze desktop open is (zijn nummer), of null. */
        private final @Nullable UUID phone;

        Session(BlockPos core, boolean remote, UUID viewer, ResourceKey<Level> dimension, @Nullable BlockPos computer,
                BlockPos anchor) {
            this(core, remote, viewer, dimension, computer, anchor, null);
        }

        Session(BlockPos core, boolean remote, UUID viewer, ResourceKey<Level> dimension, @Nullable BlockPos computer,
                BlockPos anchor, @Nullable UUID phone) {
            this.core = core;
            this.remote = remote;
            this.viewer = viewer;
            this.dimension = dimension;
            this.computer = computer;
            this.anchor = anchor;
            this.phone = phone;
        }

        public @Nullable BlockPos computer() {
            return computer;
        }

        /**
         * Wat je op deze desktop mag: alles op de kern of met de laptop; op een Computer wat de kern daar toestaat (elke
         * keer opnieuw uit de computer gelezen, zodat een wijziging in de app Computers meteen geldt). Is de computer
         * weg, dan niets.
         */
        public DesktopRights rights(ServerLevel level) {
            if (phone != null) {
                return DesktopRights.PHONE;
            }
            if (computer == null) {
                return DesktopRights.FULL;
            }
            return level.getBlockEntity(computer) instanceof com.senne.imttech.computer.ComputerBlockEntity entity
                    ? entity.rights() : DesktopRights.NONE;
        }

        public UUID viewer() {
            return viewer;
        }

        /** Hoofdstuk 50: de telefoon waarmee deze desktop open is, of null. */
        public @Nullable UUID phone() {
            return phone;
        }

        public BlockPos core() {
            return core;
        }

        public boolean remote() {
            return remote;
        }

        /** In welke dimensie de kern staat. */
        public ResourceKey<Level> dimension() {
            return dimension;
        }
    }

    /** Alle desktops die nu open zijn (hoofdstuk 94: hun netwerk blijft geladen, zie lan/NetworkKeeper). */
    public static java.util.List<Session> sessions() {
        return java.util.List.copyOf(SESSIONS.values());
    }

    /** Wie op welk systeem buitengesloten is (na een valstrik), en tot wanneer (speltijd). */
    private record Lockout(BlockPos core, long until) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Lockout> LOCKOUTS = new HashMap<>();

    // ================================================================ Openen en sluiten

    /**
     * Rechtsklik met lege hand op de kern. Alleen de eigenaar komt op de desktop; een vertrouwde
     * speler niet, en wie de beveiliging niet bezit moet eerst hacken (met de laptop).
     */
    public static void open(ServerPlayer player, BlockPos corePos) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(corePos) instanceof SecurityCoreBlockEntity core)
                || player.distanceToSqr(corePos.getCenter()) > REACH * REACH) {
            return;
        }
        core.ensureSystem(level);
        // Hoofdstuk 136: de map voor geluiden (playsound zoekt daar), ook op een kern van vóór vandaag.
        if (core.fs().get(VirtualFs.SOUNDS) == null && core.fs().count() < VirtualFs.MAX_NODES) {
            core.fs().mkdirs(VirtualFs.SOUNDS);
            core.setChanged();
        }
        if (core.ownerId() == null) {
            player.displayClientMessage(Component.translatable("os.imttech.msg.no_owner")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!isOwner(player, core)) {
            player.displayClientMessage(Component.translatable("os.imttech.msg.access_denied")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        // De namen van je apparaten (je redstone-lijst) meteen overnemen, voor de app Logic.
        com.senne.imttech.logic.LogicDevices.refresh(level, core, player);
        if (openFor(player, core, false)) {
            level.playSound(null, corePos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4f, 1.8f);
        }
    }

    /**
     * Opent de desktop echt (gedeeld door de lokale weg hierboven en straks de SSH-weg op afstand).
     * Geeft false als deze speler hier nog buitengesloten is (na een valstrik).
     */
    public static boolean openFor(ServerPlayer player, SecurityCoreBlockEntity core, boolean remote) {
        return openFor(player, core, remote, null, core.getBlockPos());
    }

    /**
     * Hoofdstuk 40: de desktop op een Computer (rechtsklik op zijn monitor, zie computer/Computers.use). Je ziet alleen de
     * apps die de kern daar toestaat, en je moet bij de monitor blijven.
     */
    public static boolean openComputer(ServerPlayer player, SecurityCoreBlockEntity core, BlockPos computer, BlockPos monitor) {
        return openFor(player, core, false, computer, monitor);
    }

    /** Hoofdstuk 50: de desktop op een I.M.T Phone (op afstand, met alleen de apps van de telefoon). */
    static boolean openPhone(ServerPlayer player, SecurityCoreBlockEntity core, UUID phone) {
        return openFor(player, core, true, null, core.getBlockPos(), phone);
    }

    private static boolean openFor(ServerPlayer player, SecurityCoreBlockEntity core, boolean remote, @Nullable BlockPos computer,
                                   BlockPos anchor) {
        return openFor(player, core, remote, computer, anchor, null);
    }

    private static boolean openFor(ServerPlayer player, SecurityCoreBlockEntity core, boolean remote, @Nullable BlockPos computer,
                                   BlockPos anchor, @Nullable UUID phone) {
        // Tijdens een oefening (hoofdstuk 27) is de trainee een indringer: zijn eigen desktop gaat niet open (ook niet op afstand).
        if (com.senne.imttech.training.Training.isDrillTrainee(player.serverLevel(), core.getBlockPos(), player.getUUID())) {
            player.displayClientMessage(Component.literal("The desktop is locked: you are the intruder in a drill")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        int locked = lockedSeconds(player, core);
        if (locked > 0) {
            player.displayClientMessage(Component.translatable("os.imttech.msg.locked_out", locked)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        Session session = new Session(core.getBlockPos().immutable(), remote, player.getUUID(), player.serverLevel().dimension(),
                computer == null ? null : computer.immutable(), anchor.immutable(), phone);
        SESSIONS.put(player.getUUID(), session);
        if (remote) {
            keepLoaded(player.serverLevel(), session.core());
        }
        ModNetwork.sendTo(player, new OsOpenPayload(core.getBlockPos(), true, remote));
        sendSnapshot(player, core, session);
        return true;
    }

    /** Sluit de desktop van deze speler (het scherm aan zijn kant gaat ook dicht). */
    public static void close(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            ModNetwork.sendTo(player, OsOpenPayload.close(session.core()));
        }
    }

    /** Hoofdstuk 40: deze Computer is weg (afgebroken): wie hem open had, krijgt hem dicht. */
    public static void closeComputer(ServerLevel level, BlockPos computer) {
        for (Map.Entry<UUID, Session> entry : java.util.List.copyOf(SESSIONS.entrySet())) {
            if (computer.equals(entry.getValue().computer())) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    close(player);
                } else {
                    SESSIONS.remove(entry.getKey());
                }
            }
        }
    }

    /** Heeft deze speler nu een desktop open? (Voor de tests.) */
    public static boolean hasDesktop(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** Is deze speler de eigenaar van de kern? (Alleen die ziet en zet valstrikken.) */
    private static boolean isOwner(ServerPlayer player, SecurityCoreBlockEntity core) {
        return player.getUUID().equals(core.ownerId());
    }

    /** Hoeveel seconden deze speler nog buitengesloten is op deze kern (0 = niet). */
    public static int lockedSeconds(ServerPlayer player, SecurityCoreBlockEntity core) {
        Lockout lockout = LOCKOUTS.get(player.getUUID());
        if (lockout == null || !lockout.core().equals(core.getBlockPos())) {
            return 0;
        }
        long left = lockout.until() - player.serverLevel().getGameTime();
        if (left <= 0) {
            LOCKOUTS.remove(player.getUUID());
            return 0;
        }
        return (int) Math.ceil(left / 20.0);
    }

    // ================================================================ Inloggen met de laptop (hoofdstuk 40)

    /**
     * Het "ticket" dat het stukje wereld met de kern geladen houdt tijdens een sessie op afstand (anders kon de server
     * de kern niet lezen als je ver weg bent). Het verloopt vanzelf na 5 seconden; zolang de sessie loopt, vernieuwen we
     * het elke seconde.
     */
    private static final TicketType<ChunkPos> REMOTE_TICKET =
            TicketType.create("imttech_remote_desktop", Comparator.comparingLong(ChunkPos::toLong), 100);

    /** Zo lang (ticks) krijgt de eigenaar maar één alarm over foute wachtwoorden van dezelfde speler (een minuut). */
    private static final int FAIL_ALERT_EVERY = 1200;

    /** Hoeveel foute wachtwoorden na elkaar deze speler al typte (tot [security] passwordAttempts). */
    private static final Map<UUID, Integer> WRONG = new HashMap<>();
    /** Na te veel foute wachtwoorden: tot wanneer (speltijd) deze speler moet wachten. */
    private static final Map<UUID, Long> WAIT_UNTIL = new HashMap<>();
    /** Wanneer de eigenaar het laatste alarm kreeg over foute wachtwoorden van deze speler. */
    private static final Map<UUID, Long> LAST_FAIL_ALERT = new HashMap<>();

    /** Het stukje wereld met de kern geladen houden (een sessie op afstand), nog 5 seconden. */
    private static void keepLoaded(ServerLevel level, BlockPos core) {
        ChunkPos chunk = new ChunkPos(core);
        level.getChunkSource().addRegionTicket(REMOTE_TICKET, chunk, 2, chunk);
    }

    /** De Hacking Laptop in de lucht gebruikt: het inlogscherm openen, met het IP van de vorige keer. */
    public static void promptLogin(ServerPlayer player, ItemStack laptop) {
        String lastIp = laptop.getOrDefault(com.senne.imttech.registry.ModDataComponents.LAST_IP.get(), "");
        ModNetwork.sendTo(player, new OsLoginPromptPayload(lastIp, "", true));
    }

    /** Heeft deze speler een Hacking Laptop bij zich (ergens in zijn inventaris)? */
    private static boolean hasLaptop(ServerPlayer player) {
        return player.getInventory().contains(stack -> stack.is(ModItems.HACKING_LAPTOP.get()));
    }

    /**
     * Inloggen met een IP en een wachtwoord (het inlogscherm van de laptop). Zo gaat het:
     * <ul>
     *   <li>we zoeken de kern met dat IP in het netwerk van de wereld (CoreNetworkData) en laden zo nodig het stukje
     *       wereld waar hij staat. Hij moet in dezelfde dimensie staan als jij;</li>
     *   <li>een kern zonder stroom antwoordt niet; een kern zonder wachtwoord laat niemand van ver binnen (ook de
     *       eigenaar niet: die zet eerst een wachtwoord in Settings);</li>
     *   <li>een fout wachtwoord: een regel in het logboek, de eigenaar krijgt een alarm (hooguit één per minuut per
     *       speler), en na [security] passwordAttempts keer moet je even wachten;</li>
     *   <li>het goede wachtwoord: de desktop opent op afstand ("REMOTE SESSION"), de laptop onthoudt het IP, en ben je
     *       niet de eigenaar, dan weet de eigenaar het meteen.</li>
     * </ul>
     */
    public static void login(ServerPlayer player, String ip, String password) {
        ServerLevel here = player.serverLevel();
        if (!hasLaptop(player)) {
            reply(player, "You need a Hacking Laptop to log in from a distance.");
            return;
        }
        SecurityCoreBlockEntity core = connect(player, ip, password, message -> reply(player, message));
        if (core == null) {
            return;
        }
        String name = player.getGameProfile().getName();
        boolean owner = isOwner(player, core);
        if (!openFor(player, core, true)) {
            reply(player, "Connection refused.");
            return;
        }
        core.logEvent(EventLog.Type.LOGIN, name + " logged in remotely");
        if (!owner) {
            alertOwner(here, core, player, "os.imttech.msg.login_alert");
        }
        ItemStack laptop = player.getMainHandItem().is(ModItems.HACKING_LAPTOP.get()) ? player.getMainHandItem()
                : player.getOffhandItem().is(ModItems.HACKING_LAPTOP.get()) ? player.getOffhandItem() : ItemStack.EMPTY;
        for (int i = 0; laptop.isEmpty() && i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.HACKING_LAPTOP.get())) {
                laptop = player.getInventory().getItem(i);
            }
        }
        if (!laptop.isEmpty()) {
            laptop.set(com.senne.imttech.registry.ModDataComponents.LAST_IP.get(), core.ip());
        }
        com.senne.imttech.logic.LogicDevices.refresh(here, core, player);
    }

    /**
     * Het IP en het wachtwoord nakijken, voor de laptop, (hoofdstuk 50) de telefoon en (hoofdstuk 122) de I.M.T-bril die
     * je met een datacenter verbindt (logic/GlassesUplink): de kern met dat IP opzoeken (in dezelfde dimensie, met stroom
     * en een wachtwoord), de wachttijd na te veel foute wachtwoorden, en het wachtwoord zelf (fout: tellen, de eigenaar
     * waarschuwen, en na [security] passwordAttempts even wachten). Geeft de kern, of null nadat {@code reply} zei waarom
     * niet.
     */
    public static @Nullable SecurityCoreBlockEntity connect(ServerPlayer player, String ip, String password, java.util.function.Consumer<String> reply) {
        ServerLevel here = player.serverLevel();
        String address = ip.strip();
        long now = here.getGameTime();
        Long waitUntil = WAIT_UNTIL.get(player.getUUID());
        if (waitUntil != null && now < waitUntil) {
            reply.accept("Too many wrong passwords: wait " + (int) Math.ceil((waitUntil - now) / 20.0) + " s.");
            return null;
        }
        GlobalPos where = CoreNetworkData.get(here).find(address);
        if (where == null) {
            reply.accept("ssh: no system answers at " + address + ".");
            return null;
        }
        if (!where.dimension().equals(here.dimension())) {
            reply.accept("ssh: no route to " + address + ". That system is in another dimension.");
            return null;
        }
        BlockPos pos = where.pos();
        here.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (!(here.getBlockEntity(pos) instanceof SecurityCoreBlockEntity core)) {
            reply.accept("ssh: no system answers at " + address + ".");
            return null;
        }
        boolean owner = isOwner(player, core);
        if (!core.isOnline()) {
            reply.accept("ssh: " + address + " does not answer. That system has no power.");
            return null;
        }
        if (!core.hasPassword()) {
            reply.accept(owner ? "Set a password in Settings on your core first: without one, nobody can log in from far away."
                    : "This system takes no logins: it has no password. Take it over on site with a Hacking Laptop.");
            return null;
        }
        int locked = lockedSeconds(player, core);
        if (locked > 0) {
            reply.accept("ACCESS DENIED: you are locked out of this system for " + locked + " s.");
            return null;
        }
        String name = player.getGameProfile().getName();
        if (!core.checkPassword(password)) {
            int wrong = WRONG.merge(player.getUUID(), 1, Integer::sum);
            core.logEvent(EventLog.Type.LOGIN_FAIL, "Wrong password from " + name + " (remote)");
            Long lastAlert = LAST_FAIL_ALERT.get(player.getUUID());
            if (!owner && (lastAlert == null || now - lastAlert > FAIL_ALERT_EVERY)) {
                LAST_FAIL_ALERT.put(player.getUUID(), now);
                alertOwner(here, core, player, "os.imttech.msg.login_fail_alert");
            }
            if (wrong >= Math.max(1, Math.round(ImtConfig.Security.PASSWORD_ATTEMPTS))) {
                WRONG.remove(player.getUUID());
                long ticks = Math.max(20, Math.round(ImtConfig.Security.PASSWORD_LOCKOUT_MILLIS / 50.0));
                WAIT_UNTIL.put(player.getUUID(), now + ticks);
                reply.accept("Permission denied. Too many wrong passwords: wait " + (int) Math.ceil(ticks / 20.0) + " s.");
            } else {
                reply.accept("Permission denied, please try again.");
            }
            return null;
        }
        WRONG.remove(player.getUUID());
        WAIT_UNTIL.remove(player.getUUID());
        return core;
    }

    /** Een antwoord voor het inlogscherm (waarom het niet lukte). */
    private static void reply(ServerPlayer player, String message) {
        ModNetwork.sendTo(player, OsLoginPromptPayload.reply(message));
    }

    /** Een alarm van I.M.T voor de eigenaar (als hij online is, waar hij ook is). */
    private static void alertOwner(ServerLevel level, SecurityCoreBlockEntity core, ServerPlayer who, String key) {
        core.addMessage(who.getGameProfile().getName() + (key.endsWith("login_alert") ? " logged in remotely"
                : " typed a wrong password (remote)"), true);
        ServerPlayer owner = core.ownerId() == null ? null : level.getServer().getPlayerList().getPlayer(core.ownerId());
        if (owner != null && owner != who) {
            ImtServer.send(owner, Component.translatable(key, who.getGameProfile().getName(), core.hostname()),
                    ImtMessagePayload.Level.ALERT);
        }
    }

    // ================================================================ Opdrachten van het scherm

    public static void handleAction(ServerPlayer player, OsActionPayload payload) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.core().equals(payload.pos())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(session.core()) instanceof SecurityCoreBlockEntity core)) {
            close(player);
            return;
        }
        CompoundTag args = payload.args();
        // Hoofdstuk 40: op een Computer mag alleen wat de kern daar toestaat (een app op View: alleen kijken).
        DesktopRights rights = session.rights(level);
        if (!rights.allows(payload.op(), args)) {
            String app = DesktopRights.appOf(payload.op(), args);
            notify(player, core, session, rights.shows(app) ? "View only on this computer"
                    : "This computer does not allow that", true);
            return;
        }
        // Hoofdstuk 130: Files en Notes op een SD-kaart (een pad dat met sd: begint), en kopiëren tussen je systeem en de kaart.
        String op = payload.op();
        if (op.equals("fs_copy") || op.startsWith("fs_") && !op.equals("fs_open")
                && com.senne.imttech.sdcard.SdDesktop.touchesCard(args)) {
            var view = com.senne.imttech.sdcard.SdDesktop.view(level, sdHolder(level, core, session));
            sdResult(player, core, session, op.equals("fs_copy")
                    ? com.senne.imttech.sdcard.SdDesktop.copy(player, core, view, args.getString("from"), args.getString("to"),
                    isOwner(player, core))
                    : com.senne.imttech.sdcard.SdDesktop.fileOp(player, core, view, op, args, isOwner(player, core)));
            return;
        }
        switch (op) {
            case "close" -> close(player);
            // De desktop ging dicht: onthoud welke vensters er open stonden (19 oktober). Uitzetten met de aan/uit-knop
            // vergeet ze: de volgende keer start hij met alleen Status.
            case "desk_save" -> {
                // Op een Computer onthoudt de computer zijn eigen vensters (hoofdstuk 40), los van de kern.
                if (session.computer() != null && level.getBlockEntity(session.computer())
                        instanceof com.senne.imttech.computer.ComputerBlockEntity computer) {
                    computer.setDesktop(desktopWindows(args.getList("w", Tag.TAG_COMPOUND)));
                } else if (session.computer() == null) {
                    core.setDesktop(desktopWindows(args.getList("w", Tag.TAG_COMPOUND)));
                }
            }
            case "desk_off" -> {
                if (session.computer() != null) {
                    if (level.getBlockEntity(session.computer()) instanceof com.senne.imttech.computer.ComputerBlockEntity computer) {
                        computer.setDesktop(new ListTag());
                    }
                } else {
                    core.setDesktop(new ListTag());
                    core.logEvent(EventLog.Type.LOGIN, player.getGameProfile().getName() + " shut down the desktop");
                }
                close(player);
            }
            case "passwd" -> passwd(player, core, session, args.getString("pw"));
            case "app_hide" -> hideApp(player, core, session, args.getString("app"), args.getBoolean("on"));
            case "auth_set" -> setAuth(player, core, session, args.getInt("len"), args.getInt("min"), args.getBoolean("old"));
            case "req_answer" -> answerRequest(player, core, session, args.getInt("id"), args.getBoolean("ok"));
            case "phone_block" -> blockPhone(player, core, session, args.getString("id"), args.getBoolean("on"));
            case "msg_send" -> Phones.sendMessage(player, args.getString("to"), args.getString("text"));
            case "fs_open" -> {
                if (com.senne.imttech.sdcard.SdDesktop.onCard(args.getString("path"))) {
                    openCardFile(player, core, session, args.getString("path")); // hoofdstuk 130: een bestand op de SD-kaart
                } else {
                    openFile(player, core, session, path(args, "path"));
                }
            }
            // Hoofdstuk 136: een geluid beluisteren (dubbelklik in Files). Een eigen opdracht, zodat het onder Files valt en
            // niet onder Notes (op een Computer met Files maar zonder Notes kon je eerst niets horen).
            case "fs_play" -> {
                VirtualFs.Node node = core.fs().get(path(args, "path"));
                if (node == null || !node.isSound()) {
                    notify(player, core, session, VirtualFs.nameOf(path(args, "path")) + ": no such sound", true);
                } else if (node.isTrap() && !isOwner(player, core)) {
                    springTrap(player, core, path(args, "path"));
                } else {
                    com.senne.imttech.alarm.AlarmSpeakers.preview(player, node);
                }
            }
            case "fs_mkdir" -> fileResult(player, core, session, core.fs().mkdir(path(args, "path")));
            case "fs_touch" -> fileResult(player, core, session, touch(core.fs(), path(args, "path")));
            case "fs_save" -> saveFile(player, core, session, path(args, "path"), args.getString("text"));
            case "fs_move" -> fileResult(player, core, session, core.fs().move(path(args, "from"), path(args, "to")));
            case "fs_delete" -> fileResult(player, core, session, core.fs().delete(path(args, "path")));
            case "fs_trap" -> setTrap(player, core, session, path(args, "path"), args.getBoolean("on"));
            case "flag" -> setFlag(player, core, session, args.getString("name"), args.getBoolean("on"));
            case "dev_toggle" -> toggleTurret(player, core, session,
                    new BlockPos(args.getInt("x"), args.getInt("y"), args.getInt("z")), args.getBoolean("on"));
            case "sw_port" -> switchPort(player, core, session,
                    new BlockPos(args.getInt("x"), args.getInt("y"), args.getInt("z")), args.getInt("side"), args.getBoolean("on"));
            case "cam_aim" -> aimCamera(player, core, session,
                    new BlockPos(args.getInt("x"), args.getInt("y"), args.getInt("z")), args.getFloat("pan"), args.getFloat("tilt"));
            case "cam_rename" -> renameCamera(player, core, session,
                    new BlockPos(args.getInt("x"), args.getInt("y"), args.getInt("z")), args.getString("name"));
            case "dev_rename" -> renameDevice(player, core, session,
                    new BlockPos(args.getInt("x"), args.getInt("y"), args.getInt("z")), args.getString("name"));
            case "trust_add" -> trust(player, core, session, args.getString("name").strip());
            case "trust_remove" -> untrust(player, core, session, args.getString("name").strip());
            case "card_write" -> writeCard(player, core, session, args.getString("label"), args.getInt("colour"), args.getString("group"));
            case "card_block" -> blockCard(player, core, session, args.getInt("n"), args.getBoolean("on"));
            case "card_delete" -> deleteCard(player, core, session, args.getInt("n"));
            case "card_delete_blocked" -> deleteBlockedCards(player, core, session);
            // Keycard-groepen (25 oktober): in de app Keycards, links.
            case "cgroup_new" -> newCardGroup(player, core, session, args.getString("name"), args.getString("parent"));
            case "cgroup_rename" -> renameCardGroup(player, core, session, args.getString("group"), args.getString("name"));
            case "cgroup_delete" -> deleteCardGroup(player, core, session, args.getString("group"));
            case "cgroup_move" -> moveCardGroup(player, core, session, args.getString("group"), args.getString("parent"));
            case "card_group_add" -> cardInGroup(player, core, session, args.getInt("n"), args.getString("group"), true);
            case "card_group_remove" -> cardInGroup(player, core, session, args.getInt("n"), args.getString("group"), false);
            case "logic_save" -> saveProgram(player, core, session, args.getString("old"), args.getString("name"),
                    args.getCompound("graph"), args.getString("source"), args.getString("kind"));
            case "logic_delete" -> deleteProgram(player, core, session, args.getString("name"));
            case "logic_enable" -> enableProgram(player, core, session, args.getString("name"), args.getBoolean("on"));
            // Hoofdstuk 130: de programma's op de SD-kaart (in de app Logic).
            case "sd_copy" -> sdResult(player, core, session, com.senne.imttech.sdcard.SdDesktop.copyToCard(player, core,
                    com.senne.imttech.sdcard.SdDesktop.view(level, sdHolder(level, core, session)), args.getString("name"),
                    args.getBoolean("locked")));
            case "sd_install" -> sdResult(player, core, session, com.senne.imttech.sdcard.SdDesktop.install(player, core,
                    com.senne.imttech.sdcard.SdDesktop.view(level, sdHolder(level, core, session)), args.getString("name")));
            case "sd_delete" -> sdResult(player, core, session, com.senne.imttech.sdcard.SdDesktop.deleteFromCard(player, core,
                    com.senne.imttech.sdcard.SdDesktop.view(level, sdHolder(level, core, session)), args.getString("name")));
            case "sd_view" -> viewCardProgram(player, core, session, args.getString("name"));
            case "input_rename" -> renameInput(player, core, session,
                    new BlockPos(args.getInt("x"), args.getInt("y"), args.getInt("z")), args.getString("name"));
            case "zone_new", "zone_scan", "zone_undo", "zone_delete", "zone_grow", "zone_set" -> {
                // De app Zone (hoofdstuk 10), zie ZoneApp.
                com.senne.imttech.security.zone.ZoneApp.Result result = com.senne.imttech.security.zone.ZoneApp.handle(
                        player, core, payload.op(), args, session.remote());
                if (!result.text().isEmpty()) {
                    notify(player, core, session, result.text(), result.error());
                } else {
                    sendSnapshot(player, core, session);
                }
            }
            case "cam_watch" -> com.senne.imttech.robot.Robots.watched(player.serverLevel(), core, args.getInt("robot"));
            // De app Training (hoofdstuk 26): de training stoppen.
            case "training_stop" -> notify(player, core, session, com.senne.imttech.training.Trainings.stop(player, core), false);
            case "robot_car_program" -> {
                // Hoofdstuk 119: een RC program van deze server op een RC Car laden (de app Robots), zie car/RcCars.
                com.senne.imttech.robot.Robots.Desktop result = com.senne.imttech.car.RcCars.desktop(player, core, payload.op(), args);
                notify(player, core, session, result.text(), result.error());
            }
            case "robot_switch", "robot_rename", "robot_program", "robot_squad", "robot_delete" -> {
                // De app Robots (hoofdstuk 21; een programma sturen: hoofdstuk 23; weghalen: 18 oktober), zie robot/Robots.
                com.senne.imttech.robot.Robots.Desktop result = com.senne.imttech.robot.Robots.desktop(
                        player, core, payload.op(), args);
                notify(player, core, session, result.text(), result.error());
            }
            case "pc_rename", "pc_who", "pc_code", "pc_app" -> {
                // De app Computers (hoofdstuk 40), zie computer/Computers.
                com.senne.imttech.computer.Computers.Result result = com.senne.imttech.computer.Computers.desktop(
                        player, core, payload.op(), args);
                notify(player, core, session, result.text(), result.error());
            }
            case "backup_target", "backup_auto", "backup_now", "backup_restore", "backup_delete" -> {
                // De app Backup (8 oktober), zie Backups.
                com.senne.imttech.logic.Backups.Result result = com.senne.imttech.logic.Backups.handle(player, core, payload.op(), args);
                notify(player, core, session, result.text(), result.error());
            }
            default -> { } // onbekende opdracht: later uitgebreid (camera's)
        }
    }

    /**
     * De vensters die het scherm stuurt, netjes gemaakt: per venster de naam van de app (hoofdletters en _, zoals
     * "DEVICES") en waar het stond; hooguit 20, en elke app één keer. Wat er niet uitziet als een venster, laten we weg.
     */
    private static ListTag desktopWindows(ListTag sent) {
        ListTag windows = new ListTag();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Tag element : sent) {
            if (!(element instanceof CompoundTag window) || windows.size() >= 20) {
                continue;
            }
            String app = window.getString("a");
            if (app.isEmpty() || app.length() > 16 || !app.matches("[A-Z_]+") || !seen.add(app)) {
                continue;
            }
            CompoundTag clean = new CompoundTag();
            clean.putString("a", app);
            clean.putInt("x", Math.max(-1000, Math.min(4000, window.getInt("x"))));
            clean.putInt("y", Math.max(-1000, Math.min(4000, window.getInt("y"))));
            windows.add(clean);
        }
        return windows;
    }

    // ================================================================ Apparaten en toegang

    /** Zoveel spelers kan een eigenaar hooguit vertrouwen. */
    public static final int MAX_TRUSTED = 32;

    /**
     * Een schakelaar van het hele netwerk (de apps Access en Devices): monsters aanvallen, andere
     * spelers aanvallen, de drones, de laserwanden.
     */
    private static void setFlag(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String name, boolean on) {
        String text;
        switch (name) {
            case "mobs" -> {
                core.setAttackMobs(on);
                text = on ? "Security attacks monsters" : "Security ignores monsters";
            }
            case "players" -> {
                core.setAttackPlayers(on);
                text = on ? "Security attacks other players" : "Security ignores other players";
            }
            case "drones" -> {
                // De oude drones landen (of vliegen weer); je security drones (hoofdstuk 25) gaan aan of naar hun dock.
                core.setDronesEnabled(on);
                int drones = com.senne.imttech.security.SecurityDevices.switchDrones(player.serverLevel(), core, on);
                text = (on ? "Drones on" : "Drones landing")
                        + (drones == 0 ? "" : " (" + drones + " security drone" + (drones == 1 ? ")" : "s)"));
            }
            case "lasers" -> {
                com.senne.imttech.security.SecurityDevices.setLasers(player.serverLevel(), core, on);
                text = on ? "Laser walls on" : "Laser walls off";
            }
            case "cameras" -> {
                com.senne.imttech.security.SecurityDevices.setCameras(player.serverLevel(), core, on);
                text = on ? "Cameras on" : "Cameras off";
            }
            default -> {
                return;
            }
        }
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + ": " + text.toLowerCase(java.util.Locale.ROOT));
        notify(player, core, session, text, false);
    }

    /**
     * Hoofdstuk 54 (jouw notitie: "op de security core apps kunnen installeren en verwijderen dat je daar enkel ziet staan
     * wat je nodig hebt"): een app van het bureaublad halen of terugzetten. Alleen de eigenaar, en Settings zelf nooit
     * (anders kom je er nooit meer bij).
     */
    private static void hideApp(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String app, boolean hidden) {
        if (!isOwner(player, core)) {
            notify(player, core, session, "Only the owner can install or remove apps", true);
            return;
        }
        if (app.isEmpty() || app.equals("SETTINGS")) {
            return;
        }
        core.setAppHidden(app, hidden);
        String name = app.charAt(0) + app.substring(1).toLowerCase(java.util.Locale.ROOT);
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + ": " + (hidden ? "removed " : "installed ") + name);
        notify(player, core, session, (hidden ? "Removed " : "Installed ") + name, false);
    }

    /** Een camera een stukje draaien of kantelen (de pijltjes bij het grote camerabeeld). */
    private static void aimCamera(ServerPlayer player, SecurityCoreBlockEntity core, Session session, BlockPos pos,
                                  float dPan, float dTilt) {
        String error = com.senne.imttech.security.SecurityDevices.aimCamera(player.serverLevel(), core, pos, dPan, dTilt);
        if (error != null) {
            notify(player, core, session, error, true);
            return;
        }
        sendSnapshot(player, core, session);
    }

    /** Een camera een andere naam geven (de knop Rename bij het grote camerabeeld). Leeg = weer "Camera 3". */
    private static void renameCamera(ServerPlayer player, SecurityCoreBlockEntity core, Session session, BlockPos pos, String name) {
        String clean = com.senne.imttech.security.CameraBlockEntity.cleanName(name);
        String error = com.senne.imttech.security.SecurityDevices.renameCamera(player.serverLevel(), core, pos, clean);
        if (error != null) {
            notify(player, core, session, error, true);
            return;
        }
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " renamed the camera at "
                + pos.toShortString() + (clean.isEmpty() ? " back to its number" : " to \"" + clean + "\""));
        // Hoofdstuk 39: staat de camera in jouw I.M.T-lijst, dan krijgt hij daar dezelfde naam (één naam overal).
        if (!clean.isEmpty() && !com.senne.imttech.redstone.DeviceNames.nameOf(player, java.util.List.of(pos)).isEmpty()) {
            com.senne.imttech.redstone.DeviceNames.rename(player, java.util.List.of(pos), pos, clean,
                    com.senne.imttech.security.CameraBlock.DEFAULT_NAME);
            com.senne.imttech.logic.LogicDevices.refresh(player.serverLevel(), core, player);
        }
        notify(player, core, session, clean.isEmpty() ? "Camera name cleared" : "Camera renamed to " + clean, false);
    }

    /**
     * Rename in Devices voor een torentje, laserpoort, laserbrug of camera (hoofdstuk 39): de naam komt in de I.M.T-lijst van
     * wie aan de desktop zit (zie SecurityDevices.renameDevice).
     */
    private static void renameDevice(ServerPlayer player, SecurityCoreBlockEntity core, Session session, BlockPos pos, String name) {
        String error = com.senne.imttech.security.SecurityDevices.renameDevice(player, core, pos, name);
        if (error != null) {
            notify(player, core, session, error, true);
            return;
        }
        String clean = com.senne.imttech.redstone.ImtRedstone.clean(name, com.senne.imttech.redstone.DeviceNames.MAX_NAME, "");
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " named the device at " + pos.toShortString()
                + " \"" + clean + "\"");
        notify(player, core, session, "Renamed to " + clean + " (in your I.M.T list)", false);
    }

    /** Eén torentje aan of uit. */
    private static void toggleTurret(ServerPlayer player, SecurityCoreBlockEntity core, Session session, BlockPos pos, boolean on) {
        // Een torentje, of (hoofdstuk 39) een laserpoort of laserbrug: elk met zijn eigen aan/uit-knop in Devices.
        net.minecraft.world.level.block.Block block = player.serverLevel().getBlockState(pos).getBlock();
        String what = block instanceof com.senne.imttech.turret.TurretBlock ? "turret"
                : block instanceof com.senne.imttech.shield.LaserBridgeBlock ? "laser bridge"
                : block instanceof com.senne.imttech.security.CameraBlock ? "camera"
                : block instanceof com.senne.imttech.alarm.AlarmSpeakerBlock ? "alarm speaker" // hoofdstuk 126
                : "laser gate";
        String error = com.senne.imttech.security.SecurityDevices.switchDevice(player.serverLevel(), core, pos, on);
        if (error != null) {
            notify(player, core, session, error, true);
            return;
        }
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " switched the " + what + " at "
                + pos.toShortString() + (on ? " on" : " off"));
        notify(player, core, session, Character.toUpperCase(what.charAt(0)) + what.substring(1) + (on ? " on" : " off"), false);
    }

    /** Een poort van een Network Switch aan of uit (de knoppen onder een switch in de app Devices, hoofdstuk 38). */
    private static void switchPort(ServerPlayer player, SecurityCoreBlockEntity core, Session session, BlockPos pos, int side,
                                   boolean on) {
        net.minecraft.core.Direction direction = net.minecraft.core.Direction.from3DDataValue(side);
        String error = com.senne.imttech.security.SecurityDevices.switchPort(player.serverLevel(), core, pos, direction, on);
        if (error != null) {
            notify(player, core, session, error, true);
            return;
        }
        String name = player.serverLevel().getBlockEntity(pos) instanceof com.senne.imttech.lan.NetworkSwitchBlockEntity entity
                ? entity.name() : "a switch";
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " switched port " + direction.getName()
                + " of " + name + (on ? " on" : " off"));
        notify(player, core, session, name + ": port " + direction.getName() + (on ? " on" : " off"), false);
    }

    /**
     * Een speler vertrouwen (op naam): de beveiliging schiet niet meer op hem. Hij moet wel al eens
     * op deze server geweest zijn, anders weten we niet wie het is.
     */
    private static void trust(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String name) {
        if (name.isEmpty()) {
            return;
        }
        if (core.trustedNames().size() >= MAX_TRUSTED) {
            notify(player, core, session, "The trusted list is full (max " + MAX_TRUSTED + ")", true);
            return;
        }
        com.mojang.authlib.GameProfile profile = findProfile(player, name);
        if (profile == null) {
            notify(player, core, session, "No player called " + name + " has been on this server", true);
            return;
        }
        if (profile.getId().equals(core.ownerId())) {
            notify(player, core, session, profile.getName() + " owns this system", true);
            return;
        }
        if (core.isTrusted(profile.getId())) {
            notify(player, core, session, profile.getName() + " is already trusted", true);
            return;
        }
        core.addTrusted(profile.getId(), profile.getName());
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " trusted " + profile.getName());
        notify(player, core, session, profile.getName() + " is now trusted", false);
    }

    private static void untrust(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String name) {
        if (!core.removeTrusted(name)) {
            notify(player, core, session, name + " was not trusted", true);
            return;
        }
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " no longer trusts " + name);
        notify(player, core, session, name + " is no longer trusted", false);
    }

    // ================================================================ Keycards

    /**
     * Een keycard schrijven (de app Keycards): de eerste lege keycard in je rugzak krijgt een naam,
     * een kleur en het volgende nummer van dit netwerk, en de kern zet hem in groep {@code group}
     * ("" = in geen groep). Je krijgt hem terug als aparte kaart.
     */
    private static void writeCard(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String label, int colour,
                                  String group) {
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        int slot = -1;
        if (com.senne.imttech.logic.KeycardItem.isBlank(inventory.getSelected())) {
            slot = inventory.selected;
        }
        for (int i = 0; slot < 0 && i < inventory.getContainerSize(); i++) {
            if (com.senne.imttech.logic.KeycardItem.isBlank(inventory.getItem(i))) {
                slot = i;
            }
        }
        if (slot < 0) {
            notify(player, core, session, "No blank keycard in your inventory", true);
            return;
        }
        String clean = com.senne.imttech.logic.InputDeviceBlockEntity.cleanName(label);
        if (clean.isEmpty()) {
            clean = "Keycard";
        }
        String writer = player.getGameProfile().getName();
        com.senne.imttech.logic.LogicStore.Card card = core.logic().issue(clean, colour, writer, group);
        if (card == null) {
            notify(player, core, session, "This network cannot write more than "
                    + com.senne.imttech.logic.LogicStore.MAX_CARDS + " cards", true);
            return;
        }
        net.minecraft.world.item.ItemStack written = inventory.getItem(slot).split(1);
        com.senne.imttech.logic.KeycardItem.write(written, new com.senne.imttech.logic.KeycardData(
                core.ip(), core.hostname(), card.number(), card.colour(), card.label()));
        if (!inventory.add(written)) {
            player.drop(written, false);
        }
        core.setChanged();
        String where = card.groups().isEmpty() ? "no group" : "group " + card.groups().getFirst();
        core.logEvent(EventLog.Type.DEVICE, writer + " wrote keycard #" + card.number() + " \"" + card.label()
                + "\" (" + where + ")");
        player.serverLevel().playSound(null, core.getBlockPos(), com.senne.imttech.registry.ModSounds.IMT_CONFIRM.get(),
                SoundSource.BLOCKS, 0.5f, 1.2f);
        notify(player, core, session, "Card #" + card.number() + " written: " + card.label() + " (" + where + ")", false);
    }

    /** Een kaart blokkeren (verloren, gestolen) of weer toelaten. */
    private static void blockCard(ServerPlayer player, SecurityCoreBlockEntity core, Session session, int number, boolean blocked) {
        if (!core.logic().setBlocked(number, blocked)) {
            notify(player, core, session, "There is no card #" + number, true);
            return;
        }
        core.setChanged();
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + (blocked ? " blocked" : " unblocked")
                + " keycard #" + number);
        notify(player, core, session, "Card #" + number + (blocked ? " blocked" : " is valid again"), false);
    }

    // ================================================================ De diagrammen (app Logic)

    /**
     * Een diagram bewaren (de knop Save in de app Logic). De server controleert alles opnieuw: kloppen
     * de blokjes, draait de server, past het op de SSD's, is de naam vrij? Een apparaat dat (nog) niet
     * bestaat is geen fout (misschien bouw je het straks), maar je hoort het wel.
     */
    private static void saveProgram(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String oldName,
                                    String newName, CompoundTag graphTag, String source, String kindName) {
        ServerLevel level = player.serverLevel();
        com.senne.imttech.logic.LogicStore store = core.logic();
        String name = com.senne.imttech.logic.InputDeviceBlockEntity.cleanName(newName);
        if (name.isEmpty()) {
            notify(player, core, session, "Not saved: the diagram needs a name", true);
            return;
        }
        var graph = com.senne.imttech.logic.program.LogicGraph.load(graphTag);
        var kind = com.senne.imttech.logic.program.ProgramKind.byName(kindName);
        // Hoofdstuk 119 (gevonden door de nalezing): de auto krijgt de code van een RC program letterlijk. De server maakt
        // de blokjes daarom zelf uit die code (dan telt op de SSD wat de auto echt krijgt, en kan de code niet anders zijn
        // dan de blokjes), en de code mag niet langer zijn dan wat een auto kan bewaren.
        if (kind == com.senne.imttech.logic.program.ProgramKind.CAR) {
            if (source.length() > com.senne.imttech.logic.program.LogicProgram.MAX_SOURCE) {
                notify(player, core, session, "Not saved: an RC program can be at most "
                        + com.senne.imttech.logic.program.LogicProgram.MAX_SOURCE + " characters (a car keeps no more)", true);
                return;
            }
            try {
                graph = com.senne.imttech.logic.program.GraphConverter.toGraph(com.senne.imttech.logic.program.ScriptParser.parse(
                        source, com.senne.imttech.logic.program.ProgramKind.CAR), graph);
            } catch (com.senne.imttech.logic.program.LogicException e) {
                notify(player, core, session, "Not saved: " + e.describe(), true);
                return;
            }
        }
        // De defs en gedeelde namen van de andere global scripts (hoofdstuk 43) mag dit diagram gebruiken. Een RC program
        // (hoofdstuk 119) niet: dat draait op de auto, die alleen zijn eigen kopie heeft en de server niet kan vragen.
        var library = kind == com.senne.imttech.logic.program.ProgramKind.CAR ? com.senne.imttech.logic.program.Library.EMPTY
                : com.senne.imttech.logic.program.Library.of(store.programs(), oldName.isEmpty() ? name : oldName);
        var problems = graph.problems(kind, library);
        if (!problems.isEmpty()) {
            notify(player, core, session, "Not saved: " + problems.getFirst().message(), true);
            return;
        }
        // Hoofdstuk 135 (nalezing): een when met een berekende naam ({@code when keycard(f"{room} door"):}) rekent zijn naam
        // opnieuw uit met de namen bovenaan, ook als je die in de blokjes veranderde.
        com.senne.imttech.logic.program.ScriptParser.refreshTriggerNames(graph, kind, true);
        var stats = com.senne.imttech.logic.LogicServer.stats(level, core);
        if (!stats.online()) {
            notify(player, core, session, "Not saved: " + stats.problem(), true);
            return;
        }
        var old = oldName.isEmpty() ? null : store.program(oldName);
        // Hoofdstuk 130: een programma op slot (van een SD-kaart) verander je nooit; je spel heeft zijn code ook niet.
        if (old != null && old.locked()) {
            notify(player, core, session, "Not saved: " + old.name() + " is locked, you can only use it", true);
            return;
        }
        var clash = store.program(name);
        if (clash != null && clash != old) {
            notify(player, core, session, "Not saved: there already is a diagram called " + clash.name(), true);
            return;
        }
        if (old == null && store.programs().size() >= com.senne.imttech.logic.LogicStore.MAX_PROGRAMS) {
            notify(player, core, session, "Not saved: a server holds at most "
                    + com.senne.imttech.logic.LogicStore.MAX_PROGRAMS + " diagrams", true);
            return;
        }
        int used = store.blocks() - (old == null ? 0 : old.graph().size()) + graph.size();
        if (used > stats.capacity()) {
            notify(player, core, session, "Not enough storage: this needs "
                    + com.senne.imttech.logic.LogicStats.bytes(com.senne.imttech.logic.LogicStats.blocksMb(used))
                    + ", your SSDs hold " + com.senne.imttech.logic.LogicStats.bytes(stats.ssdMb())
                    + ". Add an SSD to a server rack.", true);
            return;
        }
        store.put(old == null ? null : old.name(), new com.senne.imttech.logic.program.LogicProgram(name,
                old == null || old.enabled(), graph,
                source.length() > com.senne.imttech.logic.program.LogicProgram.MAX_SOURCE ? "" : source, kind));
        core.setChanged();
        com.senne.imttech.logic.LogicDevices.refresh(level, core, player);
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " saved the diagram \"" + name + "\"");
        String unknown = unknownDevice(level, core, graph);
        notify(player, core, session, unknown == null ? "Saved " + name + " (" + graph.size() + " blocks)"
                : "Saved " + name + ", but there is no device called \"" + unknown + "\" (yet)", unknown != null);
    }

    /**
     * De eerste naam van een apparaat in dit diagram die de server (nog) niet kent, of null. Een naam die pas tijdens het
     * draaien uitgerekend wordt ({@code on(f"gate {i}")}, {@code on("gate " + i)}, {@code pulse(door)} met een
     * parameter) telt niet: die kennen we bij het bewaren nog niet (hoofdstuk 131; eerst kreeg je dan de rode melding
     * no device called ""). Public voor de test logic_text_with_names_run.
     */
    public static @Nullable String unknownDevice(ServerLevel level, SecurityCoreBlockEntity core,
                                                 com.senne.imttech.logic.program.LogicGraph graph) {
        // Per trigger (keycard, codepanel, button, lever) de namen van de apparaten die hem starten.
        java.util.Set<String> inputs = new java.util.HashSet<>();
        for (var input : com.senne.imttech.logic.LogicDevices.inputs(level, core)) {
            inputs.add(input.trigger().code() + ":" + input.name().toLowerCase(java.util.Locale.ROOT));
        }
        java.util.Set<Integer> reachable = graph.reachable();
        for (var node : graph.nodes()) {
            // Een when met een berekende naam (hoofdstuk 135) heeft zijn uitgerekende naam in text: die kijken we wel na.
            if (!reachable.contains(node.id) || node.op.textArg() != com.senne.imttech.logic.program.Op.Arg.DEVICE
                    || !node.op.isTrigger() && !node.textExpr.isBlank()) {
                continue;
            }
            boolean known = node.op.fromInput()
                    ? inputs.contains(node.op.code() + ":" + node.text.toLowerCase(java.util.Locale.ROOT))
                    : !core.logic().devices(node.text).isEmpty();
            if (!known) {
                return node.text;
            }
        }
        return null;
    }

    private static void deleteProgram(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String name) {
        if (!core.logic().remove(name)) {
            notify(player, core, session, "There is no diagram called " + name, true);
            return;
        }
        core.setChanged();
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " deleted the diagram \"" + name + "\"");
        notify(player, core, session, "Deleted " + name, false);
    }

    // ================================================================ De SD-kaart (hoofdstuk 130)

    /**
     * Het blok waarvan deze desktop de SD-kaart gebruikt: op een Computer de kaart in die kast; op de kern zelf en met de
     * laptop de kaart in de kern. De telefoon heeft geen Logic of Files, dus geen kaart.
     */
    private static @Nullable com.senne.imttech.sdcard.SdCardHolder sdHolder(ServerLevel level, SecurityCoreBlockEntity core,
                                                                          Session session) {
        if (session.phone() != null) {
            return null;
        }
        if (session.computer() != null) {
            return level.getBlockEntity(session.computer()) instanceof com.senne.imttech.computer.ComputerBlockEntity computer
                    ? computer : null;
        }
        return core;
    }

    /** Wat een opdracht op de SD-kaart zei, onderaan de desktop (geen tekst: alleen de nieuwe lijsten sturen). */
    private static void sdResult(ServerPlayer player, SecurityCoreBlockEntity core, Session session,
                                 com.senne.imttech.sdcard.SdDesktop.Result result) {
        // Een valstrik kan de desktop net gesloten hebben (SdDesktop.copy): dan sturen we niets meer.
        if (SESSIONS.get(player.getUUID()) != session) {
            return;
        }
        if (result.text() == null) {
            sendSnapshot(player, core, session);
        } else {
            notify(player, core, session, result.text(), result.error());
        }
    }

    /** Een bestand van de SD-kaart openen in Notes (de inhoud gaat één keer naar het scherm, zoals bij een gewoon bestand). */
    private static void openCardFile(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String path) {
        ServerLevel level = player.serverLevel();
        var view = com.senne.imttech.sdcard.SdDesktop.view(level, sdHolder(level, core, session));
        if (com.senne.imttech.sdcard.SdDesktop.cardFile(view, path) == null) {
            notify(player, core, session, VirtualFs.nameOf(path) + ": no such file on the SD card", true);
            return;
        }
        session.fileToSend = com.senne.imttech.sdcard.SdDesktop.PREFIX + com.senne.imttech.sdcard.SdDesktop.cardPath(path);
        sendSnapshot(player, core, session);
    }

    /** De code van een open programma op de SD-kaart bekijken voor je het installeert (op slot: nooit). */
    private static void viewCardProgram(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String name) {
        ServerLevel level = player.serverLevel();
        var view = com.senne.imttech.sdcard.SdDesktop.view(level, sdHolder(level, core, session));
        String code = com.senne.imttech.sdcard.SdDesktop.code(view, name);
        if (code == null) {
            notify(player, core, session, "You can't read " + name + ": it is locked, or not on the SD card", true);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("n", name);
        tag.putString("c", code);
        session.sdCodeToSend = tag;
        sendSnapshot(player, core, session);
    }

    /** Een diagram aan- of uitzetten (uit = het blijft bewaard, maar doet niets). */
    private static void enableProgram(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String name, boolean on) {
        var program = core.logic().program(name);
        if (program == null) {
            notify(player, core, session, "There is no diagram called " + name, true);
            return;
        }
        core.logic().put(program.name(), program.withEnabled(on));
        core.setChanged();
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " switched the diagram \""
                + program.name() + "\" " + (on ? "on" : "off"));
        notify(player, core, session, program.name() + (on ? " is on" : " is off"), false);
    }

    /** Een keycardlezer of codepaneel een andere naam geven (in de app Devices of Logic). */
    private static void renameInput(ServerPlayer player, SecurityCoreBlockEntity core, Session session, BlockPos pos, String name) {
        String clean = com.senne.imttech.logic.InputDeviceBlockEntity.cleanName(name);
        // Een lezer, paneel of (hoofdstuk 38) switch: alles met een naam in het blok zelf.
        if (clean.isEmpty() || !SecurityCores.linkedTo(player.serverLevel(), core, pos)
                || !(player.serverLevel().getBlockEntity(pos) instanceof com.senne.imttech.lan.NamedDevice device)) {
            notify(player, core, session, "That name cannot be used", true);
            return;
        }
        String old = device.name();
        device.setName(clean);
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " renamed \"" + old + "\" to \"" + clean + "\"");
        notify(player, core, session, "Renamed to " + clean, false);
    }

    /**
     * Een geblokkeerde kaart echt weggooien (zodat de lijst niet vol en rommelig wordt). Een actieve
     * kaart moet je eerst blokkeren: zo gooi je er niet per ongeluk een weg die nog gebruikt wordt.
     */
    private static void deleteCard(ServerPlayer player, SecurityCoreBlockEntity core, Session session, int number) {
        var card = core.logic().card(number);
        if (card == null) {
            notify(player, core, session, "There is no card #" + number, true);
            return;
        }
        if (!card.blocked()) {
            notify(player, core, session, "Block card #" + number + " first: only blocked cards can be deleted", true);
            return;
        }
        core.logic().forget(number);
        core.setChanged();
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " deleted keycard #" + number);
        notify(player, core, session, "Card #" + number + " deleted", false);
    }

    /** Alle geblokkeerde kaarten in één keer weggooien. */
    private static void deleteBlockedCards(ServerPlayer player, SecurityCoreBlockEntity core, Session session) {
        int removed = core.logic().forgetBlocked();
        if (removed == 0) {
            notify(player, core, session, "There are no blocked cards", true);
            return;
        }
        core.setChanged();
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " deleted " + removed + " blocked keycard"
                + (removed == 1 ? "" : "s"));
        notify(player, core, session, "Deleted " + removed + " blocked card" + (removed == 1 ? "" : "s"), false);
    }

    // ================================================================ Keycard-groepen (25 oktober)

    /** Een nieuwe keycard-groep, in groep {@code parent} ("" = bovenaan). */
    private static void newCardGroup(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String name, String parent) {
        var store = core.logic();
        String clean = name.strip();
        if (!com.senne.imttech.logic.LogicStore.validGroupName(clean)) {
            notify(player, core, session, "A group name needs 1 to " + com.senne.imttech.logic.LogicStore.MAX_GROUP_NAME
                    + " letters, without quotes", true);
            return;
        }
        if (store.hasGroup(clean)) {
            notify(player, core, session, "There is already a group called '" + store.spelling(clean) + "'", true);
            return;
        }
        if (store.groups().size() >= com.senne.imttech.logic.LogicStore.MAX_GROUPS) {
            notify(player, core, session, "This network cannot have more than " + com.senne.imttech.logic.LogicStore.MAX_GROUPS
                    + " keycard groups", true);
            return;
        }
        String above = store.hasGroup(parent) ? store.spelling(parent) : "";
        store.addGroup(clean, above);
        core.setChanged();
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " made the keycard group '" + clean + "'"
                + (above.isEmpty() ? "" : " in '" + above + "'"));
        notify(player, core, session, "Group '" + clean + "' made" + (above.isEmpty() ? "" : " in '" + above + "'"), false);
    }

    /** Een keycard-groep een andere naam geven (de kaarten en groepen erin gaan mee). */
    private static void renameCardGroup(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String group, String name) {
        var store = core.logic();
        String old = store.spelling(group);
        String clean = name.strip();
        if (old == null) {
            notify(player, core, session, "There is no group '" + group + "'", true);
            return;
        }
        if (!com.senne.imttech.logic.LogicStore.validGroupName(clean)) {
            notify(player, core, session, "A group name needs 1 to " + com.senne.imttech.logic.LogicStore.MAX_GROUP_NAME
                    + " letters, without quotes", true);
            return;
        }
        if (!store.renameGroup(old, clean)) {
            notify(player, core, session, "There is already a group called '" + store.spelling(clean) + "'", true);
            return;
        }
        core.setChanged();
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " renamed the keycard group '" + old + "' to '" + clean + "'");
        notify(player, core, session, "Renamed '" + old + "' to '" + clean + "'", false);
    }

    /** Een keycard-groep weggooien: wat erin zat (kaarten en groepen), schuift een laag omhoog. */
    private static void deleteCardGroup(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String group) {
        var store = core.logic();
        String old = store.spelling(group);
        if (old == null) {
            notify(player, core, session, "There is no group '" + group + "'", true);
            return;
        }
        String parent = store.parentOf(old);
        store.removeGroup(old);
        core.setChanged();
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " deleted the keycard group '" + old + "'");
        notify(player, core, session, "Deleted '" + old + "'" + (parent.isEmpty() ? "" : ": its cards and groups are now in '" + parent + "'"),
                false);
    }

    /** Een keycard-groep in een andere groep steken ("" = bovenaan). Een rondje mag niet. */
    private static void moveCardGroup(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String group, String parent) {
        var store = core.logic();
        String own = store.spelling(group);
        String above = parent.isBlank() ? "" : store.spelling(parent);
        if (own == null || above == null) {
            notify(player, core, session, "There is no group '" + (own == null ? group : parent) + "'", true);
            return;
        }
        if (!store.moveGroup(own, above)) {
            notify(player, core, session, "'" + own + "' can't go inside '" + above + "': that would make a loop", true);
            return;
        }
        core.setChanged();
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + " put the keycard group '" + own + "' in "
                + (above.isEmpty() ? "no group" : "'" + above + "'"));
        notify(player, core, session, "'" + own + "' is now in " + (above.isEmpty() ? "no group" : "'" + above + "'"), false);
    }

    /** Een kaart ook in een groep zetten ({@code in}), of er weer uit halen. Zijn andere groepen blijven. */
    private static void cardInGroup(ServerPlayer player, SecurityCoreBlockEntity core, Session session, int number, String group, boolean in) {
        var store = core.logic();
        String own = store.spelling(group);
        if (store.card(number) == null || own == null) {
            notify(player, core, session, store.card(number) == null ? "There is no card #" + number : "There is no group '" + group + "'", true);
            return;
        }
        boolean changed = in ? store.addToGroup(number, own) : store.removeFromGroup(number, own);
        if (!changed) {
            notify(player, core, session, "Card #" + number + (in ? " is already in '" : " is not in '") + own + "'", true);
            return;
        }
        core.setChanged();
        core.logEvent(EventLog.Type.DEVICE, player.getGameProfile().getName() + (in ? " put keycard #" : " took keycard #") + number
                + (in ? " in '" : " out of '") + own + "'");
        notify(player, core, session, "Card #" + number + (in ? " is now also in '" : " is no longer in '") + own + "'", false);
    }

    /** Wie heet zo? Eerst wie nu online is, anders wie hier ooit was (Minecraft onthoudt dat). */
    private static @Nullable com.mojang.authlib.GameProfile findProfile(ServerPlayer asker, String name) {
        if (asker.getServer() == null) {
            return null;
        }
        ServerPlayer online = asker.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getGameProfile();
        }
        var cache = asker.getServer().getProfileCache();
        return cache == null ? null : cache.get(name).orElse(null);
    }

    /** Een nieuw, leeg bestand. Bestaat het al, dan blijft het zoals het is (en hoor je dat). */
    private static @Nullable String touch(VirtualFs fs, String path) {
        return fs.get(path) != null ? VirtualFs.nameOf(path) + " already exists" : fs.write(path, "");
    }

    /** Een pad uit de opdracht, altijd netjes gemaakt (absoluut, zonder {@code ..}). */
    private static String path(CompoundTag args, String key) {
        return VirtualFs.resolve("/", args.getString(key));
    }

    /** Een nieuw wachtwoord zetten of wijzigen. Dat gooit meteen alle andere sessies van deze kern eruit. */
    private static void passwd(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String newPassword) {
        String problem = SystemPassword.problem(newPassword);
        if (problem != null) {
            notify(player, core, session, "Password rejected: " + problem, true);
            return;
        }
        core.setPassword(newPassword, player.serverLevel().random);
        core.logEvent(EventLog.Type.SYSTEM, player.getGameProfile().getName() + " changed the password");
        // Een nieuw wachtwoord verbreekt elke andere sessie op deze kern.
        kickOthers(player, core.getBlockPos());
        notify(player, core, session, "Password updated. Other sessions were logged out.", false);
    }

    /** De autorisatiecodes instellen (hoofdstuk 49, Settings): alleen de eigenaar. */
    private static void setAuth(ServerPlayer player, SecurityCoreBlockEntity core, Session session, int length, int minutes, boolean old) {
        if (!isOwner(player, core)) {
            notify(player, core, session, "Only the owner can change the authorization codes.", true);
            return;
        }
        core.setAuthSettings(length, minutes, old);
        core.logEvent(EventLog.Type.SYSTEM, player.getGameProfile().getName() + " set the authorization codes to " + core.authLength()
                + " digits, new every " + core.authMinutes() + " min, old codes " + (core.authOldAllowed() ? "allowed" : "not allowed"));
        notify(player, core, session, "Authorization codes: " + core.authLength() + " digits, a new one every " + core.authMinutes()
                + " min, old codes " + (core.authOldAllowed() ? "allowed (logged)" : "not allowed") + ".", false);
    }

    /** Een toegangsverzoek beantwoorden (hoofdstuk 49, de app Requests). */
    private static void answerRequest(ServerPlayer player, SecurityCoreBlockEntity core, Session session, int id, boolean accepted) {
        if (!com.senne.imttech.logic.AuthRequests.answer(player.serverLevel(), core, id, accepted, player)) {
            notify(player, core, session, "That request is gone: it was answered already, or the time ran out.", true);
            return;
        }
        sendSnapshot(player, core, session);
    }

    /** Een telefoon blokkeren of weer vrijgeven (hoofdstuk 50, de app Access): alleen de eigenaar. */
    private static void blockPhone(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String id, boolean blocked) {
        if (!isOwner(player, core)) {
            notify(player, core, session, "Only the owner can block a phone.", true);
            return;
        }
        UUID phoneId;
        try {
            phoneId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return;
        }
        SecurityCoreBlockEntity.Phone phone = core.phone(phoneId);
        if (phone == null) {
            return;
        }
        core.blockPhone(phoneId, blocked);
        core.logEvent(EventLog.Type.SYSTEM, player.getGameProfile().getName() + (blocked ? " blocked" : " unblocked")
                + " the phone of " + phone.name());
        notify(player, core, session, (blocked ? "Blocked" : "Unblocked") + " the phone of " + phone.name() + ".", false);
    }

    /** Gooit iedereen behalve {@code keep} van deze kern af (bij een nieuw wachtwoord of een overname). */
    public static void kickOthers(@Nullable ServerPlayer keep, BlockPos corePos) {
        if (keep == null || keep.getServer() == null) {
            return;
        }
        SESSIONS.entrySet().removeIf(entry -> {
            if (!entry.getValue().core().equals(corePos) || entry.getKey().equals(keep.getUUID())) {
                return false;
            }
            ServerPlayer other = keep.getServer().getPlayerList().getPlayer(entry.getKey());
            if (other != null) {
                ModNetwork.sendTo(other, OsOpenPayload.close(corePos));
            }
            return true;
        });
    }

    // ================================================================ Mappen en bestanden

    /** Na een opdracht op de mappen: lukte het niet, dan zie je waarom; anders meteen de nieuwe lijst. */
    private static void fileResult(ServerPlayer player, SecurityCoreBlockEntity core, Session session, @Nullable String error) {
        if (error != null) {
            notify(player, core, session, error, true);
            return;
        }
        core.setChanged();
        sendSnapshot(player, core, session);
    }

    /**
     * Een bestand openen (in Notes). Is het een valstrik en ben jij niet de eigenaar, dan gaat hij
     * af. De inhoud gaat één keer naar het scherm, zodat wat je aan het typen bent niet steeds
     * overschreven wordt.
     */
    public static void openFile(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String path) {
        VirtualFs.Node node = core.fs().get(path);
        if (node == null || node.isDir()) {
            notify(player, core, session, VirtualFs.nameOf(path) + ": no such file", true);
            return;
        }
        if (node.isTrap() && !isOwner(player, core)) {
            springTrap(player, core, path);
            return;
        }
        if (node.isSound()) {
            // Hoofdstuk 136: een geluid opent niet in Notes; je spel speelt het alleen voor jou.
            com.senne.imttech.alarm.AlarmSpeakers.preview(player, node);
            return;
        }
        session.fileToSend = path;
        sendSnapshot(player, core, session);
    }

    /**
     * Hoofdstuk 136: mag deze speler in Files van de kern op deze plek een geluid zetten? Zijn bureaublad van die kern staat
     * open, en daar mag hij bestanden maken (op een Computer: als de kern dat daar toestaat). Dan die kern, anders null.
     */
    public static @Nullable SecurityCoreBlockEntity filesCore(ServerPlayer player, BlockPos corePos) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.core().equals(corePos)) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(corePos) || !(level.getBlockEntity(corePos) instanceof SecurityCoreBlockEntity core)) {
            return null;
        }
        CompoundTag args = new CompoundTag();
        args.putString("path", VirtualFs.SOUNDS + "/sound");
        return session.rights(level).allows("fs_touch", args) ? core : null;
    }

    /** Hoofdstuk 136: er kwam een geluid bij in Files: de mappenlijst op het bureaublad van deze speler bijwerken. */
    public static void filesChanged(ServerPlayer player, SecurityCoreBlockEntity core) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.core().equals(core.getBlockPos())) {
            sendSnapshot(player, core, session);
        }
    }

    /** Een notitie bewaren (de Save-knop van Notes). */
    private static void saveFile(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String path, String text) {
        // Een valstrik overschrijven is ook "erin kijken": wie niet de eigenaar is, laat hem afgaan.
        VirtualFs.Node node = core.fs().get(path);
        if (node != null && node.isTrap() && !isOwner(player, core)) {
            springTrap(player, core, path);
            return;
        }
        String error = core.fs().write(path, text);
        if (error != null) {
            notify(player, core, session, error, true);
            return;
        }
        core.setChanged();
        notify(player, core, session, "Saved " + VirtualFs.nameOf(path), false);
    }

    /** Een bestand als valstrik markeren (of weer gewoon maken). Alleen de eigenaar mag dat. */
    private static void setTrap(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String path, boolean on) {
        if (!isOwner(player, core)) {
            notify(player, core, session, "Only the owner can set traps", true);
            return;
        }
        String error = core.fs().setTrap(path, on);
        if (error != null) {
            notify(player, core, session, error, true);
            return;
        }
        core.setChanged();
        notify(player, core, session, VirtualFs.nameOf(path) + (on ? " is now a trap" : " is no longer a trap"), false);
    }

    /**
     * Iemand die niet de eigenaar is, opende een valstrik. Dan:
     * <ul>
     *   <li>een regel in het logboek, en de eigenaar krijgt een alarm van I.M.T;</li>
     *   <li>de lezer wordt uit zijn sessie gegooid en is TRAP_LOCKOUT ticks buitengesloten;</li>
     *   <li>de beveiliging jaagt even op hem (ook als "attack players" uit staat), en de drones
     *       gaan meteen op hem af als hij in het gebied is.</li>
     * </ul>
     * Publiek, zodat straks ook de terminal (cat, grep) hem kan laten afgaan.
     */
    public static void springTrap(ServerPlayer reader, SecurityCoreBlockEntity core, String path) {
        ServerLevel level = reader.serverLevel();
        String name = reader.getGameProfile().getName();
        long until = level.getGameTime() + Math.round(ImtConfig.Security.TRAP_LOCKOUT);
        core.logEvent(EventLog.Type.TRAP, name + " opened the trap file " + path);
        LOCKOUTS.put(reader.getUUID(), new Lockout(core.getBlockPos().immutable(), until));
        close(reader);
        reader.displayClientMessage(Component.translatable("os.imttech.msg.trap_sprung",
                VirtualFs.nameOf(path), Math.round(ImtConfig.Security.TRAP_LOCKOUT) / 20).withStyle(ChatFormatting.RED), true);
        level.playSound(null, core.getBlockPos(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 0.6f);
        // De eigenaar weet het meteen (waar hij ook is).
        ServerPlayer owner = core.ownerId() == null || level.getServer() == null ? null
                : level.getServer().getPlayerList().getPlayer(core.ownerId());
        if (owner != null && owner != reader) {
            ImtServer.send(owner, Component.translatable("os.imttech.msg.trap_alert", name,
                    VirtualFs.nameOf(path), core.hostname()), ImtMessagePayload.Level.ALERT);
        }
        // De beveiliging jaagt op hem.
        core.hunt(reader.getUUID(), until);
        if (core.isOnline() && SecurityCores.inArea(level, core.getBlockPos(), reader.blockPosition())
                && SecurityCores.isIntruder(level, core, reader)) {
            for (SecurityDroneEntity drone : level.getEntitiesOfClass(SecurityDroneEntity.class, core.area(),
                    drone -> core.getBlockPos().equals(drone.home()))) {
                drone.setTarget(reader);
            }
        }
    }

    // ================================================================ De momentopname

    /** Een melding in de desktop (onderaan, een paar seconden), met meteen een nieuwe momentopname. */
    private static void notify(ServerPlayer player, SecurityCoreBlockEntity core, Session session, String text, boolean error) {
        session.notice = text;
        session.noticeError = error;
        sendSnapshot(player, core, session);
    }

    private static void sendSnapshot(ServerPlayer player, SecurityCoreBlockEntity core, Session session) {
        ModNetwork.sendTo(player, new OsSnapshotPayload(core.getBlockPos(),
                snapshot(core, player.serverLevel(), session, isOwner(player, core))));
    }

    /**
     * De hele desktop in één CompoundTag: de status, het logboek, en (alleen als ze veranderd zijn)
     * de mappen. Een geopend bestand en een melding gaan er maar één keer in. Wie niet de eigenaar
     * is, ziet niet welke bestanden valstrikken zijn.
     */
    public static CompoundTag snapshot(SecurityCoreBlockEntity core, ServerLevel level, Session session, boolean owner) {
        CompoundTag data = new CompoundTag();
        // Hoofdstuk 40: op een Computer alleen de gegevens van de apps die de kern daar toestaat (wat er niet op staat,
        // krijgt die pc ook niet), plus de naam van de computer en de rechten (voor de icoontjes en knoppen).
        DesktopRights rights = session.rights(level);
        if (!rights.full()) {
            data.put("rights", rights.save());
            if (session.computer() != null && level.getBlockEntity(session.computer())
                    instanceof com.senne.imttech.computer.ComputerBlockEntity computer) {
                data.putString("pc", computer.name());
            }
        }
        data.putString("host", core.hostname());
        data.putString("ip", core.ip());
        data.putInt("port", core.sshPort());
        data.putString("owner", core.ownerName());
        data.putBoolean("isOwner", owner);
        data.putBoolean("hasPassword", core.hasPassword());
        // Hoofdstuk 54: op de kern zelf (en via de laptop) de apps die de eigenaar van het bureaublad haalde.
        if (rights.full()) {
            ListTag hidden = new ListTag();
            for (String app : core.hiddenApps()) {
                hidden.add(net.minecraft.nbt.StringTag.valueOf(app));
            }
            data.put("hidden", hidden);
        }
        data.putBoolean("online", core.isOnline());
        data.putBoolean("remote", session.remote());
        data.putBoolean("phone", session.phone() != null);
        // Het logboek, met per regel hoe lang geleden het was (in seconden).
        long now = level.getGameTime();
        if (rights.shows("LOGS")) {
            ListTag log = new ListTag();
            for (EventLog.Entry entry : core.log().entries()) {
                CompoundTag line = new CompoundTag();
                line.putInt("ago", (int) Math.max(0, (now - entry.time()) / 20));
                line.putString("k", entry.type().name());
                line.putString("x", entry.text());
                if (!entry.flag().isEmpty()) {
                    line.putString("f", entry.flag()); // hoofdstuk 138: gemarkeerd door when logged:
                }
                log.add(line);
            }
            data.put("log", log);
        }
        appSnapshot(data, core, level, session, rights);
        // De mappen: alleen als het scherm een oudere versie heeft (en op een Computer alleen met Files of Notes).
        boolean files = rights.shows("FILES") || rights.shows("NOTES");
        VirtualFs fs = core.fs();
        if (files && (session.sentFs != fs || session.sentRevision != fs.revision())) {
            data.put("fs", fsList(fs, owner));
            session.sentFs = fs;
            session.sentRevision = fs.revision();
        }
        // Hoofdstuk 130: de SD-kaart (voor Logic en Files), alleen als er iets aan veranderde: een andere kaart, iets erop
        // veranderd, of eruit gehaald. Nooit code; de code van een open programma alleen als je hem wilt bekijken (één keer).
        boolean logicApp = rights.shows("LOGIC");
        var sdView = logicApp || files ? com.senne.imttech.sdcard.SdDesktop.view(level, sdHolder(level, core, session)) : null;
        if (logicApp || files) {
            String sdKey = com.senne.imttech.sdcard.SdDesktop.key(sdView);
            if (!sdKey.equals(session.sentSdKey)) {
                data.put("sd", com.senne.imttech.sdcard.SdDesktop.snapshot(sdView));
                session.sentSdKey = sdKey;
            }
        }
        if (session.sdCodeToSend != null) {
            if (logicApp) {
                data.put("sdCode", session.sdCodeToSend);
            }
            session.sdCodeToSend = null;
        }
        // Een net geopend bestand (één keer).
        if (session.fileToSend != null && com.senne.imttech.sdcard.SdDesktop.onCard(session.fileToSend)) {
            // Hoofdstuk 130: een bestand van de SD-kaart (gewoon te bewaren: een kaart heeft geen security.conf).
            VirtualFs.Node node = com.senne.imttech.sdcard.SdDesktop.cardFile(sdView, session.fileToSend);
            if (files && node != null) {
                CompoundTag file = new CompoundTag();
                file.putString("p", session.fileToSend);
                file.putString("c", node.content());
                file.putBoolean("s", false);
                data.put("file", file);
            }
            session.fileToSend = null;
        }
        if (session.fileToSend != null) {
            VirtualFs.Node node = fs.get(session.fileToSend);
            if (files && node != null && !node.isDir()) {
                CompoundTag file = new CompoundTag();
                file.putString("p", session.fileToSend);
                file.putString("c", node.content());
                file.putBoolean("s", node.isSystem());
                data.put("file", file);
            }
            session.fileToSend = null;
        }
        // Welke vensters er de vorige keer open stonden (één keer, bij het openen; 19 oktober). Een Computer heeft zijn eigen.
        if (!session.sentDesktop) {
            ListTag desk = core.desktop();
            if (session.computer() != null) {
                desk = level.getBlockEntity(session.computer()) instanceof com.senne.imttech.computer.ComputerBlockEntity computer
                        ? computer.desktop() : new ListTag();
            }
            data.put("desk", desk);
            session.sentDesktop = true;
        }
        // Een melding (één keer).
        if (session.notice != null) {
            CompoundTag notice = new CompoundTag();
            notice.putString("x", session.notice);
            notice.putBoolean("err", session.noticeError);
            data.put("notice", notice);
            session.notice = null;
        }
        return data;
    }

    /**
     * De gegevens van de apps, elk alleen als die app op deze desktop staat (op de kern en met de laptop: allemaal).
     * Sommige apps lezen ook gegevens van een andere: Devices de schakelaars van Access, Logic de keycard-groepen en de
     * server, Status de server.
     */
    private static void appSnapshot(CompoundTag data, SecurityCoreBlockEntity core, ServerLevel level, Session session,
                                    DesktopRights rights) {
        // Toegang en de schakelaars van het netwerk (de apps Access en Devices).
        if (rights.shows("ACCESS") || rights.shows("DEVICES")) {
            CompoundTag access = new CompoundTag();
            access.putBoolean("mobs", core.attackMobs());
            access.putBoolean("players", core.attackPlayers());
            access.putBoolean("pvp", level.getServer().isPvpAllowed());
            access.putBoolean("drones", core.dronesEnabled());
            access.putBoolean("lasers", core.lasersEnabled());
            access.putBoolean("cameras", core.camerasEnabled());
            ListTag trusted = new ListTag();
            for (String name : core.trustedNames()) {
                trusted.add(net.minecraft.nbt.StringTag.valueOf(name));
            }
            access.put("trusted", trusted);
            // De telefoons die aan deze kern hangen (hoofdstuk 50): blokkeren kan hier.
            ListTag phones = new ListTag();
            for (SecurityCoreBlockEntity.Phone phone : core.phones()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", phone.id().toString());
                entry.putString("n", phone.name());
                entry.putBoolean("b", phone.blocked());
                phones.add(entry);
            }
            access.put("phones", phones);
            data.put("access", access);
        }
        // De apparaten met hun status.
        if (rights.shows("DEVICES")) {
            data.put("devices", com.senne.imttech.security.SecurityDevices.snapshot(level, core));
        }
        if (rights.shows("CAMERAS")) {
            ListTag cameras = com.senne.imttech.security.SecurityDevices.cameras(level, core);
            cameras.addAll(com.senne.imttech.robot.Robots.cameras(level, core)); // robots met een camera (hoofdstuk 24)
            data.put("cameras", cameras);
        }
        // De guard robots in het gebied van de kern (de app Robots, hoofdstuk 21).
        if (rights.shows("ROBOTS")) {
            data.put("robots", com.senne.imttech.robot.Robots.snapshot(level, core));
            data.put("robotPrograms", com.senne.imttech.robot.Robots.programNames(core, com.senne.imttech.logic.program.ProgramKind.ROBOT));
            data.put("dronePrograms", com.senne.imttech.robot.Robots.programNames(core, com.senne.imttech.logic.program.ProgramKind.DRONE));
            // Hoofdstuk 119: de RC Cars in het gebied, en de RC programs op deze server.
            data.put("cars", com.senne.imttech.car.RcCars.snapshot(level, core));
            data.put("carPrograms", com.senne.imttech.robot.Robots.programNames(core, com.senne.imttech.logic.program.ProgramKind.CAR));
        }
        if (rights.shows("TRAINING")) {
            data.put("training", com.senne.imttech.training.Trainings.snapshot(level, core));
        }
        // De autorisatiecode van dit moment (hoofdstuk 49): voor iedereen die de desktop mag openen (Status), en de
        // instellingen (Settings). De toegangsverzoeken van de Auth Consoles (de app Requests).
        if (rights.shows("STATUS") || rights.full()) {
            CompoundTag auth = new CompoundTag();
            auth.putString("code", core.authCode());
            auth.putInt("left", core.authSecondsLeft(level.getGameTime()));
            auth.putInt("len", core.authLength());
            auth.putInt("min", core.authMinutes());
            auth.putBoolean("old", core.authOldAllowed());
            data.put("auth", auth);
        }
        if (rights.shows("REQUESTS")) {
            data.put("requests", com.senne.imttech.logic.AuthRequests.snapshot(level, core));
        }
        // Wat I.M.T je zei (hoofdstuk 50, de app Messages), met per bericht hoe lang geleden (seconden).
        if (rights.shows("MESSAGES")) {
            ListTag messages = new ListTag();
            for (SecurityCoreBlockEntity.Message message : core.messages()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("ago", (int) Math.max(0, (level.getGameTime() - message.time()) / 20));
                entry.putString("x", message.text());
                entry.putBoolean("a", message.alarm());
                messages.add(entry);
            }
            data.put("messages", messages);
        }
        // De server (serverracks) waar de redstone-diagrammen op draaien.
        if (rights.shows("MONITOR") || rights.shows("STATUS") || rights.shows("LOGIC")) {
            data.put("server", com.senne.imttech.logic.LogicServer.snapshot(level, core));
        }
        // De backups van deze server en van de eigenaar (de app Backup).
        if (rights.shows("BACKUP")) {
            data.put("backup", com.senne.imttech.logic.Backups.snapshot(level, core));
        }
        // De computers op het netwerk van deze kern (de app Computers, hoofdstuk 40): nooit op een Computer zelf.
        if (rights.full()) {
            data.put("computers", com.senne.imttech.computer.Computers.snapshot(level, core));
        }
        // De zone: de kubussen waarin alleen jij en je vrienden bouwen (de app Zone, hoofdstuk 10).
        if (rights.shows("ZONE")) {
            data.put("zone", com.senne.imttech.security.zone.ZoneApp.snapshot(level, core,
                    level.getServer().getPlayerList().getPlayer(session.viewer()), session.remote()));
        }
        // De keycards die dit netwerk schreef, met hun kleur en hun eigen groepen.
        if (rights.shows("KEYCARDS")) {
            ListTag cards = new ListTag();
            for (com.senne.imttech.logic.LogicStore.Card card : core.logic().cards()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("n", card.number());
                entry.putString("l", card.label());
                entry.putInt("c", card.colour());
                entry.putBoolean("b", card.blocked());
                entry.putString("w", card.writtenBy());
                ListTag groups = new ListTag();
                for (String group : card.groups()) {
                    groups.add(net.minecraft.nbt.StringTag.valueOf(group));
                }
                entry.put("g", groups);
                cards.add(entry);
            }
            data.put("cards", cards);
        }
        // De keycard-groepen (25 oktober): per groep in welke groep hij zit ("" = bovenaan).
        if (rights.shows("KEYCARDS") || rights.shows("LOGIC") || rights.full()) {
            ListTag cardGroups = new ListTag();
            for (String group : core.logic().groups()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("n", group);
                entry.putString("p", core.logic().parentOf(group));
                cardGroups.add(entry);
            }
            data.put("cardGroups", cardGroups);
        }
        if (!rights.shows("LOGIC")) {
            return;
        }
        // De app Logic: hoe vol de SSD's zijn, wat er bezig is, en welke apparaten een diagram kan noemen.
        CompoundTag info = new CompoundTag();
        info.putInt("used", core.logic().blocks());
        int[] load = com.senne.imttech.logic.LogicRunner.load(level, core);
        info.putInt("running", load[0]);
        info.putInt("queued", load[1]);
        ListTag outputs = new ListTag();
        for (String name : core.logic().deviceNames()) {
            outputs.add(net.minecraft.nbt.StringTag.valueOf(name));
        }
        info.put("outputs", outputs);
        ListTag inputs = new ListTag();
        for (var input : com.senne.imttech.logic.LogicDevices.inputs(level, core)) {
            CompoundTag entry = new CompoundTag();
            entry.putString("n", input.name());
            // Welke trigger hij start (hoofdstuk 38): keycard, codepanel, button of lever.
            entry.putString("t", input.trigger().code());
            entry.putInt("x", input.pos().getX());
            entry.putInt("y", input.pos().getY());
            entry.putInt("z", input.pos().getZ());
            inputs.add(entry);
        }
        info.put("inputs", inputs);
        // Je robots (hoofdstuk 23): hun namen en wat er op hun pinnen zit, voor Help en Ctrl+Spatie.
        info.put("robots", com.senne.imttech.robot.Robots.logicInfo(level, core));
        // De gedeelde namen van de global scripts (hoofdstuk 43) met hun waarde van nu: de app Logic laat ze zien.
        ListTag shared = new ListTag();
        core.logic().sharedValues().forEach((name, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("n", name);
            entry.putString("t", com.senne.imttech.logic.program.VarType.of(value).word());
            entry.putString("v", value instanceof String text ? "\"" + text + "\"" : com.senne.imttech.logic.program.Expr.text(value));
            shared.add(entry);
        });
        info.put("shared", shared);
        data.put("logicInfo", info);
        // De diagrammen zelf: alleen als het scherm een oudere versie heeft (anders zou wat je aan het
        // bewerken bent steeds overschreven worden).
        com.senne.imttech.logic.LogicStore store = core.logic();
        if (session.sentLogic != store || session.sentLogicRevision != store.revision()) {
            CompoundTag logic = new CompoundTag();
            logic.putInt("rev", store.revision());
            ListTag programs = new ListTag();
            for (var program : store.programs()) {
                // Hoofdstuk 130: van een programma op slot alleen de naam en zo, nooit de blokjes of de code.
                programs.add(program.saveForClient());
            }
            logic.put("programs", programs);
            data.put("logic", logic);
            session.sentLogic = store;
            session.sentLogicRevision = store.revision();
        }
    }

    /** Alle mappen en bestanden als lijst: pad, map of niet, systeem, valstrik (alleen voor de eigenaar), grootte. */
    private static ListTag fsList(VirtualFs fs, boolean owner) {
        ListTag list = new ListTag();
        for (Map.Entry<String, VirtualFs.Node> entry : fs.walk().entrySet()) {
            VirtualFs.Node node = entry.getValue();
            CompoundTag item = new CompoundTag();
            item.putString("p", entry.getKey());
            if (node.isDir()) {
                item.putBoolean("d", true);
            } else {
                item.putInt("n", node.content().length());
                if (node.isSystem()) {
                    item.putBoolean("s", true);
                }
                if (node.isSound()) {
                    // Hoofdstuk 136: een geluid, en hoe lang het duurt (de vingerafdruk zelf heeft het scherm niet nodig).
                    item.putFloat("a", node.soundSamples() / (float) com.senne.imttech.alarm.audio.AlarmAudio.RATE);
                }
                if (owner && node.isTrap()) {
                    item.putBoolean("t", true);
                }
            }
            list.add(item);
        }
        return list;
    }

    // ================================================================ Elke tick en opruimen

    /** Elke seconde: nog bij de kern (lokaal) en leeft de speler nog? En de momentopname verversen. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != session.dimension
                || !(level.getBlockEntity(session.core()) instanceof SecurityCoreBlockEntity core)
                || !player.isAlive()
                || !session.remote() && player.distanceToSqr(session.anchor.getCenter()) > REACH * REACH) {
            close(player);
            return;
        }
        // Op een Computer (hoofdstuk 40): werkt hij nog, en mag jij er nog op (iemand kan "wie" net veranderd hebben)?
        if (session.computer() != null && level.getGameTime() % 20 == 0) {
            Component problem = com.senne.imttech.computer.Computers.stillAllowed(player, core, session.computer());
            if (problem != null) {
                close(player);
                player.displayClientMessage(problem.copy().withStyle(ChatFormatting.YELLOW), true);
                return;
            }
        }
        // Op afstand (hoofdstuk 40): de kern geladen houden, en de verbinding valt weg zonder laptop of zonder stroom.
        if (session.remote() && level.getGameTime() % 20 == 0) {
            // Met de telefoon (hoofdstuk 50): die moet nog in je rugzak zitten en niet geblokkeerd zijn.
            boolean lostDevice = session.phone() != null
                    ? !Phones.hasPhone(player, session.phone()) || core.phoneBlocked(session.phone())
                    : !hasLaptop(player);
            if (lostDevice || !core.isOnline()) {
                close(player);
                player.displayClientMessage(Component.translatable(!core.isOnline() ? "os.imttech.msg.lost_power"
                        : session.phone() != null ? "os.imttech.msg.lost_phone" : "os.imttech.msg.lost_laptop", core.hostname())
                        .withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            keepLoaded(level, session.core());
        }
        if (level.getGameTime() % SNAPSHOT_INTERVAL == 0) {
            sendSnapshot(player, core, session);
        }
    }

    /** De sessie van deze speler (of null). Voor de tests. */
    public static @Nullable Session session(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
        com.senne.imttech.logic.DatacenterBrain.forget(event.getEntity().getUUID());
        com.senne.imttech.logic.DatacenterGlasses.forget(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        com.senne.imttech.logic.AuthRequests.clear();
        LOCKOUTS.clear();
        WRONG.clear();
        WAIT_UNTIL.clear();
        LAST_FAIL_ALERT.clear();
        com.senne.imttech.computer.Computers.clear();
        com.senne.imttech.security.SecurityDevices.clear();
        com.senne.imttech.logic.LogicServer.clear();
        com.senne.imttech.logic.LogicEvents.clear();
        com.senne.imttech.logic.LogicRunner.clear();
        com.senne.imttech.logic.DatacenterBrain.clear();
        com.senne.imttech.logic.DatacenterGlasses.clear();
    }
}
