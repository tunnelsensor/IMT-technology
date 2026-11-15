package com.senne.imttech.lan;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.network.RouterActionPayload;
import com.senne.imttech.network.RouterOpenPayload;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.redstone.ImtRelays;
import com.senne.imttech.redstone.Relay;
import com.senne.imttech.redstone.RelayBlock;
import com.senne.imttech.redstone.RelayBlockEntity;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.security.BaseTrust;
import com.senne.imttech.security.SecurityCoreBlock;
import com.senne.imttech.turret.TurretBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De regels van de Router (hoofdstuk 112, zie RouterBlock): wie mag er draadloos aan, hoe ver, hoeveel, en wat er
 * vanzelf gebeurt.
 * <ul>
 *   <li><b>Wat</b>: elk blok met een netwerkaansluiting (zie NetworkConnectable): relais, Chest Sensors, camera's,
 *       lezers, laserzenders, serverracks, een andere router, zelfs de Security Core. Een apparaat hangt aan hooguit één
 *       router; alleen de kern mag aan meer routers tegelijk hangen (zo pakt een tweede router hem niet af van de
 *       eerste).</li>
 *   <li><b>Hoe ver</b>: {@code routerRange} blokken in een rechte lijn (32), ook door muren.</li>
 *   <li><b>Hoeveel</b>: {@code routerDevices} per router (10). Een verbinding tussen twee routers kost bij allebei één
 *       plek (jouw wens: "dan is het één router + negen andere devices").</li>
 *   <li><b>Vanzelf</b>: zet je een apparaat neer dat nog nergens aan hangt, dan gaat het aan de dichtstbijzijnde router
 *       met een vrije plek die van jou is (of van je basis). Zet je een router neer, dan pakt hij de losse apparaten in
 *       zijn buurt, de dichtste eerst. "Los" betekent: er zit geen router en geen kern in het net van dat apparaat. Zo
 *       kost een laserpoort van vier zenders of een kast vol racks maar één plek: de rest hangt eraan vast.</li>
 *   <li><b>Met de hand</b>: in het scherm van de router (rechtsklik), door wie hem neerzette of in die basis vertrouwd
 *       is; een ander krijgt dat scherm niet eens te zien. Een apparaat verbind je alleen als je dat ook mag instellen
 *       (zie {@link #mayLink}): zo hangt niemand jouw camera of je torentje aan zijn router, en toont het scherm van zijn
 *       router niet wat er in jouw basis staat.</li>
 * </ul>
 * Een apparaat dat weg is, ruimen we op zodra het netwerk langs zijn router zoekt (zie {@link #partners}).
 */
public final class Routers {
    private Routers() {}

    /** Zo dichtbij moet je staan om het scherm te openen en te gebruiken (blokken), net als bij de switch. */
    private static final double REACH = 6.0;
    /** Zoveel regels sturen we hooguit van wat er in de buurt staat (de dichtste eerst). */
    private static final int MAX_NEARBY = 48;

    /** Hoe ver een router reikt (blokken). */
    public static double range() {
        return Math.max(1.0, ImtConfig.Network.ROUTER_RANGE);
    }

    /** Hoeveel verbindingen één router kan hebben (hooguit 64: de lijst moet in één bericht naar het scherm passen). */
    public static int capacity() {
        return Math.max(1, Math.min(64, Math.round(ImtConfig.Network.ROUTER_DEVICES)));
    }

    /** Staan deze twee plekken binnen bereik van elkaar? (Van het midden van het ene blok naar het midden van het andere.) */
    public static boolean inRange(BlockPos a, BlockPos b) {
        double range = range();
        return a.distSqr(b) <= range * range;
    }

    // ================================================================ Voor het netwerk (LanGrid)

    /**
     * De draadloze buren van deze plek die nu tellen: van een router de apparaten en routers waar hij mee verbonden is,
     * van een apparaat zijn router; alleen wie binnen bereik staat. Ruimt meteen op wat er niet meer staat (een apparaat
     * dat weggehaald is, een router die weg is): dan komt zijn plek weer vrij.
     */
    public static List<BlockPos> partners(ServerLevel level, BlockPos pos) {
        RouterLinks links = RouterLinks.get(level);
        List<BlockPos> partners = links.partners(pos);
        if (partners.isEmpty()) {
            return partners;
        }
        List<BlockPos> live = new ArrayList<>(partners.size());
        for (BlockPos other : partners) {
            if (level.isLoaded(other) && !stillThere(level, links, other)) {
                continue;
            }
            if (inRange(pos, other)) {
                live.add(other);
            }
        }
        return live;
    }

    /**
     * Staat op deze (geladen) plek nog wat de lijst zegt: een router waar een router hoort, een netwerkblok waar een
     * apparaat hoort? Zo niet, dan halen we het uit de lijst.
     */
    private static boolean stillThere(ServerLevel level, RouterLinks links, BlockPos other) {
        BlockState state = level.getBlockState(other);
        if (links.isRouter(other)) {
            if (state.getBlock() instanceof RouterBlock) {
                return true;
            }
            links.removeRouter(other);
            return false;
        }
        if (state.getBlock() instanceof NetworkConnectable && !(state.getBlock() instanceof RouterBlock)) {
            return true;
        }
        links.forget(other);
        return false;
    }

    // ================================================================ Verbinden

    /**
     * Waarom deze router niet met dit blok kan verbinden (de sleutel van de melding), of null als het kan. De regels:
     * een netwerkblok, binnen bereik, en bij de router (en bij een andere router ook bij die) nog een plek vrij.
     */
    public static @Nullable String whyNot(ServerLevel level, BlockPos router, BlockPos other) {
        RouterLinks links = RouterLinks.get(level);
        if (!links.isRouter(router) || !(level.getBlockState(router).getBlock() instanceof RouterBlock)) {
            return "screen.imttech.router.gone";
        }
        if (router.equals(other) || !level.isLoaded(other)
                || !(level.getBlockState(other).getBlock() instanceof NetworkConnectable)) {
            return "screen.imttech.router.not_a_device";
        }
        if (links.linked(router, other)) {
            return "screen.imttech.router.already";
        }
        if (!inRange(router, other)) {
            return "screen.imttech.router.too_far";
        }
        if (links.count(router) >= capacity()) {
            return "screen.imttech.router.full";
        }
        if (links.isRouter(other) && links.count(other) >= capacity()) {
            return "screen.imttech.router.other_full";
        }
        return null;
    }

    /**
     * Verbindt deze router met dit apparaat (of deze andere router), als dat kan (zie {@link #whyNot}). Een apparaat dat
     * aan een andere router hing, verhuist; alleen de Security Core blijft ook aan zijn andere routers hangen. Je ziet
     * even een rij lichtjes van het apparaat naar de router lopen.
     */
    public static boolean link(ServerLevel level, BlockPos router, BlockPos other) {
        if (whyNot(level, router, other) != null) {
            return false;
        }
        RouterLinks links = RouterLinks.get(level);
        BlockState state = level.getBlockState(other);
        if (state.getBlock() instanceof RouterBlock) {
            links.addRouter(other); // een router die zich nog niet aanmeldde (neergezet met een commando)
        }
        if (!links.link(router, other, state.getBlock() instanceof SecurityCoreBlock)) {
            return false;
        }
        trail(level, other, router);
        level.playSound(null, other, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, 1.8f);
        return true;
    }

    /** Haalt de verbinding tussen deze router en dit apparaat weg. */
    public static boolean unlink(ServerLevel level, BlockPos router, BlockPos other) {
        if (!RouterLinks.get(level).unlink(router, other)) {
            return false;
        }
        level.playSound(null, router, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, 1.2f);
        return true;
    }

    /**
     * Laat zien welke twee er (net) verbonden zijn: een rij lichtjes van {@code from} naar {@code to} die een paar seconden
     * blijft hangen, vonkjes bij allebei, en een signaal dat van de een naar de ander door de lucht vliegt (hetzelfde
     * lichtblauwe vakje als bij een kaart of een knop via een router, zie client/CableTraffic).
     */
    public static void trail(ServerLevel level, BlockPos from, BlockPos to) {
        Vec3 a = Vec3.atCenterOf(from);
        Vec3 b = Vec3.atCenterOf(to);
        double length = a.distanceTo(b);
        int steps = (int) Math.max(2, Math.min(80, Math.round(length / 0.5)));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = a.lerp(b, i / (double) steps);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, a.x, a.y, a.z, 8, 0.2, 0.2, 0.2, 0.05);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, 8, 0.2, 0.2, 0.2, 0.05);
        double range = ImtConfig.Network.SIGNAL_RANGE;
        if (range > 0) {
            ModNetwork.sendNear(level, a.lerp(b, 0.5), range + length / 2,
                    new com.senne.imttech.network.CableTrafficPayload(false, List.of(from.immutable(), to.immutable()), 0));
        }
    }

    // ================================================================ Vanzelf

    /**
     * Hangt dit apparaat al aan een netwerk? Dat is zo als er in zijn net (alles wat je vanaf hier langs kabels,
     * doorgevende blokken en routers bereikt) een router of een kern zit.
     */
    public static boolean hasUplink(ServerLevel level, BlockPos device) {
        for (BlockPos other : LanGrid.networkOf(level, device)) {
            if (other.equals(device)) {
                continue;
            }
            BlockState state = level.getBlockState(other);
            if (state.getBlock() instanceof RouterBlock || state.getBlock() instanceof SecurityCoreBlock) {
                return true;
            }
        }
        return false;
    }

    /**
     * Een router is net neergezet: hangt hij zelf nog nergens aan, dan zoekt hij eerst een andere router (of de kern) in
     * de buurt; daarna pakt hij de losse apparaten binnen zijn bereik, de dichtste eerst, tot hij vol is. Alleen wat
     * {@code by} mag instellen (null = alles: een commando, een test). Geeft hoeveel hij er verbond.
     */
    public static int autoLink(ServerLevel level, BlockPos router, @Nullable ServerPlayer by) {
        RouterLinks links = RouterLinks.get(level);
        if (!links.isRouter(router)) {
            return 0;
        }
        List<BlockPos> near = devicesNear(level, router);
        int linked = 0;
        // Eerst zijn eigen weg naar het netwerk: een kern of een router binnen bereik (de dichtste), als hij geen kabel heeft.
        if (!hasUplink(level, router)) {
            for (BlockPos other : near) {
                BlockState state = level.getBlockState(other);
                boolean up = state.getBlock() instanceof SecurityCoreBlock
                        || state.getBlock() instanceof RouterBlock && links.isRouter(other);
                if (up && mayLink(level, other, by) && link(level, router, other)) {
                    linked++;
                    break;
                }
            }
        }
        for (BlockPos other : near) {
            if (links.count(router) >= capacity()) {
                break;
            }
            BlockState state = level.getBlockState(other);
            // Eerst van wie het is, dan pas of het al een netwerk heeft: dat laatste kost een zoektocht door zijn net.
            if (state.getBlock() instanceof RouterBlock || state.getBlock() instanceof SecurityCoreBlock
                    || links.routerOf(other) != null || !mayLink(level, other, by) || hasUplink(level, other)) {
                continue;
            }
            if (link(level, router, other)) {
                linked++;
            }
        }
        if (by != null && linked > 0) {
            by.displayClientMessage(Component.translatable("message.imttech.router.linked_some", linked,
                    links.count(router), capacity()), true);
        }
        return linked;
    }

    /**
     * Een apparaat is net neergezet: hangt het nog nergens aan, dan gaat het aan de dichtstbijzijnde router binnen bereik
     * die nog een plek heeft en die {@code by} mag instellen (null = elke router). Geeft die router, of null.
     */
    public static @Nullable BlockPos autoJoin(ServerLevel level, BlockPos device, @Nullable ServerPlayer by) {
        BlockState state = level.getBlockState(device);
        if (!(state.getBlock() instanceof NetworkConnectable) || state.getBlock() instanceof RouterBlock
                || state.getBlock() instanceof SecurityCoreBlock) {
            return null;
        }
        RouterLinks links = RouterLinks.get(level);
        if (links.routerOf(device) != null || hasUplink(level, device)) {
            return null;
        }
        List<BlockPos> routers = new ArrayList<>();
        for (BlockPos router : links.routers()) {
            if (inRange(router, device) && level.isLoaded(router) && level.getBlockState(router).getBlock() instanceof RouterBlock) {
                routers.add(router);
            }
        }
        routers.sort(Comparator.comparingDouble(router -> router.distSqr(device)));
        BlockPos full = null;
        for (BlockPos router : routers) {
            if (by != null && !(level.getBlockEntity(router) instanceof RouterBlockEntity entity && BaseTrust.wouldAllow(by, entity))) {
                continue;
            }
            if (links.count(router) >= capacity()) {
                full = full == null ? router : full;
                continue;
            }
            if (link(level, router, device)) {
                if (by != null) {
                    by.displayClientMessage(Component.translatable("message.imttech.router.joined", name(level, router),
                            links.count(router), capacity()), true);
                }
                return router;
            }
        }
        if (by != null && full != null) {
            by.displayClientMessage(Component.translatable("message.imttech.router.full", name(level, full), capacity())
                    .withStyle(ChatFormatting.GOLD), true);
        }
        return null;
    }

    /**
     * Een speler zette een blok neer (zie ImtTech): een netwerkapparaat zoekt zijn router. Een relais dat daarna nog
     * steeds geen netwerk heeft, zegt dat (hoofdstuk 112: een nieuw relais luistert alleen met een netwerk).
     */
    public static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Alleen wat een speler neerzet: iets anders (een RC Car die bouwt) heeft geen rechten op andermans router.
        if (!(event.getEntity() instanceof ServerPlayer by)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof NetworkConnectable) || state.getBlock() instanceof RouterBlock) {
            return;
        }
        BlockPos router = autoJoin(level, pos, by);
        if (router == null && state.getBlock() instanceof RelayBlock && ImtRedstone.offline(level, pos)) {
            by.displayClientMessage(Component.translatable("message.imttech.relay.no_network", Math.round(range()))
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    /**
     * Mag {@code by} dit apparaat aan een router hangen (en ziet hij het dus in het scherm van zijn router)? Null = geen
     * speler (een test, een commando): alles mag. De regel is overal dezelfde: het is van jou, of je bent vertrouwd in de
     * basis waar het staat; iets van niemand mag alleen op vrije grond (niet in iemands basis, niet in een gebouw van deze
     * mod).
     * <ul>
     *   <li>Een apparaat dat onthoudt wie het neerzette (lezer, camera, laserzender, rack, switch...): wie het mag
     *       instellen (BaseTrust.wouldAllow).</li>
     *   <li>Een relais of een torentje: wie het neerzette, of wie vertrouwd is in de basis waar het staat. (Gevonden door
     *       de nalezing: een torentje onthoudt zijn eigenaar op zijn eigen manier, en viel daardoor onder "alles mag": zo
     *       hing je het torentje van een ander aan je router.)</li>
     *   <li>De Security Core: wie daar vertrouwd is.</li>
     *   <li>Een netwerkblok dat we niet kennen (van later): alleen op vrije grond, of als je daar vertrouwd bent.</li>
     * </ul>
     * Dit vraagt het alleen: een apparaat of relais van niemand blijft van niemand. Verbinden gebeurt van een afstand, en
     * dan hoort er niets van eigenaar te wisselen. Het apparaat moet in een geladen stuk wereld staan: hiervoor laden we
     * niets.
     */
    private static boolean mayLink(ServerLevel level, BlockPos device, @Nullable ServerPlayer by) {
        if (by == null) {
            return true;
        }
        if (!level.isLoaded(device)) {
            return false;
        }
        BlockEntity entity = level.getBlockEntity(device);
        if (entity instanceof BaseTrust.Placed placed) {
            return BaseTrust.wouldAllow(by, placed);
        }
        // Tijdens een oefening (hoofdstuk 27) is de trainee een indringer (BaseTrust.wouldAllow kijkt dat hierboven zelf na).
        if (com.senne.imttech.training.Training.isDrillTraineeAt(level, device, by.getUUID())) {
            return false;
        }
        if (BaseTrust.trustedHere(level, device, by)) {
            return true;
        }
        if (level.getBlockState(device).getBlock() instanceof SecurityCoreBlock) {
            return false; // een kern: alleen wie daar vertrouwd is
        }
        java.util.UUID owner = null;
        if (entity instanceof RelayBlockEntity relay) {
            owner = relay.owner();
        } else if (entity instanceof TurretBlockEntity turret) {
            owner = turret.ownerId();
        }
        // Van jou, of van niemand en op vrije grond. (Een relais of torentje van niemand wordt hier niet van jou.)
        return owner != null ? owner.equals(by.getUUID())
                : BaseTrust.baseOwnerAt(level, device) == null && !BaseTrust.inBuilding(level, device);
    }

    // ================================================================ Zoeken wat er in de buurt staat

    /**
     * Alle netwerkblokken binnen bereik van deze plek (in stukken wereld die geladen zijn), de dichtste eerst, zonder de
     * plek zelf. We lopen niet elk blok af: elk stukje wereld (16x16x16) weet welke soorten blokken erin zitten, en zit er
     * geen netwerkblok in, dan slaan we het over (zoals SecurityDevices met de laserzenders doet).
     */
    public static List<BlockPos> devicesNear(ServerLevel level, BlockPos center) {
        int reach = (int) Math.ceil(range());
        List<BlockPos> found = new ArrayList<>();
        int minCx = SectionPos.blockToSectionCoord(center.getX() - reach);
        int maxCx = SectionPos.blockToSectionCoord(center.getX() + reach);
        int minCz = SectionPos.blockToSectionCoord(center.getZ() - reach);
        int maxCz = SectionPos.blockToSectionCoord(center.getZ() + reach);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    if (section.hasOnlyAir() || baseY > center.getY() + reach || baseY + 15 < center.getY() - reach
                            || !section.maybeHas(state -> state.getBlock() instanceof NetworkConnectable)) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (section.getBlockState(x, y, z).getBlock() instanceof NetworkConnectable) {
                                    BlockPos pos = new BlockPos(SectionPos.sectionToBlockCoord(cx) + x, baseY + y,
                                            SectionPos.sectionToBlockCoord(cz) + z);
                                    if (!pos.equals(center) && inRange(center, pos)) {
                                        found.add(pos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(center)).thenComparing(Comparator.naturalOrder()));
        return found;
    }

    // ================================================================ Namen

    /** De naam van de router op deze plek ("Router 1"), of "Router" als hij er geen heeft. */
    public static String name(ServerLevel level, BlockPos router) {
        return level.getBlockEntity(router) instanceof RouterBlockEntity entity && !entity.name().isEmpty()
                ? entity.name() : "Router";
    }

    /** "Router 1", "Router 2", ...: het eerste nummer dat geen geladen router van deze wereld al heeft. */
    static String freeName(ServerLevel level, BlockPos pos) {
        Set<String> used = new HashSet<>();
        for (BlockPos other : RouterLinks.get(level).routers()) {
            if (!other.equals(pos) && level.isLoaded(other) && level.getBlockEntity(other) instanceof RouterBlockEntity entity) {
                used.add(entity.name());
            }
        }
        int number = 1;
        while (used.contains("Router " + number)) {
            number++;
        }
        return "Router " + number;
    }

    /**
     * De eigen naam van het apparaat op deze plek, voor de lijst in het scherm ("" als het er geen heeft): de naam in het
     * blok zelf (een lezer, een switch, een router), of de naam die deze speler zijn relais gaf.
     */
    private static String deviceName(ServerLevel level, BlockPos pos, ServerPlayer viewer) {
        if (level.getBlockEntity(pos) instanceof NamedDevice named) {
            return named.name();
        }
        ImtRelays relays = viewer.getData(ModAttachments.RELAYS);
        Relay relay = relays.at(level.dimension(), pos);
        return relay == null ? "" : relay.name();
    }

    // ================================================================ Het scherm

    /**
     * Rechtsklik op de router: staat de speler dichtbij genoeg en mag hij deze router instellen, dan opent zijn spel het
     * scherm. Een ander krijgt het scherm niet; hij leest van wie de router is. De lijst toont namelijk wat er tot 32
     * blokken ver staat, ook achter muren: zo vond je anders andermans basis. (Gevonden door de nalezing; dezelfde les als
     * bij de zonerand en de kabels.)
     */
    public static void open(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!near(player, pos) || !(level.getBlockEntity(pos) instanceof RouterBlockEntity entity)) {
            return;
        }
        RouterOpenPayload data = screenData(player, pos, true);
        if (data == null) {
            refuse(player, level, entity);
            return;
        }
        ModNetwork.sendTo(player, data);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, 1.5f);
    }

    /**
     * Staat de speler dichtbij genoeg, en is dat stuk wereld geladen? Altijd eerst dit: een plek uit een bericht van een
     * speler mag de server nooit een stuk wereld laten laden. (Gevonden door de nalezing.)
     */
    private static boolean near(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getCenter()) <= REACH * REACH && player.serverLevel().isLoaded(pos);
    }

    /**
     * Wat het scherm van deze router aan deze speler toont: wat eraan hangt, en wat er in de buurt staat. Null als hij het
     * niet mag zien (te ver weg, geen router, of hij mag deze router niet instellen). Onder IN RANGE staat alleen wat de
     * speler zelf aan een router mag hangen: de apparaten van een ander blijven geheim.
     */
    public static @Nullable RouterOpenPayload screenData(ServerPlayer player, BlockPos pos, boolean open) {
        ServerLevel level = player.serverLevel();
        if (!near(player, pos) || !(level.getBlockEntity(pos) instanceof RouterBlockEntity entity)
                || !BaseTrust.mayEdit(player, entity)) {
            return null;
        }
        RouterLinks links = RouterLinks.get(level);
        links.addRouter(pos); // een router van vóór zijn lijst (neergezet met een commando) meldt zich nu aan
        // Eerst opruimen wat er niet meer staat.
        partners(level, pos);
        List<RouterOpenPayload.Entry> linked = new ArrayList<>();
        for (BlockPos other : links.linked(pos)) {
            int status = !level.isLoaded(other) ? RouterOpenPayload.FAR : !inRange(pos, other) ? RouterOpenPayload.OUT_OF_RANGE
                    : RouterOpenPayload.OK;
            linked.add(entry(level, pos, other, player, status));
        }
        List<RouterOpenPayload.Entry> nearby = new ArrayList<>();
        for (BlockPos other : devicesNear(level, pos)) {
            if (nearby.size() >= MAX_NEARBY) {
                break;
            }
            if (links.linked(pos, other) || !mayLink(level, other, player)) {
                continue;
            }
            nearby.add(entry(level, pos, other, player, nearbyStatus(level, links, pos, other)));
        }
        boolean online = level.getBlockState(pos).getValue(RouterBlock.ONLINE);
        boolean core = LanGrid.coreOf(level, pos) != null;
        return new RouterOpenPayload(pos.immutable(), entity.name(), online, core, capacity(), (int) Math.round(range()), open,
                linked, nearby);
    }

    /**
     * Hoe een apparaat in de buurt ervoor staat: los, aan een andere router, al aan een netwerk (een kabel), of al in het
     * netwerk van deze router zelf.
     */
    private static int nearbyStatus(ServerLevel level, RouterLinks links, BlockPos router, BlockPos other) {
        // Zit het al in het netwerk van deze router (met een kabel, of via een andere router)? Dan hoeft verbinden niet:
        // dat kost alleen een plek. (Gezien op de foto: de kern stond er met een knop Link, terwijl de router er met een
        // kabel aan hing.)
        if (LanGrid.networkOf(level, router).contains(other)) {
            return RouterOpenPayload.SAME_NET;
        }
        if (level.getBlockState(other).getBlock() instanceof SecurityCoreBlock) {
            return RouterOpenPayload.FREE; // de kern mag aan meer routers tegelijk hangen
        }
        if (links.routerOf(other) != null) {
            return RouterOpenPayload.OTHER_ROUTER;
        }
        return wired(level, router, other) ? RouterOpenPayload.WIRED : RouterOpenPayload.FREE;
    }

    private static RouterOpenPayload.Entry entry(ServerLevel level, BlockPos router, BlockPos other, ServerPlayer viewer, int status) {
        String block = level.isLoaded(other) ? level.getBlockState(other).getBlock().getDescriptionId() : "";
        String name = level.isLoaded(other) ? deviceName(level, other, viewer) : "";
        return new RouterOpenPayload.Entry(other, block, name, (int) Math.round(Math.sqrt(other.distSqr(router))), status);
    }

    /**
     * Een knop uit het scherm: verbinden, losmaken, aanwijzen (lichtjes) of gewoon verversen. Alleen voor wie de router mag
     * instellen: een ander krijgt ook de lijst niet (zie {@link #open}).
     */
    public static void handleAction(ServerPlayer player, RouterActionPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        if (!near(player, pos) || !(level.getBlockEntity(pos) instanceof RouterBlockEntity entity)) {
            return;
        }
        if (!BaseTrust.mayEdit(player, entity)) {
            if (payload.op() != RouterActionPayload.REFRESH) {
                refuse(player, level, entity);
            }
            return;
        }
        BlockPos other = payload.target();
        switch (payload.op()) {
            case RouterActionPayload.LINK -> {
                if (spend(player, 1)) {
                    // Eerst of het kan (een netwerkblok, geladen, in bereik, een plek vrij), dan pas van wie het is.
                    String why = whyNot(level, pos, other);
                    if (why != null) {
                        player.displayClientMessage(Component.translatable(why, capacity(), Math.round(range()))
                                .withStyle(ChatFormatting.RED), true);
                    } else if (!mayLink(level, other, player)) {
                        player.displayClientMessage(Component.translatable("screen.imttech.router.not_yours")
                                .withStyle(ChatFormatting.RED), true);
                    } else {
                        link(level, pos, other);
                    }
                }
            }
            case RouterActionPayload.UNLINK -> {
                if (spend(player, 1)) {
                    unlink(level, pos, other);
                }
            }
            case RouterActionPayload.PING -> {
                // Aanwijzen: alleen wat aan deze router hangt, of wat je er zelf aan mag hangen (zo wijst het scherm nooit
                // het apparaat van een ander aan).
                if (inRange(pos, other) && level.isLoaded(other)
                        && (RouterLinks.get(level).linked(pos, other) || mayLink(level, other, player)) && spend(player, 4)) {
                    trail(level, other, pos);
                }
            }
            default -> { }
        }
        RouterOpenPayload data = screenData(player, pos, false);
        if (data != null) {
            ModNetwork.sendTo(player, data);
        }
    }

    /** Je mag deze router niet instellen: zeg van wie hij is. */
    private static void refuse(ServerPlayer player, ServerLevel level, RouterBlockEntity entity) {
        String owner = BaseTrust.belongsTo(level, entity);
        player.displayClientMessage((owner.isEmpty() ? Component.translatable("screen.imttech.router.locked")
                : Component.translatable("screen.imttech.router.not_your_router", owner)).withStyle(ChatFormatting.RED), true);
    }

    // ================================================================ Zuinig met zoekwerk

    /** Zo lang (ticks) onthoudt een router welke apparaten in zijn buurt al een netwerk hebben, als er niets verandert. */
    private static final int WIRED_TICKS = 100;

    /** Wat een router weet van de apparaten in zijn buurt: bij welke stand van het netwerk, wanneer, en per apparaat het antwoord. */
    private record Wired(int version, long gameTime, Map<BlockPos, Boolean> answers) {}

    private static final Map<GlobalPos, Wired> WIRED = new HashMap<>();

    /**
     * Heeft dit apparaat in de buurt van deze router al een netwerk (zie {@link #hasUplink})? Voor het scherm, dat elke
     * seconde ververst: het antwoord kost een zoektocht door het hele net van dat apparaat, dus we onthouden het tot het
     * netwerk verandert (of hooguit vijf seconden). (Gevonden door de nalezing: het scherm zocht voor elk apparaat in de
     * buurt steeds opnieuw.)
     */
    private static boolean wired(ServerLevel level, BlockPos router, BlockPos device) {
        GlobalPos key = GlobalPos.of(level.dimension(), router.immutable());
        long now = level.getGameTime();
        Wired known = WIRED.get(key);
        if (known == null || known.version() != LanGrid.version() || now - known.gameTime() >= WIRED_TICKS || now < known.gameTime()) {
            if (WIRED.size() > 256) {
                WIRED.clear(); // routers waar al lang niemand meer naar keek
            }
            known = new Wired(LanGrid.version(), now, new HashMap<>());
            WIRED.put(key, known);
        }
        Boolean answer = known.answers().get(device);
        if (answer == null) {
            answer = hasUplink(level, device);
            known.answers().put(device.immutable(), answer);
        }
        return answer;
    }

    /** Zoveel beurten heeft een speler hooguit opgespaard voor de knoppen van het routerscherm. */
    private static final float TURNS = 8.0f;
    /** Zoveel beurten komen er per tick bij. */
    private static final float TURNS_PER_TICK = 0.5f;

    /** Hoeveel beurten een speler nog heeft, en op welke tick van de server we dat voor het laatst uitrekenden. */
    private record Turns(float left, int tick) {}

    private static final Map<ServerPlayer, Turns> TURNS_OF = new WeakHashMap<>();

    /**
     * Mag deze speler nu nog een knop van het routerscherm gebruiken? Elke speler spaart beurten op (hooguit acht, elke
     * tick een halve erbij). Verbinden of losmaken kost er één (het hele netwerk zoekt daarna opnieuw), aanwijzen vier (een
     * rij lichtjes voor iedereen in de buurt). Wie gewoon klikt, merkt hier niets van; een spel dat honderd berichten per
     * seconde stuurt, wel.
     */
    private static boolean spend(ServerPlayer player, float cost) {
        int now = player.server.getTickCount();
        Turns turns = TURNS_OF.get(player);
        float left = turns == null ? TURNS : Math.min(TURNS, turns.left() + Math.max(0, now - turns.tick()) * TURNS_PER_TICK);
        boolean enough = left >= cost;
        TURNS_OF.put(player, new Turns(enough ? left - cost : left, now));
        return enough;
    }

    /** Bij het afsluiten van de wereld: alles vergeten (zie LanGrid.clear). Ook voor de tests. */
    public static void clear() {
        WIRED.clear();
        TURNS_OF.clear();
    }
}
