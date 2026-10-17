package com.senne.imttech;

import com.mojang.logging.LogUtils;
import com.senne.imttech.arsenal.ArsenalServer;
import com.senne.imttech.comms.ImtComms;
import com.senne.imttech.config.ImtConfigLoader;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.imt.ImtThreats;
import com.senne.imttech.gametest.ImtTechGameTests;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.registry.ModArmorMaterials;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModCreativeTabs;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModMenus;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.hack.ImtHack;
import com.senne.imttech.illusion.ImtIllusions;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.satellite.SatelliteStrikes;
import com.senne.imttech.satellite.SatelliteWorkshop;
import com.senne.imttech.shield.ImtShield;
import com.senne.imttech.shield.LaserAnimations;
import com.senne.imttech.raid.ImtRaids;
import com.senne.imttech.raid.ModGameRules;
import com.senne.imttech.shield.ImtDomes;
import com.senne.imttech.spy.ImtSpy;
import com.senne.imttech.suit.ImtSuit;
import com.senne.imttech.suit.SuitRockets;
import com.senne.imttech.transport.ImtTransport;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.slf4j.Logger;

/**
 * Het startpunt van de mod. NeoForge maakt deze klasse aan als het spel opstart.
 * Hier melden we alles aan: items, blokken, geluiden, netwerkberichten en events.
 *
 * De client-kant (HUD, toetsen, schermen) wordt aangemeld in client/ImtTechClient.
 */
@Mod(ImtTech.MODID)
public class ImtTech {
    /** De unieke naam van de mod. Moet overeenkomen met mod_id in gradle.properties. */
    public static final String MODID = "imttech";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImtTech(IEventBus modEventBus, ModContainer modContainer) {
        // De mod heette eerst Stark Tech: zet bestanden met die oude naam over (zie LegacyFiles).
        LegacyFiles.moveToNewName();

        // Alle getallen van de mod (kosten, schade, wachttijden, stroom...) in één bestand: config/imttech.toml,
        // gemaakt uit config/ImtConfig.java. Het wordt ook naar de spelers gestuurd, zodat het menu dezelfde
        // getallen toont als de server rekent.
        modContainer.registerConfig(ModConfig.Type.SERVER, ImtConfigLoader.SPEC, ImtConfigLoader.FILE_NAME);
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event) ->
                ImtConfigLoader.onLoad(event));
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading event) ->
                ImtConfigLoader.onLoad(event));
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Unloading event) ->
                ImtConfigLoader.onLoad(event));

        // Registers: vertellen het spel welke dingen onze mod toevoegt.
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        // Moet vóór de items: de I.M.T-bril is een stuk armor en zoekt dit materiaal op.
        ModArmorMaterials.MATERIALS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        com.senne.imttech.registry.ModStructures.TYPES.register(modEventBus);
        com.senne.imttech.registry.ModStructures.PIECES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        modEventBus.addListener(ModEntities::registerAttributes);
        // Een trechter kan raketten in een Biebia's Industries-torentje doen.
        modEventBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                        ModBlockEntities.TURRET.get(), (turret, side) -> turret.kind().usesAmmo() ? turret.ammo() : null));
        // Trechters en buizen bij de Crusher en de Compactor: van boven en opzij erin, van onder eruit (hoofdstuk 11).
        modEventBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                        ModBlockEntities.CRACK_MACHINE.get(), (machine, side) -> side == null
                                ? new net.neoforged.neoforge.items.wrapper.InvWrapper(machine)
                                : new net.neoforged.neoforge.items.wrapper.SidedInvWrapper(machine, side)));

        // Netwerkberichten tussen speler en server.
        modEventBus.addListener(ModNetwork::register);

        // Automatische tests (draaien alleen met "gradlew runGameTestServer").
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(ImtTechGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.LogicGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.DatacenterGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.ZoneGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.C4GameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.CrackGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.BypassGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.CoreConfigGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.LightGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.RelayGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.LaserGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.RobotGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.BotProgramGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.BotSenseGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.DroneGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.imttech.gametest.TrainingGameTests.class));
        // Controle van de wereldgeneratie (alleen met "gradlew runStructureCheck").
        com.senne.imttech.gametest.StructureCheck.registerIfEnabled();

        // Gebeurtenissen tijdens het spelen.
        NeoForge.EVENT_BUS.addListener(ImtServer::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ImtServer::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(ArsenalServer::onLogin);
        NeoForge.EVENT_BUS.addListener(ArsenalServer::onPlayerTick);
        // Hoofdstuk 26: trainingen (aftellen, de tijd, en of de trainee er nog is).
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.training.Training::onServerTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> {
            com.senne.imttech.training.Training.clear();
            com.senne.imttech.training.TrainingGoals.clear();
            com.senne.imttech.training.TrainingEnds.clear();
        });
        NeoForge.EVENT_BUS.addListener(ImtServer::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ImtServer::onLivingDamaged);
        // Onthouden wie jou raakte, zodat de dreigingsscan alleen echte aanvallers toont
        NeoForge.EVENT_BUS.addListener(ImtThreats::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ImtComms::onServerStopped);
        // Fase 6: de spionagedrone (opdrachten, rapporten en het logboek)
        NeoForge.EVENT_BUS.addListener(ImtSpy::onServerTick);
        NeoForge.EVENT_BUS.addListener(ImtSpy::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ImtSpy::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(ImtSpy::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(ImtSpy::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(ImtSpy::onContainerOpen);
        NeoForge.EVENT_BUS.addListener(ImtSpy::onAttack);
        NeoForge.EVENT_BUS.addListener(ImtSpy::onDeath);
        NeoForge.EVENT_BUS.addListener(ImtSpy::onChat);
        NeoForge.EVENT_BUS.addListener(ImtSpy::onChangedDimension);
        // Fase 7: vrachtdrones laten hun spullen vallen als de server uitgaat
        NeoForge.EVENT_BUS.addListener(ImtTransport::onServerStopping);
        // Sluipen onder een drone = landen (in plaats van meteen losgelaten worden)
        NeoForge.EVENT_BUS.addListener(ImtTransport::onDismount);
        // Wie door een drone wordt neergezet, valt niet: hij zakt (en houdt er niets aan over)
        NeoForge.EVENT_BUS.addListener(ImtTransport::onFallDamage);
        // Fase 9: redstone-relais (pulsen aftellen)
        NeoForge.EVENT_BUS.addListener(ImtRedstone::onServerTick);
        // Getekende kabels: oude kabelblokken omzetten, en knippen met een schaar.
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.energy.PowerCables::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.energy.PowerCables::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.energy.PowerCables::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.energy.CableSpoolItem::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.energy.CableSpoolItem::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.energy.CableSpoolItem::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) ->
                com.senne.imttech.energy.PowerCables.clear());
        NeoForge.EVENT_BUS.addListener(ImtRedstone::onServerStopped);
        // Fase 12: cloakingdrones en hun illusies
        NeoForge.EVENT_BUS.addListener(ImtIllusions::onServerTick);
        NeoForge.EVENT_BUS.addListener(ImtIllusions::onServerStopped);
        // Fase 11: het persoonlijke schild vangt schade op
        NeoForge.EVENT_BUS.addListener(ImtShield::onIncomingDamage);

        // Fase 15: raids
        ModGameRules.register();
        NeoForge.EVENT_BUS.addListener(ImtRaids::onServerTick);
        NeoForge.EVENT_BUS.addListener(ImtRaids::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(ImtRaids::onServerStopped);

        // Fase 15: het harnas
        NeoForge.EVENT_BUS.addListener(ImtSuit::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ImtSuit::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ImtSuit::onFall);
        NeoForge.EVENT_BUS.addListener(ImtSuit::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(ImtSuit::onServerStopped);
        NeoForge.EVENT_BUS.addListener(SuitRockets::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(SuitRockets::onServerStopped);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.TowerHack::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.TowerHack::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.TowerHack::onServerStopped);
        // Hoofdstuk 12: lezers en panelen hacken met de Bypass Kit (wie wat hackt, weglopen, de klok).
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.bypass.Bypass::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.bypass.Bypass::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.bypass.Bypass::onServerStopped);
        // Hoofdstuk 12: zakkenrollen (sluipend rechtsklikken op een speler, en de zak bijhouden).
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.pickpocket.Pickpocket::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.pickpocket.Pickpocket::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.pickpocket.Pickpocket::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.pickpocket.Pickpocket::onServerStopped);
        // De Security OS-desktop op de beveiligingskern
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.os.SecurityOs::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.os.SecurityOs::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.os.SecurityOs::onServerStopped);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) ->
                com.senne.imttech.security.SecurityCores.clear());
        // Hoofdstuk 10: zones. Met HIGH, zodat een geweigerd blok niet eerst nog door de spionagedrone
        // of de raids geteld wordt (die krijgen een geannuleerd event niet meer te zien).
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, com.senne.imttech.security.zone.Zones::onLeftClick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, com.senne.imttech.security.zone.Zones::onBreak);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, com.senne.imttech.security.zone.Zones::onPlace);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, com.senne.imttech.security.zone.Zones::onUseItem);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.senne.imttech.security.zone.Zones::onToolModification);
        // Alleen C4 komt door een zone: geen explosies, zuigers, monsters of lava (vuur: zie FireBlockMixin).
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.zone.Zones::onExplosion);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.zone.Zones::onPiston);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.zone.Zones::onMobGriefing);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.zone.Zones::onFluidPlace);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.zone.Zones::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.zone.Zones::onServerStopped);
        // De rand van je zone: de server stuurt je zones naar jou en je vrienden.
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.zone.ZoneSync::onServerTick);
        // De barsten van een klap blijven zichtbaar zolang ze er zijn (tot je ze herstelt).
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.c4.Cracks::onServerTick);
        // Een blok met barsten weghakken geeft een gebarsten blok. Als laatste: pas als niemand het
        // breken tegenhield (een zone bijvoorbeeld).
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.senne.imttech.repair.CrackDrops::onBreak);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.repair.CrackDrops::onDrops);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.repair.CrackDrops::onServerTick);
        // Het alarm van C4: het verslag als je weer inlogt, en de hint "alleen met een schaar".
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.c4.C4Alarm::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.c4.C4Alarm::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.c4.C4Alarm::onServerStopped);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.c4.C4Block::onLeftClick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.zone.ZoneSync::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.security.zone.ZoneSync::onServerStopped);
        // Fase 11: rayshields schuiven open en dicht in plaats van in één klap aan te gaan
        NeoForge.EVENT_BUS.addListener(LaserAnimations::onServerTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> LaserAnimations.clear());
        // Fase 10: privéberichten onderscheppen (hack player)
        NeoForge.EVENT_BUS.addListener(ImtHack::onCommand);
        NeoForge.EVENT_BUS.addListener(ImtHack::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(ImtHack::onServerStopped);
        NeoForge.EVENT_BUS.addListener(SatelliteStrikes::onServerTick);
        NeoForge.EVENT_BUS.addListener(SatelliteStrikes::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ImtDomes::onServerStopped);
        // Satelliet-upgrades: onderdelen die met drones naar boven gaan
        NeoForge.EVENT_BUS.addListener(SatelliteWorkshop::onServerTick);
        NeoForge.EVENT_BUS.addListener(SatelliteWorkshop::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(SatelliteWorkshop::onServerStopped);
        // De Arc Lamp (lichtniveau 100): metingen afmaken, opnieuw meten na het laden van chunks, oud licht opruimen.
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.light.ArcLampLight::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.light.ArcLampLight::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.light.ArcLampLight::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(com.senne.imttech.light.ArcLampLight::onServerStopped);

        LOGGER.info("I.M.T Technology loaded. I.M.T is standing by.");
    }
}
