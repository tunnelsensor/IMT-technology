package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.drone.EdithDrones;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.edith.EdithAction;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithOwner;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.edith.EdithThreats;
import com.senne.starktech.nav.EdithNavigation;
import com.senne.starktech.nav.EdithWaypoints;
import com.senne.starktech.nav.Waypoint;
import com.senne.starktech.network.DroneControlPayload;
import com.senne.starktech.network.WaypointActionPayload;
import com.senne.starktech.registry.ModArmorMaterials;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.satellite.SatelliteMode;
import com.senne.starktech.satellite.SatelliteStrikes;
import com.senne.starktech.shield.LaserEmitterBlock;
import com.senne.starktech.shield.LaserWallBlock;
import com.senne.starktech.shield.ShieldColor;
import com.senne.starktech.spy.EdithSpy;
import com.senne.starktech.transport.EdithTransport;
import com.senne.starktech.client.music.MusicQuery;
import com.senne.starktech.client.voice.VoiceAction;
import com.senne.starktech.client.voice.VoiceDictionary;
import com.senne.starktech.client.voice.VoiceMatcher;
import com.senne.starktech.station.ChargerBlockEntity;
import com.senne.starktech.station.HeatGeneratorBlockEntity;
import com.senne.starktech.station.ChargerMenu;
import com.senne.starktech.station.ChargingStationBlockEntity;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Automatische tests. Ze draaien met "gradlew runGameTestServer", of in het spel met /test runall.
 *
 * In Minecraft 1.21.1 meld je een test aan met @GameTest boven een methode. "template" is het
 * stukje wereld waarin de test draait (een structuur-bestand), "batch" bepaalt welke tests
 * tegelijk lopen: tests met dezelfde batch draaien samen, verschillende batches na elkaar.
 * Dat is belangrijk, want de explosies van de satelliettest mogen de drones van een andere
 * test niet raken.
 *
 * - stark_tower: bouwt de Stark Tower na, controleert de kist en kijkt of het laadstation
 *   een bril echt oplaadt en een upgrade inbouwt.
 * - drone_swarm: een zwerm drones schakelt een vijand uit.
 * - satellite_strike: de satelliet wacht eerst en slaat dan in.
 * - navigation: waypoints opslaan, laden, navigeren en aankomen.
 * - scout_drone: de verkenningsdrone vliegt zoals de speler hem bestuurt.
 * - spy_drone: de spionagedrone volgt zijn doelwit, ook als dat wegloopt.
 * - voice_dictionary: de spraakcommando's worden herkend (ook met een invulplek in de zin).
 */
@GameTestHolder(StarkTech.MODID)
@PrefixGameTestTemplate(false)
public final class StarkTechGameTests {
    private StarkTechGameTests() {}

    // Posities in de Stark Tower (zie tools/generate_structure.py). In een gametest ligt de
    // structuur één blok boven het "structuurblok", dus tellen we bij y altijd 1 op.
    // Plekken in de Stark Tower (zie tools/generate_structure.py; in de test ligt alles
    // één blok hoger dan in het bouwscript, want de structuur staat op de testvloer).
    private static final BlockPos STATION_POS = new BlockPos(20, 2, 5);
    private static final BlockPos CHEST_POS = new BlockPos(5, 2, 32);
    /** De kluiskist bovenin, met de E.D.I.T.H.-bril erin. */
    private static final BlockPos VAULT_CHEST_POS = new BlockPos(20, 81, 20);

    /** De lege test-arena (zie tools/generate_test_structure.py). */
    private static final String ARENA = "empty";

    // ================================================================ De tests

    @GameTest(template = "stark_tower", timeoutTicks = 200, batch = "station")
    public static void stark_tower(GameTestHelper helper) {
        towerAndStation(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 2400, batch = "drones")
    public static void drone_swarm(GameTestHelper helper) {
        droneSwarm(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 2400, batch = "scout")
    public static void scout_drone(GameTestHelper helper) {
        scoutDrone(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 2400, batch = "spy")
    public static void spy_drone(GameTestHelper helper) {
        spyDrone(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 2400, batch = "transport")
    public static void transport_ride(GameTestHelper helper) {
        transportRide(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 2400, batch = "transport")
    public static void transport_landing(GameTestHelper helper) {
        transportLanding(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 300, batch = "satellite")
    public static void satellite_strike(GameTestHelper helper) {
        satelliteStrike(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = "navigation")
    public static void navigation(GameTestHelper helper) {
        navigationTest(helper);
    }

    /**
     * Blokken met een block entity zijn standaard onzichtbaar. Deze test bewaakt dat het
     * laadstation zijn model laat zien, zodat het niet als een onzichtbare muur in je wereld staat.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "voice")
    public static void charging_station_is_visible(GameTestHelper helper) {
        BlockState state = ModBlocks.CHARGING_STATION.get().defaultBlockState();
        if (state.getRenderShape() != RenderShape.MODEL) {
            throw new GameTestAssertException("Het laadstation wordt niet getekend: " + state.getRenderShape());
        }
        helper.succeed();
    }

    /**
     * De bril is een stuk armor, en armor heeft een materiaal nodig dat ook echt in het spel
     * geregistreerd is. Was dat niet zo, dan crashte het spel zodra iets de eigenschappen van
     * de bril opvroeg - bijvoorbeeld bij het tonen van de tooltip in de creative-tab.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "voice")
    public static void glasses_armor_material(GameTestHelper helper) {
        if (!ModArmorMaterials.EDITH.isBound()) {
            throw new GameTestAssertException("Het materiaal starktech:edith is niet geregistreerd.");
        }
        ItemStack glasses = new ItemStack(ModItems.EDITH_GLASSES.get());
        // Dit is precies wat de tooltip doet. Een niet-geregistreerd materiaal knalt hier.
        glasses.forEachModifier(EquipmentSlot.HEAD, (attribute, modifier) -> {});
        helper.succeed();
    }

    /**
     * Het woordenboek van de spraakbesturing: herkent E.D.I.T.H. de zinnen die erin staan,
     * en haalt ze de juiste waarde uit een zin met een invulplek?
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "voice")
    public static void voice_dictionary(GameTestHelper helper) {
        VoiceDictionary dictionary;
        try {
            dictionary = VoiceDictionary.loadDefault();
        } catch (java.io.IOException e) {
            throw new GameTestAssertException("Woordenboek kan niet geladen worden: " + e.getMessage());
        }
        VoiceMatcher matcher = new VoiceMatcher(dictionary);

        // Zin -> verwachte actie, en (optioneel) wat er ingevuld moet worden.
        checkVoice(matcher, "send the drones", VoiceAction.LAUNCH_DRONES, null);
        checkVoice(matcher, "stand down", VoiceAction.STAND_DOWN, null);
        checkVoice(matcher, "spy on him", VoiceAction.SPY_DRONE, null);
        checkVoice(matcher, "pick me up", VoiceAction.TRANSPORT_RIDE, null);
        checkVoice(matcher, "navigate to the diamond mine", VoiceAction.NAVIGATE_TO, "diamond mine");
        // Invulplek in het MIDDEN van de zin (dit kon eerst niet).
        checkVoice(matcher, "turn on the front door", VoiceAction.REDSTONE_ON, "front door");
        checkVoice(matcher, "turn off the front door", VoiceAction.REDSTONE_OFF, "front door");
        checkVoice(matcher, "pulse the trap", VoiceAction.REDSTONE_PULSE, "trap");
        // Muziek. "turn on the music" leek vroeger op "turn on {thing}" en probeerde dus een
        // redstone-relais met de naam "music" aan te zetten.
        checkVoice(matcher, "pause", VoiceAction.MUSIC_PAUSE, null);
        checkVoice(matcher, "turn on the music", VoiceAction.MUSIC_RESUME, null);
        checkVoice(matcher, "resume the music", VoiceAction.MUSIC_RESUME, null);
        checkVoice(matcher, "next song", VoiceAction.MUSIC_NEXT, null);
        checkVoice(matcher, "what song is this", VoiceAction.MUSIC_WHAT, null);
        checkVoice(matcher, "play the song thunderstruck", VoiceAction.MUSIC_PLAY, "thunderstruck");
        // Hier hoort "something by queen" uit te komen; MusicQuery maakt daar artist:"queen"
        // van (zie de test music_query).
        checkVoice(matcher, "play something by queen", VoiceAction.MUSIC_PLAY, "something by queen");
        helper.succeed();
    }

    /**
     * Wat we van jouw zin maken voor Spotify. Met track:/artist: vindt Spotify het echte
     * nummer; zonder die filters kreeg je zomaar een cover met dezelfde woorden in de titel.
     */
    /** Waypoints kun je niet alleen weggooien, maar ook een andere naam en kleur geven. */
    /**
     * Een rayshield klapt niet in één keer aan: hij schuift dicht vanaf de uiteinden naar het
     * midden, in de kleur die de zender heeft.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "voice")
    public static void rayshield_slides_shut(GameTestHelper helper) {
        BlockPos emitter = new BlockPos(1, 3, 1);
        // Twee zenders tegenover elkaar, met ruimte ertussen.
        helper.setBlock(emitter, ModBlocks.LASER_EMITTER.get().defaultBlockState()
                .setValue(LaserEmitterBlock.FACING, Direction.EAST)
                .setValue(LaserEmitterBlock.COLOR, ShieldColor.GREEN));
        BlockPos far = emitter.east(5);
        helper.setBlock(far, ModBlocks.LASER_EMITTER.get().defaultBlockState()
                .setValue(LaserEmitterBlock.FACING, Direction.WEST));
        BlockPos abs = helper.absolutePos(emitter);
        ((LaserEmitterBlock) ModBlocks.LASER_EMITTER.get())
                .setSwitched(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), true);

        // Meteen daarna mag hij nog niet dicht zijn: hij hoort te schuiven.
        int immediately = countWall(helper, emitter, 4);
        if (immediately == 4) {
            throw new GameTestAssertException("Het veld stond meteen helemaal dicht (geen animatie)");
        }
        helper.succeedWhen(() -> {
            if (countWall(helper, emitter, 4) < 4) {
                throw new GameTestAssertException("Het veld is nog niet dicht");
            }
            BlockState wall = helper.getBlockState(emitter.east(1));
            if (wall.getValue(LaserWallBlock.COLOR) != ShieldColor.GREEN) {
                throw new GameTestAssertException("Verkeerde kleur: " + wall.getValue(LaserWallBlock.COLOR));
            }
        });
    }

    /** Hoeveel van de plekken voor de zender zijn al laserwand? */
    private static int countWall(GameTestHelper helper, BlockPos emitter, int length) {
        int count = 0;
        for (int i = 1; i <= length; i++) {
            if (helper.getBlockState(emitter.east(i)).is(ModBlocks.LASER_WALL.get())) {
                count++;
            }
        }
        return count;
    }

    /**
     * De dreigingsscan markeert alleen wat jou echt aanvalt. Een zombie die daar wat staat
     * hoort er niet bij; een zombie die op jou jaagt wel.
     */
    /**
     * De warmtegenerator laadt je bril op met brandstof, en sneller als er hitte omheen staat.
     * (Het zonnepaneel is lastiger te testen: dat hangt van de stand van de zon af.)
     */
    /**
     * Het scherm van een lader moet meelopen terwijl het openstaat: je hoeft het niet te
     * sluiten en opnieuw te openen om te zien hoeveel erin zit.
     *
     * Minecraft stuurt die getallen alleen door als ze in de "remote" kopie van het menu
     * veranderen. Precies dat controleren we hier.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "voice")
    public static void charger_screen_updates_live(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 3, 3);
        helper.setBlock(pos, ModBlocks.HEAT_GENERATOR.get());
        if (!(helper.getBlockEntity(pos) instanceof HeatGeneratorBlockEntity generator)) {
            throw new GameTestAssertException("De warmtegenerator heeft geen block entity");
        }
        generator.setItem(ChargerBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 8));

        ServerPlayer player = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        player.openMenu(generator);
        if (!(player.containerMenu instanceof ChargerMenu menu)) {
            throw new GameTestAssertException("Het scherm van de lader ging niet open");
        }
        // Even laten draaien, zodat er energie in komt.
        for (int i = 0; i < 20; i++) {
            generator.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos));
        }
        menu.broadcastChanges();
        int remote = remoteDataSlot(menu, ChargerBlockEntity.DATA_ENERGY);
        if (remote != menu.getEnergy() || remote <= 0) {
            throw new GameTestAssertException("Jouw kant kreeg " + remote
                    + " door terwijl het apparaat op " + menu.getEnergy() + " staat");
        }
        // En de teller voor "hoeveel al geproduceerd" loopt ook mee.
        if (menu.getTotalProduced() <= 0) {
            throw new GameTestAssertException("De productieteller staat nog op nul");
        }
        player.closeContainer();
        helper.succeed();
    }

    /** Wat er volgens het menu al naar de speler is gestuurd (de "remote" kopie). */
    private static int remoteDataSlot(AbstractContainerMenu menu, int index) {
        try {
            java.lang.reflect.Field field = AbstractContainerMenu.class.getDeclaredField("remoteDataSlots");
            field.setAccessible(true);
            return ((it.unimi.dsi.fastutil.ints.IntList) field.get(menu)).getInt(index);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Kan de verstuurde waarden niet lezen: " + e);
        }
    }

    @GameTest(template = ARENA, timeoutTicks = 400, batch = "voice")
    public static void heat_generator_charges(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 3, 2);
        helper.setBlock(pos, ModBlocks.HEAT_GENERATOR.get());
        if (!(helper.getBlockEntity(pos) instanceof HeatGeneratorBlockEntity generator)) {
            throw new GameTestAssertException("De warmtegenerator heeft geen block entity");
        }
        // Een lege bril en een stuk kolen erin.
        ItemStack glasses = new ItemStack(ModItems.EDITH_GLASSES.get());
        EdithData.setEnergy(glasses, 0);
        generator.setItem(ChargerBlockEntity.SLOT_GLASSES, glasses);
        generator.setItem(ChargerBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
        helper.succeedWhen(() -> {
            generator.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos));
            ItemStack inside = generator.getItem(ChargerBlockEntity.SLOT_GLASSES);
            if (EdithData.getEnergy(inside) <= 0) {
                throw new GameTestAssertException("De bril wordt niet opgeladen");
            }
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = "voice")
    public static void threats_only_attackers(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        Husk bystander = spawnDummyEnemy(helper, new BlockPos(5, 3, 1));
        Husk hunter = spawnDummyEnemy(helper, new BlockPos(7, 3, 1));

        if (!EdithThreats.of(owner, 32).isEmpty()) {
            throw new GameTestAssertException("Er staat niets te doen, maar er is wel een dreiging gemeld");
        }
        // Eentje kiest jou als doelwit: die hoort er nu wel bij.
        hunter.setTarget(owner);
        List<LivingEntity> threats = EdithThreats.of(owner, 32);
        if (threats.size() != 1 || threats.getFirst() != hunter) {
            throw new GameTestAssertException("Verwacht alleen de aanvaller, kreeg: " + threats);
        }
        if (threats.contains(bystander)) {
            throw new GameTestAssertException("De omstander werd ook gemarkeerd");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = "voice")
    public static void waypoint_edit(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        EdithNavigation.handleWaypointAction(owner,
                new WaypointActionPayload(WaypointActionPayload.Op.ADD, 0, "Mijn plek", Waypoint.COLORS[0]));
        if (owner.getData(ModAttachments.WAYPOINTS).waypoints().isEmpty()) {
            throw new GameTestAssertException("Het waypoint werd niet opgeslagen");
        }
        // Naam en kleur wijzigen; de plek moet blijven waar hij was.
        BlockPos where = owner.getData(ModAttachments.WAYPOINTS).waypoints().getFirst().pos();
        EdithNavigation.handleWaypointAction(owner,
                new WaypointActionPayload(WaypointActionPayload.Op.RENAME, 0, "Basis", Waypoint.COLORS[3]));
        Waypoint changed = owner.getData(ModAttachments.WAYPOINTS).waypoints().getFirst();
        if (!changed.name().equals("Basis")) {
            throw new GameTestAssertException("Naam werd niet gewijzigd: " + changed.name());
        }
        if (changed.color() != Waypoint.COLORS[3]) {
            throw new GameTestAssertException("Kleur werd niet gewijzigd");
        }
        if (!changed.pos().equals(where)) {
            throw new GameTestAssertException("De plek verschoof: " + changed.pos());
        }
        EdithNavigation.handleWaypointAction(owner,
                new WaypointActionPayload(WaypointActionPayload.Op.REMOVE, 0, "", 0));
        if (!owner.getData(ModAttachments.WAYPOINTS).waypoints().isEmpty()) {
            throw new GameTestAssertException("Het waypoint werd niet weggegooid");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = "voice")
    public static void relay_forgotten_when_broken(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        BlockPos relayPos = new BlockPos(3, 2, 3);
        helper.setBlock(relayPos, ModBlocks.RELAY.get());
        EdithRedstone.register(owner, helper.absolutePos(relayPos));
        if (owner.getData(ModAttachments.RELAYS).relays().isEmpty()) {
            throw new GameTestAssertException("Het relais kwam niet in de lijst van de bril");
        }
        // Slopen: dan hoort hij ook uit het redstone-scherm te verdwijnen.
        helper.setBlock(relayPos, Blocks.AIR);
        if (!owner.getData(ModAttachments.RELAYS).relays().isEmpty()) {
            throw new GameTestAssertException("Het gesloopte relais staat nog in de lijst");
        }

        // En het opruimen achteraf, voor relais die verdwenen terwijl je uitgelogd was
        // (of die al in je lijst stonden van voor deze opruiming).
        EdithRedstone.register(owner, helper.absolutePos(new BlockPos(4, 2, 4))); // daar staat niets
        if (owner.getData(ModAttachments.RELAYS).relays().isEmpty()) {
            throw new GameTestAssertException("Het spook-relais kwam niet in de lijst");
        }
        EdithRedstone.pruneGhosts(owner);
        if (!owner.getData(ModAttachments.RELAYS).relays().isEmpty()) {
            throw new GameTestAssertException("Een relais zonder kastje bleef in de lijst staan");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = "voice")
    public static void music_query(GameTestHelper helper) {
        checkQuery("thunderstruck by ac dc", "track:\"thunderstruck\" artist:\"ac dc\"");
        checkQuery("thunderstruck from acdc", "track:\"thunderstruck\" artist:\"acdc\"");
        checkQuery("the song thunderstruck", "thunderstruck");
        checkQuery("something by queen", "artist:\"queen\"");
        checkQuery("some music by queen", "artist:\"queen\"");
        checkQuery("thunderstruck", "thunderstruck");
        checkQuery("music", "");
        // De terugval als track:/artist: niets oplevert ("man of steel" is gewoon een titel).
        if (!MusicQuery.plain("man of steel by anthem").equals("man of steel anthem")) {
            throw new GameTestAssertException("Terugval klopt niet: " + MusicQuery.plain("man of steel by anthem"));
        }
        helper.succeed();
    }

    private static void checkQuery(String spoken, String expected) {
        String got = MusicQuery.build(spoken);
        if (!got.equals(expected)) {
            throw new GameTestAssertException("\"" + spoken + "\" werd \"" + got + "\" in plaats van \"" + expected + "\"");
        }
    }

    /** Controleert of een zin het juiste commando (en de juiste waarde) oplevert. */
    private static void checkVoice(VoiceMatcher matcher, String heard, VoiceAction expected, @Nullable String value) {
        VoiceMatcher.Match match = matcher.best(heard);
        if (match == null) {
            throw new GameTestAssertException("Niet herkend: \"" + heard + "\"");
        }
        boolean found = match.command().actions.stream().anyMatch(ref -> ref.action() == expected);
        if (!found) {
            throw new GameTestAssertException("\"" + heard + "\" werd " + match.command().name
                    + " in plaats van " + expected);
        }
        if (value != null && !match.captured().equalsIgnoreCase(value)) {
            throw new GameTestAssertException("\"" + heard + "\" vulde in: \"" + match.captured()
                    + "\" in plaats van \"" + value + "\"");
        }
    }

    // ================================================================ Hulpjes

    /** Een nep-speler met een opgeladen bril op, die eigenaar is van die bril. */
    @SuppressWarnings("removal")
    private static ServerPlayer makeEdithOwner(GameTestHelper helper, BlockPos relativePos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(relativePos));
        // Een nep-speler houdt de chunks rond zich niet "levend". Zonder dit zouden drones
        // die buiten de chunk van de test vliegen stilvallen.
        int chunkX = SectionPos.blockToSectionCoord(pos.x);
        int chunkZ = SectionPos.blockToSectionCoord(pos.z);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                helper.getLevel().setChunkForced(chunkX + dx, chunkZ + dz, true);
                helper.getLevel().getChunk(chunkX + dx, chunkZ + dz); // meteen inladen
            }
        }
        player.moveTo(pos.x, pos.y, pos.z);
        player.setNoGravity(true);
        ItemStack glasses = new ItemStack(ModItems.EDITH_GLASSES.get());
        EdithData.setEnergy(glasses, EdithData.getMaxEnergy(glasses));
        EdithData.setOwner(glasses, new EdithOwner(player.getUUID(), player.getGameProfile().getName(), false));
        player.setItemSlot(EquipmentSlot.HEAD, glasses);
        return player;
    }

    /** Een vijand die stil blijft staan (geen AI) en niet in de zon verbrandt. */
    private static Husk spawnDummyEnemy(GameTestHelper helper, BlockPos relativePos) {
        Husk husk = helper.spawn(EntityType.HUSK, relativePos);
        husk.setNoAi(true);
        husk.setNoGravity(true);
        return husk;
    }

    /**
     * Voert iets uit zodra alle posities in een actieve ("entity ticking") chunk liggen.
     * In de testwereld kan het even duren voordat geforceerde chunks actief worden.
     */
    private static void whenEntityTicking(GameTestHelper helper, List<BlockPos> positions, Runnable action) {
        if (positions.stream().allMatch(helper.getLevel()::isPositionEntityTicking)) {
            action.run();
        } else {
            helper.runAfterDelay(5, () -> whenEntityTicking(helper, positions, action));
        }
    }

    /**
     * Navigatie: een plek opslaan, ernaartoe navigeren, en bij aankomst stopt de navigatie.
     * Controleert ook dat de waypoints goed opgeslagen en weer ingelezen worden.
     */
    private static void navigationTest(GameTestHelper helper) {
        ServerPlayer player = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        EdithNavigation.handleWaypointAction(player,
                new WaypointActionPayload(WaypointActionPayload.Op.ADD, 0, "  Thuis\u00a7c  ", Waypoint.COLORS[2]));
        EdithWaypoints data = player.getData(ModAttachments.WAYPOINTS);
        if (data.waypoints().size() != 1 || !data.waypoints().getFirst().name().equals("Thuis")) {
            throw new GameTestAssertException("Waypoint niet (goed) opgeslagen: " + data);
        }

        // Opslaan en weer inlezen (zoals bij het afsluiten en opnieuw laden van de wereld).
        var saved = EdithWaypoints.CODEC.codec().encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        EdithWaypoints loaded = EdithWaypoints.CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
        if (!loaded.equals(data)) {
            throw new GameTestAssertException("Waypoints anders na opslaan: " + loaded);
        }

        EdithNavigation.handleWaypointAction(player, new WaypointActionPayload(WaypointActionPayload.Op.NAVIGATE, 0, "", 0));
        if (player.getData(ModAttachments.WAYPOINTS).active() != 0) {
            throw new GameTestAssertException("Navigatie niet gestart");
        }
        // De speler staat al op de plek, dus de volgende controle moet "aangekomen" zeggen.
        EdithNavigation.tick(player);
        if (player.getData(ModAttachments.WAYPOINTS).active() != EdithWaypoints.NONE) {
            throw new GameTestAssertException("Aankomst niet herkend");
        }

        EdithNavigation.handleWaypointAction(player, new WaypointActionPayload(WaypointActionPayload.Op.REMOVE, 0, "", 0));
        if (!player.getData(ModAttachments.WAYPOINTS).waypoints().isEmpty()) {
            throw new GameTestAssertException("Waypoint niet verwijderd");
        }
        helper.succeed();
    }

    /**
     * De spionagedrone: sturen, laten volgen terwijl het doelwit zich verplaatst, en kijken
     * of het logboek bijgehouden wordt.
     */
    private static void spyDrone(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        Husk target = spawnDummyEnemy(helper, new BlockPos(3, 3, 1));
        AtomicBoolean started = new AtomicBoolean();
        whenEntityTicking(helper, List.of(owner.blockPosition(), target.blockPosition().east(12)), () -> {
            if (started.getAndSet(true)) {
                return;
            }
            // De speler kijkt niet naar het doelwit, dus markeren we het eerst: de spion pakt dan
            // het eerste gemarkeerde doelwit.
            EdithServer.handleMark(owner, target.getId());
            int marked = EdithServer.getMarkedTargets(owner).size();
            EdithServer.handleAction(owner, EdithAction.SPY_DRONE.ordinal());
            if (EdithSpy.of(owner) == null) {
                throw new GameTestAssertException("Geen spionage-opdracht gestart (gemarkeerd: " + marked
                        + ", energie: " + EdithData.getEnergy(EdithData.getWornGlasses(owner))
                        + ", drones: " + EdithDrones.find(owner, StarkDroneEntity.Mode.SPY).size() + ")");
            }
            if (EdithSpy.of(owner).log().isEmpty()) {
                throw new GameTestAssertException("Logboek blijft leeg");
            }
            if (EdithDrones.find(owner, StarkDroneEntity.Mode.SPY).isEmpty()) {
                throw new GameTestAssertException("Geen spionagedrone in de lucht");
            }
            // Het doelwit verplaatst zich: de drone moet erachteraan.
            target.moveTo(helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(14, 3, 1))));
        });
        helper.succeedWhen(() -> {
            List<StarkDroneEntity> spies = EdithDrones.find(owner, StarkDroneEntity.Mode.SPY);
            if (spies.isEmpty()) {
                throw new GameTestAssertException("Spionagedrone verdwenen");
            }
            float distance = spies.getFirst().distanceTo(target);
            if (distance > 10) {
                throw new GameTestAssertException("Spion volgt zijn doelwit niet: " + distance);
            }
        });
    }

    /** De verkenningsdrone vliegt de kant op die de speler hem "stuurt", en stopt weer netjes. */
    private static void scoutDrone(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        owner.setYRot(-90); // naar het oosten kijken (+X)
        // Let op: de testomgeving kan een ingeplande taak soms twee keer uitvoeren. De drone-camera
        // is een aan/uit-knop, dus we zorgen er zelf voor dat we hem maar één keer aanzetten.
        AtomicBoolean launched = new AtomicBoolean();
        whenEntityTicking(helper, List.of(owner.blockPosition(), owner.blockPosition().east(12)), () -> {
            if (launched.getAndSet(true)) {
                return;
            }
            EdithServer.handleAction(owner, EdithAction.DRONE_CAMERA.ordinal());
            StarkDroneEntity scout = EdithDrones.findScout(owner);
            if (scout == null) {
                throw new GameTestAssertException("Geen verkenningsdrone gelanceerd");
            }
            UUID scoutId = scout.getUUID();
            double startX = scout.getX();
            helper.succeedWhen(() -> {
                // Elke tick "vooruit" sturen (naar het oosten), zoals het spel van de speler zou doen.
                EdithServer.handleDroneControl(owner, new DroneControlPayload(1, 0, 0, false, -90, 0));
                if (!(helper.getLevel().getEntity(scoutId) instanceof StarkDroneEntity drone)) {
                    throw new GameTestAssertException("Verkenningsdrone verdwenen");
                }
                if (drone.getX() - startX < 5) {
                    throw new GameTestAssertException("Drone vloog niet vooruit: " + (drone.getX() - startX));
                }
            });
        });
    }

    /**
     * Zelf vliegen: een drone pakt je op, en hij vliegt de kant op die jij stuurt.
     * Dit test de serverkant; jouw spel hoort elke tick de besturing door te sturen.
     */
    private static void transportRide(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        owner.setYRot(-90); // naar het oosten kijken (+X)
        AtomicBoolean started = new AtomicBoolean();
        whenEntityTicking(helper, List.of(owner.blockPosition(), owner.blockPosition().east(12)), () -> {
            if (started.getAndSet(true)) {
                return;
            }
            EdithTransport.startRide(owner, EdithData.getWornGlasses(owner), false);
            StarkDroneEntity carrier = EdithTransport.carrierOf(owner);
            if (carrier == null) {
                throw new GameTestAssertException("Er kwam geen transportdrone onder je hangen");
            }
            UUID carrierId = carrier.getUUID();
            double startX = carrier.getX();
            helper.succeedWhen(() -> {
                // Elke tick "vooruit", precies wat het spel van de speler hoort te sturen.
                EdithServer.handleDroneControl(owner, new DroneControlPayload(1, 0, 0, false, -90, 0));
                if (!(helper.getLevel().getEntity(carrierId) instanceof StarkDroneEntity drone)) {
                    throw new GameTestAssertException("Transportdrone verdwenen");
                }
                if (drone.getX() - startX < 5) {
                    throw new GameTestAssertException("Drone vloog niet vooruit: " + (drone.getX() - startX));
                }
            });
        });
    }

    /**
     * Landen: de drone zakt met je mee en zet je op de grond. Vroeger liet hij je vallen
     * met zweefval; daar sta je dan seconden later pas mee beneden.
     */
    private static void transportLanding(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean landing = new AtomicBoolean();
        whenEntityTicking(helper, List.of(owner.blockPosition()), () -> {
            if (started.getAndSet(true)) {
                return;
            }
            EdithTransport.startRide(owner, EdithData.getWornGlasses(owner), false);
            if (EdithTransport.carrierOf(owner) == null) {
                throw new GameTestAssertException("Er kwam geen transportdrone onder je hangen");
            }
            helper.succeedWhen(() -> {
                // De batterij vol houden: anders laat de drone je halverwege los omdat de
                // energie op is, en dan meten we het landen niet meer.
                ItemStack worn = EdithData.getWornGlasses(owner);
                EdithData.setEnergy(worn, EdithData.getMaxEnergy(worn));
                StarkDroneEntity carrier = EdithTransport.carrierOf(owner);
                if (!landing.get()) {
                    if (carrier == null) {
                        throw new GameTestAssertException("De rit stopte uit zichzelf");
                    }
                    if (EdithTransport.distanceToGround(owner) < 8) {
                        // Omhoog (spatie), tot we hoog genoeg hangen om te kunnen landen.
                        EdithServer.handleDroneControl(owner, new DroneControlPayload(0, 0, 1, false, 0, 0));
                        throw new GameTestAssertException("Nog aan het stijgen");
                    }
                    landing.set(true);
                    owner.setNoGravity(false); // vanaf nu telt de zwaartekracht mee
                    carrier.beginLanding(); // wat sluipen doet (zie EdithTransport.onDismount)
                    throw new GameTestAssertException("Landen net begonnen");
                }
                if (carrier != null) {
                    throw new GameTestAssertException("Hangt nog steeds onder de drone");
                }
                if (owner.hasEffect(MobEffects.SLOW_FALLING)) {
                    throw new GameTestAssertException("Je kreeg zweefval in plaats van neergezet te worden");
                }
                // De drone heeft je losgelaten. Dan moet je zakken, niet vallen: de mod zet
                // je in de zachte landing en houdt je valafstand op nul.
                //
                // Dat je daarna ook echt rustig naar beneden komt, kan deze test niet zien:
                // een nep-speler heeft geen spel dat hem laat vallen. Dat merk je alleen in
                // het echte spel.
                if (!EdithTransport.isSoftLanding(owner) && EdithTransport.distanceToGround(owner) > 1.0) {
                    throw new GameTestAssertException("Je werd losgelaten zonder zachte landing");
                }
                if (owner.fallDistance > 1.0f) {
                    throw new GameTestAssertException("Valafstand loopt op: " + owner.fallDistance);
                }
            });
        });
    }

    /** Een zwerm aanvalsdrones schakelt een vijand in de buurt uit. */
    private static void droneSwarm(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        Husk enemy = spawnDummyEnemy(helper, new BlockPos(7, 3, 1));
        UUID enemyId = enemy.getUUID();
        // Wachten tot de chunks van eigenaar en vijand echt "actief" zijn, dan pas lanceren.
        whenEntityTicking(helper, List.of(owner.blockPosition(), enemy.blockPosition()), () -> {
            // Hoeveel drones er in een zwerm zitten staat in de config (EdithConfig), dus vragen we het op.
            int expected = EdithData.getSwarmSize(EdithData.getWornGlasses(owner));
            int launched = EdithDrones.launchSwarm(owner, EdithData.getWornGlasses(owner));
            if (launched != expected) {
                throw new GameTestAssertException("Verwacht " + expected + " drones, kreeg er " + launched);
            }
        });
        helper.succeedWhen(() -> {
            if (findAlive(helper, enemyId) instanceof LivingEntity alive) {
                throw new GameTestAssertException("Vijand leeft nog: " + alive.getHealth() + " levens");
            }
        });
    }

    /** De satelliet slaat na het opladen in en schakelt een vijand op het doel uit. */
    private static void satelliteStrike(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(0, 3, 0));
        Husk enemy = spawnDummyEnemy(helper, new BlockPos(14, 3, 0));
        // Pas na 5 seconden starten: de tests draaien tegelijk, en zo is de drone-test
        // klaar voordat deze explosie zijn drones zou kunnen raken.
        int startDelay = 100;
        // Bombardement: meerdere inslagen, dus de vijand gaat er zeker aan.
        helper.runAfterDelay(startDelay, () -> SatelliteStrikes.start(helper.getLevel(), enemy.position(), owner.getUUID(),
                SatelliteMode.BOMBARDMENT, null, new Vec3(1, 0, 0)));
        // Tijdens het opladen mag er nog niets gebeuren...
        UUID enemyId = enemy.getUUID();
        helper.runAfterDelay(startDelay + EdithBalance.SATELLITE_CHARGE_TIME - 10, () -> {
            if (!(findAlive(helper, enemyId) instanceof LivingEntity alive) || alive.getHealth() < alive.getMaxHealth()) {
                throw new GameTestAssertException("De satelliet sloeg te vroeg in");
            }
        });
        // ...en daarna is de vijand weg.
        helper.succeedWhen(() -> {
            if (findAlive(helper, enemyId) != null) {
                throw new GameTestAssertException("Vijand leeft nog na de inslag");
            }
        });
    }

    /**
     * Zoekt een wezen op via zijn UUID. Let op: een wezen kan tijdens een test opnieuw
     * ingeladen worden als nieuw object, dus we bewaren nooit het object zelf.
     */
    private static @Nullable LivingEntity findAlive(GameTestHelper helper, UUID id) {
        return helper.getLevel().getEntity(id) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /**
     * De kluis moet echt een kluis zijn: bewakers, laserpoorten op de weg ernaartoe, en
     * deuren die je open moet zien te krijgen.
     */
    private static void checkVault(GameTestHelper helper) {
        int emitters = 0;
        int spawners = 0;
        int doors = 0;
        int levers = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 41; x++) {
            for (int z = 0; z < 41; z++) {
                for (int y = 67; y < 88; y++) {
                    BlockState state = helper.getBlockState(pos.set(x, y, z));
                    if (state.is(ModBlocks.LASER_EMITTER.get())) {
                        emitters++;
                    } else if (state.is(Blocks.SPAWNER)) {
                        spawners++;
                    } else if (state.is(Blocks.IRON_DOOR)) {
                        doors++;
                    } else if (state.is(Blocks.LEVER)) {
                        levers++;
                    }
                }
            }
        }
        if (emitters < 12) {
            throw new GameTestAssertException("Te weinig laserpoorten op de weg naar de kluis: " + emitters);
        }
        if (spawners < 4) {
            throw new GameTestAssertException("De kluis wordt niet bewaakt: " + spawners + " spawners");
        }
        if (doors < 3 || levers < 3) {
            throw new GameTestAssertException("De omweg klopt niet: " + doors + " deuren, " + levers + " schakelaars");
        }
    }

    private static void towerAndStation(GameTestHelper helper) {
        // 1. Het gebouw is goed geplaatst.
        helper.assertBlockPresent(ModBlocks.CHARGING_STATION.get(), STATION_POS);
        helper.assertBlockPresent(Blocks.CHEST, CHEST_POS);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(CHEST_POS);
        if (chest.getLootTable() == null) {
            throw new GameTestAssertException("De kist heeft geen loot table");
        }
        // De bril ligt niet in de gewone kisten, maar in de kluis bovenin.
        helper.assertBlockPresent(Blocks.CHEST, VAULT_CHEST_POS);
        ChestBlockEntity vault = (ChestBlockEntity) helper.getBlockEntity(VAULT_CHEST_POS);
        if (vault.getLootTable() == null || !vault.getLootTable().location().getPath().contains("vault")) {
            throw new GameTestAssertException("De kluiskist heeft niet de kluis-loot: " + vault.getLootTable());
        }
        checkVault(helper);

        // 2. Het laadstation laadt op en bouwt een upgrade in.
        ChargingStationBlockEntity station = (ChargingStationBlockEntity) helper.getBlockEntity(STATION_POS);
        station.setItem(ChargingStationBlockEntity.SLOT_GLASSES, new ItemStack(ModItems.EDITH_GLASSES.get()));
        station.setItem(ChargingStationBlockEntity.SLOT_FUEL, new ItemStack(Items.REDSTONE, 4));
        station.setItem(ChargingStationBlockEntity.SLOT_UPGRADE, new ItemStack(ModItems.BATTERY_UPGRADE.get()));

        helper.succeedWhen(() -> {
            ItemStack glasses = station.getItem(ChargingStationBlockEntity.SLOT_GLASSES);
            if (EdithData.getUpgrades(glasses).battery() != 1) {
                throw new GameTestAssertException("Upgrade nog niet ingebouwd");
            }
            if (EdithData.getEnergy(glasses) < 4 * 500) {
                throw new GameTestAssertException("Bril nog niet opgeladen: " + EdithData.getEnergy(glasses));
            }
        });
    }
}
