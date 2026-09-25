package com.senne.starktech.client.dev;

import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithFeature;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.hack.EdithHack;
import com.senne.starktech.illusion.EdithIllusions;
import com.senne.starktech.nav.EdithWaypoints;
import com.senne.starktech.nav.Waypoint;
import com.senne.starktech.raid.EdithRaids;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.satellite.SatelliteStrikes;
import com.senne.starktech.shield.DomeGeneratorBlockEntity;
import com.senne.starktech.shield.LaserEmitterBlock;
import com.senne.starktech.spy.EdithSpy;
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
 *        cow        kist    Happy
 *   toren          JIJ (0,0)          piramide
 *      laadstation, zonnepaneel, holotafel, relais+lamp    laserpoort
 *   koepel                                          Stark Tower (ver naar het oosten)
 *            zuid (+z)
 * </pre>
 */
final class ShowcaseScene {
    private ShowcaseScene() {}

    static final String FRIEND = "Happy";

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
    /** De Stark Tower: een hoek van het gebouw (41 x 110 x 41 blokken). */
    static final BlockPos STARK_TOWER = new BlockPos(90, -61, -20);
    static final BlockPos HOME = new BlockPos(34, -60, 30);
    static final BlockPos MINE = new BlockPos(-34, -60, 26);

    // ================================================================ Eén keer: de wereld bouwen

    static void build(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        DevWorld.command("gamerule doDaylightCycle false");
        DevWorld.command("gamerule doWeatherCycle false");
        DevWorld.command("gamerule doMobSpawning false");
        DevWorld.command("gamerule starktechRaidGriefing false");
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
        EdithRedstone.register(player, RELAY);

        // De laserpoort: twee zenders die elkaar aankijken.
        level.setBlockAndUpdate(LASER_A, ModBlocks.LASER_EMITTER.get().defaultBlockState()
                .setValue(LaserEmitterBlock.FACING, Direction.EAST));
        level.setBlockAndUpdate(LASER_B, ModBlocks.LASER_EMITTER.get().defaultBlockState()
                .setValue(LaserEmitterBlock.FACING, Direction.WEST));
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
        player.setData(ModAttachments.WAYPOINTS, EdithWaypoints.EMPTY
                .with(new Waypoint("Home", level.dimension(), HOME, 0x5CFF9D))
                .with(new Waypoint("Mine", level.dimension(), MINE, 0xFFA93F)));
        level.setBlockAndUpdate(HOME.north(2), Blocks.CHEST.defaultBlockState());

        // De vriend: een tweede speler met een eigen bril, om te hacken en te bespioneren.
        ServerPlayer friend = DevWorld.fakePlayer(level, FRIEND, FRIEND_SPOT);
        DevWorld.wearGlasses(friend);
    }

    /** De Stark Tower komt later: dan zijn de stukken wereld waar hij komt al geladen. */
    static void placeTower() {
        DevWorld.command("place template starktech:stark_tower " + STARK_TOWER.getX() + " "
                + STARK_TOWER.getY() + " " + STARK_TOWER.getZ());
    }

    // ================================================================ Voor elke functie: opruimen

    /**
     * Zet de wereld en de speler terug naar het begin: geen drones, illusies, hacks of
     * satellieten meer bezig, volle bril, vol arsenaal, verse doelwitten.
     */
    static void reset(ServerPlayer player, GameType mode) {
        ServerLevel level = player.serverLevel();
        player.stopRiding();
        if (EdithSpy.of(player) != null) {
            EdithSpy.stop(player, "edith.starktech.spy.log.end_recalled");
        }
        EdithHack.stopAll(player, false);
        if (EdithIllusions.countOf(player) > 0) {
            EdithIllusions.stopAll(player); // anders zegt E.D.I.T.H. elke keer "No illusions running"
        }
        SatelliteStrikes.cancel(player.getUUID());
        EdithRaids.clear();
        // Weghalen, niet doden: een gedode drone telt als "neergehaald" en dan meldt E.D.I.T.H.
        // bij de volgende functie "Drone down".
        AABB everywhere = new AABB(START).inflate(400, 200, 400);
        for (Entity entity : level.getEntities((Entity) null, everywhere,
                e -> e instanceof com.senne.starktech.drone.StarkDroneEntity
                        || e instanceof com.senne.starktech.raid.RaidDroneEntity
                        || e instanceof net.minecraft.world.entity.item.ItemEntity)) {
            entity.discard();
        }
        EdithServer.resetForShowcase(player);

        // De bril: eigen, vol, en alle schakelaars uit (behalve de doelanalyse, die staat
        // standaard aan).
        if (EdithData.getWornGlasses(player).isEmpty() || !EdithData.isAuthorized(EdithData.getWornGlasses(player), player)) {
            DevWorld.wearGlasses(player);
        }
        ItemStack glasses = EdithData.getWornGlasses(player);
        for (EdithFeature feature : EdithFeature.values()) {
            boolean wanted = feature == EdithFeature.TARGET_ANALYSIS;
            if (EdithData.isEnabled(glasses, feature) != wanted) {
                EdithData.toggle(glasses, feature);
            }
        }
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
        player.setData(ModAttachments.WAYPOINTS, player.getData(ModAttachments.WAYPOINTS).withActive(EdithWaypoints.NONE));
        level.setBlockAndUpdate(LASER_POWER, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(DOME_POWER, Blocks.AIR.defaultBlockState());
        // De kabel die 'Power grid' doorknipt, altijd weer heel.
        level.setBlockAndUpdate(GRID_CUT, ModBlocks.CABLE.get().defaultBlockState());
        if (level.getBlockEntity(DOME) instanceof DomeGeneratorBlockEntity dome) {
            dome.setEnabled(false);
        }
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
            chest.setItem(2, new ItemStack(ModItems.STARK_CHIP.get(), 4));
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
