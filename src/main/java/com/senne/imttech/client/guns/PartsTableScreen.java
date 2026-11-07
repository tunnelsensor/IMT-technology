package com.senne.imttech.client.guns;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.GunsmithMenu;
import com.senne.imttech.guns.PartState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Het scherm van een tafel waarop je een wapen uit onderdelen in elkaar sleept (hoofdstuk 52 en 55): de Gunsmith Table
 * (de Glock, zie {@link GunsmithScreen}) en de Laser Workbench (de lasergeweren, zie client/weapons/LaserWorkbenchScreen).
 * Jouw wens: "niet met GUI vakjes maar echt dat je een tafel hebt met de onderdelen die je kunt gebruiken en in je
 * inventory hebt; dan versleep je de onderdelen naar waar het moet komen en dan heeft dat wel snap in place".
 *
 * <pre>
 *  YOUR PARTS        |         het wapen, groot, van opzij            |  GLOCK 19
 *  [slede]  Steel    |   (wat er niet in zit, ligt er los omheen:     |  Frame   Steel  ####  [x]
 *  [veren]  Titanium |    een gestippeld vak met de naam)             |  Slide   -----
 *  [Glock]  ...      |                                                 |  ...
 *                    |                                                 |  [ Take the pistol ]
 * </pre>
 * Links: alles uit je inventaris dat hier hoort (onderdelen en wapens); andere spullen zie je niet. Sleep een onderdeel
 * naar zijn plek: kom je in de buurt, dan licht de plek op en klikt het vast als je loslaat (de server zet het erin, zie
 * PartsTable). Sleep een onderdeel van het wapen af (of klik op zijn [x] rechts) en het komt terug in je inventaris. Een
 * wapen uit je inventaris sleep je op de tafel; de knop rechtsonder neemt het mee.
 *
 * Alles wat hier staat is hetzelfde voor beide tafels: slepen, de vakken, de lijsten. Wat per tafel verschilt (welke
 * onderdelen, waar ze zitten, hoe het wapen getekend wordt, de teksten) vragen we aan de tafel zelf: de abstracte
 * methodes onderaan.
 *
 * @param <P> de soort onderdelen van deze tafel (GunPart of LaserPart); zijn nummer is het nummer voor de server.
 */
public abstract class PartsTableScreen<P extends Enum<P>> extends AbstractContainerScreen<GunsmithMenu> {
    protected static final int FRAME = HudStyle.argb(255, 0x465460);
    private static final int PANEL = HudStyle.argb(245, 0x0E1116);
    private static final int TRAY_BG = HudStyle.argb(255, 0x161A20);
    private static final int ROW_H = 20;
    protected static final int TRAY_X = 6, TRAY_Y = 26, TRAY_W = 74, TRAY_H = 152;
    protected static final int VIEW_X = 84, VIEW_Y = 18, VIEW_W = 150, VIEW_H = 160;
    protected static final int SIDE_X = 238, SIDE_W = 84;
    /** Tot zo ver (pixels) van zijn plek klikt een onderdeel vast. */
    private static final int SNAP = 16;

    /** Eén rij in de lijst links: een vakje van je inventaris, met wat erin zit (een onderdeel of een wapen). */
    protected record Row(int slot, ItemStack stack, boolean part, boolean gun) {}

    private final P[] all;
    private final List<Row> rows = new ArrayList<>();
    private int scroll;
    /** Wat je sleept: een vakje van je inventaris (-1 = niets), of een onderdeel van het wapen (null = niets). */
    private int dragSlot = -1;
    private @Nullable P dragPart;
    private double dragX, dragY;
    private float yaw = 28;
    private float pitch = 12;
    private Button take;
    private final Map<P, Button> removeButtons = new HashMap<>();
    /** Waar elk onderdeel op het scherm ligt (deze frame): zijn vak. */
    private final Map<P, int[]> zones = new HashMap<>();

    protected PartsTableScreen(GunsmithMenu menu, Inventory inventory, Component title, P[] all) {
        super(menu, inventory, title);
        this.all = all;
        imageWidth = 328;
        imageHeight = 186;
    }

    // ================================================================ Wat elke tafel zelf weet

    /** Alle onderdelen van deze tafel, in de volgorde van de lijst rechts. */
    protected final P[] parts() {
        return all;
    }

    /** Het onderdeel waarmee een wapen begint (het frame). */
    protected abstract P base();

    /** Welk vak dit item uit je inventaris vult, of null als het geen onderdeel is. */
    protected abstract @Nullable P partOf(ItemStack stack);

    /** Is dit een wapen dat op deze tafel hoort? */
    protected abstract boolean isGun(ItemStack stack);

    /** Wat er van dit onderdeel in het wapen zit, of null als het er niet in zit. */
    protected abstract @Nullable PartState state(ItemStack gun, P part);

    /** Heeft dit onderdeel een materiaal (dan staat dat in de lijst rechts)? */
    protected abstract boolean hasMaterial(P part);

    protected abstract Component partName(P part);

    /** Hoort dit onderdeel op dit wapen (een pistool heeft geen sling)? */
    protected abstract boolean allowed(P part, ItemStack gun);

    /** Kun je er nu bij (de loop van de Glock zit onder de slede)? */
    protected abstract boolean reachable(P part, ItemStack gun);

    /** Mag dit onderdeel er nu uit (het frame pas als laatste)? */
    protected abstract boolean removable(P part, ItemStack gun);

    /** Past dit onderdeel uit je inventaris op het wapen dat er ligt (een emitter van een rifle niet op een sniper)? */
    protected abstract boolean fits(ItemStack stack, ItemStack gun);

    /** Het vak van een onderdeel in het model (model-pixels); met een leeg wapen: waar het frame komt. */
    protected abstract AABB bounds(ItemStack gun, P part);

    /** Hoe ver het uit elkaar geschoven ligt als het er niet in zit (model-pixels). */
    protected abstract Vector3f exploded(ItemStack gun, P part);

    /** Het midden van het wapen (model-pixels): dat komt in het midden van de tafel. */
    protected abstract Vector3f centre(ItemStack gun);

    /** Zo groot is het wapen op het scherm: pixels per pixel van het model. */
    protected abstract float zoom(ItemStack gun);

    /** De onderdelen die erin zitten, in 3D (behalve {@code skip}: dat sleep je net weg). */
    protected abstract void renderInstalled(PoseStack pose, MultiBufferSource buffers, ItemStack gun, @Nullable P skip);

    /** Het onderdeel als item (voor aan je muis als je het van het wapen af sleept). */
    protected abstract ItemStack partStack(ItemStack gun, P part);

    /** De naam boven de lijst rechts: het wapen, of "lege tafel". */
    protected abstract Component gunTitle(ItemStack gun);

    protected abstract String rowName(Row row);

    protected abstract String rowSub(Row row);

    /** Onderaan rechts: wat er nog moet gebeuren, en in welke kleur. */
    protected abstract String readyText(ItemStack gun);

    protected abstract int readyColour(ItemStack gun);

    protected abstract Component takeText();

    /** De twee regels links als je niets bij je hebt. */
    protected abstract String[] emptyTray();

    /** Het zinnetje onder de tafel. */
    protected abstract String hint();

    /** De uitleg bij een onderdeel dat je niet kunt pakken, of null. */
    protected abstract @Nullable Component coveredTooltip(P part, ItemStack gun);

    /** De uitleg bij een onderdeel in het wapen als je er met de muis over gaat. */
    protected abstract List<Component> partTooltip(ItemStack gun, P part, PartState state);

    // ================================================================ Het scherm

    @Override
    protected void init() {
        super.init();
        inventoryLabelY = -100;
        take = addRenderableWidget(Button.builder(takeText(), b -> click(GunsmithMenu.TAKE_GUN))
                .bounds(leftPos + SIDE_X, topPos + imageHeight - 26, SIDE_W, 18).build());
        int y = topPos + 30;
        for (P part : all) {
            Button button = addRenderableWidget(Button.builder(Component.literal("x"),
                    b -> click(GunsmithMenu.REMOVE + part.ordinal())).bounds(leftPos + SIDE_X + SIDE_W - 12, y, 12, 12).build());
            removeButtons.put(part, button);
            y += rowStep();
        }
    }

    /** De rijen rechts: zoals altijd 20 pixels uit elkaar, iets dichter als er veel onderdelen zijn (de laser heeft er acht). */
    private int rowStep() {
        return all.length <= 6 ? 20 : 13;
    }

    protected final void click(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    protected final @Nullable P dragging() {
        return dragPart;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        rebuildRows();
        ItemStack gun = menu.gun();
        take.active = !gun.isEmpty();
        for (P part : all) {
            Button button = removeButtons.get(part);
            PartState state = state(gun, part);
            boolean covered = state != null && !reachable(part, gun);
            button.visible = state != null;
            button.active = state != null && !covered && removable(part, gun);
            Component why = covered ? coveredTooltip(part, gun) : null;
            button.setTooltip(why == null ? null : Tooltip.create(why));
        }
    }

    /** De lijst links: onderdelen en wapens uit je inventaris (niets anders). */
    private void rebuildRows() {
        rows.clear();
        Inventory inventory = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getInventory();
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            boolean part = partOf(stack) != null;
            boolean gun = isGun(stack);
            if (part || gun) {
                rows.add(new Row(slot, stack, part, gun));
            }
        }
        int max = Math.max(0, rows.size() * ROW_H - TRAY_H + 4);
        scroll = Mth.clamp(scroll, 0, max);
    }

    // ================================================================ Tekenen

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        g.renderOutline(x, y, imageWidth, imageHeight, FRAME);
        HudStyle.brackets(g, x + 2, y + 2, x + imageWidth - 3, y + imageHeight - 3, 8, HudStyle.argb(255, HudStyle.ORANGE));
        // Links: de lijst.
        g.fill(x + TRAY_X, y + TRAY_Y, x + TRAY_X + TRAY_W, y + TRAY_Y + TRAY_H, TRAY_BG);
        g.renderOutline(x + TRAY_X, y + TRAY_Y, TRAY_W, TRAY_H, FRAME);
        g.enableScissor(x + TRAY_X + 1, y + TRAY_Y + 1, x + TRAY_X + TRAY_W - 1, y + TRAY_Y + TRAY_H - 1);
        int rowY = y + TRAY_Y + 2 - scroll;
        for (Row row : rows) {
            boolean hover = dragSlot < 0 && dragPart == null && mouseX >= x + TRAY_X && mouseX < x + TRAY_X + TRAY_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H && mouseY >= y + TRAY_Y && mouseY < y + TRAY_Y + TRAY_H;
            if (hover) {
                g.fill(x + TRAY_X + 1, rowY, x + TRAY_X + TRAY_W - 1, rowY + ROW_H, HudStyle.argb(60, HudStyle.CYAN));
            }
            if (row.slot != dragSlot) {
                g.renderItem(row.stack, x + TRAY_X + 3, rowY + 2);
                if (row.stack.getCount() > 1) {
                    g.renderItemDecorations(font, row.stack, x + TRAY_X + 3, rowY + 2);
                }
            }
            small(g, rowName(row), x + TRAY_X + 22, rowY + 3, HudStyle.argb(255, HudStyle.WHITE));
            small(g, rowSub(row), x + TRAY_X + 22, rowY + 11, HudStyle.argb(200, row.gun ? HudStyle.ORANGE : HudStyle.CYAN));
            rowY += ROW_H;
        }
        if (rows.isEmpty()) {
            String[] lines = emptyTray();
            for (int i = 0; i < lines.length; i++) {
                small(g, lines[i], x + TRAY_X + 4, y + TRAY_Y + 6 + 8 * i, HudStyle.argb(160, HudStyle.WHITE));
            }
        }
        g.disableScissor();
        // Midden: de tafel met het wapen.
        g.fill(x + VIEW_X, y + VIEW_Y, x + VIEW_X + VIEW_W, y + VIEW_Y + VIEW_H, HudStyle.argb(255, 0x0A0D12));
        Row dragged = draggedRow();
        g.renderOutline(x + VIEW_X, y + VIEW_Y, VIEW_W, VIEW_H, dragged != null && dragged.gun
                ? HudStyle.argb(255, HudStyle.CYAN) : FRAME);
        renderGun(g, x + VIEW_X + VIEW_W / 2, y + VIEW_Y + VIEW_H / 2 + 6, mouseX, mouseY);
        // Rechts: de lijst van onderdelen.
        renderSide(g, x + SIDE_X, y + 18);
        // Wat je sleept, aan je muis.
        if (dragged != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.renderItem(dragged.stack, mouseX - 8, mouseY - 8);
            g.pose().popPose();
        } else if (dragPart != null) {
            ItemStack stack = partStack(menu.gun(), dragPart);
            if (!stack.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.renderItem(stack, mouseX - 8, mouseY - 8);
                g.pose().popPose();
            }
        }
    }

    private @Nullable Row draggedRow() {
        if (dragSlot < 0) {
            return null;
        }
        for (Row row : rows) {
            if (row.slot == dragSlot) {
                return row;
            }
        }
        return null;
    }

    /** De matrix van het wapen op het scherm: midden ({@code cx}, {@code cy}), gedraaid, en {@code zoom} pixels per model-pixel. */
    private Matrix4f view(ItemStack gun, int cx, int cy, float z) {
        float zoom = zoom(gun);
        Vector3f c = centre(gun);
        return new Matrix4f()
                .translate(cx, cy, z)
                .scale(16 * zoom, -16 * zoom, 16 * zoom)
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw))
                .translate(-c.x / 16, -c.y / 16, -c.z / 16);
    }

    /** Het wapen: elk onderdeel dat erin zit op zijn plek, wat ontbreekt als een gestippeld vak ernaast. */
    private void renderGun(GuiGraphics g, int cx, int cy, int mouseX, int mouseY) {
        ItemStack gun = menu.gun();
        zones.clear();
        Matrix4f project = view(gun, cx, cy, 0);
        if (!isGun(gun)) {
            // Geen wapen: alleen de plek van het frame.
            P base = base();
            int[] zone = zone(project, gun, base, exploded(gun, base));
            zones.put(base, zone);
            boolean hot = hotZone(base, mouseX, mouseY);
            dashed(g, zone, hot ? HudStyle.argb(255, HudStyle.CYAN) : HudStyle.argb(120, HudStyle.WHITE));
            small(g, partName(base).getString().toUpperCase(java.util.Locale.ROOT), zone[0] + 2, zone[1] + 2, HudStyle.argb(200, HudStyle.WHITE));
            small(g, "drag a " + partName(base).getString().toLowerCase(java.util.Locale.ROOT) + " here", cx - 36, cy + 40,
                    HudStyle.argb(160, HudStyle.WHITE));
            return;
        }
        // De onderdelen die erin zitten, in 3D.
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.mulPose(view(gun, cx, cy, 160));
        Lighting.setupFor3DItems();
        renderInstalled(pose, g.bufferSource(), gun, dragPart);
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
        // De vakken: waar elk onderdeel ligt (erin) of hoort (ernaast, gestippeld). Eerst allemaal berekenen, dan tekenen.
        for (P part : all) {
            if (!allowed(part, gun)) {
                continue;
            }
            boolean installed = state(gun, part) != null && part != dragPart;
            zones.put(part, zone(project, gun, part, installed ? new Vector3f() : exploded(gun, part)));
        }
        P under = dragSlot < 0 && dragPart == null ? hoveredPart(mouseX, mouseY) : null;
        pose.pushPose();
        pose.translate(0, 0, 200); // boven het wapen
        for (P part : all) {
            int[] zone = zones.get(part);
            if (zone == null) {
                continue;
            }
            boolean installed = state(gun, part) != null && part != dragPart;
            boolean hot = hotZone(part, mouseX, mouseY);
            if (!installed) {
                boolean covered = !reachable(part, gun);
                dashed(g, zone, hot ? HudStyle.argb(255, HudStyle.CYAN) : HudStyle.argb(covered ? 45 : 110, HudStyle.WHITE));
                label(g, partName(part).getString().toUpperCase(java.util.Locale.ROOT), zone,
                        HudStyle.argb(hot ? 255 : covered ? 80 : 170, hot ? HudStyle.CYAN : HudStyle.WHITE));
            } else if (under == part) {
                g.renderOutline(zone[0], zone[1], zone[2] - zone[0], zone[3] - zone[1], HudStyle.argb(160, HudStyle.ORANGE));
            }
        }
        pose.popPose();
    }

    /** De naam bij een vak: erboven, of eronder als dat niet past; nooit buiten de tafel. */
    private void label(GuiGraphics g, String text, int[] zone, int colour) {
        int width = (int) Math.ceil(font.width(text) * 0.75f);
        int x = Mth.clamp(zone[0] + 2, leftPos + VIEW_X + 2, leftPos + VIEW_X + VIEW_W - 2 - width);
        int y = zone[1] - 7 >= topPos + VIEW_Y + 2 ? zone[1] - 7 : zone[3] + 1;
        small(g, text, x, y, colour);
    }

    /** Is dit het vak waar het gesleepte onderdeel heen moet, en is de muis er dichtbij? */
    private boolean hotZone(P part, int mouseX, int mouseY) {
        Row row = draggedRow();
        if (row == null || !row.part || partOf(row.stack) != part) {
            return false;
        }
        ItemStack gun = menu.gun();
        if ((isGun(gun) && (!reachable(part, gun) || !fits(row.stack, gun))) || (!isGun(gun) && part != base())) {
            return false;
        }
        int[] zone = zones.get(part);
        return zone != null && near(zone, mouseX, mouseY);
    }

    /** Het onderdeel onder de muis (het kleinste vak wint; wat je niet kunt pakken, telt niet mee). */
    private @Nullable P hoveredPart(double mouseX, double mouseY) {
        P best = null;
        long bestArea = Long.MAX_VALUE;
        ItemStack gun = menu.gun();
        for (Map.Entry<P, int[]> entry : zones.entrySet()) {
            int[] zone = entry.getValue();
            if (inside(zone, mouseX, mouseY) && reachable(entry.getKey(), gun)) {
                long area = (long) (zone[2] - zone[0]) * (zone[3] - zone[1]);
                if (area < bestArea) {
                    bestArea = area;
                    best = entry.getKey();
                }
            }
        }
        return best;
    }

    /** Het vak van een onderdeel op het scherm: zijn hoeken door de matrix, en daar een rechthoek omheen. */
    private int[] zone(Matrix4f project, ItemStack gun, P part, Vector3f offset) {
        AABB box = bounds(gun, part);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            float x = (float) ((i & 1) == 0 ? box.minX : box.maxX) + offset.x;
            float y = (float) ((i & 2) == 0 ? box.minY : box.maxY) + offset.y;
            float z = (float) ((i & 4) == 0 ? box.minZ : box.maxZ) + offset.z;
            Vector3f p = project.transformPosition(new Vector3f(x / 16, y / 16, z / 16));
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }
        return new int[]{Math.round(minX) - 2, Math.round(minY) - 2, Math.round(maxX) + 2, Math.round(maxY) + 2};
    }

    private static boolean inside(int[] zone, double x, double y) {
        return x >= zone[0] && x <= zone[2] && y >= zone[1] && y <= zone[3];
    }

    private static boolean near(int[] zone, double x, double y) {
        return x >= zone[0] - SNAP && x <= zone[2] + SNAP && y >= zone[1] - SNAP && y <= zone[3] + SNAP;
    }

    private static void dashed(GuiGraphics g, int[] zone, int colour) {
        for (int x = zone[0]; x < zone[2]; x += 4) {
            g.fill(x, zone[1], Math.min(x + 2, zone[2]), zone[1] + 1, colour);
            g.fill(x, zone[3] - 1, Math.min(x + 2, zone[2]), zone[3], colour);
        }
        for (int y = zone[1]; y < zone[3]; y += 4) {
            g.fill(zone[0], y, zone[0] + 1, Math.min(y + 2, zone[3]), colour);
            g.fill(zone[2] - 1, y, zone[2], Math.min(y + 2, zone[3]), colour);
        }
    }

    /** Rechts: per onderdeel wat erin zit en hoe heel het is, en of het wapen klaar is. */
    private void renderSide(GuiGraphics g, int x, int y) {
        ItemStack gun = menu.gun();
        g.drawString(font, gunTitle(gun), x, y - 4, HudStyle.argb(255, HudStyle.ORANGE), false);
        int rowY = y + 12;
        int step = rowStep();
        for (P part : all) {
            PartState state = state(gun, part);
            boolean allowed = allowed(part, gun);
            small(g, partName(part).getString(), x, rowY, HudStyle.argb(allowed ? 220 : 90, HudStyle.WHITE));
            if (state == null) {
                small(g, allowed ? "-" : "", x + 42, rowY, HudStyle.argb(120, HudStyle.WHITE));
            } else if (hasMaterial(part)) {
                small(g, state.material().text().getString(), x + 34, rowY, HudStyle.argb(255, state.material().colour));
                int colour = state.broken() ? HudStyle.RED : state.worn() ? HudStyle.ORANGE : HudStyle.GREEN;
                HudStyle.bar(g, x, rowY + 9, SIDE_W - 16, 3, state.condition() / (float) PartState.NEW, colour);
            } else {
                small(g, "in", x + 42, rowY, HudStyle.argb(255, HudStyle.GREEN));
            }
            rowY += step;
        }
        String ready = readyText(gun);
        int colour = readyColour(gun);
        int wrapY = rowY + 2;
        for (var line : font.split(Component.literal(ready), (int) (SIDE_W / 0.75f))) {
            g.pose().pushPose();
            g.pose().translate(x, wrapY, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, line, 0, 0, HudStyle.argb(220, colour), false);
            g.pose().popPose();
            wrapY += 7;
        }
    }

    protected final void small(GuiGraphics g, String text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, HudStyle.argb(255, HudStyle.CYAN), false);
        small(g, "YOUR PARTS", TRAY_X + 2, TRAY_Y - 9, HudStyle.argb(170, HudStyle.ORANGE));
        small(g, hint(), VIEW_X + 2, VIEW_Y + VIEW_H + 2, HudStyle.argb(140, HudStyle.WHITE));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // Een uitleg bij een onderdeel in het wapen.
        if (dragSlot < 0 && dragPart == null) {
            P part = hoveredPart(mouseX, mouseY);
            PartState state = part == null ? null : state(menu.gun(), part);
            if (state != null && mouseX >= leftPos + VIEW_X && mouseX < leftPos + VIEW_X + VIEW_W) {
                g.renderTooltip(font, partTooltip(menu.gun(), part, state), java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    // ================================================================ Slepen

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Een rij links: beginnen met slepen.
            int x = leftPos + TRAY_X;
            int y = topPos + TRAY_Y;
            if (mouseX >= x && mouseX < x + TRAY_W && mouseY >= y && mouseY < y + TRAY_H) {
                int index = (int) ((mouseY - y - 2 + scroll) / ROW_H);
                if (index >= 0 && index < rows.size()) {
                    dragSlot = rows.get(index).slot;
                    dragX = mouseX;
                    dragY = mouseY;
                    return true;
                }
            }
            // Een onderdeel van het wapen: van het wapen af slepen.
            if (mouseX >= leftPos + VIEW_X && mouseX < leftPos + VIEW_X + VIEW_W && mouseY >= topPos + VIEW_Y
                    && mouseY < topPos + VIEW_Y + VIEW_H) {
                P part = hoveredPart(mouseX, mouseY);
                if (part != null && state(menu.gun(), part) != null) {
                    dragPart = part;
                    dragX = mouseX;
                    dragY = mouseY;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragSlot >= 0) {
            Row row = draggedRow();
            int slot = dragSlot;
            dragSlot = -1;
            if (row != null) {
                boolean onTable = mouseX >= leftPos + VIEW_X && mouseX < leftPos + VIEW_X + VIEW_W
                        && mouseY >= topPos + VIEW_Y && mouseY < topPos + VIEW_Y + VIEW_H;
                ItemStack gun = menu.gun();
                P part = partOf(row.stack);
                if (row.gun && onTable) {
                    click(GunsmithMenu.PUT_GUN + slot);
                } else if (part != null) {
                    int[] zone = zones.get(part);
                    boolean free = isGun(gun) ? state(gun, part) == null && reachable(part, gun) && fits(row.stack, gun) : part == base();
                    if (zone != null && near(zone, mouseX, mouseY) && free) {
                        click(GunsmithMenu.INSTALL + slot);
                    }
                }
            }
            return true;
        }
        if (button == 0 && dragPart != null) {
            P part = dragPart;
            dragPart = null;
            int[] zone = zones.get(part);
            // Ver genoeg van zijn plek losgelaten: eruit.
            if (zone == null || !near(zone, mouseX, mouseY) || Math.abs(mouseX - dragX) + Math.abs(mouseY - dragY) > 30) {
                click(GunsmithMenu.REMOVE + part.ordinal());
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftPos + TRAY_X && mouseX < leftPos + TRAY_X + TRAY_W) {
            scroll = Mth.clamp(scroll - (int) (scrollY * ROW_H), 0, Math.max(0, rows.size() * ROW_H - TRAY_H + 4));
            return true;
        }
        if (mouseX >= leftPos + VIEW_X && mouseX < leftPos + VIEW_X + VIEW_W) {
            yaw += (float) scrollY * 10;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 1 && mouseX >= leftPos + VIEW_X && mouseX < leftPos + VIEW_X + VIEW_W) {
            yaw += (float) dragX * 0.8f;
            pitch = Mth.clamp(pitch + (float) dragY * 0.8f, -60, 60);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
}
