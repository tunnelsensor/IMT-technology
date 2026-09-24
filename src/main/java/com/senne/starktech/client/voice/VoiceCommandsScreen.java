package com.senne.starktech.client.voice;

import com.senne.starktech.client.ScrollBar;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;

/**
 * Het scherm "Spraakcommando's" (G > Systemen > Commando's): hier bewerk je het woordenboek van
 * E.D.I.T.H. terwijl je speelt.
 *
 * Tabblad Commando's: links alle commando's (met zoekveld), rechts het gekozen commando:
 * - de naam
 * - de acties (klik op een actie om een andere te kiezen, × om hem weg te halen)
 * - wat E.D.I.T.H. zegt (mag leeg)
 * - de zinnen die het starten, één per regel. Een zin mag eindigen op {plek}, {naam} of {tekst}:
 *   wat je daar zegt, wordt de waarde van de actie.
 *
 * Tabblad Synoniemen: één regel per woord, bv. "drones = drone, zwerm, swarm". Alle woorden op
 * een regel betekenen voor E.D.I.T.H. hetzelfde. Daaronder de woorden die ze negeert.
 *
 * Onderaan kun je een zin testen: je ziet meteen welk commando E.D.I.T.H. zou kiezen.
 * Je wijzigingen worden opgeslagen als je op Opslaan of Klaar klikt (ook met Esc).
 *
 * Past het gekozen commando niet in beeld (veel acties, een klein scherm), dan krijgt de
 * rechterkant een scrollbalk: scrollen met het wieltje of de balk slepen. De lijst links heeft
 * er ook een als er meer commando's zijn dan er passen.
 */
public class VoiceCommandsScreen extends Screen {
    private enum Tab { COMMANDS, SYNONYMS }

    private static final int ROW_H = 14;
    /** Het vak met de zinnen wordt nooit lager dan dit (anders zie je er niets in). */
    private static final int MIN_PHRASES_H = 60;
    /** Eén tik aan het wieltje scrolt de rechterkant zoveel pixels (één regel met een actie). */
    private static final int EDITOR_STEP = 18;
    private static final int WHITE = 0xFFE8FBFF;
    private static final int CYAN = 0xFF3FD8FF;
    private static final int GREY = 0xFF8A9BA0;
    private static final int GREEN = 0xFF5CFF9D;
    private static final int ORANGE = 0xFFFFA93F;

    private final Screen parent;
    private Tab tab = Tab.COMMANDS;
    private VoiceDictionary working;
    private int selected;
    private int listScroll;
    private String filter = "";
    private String testText = "";
    private String synonymsText;
    private String ignoreText;
    private boolean dirty;
    private Component status = Component.empty();
    private long statusUntil;
    private VoiceMatcher testMatcher;
    private Component testResult = Component.empty();

    // Plekken van de teksten, uitgerekend in init()
    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int listW;
    private int rightX;
    private int nameLabelY;
    private int actionsLabelY;
    private int hintY;
    private int replyLabelY;
    private int phrasesLabelY;
    private int testY;
    private final List<Integer> visible = new ArrayList<>();
    /** De lijst links: hoeveel regels er passen, en zijn scrollbalk. */
    private int listRows;
    private final ScrollBar listBar = new ScrollBar();
    /** De rechterkant (het gekozen commando): hoe ver gescrold, waar hij begint en eindigt, en zijn balk. */
    private int editorScroll;
    private int editorY0;
    private int editorY1;
    private final ScrollBar editorBar = new ScrollBar();

    public VoiceCommandsScreen(Screen parent) {
        super(Component.translatable("screen.starktech.voice_edit"));
        this.parent = parent;
        this.working = VoiceCommandStore.dictionary().copy();
        loadSynonymTexts();
    }

    // ---------------------------------------------------------------- Opbouw

    @Override
    protected void init() {
        panelW = Math.min(width - 16, 480);
        panelH = Math.min(height - 16, 310);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        listW = Math.min(150, panelW / 3);
        rightX = left + listW + 10;

        // Tabbladen
        addRenderableWidget(Button.builder(tabLabel(Tab.COMMANDS), b -> switchTab(Tab.COMMANDS)).bounds(left, top, 100, 16).build());
        addRenderableWidget(Button.builder(tabLabel(Tab.SYNONYMS), b -> switchTab(Tab.SYNONYMS)).bounds(left + 104, top, 100, 16).build());

        // Onderaan: testen, standaard herstellen, opslaan, klaar
        int bottom = top + panelH - 20;
        testY = bottom - 24;
        EditBox test = new EditBox(font, left + 70, testY, Math.max(80, panelW / 2 - 70), 18, Component.translatable("screen.starktech.voice_edit.test"));
        test.setMaxLength(200);
        test.setValue(testText);
        test.setHint(Component.translatable("screen.starktech.voice_edit.test_hint"));
        test.setResponder(value -> {
            testText = value;
            updateTest();
        });
        addRenderableWidget(test);
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.voice_edit.reset"), b -> confirmReset())
                .bounds(left, bottom, 130, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.voice_edit.save"), b -> save(true))
                .bounds(left + panelW - 164, bottom, 80, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(left + panelW - 80, bottom, 80, 20).build());

        if (tab == Tab.COMMANDS) {
            initCommands(top + 22, testY - 6);
        } else {
            initSynonyms(top + 22, testY - 6);
        }
    }

    private void initCommands(int y0, int y1) {
        // --- Links: zoekveld, lijst, nieuw/verwijder
        EditBox search = new EditBox(font, left, y0, listW, 16, Component.translatable("screen.starktech.voice_edit.search"));
        search.setMaxLength(50);
        search.setValue(filter);
        search.setHint(Component.translatable("screen.starktech.voice_edit.search"));
        search.setResponder(value -> {
            filter = value;
            listScroll = 0;
            rebuildWidgets();
            setFocused(findSearchBox());
        });
        addRenderableWidget(search);

        updateVisible();
        int listTop = y0 + 20;
        int rows = Math.max(1, (y1 - 22 - listTop) / ROW_H);
        listRows = rows;
        listScroll = Math.max(0, Math.min(listScroll, visible.size() - rows));
        listBar.set(left + listW - ScrollBar.WIDTH, listTop, rows * ROW_H - 1, visible.size(), rows, listScroll);
        // Is er een balk nodig, dan worden de knoppen iets smaller zodat hij ernaast past.
        int buttonW = listBar.needed() ? listW - ScrollBar.WIDTH - 2 : listW;
        for (int row = 0; row < rows && listScroll + row < visible.size(); row++) {
            int index = visible.get(listScroll + row);
            VoiceDictionary.Command command = working.commands.get(index);
            String name = command.name.isBlank() ? "?" : command.name;
            Component label = Component.literal((index == selected ? "» " : "") + name);
            addRenderableWidget(Button.builder(label, b -> {
                selected = index;
                rebuildWidgets();
            }).bounds(left, listTop + row * ROW_H, buttonW, ROW_H - 1).build());
        }
        int half = (listW - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.voice_edit.new"), b -> addCommand())
                .bounds(left, y1 - 18, half, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.voice_edit.delete"), b -> confirmDelete())
                .bounds(left + half + 4, y1 - 18, half, 18).build());

        // --- Rechts: het gekozen commando, in een kolom die scrolt als hij niet in beeld past
        editorY0 = y0;
        editorY1 = y1;
        if (selected < 0 || selected >= working.commands.size()) {
            editorBar.set(0, 0, 0, 0, 0, 0);
            return;
        }
        VoiceDictionary.Command command = working.commands.get(selected);
        String last = VoiceControl.lastUnknown();
        // Eerst uitrekenen hoe hoog alles samen is. Past het, dan krijgen de zinnen de rest van
        // de ruimte; past het niet, dan krijgen ze MIN_PHRASES_H en komt er een scrollbalk.
        int view = y1 - y0;
        int fixed = 20 + command.actions.size() * 18 + (command.actions.size() < 4 ? 18 : 0)
                + (needsSlotHint(command) ? 12 : 0) + 2 + 22 + 11 + (last.isEmpty() ? 0 : 20);
        int phrasesH = Math.max(MIN_PHRASES_H, view - fixed);
        int content = fixed + phrasesH;
        editorScroll = Math.max(0, Math.min(editorScroll, content - view));
        editorBar.set(left + panelW - ScrollBar.WIDTH, y0, view, content, view, editorScroll);
        int rightW = left + panelW - rightX - (editorBar.needed() ? ScrollBar.WIDTH + 3 : 0);
        int y = y0 - editorScroll; // waar de kolom op het scherm begint (boven de rand als je gescrold hebt)

        nameLabelY = y + 4;
        if (inEditor(y, 16)) {
            EditBox name = new EditBox(font, rightX + 60, y, rightW - 60, 16, Component.translatable("screen.starktech.voice_edit.name"));
            name.setMaxLength(40);
            name.setValue(command.name);
            name.setResponder(value -> {
                command.name = value;
                changed();
            });
            addRenderableWidget(name);
        }
        y += 20;

        actionsLabelY = y + 4;
        for (int i = 0; i < command.actions.size(); i++) {
            int actionIndex = i;
            VoiceDictionary.ActionRef ref = command.actions.get(i);
            int actionW = ref.action().needsValue() ? (rightW - 60) / 2 : rightW - 60 - 20;
            if (inEditor(y, 16)) {
                addRenderableWidget(Button.builder(Component.translatable(ref.action().translationKey()),
                                b -> pickAction(command, actionIndex))
                        .bounds(rightX + 60, y, actionW, 16).build());
                if (ref.action().needsValue()) {
                    EditBox value = new EditBox(font, rightX + 62 + actionW, y, rightW - 60 - actionW - 22, 16,
                            Component.translatable("screen.starktech.voice_edit.value"));
                    value.setMaxLength(100);
                    value.setValue(ref.value());
                    value.setHint(Component.translatable("screen.starktech.voice_edit.value_hint"));
                    value.setResponder(text -> {
                        command.actions.set(actionIndex, new VoiceDictionary.ActionRef(command.actions.get(actionIndex).action(), text));
                        changed();
                    });
                    addRenderableWidget(value);
                }
                addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                    command.actions.remove(actionIndex);
                    changed();
                    rebuildWidgets();
                }).bounds(rightX + rightW - 18, y, 18, 16).build());
            }
            y += 18;
        }
        if (command.actions.size() < 4) {
            if (inEditor(y, 16)) {
                addRenderableWidget(Button.builder(Component.translatable("screen.starktech.voice_edit.add_action"),
                        b -> pickAction(command, -1)).bounds(rightX + 60, y, 90, 16).build());
            }
            y += 18;
        }
        hintY = y + 2;
        if (needsSlotHint(command)) {
            y += 12;
        }
        y += 2;

        replyLabelY = y + 4;
        if (inEditor(y, 16)) {
            EditBox reply = new EditBox(font, rightX + 60, y, rightW - 60, 16, Component.translatable("screen.starktech.voice_edit.reply"));
            reply.setMaxLength(200);
            // Antwoorden uit het standaard-woordenboek zijn vertaalsleutels (@...): toon de echte tekst.
            reply.setValue(command.reply.startsWith("@") ? Component.translatable(command.reply.substring(1)).getString() : command.reply);
            reply.setHint(Component.translatable("screen.starktech.voice_edit.reply_hint"));
            reply.setResponder(value -> {
                command.reply = value;
                changed();
            });
            addRenderableWidget(reply);
        }
        y += 22;

        phrasesLabelY = y;
        y += 11;
        // Het zinnenvak scrolt zelf ook; we laten het stuk zien dat binnen de kolom valt.
        int boxTop = Math.max(y, y0);
        int boxBottom = Math.min(y + phrasesH, y1);
        if (boxBottom - boxTop >= 24) {
            MultiLineEditBox phrases = new MultiLineEditBox(font, rightX, boxTop, rightW, boxBottom - boxTop,
                    Component.translatable("screen.starktech.voice_edit.phrases_hint"),
                    Component.translatable("screen.starktech.voice_edit.phrases"));
            phrases.setCharacterLimit(20000);
            phrases.setValue(String.join("\n", command.phrases));
            phrases.setValueListener(value -> {
                command.phrases.clear();
                Arrays.stream(value.split("\n")).map(String::strip).filter(line -> !line.isEmpty()).forEach(command.phrases::add);
                changed();
            });
            addRenderableWidget(phrases);
        }
        y += phrasesH + 2;
        if (!last.isEmpty() && inEditor(y, 18)) {
            String shown = last.length() > 40 ? last.substring(0, 40) + "…" : last;
            addRenderableWidget(Button.builder(Component.translatable("screen.starktech.voice_edit.add_last", shown), b -> {
                command.phrases.add(last);
                changed();
                rebuildWidgets();
            }).bounds(rightX, y, rightW, 18).build());
        }
    }

    /** Valt dit stuk (van y, zo hoog) helemaal binnen de zichtbare kolom rechts? */
    private boolean inEditor(int y, int height) {
        return y >= editorY0 && y + height <= editorY1;
    }

    private void initSynonyms(int y0, int y1) {
        int y = y0 + 12;
        MultiLineEditBox synonyms = new MultiLineEditBox(font, left, y, panelW, Math.max(40, y1 - 40 - y),
                Component.translatable("screen.starktech.voice_edit.synonyms_hint"),
                Component.translatable("screen.starktech.voice_edit.synonyms"));
        synonyms.setCharacterLimit(50000);
        synonyms.setValue(synonymsText);
        synonyms.setValueListener(value -> {
            synonymsText = value;
            changed();
        });
        addRenderableWidget(synonyms);
        EditBox ignore = new EditBox(font, left, y1 - 18, panelW, 18, Component.translatable("screen.starktech.voice_edit.ignore"));
        ignore.setMaxLength(3000);
        ignore.setValue(ignoreText);
        ignore.setResponder(value -> {
            ignoreText = value;
            changed();
        });
        addRenderableWidget(ignore);
    }

    private EditBox findSearchBox() {
        return children().stream().filter(child -> child instanceof EditBox).map(child -> (EditBox) child)
                .filter(box -> box.getX() == left).findFirst().orElse(null);
    }

    private Component tabLabel(Tab which) {
        Component label = Component.translatable(which == Tab.COMMANDS ? "screen.starktech.voice_edit.tab.commands"
                : "screen.starktech.voice_edit.tab.synonyms");
        return which == tab ? Component.literal("» ").append(label) : label;
    }

    // ---------------------------------------------------------------- Tekenen

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);
        g.drawString(font, title, left + 212, top + 4, CYAN, true);
        if (tab == Tab.COMMANDS) {
            if (selected >= 0 && selected < working.commands.size()) {
                VoiceDictionary.Command command = working.commands.get(selected);
                label(g, Component.translatable("screen.starktech.voice_edit.name"), nameLabelY, GREY);
                label(g, Component.translatable("screen.starktech.voice_edit.actions"), actionsLabelY, GREY);
                if (needsSlotHint(command)) {
                    label(g, Component.translatable("screen.starktech.voice_edit.slot_hint"), hintY, ORANGE);
                }
                label(g, Component.translatable("screen.starktech.voice_edit.reply"), replyLabelY, GREY);
                label(g, Component.translatable("screen.starktech.voice_edit.phrases", command.phrases.size()), phrasesLabelY, GREY);
                editorBar.render(g, mouseX, mouseY);
            }
            listBar.render(g, mouseX, mouseY);
        } else {
            g.drawString(font, Component.translatable("screen.starktech.voice_edit.synonyms_label"), left, top + 22, GREY, false);
            g.drawString(font, Component.translatable("screen.starktech.voice_edit.ignore"), left, testY - 36, GREY, false);
        }
        g.drawString(font, Component.translatable("screen.starktech.voice_edit.test"), left, testY + 5, GREY, false);
        g.drawString(font, testResult, left + 76 + Math.max(80, panelW / 2 - 70), testY + 5, WHITE, false);
        if (Util.getMillis() < statusUntil) {
            g.drawCenteredString(font, status, left + panelW / 2, top + panelH - 14, GREEN);
        }
    }

    /** Een opschrift in de kolom rechts, alleen als het (na scrollen) nog in beeld is. */
    private void label(GuiGraphics g, Component text, int y, int colour) {
        if (inEditor(y, 8)) {
            g.drawString(font, text, rightX, y, colour, false);
        }
    }

    /** Heeft een actie een waarde nodig, maar staat die nergens (geen waarde en geen {..} in de zinnen)? */
    private static boolean needsSlotHint(VoiceDictionary.Command command) {
        boolean slot = command.phrases.stream().anyMatch(phrase -> phrase.contains("{"));
        return command.actions.stream().anyMatch(ref -> ref.action().needsValue() && ref.value().isBlank()) && !slot;
    }

    // ---------------------------------------------------------------- Acties van de knoppen

    private void switchTab(Tab newTab) {
        if (tab == Tab.SYNONYMS) {
            applySynonymTexts();
        }
        tab = newTab;
        rebuildWidgets();
    }

    private void pickAction(VoiceDictionary.Command command, int index) {
        minecraft.setScreen(new ActionPickerScreen(this, action -> {
            VoiceDictionary.ActionRef ref = new VoiceDictionary.ActionRef(action, "");
            if (index < 0) {
                command.actions.add(ref);
            } else {
                command.actions.set(index, ref);
            }
            changed();
        }));
    }

    private void addCommand() {
        VoiceDictionary.Command command = new VoiceDictionary.Command(Component.translatable("screen.starktech.voice_edit.new_name").getString());
        String last = VoiceControl.lastUnknown();
        if (!last.isEmpty()) {
            command.phrases.add(last);
        }
        working.commands.add(command);
        selected = working.commands.size() - 1;
        editorScroll = 0;
        filter = "";
        listScroll = Integer.MAX_VALUE; // naar het einde van de lijst
        changed();
        rebuildWidgets();
    }

    private void confirmDelete() {
        if (selected < 0 || selected >= working.commands.size()) {
            return;
        }
        String name = working.commands.get(selected).name;
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                working.commands.remove(selected);
                selected = Math.min(selected, working.commands.size() - 1);
                changed();
            }
            minecraft.setScreen(this);
        }, Component.translatable("screen.starktech.voice_edit.delete_title"), Component.translatable("screen.starktech.voice_edit.delete_message", name)));
    }

    private void confirmReset() {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                try {
                    working = VoiceCommandStore.resetToDefault();
                    loadSynonymTexts();
                    selected = 0;
                    listScroll = 0;
                    dirty = false;
                    testMatcher = null;
                    updateTest();
                    showStatus(Component.translatable("screen.starktech.voice_edit.reset_done"));
                } catch (IOException e) {
                    showStatus(Component.translatable("screen.starktech.voice_edit.save_failed", e.getMessage()));
                }
            }
            minecraft.setScreen(this);
        }, Component.translatable("screen.starktech.voice_edit.reset_title"), Component.translatable("screen.starktech.voice_edit.reset_message")));
    }

    private void save(boolean showStatus) {
        applySynonymTexts();
        try {
            VoiceCommandStore.save(working.copy());
            dirty = false;
            if (showStatus) {
                showStatus(Component.translatable("screen.starktech.voice_edit.saved"));
            }
        } catch (IOException e) {
            showStatus(Component.translatable("screen.starktech.voice_edit.save_failed", e.getMessage()));
        }
    }

    private void changed() {
        dirty = true;
        testMatcher = null;
        updateTest();
    }

    private void showStatus(Component message) {
        status = message;
        statusUntil = Util.getMillis() + 2500;
    }

    /** Test de zin uit het testveld met het woordenboek zoals het nu (onopgeslagen) is. */
    private void updateTest() {
        if (testText.isBlank()) {
            testResult = Component.empty();
            return;
        }
        if (testMatcher == null) {
            VoiceDictionary snapshot = working.copy();
            snapshot.synonyms.clear();
            snapshot.synonyms.addAll(parseSynonyms(synonymsText));
            snapshot.ignore.clear();
            snapshot.ignore.addAll(parseIgnore(ignoreText));
            testMatcher = new VoiceMatcher(snapshot);
        }
        List<VoiceMatcher.Match> matches = testMatcher.match(testText);
        if (matches.isEmpty()) {
            testResult = Component.translatable("screen.starktech.voice_edit.test_none").withColor(ORANGE);
            return;
        }
        List<String> names = new ArrayList<>();
        for (VoiceMatcher.Match match : matches) {
            names.add(match.command().name + (match.captured().isEmpty() ? "" : " (\"" + match.captured() + "\")"));
        }
        int percent = (int) Math.round(matches.getFirst().score() * 100);
        testResult = Component.literal("→ " + String.join(" + ", names) + "  " + percent + "%").withColor(GREEN);
    }

    private void updateVisible() {
        visible.clear();
        String wanted = filter.toLowerCase(Locale.ROOT).strip();
        for (int i = 0; i < working.commands.size(); i++) {
            VoiceDictionary.Command command = working.commands.get(i);
            boolean hit = wanted.isEmpty() || command.name.toLowerCase(Locale.ROOT).contains(wanted)
                    || command.phrases.stream().anyMatch(phrase -> phrase.toLowerCase(Locale.ROOT).contains(wanted));
            if (hit) {
                visible.add(i);
            }
        }
    }

    // ---------------------------------------------------------------- Synoniemen als tekst

    private void loadSynonymTexts() {
        StringBuilder text = new StringBuilder();
        for (VoiceDictionary.Synonym synonym : working.synonyms) {
            text.append(synonym.word()).append(" = ").append(String.join(", ", synonym.synonyms())).append('\n');
        }
        synonymsText = text.toString().strip();
        ignoreText = String.join(", ", working.ignore);
    }

    private void applySynonymTexts() {
        working.synonyms.clear();
        working.synonyms.addAll(parseSynonyms(synonymsText));
        working.ignore.clear();
        working.ignore.addAll(parseIgnore(ignoreText));
    }

    /** "drones = drone, zwerm" → een woord met zijn synoniemen. Regels zonder = of met # vooraan tellen niet. */
    private static List<VoiceDictionary.Synonym> parseSynonyms(String text) {
        List<VoiceDictionary.Synonym> result = new ArrayList<>();
        for (String line : text.split("\n")) {
            int equals = line.indexOf('=');
            if (line.strip().startsWith("#") || equals < 0) {
                continue;
            }
            String word = line.substring(0, equals).strip();
            List<String> others = Arrays.stream(line.substring(equals + 1).split(","))
                    .map(String::strip).filter(other -> !other.isEmpty()).toList();
            if (!word.isEmpty()) {
                result.add(new VoiceDictionary.Synonym(word, others));
            }
        }
        return result;
    }

    private static List<String> parseIgnore(String text) {
        return Arrays.stream(text.split(",")).map(String::strip).filter(word -> !word.isEmpty()).toList();
    }

    // ---------------------------------------------------------------- Scrollen en sluiten

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (tab == Tab.COMMANDS && x >= left && x < left + listW && scrollY != 0) {
            listScroll = Math.max(0, listScroll - (int) Math.signum(scrollY) * 3);
            rebuildWidgets();
            return true;
        }
        // Eerst het zinnenvak (dat scrolt zelf), anders de hele kolom rechts.
        if (super.mouseScrolled(x, y, scrollX, scrollY)) {
            return true;
        }
        if (tab == Tab.COMMANDS && x >= rightX && editorBar.needed() && scrollY != 0) {
            editorScroll = Math.max(0, Math.min(editorBar.max(), editorScroll - (int) Math.signum(scrollY) * EDITOR_STEP));
            rebuildWidgets();
            return true;
        }
        return false;
    }

    /** Een klik op een van de scrollbalken pakt hem vast (of springt erheen). */
    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (tab == Tab.COMMANDS && button == 0) {
            int list = listBar.mouseClicked(x, y);
            if (list >= 0) {
                setListScroll(list);
                return true;
            }
            int editor = editorBar.mouseClicked(x, y);
            if (editor >= 0) {
                setEditorScroll(editor);
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (listBar.isDragging()) {
            setListScroll(listBar.mouseDragged(y));
            return true;
        }
        if (editorBar.isDragging()) {
            setEditorScroll(editorBar.mouseDragged(y));
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        listBar.mouseReleased();
        editorBar.mouseReleased();
        return super.mouseReleased(x, y, button);
    }

    private void setListScroll(int rows) {
        if (rows != listScroll) {
            listScroll = rows;
            rebuildWidgets();
        }
    }

    private void setEditorScroll(int pixels) {
        if (pixels != editorScroll) {
            editorScroll = pixels;
            rebuildWidgets();
        }
    }

    @Override
    public void onClose() {
        if (dirty) {
            save(false);
        }
        minecraft.setScreen(parent);
    }
}
