package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.logic.InputDeviceBlockEntity;
import com.senne.imttech.security.os.DesktopRights;
import com.senne.imttech.security.os.SystemPassword;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * De app Computers op de Security OS-desktop (hoofdstuk 40, jouw wens: "configureren via de security core wat je exact op
 * die desktop mag en kan doen"). Links de computers op het netwerk van deze kern, rechts wat je van de gekozen computer
 * instelt:
 * <pre>
 *  COMPUTERS       │ Reception                                   [Rename]
 *  ● Reception     │ Working
 *  ● Office        │ Who    [Keycard group] [<] staff [>]
 *                  │ Code   set                        [Change] [Clear]
 *                  │ APPS                          click: Off, View, Use
 *                  │ Status      [View]   Logic        [Off]
 *                  │ Cameras     [View]   Monitor      [Off]
 *                  │ Devices     [Use]    ...
 * </pre>
 * Elke knop gaat meteen naar de server (die kijkt na of het mag en zet het in het logboek). Off = de app staat er niet op;
 * View = alleen kijken; Use = ook bedienen. Status, Logs en Monitor hebben geen knoppen: die zijn Off of View.
 */
final class ComputersApp {
    private static final int LIST_W = 112;
    private static final int ROW_H = 14;
    private static final int APP_ROWS = 7;
    private static final int APP_H = 13;
    private static final int LABEL_W = 40;
    private static final int TITLE_COLOUR = 0x6F8C99;

    /** Wat je aan het typen bent: niets, een naam of een code. */
    private enum Editing { NONE, NAME, CODE }

    private final SecurityOsScreen screen;
    private @Nullable BlockPos selected;
    private int scroll;
    private Editing editing = Editing.NONE;
    private @Nullable EditBox nameBox;
    private @Nullable EditBox codeBox;

    ComputersApp(SecurityOsScreen screen) {
        this.screen = screen;
    }

    // ================================================================ De gegevens van de server

    private List<CompoundTag> computers() {
        List<CompoundTag> list = new ArrayList<>();
        for (Tag element : screen.data().getList("computers", Tag.TAG_COMPOUND)) {
            list.add((CompoundTag) element);
        }
        return list;
    }

    private static BlockPos posOf(CompoundTag computer) {
        return new BlockPos(computer.getInt("x"), computer.getInt("y"), computer.getInt("z"));
    }

    /** De gekozen computer (de eerste als je nog niets koos, of als de gekozen weg is), of null als er geen is. */
    private @Nullable CompoundTag current() {
        List<CompoundTag> list = computers();
        for (CompoundTag computer : list) {
            if (posOf(computer).equals(selected)) {
                return computer;
            }
        }
        if (list.isEmpty()) {
            return null;
        }
        selected = posOf(list.getFirst());
        editing = Editing.NONE;
        return list.getFirst();
    }

    /** De keycard-groepen van deze kern (voor "Keycard group"), op alfabet. */
    private List<String> cardGroups() {
        List<String> groups = new ArrayList<>();
        for (Tag element : screen.data().getList("cardGroups", Tag.TAG_COMPOUND)) {
            groups.add(((CompoundTag) element).getString("n"));
        }
        return groups;
    }

    private static DesktopRights.Access accessOf(CompoundTag computer, String app) {
        return DesktopRights.load(computer.getCompound("a")).access(app);
    }

    /** "STATUS" wordt "Status". */
    private static String label(String app) {
        return app.charAt(0) + app.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private static String accessLabel(DesktopRights.Access access) {
        return switch (access) {
            case OFF -> "Off";
            case VIEW -> "View";
            case USE -> "Use";
        };
    }

    private static int accessColour(DesktopRights.Access access) {
        return switch (access) {
            case OFF -> OsDraw.GREY;
            case VIEW -> HudStyle.CYAN;
            case USE -> HudStyle.GREEN;
        };
    }

    private static String whoLabel(String who) {
        return switch (who) {
            case "EVERYONE" -> "Everyone";
            case "GROUP" -> "Keycard group";
            default -> "Trusted";
        };
    }

    // ================================================================ Plekken (tekenen en klikken gebruiken dezelfde)

    private static int rightX(SecurityOsScreen.Area a) {
        return a.x() + LIST_W + 8;
    }

    private static int rightW(SecurityOsScreen.Area a) {
        return a.x() + a.w() - rightX(a);
    }

    /** De knoppen op de regel "Who": {x van de keuze, x van <, x van >} (de pijltjes alleen bij een keycard-groep). */
    private int[] whoButtons(Font font, SecurityOsScreen.Area a, CompoundTag computer) {
        int x = rightX(a) + LABEL_W;
        int prev = x + OsDraw.buttonWidth(font, whoLabel(computer.getString("w"))) + 4;
        int next = prev + OsDraw.buttonWidth(font, "<") + 4 + font.width(groupShown(font, computer)) + 4;
        return new int[] {x, prev, next};
    }

    private String groupShown(Font font, CompoundTag computer) {
        String group = computer.getString("g");
        return OsDraw.fitRight(font, group.isEmpty() ? "-" : group, 70);
    }

    /** Het vak van één app in het rooster: {x, y, breedte}. */
    private static int[] appCell(SecurityOsScreen.Area a, int index) {
        // Zoveel kolommen als nodig, zeven rijen per kolom. Met twee vaste kolommen stond de derde (Notes en Logs, die
        // later bij kwamen) half buiten het venster (gezien op een plaatje van How it works).
        int columns = Math.max(1, (DesktopRights.COMPUTER_APPS.size() + APP_ROWS - 1) / APP_ROWS);
        int cellW = rightW(a) / columns;
        int x = rightX(a) + (index / APP_ROWS) * cellW;
        int y = a.y() + 78 + (index % APP_ROWS) * APP_H;
        return new int[] {x, y, cellW};
    }

    // ================================================================ Tekenen

    void render(GuiGraphics g, Font font, SecurityOsScreen.Area a, int mx, int my, float partialTick) {
        List<CompoundTag> list = computers();
        if (list.isEmpty()) {
            int y = a.y() + 2;
            for (String line : new String[] {"No computers on this network yet.", "",
                    "1. Place a Computer and put a Monitor against it.",
                    "2. Lay a network cable (or PoE cable) from the",
                    "   computer to this security core.",
                    "3. Give the computer power. Then choose here",
                    "   what people may do on it."}) {
                g.drawString(font, line, a.x() + 2, y, HudStyle.argb(255, line.startsWith("No") ? HudStyle.WHITE : OsDraw.GREY), false);
                y += 11;
            }
            return;
        }
        CompoundTag computer = current();
        renderList(g, font, a, list);
        if (computer != null) {
            renderDetails(g, font, a, computer, mx, my, partialTick);
        }
    }

    private void renderList(GuiGraphics g, Font font, SecurityOsScreen.Area a, List<CompoundTag> list) {
        g.drawString(font, "COMPUTERS", a.x() + 2, a.y() + 1, HudStyle.argb(255, TITLE_COLOUR), false);
        g.fill(a.x(), a.y() + 11, a.x() + LIST_W, a.y() + 12, HudStyle.argb(255, OsDraw.DIM));
        g.fill(a.x() + LIST_W + 3, a.y(), a.x() + LIST_W + 4, a.y() + a.h(), HudStyle.argb(255, OsDraw.DIM));
        int rows = Math.max(1, (a.h() - 14) / ROW_H);
        scroll = Math.max(0, Math.min(scroll, list.size() - rows));
        for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
            CompoundTag computer = list.get(i);
            int y = a.y() + 14 + (i - scroll) * ROW_H;
            if (posOf(computer).equals(selected)) {
                g.fill(a.x(), y, a.x() + LIST_W, y + ROW_H - 1, HudStyle.argb(255, 0x16303F));
            }
            boolean working = computer.getString("p").isEmpty();
            g.fill(a.x() + 3, y + 5, a.x() + 7, y + 9, HudStyle.argb(255, working ? HudStyle.GREEN : HudStyle.RED));
            g.drawString(font, OsDraw.fitRight(font, computer.getString("n"), LIST_W - 14), a.x() + 11, y + 3,
                    HudStyle.argb(255, HudStyle.WHITE), false);
        }
    }

    private void renderDetails(GuiGraphics g, Font font, SecurityOsScreen.Area a, CompoundTag computer, int mx, int my,
                               float partialTick) {
        int x = rightX(a);
        int w = rightW(a);
        int y = a.y();
        // De naam, met rechts Rename (of het vak waar je de nieuwe naam typt).
        if (editing == Editing.NAME) {
            OsDraw.field(g, x, y, x + w - 2, y + 12, true);
            EditBox box = nameBox();
            box.setX(x + 3);
            box.setY(y + 2);
            box.setWidth(w - 8);
            box.render(g, mx, my, partialTick);
        } else {
            g.drawString(font, OsDraw.fitRight(font, computer.getString("n"), w - 60), x, y + 2, HudStyle.argb(255, HudStyle.WHITE), false);
            OsDraw.button(g, font, x + w - OsDraw.buttonWidth(font, "Rename") - 2, y, "Rename", true, HudStyle.CYAN);
        }
        // Werkt hij?
        String problem = computer.getString("p");
        String status = editing != Editing.NONE ? "Enter: save, Esc: cancel" : problem.isEmpty() ? "Working" : problem;
        int statusColour = editing != Editing.NONE ? OsDraw.GREY : problem.isEmpty() ? HudStyle.GREEN : HudStyle.ORANGE;
        g.drawString(font, OsDraw.fitRight(font, status, w - 2), x, y + 16, HudStyle.argb(255, statusColour), false);
        // Wie: Everyone, Trusted of Keycard group (met de groep en pijltjes).
        g.drawString(font, "Who", x, y + 32, HudStyle.argb(255, OsDraw.GREY), false);
        int[] who = whoButtons(font, a, computer);
        OsDraw.button(g, font, who[0], y + 30, whoLabel(computer.getString("w")), true, HudStyle.CYAN);
        if (computer.getString("w").equals("GROUP")) {
            OsDraw.button(g, font, who[1], y + 30, "<", true, HudStyle.CYAN);
            g.drawString(font, groupShown(font, computer), who[1] + OsDraw.buttonWidth(font, "<") + 4, y + 32,
                    HudStyle.argb(255, HudStyle.WHITE), false);
            OsDraw.button(g, font, who[2], y + 30, ">", true, HudStyle.CYAN);
        }
        // De code: "set" of "none", met Set code / Change en Clear (of het vak waar je hem typt: sterretjes).
        g.drawString(font, "Code", x, y + 48, HudStyle.argb(255, OsDraw.GREY), false);
        boolean hasCode = computer.getBoolean("c");
        if (editing == Editing.CODE) {
            OsDraw.field(g, x + LABEL_W, y + 46, x + LABEL_W + 80, y + 58, true);
            EditBox box = codeBox();
            box.setX(x + LABEL_W + 3);
            box.setY(y + 48);
            box.setWidth(74);
            box.render(g, mx, my, partialTick);
            g.drawString(font, "4-8: a-z, 0-9", x + LABEL_W + 86, y + 48, HudStyle.argb(255, OsDraw.GREY), false);
        } else {
            g.drawString(font, hasCode ? "set" : "none", x + LABEL_W, y + 48,
                    HudStyle.argb(255, hasCode ? HudStyle.GREEN : OsDraw.GREY), false);
            int bx = x + LABEL_W + 34;
            bx += OsDraw.button(g, font, bx, y + 46, hasCode ? "Change" : "Set code", true, HudStyle.CYAN) + 4;
            if (hasCode) {
                OsDraw.button(g, font, bx, y + 46, "Clear", true, HudStyle.RED);
            }
        }
        // De apps, in kolommen van zeven (zie appCell).
        g.drawString(font, "APPS", x, y + 64, HudStyle.argb(255, TITLE_COLOUR), false);
        String hint = "click: Off, View, Use";
        g.drawString(font, hint, x + w - font.width(hint) - 2, y + 64, HudStyle.argb(255, OsDraw.GREY), false);
        g.fill(x, y + 74, x + w - 2, y + 75, HudStyle.argb(255, OsDraw.DIM));
        List<String> apps = DesktopRights.COMPUTER_APPS;
        for (int i = 0; i < apps.size(); i++) {
            int[] cell = appCell(a, i);
            DesktopRights.Access access = accessOf(computer, apps.get(i));
            String button = accessLabel(access);
            int bw = OsDraw.buttonWidth(font, button);
            // De naam mag tot vlak voor de breedste knop (View), dus hij wordt niet korter als je klikt. Met een vaste 40
            // werden Messages en Keycards in drie kolommen net afgekapt.
            int labelW = cell[2] - OsDraw.buttonWidth(font, accessLabel(DesktopRights.Access.VIEW)) - 8;
            g.drawString(font, OsDraw.fitRight(font, label(apps.get(i)), labelW), cell[0], cell[1] + 2,
                    HudStyle.argb(255, access == DesktopRights.Access.OFF ? OsDraw.GREY : HudStyle.WHITE), false);
            OsDraw.button(g, font, cell[0] + cell[2] - bw - 6, cell[1], button, true, accessColour(access));
        }
    }

    // ================================================================ Klikken

    void mouseClicked(Font font, SecurityOsScreen.Area a, int mx, int my) {
        List<CompoundTag> list = computers();
        // Links: een computer kiezen.
        if (mx < a.x() + LIST_W) {
            int index = scroll + (my - a.y() - 14) / ROW_H;
            if (my >= a.y() + 14 && index >= 0 && index < list.size()) {
                selected = posOf(list.get(index));
                editing = Editing.NONE;
            }
            return;
        }
        CompoundTag computer = current();
        if (computer == null) {
            return;
        }
        int x = rightX(a);
        int w = rightW(a);
        int y = a.y();
        if (editing == Editing.NAME && OsDraw.inside(mx, my, x, y, w, 12)) {
            nameBox().mouseClicked(mx, my, 0);
            return;
        }
        if (editing == Editing.CODE && OsDraw.inside(mx, my, x + LABEL_W, y + 46, 80, 12)) {
            codeBox().mouseClicked(mx, my, 0);
            return;
        }
        editing = Editing.NONE; // ergens anders geklikt: wat je typte, blijft zoals het was
        int renameW = OsDraw.buttonWidth(font, "Rename");
        if (OsDraw.inside(mx, my, x + w - renameW - 2, y, renameW, OsDraw.BUTTON_H)) {
            startEditing(Editing.NAME, computer.getString("n"));
            return;
        }
        // Wie.
        int[] who = whoButtons(font, a, computer);
        String current = computer.getString("w");
        if (OsDraw.inside(mx, my, who[0], y + 30, OsDraw.buttonWidth(font, whoLabel(current)), OsDraw.BUTTON_H)) {
            String next = switch (current) {
                case "EVERYONE" -> "TRUSTED";
                case "TRUSTED" -> "GROUP";
                default -> "EVERYONE";
            };
            List<String> groups = cardGroups();
            sendWho(computer, next, groups.isEmpty() ? "" : groups.getFirst());
            return;
        }
        if (current.equals("GROUP")) {
            int step = OsDraw.inside(mx, my, who[1], y + 30, OsDraw.buttonWidth(font, "<"), OsDraw.BUTTON_H) ? -1
                    : OsDraw.inside(mx, my, who[2], y + 30, OsDraw.buttonWidth(font, ">"), OsDraw.BUTTON_H) ? 1 : 0;
            List<String> groups = cardGroups();
            if (step != 0 && !groups.isEmpty()) {
                int at = Math.max(0, groups.indexOf(computer.getString("g")));
                sendWho(computer, "GROUP", groups.get(Math.floorMod(at + step, groups.size())));
                return;
            }
        }
        // De code.
        boolean hasCode = computer.getBoolean("c");
        int bx = x + LABEL_W + 34;
        int setW = OsDraw.buttonWidth(font, hasCode ? "Change" : "Set code");
        if (OsDraw.inside(mx, my, bx, y + 46, setW, OsDraw.BUTTON_H)) {
            startEditing(Editing.CODE, "");
            return;
        }
        if (hasCode && OsDraw.inside(mx, my, bx + setW + 4, y + 46, OsDraw.buttonWidth(font, "Clear"), OsDraw.BUTTON_H)) {
            sendCode(computer, "");
            return;
        }
        // De apps: Off -> View -> Use -> Off (zonder knoppen: Off -> View -> Off).
        List<String> apps = DesktopRights.COMPUTER_APPS;
        for (int i = 0; i < apps.size(); i++) {
            int[] cell = appCell(a, i);
            DesktopRights.Access access = accessOf(computer, apps.get(i));
            int bw = OsDraw.buttonWidth(font, accessLabel(access));
            if (OsDraw.inside(mx, my, cell[0] + cell[2] - bw - 6, cell[1], bw, OsDraw.BUTTON_H)) {
                DesktopRights.Access next = switch (access) {
                    case OFF -> DesktopRights.Access.VIEW;
                    case VIEW -> DesktopRights.LOOK_ONLY.contains(apps.get(i)) ? DesktopRights.Access.OFF : DesktopRights.Access.USE;
                    case USE -> DesktopRights.Access.OFF;
                };
                CompoundTag args = posArgs(computer);
                args.putString("app", apps.get(i));
                args.putInt("access", next.ordinal());
                screen.send("pc_app", args);
                return;
            }
        }
    }

    void mouseScrolled(double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
    }

    private static CompoundTag posArgs(CompoundTag computer) {
        CompoundTag args = new CompoundTag();
        args.putInt("x", computer.getInt("x"));
        args.putInt("y", computer.getInt("y"));
        args.putInt("z", computer.getInt("z"));
        return args;
    }

    private void sendWho(CompoundTag computer, String who, String group) {
        CompoundTag args = posArgs(computer);
        args.putString("who", who);
        args.putString("group", group);
        screen.send("pc_who", args);
    }

    private void sendCode(CompoundTag computer, String code) {
        CompoundTag args = posArgs(computer);
        args.putString("code", code);
        screen.send("pc_code", args);
    }

    // ================================================================ Typen (een naam of een code)

    private EditBox nameBox() {
        if (nameBox == null) {
            nameBox = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 10, Component.literal("Computer name"));
            nameBox.setBordered(false);
            nameBox.setMaxLength(InputDeviceBlockEntity.MAX_NAME);
            // Dezelfde tekens die de server weghaalt (zie InputDeviceBlockEntity.cleanName): wat je ziet, wordt de naam.
            nameBox.setFilter(value -> value.indexOf('§') < 0 && value.indexOf('"') < 0 && value.indexOf('\\') < 0);
            nameBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
        }
        return nameBox;
    }

    /** Het vak voor de code: alleen a-z en 0-9, hooguit 8 tekens, en je ziet sterretjes. */
    private EditBox codeBox() {
        if (codeBox == null) {
            codeBox = new EditBox(Minecraft.getInstance().font, 0, 0, 74, 10, Component.literal("Code"));
            codeBox.setBordered(false);
            codeBox.setMaxLength(SystemPassword.MAX_LENGTH);
            codeBox.setFilter(value -> value.chars().allMatch(c -> c >= 'a' && c <= 'z' || c >= '0' && c <= '9'));
            codeBox.setFormatter((text, start) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
            codeBox.setTextColor(HudStyle.argb(255, HudStyle.WHITE));
        }
        return codeBox;
    }

    private void startEditing(Editing what, String value) {
        EditBox box = what == Editing.NAME ? nameBox() : codeBox();
        box.setValue(value);
        box.moveCursorToEnd(false);
        box.setHighlightPos(0); // alles geselecteerd: typen vervangt de oude naam
        box.setFocused(true);
        editing = what;
    }

    /** Enter: de naam of de code naar de server (die maakt hem netjes, en zegt het als hij niet mag). */
    private void submit() {
        CompoundTag computer = current();
        Editing what = editing;
        editing = Editing.NONE;
        if (computer == null) {
            return;
        }
        if (what == Editing.NAME) {
            CompoundTag args = posArgs(computer);
            args.putString("name", nameBox().getValue());
            screen.send("pc_rename", args);
        } else if (what == Editing.CODE && !codeBox().getValue().isEmpty()) {
            sendCode(computer, codeBox().getValue());
        }
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editing == Editing.NONE) {
            return false;
        }
        switch (keyCode) {
            case 257, 335 -> submit(); // enter
            case 256 -> editing = Editing.NONE; // esc
            default -> (editing == Editing.NAME ? nameBox() : codeBox()).keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (editing == Editing.NONE) {
            return false;
        }
        (editing == Editing.NAME ? nameBox() : codeBox()).charTyped(codePoint, modifiers);
        return true;
    }

    /** Een ander venster kwam bovenop, of dit venster ging dicht: stop met typen. */
    void blur() {
        editing = Editing.NONE;
    }

    // ================================================================ Voor de screenshots en de showcase

    /** Alleen voor de screenshot-rondleiding: deze computer kiezen. */
    void tourSelect(BlockPos pos) {
        selected = pos;
        editing = Editing.NONE;
    }
}
