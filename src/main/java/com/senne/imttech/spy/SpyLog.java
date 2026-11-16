package com.senne.imttech.spy;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;

/**
 * Hoe een regel in het logboek van de spionagedrone eruitziet: de tijd tussen haakjes, en dan wat er
 * gebeurde, bv. "[3:07] Steve is at 120 64 -40, 10 hearts, holding Diamond Pickaxe. Alone."
 *
 * De server maakt de regels hier ({@link #line}), en het rapportscherm haalt ze hier weer uit elkaar
 * ({@link #split}): de tijd komt in een eigen kolom links, en de tekst ernaast breekt af op de breedte van
 * het scherm. Omdat beide kanten hier staan, passen ze altijd bij elkaar (hoofdstuk 129).
 */
public final class SpyLog {
    private SpyLog() {}

    /**
     * Een regel uit het logboek, in zijn twee delen.
     *
     * @param stamp de tijd, bv. "[3:07] " (leeg als de regel geen tijd heeft)
     * @param text  wat er gebeurde
     */
    public record Entry(Component stamp, Component text) {}

    /**
     * Maakt een regel voor het logboek: de tijd in donker cyaan, en dan de tekst.
     *
     * De tijd en de tekst staan naast elkaar in een lege regel, niet de tekst ín de tijd. Zo erft de tekst
     * de kleur van de tijd niet (vroeger was daardoor de hele regel donker cyaan).
     *
     * @param elapsed hoe lang de opdracht al loopt, bv. "3:07"
     */
    public static Component line(String elapsed, Component text) {
        return Component.empty()
                .append(Component.literal("[" + elapsed + "] ").withStyle(ChatFormatting.DARK_AQUA))
                .append(text);
    }

    /**
     * Haalt een regel van {@link #line} weer uit elkaar in de tijd en de tekst. Dat lukt ook na de reis
     * over het netwerk. Een regel die er anders uitziet, wordt één stuk tekst zonder tijd.
     */
    public static Entry split(Component line) {
        List<Component> parts = line.getSiblings();
        if (line.getContents() instanceof PlainTextContents plain && plain.text().isEmpty() && parts.size() == 2) {
            String stamp = parts.getFirst().getString();
            if (stamp.startsWith("[") && stamp.endsWith("] ")) {
                return new Entry(parts.getFirst(), parts.get(1));
            }
        }
        return new Entry(Component.empty(), line);
    }
}
