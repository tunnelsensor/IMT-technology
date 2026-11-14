package com.senne.imttech.client.armor;

import com.senne.imttech.ImtTech;
import com.senne.imttech.armor.ArmorRegistry;
import com.senne.imttech.armor.PlateItem;
import com.senne.imttech.armor.PlateTier;
import com.senne.imttech.armor.VestItem;
import com.senne.imttech.armor.Vests;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * De body armor in jouw spel (hoofdstuk 109): het scherm van het vest, het vest op elk lijf, zijn kleur en camo, de
 * gebarsten plaatjes van de plates, en de plates naast je hartjes.
 */
public final class ArmorClient {
    private ArmorClient() {}

    /** Bij het opstarten (ImtTechClient). */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterMenuScreensEvent event) -> {
            event.register(ArmorRegistry.VEST_MENU.get(), VestScreen::new);
            // Hoofdstuk 111: de Armor Workbench.
            event.register(ArmorRegistry.ARMOR_WORKBENCH_MENU.get(), ArmorWorkbenchScreen::new);
        });
        modEventBus.addListener(ArmorClient::addLayers);
        modEventBus.addListener(VestRenderer::registerModels);
        modEventBus.addListener(ArmorClient::registerColours);
        modEventBus.addListener(ArmorClient::registerProperties);
        NeoForge.EVENT_BUS.addListener(PlateHud::onRenderGui);
    }

    /** Het vest op elke speler (brede en smalle huid). */
    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new VestLayer<>(renderer, (AbstractClientPlayer player) -> Vests.worn(player)));
            }
        }
    }

    /** De stof in zijn kleur (tintindex 0): wat je erop verfde, of antracietgrijs. De band, de gespen en het klittenband niet. */
    private static void registerColours(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? FastColor.ARGB32.opaque(VestItem.colour(stack)) : -1,
                ArmorRegistry.PLATE_CARRIER.get());
    }

    /** Camo (het model met het camoplaatje) en gebarsten plates (het plaatje met de barsten). */
    private static void registerProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ArmorRegistry.PLATE_CARRIER.get(), ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "camo"),
                    (stack, level, entity, seed) -> VestItem.camo(stack) ? 1 : 0);
            for (PlateTier tier : PlateTier.values()) {
                ItemProperties.register(ArmorRegistry.plate(tier), ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "cracked"),
                        (stack, level, entity, seed) -> PlateItem.broken(stack) ? 1 : 0);
            }
        });
    }
}
