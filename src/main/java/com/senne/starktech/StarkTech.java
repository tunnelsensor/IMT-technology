package com.senne.starktech;

import com.mojang.logging.LogUtils;
import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.comms.EdithComms;
import com.senne.starktech.config.StarkConfigLoader;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.edith.EdithThreats;
import com.senne.starktech.gametest.StarkTechGameTests;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.registry.ModArmorMaterials;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModCreativeTabs;
import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.registry.ModMenus;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.hack.EdithHack;
import com.senne.starktech.illusion.EdithIllusions;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.satellite.SatelliteStrikes;
import com.senne.starktech.satellite.SatelliteWorkshop;
import com.senne.starktech.shield.EdithShield;
import com.senne.starktech.shield.LaserAnimations;
import com.senne.starktech.raid.EdithRaids;
import com.senne.starktech.raid.ModGameRules;
import com.senne.starktech.shield.EdithDomes;
import com.senne.starktech.spy.EdithSpy;
import com.senne.starktech.suit.EdithSuit;
import com.senne.starktech.suit.SuitRockets;
import com.senne.starktech.transport.EdithTransport;
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
 * De client-kant (HUD, toetsen, schermen) wordt aangemeld in client/StarkTechClient.
 */
@Mod(StarkTech.MODID)
public class StarkTech {
    /** De unieke naam van de mod. Moet overeenkomen met mod_id in gradle.properties. */
    public static final String MODID = "starktech";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StarkTech(IEventBus modEventBus, ModContainer modContainer) {
        // Alle getallen van de mod (kosten, schade, wachttijden, stroom...) in één bestand: config/starktech.toml,
        // gemaakt uit config/StarkConfig.java. Het wordt ook naar de spelers gestuurd, zodat het menu dezelfde
        // getallen toont als de server rekent.
        modContainer.registerConfig(ModConfig.Type.SERVER, StarkConfigLoader.SPEC, StarkConfigLoader.FILE_NAME);
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event) ->
                StarkConfigLoader.onLoad(event));
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading event) ->
                StarkConfigLoader.onLoad(event));
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Unloading event) ->
                StarkConfigLoader.onLoad(event));

        // Registers: vertellen het spel welke dingen onze mod toevoegt.
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        // Moet vóór de items: de E.D.I.T.H.-bril is een stuk armor en zoekt dit materiaal op.
        ModArmorMaterials.MATERIALS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        com.senne.starktech.registry.ModStructures.TYPES.register(modEventBus);
        com.senne.starktech.registry.ModStructures.PIECES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        modEventBus.addListener(ModEntities::registerAttributes);
        // Een trechter kan raketten in een Stark-torentje doen.
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
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(StarkTechGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.starktech.gametest.LogicGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.starktech.gametest.DatacenterGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.starktech.gametest.ZoneGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.starktech.gametest.C4GameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.starktech.gametest.CrackGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.starktech.gametest.BypassGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.starktech.gametest.CoreConfigGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.starktech.gametest.LightGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.starktech.gametest.RelayGameTests.class));
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.senne.starktech.gametest.LaserGameTests.class));
        // Controle van de wereldgeneratie (alleen met "gradlew runStructureCheck").
        com.senne.starktech.gametest.StructureCheck.registerIfEnabled();

        // Gebeurtenissen tijdens het spelen.
        NeoForge.EVENT_BUS.addListener(EdithServer::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EdithServer::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(ArsenalServer::onLogin);
        NeoForge.EVENT_BUS.addListener(EdithServer::onServerStopped);
        NeoForge.EVENT_BUS.addListener(EdithServer::onLivingDamaged);
        // Onthouden wie jou raakte, zodat de dreigingsscan alleen echte aanvallers toont
        NeoForge.EVENT_BUS.addListener(EdithThreats::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(EdithComms::onServerStopped);
        // Fase 6: de spionagedrone (opdrachten, rapporten en het logboek)
        NeoForge.EVENT_BUS.addListener(EdithSpy::onServerTick);
        NeoForge.EVENT_BUS.addListener(EdithSpy::onServerStopped);
        NeoForge.EVENT_BUS.addListener(EdithSpy::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(EdithSpy::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(EdithSpy::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(EdithSpy::onContainerOpen);
        NeoForge.EVENT_BUS.addListener(EdithSpy::onAttack);
        NeoForge.EVENT_BUS.addListener(EdithSpy::onDeath);
        NeoForge.EVENT_BUS.addListener(EdithSpy::onChat);
        NeoForge.EVENT_BUS.addListener(EdithSpy::onChangedDimension);
        // Fase 7: vrachtdrones laten hun spullen vallen als de server uitgaat
        NeoForge.EVENT_BUS.addListener(EdithTransport::onServerStopping);
        // Sluipen onder een drone = landen (in plaats van meteen losgelaten worden)
        NeoForge.EVENT_BUS.addListener(EdithTransport::onDismount);
        // Wie door een drone wordt neergezet, valt niet: hij zakt (en houdt er niets aan over)
        NeoForge.EVENT_BUS.addListener(EdithTransport::onFallDamage);
        // Fase 9: redstone-relais (pulsen aftellen)
        NeoForge.EVENT_BUS.addListener(EdithRedstone::onServerTick);
        // Getekende kabels: oude kabelblokken omzetten, en knippen met een schaar.
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.energy.StarkCables::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.energy.StarkCables::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.energy.StarkCables::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.energy.CableSpoolItem::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.energy.CableSpoolItem::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.energy.CableSpoolItem::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) ->
                com.senne.starktech.energy.StarkCables.clear());
        NeoForge.EVENT_BUS.addListener(EdithRedstone::onServerStopped);
        // Fase 12: cloakingdrones en hun illusies
        NeoForge.EVENT_BUS.addListener(EdithIllusions::onServerTick);
        NeoForge.EVENT_BUS.addListener(EdithIllusions::onServerStopped);
        // Fase 11: het persoonlijke schild vangt schade op
        NeoForge.EVENT_BUS.addListener(EdithShield::onIncomingDamage);

        // Fase 15: raids
        ModGameRules.register();
        NeoForge.EVENT_BUS.addListener(EdithRaids::onServerTick);
        NeoForge.EVENT_BUS.addListener(EdithRaids::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(EdithRaids::onServerStopped);

        // Fase 15: het harnas
        NeoForge.EVENT_BUS.addListener(EdithSuit::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EdithSuit::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(EdithSuit::onFall);
        NeoForge.EVENT_BUS.addListener(EdithSuit::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(EdithSuit::onServerStopped);
        NeoForge.EVENT_BUS.addListener(SuitRockets::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(SuitRockets::onServerStopped);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.TowerHack::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.TowerHack::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.TowerHack::onServerStopped);
        // Hoofdstuk 12: lezers en panelen hacken met de Bypass Kit (wie wat hackt, weglopen, de klok).
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.bypass.Bypass::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.bypass.Bypass::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.bypass.Bypass::onServerStopped);
        // Hoofdstuk 12: zakkenrollen (sluipend rechtsklikken op een speler, en de zak bijhouden).
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.pickpocket.Pickpocket::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.pickpocket.Pickpocket::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.pickpocket.Pickpocket::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.pickpocket.Pickpocket::onServerStopped);
        // De Stark OS-desktop op de beveiligingskern
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.os.StarkOs::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.os.StarkOs::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.os.StarkOs::onServerStopped);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) ->
                com.senne.starktech.security.SecurityCores.clear());
        // Hoofdstuk 10: zones. Met HIGH, zodat een geweigerd blok niet eerst nog door de spionagedrone
        // of de raids geteld wordt (die krijgen een geannuleerd event niet meer te zien).
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, com.senne.starktech.security.zone.Zones::onLeftClick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, com.senne.starktech.security.zone.Zones::onBreak);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, com.senne.starktech.security.zone.Zones::onPlace);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, com.senne.starktech.security.zone.Zones::onUseItem);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.senne.starktech.security.zone.Zones::onToolModification);
        // Alleen C4 komt door een zone: geen explosies, zuigers, monsters of lava (vuur: zie FireBlockMixin).
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.zone.Zones::onExplosion);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.zone.Zones::onPiston);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.zone.Zones::onMobGriefing);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.zone.Zones::onFluidPlace);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.zone.Zones::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.zone.Zones::onServerStopped);
        // De rand van je zone: de server stuurt je zones naar jou en je vrienden.
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.zone.ZoneSync::onServerTick);
        // De barsten van een klap blijven zichtbaar zolang ze er zijn (tot je ze herstelt).
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.c4.Cracks::onServerTick);
        // Een blok met barsten weghakken geeft een gebarsten blok. Als laatste: pas als niemand het
        // breken tegenhield (een zone bijvoorbeeld).
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.senne.starktech.repair.CrackDrops::onBreak);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.repair.CrackDrops::onDrops);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.repair.CrackDrops::onServerTick);
        // Het alarm van C4: het verslag als je weer inlogt, en de hint "alleen met een schaar".
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.c4.C4Alarm::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.c4.C4Alarm::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.c4.C4Alarm::onServerStopped);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.c4.C4Block::onLeftClick);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.zone.ZoneSync::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.security.zone.ZoneSync::onServerStopped);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) ->
                com.senne.starktech.arsenal.DroneDocks.clear());
        // Fase 11: rayshields schuiven open en dicht in plaats van in één klap aan te gaan
        NeoForge.EVENT_BUS.addListener(LaserAnimations::onServerTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> LaserAnimations.clear());
        // Fase 10: privéberichten onderscheppen (hack player)
        NeoForge.EVENT_BUS.addListener(EdithHack::onCommand);
        NeoForge.EVENT_BUS.addListener(EdithHack::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(EdithHack::onServerStopped);
        NeoForge.EVENT_BUS.addListener(SatelliteStrikes::onServerTick);
        NeoForge.EVENT_BUS.addListener(SatelliteStrikes::onServerStopped);
        NeoForge.EVENT_BUS.addListener(EdithDomes::onServerStopped);
        // Satelliet-upgrades: onderdelen die met drones naar boven gaan
        NeoForge.EVENT_BUS.addListener(SatelliteWorkshop::onServerTick);
        NeoForge.EVENT_BUS.addListener(SatelliteWorkshop::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(SatelliteWorkshop::onServerStopped);
        // De Arc Lamp (lichtniveau 100): metingen afmaken, opnieuw meten na het laden van chunks, oud licht opruimen.
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.light.ArcLampLight::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.light.ArcLampLight::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.light.ArcLampLight::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(com.senne.starktech.light.ArcLampLight::onServerStopped);

        LOGGER.info("Stark Tech loaded. E.D.I.T.H. is standing by.");
    }
}
