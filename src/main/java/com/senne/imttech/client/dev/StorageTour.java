package com.senne.imttech.client.dev;

import com.senne.imttech.ImtTech;
import com.senne.imttech.car.CarProgram;
import com.senne.imttech.car.DrillHead;
import com.senne.imttech.car.RcCarEntity;
import com.senne.imttech.car.RcCars;
import com.senne.imttech.client.car.RcCarScreen;
import com.senne.imttech.client.os.RcCarProgramScreen;
import com.senne.imttech.client.os.SecurityOsScreen;
import com.senne.imttech.client.storage.StorageScreen;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.lan.RouterBlock;
import com.senne.imttech.lan.RouterBlockEntity;
import com.senne.imttech.lan.RouterLinks;
import com.senne.imttech.lan.Routers;
import com.senne.imttech.network.RcCarActionPayload;
import com.senne.imttech.security.os.SecurityOs;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.station.ChargerBlockEntity;
import com.senne.imttech.storage.ChestSensorBlock;
import com.senne.imttech.storage.StorageActionPayload;
import com.senne.imttech.storage.StorageInterfaceBlock;
import com.senne.imttech.storage.StorageInterfaceBlockEntity;
import com.senne.imttech.storage.StorageMenu;
import com.senne.imttech.storage.StorageRegistry;
import com.senne.imttech.storage.Storages;
import com.senne.imttech.weapons.EnergyCellItem;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het opslagsysteem (hoofdstuk 113) in de rondleiding met screenshots en in de showcase.
 *
 * <ul>
 *   <li>{@code gradlew runVisualCheck -Ponly=storage}: een opslagkamer: de Storage Interface met de auto "Porter" ervoor
 *   (op een Charging Pad), en rechts een gang langs vier stapels tonnen van drie hoog. Op één stapel zit een Chest Sensor
 *   aan een netwerkkabel, op een andere een Chest Sensor aan een Router. Het overzicht, de sensors van dichtbij, de auto
 *   met zijn arm omhoog, het scherm van de interface (een item kiezen, een lopende en een afgeronde bestelling, de
 *   tabbladen Store en Cars), Porter die echt opbergt (zijn arm omhoog bij een stapel) en het scherm erna, het scherm
 *   van de auto met de knoppen van de arm, de RC programs van de server (hoofdstuk 119: zijn programma Storage staat
 *   erop), de laadplaat, en de drie items met hun tooltip. Screenshots st01 tot st14.</li>
 *   <li>{@code gradlew runVisualCheck -Ponly=howtopics:storage}: de twee plaatjes voor de animatie van How it works
 *   ({@code storage_screen}, en de app Logic met het RC program van Porter: {@code logic_rc_storage}); die komen in de
 *   mod zelf te staan.</li>
 *   <li>De showcase-functie "Storage system" (zie ShowcaseFeatures): {@link #showcase}.</li>
 * </ul>
 */
final class StorageTour {
    private StorageTour() {}

    /** Een open plek, ver van de rest. Hier staat de auto thuis (op de laadplaat), met zijn neus naar het noorden. */
    static final BlockPos YARD = new BlockPos(-620, -60, 340);
    private static final BlockPos HOME = YARD;
    private static final BlockPos STATION = HOME.north();
    /** De sensor met de kabel zit boven op stapel 2, die met de router boven op stapel 4. */
    private static final BlockPos CABLE_SENSOR = barrelAt(2, 2).above();
    private static final BlockPos ROUTER_SENSOR = barrelAt(4, 2).above();
    private static final BlockPos ROUTER = HOME.offset(7, 0, -1);

    /** De ton van stapel {@code stack} (1 tot 4, langs de gang rechts van de auto) op hoogte {@code level} (0 tot 2). */
    private static BlockPos barrelAt(int stack, int level) {
        return new BlockPos(HOME.getX() + stack, HOME.getY() + level, HOME.getZ() - 1);
    }

    /** Wat er in de tonnen ligt: per stapel van onder naar boven. */
    private static final Item[][] ITEMS = {
            {Items.COBBLESTONE, Items.OAK_PLANKS, Items.GLASS},
            {Items.IRON_INGOT, Items.GOLD_INGOT, Items.REDSTONE},
            {Items.COAL, Items.TORCH, Items.BREAD},
            {Items.ARROW, Items.ENDER_PEARL, Items.DIAMOND},
    };
    private static final int[][] COUNTS = {{96, 48, 20}, {40, 12, 64}, {64, 64, 20}, {64, 6, 9}};

    /** Het programma dat de speler van de rondleiding voor zijn auto schreef: het voorbeeld van How it works. */
    private static final String WRITTEN = CarProgram.EXAMPLE_STORAGE;

    /**
     * Hoofdstuk 119: de Security Core van de basis van de opslagkamer (met een server, en daarop het RC program "Storage"
     * van Porter). Buiten het stuk dat het toneel leegmaakt, links achter de auto.
     */
    private static final BlockPos BASE = YARD.offset(-9, 0, 4);

    /** De interface van het toneel (op de server), of null. */
    private static @Nullable StorageInterfaceBlockEntity station(ServerPlayer player) {
        return player.serverLevel().getBlockEntity(STATION) instanceof StorageInterfaceBlockEntity station ? station : null;
    }

    /** De auto van het toneel (op de server), of null. */
    private static @Nullable RcCarEntity car(ServerPlayer player) {
        for (RcCarEntity car : player.serverLevel().getEntitiesOfClass(RcCarEntity.class, new AABB(YARD).inflate(24))) {
            if (car.displayName().equals("Porter")) {
                return car;
            }
        }
        return null;
    }

    /** Het nummer van de auto in je eigen spel (voor de berichten die zijn scherm stuurt), of -1. */
    private static int carId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (RcCarEntity car : mc.level.getEntitiesOfClass(RcCarEntity.class, new AABB(YARD).inflate(24))) {
                if (car.displayName().equals("Porter")) {
                    return car.getId();
                }
            }
        }
        return -1;
    }

    /** De sensor op deze plek is van deze speler (zoals in het spel: van wie hem neerzet; de interface leest alleen die van jou). */
    private static void sensorOf(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level.getBlockEntity(pos) instanceof com.senne.imttech.storage.ChestSensorBlockEntity sensor) {
            sensor.setOwner(player);
        }
    }

    /** Een volle warmtegenerator op deze plek. */
    private static void generator(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof ChargerBlockEntity charger) {
            charger.fill(200_000);
        }
    }

    /**
     * Zet het toneel klaar: de interface (van jou), de laadplaat onder de auto met een generator ernaast, vier stapels
     * tonnen met spullen, een Chest Sensor met een netwerkkabel naar de interface, een Chest Sensor aan een Router (die
     * zelf draadloos aan de interface hangt), de basis (een kern met een server en het RC program Storage, hoofdstuk 119),
     * en de auto "Porter" met dat RC program, gekoppeld aan de interface. De interface weet al wat er in elke ton zit
     * (alsof Porter er eerder in keek).
     */
    private static void stage(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (RcCarEntity old : level.getEntitiesOfClass(RcCarEntity.class, new AABB(YARD).inflate(24))) {
            old.discard();
        }
        RouterLinks links = RouterLinks.get(level);
        for (BlockPos old : links.routers()) {
            if (old.closerThan(YARD, 32)) {
                links.removeRouter(old);
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(YARD.offset(-6, 0, -8), YARD.offset(12, 6, 8))) {
            for (CableKind kind : CableKind.values()) {
                Cables.remove(kind, level, pos);
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        // Een muur achter de tonnen en de interface (zo zie je de kabel erachter niet zweven).
        for (BlockPos pos : BlockPos.betweenClosed(HOME.offset(-1, 0, -3), HOME.offset(5, 3, -3))) {
            level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(STATION, StorageRegistry.STORAGE_INTERFACE.get().defaultBlockState()
                .setValue(StorageInterfaceBlock.FACING, Direction.SOUTH));
        StorageInterfaceBlockEntity station = station(player);
        if (station == null) {
            return;
        }
        station.setOwner(player);
        station.setName("Main storage");
        // De laadplaat onder de auto, met stroom.
        generator(level, HOME.west());
        level.setBlockAndUpdate(HOME, StorageRegistry.CHARGING_PAD.get().defaultBlockState());
        // De tonnen, met hun spullen.
        for (int stack = 1; stack <= 4; stack++) {
            for (int height = 0; height < 3; height++) {
                BlockPos pos = barrelAt(stack, height);
                level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.SOUTH));
                if (level.getBlockEntity(pos) instanceof Container barrel) {
                    int count = COUNTS[stack - 1][height];
                    Item item = ITEMS[stack - 1][height];
                    for (int slot = 0; count > 0; slot++) {
                        int part = Math.min(count, item.getDefaultMaxStackSize());
                        barrel.setItem(slot, new ItemStack(item, part));
                        count -= part;
                    }
                }
            }
        }
        // De sensor met een netwerkkabel: achter de tonnen langs, van de interface omhoog naar de sensor.
        level.setBlockAndUpdate(CABLE_SENSOR, StorageRegistry.CHEST_SENSOR.get().defaultBlockState().setValue(ChestSensorBlock.FACING, Direction.UP));
        sensorOf(level, CABLE_SENSOR, player);
        for (BlockPos cable : new BlockPos[] {STATION.north(), STATION.north().east(), STATION.north().east(2),
                STATION.north().east(2).above(), STATION.north().east(2).above(2), STATION.north().east(2).above(3)}) {
            Cables.add(CableKind.NETWORK, level, cable, player.getUUID());
        }
        // De sensor aan een Router: de router (met een generator ernaast) hangt zelf draadloos aan de interface.
        level.setBlockAndUpdate(ROUTER_SENSOR, StorageRegistry.CHEST_SENSOR.get().defaultBlockState().setValue(ChestSensorBlock.FACING, Direction.UP));
        sensorOf(level, ROUTER_SENSOR, player);
        generator(level, ROUTER.east());
        level.setBlockAndUpdate(ROUTER, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(ROUTER) instanceof RouterBlockEntity router) {
            router.setOwner(player);
            router.setName("Storage router");
            router.updateOnline(level);
        }
        links.addRouter(ROUTER);
        Routers.link(level, ROUTER, STATION);
        Routers.link(level, ROUTER, ROUTER_SENSOR);
        // De basis: zijn programma staat als RC program op de server (hoofdstuk 119).
        RcCarTour.base(player, BASE, "Storage", WRITTEN);
        // De auto, thuis op de laadplaat, gekoppeld aan de interface.
        RcCarEntity car = ModEntities.RC_CAR.get().create(level);
        if (car != null) {
            float yaw = Direction.NORTH.toYRot();
            car.moveTo(HOME.getX() + 0.5, HOME.getY() + 0.125, HOME.getZ() + 0.5, yaw, 0);
            car.setYHeadRot(yaw);
            car.setYBodyRot(yaw);
            car.body().setFacing(Direction.NORTH);
            car.setOwner(player);
            car.setCustomName(Component.literal("Porter"));
            car.swapCell(EnergyCellItem.withCharge(72.0f));
            car.setDrill(DrillHead.IRON);
            car.setRobotArm(true); // hoofdstuk 118: zonder arm komt hij niet bij de kisten hoger dan het blok vóór hem
            String problem = car.loadProgram("Storage", WRITTEN);
            if (problem != null) {
                ImtTech.LOGGER.warn("[visual check] het programma van Porter klopt niet: {}", problem);
            }
            level.addFreshEntity(car);
            RcCars.register(car);
            station.link(car);
            car.setStation(GlobalPos.of(level.dimension(), STATION));
        }
        // Wat er in de tonnen zit, weet de interface al.
        for (int stack = 1; stack <= 4; stack++) {
            for (int height = 0; height < 3; height++) {
                BlockPos pos = barrelAt(stack, height);
                var slots = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                if (slots != null) {
                    station.saw(pos, slots, level.getGameTime());
                }
            }
        }
    }

    /** Doe dit met het scherm van de interface, als het open staat. */
    private static void screen(Consumer<StorageScreen> action) {
        if (Minecraft.getInstance().screen instanceof StorageScreen screen) {
            action.accept(screen);
        } else {
            ImtTech.LOGGER.warn("[visual check] het scherm van de Storage Interface staat niet open (maar {})", Minecraft.getInstance().screen);
        }
    }

    /**
     * Doet Porter nu niets? Dat lezen we van de auto in je eigen spel (wat hij doet, stuurt de server mee): de wereld van
     * de server mogen we vanaf hier niet lezen. Na een bestelling eerst even wachten (de interface start de auto binnen
     * een halve seconde), dan hierop.
     */
    private static boolean carIdle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (RcCarEntity car : mc.level.getEntitiesOfClass(RcCarEntity.class, new AABB(YARD).inflate(24))) {
                if (car.displayName().equals("Porter")) {
                    return car.job().isEmpty();
                }
            }
        }
        return false;
    }

    /** Hoe hoog staat de arm van Porter nu (0 tot 2), in je eigen spel? -1 = hij is er niet. */
    private static int carArm() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (RcCarEntity car : mc.level.getEntitiesOfClass(RcCarEntity.class, new AABB(YARD).inflate(24))) {
                if (car.displayName().equals("Porter")) {
                    return car.arm();
                }
            }
        }
        return -1;
    }

    /** Wacht tot Porter klaar is met de bestelling die net geplaatst is (en het scherm het weet). */
    private static void waitForCar(DevScript t) {
        t.pause(40);
        t.waitUntil(1200, StorageTour::carIdle);
        t.pause(20);
    }

    /** De kijkhoek van voor het inzoomen. */
    private static int normalFov = 70;

    /** Inzoomen (een smalle kijkhoek) voor een foto van iets kleins, of weer terug. */
    private static void zoom(boolean in) {
        var fov = Minecraft.getInstance().options.fov();
        if (in) {
            normalFov = fov.get();
            fov.set(40);
        } else {
            fov.set(normalFov);
        }
    }

    private static void open(DevScript t) {
        t.onServer(10, player -> {
            StorageInterfaceBlockEntity station = station(player);
            if (station != null) {
                Storages.open(player, station);
            }
        });
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof StorageScreen);
        t.step(12, () -> HowToTour.mouseTo(2, 2));
        t.step(0, () -> Minecraft.getInstance().getToasts().clear());
    }

    static void tour(DevScript t) {
        double x = YARD.getX() + 0.5;
        double z = YARD.getZ() + 0.5;
        t.stand(0, x + 2, YARD.getY(), z + 6, 180, 10);
        t.waitUntil(600, () -> VisualCheck.raidLeaveTicking(YARD));
        t.command(0, "time set noon");
        t.onServer(10, StorageTour::stage);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        // Het overzicht: de interface, de auto op de laadplaat, de gang en de vier stapels tonnen met de sensors erop.
        t.step(0, () -> zoom(true));
        t.view(20, x + 2.5, YARD.getY() + 3.0, z + 9.5, 180, 12);
        t.shot(30, "st01_storage_room");
        // De twee sensors en de router van dichtbij, van schuin boven.
        t.view(0, x + 4.0, YARD.getY() + 7.0, z + 4.5, 180, 42);
        t.shot(20, "st02_chest_sensors");
        // De auto met zijn arm omhoog bij een stapel (hier voor de foto: zijn arm bij de bovenste ton van de stapel, stand 2).
        t.onServer(0, player -> {
            RcCarEntity car = car(player);
            if (car != null) {
                car.setArm(2);
            }
        });
        t.view(0, x + 2.8, YARD.getY() + 0.3, z + 4.6, 149, 4);
        t.shot(30, "st03_car_arm_up");
        t.onServer(0, player -> {
            RcCarEntity car = car(player);
            if (car != null) {
                car.setArm(0);
            }
        });
        t.step(0, () -> zoom(false));
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        // Het scherm van de interface: een item kiezen...
        t.stand(10, x, YARD.getY(), z + 2.5, 180, 20);
        open(t);
        t.step(5, () -> screen(screen -> screen.tourSelect("iron_ingot", 12)));
        t.shot(10, "st04_screen_order");
        // ...bestellen: de bestelling loopt (Porter is onderweg)...
        t.step(0, () -> PacketDistributor.sendToServer(StorageActionPayload.fetch(new ItemStack(Items.IRON_INGOT), 12)));
        t.shot(45, "st05_screen_running");
        // ...en klaar: het ijzer ligt in de afhaalvakjes.
        waitForCar(t);
        t.shot(10, "st06_screen_done");
        // Het tabblad Store, met spullen om op te bergen; en Cars.
        t.onServer(0, player -> {
            StorageInterfaceBlockEntity station = station(player);
            if (station != null) {
                station.in().setItem(0, new ItemStack(Items.COBBLESTONE, 32));
                station.in().setItem(1, new ItemStack(Items.DIAMOND, 3));
            }
        });
        t.step(5, () -> screen(screen -> screen.tourTab(StorageMenu.TAB_STORE)));
        t.shot(15, "st07_screen_store");
        t.step(0, () -> screen(screen -> screen.tourTab(StorageMenu.TAB_CARS)));
        t.shot(15, "st08_screen_cars");
        // Opbergen: de knop Store (de stenen en de diamanten worden twee bestellingen). Porter haalt de stenen bij de
        // interface en brengt ze naar de eerste stapel; de foto komt als zijn arm daar helemaal boven is.
        t.step(0, () -> PacketDistributor.sendToServer(StorageActionPayload.store()));
        t.close(5);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.step(0, () -> zoom(true));
        t.view(0, x + 3.9, YARD.getY() + 0.4, z + 4.1, 145, 6);
        t.waitUntil(600, () -> carArm() == 2);
        t.shot(3, "st08b_car_at_work");
        t.step(0, () -> zoom(false));
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        // Twee bestellingen: twee keer wachten tot Porter klaar is. Dan het scherm: alles done, de vakjes van Store leeg.
        waitForCar(t);
        waitForCar(t);
        t.stand(10, x, YARD.getY(), z + 2.5, 180, 20);
        open(t);
        t.shot(10, "st08c_screen_stored");
        t.close(5);
        // Het scherm van de auto: de stand van zijn arm met de twee knoppen, en de RC programs van de server (Program).
        t.onServer(10, player -> {
            RcCarEntity car = car(player);
            if (car != null) {
                RcCars.open(player, car, false);
            }
        });
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof RcCarScreen);
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.shot(15, "st09_car_screen_arm");
        t.step(0, () -> {
            int id = carId();
            if (id >= 0) {
                PacketDistributor.sendToServer(new RcCarActionPayload(id, RcCarActionPayload.Op.PROGRAMS, ""));
            }
        });
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof RcCarProgramScreen);
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.shot(15, "st10_car_program");
        t.close(5);
        // De laadplaat: Porter met een halflege cel, de strepen gloeien.
        t.onServer(0, player -> {
            RcCarEntity car = car(player);
            if (car != null) {
                car.swapCell(EnergyCellItem.withCharge(35.0f));
            }
        });
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.step(0, () -> zoom(true));
        t.view(10, x + 2.4, YARD.getY() + 0.4, z + 2.6, 137, 26);
        t.shot(30, "st11_charging_pad");
        t.step(0, () -> zoom(false));
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        // De drie items in je inventaris, elk met zijn tooltip (in survival: in creative opent het creative-menu).
        t.command(0, "gamemode survival @a");
        t.onServer(0, player -> {
            player.getInventory().setItem(0, new ItemStack(StorageRegistry.STORAGE_INTERFACE_ITEM.get()));
            player.getInventory().setItem(1, new ItemStack(StorageRegistry.CHEST_SENSOR_ITEM.get()));
            player.getInventory().setItem(2, new ItemStack(StorageRegistry.CHARGING_PAD_ITEM.get()));
            player.getInventory().selected = 4;
        });
        t.open(10, () -> new InventoryScreen(Minecraft.getInstance().player));
        for (int slot = 0; slot < 3; slot++) {
            int column = slot;
            t.step(5, () -> {
                if (Minecraft.getInstance().screen instanceof InventoryScreen screen) {
                    HowToTour.mouseTo(screen.getGuiLeft() + 16 + column * 18, screen.getGuiTop() + 150);
                }
            });
            t.shot(6, "st1" + (2 + slot) + "_item_tooltip");
        }
        t.close(5);
        t.onServer(0, player -> {
            for (int slot = 0; slot < 3; slot++) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        });
        t.command(0, "gamemode creative @a");
    }

    /**
     * De plaatjes van echte schermen voor de animatie van How it works: het scherm van de interface (alleen zijn paneel:
     * een item gekozen, een bestelling klaar), en de app Logic met het RC program van de auto (het hele venster, hoofdstuk
     * 119). Ze komen in src/main/resources te staan (zie HowToTour.capture).
     */
    static void pictures(DevScript t) {
        double x = YARD.getX() + 0.5;
        double z = YARD.getZ() + 0.5;
        t.stand(0, x, YARD.getY(), z + 2.5, 180, 20);
        t.waitUntil(600, () -> VisualCheck.raidLeaveTicking(YARD));
        t.command(0, "time set noon");
        t.onServer(10, StorageTour::stage);
        // Geen HUD achter de schermen: die schijnt anders door het paneel heen.
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        // Eerst een bestelling laten lopen, zodat er iets in de afhaalvakjes en in de lijst staat.
        t.onServer(20, player -> {
            StorageInterfaceBlockEntity station = station(player);
            if (station != null) {
                station.fetch(player.serverLevel(), com.senne.imttech.storage.ItemKind.of(new ItemStack(Items.BREAD)), 6, player);
            }
        });
        waitForCar(t);
        open(t);
        t.step(5, () -> screen(screen -> screen.tourSelect("iron_ingot", 12)));
        t.step(15, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof StorageScreen screen) {
                HowToTour.capture("storage_screen", (screen.width - StorageMenu.WIDTH) / 2, (screen.height - StorageMenu.HEIGHT) / 2,
                        StorageMenu.WIDTH, StorageMenu.HEIGHT);
            }
        });
        t.close(5);
        // De app Logic op de desktop van de kern, met het RC program van Porter (hoofdstuk 119).
        t.onServer(10, player -> {
            player.teleportTo(BASE.getX() + 0.5, BASE.getY(), BASE.getZ() + 2.5);
            SecurityOs.open(player, BASE);
        });
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof SecurityOsScreen);
        t.step(5, () -> HowToTour.mouseTo(1, 1));
        t.step(5, () -> {
            if (Minecraft.getInstance().screen instanceof SecurityOsScreen screen) {
                screen.tourCloseAll();
                screen.tourLogicOpen("Storage");
            }
        });
        t.pause(14);
        t.waitUntil(40, HowToTour::caretHidden);
        t.step(0, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof SecurityOsScreen) {
                HowToTour.capture("logic_rc_storage", 0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            } else {
                ImtTech.LOGGER.warn("[visual check] de desktop staat niet open (maar {})", mc.screen);
            }
        });
        t.close(5);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
    }

    // ================================================================ De showcase

    /** Waar je staat in de showcase: voor de gang, met de interface links en de tonnen voor je. */
    static final Vec3 SHOW_FROM = Vec3.atBottomCenterOf(YARD.offset(2, 0, 5));

    /**
     * De showcase-functie "Storage system": de opslagkamer, het scherm van de interface (ijzer kiezen en bestellen),
     * Porter die de gang in rijdt en met zijn arm in de tonnen kijkt, het ijzer in de afhaalvakjes, en dan opbergen: stenen
     * en diamanten in het tabblad Store, en Porter die ze wegbrengt.
     */
    static void showcase(DevScript s) {
        double x = YARD.getX() + 0.5;
        double z = YARD.getZ() + 0.5;
        s.command(0, "time set noon")
                .onServer(0, StorageTour::stage)
                .pause(120)
                // Het scherm: wat er in de opslag zit, en 12 ijzer bestellen.
                .stand(0, x, YARD.getY(), z + 2.5, 180, 20);
        open(s);
        s.pause(80)
                .step(0, () -> screen(screen -> screen.tourSelect("iron_ingot", 12)))
                .pause(80)
                .step(0, () -> PacketDistributor.sendToServer(StorageActionPayload.fetch(new ItemStack(Items.IRON_INGOT), 12)))
                .pause(50)
                .close(10)
                // Van schuin boven: Porter rijdt de gang in en kijkt met zijn arm in de tonnen.
                .view(0, x + 3.5, YARD.getY() + 4.0, z + 5.5, 175, 28);
        waitForCar(s);
        s.pause(20)
                .stand(0, x, YARD.getY(), z + 2.5, 180, 20);
        open(s);
        s.pause(120)
                // Opbergen: stenen en diamanten in het tabblad Store, en de knop Store.
                .onServer(0, player -> {
                    StorageInterfaceBlockEntity station = station(player);
                    if (station != null) {
                        station.in().setItem(0, new ItemStack(Items.COBBLESTONE, 32));
                        station.in().setItem(1, new ItemStack(Items.DIAMOND, 3));
                    }
                })
                .step(0, () -> screen(screen -> screen.tourTab(StorageMenu.TAB_STORE)))
                .pause(80)
                .step(0, () -> PacketDistributor.sendToServer(StorageActionPayload.store()))
                .pause(60)
                .close(10)
                .view(0, x + 3.5, YARD.getY() + 4.0, z + 5.5, 175, 28);
        // Twee bestellingen (de stenen, dan de diamanten): twee keer wachten tot Porter klaar is.
        waitForCar(s);
        waitForCar(s);
        s.pause(20)
                .stand(0, x, YARD.getY(), z + 2.5, 180, 20);
        open(s);
        s.pause(120).close(10);
    }
}
