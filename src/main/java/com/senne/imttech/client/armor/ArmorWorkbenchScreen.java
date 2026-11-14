package com.senne.imttech.client.armor;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.armor.ArmorRecipe;
import com.senne.imttech.armor.ArmorWorkbenchMenu;
import com.senne.imttech.armor.PlateItem;
import com.senne.imttech.armor.PlateTier;
import com.senne.imttech.armor.VestData;
import com.senne.imttech.armor.VestItem;
import com.senne.imttech.armor.VestTextPayload;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.GunsmithRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Het scherm van de Armor Workbench (hoofdstuk 111, zie ArmorWorkbenchMenu). Bovenaan drie tabbladen:
 * <ul>
 *   <li><b>Vests</b> en <b>Plates</b>: links een tegel per ding dat je kunt maken (een groene rand = je hebt alles bij je),
 *       rechts wat het gekozen ding kost (wat je hebt en wat nodig is, groen of rood), hoe vaak je het kunt maken, en Make.
 *       Op Plates staat linksonder ook <b>een plate herstellen</b>: leg hem in het vakje, je ziet wat het kost, Repair.</li>
 *   <li><b>Customize</b>: links het vakje voor je vest en het vest zelf van achteren (met je tekst); rechts de tekst op je
 *       rug (12 tekens) en zijn kleur, en de kleur van de stof: elke kleur kost een kleurstof uit je inventaris (een kleur
 *       die je niet hebt is donker), Camo kost bruin, lichtgroen en groen.</li>
 * </ul>
 */
public class ArmorWorkbenchScreen extends AbstractContainerScreen<ArmorWorkbenchMenu> {
    private static final int PANEL = 0x10141A;
    private static final int FRAME = 0x465460;
    private static final int SLOT = 0x1A2028;
    private static final int BOX = 0x0C1015;
    private static final int TILE_READY = 0x12281D;

    /** De tabbladen bovenaan. */
    private static final int TAB_Y = 4, TAB_H = 13;
    private static final String[] TABS = {"vests", "plates", "customize"};

    /** Vests en Plates: het vak met tegels (links) en het vak met de kosten (rechts). */
    private static final int TILES_X = 6, TILES_Y = 22, TILES_W = 112, TILES_H = 56;
    private static final int TILE = 20, TILE_STEP = 22, PER_ROW = 5;
    private static final int COSTS_X = 122, COSTS_Y = 22, COSTS_W = 102, COSTS_H = 106;

    /** Customize: het vest in beeld, het tekstvak, de kleuren van de tekst en van de stof. */
    private static final int PREVIEW_X = 60, PREVIEW_Y = 86;
    private static final float PREVIEW_SCALE = 3.8f;
    private static final int RIGHT_X = 100;
    private static final int TEXT_Y = 31;
    private static final int TEXT_COLOURS_Y = 55;
    private static final int FABRIC_Y = 86;
    private static final int SWATCH = 7, SWATCH_STEP = 8, SWATCHES_PER_ROW = 8;

    /** Welk tabblad en welk recept je het laatst koos: zo gaat de werkbank weer open zoals je hem achterliet. */
    private static int lastTab = ArmorWorkbenchMenu.TAB_VESTS;
    private static final int[] lastSelected = {-1, -1};

    private final int[] selected = {lastSelected[0], lastSelected[1]};
    private Button make;
    private Button repair;
    private Button camo;
    private EditBox text;
    private int textColour;
    /** Het vest in het vakje zoals het er de vorige tick uitzag (zie containerTick). */
    private ItemStack shownVest = ItemStack.EMPTY;
    /**
     * Wat dit scherm de server stuurde (tekst en kleur, zoals de server ze opslaat) en nog niet terugzag. Elke letter gaat
     * meteen naar de server, die een paar tikken achterloopt: komt het vest terug met een van deze, dan is dat ons eigen
     * typen en blijft het tekstvak staan. Elke andere verandering (een ander vest in het vakje, of eruit) leest het tekstvak
     * opnieuw, ook als je erin aan het typen bent: anders schreef je volgende letter of kleur de oude tekst op het nieuwe vest.
     */
    private final List<String> pending = new ArrayList<>();

    public ArmorWorkbenchScreen(ArmorWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = ArmorWorkbenchMenu.WIDTH;
        imageHeight = ArmorWorkbenchMenu.HEIGHT;
        inventoryLabelX = ArmorWorkbenchMenu.INVENTORY_X;
        inventoryLabelY = ArmorWorkbenchMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        // Ook na het groter of kleiner maken van het venster (dan bouwt Minecraft het scherm opnieuw, met een leeg tekstvak):
        // de volgende tick leest het nieuwe tekstvak de tekst van het vest.
        shownVest = ItemStack.EMPTY;
        pending.clear();
        make = addRenderableWidget(Button.builder(Component.translatable("screen.imttech.armor_workbench.make"), b -> {
            RecipeHolder<ArmorRecipe> recipe = selectedRecipe();
            if (recipe != null) {
                click(ArmorWorkbenchMenu.MAKE + indexOf(recipe));
            }
        }).bounds(leftPos + COSTS_X + 2, topPos + COSTS_Y + COSTS_H - 20, COSTS_W - 4, 18).build());
        repair = addRenderableWidget(Button.builder(Component.translatable("screen.imttech.armor_workbench.repair"),
                b -> click(ArmorWorkbenchMenu.REPAIR)).bounds(leftPos + TILES_X, topPos + 112, TILES_W, 16).build());
        camo = addRenderableWidget(Button.builder(Component.translatable("screen.imttech.armor_workbench.camo"),
                b -> click(ArmorWorkbenchMenu.CAMO)).bounds(leftPos + RIGHT_X + 68, topPos + FABRIC_Y - 1, 56, 16).build());
        text = new EditBox(font, leftPos + RIGHT_X, topPos + TEXT_Y, 122, 12, Component.translatable("screen.imttech.vest.text"));
        text.setMaxLength(VestData.MAX_TEXT);
        text.setHint(Component.translatable("screen.imttech.vest.text_hint"));
        text.setResponder(value -> sendText());
        addRenderableWidget(text);
        switchTo(lastTab);
    }

    // ================================================================ Tabbladen en knoppen

    /** Dit tabblad openen (ook voor de rondleiding). */
    public void switchTo(int tab) {
        menu.setTab(tab);
        lastTab = menu.tab();
        click(ArmorWorkbenchMenu.TAB + menu.tab());
        updateWidgets();
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    /** De tekst en zijn kleur naar de server (die zet ze op het vest in het vakje). */
    private void sendText() {
        if (VestItem.isVest(menu.vest())) {
            pending.add(key(VestData.clean(text.getValue()), textColour));
            PacketDistributor.sendToServer(new VestTextPayload(text.getValue(), textColour));
        }
    }

    /** Tekst en kleur samen, om te vergelijken met wat de server terugstuurt. */
    private static String key(String value, int colour) {
        return colour + ":" + value;
    }

    /** Voor de tests en de rondleiding: deze tekst en kleur typen (het tekstvak stuurt ze zelf naar de server). */
    public void type(String value, DyeColor colour) {
        textColour = colour.getId();
        text.setValue(value);
    }

    private void updateWidgets() {
        int tab = menu.tab();
        boolean making = tab != ArmorWorkbenchMenu.TAB_CUSTOMIZE;
        RecipeHolder<ArmorRecipe> recipe = selectedRecipe();
        make.visible = making;
        make.active = making && recipe != null && minecraft != null && minecraft.player != null
                && recipe.value().affordable(GunsmithRecipe.Carried.of(minecraft.player.getInventory()));
        ItemStack plate = menu.plate();
        repair.visible = tab == ArmorWorkbenchMenu.TAB_PLATES;
        repair.active = PlateItem.tierOf(plate) != null && plate.isDamaged() && haveRepairMaterial(plate);
        boolean customizing = tab == ArmorWorkbenchMenu.TAB_CUSTOMIZE;
        boolean vest = VestItem.isVest(menu.vest());
        camo.visible = customizing;
        camo.active = vest && minecraft != null && minecraft.player != null
                && !ArmorWorkbenchMenu.findDye(minecraft.player.getInventory(), DyeColor.BROWN).isEmpty()
                && !ArmorWorkbenchMenu.findDye(minecraft.player.getInventory(), DyeColor.LIME).isEmpty()
                && !ArmorWorkbenchMenu.findDye(minecraft.player.getInventory(), DyeColor.GREEN).isEmpty();
        text.visible = customizing;
        text.setEditable(vest);
    }

    private boolean haveRepairMaterial(ItemStack plate) {
        PlateTier tier = PlateItem.tierOf(plate);
        return tier != null && minecraft != null && minecraft.player != null
                && minecraft.player.getInventory().countItem(tier.repairItem()) > 0;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack vest = menu.vest();
        if (!ItemStack.isSameItemSameComponents(vest, shownVest)) {
            // Is dit hetzelfde vest als daarnet, met alleen een andere tekst of kleur, precies zoals wij die stuurden? Dan
            // is het de server die ons typen verwerkte. Al het andere (een ander vest, verven, het vakje leeg) leest opnieuw.
            boolean echo = false;
            if (VestItem.isVest(vest) && VestItem.isVest(shownVest)) {
                VestData now = VestItem.data(vest);
                VestData before = VestItem.data(shownVest);
                boolean sameVest = ItemStack.isSameItemSameComponents(
                        VestItem.with(vest, now.withText(before.text(), before.textColour())), shownVest);
                int at = pending.indexOf(key(now.text(), now.textColour()));
                echo = sameVest && at >= 0;
                if (echo) {
                    // Wat we daarvoor stuurden, heeft de server ook al gehad.
                    pending.subList(0, at + 1).clear();
                }
            }
            shownVest = vest.copy();
            if (!echo) {
                load(vest);
            }
        }
        updateWidgets();
    }

    /** Het tekstvak en de tekstkleur van dit vest lezen (leeg als er geen vest in het vakje ligt), zonder iets te sturen. */
    private void load(ItemStack vest) {
        pending.clear();
        boolean has = VestItem.isVest(vest);
        VestData data = VestItem.data(vest);
        textColour = has ? data.textColour() : DyeColor.WHITE.getId();
        text.setResponder(value -> { });
        text.setValue(has ? data.text() : "");
        text.setResponder(value -> sendText());
    }

    // ================================================================ De recepten

    private List<RecipeHolder<ArmorRecipe>> recipes() {
        if (minecraft == null || minecraft.level == null) {
            return List.of();
        }
        return ArmorRecipe.allArmor(minecraft.level.getRecipeManager());
    }

    /** De recepten van dit tabblad (Vests of Plates), in hun vaste volgorde. */
    private List<RecipeHolder<ArmorRecipe>> onTab(int tab) {
        ArmorRecipe.Tab wanted = tab == ArmorWorkbenchMenu.TAB_VESTS ? ArmorRecipe.Tab.VESTS : ArmorRecipe.Tab.PLATES;
        List<RecipeHolder<ArmorRecipe>> list = new ArrayList<>();
        for (RecipeHolder<ArmorRecipe> holder : recipes()) {
            if (holder.value().tab() == wanted) {
                list.add(holder);
            }
        }
        return list;
    }

    private int indexOf(RecipeHolder<ArmorRecipe> recipe) {
        return recipes().indexOf(recipe);
    }

    /** Het gekozen recept op dit tabblad (het eerste als je nog niets koos), of null. */
    private RecipeHolder<ArmorRecipe> selectedRecipe() {
        int tab = menu.tab();
        if (tab == ArmorWorkbenchMenu.TAB_CUSTOMIZE) {
            return null;
        }
        List<RecipeHolder<ArmorRecipe>> list = onTab(tab);
        if (list.isEmpty()) {
            return null;
        }
        int index = selected[tab];
        return index >= 0 && index < list.size() ? list.get(index) : list.getFirst();
    }

    /** Voor de rondleiding: het recept met deze uitkomst kiezen op het huidige tabblad. */
    public void select(net.minecraft.world.item.Item result) {
        List<RecipeHolder<ArmorRecipe>> list = onTab(menu.tab());
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).value().result().is(result)) {
                selected[menu.tab()] = i;
                lastSelected[menu.tab()] = i;
            }
        }
        updateWidgets();
    }

    // ================================================================ Tekenen

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, HudStyle.argb(245, PANEL));
        g.renderOutline(x, y, imageWidth, imageHeight, HudStyle.argb(255, FRAME));
        HudStyle.brackets(g, x + 2, y + 19, x + imageWidth - 3, y + ArmorWorkbenchMenu.INVENTORY_Y - 13, 6,
                HudStyle.argb(255, HudStyle.ORANGE));
        renderTabs(g, mouseX, mouseY);
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, HudStyle.argb(255, SLOT));
            g.renderOutline(sx - 1, sy - 1, 18, 18, HudStyle.argb(255, FRAME));
        }
        int tab = menu.tab();
        if (tab == ArmorWorkbenchMenu.TAB_CUSTOMIZE) {
            renderCustomize(g, mouseX, mouseY, partialTick);
        } else {
            renderTiles(g, mouseX, mouseY, tab);
            renderCosts(g);
            if (tab == ArmorWorkbenchMenu.TAB_PLATES) {
                renderRepair(g);
            }
        }
    }

    private int[] tabBounds(int tab) {
        int tx = leftPos + 6;
        for (int i = 0; i < tab; i++) {
            tx += font.width(Component.translatable("screen.imttech.armor_workbench.tab." + TABS[i])) + 12 + 3;
        }
        return new int[]{tx, font.width(Component.translatable("screen.imttech.armor_workbench.tab." + TABS[tab])) + 12};
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (int tab = 0; tab < TABS.length; tab++) {
            int[] b = tabBounds(tab);
            int y = topPos + TAB_Y;
            boolean active = tab == menu.tab();
            boolean hover = mouseX >= b[0] && mouseX < b[0] + b[1] && mouseY >= y && mouseY < y + TAB_H;
            if (active) {
                g.fill(b[0], y, b[0] + b[1], y + TAB_H, HudStyle.argb(45, HudStyle.CYAN));
            }
            g.renderOutline(b[0], y, b[1], TAB_H, HudStyle.argb(255, active ? HudStyle.CYAN : FRAME));
            int colour = active ? HudStyle.CYAN : hover ? HudStyle.WHITE : 0x9AA7B0;
            g.drawString(font, Component.translatable("screen.imttech.armor_workbench.tab." + TABS[tab]), b[0] + 6, y + 3,
                    HudStyle.argb(255, colour), false);
        }
    }

    private void renderTiles(GuiGraphics g, int mouseX, int mouseY, int tab) {
        int x0 = leftPos + TILES_X;
        int y0 = topPos + TILES_Y;
        g.fill(x0, y0, x0 + TILES_W, y0 + TILES_H, HudStyle.argb(255, BOX));
        g.renderOutline(x0, y0, TILES_W, TILES_H, HudStyle.argb(255, FRAME));
        List<RecipeHolder<ArmorRecipe>> list = onTab(tab);
        RecipeHolder<ArmorRecipe> chosen = selectedRecipe();
        GunsmithRecipe.Carried carried = minecraft == null || minecraft.player == null ? null
                : GunsmithRecipe.Carried.of(minecraft.player.getInventory());
        for (int i = 0; i < list.size(); i++) {
            int tx = x0 + 4 + (i % PER_ROW) * TILE_STEP;
            int ty = y0 + 4 + (i / PER_ROW) * TILE_STEP;
            ArmorRecipe recipe = list.get(i).value();
            boolean ready = carried != null && recipe.affordable(carried);
            boolean isChosen = list.get(i).equals(chosen);
            boolean hover = mouseX >= tx && mouseX < tx + TILE && mouseY >= ty && mouseY < ty + TILE;
            g.fill(tx, ty, tx + TILE, ty + TILE, HudStyle.argb(255, ready ? TILE_READY : SLOT));
            int edge = isChosen ? HudStyle.CYAN : hover ? HudStyle.WHITE : ready ? HudStyle.GREEN : FRAME;
            g.renderOutline(tx, ty, TILE, TILE, HudStyle.argb(255, edge));
            g.renderItem(recipe.result(), tx + 2, ty + 2);
        }
        if (tab == ArmorWorkbenchMenu.TAB_VESTS) {
            // Onder de vesten: waar je de tekst en de kleur aanpast.
            List<FormattedCharSequence> hint = font.split(Component.translatable("screen.imttech.armor_workbench.vests_hint"), 140);
            for (int line = 0; line < hint.size(); line++) {
                smallSeq(g, hint.get(line), x0, y0 + TILES_H + 6 + line * 8, HudStyle.argb(150, HudStyle.WHITE));
            }
        }
    }

    private void renderCosts(GuiGraphics g) {
        int x0 = leftPos + COSTS_X;
        int y0 = topPos + COSTS_Y;
        g.fill(x0, y0, x0 + COSTS_W, y0 + COSTS_H, HudStyle.argb(255, BOX));
        g.renderOutline(x0, y0, COSTS_W, COSTS_H, HudStyle.argb(255, FRAME));
        RecipeHolder<ArmorRecipe> holder = selectedRecipe();
        if (holder == null || minecraft == null || minecraft.player == null) {
            return;
        }
        ArmorRecipe recipe = holder.value();
        GunsmithRecipe.Carried carried = GunsmithRecipe.Carried.of(minecraft.player.getInventory());
        int y = y0 + 4;
        for (FormattedCharSequence line : font.split(recipe.result().getHoverName(), COSTS_W - 8)) {
            g.drawString(font, line, x0 + 4, y, HudStyle.argb(255, HudStyle.ORANGE), false);
            y += 10;
        }
        small(g, Component.translatable("screen.imttech.armor_workbench.costs"), x0 + 4, y + 1, HudStyle.argb(170, HudStyle.WHITE));
        y += 10;
        for (SizedIngredient ingredient : recipe.ingredients()) {
            ItemStack[] items = ingredient.ingredient().getItems();
            if (items.length > 0) {
                g.renderItem(items[0], x0 + 4, y);
            }
            int have = GunsmithRecipe.have(carried, ingredient);
            int need = ingredient.count();
            String amount = have + " / " + need;
            g.drawString(font, amount, x0 + 24, y + 4, HudStyle.argb(255, have >= need ? HudStyle.GREEN : HudStyle.RED), false);
            y += 17;
        }
        int times = recipe.times(carried);
        small(g, Component.translatable("screen.imttech.armor_workbench.can_make", times), x0 + 4, y0 + COSTS_H - 30,
                HudStyle.argb(200, times > 0 ? HudStyle.GREEN : HudStyle.WHITE));
    }

    private void renderRepair(GuiGraphics g) {
        int x0 = leftPos + TILES_X;
        small(g, Component.translatable("screen.imttech.armor_workbench.repair_title"), x0, topPos + 82, HudStyle.argb(220, HudStyle.WHITE));
        // Rechts op dezelfde regel (niet achter het getal: daar liep het in "You can make" van het vak ernaast).
        Component each = Component.translatable("screen.imttech.armor_workbench.repair_each");
        small(g, each, x0 + TILES_W - Math.round(font.width(each) * 0.75f), topPos + 82, HudStyle.argb(150, HudStyle.WHITE));
        ItemStack plate = menu.plate();
        PlateTier tier = PlateItem.tierOf(plate);
        int tx = leftPos + ArmorWorkbenchMenu.PLATE_X + 22;
        int ty = topPos + ArmorWorkbenchMenu.PLATE_Y + 1;
        if (tier == null) {
            small(g, Component.translatable("screen.imttech.armor_workbench.repair_empty"), tx, ty + 4, HudStyle.argb(150, HudStyle.WHITE));
            return;
        }
        if (!plate.isDamaged()) {
            small(g, Component.translatable("screen.imttech.armor_workbench.repair_whole"), tx, ty + 4, HudStyle.argb(200, HudStyle.GREEN));
            return;
        }
        int need = ArmorWorkbenchMenu.repairCost(plate);
        int have = minecraft == null || minecraft.player == null ? 0 : minecraft.player.getInventory().countItem(tier.repairItem());
        g.renderItem(new ItemStack(tier.repairItem()), tx, ty);
        g.drawString(font, have + " / " + need, tx + 19, ty + 4, HudStyle.argb(255, have >= need ? HudStyle.GREEN : HudStyle.RED), false);
    }

    private void renderCustomize(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        small(g, Component.translatable("screen.imttech.armor_workbench.vest"), ArmorWorkbenchMenu.VEST_X - 1 + leftPos,
                topPos + ArmorWorkbenchMenu.VEST_Y - 10, HudStyle.argb(220, HudStyle.WHITE));
        ItemStack vest = menu.vest();
        boolean has = VestItem.isVest(vest);
        if (has) {
            renderPreview(g, vest, partialTick);
        } else {
            List<FormattedCharSequence> hint = font.split(Component.translatable("screen.imttech.armor_workbench.put_vest"), 110);
            for (int line = 0; line < hint.size(); line++) {
                smallSeq(g, hint.get(line), leftPos + 10, topPos + 60 + line * 8, HudStyle.argb(150, HudStyle.WHITE));
            }
        }
        int rx = leftPos + RIGHT_X;
        small(g, Component.translatable("screen.imttech.vest.text"), rx - 1, topPos + TEXT_Y - 9, HudStyle.argb(220, HudStyle.WHITE));
        small(g, Component.translatable("screen.imttech.vest.colour"), rx - 1, topPos + TEXT_COLOURS_Y - 8, HudStyle.argb(170, HudStyle.WHITE));
        small(g, Component.translatable("screen.imttech.armor_workbench.fabric"), rx - 1, topPos + FABRIC_Y - 8, HudStyle.argb(170, HudStyle.WHITE));
        DyeColor[] dyes = DyeColor.values();
        for (int i = 0; i < dyes.length; i++) {
            // De kleur van de tekst (gratis).
            int[] t = swatch(TEXT_COLOURS_Y, i);
            g.fill(t[0], t[1], t[0] + SWATCH, t[1] + SWATCH, HudStyle.argb(has ? 255 : 90, dyes[i].getTextColor()));
            boolean hoverText = has && over(t, mouseX, mouseY);
            if (has && (i == textColour || hoverText)) {
                g.renderOutline(t[0] - 1, t[1] - 1, SWATCH + 2, SWATCH + 2, HudStyle.argb(255, i == textColour ? HudStyle.WHITE : HudStyle.CYAN));
            }
            // De kleur van de stof (kost een kleurstof): donker als je die kleurstof niet hebt.
            int[] f = swatch(FABRIC_Y, i);
            boolean owned = has && minecraft != null && minecraft.player != null
                    && !ArmorWorkbenchMenu.findDye(minecraft.player.getInventory(), dyes[i]).isEmpty();
            g.fill(f[0], f[1], f[0] + SWATCH, f[1] + SWATCH, HudStyle.argb(owned ? 255 : 70, dyes[i].getTextureDiffuseColor()));
            if (owned && over(f, mouseX, mouseY)) {
                g.renderOutline(f[0] - 1, f[1] - 1, SWATCH + 2, SWATCH + 2, HudStyle.argb(255, HudStyle.CYAN));
            }
        }
        List<FormattedCharSequence> hint = font.split(Component.translatable("screen.imttech.armor_workbench.dye_hint"), 160);
        for (int line = 0; line < hint.size(); line++) {
            smallSeq(g, hint.get(line), rx - 1, topPos + FABRIC_Y + 19 + line * 8, HudStyle.argb(150, HudStyle.WHITE));
        }
    }

    /** Het vest in het vakje, van achteren (zodat je je tekst ziet), een beetje schommelend, met de tekst die je typt. */
    private void renderPreview(GuiGraphics g, ItemStack vest, float partialTick) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        float time = minecraft.player.tickCount + partialTick;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(leftPos + PREVIEW_X, topPos + PREVIEW_Y, 120);
        float scale = PREVIEW_SCALE * 16;
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(8));
        pose.mulPose(Axis.YP.rotationDegrees(180 + Mth.sin(time * 0.04f) * 22));
        pose.translate(-0.5, -10 / 16.0, -0.5);
        Lighting.setupForEntityInInventory();
        VestData shown = VestItem.data(vest).withText(text.getValue(), textColour);
        VestRenderer.renderModel(pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, vest, minecraft.level, shown);
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
    }

    private int[] swatch(int rowY, int index) {
        return new int[]{leftPos + RIGHT_X + (index % SWATCHES_PER_ROW) * SWATCH_STEP,
                topPos + rowY + (index / SWATCHES_PER_ROW) * SWATCH_STEP};
    }

    private static boolean over(int[] at, double mouseX, double mouseY) {
        return mouseX >= at[0] && mouseX < at[0] + SWATCH && mouseY >= at[1] && mouseY < at[1] + SWATCH;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, HudStyle.argb(170, HudStyle.WHITE), false);
    }

    private void small(GuiGraphics g, Component line, int x, int y, int argb) {
        smallSeq(g, line.getVisualOrderText(), x, y, argb);
    }

    /** Kleine tekst (driekwart), zodat alles naast elkaar past zonder over elkaar te lopen. */
    private void smallSeq(GuiGraphics g, FormattedCharSequence line, int x, int y, int argb) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, line, 0, 0, argb, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        // De naam van een tegel als je hem aanwijst.
        int tab = menu.tab();
        if (tab != ArmorWorkbenchMenu.TAB_CUSTOMIZE && menu.getCarried().isEmpty()) {
            List<RecipeHolder<ArmorRecipe>> list = onTab(tab);
            for (int i = 0; i < list.size(); i++) {
                int tx = leftPos + TILES_X + 4 + (i % PER_ROW) * TILE_STEP;
                int ty = topPos + TILES_Y + 4 + (i / PER_ROW) * TILE_STEP;
                if (mouseX >= tx && mouseX < tx + TILE && mouseY >= ty && mouseY < ty + TILE) {
                    g.renderTooltip(font, list.get(i).value().result(), mouseX, mouseY);
                }
            }
        }
    }

    // ================================================================ Klikken en typen

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int tab = 0; tab < TABS.length; tab++) {
                int[] b = tabBounds(tab);
                if (mouseX >= b[0] && mouseX < b[0] + b[1] && mouseY >= topPos + TAB_Y && mouseY < topPos + TAB_Y + TAB_H) {
                    switchTo(tab);
                    return true;
                }
            }
            int tab = menu.tab();
            if (tab != ArmorWorkbenchMenu.TAB_CUSTOMIZE) {
                List<RecipeHolder<ArmorRecipe>> list = onTab(tab);
                for (int i = 0; i < list.size(); i++) {
                    int tx = leftPos + TILES_X + 4 + (i % PER_ROW) * TILE_STEP;
                    int ty = topPos + TILES_Y + 4 + (i / PER_ROW) * TILE_STEP;
                    if (mouseX >= tx && mouseX < tx + TILE && mouseY >= ty && mouseY < ty + TILE) {
                        selected[tab] = i;
                        lastSelected[tab] = i;
                        updateWidgets();
                        return true;
                    }
                }
            } else if (VestItem.isVest(menu.vest())) {
                DyeColor[] dyes = DyeColor.values();
                for (int i = 0; i < dyes.length; i++) {
                    if (over(swatch(TEXT_COLOURS_Y, i), mouseX, mouseY)) {
                        textColour = i;
                        sendText();
                        return true;
                    }
                    if (over(swatch(FABRIC_Y, i), mouseX, mouseY)) {
                        click(ArmorWorkbenchMenu.DYE + i);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Typ je in het tekstvak, dan sluit de inventaristoets (E) het scherm niet (zoals bij een aambeeld). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
            return true;
        }
        if (text != null && text.isFocused() && (text.keyPressed(keyCode, scanCode, modifiers) || text.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
