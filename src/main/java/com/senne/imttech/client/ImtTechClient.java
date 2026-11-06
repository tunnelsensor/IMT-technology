package com.senne.imttech.client;

import com.senne.imttech.ImtTech;
import com.senne.imttech.client.dev.Showcase;
import com.senne.imttech.client.dev.VisualCheck;
import com.senne.imttech.client.drone.DroneModel;
import com.senne.imttech.client.illusion.IllusionRenderer;
import com.senne.imttech.client.drone.DroneRenderer;
import com.senne.imttech.client.drone.RaidDroneRenderer;
import com.senne.imttech.client.holo.HoloTableRenderer;
import com.senne.imttech.client.shield.DomeRenderer;
import com.senne.imttech.client.suit.LaserCannonHand;
import com.senne.imttech.client.suit.TechSuitModel;
import com.senne.imttech.client.suit.SuitEffects;
import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.client.voice.VoiceControl;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Het startpunt voor de client-kant (jouw eigen spel). Deze klasse wordt NIET geladen
 * op een dedicated server, dus hier mag alles wat met tekenen en toetsen te maken heeft.
 */
@Mod(value = ImtTech.MODID, dist = Dist.CLIENT)
public class ImtTechClient {
    public ImtTechClient(IEventBus modEventBus, ModContainer container) {
        // Instellingen voor jouw eigen spel (bv. welk HUD-tabblad open staat).
        container.registerConfig(ModConfig.Type.CLIENT, ImtClientConfig.SPEC);
        // Sluit het spel af? Dan ook de achtergrondprogramma's van I.M.T stoppen (stem en AI).
        Runtime.getRuntime().addShutdownHook(new Thread(VoiceControl::shutdown, "I.M.T shutdown"));

        // Aanmelden bij het opstarten van het spel
        modEventBus.addListener(ImtKeys::register);
        modEventBus.addListener(ImtTechClient::registerGuiLayers);
        modEventBus.addListener(ImtTechClient::registerScreens);
        modEventBus.addListener(ImtTechClient::registerEntityRenderers);
        modEventBus.addListener(ImtTechClient::registerLayerDefinitions);
        // De losse onderdelen van de wapens (hoofdstuk 35): granaten en geweren.
        modEventBus.addListener(com.senne.imttech.client.weapons.WeaponModels::register);
        // De rook van de smokebom, en de kleur van een gekleurde smokebom.
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) ->
                event.registerSpriteSet(com.senne.imttech.registry.ModParticles.SMOKE_CLOUD.get(),
                        com.senne.imttech.client.weapons.SmokeCloudParticle.Provider::new));
        modEventBus.addListener(com.senne.imttech.client.weapons.WeaponModels::registerItemColors);
        // Een lasergeweer houd je in derde persoon met twee handen vast.
        modEventBus.addListener(com.senne.imttech.client.weapons.WeaponModels::registerExtensions);
        // De vuurwapens (hoofdstuk 52): de losse onderdelen van de Glock 19, het plaatje per materiaal, twee handen.
        modEventBus.addListener(com.senne.imttech.client.guns.FirearmModels::register);
        modEventBus.addListener(com.senne.imttech.client.guns.FirearmModels::registerProperties);
        modEventBus.addListener(com.senne.imttech.client.guns.FirearmModels::registerExtensions);
        // De holster en de pouches op je heup (hoofdstuk 52, stap 2).
        modEventBus.addListener(com.senne.imttech.client.guns.GearLayer::addLayers);
        // De gloeiende handpalm en reactor op spelers, en het 3D-model van het harnas.
        modEventBus.addListener(LaserCannonHand::addLayers);
        modEventBus.addListener(TechSuitModel::registerExtensions);
        // Een gebarsten blok ziet eruit als zijn blok met barsten; poeder krijgt de kleur van zijn blok.
        modEventBus.addListener(com.senne.imttech.client.repair.CrackedBlockRenderer::registerExtensions);
        modEventBus.addListener(com.senne.imttech.client.repair.CrackedBlockRenderer::registerItemColors);

        // Gebeurtenissen tijdens het spelen
        NeoForge.EVENT_BUS.addListener(ImtClientEvents::onClientTick);
        // De bewakingscamera's: hun beeldjes tekenen vlak voor elk frame (alleen als de muur open staat).
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.os.camera.CameraFeeds::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.os.camera.CameraFeeds::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(CableRenderer::onRenderLevel);
        // Hoofdstuk 10: de rand van je zones (en die van wie jou vertrouwt).
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.zone.ZoneBorderRenderer::onRenderLevel);
        // Hoofdstuk 44: het randje om verstevigde blokken (met de bril op en de Block Reinforcer in je hand).
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.repair.ReinforcedRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.cuffs.CuffRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.zone.ZoneMarkerControl::onClientTick);
        // Een trigger in je hand: bordjes boven je Remote EMP's of Remote Frags.
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.weapons.TriggerMarkers::onRenderLevel);
        // Een Remote Frag op je zone: rode markeringen "FRAG!" (zie BlastAlarm).
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.zone.ChargeAlerts::onRenderLevel);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
                com.senne.imttech.client.zone.ChargeAlerts.clear());
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.zone.ZoneMarkerControl::onInteraction);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
                com.senne.imttech.client.zone.ZoneClientState.clear());
        NeoForge.EVENT_BUS.addListener(ImtClientEvents::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(ImtClientEvents::onComputeFov);
        NeoForge.EVENT_BUS.addListener(ImtClientEvents::onPlayerTurn);
        NeoForge.EVENT_BUS.addListener(ImtClientEvents::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(DroneCamera::onCameraAngles);
        // Fase 8: het beeld schudt bij een inslag van de satelliet
        NeoForge.EVENT_BUS.addListener(CameraShake::onCameraAngles);
        // Fase 12: de illusies van cloakingdrones tekenen (en dingen verbergen)
        NeoForge.EVENT_BUS.addListener(IllusionRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(IllusionRenderer::onRenderLiving);
        NeoForge.EVENT_BUS.addListener(IllusionRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.shield.RayshieldHum::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.robot.RobotChargeHum::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.robot.DroneRotorSound::onClientTick);
        NeoForge.EVENT_BUS.addListener(DroneCamera::onRenderHand);
        NeoForge.EVENT_BUS.addListener(DroneCamera::onInteraction);
        // De laser cannon aan? Dan is de aanvalsknop de laser cannon.
        NeoForge.EVENT_BUS.addListener(LaserCannonControl::onInteraction);
        // Vliegen met het harnas: de natuurkunde, en het beeld dat overhelt in de bochten.
        NeoForge.EVENT_BUS.addListener(SuitFlight::onPlayerTickPre);
        NeoForge.EVENT_BUS.addListener(SuitFlight::onPlayerTickPost);
        NeoForge.EVENT_BUS.addListener(SuitFlight::onCameraAngles);
        // Het harnas in beeld: laserkanonschoten, straalvlammen en de laserkanonhand.
        NeoForge.EVENT_BUS.addListener(SuitEffects::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(LaserCannonHand::onRenderHand);
        NeoForge.EVENT_BUS.addListener(LaserCannonHand::onRenderPlayer);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.suit.FlightPose::onRenderPlayerPre);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.suit.FlightPose::onRenderPlayerPost);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.suit.FlightPose::onClientTick);
        // De harnas-werkbank: waar je naar kijkt op het hologram, en je klik daarop.
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.workbench.WorkbenchHologram::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.workbench.WorkbenchHologram::onInteraction);
        // Wapens (hoofdstuk 35): de bol van licht van een EMP-granaat, en gedempt geluid na een flashbang.
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.weapons.EmpEffects::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.weapons.FlashOverlay::onPlaySound);
        // De lasergeweren: bediening, inzoomen, langzamer lopen, het geweer in je beeld, de bouten en de HUD.
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.weapons.GunControl::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.weapons.GunControl::onInteraction);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.weapons.GunControl::onMovementInput);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                com.senne.imttech.client.weapons.GunControl::onComputeFov);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.weapons.GunControl::onPlayerTurn);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.weapons.GunRenderer::onRenderHand);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.weapons.GunEffects::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.weapons.GunHud::onRenderLayer);
        // De vuurwapens (hoofdstuk 52): bediening, het pistool in je beeld, de HUD.
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.guns.FirearmControl::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.guns.FirearmControl::onInteraction);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.guns.FirearmControl::onMovementInput);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                com.senne.imttech.client.guns.FirearmControl::onComputeFov);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.guns.FirearmControl::onPlayerTurn);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.guns.FirearmRenderer::onRenderHand);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.guns.FirearmHud::onRenderLayer);
        // De holster en de pouches (hoofdstuk 52, stap 2): het wiel op linker Alt en de eigen vakjes in je inventaris.
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.guns.GearControl::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.guns.GearSlotsOverlay::onRender);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.client.guns.GearSlotsOverlay::onClick);

        // Alleen tijdens het ontwikkelen (gradlew runVisualCheck): screenshots van alles maken.
        VisualCheck.registerIfEnabled();
        // Alleen tijdens het ontwikkelen (python tools/showcase.py): alle functies laten zien.
        Showcase.registerIfEnabled();
    }

    /** De HUD komt boven de camera-effecten maar onder de hotbar, chat en menu's. */
    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "imt_hud"), new ImtHud());
        // Het kaartje naast je vizier als je naar het werkbank-hologram kijkt.
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "workbench_hint"),
                com.senne.imttech.client.workbench.WorkbenchHologram::renderHint);
        // Met een lasergeweer (hoofdstuk 35): je munitie rechtsonder, je kruisje en de kijker van de sniper.
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "laser_gun"),
                new com.senne.imttech.client.weapons.GunHud());
        // Met een pistool of magazijn (hoofdstuk 52): je patronen rechtsonder, je kruisje, de handeling.
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "firearm"),
                new com.senne.imttech.client.guns.FirearmHud());
        // Na een flashbang (hoofdstuk 35): een wit scherm over alles heen, ook over je HUD.
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "flashbang"),
                new com.senne.imttech.client.weapons.FlashOverlay());
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CHARGING_STATION.get(), ChargingStationScreen::new);
        event.register(ModMenus.CHARGER.get(), ChargerScreen::new);
        event.register(ModMenus.LANDING_PAD.get(), com.senne.imttech.client.LandingPadScreen::new);
        event.register(ModMenus.SERVER_RACK.get(), com.senne.imttech.client.ServerRackScreen::new);
        event.register(ModMenus.CRACK_MACHINE.get(), com.senne.imttech.client.repair.CrackMachineScreen::new);
        event.register(ModMenus.ROBOT_ASSEMBLER.get(), com.senne.imttech.client.robot.RobotAssemblerScreen::new);
        event.register(ModMenus.GUNSMITH_TABLE.get(), com.senne.imttech.client.guns.GunsmithScreen::new);
        event.register(ModMenus.RELOADING_BENCH.get(), com.senne.imttech.client.guns.ReloadingBenchScreen::new);
    }

    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DRONE.get(), DroneRenderer::new);
        event.registerEntityRenderer(ModEntities.RAID_DRONE.get(), RaidDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.RAVEN_SOLDIER.get(), com.senne.imttech.client.raven.RavenSoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.MINI_ROCKET.get(), com.senne.imttech.client.suit.MiniRocketRenderer::new);
        event.registerEntityRenderer(ModEntities.ORBITAL_MISSILE.get(), com.senne.imttech.client.satellite.OrbitalMissileRenderer::new);
        event.registerEntityRenderer(ModEntities.SECURITY_DRONE.get(), com.senne.imttech.client.drone.SecurityDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.GUARD_ROBOT.get(), com.senne.imttech.client.robot.GuardRobotRenderer::new);
        event.registerEntityRenderer(ModEntities.GUARD_DRONE.get(), com.senne.imttech.client.robot.GuardDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.ROLLING_EMP_GRENADE.get(), com.senne.imttech.client.weapons.GrenadeRenderer::new);
        event.registerEntityRenderer(ModEntities.STICKY_EMP_GRENADE.get(), com.senne.imttech.client.weapons.GrenadeRenderer::new);
        event.registerEntityRenderer(ModEntities.FRAG_GRENADE.get(), com.senne.imttech.client.weapons.GrenadeRenderer::new);
        event.registerEntityRenderer(ModEntities.STICKY_FRAG_GRENADE.get(), com.senne.imttech.client.weapons.GrenadeRenderer::new);
        event.registerEntityRenderer(ModEntities.SMOKE_GRENADE.get(), com.senne.imttech.client.weapons.GrenadeRenderer::new);
        event.registerEntityRenderer(ModEntities.FLASHBANG.get(), com.senne.imttech.client.weapons.GrenadeRenderer::new);
        // De vuurwapens (hoofdstuk 52): de kogel als streepje, de huls als messing buisje.
        event.registerEntityRenderer(ModEntities.BULLET.get(), com.senne.imttech.client.guns.BulletRenderer::new);
        event.registerEntityRenderer(ModEntities.CASING.get(), com.senne.imttech.client.guns.CasingRenderer::new);
        // De holotafel tekent zijn hologram zelf, bovenop het gewone blokmodel.
        event.registerBlockEntityRenderer(ModBlockEntities.HOLO_TABLE.get(), HoloTableRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DOME_GENERATOR.get(), DomeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TURRET.get(), com.senne.imttech.client.turret.TurretRenderer::new);
        // De schermmuur (19 oktober) tekent de camerabeelden op zijn monitoren.
        event.registerBlockEntityRenderer(ModBlockEntities.CAMERA_MONITOR.get(), com.senne.imttech.client.os.camera.CameraWallRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SUIT_WORKBENCH.get(),
                com.senne.imttech.client.workbench.SuitHologramRenderer::new);
    }

    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DroneModel.LAYER, DroneModel::createBodyLayer);
        event.registerLayerDefinition(com.senne.imttech.client.robot.GuardDroneModel.LAYER,
                com.senne.imttech.client.robot.GuardDroneModel::createBodyLayer);
        event.registerLayerDefinition(com.senne.imttech.client.raven.RavenSoldierRenderer.LAYER,
                com.senne.imttech.client.raven.RavenSoldierRenderer::createLayer);
        event.registerLayerDefinition(com.senne.imttech.client.suit.MiniRocketRenderer.LAYER,
                com.senne.imttech.client.suit.MiniRocketRenderer::createLayer);
        event.registerLayerDefinition(com.senne.imttech.client.satellite.OrbitalMissileRenderer.LAYER,
                com.senne.imttech.client.satellite.OrbitalMissileRenderer::createLayer);
        event.registerLayerDefinition(com.senne.imttech.client.turret.TurretRenderer.LAYER,
                com.senne.imttech.client.turret.TurretRenderer::createLayer);
        event.registerLayerDefinition(com.senne.imttech.client.robot.RobotModel.LAYER,
                com.senne.imttech.client.robot.RobotModel::createLayer);
        event.registerLayerDefinition(TechSuitModel.OUTER, TechSuitModel::createOuter);
        event.registerLayerDefinition(TechSuitModel.INNER, TechSuitModel::createInner);
    }


}
