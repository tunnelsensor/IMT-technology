package com.senne.imttech.registry;

import com.senne.imttech.ImtTech;
import com.senne.imttech.energy.PowerCableBlock;
import com.senne.imttech.redstone.RelayBlock;
import com.senne.imttech.shield.DomeGeneratorBlock;
import com.senne.imttech.holo.HoloTableBlock;
import com.senne.imttech.shield.LaserEmitterBlock;
import com.senne.imttech.shield.LaserWallBlock;
import com.senne.imttech.station.ChargingStationBlock;
import com.senne.imttech.station.SolarPanelBlock;
import com.senne.imttech.station.HeatGeneratorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Alle blokken van de mod.
 */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ImtTech.MODID);

    static {
        // C4 bestaat niet meer (26 oktober): C4 in een wereld van vroeger wordt een Remote Frag.
        BLOCKS.addAlias(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "timed_c4"),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "remote_frag"));
        BLOCKS.addAlias(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "remote_c4"),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "remote_frag"));
    }

    public static final DeferredBlock<ChargingStationBlock> CHARGING_STATION = BLOCKS.registerBlock("charging_station",
            ChargingStationBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    // Het blok geeft een beetje licht, zoals een scherm.
                    .lightLevel(state -> 7)
                    .noOcclusion());

    /**
     * Het I.M.T-relais: een plat kastje dat redstone geeft als I.M.T het aanzet.
     * Het lampje gloeit zachtjes als het aan staat.
     */
    public static final DeferredBlock<RelayBlock> RELAY = BLOCKS.registerBlock("relay",
            RelayBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 4.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RelayBlock.POWERED) ? 8 : 2)
                    .noOcclusion());

    /**
     * De relay emitter (13 oktober): het omgekeerde van een relais. Krijgt hij redstone, dan schakelt hij
     * jouw relais (een naam of een tag). Zijn lampje gloeit rood als er redstone in gaat.
     */
    public static final DeferredBlock<com.senne.imttech.redstone.RelayEmitterBlock> RELAY_EMITTER = BLOCKS.registerBlock(
            "relay_emitter", com.senne.imttech.redstone.RelayEmitterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 4.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.senne.imttech.redstone.RelayEmitterBlock.POWERED) ? 6 : 1)
                    .noOcclusion());

    /**
     * De stroomkabel: verbindt zonnepanelen en warmtegeneratoren met alles wat stroom gebruikt.
     * Dun en snel te breken, net als een stuk draad.
     */
    public static final DeferredBlock<PowerCableBlock> CABLE = BLOCKS.registerBlock("cable",
            PowerCableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.5f, 3.0f)
                    .sound(SoundType.COPPER)
                    .noOcclusion());

    /** Het zonnepaneel: laadt je bril op met daglicht. */
    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL = BLOCKS.registerBlock("solar_panel",
            SolarPanelBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(2.5f, 5.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion());

    // ---------------------------------------------------------------- De Power Battery (hoofdstuk 47)
    /** Battery Casing: de wand van een Power Battery. */
    public static final DeferredBlock<com.senne.imttech.battery.BatteryCasingBlock> BATTERY_CASING = BLOCKS.registerBlock("battery_casing",
            properties -> new com.senne.imttech.battery.BatteryCasingBlock(properties, false),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 8.0f).requiresCorrectToolForDrops().sound(SoundType.METAL));
    /** Battery Glass: een doorzichtig wandblok, zodat je de cellen ziet; hangt aan elkaar (hoofdstuk 54). */
    public static final DeferredBlock<com.senne.imttech.battery.BatteryGlassBlock> BATTERY_GLASS = BLOCKS.registerBlock("battery_glass",
            com.senne.imttech.battery.BatteryGlassBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(3.0f, 8.0f).requiresCorrectToolForDrops().sound(SoundType.GLASS)
                    .noOcclusion().isViewBlocking((state, level, pos) -> false).isSuffocating((state, level, pos) -> false));
    /** Battery Cell: binnenin de doos, elke cel is opslag. */
    public static final DeferredBlock<com.senne.imttech.battery.BatteryCellBlock> BATTERY_CELL = BLOCKS.registerBlock("battery_cell",
            com.senne.imttech.battery.BatteryCellBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(3.0f, 8.0f).requiresCorrectToolForDrops().sound(SoundType.METAL));
    /** Battery Port: in de wand, daar sluiten de kabels aan; het brein van de batterij. */
    public static final DeferredBlock<com.senne.imttech.battery.BatteryPortBlock> BATTERY_PORT = BLOCKS.registerBlock("battery_port",
            com.senne.imttech.battery.BatteryPortBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 8.0f).requiresCorrectToolForDrops().sound(SoundType.METAL));

    /** De warmtegenerator: maakt energie van brandstof en van de hitte om hem heen. */
    public static final DeferredBlock<HeatGeneratorBlock> HEAT_GENERATOR = BLOCKS.registerBlock("heat_generator",
            HeatGeneratorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    // Alleen licht als hij werkt (dan gloeit zijn rooster).
                    .lightLevel(state -> state.getValue(HeatGeneratorBlock.LIT) ? 7 : 0)
                    // Zijn model is geen vol blok (er zitten kieren langs de kast). Zonder dit lieten de
                    // blokken ernaast hun kant weg, en keek je door die kieren dwars door de wereld.
                    // (Licht houdt hij wel tegen: zie HeatGeneratorBlock.getLightBlock.)
                    .noOcclusion());

    // ---------------------------------------------------------------- Fase 11: rayshield

    /** Fase 15: de holotafel, die een hologram boven zich projecteert. */
    public static final DeferredBlock<HoloTableBlock> HOLO_TABLE = BLOCKS.registerBlock("holo_table",
            HoloTableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 7)
                    .noOcclusion());

    /** De laserzender: twee tegenover elkaar maken een energiewand. */
    public static final DeferredBlock<LaserEmitterBlock> LASER_EMITTER = BLOCKS.registerBlock("laser_emitter",
            LaserEmitterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(50.0f, 1200.0f)   // zo sterk als obsidiaan
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(LaserEmitterBlock.POWERED) ? 10 : 3));

    /**
     * De energiewand zelf. Je kunt hem niet breken en hij valt nooit als item: hij hoort bij
     * de zenders. Daarom: -1 hardheid (onbreekbaar) en geen buit.
     */
    public static final DeferredBlock<LaserWallBlock> LASER_WALL = BLOCKS.registerBlock("laser_wall",
            LaserWallBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(-1.0f, 3600000.0f)
                    .noLootTable()
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 10)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false));

    /**
     * De laserbrug (hoofdstuk 34): een zender in de rand van de vloer die een brug van licht uitschuift. Zo sterk als
     * een ijzerblok: hij hoort niet bij de beveiliging, dus hij hoeft niet zo sterk te zijn als de laserzender.
     */
    public static final DeferredBlock<com.senne.imttech.shield.LaserBridgeBlock> LASER_BRIDGE = BLOCKS.registerBlock(
            "laser_bridge", com.senne.imttech.shield.LaserBridgeBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.senne.imttech.shield.LaserBridgeBlock.POWERED) ? 10 : 3));

    /**
     * Eén blok van de laserbrug, de plaat van licht waar je over loopt. Net als de laserwand: onbreekbaar (-1) en geen
     * buit, want hij hoort bij de zender.
     */
    public static final DeferredBlock<com.senne.imttech.shield.LaserBridgePlateBlock> LASER_BRIDGE_PLATE = BLOCKS.registerBlock(
            "laser_bridge_plate", com.senne.imttech.shield.LaserBridgePlateBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(-1.0f, 3600000.0f)
                    .noLootTable()
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 10)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false));

    /** De stroomkast: schakelaars voor de kabels die erop uitkomen (zie PowerCabinetBlock). */
    public static final DeferredBlock<com.senne.imttech.energy.PowerCabinetBlock> POWER_CABINET = BLOCKS.registerBlock(
            "power_cabinet", com.senne.imttech.energy.PowerCabinetBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 1200.0f)   // zwaar staal: explosies krijgen hem niet stuk
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Hoofdstuk 38: de Network Switch, waar netwerk- en PoE-kabels samenkomen (zie lan/NetworkSwitchBlock). */
    public static final DeferredBlock<com.senne.imttech.lan.NetworkSwitchBlock> NETWORK_SWITCH = BLOCKS.registerBlock(
            "network_switch", com.senne.imttech.lan.NetworkSwitchBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0f, 9.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.senne.imttech.lan.NetworkSwitchBlock.ONLINE) ? 3 : 0));

    /** Hoofdstuk 112: de Router, die het netwerk draadloos doorgeeft aan de apparaten om hem heen (zie lan/RouterBlock). */
    public static final DeferredBlock<com.senne.imttech.lan.RouterBlock> ROUTER = BLOCKS.registerBlock(
            "router", com.senne.imttech.lan.RouterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(com.senne.imttech.lan.RouterBlock.ONLINE) ? 3 : 0));

    /** Hoofdstuk 40: de Computer, een pc-kast op het netwerk van een kern (zie computer/ComputerBlock). */
    public static final DeferredBlock<com.senne.imttech.computer.ComputerBlock> COMPUTER = BLOCKS.registerBlock(
            "computer", com.senne.imttech.computer.ComputerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.senne.imttech.computer.ComputerBlock.ON) ? 2 : 0));

    /** 19 oktober: de Camera Monitor; meer samen zijn één scherm met camerabeelden (zie security/wall/CameraWall). */
    public static final DeferredBlock<com.senne.imttech.security.wall.CameraMonitorBlock> CAMERA_MONITOR = BLOCKS.registerBlock(
            "camera_monitor", com.senne.imttech.security.wall.CameraMonitorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(com.senne.imttech.security.wall.CameraMonitorBlock.ON) ? 5 : 0));

    /** Hoofdstuk 40: de Monitor met toetsenbord, tegen een Computer (zie computer/MonitorBlock). Het scherm geeft wat licht. */
    public static final DeferredBlock<com.senne.imttech.computer.MonitorBlock> MONITOR = BLOCKS.registerBlock(
            "monitor", com.senne.imttech.computer.MonitorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5f, 3.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(com.senne.imttech.computer.MonitorBlock.ON) ? 6 : 0));

    /** De schildkoepel-generator. */
    public static final DeferredBlock<DomeGeneratorBlock> DOME_GENERATOR = BLOCKS.registerBlock("dome_generator",
            DomeGeneratorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(DomeGeneratorBlock.POWERED) ? 12 : 3)
                    .noOcclusion());

    /** Het automatische torentje voor je basis (te maken met een Raven-onderdeel). */
    public static final DeferredBlock<com.senne.imttech.turret.TurretBlock> ROCKET_TURRET = BLOCKS.registerBlock(
            "rocket_turret", properties -> new com.senne.imttech.turret.TurretBlock(com.senne.imttech.turret.TurretKind.ROCKET, properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** Het vijandige torentje uit de Biebia's Industries-bunker. */
    /** Het oude Raven-torentje van de bunker: sinds hoofdstuk 54 gewoon een laserturret (voor oude werelden). */
    public static final DeferredBlock<com.senne.imttech.turret.TurretBlock> RAVEN_TURRET = BLOCKS.registerBlock(
            "raven_turret", properties -> new com.senne.imttech.turret.TurretBlock(com.senne.imttech.turret.TurretKind.SECURITY, properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(5.0f, 9.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** Het beveiligingstorentje van de Biebia's Industries Tower (luistert naar de beveiligingskern). */
    public static final DeferredBlock<com.senne.imttech.turret.TurretBlock> SECURITY_TURRET = BLOCKS.registerBlock(
            "security_turret", properties -> new com.senne.imttech.turret.TurretBlock(
                    com.senne.imttech.turret.TurretKind.SECURITY, properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 9.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /**
     * Hoofdstuk 35: de Remote EMP, een halve bol met een antenne op een vloer, muur of plafond. Gaat af (een EMP-puls)
     * als iemand op zijn EMP Trigger drukt. Iedereen kan hem eraf trekken.
     */
    public static final DeferredBlock<com.senne.imttech.weapons.RemoteChargeBlock> REMOTE_EMP = BLOCKS.registerBlock(
            "remote_emp", properties -> new com.senne.imttech.weapons.RemoteChargeBlock(
                    com.senne.imttech.weapons.RemoteChargeBlock.Kind.EMP, properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(0.4f, 1.0f)
                    .sound(SoundType.METAL)
                    .noCollission()
                    .noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    /**
     * Hoofdstuk 35 (26 oktober, in plaats van C4): de Remote Frag, de halve bol van een Sticky Frag met een antenne. Gaat
     * af (een klap zoals een Sticky Frag) als iemand op zijn Frag Trigger drukt. Met de hand krijg je hem er niet af
     * (alleen met een schaar), en een explosie maakt hem niet kapot (dan gaat hij zelf af).
     */
    public static final DeferredBlock<com.senne.imttech.weapons.RemoteChargeBlock> REMOTE_FRAG = BLOCKS.registerBlock(
            "remote_frag", properties -> new com.senne.imttech.weapons.RemoteChargeBlock(
                    com.senne.imttech.weapons.RemoteChargeBlock.Kind.FRAG, properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(-1.0f, 3_600_000.0f)
                    .sound(SoundType.METAL)
                    .noCollission()
                    .noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));

    /** De Crusher (hoofdstuk 11): maakt van een gebarsten blok poeder, op netstroom. */
    public static final DeferredBlock<com.senne.imttech.repair.CrackMachineBlock> CRUSHER = BLOCKS.registerBlock(
            "crusher", properties -> new com.senne.imttech.repair.CrackMachineBlock(
                    com.senne.imttech.repair.CrackMachineBlock.Kind.CRUSHER, properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** De Compactor (hoofdstuk 11): perst poeder tot een nieuw, sterk blok, op netstroom. */
    public static final DeferredBlock<com.senne.imttech.repair.CrackMachineBlock> COMPACTOR = BLOCKS.registerBlock(
            "compactor", properties -> new com.senne.imttech.repair.CrackMachineBlock(
                    com.senne.imttech.repair.CrackMachineBlock.Kind.COMPACTOR, properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /**
     * De Polymerizer (hoofdstuk 85): maakt polymeer van kool en water, op netstroom. Net zo sterk als de Crusher en de
     * Compactor.
     */
    public static final DeferredBlock<com.senne.imttech.plastic.PolymerizerBlock> POLYMERIZER = BLOCKS.registerBlock(
            "polymerizer", com.senne.imttech.plastic.PolymerizerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /**
     * De Electric Furnace (hoofdstuk 87): smelt alles wat een oven smelt, op netstroom in plaats van brandstof. Net zo sterk
     * als de Crusher; terwijl hij werkt geeft hij licht, zoals een brandende oven.
     */
    public static final DeferredBlock<com.senne.imttech.furnace.ElectricFurnaceBlock> ELECTRIC_FURNACE = BLOCKS.registerBlock(
            "electric_furnace", com.senne.imttech.furnace.ElectricFurnaceBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.senne.imttech.furnace.ElectricFurnaceBlock.WORKING)
                            ? com.senne.imttech.furnace.ElectricFurnaceBlock.LIGHT : 0));

    /** De bewakingscamera: aan de muur, het plafond of de vloer; zijn beeld zie je op de Security OS-desktop. */
    public static final DeferredBlock<com.senne.imttech.security.CameraBlock> SECURITY_CAMERA = BLOCKS.registerBlock(
            "security_camera", com.senne.imttech.security.CameraBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(com.senne.imttech.security.CameraBlock.ONLINE) ? 2 : 0));

    /** Hoofdstuk 126: de Alarm Speaker, een sirene met je eigen geluid aan de muur, het plafond of de vloer (zie alarm/AlarmSpeakerBlock). */
    public static final DeferredBlock<com.senne.imttech.alarm.AlarmSpeakerBlock> ALARM_SPEAKER = BLOCKS.registerBlock(
            "alarm_speaker", com.senne.imttech.alarm.AlarmSpeakerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(com.senne.imttech.alarm.AlarmSpeakerBlock.SOUNDING) ? 7
                            : state.getValue(com.senne.imttech.alarm.AlarmSpeakerBlock.ONLINE) ? 1 : 0));

    /**
     * Het serverrack: racks die elkaar raken zijn samen de server van je beveiligingskern, waar je
     * redstone-diagrammen op draaien (7 oktober).
     */
    public static final DeferredBlock<com.senne.imttech.logic.ServerRackBlock> SERVER_RACK = BLOCKS.registerBlock(
            "server_rack", com.senne.imttech.logic.ServerRackBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(4.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.senne.imttech.logic.ServerRackBlock.ONLINE) ? 3 : 0));

    /** De keycardlezer: een kaart ertegen houden start je diagram (7 oktober). */
    public static final DeferredBlock<com.senne.imttech.logic.KeycardReaderBlock> KEYCARD_READER = BLOCKS.registerBlock(
            "keycard_reader", com.senne.imttech.logic.KeycardReaderBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(com.senne.imttech.logic.InputDeviceBlock.LIGHT)
                            == com.senne.imttech.logic.InputLight.OFF ? 0 : 4));

    /** Het codepaneel: een code intypen start je diagram (7 oktober). */
    public static final DeferredBlock<com.senne.imttech.logic.CodePanelBlock> CODE_PANEL = BLOCKS.registerBlock(
            "code_panel", com.senne.imttech.logic.CodePanelBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(com.senne.imttech.logic.InputDeviceBlock.LIGHT)
                            == com.senne.imttech.logic.InputLight.OFF ? 0 : 4));

    /** De Auth Console (hoofdstuk 49): één knop om toegang te vragen; het antwoord komt van de desktop. */
    public static final DeferredBlock<com.senne.imttech.logic.AuthConsoleBlock> AUTH_CONSOLE = BLOCKS.registerBlock(
            "auth_console", com.senne.imttech.logic.AuthConsoleBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(com.senne.imttech.logic.InputDeviceBlock.LIGHT)
                            == com.senne.imttech.logic.InputLight.OFF ? 0 : 4));

    /** De netwerkknop (hoofdstuk 38): erop drukken start je diagram, zonder redstone. */
    public static final DeferredBlock<com.senne.imttech.logic.NetworkButtonBlock> NETWORK_BUTTON = BLOCKS.registerBlock(
            "network_button", com.senne.imttech.logic.NetworkButtonBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .noCollission()
                    .noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    /** De netwerkhendel (hoofdstuk 38): omzetten start je diagram (lever.on zegt hoe hij staat). */
    public static final DeferredBlock<com.senne.imttech.logic.NetworkLeverBlock> NETWORK_LEVER = BLOCKS.registerBlock(
            "network_lever", com.senne.imttech.logic.NetworkLeverBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .noCollission()
                    .noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    /** De beveiligingskern van de Biebia's Industries Tower: heel sterk, zodat hacken de weg is. */
    public static final DeferredBlock<com.senne.imttech.security.SecurityCoreBlock> SECURITY_CORE = BLOCKS.registerBlock(
            "security_core", com.senne.imttech.security.SecurityCoreBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(50.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 4));

    /**
     * De hacklaptop, opengeklapt op een beveiligingskern (daar loopt de hack). Het item is
     * HackingLaptopItem; dit blok ontstaat als je daarmee op de kern klikt.
     */
    public static final DeferredBlock<com.senne.imttech.security.HackingLaptopBlock> HACKING_LAPTOP = BLOCKS.registerBlock(
            "hacking_laptop", com.senne.imttech.security.HackingLaptopBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 5)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    /** De harnas-werkbank: het harnas bouwen en er upgrades in zetten (Biebia's lab). */
    public static final DeferredBlock<com.senne.imttech.workbench.SuitWorkbenchBlock> SUIT_WORKBENCH = BLOCKS.registerBlock(
            "suit_workbench", com.senne.imttech.workbench.SuitWorkbenchBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Een drone-dock voor de hangar: hier staat een drone uit je arsenaal, en wordt hij gerepareerd. */
    public static final DeferredBlock<com.senne.imttech.arsenal.DroneDockBlock> DRONE_DOCK = BLOCKS.registerBlock(
            "drone_dock", com.senne.imttech.arsenal.DroneDockBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 5)
                    .noOcclusion());

    /** Het midden van een landingsplatform: hier land je met het harnas, en ben je thuis. */
    public static final DeferredBlock<com.senne.imttech.suit.LandingPadBlock> LANDING_PAD = BLOCKS.registerBlock(
            "landing_pad", com.senne.imttech.suit.LandingPadBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 10));

    /**
     * De Arc Lamp (9 oktober): op netstroom, lichtniveau tot 100. Het blok zelf geeft als hij brandt zijn
     * lichtniveau (hooguit 15, zie ArcLampBlock.LIGHT); de rest van zijn licht (tot 100 blokken ver) regelt
     * light/ArcLampLight.
     */
    public static final DeferredBlock<com.senne.imttech.light.ArcLampBlock> ARC_LAMP = BLOCKS.registerBlock(
            "arc_lamp", com.senne.imttech.light.ArcLampBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.senne.imttech.light.ArcLampBlock.LIT)
                            ? state.getValue(com.senne.imttech.light.ArcLampBlock.LIGHT) : 0));

    /** De lichtschakelaar (9 oktober): zet hem op een kabel; aan = de stroom gaat erdoor, uit = niet. */
    public static final DeferredBlock<com.senne.imttech.light.LightSwitchBlock> LIGHT_SWITCH = BLOCKS.registerBlock(
            "light_switch", com.senne.imttech.light.LightSwitchBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noCollission()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    /** Hoofdstuk 26: het Training Start-blok (klik = een training beginnen, met jou als trainee). */
    public static final DeferredBlock<com.senne.imttech.training.TrainingStartBlock> TRAINING_START = BLOCKS.registerBlock(
            "training_start", com.senne.imttech.training.TrainingStartBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Hoofdstuk 26: het Training End-blok (een redstone-puls en zijn programma bij elke treffer). */
    public static final DeferredBlock<com.senne.imttech.training.TrainingEndBlock> TRAINING_END = BLOCKS.registerBlock(
            "training_end", com.senne.imttech.training.TrainingEndBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Hoofdstuk 26: het Training Goal-blok (de trainee stapt erop: geslaagd). */
    public static final DeferredBlock<com.senne.imttech.training.TrainingGoalBlock> TRAINING_GOAL = BLOCKS.registerBlock(
            "training_goal", com.senne.imttech.training.TrainingGoalBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Hoofdstuk 21: de Robot Assembler, de werkbank waarop je een guard robot in elkaar zet. */
    public static final DeferredBlock<com.senne.imttech.robot.RobotAssemblerBlock> ROBOT_ASSEMBLER = BLOCKS.registerBlock(
            "robot_assembler", com.senne.imttech.robot.RobotAssemblerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Hoofdstuk 21: het Robot Dock, het laadstation van één guard robot (op kabelstroom). */
    public static final DeferredBlock<com.senne.imttech.robot.RobotDockBlock> ROBOT_DOCK = BLOCKS.registerBlock(
            "robot_dock", com.senne.imttech.robot.RobotDockBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.METAL));

    /** Hoofdstuk 52: de Gunsmith Table, de werkbank waarop je een pistool uit zijn onderdelen in elkaar sleept. */
    public static final DeferredBlock<com.senne.imttech.guns.GunsmithTableBlock> GUNSMITH_TABLE = BLOCKS.registerBlock(
            "gunsmith_table", com.senne.imttech.guns.GunsmithTableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Hoofdstuk 52: de Reloading Bench, de pers die van een huls, een kogelpunt en buskruit een 9mm-patroon maakt. */
    public static final DeferredBlock<com.senne.imttech.guns.ReloadingBenchBlock> RELOADING_BENCH = BLOCKS.registerBlock(
            "reloading_bench", com.senne.imttech.guns.ReloadingBenchBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f)
                    .sound(SoundType.WOOD));

    /** Hoofdstuk 55: de Laser Workbench, de werkbank waarop je een lasergeweer uit zijn onderdelen in elkaar sleept. */
    public static final DeferredBlock<com.senne.imttech.weapons.LaserWorkbenchBlock> LASER_WORKBENCH = BLOCKS.registerBlock(
            "laser_workbench", com.senne.imttech.weapons.LaserWorkbenchBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(4.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Hoofdstuk 67: het Pegboard, een stalen paneel met gaatjes aan de muur waar je je wapens en uitrusting aan hangt. */
    public static final DeferredBlock<com.senne.imttech.armory.PegboardBlock> PEGBOARD = BLOCKS.registerBlock(
            "pegboard", com.senne.imttech.armory.PegboardBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5f, 4.0f)
                    .noOcclusion()
                    .sound(SoundType.METAL));

    /** Hoofdstuk 67: de Ammo Box, een stalen munitiekist (één soort per kist, in en uit met klikken). */
    public static final DeferredBlock<com.senne.imttech.armory.AmmoBoxBlock> AMMO_BOX = BLOCKS.registerBlock(
            "ammo_box", com.senne.imttech.armory.AmmoBoxBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(2.0f, 6.0f)
                    .noOcclusion()
                    .sound(SoundType.METAL));
}
