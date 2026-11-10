package com.senne.imttech.client.backpack;

import com.mojang.blaze3d.platform.InputConstants;
import com.senne.imttech.ImtTech;
import com.senne.imttech.backpack.BackpackActionPayload;
import com.senne.imttech.backpack.BackpackItem;
import com.senne.imttech.backpack.BackpackRegistry;
import com.senne.imttech.client.ImtKeys;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.Gear;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * De rugzakken in jouw spel (hoofdstuk 72): het scherm, het tekenen (op je rug, neergezet), de kleur, de rugzaktoets en
 * het balkje van de rits.
 *
 * <b>De rugzaktoets</b> is standaard <b>Shift + B</b> (aan te passen in Opties > Besturing). Je koos "B", maar B is al de
 * Ore Scan van de bril; met Shift erbij botsen ze niet (NeoForge kijkt eerst naar toetsen met Shift, en gaat dan niet ook
 * nog de Ore Scan af). Sluipen tijdens het ritsen mag: je moet alleen stilstaan.
 */
public final class BackpackClient {
    private BackpackClient() {}

    public static final KeyMapping OPEN_BACKPACK = new KeyMapping("key.imttech.open_backpack", KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT, InputConstants.Type.KEYSYM, InputConstants.KEY_B, ImtKeys.CATEGORY);

    /** De rits: zoveel ticks in totaal (0 = geen rits bezig), en wanneer hij begon (speltijd). */
    private static int zipTotal;
    private static long zipStart;

    /** Bij het opstarten (ImtTechClient). */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(OPEN_BACKPACK));
        modEventBus.addListener((RegisterMenuScreensEvent event) -> {
            event.register(BackpackRegistry.BACKPACK_MENU.get(), BackpackScreen::new);
            event.register(BackpackRegistry.MOLLE_MENU.get(), MolleScreen::new);
        });
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(BackpackRegistry.BACKPACK_BLOCK_ENTITY.get(), BackpackRenderer::new));
        modEventBus.addListener(BackpackLayer::addLayers);
        modEventBus.addListener(BackpackLayer::registerModels);
        modEventBus.addListener(BackpackClient::registerColours);
        modEventBus.addListener(BackpackClient::registerProperties);
        NeoForge.EVENT_BUS.addListener(BackpackClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(BackpackClient::onRenderGui);
    }

    /** De stof in zijn kleur (tintindex 0): wat je erop verfde, of olijfgroen. De band en de gespen niet. */
    private static void registerColours(RegisterColorHandlersEvent.Item event) {
        for (BackpackItem.Size size : BackpackItem.Size.values()) {
            event.register((stack, tintIndex) -> tintIndex == 0 ? FastColor.ARGB32.opaque(BackpackItem.colour(stack)) : -1,
                    BackpackRegistry.item(size));
        }
        // Stap 2: de MOLLE-pouches ook (nieuw olijfgroen, net als de rugzak).
        for (com.senne.imttech.backpack.MolleItem.Kind kind : com.senne.imttech.backpack.MolleItem.Kind.values()) {
            event.register((stack, tintIndex) -> tintIndex == 0 ? FastColor.ARGB32.opaque(BackpackItem.colour(stack)) : -1,
                    BackpackRegistry.molle(kind));
        }
    }

    /** Camo: het model met het camoplaatje (de override imttech:camo in models/item/assault_pack.json en de andere). */
    private static void registerProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (BackpackItem.Size size : BackpackItem.Size.values()) {
                ItemProperties.register(BackpackRegistry.item(size), ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "camo"),
                        (stack, level, entity, seed) -> BackpackItem.camo(stack) ? 1 : 0);
            }
            for (com.senne.imttech.backpack.MolleItem.Kind kind : com.senne.imttech.backpack.MolleItem.Kind.values()) {
                ItemProperties.register(BackpackRegistry.molle(kind), ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "camo"),
                        (stack, level, entity, seed) -> BackpackItem.camo(stack) ? 1 : 0);
            }
        });
    }

    /** Van de server: de rits begint ({@code ticks} lang) of stopt (0). */
    public static void zip(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        zipTotal = ticks;
        zipStart = mc.level == null ? 0 : mc.level.getGameTime();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            zipTotal = 0;
            return;
        }
        while (OPEN_BACKPACK.consumeClick()) {
            if (Gear.back(player).isEmpty()) {
                player.displayClientMessage(Component.translatable("backpack.imttech.not_wearing").withStyle(ChatFormatting.RED), true);
            } else {
                PacketDistributor.sendToServer(new BackpackActionPayload(BackpackActionPayload.OPEN_WORN, 0));
            }
        }
        // Vangnet: kwam het "klaar" van de server niet aan, dan verdwijnt het balkje vanzelf.
        if (zipTotal > 0 && mc.level != null && mc.level.getGameTime() - zipStart > zipTotal + 20) {
            zipTotal = 0;
        }
    }

    /** Het balkje van de rits, onder je vizier: "Unzipping" en hoe ver hij is. */
    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (zipTotal <= 0 || mc.level == null || mc.options.hideGui) {
            return;
        }
        float done = Math.min(1, (mc.level.getGameTime() - zipStart + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / zipTotal);
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        int y = g.guiHeight() / 2 + 14;
        Component text = Component.translatable("backpack.imttech.unzipping");
        g.drawString(font, text, cx - font.width(text) / 2, y, HudStyle.argb(230, HudStyle.WHITE), true);
        int width = 64;
        g.fill(cx - width / 2 - 1, y + 11, cx + width / 2 + 1, y + 16, HudStyle.argb(200, 0x0E1116));
        HudStyle.bar(g, cx - width / 2, y + 12, width, 3, done, HudStyle.ORANGE);
    }

    /** Is er nu een rits bezig (voor de showcase)? */
    public static boolean zipping() {
        return zipTotal > 0;
    }
}
