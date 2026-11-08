package com.senne.imttech.howto;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Een onderwerp van "How it works" (hoofdstuk 63): items die bij elkaar horen en samen één of meer animaties delen. Zo
 * horen Battery Casing, Battery Glass, Battery Cell en Battery Port bij het onderwerp "Power Battery" (jouw antwoord 4:
 * per onderwerp, extra waar nodig).
 *
 * @param id     uniek, bv. "power_battery"
 * @param tab    het creative-tabblad waar het onderwerp in het overzicht onder staat ("power", "security", ...)
 * @param items  de items die dit onderwerp openen (met W op het item)
 * @param scenes de animaties; elke keer opnieuw gemaakt, zodat getallen uit de config kloppen met de wereld waarin je
 *               speelt. Null = nog niet gemaakt (staat in het plan, hoofdstuk 63 stap 6 tot 12)
 */
public record HowToTopic(String id, String tab, Component title, Supplier<List<Item>> items,
                         @Nullable Supplier<List<HowToScene>> scenes) {

    /** Zijn de animaties van dit onderwerp al gemaakt? */
    public boolean ready() {
        return scenes != null;
    }

    /** De animaties (leeg als ze nog niet gemaakt zijn). */
    public List<HowToScene> buildScenes() {
        return scenes == null ? List.of() : scenes.get();
    }
}
