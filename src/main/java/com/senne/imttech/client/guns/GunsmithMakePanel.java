package com.senne.imttech.client.guns;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunPartItem;
import com.senne.imttech.guns.GunParts;
import com.senne.imttech.guns.GunsmithRecipe;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModRecipes;
import com.senne.imttech.weapons.GunSlingItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import com.senne.imttech.weapons.LaserPart;
import com.senne.imttech.weapons.LaserPartItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.Nullable;

/**
 * Het tabblad "Make parts" van de Gunsmith Table (hoofdstuk 78). Jouw wens: "Zorg dat je gun onderdelen niet meer in de
 * crafting table kunt maken maar enkel in de gun smit table"; jouw keuze: een tabblad met tegels, geen vakjes.
 *
 * <pre>
 *  WEAPONS            |            Polymer  Steel  Titanium   |  Steel Slide
 *  [G] Glock 19       |  Frame       [ ]     [ ]     [ ]      |  COSTS
 *      2 to make      |  Slide               [ ]     [ ]      |  [i] Steel Ingot
 *  [P] Laser Pistol   |  ...                                  |      7 / 5
 *  [R] Laser Rifle    |  Magazine            [ ]              |  You can make 1
 *  ...                |                                       |  [     Make     ]
 * </pre>
 * Links kies je een wapen, in het midden een onderdeel (een rij per onderdeel, een kolom per materiaal; een tegel met een
 * groene rand kun je nu maken, een donkere niet), en rechts staat wat het kost: wat je hebt en wat nodig is, groen of rood.
 * De knop Make (van het scherm, zie PartsTableScreen) vraagt de server om het te maken; die haalt de materialen uit je
 * inventaris (zie GunsmithRecipe). De recepten zelf komen van de server: wat in data/imttech/recipe staat, staat hier.
 */
public final class GunsmithMakePanel {
    /** Een wapen in de lijst links: de Glock, elk lasergeweer, en "Other" voor recepten die een datapack toevoegt. */
    public enum Group { GLOCK, PISTOL, RIFLE, SNIPER, SHOTGUN, OTHER }

    /** Eén tegel: een recept (zijn nummer voor de server) op zijn plek: rij = onderdeel, kolom = materiaal. */
    private record Tile(int index, GunsmithRecipe recipe, ItemStack result, int row, int column) {}

    private static final int ROW_H = 20;
    private static final int TILE = 18;
    /**
     * De indeling van dit tabblad, iets anders dan bij Build: de lijst met wapens links is breder (zodat "Laser Shotgun"
     * past), het vak met de tegels iets smaller. De kosten rechts staan op dezelfde plek als de lijst bij Build.
     */
    public static final int LIST_X = 6, LIST_Y = 26, LIST_W = 84, LIST_H = 152;
    public static final int GRID_X = 94, GRID_Y = 18, GRID_W = 140, GRID_H = 160;
    /** Zoveel tegels naast elkaar bij "Other" (daar zijn geen rijen per onderdeel). */
    private static final int OTHER_COLUMNS = 6;
    /** Rechts: waar het eerste ingrediënt staat, hoeveel plaats ze samen hebben, en de regel "You can make" eronder. */
    private static final int COSTS_Y = 46;
    private static final int COSTS_HEIGHT = 102;
    private static final int STATUS_Y = 150;
    private static final int TRAY_BG = HudStyle.argb(255, 0x161A20);
    private static final int TILE_READY = HudStyle.argb(255, 0x12281D);
    private static final int TABLE_BG = HudStyle.argb(255, 0x0A0D12);

    /** Wat je het laatst koos: zo blijft het zolang het spel open is, ook als je de tafel dicht- en weer opendoet. */
    private static Group lastGroup = Group.GLOCK;
    private static int lastSelected = -1;

    private final Font font;
    private final Map<Group, List<Tile>> tiles = new EnumMap<>(Group.class);
    private final Map<Group, ItemStack> icons = new EnumMap<>(Group.class);
    /** De recepten (hun nummers) die je nu kunt maken, en per wapen hoeveel. */
    private final Set<Integer> affordable = new HashSet<>();
    private final Map<Group, Integer> ready = new EnumMap<>(Group.class);
    private Group group = lastGroup;
    private int selected = lastSelected;
    /** Hoe vaak je het gekozen recept kunt maken. */
    private int times;
    /** Zoveel recepten waren er de vorige keer (komt er een /reload, dan bouwen we de tegels opnieuw). */
    private int recipeCount = -1;

    public GunsmithMakePanel(Font font) {
        this.font = font;
    }

    // ================================================================ De tegels

    /** De tegels opnieuw neerleggen, uit de recepten die de server stuurde. */
    private void rebuild() {
        tiles.clear();
        icons.clear();
        for (Group each : Group.values()) {
            tiles.put(each, new ArrayList<>());
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        var all = GunsmithRecipe.all(minecraft.level.getRecipeManager());
        recipeCount = all.size();
        List<Tile> other = tiles.get(Group.OTHER);
        for (int index = 0; index < all.size(); index++) {
            GunsmithRecipe recipe = all.get(index).value();
            ItemStack result = recipe.result();
            boolean placed = false;
            if (result.getItem() instanceof GunPartItem item) {
                int column = switch (GunPartItem.materialOf(result)) {
                    case POLYMER -> 0;
                    case STEEL -> 1;
                    case TITANIUM -> 2;
                    default -> -1;
                };
                placed = column >= 0 && add(Group.GLOCK, index, recipe, result, item.part().ordinal(), column);
            } else if (result.is(ModItems.GLOCK_19_MAGAZINE.get())) {
                placed = add(Group.GLOCK, index, recipe, result, GunPart.values().length, 1);
            } else if (result.getItem() instanceof LaserPartItem item) {
                int column = LaserPartItem.materialOf(result) == GunMaterial.NETHERITE ? 1 : 0;
                placed = add(group(item.gun()), index, recipe, result, LaserPart.wearing().indexOf(item.part()), column);
            } else if (result.getItem() instanceof GunSlingItem) {
                // De sling past op drie geweren: hij staat bij elk van de drie (het is hetzelfde recept).
                int row = LaserPart.wearing().size();
                placed = add(Group.RIFLE, index, recipe, result, row, 0) | add(Group.SNIPER, index, recipe, result, row, 0)
                        | add(Group.SHOTGUN, index, recipe, result, row, 0);
            }
            if (!placed) {
                other.add(new Tile(index, recipe, result, other.size() / OTHER_COLUMNS, other.size() % OTHER_COLUMNS));
            }
        }
        icons.put(Group.GLOCK, FirearmItem.withParts(FirearmType.GLOCK_19, GunParts.classic()));
        for (GunType type : GunType.values()) {
            icons.put(group(type), LaserGunItem.complete(type, GunMaterial.TITANIUM));
        }
        icons.put(Group.OTHER, other.isEmpty() ? ItemStack.EMPTY : other.getFirst().result());
        if (tiles.get(group).isEmpty()) {
            List<Group> shown = groups();
            group = shown.isEmpty() ? Group.GLOCK : shown.getFirst();
        }
    }

    /** Een tegel op zijn plek leggen; false als daar al een ligt (dan gaat hij naar "Other"). */
    private boolean add(Group group, int index, GunsmithRecipe recipe, ItemStack result, int row, int column) {
        if (row < 0) {
            return false;
        }
        List<Tile> list = tiles.get(group);
        for (Tile tile : list) {
            if (tile.row == row && tile.column == column) {
                return false;
            }
        }
        list.add(new Tile(index, recipe, result, row, column));
        return true;
    }

    private static Group group(GunType type) {
        return switch (type) {
            case PISTOL -> Group.PISTOL;
            case RIFLE -> Group.RIFLE;
            case SNIPER -> Group.SNIPER;
            case SHOTGUN -> Group.SHOTGUN;
        };
    }

    /** De wapens in de lijst links: alleen die waar iets te maken valt. */
    private List<Group> groups() {
        List<Group> shown = new ArrayList<>();
        for (Group each : Group.values()) {
            if (!tiles.getOrDefault(each, List.of()).isEmpty()) {
                shown.add(each);
            }
        }
        return shown;
    }

    private static Component groupName(Group group) {
        return switch (group) {
            case GLOCK -> FirearmType.GLOCK_19.text();
            case PISTOL -> Component.translatable("item.imttech." + GunType.PISTOL.id);
            case RIFLE -> Component.translatable("item.imttech." + GunType.RIFLE.id);
            case SNIPER -> Component.translatable("item.imttech." + GunType.SNIPER.id);
            case SHOTGUN -> Component.translatable("item.imttech." + GunType.SHOTGUN.id);
            case OTHER -> Component.translatable("screen.imttech.gunsmith.make.other");
        };
    }

    /** De kolommen: de materialen van de Glock, of titanium en netherite bij een lasergeweer. "Other" heeft er geen. */
    private static List<GunMaterial> columns(Group group) {
        return switch (group) {
            case GLOCK -> List.of(GunMaterial.POLYMER, GunMaterial.STEEL, GunMaterial.TITANIUM);
            case OTHER -> List.of();
            default -> List.of(GunMaterial.TITANIUM, GunMaterial.NETHERITE);
        };
    }

    /** De naam van een rij: het onderdeel, of het magazijn (de Glock) en de sling (de lange lasergeweren). */
    private static Component rowName(Group group, int row) {
        if (group == Group.GLOCK) {
            return row < GunPart.values().length ? GunPart.values()[row].text()
                    : Component.translatable("screen.imttech.gunsmith.make.magazine");
        }
        List<LaserPart> parts = LaserPart.wearing();
        return row < parts.size() ? parts.get(row).text() : LaserPart.SLING.text();
    }

    /** De gekozen tegel (in het gekozen wapen); kies de eerste als er nog niets (of iets van een ander wapen) gekozen is. */
    private @Nullable Tile selectedTile() {
        List<Tile> list = tiles.getOrDefault(group, List.of());
        Tile first = null;
        for (Tile tile : list) {
            if (tile.index == selected) {
                return tile;
            }
            if (first == null || tile.row < first.row || (tile.row == first.row && tile.column < first.column)) {
                first = tile;
            }
        }
        if (first != null) {
            selected = first.index;
            lastSelected = selected;
        }
        return first;
    }

    // ================================================================ Voor het scherm

    /** Elke tick: wat kun je nu maken (je inventaris verandert, ook door Make). */
    public void tick(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (minecraft.level.getRecipeManager().getAllRecipesFor(ModRecipes.GUNSMITHING.get()).size() != recipeCount) {
            rebuild();
        }
        GunsmithRecipe.Carried carried = GunsmithRecipe.Carried.of(player.getInventory());
        affordable.clear();
        ready.clear();
        Set<Integer> checked = new HashSet<>();
        for (Group each : Group.values()) {
            int count = 0;
            for (Tile tile : tiles.getOrDefault(each, List.of())) {
                if (checked.add(tile.index) && tile.recipe.affordable(carried)) {
                    affordable.add(tile.index);
                }
                if (affordable.contains(tile.index)) {
                    count++;
                }
            }
            ready.put(each, count);
        }
        Tile tile = selectedTile();
        times = tile == null ? 0 : tile.recipe.times(carried);
    }

    /** Het nummer van het gekozen recept (voor de knop Make), of -1. */
    public int selectedIndex() {
        Tile tile = selectedTile();
        return tile == null ? -1 : tile.index;
    }

    /** Kun je het gekozen recept nu maken? */
    public boolean canMake() {
        return times > 0;
    }

    /**
     * Voor de rondleiding en de showcase: dit wapen kiezen, en daarin de tegel waarvan het resultaat aan {@code result}
     * voldoet (of de eerste).
     */
    public void show(Group group, Predicate<ItemStack> result) {
        if (recipeCount < 0) {
            rebuild();
        }
        this.group = group;
        lastGroup = group;
        for (Tile tile : tiles.getOrDefault(group, List.of())) {
            if (result.test(tile.result)) {
                selected = tile.index;
                lastSelected = selected;
                return;
            }
        }
    }

    // ================================================================ Tekenen

    /** Het hele tabblad (behalve de knop Make: die is van het scherm). {@code left} en {@code top}: de hoek van het scherm. */
    public void render(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        if (recipeCount < 0) {
            rebuild();
        }
        renderGroups(g, left + LIST_X, top + LIST_Y, mouseX, mouseY);
        renderTiles(g, left + GRID_X, top + GRID_Y, mouseX, mouseY);
        renderCosts(g, left + PartsTableScreen.SIDE_X, top, mouseX, mouseY);
    }

    /** Links: de wapens, met hoeveel je er nu van kunt maken. */
    private void renderGroups(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int w = LIST_W;
        g.fill(x, y, x + w, y + LIST_H, TRAY_BG);
        g.renderOutline(x, y, w, LIST_H, PartsTableScreen.FRAME);
        int rowY = y + 2;
        for (Group each : groups()) {
            boolean active = each == group;
            if (active) {
                g.fill(x + 1, rowY, x + w - 1, rowY + ROW_H, HudStyle.argb(70, HudStyle.CYAN));
            } else if (inside(mouseX, mouseY, x, rowY, w, ROW_H)) {
                g.fill(x + 1, rowY, x + w - 1, rowY + ROW_H, HudStyle.argb(35, HudStyle.CYAN));
            }
            g.renderItem(icons.getOrDefault(each, ItemStack.EMPTY), x + 3, rowY + 2);
            small(g, groupName(each).getString(), x + 22, rowY + 3,
                    HudStyle.argb(255, active ? HudStyle.CYAN : HudStyle.WHITE), w - 24);
            int count = ready.getOrDefault(each, 0);
            small(g, count > 0 ? Component.translatable("screen.imttech.gunsmith.make.ready", count).getString()
                            : Component.translatable("screen.imttech.gunsmith.make.none").getString(),
                    x + 22, rowY + 11, count > 0 ? HudStyle.argb(220, HudStyle.GREEN) : HudStyle.argb(120, HudStyle.WHITE), w - 24);
            rowY += ROW_H;
        }
    }

    /** Midden: de tegels, een rij per onderdeel en een kolom per materiaal. */
    private void renderTiles(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int w = GRID_W;
        g.fill(x, y, x + w, y + GRID_H, TABLE_BG);
        g.renderOutline(x, y, w, GRID_H, PartsTableScreen.FRAME);
        List<GunMaterial> columns = columns(group);
        int namesW = namesWidth(group);
        int columnW = columnWidth(group);
        // De materialen boven de kolommen, elk in zijn eigen kleur (gecentreerd en nooit breder dan de kolom, dus nooit
        // over de naam ernaast).
        for (int c = 0; c < columns.size(); c++) {
            GunMaterial material = columns.get(c);
            centred(g, material.text().getString(), x + namesW + c * columnW + columnW / 2, y + 5,
                    HudStyle.argb(230, material.colour), columnW);
        }
        List<Tile> list = tiles.getOrDefault(group, List.of());
        // De namen van de rijen (alleen rijen waar een tegel ligt).
        if (!columns.isEmpty()) {
            Set<Integer> rows = new HashSet<>();
            for (Tile tile : list) {
                rows.add(tile.row);
            }
            for (int row : rows) {
                small(g, rowName(group, row).getString(), x + 5, y + 17 + row * ROW_H + 5, HudStyle.argb(220, HudStyle.WHITE),
                        namesW - 7);
            }
        }
        // De tegels.
        for (Tile tile : list) {
            int tx = x + namesW + tile.column * columnW + (columnW - TILE) / 2;
            int ty = y + 17 + tile.row * ROW_H;
            boolean can = affordable.contains(tile.index);
            boolean chosen = tile.index == selected;
            boolean hover = inside(mouseX, mouseY, tx, ty, TILE, TILE);
            g.fill(tx, ty, tx + TILE, ty + TILE, can ? TILE_READY : TRAY_BG);
            g.renderItem(tile.result, tx + 1, ty + 1);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            if (!can) {
                // Kun je het (nog) niet maken: wat donkerder.
                g.fill(tx + 1, ty + 1, tx + TILE - 1, ty + TILE - 1, HudStyle.argb(120, 0x0A0D12));
            }
            int edge = chosen ? HudStyle.argb(255, HudStyle.CYAN) : hover ? HudStyle.argb(220, HudStyle.WHITE)
                    : can ? HudStyle.argb(200, HudStyle.GREEN) : PartsTableScreen.FRAME;
            g.renderOutline(tx, ty, TILE, TILE, edge);
            if (chosen) {
                g.renderOutline(tx - 1, ty - 1, TILE + 2, TILE + 2, HudStyle.argb(110, HudStyle.CYAN));
            }
            g.pose().popPose();
        }
    }

    /**
     * Zo breed is de kolom met de namen van de rijen: net genoeg voor de langste naam ("Magazine" bij de Glock, "Trigger
     * Module" bij een lasergeweer). Zo blijft er zoveel mogelijk plaats voor de kolommen met tegels, en passen de namen
     * van de materialen erboven ("Polymer", "Netherite").
     */
    private int namesWidth(Group group) {
        if (group == Group.OTHER) {
            return 4;
        }
        int widest = 0;
        for (Tile tile : tiles.getOrDefault(group, List.of())) {
            widest = Math.max(widest, font.width(rowName(group, tile.row)));
        }
        return Math.min(GRID_W / 2, 8 + (int) Math.ceil(widest * 0.75f));
    }

    /** Zo breed is een kolom met tegels. */
    private int columnWidth(Group group) {
        List<GunMaterial> columns = columns(group);
        int count = columns.isEmpty() ? OTHER_COLUMNS : columns.size();
        return (GRID_W - namesWidth(group) - 4) / count;
    }

    /** Rechts: het gekozen onderdeel en wat het kost (wat je hebt / wat nodig is), en of je het kunt maken. */
    private void renderCosts(GuiGraphics g, int x, int top, int mouseX, int mouseY) {
        int w = PartsTableScreen.SIDE_W;
        Tile tile = selectedTile();
        if (tile == null) {
            small(g, Component.translatable("screen.imttech.gunsmith.make.nothing").getString(), x, top + 18,
                    HudStyle.argb(160, HudStyle.WHITE), w);
            return;
        }
        // De naam, op hoogstens drie regels.
        List<FormattedCharSequence> lines = font.split(tile.result.getHoverName(), (int) (w / 0.75f));
        int lineY = top + 14;
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            g.pose().pushPose();
            g.pose().translate(x, lineY, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, lines.get(i), 0, 0, HudStyle.argb(255, HudStyle.ORANGE), false);
            g.pose().popPose();
            lineY += 7;
        }
        small(g, Component.translatable("screen.imttech.gunsmith.make.costs").getString(), x, top + COSTS_Y - 8,
                HudStyle.argb(170, HudStyle.ORANGE), w);
        int rowY = top + COSTS_Y;
        Player player = Minecraft.getInstance().player;
        GunsmithRecipe.Carried carried = player == null ? new GunsmithRecipe.Carried(List.of())
                : GunsmithRecipe.Carried.of(player.getInventory());
        int step = costStep(tile);
        for (SizedIngredient ingredient : tile.recipe.ingredients()) {
            ItemStack shown = shown(ingredient);
            g.renderItem(shown, x, rowY);
            int have = GunsmithRecipe.have(carried, ingredient);
            small(g, shown.getHoverName().getString(), x + 18, rowY + 1, HudStyle.argb(230, HudStyle.WHITE), w - 18);
            small(g, have + " / " + ingredient.count(), x + 18, rowY + 9,
                    HudStyle.argb(255, have >= ingredient.count() ? HudStyle.GREEN : HudStyle.RED), w - 18);
            rowY += step;
        }
        String status = times > 0 ? Component.translatable("screen.imttech.gunsmith.make.can", times).getString()
                : Component.translatable("screen.imttech.gunsmith.make.missing").getString();
        small(g, status, x, top + STATUS_Y, HudStyle.argb(230, times > 0 ? HudStyle.GREEN : HudStyle.RED), w);
    }

    /**
     * Hoe ver de ingrediënten rechts uit elkaar staan: 19 pixels, of dichter bij elkaar als er meer zijn dan er passen
     * (onze recepten hebben er hoogstens vijf; een datapack kan er meer hebben). Zo komen ze nooit over de knop.
     */
    private static int costStep(Tile tile) {
        return Math.min(19, COSTS_HEIGHT / Math.max(1, tile.recipe.ingredients().size()));
    }

    /** Wat we van een ingrediënt laten zien: zijn eerste item (bij een tag, zoals "elke glazen ruit", het eerste uit de tag). */
    private static ItemStack shown(SizedIngredient ingredient) {
        ItemStack[] items = ingredient.ingredient().getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[0];
    }

    /** De uitleg onder de muis: bij een tegel het item, bij een ingrediënt rechts ook (en of er meer soorten goed zijn). */
    public void renderTooltip(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        Tile tile = tileAt(left, top, mouseX, mouseY);
        if (tile != null) {
            g.renderTooltip(font, tile.result, mouseX, mouseY);
            return;
        }
        SizedIngredient ingredient = ingredientAt(left, top, mouseX, mouseY);
        if (ingredient != null) {
            ItemStack shown = shown(ingredient);
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), shown));
            int kinds = ingredient.ingredient().getItems().length;
            if (kinds > 1) {
                lines.add(Component.translatable("screen.imttech.gunsmith.make.any", kinds).withStyle(ChatFormatting.GRAY));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    // ================================================================ Klikken

    /** Een klik op het tabblad: een wapen of een tegel kiezen. True als de klik hier hoorde. */
    public boolean mouseClicked(int left, int top, double mouseX, double mouseY) {
        int x = left + LIST_X;
        int rowY = top + LIST_Y + 2;
        for (Group each : groups()) {
            if (inside(mouseX, mouseY, x, rowY, LIST_W, ROW_H)) {
                if (each != group) {
                    group = each;
                    lastGroup = each;
                    selectedTile();
                    clickSound();
                }
                return true;
            }
            rowY += ROW_H;
        }
        Tile tile = tileAt(left, top, mouseX, mouseY);
        if (tile != null) {
            if (tile.index != selected) {
                selected = tile.index;
                lastSelected = selected;
                clickSound();
            }
            return true;
        }
        return false;
    }

    /** Scrollen boven de lijst links: het volgende of vorige wapen. */
    public boolean mouseScrolled(int left, int top, double mouseX, double mouseY, double scrollY) {
        if (!inside(mouseX, mouseY, left + LIST_X, top + LIST_Y, LIST_W,
                LIST_H)) {
            return false;
        }
        List<Group> shown = groups();
        int at = shown.indexOf(group);
        if (!shown.isEmpty() && at >= 0) {
            int next = Math.floorMod(at + (scrollY < 0 ? 1 : -1), shown.size());
            group = shown.get(next);
            lastGroup = group;
            selectedTile();
        }
        return true;
    }

    /** Voor de rondleiding: het midden van de eerste tegel (in het gekozen wapen) waarvan het resultaat voldoet, of null. */
    public int @Nullable [] tileCentre(int left, int top, Predicate<ItemStack> result) {
        int namesW = namesWidth(group);
        int columnW = columnWidth(group);
        for (Tile tile : tiles.getOrDefault(group, List.of())) {
            if (result.test(tile.result)) {
                return new int[]{left + GRID_X + namesW + tile.column * columnW + columnW / 2,
                        top + GRID_Y + 17 + tile.row * ROW_H + TILE / 2};
            }
        }
        return null;
    }

    private @Nullable Tile tileAt(int left, int top, double mouseX, double mouseY) {
        int x = left + GRID_X;
        int y = top + GRID_Y;
        int namesW = namesWidth(group);
        int columnW = columnWidth(group);
        for (Tile tile : tiles.getOrDefault(group, List.of())) {
            int tx = x + namesW + tile.column * columnW + (columnW - TILE) / 2;
            int ty = y + 17 + tile.row * ROW_H;
            if (inside(mouseX, mouseY, tx, ty, TILE, TILE)) {
                return tile;
            }
        }
        return null;
    }

    private @Nullable SizedIngredient ingredientAt(int left, int top, double mouseX, double mouseY) {
        Tile tile = selectedTile();
        if (tile == null) {
            return null;
        }
        int x = left + PartsTableScreen.SIDE_X;
        int rowY = top + COSTS_Y;
        int step = costStep(tile);
        for (SizedIngredient ingredient : tile.recipe.ingredients()) {
            if (inside(mouseX, mouseY, x, rowY, PartsTableScreen.SIDE_W, Math.min(18, step))) {
                return ingredient;
            }
            rowY += step;
        }
        return null;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static void clickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    // ================================================================ Tekst

    /** Kleine tekst (driekwart), afgekapt met "..." als hij breder is dan {@code maxWidth} pixels: nooit over iets anders heen. */
    private void small(GuiGraphics g, String text, int x, int y, int colour, int maxWidth) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(font, fit(text, maxWidth), 0, 0, colour, false);
        g.pose().popPose();
    }

    /** Kleine tekst met zijn midden op {@code centreX}. */
    private void centred(GuiGraphics g, String text, int centreX, int y, int colour, int maxWidth) {
        String shown = fit(text, maxWidth);
        int width = Math.round(font.width(shown) * 0.75f);
        small(g, shown, centreX - width / 2, y, colour, maxWidth);
    }

    private String fit(String text, int maxWidth) {
        int max = (int) (maxWidth / 0.75f);
        if (font.width(text) <= max) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, max - font.width("..."))) + "...";
    }
}
