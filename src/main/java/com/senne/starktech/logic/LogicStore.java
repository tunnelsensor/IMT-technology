package com.senne.starktech.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Wat de server van een beveiligingskern onthoudt: de keycards die hij schreef (en welke
 * geblokkeerd zijn), en (vanaf stap 5) je redstone-diagrammen.
 *
 * Het staat in de kern zelf (SecurityCoreBlockEntity), niet in de racks: breek je een rack af, dan
 * ben je je diagrammen niet kwijt. Neemt iemand je kern over, dan krijgt hij dit allemaal mee (net
 * als je bestanden).
 */
public final class LogicStore {
    /** Zoveel kaarten kan één netwerk hooguit schrijven. */
    public static final int MAX_CARDS = 200;
    /** Het eerste kaartnummer. */
    private static final int FIRST_CARD = 1001;

    /** Een geschreven kaart: zijn nummer, naam, niveau, of hij geblokkeerd is, en wie hem schreef. */
    public record Card(int number, String label, int level, boolean blocked, String writtenBy) {
        Card withBlocked(boolean blocked) {
            return new Card(number, label, level, blocked, writtenBy);
        }
    }

    private final TreeMap<Integer, Card> cards = new TreeMap<>();
    private int nextCard = FIRST_CARD;

    // ================================================================ Keycards

    /** Alle kaarten, op nummer. */
    public List<Card> cards() {
        return new ArrayList<>(cards.values());
    }

    public @Nullable Card card(int number) {
        return cards.get(number);
    }

    /** Een nieuwe kaart in de lijst (met het volgende nummer), of null als de lijst vol is. */
    public @Nullable Card issue(String label, int level, String writtenBy) {
        if (cards.size() >= MAX_CARDS) {
            return null;
        }
        Card card = new Card(nextCard++, label, Math.max(1, Math.min(KeycardData.MAX_LEVEL, level)), false, writtenBy);
        cards.put(card.number(), card);
        return card;
    }

    /** Een kaart blokkeren (of weer toelaten). False als die kaart niet bestaat. */
    public boolean setBlocked(int number, boolean blocked) {
        Card card = cards.get(number);
        if (card == null) {
            return false;
        }
        cards.put(number, card.withBlocked(blocked));
        return true;
    }

    /** Een kaart uit de lijst halen (dan is hij nergens meer geldig). */
    public boolean forget(int number) {
        return cards.remove(number) != null;
    }

    /**
     * Is deze kaart hier geldig? Hij moet door DIT netwerk geschreven zijn (zijn IP), nog in de lijst
     * staan, niet geblokkeerd zijn, en hetzelfde niveau hebben als in de lijst.
     */
    public boolean isValid(String networkIp, @Nullable KeycardData data) {
        if (data == null || !data.ip().equals(networkIp)) {
            return false;
        }
        Card card = cards.get(data.number());
        return card != null && !card.blocked() && card.level() == data.level();
    }

    // ================================================================ Opslaan

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("NextCard", nextCard);
        ListTag list = new ListTag();
        for (Card card : cards.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("N", card.number());
            entry.putString("L", card.label());
            entry.putInt("V", card.level());
            entry.putBoolean("B", card.blocked());
            entry.putString("W", card.writtenBy());
            list.add(entry);
        }
        tag.put("Cards", list);
        return tag;
    }

    public static LogicStore load(CompoundTag tag) {
        LogicStore store = new LogicStore();
        store.nextCard = Math.max(FIRST_CARD, tag.getInt("NextCard"));
        for (Tag element : tag.getList("Cards", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            Card card = new Card(entry.getInt("N"), entry.getString("L"), entry.getInt("V"), entry.getBoolean("B"),
                    entry.getString("W"));
            store.cards.put(card.number(), card);
            store.nextCard = Math.max(store.nextCard, card.number() + 1);
        }
        return store;
    }
}
