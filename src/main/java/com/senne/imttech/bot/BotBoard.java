package com.senne.imttech.bot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * De printplaat van een robot (hoofdstuk 21; later ook van een drone): 8 genummerde pinnen. Op elke pin
 * kun je een module solderen (hoofdstuk 22: Scan, Laser, Camera...), en in je code gebruik je de pin met
 * zijn nummer: {@code scanPin = pin(1)}.
 *
 * Solderen kan misgaan: dan loopt het soldeer naar de pin ernaast, een soldeerbrug. Die onthouden we in
 * {@code bridges}: bit n = een brug tussen pin n+1 en pin n+2 (pin 1 en 2 is bit 0). Zie hoofdstuk 22.
 *
 * @param pins    per pin (8) de naam van de module die erop zit, of "" als hij leeg is
 * @param bridges de soldeerbruggen tussen naburige pinnen
 */
public record BotBoard(List<String> pins, int bridges) {
    /** Hoeveel pinnen een printplaat heeft. */
    public static final int PINS = 8;
    /** Een nieuwe, lege printplaat. */
    public static final BotBoard EMPTY = new BotBoard(Collections.nCopies(PINS, ""), 0);

    public BotBoard {
        // Altijd precies 8 pinnen, ook als de opslag iets anders zegt.
        List<String> fixed = new ArrayList<>(pins.subList(0, Math.min(pins.size(), PINS)));
        while (fixed.size() < PINS) {
            fixed.add("");
        }
        pins = List.copyOf(fixed);
        bridges &= (1 << (PINS - 1)) - 1;
    }

    public static final Codec<BotBoard> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().fieldOf("pins").forGetter(BotBoard::pins),
            Codec.INT.optionalFieldOf("bridges", 0).forGetter(BotBoard::bridges)
    ).apply(instance, BotBoard::new));

    public static final StreamCodec<ByteBuf, BotBoard> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(PINS)), BotBoard::pins,
            ByteBufCodecs.VAR_INT, BotBoard::bridges,
            BotBoard::new);

    /** Wat er op pin {@code number} (1 tot 8) zit, of "" als hij leeg is. */
    public String pin(int number) {
        return number >= 1 && number <= PINS ? pins.get(number - 1) : "";
    }

    /** Hoeveel pinnen er een module hebben. */
    public int used() {
        int used = 0;
        for (String pin : pins) {
            if (!pin.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /** Zit deze module op een van de pinnen? */
    public boolean has(BotModule module) {
        for (int number = 1; number <= PINS; number++) {
            if (module(number) == module) {
                return true;
            }
        }
        return false;
    }

    /** De module op pin {@code number}, of null als hij leeg is (of als er iets onbekends op staat). */
    public @Nullable BotModule module(int number) {
        return BotModule.byId(pin(number));
    }

    /** Is er een soldeerbrug tussen pin {@code left} en pin {@code left + 1}? */
    public boolean bridge(int left) {
        return left >= 1 && left < PINS && (bridges & 1 << (left - 1)) != 0;
    }

    /** Hetzelfde bord, met deze module op pin {@code number} ("" = leeg). */
    public BotBoard with(int number, String module) {
        List<String> copy = new ArrayList<>(pins);
        copy.set(number - 1, module);
        return new BotBoard(copy, bridges);
    }

    /** Hetzelfde bord, met een soldeerbrug tussen pin {@code left} en {@code left + 1}. */
    public BotBoard withBridge(int left) {
        return new BotBoard(pins, bridges | 1 << (left - 1));
    }

    /** Hetzelfde bord zonder de soldeerbruggen aan pin {@code number} (links en rechts). */
    public BotBoard withoutBridgesAt(int number) {
        int mask = 0;
        if (number > 1) {
            mask |= 1 << (number - 2);
        }
        if (number < PINS) {
            mask |= 1 << (number - 1);
        }
        return new BotBoard(pins, bridges & ~mask);
    }

    /** Lossolderen: de module eraf en de bruggen aan deze pin weg (een brug kan ook aan een lege pin zitten). */
    public BotBoard desolder(int number) {
        return with(number, "").withoutBridgesAt(number);
    }

    /**
     * De bruggen die kortsluiting geven als je de robot aanzet (de linker pin van elke brug): een brug tussen
     * twee lege pinnen doet niets, maar raakt hij een module, dan gaat het mis.
     */
    public List<Integer> shorts() {
        List<Integer> list = new ArrayList<>();
        for (int left = 1; left < PINS; left++) {
            if (bridge(left) && (!pin(left).isEmpty() || !pin(left + 1).isEmpty())) {
                list.add(left);
            }
        }
        return list;
    }

    /** Welke modules er op zitten, één bit per BotModule (voor het model in je spel). */
    public int modules() {
        int mask = 0;
        for (int number = 1; number <= PINS; number++) {
            BotModule module = module(number);
            if (module != null) {
                mask |= 1 << module.ordinal();
            }
        }
        return mask;
    }
}
