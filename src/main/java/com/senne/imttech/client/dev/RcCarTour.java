package com.senne.imttech.client.dev;

import com.senne.imttech.ImtTech;
import com.senne.imttech.car.CarMove;
import com.senne.imttech.car.CarProgram;
import com.senne.imttech.car.DrillHead;
import com.senne.imttech.car.RcCarEntity;
import com.senne.imttech.car.RcCars;
import com.senne.imttech.car.RcControllerItem;
import com.senne.imttech.client.car.RcCarScreen;
import com.senne.imttech.client.os.RcCarCodeScreen;
import com.senne.imttech.network.RcCarActionPayload;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.EnergyCellItem;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * De RC Car (hoofdstuk 108) in de rondleiding met screenshots en in de showcase.
 *
 * <ul>
 *   <li>{@code gradlew runVisualCheck -Ponly=rccar}: een auto voor een blok steen, van voren, van opzij, van achter en in
 *   het donker (zijn lichtjes); dan boort hij een gang (de boor draait, de barstjes), zijn scherm, de code-editor (met
 *   Ctrl+Spatie en het paneel rechts), zijn kofferbak, en de items in je inventaris met hun tooltip (de auto, de
 *   controller, de ijzeren en de netherite boorkop); dan vier auto's met elk een andere boorkop, en het scherm en de
 *   lege editor van een nieuwe auto. Screenshots rc01 tot rc20.</li>
 *   <li>{@code gradlew runVisualCheck -Ponly=howtopics:car}: de drie plaatjes van echte schermen voor de animatie van
 *   How it works ({@code rc_car_screen}, en de editor met de twee voorbeelden: {@code rc_car_code} en
 *   {@code rc_car_code_room}); die komen in de mod zelf te staan.</li>
 *   <li>De showcase-functie "RC Car" (zie ShowcaseFeatures): {@link #showcase}.</li>
 * </ul>
 */
final class RcCarTour {
    private RcCarTour() {}

    /** Een open plek, ver van de rest. De auto "Digger" staat hier en kijkt naar het noorden, naar een blok steen. */
    static final BlockPos YARD = new BlockPos(-500, -60, 300);
    /** De hoek van de kamer die de tweede auto ("Miner") in de showcase uithakt: in een blok steen met een glazen dak. */
    private static final BlockPos ROOM = YARD.offset(9, 0, -4);
    /** Vanaf hier staan vier auto's naast elkaar (naar het oosten toe), elk met een andere boorkop. */
    private static final BlockPos HEADS = YARD.offset(4, 0, 4);

    /** De auto met deze naam bij het toneel (op de server), of null. */
    private static @Nullable RcCarEntity car(ServerPlayer player, String name) {
        for (RcCarEntity car : player.serverLevel().getEntitiesOfClass(RcCarEntity.class, new AABB(YARD).inflate(40))) {
            if (car.displayName().equals(name)) {
                return car;
            }
        }
        return null;
    }

    /** Het nummer van die auto in je eigen spel (voor de berichten die een scherm stuurt), of -1. */
    private static int carId(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (RcCarEntity car : mc.level.getEntitiesOfClass(RcCarEntity.class, new AABB(YARD).inflate(40))) {
                if (car.displayName().equals(name)) {
                    return car.getId();
                }
            }
        }
        return -1;
    }

    /**
     * Het programma dat de speler van de rondleiding op zijn auto's schreef: jouw twee voorbeelden (een nieuwe auto heeft
     * zelf geen programma).
     */
    private static final String WRITTEN = CarProgram.EXAMPLE_STRIP_MINE + "\n" + CarProgram.EXAMPLE_ROOM;

    /**
     * Een nieuwe auto op deze plek, met zijn neus naar het noorden, van deze speler, met een cel, deze boorkop en dit
     * programma.
     */
    private static @Nullable RcCarEntity spawn(ServerPlayer player, BlockPos pos, String name, float cell, DrillHead head,
                                               String source) {
        ServerLevel level = player.serverLevel();
        RcCarEntity car = ModEntities.RC_CAR.get().create(level);
        if (car == null) {
            return null;
        }
        float yaw = Direction.NORTH.toYRot();
        car.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        car.setYHeadRot(yaw);
        car.setYBodyRot(yaw);
        car.body().setFacing(Direction.NORTH);
        car.setOwner(player);
        car.setCustomName(Component.literal(name));
        car.swapCell(EnergyCellItem.withCharge(cell));
        car.setDrill(head);
        String problem = car.setSource(source);
        if (problem != null) {
            ImtTech.LOGGER.warn("[visual check] het programma van {} klopt niet: {}", name, problem);
        }
        level.addFreshEntity(car);
        RcCars.register(car);
        return car;
    }

    /**
     * Zet het toneel klaar: een vlakke plek, een blok steen van 3 breed, 3 hoog en 6 diep ten noorden van de auto, en de
     * auto zelf ("Digger", van jou, met een cel van 86%, de ijzeren boorkop van een nieuwe auto, wat cobblestone in zijn
     * kofferbak, en een programma: de twee voorbeelden plus een hoofdprogramma).
     */
    private static void stage(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (RcCarEntity old : level.getEntitiesOfClass(RcCarEntity.class, new AABB(YARD).inflate(48))) {
            old.discard();
        }
        for (BlockPos pos : BlockPos.betweenClosed(YARD.offset(-6, 0, -26), YARD.offset(16, 6, 8))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(YARD.offset(-1, 0, -7), YARD.offset(1, 2, -2))) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        RcCarEntity car = spawn(player, YARD, "Digger", 86.0f, DrillHead.IRON, WRITTEN + """

                # Runs when you press Start.
                when started:
                    say("Hello, I am " + car.name)
                    strip_mine()
                """);
        if (car != null) {
            car.trunk().addItem(new ItemStack(Items.COBBLESTONE, 23));
        }
    }

    /**
     * Alleen voor de rondleiding met screenshots: vier auto's op een rij met elk een andere boorkop, en "Rookie": een
     * nieuwe auto, zonder programma en met de ijzeren kop.
     */
    private static void extras(ServerPlayer player) {
        for (DrillHead head : DrillHead.values()) {
            // De camera kijkt naar het zuiden: het oosten is dan links. IJzer hoort links te staan, dus het meest oostelijk.
            spawn(player, HEADS.offset(DrillHead.values().length - 1 - head.ordinal(), 0, 0), head.label(), 100.0f, head,
                    CarProgram.EMPTY);
        }
        spawn(player, YARD.offset(-4, 0, 3), "Rookie", 100.0f, DrillHead.IRON, CarProgram.EMPTY);
    }

    /**
     * Voor de showcase: een blok steen van 7 x 7 en 3 hoog met een glazen dak, en in de hoek ervan (een uitgehakt blokje)
     * de auto "Miner" met de netherite boorkop en het programma dat je erop schreef. Zo zie je door het dak hoe hij de kamer
     * van 5 x 3 x 5 uithakt.
     */
    private static void roomStage(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : BlockPos.betweenClosed(ROOM.offset(-1, 0, -5), ROOM.offset(5, 2, 1))) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(ROOM.offset(-1, 3, -5), ROOM.offset(5, 3, 1))) {
            level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
        }
        level.setBlockAndUpdate(ROOM, Blocks.AIR.defaultBlockState());
        spawn(player, ROOM, "Miner", 100.0f, DrillHead.NETHERITE, WRITTEN);
    }

    static void tour(DevScript t) {
        double x = YARD.getX() + 0.5;
        double z = YARD.getZ() + 0.5;
        t.stand(0, x, YARD.getY(), z + 5, 180, 10);
        t.waitUntil(200, () -> VisualCheck.raidLeaveTicking(YARD));
        t.command(0, "time set noon");
        t.onServer(10, RcCarTour::stage);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        // De auto van schuin voor, van opzij, van achter (zijn laadbak) en in het donker (zijn lichtjes). Ingezoomd (een
        // smalle kijkhoek), want hij is klein en je ogen zitten 1,62 boven de grond.
        t.step(0, () -> zoom(true));
        t.view(20, x + 3.6, YARD.getY(), z - 2.0, 61, 18);
        t.shot(20, "rc01_car_front");
        t.view(0, x + 4.5, YARD.getY(), z, 90, 16);
        t.shot(15, "rc02_car_side");
        t.view(0, x - 2.2, YARD.getY() + 1.5, z + 3.0, -144, 37);
        t.shot(15, "rc03_car_back_top");
        t.command(0, "time set midnight");
        t.view(0, x + 3.6, YARD.getY(), z - 2.0, 61, 18);
        t.shot(40, "rc04_car_night");
        t.command(0, "time set noon");
        // Hij rijdt naar de steen en boort een gang (de functie strip_mine): de boor draait en het blok barst. Eerst krijgt
        // hij de netherite boorkop (de volle snelheid): dan staat hij op de foto's waar hij hoort te staan.
        t.view(20, x + 3.0, YARD.getY(), z + 1.5, 130, 19);
        t.onServer(0, player -> on(player, "Digger", car -> {
            car.setDrill(DrillHead.NETHERITE);
            car.brain().startManual(new CarMove.Drive(1));
        }));
        t.onServer(20, player -> on(player, "Digger", car -> car.brain().startFunction("strip_mine")));
        t.shot(5, "rc05_drilling");
        t.view(0, x, YARD.getY(), z + 2.5, 180, 14);
        t.shot(50, "rc06_in_the_tunnel");
        t.step(0, () -> zoom(false));
        t.view(0, x, YARD.getY(), z + 4.0, 180, 8);
        t.shot(40, "rc07_tunnel_from_behind");
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        // Zijn scherm, terwijl hij bezig is; dan gestopt (de auto is intussen een eind weggereden: Stop werkt nog).
        t.onServer(0, player -> on(player, "Digger", car -> RcCars.open(player, car, false)));
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.shot(15, "rc08_screen_running");
        t.step(0, () -> send("Digger", RcCarActionPayload.Op.STOP, ""));
        t.shot(15, "rc09_screen_ready");
        // De code-editor: de knop Program. Dan Ctrl+Spatie op een lege, ingesprongen regel, en de muis op het paneel rechts.
        t.step(0, () -> send("Digger", RcCarActionPayload.Op.CODE, ""));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof RcCarCodeScreen);
        t.shot(10, "rc10_code_editor");
        t.step(0, () -> {
            if (Minecraft.getInstance().screen instanceof RcCarCodeScreen screen) {
                screen.tourCompletion();
            }
        });
        t.shot(5, "rc11_code_ctrl_space");
        t.step(0, () -> {
            if (Minecraft.getInstance().screen instanceof RcCarCodeScreen screen) {
                screen.tourHelp(9);
            }
        });
        t.shot(5, "rc12_code_help");
        t.close(2);
        t.close(5);
        t.close(5);
        // De kofferbak (rechtsklik op de auto): daarvoor sta je erbij.
        t.onServer(5, player -> on(player, "Digger", car -> {
            player.teleportTo(car.getX(), car.getY(), car.getZ() + 2.0);
            RcCars.openTrunk(player, car);
        }));
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.shot(15, "rc13_trunk");
        t.close(5);
        // De items in je inventaris, met hun tooltip (in survival: in creative opent het creative-menu): de auto, de
        // controller, en de ijzeren en de netherite boorkop.
        t.command(0, "gamemode survival @a");
        t.onServer(0, player -> {
            ItemStack controller = new ItemStack(ModItems.RC_CONTROLLER.get());
            on(player, "Digger", car -> {
                RcControllerItem.setLink(controller, car);
                player.getInventory().setItem(0, car.toItem());
            });
            player.getInventory().setItem(1, controller);
            player.getInventory().setItem(2, new ItemStack(ModItems.IRON_DRILL_HEAD.get()));
            player.getInventory().setItem(3, new ItemStack(ModItems.NETHERITE_DRILL_HEAD.get()));
            player.getInventory().selected = 4;
        });
        t.open(10, () -> new InventoryScreen(Minecraft.getInstance().player));
        t.step(5, () -> inventory(screen -> HowToTour.mouseTo(screen.getGuiLeft() + 16, screen.getGuiTop() + 150)));
        t.shot(10, "rc14_item_car_tooltip");
        t.step(0, () -> inventory(screen -> HowToTour.mouseTo(screen.getGuiLeft() + 34, screen.getGuiTop() + 150)));
        t.shot(5, "rc15_item_controller_tooltip");
        t.step(0, () -> inventory(screen -> HowToTour.mouseTo(screen.getGuiLeft() + 52, screen.getGuiTop() + 150)));
        t.shot(5, "rc16_item_iron_head_tooltip");
        t.step(0, () -> inventory(screen -> HowToTour.mouseTo(screen.getGuiLeft() + 70, screen.getGuiTop() + 150)));
        t.shot(5, "rc17_item_netherite_head_tooltip");
        t.close(5);
        t.onServer(0, player -> {
            for (int slot = 0; slot < 4; slot++) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        });
        t.command(0, "gamemode creative @a");
        // De vier boorkoppen: vier auto's naast elkaar, van voren (ze kijken naar het noorden, naar de camera): ijzer links
        // op de foto, dan steel, titanium, en netherite rechts.
        t.onServer(0, RcCarTour::extras);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.step(0, () -> zoom(true));
        t.stand(0, HEADS.getX() + 2.0, YARD.getY(), HEADS.getZ() - 4.0, 0, 16);
        t.shot(30, "rc18_four_drill_heads");
        t.step(0, () -> zoom(false));
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        // Een nieuwe auto: geen programma. Zijn scherm zegt dat, en zijn editor is leeg (met grijze uitleg).
        t.onServer(0, player -> on(player, "Rookie", car -> {
            player.teleportTo(car.getX(), car.getY(), car.getZ() + 2.0);
            RcCars.open(player, car, false);
        }));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof RcCarScreen);
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.shot(15, "rc19_new_car_screen");
        t.step(0, () -> send("Rookie", RcCarActionPayload.Op.CODE, ""));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof RcCarCodeScreen);
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.shot(10, "rc20_empty_editor");
        t.close(5);
        t.close(5);
    }

    /**
     * De plaatjes van echte schermen voor de animatie van How it works: het scherm van de auto (alleen zijn paneel), en
     * twee keer de code-editor (het hele venster): met het voorbeeld strip mine, en met het voorbeeld kamer. Ze komen in
     * src/main/resources te staan (zie HowToTour.capture).
     */
    static void pictures(DevScript t) {
        double x = YARD.getX() + 0.5;
        double z = YARD.getZ() + 0.5;
        t.stand(0, x, YARD.getY(), z + 5, 180, 10);
        t.waitUntil(200, () -> VisualCheck.raidLeaveTicking(YARD));
        t.command(0, "time set noon");
        t.onServer(10, RcCarTour::stage);
        // Geen HUD achter de schermen: die schijnt anders door het paneel heen.
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.onServer(20, player -> on(player, "Digger", car -> {
            // Voor het plaatje: de twee functies uit de voorbeelden (zonder het hoofdprogramma van de rondleiding).
            car.setSource(WRITTEN);
            RcCars.open(player, car, false);
        }));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof RcCarScreen);
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.step(15, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof RcCarScreen screen) {
                // Het paneel met zijn rand (zie RcCarScreen: 330 x 234, vier pixels rand links, rechts en boven).
                HowToTour.capture("rc_car_screen", (screen.width - 330) / 2 - 4, Math.max(4, (screen.height - 234) / 2) - 4, 338, 238);
            } else {
                ImtTech.LOGGER.warn("[visual check] het scherm van de RC Car staat niet open (maar {})", mc.screen);
            }
        });
        t.close(5);
        codePicture(t, "rc_car_code", CarProgram.EXAMPLE_STRIP_MINE);
        codePicture(t, "rc_car_code_room", CarProgram.EXAMPLE_ROOM);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
    }

    /** Een plaatje van de code-editor van Digger met deze code erin (het hele venster). */
    private static void codePicture(DevScript t, String name, String source) {
        t.onServer(10, player -> on(player, "Digger", car -> {
            car.setSource(source);
            RcCars.open(player, car, false);
        }));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof RcCarScreen);
        t.step(5, () -> send("Digger", RcCarActionPayload.Op.CODE, ""));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof RcCarCodeScreen);
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.step(15, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof RcCarCodeScreen) {
                HowToTour.capture(name, 0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            } else {
                ImtTech.LOGGER.warn("[visual check] de editor van de RC Car staat niet open (maar {})", mc.screen);
            }
        });
        t.close(5);
        t.close(5);
        t.close(5);
    }

    // ================================================================ De showcase

    /** Waar je staat in de showcase: schuin achter Digger, met het blok steen voor hem en de kamer van Miner rechts. */
    static final Vec3 SHOW_FROM = Vec3.atBottomCenterOf(YARD.offset(3, 0, 4));

    /**
     * De showcase-functie "RC Car": Digger (voor een blok steen) en zijn scherm, zijn programma, de functie strip_mine
     * vanaf zijn scherm (eerst met de ijzeren boorkop, dan met de netherite: vier keer zo snel), zijn kofferbak; dan de RC
     * Controller, gekoppeld met Miner, die van ver de functie room start: door het glazen dak zie je de kamer van 5 x 3 x 5
     * ontstaan. Tot slot pak je Digger op.
     */
    static void showcase(DevScript s) {
        double x = YARD.getX() + 0.5;
        double z = YARD.getZ() + 0.5;
        s.command(0, "time set noon")
                .onServer(0, RcCarTour::stage)
                .onServer(0, RcCarTour::roomStage)
                .pause(80)
                // Het scherm van Digger (sneak + rechtsklik), en zijn programma (de knop Program).
                .onServer(0, player -> on(player, "Digger", car -> RcCars.open(player, car, false)))
                .pause(120)
                .step(0, () -> send("Digger", RcCarActionPayload.Op.CODE, ""))
                .pause(200)
                .close(10)
                // Met de pijltjes één blok vooruit, en dan de functie strip_mine: hij boort een gang door de steen.
                .step(10, () -> send("Digger", RcCarActionPayload.Op.STEP, "forward"))
                .pause(30)
                .step(0, () -> send("Digger", RcCarActionPayload.Op.RUN, "strip_mine"))
                .pause(40)
                .close(10)
                .view(0, x + 2.6, YARD.getY() + 1.4, z + 1.2, 135, 16)
                .pause(150)
                // Een betere boorkop erop terwijl hij boort (rechtsklik met de kop): netherite is vier keer zo snel als ijzer.
                .onServer(0, player -> on(player, "Digger", car -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.NETHERITE_DRILL_HEAD.get()));
                    RcCars.swapDrill(player, car, InteractionHand.MAIN_HAND);
                }))
                .pause(160)
                .onServer(0, player -> on(player, "Digger", car -> {
                    car.brain().stop();
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }))
                // Zijn kofferbak: de steen die hij brak.
                .view(0, x + 2.0, YARD.getY() + 1.0, z - 3.0, 100, 20)
                .onServer(20, player -> on(player, "Digger", car -> RcCars.openTrunk(player, car)))
                .pause(100)
                .close(10)
                // De RC Controller (gekoppeld met Miner): rechtsklik opent zijn scherm van ver; de functie room.
                .stand(0, x + 3, YARD.getY(), z + 4, -160, 5)
                .onServer(10, player -> on(player, "Miner", car -> {
                    ItemStack controller = new ItemStack(ModItems.RC_CONTROLLER.get());
                    RcControllerItem.setLink(controller, car);
                    player.setItemInHand(InteractionHand.MAIN_HAND, controller);
                }))
                .pause(60)
                .onServer(0, player -> RcCars.useController(player, player.getMainHandItem()))
                .pause(100)
                .step(0, () -> send("Miner", RcCarActionPayload.Op.RUN, "room"))
                .pause(50)
                .close(10)
                // Van boven, door het glazen dak: de kamer van 5 x 3 x 5.
                .view(0, ROOM.getX() + 2.5, YARD.getY() + 11.5, ROOM.getZ() + 3.5, 180, 62)
                .pause(1500)
                .view(0, ROOM.getX() + 2.5, YARD.getY() + 6.5, ROOM.getZ() + 4.5, 180, 50)
                .pause(120)
                // Tot slot: Digger oppakken (de knop Pick up): hij zit in je rugzak, met alles erin.
                .stand(0, x + 1.5, YARD.getY(), z - 2.5, 60, 25)
                .onServer(10, player -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    on(player, "Digger", car -> RcCars.open(player, car, false));
                })
                .pause(80)
                .step(0, () -> send("Digger", RcCarActionPayload.Op.PICK_UP, ""))
                .close(10)
                .pause(100);
    }

    // ================================================================ Hulpjes

    /** De kijkhoek van voor het inzoomen. */
    private static int normalFov = 70;

    /** Inzoomen (de smalste kijkhoek van Minecraft: 30 graden) voor een foto van iets kleins, of weer terug. */
    private static void zoom(boolean in) {
        var fov = Minecraft.getInstance().options.fov();
        if (in) {
            normalFov = fov.get();
            fov.set(30);
        } else {
            fov.set(normalFov);
        }
    }

    /** Doe dit met de auto met deze naam (op de server), als hij er is. */
    private static void on(ServerPlayer player, String name, Consumer<RcCarEntity> action) {
        RcCarEntity car = car(player, name);
        if (car != null) {
            action.accept(car);
        } else {
            ImtTech.LOGGER.warn("[visual check] de RC Car {} is er niet", name);
        }
    }

    /** Een knop van het scherm van de auto, zoals het scherm hem zelf stuurt. */
    private static void send(String name, RcCarActionPayload.Op op, String text) {
        int id = carId(name);
        if (id >= 0) {
            PacketDistributor.sendToServer(new RcCarActionPayload(id, op, text));
        }
    }

    private static void inventory(Consumer<InventoryScreen> action) {
        if (Minecraft.getInstance().screen instanceof InventoryScreen screen) {
            action.accept(screen);
        } else {
            ImtTech.LOGGER.warn("[visual check] je inventaris staat niet open (maar {})", Minecraft.getInstance().screen);
        }
    }
}
