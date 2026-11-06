package com.senne.imttech.client.dev;

import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtFeature;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.hack.ImtHack;
import com.senne.imttech.illusion.ImtIllusions;
import com.senne.imttech.nav.ImtWaypoints;
import com.senne.imttech.nav.Waypoint;
import com.senne.imttech.raid.ImtRaids;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.satellite.SatelliteStrikes;
import com.senne.imttech.shield.DomeGeneratorBlockEntity;
import com.senne.imttech.shield.LaserEmitterBlock;
import com.senne.imttech.spy.ImtSpy;
import com.senne.imttech.station.ChargingStationBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * De wereld van de showcase: waar alles staat, en hoe we hem voor elke functie weer netjes
 * klaarzetten.
 *
 * Het is een platte wereld; de grond is y = -61 en je staat op y = -60. Van boven gezien:
 *
 * <pre>
 *            noord (-z)
 *   bunker      husks (doelwitten)      verre husk (satelliet)
 *        cow        kist    Seppe
 *   toren          JIJ (0,0)          piramide
 *      laadstation, zonnepaneel, holotafel, relais+lamp    laserpoort
 *   koepel                                          Biebia's Industries Tower (ver naar het oosten)
 *            zuid (+z)
 * </pre>
 */
final class ShowcaseScene {
    private ShowcaseScene() {}

    static final String FRIEND = "Seppe";

    // ---------------------------------------------------------------- Waar alles staat
    static final BlockPos START = new BlockPos(0, -60, 0);
    /** Drie doelwitten die stil blijven staan. */
    static final List<BlockPos> TARGETS = List.of(
            new BlockPos(-4, -60, -20), new BlockPos(0, -60, -22), new BlockPos(4, -60, -20));
    /** Een doelwit ver weg, voor de satelliet. */
    static final BlockPos FAR_TARGET = new BlockPos(0, -60, -45);
    static final BlockPos COW = new BlockPos(-8, -60, -8);
    static final BlockPos FRIEND_SPOT = new BlockPos(8, -60, -8);
    static final BlockPos CHEST = new BlockPos(-3, -60, -4);
    /** Een dichte bunker met twee husks die op je af willen (voor de dreigingsscan). */
    static final BlockPos BUNKER = new BlockPos(-24, -60, -22);
    static final BlockPos HOLO_TABLE = new BlockPos(0, -60, 6);
    static final BlockPos CHARGING_STATION = new BlockPos(-3, -60, 6);
    static final BlockPos SOLAR_PANEL = new BlockPos(-5, -60, 6);
    static final BlockPos HEAT_GENERATOR = new BlockPos(-7, -60, 6);
    static final BlockPos RELAY = new BlockPos(3, -60, 6);
    /** Het stuk kabel onder de basis dat de showcase doorknipt (tussen laadstation en holotafel). */
    static final BlockPos GRID_CUT = new BlockPos(-1, -61, 6);
    static final BlockPos LAMP = new BlockPos(4, -60, 6);
    static final BlockPos LASER_A = new BlockPos(10, -60, 12);
    static final BlockPos LASER_B = new BlockPos(16, -60, 12);
    /** Waar een redstoneblok komt om de laserpoort aan te zetten (naast zender A). */
    static final BlockPos LASER_POWER = new BlockPos(10, -60, 13);
    static final BlockPos DOME = new BlockPos(-22, -60, 18);
    static final BlockPos DOME_POWER = new BlockPos(-22, -60, 19);
    static final BlockPos PYRAMID = new BlockPos(24, -60, -8);
    static final BlockPos IRON_TOWER = new BlockPos(-16, -60, -2);
    /** De Biebia's Industries Tower: een hoek van het gebouw (41 x 110 x 41 blokken). */
    static final BlockPos BIEBIA_TOWER = new BlockPos(90, -61, -20);
    static final BlockPos HOME = new BlockPos(34, -60, 30);
    static final BlockPos MINE = new BlockPos(-34, -60, 26);
    /** Het midden van een dichte hut waar je je verstopt voor raiddrones (ze breken erin). */
    static final BlockPos HUT = new BlockPos(14, -60, 36);
    /** Het midden van het wrak van de neergestorte satelliet (op het gras, ver naar het westen). */
    static final BlockPos CRASH_SITE = new BlockPos(-80, -61, 0);
    /** De zwarte doos in de romp van het wrak (plek 15, 2, 16 in generate_crash_site.py). */
    static final BlockPos BLACK_BOX = CRASH_SITE.offset(-1, 0, 0);
    /**
     * Waar je voor de deur van de Biebia's Industries-bunker staat (de ingang kijkt naar het noorden, de bunker
     * loopt de berg in naar het zuiden). Een plek (x, y, z) uit tools/generate_bunker.py ligt in
     * de wereld op (23 + x, -61 + y, 80 + z).
     */
    static final BlockPos BIEBIA_BUNKER = new BlockPos(40, -60, 80);
    /** Het Biebia's Industries-torentje van de demo (de warmtegenerator staat ernaast, het Raven-torentje rechts). */
    static final BlockPos TURRET = new BlockPos(0, -60, 40);
    /**
     * De laserbrug (hoofdstuk 34): de middelste van drie brugzenders in de grond, aan de westkant van een gracht. De
     * brug loopt naar het oosten over het water.
     */
    static final BlockPos BRIDGE = new BlockPos(20, -61, 16);
    /** Waar een redstoneblok komt om de brug aan te zetten: onder de noordelijke zender, in de grond. */
    static final BlockPos BRIDGE_POWER = BRIDGE.north().below();
    /**
     * De wapens (hoofdstuk 35): een veld van gladde steen met een muur in het oosten (x + 10). Ertussen een guard
     * robot, een raketturret op stroom en een camera; aan de westkant drie husks om op te schieten.
     */
    static final BlockPos WEAPONS = new BlockPos(160, -61, -60);

    // ================================================================ Eén keer: de wereld bouwen

    static void build(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        DevWorld.command("gamerule doDaylightCycle false");
        DevWorld.command("gamerule doWeatherCycle false");
        DevWorld.command("gamerule doMobSpawning false");
        DevWorld.command("gamerule imttechRaidGriefing false");
        DevWorld.command("kill @e[type=minecraft:slime]");

        // Een piramide en een ijzeren toren: iets om te kopiëren (illusie) en voor de kaart.
        for (int layer = 0; layer < 8; layer++) {
            int r = 7 - layer;
            fill(level, PYRAMID.offset(-r, layer, -r), PYRAMID.offset(r, layer, r), Blocks.STONE.defaultBlockState());
        }
        fill(level, IRON_TOWER, IRON_TOWER.above(14), Blocks.IRON_BLOCK.defaultBlockState());

        // De basis: laadstation, stroom, holotafel, relais met lamp.
        level.setBlockAndUpdate(CHARGING_STATION, ModBlocks.CHARGING_STATION.get().defaultBlockState());
        level.setBlockAndUpdate(SOLAR_PANEL, ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        level.setBlockAndUpdate(HEAT_GENERATOR, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        level.setBlockAndUpdate(HEAT_GENERATOR.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(HOLO_TABLE, ModBlocks.HOLO_TABLE.get().defaultBlockState());
        // Stroom: een kabel onder de grond van de warmtegenerator langs het zonnepaneel en het
        // laadstation naar de holotafel (die doet zonder stroom niets).
        level.setBlockAndUpdate(HEAT_GENERATOR.east(), ModBlocks.CABLE.get().defaultBlockState());
        for (int x = HEAT_GENERATOR.getX() + 1; x <= HOLO_TABLE.getX(); x++) {
            level.setBlockAndUpdate(new BlockPos(x, HOLO_TABLE.getY() - 1, HOLO_TABLE.getZ()),
                    ModBlocks.CABLE.get().defaultBlockState());
        }
        DevWorld.fillGenerator(level, HEAT_GENERATOR);
        level.setBlockAndUpdate(LAMP, Blocks.REDSTONE_LAMP.defaultBlockState());
        level.setBlockAndUpdate(RELAY, ModBlocks.RELAY.get().defaultBlockState());
        ImtRedstone.register(player, RELAY);

        // De laserpoort: twee zenders die elkaar aankijken, nog uit (hoofdstuk 39: de demo zet ze aan, zie lasers).
        level.setBlockAndUpdate(LASER_A, ModBlocks.LASER_EMITTER.get().defaultBlockState()
                .setValue(LaserEmitterBlock.FACING, Direction.EAST).setValue(LaserEmitterBlock.SWITCHED_ON, false));
        level.setBlockAndUpdate(LASER_B, ModBlocks.LASER_EMITTER.get().defaultBlockState()
                .setValue(LaserEmitterBlock.FACING, Direction.WEST).setValue(LaserEmitterBlock.SWITCHED_ON, false));
        level.setBlockAndUpdate(DOME, ModBlocks.DOME_GENERATOR.get().defaultBlockState());
        // Ook de laserzender en de koepel hebben stroom nodig: een warmtegenerator op magma ernaast.
        DevWorld.powerNextTo(level, LASER_A.north());
        DevWorld.powerNextTo(level, DOME.west());
        dome(player).setOwner(player);
        dome(player).setEnabled(false);

        // De bunker: dichte stenen doos, twee husks erin (die krijgen jou als doelwit).
        fill(level, BUNKER.offset(-3, 0, -3), BUNKER.offset(3, 3, 3), Blocks.STONE_BRICKS.defaultBlockState());
        fill(level, BUNKER.offset(-2, 0, -2), BUNKER.offset(2, 2, 2), Blocks.AIR.defaultBlockState());

        // Ertsen onder de grond, voor de ertsscanner.
        BlockState[] ores = {Blocks.DIAMOND_ORE.defaultBlockState(), Blocks.GOLD_ORE.defaultBlockState(),
                Blocks.IRON_ORE.defaultBlockState(), Blocks.REDSTONE_ORE.defaultBlockState(),
                Blocks.EMERALD_ORE.defaultBlockState(), Blocks.LAPIS_ORE.defaultBlockState()};
        for (int i = 0; i < 18; i++) {
            int x = (i * 7) % 15 - 7;
            int z = (i * 11) % 13 - 6;
            level.setBlockAndUpdate(new BlockPos(x, -62 - (i % 2), z), ores[i % ores.length]);
        }

        // Waypoints, en bij "Home" een kist waar de vrachtdrone zijn spullen in legt.
        player.setData(ModAttachments.WAYPOINTS, ImtWaypoints.EMPTY
                .with(new Waypoint("Home", level.dimension(), HOME, 0x5CFF9D))
                .with(new Waypoint("Mine", level.dimension(), MINE, 0xFFA93F)));
        level.setBlockAndUpdate(HOME.north(2), Blocks.CHEST.defaultBlockState());

        // De vriend: een tweede speler met een eigen bril, om te hacken en te bespioneren.
        // Onkwetsbaar: raiddrones kiezen de dichtstbijzijnde speler, en een dode Seppe kun je
        // niet meer hacken, bespioneren of met een nep-leger afleiden.
        ServerPlayer friend = DevWorld.fakePlayer(level, FRIEND, FRIEND_SPOT);
        DevWorld.wearGlasses(friend);
        friend.getAbilities().invulnerable = true;
        friend.onUpdateAbilities();
    }

    /** De Biebia's Industries Tower komt later: dan zijn de stukken wereld waar hij komt al geladen. */
    static void placeTower() {
        DevWorld.command("place template imttech:biebia_tower " + BIEBIA_TOWER.getX() + " "
                + BIEBIA_TOWER.getY() + " " + BIEBIA_TOWER.getZ());
    }

    // ================================================================ Voor elke functie: opruimen

    /**
     * Zet de wereld en de speler terug naar het begin: geen drones, illusies, hacks of
     * satellieten meer bezig, volle bril, vol arsenaal, verse doelwitten.
     */
    static void reset(ServerPlayer player, GameType mode) {
        ServerLevel level = player.serverLevel();
        // Hoofdstuk 38: de oudere functies werken zonder netwerkkabels; de functies over het netwerk zetten het zelf aan.
        com.senne.imttech.lan.LanGrid.overrideRequired(false);
        player.stopRiding();
        if (ImtSpy.of(player) != null) {
            ImtSpy.stop(player, "imt.imttech.spy.log.end_recalled");
        }
        ImtHack.stopAll(player, false);
        if (ImtIllusions.countOf(player) > 0) {
            ImtIllusions.stopAll(player); // anders zegt I.M.T elke keer "No illusions running"
        }
        SatelliteStrikes.cancel(player.getUUID());
        ImtRaids.clear();
        // Weghalen, niet doden: een gedode drone telt als "neergehaald" en dan meldt I.M.T
        // bij de volgende functie "Drone down".
        AABB everywhere = new AABB(START).inflate(400, 200, 400);
        for (Entity entity : level.getEntities((Entity) null, everywhere,
                e -> e instanceof com.senne.imttech.drone.DroneEntity
                        || e instanceof com.senne.imttech.raid.RaidDroneEntity
                        || e instanceof net.minecraft.world.entity.item.ItemEntity
                        || e instanceof net.minecraft.world.entity.projectile.AbstractArrow
                        || e instanceof com.senne.imttech.raven.RavenSoldierEntity
                        || e instanceof com.senne.imttech.suit.MiniRocketEntity)) {
            entity.discard();
        }
        ImtServer.resetForShowcase(player);
        // De torentjes van de demo weg, anders schiet het Raven-torentje ook in andere functies.
        fill(level, TURRET.offset(-2, 0, -4), TURRET.offset(9, 1, 1), Blocks.AIR.defaultBlockState());
        level.getEntitiesOfClass(Husk.class, new AABB(TURRET).inflate(24)).forEach(Entity::discard);

        // De bril: eigen, vol, en alle schakelaars uit (behalve de doelanalyse, die staat
        // standaard aan).
        if (ImtData.getWornGlasses(player).isEmpty() || !ImtData.isAuthorized(ImtData.getWornGlasses(player), player)) {
            DevWorld.wearGlasses(player);
        }
        ItemStack glasses = ImtData.getWornGlasses(player);
        for (ImtFeature feature : ImtFeature.values()) {
            boolean wanted = feature == ImtFeature.TARGET_ANALYSIS;
            if (ImtData.isEnabled(glasses, feature) != wanted) {
                ImtData.toggle(glasses, feature);
            }
        }
        ImtData.setFormation(glasses, com.senne.imttech.drone.DroneFormation.ORBIT);
        player.removeEffect(MobEffects.NIGHT_VISION);
        DevWorld.refill(player);
        DevWorld.freshArsenal(player, 32);

        // Geen harnas en lege handen, tenzij een functie iets anders wil.
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        player.setGameMode(mode);
        // Onkwetsbaar, ook in survival: de raiddrones schieten echt, en een doodsscherm midden
        // in de showcase houdt alles tegen.
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);

        // Geen navigatie actief, schakelaars uit.
        player.setData(ModAttachments.WAYPOINTS, player.getData(ModAttachments.WAYPOINTS).withActive(ImtWaypoints.NONE));
        level.setBlockAndUpdate(LASER_POWER, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(BRIDGE_POWER, Blocks.DIRT.defaultBlockState());
        lasers(level, false);
        bridge(level, false);
        level.setBlockAndUpdate(DOME_POWER, Blocks.AIR.defaultBlockState());
        // De kabel die 'Power grid' doorknipt, altijd weer heel.
        level.setBlockAndUpdate(GRID_CUT, ModBlocks.CABLE.get().defaultBlockState());
        if (level.getBlockEntity(DOME) instanceof DomeGeneratorBlockEntity dome) {
            dome.setEnabled(false);
        }
        // Het laadstation leeg (de upgrade-demo legt er een bril en upgrades in).
        if (level.getBlockEntity(CHARGING_STATION) instanceof ChargingStationBlockEntity station) {
            station.clearContent();
        }
        // Raiddrones mogen alleen in "Raid drones: breaking in" iets slopen.
        DevWorld.command("gamerule imttechRaidGriefing false");
        DevWorld.command("time set noon");
        DevWorld.command("weather clear");

        respawnMobs(level);
        refillChest(level);
    }

    /** Verse doelwitten, een koe en twee husks in de bunker. Wat er nog stond gaat eerst weg. */
    private static void respawnMobs(ServerLevel level) {
        AABB area = new AABB(START).inflate(80, 30, 80);
        for (Entity entity : level.getEntities((Entity) null, area, e -> e instanceof Husk || e instanceof Cow)) {
            entity.discard();
        }
        for (BlockPos pos : TARGETS) {
            DevWorld.spawnStill(level, EntityType.HUSK, pos, 0);
        }
        DevWorld.spawnStill(level, EntityType.HUSK, FAR_TARGET, 0);
        DevWorld.spawnStill(level, EntityType.COW, COW, 90);
        // De husks in de bunker hebben wel een eigen wil: ze willen naar je toe.
        for (int i = 0; i < 2; i++) {
            Husk husk = EntityType.HUSK.create(level);
            if (husk != null) {
                husk.moveTo(BUNKER.getX() - 1 + i * 2 + 0.5, BUNKER.getY(), BUNKER.getZ() + 0.5, 0, 0);
                husk.setPersistenceRequired();
                level.addFreshEntity(husk);
            }
        }
    }

    private static void refillChest(ServerLevel level) {
        level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(CHEST) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 12));
            chest.setItem(1, new ItemStack(Items.IRON_INGOT, 32));
            chest.setItem(2, new ItemStack(ModItems.COMPUTER_CHIP.get(), 4));
        }
    }

    /** De husks in de bunker: geef ze de speler als doelwit (dan tellen ze als dreiging). */
    static void aggroBunker(ServerPlayer player) {
        AABB bunker = new AABB(BUNKER).inflate(4);
        for (Husk husk : player.serverLevel().getEntitiesOfClass(Husk.class, bunker)) {
            husk.setTarget(player);
        }
    }

    /** De tweede speler, of null als die er (nog) niet is. */
    static @Nullable ServerPlayer friend(ServerPlayer player) {
        return player.getServer() == null ? null : player.getServer().getPlayerList().getPlayerByName(FRIEND);
    }

    /** De UUID's van de husks bij de doelwitten (voor markeren vanaf de server). */
    static List<Entity> targets(ServerLevel level) {
        List<Entity> found = new ArrayList<>();
        for (BlockPos pos : TARGETS) {
            found.addAll(level.getEntitiesOfClass(Husk.class, new AABB(pos).inflate(1.5)));
        }
        return found;
    }

    private static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlockAndUpdate(pos, state);
        }
    }

    /** De koepelgenerator van de showcase. */
    static DomeGeneratorBlockEntity dome(ServerPlayer player) {
        return (DomeGeneratorBlockEntity) player.serverLevel().getBlockEntity(DOME);
    }

    /**
     * De laserpoort van de demo aan of uit, zoals I.M.T hem schakelt (hoofdstuk 39: redstone doet niets meer).
     */
    static void lasers(ServerLevel level, boolean on) {
        for (BlockPos pos : new BlockPos[] {LASER_A, LASER_B}) {
            if (level.getBlockState(pos).getBlock() instanceof LaserEmitterBlock) {
                ImtRedstone.switchAt(level, pos, -1, on ? ImtRedstone.Op.ON : ImtRedstone.Op.OFF, 0);
            }
        }
    }

    /** De laserbrug van de demo aan of uit (hoofdstuk 39), zoals I.M.T hem schakelt. */
    static void bridge(ServerLevel level, boolean on) {
        if (level.getBlockState(BRIDGE).getBlock() instanceof com.senne.imttech.shield.LaserBridgeBlock) {
            ImtRedstone.switchAt(level, BRIDGE, -1, on ? ImtRedstone.Op.ON : ImtRedstone.Op.OFF, 0);
        }
    }

    /**
     * De laserbrug (hoofdstuk 34): een gracht met drie brugzenders in de westrand. Elke keer opnieuw gebouwd, zodat de
     * showcase altijd hetzelfde laat zien (ook in een wereld van vóór de laserbrug).
     */
    static void buildBridge(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // De gracht: 6 breed, 3 diep, met onderin water.
        fill(level, BRIDGE.offset(1, 0, -3), BRIDGE.offset(6, -1, 3), Blocks.AIR.defaultBlockState());
        fill(level, BRIDGE.offset(1, -2, -3), BRIDGE.offset(6, -2, 3), Blocks.WATER.defaultBlockState());
        // Drie brugzenders naast elkaar in de rand van de vloer (samen één brug van drie breed), van jou en cyaan.
        for (int dz = -1; dz <= 1; dz++) {
            BlockPos emitter = BRIDGE.offset(0, 0, dz);
            level.setBlockAndUpdate(emitter, ModBlocks.LASER_BRIDGE.get().defaultBlockState()
                    .setValue(com.senne.imttech.shield.LaserBridgeBlock.FACING, Direction.EAST)
                    .setValue(com.senne.imttech.shield.LaserBridgeBlock.SWITCHED_ON, false));
            if (level.getBlockEntity(emitter) instanceof com.senne.imttech.shield.LaserBridgeBlockEntity bridge) {
                bridge.setOwner(player);
            }
        }
        // Stroom: een warmtegenerator op magma onder de middelste zender (de zenders geven hem aan elkaar door).
        DevWorld.powerNextTo(level, BRIDGE.below());
        level.setBlockAndUpdate(BRIDGE_POWER, Blocks.DIRT.defaultBlockState());
    }

    /**
     * Het veld voor de wapens (hoofdstuk 35), elke keer opnieuw: de muur heeft na de frag-granaat een gat. Een guard
     * robot en een raketturret op stroom vlak voor de muur, en drie husks die niets doen (doelwitten) in het westen.
     */
    static void buildWeapons(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        fill(level, WEAPONS.offset(-14, 1, -8), WEAPONS.offset(12, 7, 8), Blocks.AIR.defaultBlockState());
        fill(level, WEAPONS.offset(-14, 0, -8), WEAPONS.offset(12, 0, 8), Blocks.SMOOTH_STONE.defaultBlockState());
        fill(level, WEAPONS.offset(10, 1, -6), WEAPONS.offset(10, 5, 6), Blocks.STONE_BRICKS.defaultBlockState());
        for (Entity old : level.getEntitiesOfClass(Entity.class, new AABB(WEAPONS).inflate(16),
                entity -> !(entity instanceof net.minecraft.world.entity.player.Player))) {
            old.discard();
        }
        BlockPos turret = WEAPONS.offset(8, 1, 3);
        level.setBlockAndUpdate(turret, ModBlocks.ROCKET_TURRET.get().defaultBlockState());
        DevWorld.powerNextTo(level, turret.below());
        DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get(), WEAPONS.offset(7, 1, -2), 90);
        for (int z = -4; z <= 4; z += 4) {
            DevWorld.spawnStill(level, EntityType.HUSK, WEAPONS.offset(-12, 1, z), -90);
        }
    }

    /**
     * Een dichte hut van 5 x 5 met stenen muren, een glazen dak en een lamp in de vloer: binnen
     * zien raiddrones je niet, dus breken ze erin. Door het dak zie je ze aankomen. Elke keer
     * opnieuw gebouwd, want na de demo zitten er gaten in.
     */
    static void buildHut(ServerLevel level) {
        fill(level, HUT.offset(-2, 0, -2), HUT.offset(2, 3, 2), Blocks.STONE_BRICKS.defaultBlockState());
        fill(level, HUT.offset(-2, 4, -2), HUT.offset(2, 4, 2), Blocks.GLASS.defaultBlockState());
        fill(level, HUT.offset(-1, 0, -1), HUT.offset(1, 3, 1), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(HUT.below(), Blocks.SEA_LANTERN.defaultBlockState());
    }

    /**
     * De torentjes-demo: een raketturret met een volle warmtegenerator ernaast en 16 raketten erin (van jou), en
     * rechts daarvan een laserturret van Biebia's aan een kern van niemand.
     */
    static void buildTurrets(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.setBlockAndUpdate(TURRET, ModBlocks.ROCKET_TURRET.get().defaultBlockState());
        if (level.getBlockEntity(TURRET) instanceof com.senne.imttech.turret.TurretBlockEntity turret) {
            turret.setOwner(player);
            turret.ammo().insertItem(0, new ItemStack(com.senne.imttech.registry.ModItems.ROCKET_AMMO.get(), 16), false);
        }
        level.setBlockAndUpdate(TURRET.east(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(TURRET.east()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
            generator.fill(100_000);
        }
        // Rechts een laserturret van Biebia's (hoofdstuk 54: het Raven-torentje is er niet meer), aan een kern van
        // niemand met een generator eronder: die schiet op iedereen, ook op jou.
        BlockPos laser = TURRET.offset(7, 0, -2);
        level.setBlockAndUpdate(laser, ModBlocks.SECURITY_TURRET.get().defaultBlockState());
        level.setBlockAndUpdate(laser.east(), ModBlocks.SECURITY_CORE.get().defaultBlockState());
        level.setBlockAndUpdate(laser.east().below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(laser.east().below()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
            generator.fill(100_000);
        }
    }

    /** Twee husks ver voor het torentje, die op je af komen. */
    static void turretAttackers(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (int x = -2; x <= 2; x += 4) {
            Husk husk = EntityType.HUSK.create(level);
            if (husk != null) {
                husk.moveTo(TURRET.getX() + x + 0.5, TURRET.getY(), TURRET.getZ() - 23 + 0.5, 0, 0);
                level.addFreshEntity(husk);
                husk.setTarget(player);
            }
        }
    }

    /** Een pijl (van niemand) die van buiten op de koepel af vliegt. */
    static void shootAtDome(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.entity.projectile.Arrow arrow =
                new net.minecraft.world.entity.projectile.Arrow(net.minecraft.world.entity.EntityType.ARROW, level);
        arrow.setPos(DOME.getX() + 0.5, DOME.getY() + 5, DOME.getZ() - 22);
        arrow.setDeltaMovement(0, 0, 2.0);
        arrow.setNoGravity(true);
        level.addFreshEntity(arrow);
    }
}
