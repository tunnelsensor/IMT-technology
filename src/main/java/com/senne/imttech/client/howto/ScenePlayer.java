package com.senne.imttech.client.howto;

import com.senne.imttech.client.ImtClientConfig;
import com.senne.imttech.howto.HowToCue;
import com.senne.imttech.howto.HowToScene;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Speelt één animatie af (hoofdstuk 63): houdt bij waar we op de tijdlijn zijn, en hoe het wereldje er op dat moment uit
 * ziet. Alles volgt uit het draaiboek en de tijd: springen naar een hoofdstukje of terugspoelen is dus gewoon de tijd
 * veranderen; het wereldje wordt opnieuw opgebouwd uit de cues tot dat moment.
 */
final class ScenePlayer {
    /** Zo hoog boven zijn plek begint een blok dat valt (in blokken). */
    static final float DROP_HEIGHT = 0.9f;

    final HowToScene scene;
    final SceneWorld world = new SceneWorld();
    /** Blokken die nog aan het vallen zijn (die staan nog niet in het wereldje, zodat de buren hun kant blijven tekenen). */
    final List<Falling> falling = new ArrayList<>();

    private int tick;
    private boolean paused;
    private int builtFor = -1;

    /** Een vallend blok: waar het landt, wat het is, en wanneer het begon en hoe lang het valt. */
    record Falling(BlockPos pos, BlockState state, int start, int drop) {
        /** Hoe hoog boven zijn plek het blok nu hangt. */
        float offset(float time) {
            float p = Math.min(1.0f, Math.max(0.0f, (time - start) / drop));
            float eased = 1.0f - (1.0f - p) * (1.0f - p) * (1.0f - p);
            return (1.0f - eased) * DROP_HEIGHT;
        }
    }

    ScenePlayer(HowToScene scene) {
        this.scene = scene;
    }

    int tick() {
        return tick;
    }

    boolean paused() {
        return paused;
    }

    boolean finished() {
        return tick >= scene.length();
    }

    void setPaused(boolean paused) {
        this.paused = paused;
    }

    /** De tijd met de fractie van de huidige tick erbij (voor vloeiende bewegingen). */
    float time(float partialTick) {
        return paused || finished() ? tick : tick + partialTick;
    }

    /** Eén tick verder (20 keer per seconde), met de geluidjes van deze tick. */
    void advance() {
        if (paused || finished()) {
            return;
        }
        tick++;
        for (HowToCue cue : scene.cues()) {
            if (cue.tick() > tick) {
                break;
            }
            if (cue.tick() == tick && cue instanceof HowToCue.Sound sound) {
                play(sound);
            }
        }
    }

    /** Naar een moment springen (zonder geluidjes). */
    void seek(int to) {
        tick = Math.max(0, Math.min(scene.length(), to));
        builtFor = -1;
    }

    private static void play(HowToCue.Sound sound) {
        float volume = (float) (sound.volume() * ImtClientConfig.howToVolume());
        if (volume > 0.001f) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.sound(), sound.pitch(), volume));
        }
    }

    /** Bouwt het wereldje voor de huidige tick op, als dat nog niet gebeurd is. */
    void update() {
        if (builtFor == tick) {
            return;
        }
        world.clear();
        falling.clear();
        for (HowToCue cue : scene.cues()) {
            if (cue.tick() > tick) {
                break;
            }
            switch (cue) {
                case HowToCue.Place place -> {
                    boolean landed = tick >= place.tick() + place.drop();
                    for (Map.Entry<BlockPos, BlockState> entry : place.blocks().entrySet()) {
                        BlockPos pos = entry.getKey();
                        falling.removeIf(f -> f.pos().equals(pos));
                        if (landed) {
                            world.set(pos, entry.getValue());
                        } else {
                            world.remove(pos);
                            falling.add(new Falling(pos, entry.getValue(), place.tick(), place.drop()));
                        }
                    }
                }
                case HowToCue.Remove remove -> {
                    for (BlockPos pos : remove.positions()) {
                        world.remove(pos);
                        falling.removeIf(f -> f.pos().equals(pos));
                    }
                }
                default -> {
                }
            }
        }
        world.connect();
        builtFor = tick;
    }

    // ================================================================ Wat er nu te zien is

    /** Hoe ver een cue met een duur nu is: 0 bij het begin, 1 aan het eind; buiten die tijd -1. */
    static float progress(float time, int start, int duration) {
        if (time < start || time >= start + Math.max(1, duration)) {
            return -1.0f;
        }
        return (time - start) / Math.max(1, duration);
    }

    /** Hoe zichtbaar iets is dat van {@code start} tot {@code start + duration} duurt: zacht erin en eruit (5 ticks). */
    static float fade(float time, int start, int duration) {
        if (time < start || time >= start + duration) {
            return 0.0f;
        }
        float in = Math.min(1.0f, (time - start) / 5.0f);
        float out = Math.min(1.0f, (start + duration - time) / 5.0f);
        return Math.min(in, out);
    }

    /** Het punt op {@code fraction} (0 tot 1) van de lengte van een lijn langs {@code points}. */
    static Vec3 along(List<Vec3> points, float fraction) {
        if (points.size() == 1) {
            return points.getFirst();
        }
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += points.get(i - 1).distanceTo(points.get(i));
        }
        double target = total * Math.max(0.0f, Math.min(1.0f, fraction));
        for (int i = 1; i < points.size(); i++) {
            Vec3 a = points.get(i - 1);
            Vec3 b = points.get(i);
            double length = a.distanceTo(b);
            if (target <= length || i == points.size() - 1) {
                return length < 1.0E-6 ? b : a.lerp(b, Math.min(1.0, target / length));
            }
            target -= length;
        }
        return points.getLast();
    }

    /** De punten van een lijn langs {@code points}, maar alleen het eerste stuk ({@code fraction} van de lengte). */
    static List<Vec3> partial(List<Vec3> points, float fraction) {
        if (fraction >= 1.0f) {
            return points;
        }
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += points.get(i - 1).distanceTo(points.get(i));
        }
        double target = total * Math.max(0.0f, fraction);
        List<Vec3> out = new ArrayList<>();
        out.add(points.getFirst());
        for (int i = 1; i < points.size(); i++) {
            Vec3 a = points.get(i - 1);
            Vec3 b = points.get(i);
            double length = a.distanceTo(b);
            if (target <= length) {
                out.add(length < 1.0E-6 ? b : a.lerp(b, target / length));
                return out;
            }
            out.add(b);
            target -= length;
        }
        return out;
    }
}
