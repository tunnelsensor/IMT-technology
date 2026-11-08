package com.senne.imttech.client.howto;

import com.mojang.blaze3d.platform.InputConstants;
import com.senne.imttech.client.ImtKeys;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.howto.HowToTopics;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

/**
 * W vasthouden op een item (hoofdstuk 63, jouw antwoord 1, zoals bij Create): in elk scherm met voorwerpen (je inventaris,
 * een kist, het creative-menu, de lijst van JEI) krijgt een item met een animatie een regel in zijn tooltip, "Hold [W]:
 * How it works". Houd je de toets ingedrukt terwijl je muis erop staat, dan loopt die regel vol, en na 0,6 seconde gaat
 * de animatie open. De toets staat in Controls onder I.M.T.
 *
 * Welk item je aanwijst, weten we van het tooltip dat Minecraft echt tekent (RenderTooltipEvent): dat werkt in elk scherm,
 * ook van andere mods, zonder dat we die schermen hoeven te kennen.
 */
public final class HowToHover {
    private HowToHover() {}

    /** Zo lang houd je de toets vast voor de animatie opengaat (12 ticks = 0,6 seconde). */
    static final int HOLD_TICKS = 12;

    private static ItemStack hovered = ItemStack.EMPTY;
    private static long hoveredAt;
    private static ItemStack holding = ItemStack.EMPTY;
    private static int held;
    /** Net een animatie geopend: eerst de toets loslaten, anders gaat hij na het sluiten meteen weer open. */
    private static boolean waitForRelease;

    /** De regel in de tooltip, die volloopt terwijl je de toets vasthoudt. */
    public static void onTooltip(ItemTooltipEvent event) {
        if (event.getEntity() == null || Minecraft.getInstance().screen == null) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!HowToTopics.has(stack.getItem())) {
            return;
        }
        float progress = ItemStack.isSameItem(holding, stack) ? held / (float) HOLD_TICKS : 0.0f;
        event.getToolTip().add(line(progress));
    }

    /** "Hold [W]: How it works", van links naar rechts in cyaan naarmate je langer vasthoudt. */
    static Component line(float progress) {
        String text = Component.translatable("tooltip.imttech.howto", ImtKeys.HOW_IT_WORKS.getTranslatedKeyMessage()).getString();
        int split = Math.max(0, Math.min(text.length(), Math.round(text.length() * progress)));
        MutableComponent out = Component.empty();
        out.append(Component.literal(text.substring(0, split)).withStyle(style -> style.withColor(HudStyle.CYAN)));
        out.append(Component.literal(text.substring(split)).withStyle(ChatFormatting.DARK_GRAY));
        return out;
    }

    /** Welk item er nu echt een tooltip heeft (= waar je muis op staat). */
    public static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        if (!event.getItemStack().isEmpty()) {
            hovered = event.getItemStack();
            hoveredAt = Util.getMillis();
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!keyDown(mc)) {
            waitForRelease = false;
            reset();
            return;
        }
        Screen screen = mc.screen;
        if (screen == null) {
            // W ingedrukt zonder scherm = je loopt. Open je dan je inventaris met W nog ingedrukt, dan moet je hem eerst
            // loslaten: anders gaat er een animatie open van het item waar je muis toevallig op staat.
            waitForRelease = true;
            reset();
            return;
        }
        boolean hovering = screen != null && !(screen instanceof HowToScreen) && !(screen instanceof HowToIndexScreen)
                && Util.getMillis() - hoveredAt < 150 && !hovered.isEmpty() && HowToTopics.has(hovered.getItem())
                && !typing(screen);
        if (waitForRelease || !hovering) {
            reset();
            return;
        }
        if (!ItemStack.isSameItem(holding, hovered)) {
            holding = hovered.copy();
            held = 0;
        }
        if (++held >= HOLD_TICKS) {
            ItemStack open = holding;
            reset();
            waitForRelease = true;
            HowToScreen.open(open);
        }
    }

    private static void reset() {
        held = 0;
        holding = ItemStack.EMPTY;
    }

    /** Staat de toets van How it works ingedrukt (ook als hij op een muisknop staat)? */
    static boolean keyDown(Minecraft mc) {
        KeyMapping mapping = ImtKeys.HOW_IT_WORKS;
        InputConstants.Key key = mapping.getKey();
        if (key.equals(InputConstants.UNKNOWN) || !mapping.getKeyModifier().isActive(mapping.getKeyConflictContext())) {
            return false;
        }
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window, key.getValue());
    }

    /** Typ je in een tekstvak (het zoekvak van het creative-menu)? Dan is W gewoon een letter. */
    private static boolean typing(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox box && box.isFocused() && box.isVisible()) {
                return true;
            }
        }
        return screen.getFocused() instanceof EditBox box && box.isFocused();
    }
}
