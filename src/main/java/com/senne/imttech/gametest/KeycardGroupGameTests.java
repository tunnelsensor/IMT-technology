package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.logic.KeycardData;
import com.senne.imttech.logic.LogicStore;
import com.senne.imttech.logic.program.Expr;
import com.senne.imttech.logic.program.ExprCache;
import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.LogicException;
import com.senne.imttech.logic.program.LogicGraph;
import com.senne.imttech.logic.program.Op;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.logic.program.ScriptPrinter;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Keycards in groepen in plaats van niveaus (hoofdstuk 32, 25 oktober): een kaart kan in meer dan één groep
 * zitten, groepen zitten in groepen, {@code card.in_group("security")} in de taal, en kernen en kaarten van voor
 * die dag (met een niveau). Het hele verhaal met een lezer en een server staat in LogicGameTests
 * ({@code logic_keycard_opens_vault}), het hacken in BypassGameTests.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class KeycardGroupGameTests {
    private KeycardGroupGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * De boom: groepen maken (hoofdletters maken niet uit, een naam die niet mag kan niet), groepen in groepen zonder
     * rondjes, een kaart in twee groepen, hernoemen (de boom en de kaarten gaan mee), weggooien (wat erin zat schuift
     * een laag omhoog), een kaart uit een groep halen, en hooguit 32 groepen.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "keycard_groups")
    public static void keycard_groups_tree(GameTestHelper helper) {
        LogicStore store = new LogicStore();
        check(store.addGroup("staff", "") && store.addGroup("Security", "staff") && store.addGroup("night shift", "security"),
                "Groepen maken lukte niet");
        check(!store.addGroup("SECURITY", ""), "Dezelfde groep (met andere hoofdletters) kon er twee keer in");
        check(!store.addGroup("", "") && !store.addGroup("a\"b", "") && !store.addGroup("x".repeat(25), "")
                && !store.addGroup("lab", "kitchen"), "Een naam die niet mag (of een groep in een groep die er niet is) werd toegelaten");
        check(store.parentOf("night shift").equals("Security") && store.childrenOf("").equals(List.of("staff"))
                && store.childrenOf("STAFF").equals(List.of("Security")), "De boom klopt niet: " + store.groups());
        // Geen rondjes: staff kan niet in night shift (die zit al in staff), en een groep niet in zichzelf.
        check(!store.moveGroup("staff", "night shift") && !store.moveGroup("staff", "staff"), "Een rondje werd toegelaten");
        check(store.isInside("night shift", "STAFF") && !store.isInside("staff", "night shift"), "Wat in wat zit, klopt niet");

        // Een kaart in twee groepen.
        var card = store.issue("Lotte", 6, "test", "night shift");
        check(card != null && card.colour() == 6 && card.groups().equals(List.of("night shift")), "De nieuwe kaart: " + card);
        check(store.addGroup("lab", "") && store.addToGroup(card.number(), "LAB") && !store.addToGroup(card.number(), "lab"),
                "In een tweede groep zetten lukte niet (of kon twee keer)");
        check(store.inGroup(card.number(), "staff") && store.inGroup(card.number(), "lab") && store.inGroup(card.number(), "Night Shift"),
                "De kaart zit niet in zijn groepen (of in de groepen daarboven)");
        check(!store.inGroup(card.number(), "guest") && !store.inGroup(999, "staff"), "Een kaart zit in een groep waar hij niet in zit");

        // Hernoemen: de groepen en de kaarten erin gaan mee.
        check(store.renameGroup("security", "guards") && store.parentOf("night shift").equals("guards")
                && store.inGroup(card.number(), "guards") && !store.hasGroup("security"), "Hernoemen nam de boom of de kaart niet mee");
        check(!store.renameGroup("guards", "LAB") && store.renameGroup("guards", "Guards") && store.spelling("GUARDS").equals("Guards"),
                "Hernoemen naar een naam die al bestaat (of alleen andere hoofdletters) klopt niet");

        // Weggooien: wat erin zat, schuift een laag omhoog (de kaart zit dan in Guards, en dus nog steeds in staff).
        check(store.removeGroup("night shift") && store.card(card.number()).groups().equals(List.of("Guards", "lab"))
                && store.inGroup(card.number(), "staff"), "Na weggooien zit de kaart niet in de groep erboven: " + store.card(card.number()).groups());
        check(store.removeGroup("staff") && store.parentOf("Guards").isEmpty() && store.childrenOf("").contains("Guards"),
                "Een groep in een weggegooide groep schoof niet omhoog");
        check(store.removeFromGroup(card.number(), "LAB") && store.card(card.number()).groups().equals(List.of("Guards"))
                && !store.removeFromGroup(card.number(), "lab"), "Uit een groep halen klopt niet");

        // Hooguit 32 groepen.
        LogicStore full = new LogicStore();
        for (int i = 0; i < LogicStore.MAX_GROUPS; i++) {
            check(full.addGroup("group " + i, ""), "Groep " + i + " paste er niet meer in");
        }
        check(!full.addGroup("one too many", ""), "Er paste een groep te veel in");
        helper.succeed();
    }

    /**
     * Opslaan en weer laden houdt alles (de kleur, de groepen van een kaart, de boom). Een verbrande SSD gooit de
     * groepen weg, net als de kaarten; een backup van dit netwerk zet ze terug, een van een ander netwerk niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "keycard_groups")
    public static void keycard_groups_save_and_backup(GameTestHelper helper) {
        LogicStore store = new LogicStore();
        store.addGroup("staff", "");
        store.addGroup("lab", "staff");
        store.addGroup("guest", "");
        var card = store.issue("Lab access", 4, "test", "lab");
        store.addToGroup(card.number(), "guest");
        LogicStore loaded = LogicStore.load(store.save());
        check(loaded.groups().equals(List.of("guest", "lab", "staff")) && loaded.parentOf("lab").equals("staff"),
                "Na laden is de boom anders: " + loaded.groups());
        check(loaded.card(card.number()).colour() == 4 && loaded.card(card.number()).groups().equals(List.of("guest", "lab")),
                "Na laden is de kaart anders: " + loaded.card(card.number()));
        LogicStore backup = LogicStore.load(store.save());
        loaded.wipe();
        check(loaded.groups().isEmpty() && loaded.cards().isEmpty(), "Na een verbrande SSD zijn er nog groepen of kaarten");
        loaded.restoreFrom(backup, false);
        check(loaded.groups().isEmpty(), "Een backup van een ander netwerk zette groepen terug");
        loaded.restoreFrom(backup, true);
        check(loaded.hasGroup("lab") && loaded.inGroup(card.number(), "staff"), "De backup zette de groepen niet terug");
        helper.succeed();
    }

    /**
     * Een kern van voor 25 oktober: kaarten met een niveau komen in de groepen "level 1" tot het hoogste niveau, die
     * in elkaar zitten, zodat {@code card.in_group("level 3")} hetzelfde is als {@code card.level >= 3} vroeger. Hun kleur
     * blijft. Na opslaan en laden gebeurt dat niet nog een keer. Een oude kaart (item) met "level" krijgt die kleur.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "keycard_groups")
    public static void keycard_levels_become_groups(GameTestHelper helper) {
        CompoundTag old = new CompoundTag();
        old.putInt("NextCard", 1004);
        ListTag cards = new ListTag();
        int[] levels = {2, 4, 1};
        for (int i = 0; i < levels.length; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("N", 1001 + i);
            entry.putString("L", "Card " + i);
            entry.putInt("V", levels[i]);
            entry.putBoolean("B", false);
            entry.putString("W", "old");
            cards.add(entry);
        }
        old.put("Cards", cards);
        LogicStore store = LogicStore.load(old);
        check(store.groups().equals(List.of("level 1", "level 2", "level 3", "level 4")), "De groepen: " + store.groups());
        check(store.parentOf("level 4").equals("level 3") && store.parentOf("level 2").equals("level 1")
                && store.parentOf("level 1").isEmpty(), "De niveaugroepen zitten niet in elkaar");
        check(store.card(1001).colour() == 2 && store.card(1002).colour() == 4 && store.card(1003).colour() == 1,
                "Een oude kaart kreeg een andere kleur");
        check(store.inGroup(1002, "level 3") && store.inGroup(1001, "level 2") && !store.inGroup(1001, "level 3")
                && store.inGroup(1003, "level 1") && !store.inGroup(1003, "level 2"), "in_group(\"level N\") is niet card.level >= N");
        check(store.removeGroup("level 4") && !LogicStore.load(store.save()).hasGroup("level 4"),
                "Na opslaan en laden werden de oude niveaus opnieuw groepen");

        // Een kaart in iemands rugzak van voor 25 oktober: "level" wordt zijn kleur, en hij wordt opgeslagen met "colour".
        CompoundTag item = new CompoundTag();
        item.putString("ip", "10.0.0.1");
        item.putInt("number", 1001);
        item.putInt("level", 3);
        KeycardData data = KeycardData.CODEC.parse(NbtOps.INSTANCE, item).getOrThrow();
        check(data.colour() == 3, "Een oude kaart met niveau 3 kreeg kleur " + data.colour());
        CompoundTag saved = (CompoundTag) KeycardData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        check(saved.getInt("colour") == 3 && !saved.contains("level"), "Een kaart wordt opgeslagen als " + saved);
        helper.succeed();
    }

    /**
     * {@code card.in_group(...)} in de taal: met ' of " (de printer schrijft "), ook als blokjes; uitrekenen (hoofdletters
     * maken niet uit); en foutmeldingen voor card.level, card.group, buiten when keycard, en zonder groepsnaam.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "keycard_groups")
    public static void keycard_in_group_language(GameTestHelper helper) {
        String code = "when keycard(\"Lab door\"):\n    if card.in_group('Staff') and not card.in_group(\"guest\"):\n        accept()\n";
        String clean = "when keycard(\"Lab door\"):\n    if card.in_group(\"Staff\") and not card.in_group(\"guest\"):\n        accept()\n";
        String printed = ScriptPrinter.print(ScriptParser.parse(code));
        check(printed.equals(clean), "card.in_group terugschrijven gaf:\n" + printed);
        LogicGraph graph = GraphConverter.toGraph(ScriptParser.parse(code), null);
        check(graph.problems().isEmpty(), "Als blokjes heeft card.in_group fouten: " + graph.problems());
        check(ScriptPrinter.print(GraphConverter.toScript(graph)).equals(clean), "Van blokjes terug naar code ging mis");
        // Een naam of tekst met namen erin mag ook tussen de haakjes.
        ScriptParser.parse("when keycard(\"Lab door\"):\n    group = \"staff\"\n    if card.in_group(group) or card.in_group($\"{group}\"):\n        accept()\n");

        Expr.Facts staff = new Expr.Facts() {
            @Override
            public Object value(String variable) {
                return false;
            }

            @Override
            public boolean isOn(String device) {
                return false;
            }

            @Override
            public boolean inGroup(String group) {
                return group.equalsIgnoreCase("staff");
            }
        };
        Expr yes = ExprCache.get("card.in_group(\"STAFF\") and not card.in_group('guest')", Op.KEYCARD);
        check(yes != null && ExprCache.test(yes, staff), "card.in_group(\"STAFF\") zou waar moeten zijn");
        Expr no = ExprCache.get("card.in_group(\"guest\")", Op.KEYCARD);
        check(no != null && !ExprCache.test(no, staff), "card.in_group(\"guest\") zou niet waar moeten zijn");

        expectError("when keycard(\"Lab\"):\n    if card.level >= 3:\n        accept()", 2, "card.level is gone");
        expectError("when keycard(\"Lab\"):\n    if card.group == \"staff\":\n        accept()", 2, "use card.in_group(\"security\")");
        expectError("when every(5):\n    if card.in_group(\"staff\"):\n        pass", 2, "card.in_group(...) only works inside when keycard(...)");
        expectError("when keycard(\"Lab\"):\n    if card.in_group(3):\n        pass", 2, "needs the name of a keycard group");
        expectError("when keycard(\"Lab\"):\n    if card.in_group():\n        pass", 2, "needs the name of a keycard group");
        helper.succeed();
    }

    private static void expectError(String code, int line, String part) {
        try {
            ScriptParser.parse(code);
        } catch (LogicException e) {
            if (e.line() != line || !e.getMessage().contains(part)) {
                throw new GameTestAssertException("Voor:\n" + code + "\nverwachtte ik regel " + line + " met \"" + part
                        + "\", maar kreeg: " + e.describe());
            }
            return;
        }
        throw new GameTestAssertException("Deze code zou een fout moeten geven:\n" + code);
    }
}
