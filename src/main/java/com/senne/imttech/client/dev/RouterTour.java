package com.senne.imttech.client.dev;

import com.senne.imttech.ImtTech;
import com.senne.imttech.client.RelayEditScreen;
import com.senne.imttech.client.RouterScreen;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.lan.LanGrid;
import com.senne.imttech.lan.RouterBlock;
import com.senne.imttech.lan.RouterBlockEntity;
import com.senne.imttech.lan.RouterLinks;
import com.senne.imttech.lan.Routers;
import com.senne.imttech.logic.InputDeviceBlock;
import com.senne.imttech.logic.InputDeviceBlockEntity;
import com.senne.imttech.network.RelayActionPayload;
import com.senne.imttech.network.RouterActionPayload;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.redstone.RelayBlockEntity;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.CameraBlock;
import com.senne.imttech.security.SecurityCoreBlock;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.station.ChargerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * De Router (hoofdstuk 112) in de rondleiding met screenshots en in de showcase.
 *
 * <ul>
 *   <li>{@code gradlew runVisualCheck -Ponly=router}: een kern met één PoE-kabel naar een router, en daaromheen apparaten
 *   zonder kabel: een relais met een lamp, een camera, een lezer op een pilaar, en verderop een tweede router met nog een
 *   relais. De router met en zonder stroom, het overzicht, zijn scherm (met de muis op een knop), de vonkjes als je een
 *   regel aanklikt, een signaal dat door de lucht vliegt, het scherm van een relais met en zonder netwerk, en het item met
 *   zijn tooltip. Screenshots rt01 tot rt11.</li>
 *   <li>{@code gradlew runVisualCheck -Ponly=howtopics:router}: het plaatje van het echte scherm voor de animatie van How
 *   it works ({@code router_screen}); dat komt in de mod zelf te staan.</li>
 *   <li>De showcase-functie "Router" (zie ShowcaseFeatures): {@link #showcase}.</li>
 * </ul>
 */
final class RouterTour {
    private RouterTour() {}

    /** Een open plek, ver van de rest. Hier staat de kern; alles staat ten oosten en zuiden ervan. */
    static final BlockPos YARD = new BlockPos(-560, -60, 380);
    private static final BlockPos CORE = YARD;
    private static final BlockPos ROUTER = YARD.east(3);
    private static final BlockPos RELAY = YARD.offset(7, 0, -3);
    private static final BlockPos LAMP = RELAY.east();
    private static final BlockPos CAMERA = YARD.offset(6, 0, 3);
    /** De lezer hangt op een pilaar, twee blokken hoog. */
    private static final BlockPos READER = YARD.offset(9, 1, 0);
    private static final BlockPos SECOND = YARD.offset(4, 0, 9);
    private static final BlockPos FAR_RELAY = YARD.offset(6, 0, 11);
    /** Een nieuw relais zonder router of kabel in de buurt (te ver van allebei de routers weg voor de foto: los gelaten). */
    private static final BlockPos LOOSE_RELAY = YARD.offset(-3, 0, 4);

    /** Een volle warmtegenerator op deze plek. */
    private static void generator(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof ChargerBlockEntity charger) {
            charger.fill(200_000);
        }
    }

    /** Een relais zoals een speler het nu neerzet: van jou, met een naam, en het heeft een netwerk nodig. */
    private static void relay(ServerPlayer player, BlockPos pos, String name) {
        ServerLevel level = player.serverLevel();
        level.setBlockAndUpdate(pos, ModBlocks.RELAY.get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof RelayBlockEntity relay) {
            relay.setOwner(player);
            relay.setNetworked(true);
        }
        ImtRedstone.register(player, pos);
        int index = player.getData(ModAttachments.RELAYS).indexAt(level.dimension(), pos);
        if (index >= 0) {
            ImtRedstone.handleAction(player, RelayActionPayload.rename(index, name, ""));
        }
    }

    /**
     * Een router op deze plek, van jou, met deze naam. {@code power} is waar zijn generator komt (er tegenaan), of null: dan
     * komt zijn stroom van de PoE-kabel.
     */
    private static void router(ServerPlayer player, BlockPos pos, String name, @org.jetbrains.annotations.Nullable BlockPos power) {
        ServerLevel level = player.serverLevel();
        if (power != null) {
            generator(level, power);
        }
        level.setBlockAndUpdate(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(pos) instanceof RouterBlockEntity entity) {
            entity.setOwner(player);
            entity.setName(name);
            entity.updateOnline(level);
        }
    }

    /**
     * Zet het toneel klaar. De kern (van jou) met een generator ernaast en een PoE-kabel naar "Router 1"; daaromheen, zonder
     * kabels: het relais "Front door" met een lamp, een camera met een eigen generator, en de lezer "Lab door" op een
     * pilaar. Verderop "Router 2" (draadloos aan Router 1) met het relais "Garden light". En links een los relais
     * ("Shed light"): te ver van elke router, dus zonder netwerk.
     */
    private static void stage(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RouterLinks links = RouterLinks.get(level);
        for (BlockPos old : links.routers()) {
            if (old.closerThan(YARD, 64)) {
                links.removeRouter(old);
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(YARD.offset(-8, 0, -8), YARD.offset(16, 5, 16))) {
            for (CableKind kind : CableKind.values()) {
                Cables.remove(kind, level, pos);
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        // De kern, met stroom, en van jou.
        generator(level, CORE.below());
        level.setBlockAndUpdate(CORE, ModBlocks.SECURITY_CORE.get().defaultBlockState()
                .setValue(SecurityCoreBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(CORE) instanceof SecurityCoreBlockEntity core) {
            core.setOwner(player);
            core.ensureSystem(level);
        }
        for (int dx = 1; dx <= 2; dx++) {
            Cables.add(CableKind.POE, level, CORE.east(dx), player.getUUID());
        }
        // De apparaten, zonder kabels.
        relay(player, RELAY, "Front door");
        level.setBlockAndUpdate(LAMP, Blocks.REDSTONE_LAMP.defaultBlockState());
        generator(level, CAMERA.below());
        level.setBlockAndUpdate(CAMERA, ModBlocks.SECURITY_CAMERA.get().defaultBlockState()
                .setValue(CameraBlock.FACE, AttachFace.FLOOR).setValue(CameraBlock.FACING, Direction.WEST));
        level.setBlockAndUpdate(READER.below().east(), Blocks.STONE_BRICKS.defaultBlockState());
        level.setBlockAndUpdate(READER.east(), Blocks.STONE_BRICKS.defaultBlockState());
        generator(level, READER.below(2).east());
        level.setBlockAndUpdate(READER, ModBlocks.KEYCARD_READER.get().defaultBlockState()
                .setValue(InputDeviceBlock.FACING, Direction.WEST));
        if (level.getBlockEntity(READER) instanceof InputDeviceBlockEntity reader) {
            reader.setOwner(player);
            reader.setName("Lab door");
        }
        relay(player, FAR_RELAY, "Garden light");
        level.setBlockAndUpdate(FAR_RELAY.east(), Blocks.REDSTONE_LAMP.defaultBlockState());
        // De routers: eerst de eerste (aan de PoE-kabel), dan de tweede (die zoekt zelf de eerste).
        router(player, ROUTER, "Router 1", null);
        links.addRouter(ROUTER);
        Routers.autoLink(level, ROUTER, player);
        router(player, SECOND, "Router 2", SECOND.west());
        links.addRouter(SECOND);
        // De tweede router hangt aan de eerste, en pakt het relais dat bij hem in de buurt staat.
        Routers.link(level, SECOND, ROUTER);
        links.unlink(ROUTER, FAR_RELAY);
        Routers.link(level, SECOND, FAR_RELAY);
        // De camera weet pas na zijn eigen klok dat hij een netwerk heeft: nu meteen.
        if (level.getBlockEntity(CAMERA) instanceof com.senne.imttech.security.CameraBlockEntity camera) {
            camera.updateOnline(level, CAMERA, level.getBlockState(CAMERA));
        }
    }

    /** Het losse relais voor de foto van "geen netwerk": pas later neergezet, zodat geen router het meeneemt. */
    private static void looseRelay(ServerPlayer player) {
        float old = com.senne.imttech.config.ImtConfig.Network.ROUTER_RANGE;
        relay(player, LOOSE_RELAY, "Shed light");
        // Het staat binnen 32 blokken van de routers, maar voor de foto hangt het nergens aan.
        RouterLinks links = RouterLinks.get(player.serverLevel());
        BlockPos router = links.routerOf(LOOSE_RELAY);
        if (router != null) {
            links.unlink(router, LOOSE_RELAY);
        }
        com.senne.imttech.config.ImtConfig.Network.ROUTER_RANGE = old;
    }

    /**
     * Router 1 met of zonder stroom: zijn PoE-kabel doorgeknipt (dan heeft hij geen stroom en geen netwerk meer) of weer
     * heel. Hij kijkt zelf maar om de twee seconden of hij stroom heeft; hier meteen.
     */
    private static void power(ServerPlayer player, boolean on) {
        ServerLevel level = player.serverLevel();
        if (on) {
            Cables.add(CableKind.POE, level, CORE.east(1), player.getUUID());
        } else {
            Cables.remove(CableKind.POE, level, CORE.east(1));
        }
        com.senne.imttech.energy.PowerGrid.changed();
        if (level.getBlockEntity(ROUTER) instanceof RouterBlockEntity entity) {
            entity.updateOnline(level);
        }
    }

    static void tour(DevScript t) {
        double x = YARD.getX() + 0.5;
        double z = YARD.getZ() + 0.5;
        t.stand(0, x + 3, YARD.getY(), z + 5, 180, 10);
        t.waitUntil(600, () -> VisualCheck.raidLeaveTicking(YARD));
        t.command(0, "time set noon");
        t.step(0, () -> LanGrid.overrideRequired(true));
        t.onServer(10, RouterTour::stage);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        // De router van dichtbij (ingezoomd: het is een klein kastje): met stroom, en zonder (de lampjes uit).
        t.step(0, () -> zoom(true));
        t.view(20, ROUTER.getX() + 2.6, YARD.getY() + 0.4, ROUTER.getZ() + 3.2, 148, 18);
        t.shot(30, "rt01_router_on");
        t.onServer(0, player -> power(player, false));
        t.shot(15, "rt02_router_no_power");
        t.onServer(0, player -> power(player, true));
        t.step(5, () -> zoom(false));
        // Het overzicht: de kern met zijn ene kabel, de router, en de apparaten eromheen zonder kabels.
        t.view(0, x + 4.5, YARD.getY() + 5.0, z + 9.5, 180, 38);
        t.shot(25, "rt03_overview");
        // Een signaal van de lezer naar de kern: door de lucht naar de router, en dan over de PoE-kabel (stilgezet onderweg).
        t.view(0, x + 5.5, YARD.getY() + 2.2, z + 5.0, 180, 22);
        t.onServer(10, player -> com.senne.imttech.lan.CableTraffic.signal(player.serverLevel(), READER, true));
        t.step(2, () -> com.senne.imttech.client.CableTraffic.freeze(true));
        t.shot(5, "rt04_signal_through_the_air");
        t.step(0, () -> com.senne.imttech.client.CableTraffic.freeze(false));
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        // Zijn scherm (rechtsklik, je staat ernaast): wat eraan hangt en wat er in de buurt staat; de muis op een knop.
        t.stand(0, ROUTER.getX() + 0.5, YARD.getY(), ROUTER.getZ() + 2.5, 180, 30);
        t.onServer(10, player -> Routers.open(player, ROUTER));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof RouterScreen);
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.step(0, () -> Minecraft.getInstance().getToasts().clear());
        t.shot(15, "rt05_router_screen");
        t.step(0, () -> {
            if (Minecraft.getInstance().screen instanceof RouterScreen screen) {
                // De knop van de eerste verbonden regel (zie RouterScreen: de lijst begint 42 onder de bovenrand, regels van 16).
                HowToTour.mouseTo((screen.width - 300) / 2.0 + 300 - 16 - 27, Math.max(6, (screen.height - 226) / 2.0) + 42 + 16 + 8);
            }
        });
        t.shot(5, "rt06_router_screen_button");
        // Een regel aanklikken: vonkjes van dat apparaat naar de router (hier het relais).
        t.step(0, () -> PacketDistributor.sendToServer(new RouterActionPayload(ROUTER, RouterActionPayload.PING, RELAY)));
        t.close(1);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.view(0, x + 5.0, YARD.getY() + 3.0, z + 3.5, 190, 30);
        t.onServer(3, player -> Routers.trail(player.serverLevel(), RELAY, ROUTER));
        t.shot(6, "rt07_sparks_to_the_router");
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        // Het scherm van de tweede router: zijn verbinding met Router 1 telt als één van zijn plekken.
        t.stand(0, SECOND.getX() + 0.5, YARD.getY(), SECOND.getZ() + 2.5, 180, 30);
        t.onServer(10, player -> Routers.open(player, SECOND));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof RouterScreen);
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.step(0, () -> Minecraft.getInstance().getToasts().clear());
        t.shot(15, "rt08_second_router_screen");
        t.close(5);
        // Het scherm van een relais aan de router (Network: connected), en van een los relais (No network).
        t.stand(0, RELAY.getX() + 0.5, YARD.getY(), RELAY.getZ() + 2.5, 180, 30);
        t.onServer(10, player -> ImtRedstone.openEditor(player, RELAY));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof RelayEditScreen);
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.shot(15, "rt09_relay_on_the_network");
        t.close(5);
        t.onServer(5, RouterTour::looseRelay);
        t.stand(5, LOOSE_RELAY.getX() + 0.5, YARD.getY(), LOOSE_RELAY.getZ() + 2.5, 180, 30);
        t.onServer(10, player -> ImtRedstone.openEditor(player, LOOSE_RELAY));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof RelayEditScreen);
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.shot(15, "rt10_relay_without_network");
        t.close(5);
        // Het item in je inventaris met zijn tooltip (in survival: in creative opent het creative-menu).
        t.command(0, "gamemode survival @a");
        t.onServer(0, player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ROUTER.get()));
            player.getInventory().selected = 4;
        });
        t.open(10, () -> new InventoryScreen(Minecraft.getInstance().player));
        t.step(5, () -> {
            if (Minecraft.getInstance().screen instanceof InventoryScreen screen) {
                HowToTour.mouseTo(screen.getGuiLeft() + 16, screen.getGuiTop() + 150);
            }
        });
        t.shot(8, "rt11_item_tooltip");
        t.close(5);
        t.onServer(0, player -> player.getInventory().setItem(0, ItemStack.EMPTY));
        t.command(0, "gamemode creative @a");
        t.step(0, () -> LanGrid.overrideRequired(false));
    }

    /**
     * Het plaatje van het echte scherm van de router voor de animatie van How it works. Het komt in src/main/resources te
     * staan (zie HowToTour.capture).
     */
    static void pictures(DevScript t) {
        double x = YARD.getX() + 0.5;
        double z = YARD.getZ() + 0.5;
        t.stand(0, x + 3, YARD.getY(), z + 5, 180, 10);
        t.waitUntil(600, () -> VisualCheck.raidLeaveTicking(YARD));
        t.command(0, "time set noon");
        t.step(0, () -> LanGrid.overrideRequired(true));
        t.onServer(10, RouterTour::stage);
        // Geen HUD achter het scherm: die schijnt anders door het paneel heen.
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.stand(0, ROUTER.getX() + 0.5, YARD.getY(), ROUTER.getZ() + 2.5, 180, 30);
        t.onServer(20, player -> Routers.open(player, ROUTER));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof RouterScreen);
        t.step(5, () -> HowToTour.mouseTo(2, 2));
        t.step(0, () -> Minecraft.getInstance().getToasts().clear());
        t.step(15, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof RouterScreen screen) {
                // Het paneel van RouterScreen: 300 x 226, in het midden.
                HowToTour.capture("router_screen", (screen.width - 300) / 2, Math.max(6, (screen.height - 226) / 2), 300, 226);
            } else {
                ImtTech.LOGGER.warn("[visual check] het scherm van de router staat niet open (maar {})", mc.screen);
            }
        });
        t.close(5);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        t.step(0, () -> LanGrid.overrideRequired(false));
    }

    // ================================================================ De showcase

    /** Waar je staat in de showcase: ten zuiden van de router, met de kern links en de apparaten rechts. */
    static final Vec3 SHOW_FROM = Vec3.atBottomCenterOf(YARD.offset(4, 0, 7));

    /**
     * De showcase-functie "Router": het toneel (een kern met één kabel naar een router, en apparaten zonder kabels), het
     * scherm van de router, vonkjes van het relais naar de router, het relais schakelt (de lamp), de router zonder stroom
     * (het relais luistert niet meer) en weer met stroom, en de tweede router die aan de eerste hangt.
     */
    static void showcase(DevScript s) {
        double x = YARD.getX() + 0.5;
        double z = YARD.getZ() + 0.5;
        s.command(0, "time set noon")
                .step(0, () -> LanGrid.overrideRequired(true))
                .onServer(0, RouterTour::stage)
                .pause(100)
                // Zijn scherm: wat eraan hangt en wat er in de buurt staat.
                .stand(0, ROUTER.getX() + 0.5, YARD.getY(), ROUTER.getZ() + 2.5, 180, 30)
                .onServer(10, player -> Routers.open(player, ROUTER))
                .pause(160)
                .step(0, () -> PacketDistributor.sendToServer(new RouterActionPayload(ROUTER, RouterActionPayload.PING, RELAY)))
                .close(10)
                // Van schuin boven: vonkjes van het relais naar de router, en het relais schakelt de lamp.
                .view(0, x + 5.0, YARD.getY() + 3.5, z + 5.5, 185, 28)
                .onServer(10, player -> Routers.trail(player.serverLevel(), RELAY, ROUTER))
                .pause(40)
                .onServer(0, player -> ImtRedstone.switchAt(player.serverLevel(), RELAY, -1, ImtRedstone.Op.ON, 20))
                .pause(60)
                .onServer(0, player -> ImtRedstone.switchAt(player.serverLevel(), RELAY, -1, ImtRedstone.Op.OFF, 20))
                .pause(40)
                // De router zonder stroom: zijn lampjes uit, en het relais luistert niet meer.
                .onServer(0, player -> power(player, false))
                .pause(30)
                .onServer(0, player -> ImtRedstone.switchAt(player.serverLevel(), RELAY, -1, ImtRedstone.Op.ON, 20))
                .pause(60)
                .onServer(0, player -> power(player, true))
                .pause(20)
                .onServer(0, player -> ImtRedstone.switchAt(player.serverLevel(), RELAY, -1, ImtRedstone.Op.ON, 20))
                .pause(60)
                // De tweede router: draadloos aan de eerste, met zijn eigen relais.
                .stand(0, SECOND.getX() + 0.5, YARD.getY(), SECOND.getZ() + 2.5, 180, 30)
                .onServer(10, player -> Routers.open(player, SECOND))
                .pause(140)
                .close(10)
                .view(0, x + 4.5, YARD.getY() + 6.5, z + 13.5, 180, 35)
                .onServer(10, player -> ImtRedstone.switchAt(player.serverLevel(), FAR_RELAY, -1, ImtRedstone.Op.ON, 20))
                .pause(80)
                .step(0, () -> LanGrid.overrideRequired(false));
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
}
