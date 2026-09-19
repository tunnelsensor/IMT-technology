package com.senne.starktech;

import com.mojang.logging.LogUtils;
import com.senne.starktech.comms.EdithComms;
import com.senne.starktech.edith.EdithConfig;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.gametest.StarkTechGameTests;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModCreativeTabs;
import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.registry.ModMenus;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.satellite.SatelliteStrikes;
import com.senne.starktech.spy.EdithSpy;
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
        // Instellingen van de wereld/server (bv. hoeveel drones er maximaal mogen).
        // Deze worden ook naar de spelers gestuurd, zodat het menu dezelfde getallen toont.
        modContainer.registerConfig(ModConfig.Type.SERVER, EdithConfig.SPEC);

        // Registers: vertellen het spel welke dingen onze mod toevoegt.
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        modEventBus.addListener(ModEntities::registerAttributes);

        // Netwerkberichten tussen speler en server.
        modEventBus.addListener(ModNetwork::register);

        // Automatische tests (draaien alleen met "gradlew runGameTestServer").
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(StarkTechGameTests.class));

        // Gebeurtenissen tijdens het spelen.
        NeoForge.EVENT_BUS.addListener(EdithServer::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EdithServer::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(EdithServer::onServerStopped);
        NeoForge.EVENT_BUS.addListener(EdithServer::onLivingDamaged);
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
        // Fase 9: redstone-relais (pulsen aftellen)
        NeoForge.EVENT_BUS.addListener(EdithRedstone::onServerTick);
        NeoForge.EVENT_BUS.addListener(EdithRedstone::onServerStopped);
        NeoForge.EVENT_BUS.addListener(SatelliteStrikes::onServerTick);
        NeoForge.EVENT_BUS.addListener(SatelliteStrikes::onServerStopped);

        LOGGER.info("Stark Tech geladen. E.D.I.T.H. staat klaar.");
    }
}
