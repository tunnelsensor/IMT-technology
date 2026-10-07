package com.senne.starktech.client;

import com.senne.starktech.StarkTech;
import com.senne.starktech.client.dev.Showcase;
import com.senne.starktech.client.dev.VisualCheck;
import com.senne.starktech.client.drone.DroneModel;
import com.senne.starktech.client.illusion.IllusionRenderer;
import com.senne.starktech.client.drone.DroneRenderer;
import com.senne.starktech.client.drone.RaidDroneRenderer;
import com.senne.starktech.client.holo.HoloTableRenderer;
import com.senne.starktech.client.shield.DomeRenderer;
import com.senne.starktech.client.suit.RepulsorHand;
import com.senne.starktech.client.suit.StarkSuitModel;
import com.senne.starktech.client.suit.SuitEffects;
import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.client.voice.VoiceControl;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModMenus;
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
@Mod(value = StarkTech.MODID, dist = Dist.CLIENT)
public class StarkTechClient {
    public StarkTechClient(IEventBus modEventBus, ModContainer container) {
        // Instellingen voor jouw eigen spel (bv. welk HUD-tabblad open staat).
        container.registerConfig(ModConfig.Type.CLIENT, EdithClientConfig.SPEC);
        // Sluit het spel af? Dan ook de achtergrondprogramma's van E.D.I.T.H. stoppen (stem en AI).
        Runtime.getRuntime().addShutdownHook(new Thread(VoiceControl::shutdown, "E.D.I.T.H. shutdown"));

        // Aanmelden bij het opstarten van het spel
        modEventBus.addListener(EdithKeys::register);
        modEventBus.addListener(StarkTechClient::registerGuiLayers);
        modEventBus.addListener(StarkTechClient::registerScreens);
        modEventBus.addListener(StarkTechClient::registerEntityRenderers);
        modEventBus.addListener(StarkTechClient::registerLayerDefinitions);
        // De gloeiende handpalm en reactor op spelers, en het 3D-model van het harnas.
        modEventBus.addListener(RepulsorHand::addLayers);
        modEventBus.addListener(StarkSuitModel::registerExtensions);

        // Gebeurtenissen tijdens het spelen
        NeoForge.EVENT_BUS.addListener(EdithClientEvents::onClientTick);
        // De bewakingscamera's: hun beeldjes tekenen vlak voor elk frame (alleen als de muur open staat).
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.client.os.camera.CameraFeeds::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.client.os.camera.CameraFeeds::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(CableRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(EdithClientEvents::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(EdithClientEvents::onComputeFov);
        NeoForge.EVENT_BUS.addListener(EdithClientEvents::onPlayerTurn);
        NeoForge.EVENT_BUS.addListener(EdithClientEvents::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(DroneCamera::onCameraAngles);
        // Fase 8: het beeld schudt bij een inslag van de satelliet
        NeoForge.EVENT_BUS.addListener(CameraShake::onCameraAngles);
        // Fase 12: de illusies van cloakingdrones tekenen (en dingen verbergen)
        NeoForge.EVENT_BUS.addListener(IllusionRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(IllusionRenderer::onRenderLiving);
        NeoForge.EVENT_BUS.addListener(IllusionRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.client.shield.RayshieldHum::onClientTick);
        NeoForge.EVENT_BUS.addListener(DroneCamera::onRenderHand);
        NeoForge.EVENT_BUS.addListener(DroneCamera::onInteraction);
        // De repulsor aan? Dan is de aanvalsknop de repulsor.
        NeoForge.EVENT_BUS.addListener(RepulsorControl::onInteraction);
        // Vliegen met het harnas: de natuurkunde, en het beeld dat overhelt in de bochten.
        NeoForge.EVENT_BUS.addListener(SuitFlight::onPlayerTickPre);
        NeoForge.EVENT_BUS.addListener(SuitFlight::onPlayerTickPost);
        NeoForge.EVENT_BUS.addListener(SuitFlight::onCameraAngles);
        // Het harnas in beeld: repulsorschoten, straalvlammen en de repulsorhand.
        NeoForge.EVENT_BUS.addListener(SuitEffects::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(RepulsorHand::onRenderHand);
        NeoForge.EVENT_BUS.addListener(RepulsorHand::onRenderPlayer);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.client.suit.FlightPose::onRenderPlayerPre);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.client.suit.FlightPose::onRenderPlayerPost);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.client.suit.FlightPose::onClientTick);
        // De harnas-werkbank: waar je naar kijkt op het hologram, en je klik daarop.
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.client.workbench.WorkbenchHologram::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.client.workbench.WorkbenchHologram::onInteraction);

        // Alleen tijdens het ontwikkelen (gradlew runVisualCheck): screenshots van alles maken.
        VisualCheck.registerIfEnabled();
        // Alleen tijdens het ontwikkelen (python tools/showcase.py): alle functies laten zien.
        Showcase.registerIfEnabled();
    }

    /** De HUD komt boven de camera-effecten maar onder de hotbar, chat en menu's. */
    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "edith_hud"), new EdithHud());
        // Het kaartje naast je vizier als je naar het werkbank-hologram kijkt.
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "workbench_hint"),
                com.senne.starktech.client.workbench.WorkbenchHologram::renderHint);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CHARGING_STATION.get(), ChargingStationScreen::new);
        event.register(ModMenus.CHARGER.get(), ChargerScreen::new);
        event.register(ModMenus.LANDING_PAD.get(), com.senne.starktech.client.LandingPadScreen::new);
        event.register(ModMenus.SERVER_RACK.get(), com.senne.starktech.client.ServerRackScreen::new);
    }

    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.STARK_DRONE.get(), DroneRenderer::new);
        event.registerEntityRenderer(ModEntities.RAID_DRONE.get(), RaidDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.HYDRA_SOLDIER.get(), com.senne.starktech.client.hydra.HydraSoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.MINI_ROCKET.get(), com.senne.starktech.client.suit.MiniRocketRenderer::new);
        event.registerEntityRenderer(ModEntities.ORBITAL_MISSILE.get(), com.senne.starktech.client.satellite.OrbitalMissileRenderer::new);
        event.registerEntityRenderer(ModEntities.SECURITY_DRONE.get(), com.senne.starktech.client.drone.SecurityDroneRenderer::new);
        // De holotafel tekent zijn hologram zelf, bovenop het gewone blokmodel.
        event.registerBlockEntityRenderer(ModBlockEntities.HOLO_TABLE.get(), HoloTableRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DOME_GENERATOR.get(), DomeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TURRET.get(), com.senne.starktech.client.turret.TurretRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DRONE_DOCK.get(), com.senne.starktech.client.drone.DroneDockRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SUIT_WORKBENCH.get(),
                com.senne.starktech.client.workbench.SuitHologramRenderer::new);
    }

    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DroneModel.LAYER, DroneModel::createBodyLayer);
        event.registerLayerDefinition(com.senne.starktech.client.hydra.HydraSoldierRenderer.LAYER,
                com.senne.starktech.client.hydra.HydraSoldierRenderer::createLayer);
        event.registerLayerDefinition(com.senne.starktech.client.suit.MiniRocketRenderer.LAYER,
                com.senne.starktech.client.suit.MiniRocketRenderer::createLayer);
        event.registerLayerDefinition(com.senne.starktech.client.satellite.OrbitalMissileRenderer.LAYER,
                com.senne.starktech.client.satellite.OrbitalMissileRenderer::createLayer);
        event.registerLayerDefinition(com.senne.starktech.client.turret.TurretRenderer.LAYER,
                com.senne.starktech.client.turret.TurretRenderer::createLayer);
        event.registerLayerDefinition(StarkSuitModel.OUTER, StarkSuitModel::createOuter);
        event.registerLayerDefinition(StarkSuitModel.INNER, StarkSuitModel::createInner);
    }


}
