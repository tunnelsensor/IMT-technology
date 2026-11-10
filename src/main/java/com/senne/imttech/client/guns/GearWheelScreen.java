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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
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
 * aanwijst. Rechts naast de ring staan knoppen voor wat geen quickdraw is: een pouch openen, je rugzak openritsen
 * (hoofdstuk 72) (de skilltree staat er
 * niet, die open je alleen met zijn toets, F6). Schuif je muis naar een tegel en laat Alt los: gekozen (een tegel
 * aanklikken kan ook; rechtsklik of Esc annuleert). Het wiel staat er <b>alleen zolang je Alt vasthoudt</b> (jouw keuze:
 * niet aan en uit te zetten); laat je los zonder iets aan te wijzen, dan gaat het gewoon dicht.
 *
 * Ondertussen <b>loop je door</b> (jouw wens: dekking zoeken terwijl je je wapen pakt): lopen, springen, sluipen en
 * sprinten werken met het wiel open; alleen de camera draait niet mee, want je muis kiest. Dat gaat niet vanzelf: zodra
 * een scherm open is, zet Minecraft alle toetsen op "los" en zet het nieuw ingedrukte toetsen niet meer "aan" (alleen
 * loslaten komt nog door), en {@code KeyMapping.isDown()} van de wieltoets zegt altijd nee (die toets telt alleen in het
 * spel zelf). Daarom luistert dit scherm zelf naar de toetsen ({@link #keyPressed}, {@link #keyReleased}) en vraagt het
 * als vangnet elke tick rechtstreeks aan GLFW wat er echt ingedrukt is ({@link #physicallyDown}): voor de wieltoets
 * (loslaten = kiezen) en voor de bewegingstoetsen ({@link #syncMovement}). En NeoForge laat de bewegingstoetsen van
 * Minecraft zelf ook alleen in het spel tellen, dus zolang het wiel open is gelden die overal ({@link #letMovementThrough}).
 */
public class GearWheelScreen extends Screen {
    /** Een knop naast de ring (hoofdstuk 72: compact, een vierkantje met het item en het getal). */
    private static final int BUTTON = 24;
    /** De tegels op de ring: 36 px, met het item er twee keer zo groot (32 px) in. */
    private static final int TILE = 36;
    private static final int ICON = 32;
    /** Zo ver (px) moet je muis van het midden zijn voor je iets aanwijst. */
    private static final int DEAD_ZONE = 20;
    /** Verder dan dit (graden) van de dichtstbijzijnde tegel wijs je niets aan. */
    private static final double MAX_OFF = 45;
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
    /** Al geopend (init komt ook na een andere venstermaat)? */
    private boolean opened;
    private double lastMouseX;
    private double lastMouseY;
    /** De bewegingstoetsen die wij "aan" zetten, zodat we alleen die ook weer loslaten (zie syncMovement). */
    private final Set<KeyMapping> pressedHere = new HashSet<>();
    /**
     * Waar elke bewegingstoets telde voor wij het wiel openden (zie letMovementThrough), om dat bij het sluiten terug te
     * zetten.
     */
    private final Map<KeyMapping, IKeyConflictContext> contexts = new HashMap<>();
    /** De showcase en de screenshots houden het wiel open zonder echte toets (zie script). */
    private static boolean scriptHold;

    public GearWheelScreen() {
        super(Component.translatable("screen.imttech.wheel"));
    }

    @Override
    protected void init() {
        super.init();
        // Je muis begint in het midden (Minecraft zet hem daar bij het openen); tot de eerste tekenbeurt rekenen we daarmee.
        lastMouseX = width / 2.0;
        lastMouseY = height / 2.0;
        if (!opened) {
            opened = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(ModSounds.IMT_MENU_OPEN.get(), 0.5f, 1.2f);
            }
            letMovementThrough();
            // Minecraft zette bij het openen alle toetsen op "los": wat je nog vasthoudt (W, Shift, ...) weer aan.
            syncMovement();
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
        // Hoofdstuk 72, stap 2: ook de granaten in je MOLLE-granaatpouches.
        for (ItemStack stack : com.senne.imttech.backpack.Molle.grenades(player)) {
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
        // Hoofdstuk 72: de rugzak op je rug openritsen (1,5 s stilstaan).
        ItemStack pack = Gear.back(player);
        com.senne.imttech.backpack.BackpackItem.Size packSize = com.senne.imttech.backpack.BackpackItem.sizeOf(pack);
        if (packSize != null) {
            int filled = com.senne.imttech.backpack.BackpackItem.filled(pack);
            options.add(new Option(pack, Component.translatable("screen.imttech.wheel.backpack"),
                    lines(Component.translatable("screen.imttech.wheel.unzip", pack.getHoverName()),
                            Component.translatable("screen.imttech.wheel.filled", filled, packSize.slots())),
                    filled + "/" + packSize.slots(), HudStyle.argb(255, HudStyle.WHITE), true, Gear.ACTION_OPEN_BACKPACK, 0, -1));
        }
        // Hoofdstuk 72, stap 2: elke MOLLE-pouch op je rugzak openen (zonder rits), en de genees-items uit je IFAK (een knop
        // per soort, met hoeveel): aanwijzen en loslaten = meteen gebruiken. Ook knoppen, want de ring zit vol.
        List<ItemStack> molle = com.senne.imttech.backpack.Molle.worn(player);
        List<Option> ifak = new ArrayList<>();
        List<ItemStack> ifakItems = new ArrayList<>();
        List<Integer> ifakCounts = new ArrayList<>();
        List<Integer> ifakArgs = new ArrayList<>();
        for (int i = 0; i < molle.size(); i++) {
            ItemStack pouch = molle.get(i);
            com.senne.imttech.backpack.MolleItem.Kind kind = com.senne.imttech.backpack.MolleItem.kindOf(pouch);
            if (kind == null) {
                continue;
            }
            int filled = com.senne.imttech.backpack.MolleItem.filled(pouch);
            options.add(new Option(pouch, kind.text(),
                    lines(Component.translatable("screen.imttech.wheel.open", kind.text()),
                            Component.translatable("screen.imttech.wheel.filled", filled, kind.size())),
                    filled + "/" + kind.size(), HudStyle.argb(255, HudStyle.WHITE), true, Gear.ACTION_OPEN_MOLLE, i, -1));
            if (kind != com.senne.imttech.backpack.MolleItem.Kind.IFAK) {
                continue;
            }
            List<ItemStack> contents = com.senne.imttech.backpack.MolleItem.contents(pouch);
            for (int slot = 0; slot < contents.size(); slot++) {
                ItemStack item = contents.get(slot);
                if (!com.senne.imttech.backpack.MolleItem.healing(item)) {
                    continue;
                }
                int group = -1;
                for (int g = 0; g < ifakItems.size() && group < 0; g++) {
                    if (ItemStack.isSameItemSameComponents(ifakItems.get(g), item)) {
                        group = g;
                    }
                }
                if (group < 0) {
                    ifakItems.add(item);
                    ifakCounts.add(item.getCount());
                    ifakArgs.add(i * 16 + slot);
                } else {
                    ifakCounts.set(group, ifakCounts.get(group) + item.getCount());
                }
            }
        }
        for (int g = 0; g < ifakItems.size(); g++) {
            ItemStack item = ifakItems.get(g);
            ifak.add(new Option(item, item.getHoverName(),
                    lines(Component.translatable("screen.imttech.wheel.ifak_use"),
                            Component.translatable("screen.imttech.wheel.count", ifakCounts.get(g))),
                    String.valueOf(ifakCounts.get(g)), HudStyle.argb(255, HudStyle.GREEN), true, Gear.ACTION_USE_IFAK, ifakArgs.get(g), -1));
        }
        options.addAll(ifak);
        layout();
    }

    /** Waar alles staat: de ring zo groot als het scherm toelaat (en groter als tegels elkaar anders raken), de knoppen ernaast. */
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
        // De knoppen rechts naast de ring (eronder past niet op een klein scherm, boven de werkbalk). Sinds hoofdstuk 72
        // (stap 2: de rugzak, zijn MOLLE-pouches en de IFAK) zijn het er zoveel dat ze compact zijn, zoals de tegels: een
        // vierkantje met het item en het getal; wat het is lees je in het midden als je hem aanwijst. In kolommen van zoveel
        // als er onder elkaar passen.
        int count = 0;
        for (Option option : options) {
            if (!option.onRing()) {
                option.w = BUTTON;
                option.h = BUTTON;
                count++;
            }
        }
        int rows = Math.max(1, Math.min(count, (height - 40) / (BUTTON + 3)));
        rows = Math.min(rows, Math.max(4, (count + 2) / 3));
        int shown = Math.min(count, rows);
        int x0 = cx + radius + TILE / 2 + 10;
        int y0 = cy - (shown * BUTTON + Math.max(0, shown - 1) * 3) / 2;
        int i = 0;
        for (Option option : options) {
            if (!option.onRing()) {
                option.x = x0 + (i / rows) * (BUTTON + 3);
                option.y = y0 + (i % rows) * (BUTTON + 3);
                i++;
            }
        }
    }

    // ================================================================ Tekenen

    /** Geen wazige achtergrond van Minecraft: alleen onze eigen lichte verduistering (zie HudStyle.dim). */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    /** Het spel loopt door (ook in je eentje): je loopt immers verder met het wiel open. */
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
        // De hint boven het wiel, met je echte toets.
        Component hint = Component.translatable("screen.imttech.wheel.release", ImtKeys.GEAR_WHEEL.getTranslatedKeyMessage());
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

    /**
     * Een knop naast de ring (sinds hoofdstuk 72 compact): een vierkantje met het item en het getal klein rechtsonder; de
     * naam en wat hij doet staan in het midden zodra je hem aanwijst.
     */
    private void button(GuiGraphics g, Option o, boolean hot) {
        g.fill(o.x, o.y, o.x + o.w, o.y + o.h, HudStyle.argb(hot ? 240 : 205, PANEL));
        g.renderOutline(o.x, o.y, o.w, o.h, hot ? HudStyle.argb(255, HudStyle.CYAN) : HudStyle.argb(190, HudStyle.ORANGE));
        g.renderItem(o.icon, o.x + (o.w - 16) / 2, o.y + (o.h - 16) / 2 - 2);
        if (o.badge != null) {
            g.pose().pushPose();
            g.pose().translate(o.x + o.w - 1, o.y + o.h - 6, 200);
            g.pose().scale(0.6f, 0.6f, 1);
            g.drawString(font, o.badge, -font.width(o.badge), 0, o.badgeColour, true);
            g.pose().popPose();
        }
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

    /** Deze keuze aan de server vragen (niets als het geen keuze is, of een grijze). */
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

    /** De wieltoets is losgelaten: wat je aanwijst is gekozen (of niets), en het wiel gaat dicht. */
    private void wheelKeyUp() {
        choose(hovered(lastMouseX, lastMouseY));
        onClose();
    }

    // ================================================================ De toetsen

    /**
     * Is deze toets nu echt ingedrukt? Rechtstreeks aan GLFW gevraagd, want {@code KeyMapping.isDown()} zegt niets zinnigs
     * zolang een scherm open is (zie de uitleg bovenaan).
     */
    private static boolean physicallyDown(InputConstants.Key key) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> false;
        };
    }

    /** Houd je de wieltoets nu vast (of houdt de showcase hem vast)? */
    public static boolean keyHeld() {
        return scriptHold || physicallyDown(ImtKeys.GEAR_WHEEL.getKey());
    }

    /** De toetsen waarmee je loopt, springt, sluipt en sprint: die geeft het wiel door aan het spel. */
    private static List<KeyMapping> movementKeys() {
        Options o = Minecraft.getInstance().options;
        return List.of(o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint);
    }

    /** Sluipen of sprinten op "toggle" (Opties > Besturing) houdt het spel zelf bij; daar blijven we met het vangnet af. */
    private static boolean toggles(KeyMapping key) {
        Options o = Minecraft.getInstance().options;
        return (key == o.keyShift && o.toggleCrouch().get()) || (key == o.keySprint && o.toggleSprint().get());
    }

    /**
     * Een bewegingstoets is ingedrukt: "aan" zetten, één keer (een vastgehouden toets geeft herhaalde drukken, en met
     * sluipen of sprinten op "toggle" zou elke herhaling hem weer uitzetten). Geeft true als het een bewegingstoets was.
     */
    private boolean pressMovement(int keyCode, int scanCode) {
        boolean any = false;
        for (KeyMapping key : movementKeys()) {
            if (key.matches(keyCode, scanCode)) {
                any = true;
                if (pressedHere.add(key)) {
                    key.setDown(true);
                }
            }
        }
        return any;
    }

    /** Een bewegingstoets is losgelaten: weer "uit" (Minecraft doet dat zelf ook nog, dat kan geen kwaad). */
    private void releaseMovement(int keyCode, int scanCode) {
        for (KeyMapping key : movementKeys()) {
            if (key.matches(keyCode, scanCode) && pressedHere.remove(key)) {
                key.setDown(false);
            }
        }
    }

    /**
     * Elke tick, als vangnet: wat echt ingedrukt is "aan" (ook wat je al vasthield toen het wiel openging), en wat wij
     * aanzetten en losgelaten is weer "uit". Wat een andere mod aanzette, laten we staan.
     */
    private void syncMovement() {
        for (KeyMapping key : movementKeys()) {
            if (toggles(key)) {
                continue;
            }
            boolean down = physicallyDown(key.getKey());
            if (down && !pressedHere.contains(key)) {
                pressedHere.add(key);
                key.setDown(true);
            } else if (!down && pressedHere.contains(key)) {
                pressedHere.remove(key);
                key.setDown(false);
            }
        }
    }

    /**
     * NeoForge geeft de bewegingstoetsen van Minecraft zelf (lopen, springen, sluipen, sprinten) de context "alleen in het
     * spel" (Options: KeyConflictContext.IN_GAME), en zijn {@code KeyMapping.isDown()} zegt dan nee zolang er een scherm
     * open is, hoe hard wij de toets ook "aan" zetten; daardoor stond je toch stil (gemeten met echte toetsaanslagen in de
     * rondleiding, stuk "wheelwalk"). Zolang het wiel open is tellen die toetsen daarom overal (UNIVERSAL); bij het
     * sluiten krijgen ze hun eigen context terug (removed).
     */
    private void letMovementThrough() {
        for (KeyMapping key : movementKeys()) {
            if (!contexts.containsKey(key)) {
                contexts.put(key, key.getKeyConflictContext());
                key.setKeyConflictContext(KeyConflictContext.UNIVERSAL);
            }
        }
    }

    /** Het wiel gaat dicht (ook als een ander scherm het vervangt): de bewegingstoetsen tellen weer waar ze telden. */
    @Override
    public void removed() {
        super.removed();
        contexts.forEach(KeyMapping::setKeyConflictContext);
        contexts.clear();
    }

    @Override
    public void tick() {
        super.tick();
        rebuild();
        syncMovement();
        // Vangnet als het loslaten niet als toetsgebeurtenis binnenkomt (bijvoorbeeld als het venster zijn focus verloor).
        if (!keyHeld()) {
            wheelKeyUp();
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ImtKeys.GEAR_WHEEL.matches(keyCode, scanCode)) {
            wheelKeyUp();
            return true;
        }
        releaseMovement(keyCode, scanCode);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ImtKeys.GEAR_WHEEL.matches(keyCode, scanCode)) {
            return true; // vasthouden geeft herhaalde drukken; die doen niets
        }
        if (pressMovement(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Een tegel aanklikken kiest hem ook (je houdt Alt nog vast); rechtsklik annuleert. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ImtKeys.GEAR_WHEEL.matchesMouse(button)) {
            return true;
        }
        if (button == 1) {
            onClose();
            return true;
        }
        if (button == 0) {
            int index = hovered(mouseX, mouseY);
            if (index >= 0 && options.get(index).enabled) {
                choose(index);
                onClose();
            }
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
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ================================================================ Voor de showcase en de screenshots

    /**
     * De showcase en de screenshots doen toetsen na zonder GLFW; voor hen houdt dit het wiel open: {@code true} opent
     * het en houdt het vast alsof je Alt indrukt, {@code false} laat los (en sluit het, als het script nog niets koos).
     */
    public static void script(boolean hold) {
        scriptHold = hold;
        Minecraft mc = Minecraft.getInstance();
        if (hold) {
            if (mc.screen == null) {
                mc.setScreen(new GearWheelScreen());
            }
        } else if (mc.screen instanceof GearWheelScreen wheel) {
            wheel.wheelKeyUp();
        }
    }

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
