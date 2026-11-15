package com.senne.imttech.client.storage;

import com.senne.imttech.client.ScrollBar;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.storage.StorageActionPayload;
import com.senne.imttech.storage.StorageInterfaceBlockEntity;
import com.senne.imttech.storage.StorageMenu;
import com.senne.imttech.storage.StorageOrder;
import com.senne.imttech.storage.StorageStatePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Het scherm van een Storage Interface (hoofdstuk 113, jouw wens: "je kunt een item orderen via een storage interface
 * (GUI)").
 *
 * <pre>
 *  STORAGE  Main storage                       [Order] [Store] [Cars]
 *  [search______]        12 kinds, 3 chests   | Iron Ingot
 *  +-- wat er in de opslag zit (9 x 4) -----+ | In storage: 124 (3 chests)
 *  |                                        | | [-]  8  [+]
 *  |                                        | | [ Order ]
 *  +----------------------------------------+ |
 *  PICK-UP                                    | ORDERS
 *  [ 9 afhaalvakjes ]                         | 8 Iron Ingot        Digger 3/8
 *  [ je inventaris  ]                         | 64 Cobblestone          waiting
 * </pre>
 * Links wat er in je opslag zit (op het tabblad Store: de vakjes om in te leggen; op Cars: de auto's), rechts wat je koos
 * en de bestellingen. De getallen komen van de server zodra er iets verandert.
 */
public class StorageScreen extends AbstractContainerScreen<StorageMenu> {
    private static final int PANEL = 0x10141A;
    private static final int FRAME = 0x465460;
    private static final int SLOT = 0x1A2028;
    private static final int BOX = 0x0C1015;
    private static final int GREY = 0x8A9BA0;
    /** Wit-blauw, zoals een netwerkkabel. */
    private static final int ACCENT = 0xCFE3FF;

    private static final String[] TABS = {"order", "store", "cars"};
    private static final int TAB_Y = 4;
    private static final int SEARCH_Y = 22;
    private static final int CELL = 18;
    private static final int COLUMNS = 9;
    private static final int ROWS = 4;
    /** De regels van de auto's (tabblad Cars) en van de bestellingen. */
    private static final int CAR_ROW = 14;
    private static final int CAR_ROWS = 5;
    private static final int ORDERS_Y = 112;
    private static final int ORDER_ROW = 11;
    private static final int ORDER_ROWS = 8;
    /** De knoppen in de kolom rechts (tabblad Order). */
    private static final int AMOUNT_Y = 62;
    private static final int ORDER_BUTTON_Y = 80;

    private @Nullable EditBox search;
    private int scroll;
    private int carScroll;
    private int orderScroll;
    /** De soort die je koos (of leeg), en hoeveel je ervan wilt. */
    private ItemStack selected = ItemStack.EMPTY;
    private int amount = 1;
    private final ScrollBar bar = new ScrollBar();
    private final ScrollBar carBar = new ScrollBar();

    public StorageScreen(StorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = StorageMenu.WIDTH;
        imageHeight = StorageMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        // De titel en "Inventory" tekenen we zelf.
        titleLabelY = -1000;
        inventoryLabelY = -1000;
        String typed = search == null ? "" : search.getValue();
        search = new EditBox(font, leftPos + StorageMenu.AREA_X, topPos + SEARCH_Y, 66, 11,
                Component.translatable("screen.imttech.storage.search"));
        search.setMaxLength(32);
        search.setHint(Component.translatable("screen.imttech.storage.search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        search.setValue(typed);
        search.setResponder(value -> scroll = 0);
        addRenderableWidget(search);
        showTab();
    }

    private StorageStatePayload state() {
        return menu.state();
    }

    /** Het zoekvak staat alleen op het tabblad Order. */
    private void showTab() {
        if (search != null) {
            search.visible = menu.tab() == StorageMenu.TAB_ORDER;
            if (!search.visible) {
                search.setFocused(false);
            }
        }
    }

    private void setTab(int tab) {
        if (tab == menu.tab() || minecraft == null || minecraft.gameMode == null) {
            return;
        }
        menu.setTab(tab);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, tab); // de server weet het dan ook (shift-klik)
        showTab();
        click(1.0f);
    }

    // ================================================================ Wat er te zien is

    /** De soorten die bij het zoekvak passen (op naam, zoals de server ze stuurde). */
    private List<StorageStatePayload.Entry> shown() {
        String typed = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        if (typed.isEmpty()) {
            return state().stock();
        }
        List<StorageStatePayload.Entry> shown = new ArrayList<>();
        for (StorageStatePayload.Entry entry : state().stock()) {
            if (entry.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(typed)) {
                shown.add(entry);
            }
        }
        return shown;
    }

    /** De soort die je koos zoals hij nu in de opslag ligt, of null als hij er niet (meer) is. */
    private StorageStatePayload.@Nullable Entry selectedEntry() {
        if (selected.isEmpty()) {
            return null;
        }
        for (StorageStatePayload.Entry entry : state().stock()) {
            if (ItemStack.isSameItemSameComponents(entry.stack(), selected)) {
                return entry;
            }
        }
        return null;
    }

    /** Hoeveel je hooguit in één keer bestelt: wat er ligt, en wat er in de negen afhaalvakjes past. */
    private int maxAmount() {
        StorageStatePayload.Entry entry = selectedEntry();
        if (entry == null) {
            return 1;
        }
        return Math.max(1, Math.min(entry.count(), StorageInterfaceBlockEntity.MAX_ORDER_STACKS * entry.stack().getMaxStackSize()));
    }

    private boolean anyLinkedCar() {
        for (StorageStatePayload.Car car : state().cars()) {
            if (car.linked()) {
                return true;
            }
        }
        return false;
    }

    private int stockRows() {
        return (shown().size() + COLUMNS - 1) / COLUMNS;
    }

    // ================================================================ Tekenen

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, HudStyle.argb(245, PANEL));
        g.renderOutline(x, y, imageWidth, imageHeight, HudStyle.argb(255, FRAME));
        HudStyle.brackets(g, x + 2, y + 2, x + imageWidth - 3, y + imageHeight - 3, 6, HudStyle.argb(255, ACCENT));
        // De titel en de naam van de interface.
        Component label = Component.translatable("screen.imttech.storage");
        g.drawString(font, label, x + 8, y + 7, HudStyle.argb(255, ACCENT), true);
        int nameRoom = tabBounds(0)[0] - (x + 14 + font.width(label)) - 6;
        g.drawString(font, fit(state().name(), nameRoom), x + 14 + font.width(label), y + 7, HudStyle.argb(220, HudStyle.WHITE), false);
        renderTabs(g, mouseX, mouseY);
        // De vakjes die nu te zien zijn.
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, HudStyle.argb(255, SLOT));
            g.renderOutline(sx - 1, sy - 1, 18, 18, HudStyle.argb(255, FRAME));
        }
        small(g, Component.translatable("screen.imttech.storage.pickup"), x + StorageMenu.AREA_X, y + StorageMenu.OUT_Y - 9,
                HudStyle.argb(200, GREY));
        switch (menu.tab()) {
            case StorageMenu.TAB_STORE -> renderStore(g, mouseX, mouseY);
            case StorageMenu.TAB_CARS -> renderCars(g, mouseX, mouseY);
            default -> renderOrder(g, mouseX, mouseY);
        }
        renderOrders(g, mouseX, mouseY);
    }

    /** Waar een tabblad staat: {x, breedte}. De tabbladen staan rechts bovenaan, van links naar rechts Order, Store, Cars. */
    private int[] tabBounds(int tab) {
        int total = 0;
        int[] widths = new int[TABS.length];
        for (int i = 0; i < TABS.length; i++) {
            widths[i] = font.width(Component.translatable("screen.imttech.storage.tab." + TABS[i])) + 12;
            total += widths[i] + (i > 0 ? 3 : 0);
        }
        int tx = leftPos + imageWidth - 8 - total;
        for (int i = 0; i < tab; i++) {
            tx += widths[i] + 3;
        }
        return new int[] {tx, widths[tab]};
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (int tab = 0; tab < TABS.length; tab++) {
            int[] bounds = tabBounds(tab);
            int y = topPos + TAB_Y;
            boolean active = tab == menu.tab();
            boolean hover = mouseX >= bounds[0] && mouseX < bounds[0] + bounds[1] && mouseY >= y && mouseY < y + 14;
            g.fill(bounds[0], y, bounds[0] + bounds[1], y + 14, HudStyle.argb(active ? 255 : hover ? 200 : 140, active ? SLOT : BOX));
            g.renderOutline(bounds[0], y, bounds[1], 14, HudStyle.argb(255, active ? ACCENT : FRAME));
            g.drawString(font, Component.translatable("screen.imttech.storage.tab." + TABS[tab]), bounds[0] + 6, y + 3,
                    HudStyle.argb(active ? 255 : 190, active ? ACCENT : HudStyle.WHITE), false);
        }
    }

    /** Het tabblad Order: links wat er in de opslag zit, rechts wat je koos. */
    private void renderOrder(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + StorageMenu.AREA_X;
        int y = topPos + StorageMenu.AREA_Y;
        List<StorageStatePayload.Entry> shown = shown();
        // Rechts van het zoekvak: hoeveel soorten, en hoeveel kisten de interface kent.
        // Paste niet alles in één bericht (een heel grote opslag), dan zegt hij hoeveel soorten hij toont.
        Component known = state().more() > 0
                ? Component.translatable("screen.imttech.storage.known_part", state().stock().size(), state().stock().size() + state().more())
                : Component.translatable("screen.imttech.storage.known", state().stock().size(), state().storages());
        small(g, known, x + StorageMenu.AREA_W - Math.round(font.width(known) * 0.75f), topPos + SEARCH_Y + 2, HudStyle.argb(190, GREY));
        g.fill(x, y, x + StorageMenu.AREA_W, y + StorageMenu.AREA_H, HudStyle.argb(255, BOX));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, stockRows() - ROWS)));
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int index = (scroll + row) * COLUMNS + column;
                int cx = x + column * CELL;
                int cy = y + row * CELL;
                if (index >= shown.size()) {
                    continue;
                }
                StorageStatePayload.Entry entry = shown.get(index);
                boolean chosen = ItemStack.isSameItemSameComponents(entry.stack(), selected);
                boolean hover = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
                if (chosen || hover) {
                    g.fill(cx, cy, cx + CELL, cy + CELL, HudStyle.argb(chosen ? 90 : 45, ACCENT));
                }
                if (chosen) {
                    g.renderOutline(cx, cy, CELL, CELL, HudStyle.argb(255, ACCENT));
                }
                g.renderItem(entry.stack(), cx + 1, cy + 1);
                // Het aantal klein in de hoek, boven het plaatje.
                String count = compact(entry.count());
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                small(g, Component.literal(count), cx + CELL - 1 - Math.round(font.width(count) * 0.75f), cy + CELL - 7,
                        HudStyle.argb(255, HudStyle.WHITE));
                g.pose().popPose();
            }
        }
        if (shown.isEmpty()) {
            Component none = Component.translatable(state().stock().isEmpty() ? "screen.imttech.storage.empty"
                    : "screen.imttech.storage.no_match");
            smallWrapped(g, none, x + 6, y + 8, StorageMenu.AREA_W - 12, HudStyle.argb(190, GREY));
        }
        bar.set(x + StorageMenu.AREA_W + 1, y, StorageMenu.AREA_H, stockRows(), ROWS, scroll);
        bar.render(g, mouseX, mouseY);
        renderSelection(g, mouseX, mouseY);
    }

    /** De kolom rechts op het tabblad Order: wat je koos, hoeveel, en de knop Order. */
    private void renderSelection(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + StorageMenu.SIDE_X;
        int y = topPos;
        StorageStatePayload.Entry entry = selectedEntry();
        if (entry == null) {
            smallWrapped(g, Component.translatable("screen.imttech.storage.pick"), x, y + 24, StorageMenu.SIDE_W, HudStyle.argb(190, GREY));
            return;
        }
        amount = Math.max(1, Math.min(amount, maxAmount()));
        g.renderItem(entry.stack(), x, y + 22);
        small(g, Component.literal(fitSmall(entry.stack().getHoverName().getString(), StorageMenu.SIDE_W - 20)), x + 20, y + 27,
                HudStyle.argb(255, HudStyle.WHITE));
        small(g, Component.translatable("screen.imttech.storage.in_storage", entry.count()), x, y + 42, HudStyle.argb(210, GREY));
        small(g, entry.places() == 1 ? Component.translatable("screen.imttech.storage.in_chest")
                : Component.translatable("screen.imttech.storage.in_chests", entry.places()), x, y + 51, HudStyle.argb(210, GREY));
        // Het aantal: min, het getal, plus.
        button(g, x, y + AMOUNT_Y, 14, 14, Component.literal("-"), ACCENT, amount > 1, mouseX, mouseY);
        String number = String.valueOf(amount);
        g.drawString(font, number, x + 14 + (36 - font.width(number)) / 2, y + AMOUNT_Y + 3, HudStyle.argb(255, HudStyle.WHITE), false);
        button(g, x + 50, y + AMOUNT_Y, 14, 14, Component.literal("+"), ACCENT, amount < maxAmount(), mouseX, mouseY);
        small(g, Component.translatable("screen.imttech.storage.shift"), x + 68, y + AMOUNT_Y + 4, HudStyle.argb(160, GREY));
        boolean cars = anyLinkedCar();
        button(g, x, y + ORDER_BUTTON_Y, 64, 16, Component.translatable("screen.imttech.storage.order"), HudStyle.GREEN, cars, mouseX,
                mouseY);
        if (!cars) {
            small(g, Component.translatable("screen.imttech.storage.no_car"), x, y + ORDER_BUTTON_Y + 20, HudStyle.argb(230, HudStyle.ORANGE));
        }
    }

    /** Het tabblad Store: de IN-vakjes (die tekent Minecraft zelf), de knop Store, en uitleg. */
    private void renderStore(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + StorageMenu.AREA_X;
        int y = topPos;
        small(g, Component.translatable("screen.imttech.storage.to_store"), x, y + StorageMenu.IN_Y - 9, HudStyle.argb(200, GREY));
        button(g, x, y + StorageMenu.IN_Y + 22, 64, 16, Component.translatable("screen.imttech.storage.store"), HudStyle.GREEN,
                anyLinkedCar(), mouseX, mouseY);
        smallWrapped(g, Component.translatable("screen.imttech.storage.store_hint"), x + 70, y + StorageMenu.IN_Y + 22,
                StorageMenu.AREA_W - 70, HudStyle.argb(190, GREY));
        smallWrapped(g, Component.translatable(anyLinkedCar() ? "screen.imttech.storage.store_help" : "screen.imttech.storage.no_car"),
                leftPos + StorageMenu.SIDE_X, y + 24, StorageMenu.SIDE_W, HudStyle.argb(anyLinkedCar() ? 190 : 230,
                        anyLinkedCar() ? GREY : HudStyle.ORANGE));
    }

    /** Het tabblad Cars: de gekoppelde auto's en de auto's in de buurt, elk met een knop. */
    private void renderCars(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + StorageMenu.AREA_X;
        int y = topPos + StorageMenu.AREA_Y;
        List<StorageStatePayload.Car> cars = state().cars();
        g.fill(x, y, x + StorageMenu.AREA_W, y + StorageMenu.AREA_H, HudStyle.argb(255, BOX));
        carScroll = Math.max(0, Math.min(carScroll, Math.max(0, cars.size() - CAR_ROWS)));
        for (int i = 0; i < CAR_ROWS && carScroll + i < cars.size(); i++) {
            StorageStatePayload.Car car = cars.get(carScroll + i);
            int ry = y + 1 + i * CAR_ROW;
            int colour = !car.linked() ? GREY : car.status() == StorageStatePayload.CAR_READY ? HudStyle.GREEN
                    : car.status() == StorageStatePayload.CAR_BUSY ? HudStyle.CYAN : HudStyle.ORANGE;
            g.fill(x + 3, ry + 4, x + 8, ry + 9, HudStyle.argb(255, colour));
            // Rechts de knop, links daarvan de cel en de letters F en S (heeft zijn programma fetch en store?).
            int buttonX = x + StorageMenu.AREA_W - 40;
            button(g, buttonX, ry, 38, 12, Component.translatable(car.linked() ? "screen.imttech.storage.unlink"
                    : "screen.imttech.storage.link"), car.linked() ? HudStyle.ORANGE : HudStyle.GREEN, true, mouseX, mouseY);
            String battery = car.battery() < 0 ? "" : car.battery() + "%";
            int batteryX = buttonX - 4 - Math.round(font.width(battery) * 0.75f);
            small(g, Component.literal(battery), batteryX, ry + 3, HudStyle.argb(200, GREY));
            int lettersX = batteryX - 16;
            small(g, Component.literal("F"), lettersX, ry + 3, HudStyle.argb(car.fetch() ? 255 : 90, car.fetch() ? HudStyle.GREEN : GREY));
            small(g, Component.literal("S"), lettersX + 7, ry + 3, HudStyle.argb(car.store() ? 255 : 90, car.store() ? HudStyle.GREEN : GREY));
            small(g, Component.literal(fitSmall(car.name(), lettersX - (x + 12) - 3)), x + 12, ry + 3, HudStyle.argb(240, HudStyle.WHITE));
        }
        if (cars.isEmpty()) {
            smallWrapped(g, Component.translatable("screen.imttech.storage.no_cars_near"), x + 6, y + 8, StorageMenu.AREA_W - 12,
                    HudStyle.argb(190, GREY));
        }
        carBar.set(x + StorageMenu.AREA_W + 1, y, StorageMenu.AREA_H, cars.size(), CAR_ROWS, carScroll);
        carBar.render(g, mouseX, mouseY);
        smallWrapped(g, Component.translatable("screen.imttech.storage.cars_help"), leftPos + StorageMenu.SIDE_X, topPos + 24,
                StorageMenu.SIDE_W, HudStyle.argb(190, GREY));
    }

    /** Rechts onderaan (op elk tabblad): de bestellingen, de nieuwste bovenaan. */
    private void renderOrders(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + StorageMenu.SIDE_X;
        int y = topPos + ORDERS_Y;
        List<StorageStatePayload.Order> orders = state().orders();
        small(g, Component.translatable("screen.imttech.storage.orders"), x, y, HudStyle.argb(230, ACCENT));
        g.fill(x - 2, y + 8, x + StorageMenu.SIDE_W, y + 8 + ORDER_ROWS * ORDER_ROW + 2, HudStyle.argb(255, BOX));
        orderScroll = Math.max(0, Math.min(orderScroll, Math.max(0, orders.size() - ORDER_ROWS)));
        for (int i = 0; i < ORDER_ROWS && orderScroll + i < orders.size(); i++) {
            StorageStatePayload.Order order = orders.get(orders.size() - 1 - orderScroll - i);
            int ry = y + 10 + i * ORDER_ROW;
            boolean hover = mouseX >= x - 2 && mouseX < x + StorageMenu.SIDE_W && mouseY >= ry - 1 && mouseY < ry + ORDER_ROW - 1;
            if (hover) {
                g.fill(x - 2, ry - 1, x + StorageMenu.SIDE_W, ry + ORDER_ROW - 1, HudStyle.argb(35, ACCENT));
            }
            StorageOrder.State stateNow = order.stateValue();
            // Rechts het kruisje (niet bij een lopende bestelling), links daarvan de stand; de rest is voor de naam.
            int right = x + StorageMenu.SIDE_W - 2;
            if (stateNow != StorageOrder.State.RUNNING) {
                small(g, Component.literal("x"), right - 5, ry + 1, HudStyle.argb(hover ? 255 : 150, HudStyle.RED));
                right -= 9;
            }
            String status = status(order);
            int statusX = right - Math.round(font.width(status) * 0.75f);
            small(g, Component.literal(status), statusX, ry + 1, HudStyle.argb(230, statusColour(stateNow)));
            String what = (order.store() ? "> " : "") + order.count() + " " + order.stack().getHoverName().getString();
            small(g, Component.literal(fitSmall(what, statusX - x - 4)), x, ry + 1, HudStyle.argb(235, HudStyle.WHITE));
        }
        if (orders.isEmpty()) {
            small(g, Component.translatable("screen.imttech.storage.no_orders"), x + 2, y + 12, HudStyle.argb(150, GREY));
        }
    }

    /** De stand van een bestelling in een paar tekens: "waiting", "Digger 3/8", "done", "3/8", "failed". */
    private static String status(StorageStatePayload.Order order) {
        return switch (order.stateValue()) {
            case WAITING -> Component.translatable("screen.imttech.storage.state.waiting").getString();
            case RUNNING -> order.moved() + "/" + order.count();
            case DONE -> Component.translatable("screen.imttech.storage.state.done").getString();
            case PARTIAL -> order.moved() + "/" + order.count();
            case FAILED -> Component.translatable("screen.imttech.storage.state.failed").getString();
        };
    }

    private static int statusColour(StorageOrder.State state) {
        return switch (state) {
            case WAITING -> GREY;
            case RUNNING -> HudStyle.CYAN;
            case DONE -> HudStyle.GREEN;
            case PARTIAL -> HudStyle.ORANGE;
            case FAILED -> HudStyle.RED;
        };
    }

    /** Een knop die we zelf tekenen: een vlak met een rand en de tekst in het midden. Uit = grijs. */
    private void button(GuiGraphics g, int x, int y, int w, int h, Component text, int colour, boolean enabled, int mouseX, int mouseY) {
        boolean over = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int tint = enabled ? colour : GREY;
        g.fill(x, y, x + w, y + h, HudStyle.argb(over ? 110 : 50, tint));
        g.renderOutline(x, y, w, h, HudStyle.argb(over ? 255 : enabled ? 170 : 90, tint));
        if (h >= 14) {
            g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, HudStyle.argb(enabled ? 255 : 130, HudStyle.WHITE), false);
        } else {
            small(g, text, x + (w - Math.round(font.width(text) * 0.75f)) / 2, y + (h - 6) / 2,
                    HudStyle.argb(enabled ? 255 : 130, HudStyle.WHITE));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Geen vaste teksten: alles staat in renderBg.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        // Een soort in de lijst: zijn gewone tooltip.
        StorageStatePayload.Entry entry = menu.tab() == StorageMenu.TAB_ORDER ? entryAt(mouseX, mouseY) : null;
        if (entry != null) {
            g.renderTooltip(font, entry.stack(), mouseX, mouseY);
        }
        // Een bestelling: waarom hij wacht, of wat er misging.
        StorageStatePayload.Order order = orderAt(mouseX, mouseY);
        if (order != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(order.store() ? "screen.imttech.storage.tip.store" : "screen.imttech.storage.tip.fetch",
                    order.count(), order.stack().getHoverName()));
            if (!order.car().isEmpty()) {
                lines.add(Component.translatable("screen.imttech.storage.tip.car", order.car(), order.moved(), order.count())
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            if (!order.note().isEmpty()) {
                lines.add(Component.literal(order.note()).withStyle(net.minecraft.ChatFormatting.GOLD));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    // ================================================================ Alleen voor de screenshot-rondleiding

    /** Kies de soort met deze naam ("iron_ingot") en dit aantal, alsof je erop klikte. */
    public void tourSelect(String item, int count) {
        for (StorageStatePayload.Entry entry : state().stock()) {
            if (com.senne.imttech.car.CarWork.isItem(entry.stack(), item)) {
                selected = entry.stack().copy();
                amount = Math.max(1, Math.min(count, maxAmount()));
                return;
            }
        }
    }

    /** Ga naar dit tabblad, alsof je erop klikte. */
    public void tourTab(int tab) {
        setTab(tab);
    }

    // ================================================================ Kleine tekst

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

    /** Kleine tekst over meer regels, niet breder dan {@code width} pixels. Geeft de y onder de laatste regel. */
    private int smallWrapped(GuiGraphics g, Component text, int x, int y, int width, int argb) {
        for (FormattedCharSequence line : font.split(text, Math.round(width / 0.75f))) {
            smallSeq(g, line, x, y, argb);
            y += 8;
        }
        return y;
    }

    /** Tekst die niet breder wordt dan {@code room} pixels (anders met drie puntjes). */
    private String fit(String text, int room) {
        if (room <= 0) {
            return "";
        }
        return font.width(text) <= room ? text : font.plainSubstrByWidth(text, Math.max(0, room - font.width("..."))) + "...";
    }

    /** Zoals {@link #fit}, voor kleine tekst (driekwart). */
    private String fitSmall(String text, int room) {
        return fit(text, Math.round(room / 0.75f));
    }

    /** Een aantal in hooguit vier tekens: 64, 1.2k, 12k, 1.2M. */
    static String compact(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        if (count < 10_000) {
            return String.format(Locale.ROOT, "%.1fk", count / 1000.0);
        }
        if (count < 1_000_000) {
            return count / 1000 + "k";
        }
        return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
    }

    // ================================================================ Klikken en typen

    private void click(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    /** De soort onder de muis in de lijst van de opslag, of null. */
    private StorageStatePayload.@Nullable Entry entryAt(double mouseX, double mouseY) {
        int x = leftPos + StorageMenu.AREA_X;
        int y = topPos + StorageMenu.AREA_Y;
        if (mouseX < x || mouseX >= x + COLUMNS * CELL || mouseY < y || mouseY >= y + ROWS * CELL) {
            return null;
        }
        int index = (scroll + (int) ((mouseY - y) / CELL)) * COLUMNS + (int) ((mouseX - x) / CELL);
        List<StorageStatePayload.Entry> shown = shown();
        return index >= 0 && index < shown.size() ? shown.get(index) : null;
    }

    /** De bestelling onder de muis, of null. */
    private StorageStatePayload.@Nullable Order orderAt(double mouseX, double mouseY) {
        int x = leftPos + StorageMenu.SIDE_X;
        int y = topPos + ORDERS_Y + 9;
        if (mouseX < x - 2 || mouseX >= x + StorageMenu.SIDE_W || mouseY < y || mouseY >= y + ORDER_ROWS * ORDER_ROW) {
            return null;
        }
        List<StorageStatePayload.Order> orders = state().orders();
        int index = orders.size() - 1 - orderScroll - (int) ((mouseY - y) / ORDER_ROW);
        return index >= 0 && index < orders.size() ? orders.get(index) : null;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (int tab = 0; tab < TABS.length; tab++) {
            int[] bounds = tabBounds(tab);
            if (inside(mouseX, mouseY, bounds[0], topPos + TAB_Y, bounds[1], 14)) {
                setTab(tab);
                return true;
            }
        }
        // Een bestelling weghalen: het kruisje rechts in zijn regel.
        StorageStatePayload.Order order = orderAt(mouseX, mouseY);
        if (order != null && order.stateValue() != StorageOrder.State.RUNNING
                && mouseX >= leftPos + StorageMenu.SIDE_X + StorageMenu.SIDE_W - 11) {
            PacketDistributor.sendToServer(StorageActionPayload.cancel(order.id()));
            click(0.8f);
            return true;
        }
        if (menu.tab() == StorageMenu.TAB_ORDER && clickOrderTab(mouseX, mouseY)) {
            return true;
        }
        if (menu.tab() == StorageMenu.TAB_STORE && anyLinkedCar()
                && inside(mouseX, mouseY, leftPos + StorageMenu.AREA_X, topPos + StorageMenu.IN_Y + 22, 64, 16)) {
            PacketDistributor.sendToServer(StorageActionPayload.store());
            click(1.0f);
            return true;
        }
        if (menu.tab() == StorageMenu.TAB_CARS && clickCarsTab(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickOrderTab(double mouseX, double mouseY) {
        int dragged = bar.mouseClicked(mouseX, mouseY);
        if (dragged >= 0) {
            scroll = dragged;
            return true;
        }
        StorageStatePayload.Entry entry = entryAt(mouseX, mouseY);
        if (entry != null && menu.getCarried().isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(entry.stack(), selected)) {
                selected = entry.stack().copy();
                amount = 1;
            }
            click(1.2f);
            return true;
        }
        int x = leftPos + StorageMenu.SIDE_X;
        int y = topPos;
        if (selectedEntry() == null) {
            return false;
        }
        int step = Screen.hasShiftDown() ? 16 : 1;
        if (inside(mouseX, mouseY, x, y + AMOUNT_Y, 14, 14)) {
            amount = Math.max(1, amount - step);
            click(1.0f);
            return true;
        }
        if (inside(mouseX, mouseY, x + 50, y + AMOUNT_Y, 14, 14)) {
            amount = Math.min(maxAmount(), amount + step);
            click(1.0f);
            return true;
        }
        if (anyLinkedCar() && inside(mouseX, mouseY, x, y + ORDER_BUTTON_Y, 64, 16)) {
            PacketDistributor.sendToServer(StorageActionPayload.fetch(selected, amount));
            click(1.0f);
            return true;
        }
        return false;
    }

    private boolean clickCarsTab(double mouseX, double mouseY) {
        int dragged = carBar.mouseClicked(mouseX, mouseY);
        if (dragged >= 0) {
            carScroll = dragged;
            return true;
        }
        int x = leftPos + StorageMenu.AREA_X;
        int y = topPos + StorageMenu.AREA_Y;
        List<StorageStatePayload.Car> cars = state().cars();
        for (int i = 0; i < CAR_ROWS && carScroll + i < cars.size(); i++) {
            if (inside(mouseX, mouseY, x + StorageMenu.AREA_W - 40, y + 1 + i * CAR_ROW, 38, 12)) {
                StorageStatePayload.Car car = cars.get(carScroll + i);
                PacketDistributor.sendToServer(StorageActionPayload.link(car.link(), !car.linked()));
                click(1.0f);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int dragged = menu.tab() == StorageMenu.TAB_CARS ? carBar.mouseDragged(mouseY) : bar.mouseDragged(mouseY);
        if (dragged >= 0) {
            if (menu.tab() == StorageMenu.TAB_CARS) {
                carScroll = dragged;
            } else {
                scroll = dragged;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        bar.mouseReleased();
        carBar.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = -(int) Math.signum(scrollY);
        int x = leftPos + StorageMenu.AREA_X;
        int y = topPos + StorageMenu.AREA_Y;
        if (inside(mouseX, mouseY, x, y, StorageMenu.AREA_W + 8, StorageMenu.AREA_H)) {
            if (menu.tab() == StorageMenu.TAB_ORDER) {
                scroll = Math.max(0, Math.min(Math.max(0, stockRows() - ROWS), scroll + step));
                return true;
            }
            if (menu.tab() == StorageMenu.TAB_CARS) {
                carScroll = Math.max(0, Math.min(Math.max(0, state().cars().size() - CAR_ROWS), carScroll + step));
                return true;
            }
        }
        // Het wieltje op het aantal: één meer of minder (met shift: zestien).
        if (menu.tab() == StorageMenu.TAB_ORDER && selectedEntry() != null
                && inside(mouseX, mouseY, leftPos + StorageMenu.SIDE_X, topPos + AMOUNT_Y, 64, 14)) {
            amount = Math.max(1, Math.min(maxAmount(), amount - step * (Screen.hasShiftDown() ? 16 : 1)));
            return true;
        }
        if (inside(mouseX, mouseY, leftPos + StorageMenu.SIDE_X - 2, topPos + ORDERS_Y, StorageMenu.SIDE_W + 2, ORDER_ROWS * ORDER_ROW + 12)) {
            orderScroll = Math.max(0, Math.min(Math.max(0, state().orders().size() - ORDER_ROWS), orderScroll + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Typ je in het zoekvak, dan sluit de inventaristoets (E) het scherm niet (zoals bij een aambeeld). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
            return true;
        }
        if (search != null && search.isFocused() && (search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
