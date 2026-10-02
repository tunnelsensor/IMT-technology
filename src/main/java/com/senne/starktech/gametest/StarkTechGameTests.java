package com.senne.starktech.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import com.senne.starktech.StarkTech;
import com.senne.starktech.arsenal.ArsenalBalance;
import com.senne.starktech.arsenal.ArsenalCosts;
import com.senne.starktech.arsenal.ArsenalDrone;
import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.arsenal.EdithArsenal;
import com.senne.starktech.drone.DroneFormation;
import com.senne.starktech.drone.EdithDrones;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.hydra.HydraSoldierEntity;
import com.senne.starktech.edith.EdithAction;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithConfig;
import com.senne.starktech.holo.HoloMode;
import com.senne.starktech.holo.HoloTables;
import com.senne.starktech.network.HoloActionPayload;
import com.senne.starktech.holo.HoloTableBlock;
import com.senne.starktech.holo.HoloTableBlockEntity;
import com.senne.starktech.shield.ShieldColor;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithOwner;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.edith.StarkAdvancements;
import com.senne.starktech.edith.EdithThreats;
import com.senne.starktech.nav.EdithNavigation;
import com.senne.starktech.nav.EdithWaypoints;
import com.senne.starktech.nav.Waypoint;
import com.senne.starktech.network.DroneControlPayload;
import com.senne.starktech.network.WaypointActionPayload;
import com.senne.starktech.registry.ModArmorMaterials;
import com.senne.starktech.raid.EdithRaids;
import com.senne.starktech.raid.ModGameRules;
import com.senne.starktech.raid.RaidBalance;
import com.senne.starktech.raid.RaidDroneEntity;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.satellite.SatelliteMode;
import com.senne.starktech.satellite.SatelliteStrikes;
import com.senne.starktech.satellite.SatelliteWorkshop;
import com.senne.starktech.satellite.SatelliteUpgrade;
import com.senne.starktech.satellite.SatelliteUpgrades;
import com.senne.starktech.satellite.SatelliteStats;
import com.senne.starktech.shield.LaserEmitterBlock;
import com.senne.starktech.shield.LaserWallBlock;
import com.senne.starktech.shield.ShieldColor;
import com.senne.starktech.spy.EdithSpy;
import com.senne.starktech.suit.EdithSuit;
import com.senne.starktech.suit.RocketPackItem;
import com.senne.starktech.suit.SuitRockets;
import com.senne.starktech.turret.TurretBlock;
import com.senne.starktech.turret.TurretBlockEntity;
import com.senne.starktech.suit.StarkSuitItem;
import com.senne.starktech.suit.SuitBalance;
import com.senne.starktech.transport.EdithTransport;
import com.senne.starktech.client.music.MusicQuery;
import com.senne.starktech.client.voice.VoiceAction;
import com.senne.starktech.client.voice.VoiceDictionary;
import com.senne.starktech.client.voice.VoiceMatcher;
import com.senne.starktech.illusion.EdithIllusions;
import com.senne.starktech.illusion.IllusionScene;
import com.senne.starktech.shield.DomeGeneratorBlockEntity;
import com.senne.starktech.shield.DomeSettings;
import com.senne.starktech.shield.EdithDomes;
import com.senne.starktech.station.ChargerBlockEntity;
import com.senne.starktech.station.HeatGeneratorBlock;
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
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.NonNullList;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
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

    /**
     * Het wrak van de neergestorte satelliet (zie tools/generate_crash_site.py; in de test ligt
     * alles één blok hoger): in de romp staat de zwarte doos met de satelliet-onderdelen, en de
     * reactor gloeit nog met een blauw vuurtje ernaast.
     */
    @GameTest(template = "crashed_satellite", timeoutTicks = 60, batch = "crash_site")
    public static void crashed_satellite(GameTestHelper helper) {
        BlockPos blackBox = new BlockPos(15, 3, 16);
        helper.assertBlockPresent(Blocks.CHEST, blackBox);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(blackBox);
        if (chest.getLootTable() == null || !chest.getLootTable().location().getPath().endsWith("crashed_satellite")) {
            throw new GameTestAssertException("De zwarte doos heeft niet de goede buit: " + chest.getLootTable());
        }
        helper.assertBlockPresent(Blocks.SEA_LANTERN, new BlockPos(14, 4, 16));
        helper.assertBlockProperty(new BlockPos(15, 3, 15), CampfireBlock.LIT, true);
        helper.succeed();
    }

    /**
     * Elke buittabel van de mod laadt echt. Zit er een fout in (bv. een verkeerde naam), dan
     * gooit Minecraft de hele tabel weg en blijft de kist leeg, zonder dat je het merkt.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "bunker")
    public static void loot_tables_load(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var tables = server.reloadableRegistries().get().registryOrThrow(net.minecraft.core.registries.Registries.LOOT_TABLE);
        List<String> missing = new java.util.ArrayList<>();
        // Alle bestanden in data/starktech/loot_table, en of het spel ze ook echt kent.
        var files = server.getResourceManager().listResources("loot_table",
                location -> location.getNamespace().equals(StarkTech.MODID) && location.getPath().endsWith(".json"));
        for (ResourceLocation file : files.keySet()) {
            String name = file.getPath().substring("loot_table/".length(), file.getPath().length() - ".json".length());
            if (tables.get(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, name)) == null) {
                missing.add(name);
            }
        }
        if (files.size() < 10) {
            throw new GameTestAssertException("Maar " + files.size() + " buittabellen gevonden");
        }
        if (!missing.isEmpty()) {
            throw new GameTestAssertException("Deze buittabellen laden niet (kijk in het log): " + missing);
        }
        helper.succeed();
    }

    /**
     * Een illusie (of het onzichtbare veld) komt waar je naar kijkt. Kijk je naar een wezen met
     * een muur erachter, dan komt hij bij het wezen, niet op de muur. Vroeger ging hij de muur in,
     * en stikte de drone daar.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "illusion_spot")
    public static void illusion_goes_where_you_look(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(12, 3, 4));
        Husk target = spawnDummyEnemy(helper, new BlockPos(12, 3, 12));
        // Een muur ver achter het doelwit.
        for (int x = 8; x <= 16; x++) {
            for (int y = 3; y <= 7; y++) {
                helper.setBlock(new BlockPos(x, y, 20), Blocks.STONE);
            }
        }
        owner.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        Vec3 spot = EdithDrones.illusionSpot(owner);
        if (Math.hypot(spot.x - target.getX(), spot.z - target.getZ()) > 2) {
            throw new GameTestAssertException("De illusie kwam op " + helper.relativeVec(spot)
                    + " in plaats van bij het doelwit op " + helper.relativeVec(target.position()));
        }
        helper.succeed();
    }

    /** Een drone ademt niet: klem in een blok stikt hij niet. */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "illusion_spot")
    public static void drones_do_not_suffocate(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(2, 3, 2));
        BlockPos inside = new BlockPos(12, 4, 12);
        helper.setBlock(inside, Blocks.STONE);
        helper.setBlock(inside.above(), Blocks.STONE);
        StarkDroneEntity drone = ModEntities.STARK_DRONE.get().create(helper.getLevel());
        drone.setup(owner, StarkDroneEntity.Mode.ILLUSION, 0, -1);
        drone.setNoAi(true);
        Vec3 pos = helper.absoluteVec(Vec3.atCenterOf(inside));
        drone.moveTo(pos.x, pos.y - 0.2, pos.z, 0, 0);
        helper.getLevel().addFreshEntity(drone);
        float health = drone.getHealth();
        helper.runAfterDelay(60, () -> {
            if (!drone.isAlive() || drone.getHealth() < health) {
                throw new GameTestAssertException("De drone kreeg schade in het blok (" + drone.getHealth() + "/" + health + ")");
            }
            helper.succeed();
        });
    }

    /** Telt de getallen in een (geneste) config: elk kopje is weer een config met getallen erin. */
    private static int countValues(com.electronwill.nightconfig.core.UnmodifiableConfig config) {
        int count = 0;
        for (Object value : config.valueMap().values()) {
            count += value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig inner ? countValues(inner) : 1;
        }
        return count;
    }

    /**
     * Het configbestand starktech-balance.toml: alle @Tunable-getallen staan erin (ruim 140), en
     * verander je er een (zoals de wereldeigenaar in het bestand doet), dan rekent de mod met het
     * nieuwe getal. We tellen de regels in het geladen bestand, zodat er geen vast getal in deze
     * test hoeft (dat moest bij elk nieuw getal omhoog).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "balance")
    public static void balance_config_changes_the_numbers(GameTestHelper helper) {
        var spec = com.senne.starktech.edith.BalanceConfig.SPEC;
        if (!spec.isLoaded()) {
            throw new GameTestAssertException("starktech-balance.toml is niet geladen");
        }
        int inFile = countValues(spec.getValues());
        if (inFile != com.senne.starktech.edith.BalanceConfig.size() || inFile < 140) {
            throw new GameTestAssertException("Er staan " + inFile + " getallen in het bestand, maar er zijn er "
                    + com.senne.starktech.edith.BalanceConfig.size() + " met @Tunable");
        }
        net.neoforged.neoforge.common.ModConfigSpec.IntValue cost = spec.getValues().get("scanner.oreScanCost");
        net.neoforged.neoforge.common.ModConfigSpec.DoubleValue damage = spec.getValues().get("suit.repulsorDamage");
        int oldCost = cost.get();
        double oldDamage = damage.get();
        try {
            cost.set(123);
            damage.set(9.5);
            com.senne.starktech.edith.BalanceConfig.reapply();
            if (EdithBalance.ORE_SCAN_COST != 123 || SuitBalance.REPULSOR_DAMAGE != 9.5f) {
                throw new GameTestAssertException("De nieuwe getallen tellen niet: ertsen-scan kost "
                        + EdithBalance.ORE_SCAN_COST + ", repulsor doet " + SuitBalance.REPULSOR_DAMAGE);
            }
        } finally {
            cost.set(oldCost);
            damage.set(oldDamage);
            com.senne.starktech.edith.BalanceConfig.reapply();
        }
        helper.succeed();
    }

    // ================================================================ De beveiliging van de Stark Tower

    /** Zet een beveiligingskern neer (zonder eigenaar, of met de gegeven eigenaar). */
    private static com.senne.starktech.security.SecurityCoreBlockEntity placeCore(GameTestHelper helper, BlockPos pos,
                                                                                   @Nullable ServerPlayer owner) {
        helper.setBlock(pos, ModBlocks.SECURITY_CORE.get());
        powerTurret(helper, pos.below()); // de kern heeft stroom nodig
        var core = (com.senne.starktech.security.SecurityCoreBlockEntity) helper.getBlockEntity(pos);
        if (owner != null) {
            core.setOwner(owner);
        }
        return core;
    }

    /** Een toren van niemand: het beveiligingstorentje schiet op wie er binnenkomt. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "security")
    public static void security_turret_shoots_intruders(GameTestHelper helper) {
        placeCore(helper, new BlockPos(2, 3, 2), null);
        helper.setBlock(new BlockPos(12, 3, 12), ModBlocks.SECURITY_TURRET.get());
        powerTurret(helper, new BlockPos(12, 2, 12));
        ServerPlayer intruder = makeSurvivalOwner(helper, new BlockPos(12, 3, 18));
        helper.succeedWhen(() -> {
            if (intruder.getHealth() >= intruder.getMaxHealth()) {
                throw new GameTestAssertException("Het beveiligingstorentje heeft de indringer nog niet geraakt");
            }
        });
    }

    /**
     * Na het hacken is de toren van jou: het torentje laat jou met rust, maar schiet nog wel op
     * een monster. Door de laserwand van je eigen toren loop je zonder te branden.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "security")
    public static void security_works_for_the_owner(GameTestHelper helper) {
        ServerPlayer owner = makeSurvivalOwner(helper, new BlockPos(12, 3, 18));
        placeCore(helper, new BlockPos(2, 3, 2), owner);
        helper.setBlock(new BlockPos(12, 3, 12), ModBlocks.SECURITY_TURRET.get());
        powerTurret(helper, new BlockPos(12, 2, 12));
        Husk monster = spawnDummyEnemy(helper, new BlockPos(18, 3, 12));
        // Een laserwand waar de eigenaar in staat.
        helper.setBlock(new BlockPos(12, 3, 18), ModBlocks.LASER_WALL.get());
        helper.setBlock(new BlockPos(12, 4, 18), ModBlocks.LASER_WALL.get());
        helper.succeedWhen(() -> {
            if (owner.getHealth() < owner.getMaxHealth()) {
                throw new GameTestAssertException("De beveiliging (of de laserwand) raakte de eigenaar");
            }
            if (monster.isAlive() && monster.getHealth() >= monster.getMaxHealth()) {
                throw new GameTestAssertException("Het monster is nog niet geraakt");
            }
        });
    }

    /** De kern stuurt zijn beveiligingsdrones de lucht in, tot er vier rondvliegen. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "security")
    public static void security_core_launches_drones(GameTestHelper helper) {
        BlockPos pos = new BlockPos(12, 3, 12);
        placeCore(helper, pos, null);
        BlockPos absolute = helper.absolutePos(pos);
        helper.succeedWhen(() -> {
            int drones = helper.getLevel().getEntitiesOfClass(com.senne.starktech.security.SecurityDroneEntity.class,
                    new AABB(absolute).inflate(40), drone -> absolute.equals(drone.home())).size();
            if (drones != com.senne.starktech.security.SecurityBalance.MAX_DRONES) {
                throw new GameTestAssertException("Er vliegen " + drones + " beveiligingsdrones in plaats van "
                        + com.senne.starktech.security.SecurityBalance.MAX_DRONES);
            }
        });
    }

    /**
     * De oplossing van de hack op deze laptop, zoals een speler die alles in één keer goed doet:
     * de open poort, het wachtwoord, override op de beveiligingsdaemon, de waakhond uit en jezelf
     * eigenaar.
     */
    private static java.util.List<String> hackSolution(com.senne.starktech.security.StarkLaptopBlockEntity laptop) {
        var terminal = new com.senne.starktech.security.HackTerminal(laptop.seed(), laptop.ownerName());
        String connect = "connect " + terminal.openPort();
        terminal.run(connect, 0); // pas daarna staat het wachtwoord vast
        return java.util.List.of(connect, "login " + terminal.password(), "override " + terminal.guardPid(),
                "disable watchdog", "set owner " + laptop.ownerName());
    }

    private static void typeOnLaptop(ServerPlayer hacker, BlockPos laptopAbsolute, java.util.List<String> commands) {
        for (String command : commands) {
            com.senne.starktech.security.TowerHack.handleCommand(hacker,
                    new com.senne.starktech.network.HackCommandPayload(laptopAbsolute, command));
        }
    }

    /**
     * Met de laptop op de kern klikken zet hem erop (en de hack begint). Speel je de terminal uit
     * (en duurt dat lang genoeg), dan is de toren van jou en krijg je de laptop terug.
     */
    @GameTest(template = ARENA, timeoutTicks = 260, batch = "security")
    public static void laptop_hack_takes_the_tower(GameTestHelper helper) {
        BlockPos pos = new BlockPos(12, 3, 12);
        var core = placeCore(helper, pos, null);
        ServerPlayer hacker = makeSurvivalOwner(helper, new BlockPos(12, 3, 15));
        // De beveiliging schiet tijdens de hack echt op je; in deze test overleeft de hacker dat.
        hacker.getAbilities().invulnerable = true;
        hacker.getAbilities().instabuild = false; // anders blijft de laptop (zoals in creative) in je hand
        ItemStack laptopItem = new ItemStack(ModItems.STARK_LAPTOP.get());
        if (!com.senne.starktech.security.TowerHack.placeLaptop(hacker, helper.absolutePos(pos), laptopItem)) {
            throw new GameTestAssertException("De laptop ging niet op de kern");
        }
        helper.assertBlockPresent(ModBlocks.STARK_LAPTOP.get(), pos.above());
        if (!laptopItem.isEmpty() || !com.senne.starktech.security.TowerHack.isHacking(hacker)) {
            throw new GameTestAssertException("De laptop bleef in je hand, of de terminal ging niet open");
        }
        var laptop = (com.senne.starktech.security.StarkLaptopBlockEntity) helper.getBlockEntity(pos.above());
        helper.runAfterDelay(com.senne.starktech.security.TowerHack.MIN_TICKS + 5, () -> {
            typeOnLaptop(hacker, helper.absolutePos(pos.above()), hackSolution(laptop));
            if (!hacker.getUUID().equals(core.ownerId())) {
                throw new GameTestAssertException("Na de hack is de toren niet van de hacker");
            }
            helper.assertBlockProperty(pos, com.senne.starktech.security.SecurityCoreBlock.CLAIMED, true);
            helper.assertBlockNotPresent(ModBlocks.STARK_LAPTOP.get(), pos.above());
            if (!hacker.getInventory().contains(new ItemStack(ModItems.STARK_LAPTOP.get()))) {
                throw new GameTestAssertException("De hacker kreeg de laptop niet terug");
            }
            helper.succeed();
        });
    }

    /** Een hack die onmogelijk snel klaar is (een valsspelend spel), telt niet. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "security")
    public static void laptop_hack_too_fast_is_rejected(GameTestHelper helper) {
        BlockPos pos = new BlockPos(12, 3, 12);
        var core = placeCore(helper, pos, null);
        ServerPlayer hacker = makeSurvivalOwner(helper, new BlockPos(12, 3, 15));
        com.senne.starktech.security.TowerHack.placeLaptop(hacker, helper.absolutePos(pos),
                new ItemStack(ModItems.STARK_LAPTOP.get()));
        var laptop = (com.senne.starktech.security.StarkLaptopBlockEntity) helper.getBlockEntity(pos.above());
        typeOnLaptop(hacker, helper.absolutePos(pos.above()), hackSolution(laptop));
        if (core.ownerId() != null) {
            throw new GameTestAssertException("Een hack van 0 seconden werd goedgekeurd");
        }
        helper.succeed();
    }

    /**
     * Loop je weg bij de laptop, dan gaat het scherm dicht, maar je voortgang blijft op de laptop:
     * open je hem weer, dan sta je nog bij het wachtwoord.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "security")
    public static void laptop_hack_keeps_progress(GameTestHelper helper) {
        BlockPos pos = new BlockPos(12, 3, 12);
        placeCore(helper, pos, null);
        ServerPlayer hacker = makeSurvivalOwner(helper, new BlockPos(12, 3, 15));
        com.senne.starktech.security.TowerHack.placeLaptop(hacker, helper.absolutePos(pos),
                new ItemStack(ModItems.STARK_LAPTOP.get()));
        var laptop = (com.senne.starktech.security.StarkLaptopBlockEntity) helper.getBlockEntity(pos.above());
        typeOnLaptop(hacker, helper.absolutePos(pos.above()), hackSolution(laptop).subList(0, 1));
        Vec3 far = helper.absoluteVec(new Vec3(12.5, 3, 22.5));
        hacker.teleportTo(far.x, far.y, far.z);
        com.senne.starktech.security.TowerHack.check(hacker);
        if (com.senne.starktech.security.TowerHack.isHacking(hacker)) {
            throw new GameTestAssertException("Het scherm bleef open terwijl de hacker 10 blokken verderop stond");
        }
        if (laptop.terminal().stage() != com.senne.starktech.security.HackTerminal.Stage.LOGIN) {
            throw new GameTestAssertException("De laptop vergat de voortgang: " + laptop.commands());
        }
        Vec3 back = helper.absoluteVec(new Vec3(12.5, 3, 15.5));
        hacker.teleportTo(back.x, back.y, back.z);
        com.senne.starktech.security.TowerHack.open(hacker, helper.absolutePos(pos.above()));
        if (!com.senne.starktech.security.TowerHack.isHacking(hacker)) {
            throw new GameTestAssertException("De laptop ging niet weer open");
        }
        helper.succeed();
    }

    /**
     * Elke kern is een klein systeem: twee kernen krijgen elk een eigen IP, een kern van niemand is
     * Tony's systeem (met zijn README en het systeembestand security.conf), en breek je een kern
     * af, dan is zijn IP weer vrij.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "security")
    public static void security_core_gets_a_system(GameTestHelper helper) {
        var first = placeCore(helper, new BlockPos(4, 3, 4), null);
        var second = placeCore(helper, new BlockPos(20, 3, 20), null);
        ServerLevel level = helper.getLevel();
        first.ensureSystem(level);
        second.ensureSystem(level);
        if (first.ip().isEmpty() || first.ip().equals(second.ip()) || !first.ip().startsWith("10.")) {
            throw new GameTestAssertException("De kernen kregen geen eigen IP: " + first.ip() + " en " + second.ip());
        }
        var network = com.senne.starktech.security.net.StarkNetworkData.get(level);
        if (!helper.absolutePos(new BlockPos(4, 3, 4)).equals(network.find(first.ip()).pos())) {
            throw new GameTestAssertException("Het netwerk vindt de kern niet terug op zijn IP");
        }
        if (!first.hostname().equals("stark-sec-core") || !first.fs().isFile("/README.txt")) {
            throw new GameTestAssertException("Een kern van niemand is niet het systeem van Tony");
        }
        var conf = first.fs().get("/etc/security.conf");
        if (conf == null || !conf.isSystem() || !conf.content().contains("daemon = " + first.daemon())) {
            throw new GameTestAssertException("security.conf ontbreekt of noemt het beveiligingsprogramma niet");
        }
        if (first.hasPassword() || first.sshPort() < 1024) {
            throw new GameTestAssertException("Een nieuw systeem heeft al een wachtwoord, of een rare poort");
        }
        // Nog eens: niets verandert (hetzelfde IP).
        String ip = first.ip();
        first.ensureSystem(level);
        if (!ip.equals(first.ip())) {
            throw new GameTestAssertException("ensureSystem gaf de kern een ander IP");
        }
        helper.setBlock(new BlockPos(4, 3, 4), Blocks.AIR);
        if (network.find(ip) != null) {
            throw new GameTestAssertException("De kern is weg, maar zijn IP is nog bezet");
        }
        helper.succeed();
    }

    /**
     * Het systeem blijft bewaard: wachtwoord (alleen als hash), mappen, een valstrik, het logboek.
     * En een kern uit een oude wereld (zonder al die gegevens) laadt gewoon.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "security")
    public static void security_core_system_is_saved(GameTestHelper helper) {
        ServerPlayer owner = makeSurvivalOwner(helper, new BlockPos(12, 3, 15));
        var core = placeCore(helper, new BlockPos(12, 3, 12), owner);
        ServerLevel level = helper.getLevel();
        core.ensureSystem(level);
        String home = "/home/" + com.senne.starktech.security.os.SystemDefaults.homeName(owner.getGameProfile().getName());
        if (!core.fs().isDir(home) || core.hostname().equals("stark-sec-core")) {
            throw new GameTestAssertException("Een kern van een speler kreeg geen thuismap of eigen naam");
        }
        core.setPassword("senne123", level.random);
        core.fs().mkdir(home + "/secret");
        core.fs().write(home + "/secret/passwords.txt", "nice try");
        core.fs().setTrap(home + "/secret/passwords.txt", true);
        core.fs().move("/etc/security.conf", home + "/secret/recipes.txt");
        core.logEvent(com.senne.starktech.security.os.EventLog.Type.LOGIN, "test entry");

        var registries = level.registryAccess();
        var tag = core.saveWithFullMetadata(registries);
        if (tag.toString().contains("senne123")) {
            throw new GameTestAssertException("Het wachtwoord staat leesbaar in de save");
        }
        var copy = new com.senne.starktech.security.SecurityCoreBlockEntity(core.getBlockPos(), core.getBlockState());
        copy.loadWithComponents(tag, registries);
        if (!copy.checkPassword("senne123") || copy.checkPassword("senne124")) {
            throw new GameTestAssertException("Het wachtwoord klopt niet meer na laden");
        }
        var trap = copy.fs().get(home + "/secret/passwords.txt");
        var hidden = copy.fs().get(home + "/secret/recipes.txt");
        if (trap == null || !trap.isTrap() || hidden == null || !hidden.isSystem()
                || !(home + "/secret/recipes.txt").equals(copy.fs().systemFile())) {
            throw new GameTestAssertException("De mappen, de valstrik of het verstopte security.conf zijn weg na laden");
        }
        if (!copy.ip().equals(core.ip()) || !copy.daemon().equals(core.daemon())
                || copy.log().entries().stream().noneMatch(entry -> entry.text().equals("test entry"))) {
            throw new GameTestAssertException("IP, daemon of logboek zijn weg na laden");
        }
        // Een oude kern: alleen de eigenaar, verder niets. Laadt, en heeft nog geen systeem.
        var old = new com.senne.starktech.security.SecurityCoreBlockEntity(core.getBlockPos(), core.getBlockState());
        var oldTag = new net.minecraft.nbt.CompoundTag();
        oldTag.putString("OwnerName", "Old");
        old.loadWithComponents(oldTag, registries);
        if (old.hasSystem() || old.hasPassword() || old.fs().count() != 0) {
            throw new GameTestAssertException("Een oude kern laadde met een half systeem");
        }
        helper.succeed();
    }

    /**
     * De regels van de mappen: security.conf is een systeembestand (verplaatsen en hernoemen mag,
     * weggooien en overschrijven niet), namen moeten kloppen, een map kan niet in zichzelf, en een
     * te groot bestand past niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "security")
    public static void virtual_fs_rules(GameTestHelper helper) {
        var fs = new com.senne.starktech.security.os.VirtualFs();
        com.senne.starktech.security.os.SystemDefaults.tonyFiles(fs, "aegis");
        if (fs.delete("/etc/security.conf") == null || fs.delete("/etc") == null || !fs.isFile("/etc/security.conf")) {
            throw new GameTestAssertException("security.conf (of de map erom) kon weg");
        }
        if (fs.write("/etc/security.conf", "daemon = nope") == null) {
            throw new GameTestAssertException("security.conf kon overschreven worden");
        }
        if (fs.mkdir("/home/tony/deep") != null || fs.move("/etc/security.conf", "/home/tony/deep/cake.txt") != null
                || !"/home/tony/deep/cake.txt".equals(fs.systemFile())) {
            throw new GameTestAssertException("security.conf verstoppen lukte niet");
        }
        if (fs.move("/home", "/home/tony/deep") == null) {
            throw new GameTestAssertException("Een map ging in zichzelf");
        }
        if (fs.mkdir("/bad name") == null || fs.mkdir("/..") == null || fs.mkdir("/notes") != null) {
            throw new GameTestAssertException("Een foute naam werd toch een map");
        }
        if (fs.write("/big.txt", "x".repeat(com.senne.starktech.security.os.VirtualFs.MAX_FILE_LENGTH + 1)) == null) {
            throw new GameTestAssertException("Een te groot bestand paste toch");
        }
        if (fs.setTrap("/home/tony/deep/cake.txt", true) == null || fs.setTrap("/README.txt", true) != null
                || fs.delete("/README.txt") != null || fs.isFile("/README.txt")) {
            throw new GameTestAssertException("Valstrik zetten of een gewoon bestand weggooien liep fout");
        }
        if (!com.senne.starktech.security.os.VirtualFs.resolve("/home/tony", "../../etc/./x").equals("/etc/x")) {
            throw new GameTestAssertException("Paden met .. en . kloppen niet");
        }
        helper.succeed();
    }

    /**
     * De toegang die de desktop regelt: een vertrouwde speler wordt niet beschoten, "attack mobs"
     * uit laat monsters met rust, en al die instellingen plus de vertrouwde lijst blijven na
     * opslaan en laden. Bij een overname (resetAccessForNewOwner) is de lijst weer leeg en het
     * wachtwoord gewist.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "security_os")
    public static void security_access_flags_and_trusted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeSurvivalOwner(helper, new BlockPos(2, 3, 2));
        var core = placeCore(helper, new BlockPos(12, 3, 12), owner);
        Husk monster = spawnDummyEnemy(helper, new BlockPos(14, 3, 12));
        if (!com.senne.starktech.security.SecurityCores.isIntruder(level, core, monster)) {
            throw new GameTestAssertException("Een eigen kern ziet een monster standaard niet als indringer");
        }
        core.setAttackMobs(false);
        if (com.senne.starktech.security.SecurityCores.isIntruder(level, core, monster)) {
            throw new GameTestAssertException("Met 'attack mobs' uit werd het monster toch een doelwit");
        }
        ServerPlayer guest = makeSurvivalOwner(helper, new BlockPos(16, 3, 12));
        if (com.senne.starktech.security.SecurityCores.isFriend(level, core, guest)) {
            throw new GameTestAssertException("Een onbekende speler was al vriend");
        }
        core.addTrusted(guest.getUUID(), guest.getGameProfile().getName());
        if (!com.senne.starktech.security.SecurityCores.isFriend(level, core, guest)
                || com.senne.starktech.security.SecurityCores.isIntruder(level, core, guest)) {
            throw new GameTestAssertException("Een vertrouwde speler wordt toch als indringer gezien");
        }
        var registries = level.registryAccess();
        var copy = new com.senne.starktech.security.SecurityCoreBlockEntity(core.getBlockPos(), core.getBlockState());
        copy.loadWithComponents(core.saveWithFullMetadata(registries), registries);
        if (copy.attackMobs() || !copy.isTrusted(guest.getUUID()) || copy.trustedNames().size() != 1) {
            throw new GameTestAssertException("De toegang (attack mobs / vertrouwde lijst) ging verloren na laden");
        }
        copy.setPassword("abcd12", level.random);
        copy.resetAccessForNewOwner();
        if (copy.hasPassword() || !copy.trustedNames().isEmpty() || !copy.attackMobs()) {
            throw new GameTestAssertException("resetAccessForNewOwner liet de oude toegang staan");
        }
        helper.succeed();
    }

    /**
     * De Stark OS-desktop: de eigenaar opent hem door op de kern te klikken, een vreemde wordt
     * geweigerd, en vanaf de desktop zet de eigenaar een wachtwoord (een fout wachtwoord telt niet).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "security_os")
    public static void security_desktop_opens_for_the_owner(GameTestHelper helper) {
        ServerPlayer owner = makeSurvivalOwner(helper, new BlockPos(12, 3, 15));
        BlockPos pos = new BlockPos(12, 3, 12);
        var core = placeCore(helper, pos, owner);
        BlockPos abs = helper.absolutePos(pos);
        com.senne.starktech.security.os.StarkOs.open(owner, abs);
        if (!com.senne.starktech.security.os.StarkOs.hasDesktop(owner)) {
            throw new GameTestAssertException("De eigenaar kreeg geen desktop");
        }
        ServerPlayer guest = makeSurvivalOwner(helper, new BlockPos(12, 3, 14));
        com.senne.starktech.security.os.StarkOs.open(guest, abs);
        if (com.senne.starktech.security.os.StarkOs.hasDesktop(guest)) {
            throw new GameTestAssertException("Een vreemde kwam toch op de desktop");
        }
        net.minecraft.nbt.CompoundTag ok = new net.minecraft.nbt.CompoundTag();
        ok.putString("pw", "abcd12");
        com.senne.starktech.security.os.StarkOs.handleAction(owner,
                new com.senne.starktech.network.OsActionPayload(abs, "passwd", ok));
        if (!core.checkPassword("abcd12")) {
            throw new GameTestAssertException("Het wachtwoord werd niet gezet");
        }
        net.minecraft.nbt.CompoundTag bad = new net.minecraft.nbt.CompoundTag();
        bad.putString("pw", "AB");
        com.senne.starktech.security.os.StarkOs.handleAction(owner,
                new com.senne.starktech.network.OsActionPayload(abs, "passwd", bad));
        if (core.checkPassword("AB") || !core.checkPassword("abcd12")) {
            throw new GameTestAssertException("Een ongeldig wachtwoord werd toch overgenomen");
        }
        com.senne.starktech.security.os.StarkOs.close(owner);
        if (com.senne.starktech.security.os.StarkOs.hasDesktop(owner)) {
            throw new GameTestAssertException("De desktop bleef open na close");
        }
        helper.succeed();
    }

    /**
     * De Devices- en Access-apps: een torentje dat je op de desktop uitzet schiet niet meer, met de
     * drones uit vliegen er geen, met de lasers uit gaat een laserpoort niet aan (en weer aan als
     * je ze aanzet), en een Stark-torentje van de eigenaar volgt "attack mobs" van zijn kern. Een
     * onbekende naam vertrouwen lukt niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 240, batch = "security_devices")
    public static void security_devices_switch_from_the_desktop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeSurvivalOwner(helper, new BlockPos(5, 3, 5));
        BlockPos corePos = new BlockPos(2, 3, 2);
        var core = placeCore(helper, corePos, owner);
        BlockPos coreAbs = helper.absolutePos(corePos);
        com.senne.starktech.security.os.StarkOs.open(owner, coreAbs);
        if (!com.senne.starktech.security.os.StarkOs.hasDesktop(owner)) {
            throw new GameTestAssertException("De eigenaar kreeg geen desktop");
        }
        // De drones en de lasers meteen uit.
        osAction(owner, coreAbs, "flag", "name", "drones", "on", "false");
        osAction(owner, coreAbs, "flag", "name", "lasers", "on", "false");
        // Een beveiligingstorentje met een monster ervoor, en dat torentje uitzetten.
        BlockPos turretPos = new BlockPos(12, 3, 12);
        helper.setBlock(turretPos, ModBlocks.SECURITY_TURRET.get());
        powerTurret(helper, turretPos.below());
        Husk monster = spawnDummyEnemy(helper, new BlockPos(18, 3, 12));
        BlockPos turretAbs = helper.absolutePos(turretPos);
        if (!com.senne.starktech.security.SecurityDevices.scan(level, core).turrets().contains(turretAbs)
                && !com.senne.starktech.security.SecurityDevices.belongs(core,
                        (com.senne.starktech.turret.TurretBlockEntity) helper.getBlockEntity(turretPos))) {
            throw new GameTestAssertException("Het beveiligingstorentje hoort niet bij de kern");
        }
        // dev_toggle wil getallen (osAction maakt alleen tekst en ja/nee): dus zelf.
        net.minecraft.nbt.CompoundTag off = new net.minecraft.nbt.CompoundTag();
        off.putInt("x", turretAbs.getX());
        off.putInt("y", turretAbs.getY());
        off.putInt("z", turretAbs.getZ());
        off.putBoolean("on", false);
        com.senne.starktech.security.os.StarkOs.handleAction(owner,
                new com.senne.starktech.network.OsActionPayload(coreAbs, "dev_toggle", off));
        helper.assertBlockProperty(turretPos, com.senne.starktech.turret.TurretBlock.ENABLED, false);
        // Een laserpoort met redstone en stroom: met de lasers uit gaat hij niet aan.
        BlockPos emitter = new BlockPos(6, 3, 2);
        helper.setBlock(emitter, ModBlocks.LASER_EMITTER.get().defaultBlockState()
                .setValue(LaserEmitterBlock.FACING, Direction.EAST));
        helper.setBlock(emitter.above(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(emitter.above())).fill(5_000);
        helper.setBlock(emitter.below(), Blocks.REDSTONE_BLOCK);
        // Een naam die nooit op de server was, kun je niet vertrouwen.
        osAction(owner, coreAbs, "trust_add", "name", "nobody_like_this");
        if (!core.trustedNames().isEmpty()) {
            throw new GameTestAssertException("Een onbekende naam kwam toch op de vertrouwde lijst");
        }
        helper.runAfterDelay(80, () -> {
            // Een Stark-torentje van de eigenaar in zijn eigen gebied volgt de kern. (Pas nu: een
            // kern meldt zich een tick na het neerzetten aan.)
            var starkKind = com.senne.starktech.turret.TurretKind.STARK;
            String ownerName = owner.getGameProfile().getName();
            if (!com.senne.starktech.turret.TurretTargeting.isTarget(level, starkKind, owner.getUUID(), ownerName, monster, turretAbs)) {
                throw new GameTestAssertException("Het Stark-torentje van de eigenaar ziet het monster niet als doelwit");
            }
            osAction(owner, coreAbs, "flag", "name", "mobs", "on", "false");
            if (core.attackMobs() || com.senne.starktech.turret.TurretTargeting.isTarget(level, starkKind, owner.getUUID(),
                    ownerName, monster, turretAbs)) {
                throw new GameTestAssertException("Met 'attack mobs' uit schiet het Stark-torentje van de eigenaar toch");
            }
            if (monster.getHealth() < monster.getMaxHealth()) {
                throw new GameTestAssertException("Het uitgezette torentje (of een drone) raakte het monster toch");
            }
            int drones = level.getEntitiesOfClass(com.senne.starktech.security.SecurityDroneEntity.class,
                    new AABB(coreAbs).inflate(40), drone -> coreAbs.equals(drone.home())).size();
            if (drones != 0) {
                throw new GameTestAssertException("Met de drones uit vlogen er toch " + drones + " drones");
            }
            if (helper.getBlockState(emitter).getValue(LaserEmitterBlock.POWERED)) {
                throw new GameTestAssertException("Met de lasers uit ging de laserpoort toch aan");
            }
            osAction(owner, coreAbs, "flag", "name", "lasers", "on", "true");
            helper.runAfterDelay(30, () -> {
                if (!helper.getBlockState(emitter).getValue(LaserEmitterBlock.POWERED)) {
                    throw new GameTestAssertException("Met de lasers weer aan ging de laserpoort niet aan");
                }
                helper.succeed();
            });
        });
    }

    /**
     * Zelf een kern neerzetten: hij is meteen van jou, met een IP en een eigen naam. Een tweede kern
     * vlakbij (de gebieden zouden overlappen) lukt niet, en je houdt het item.
     *
     * De kernen staan 210 blokken boven de testvloer, in de lucht: de testwereld wordt hergebruikt,
     * en de kernen van andere tests (ook uit eerdere rondes, en een Stark Tower uit de gebouwtest)
     * blijven staan. Die liggen soms dichterbij dan 60 blokken, en dan zou ook de eerste kern terecht
     * geweigerd worden. Zo hoog valt buiten elk gebied (dat reikt 60 omlaag en 55 omhoog).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "security_place")
    public static void security_core_placed_by_a_player(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeSurvivalOwner(helper, new BlockPos(5, 3, 5));
        player.getAbilities().instabuild = false; // anders blijft het item (zoals in creative) in je hand
        var item = (net.minecraft.world.item.BlockItem) ModItems.SECURITY_CORE.get();
        ItemStack stack = new ItemStack(item, 2);
        BlockPos first = new BlockPos(8, 210, 8);
        var result = item.place(new net.minecraft.world.item.context.BlockPlaceContext(level, player,
                net.minecraft.world.InteractionHand.MAIN_HAND, stack, new net.minecraft.world.phys.BlockHitResult(
                        helper.absoluteVec(new net.minecraft.world.phys.Vec3(8.5, 210, 8.5)), Direction.UP,
                        helper.absolutePos(first), false)));
        helper.assertBlockPresent(ModBlocks.SECURITY_CORE.get(), first);
        var core = (com.senne.starktech.security.SecurityCoreBlockEntity) helper.getBlockEntity(first);
        if (!result.consumesAction() || !player.getUUID().equals(core.ownerId()) || !core.hasSystem()
                || !core.hostname().endsWith("-sec") || stack.getCount() != 1) {
            throw new GameTestAssertException("Een zelf neergezette kern is niet van jou, of heeft geen systeem");
        }
        helper.assertBlockProperty(first, com.senne.starktech.security.SecurityCoreBlock.CLAIMED, true);
        BlockPos second = new BlockPos(8, 210, 16);
        var refused = item.place(new net.minecraft.world.item.context.BlockPlaceContext(level, player,
                net.minecraft.world.InteractionHand.MAIN_HAND, stack, new net.minecraft.world.phys.BlockHitResult(
                        helper.absoluteVec(new net.minecraft.world.phys.Vec3(8.5, 210, 16.5)), Direction.UP,
                        helper.absolutePos(second), false)));
        helper.assertBlockNotPresent(ModBlocks.SECURITY_CORE.get(), second);
        if (refused.consumesAction() || stack.getCount() != 1) {
            throw new GameTestAssertException("Een tweede kern vlak naast een ander netwerk kwam er toch");
        }
        if (com.senne.starktech.security.SecurityCores.overlaps(level, helper.absolutePos(first).offset(500, 0, 0))) {
            throw new GameTestAssertException("Een kern ver weg zou ook te dichtbij zijn");
        }
        helper.succeed();
    }

    /**
     * De planner van de bewakingsmuur (zoals Vista): een beeld dat nog nooit getekend is, is meteen
     * aan de beurt; daarna 5 keer per seconde (het grote beeld 10), minder als het spel onder de 60
     * fps zakt (nooit minder dan 2), om de beurt (het langst wachtende eerst), per frame minstens één
     * en verder binnen het tijdbudget. Een beeld dat niet meer op het scherm staat, wordt niet meer
     * getekend, en na 5 seconden weggegooid.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "security_cameras")
    public static void camera_feed_planner_picks_and_drops(GameTestHelper helper) {
        var planner = new com.senne.starktech.client.os.camera.FeedPlanner();
        planner.configure(5, 2, 3.0, 5000);
        planner.want(1, 0, false);
        planner.want(2, 0, false);
        if (!planner.due(0, 120).equals(java.util.List.of(1L, 2L)) && !planner.due(0, 120).equals(java.util.List.of(2L, 1L))) {
            throw new GameTestAssertException("Twee nieuwe beelden waren niet meteen aan de beurt: " + planner.due(0, 120));
        }
        planner.rendered(1, 0);
        planner.rendered(2, 10);
        if (!planner.due(100, 120).isEmpty()) {
            throw new GameTestAssertException("Een beeld kwam al na 100 ms weer aan de beurt");
        }
        planner.want(1, 200, false);
        planner.want(2, 200, false);
        if (!planner.due(215, 120).equals(java.util.List.of(1L, 2L))) {
            throw new GameTestAssertException("Na 200 ms niet allebei aan de beurt, de oudste eerst: " + planner.due(215, 120));
        }
        if (planner.interval(120, false) != 200 || planner.interval(30, false) != 400
                || planner.interval(10, false) != 500 || planner.interval(120, true) != 100) {
            throw new GameTestAssertException("De tijden tussen twee beelden kloppen niet");
        }
        if (!planner.mayRenderMore(0, 50) || planner.mayRenderMore(1, 3.5) || !planner.mayRenderMore(1, 1.0)) {
            throw new GameTestAssertException("Het tijdbudget per frame klopt niet");
        }
        // Niet meer op het scherm: niet meer tekenen, en na 5 seconden weg.
        if (!planner.due(600, 120).isEmpty()) {
            throw new GameTestAssertException("Een beeld dat niet meer op het scherm staat, wordt nog getekend");
        }
        if (!planner.expired(4000).isEmpty() || planner.expired(5300).size() != 2 || !planner.isEmpty()) {
            throw new GameTestAssertException("Ongebruikte beelden werden niet (of te vroeg) weggegooid");
        }
        helper.succeed();
    }

    /**
     * De bewakingscamera: met stroom heeft hij beeld, met de camera's van zijn netwerk uit niet; de
     * kern ziet hem (de bewakingsmuur krijgt hem door), en draaien vanaf de desktop gaat hooguit 60
     * graden elke kant op.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "security_cameras")
    public static void security_camera_power_network_and_aim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeSurvivalOwner(helper, new BlockPos(5, 3, 5));
        BlockPos corePos = new BlockPos(2, 3, 2);
        var core = placeCore(helper, corePos, owner);
        BlockPos coreAbs = helper.absolutePos(corePos);
        // Een camera aan een muurtje, die naar het zuiden kijkt, met stroom eronder.
        BlockPos wall = new BlockPos(8, 4, 8);
        helper.setBlock(wall, Blocks.STONE);
        BlockPos cam = wall.south();
        helper.setBlock(cam, ModBlocks.SECURITY_CAMERA.get().defaultBlockState()
                .setValue(com.senne.starktech.security.CameraBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                .setValue(com.senne.starktech.security.CameraBlock.FACING, Direction.SOUTH));
        powerTurret(helper, cam.below());
        var camera = (com.senne.starktech.security.CameraBlockEntity) helper.getBlockEntity(cam);
        BlockPos camAbs = helper.absolutePos(cam);
        // Een tick later: dan heeft de kern zich aangemeld.
        helper.runAfterDelay(2, () -> {
            camera.updateOnline(level, camAbs, level.getBlockState(camAbs));
            helper.assertBlockProperty(cam, com.senne.starktech.security.CameraBlock.ONLINE, true);
            com.senne.starktech.security.os.StarkOs.open(owner, coreAbs);
            osAction(owner, coreAbs, "flag", "name", "cameras", "on", "false");
            helper.assertBlockProperty(cam, com.senne.starktech.security.CameraBlock.ONLINE, false);
            osAction(owner, coreAbs, "flag", "name", "cameras", "on", "true");
            helper.assertBlockProperty(cam, com.senne.starktech.security.CameraBlock.ONLINE, true);
            // Draaien: 5 x 20 graden naar rechts wordt hooguit 60.
            for (int i = 0; i < 5; i++) {
                net.minecraft.nbt.CompoundTag aim = new net.minecraft.nbt.CompoundTag();
                aim.putInt("x", camAbs.getX());
                aim.putInt("y", camAbs.getY());
                aim.putInt("z", camAbs.getZ());
                aim.putFloat("pan", 20f);
                aim.putFloat("tilt", -20f);
                com.senne.starktech.security.os.StarkOs.handleAction(owner,
                        new com.senne.starktech.network.OsActionPayload(coreAbs, "cam_aim", aim));
            }
            if (camera.pan() != 60f || camera.tilt() != -60f) {
                throw new GameTestAssertException("Draaien ging verder dan 60 graden: pan " + camera.pan() + ", tilt " + camera.tilt());
            }
            var list = com.senne.starktech.security.SecurityDevices.cameras(level, core);
            if (list.size() != 1) {
                throw new GameTestAssertException("De kern ziet " + list.size() + " camera's in plaats van 1");
            }
            var seen = list.getCompound(0);
            // Naar het zuiden = 0 graden; 60 naar rechts = 60. De lens zit aan de zuidkant van het blok.
            if (seen.getFloat("yaw") != 60f || !"Camera 1".equals(seen.getString("name"))
                    || seen.getDouble("ez") <= camAbs.getZ() + 0.5) {
                throw new GameTestAssertException("De bewakingsmuur krijgt de camera verkeerd door: " + seen);
            }
            helper.succeed();
        });
    }

    /** Een opdracht van de desktop, zoals het Files- of Notes-venster hem stuurt. */
    private static void osAction(ServerPlayer player, BlockPos core, String op, String... keysAndValues) {
        net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            if (keysAndValues[i + 1].equals("true") || keysAndValues[i + 1].equals("false")) {
                args.putBoolean(keysAndValues[i], Boolean.parseBoolean(keysAndValues[i + 1]));
            } else {
                args.putString(keysAndValues[i], keysAndValues[i + 1]);
            }
        }
        com.senne.starktech.security.os.StarkOs.handleAction(player,
                new com.senne.starktech.network.OsActionPayload(core, op, args));
    }

    /**
     * Files en Notes op de desktop, en de valstrikken: de eigenaar maakt mappen en notities,
     * verstopt security.conf (weggooien lukt niet) en maakt een valstrik. Iemand anders die op het
     * systeem zit (zoals straks met het wachtwoord via SSH) ziet niet welk bestand de valstrik is;
     * opent hij hem, dan vliegt hij eruit, is hij buitengesloten, staat het in het logboek en jaagt
     * de beveiliging op hem. Een te grote notitie past niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "security_os")
    public static void security_files_notes_and_traps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeSurvivalOwner(helper, new BlockPos(12, 3, 15));
        BlockPos pos = new BlockPos(12, 3, 12);
        var core = placeCore(helper, pos, owner);
        BlockPos abs = helper.absolutePos(pos);
        com.senne.starktech.security.os.StarkOs.open(owner, abs);
        var fs = core.fs();
        String home = "/home/" + com.senne.starktech.security.os.SystemDefaults.homeName(owner.getGameProfile().getName());

        // De eigenaar ruimt op: een map, een notitie, security.conf verstoppen, een valstrik.
        osAction(owner, abs, "fs_mkdir", "path", home + "/secret");
        osAction(owner, abs, "fs_touch", "path", home + "/secret/passwords.txt");
        osAction(owner, abs, "fs_save", "path", home + "/secret/passwords.txt", "text", "nice try");
        osAction(owner, abs, "fs_save", "path", home + "/todo.txt", "text", "buy more turrets");
        osAction(owner, abs, "fs_move", "from", "/etc/security.conf", "to", home + "/secret/recipes.txt");
        osAction(owner, abs, "fs_delete", "path", home + "/secret/recipes.txt");
        osAction(owner, abs, "fs_trap", "path", home + "/secret/passwords.txt", "on", "true");
        var trapFile = fs.get(home + "/secret/passwords.txt");
        if (trapFile == null || !trapFile.isTrap() || !"nice try".equals(trapFile.content())
                || !"buy more turrets".equals(fs.get(home + "/todo.txt") == null ? null : fs.get(home + "/todo.txt").content())) {
            throw new GameTestAssertException("Map, notitie of valstrik van de eigenaar kwam er niet");
        }
        if (!(home + "/secret/recipes.txt").equals(fs.systemFile()) || fs.isFile("/etc/security.conf")) {
            throw new GameTestAssertException("security.conf verstoppen lukte niet, of verwijderen lukte wél");
        }
        // Een te grote notitie past niet (de oude tekst blijft).
        osAction(owner, abs, "fs_save", "path", home + "/todo.txt",
                "text", "x".repeat(com.senne.starktech.security.os.VirtualFs.MAX_FILE_LENGTH + 1));
        if (!"buy more turrets".equals(fs.get(home + "/todo.txt").content())) {
            throw new GameTestAssertException("Een te grote notitie werd toch bewaard");
        }
        // De eigenaar zelf kan zijn eigen valstrik gewoon openen.
        osAction(owner, abs, "fs_open", "path", home + "/secret/passwords.txt");
        if (!com.senne.starktech.security.os.StarkOs.hasDesktop(owner)) {
            throw new GameTestAssertException("De eigenaar liep in zijn eigen valstrik");
        }

        // Iemand anders op het systeem (zoals straks met het wachtwoord via SSH).
        ServerPlayer guest = makeSurvivalOwner(helper, new BlockPos(12, 3, 14));
        if (!com.senne.starktech.security.os.StarkOs.openFor(guest, core, false)) {
            throw new GameTestAssertException("De tweede speler kwam niet op het systeem");
        }
        osAction(guest, abs, "fs_trap", "path", home + "/todo.txt", "on", "true");
        if (fs.get(home + "/todo.txt").isTrap()) {
            throw new GameTestAssertException("Iemand anders dan de eigenaar kon een valstrik zetten");
        }
        fs.write(home + "/new.txt", "changed"); // zodat de volgende momentopname de mappen weer meestuurt
        var snapshot = com.senne.starktech.security.os.StarkOs.snapshot(core, level,
                com.senne.starktech.security.os.StarkOs.session(guest), false);
        if (!snapshot.contains("fs") || snapshot.toString().contains("t:1b")) {
            throw new GameTestAssertException("Een ander kreeg te zien welk bestand de valstrik is");
        }
        osAction(guest, abs, "fs_open", "path", home + "/todo.txt");
        if (!com.senne.starktech.security.os.StarkOs.hasDesktop(guest)) {
            throw new GameTestAssertException("Een gewoon bestand openen gooide de speler eruit");
        }
        osAction(guest, abs, "fs_open", "path", home + "/secret/passwords.txt");
        if (com.senne.starktech.security.os.StarkOs.hasDesktop(guest)) {
            throw new GameTestAssertException("De valstrik gooide de speler er niet uit");
        }
        if (com.senne.starktech.security.os.StarkOs.openFor(guest, core, false)
                || com.senne.starktech.security.os.StarkOs.lockedSeconds(guest, core) <= 0) {
            throw new GameTestAssertException("Na de valstrik kon de speler meteen weer op het systeem");
        }
        if (core.log().entries().stream().noneMatch(entry -> entry.type() == com.senne.starktech.security.os.EventLog.Type.TRAP)) {
            throw new GameTestAssertException("De valstrik staat niet in het logboek");
        }
        if (!core.isHunted(guest.getUUID()) || core.attackPlayers()
                || level.getServer().isPvpAllowed() && !com.senne.starktech.security.SecurityCores.isIntruder(level, core, guest)) {
            throw new GameTestAssertException("De beveiliging jaagt niet op wie de valstrik opende");
        }
        helper.succeed();
    }

    // ================================================================ De harnas-werkbank

    /** Een werkbank met een volle warmtegenerator ernaast. */
    private static com.senne.starktech.workbench.SuitWorkbenchBlockEntity placeWorkbench(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.SUIT_WORKBENCH.get());
        helper.setBlock(pos.east(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(pos.east())).fill(20_000);
        return (com.senne.starktech.workbench.SuitWorkbenchBlockEntity) helper.getBlockEntity(pos);
    }

    /** Een nep-speler die naast de werkbank staat en NIET gratis bouwt (zoals in survival). */
    @SuppressWarnings("removal")
    private static ServerPlayer benchUser(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        net.minecraft.world.phys.Vec3 spot = helper.absoluteVec(new net.minecraft.world.phys.Vec3(12.5, 3, 10.5));
        player.moveTo(spot.x, spot.y, spot.z);
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        return player;
    }

    /**
     * Op het hologram klikken met de materialen in je rugzak: de werkbank bouwt een borstplaat,
     * en die vliegt naar je toe en gaat meteen aan. Zonder genoeg materiaal gebeurt er niets.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "workbench")
    public static void workbench_builds_a_chestplate(GameTestHelper helper) {
        var bench = placeWorkbench(helper, new BlockPos(12, 3, 12));
        ServerPlayer player = benchUser(helper);
        var inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT, 5));
        inventory.setItem(1, new ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD, 1));
        inventory.setItem(2, new ItemStack(ModItems.STARK_CHIP.get(), 1));
        if (bench.interact(player, com.senne.starktech.workbench.HoloPiece.CHEST)) {
            throw new GameTestAssertException("Het bouwen begon zonder genoeg Stark-chips");
        }
        if (inventory.getItem(0).getCount() != 5) {
            throw new GameTestAssertException("Er ging toch materiaal weg terwijl het bouwen niet begon");
        }
        inventory.setItem(2, new ItemStack(ModItems.STARK_CHIP.get(), 3));
        if (!bench.interact(player, com.senne.starktech.workbench.HoloPiece.CHEST)) {
            throw new GameTestAssertException("Het bouwen begon niet");
        }
        if (!inventory.getItem(0).isEmpty() || inventory.getItem(2).getCount() != 1) {
            throw new GameTestAssertException("De materialen zijn niet (goed) uit de rugzak gehaald");
        }
        helper.succeedWhen(() -> {
            if (!player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(ModItems.STARK_CHESTPLATE.get())) {
                throw new GameTestAssertException("De borstplaat is nog niet naar de speler gevlogen");
            }
        });
    }

    /**
     * Laarzen op het hologram leggen en er een stuwraket-upgrade in bouwen. Een condensator
     * hoort in de borstplaat, dus die past niet in laarzen.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "workbench")
    public static void workbench_installs_upgrades(GameTestHelper helper) {
        var bench = placeWorkbench(helper, new BlockPos(12, 3, 12));
        ServerPlayer player = benchUser(helper);
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        var boots = com.senne.starktech.workbench.HoloPiece.BOOTS;
        player.setItemInHand(hand, new ItemStack(ModItems.STARK_BOOTS.get()));
        if (!bench.interact(player, boots) || !player.getMainHandItem().isEmpty()
                || !bench.laid(boots).is(ModItems.STARK_BOOTS.get())) {
            throw new GameTestAssertException("De laarzen gingen niet op het hologram");
        }
        player.setItemInHand(hand, new ItemStack(ModItems.REPULSOR_CAPACITOR.get()));
        if (bench.interact(player, boots) || player.getMainHandItem().getCount() != 1) {
            throw new GameTestAssertException("Een repulsor-condensator ging in laarzen");
        }
        player.setItemInHand(hand, new ItemStack(ModItems.THRUSTER_UPGRADE.get()));
        if (!bench.interact(player, boots)) {
            throw new GameTestAssertException("Het inbouwen begon niet");
        }
        helper.succeedWhen(() -> {
            if (com.senne.starktech.workbench.SuitUpgrades.of(bench.laid(boots)).thrusters() != 1
                    || !player.getMainHandItem().isEmpty()) {
                throw new GameTestAssertException("De stuwraket-upgrade zit er nog niet in");
            }
        });
    }

    /** Met lege hand op een stuk dat er ligt klikken: het vliegt naar je toe en gaat aan. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "workbench")
    public static void workbench_hands_you_a_piece(GameTestHelper helper) {
        var bench = placeWorkbench(helper, new BlockPos(12, 3, 12));
        ServerPlayer player = benchUser(helper);
        var legs = com.senne.starktech.workbench.HoloPiece.LEGS;
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.STARK_LEGGINGS.get()));
        bench.interact(player, legs);
        if (!bench.interact(player, legs)) {
            throw new GameTestAssertException("De broek pakken lukte niet");
        }
        helper.succeedWhen(() -> {
            if (!player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).is(ModItems.STARK_LEGGINGS.get())
                    || !bench.laid(legs).isEmpty()) {
                throw new GameTestAssertException("De broek is niet naar de speler gevlogen");
            }
        });
    }

    /**
     * Een werkbank uit een oudere versie (met vakjes): het harnasstuk uit het werkvak komt op
     * het hologram, de materialen vallen eruit.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "workbench")
    public static void workbench_empties_an_old_bench(GameTestHelper helper) {
        var bench = placeWorkbench(helper, new BlockPos(12, 3, 12));
        net.minecraft.core.NonNullList<ItemStack> old = net.minecraft.core.NonNullList.withSize(11, ItemStack.EMPTY);
        old.set(0, new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT, 4));
        old.set(9, new ItemStack(ModItems.STARK_CHESTPLATE.get()));
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        net.minecraft.world.ContainerHelper.saveAllItems(tag, old, helper.getLevel().registryAccess());
        bench.loadWithComponents(tag, helper.getLevel().registryAccess());
        if (!bench.laid(com.senne.starktech.workbench.HoloPiece.CHEST).is(ModItems.STARK_CHESTPLATE.get())) {
            throw new GameTestAssertException("De borstplaat uit het werkvak ligt niet op het hologram");
        }
        helper.succeedWhen(() -> helper.assertItemEntityPresent(net.minecraft.world.item.Items.NETHERITE_INGOT,
                new BlockPos(12, 4, 12), 3));
    }

    /** Het harnas maak je aan de werkbank: in de crafting table staat het niet meer. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "workbench")
    public static void suit_is_not_in_the_crafting_table(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        for (var recipe : helper.getLevel().getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
            ItemStack result = recipe.value().getResultItem(registries);
            if (result.getItem() instanceof StarkSuitItem) {
                throw new GameTestAssertException("De crafting table maakt nog steeds " + result.getHoverName().getString()
                        + " (" + recipe.id() + ")");
            }
        }
        helper.succeed();
    }

    /** Stukken harnas stapel je niet: elk stuk is er één (ze hebben hun eigen upgrades). */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "workbench")
    public static void suit_pieces_do_not_stack(GameTestHelper helper) {
        for (var piece : List.of(ModItems.STARK_CHESTPLATE, ModItems.STARK_LEGGINGS, ModItems.STARK_BOOTS)) {
            if (new ItemStack(piece.get()).getMaxStackSize() != 1) {
                throw new GameTestAssertException(piece.getId() + " is nog stapelbaar");
            }
        }
        helper.succeed();
    }

    /** Bepantsering (werkbank) laat het harnas meer schade tegenhouden. */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "workbench")
    public static void armor_plating_stops_more_damage(GameTestHelper helper) {
        ServerPlayer owner = makeSuitOwner(helper, new BlockPos(2, 3, 2));
        wearSuit(owner);
        // Een net binnengekomen speler is 3 seconden onkwetsbaar: even wachten.
        helper.runAfterDelay(SPAWN_INVULNERABLE_TICKS, () -> {
            float without = hurtAndMeasure(owner, 10);
            for (ItemStack piece : owner.getInventory().armor) {
                if (piece.getItem() instanceof StarkSuitItem) {
                    com.senne.starktech.workbench.SuitUpgrades.set(piece, new com.senne.starktech.workbench.SuitUpgrades(0, 2, 0, 0, 0));
                }
            }
            float with = hurtAndMeasure(owner, 10);
            if (without <= 0 || with >= without) {
                throw new GameTestAssertException("Met bepantsering kwam er niet minder schade door (" + with + " tegen " + without + ")");
            }
            helper.succeed();
        });
    }

    // ================================================================ Drone-docks in de hangar

    /**
     * Een drone-dock pakt een drone uit het arsenaal van zijn eigenaar ("In hangar" in het
     * arsenaal) en repareert hem met stroom van het net. Afgebroken: de drone staat niet meer
     * in de hangar.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "drone_dock")
    public static void drone_dock_repairs_its_drone(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        owner.setData(ModAttachments.ARSENAL, EdithArsenal.EMPTY);
        UUID id = ArsenalServer.add(owner);
        ArsenalServer.returnToOrbit(owner, id, 3.0f);
        BlockPos pos = new BlockPos(6, 3, 6);
        helper.setBlock(pos, ModBlocks.DRONE_DOCK.get());
        helper.setBlock(pos.east(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(pos.east())).fill(20_000);
        var dock = (com.senne.starktech.arsenal.DroneDockBlockEntity) helper.getBlockEntity(pos);
        dock.setOwner(owner.getUUID());

        helper.startSequence()
                .thenWaitUntil(() -> {
                    ArsenalDrone drone = ArsenalServer.get(owner).find(id);
                    if (drone == null || !drone.docked() || !dock.present()) {
                        throw new GameTestAssertException("De drone staat nog niet op de dock: " + drone);
                    }
                    if (drone.health() < 3.5f) {
                        throw new GameTestAssertException("De drone wordt niet gerepareerd: " + drone.health());
                    }
                })
                .thenExecute(() -> helper.destroyBlock(pos))
                .thenExecuteAfter(1, () -> {
                    ArsenalDrone drone = ArsenalServer.get(owner).find(id);
                    if (drone == null || drone.docked()) {
                        throw new GameTestAssertException("Na het afbreken staat de drone nog in de hangar: " + drone);
                    }
                })
                .thenSucceed();
    }

    /** Eén drone, twee docks: hij staat maar op één van de twee. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "drone_dock")
    public static void one_drone_one_dock(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        owner.setData(ModAttachments.ARSENAL, EdithArsenal.EMPTY);
        ArsenalServer.add(owner);
        List<com.senne.starktech.arsenal.DroneDockBlockEntity> docks = new java.util.ArrayList<>();
        for (BlockPos pos : List.of(new BlockPos(6, 3, 6), new BlockPos(9, 3, 6))) {
            helper.setBlock(pos, ModBlocks.DRONE_DOCK.get());
            var dock = (com.senne.starktech.arsenal.DroneDockBlockEntity) helper.getBlockEntity(pos);
            dock.setOwner(owner.getUUID());
            docks.add(dock);
        }
        helper.runAfterDelay(40, () -> {
            long taken = docks.stream().filter(com.senne.starktech.arsenal.DroneDockBlockEntity::hasDrone).count();
            if (taken != 1) {
                throw new GameTestAssertException("De drone staat op " + taken + " docks in plaats van 1");
            }
            helper.succeed();
        });
    }

    /**
     * De hangar van de Stark Tower: zes drone-docks langs de noordmuur, en ze krijgen stroom van
     * de warmtegenerator in de hoek (via de kabel langs de muur).
     */
    @GameTest(template = "stark_tower", timeoutTicks = 100, batch = "tower_hangar")
    public static void stark_tower_hangar_has_powered_docks(GameTestHelper helper) {
        helper.runAfterDelay(40, () -> {
            List<BlockPos> docks = new java.util.ArrayList<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 41; x++) {
                for (int z = 0; z < 41; z++) {
                    for (int y = 30; y < 45; y++) {
                        if (helper.getBlockState(pos.set(x, y, z)).is(ModBlocks.DRONE_DOCK.get())) {
                            docks.add(pos.immutable());
                        }
                    }
                }
            }
            if (docks.size() != 6) {
                throw new GameTestAssertException("Er staan " + docks.size() + " drone-docks in de hangar in plaats van 6");
            }
            for (BlockPos dock : docks) {
                if (com.senne.starktech.energy.StarkGrid.available(helper.getLevel(), helper.absolutePos(dock)) <= 0) {
                    throw new GameTestAssertException("De drone-dock op " + dock + " heeft geen stroom");
                }
            }
            helper.succeed();
        });
    }

    // ================================================================ Het AI-brein

    /**
     * Het antwoord van het brein lezen: een verzonnen actie valt weg, een waarde bij een actie
     * zonder waarde ook, en als ze twijfelt ("bedoelde je ...?") doet ze niets. Kapot JSON geeft
     * een fout (dan gebruikt ze het woordenboek).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "brain")
    public static void brain_answer_is_read_safely(GameTestHelper helper) {
        var decision = com.senne.starktech.client.voice.BrainDecision.parse("""
                {"reply": "On it.", "actions": [{"action": "fly_to_the_moon", "value": ""},
                 {"action": "ore_scan", "value": "everything"}, {"action": "navigate_to", "value": "Oak Hill"}],
                 "remember": "The base is called Oak Hill.", "forget": "", "unsure": false}""");
        if (decision.actions().size() != 2
                || decision.actions().get(0).action() != com.senne.starktech.client.voice.VoiceAction.ORE_SCAN
                || !decision.actions().get(0).value().isEmpty()
                || !decision.actions().get(1).value().equals("Oak Hill")) {
            throw new GameTestAssertException("De acties zijn verkeerd gelezen: " + decision.actions());
        }
        var unsure = com.senne.starktech.client.voice.BrainDecision.parse("""
                {"reply": "Did you mean the ore scan?", "actions": [{"action": "ore_scan", "value": ""}],
                 "remember": "", "forget": "", "unsure": true}""");
        if (!unsure.actions().isEmpty()) {
            throw new GameTestAssertException("Bij twijfel voerde ze toch een actie uit");
        }
        try {
            com.senne.starktech.client.voice.BrainDecision.parse("Sure! Launching drones.");
            throw new GameTestAssertException("Geen JSON werd niet geweigerd");
        } catch (IllegalArgumentException expected) {
            // goed zo
        }
        helper.succeed();
    }

    /**
     * Ze verstaat alleen Engels: het oor zegt in welke taal je praatte, en een andere taal voert
     * ze niet uit (behalve Engels met een accent, dat het oor voor Nederlands aanzag). Een oud
     * woordenboek raakt zijn Nederlandse woorden kwijt, maar houdt je eigen Engelse zinnen.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "brain")
    public static void brain_only_understands_english(GameTestHelper helper) {
        Object[][] cases = {
                {"language Dutch<asr_text>Hoeveel energie heb ik nog?", false},
                {"language English<asr_text>Send the drones after that zombie.", true},
                {"language Dutch<asr_text>Send the drones.", true},     // Engels met een accent
                {"language French<asr_text>Bonjour Edith.", false},
                {"language None<asr_text>", true},                      // niets gezegd: gewoon leeg
                {"Launch drones", true},                                // zonder taal: Engels
        };
        for (Object[] test : cases) {
            var heard = com.senne.starktech.client.voice.EnglishOnly.parse((String) test[0]);
            if (com.senne.starktech.client.voice.EnglishOnly.accepted(heard) != (Boolean) test[1]) {
                throw new GameTestAssertException("\"" + test[0] + "\" werd " + (((Boolean) test[1]) ? "geweigerd" : "aangenomen"));
            }
        }
        if (!com.senne.starktech.client.voice.EnglishOnly.parse("language English<asr_text> Lights on. ").text().equals("Lights on.")) {
            throw new GameTestAssertException("De tekst na <asr_text> werd verkeerd gelezen");
        }

        // Een woordenboek uit versie 2: Nederlandse synoniemen, een Nederlandse zin en een
        // geleerde Nederlandse zin. Die moeten weg; de rest blijft.
        var dictionary = com.senne.starktech.client.voice.VoiceDictionary.parse("""
                {"version": 2,
                 "commands": [
                   {"name": "Never mind", "actions": [], "phrases": ["nee", "cancel", "my own phrase"]},
                   {"name": "Learned: night_vision_on", "actions": [], "phrases": ["zet het licht aan"]}],
                 "synonyms": [{"word": "drones", "synonyms": ["droontjes", "robots"]},
                              {"word": "map", "synonyms": ["plattegrond"]}],
                 "ignore": []}""");
        int removed = com.senne.starktech.client.voice.EnglishOnly.strip(dictionary);
        if (removed != 4 || !dictionary.commands.getFirst().phrases.equals(java.util.List.of("cancel", "my own phrase"))
                || dictionary.commands.size() != 1 || dictionary.synonyms.size() != 1
                || !dictionary.synonyms.getFirst().synonyms().equals(java.util.List.of("robots"))) {
            throw new GameTestAssertException("Opschonen ging mis (" + removed + " weg): " + dictionary.toJson());
        }
        // En het standaard-woordenboek zelf heeft geen Nederlands meer.
        try {
            var standard = com.senne.starktech.client.voice.VoiceDictionary.loadDefault();
            if (com.senne.starktech.client.voice.EnglishOnly.strip(standard) != 0) {
                throw new GameTestAssertException("Het standaard-woordenboek bevat nog Nederlandse woorden");
            }
        } catch (java.io.IOException e) {
            throw new GameTestAssertException("Standaard-woordenboek niet gevonden: " + e);
        }
        helper.succeed();
    }

    // ================================================================ Het landingsplatform

    /**
     * Sta je op je eigen landingsplatform, dan laadt je bril op en wordt je harnas gerepareerd.
     * Het platform staat op je lijst (voor de HUD) en gaat eraf als hij wordt afgebroken.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "landing_pad")
    public static void landing_pad_charges_and_repairs(GameTestHelper helper) {
        BlockPos padPos = new BlockPos(6, 2, 6);
        helper.setBlock(padPos, ModBlocks.LANDING_PAD.get());
        helper.setBlock(padPos.below(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(padPos.below())).fill(20_000);
        ServerPlayer owner = makeSuitOwner(helper, padPos.above());
        wearSuit(owner);
        ItemStack glasses = EdithData.getWornGlasses(owner);
        EdithData.setEnergy(glasses, 100);
        ItemStack chest = owner.getItemBySlot(EquipmentSlot.CHEST);
        chest.setDamageValue(50);
        var pad = (com.senne.starktech.suit.LandingPadBlockEntity) helper.getBlockEntity(padPos);
        pad.setOwner(owner.getUUID());
        net.minecraft.core.GlobalPos here = net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(),
                helper.absolutePos(padPos));

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!com.senne.starktech.suit.LandingPads.of(owner).pads().contains(here)) {
                        throw new GameTestAssertException("Het platform staat niet op de lijst van de eigenaar");
                    }
                    if (com.senne.starktech.suit.LandingPadBlockEntity.padUnder(owner) != pad) {
                        throw new GameTestAssertException("Het platform ziet niet dat de eigenaar erop staat");
                    }
                    if (EdithData.getEnergy(EdithData.getWornGlasses(owner)) < 100 + SuitBalance.PAD_CHARGE_PER_SECOND) {
                        throw new GameTestAssertException("De bril laadt niet op: " + EdithData.getEnergy(EdithData.getWornGlasses(owner)));
                    }
                    if (owner.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() >= 50) {
                        throw new GameTestAssertException("Het harnas wordt niet gerepareerd");
                    }
                })
                .thenExecute(() -> helper.destroyBlock(padPos))
                .thenExecuteAfter(1, () -> {
                    if (com.senne.starktech.suit.LandingPads.of(owner).pads().contains(here)) {
                        throw new GameTestAssertException("Het afgebroken platform staat nog op de lijst");
                    }
                })
                .thenSucceed();
    }

    /**
     * Het platform heeft een eigen batterij: die vult hij met wat het net over heeft, en daaruit
     * laadt hij je bril sneller op dan alleen uit het net. Batterijcellen maken hem groter.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "landing_pad")
    public static void landing_pad_battery_charges_faster(GameTestHelper helper) {
        BlockPos padPos = new BlockPos(6, 2, 6);
        helper.setBlock(padPos, ModBlocks.LANDING_PAD.get());
        helper.setBlock(padPos.below(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(padPos.below())).fill(20_000);
        var pad = (com.senne.starktech.suit.LandingPadBlockEntity) helper.getBlockEntity(padPos);
        int base = pad.capacity();
        pad.cells().setItem(0, new ItemStack(ModItems.BATTERY_UPGRADE.get(), 2));
        if (pad.capacity() != base + 2 * SuitBalance.PAD_BATTERY_PER_CELL) {
            throw new GameTestAssertException("Twee batterijcellen maakten de batterij niet groter: " + pad.capacity());
        }
        ServerPlayer[] owner = new ServerPlayer[1];
        int[] before = new int[1];
        helper.startSequence()
                // Niemand op het platform: de batterij vult zich uit het overschot van het net.
                .thenWaitUntil(() -> {
                    if (pad.energy() < 2 * SuitBalance.PAD_BATTERY_FILL) {
                        throw new GameTestAssertException("De batterij vult niet: " + pad.energy());
                    }
                })
                .thenExecute(() -> {
                    owner[0] = makeSuitOwner(helper, padPos.above());
                    pad.setOwner(owner[0].getUUID());
                    EdithData.setEnergy(EdithData.getWornGlasses(owner[0]), 100);
                    before[0] = pad.energy();
                })
                // Er loopt stroom uit de batterij naar je bril, meer dan het net alleen per seconde geeft.
                .thenWaitUntil(() -> {
                    int glasses = EdithData.getEnergy(EdithData.getWornGlasses(owner[0]));
                    if (glasses < 100 + SuitBalance.PAD_CHARGE_PER_SECOND + 200 || pad.energy() >= before[0]) {
                        throw new GameTestAssertException("De bril laadt niet sneller op uit de batterij: " + glasses);
                    }
                })
                .thenSucceed();
    }

    /** Het platform van iemand anders doet niets voor jou (en staat niet op jouw lijst). */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "landing_pad")
    public static void landing_pad_ignores_strangers(GameTestHelper helper) {
        BlockPos padPos = new BlockPos(6, 2, 6);
        helper.setBlock(padPos, ModBlocks.LANDING_PAD.get());
        helper.setBlock(padPos.below(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(padPos.below())).fill(20_000);
        ServerPlayer stranger = makeSuitOwner(helper, padPos.above());
        EdithData.setEnergy(EdithData.getWornGlasses(stranger), 100);
        ((com.senne.starktech.suit.LandingPadBlockEntity) helper.getBlockEntity(padPos)).setOwner(UUID.randomUUID());
        helper.runAfterDelay(45, () -> {
            if (EdithData.getEnergy(EdithData.getWornGlasses(stranger)) != 100) {
                throw new GameTestAssertException("Het platform van een ander laadde de bril op");
            }
            if (!com.senne.starktech.suit.LandingPads.of(stranger).pads().isEmpty()) {
                throw new GameTestAssertException("Het platform van een ander staat op jouw lijst");
            }
            helper.succeed();
        });
    }

    /**
     * Het dak van de Stark Tower: één landingsplatform, en het heeft stroom van de generator
     * eronder.
     */
    @GameTest(template = "stark_tower", timeoutTicks = 100, batch = "tower_pad")
    public static void stark_tower_has_a_powered_landing_pad(GameTestHelper helper) {
        helper.runAfterDelay(40, () -> {
            List<BlockPos> pads = new java.util.ArrayList<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 41; x++) {
                for (int z = 0; z < 41; z++) {
                    for (int y = 90; y < 110; y++) {
                        if (helper.getBlockState(pos.set(x, y, z)).is(ModBlocks.LANDING_PAD.get())) {
                            pads.add(pos.immutable());
                        }
                    }
                }
            }
            if (pads.size() != 1) {
                throw new GameTestAssertException("Er liggen " + pads.size() + " landingsplatforms op het dak in plaats van 1");
            }
            if (com.senne.starktech.energy.StarkGrid.available(helper.getLevel(), helper.absolutePos(pads.getFirst())) <= 0) {
                throw new GameTestAssertException("Het landingsplatform heeft geen stroom");
            }
            helper.succeed();
        });
    }

    // ================================================================ De Hydra-bunker
    // In de test ligt alles één blok hoger dan in tools/generate_bunker.py (y + 1).

    /** Telt in de hele bunker de blokken waarvoor {@code test} klopt. */
    private static int countInBunker(GameTestHelper helper, java.util.function.Predicate<BlockPos> test) {
        int count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 35; x++) {
            for (int y = 1; y <= 8; y++) {
                for (int z = 0; z < 44; z++) {
                    if (test.test(pos.set(x, y, z))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * De kluis heeft twee kisten met Hydra-buit, er staan vijf Hydra-torentjes, en op acht plekken
     * een markering waar bij het maken van de wereld een soldaat komt.
     */
    @GameTest(template = "hydra_bunker", timeoutTicks = 60, batch = "bunker")
    public static void hydra_bunker_vault_and_guards(GameTestHelper helper) {
        for (BlockPos pos : List.of(new BlockPos(13, 2, 42), new BlockPos(21, 2, 42))) {
            helper.assertBlockPresent(Blocks.CHEST, pos);
            ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(pos);
            if (chest.getLootTable() == null || !chest.getLootTable().location().getPath().endsWith("hydra_vault")) {
                throw new GameTestAssertException("Een kluiskist heeft niet de goede buit: " + chest.getLootTable());
            }
        }
        int turrets = countInBunker(helper, pos -> helper.getBlockState(pos).is(ModBlocks.HYDRA_TURRET.get()));
        if (turrets != 6) {
            throw new GameTestAssertException("Er staan " + turrets + " Hydra-torentjes in plaats van 6");
        }
        int soldiers = countInBunker(helper, pos -> helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof net.minecraft.world.level.block.entity.StructureBlockEntity marker
                && com.senne.starktech.hydra.HydraBunkerPiece.SOLDIER.equals(marker.getMetaData()));
        if (soldiers != 8) {
            throw new GameTestAssertException("Er zijn " + soldiers + " plekken voor een soldaat in plaats van 8");
        }
        helper.succeed();
    }

    /**
     * Alle Hydra-torentjes hangen via verborgen kabels aan de stroomkast in de reactorkamer, en
     * hebben dus stroom.
     */
    @GameTest(template = "hydra_bunker", timeoutTicks = 100, batch = "bunker")
    public static void hydra_bunker_turrets_have_power(GameTestHelper helper) {
        helper.runAfterDelay(40, () -> {
            int cabinets = countInBunker(helper, pos -> helper.getBlockState(pos).is(ModBlocks.POWER_CABINET.get()));
            if (cabinets != 1) {
                throw new GameTestAssertException("Er staan " + cabinets + " stroomkasten in de bunker in plaats van 1");
            }
            List<BlockPos> dark = new java.util.ArrayList<>();
            countInBunker(helper, pos -> {
                if (helper.getBlockState(pos).is(ModBlocks.HYDRA_TURRET.get())
                        && com.senne.starktech.energy.StarkGrid.available(helper.getLevel(), helper.absolutePos(pos))
                        < com.senne.starktech.turret.TurretBalance.LASER_COST) {
                    dark.add(pos.immutable());
                }
                return false;
            });
            if (!dark.isEmpty()) {
                throw new GameTestAssertException("Deze Hydra-torentjes hebben geen stroom: " + dark);
            }
            helper.succeed();
        });
    }

    /**
     * De laserwanden in de kluisgang (twee wanden van 3 x 3) gaan aan met stroom uit de reactor.
     * Sloop je de twee warmtegeneratoren, dan valt de stroom weg en gaan ze uit: zo kom je erdoor.
     */
    @GameTest(template = "hydra_bunker", timeoutTicks = 400, batch = "bunker")
    public static void hydra_bunker_lasers_need_the_reactor(GameTestHelper helper) {
        java.util.function.IntSupplier walls = () -> countInBunker(helper,
                pos -> helper.getBlockState(pos).is(ModBlocks.LASER_WALL.get()));
        // Net als in de wereld: de zenders gaan aan bij een willekeurige tik; die doen we na.
        helper.runAfterDelay(40, () -> countInBunker(helper, pos -> {
            BlockState state = helper.getBlockState(pos);
            if (state.is(ModBlocks.LASER_EMITTER.get())) {
                state.randomTick(helper.getLevel(), helper.absolutePos(pos), helper.getLevel().random);
            }
            return false;
        }));
        boolean[] reactorGone = {false};
        helper.onEachTick(() -> {
            if (!reactorGone[0] && walls.getAsInt() == 18) {
                reactorGone[0] = true;
                helper.setBlock(new BlockPos(30, 2, 20), Blocks.AIR);
                helper.setBlock(new BlockPos(30, 2, 22), Blocks.AIR);
            }
        });
        helper.succeedWhen(() -> {
            if (!reactorGone[0]) {
                throw new GameTestAssertException("De laserwanden staan nog niet allemaal aan (" + walls.getAsInt() + " van 18)");
            }
            if (walls.getAsInt() > 0) {
                throw new GameTestAssertException("Zonder reactor staan er nog " + walls.getAsInt() + " laserblokken");
            }
        });
    }

    /**
     * De lasergang in de Stark Tower gaat aan met stroom uit de verborgen krachtcentrale: na het
     * neerzetten van de toren moeten er laserwanden in de gang staan (acht rijen van drie).
     */
    @GameTest(template = "stark_tower", timeoutTicks = 100, batch = "tower_power")
    public static void stark_tower_security_has_power(GameTestHelper helper) {
        helper.runAfterDelay(40, () -> {
            var level = helper.getLevel();
            List<BlockPos> security = new java.util.ArrayList<>();
            BlockPos cabinet = null;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 41; x++) {
                for (int z = 0; z < 55; z++) {
                    for (int y = 0; y < 100; y++) {
                        BlockState state = helper.getBlockState(pos.set(x, y, z));
                        if (state.is(ModBlocks.SECURITY_TURRET.get()) || state.is(ModBlocks.SECURITY_CORE.get())) {
                            security.add(pos.immutable());
                        } else if (state.is(ModBlocks.POWER_CABINET.get())) {
                            cabinet = pos.immutable();
                        }
                    }
                }
            }
            if (cabinet == null) {
                throw new GameTestAssertException("Er staat geen stroomkast in de krachtcentrale");
            }
            if (security.size() != 10) {
                throw new GameTestAssertException("Er staan " + security.size() + " torentjes + kern in plaats van 10");
            }
            List<BlockPos> dark = security.stream().filter(p -> com.senne.starktech.energy.StarkGrid.available(level,
                    helper.absolutePos(p)) < com.senne.starktech.turret.TurretBalance.LASER_COST).toList();
            if (!dark.isEmpty()) {
                throw new GameTestAssertException("Deze beveiliging heeft geen stroom: " + dark);
            }
            // De schakelaar van de beveiliging (de noordkant van de kast) uit: dan valt de kern uit.
            com.senne.starktech.energy.PowerCabinets.setSwitch(level, helper.absolutePos(cabinet), Direction.NORTH, false);
            BlockPos core = security.stream().filter(p -> helper.getBlockState(p).is(ModBlocks.SECURITY_CORE.get()))
                    .findFirst().orElseThrow();
            if (com.senne.starktech.energy.StarkGrid.available(level, helper.absolutePos(core)) > 0) {
                throw new GameTestAssertException("Met de schakelaar uit heeft de beveiligingskern nog stroom");
            }
            helper.succeed();
        });
    }

    /**
     * De bewakingscamera's van de Stark Tower: zes stuks (drie op de beveiligingsverdieping, de
     * lobby, de lasergang en de kluis), elk met stroom en een naam, en de kern van de toren (met
     * een IP, maar zonder wachtwoord: Tony's oude systeem) ziet ze alle zes.
     */
    @GameTest(template = "stark_tower", timeoutTicks = 100, batch = "tower_cameras")
    public static void stark_tower_has_powered_cameras(GameTestHelper helper) {
        helper.runAfterDelay(40, () -> {
            var level = helper.getLevel();
            List<BlockPos> cameras = new java.util.ArrayList<>();
            BlockPos core = null;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 41; x++) {
                for (int z = 0; z < 55; z++) {
                    for (int y = 0; y < 100; y++) {
                        BlockState state = helper.getBlockState(pos.set(x, y, z));
                        if (state.is(ModBlocks.SECURITY_CAMERA.get())) {
                            cameras.add(pos.immutable());
                        } else if (state.is(ModBlocks.SECURITY_CORE.get())) {
                            core = pos.immutable();
                        }
                    }
                }
            }
            if (cameras.size() != 6 || core == null) {
                throw new GameTestAssertException("Er staan " + cameras.size() + " camera's (en " + (core == null ? "geen" : "een")
                        + " kern) in de toren in plaats van 6");
            }
            List<BlockPos> dark = cameras.stream().filter(p -> com.senne.starktech.energy.StarkGrid.available(level,
                    helper.absolutePos(p)) < com.senne.starktech.security.SecurityBalance.CAMERA_UPKEEP).toList();
            if (!dark.isEmpty()) {
                throw new GameTestAssertException("Deze camera's hebben geen stroom: " + dark);
            }
            var coreEntity = (com.senne.starktech.security.SecurityCoreBlockEntity) helper.getBlockEntity(core);
            coreEntity.ensureSystem(level);
            if (coreEntity.ip().isEmpty() || coreEntity.hasPassword() || !coreEntity.hostname().equals("stark-sec-core")) {
                throw new GameTestAssertException("De kern van de toren heeft geen IP, of al een wachtwoord");
            }
            java.util.Set<String> names = new java.util.HashSet<>();
            for (var tag : com.senne.starktech.security.SecurityDevices.cameras(level, coreEntity)) {
                names.add(((net.minecraft.nbt.CompoundTag) tag).getString("name"));
            }
            if (!names.equals(java.util.Set.of("Lobby", "Laser hall", "Vault", "Security NW", "Security SE", "Security SW"))) {
                throw new GameTestAssertException("De kern ziet deze camera's: " + names);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "stark_tower", timeoutTicks = 300, batch = "tower_power")
    public static void stark_tower_lasers_have_power(GameTestHelper helper) {
        // Een toren uit de wereldgeneratie krijgt geen "je bent geplaatst"-seintje: de zenders
        // gaan aan bij een willekeurige tik (gemiddeld om de minuut). Die doen we hier na, zodra
        // de krachtcentrale een beetje stroom heeft.
        helper.runAfterDelay(40, () -> {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 41; x++) {
                for (int z = 0; z < 41; z++) {
                    for (int y = 64; y < 74; y++) {
                        BlockState state = helper.getBlockState(pos.set(x, y, z));
                        if (state.is(ModBlocks.LASER_EMITTER.get())) {
                            state.randomTick(helper.getLevel(), helper.absolutePos(pos), helper.getLevel().random);
                        }
                    }
                }
            }
        });
        helper.succeedWhen(() -> {
            int walls = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 41; x++) {
                for (int z = 0; z < 41; z++) {
                    for (int y = 64; y < 74; y++) {
                        if (helper.getBlockState(pos.set(x, y, z)).is(ModBlocks.LASER_WALL.get())) {
                            walls++;
                        }
                    }
                }
            }
            if (walls < 24) {
                throw new GameTestAssertException("Pas " + walls + " van de 24 laserwanden staan aan");
            }
        });
    }

    /**
     * De val in de kluis: komt er iets op de drukplaat voor de sokkel (hier een ijzerstaaf), dan
     * schieten er uit de vloer eromheen vier laserzuilen omhoog: een kooi. Ze draaien op stroom
     * van de krachtcentrale, via een kabel door de muur van de lasergang.
     */
    @GameTest(template = "stark_tower", timeoutTicks = 200, batch = "tower_trap")
    public static void stark_tower_vault_trap(GameTestHelper helper) {
        // De krachtcentrale eerst even laten opwarmen, dan de staaf op de plaat.
        helper.runAfterDelay(20, () -> helper.spawnItem(Items.IRON_INGOT, 20.5f, 79.1f, 23.5f));
        helper.succeedWhen(() -> {
            for (BlockPos column : List.of(new BlockPos(19, 80, 23), new BlockPos(21, 80, 23),
                    new BlockPos(20, 80, 22), new BlockPos(20, 80, 24))) {
                helper.assertBlockPresent(ModBlocks.LASER_WALL.get(), column);
            }
        });
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

    /**
     * De schuivende laser trekt van het beginpunt naar het eindpunt dat je koos: een vijand die
     * aan het eind staat (en niet aan het begin) wordt geraakt, en een vijand ver achter het
     * eindpunt niet (de laser schiet er niet overheen).
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "satellite_sweep")
    public static void satellite_sweep_goes_from_start_to_end(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(0, 3, 0));
        Husk atEnd = spawnDummyEnemy(helper, new BlockPos(14, 3, 12));
        Husk beyond = spawnDummyEnemy(helper, new BlockPos(14, 3, 22));
        Vec3 start = helper.absoluteVec(new Vec3(14.5, 3, 2.5));
        Vec3 end = atEnd.position();
        SatelliteStrikes.start(helper.getLevel(), start, owner.getUUID(), SatelliteMode.SWEEP, null, end);
        helper.succeedWhen(() -> {
            if (atEnd.getHealth() >= atEnd.getMaxHealth()) {
                throw new GameTestAssertException("De laser kwam niet bij het eindpunt");
            }
            if (SatelliteStrikes.pendingFor(owner.getUUID()) != null) {
                throw new GameTestAssertException("De laser is nog bezig");
            }
            if (beyond.getHealth() < beyond.getMaxHealth()) {
                throw new GameTestAssertException("De laser schoot voorbij het eindpunt");
            }
        });
    }

    // skyAccess: zonder dat krijgt een testarena een onzichtbaar dak van barrier-blokken, en daar
    // zou de raket op inslaan (zoals hij in het echt op een dak inslaat).
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "satellite", skyAccess = true)
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
     * Een rayshield klapt niet in één keer aan: hij schuift uit de zender naar voren (altijd van
     * één kant, dus steeds een aaneengesloten stuk vanaf de zender), in de kleur van de zender.
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
        // Stroom (een volle warmtegenerator erboven), en dan aanzetten met redstone eronder.
        helper.setBlock(emitter.above(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(emitter.above())).fill(5_000);
        helper.setBlock(emitter.below(), Blocks.REDSTONE_BLOCK);

        // Meteen daarna mag hij nog niet dicht zijn: hij hoort te schuiven.
        int immediately = countWall(helper, emitter, 4);
        if (immediately == 4) {
            throw new GameTestAssertException("Het veld stond meteen helemaal dicht (geen animatie)");
        }
        // Elke tick: wat er staat, begint bij de zender en heeft geen gaten.
        helper.onEachTick(() -> {
            int count = countWall(helper, emitter, 4);
            for (int i = 1; i <= count; i++) {
                if (!helper.getBlockState(emitter.east(i)).is(ModBlocks.LASER_WALL.get())) {
                    throw new GameTestAssertException("Het veld groeit niet vanaf de zender (gat op plek " + i + ")");
                }
            }
        });
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

    /**
     * Zenders die tegen elkaar aan zitten zijn één laserpoort: redstone op de onderste en stroom
     * op de bovenste zet alle drie aan (een wand van drie hoog), en zonder redstone gaan ze
     * allemaal weer uit.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "laser_group")
    public static void touching_laser_emitters_are_one_gate(GameTestHelper helper) {
        BlockPos bottom = new BlockPos(1, 2, 8);
        for (int i = 0; i < 3; i++) {
            helper.setBlock(bottom.above(i), ModBlocks.LASER_EMITTER.get().defaultBlockState()
                    .setValue(LaserEmitterBlock.FACING, Direction.EAST));
        }
        // Stroom alleen bij de bovenste (de zenders geven hem door), redstone alleen bij de onderste.
        helper.setBlock(bottom.above(3), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(bottom.above(3))).fill(5_000);
        helper.setBlock(bottom.west(), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (int i = 0; i < 3; i++) {
                        if (countWall(helper, bottom.above(i), 4) < 4) {
                            throw new GameTestAssertException("Rij " + (i + 1) + " van de laserpoort is niet dicht");
                        }
                    }
                })
                .thenExecute(() -> helper.setBlock(bottom.west(), Blocks.AIR))
                .thenWaitUntil(() -> {
                    for (int i = 0; i < 3; i++) {
                        if (countWall(helper, bottom.above(i), 4) > 0) {
                            throw new GameTestAssertException("Rij " + (i + 1) + " ging niet mee uit");
                        }
                    }
                })
                .thenSucceed();
    }

    /**
     * Twee zenders tegenover elkaar die allebei aan staan, delen één wand (die maar van één kant
     * komt). Gaat er één uit, dan blijft de wand staan zolang de andere nog aan is.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "voice")
    public static void rayshield_shared_by_two_emitters(GameTestHelper helper) {
        BlockPos emitter = new BlockPos(1, 3, 1);
        BlockPos far = emitter.east(5);
        helper.setBlock(emitter, ModBlocks.LASER_EMITTER.get().defaultBlockState()
                .setValue(LaserEmitterBlock.FACING, Direction.EAST));
        helper.setBlock(far, ModBlocks.LASER_EMITTER.get().defaultBlockState()
                .setValue(LaserEmitterBlock.FACING, Direction.WEST));
        for (BlockPos each : List.of(emitter, far)) {
            helper.setBlock(each.above(), ModBlocks.HEAT_GENERATOR.get());
            ((ChargerBlockEntity) helper.getBlockEntity(each.above())).fill(5_000);
            helper.setBlock(each.below(), Blocks.REDSTONE_BLOCK);
        }
        helper.runAfterDelay(40, () -> {
            if (countWall(helper, emitter, 4) < 4) {
                throw new GameTestAssertException("De wand tussen de twee zenders stond er niet");
            }
            helper.setBlock(emitter.below(), Blocks.AIR); // de eerste zender gaat uit
            helper.runAfterDelay(40, () -> {
                if (countWall(helper, emitter, 4) < 4) {
                    throw new GameTestAssertException("De wand ging weg terwijl de zender aan de overkant nog aan stond");
                }
                helper.succeed();
            });
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
            // Zolang hij brandt, gloeit zijn rooster (en geeft hij licht).
            if (!helper.getBlockState(pos).getValue(HeatGeneratorBlock.LIT)) {
                throw new GameTestAssertException("Het rooster gloeit niet terwijl hij brandt");
            }
        });
    }

    // ================================================================ Fase 15: raids

    /** Een golf raiddrones komt uit de lucht op je af en kiest jou als doelwit. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "raids")
    public static void raid_drones_come_for_you(GameTestHelper helper) {
        EdithRaids.clear();
        // Midden in de arena: de drones komen tot 8 blokken naast je binnen, en aan de rand van
        // de arena zou dat buiten de muren van de test zijn.
        ServerPlayer owner = makeSurvivalOwner(helper, new BlockPos(12, 3, 12));

        int spawned = EdithRaids.startRaid(owner, 3);
        if (spawned != 3) {
            throw new GameTestAssertException("Er kwamen " + spawned + " drones in plaats van 3");
        }
        List<RaidDroneEntity> drones = helper.getLevel().getEntitiesOfClass(RaidDroneEntity.class,
                owner.getBoundingBox().inflate(RaidBalance.ARRIVAL_HEIGHT + 32));
        if (drones.size() != 3) {
            throw new GameTestAssertException("Niet alle drones zijn te vinden: " + drones.size());
        }
        // Ze horen uit de lucht te komen: zo hoog als de ruimte toelaat (in deze arena zit een
        // plafond, net als bij de test voor je eigen drones) en duikend. Nooit hoger dan
        // ARRIVAL_HEIGHT, en nooit dwars door dat plafond heen.
        for (RaidDroneEntity drone : drones) {
            double above = drone.getY() - owner.getY();
            if (above < 6) {
                throw new GameTestAssertException("Een raiddrone begon maar " + Math.round(above) + " blokken hoog");
            }
            if (above > RaidBalance.ARRIVAL_HEIGHT + RaidBalance.HOVER_ABOVE + 2) {
                throw new GameTestAssertException("Een raiddrone begon " + Math.round(above)
                        + " blokken hoog, hoger dan hij ooit mag");
            }
        }

        // Even laten vallen; daarna horen ze jou op de korrel te hebben.
        helper.succeedWhen(() -> {
            for (RaidDroneEntity drone : helper.getLevel().getEntitiesOfClass(RaidDroneEntity.class,
                    owner.getBoundingBox().inflate(RaidBalance.ARRIVAL_HEIGHT + 32))) {
                if (drone.isDiving()) {
                    throw new GameTestAssertException("Er duikt er nog een");
                }
                if (drone.getTarget() != owner) {
                    throw new GameTestAssertException("Een raiddrone heeft jou niet als doelwit");
                }
            }
        });
    }

    /**
     * Met de gamerule aan breekt een raiddrone zich een weg naar je toe als je je verstopt.
     *
     * De speler zit dicht ingemetseld, zodat de drone hem nooit kan zien en wel moet slopen.
     * De twee gamerule-tests staan elk in een eigen groep: de regel geldt voor de hele wereld,
     * en tests in dezelfde groep draaien tegelijk.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "raids_grief_on")
    public static void raid_drone_breaks_in_when_allowed(GameTestHelper helper) {
        EdithRaids.clear();
        setRaidGriefing(helper, true);
        ServerPlayer owner = makeSurvivalOwner(helper, new BlockPos(12, 3, 12));
        List<BlockPos> shell = encloseInStone(helper, new BlockPos(12, 3, 12));
        spawnHunter(helper, owner, new BlockPos(12, 8, 18));

        helper.succeedWhen(() -> {
            for (BlockPos pos : shell) {
                if (helper.getBlockState(pos).isAir()) {
                    return; // er is een gat: hij is aan het inbreken
                }
            }
            throw new GameTestAssertException("De drone heeft nog geen blok weggehaald");
        });
    }

    /** Met de gamerule uit blijft je schuilplaats heel, hoe lang de drone ook zoekt. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "raids_grief_off")
    public static void raid_drone_leaves_walls_when_forbidden(GameTestHelper helper) {
        EdithRaids.clear();
        setRaidGriefing(helper, false);
        ServerPlayer owner = makeSurvivalOwner(helper, new BlockPos(12, 3, 12));
        List<BlockPos> shell = encloseInStone(helper, new BlockPos(12, 3, 12));
        spawnHunter(helper, owner, new BlockPos(12, 8, 18));

        helper.runAfterDelay(200, () -> {
            // Eerst terugzetten, zodat een mislukte test de rest niet meesleept.
            setRaidGriefing(helper, true);
            for (BlockPos pos : shell) {
                helper.assertBlockPresent(Blocks.STONE, pos);
            }
            helper.succeed();
        });
    }

    private static void setRaidGriefing(GameTestHelper helper, boolean allowed) {
        helper.getLevel().getGameRules().getRule(ModGameRules.RAID_GRIEFING)
                .set(allowed, helper.getLevel().getServer());
    }

    /** Metselt een speler helemaal in: steen om voeten en hoofd heen, met een vloer en een dak. */
    private static List<BlockPos> encloseInStone(GameTestHelper helper, BlockPos feet) {
        List<BlockPos> shell = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    boolean inside = dx == 0 && dz == 0 && (dy == 0 || dy == 1);
                    if (!inside) {
                        BlockPos pos = feet.offset(dx, dy, dz);
                        helper.setBlock(pos, Blocks.STONE);
                        shell.add(pos);
                    }
                }
            }
        }
        return shell;
    }

    /** Een raiddrone op een vaste plek, meteen op deze speler gericht. */
    private static void spawnHunter(GameTestHelper helper, ServerPlayer target, BlockPos at) {
        Vec3 where = helper.absoluteVec(Vec3.atCenterOf(at));
        RaidDroneEntity drone = RaidDroneEntity.spawn(helper.getLevel(), where, where);
        if (drone == null) {
            throw new GameTestAssertException("De raiddrone verscheen niet");
        }
        drone.setTarget(target);
    }

    // ================================================================ Fase 15: prestaties

    /** Alle prestaties van de mod, in de volgorde van de boom. */
    private static final String[] ADVANCEMENTS = {
        "root", "charging_station", "real_power", "find_tower", "glasses",
        "first_drone", "swarm", "drone_down", "satellite", "relay",
        "rayshield", "illusion", "holo_table", "suit", "fly", "repulsor"
    };

    /**
     * Elke prestatie moet inladen.
     *
     * Een prestatie met een fout erin wordt door Minecraft stilletjes overgeslagen: je ziet
     * alleen een regel in het log en daarna ontbreekt hij gewoon. Deze test valt dan om.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "advancements")
    public static void all_advancements_load(GameTestHelper helper) {
        ServerAdvancementManager manager = helper.getLevel().getServer().getAdvancements();
        for (String name : ADVANCEMENTS) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, name);
            if (manager.get(id) == null) {
                throw new GameTestAssertException("Prestatie ontbreekt of laadde niet: " + id);
            }
        }
        helper.succeed();
    }

    /**
     * De prestaties die de mod zelf toekent, werken.
     *
     * Die hebben een voorwaarde die nooit vanzelf uitkomt, dus als het toekennen niet werkt,
     * krijg je ze nooit en merkt niemand dat.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "advancements")
    public static void code_awarded_advancements_work(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, StarkAdvancements.FIRST_DRONE));
        if (holder == null) {
            throw new GameTestAssertException("De prestatie voor je eerste drone bestaat niet");
        }
        if (owner.getAdvancements().getOrStartProgress(holder).isDone()) {
            throw new GameTestAssertException("Hij stond al aan voordat er iets gebeurde");
        }
        StarkAdvancements.award(owner, StarkAdvancements.FIRST_DRONE);
        if (!owner.getAdvancements().getOrStartProgress(holder).isDone()) {
            throw new GameTestAssertException("Toekennen werkte niet");
        }
        helper.succeed();
    }

    /**
     * Een bril doorgeven zonder dat de nieuwe drager eerst zijn eigen bril afzet: hij verwisselt
     * ze direct (zoals met rechtsklik). Ook dan moet hij de nieuwe eigenaar worden. Vroeger telde
     * alleen "eerst niets op, dan een bril", en bleef de bril van de vorige eigenaar.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "glasses")
    public static void swapped_glasses_change_owner(GameTestHelper helper) {
        ServerPlayer player = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        EdithServer.onPlayerTick(new PlayerTickEvent.Post(player)); // hij heeft zijn eigen bril op
        ItemStack released = new ItemStack(ModItems.EDITH_GLASSES.get());
        EdithData.setOwner(released, new EdithOwner(UUID.randomUUID(), "someone else", true));
        player.setItemSlot(EquipmentSlot.HEAD, released);
        EdithServer.onPlayerTick(new PlayerTickEvent.Post(player));
        EdithOwner owner = EdithData.getOwner(EdithData.getWornGlasses(player));
        if (owner == null || !owner.id().equals(player.getUUID()) || owner.transferPending()) {
            throw new GameTestAssertException("De verwisselde bril is niet van de nieuwe drager geworden: " + owner);
        }
        helper.succeed();
    }

    // ================================================================ Fase 15: het harnas

    /**
     * Een speler met bril die in survival staat.
     *
     * Dat laatste moet: in creative mag je toch al vliegen en krijg je geen schade, dus daar
     * valt van het harnas niets te zien.
     */
    private static ServerPlayer makeSuitOwner(GameTestHelper helper, BlockPos pos) {
        return makeSurvivalOwner(helper, pos);
    }

    /** Het hele harnas aantrekken op een speler die al een bril op heeft. */
    private static void wearSuit(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.STARK_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.STARK_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.STARK_BOOTS.get()));
    }

    /**
     * Met het hele harnas én de bril kun je vliegen; gaat de bril af, dan is het meteen over.
     * Dat is het hele punt van het harnas: zonder E.D.I.T.H. doet het niets.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "suit")
    public static void suit_flies_only_with_the_glasses(GameTestHelper helper) {
        ServerPlayer owner = makeSuitOwner(helper, new BlockPos(1, 3, 1));
        wearSuit(owner);
        if (!StarkSuitItem.fullSet(owner) || !StarkSuitItem.isPowered(owner)) {
            throw new GameTestAssertException("Het harnas telt niet als compleet en aan");
        }
        EdithSuit.onPlayerTick(new PlayerTickEvent.Post(owner));
        if (!owner.getAbilities().mayfly) {
            throw new GameTestAssertException("Je mag niet vliegen met een compleet harnas");
        }

        // Bril af: het vliegen hoort meteen weg te zijn.
        owner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        EdithSuit.onPlayerTick(new PlayerTickEvent.Post(owner));
        if (owner.getAbilities().mayfly) {
            throw new GameTestAssertException("Je vliegt nog zonder bril");
        }
        helper.succeed();
    }

    /**
     * Stuwraketten aan: in de lucht kost dat energie en vangen ze je val op. Gaat de bril af,
     * dan gaan ze meteen uit. (Het vliegen zelf rekent het spel van de speler uit; dat kan een
     * test zonder beeld niet nadoen.)
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "suit")
    public static void suit_thrusters_cost_energy_and_catch_you(GameTestHelper helper) {
        ServerPlayer owner = makeSuitOwner(helper, new BlockPos(1, 3, 1));
        wearSuit(owner);
        EdithSuit.onPlayerTick(new PlayerTickEvent.Post(owner));
        EdithSuit.setEngaged(owner, true);
        if (!EdithSuit.isEngaged(owner)) {
            throw new GameTestAssertException("De stuwraketten gingen niet aan met een compleet harnas");
        }

        ItemStack glasses = EdithData.getWornGlasses(owner);
        int before = EdithData.getEnergy(glasses);
        owner.setOnGround(false);
        owner.fallDistance = 30.0f;
        // De rekening komt één keer per seconde: op een tick die deelbaar is door 20.
        owner.tickCount = 40;
        EdithSuit.onPlayerTick(new PlayerTickEvent.Post(owner));
        if (owner.fallDistance != 0.0f) {
            throw new GameTestAssertException("De val werd niet opgevangen: " + owner.fallDistance);
        }
        int spent = before - EdithData.getEnergy(glasses);
        if (spent != SuitBalance.DRAIN_HOVER) {
            throw new GameTestAssertException("Stilhangen kostte " + spent + " in plaats van " + SuitBalance.DRAIN_HOVER);
        }

        owner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        EdithSuit.onPlayerTick(new PlayerTickEvent.Post(owner));
        if (EdithSuit.isEngaged(owner)) {
            throw new GameTestAssertException("De stuwraketten bleven aan zonder bril");
        }
        helper.succeed();
    }

    /** Zonder het hele harnas gaan de stuwraketten niet aan, ook niet als je spel dat vraagt. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "suit")
    public static void suit_thrusters_need_the_whole_suit(GameTestHelper helper) {
        ServerPlayer owner = makeSuitOwner(helper, new BlockPos(1, 3, 1));
        owner.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.STARK_CHESTPLATE.get()));
        owner.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.STARK_BOOTS.get()));
        EdithSuit.setEngaged(owner, true);
        if (EdithSuit.isEngaged(owner)) {
            throw new GameTestAssertException("De stuwraketten gingen aan zonder broek");
        }
        helper.succeed();
    }

    /** Een half harnas is niet genoeg om te vliegen, maar beschermt wel. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "suit")
    public static void half_a_suit_does_not_fly(GameTestHelper helper) {
        ServerPlayer owner = makeSuitOwner(helper, new BlockPos(1, 3, 1));
        owner.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.STARK_CHESTPLATE.get()));
        EdithSuit.onPlayerTick(new PlayerTickEvent.Post(owner));
        if (owner.getAbilities().mayfly) {
            throw new GameTestAssertException("Je vliegt met maar één stuk harnas");
        }
        if (StarkSuitItem.wornPieces(owner) != 1 || !StarkSuitItem.isPowered(owner)) {
            throw new GameTestAssertException("Eén stuk harnas telt niet mee");
        }
        helper.succeed();
    }

    /**
     * Het harnas haalt een deel van de klap weg en betaalt dat uit je bril. Zonder bril komt
     * de klap harder aan.
     *
     * We slaan hier echt toe in plaats van de gebeurtenis na te bootsen, zodat ook getest
     * wordt dat het harnas op de juiste gebeurtenis is aangesloten. Wel even wachten: een
     * speler die net in de wereld komt is de eerste drie seconden onkwetsbaar.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "suit")
    public static void suit_absorbs_damage_and_pays_for_it(GameTestHelper helper) {
        ServerPlayer owner = makeSuitOwner(helper, new BlockPos(1, 3, 1));
        wearSuit(owner);

        helper.runAfterDelay(SPAWN_INVULNERABLE_TICKS, () -> {
            ItemStack glasses = EdithData.getWornGlasses(owner);
            int before = EdithData.getEnergy(glasses);

            float withSuit = hurtAndMeasure(owner, 10.0f);
            if (withSuit <= 0.0f) {
                throw new GameTestAssertException("De klap kwam helemaal niet aan");
            }
            int spent = before - EdithData.getEnergy(glasses);
            if (spent <= 0) {
                throw new GameTestAssertException("Het harnas kostte geen energie");
            }

            // Bril af: hetzelfde harnas, maar nu zonder stroom.
            owner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            float withoutGlasses = hurtAndMeasure(owner, 10.0f);

            if (!(withSuit < withoutGlasses)) {
                throw new GameTestAssertException("Met bril verloor je " + withSuit
                        + " leven en zonder bril " + withoutGlasses + "; dat hoort andersom");
            }
            helper.succeed();
        });
    }

    /** Zo lang is een speler onkwetsbaar nadat hij in de wereld verschijnt (zie ServerPlayer). */
    private static final int SPAWN_INVULNERABLE_TICKS = 70;

    /** Geeft de speler een klap en zegt hoeveel leven dat kostte. */
    private static float hurtAndMeasure(ServerPlayer player, float damage) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().generic(), damage);
        return player.getMaxHealth() - player.getHealth();
    }

    /** De repulsor raakt wat voor je staat, duwt het weg en kost energie. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "suit")
    public static void repulsor_hits_what_you_look_at(GameTestHelper helper) {
        ServerPlayer owner = makeSuitOwner(helper, new BlockPos(1, 3, 1));
        wearSuit(owner);
        Husk target = spawnDummyEnemy(helper, new BlockPos(1, 3, 6));
        // Recht naar het doelwit kijken (in de test-arena ligt +z voor je).
        owner.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.position());

        ItemStack glasses = EdithData.getWornGlasses(owner);
        int before = EdithData.getEnergy(glasses);
        float health = target.getHealth();

        EdithSuit.repulsor(owner);

        if (target.getHealth() >= health) {
            throw new GameTestAssertException("De repulsor deed geen schade");
        }
        if (EdithData.getEnergy(glasses) != before - SuitBalance.REPULSOR_COST) {
            throw new GameTestAssertException("De repulsor kostte niet " + SuitBalance.REPULSOR_COST
                    + " maar " + (before - EdithData.getEnergy(glasses)));
        }
        helper.succeed();
    }

    /**
     * De repulsor vuur je door de aanvalsknop vast te houden, dus er komt elke tick een "vuur"
     * binnen. De server laat er maar één per wachttijd door: de tweede kost niets.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "suit")
    public static void repulsor_waits_for_its_cooldown(GameTestHelper helper) {
        ServerPlayer owner = makeSuitOwner(helper, new BlockPos(1, 3, 1));
        wearSuit(owner);
        ItemStack glasses = EdithData.getWornGlasses(owner);
        int full = EdithData.getEnergy(glasses);

        EdithSuit.repulsor(owner);
        EdithSuit.repulsor(owner);
        int afterTwo = EdithData.getEnergy(glasses);
        if (afterTwo != full - SuitBalance.REPULSOR_COST) {
            throw new GameTestAssertException("Twee stoten in dezelfde tick kostten " + (full - afterTwo)
                    + " in plaats van " + SuitBalance.REPULSOR_COST);
        }
        // Na de wachttijd mag hij weer.
        helper.runAfterDelay(SuitBalance.REPULSOR_COOLDOWN + 1, () -> {
            EdithSuit.repulsor(owner);
            if (EdithData.getEnergy(glasses) != afterTwo - SuitBalance.REPULSOR_COST) {
                throw new GameTestAssertException("Na de wachttijd vuurde de repulsor niet opnieuw");
            }
            helper.succeed();
        });
    }

    /**
     * Mini-raketten: zonder raketpakket gebeurt er niets. Bouw je het pakket in (rechtsklik met
     * de borstplaat aan), dan kost een salvo energie, en meteen nog een salvo kan niet: het
     * pakket moet eerst herladen.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "suit")
    public static void rockets_need_the_rocket_pack(GameTestHelper helper) {
        ServerPlayer owner = makeSuitOwner(helper, new BlockPos(1, 3, 1));
        wearSuit(owner);
        ItemStack glasses = EdithData.getWornGlasses(owner);
        int full = EdithData.getEnergy(glasses);

        SuitRockets.fire(owner);
        if (EdithData.getEnergy(glasses) != full) {
            throw new GameTestAssertException("Zonder raketpakket kostte het toch energie");
        }

        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROCKET_PACK.get()));
        owner.getItemInHand(InteractionHand.MAIN_HAND).use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        if (!RocketPackItem.worn(owner)) {
            throw new GameTestAssertException("Het raketpakket zit niet op de borstplaat");
        }

        SuitRockets.fire(owner);
        int afterOne = EdithData.getEnergy(glasses);
        if (afterOne != full - SuitBalance.ROCKET_COST) {
            throw new GameTestAssertException("Een salvo kostte " + (full - afterOne) + " in plaats van "
                    + SuitBalance.ROCKET_COST);
        }
        helper.runAfterDelay(SuitBalance.ROCKET_SALVO * SuitBalance.ROCKET_INTERVAL + 2, () -> {
            SuitRockets.fire(owner);
            if (EdithData.getEnergy(glasses) != afterOne) {
                throw new GameTestAssertException("Een tweede salvo tijdens het herladen kostte toch energie");
            }
            helper.succeed();
        });
    }

    /**
     * Een salvo gaat op het wezen waar je naar kijkt: de raketten vliegen eerst omhoog, sturen
     * dan bij en raken het meer dan eens. Jou raken ze nooit.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "suit")
    public static void rockets_home_in_on_the_target(GameTestHelper helper) {
        // Midden in de arena: langs de rand staat een onzichtbare muur waar raketten op ontploffen.
        ServerPlayer owner = makeSuitOwner(helper, new BlockPos(12, 3, 4));
        wearSuit(owner);
        owner.getItemBySlot(EquipmentSlot.CHEST).set(ModDataComponents.ROCKET_PACK.get(), true);
        Husk target = spawnDummyEnemy(helper, new BlockPos(12, 3, 16));
        owner.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.position());
        float full = target.getMaxHealth();

        SuitRockets.fire(owner);
        // Een testspeler tikt niet vanzelf; het salvo gaat verder als de speler tikt.
        helper.onEachTick(() -> SuitRockets.tick(owner));
        helper.succeedWhen(() -> {
            if (owner.getHealth() < owner.getMaxHealth()) {
                throw new GameTestAssertException("De raketten raakten je zelf");
            }
            // Twee treffers of meer: dan telt ook een raket vlak na de vorige.
            if (target.isAlive() && target.getHealth() > full - 2 * SuitBalance.ROCKET_DAMAGE * 0.8f) {
                throw new GameTestAssertException("Het doelwit is nog niet twee keer geraakt ("
                        + target.getHealth() + "/" + full + ")");
            }
        });
    }

    /**
     * Raket-upgrades: een raketkop of magazijn past pas op een borstplaat met raketpakket, en
     * hooguit drie keer. Een magazijn geeft twee raketten meer per salvo, een raketkop 40% meer
     * schade; een oud harnas zonder deze upgrades schiet gewoon zes gewone raketten.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "suit")
    public static void rocket_upgrades_add_rockets_and_damage(GameTestHelper helper) {
        ServerPlayer owner = makeSuitOwner(helper, new BlockPos(1, 3, 1));
        wearSuit(owner);
        ItemStack chest = owner.getItemBySlot(EquipmentSlot.CHEST);
        var warhead = (com.senne.starktech.workbench.SuitModuleItem) ModItems.ROCKET_WARHEAD.get();
        var magazine = (com.senne.starktech.workbench.SuitModuleItem) ModItems.ROCKET_MAGAZINE.get();
        if (warhead.canApplyTo(chest) || magazine.canApplyTo(chest)) {
            throw new GameTestAssertException("Een raket-upgrade paste zonder raketpakket");
        }
        if (SuitRockets.salvoSize(owner) != SuitBalance.ROCKET_SALVO || SuitRockets.damageScale(owner) != 1.0f) {
            throw new GameTestAssertException("Zonder upgrades is het salvo niet gewoon " + SuitBalance.ROCKET_SALVO);
        }
        chest.set(ModDataComponents.ROCKET_PACK.get(), true);
        for (int i = 0; i < 3; i++) {
            if (!magazine.canApplyTo(chest) || !warhead.canApplyTo(chest)) {
                throw new GameTestAssertException("Upgrade " + (i + 1) + " paste niet");
            }
            magazine.applyTo(chest);
            warhead.applyTo(chest);
        }
        if (magazine.canApplyTo(chest) || warhead.canApplyTo(chest)) {
            throw new GameTestAssertException("Er paste een vierde raket-upgrade in");
        }
        int size = SuitRockets.salvoSize(owner);
        if (size != SuitBalance.ROCKET_SALVO + 3 * com.senne.starktech.workbench.WorkbenchBalance.MAGAZINE_ROCKETS) {
            throw new GameTestAssertException("Met drie magazijnen telt een salvo " + size + " raketten");
        }
        if (Math.abs(SuitRockets.damageScale(owner) - (1 + 3 * com.senne.starktech.workbench.WorkbenchBalance.WARHEAD_DAMAGE)) > 1e-4) {
            throw new GameTestAssertException("Met drie raketkoppen is de schade x" + SuitRockets.damageScale(owner));
        }
        // De upgrades gaan mee met de borstplaat (ook na opslaan en laden).
        var saved = com.senne.starktech.workbench.SuitUpgrades.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,
                com.senne.starktech.workbench.SuitUpgrades.of(chest)).getOrThrow();
        var loaded = com.senne.starktech.workbench.SuitUpgrades.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow();
        if (loaded.warheads() != 3 || loaded.magazines() != 3) {
            throw new GameTestAssertException("Na opslaan en laden: " + loaded);
        }
        helper.succeed();
    }

    // ================================================================ Torentjes

    /** Zet een torentje neer, met (voor een Stark-torentje) een eigenaar. */
    private static TurretBlockEntity placeTurret(GameTestHelper helper, BlockPos pos, boolean hydra,
                                                 @Nullable ServerPlayer owner) {
        helper.setBlock(pos, (hydra ? ModBlocks.HYDRA_TURRET : ModBlocks.STARK_TURRET).get());
        TurretBlockEntity turret = (TurretBlockEntity) helper.getBlockEntity(pos);
        if (owner != null) {
            turret.setOwner(owner);
        }
        return turret;
    }

    /** Een warmtegenerator vol stroom naast het torentje (er direct tegenaan is ook aangesloten). */
    private static void powerTurret(GameTestHelper helper, BlockPos generator) {
        helper.setBlock(generator, ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(generator)).fill(5_000);
    }

    /**
     * Een Stark-torentje op stroom schiet het monster neer, maar niet de koe die dichterbij
     * staat, en ook niet zijn eigenaar.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "turret")
    public static void stark_turret_shoots_monsters_only(GameTestHelper helper) {
        BlockPos pos = new BlockPos(12, 3, 12);
        ServerPlayer owner = makeSurvivalOwner(helper, new BlockPos(12, 3, 8));
        placeTurret(helper, pos, false, owner);
        powerTurret(helper, pos.east());
        Husk monster = spawnDummyEnemy(helper, new BlockPos(12, 3, 18));
        net.minecraft.world.entity.animal.Cow cow = helper.spawn(EntityType.COW, new BlockPos(9, 3, 12));
        cow.setNoAi(true);
        float full = monster.getMaxHealth();

        helper.succeedWhen(() -> {
            if (cow.getHealth() < cow.getMaxHealth()) {
                throw new GameTestAssertException("Het torentje schoot op een koe");
            }
            if (owner.getHealth() < owner.getMaxHealth()) {
                throw new GameTestAssertException("Het torentje schoot op zijn eigenaar");
            }
            if (monster.isAlive() && monster.getHealth() >= full) {
                throw new GameTestAssertException("Het monster is nog niet geraakt");
            }
        });
    }

    /** Een redstone-signaal zet het torentje uit: dan schiet het niet, ook niet met stroom. */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "turret")
    public static void turret_is_off_with_redstone(GameTestHelper helper) {
        BlockPos pos = new BlockPos(12, 3, 12);
        ServerPlayer owner = makeSurvivalOwner(helper, new BlockPos(2, 3, 2));
        placeTurret(helper, pos, false, owner);
        powerTurret(helper, pos.east());
        helper.setBlock(pos.west(), Blocks.REDSTONE_BLOCK);
        if (!helper.getBlockState(pos).getValue(TurretBlock.POWERED)) {
            throw new GameTestAssertException("Het torentje merkte het redstone-signaal niet");
        }
        Husk monster = spawnDummyEnemy(helper, new BlockPos(12, 3, 18));
        helper.runAfterDelay(80, () -> {
            if (monster.getHealth() < monster.getMaxHealth()) {
                throw new GameTestAssertException("Het torentje schoot terwijl het uit stond");
            }
            helper.succeed();
        });
    }

    /** Zonder stroom geen laser, maar met munitie erin schiet het wel raketten. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "turret")
    public static void turret_fires_rockets_from_its_ammo(GameTestHelper helper) {
        BlockPos pos = new BlockPos(12, 3, 4);
        ServerPlayer owner = makeSurvivalOwner(helper, new BlockPos(2, 3, 2));
        TurretBlockEntity turret = placeTurret(helper, pos, false, owner);
        powerTurret(helper, pos.east());
        turret.ammo().insertItem(0, new ItemStack(ModItems.ROCKET_AMMO.get(), 4), false);
        Husk monster = spawnDummyEnemy(helper, new BlockPos(12, 3, 18));
        float full = monster.getMaxHealth();

        helper.succeedWhen(() -> {
            if (turret.ammoCount() >= 4) {
                throw new GameTestAssertException("Er is nog geen raket weg");
            }
            if (monster.isAlive() && monster.getHealth() >= full) {
                throw new GameTestAssertException("De raket heeft het monster nog niet geraakt");
            }
        });
    }

    /** Het Hydra-torentje (uit de bunker) heeft geen stroom nodig en schiet op spelers. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "turret")
    public static void hydra_turret_shoots_players(GameTestHelper helper) {
        BlockPos pos = new BlockPos(12, 3, 12);
        placeTurret(helper, pos, true, null);
        powerTurret(helper, pos.east());
        ServerPlayer player = makeSurvivalOwner(helper, new BlockPos(12, 3, 18));
        helper.succeedWhen(() -> {
            if (player.getHealth() >= player.getMaxHealth()) {
                throw new GameTestAssertException("Het Hydra-torentje heeft de speler nog niet geraakt");
            }
        });
    }

    /**
     * Zonder stroom doet geen enkel torentje iets (ook Hydra's niet): de loop hangt omlaag en er
     * valt geen schot. En een beveiligingskern zonder stroom stuurt geen drones.
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "turrets")
    public static void turrets_and_security_need_power(GameTestHelper helper) {
        BlockPos pos = new BlockPos(12, 3, 12);
        placeTurret(helper, pos, true, null);
        ServerPlayer player = makeSurvivalOwner(helper, new BlockPos(12, 3, 18));
        helper.setBlock(new BlockPos(3, 3, 3), ModBlocks.SECURITY_CORE.get());
        helper.runAfterDelay(100, () -> {
            if (player.getHealth() < player.getMaxHealth()) {
                throw new GameTestAssertException("Een torentje zonder stroom schoot toch");
            }
            if (helper.getBlockState(pos).getValue(com.senne.starktech.turret.TurretBlock.ONLINE)) {
                throw new GameTestAssertException("Het torentje staat als ONLINE, maar er is geen stroom");
            }
            int drones = helper.getLevel().getEntitiesOfClass(com.senne.starktech.security.SecurityDroneEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(3, 3, 3))).inflate(16)).size();
            if (drones > 0) {
                throw new GameTestAssertException("Een kern zonder stroom stuurde toch " + drones + " drones");
            }
            helper.succeed();
        });
    }

    // ================================================================ Fase 15: de holotafel

    /**
     * De holotafel onthoudt wat hij laat zien, en je bedient hem met de bril op:
     * lege hand = volgende stand, voorwerp = bouwtekening, sluipen = volgende kleur.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "holo")
    public static void holo_table_switches_mode_and_colour(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.HOLO_TABLE.get().defaultBlockState());
        if (!(helper.getBlockEntity(pos) instanceof HoloTableBlockEntity table)) {
            throw new GameTestAssertException("De holotafel heeft geen block entity");
        }
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 2, 1));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)),
                Direction.UP, helper.absolutePos(pos), false);

        // Het instelscherm zet de stand; hier doen we wat dat scherm stuurt.
        HoloMode wanted = table.mode().next();
        HoloTables.handleAction(owner, new HoloActionPayload(helper.absolutePos(pos),
                HoloActionPayload.Op.MODE, wanted.ordinal()));
        if (table.mode() != wanted) {
            throw new GameTestAssertException("De stand veranderde niet: " + table.mode());
        }
        // Bereik, grootte en ronddraaien komen ook uit dat scherm.
        HoloTables.handleAction(owner, new HoloActionPayload(helper.absolutePos(pos),
                HoloActionPayload.Op.RANGE, 3));
        HoloTables.handleAction(owner, new HoloActionPayload(helper.absolutePos(pos),
                HoloActionPayload.Op.SPIN, 0));
        if (table.range() != 3 || table.spin()) {
            throw new GameTestAssertException("Bereik of draaien werd niet overgenomen: "
                    + table.range() + ", " + table.spin());
        }
        // Buiten bereik mag niemand aan je tafel zitten.
        owner.moveTo(helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(1, 2, 1))).add(200, 0, 0));
        HoloTables.handleAction(owner, new HoloActionPayload(helper.absolutePos(pos),
                HoloActionPayload.Op.RANGE, 0));
        if (table.range() != 3) {
            throw new GameTestAssertException("De tafel luisterde naar iemand die er niet bij staat");
        }
        owner.moveTo(helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(1, 2, 1))));

        // Een voorwerp: dat wordt de bouwtekening, en hij springt naar die stand.
        ItemStack chip = new ItemStack(ModItems.STARK_CHIP.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, chip);
        helper.getBlockState(pos).useItemOn(chip, helper.getLevel(), owner, InteractionHand.MAIN_HAND, hit);
        if (table.mode() != HoloMode.BLUEPRINT) {
            throw new GameTestAssertException("Hij sprong niet naar de bouwtekening: " + table.mode());
        }
        if (!table.blueprint().is(ModItems.STARK_CHIP.get())) {
            throw new GameTestAssertException("De bouwtekening klopt niet: " + table.blueprint());
        }

        // Sluipen: volgende kleur.
        ShieldColor before = helper.getBlockState(pos).getValue(HoloTableBlock.COLOR);
        owner.setShiftKeyDown(true);
        helper.getBlockState(pos).useWithoutItem(helper.getLevel(), owner, hit);
        owner.setShiftKeyDown(false);
        ShieldColor after = helper.getBlockState(pos).getValue(HoloTableBlock.COLOR);
        if (after != before.next()) {
            throw new GameTestAssertException("De kleur ging van " + before + " naar " + after);
        }

        // En alles blijft bewaard als de tafel opnieuw wordt ingelezen.
        HoloTableBlockEntity reloaded = new HoloTableBlockEntity(pos, helper.getBlockState(pos));
        reloaded.loadWithComponents(table.saveWithoutMetadata(helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        if (reloaded.mode() != table.mode() || !reloaded.blueprint().is(ModItems.STARK_CHIP.get())) {
            throw new GameTestAssertException("Na opnieuw inlezen is hij zijn instelling kwijt");
        }
        helper.succeed();
    }

    /** De tafel werkt ook zonder bril: sluipen + klik = volgende kleur, een voorwerp = het recept. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "holo")
    public static void holo_table_works_without_glasses(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.HOLO_TABLE.get().defaultBlockState());
        if (!(helper.getBlockEntity(pos) instanceof HoloTableBlockEntity table)) {
            throw new GameTestAssertException("De holotafel heeft geen block entity");
        }
        // Een speler zonder bril.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)),
                Direction.UP, helper.absolutePos(pos), false);

        var before = helper.getBlockState(pos).getValue(HoloTableBlock.COLOR);
        player.setShiftKeyDown(true);
        helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit);
        if (helper.getBlockState(pos).getValue(HoloTableBlock.COLOR) == before) {
            throw new GameTestAssertException("Zonder bril veranderde de kleur niet");
        }
        player.setShiftKeyDown(false);
        helper.getBlockState(pos).useItemOn(new ItemStack(ModItems.STARK_CHIP.get()), helper.getLevel(), player,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        if (!table.blueprint().is(ModItems.STARK_CHIP.get())) {
            throw new GameTestAssertException("Zonder bril kwam het recept niet op tafel");
        }
        helper.succeed();
    }

    /**
     * Rechtsklik met een lege hand opent het instelscherm; het mag de tafel niet veranderen.
     *
     * Minecraft geeft een lege hand eerst als "voorwerp" door aan de tafel. Die legde toen
     * "lucht" op tafel als bouwtekening, en het instelscherm ging nooit open. We lopen hier
     * dezelfde weg als het echte spel (via gameMode), zodat precies dat pad getest wordt.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "holo")
    public static void holo_table_empty_hand_is_not_a_blueprint(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.HOLO_TABLE.get().defaultBlockState());
        if (!(helper.getBlockEntity(pos) instanceof HoloTableBlockEntity table)) {
            throw new GameTestAssertException("De holotafel heeft geen block entity");
        }
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(2, 2, 1));
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)),
                Direction.UP, helper.absolutePos(pos), false);

        HoloMode before = table.mode();
        owner.gameMode.useItemOn(owner, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        if (!table.blueprint().isEmpty()) {
            throw new GameTestAssertException("Een lege hand legde " + table.blueprint() + " op tafel");
        }
        if (table.mode() != before) {
            throw new GameTestAssertException("Een lege hand veranderde de stand naar " + table.mode());
        }
        helper.succeed();
    }

    // ================================================================ Stroomnet

    /**
     * Een warmtegenerator levert via vier kabels stroom aan een laadstation. Een tweede rij met
     * een gat in de kabel krijgt niets: kabels moeten aan elkaar zitten.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "grid")
    public static void grid_carries_power_over_cables(GameTestHelper helper) {
        BlockPos connected = gridRow(helper, 1, false);
        BlockPos broken = gridRow(helper, 3, true);
        helper.runAfterDelay(10, () -> {
            // Het station stuurt de stroom meteen door naar de bril die erin ligt.
            int got = EdithData.getEnergy(((ChargingStationBlockEntity) helper.getBlockEntity(connected))
                    .getItem(ChargingStationBlockEntity.SLOT_GLASSES));
            int gotThroughGap = EdithData.getEnergy(((ChargingStationBlockEntity) helper.getBlockEntity(broken))
                    .getItem(ChargingStationBlockEntity.SLOT_GLASSES));
            if (got <= 0) {
                throw new GameTestAssertException("Het laadstation kreeg geen stroom over de kabels");
            }
            if (gotThroughGap != 0) {
                throw new GameTestAssertException("Stroom sprong over een gat in de kabel: " + gotThroughGap);
            }
            helper.succeed();
        });
    }

    /**
     * Generator (vol) - vier kabels - laadstation met een lege bril erin, op rij z. Met {@code gap}
     * ontbreekt de middelste kabel.
     */
    private static BlockPos gridRow(GameTestHelper helper, int z, boolean gap) {
        BlockPos source = new BlockPos(1, 3, z);
        helper.setBlock(source, ModBlocks.HEAT_GENERATOR.get());
        for (int x = 2; x <= 5; x++) {
            if (!(gap && x == 4)) {
                helper.setBlock(new BlockPos(x, 3, z), ModBlocks.CABLE.get());
            }
        }
        BlockPos station = new BlockPos(6, 3, z);
        helper.setBlock(station, ModBlocks.CHARGING_STATION.get());
        ((ChargingStationBlockEntity) helper.getBlockEntity(station)).setItem(ChargingStationBlockEntity.SLOT_GLASSES,
                new ItemStack(ModItems.EDITH_GLASSES.get()));
        ((ChargerBlockEntity) helper.getBlockEntity(source)).fill(5_000);
        return station;
    }

    /**
     * Ook zonder bril erin vult het laadstation zijn eigen voorraad uit het net, maar alleen met
     * wat over is: een generator houdt de helft van wat erin past achter voor de rest van het
     * net. Een volle generator geeft dus wel iets, een die voor een kwart vol is niets.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "grid")
    public static void station_stores_only_spare_grid_power(GameTestHelper helper) {
        BlockPos full = storeRow(helper, 1, EdithBalance.HEAT_CAPACITY);
        BlockPos quarter = storeRow(helper, 3, EdithBalance.HEAT_CAPACITY / 4);
        helper.runAfterDelay(40, () -> {
            int fromFull = ((ChargingStationBlockEntity) helper.getBlockEntity(full)).energy();
            int fromQuarter = ((ChargingStationBlockEntity) helper.getBlockEntity(quarter)).energy();
            int left = ((ChargerBlockEntity) helper.getBlockEntity(new BlockPos(1, 3, 1))).energy();
            if (fromFull <= 0) {
                throw new GameTestAssertException("Het station vulde zijn voorraad niet uit een volle generator");
            }
            if (fromQuarter != 0) {
                throw new GameTestAssertException("Het station nam stroom die voor de rest van het net was: " + fromQuarter);
            }
            if (left < EdithBalance.HEAT_CAPACITY * EdithBalance.STATION_GRID_RESERVE) {
                throw new GameTestAssertException("De generator zakte onder zijn reserve: " + left);
            }
            helper.succeed();
        });
    }

    /**
     * Getekende kabels: een route over de vloer loopt aan één stuk van begin tot eind. Stroom
     * gaat over de kabel, ook als je er een muur overheen bouwt (dan zit hij in de muur). Knip je
     * één stukje weg, dan houdt de stroom daar op.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "cables")
    public static void drawn_cables_carry_power_under_walls(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos generator = new BlockPos(2, 2, 6);
        helper.setBlock(generator, ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(generator)).fill(5_000);
        List<BlockPos> route = com.senne.starktech.energy.StarkCables.route(level,
                helper.absolutePos(new BlockPos(3, 2, 6)), helper.absolutePos(new BlockPos(9, 2, 11)));
        for (int i = 1; i < route.size(); i++) {
            if (route.get(i).distManhattan(route.get(i - 1)) != 1) {
                throw new GameTestAssertException("De getekende route heeft een gat bij " + route.get(i));
            }
        }
        if (!route.getLast().equals(helper.absolutePos(new BlockPos(9, 2, 11)))) {
            throw new GameTestAssertException("De route eindigt niet waar je keek: " + route.getLast());
        }
        for (BlockPos pos : route) {
            com.senne.starktech.energy.StarkCables.add(level, pos);
        }
        BlockPos device = helper.absolutePos(new BlockPos(10, 2, 11));
        if (com.senne.starktech.energy.StarkGrid.available(level, device) <= 0) {
            throw new GameTestAssertException("Er komt geen stroom over de getekende kabel");
        }
        // Een muur over de hele kabel heen: hij loopt erin door.
        for (BlockPos pos : route) {
            level.setBlockAndUpdate(pos, Blocks.STONE_BRICKS.defaultBlockState());
        }
        if (com.senne.starktech.energy.StarkGrid.available(level, device) <= 0) {
            throw new GameTestAssertException("Onder de muur gaat de stroom niet meer door");
        }
        // Eén stukje weg (doorgeknipt): dan is het net onderbroken.
        com.senne.starktech.energy.StarkCables.remove(level, route.get(route.size() / 2));
        if (com.senne.starktech.energy.StarkGrid.available(level, device) > 0) {
            throw new GameTestAssertException("De stroom sprong over het doorgeknipte stuk");
        }
        helper.succeed();
    }

    /**
     * De hack-terminal van de laptop, helemaal uitgespeeld: een verkeerde poort wordt geweigerd,
     * het firewall-logboek verraadt de open poort, een fout wachtwoord geeft de "likeness", het
     * goede wachtwoord geeft een shell, de config noemt de beveiligingsdaemon, en override op het
     * goede procesnummer brengt je bij de laatste stap.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "hack")
    public static void hack_terminal_can_be_solved(GameTestHelper helper) {
        var terminal = new com.senne.starktech.security.HackTerminal(42L, "Dev");
        long now = 0;
        terminal.run("connect 22", now);
        if (terminal.stage() != com.senne.starktech.security.HackTerminal.Stage.RECON) {
            throw new GameTestAssertException("Een gefilterde poort liet je toch binnen");
        }
        terminal.run("logs", now);
        boolean logKnows = terminal.output().stream().anyMatch(line -> line.text().contains("ALLOW")
                && line.text().contains(":" + terminal.openPort() + " "));
        if (!logKnows) {
            throw new GameTestAssertException("Het firewall-logboek noemt de open poort niet");
        }
        terminal.run("connect " + terminal.openPort(), now);
        if (terminal.stage() != com.senne.starktech.security.HackTerminal.Stage.LOGIN) {
            throw new GameTestAssertException("De open poort gaf geen inlogscherm");
        }
        String wrong = terminal.candidates().stream().filter(word -> !word.equals(terminal.password())).findFirst().orElseThrow();
        terminal.run("login " + wrong, now);
        int likeness = com.senne.starktech.security.HackTerminal.likeness(wrong, terminal.password());
        if (terminal.output().stream().noneMatch(line -> line.text().contains("Likeness " + likeness + "/"))) {
            throw new GameTestAssertException("Een fout wachtwoord gaf niet de juiste likeness (" + likeness + ")");
        }
        String pattern = com.senne.starktech.security.HackTerminal.pattern(wrong, terminal.password());
        if (terminal.output().stream().noneMatch(line -> line.text().startsWith(wrong + " -> " + pattern))) {
            throw new GameTestAssertException("De terminal liet niet zien welke letters goed staan (" + pattern + ")");
        }
        if (terminal.possible().contains(wrong) || !terminal.possible().contains(terminal.password())) {
            throw new GameTestAssertException("Het doorstrepen klopt niet: " + terminal.possible());
        }
        terminal.run("login " + terminal.password().toLowerCase(), now);
        if (terminal.stage() != com.senne.starktech.security.HackTerminal.Stage.SHELL) {
            throw new GameTestAssertException("Het goede wachtwoord gaf geen shell");
        }
        terminal.run("ls", now);
        if (terminal.output().getLast().text().indexOf("README.txt") != 0) {
            throw new GameTestAssertException("De README staat niet bovenaan: " + terminal.output().getLast().text());
        }
        terminal.run("cat README.txt", now);
        if (terminal.output().stream().noneMatch(line -> line.text().contains("security config"))
                || terminal.output().stream().noneMatch(line -> line.text().contains("'ps'"))
                || terminal.output().stream().noneMatch(line -> line.text().contains("override the daemon"))) {
            throw new GameTestAssertException("De README geeft niet alle aanwijzingen");
        }
        // Vanaf de map waar je binnenkomt: cat vindt het bestand ook zonder pad.
        terminal.run("cat security.conf", now);
        if (terminal.output().stream().noneMatch(line -> line.text().equals("daemon = " + terminal.guard()))) {
            throw new GameTestAssertException("De config noemt de beveiligingsdaemon niet");
        }
        terminal.run("override " + terminal.guard(), now);
        if (!terminal.output().getLast().text().contains("'ps'")) {
            throw new GameTestAssertException("Override met een naam zei niet waar het nummer staat");
        }
        if (!terminal.complete("cat sec").equals("cat security.conf")) {
            throw new GameTestAssertException("Tab vulde de bestandsnaam niet aan: " + terminal.complete("cat sec"));
        }
        terminal.run("override " + (terminal.guardPid() + 1), now);
        if (terminal.stage() != com.senne.starktech.security.HackTerminal.Stage.SHELL) {
            throw new GameTestAssertException("Override op het verkeerde proces ging toch door");
        }
        terminal.run("override " + terminal.guardPid(), now);
        if (terminal.stage() != com.senne.starktech.security.HackTerminal.Stage.OVERRIDE) {
            throw new GameTestAssertException("Override op de beveiligingsdaemon ging niet door");
        }
        // De console: met de waakhond nog aan zet hij de eigenaar terug, een andere naam kent hij
        // niet, en na "disable watchdog" + "set owner <jouw naam>" is hij van jou.
        terminal.run("set owner Dev", now);
        if (terminal.stage() != com.senne.starktech.security.HackTerminal.Stage.OVERRIDE
                || terminal.output().stream().noneMatch(line -> line.text().contains("disable watchdog"))) {
            throw new GameTestAssertException("Met de waakhond aan ging de eigenaar toch om (of geen hint)");
        }
        if (!terminal.complete("disable wat").equals("disable watchdog")
                || !terminal.complete("set owner D").equals("set owner Dev")) {
            throw new GameTestAssertException("Tab vulde de console niet aan");
        }
        terminal.run("disable watchdog", now);
        terminal.run("set owner Rhodey", now);
        if (terminal.stage() != com.senne.starktech.security.HackTerminal.Stage.OVERRIDE) {
            throw new GameTestAssertException("Een andere naam dan de jouwe werd toch eigenaar");
        }
        terminal.run("set owner dev", now);
        if (terminal.stage() != com.senne.starktech.security.HackTerminal.Stage.DONE) {
            throw new GameTestAssertException("disable watchdog + set owner <naam> nam de toren niet over");
        }
        helper.succeed();
    }

    /**
     * Het wachtwoord raden moet te doen zijn: 300 keer spelen, elke keer het eerste woord gokken
     * dat nog kan. Het wachtwoord mag nooit doorgestreept worden, gemiddeld moet het in 3 keer
     * lukken, en (bijna) altijd voordat de pogingen op zijn.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "hack")
    public static void hack_password_is_fair(GameTestHelper helper) {
        int games = 300;
        int guesses = 0;
        int locked = 0;
        for (long seed = 0; seed < games; seed++) {
            var terminal = new com.senne.starktech.security.HackTerminal(seed, "Dev");
            terminal.run("connect " + terminal.openPort(), 0);
            int wrong = 0;
            while (terminal.stage() == com.senne.starktech.security.HackTerminal.Stage.LOGIN) {
                if (!terminal.possible().contains(terminal.password())) {
                    throw new GameTestAssertException("Het wachtwoord werd doorgestreept (spel " + seed + ")");
                }
                String guess = terminal.possible().getFirst();
                terminal.run(guess, 0);
                guesses++;
                if (!guess.equals(terminal.password())) {
                    wrong++;
                }
                if (wrong >= com.senne.starktech.security.HackTerminal.ATTEMPTS) {
                    locked++;
                    break;
                }
            }
        }
        double average = guesses / (double) games;
        if (average > 3.0 || locked > games / 100) {
            throw new GameTestAssertException("Het wachtwoord is te moeilijk: gemiddeld " + average
                    + " pogingen, " + locked + " van de " + games + " keer geblokkeerd");
        }
        helper.succeed();
    }

    /**
     * De stroomkast: een generator aan de achterkant, kabels naar links en rechts. Zet je de
     * schakelaar van één kant uit, dan krijgt alleen die kant geen stroom meer. De eigenaar meldt
     * de schakelaars aan bij E.D.I.T.H. en zet hem op afstand weer aan.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "cables")
    public static void power_cabinet_switches_cut_one_side(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos cabinet = new BlockPos(10, 2, 10);
        helper.setBlock(cabinet, ModBlocks.POWER_CABINET.get());
        helper.setBlock(cabinet.south(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(cabinet.south())).fill(5_000);
        for (int i = 1; i <= 3; i++) {
            com.senne.starktech.energy.StarkCables.add(level, helper.absolutePos(cabinet.east(i)));
            com.senne.starktech.energy.StarkCables.add(level, helper.absolutePos(cabinet.west(i)));
        }
        BlockPos east = helper.absolutePos(cabinet.east(4));
        BlockPos west = helper.absolutePos(cabinet.west(4));
        if (com.senne.starktech.energy.StarkGrid.available(level, east) <= 0
                || com.senne.starktech.energy.StarkGrid.available(level, west) <= 0) {
            throw new GameTestAssertException("Via de stroomkast komt er geen stroom aan beide kanten");
        }
        com.senne.starktech.energy.PowerCabinets.setSwitch(level, helper.absolutePos(cabinet), Direction.EAST, false);
        if (com.senne.starktech.energy.StarkGrid.available(level, east) > 0) {
            throw new GameTestAssertException("De oostkant staat uit, maar krijgt nog stroom");
        }
        if (com.senne.starktech.energy.StarkGrid.available(level, west) <= 0) {
            throw new GameTestAssertException("De westkant staat aan, maar kreeg geen stroom meer");
        }
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        ((com.senne.starktech.energy.PowerCabinetBlockEntity) helper.getBlockEntity(cabinet)).setOwner(owner.getUUID());
        EdithRedstone.registerChannel(owner, helper.absolutePos(cabinet), Direction.EAST.get3DDataValue(),
                "cabinet east", "Cabinet 1");
        EdithRedstone.handleAction(owner, com.senne.starktech.network.RelayActionPayload.switchNamed("cabinet east",
                EdithRedstone.Op.ON));
        if (com.senne.starktech.energy.StarkGrid.available(level, east) <= 0) {
            throw new GameTestAssertException("E.D.I.T.H. zette de oostkant niet weer aan");
        }
        helper.succeed();
    }

    /**
     * Oude kabelblokken (uit een oude wereld of een gebouw) worden vanzelf een getekende kabel.
     * Een kabel die in een muur zat, laat de muur heel; een kabel op de vloer laat lucht achter.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "cables")
    public static void old_cable_blocks_become_drawn_cables(GameTestHelper helper) {
        // In een muur: stenen aan beide kanten (noord en zuid), een vloer eronder.
        BlockPos inWall = new BlockPos(5, 2, 5);
        helper.setBlock(inWall.north(), Blocks.STONE_BRICKS);
        helper.setBlock(inWall.south(), Blocks.STONE_BRICKS);
        helper.setBlock(inWall.above(), Blocks.STONE_BRICKS);
        helper.setBlock(inWall, ModBlocks.CABLE.get());
        // Op de vloer, vrij.
        BlockPos onFloor = new BlockPos(12, 2, 12);
        helper.setBlock(onFloor, ModBlocks.CABLE.get());
        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            if (!com.senne.starktech.energy.StarkCables.has(level, helper.absolutePos(inWall))
                    || !com.senne.starktech.energy.StarkCables.has(level, helper.absolutePos(onFloor))) {
                throw new GameTestAssertException("De oude kabelblokken zijn geen kabel geworden");
            }
            helper.assertBlockPresent(Blocks.STONE_BRICKS, inWall);
            helper.assertBlockPresent(Blocks.AIR, onFloor);
            helper.succeed();
        });
    }

    /** Generator (met {@code energy} erin) - vier kabels - laadstation zonder bril, op rij z. */
    private static BlockPos storeRow(GameTestHelper helper, int z, int energy) {
        BlockPos source = new BlockPos(1, 3, z);
        helper.setBlock(source, ModBlocks.HEAT_GENERATOR.get());
        for (int x = 2; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 3, z), ModBlocks.CABLE.get());
        }
        BlockPos station = new BlockPos(6, 3, z);
        helper.setBlock(station, ModBlocks.CHARGING_STATION.get());
        ((ChargerBlockEntity) helper.getBlockEntity(source)).fill(energy);
        return station;
    }

    /** Een laserzender zonder stroom doet niets, ook niet met redstone erop. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "grid")
    public static void laser_gate_needs_power(GameTestHelper helper) {
        BlockPos emitter = new BlockPos(1, 3, 1);
        helper.setBlock(emitter, ModBlocks.LASER_EMITTER.get().defaultBlockState()
                .setValue(LaserEmitterBlock.FACING, Direction.EAST));
        helper.setBlock(emitter.below(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(20, () -> {
            if (countWall(helper, emitter, 4) > 0 || helper.getBlockState(emitter).getValue(LaserEmitterBlock.POWERED)) {
                throw new GameTestAssertException("De laserpoort ging aan zonder stroom");
            }
            helper.succeed();
        });
    }

    /**
     * Te weinig stroom (een bijna lege generator): de laserpoort blijft gewoon uit, in plaats van
     * steeds even aan en weer uit te gaan. Komt er genoeg stroom bij, dan gaat hij vanzelf aan.
     */
    @GameTest(template = ARENA, timeoutTicks = 160, batch = "grid")
    public static void laser_gate_waits_for_enough_power(GameTestHelper helper) {
        BlockPos emitter = new BlockPos(1, 3, 4);
        helper.setBlock(emitter, ModBlocks.LASER_EMITTER.get().defaultBlockState()
                .setValue(LaserEmitterBlock.FACING, Direction.EAST));
        helper.setBlock(emitter.west(), ModBlocks.HEAT_GENERATOR.get());
        var generator = (ChargerBlockEntity) helper.getBlockEntity(emitter.west());
        generator.fill(40); // genoeg voor twee seconden, niet genoeg om aan te gaan
        helper.setBlock(emitter.below(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(60, () -> {
            if (helper.getBlockState(emitter).getValue(LaserEmitterBlock.POWERED)) {
                throw new GameTestAssertException("De laserpoort ging aan met maar een beetje stroom");
            }
            generator.fill(5_000);
            helper.succeedWhen(() -> {
                if (!helper.getBlockState(emitter).getValue(LaserEmitterBlock.POWERED)) {
                    throw new GameTestAssertException("Met genoeg stroom ging de laserpoort niet vanzelf aan");
                }
            });
        });
    }

    /** De holotafel heeft stroom nodig: zonder generator blijft hij uit, met een generator gaat hij aan. */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "grid")
    public static void holo_table_needs_power(GameTestHelper helper) {
        BlockPos dark = new BlockPos(1, 3, 1);
        BlockPos lit = new BlockPos(4, 3, 1);
        helper.setBlock(dark, ModBlocks.HOLO_TABLE.get());
        helper.setBlock(lit, ModBlocks.HOLO_TABLE.get());
        helper.setBlock(lit.east(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(lit.east())).fill(5_000);
        helper.runAfterDelay(30, () -> {
            if (((HoloTableBlockEntity) helper.getBlockEntity(dark)).powered()) {
                throw new GameTestAssertException("Een holotafel zonder stroom ging toch aan");
            }
            if (!((HoloTableBlockEntity) helper.getBlockEntity(lit)).powered()) {
                throw new GameTestAssertException("Een holotafel met een generator ernaast bleef uit");
            }
            helper.succeed();
        });
    }

    // ================================================================ Schildkoepel

    /** Een koepel van 8 blokken in het midden van de arena, met een volle warmtegenerator ernaast. */
    private static DomeGeneratorBlockEntity poweredDome(GameTestHelper helper, @Nullable ServerPlayer owner) {
        BlockPos pos = new BlockPos(12, 3, 12);
        helper.setBlock(pos, ModBlocks.DOME_GENERATOR.get());
        helper.setBlock(pos.east(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(pos.east())).fill(20_000);
        DomeGeneratorBlockEntity dome = (DomeGeneratorBlockEntity) helper.getBlockEntity(pos);
        dome.setRadius(0);
        if (owner != null) {
            dome.setOwner(owner);
        }
        return dome;
    }

    /** Een pijl die van {@code distance} blokken ten zuiden van de koepel recht naar het midden vliegt. */
    private static Arrow arrowTowards(GameTestHelper helper, DomeGeneratorBlockEntity dome, double distance,
                                      @Nullable Entity shooter) {
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        Vec3 center = dome.center();
        arrow.setPos(center.x, center.y + 2, center.z + distance);
        arrow.setDeltaMovement(0, 0, -1.5);
        arrow.setNoGravity(true);
        if (shooter != null) {
            arrow.setOwner(shooter);
        }
        helper.getLevel().addFreshEntity(arrow);
        return arrow;
    }

    /**
     * Met stroom gaat de koepel op. Een pijl van buiten (van niemand) verdampt op de rand en kost
     * schildkracht; een pijl van de eigenaar gaat er gewoon doorheen.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "dome")
    public static void dome_blocks_arrows_but_not_yours(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        DomeGeneratorBlockEntity dome = poweredDome(helper, owner);
        helper.runAfterDelay(20, () -> {
            if (!dome.isUp()) {
                throw new GameTestAssertException("De koepel ging niet op, status " + dome.status());
            }
            int before = dome.shield();
            Arrow stranger = arrowTowards(helper, dome, 10.5, null);
            Arrow mine = arrowTowards(helper, dome, 10.0, owner);
            helper.runAfterDelay(4, () -> {
                if (stranger.isAlive()) {
                    throw new GameTestAssertException("Een vreemde pijl kwam door de koepel");
                }
                if (!mine.isAlive() || !dome.contains(mine.position())) {
                    throw new GameTestAssertException("De pijl van de eigenaar kwam niet binnen");
                }
                if (dome.shield() >= before + DomeSettings.recharge(dome.strength()) * 4) {
                    throw new GameTestAssertException("Tegenhouden kostte geen schildkracht");
                }
                helper.succeed();
            });
        });
    }

    /** Zonder stroom blijft de koepel uit (en zegt hij waarom). */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "dome")
    public static void dome_needs_power(GameTestHelper helper) {
        BlockPos pos = new BlockPos(12, 3, 12);
        helper.setBlock(pos, ModBlocks.DOME_GENERATOR.get());
        helper.runAfterDelay(25, () -> {
            DomeGeneratorBlockEntity dome = (DomeGeneratorBlockEntity) helper.getBlockEntity(pos);
            if (dome.isUp() || dome.status() != DomeGeneratorBlockEntity.Status.NO_POWER) {
                throw new GameTestAssertException("Zonder stroom: op=" + dome.isUp() + ", status " + dome.status());
            }
            helper.succeed();
        });
    }

    /**
     * Een satellietstraal van een ander op iets binnen de koepel stuit erop; die van de eigenaar
     * niet. Is de schildkracht op, dan valt de koepel even weg.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "dome")
    public static void dome_stops_satellites_until_it_is_overloaded(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        DomeGeneratorBlockEntity dome = poweredDome(helper, owner);
        helper.runAfterDelay(20, () -> {
            ServerLevel level = helper.getLevel();
            Vec3 inside = dome.center().add(2, 0, 1);
            if (EdithDomes.blockedAt(level, inside, null, owner.getUUID(), DomeSettings.COST_SATELLITE_BEAM) != null) {
                throw new GameTestAssertException("De koepel hield de satelliet van zijn eigenaar tegen");
            }
            if (EdithDomes.blockedAt(level, inside, null, UUID.randomUUID(), DomeSettings.COST_SATELLITE_BEAM) == null) {
                throw new GameTestAssertException("De satelliet van een ander kwam door de koepel");
            }
            // Blijven schieten tot hij bezwijkt: dan valt hij weg en komt er niets meer tegen.
            for (int i = 0; i < 20 && dome.isUp(); i++) {
                EdithDomes.blockedAt(level, inside, null, UUID.randomUUID(), DomeSettings.COST_SATELLITE_BEAM);
            }
            if (dome.isUp() || dome.status() != DomeGeneratorBlockEntity.Status.DEPLETED) {
                throw new GameTestAssertException("De koepel viel niet weg toen zijn schildkracht op was");
            }
            if (EdithDomes.blockedAt(level, inside, null, UUID.randomUUID(), DomeSettings.COST_SATELLITE_BEAM) != null) {
                throw new GameTestAssertException("Een weggevallen koepel hield nog iets tegen");
            }
            helper.succeed();
        });
    }

    // ================================================================ Illusies richten

    /**
     * Een valse muur richt je eerst met de drone: tot je hem neerzet, staat er nog niets. Daarna
     * staat hij op de grond onder de drone (het ankerpunt is lucht, met iets vasts eronder).
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "illusion_aim")
    public static void illusion_is_aimed_before_it_is_placed(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        ItemStack glasses = EdithData.getWornGlasses(owner);
        EdithIllusions.start(owner, glasses, IllusionScene.Kind.WALL);
        if (!EdithIllusions.isAiming(owner) || EdithIllusions.countOf(owner) != 0) {
            throw new GameTestAssertException("De muur stond er al voordat hij gericht was");
        }
        helper.runAfterDelay(10, () -> {
            EdithIllusions.place(owner);
            if (EdithIllusions.isAiming(owner) || EdithIllusions.countOf(owner) != 1) {
                throw new GameTestAssertException("Na het neerzetten projecteerde de drone niets");
            }
            BlockPos anchor = EdithIllusions.projectorsOf(owner).getFirst().anchor();
            ServerLevel level = helper.getLevel();
            boolean onGround = level.getBlockState(anchor).getCollisionShape(level, anchor).isEmpty()
                    && !level.getBlockState(anchor.below()).getCollisionShape(level, anchor.below()).isEmpty();
            if (!onGround) {
                throw new GameTestAssertException("De muur staat niet op de grond: " + anchor);
            }
            helper.succeed();
        });
    }

    /** Annuleren tijdens het richten: niets neergezet, en je richt niet meer. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "illusion_aim")
    public static void illusion_aim_can_be_cancelled(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        EdithIllusions.start(owner, EdithData.getWornGlasses(owner), IllusionScene.Kind.ARMY);
        EdithIllusions.cancelAim(owner, "edith.starktech.msg.illusion_aim_cancelled");
        if (EdithIllusions.isAiming(owner) || EdithIllusions.countOf(owner) != 0) {
            throw new GameTestAssertException("Na annuleren was er toch een illusie, of richtte je nog");
        }
        helper.succeed();
    }

    /** Nep-wezens staan op de grond onder hun eigen plek, ook op een verhoging (vroeger een blok te diep). */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "illusion_aim")
    public static void illusion_mobs_stand_on_the_ground(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 3, 3), Blocks.STONE);
        BlockPos step = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockPos flat = helper.absolutePos(new BlockPos(5, 3, 5));
        ServerLevel level = helper.getLevel();
        double onStep = IllusionScene.feetY(level, step.getX() + 0.5, step.getZ() + 0.5, flat.getY());
        double onFlat = IllusionScene.feetY(level, flat.getX() + 0.5, flat.getZ() + 0.5, flat.getY());
        if (onStep != step.getY() + 1) {
            throw new GameTestAssertException("Op de verhoging: voeten op " + onStep + ", verwacht " + (step.getY() + 1));
        }
        // Op de vlakke vloer: onder de voeten iets vasts, op de voeten zelf lucht.
        BlockPos feet = BlockPos.containing(flat.getX() + 0.5, onFlat, flat.getZ() + 0.5);
        if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                || !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
            throw new GameTestAssertException("Op de vloer: voeten op " + onFlat + " staan niet op de grond");
        }
        helper.succeed();
    }

    // ================================================================ Satelliet-upgrades

    /**
     * Een upgrade kost onderdelen én drones om ze naar boven te brengen. Zonder onderdelen
     * gebeurt er niets; mét gaan ze uit je rugzak, vertrekken de drones, en gaat de upgrade in
     * zodra ze boven zijn.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "satellite_upgrade")
    public static void satellite_upgrade_needs_parts_and_drones(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        // Alleen de rugzak leeg (niet clearContent: dat haalt ook de bril van je hoofd).
        owner.getInventory().items.replaceAll(stack -> ItemStack.EMPTY);
        int ready = ArsenalServer.get(owner).availableCount();

        SatelliteWorkshop.upgrade(owner, SatelliteUpgrade.RECHARGE);
        if (SatelliteWorkshop.get(owner).recharge() != 0 || ArsenalServer.get(owner).availableCount() != ready) {
            throw new GameTestAssertException("Een upgrade zonder onderdelen ging toch door");
        }

        List<ItemStack> cost = SatelliteUpgrade.RECHARGE.costFor(1);
        for (ItemStack part : cost) {
            owner.getInventory().add(part.copy());
        }
        SatelliteWorkshop.upgrade(owner, SatelliteUpgrade.RECHARGE);
        for (ItemStack part : cost) {
            if (SatelliteWorkshop.count(owner.getInventory(), part) != 0) {
                throw new GameTestAssertException("De onderdelen zitten nog in de rugzak: " + part);
            }
        }
        int carriers = SatelliteUpgrade.dronesFor(1);
        if (ArsenalServer.get(owner).availableCount() != ready - carriers) {
            throw new GameTestAssertException("Er vertrokken " + (ready - ArsenalServer.get(owner).availableCount())
                    + " drones in plaats van " + carriers);
        }
        if (SatelliteWorkshop.get(owner).recharge() != 0) {
            throw new GameTestAssertException("De upgrade ging in voordat de drones boven waren");
        }
        helper.runAfterDelay(SatelliteWorkshop.DELIVERY_TICKS + 5, () -> {
            if (SatelliteWorkshop.get(owner).recharge() != 1) {
                throw new GameTestAssertException("De upgrade ging niet in na de levering");
            }
            if (SatelliteStats.cooldownTicks(1) >= SatelliteStats.cooldownTicks(0)) {
                throw new GameTestAssertException("Opladen niveau 1 maakt de wachttijd niet korter");
            }
            helper.succeed();
        });
    }

    /** "Max all" in het satellietscherm: in creative alles op 10, in survival niets. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "satellite_upgrade")
    public static void satellite_max_all_only_in_creative(GameTestHelper helper) {
        ServerPlayer survival = makeSurvivalOwner(helper, new BlockPos(1, 3, 1));
        SatelliteWorkshop.maxAll(survival);
        if (!SatelliteWorkshop.get(survival).equals(SatelliteUpgrades.START)) {
            throw new GameTestAssertException("'Max all' werkte ook in survival");
        }
        ServerPlayer creative = makeEdithOwner(helper, new BlockPos(3, 3, 1));
        SatelliteWorkshop.maxAll(creative);
        for (SatelliteUpgrade upgrade : SatelliteUpgrade.values()) {
            if (SatelliteWorkshop.get(creative).level(upgrade) != SatelliteUpgrade.MAX_LEVEL) {
                throw new GameTestAssertException("Na 'Max all' staat " + upgrade + " niet op het hoogste niveau");
            }
        }
        helper.succeed();
    }

    /**
     * Geen standen meer: elke satellietaanval heeft een eigen toets en vuurt meteen. De eerste
     * druk op de laser (C) kiest alleen het beginpunt; K daarna vuurt gewoon de precisieraket.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "satellite_upgrade")
    public static void satellite_keys_fire_straight_away(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        ItemStack glasses = EdithData.getWornGlasses(owner);
        Husk target = spawnDummyEnemy(helper, new BlockPos(12, 3, 12));
        owner.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.position());
        int full = EdithData.getEnergy(glasses);

        SatelliteWorkshop.install(owner, SatelliteUpgrade.SWEEP, 1);
        EdithServer.handleAction(owner, EdithAction.STRIKE_SWEEP.ordinal());
        if (SatelliteStrikes.pendingFor(owner.getUUID()) != null || EdithData.getEnergy(glasses) != full) {
            throw new GameTestAssertException("De eerste druk op de laser vuurde al (dat is alleen het beginpunt)");
        }
        EdithServer.handleAction(owner, EdithAction.STRIKE_PRECISION.ordinal());
        SatelliteStrikes.Strike strike = SatelliteStrikes.pendingFor(owner.getUUID());
        if (strike == null || strike.mode() != SatelliteMode.PRECISION
                || EdithData.getEnergy(glasses) != full - EdithBalance.SATELLITE_COST_PRECISION) {
            throw new GameTestAssertException("K vuurde niet meteen de precisieraket: " + (strike == null ? "niets" : strike.mode()));
        }
        SatelliteStrikes.cancel(owner.getUUID());
        helper.succeed();
    }

    /**
     * De drie satellietaanvallen in het G-menu vuren meteen. Een aanval die nog op slot zit,
     * doet niets (en kost niets).
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "satellite_upgrade")
    public static void menu_satellite_options_pick_the_mode_and_fire(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        ItemStack glasses = EdithData.getWornGlasses(owner);
        Husk target = spawnDummyEnemy(helper, new BlockPos(12, 3, 12));
        owner.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.position());
        int full = EdithData.getEnergy(glasses);

        EdithServer.handleAction(owner, EdithAction.STRIKE_BOMBARDMENT.ordinal());
        if (EdithData.getEnergy(glasses) != full || SatelliteStrikes.pendingFor(owner.getUUID()) != null) {
            throw new GameTestAssertException("Het bombardement vuurde terwijl het nog op slot zat");
        }

        SatelliteWorkshop.install(owner, SatelliteUpgrade.BOMBARDMENT, 1);
        EdithServer.handleAction(owner, EdithAction.STRIKE_BOMBARDMENT.ordinal());
        if (EdithData.getSatelliteMode(glasses) != SatelliteMode.BOMBARDMENT) {
            throw new GameTestAssertException("De satelliet staat niet op bombardement maar op "
                    + EdithData.getSatelliteMode(glasses));
        }
        if (SatelliteStrikes.pendingFor(owner.getUUID()) == null
                || EdithData.getEnergy(glasses) != full - EdithBalance.SATELLITE_COST_BOMBARDMENT) {
            throw new GameTestAssertException("Het bombardement vuurde niet (of kostte niet "
                    + EdithBalance.SATELLITE_COST_BOMBARDMENT + ")");
        }
        SatelliteStrikes.cancel(owner.getUUID());
        helper.succeed();
    }

    // ================================================================ Fase 15: het drone-arsenaal

    /**
     * Een zwerm komt uit je arsenaal: de drones staan daarna als "onderweg" geboekt, en
     * als ze teruggeroepen worden hangen ze weer klaar - met de schade die ze opliepen.
     */
    @GameTest(template = ARENA, timeoutTicks = 600, batch = "arsenal")
    public static void swarm_comes_from_the_arsenal(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        ItemStack glasses = EdithData.getWornGlasses(owner);
        int before = ArsenalServer.get(owner).availableCount();

        int launched = EdithDrones.launchSwarm(owner, glasses);
        if (launched <= 0) {
            throw new GameTestAssertException("Er vertrok geen enkele drone");
        }
        EdithArsenal after = ArsenalServer.get(owner);
        if (after.deployedCount() != launched) {
            throw new GameTestAssertException("Onderweg: " + after.deployedCount() + ", gelanceerd: " + launched);
        }
        if (after.availableCount() != before - launched) {
            throw new GameTestAssertException("Er hangen er nog " + after.availableCount()
                    + " klaar, verwacht " + (before - launched));
        }
        if (after.size() != before) {
            throw new GameTestAssertException("Het arsenaal werd kleiner terwijl er niets kapot ging");
        }
        // Terugroepen: ze stijgen op en melden zich pas terug als ze hoog genoeg zijn.
        EdithDrones.recallAll(owner, StarkDroneEntity.Mode.ATTACK);
        helper.succeedWhen(() -> {
            EdithArsenal back = ArsenalServer.get(owner);
            if (back.availableCount() != before) {
                throw new GameTestAssertException("Na terugroepen hangen er " + back.availableCount()
                        + " klaar in plaats van " + before);
            }
        });
    }

    /**
     * Drones horen uit de lucht te komen en door de lucht te vertrekken.
     *
     * Hoe hoog ze precies beginnen hangt af van de ruimte boven je: in deze testarena zit een
     * plafond, dus komen ze van net daaronder. Wat we hier vastleggen is dat de mod om veel
     * hoogte vraagt, dat ze zo hoog beginnen als er ruimte is, dat ze duiken, en dat ze bij
     * het terugroepen opstijgen in plaats van ter plekke op te lossen.
     */
    @GameTest(template = ARENA, timeoutTicks = 600, batch = "arsenal")
    public static void drones_arrive_and_leave_through_the_sky(GameTestHelper helper) {
        if (EdithConfig.droneDropHeight() < ArsenalBalance.ORBIT_HEIGHT) {
            throw new GameTestAssertException("De mod vraagt maar " + EdithConfig.droneDropHeight()
                    + " blokken hoogte; verwacht minstens " + ArsenalBalance.ORBIT_HEIGHT);
        }

        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        ItemStack glasses = EdithData.getWornGlasses(owner);
        if (EdithDrones.launchSwarm(owner, glasses) <= 0) {
            throw new GameTestAssertException("Er vertrok geen enkele drone");
        }

        List<StarkDroneEntity> drones = EdithDrones.find(owner, StarkDroneEntity.Mode.ATTACK);
        if (drones.isEmpty()) {
            throw new GameTestAssertException("De gelanceerde drones zijn niet te vinden");
        }
        // Ze beginnen hoog boven je (zo hoog als het plafond toelaat) en duiken naar beneden.
        double highest = 0;
        boolean anyDiving = false;
        for (StarkDroneEntity drone : drones) {
            highest = Math.max(highest, drone.getY() - owner.getY());
            anyDiving |= drone.isDiving();
        }
        if (highest < 8) {
            throw new GameTestAssertException("De hoogste drone begon maar " + Math.round(highest)
                    + " blokken boven je");
        }
        if (!anyDiving) {
            throw new GameTestAssertException("Geen enkele drone is aan het duiken");
        }

        // Even laten vallen tot ze bij je zijn.
        helper.runAfterDelay(40, () -> {
            for (StarkDroneEntity drone : EdithDrones.find(owner, StarkDroneEntity.Mode.ATTACK)) {
                if (drone.isDiving()) {
                    throw new GameTestAssertException("Er duikt er nog een na twee seconden");
                }
                if (drone.getY() - owner.getY() > 8) {
                    throw new GameTestAssertException("Een drone hangt nog op "
                            + Math.round(drone.getY() - owner.getY()) + " blokken hoogte");
                }
            }
            // Terugroepen: ze stijgen op, ze verdwijnen niet meteen.
            EdithDrones.recallAll(owner, StarkDroneEntity.Mode.ATTACK);
            List<StarkDroneEntity> leaving = EdithDrones.find(owner, StarkDroneEntity.Mode.LAUNCH);
            if (leaving.isEmpty()) {
                throw new GameTestAssertException("Na het terugroepen stijgt er niemand op");
            }
            helper.succeed();
        });
    }

    /** Is je arsenaal leeg, dan vertrekt er niets - ook niet als je genoeg energie hebt. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "arsenal")
    public static void empty_arsenal_launches_nothing(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        owner.setData(ModAttachments.ARSENAL, EdithArsenal.EMPTY);
        ItemStack glasses = EdithData.getWornGlasses(owner);

        if (EdithDrones.launchSwarm(owner, glasses) != 0) {
            throw new GameTestAssertException("Er vertrok een zwerm uit een leeg arsenaal");
        }
        if (EdithDrones.launchScout(owner) != null) {
            throw new GameTestAssertException("Er vertrok een verkenner uit een leeg arsenaal");
        }
        if (EdithDrones.launchIllusion(owner) != null) {
            throw new GameTestAssertException("Er vertrok een cloakingdrone uit een leeg arsenaal");
        }
        helper.succeed();
    }

    /**
     * Een drone die gehavend terugkomt, gaat gehavend weer de lucht in: zijn schade blijft
     * in het arsenaal staan tot je hem repareert.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "arsenal")
    public static void damage_survives_a_trip(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        owner.setData(ModAttachments.ARSENAL, EdithArsenal.EMPTY);
        UUID id = ArsenalServer.add(owner);
        float hurt = (float) ArsenalBalance.DRONE_MAX_HEALTH / 2.0f;
        ArsenalServer.returnToOrbit(owner, id, hurt);

        StarkDroneEntity drone = EdithDrones.launchScout(owner);
        if (drone == null) {
            throw new GameTestAssertException("De verkenner vertrok niet");
        }
        if (Math.abs(drone.getHealth() - hurt) > 0.01f) {
            throw new GameTestAssertException("De drone ging de lucht in met " + drone.getHealth()
                    + " leven in plaats van " + hurt);
        }
        drone.recall(helper.getLevel());
        ArsenalDrone back = ArsenalServer.get(owner).find(id);
        if (back == null || Math.abs(back.health() - hurt) > 0.01f) {
            throw new GameTestAssertException("De schade ging verloren: " + back);
        }
        helper.succeed();
    }

    /**
     * Een neergehaalde drone is uit je arsenaal verdwenen. Er komt een beetje schroot terug,
     * maar nooit meer dan je erin stopte.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "arsenal")
    public static void a_wreck_leaves_the_arsenal(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        owner.setData(ModAttachments.ARSENAL, EdithArsenal.EMPTY);
        UUID id = ArsenalServer.add(owner);

        ArsenalServer.destroyed(owner, id, helper.getLevel(),
                helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(3, 3, 3))));
        if (ArsenalServer.get(owner).find(id) != null) {
            throw new GameTestAssertException("De kapotte drone staat nog in het arsenaal");
        }
        // Het schroot is een klein deel van de bouwkosten, nooit het geheel.
        for (int roll = 0; roll < 200; roll++) {
            for (ItemStack scrap : ArsenalCosts.wreckage(helper.getLevel().getRandom())) {
                int full = buildCostOf(scrap);
                if (scrap.getCount() > Math.max(1, (int) Math.ceil(full * ArsenalBalance.WRECK_RECOVERY))) {
                    throw new GameTestAssertException("Te veel schroot: " + scrap.getCount() + "x "
                            + scrap.getHoverName().getString() + " van " + full);
                }
            }
        }
        helper.succeed();
    }

    private static int buildCostOf(ItemStack stack) {
        for (ArsenalCosts.Part part : ArsenalCosts.BUILD_COST) {
            if (stack.is(part.item())) {
                return part.count();
            }
        }
        throw new GameTestAssertException("Schroot dat niet in de bouwkosten zit: " + stack);
    }

    /**
     * De bouwkosten in de code moeten kloppen met het recept. Anders krijg je schroot terug
     * van materialen die er nooit in zaten (of juist niet van materialen die je wel betaalde).
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "arsenal")
    public static void build_cost_matches_the_recipe(GameTestHelper helper) {
        ResourceLocation name = ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "stark_drone");
        RecipeHolder<?> holder = null;
        for (RecipeHolder<?> candidate : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
            if (candidate.id().equals(name)) {
                holder = candidate;
                break;
            }
        }
        if (holder == null) {
            throw new GameTestAssertException("Het recept voor de drone bestaat niet: " + name);
        }
        NonNullList<Ingredient> ingredients = holder.value().getIngredients();
        for (ArsenalCosts.Part part : ArsenalCosts.BUILD_COST) {
            int slots = 0;
            for (Ingredient ingredient : ingredients) {
                if (!ingredient.isEmpty() && ingredient.test(new ItemStack(part.item()))) {
                    slots++;
                }
            }
            if (slots != part.count()) {
                throw new GameTestAssertException("Het recept gebruikt " + slots + "x "
                        + part.item() + ", maar ArsenalCosts zegt " + part.count());
            }
        }
        helper.succeed();
    }

    /**
     * Een Hydra-soldaat met zijn lasergeweer: hij legt aan op een speler in survival en raakt
     * hem. Zijn geweer laat hij nooit vallen.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "hydra")
    public static void hydra_soldier_shoots(GameTestHelper helper) {
        ServerPlayer player = makeSurvivalOwner(helper, new BlockPos(4, 3, 4));
        HydraSoldierEntity soldier = helper.spawn(ModEntities.HYDRA_SOLDIER.get(), new BlockPos(14, 3, 12));
        soldier.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(soldier.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.SPAWN_EGG, null);
        if (!soldier.getMainHandItem().is(ModItems.HYDRA_RIFLE.get())) {
            throw new GameTestAssertException("De soldaat heeft geen lasergeweer");
        }
        soldier.setTarget(player);
        float start = player.getHealth();
        helper.succeedWhen(() -> {
            if (player.getHealth() >= start) {
                throw new GameTestAssertException("De soldaat heeft nog niet geraakt");
            }
        });
    }

    /**
     * Verstop je je achter een muur, dan blijft een Hydra-soldaat daar niet staan wachten: hij
     * zoekt een plek om de muur heen van waaruit hij je wél ziet, en schiet dan.
     */
    @GameTest(template = ARENA, timeoutTicks = 500, batch = "hydra")
    public static void hydra_soldier_flanks_around_a_wall(GameTestHelper helper) {
        // Een muur van drie hoog midden in de arena, met aan beide kanten een doorgang.
        for (int z = 5; z <= 19; z++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(12, y, z), Blocks.STONE_BRICKS);
            }
        }
        ServerPlayer player = makeSurvivalOwner(helper, new BlockPos(19, 2, 12));
        HydraSoldierEntity soldier = helper.spawn(ModEntities.HYDRA_SOLDIER.get(), new BlockPos(5, 2, 12));
        soldier.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(soldier.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.SPAWN_EGG, null);
        if (soldier.hasLineOfSight(player)) {
            throw new GameTestAssertException("De soldaat ziet de speler al door de muur heen (test klopt niet)");
        }
        soldier.setTarget(player);
        float start = player.getHealth();
        helper.succeedWhen(() -> {
            if (player.getHealth() >= start) {
                throw new GameTestAssertException("De soldaat vond nog geen plek om de muur heen ("
                        + soldier.blockPosition() + ")");
            }
        });
    }

    /**
     * Ziet één soldaat je, dan weten zijn maten in de buurt het ook, zelfs een maat die je zelf
     * niet kan zien (hier: in een dicht hokje).
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "hydra")
    public static void hydra_soldiers_alert_each_other(GameTestHelper helper) {
        BlockPos hut = new BlockPos(18, 2, 18);
        for (BlockPos pos : BlockPos.betweenClosed(hut.offset(-1, 0, -1), hut.offset(1, 2, 1))) {
            if (!(pos.getX() == hut.getX() && pos.getZ() == hut.getZ() && pos.getY() < hut.getY() + 2)) {
                helper.setBlock(pos, Blocks.STONE_BRICKS);
            }
        }
        ServerPlayer player = makeSurvivalOwner(helper, new BlockPos(4, 2, 4));
        HydraSoldierEntity first = helper.spawn(ModEntities.HYDRA_SOLDIER.get(), new BlockPos(8, 2, 6));
        HydraSoldierEntity mate = helper.spawn(ModEntities.HYDRA_SOLDIER.get(), hut);
        if (mate.hasLineOfSight(player)) {
            throw new GameTestAssertException("De soldaat in het hokje ziet de speler toch (test klopt niet)");
        }
        first.setTarget(player);
        helper.succeedWhen(() -> {
            if (mate.getTarget() != player) {
                throw new GameTestAssertException("De tweede soldaat weet niet dat er iemand is");
            }
        });
    }

    /**
     * De formaties van de escorte: in het schild hangen ze dicht om je heen (en niet op elkaar),
     * in de pijl achter je (tegen je kijkrichting in), en in de wijde cirkel op vijf blokken.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "formations")
    public static void escort_formations(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(6, 3, 6));
        owner.setYRot(0); // kijkt naar het zuiden (+z)
        Vec3 forward = new Vec3(0, 0, 1);
        for (int slot = 0; slot < 4; slot++) {
            Vec3 shield = DroneFormation.SHIELD.spot(owner, slot, 4, 100);
            if (Math.hypot(shield.x - owner.getX(), shield.z - owner.getZ()) > 1.6) {
                throw new GameTestAssertException("In het schild hangt drone " + slot + " te ver weg");
            }
            Vec3 arrow = DroneFormation.ARROW.spot(owner, slot, 4, 100);
            if (arrow.subtract(owner.position()).dot(forward) >= 0) {
                throw new GameTestAssertException("In de pijl hangt drone " + slot + " niet achter je");
            }
            Vec3 circle = DroneFormation.CIRCLE.spot(owner, slot, 4, 100);
            if (Math.abs(Math.hypot(circle.x - owner.getX(), circle.z - owner.getZ()) - 5) > 0.1) {
                throw new GameTestAssertException("De cirkel is niet vijf blokken wijd");
            }
        }
        if (DroneFormation.SHIELD.spot(owner, 0, 4, 100).distanceTo(DroneFormation.SHIELD.spot(owner, 1, 4, 100)) < 1.0) {
            throw new GameTestAssertException("Twee drones in het schild hangen op elkaar");
        }
        helper.succeed();
    }

    /**
     * Twee drones vlak na elkaar terugroepen: ze landen naast elkaar, niet op elkaar. Het plekje
     * van de eerste is al bezet terwijl hij nog onderweg naar beneden is.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "arsenal_recall")
    public static void recalled_drones_land_side_by_side(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(6, 3, 4));
        owner.setData(ModAttachments.ARSENAL, EdithArsenal.EMPTY);
        UUID first = ArsenalServer.add(owner);
        UUID second = ArsenalServer.add(owner);
        ArsenalServer.returnToOrbit(owner, first, 1.0f);
        ArsenalServer.returnToOrbit(owner, second, 1.0f);
        if (!ArsenalServer.recall(owner, first) || !ArsenalServer.recall(owner, second)) {
            throw new GameTestAssertException("Terugroepen lukte niet");
        }
        StarkDroneEntity a = EdithDrones.findGrounded(owner, first);
        StarkDroneEntity b = EdithDrones.findGrounded(owner, second);
        if (a == null || b == null) {
            throw new GameTestAssertException("Er kwamen geen twee drones naar beneden");
        }
        Vec3 spotA = a.landingSpot();
        Vec3 spotB = b.landingSpot();
        double apart = Math.hypot(spotA.x - spotB.x, spotA.z - spotB.z);
        if (apart < 1.0) {
            throw new GameTestAssertException("Ze landen op elkaar (" + String.format("%.2f", apart) + " blok uit elkaar)");
        }
        helper.succeed();
    }

    /**
     * Terugroepen zet een echte drone naast je neer. Daar repareer je hem met een rechtsklik
     * (ijzer uit je rugzak), en met shift-rechtsklik stuur je hem weer omhoog.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "arsenal")
    public static void recall_repair_and_launch(GameTestHelper helper) {
        ServerPlayer owner = makeEdithOwner(helper, new BlockPos(1, 3, 1));
        owner.setData(ModAttachments.ARSENAL, EdithArsenal.EMPTY);
        UUID id = ArsenalServer.add(owner);
        ArsenalServer.returnToOrbit(owner, id, 1.0f);

        if (!ArsenalServer.recall(owner, id)) {
            throw new GameTestAssertException("Terugroepen lukte niet");
        }
        if (ArsenalServer.get(owner).find(id).state() != ArsenalDrone.State.GROUNDED) {
            throw new GameTestAssertException("De drone staat niet als 'aan de grond' geboekt");
        }
        StarkDroneEntity landed = EdithDrones.findGrounded(owner, id);
        if (landed == null) {
            throw new GameTestAssertException("Er kwam geen drone naast je naar beneden");
        }
        if (Math.abs(landed.getHealth() - 1.0f) > 0.01f) {
            throw new GameTestAssertException("De drone landde met " + landed.getHealth() + " leven");
        }

        // Rechtsklikken zonder ijzer doet niets.
        landed.mobInteract(owner, InteractionHand.MAIN_HAND);
        if (landed.getHealth() > 1.01f) {
            throw new GameTestAssertException("Er werd gerepareerd zonder onderdelen");
        }
        // Met ijzer in je rugzak wel, en het ijzer gaat op.
        owner.getInventory().add(new ItemStack(ArsenalCosts.REPAIR_PART, 64));
        landed.mobInteract(owner, InteractionHand.MAIN_HAND);
        if (landed.getHealth() < landed.getMaxHealth()) {
            throw new GameTestAssertException("De drone is nog niet heel: " + landed.getHealth());
        }
        if (owner.getInventory().countItem(ArsenalCosts.REPAIR_PART) >= 64) {
            throw new GameTestAssertException("Het ijzer werd niet verbruikt");
        }
        ArsenalDrone stored = ArsenalServer.get(owner).find(id);
        if (!stored.isFullyRepaired()) {
            throw new GameTestAssertException("Het arsenaal weet nog niet dat hij heel is: " + stored.health());
        }

        // Nog een klik op een hele drone: hij vertrekt naar zijn baan.
        landed.mobInteract(owner, InteractionHand.MAIN_HAND);
        if (landed.getMode() != StarkDroneEntity.Mode.LAUNCH) {
            throw new GameTestAssertException("De drone stijgt niet op: " + landed.getMode());
        }
        helper.succeed();
    }

    /**
     * De bril hoort alleen uit de kluis van een Stark Tower te komen. Zolang er geen recept
     * voor bestaat, kun je hem in survival nergens anders vandaan halen.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "voice")
    public static void glasses_cannot_be_crafted(GameTestHelper helper) {
        for (RecipeHolder<?> holder : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
            ItemStack result = holder.value().getResultItem(helper.getLevel().registryAccess());
            if (EdithData.isGlasses(result)) {
                throw new GameTestAssertException("Er bestaat weer een recept voor de bril: " + holder.id());
            }
        }
        helper.succeed();
    }

    /** Redstone is noodvoorraad, geen stroomvoorziening: een volle bril kost er honderden. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "voice")
    public static void redstone_is_a_small_top_up(GameTestHelper helper) {
        int perRedstone = ChargingStationBlockEntity.getFuelValue(new ItemStack(Items.REDSTONE));
        if (perRedstone != EdithBalance.ENERGY_PER_REDSTONE) {
            throw new GameTestAssertException("Redstone geeft " + perRedstone + " in plaats van "
                    + EdithBalance.ENERGY_PER_REDSTONE);
        }
        // Een lege bril vol krijgen hoort meer dan een stapel redstone te kosten.
        int needed = EdithBalance.BASE_CAPACITY / perRedstone;
        if (needed <= 64) {
            throw new GameTestAssertException("Een volle bril kost maar " + needed + " redstone; dat is te makkelijk");
        }
        // Een blok hoort ongeveer negen losse stuks waard te zijn, niet meer.
        int perBlock = ChargingStationBlockEntity.getFuelValue(new ItemStack(Items.REDSTONE_BLOCK));
        if (perBlock < perRedstone * 9 || perBlock > perRedstone * 10) {
            throw new GameTestAssertException("Een redstone-blok geeft " + perBlock
                    + "; verwacht ongeveer negen keer " + perRedstone);
        }
        helper.succeed();
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
        return equipAsOwner(helper, helper.makeMockServerPlayerInLevel(), relativePos);
    }

    /**
     * Een speler met bril die NIET in creative staat.
     *
     * De gewone testspeler van Minecraft geeft altijd true terug op isCreative(), en dat is
     * niet te veranderen: het staat hard in makeMockServerPlayerInLevel(). Voor het harnas
     * maakt dat alles uit (in creative vlieg je toch al en krijg je geen schade), dus maken
     * we er hier zelf een die wel gewoon survival is.
     */
    private static ServerPlayer makeSurvivalOwner(GameTestHelper helper, BlockPos relativePos) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "test-survival-player"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        // Alleen isCreative() overschrijven is niet genoeg: een testspeler komt binnen met de
        // rechten van creative (onkwetsbaar, mag vliegen). Die zetten we hier op survival.
        player.getAbilities().invulnerable = false;
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        return equipAsOwner(helper, player, relativePos);
    }

    /** Zet een testspeler op zijn plek, met een volle bril en een gevuld arsenaal. */
    private static ServerPlayer equipAsOwner(GameTestHelper helper, ServerPlayer player, BlockPos relativePos) {
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
        // Zonder drones in een baan vertrekt er niets meer, dus vult een testspeler zijn
        // arsenaal. Ruim genoeg voor de grootste zwerm die een test kan lanceren.
        for (int i = 0; i < TEST_ARSENAL_SIZE; i++) {
            ArsenalServer.add(player);
        }
        return player;
    }

    /** Hoeveel drones een testspeler in zijn arsenaal krijgt. */
    private static final int TEST_ARSENAL_SIZE = 32;

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
                SatelliteMode.BOMBARDMENT, null, null));
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
     * De kluis moet echt een kluis zijn: bewakers (beveiligingstorentjes), laserpoorten op de weg
     * ernaartoe, en deuren die je open moet zien te krijgen. En op de beveiligingsverdieping staat
     * de beveiligingskern, die je met een laptop kunt hacken.
     */
    private static void checkVault(GameTestHelper helper) {
        int emitters = 0;
        int guards = 0;
        int doors = 0;
        int levers = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 41; x++) {
            for (int z = 0; z < 41; z++) {
                for (int y = 67; y < 88; y++) {
                    BlockState state = helper.getBlockState(pos.set(x, y, z));
                    if (state.is(ModBlocks.LASER_EMITTER.get())) {
                        emitters++;
                    } else if (state.is(ModBlocks.SECURITY_TURRET.get())) {
                        guards++;
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
        if (guards < 4) {
            throw new GameTestAssertException("De kluis wordt niet bewaakt: " + guards + " beveiligingstorentjes");
        }
        // De kern op de beveiligingsverdieping (in de rij serverkasten, zie generate_structure.py).
        helper.assertBlockPresent(ModBlocks.SECURITY_CORE.get(), new BlockPos(10, 58, 20));
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
        int redstone = 4;
        station.setItem(ChargingStationBlockEntity.SLOT_FUEL, new ItemStack(Items.REDSTONE, redstone));
        station.setItem(ChargingStationBlockEntity.SLOT_UPGRADE, new ItemStack(ModItems.BATTERY_UPGRADE.get()));

        helper.succeedWhen(() -> {
            ItemStack glasses = station.getItem(ChargingStationBlockEntity.SLOT_GLASSES);
            if (EdithData.getUpgrades(glasses).battery() != 1) {
                throw new GameTestAssertException("Upgrade nog niet ingebouwd");
            }
            if (EdithData.getEnergy(glasses) < redstone * EdithBalance.ENERGY_PER_REDSTONE) {
                throw new GameTestAssertException("Bril nog niet opgeladen: " + EdithData.getEnergy(glasses));
            }
        });
    }
}
