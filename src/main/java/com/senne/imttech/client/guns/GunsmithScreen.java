package com.senne.imttech.client.guns;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunPartItem;
import com.senne.imttech.guns.GunParts;
import com.senne.imttech.guns.GunsmithMenu;
import com.senne.imttech.guns.PartState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Het scherm van de Gunsmith Table (hoofdstuk 52). Jouw wens: "niet met GUI vakjes maar echt dat je een tafel hebt met
 * de onderdelen die je kunt gebruiken en in je inventory hebt; dan versleep je de onderdelen naar waar het moet komen
 * en dan heeft dat wel snap in place".
 *
 * <pre>
 *  YOUR PARTS        |         het pistool, groot, van opzij          |  GLOCK 19
 *  [slede]  Steel    |   (wat er niet in zit, ligt er los omheen:     |  Frame   Steel  ####  [x]
 *  [veren]  Titanium |    een gestippeld vak met de naam)             |  Slide   -----
 *  [Glock]  ...      |                                                 |  ...
 *                    |                                                 |  [ Take the pistol ]
 * </pre>
 * Links: alles uit je inventaris dat hier hoort (onderdelen en pistolen); andere spullen zie je niet. Sleep een
 * onderdeel naar zijn plek: kom je in de buurt, dan licht de plek op en klikt het vast als je loslaat (de server zet
 * het erin, zie GunsmithTableBlockEntity). Sleep een onderdeel van het pistool af (of klik op zijn [x] rechts) en het
 * komt terug in je inventaris. Een pistool uit je inventaris sleep je op de tafel; "Take the pistol" neemt het mee.
 *
 * Hoofdstuk 54 (jouw notitie: "je kunt niet goed aan elk onderdeel"): de loop, de veren en de slagpin zitten ONDER de
 * slede. Zit de slede erop, dan kun je er niet bij (niet slepen, geen [x]): eerst de slede eraf, zoals bij een echte
 * Glock. De losse onderdelen liggen verder uit elkaar, elk in zijn eigen vak, en hun naam valt nooit buiten de tafel.
 */
public class GunsmithScreen extends AbstractContainerScreen<GunsmithMenu> {
    private static final int FRAME = HudStyle.argb(255, 0x465460);
    private static final int PANEL = HudStyle.argb(245, 0x0E1116);
    private static final int TRAY_BG = HudStyle.argb(255, 0x161A20);
    private static final int ROW_H = 20;
    private static final int TRAY_X = 6, TRAY_Y = 26, TRAY_W = 74, TRAY_H = 152;
    private static final int VIEW_X = 84, VIEW_Y = 18, VIEW_W = 150, VIEW_H = 160;
    private static final int SIDE_X = 238, SIDE_W = 84;
    /** Zo groot is het pistool: pixels op je scherm per pixel van het model (sinds hoofdstuk 54 iets kleiner: de losse onderdelen liggen verder uit elkaar). */
    private static final float ZOOM = 6.6f;
    /** Tot zo ver (pixels) van zijn plek klikt een onderdeel vast. */
    private static final int SNAP = 16;

    /** Eén rij in de lijst links: een vakje van je inventaris. */
    private record Row(int slot, ItemStack stack, @Nullable GunPart part, boolean gun) {}

    private final List<Row> rows = new ArrayList<>();
    private int scroll;
    /** Wat je sleept: een vakje van je inventaris (-1 = niets), of een onderdeel van het pistool (null = niets). */
    private int dragSlot = -1;
    private @Nullable GunPart dragPart;
    private double dragX, dragY;
    private float yaw = 28;
    private float pitch = 12;
    private Button take;
    private final Map<GunPart, Button> removeButtons = new EnumMap<>(GunPart.class);
    /** Waar elk onderdeel op het scherm ligt (deze frame): zijn vak. */
    private final Map<GunPart, int[]> zones = new EnumMap<>(GunPart.class);

    public GunsmithScreen(GunsmithMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 328;
        imageHeight = 186;
    }

    @Override
    protected void init() {
        super.init();
        inventoryLabelY = -100;
        take = addRenderableWidget(Button.builder(Component.translatable("screen.imttech.gunsmith.take"),
                b -> click(GunsmithMenu.TAKE_GUN)).bounds(leftPos + SIDE_X, topPos + imageHeight - 26, SIDE_W, 18).build());
        int y = topPos + 30;
        for (GunPart part : GunPart.values()) {
            Button button = addRenderableWidget(Button.builder(Component.literal("x"),
                    b -> click(GunsmithMenu.REMOVE + part.ordinal())).bounds(leftPos + SIDE_X + SIDE_W - 12, y, 12, 12).build());
            removeButtons.put(part, button);
            y += 20;
        }
    }

    private void click(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        rebuildRows();
        ItemStack gun = menu.gun();
        GunParts parts = FirearmItem.parts(gun);
        take.active = !gun.isEmpty();
        for (GunPart part : GunPart.values()) {
            Button button = removeButtons.get(part);
            boolean covered = underSlide(part, parts);
            button.visible = parts.has(part);
            button.active = parts.has(part) && !covered && (part != GunPart.FRAME || parts.parts().size() == 1);
            button.setTooltip(covered ? Tooltip.create(Component.translatable("screen.imttech.gunsmith.under_slide")) : null);
        }
    }

    /**
     * Zit dit onderdeel onder de slede (de loop, de veren en de slagpin) terwijl de slede erop zit? Dan kun je er niet bij:
     * niet om het eruit te halen, en niet om het erin te leggen. (Sleep je de slede net weg, dan wel.)
     */
    private boolean underSlide(GunPart part, GunParts parts) {
        return (part == GunPart.BARREL || part == GunPart.SPRINGS || part == GunPart.STRIKER)
                && parts.has(GunPart.SLIDE) && dragPart != GunPart.SLIDE;
    }

    /** De lijst links: onderdelen en pistolen uit je inventaris (niets anders). */
    private void rebuildRows() {
        rows.clear();
        Inventory inventory = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getInventory();
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            GunPart part = GunPartItem.partOf(stack);
            boolean gun = FirearmItem.typeOf(stack) != null;
            if (part != null || gun) {
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
            String name = row.gun ? row.stack.getHoverName().getString()
                    : GunPartItem.materialOf(row.stack).text().getString();
            String sub = row.gun ? (FirearmItem.parts(row.stack).complete() ? "complete" : "incomplete")
                    : row.part.text().getString() + (GunPartItem.conditionOf(row.stack) < PartState.NEW
                    ? " " + GunPartItem.conditionOf(row.stack) + "%" : "");
            small(g, name, x + TRAY_X + 22, rowY + 3, HudStyle.argb(255, HudStyle.WHITE));
            small(g, sub, x + TRAY_X + 22, rowY + 11, HudStyle.argb(200, row.gun ? HudStyle.ORANGE : HudStyle.CYAN));
            rowY += ROW_H;
        }
        if (rows.isEmpty()) {
            small(g, "No parts or pistols", x + TRAY_X + 4, y + TRAY_Y + 6, HudStyle.argb(160, HudStyle.WHITE));
            small(g, "in your inventory.", x + TRAY_X + 4, y + TRAY_Y + 14, HudStyle.argb(160, HudStyle.WHITE));
        }
        g.disableScissor();
        // Midden: de tafel met het pistool.
        g.fill(x + VIEW_X, y + VIEW_Y, x + VIEW_X + VIEW_W, y + VIEW_Y + VIEW_H, HudStyle.argb(255, 0x0A0D12));
        g.renderOutline(x + VIEW_X, y + VIEW_Y, VIEW_W, VIEW_H, dragSlot >= 0 && draggedRow() != null && draggedRow().gun
                ? HudStyle.argb(255, HudStyle.CYAN) : FRAME);
        renderGun(g, x + VIEW_X + VIEW_W / 2, y + VIEW_Y + VIEW_H / 2 + 6, mouseX, mouseY);
        // Rechts: de lijst van onderdelen.
        renderSide(g, x + SIDE_X, y + 18);
        // Wat je sleept, aan je muis.
        if (dragSlot >= 0) {
            Row row = draggedRow();
            if (row != null) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.renderItem(row.stack, mouseX - 8, mouseY - 8);
                g.pose().popPose();
            }
        } else if (dragPart != null) {
            ItemStack gun = menu.gun();
            PartState state = FirearmItem.parts(gun).get(dragPart);
            if (state != null) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.renderItem(GunPartItem.of(dragPart, state.material(), state.condition()), mouseX - 8, mouseY - 8);
                g.pose().popPose();
            }
        }
    }

    private @Nullable Row draggedRow() {
        for (Row row : rows) {
            if (row.slot == dragSlot) {
                return row;
            }
        }
        return null;
    }

    /** De matrix van het pistool op het scherm: midden ({@code cx}, {@code cy}), gedraaid, en {@code ZOOM} pixels per model-pixel. */
    private Matrix4f view(int cx, int cy, float z) {
        return new Matrix4f()
                .translate(cx, cy, z)
                .scale(16 * ZOOM, -16 * ZOOM, 16 * ZOOM)
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw))
                .translate(-0.5f, -8.0f / 16, -7.6f / 16);
    }

    /** Het pistool: elk onderdeel dat erin zit op zijn plek, wat ontbreekt als een gestippeld vak ernaast. */
    private void renderGun(GuiGraphics g, int cx, int cy, int mouseX, int mouseY) {
        ItemStack gun = menu.gun();
        FirearmType type = FirearmItem.typeOf(gun);
        GunParts parts = FirearmItem.parts(gun);
        zones.clear();
        Matrix4f project = view(cx, cy, 0);
        if (type == null) {
            // Geen pistool: alleen de plek van het frame.
            int[] zone = zone(project, GunPart.FRAME, FirearmRig.exploded(GunPart.FRAME));
            zones.put(GunPart.FRAME, zone);
            boolean hot = hotZone(GunPart.FRAME, mouseX, mouseY);
            dashed(g, zone, hot ? HudStyle.argb(255, HudStyle.CYAN) : HudStyle.argb(120, HudStyle.WHITE));
            small(g, "FRAME", zone[0] + 2, zone[1] + 2, HudStyle.argb(200, HudStyle.WHITE));
            small(g, "drag a frame here", cx - 36, cy + 40, HudStyle.argb(160, HudStyle.WHITE));
            return;
        }
        // De onderdelen die erin zitten, in 3D.
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.mulPose(view(cx, cy, 160));
        Lighting.setupFor3DItems();
        var buffers = g.bufferSource();
        for (GunPart part : GunPart.values()) {
            PartState state = parts.get(part);
            if (state == null || part == dragPart) {
                continue;
            }
            pose.pushPose();
            FirearmModels.render(pose, buffers, FirearmModels.part(type, part, state.material()), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        if (FirearmItem.hasMagazine(gun)) {
            FirearmModels.render(pose, buffers, FirearmModels.magazine(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
        // De vakken: waar elk onderdeel ligt (erin) of hoort (ernaast, gestippeld). Eerst allemaal berekenen, dan tekenen.
        for (GunPart part : GunPart.values()) {
            boolean installed = parts.has(part) && part != dragPart;
            zones.put(part, zone(project, part, installed ? new Vector3f() : FirearmRig.exploded(part)));
        }
        GunPart under = dragSlot < 0 && dragPart == null ? hoveredPart(mouseX, mouseY) : null;
        pose.pushPose();
        pose.translate(0, 0, 200); // boven het pistool
        for (GunPart part : GunPart.values()) {
            boolean installed = parts.has(part) && part != dragPart;
            int[] zone = zones.get(part);
            boolean hot = hotZone(part, mouseX, mouseY);
            if (!installed) {
                boolean covered = underSlide(part, parts);
                dashed(g, zone, hot ? HudStyle.argb(255, HudStyle.CYAN) : HudStyle.argb(covered ? 45 : 110, HudStyle.WHITE));
                label(g, part.text().getString().toUpperCase(java.util.Locale.ROOT), zone,
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
    private boolean hotZone(GunPart part, int mouseX, int mouseY) {
        if (dragSlot < 0) {
            return false;
        }
        Row row = draggedRow();
        if (row == null || row.part != part || underSlide(part, FirearmItem.parts(menu.gun()))) {
            return false;
        }
        int[] zone = zones.get(part);
        return zone != null && near(zone, mouseX, mouseY);
    }

    /** Het onderdeel onder de muis (het kleinste vak wint; wat onder de slede zit, telt niet mee). */
    private @Nullable GunPart hoveredPart(double mouseX, double mouseY) {
        GunPart best = null;
        long bestArea = Long.MAX_VALUE;
        GunParts parts = FirearmItem.parts(menu.gun());
        for (Map.Entry<GunPart, int[]> entry : zones.entrySet()) {
            int[] zone = entry.getValue();
            if (inside(zone, mouseX, mouseY) && !underSlide(entry.getKey(), parts)) {
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
    private static int[] zone(Matrix4f project, GunPart part, Vector3f offset) {
        AABB box = FirearmRig.bounds(part);
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

    /** Rechts: per onderdeel wat erin zit en hoe heel het is, en of het pistool klaar is. */
    private void renderSide(GuiGraphics g, int x, int y) {
        ItemStack gun = menu.gun();
        GunParts parts = FirearmItem.parts(gun);
        FirearmType type = FirearmItem.typeOf(gun);
        g.drawString(font, type == null ? Component.translatable("screen.imttech.gunsmith.empty") : type.text(), x, y - 4,
                HudStyle.argb(255, HudStyle.ORANGE), false);
        int rowY = y + 12;
        for (GunPart part : GunPart.values()) {
            PartState state = parts.get(part);
            small(g, part.text().getString(), x, rowY, HudStyle.argb(220, HudStyle.WHITE));
            if (state == null) {
                small(g, "-", x + 42, rowY, HudStyle.argb(120, HudStyle.WHITE));
            } else {
                small(g, state.material().text().getString(), x + 34, rowY, HudStyle.argb(255, state.material().colour));
                int colour = state.broken() ? HudStyle.RED : state.worn() ? HudStyle.ORANGE : HudStyle.GREEN;
                HudStyle.bar(g, x, rowY + 9, SIDE_W - 16, 3, state.condition() / (float) PartState.NEW, colour);
            }
            rowY += 20;
        }
        String ready;
        int colour;
        if (type == null) {
            ready = "Drag a frame onto the table to start.";
            colour = HudStyle.WHITE;
        } else if (!parts.complete()) {
            ready = "Missing: " + parts.missing().size() + " part(s).";
            colour = HudStyle.ORANGE;
        } else if (!parts.broken().isEmpty()) {
            ready = "Broken: " + parts.broken().getFirst().text().getString() + ". Replace it.";
            colour = HudStyle.RED;
        } else {
            ready = FirearmItem.hasMagazine(gun) ? "Ready, with a magazine." : "Ready. Needs a magazine.";
            colour = HudStyle.GREEN;
        }
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

    private void small(GuiGraphics g, String text, int x, int y, int colour) {
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
        small(g, "turn: scroll on the table   drag parts onto the pistol, or off it", VIEW_X + 2, VIEW_Y + VIEW_H + 2,
                HudStyle.argb(140, HudStyle.WHITE));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // Een uitleg bij een onderdeel in het pistool.
        if (dragSlot < 0 && dragPart == null) {
            GunPart part = hoveredPart(mouseX, mouseY);
            PartState state = part == null ? null : FirearmItem.parts(menu.gun()).get(part);
            if (state != null && mouseX >= leftPos + VIEW_X && mouseX < leftPos + VIEW_X + VIEW_W) {
                g.renderTooltip(font, List.of(
                        Component.translatable("item.imttech.gun_part", state.material().text(), part.text()),
                        Component.translatable("tooltip.imttech.gun_part.condition", state.condition()),
                        Component.translatable("screen.imttech.gunsmith.drag_off")), java.util.Optional.empty(), mouseX, mouseY);
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
            // Een onderdeel van het pistool: van het pistool af slepen.
            if (mouseX >= leftPos + VIEW_X && mouseX < leftPos + VIEW_X + VIEW_W && mouseY >= topPos + VIEW_Y
                    && mouseY < topPos + VIEW_Y + VIEW_H) {
                GunPart part = hoveredPart(mouseX, mouseY);
                if (part != null && FirearmItem.parts(menu.gun()).has(part)) {
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
                if (row.gun && onTable) {
                    click(GunsmithMenu.PUT_GUN + slot);
                } else if (row.part != null) {
                    int[] zone = zones.get(row.part);
                    GunParts parts = FirearmItem.parts(menu.gun());
                    if (zone != null && near(zone, mouseX, mouseY) && !parts.has(row.part) && !underSlide(row.part, parts)) {
                        click(GunsmithMenu.INSTALL + slot);
                    }
                }
            }
            return true;
        }
        if (button == 0 && dragPart != null) {
            GunPart part = dragPart;
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
