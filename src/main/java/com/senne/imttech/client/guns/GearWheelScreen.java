package com.senne.imttech.client.guns;

import com.mojang.blaze3d.platform.InputConstants;
import com.senne.imttech.client.ImtKeys;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.network.GearActionPayload;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.weapons.GrenadeItem;
import com.senne.imttech.weapons.LaserGunItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Het wiel om snel te trekken (hoofdstuk 52, stap 2; opnieuw gemaakt in hoofdstuk 56, jouw wens: "compacter, sneller te
 * zien wat wat doet, en slepen met je muis in plaats van een knop indrukken").
 *
 * Houd <b>linker Alt</b> in: een ring van tegels komt rond je muis, die in het midden begint. Elke soort heeft zijn vaste
 * plek: bovenaan je pistool uit de holster, rechts je geweren op je slings (rug, borst), links elke soort granaat uit je
 * pouch, onderaan "wegsteken" (een pijltje omlaag op wat je vasthoudt). Op een tegel staat alleen het item, twee keer zo
 * groot, met een getal (schoten, hoeveel granaten). Wat de tegel is en wat er gebeurt lees je in het midden zodra je hem
 * aanwijst. Rechts naast de ring staan twee knoppen voor wat geen quickdraw is: een pouch openen (de skilltree staat er
 * niet, die open je alleen met zijn toets, F6). Schuif je muis naar een tegel en laat Alt los: gekozen. Tik je Alt kort,
 * dan blijft het wiel staan en klik (of sleep) je naar een tegel; Alt, Esc, rechtsklik of een klik in het midden sluit het.
 *
 * Waarom loslaten vroeger niet werkte: {@code KeyMapping.isDown()} zegt altijd "nee" zolang er een scherm open is (de
 * toets telt alleen in het spel zelf), dus het wiel dacht altijd dat je Alt al losgelaten had en wachtte op een klik. Nu
 * luistert het scherm zelf naar het loslaten ({@link #keyReleased}) en vraagt het als vangnet elke tick rechtstreeks aan
 * GLFW of de toets nog ingedrukt is ({@link #wheelKeyDown}).
 */
public class GearWheelScreen extends Screen {
    /** De tegels op de ring: 36 px, met het item er twee keer zo groot (32 px) in. */
    private static final int TILE = 36;
    private static final int ICON = 32;
    /** Zo ver (px) moet je muis van het midden zijn voor je iets aanwijst. */
    private static final int DEAD_ZONE = 20;
    /** Verder dan dit (graden) van de dichtstbijzijnde tegel wijs je niets aan. */
    private static final double MAX_OFF = 45;
    /** Laat je Alt binnen deze tijd (ms) los zonder iets aan te wijzen, dan was het een tik: het wiel blijft staan. */
    private static final long TAP_MS = 200;
    private static final int PANEL = 0x0E1116;
    private static final int GREY = 0x8A9BA0;

    /** Eén keuze: een tegel op de ring (met een hoek) of een knop naast de ring (hoek -1). */
    private static final class Option {
        final ItemStack icon;
        final Component name;
        /** Een of twee korte regels voor in het midden: de munitie of het aantal, en wat er met je hand gebeurt. */
        final List<Component> details;
        /** Het getal op de tegel (schoten, aantal), of null. */
        final @Nullable String badge;
        final int badgeColour;
        final boolean enabled;
        final int action;
        final int arg;
        /** De plek op de ring in graden (0 = boven, met de klok mee), of -1 voor een knop naast de ring. */
        double angle;
        /** Waar hij getekend staat: de linkerbovenhoek en de maat (zie layout). */
        int x;
        int y;
        int w;
        int h;

        Option(ItemStack icon, Component name, List<Component> details, @Nullable String badge, int badgeColour,
               boolean enabled, int action, int arg, double angle) {
            this.icon = icon;
            this.name = name;
            this.details = details;
            this.badge = badge;
            this.badgeColour = badgeColour;
            this.enabled = enabled;
            this.action = action;
            this.arg = arg;
            this.angle = angle;
        }

        boolean onRing() {
            return angle >= 0;
        }
    }

    private final List<Option> options = new ArrayList<>();
    private int cx;
    private int cy;
    private int radius;
    /** Houd je de toets vast (loslaten kiest), of was het een tik (klikken kiest)? */
    private boolean holdMode;
    private long openedAt;
    private double lastMouseX;
    private double lastMouseY;
    /** Waarop je de linkermuisknop indrukte: een keuze, -1 het midden of niets, -2 niet ingedrukt. */
    private int pressedOn = -2;

    public GearWheelScreen() {
        super(Component.translatable("screen.imttech.wheel"));
    }

    @Override
    protected void init() {
        super.init();
        if (openedAt == 0) {
            // Alleen de eerste keer (init komt ook na een andere venstermaat).
            openedAt = Util.getMillis();
            holdMode = wheelKeyDown();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(ModSounds.IMT_MENU_OPEN.get(), 0.5f, 1.2f);
            }
        }
        rebuild();
    }

    // ================================================================ De keuzes

    /** Een lijstje van wat niet null is. */
    private static List<Component> lines(@Nullable Component... parts) {
        List<Component> list = new ArrayList<>();
        for (Component part : parts) {
            if (part != null) {
                list.add(part);
            }
        }
        return list;
    }

    /** Wat er in een wapen zit, voluit: "13+1 rounds" (de Glock), "23 shots" of "no cell" (een lasergeweer). */
    private static Component ammo(ItemStack gun) {
        if (FirearmItem.typeOf(gun) != null) {
            return Component.translatable("screen.imttech.wheel.rounds", ammoBadge(gun));
        }
        if (LaserGunItem.typeOf(gun) != null) {
            return LaserGunItem.cell(gun) < 0 ? Component.translatable("screen.imttech.wheel.no_cell")
                    : Component.translatable("screen.imttech.wheel.shots", LaserGunItem.shotsLeft(gun));
        }
        return Component.empty();
    }

    /** Hetzelfde, kort, voor op de tegel: "13+1", "23" of "--" (geen cel). */
    private static String ammoBadge(ItemStack gun) {
        if (FirearmItem.typeOf(gun) != null) {
            return FirearmItem.roundsInMagazine(gun) + (FirearmItem.chambered(gun) ? "+1" : "");
        }
        if (LaserGunItem.typeOf(gun) != null) {
            return LaserGunItem.cell(gun) < 0 ? "--" : String.valueOf(LaserGunItem.shotsLeft(gun));
        }
        return "";
    }

    /** Leeg (dan staat het getal rood op de tegel)? */
    private static boolean ammoEmpty(ItemStack gun) {
        if (FirearmItem.typeOf(gun) != null) {
            return FirearmItem.roundsInMagazine(gun) == 0 && !FirearmItem.chambered(gun);
        }
        if (LaserGunItem.typeOf(gun) != null) {
            return LaserGunItem.cell(gun) < 0 || LaserGunItem.shotsLeft(gun) == 0;
        }
        return false;
    }

    /** Een tegel voor een wapen dat je kunt trekken: het getal erop; de munitie en je hand in het midden. */
    private static Option gunOption(ItemStack gun, @Nullable Component where, @Nullable Component heldNote, int action, int arg, double angle) {
        Component line = where == null ? ammo(gun) : where;
        return new Option(gun, gun.getHoverName(), lines(line, heldNote), ammoBadge(gun),
                HudStyle.argb(255, ammoEmpty(gun) ? HudStyle.RED : HudStyle.WHITE), true, action, arg, angle);
    }

    /** Een grijze tegel die alleen uitlegt waarom hier niets te trekken is. */
    private static Option disabled(ItemStack icon, Component name, @Nullable Component why, int action, double angle) {
        return new Option(icon, name, lines(why), null, 0, false, action, 0, angle);
    }

    /**
     * Wat er met wat je vasthoudt gebeurt als je dit trekt (zie Gear.intoHand): het gaat in zijn eigen plek als dat kan
     * (ook het vakje dat net leegkomt telt mee), anders valt het op de grond. Null als je hand leeg is.
     */
    private static @Nullable Component heldNote(LocalPlayer player, ItemStack held, @Nullable GearItem.Kind freed, int freedSling) {
        if (held.isEmpty()) {
            return null;
        }
        Component target = Gear.stowTarget(player, held);
        if (target == null) {
            if (Gear.slingAccepts(held)) {
                if (freedSling >= 0) {
                    target = Component.translatable("screen.imttech.wheel.onto_sling");
                }
            } else if (freed != null && freed.accepts(held)) {
                target = Component.translatable("screen.imttech.wheel.into", freed.text());
            }
        }
        return target == null ? Component.translatable("screen.imttech.wheel.drops", held.getHoverName())
                : Component.empty().append(held.getHoverName()).append(" ").append(target);
    }

    /** Deze tegels naast elkaar op de ring rond {@code centre} (graden), {@code step} uit elkaar, samen hoogstens {@code maxArc}. */
    private static void place(List<Option> group, double centre, double step, double maxArc) {
        int n = group.size();
        if (n > 1) {
            step = Math.min(step, maxArc / (n - 1));
        }
        for (int i = 0; i < n; i++) {
            group.get(i).angle = centre - (n - 1) * step / 2 + i * step;
        }
    }

    /** De keuzes opnieuw opbouwen (elke tick: wat je draagt kan veranderen terwijl het wiel open is). */
    private void rebuild() {
        options.clear();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();

        // Bovenaan: het pistool uit je holster.
        List<ItemStack> holster = Gear.contents(player, GearItem.Kind.HOLSTER);
        ItemStack gun = holster.isEmpty() ? ItemStack.EMPTY : holster.getFirst();
        if (gun.isEmpty()) {
            boolean hasHolster = !Gear.get(player, GearItem.Kind.HOLSTER).isEmpty();
            options.add(disabled(new ItemStack(ModItems.HOLSTER.get()),
                    Component.translatable(hasHolster ? "screen.imttech.wheel.holster_empty" : "screen.imttech.wheel.no_holster"),
                    null, Gear.ACTION_DRAW_GUN, 0));
        } else {
            options.add(gunOption(gun, null, heldNote(player, held, GearItem.Kind.HOLSTER, -1), Gear.ACTION_DRAW_GUN, 0, 0));
        }

        // Rechts: je geweren op je slings (op je rug, voor je borst), hoofdstuk 55.
        List<Option> slings = new ArrayList<>();
        for (int i = 0; i < Gear.SLINGS; i++) {
            ItemStack slung = Gear.sling(player, i);
            if (!slung.isEmpty()) {
                Component where = Component.translatable(i == 0 ? "screen.imttech.wheel.on_back" : "screen.imttech.wheel.on_chest", ammo(slung));
                slings.add(gunOption(slung, where, heldNote(player, held, null, i), Gear.ACTION_DRAW_SLING, i, 0));
            }
        }
        if (slings.isEmpty()) {
            slings.add(disabled(new ItemStack(ModItems.LASER_RIFLE.get()), Component.translatable("screen.imttech.wheel.sling_empty"),
                    Component.translatable("screen.imttech.wheel.sling_hint"), Gear.ACTION_DRAW_SLING, 0));
        }
        place(slings, 90, 60, 120);
        options.addAll(slings);

        // Onderaan: wegsteken wat je vasthoudt (het pijltje omlaag).
        Component target = Gear.stowTarget(player, held);
        Component stowLine = held.isEmpty() ? Component.translatable("screen.imttech.wheel.nothing")
                : target == null ? Component.translatable("screen.imttech.wheel.no_fit", held.getHoverName())
                : Component.empty().append(held.getHoverName()).append(" ").append(target);
        options.add(new Option(held, Component.translatable("screen.imttech.wheel.put_away"), lines(stowLine), null, 0,
                target != null, Gear.ACTION_HOLSTER, 0, 180));

        // Links: elke soort granaat uit je pouch, met hoeveel.
        Map<GrenadeItem.Kind, Integer> counts = new EnumMap<>(GrenadeItem.Kind.class);
        Map<GrenadeItem.Kind, ItemStack> icons = new EnumMap<>(GrenadeItem.Kind.class);
        for (ItemStack stack : Gear.contents(player, GearItem.Kind.GRENADE_POUCH)) {
            if (stack.getItem() instanceof GrenadeItem grenade) {
                counts.merge(grenade.kind(), stack.getCount(), Integer::sum);
                icons.putIfAbsent(grenade.kind(), stack);
            }
        }
        List<Option> grenades = new ArrayList<>();
        for (Map.Entry<GrenadeItem.Kind, Integer> entry : counts.entrySet()) {
            ItemStack icon = icons.get(entry.getKey());
            grenades.add(new Option(icon, icon.getHoverName(),
                    lines(Component.translatable("screen.imttech.wheel.count", entry.getValue()),
                            heldNote(player, held, GearItem.Kind.GRENADE_POUCH, -1)),
                    String.valueOf(entry.getValue()), HudStyle.argb(255, HudStyle.WHITE), true, Gear.ACTION_DRAW_GRENADE,
                    entry.getKey().ordinal(), 0));
        }
        if (grenades.isEmpty()) {
            boolean hasPouch = !Gear.get(player, GearItem.Kind.GRENADE_POUCH).isEmpty();
            grenades.add(disabled(new ItemStack(ModItems.GRENADE_POUCH.get()),
                    Component.translatable(hasPouch ? "screen.imttech.wheel.pouch_empty" : "screen.imttech.wheel.no_pouch"),
                    null, Gear.ACTION_DRAW_GRENADE, 0));
        }
        place(grenades, 270, 30, 90);
        options.addAll(grenades);

        // Ernaast: elke pouch die je draagt openen (hoofdstuk 54). De skilltree staat hier niet: alleen op zijn toets (F6).
        for (GearItem.Kind kind : new GearItem.Kind[] {GearItem.Kind.MAG_POUCH, GearItem.Kind.GRENADE_POUCH}) {
            ItemStack worn = Gear.get(player, kind);
            if (!worn.isEmpty()) {
                int filled = GearItem.filled(worn);
                Component label = Component.translatable(
                        kind == GearItem.Kind.MAG_POUCH ? "screen.imttech.wheel.mags" : "screen.imttech.wheel.grenades");
                options.add(new Option(worn, label,
                        lines(Component.translatable("screen.imttech.wheel.open", kind.text()),
                                Component.translatable("screen.imttech.wheel.filled", filled, kind.size())),
                        filled + "/" + kind.size(), HudStyle.argb(255, HudStyle.WHITE), true, Gear.ACTION_OPEN, kind.ordinal(), -1));
            }
        }
        layout();
    }

    /** Waar alles staat: de ring zo groot als het scherm toelaat (en groter als tegels elkaar anders raken), de knoppen eronder. */
    private void layout() {
        cx = width / 2;
        cy = height / 2;
        // Boven de werkbalk blijven, en rechts ruimte laten voor de knoppen.
        int fit = Math.min(width / 2 - TILE - 110, height / 2 - TILE - 24);
        radius = Mth.clamp(fit, 64, 96);
        double gap = 360;
        for (Option a : options) {
            for (Option b : options) {
                if (a != b && a.onRing() && b.onRing()) {
                    gap = Math.min(gap, Math.abs(Mth.wrapDegrees(a.angle - b.angle)));
                }
            }
        }
        if (gap < 180) {
            // Twee buren moeten minstens een tegel plus twee pixels uit elkaar (de koorde van de cirkel).
            int needed = (int) Math.ceil((TILE + 2) / 2.0 / Math.sin(Math.toRadians(gap / 2)));
            radius = Math.max(radius, Math.min(needed, radius + 24));
        }
        for (Option option : options) {
            if (option.onRing()) {
                double a = Math.toRadians(option.angle);
                option.x = (int) Math.round(cx + Math.sin(a) * radius) - TILE / 2;
                option.y = (int) Math.round(cy - Math.cos(a) * radius) - TILE / 2;
                option.w = TILE;
                option.h = TILE;
            }
        }
        // De knoppen: onder elkaar, rechts naast de ring (eronder past niet op een klein scherm, boven de werkbalk).
        int count = 0;
        for (Option option : options) {
            if (!option.onRing()) {
                option.w = 22 + font.width(option.name) + (option.badge == null ? 0 : font.width(option.badge) + 4) + 6;
                option.h = 20;
                count++;
            }
        }
        int x = cx + radius + TILE / 2 + 10;
        int y = cy - (count * 20 + Math.max(0, count - 1) * 4) / 2;
        for (Option option : options) {
            if (!option.onRing()) {
                option.x = x;
                option.y = y;
                y += option.h + 4;
            }
        }
    }

    // ================================================================ Tekenen

    /** Geen wazige achtergrond van Minecraft: alleen onze eigen lichte verduistering (zie HudStyle.dim). */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        HudStyle.dim(g, width, height);
        int hot = hovered(mouseX, mouseY);
        Option hotOption = hot < 0 ? null : options.get(hot);
        // Het wijzertje: een stippellijn van het midden naar de tegel die je aanwijst.
        if (hotOption != null && hotOption.onRing()) {
            double a = Math.toRadians(hotOption.angle);
            int colour = HudStyle.argb(hotOption.enabled ? 200 : 90, hotOption.enabled ? HudStyle.CYAN : GREY);
            for (int d = DEAD_ZONE + 8; d < radius - TILE / 2 - 4; d += 4) {
                int x = (int) Math.round(cx + Math.sin(a) * d);
                int y = (int) Math.round(cy - Math.cos(a) * d);
                g.fill(x - 1, y - 1, x + 1, y + 1, colour);
            }
        }
        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            if (option.onRing()) {
                tile(g, option, i == hot);
            } else {
                button(g, option, i == hot);
            }
        }
        // Het midden: wat je aanwijst (de naam, de munitie, wat er met je hand gebeurt), anders de titel.
        int inner = 2 * (radius - TILE / 2) - 10;
        if (hotOption != null) {
            int y = cy - 5 - hotOption.details.size() * 4;
            fitText(g, hotOption.name, cx, y, inner, 1.0f, HudStyle.argb(255, hotOption.enabled ? HudStyle.CYAN : GREY));
            y += 11;
            for (Component detail : hotOption.details) {
                fitText(g, detail, cx, y, inner, 0.75f, HudStyle.argb(230, hotOption.enabled ? HudStyle.WHITE : GREY));
                y += 8;
            }
        } else {
            g.drawCenteredString(font, title, cx, cy - 4, HudStyle.argb(220, HudStyle.ORANGE));
        }
        // De hint boven het wiel: loslaten, of klikken.
        Component hint = Component.translatable(holdMode ? "screen.imttech.wheel.release" : "screen.imttech.wheel.click",
                ImtKeys.GEAR_WHEEL.getTranslatedKeyMessage());
        fitText(g, hint, cx, Math.max(4, cy - radius - TILE / 2 - 14), width - 20, 0.75f, HudStyle.argb(170, HudStyle.WHITE));
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Een tegel op de ring: een donker vierkant met het item er twee keer zo groot in, en het getal rechtsonder. */
    private void tile(GuiGraphics g, Option o, boolean hot) {
        int x = o.x;
        int y = o.y;
        int s = TILE;
        boolean lit = hot && o.enabled;
        int fill = HudStyle.argb(lit ? 240 : 205, PANEL);
        // Zonder de vier hoekpixels: dat oogt net wat ronder.
        g.fill(x + 1, y, x + s - 1, y + s, fill);
        g.fill(x, y + 1, x + 1, y + s - 1, fill);
        g.fill(x + s - 1, y + 1, x + s, y + s - 1, fill);
        frame(g, x, y, s, !o.enabled ? HudStyle.argb(110, GREY) : lit ? HudStyle.argb(255, HudStyle.CYAN) : HudStyle.argb(190, HudStyle.ORANGE));
        if (lit) {
            frame(g, x - 1, y - 1, s + 2, HudStyle.argb(110, HudStyle.CYAN));
        }
        if (o.icon.isEmpty()) {
            // Niets in je hand (de tegel "wegsteken"): alleen een groot pijltje omlaag.
            arrow(g, x + s / 2 - 8, y + s / 2 - 8, 2, HudStyle.argb(120, GREY));
        } else {
            g.pose().pushPose();
            g.pose().translate(x + (s - ICON) / 2f, y + (s - ICON) / 2f, 0);
            g.pose().scale(ICON / 16f, ICON / 16f, 1);
            g.renderItem(o.icon, 0, 0);
            g.pose().popPose();
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 200); // boven het item (dat op diepte 150 staat)
        if (!o.enabled) {
            g.fill(x + 1, y + 1, x + s - 1, y + s - 1, HudStyle.argb(80, PANEL));
        }
        if (o.action == Gear.ACTION_HOLSTER && !o.icon.isEmpty()) {
            // Wegsteken: een pijltje omlaag op het item.
            g.fill(x + s - 12, y + s - 12, x + s - 1, y + s - 1, HudStyle.argb(220, PANEL));
            arrow(g, x + s - 11, y + s - 11, 1, HudStyle.argb(255, o.enabled ? HudStyle.ORANGE : GREY));
        } else if (o.badge != null) {
            g.drawString(font, o.badge, x + s - 2 - font.width(o.badge), y + s - 9, o.badgeColour, true);
        }
        g.pose().popPose();
    }

    /** Een knop naast de ring: het item klein, de naam, en het getal erachter. */
    private void button(GuiGraphics g, Option o, boolean hot) {
        g.fill(o.x, o.y, o.x + o.w, o.y + o.h, HudStyle.argb(hot ? 240 : 205, PANEL));
        g.renderOutline(o.x, o.y, o.w, o.h, hot ? HudStyle.argb(255, HudStyle.CYAN) : HudStyle.argb(190, HudStyle.ORANGE));
        g.renderItem(o.icon, o.x + 2, o.y + 2);
        int tx = o.x + 22;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.drawString(font, o.name, tx, o.y + 6, HudStyle.argb(255, hot ? HudStyle.CYAN : HudStyle.WHITE), false);
        if (o.badge != null) {
            g.drawString(font, o.badge, tx + font.width(o.name) + 4, o.y + 6, o.badgeColour, false);
        }
        g.pose().popPose();
    }

    /** Een rand van 1 px zonder de hoekpixels. */
    private static void frame(GuiGraphics g, int x, int y, int s, int colour) {
        g.fill(x + 1, y, x + s - 1, y + 1, colour);
        g.fill(x + 1, y + s - 1, x + s - 1, y + s, colour);
        g.fill(x, y + 1, x + 1, y + s - 1, colour);
        g.fill(x + s - 1, y + 1, x + s, y + s - 1, colour);
    }

    /** Een pijltje omlaag van 8 bij 8 (keer {@code scale}). */
    private static void arrow(GuiGraphics g, int x, int y, int scale, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.fill(3, 0, 5, 4, colour); // de steel
        g.fill(0, 4, 8, 5, colour); // de punt: 8, 6, 4 en 2 breed
        g.fill(1, 5, 7, 6, colour);
        g.fill(2, 6, 6, 7, colour);
        g.fill(3, 7, 5, 8, colour);
        g.pose().popPose();
    }

    /** Gecentreerde tekst op schaal {@code scale}, kleiner gemaakt als hij breder is dan {@code maxWidth}. */
    private void fitText(GuiGraphics g, Component text, int centerX, int y, int maxWidth, float scale, int colour) {
        int w = font.width(text);
        if (w > 0 && w * scale > maxWidth) {
            scale = (float) maxWidth / w;
        }
        g.pose().pushPose();
        g.pose().translate(centerX, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, -w / 2, 0, colour, true);
        g.pose().popPose();
    }

    // ================================================================ Aanwijzen en kiezen

    /** Welke keuze je aanwijst: een knop als je muis erop staat, anders de tegel in die richting vanuit het midden; of -1. */
    private int hovered(double mouseX, double mouseY) {
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            if (!o.onRing() && mouseX >= o.x && mouseX < o.x + o.w && mouseY >= o.y && mouseY < o.y + o.h) {
                return i;
            }
        }
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
            return -1;
        }
        double angle = Math.toDegrees(Math.atan2(dx, -dy)); // 0 = boven, met de klok mee
        int best = -1;
        double bestOff = MAX_OFF;
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            if (o.onRing()) {
                double off = Math.abs(Mth.wrapDegrees(angle - o.angle));
                if (off < bestOff) {
                    bestOff = off;
                    best = i;
                }
            }
        }
        return best;
    }

    private void choose(int index) {
        if (index < 0 || index >= options.size()) {
            return;
        }
        Option option = options.get(index);
        if (!option.enabled) {
            return;
        }
        PacketDistributor.sendToServer(new GearActionPayload(option.action, option.arg));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.level().playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), ModSounds.IMT_SELECT.get(),
                    SoundSource.PLAYERS, 0.6f, 1.0f, false);
        }
    }

    /**
     * Is de wieltoets nu echt ingedrukt? Rechtstreeks aan GLFW gevraagd, want {@code KeyMapping.isDown()} zegt altijd nee
     * zolang een scherm open is. (In de showcase, die toetsen nadoet zonder GLFW, is dit altijd nee: dan kiest het script.)
     */
    private static boolean wheelKeyDown() {
        InputConstants.Key key = ImtKeys.GEAR_WHEEL.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> false;
        };
    }

    /**
     * De wieltoets is losgelaten: wat je aanwijst is gekozen. Wees je nog niets aan, dan was een korte druk een tik (het
     * wiel blijft staan en je klikt), en een lange druk gewoon annuleren.
     */
    private void wheelKeyUp() {
        if (!holdMode) {
            return;
        }
        int index = hovered(lastMouseX, lastMouseY);
        if (index >= 0) {
            choose(index);
            onClose();
        } else if (Util.getMillis() - openedAt < TAP_MS) {
            holdMode = false;
        } else {
            onClose();
        }
    }

    @Override
    public void tick() {
        super.tick();
        rebuild();
        // Vangnet als het loslaten niet als toetsgebeurtenis binnenkomt.
        if (holdMode && !wheelKeyDown()) {
            wheelKeyUp();
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ImtKeys.GEAR_WHEEL.matches(keyCode, scanCode)) {
            wheelKeyUp();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ImtKeys.GEAR_WHEEL.matches(keyCode, scanCode)) {
            // Na een tik kiest de toets nog een keer wat je aanwijst, of sluit hij het wiel. Vasthouden geeft herhaalde
            // drukken; die tellen niet.
            if (!holdMode) {
                int index = hovered(lastMouseX, lastMouseY);
                if (index >= 0) {
                    choose(index);
                }
                onClose();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Indrukken kiest nog niets: zo kun je vanuit het midden naar een tegel slepen en daar loslaten. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            onClose();
            return true;
        }
        if (button == 0) {
            pressedOn = hovered(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ImtKeys.GEAR_WHEEL.matchesMouse(button)) {
            wheelKeyUp();
            return true;
        }
        if (button == 0) {
            int index = hovered(mouseX, mouseY);
            if (index >= 0) {
                if (options.get(index).enabled) {
                    choose(index);
                    onClose();
                }
            } else if (pressedOn == -1) {
                onClose(); // een klik in het midden (zonder te slepen): sluiten
            }
            pressedOn = -2;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ================================================================ Voor de showcase en de screenshots

    /** Deze keuze (op volgorde: pistool, slings, wegsteken, granaten, knoppen) kiezen alsof je hem aanwees. */
    public void chooseIndex(int index) {
        choose(index);
        onClose();
    }

    /** De keuze met deze actie (Gear.ACTION_...) kiezen. */
    public void chooseAction(int action) {
        chooseAction(action, -1);
    }

    /** Hetzelfde, met het extra getal erbij (welk sling-vakje, welke granaatsoort); -1 = het eerste dat past. */
    public void chooseAction(int action, int arg) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).action == action && (arg < 0 || options.get(i).arg == arg)) {
                chooseIndex(i);
                return;
            }
        }
        onClose();
    }

    /** Is het wiel open? */
    public static boolean isOpen() {
        return Minecraft.getInstance().screen instanceof GearWheelScreen;
    }

    public static @Nullable GearWheelScreen current() {
        return Minecraft.getInstance().screen instanceof GearWheelScreen wheel ? wheel : null;
    }
}
