package com.senne.imttech.howto;

import com.senne.imttech.energy.CableKind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Eén gebeurtenis op de tijdlijn van een "How it works"-animatie (hoofdstuk 63). Elke cue begint op {@link #tick()}
 * (20 per seconde, geteld vanaf het begin van de animatie). Sommige duren even en zijn dan weer weg (een tekstballon,
 * een klik, een randje); andere veranderen het wereldje voor de rest van de animatie (blokken erbij of weg, een kabel).
 *
 * Alles hier is gewone gegevens, zonder tekenwerk: zo kan een test (op de server, zonder scherm) een animatie ook
 * nalopen. Het tekenen staat in {@code client/howto}.
 */
public sealed interface HowToCue {
    int tick();

    /** Blokken erbij. Ze vallen in {@code drop} ticks van een stukje hoger op hun plek (0 = meteen, zonder vallen). */
    record Place(int tick, Map<BlockPos, BlockState> blocks, int drop) implements HowToCue {}

    /** Blokken weg (het wordt lucht). */
    record Remove(int tick, Set<BlockPos> positions) implements HowToCue {}

    /** Een tekstballon met een lijntje naar {@code anchor} (null = bovenaan in beeld, zonder lijntje). */
    record Text(int tick, int duration, Component text, @Nullable Vec3 anchor, Side side) implements HowToCue {}

    /**
     * De muis klikt, met {@code item} in je hand (leeg = een lege hand). {@code path} is waar de klik gebeurt: één punt,
     * of meer punten als je met de knop ingedrukt over iets heen beweegt (een kabel tekenen); de muis loopt dan in
     * {@code duration} ticks langs die punten.
     */
    record Click(int tick, int duration, List<Vec3> path, Button button, boolean hold, boolean sneak, ItemStack item)
            implements HowToCue {}

    /** Een gekleurd randje om een stuk van het wereldje (bv. rood om een gat in de wand). */
    record Outline(int tick, int duration, AABB box, int color) implements HowToCue {}

    /** Een getekende kabel die in {@code grow} ticks van zijn eerste punt naar zijn laatste groeit en daarna blijft liggen. */
    record Cable(int tick, int grow, CableKind kind, List<Vec3> points) implements HowToCue {}

    /**
     * Een plaatje van een echt scherm, dat in beeld schuift. {@code width} x {@code height} is hoe groot het scherm in het
     * spel is (voor de verhouding); het plaatje zelf maakt de rondleiding ({@code -Ponly=howtopics}).
     */
    record Picture(int tick, int duration, ResourceLocation texture, int width, int height, List<Arrow> arrows)
            implements HowToCue {}

    /** Een balkje bij {@code anchor} dat van {@code from} naar {@code to} loopt (0 tot 1), bv. hoe vol de batterij is. */
    record Meter(int tick, int duration, Vec3 anchor, Component label, float from, float to, int color) implements HowToCue {}

    /** Een geluidje. Alleen als de animatie er gewoon langs speelt, niet als je ernaartoe springt. */
    record Sound(int tick, SoundEvent sound, float volume, float pitch) implements HowToCue {}

    /** Aan welke kant van het beeld een tekstballon staat. */
    enum Side { LEFT, RIGHT }

    /** Welke muisknop. */
    enum Button { LEFT, RIGHT }

    /** Een pijltje naar een plek op een plaatje van een scherm ({@code x} en {@code y} van 0 tot 1), met uitleg. */
    record Arrow(float x, float y, Component label) {}
}
