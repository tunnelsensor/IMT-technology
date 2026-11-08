package com.senne.imttech.howto;

import com.senne.imttech.ImtTech;
import com.senne.imttech.energy.CableKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Schrijft het draaiboek van een "How it works"-animatie (hoofdstuk 63), zoals de SceneBuilder van Ponder in Create.
 * De bouwer houdt een klok bij ({@link #now()}): elke cue begint op de huidige tijd, en {@link #idle(int)} zet de klok
 * vooruit. Een tekst of een klik loopt dus gewoon door terwijl er daarna al iets anders gebeurt; wil je wachten tot je
 * hem gelezen hebt, dan zet je er een {@code idle} achter.
 *
 * <pre>
 * HowToSceneBuilder s = new HowToSceneBuilder("power_battery", "Building a Power Battery").plate(9, 9);
 * s.chapter("A box of Battery Casing");
 * s.layers(HowToSceneBuilder.box(a, b, pos -> casing), 8);
 * s.text(80, HowToSceneBuilder.top(a), HowToCue.Side.RIGHT, "Build a closed box of *Battery Casing*.");
 * s.idle(90);
 * </pre>
 *
 * Teksten zijn Engels (alles in het spel is Engels). Elke tekst krijgt ook een sleutel ({@code howto.imttech.<id>.text3}),
 * zodat een taalbestand hem later kan vertalen; zonder vertaling staat de Engelse tekst uit de code er. In een tekst
 * licht {@code *woord*} op in de kleur van I.M.T en staat {@code [Right Button]} in de kleur van een toets.
 */
public final class HowToSceneBuilder {
    /** Zo lang valt een blok standaard (in ticks). */
    public static final int DROP = 6;
    /** Zo lang duurt een klik standaard. */
    public static final int CLICK = 24;

    /** De kleuren van I.M.T (dezelfde als HudStyle, die alleen in je eigen spel bestaat), voor randjes en balkjes. */
    public static final int CYAN = 0x3FD8FF;
    public static final int ORANGE = 0xFFA93F;
    public static final int RED = 0xFF4545;
    public static final int GREEN = 0x5CFF9D;

    private final String id;
    private final Component title;
    private int sizeX = 5;
    private int sizeZ = 5;
    private float yaw = 40.0f;
    private float pitch = 30.0f;
    private int t;
    private int texts;
    private int chapterCount;
    private int arrows;
    private int meters;
    private final List<HowToCue> cues = new ArrayList<>();
    private final List<HowToScene.Chapter> chapters = new ArrayList<>();
    /** Hoe het wereldje eruitziet na alle cues tot nu toe (voor de test en om de hoogte te weten). */
    private final Map<BlockPos, BlockState> world = new HashMap<>();
    private int maxY = 1;
    private int minHeight;

    public HowToSceneBuilder(String id, String englishTitle) {
        this.id = id;
        this.title = Component.translatableWithFallback(key("title"), englishTitle);
    }

    private String key(String part) {
        return "howto." + ImtTech.MODID + "." + id + "." + part;
    }

    // ================================================================ Klaarzetten

    /**
     * Het blok van de grondplaat. Je ziet het nooit: het wereldje tekent op die plekken het platform van I.M.T (donker, met
     * een cyaan raster en een oplichtende rand, zie SceneRenderer). Het blok is er wel, zodat wat erop staat schaduw in
     * de hoekjes krijgt en zijn onderkant verborgen blijft. Bedrock, omdat niemand daar in een animatie mee bouwt.
     */
    public static final BlockState PLATE = Blocks.BEDROCK.defaultBlockState();

    /**
     * De grondplaat: {@code sizeX} x {@code sizeZ} blokken op y = 0 (het platform van I.M.T, jouw opmerking van 8
     * november: geen dambord zoals bij Create). Alles wat je bouwt staat op y = 1 en hoger. Roep dit als eerste aan.
     */
    public HowToSceneBuilder plate(int sizeX, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        Map<BlockPos, BlockState> plate = new LinkedHashMap<>();
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                plate.put(new BlockPos(x, 0, z), PLATE);
            }
        }
        cues.add(new HowToCue.Place(0, plate, 0));
        world.putAll(plate);
        return this;
    }

    /** Hoe de camera begint: {@code yaw} draait om de verticale as (40 = je kijkt op de zuid- en westkant), {@code pitch} van boven. */
    public HowToSceneBuilder camera(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        return this;
    }

    // ================================================================ De klok

    /** Wacht even: de volgende cue begint {@code ticks} later. */
    public HowToSceneBuilder idle(int ticks) {
        t += ticks;
        return this;
    }

    /** De huidige tijd van het draaiboek, in ticks. */
    public int now() {
        return t;
    }

    /** Een nieuw hoofdstukje vanaf nu (de knoppen springen van hoofdstukje naar hoofdstukje). */
    public HowToSceneBuilder chapter(String english) {
        chapters.add(new HowToScene.Chapter(t, Component.translatableWithFallback(key("chapter" + (++chapterCount)), english)));
        return this;
    }

    // ================================================================ Blokken

    /** Eén blok dat op zijn plek valt (met het geluid van neerzetten). */
    public HowToSceneBuilder place(BlockPos pos, BlockState state) {
        return place(Map.of(pos, state), DROP);
    }

    /** Een groep blokken die tegelijk op hun plek vallen (in {@code drop} ticks; 0 = meteen). */
    public HowToSceneBuilder place(Map<BlockPos, BlockState> blocks, int drop) {
        return place(blocks, drop, true);
    }

    /** Een blok meteen in een andere stand, zonder geluid: een lampje dat aangaat, een deur die opengaat. */
    public HowToSceneBuilder change(BlockPos pos, BlockState state) {
        return place(Map.of(pos, state), 0, false);
    }

    private HowToSceneBuilder place(Map<BlockPos, BlockState> blocks, int drop, boolean sound) {
        if (blocks.isEmpty()) {
            return this;
        }
        Map<BlockPos, BlockState> copy = new LinkedHashMap<>(blocks);
        cues.add(new HowToCue.Place(t, Collections.unmodifiableMap(copy), drop));
        world.putAll(copy);
        for (BlockPos pos : copy.keySet()) {
            maxY = Math.max(maxY, pos.getY());
        }
        if (sound) {
            BlockState first = copy.values().iterator().next();
            cues.add(new HowToCue.Sound(t + drop, first.getSoundType().getPlaceSound(), 0.5f, 0.8f));
        }
        return this;
    }

    /** Een blok meteen vervangen door een ander (geen vallen), bv. een poort in plaats van een casing. */
    public HowToSceneBuilder replace(BlockPos pos, BlockState state) {
        return place(Map.of(pos, state), 0);
    }

    /**
     * Blokken laag voor laag, van onder naar boven: elke laag valt {@code perLayer} ticks na de vorige. De klok staat
     * daarna achter de laatste laag.
     */
    public HowToSceneBuilder layers(Map<BlockPos, BlockState> blocks, int perLayer) {
        TreeMap<Integer, Map<BlockPos, BlockState>> byY = new TreeMap<>();
        blocks.forEach((pos, state) -> byY.computeIfAbsent(pos.getY(), y -> new LinkedHashMap<>()).put(pos, state));
        for (Map<BlockPos, BlockState> layer : byY.values()) {
            place(layer, DROP);
            idle(perLayer);
        }
        return this;
    }

    /** Blokken weg (met het geluid van breken). */
    public HowToSceneBuilder remove(BlockPos... positions) {
        LinkedHashSet<BlockPos> set = new LinkedHashSet<>(List.of(positions));
        if (set.isEmpty()) {
            return this;
        }
        BlockState first = world.getOrDefault(positions[0], Blocks.STONE.defaultBlockState());
        cues.add(new HowToCue.Remove(t, Collections.unmodifiableSet(set)));
        cues.add(new HowToCue.Sound(t, first.getSoundType().getBreakSound(), 0.5f, 0.8f));
        set.forEach(world::remove);
        return this;
    }

    /** Blokken weg zonder geluid (een laserwand die terugschuift: die heeft zijn eigen geluid). */
    public HowToSceneBuilder clear(BlockPos... positions) {
        LinkedHashSet<BlockPos> set = new LinkedHashSet<>(List.of(positions));
        if (!set.isEmpty()) {
            cues.add(new HowToCue.Remove(t, Collections.unmodifiableSet(set)));
            set.forEach(world::remove);
        }
        return this;
    }

    // ================================================================ Vormen en punten

    /** Alle plekken van de doos van {@code a} tot en met {@code b}, elk met het blok dat {@code state} geeft (null = overslaan). */
    public static Map<BlockPos, BlockState> box(BlockPos a, BlockPos b, Function<BlockPos, BlockState> state) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (int y = Math.min(a.getY(), b.getY()); y <= Math.max(a.getY(), b.getY()); y++) {
            for (int z = Math.min(a.getZ(), b.getZ()); z <= Math.max(a.getZ(), b.getZ()); z++) {
                for (int x = Math.min(a.getX(), b.getX()); x <= Math.max(a.getX(), b.getX()); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState s = state.apply(pos);
                    if (s != null) {
                        out.put(pos, s);
                    }
                }
            }
        }
        return out;
    }

    /** Het punt op {@code fraction} (0 tot 1) van de lengte van een lijn langs {@code points} (waar een wezen dan loopt). */
    public static Vec3 along(List<Vec3> points, float fraction) {
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

    /** Het midden van een blok. */
    public static Vec3 center(BlockPos pos) {
        return Vec3.atCenterOf(pos);
    }

    /** Het midden van de bovenkant van een blok. */
    public static Vec3 top(BlockPos pos) {
        return face(pos, Direction.UP);
    }

    /** Het midden van één kant van een blok. */
    public static Vec3 face(BlockPos pos, Direction side) {
        return Vec3.atCenterOf(pos).add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
    }

    // ================================================================ Wat er over het wereldje heen komt

    /**
     * Een tekstballon, {@code duration} ticks lang, met een lijntje naar {@code anchor} (null = bovenaan). De tekst is
     * Engels; {@code %s} wordt {@code args} (getallen uit de config, zodat de tekst klopt met jouw wereld).
     */
    public HowToSceneBuilder text(int duration, @Nullable Vec3 anchor, HowToCue.Side side, String english, Object... args) {
        Component text = Component.translatableWithFallback(key("text" + (++texts)), english, args);
        cues.add(new HowToCue.Text(t, duration, text, anchor, side));
        return this;
    }

    /** Een klik met de rechtermuisknop op {@code at}, met {@code item} in je hand. */
    public HowToSceneBuilder rightClick(Vec3 at, ItemStack item) {
        return click(CLICK, List.of(at), HowToCue.Button.RIGHT, false, false, item);
    }

    /** Sluipen en rechtsklikken op {@code at}. */
    public HowToSceneBuilder sneakRightClick(Vec3 at, ItemStack item) {
        return click(CLICK, List.of(at), HowToCue.Button.RIGHT, false, true, item);
    }

    /** De rechtermuisknop ingedrukt houden terwijl de muis in {@code duration} ticks langs {@code path} beweegt. */
    public HowToSceneBuilder holdRight(int duration, ItemStack item, Vec3... path) {
        return click(duration, List.of(path), HowToCue.Button.RIGHT, true, false, item);
    }

    public HowToSceneBuilder click(int duration, List<Vec3> path, HowToCue.Button button, boolean hold, boolean sneak,
                                   ItemStack item) {
        cues.add(new HowToCue.Click(t, duration, List.copyOf(path), button, hold, sneak, item.copy()));
        return this;
    }

    /** Een randje om de blokken van {@code a} tot en met {@code b}, in kleur {@code rgb}. */
    public HowToSceneBuilder outline(int duration, BlockPos a, BlockPos b, int rgb) {
        AABB box = new AABB(Vec3.atLowerCornerOf(a), Vec3.atLowerCornerOf(b).add(1, 1, 1));
        cues.add(new HowToCue.Outline(t, duration, box, rgb));
        return this;
    }

    /**
     * Een randje om een wezen dat op {@code feet} staat ({@code width} breed, {@code height} hoog): een flits, zoals de
     * blauwe flits rond de trainee die geraakt wordt.
     */
    public HowToSceneBuilder flash(int duration, Vec3 feet, double width, double height, int rgb) {
        AABB box = new AABB(feet.x - width / 2, feet.y, feet.z - width / 2, feet.x + width / 2, feet.y + height, feet.z + width / 2);
        cues.add(new HowToCue.Outline(t, duration, box, rgb));
        return this;
    }

    /** Een getekende kabel langs {@code points} (rechte stukken), die in {@code grow} ticks verschijnt. */
    public HowToSceneBuilder cable(CableKind kind, int grow, Vec3... points) {
        cues.add(new HowToCue.Cable(t, grow, kind, List.of(points)));
        return this;
    }

    /** Als duur van een wezen: tot het eind van de animatie. */
    public static final int UNTIL_END = -1;

    /**
     * Een wezen ({@code type}, bv. EntityType.HUSK) dat er {@code duration} ticks staat (of {@link #UNTIL_END}) en in
     * {@code move} ticks langs {@code path} loopt. Dezelfde {@code key} = hetzelfde wezen.
     */
    public HowToSceneBuilder actor(String key, EntityType<?> type, int duration, int move, float yaw, Vec3... path) {
        return actor(key, type, duration, move, yaw, new CompoundTag(), path);
    }

    /**
     * Hetzelfde, met gegevens zoals het spel ze bewaart ({@code data}): een guard robot met zijn onderdelen en printplaat,
     * aan of uit, of neergeschoten (zie RobotScenes). Een nieuwe cue met andere gegevens verandert hetzelfde wezen.
     */
    public HowToSceneBuilder actor(String key, EntityType<?> type, int duration, int move, float yaw, CompoundTag data,
                                   Vec3... path) {
        cues.add(new HowToCue.Actor(t, duration, key, type, List.of(path), move, yaw, Map.of(), data.copy()));
        return this;
    }

    /** Een speler met jouw skin, met {@code gear} aan (of in zijn hand), die in {@code move} ticks langs {@code path} loopt. */
    public HowToSceneBuilder player(String key, int duration, int move, float yaw, Map<EquipmentSlot, ItemStack> gear,
                                    Vec3... path) {
        return person(key, new CompoundTag(), duration, move, yaw, gear, path);
    }

    /**
     * Een andere speler dan jij (een vreemde in je basis), met een gewone skin van Minecraft die bij {@code name} hoort.
     * Verder net als {@link #player}.
     */
    public HowToSceneBuilder stranger(String key, String name, int duration, int move, float yaw,
                                      Map<EquipmentSlot, ItemStack> gear, Vec3... path) {
        CompoundTag data = new CompoundTag();
        data.putString("Profile", name);
        return person(key, data, duration, move, yaw, gear, path);
    }

    private HowToSceneBuilder person(String key, CompoundTag data, int duration, int move, float yaw,
                                     Map<EquipmentSlot, ItemStack> gear, Vec3... path) {
        Map<EquipmentSlot, ItemStack> copy = new EnumMap<>(EquipmentSlot.class);
        gear.forEach((slot, stack) -> copy.put(slot, stack.copy()));
        cues.add(new HowToCue.Actor(t, duration, key, null, List.of(path), move, yaw, Collections.unmodifiableMap(copy), data));
        return this;
    }

    /** Een getal uit de config voor een tekst: 30.0 wordt "30", 2.5 blijft "2.5". */
    public static String number(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }

    /** Een schot van {@code from} naar {@code to}, {@code duration} ticks lang. */
    public HowToSceneBuilder beam(int duration, Vec3 from, Vec3 to, int rgb) {
        cues.add(new HowToCue.Beam(t, duration, from, to, rgb));
        return this;
    }

    /** Gegevens voor het block entity op {@code pos} vanaf nu (zie HowToCue.BlockData). */
    public HowToSceneBuilder blockData(BlockPos pos, CompoundTag tag) {
        cues.add(new HowToCue.BlockData(t, pos, tag.copy()));
        return this;
    }

    /** Het wereldje is minstens zo hoog (de camera past dan meer in beeld), bv. voor een koepel boven de blokken. */
    public HowToSceneBuilder height(int height) {
        minHeight = height;
        return this;
    }

    /** Een helder blokje in kleur {@code rgb} dat in {@code duration} ticks langs {@code path} loopt (een signaal, stroom). */
    public HowToSceneBuilder signal(int duration, int rgb, Vec3... path) {
        cues.add(new HowToCue.Signal(t, duration, List.of(path), rgb));
        return this;
    }

    /**
     * Een plaatje van een echt scherm: {@code textures/gui/howto/<name>.png}, dat de rondleiding maakt. {@code width} x
     * {@code height}: hoe groot het scherm in het spel is.
     */
    public HowToSceneBuilder picture(int duration, String name, int width, int height, HowToCue.Arrow... arrowsOnIt) {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/gui/howto/" + name + ".png");
        cues.add(new HowToCue.Picture(t, duration, texture, width, height, List.of(arrowsOnIt)));
        return this;
    }

    /** Een pijltje voor {@link #picture}: naar plek ({@code x}, {@code y}) op het plaatje (0 tot 1), met Engelse uitleg. */
    public HowToCue.Arrow arrow(float x, float y, String english, Object... args) {
        return new HowToCue.Arrow(x, y, Component.translatableWithFallback(key("arrow" + (++arrows)), english, args));
    }

    /** Een balkje bij {@code anchor} dat in {@code duration} ticks van {@code from} naar {@code to} loopt (in procenten). */
    public HowToSceneBuilder meter(int duration, Vec3 anchor, String english, float from, float to, int rgb) {
        return meter(duration, anchor, english, from, to, rgb, 100, "%");
    }

    /** Een balkje met een eigen schaal: het getal is {@code value * max} met {@code unit} erachter. */
    public HowToSceneBuilder meter(int duration, Vec3 anchor, String english, float from, float to, int rgb, int max,
                                   String unit) {
        Component label = Component.translatableWithFallback(key("meter" + (++meters)), english);
        cues.add(new HowToCue.Meter(t, duration, anchor, label, from, to, rgb, max, unit));
        return this;
    }

    /** Een geluidje op dit moment. */
    public HowToSceneBuilder sound(SoundEvent sound, float volume, float pitch) {
        cues.add(new HowToCue.Sound(t, sound, volume, pitch));
        return this;
    }

    // ================================================================ Klaar

    /** Het wereldje zoals het nu is, na alle cues tot nu toe (voor tests). */
    public Map<BlockPos, BlockState> world() {
        return Collections.unmodifiableMap(world);
    }

    public HowToScene build() {
        List<HowToCue> sorted = new ArrayList<>(cues);
        // Op volgorde van tijd; cues op dezelfde tick houden hun volgorde (List.sort is stabiel).
        sorted.sort(Comparator.comparingInt(HowToCue::tick));
        int end = t;
        for (HowToCue cue : sorted) {
            end = Math.max(end, end(cue));
        }
        // Wezens "tot het eind" krijgen nu hun echte duur (tot een seconde na de laatste cue, als de animatie stopt).
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i) instanceof HowToCue.Actor a && a.duration() < 0) {
                sorted.set(i, new HowToCue.Actor(a.tick(), end + 20 - a.tick(), a.key(), a.type(), a.path(), a.move(), a.yaw(),
                        a.gear(), a.data()));
            }
        }
        List<HowToScene.Chapter> list = new ArrayList<>(chapters);
        if (!list.isEmpty() && list.getFirst().tick() <= 40) {
            // Het eerste hoofdstukje begint na een korte stilte: dan hoort die stilte er gewoon bij.
            list.set(0, new HowToScene.Chapter(0, list.getFirst().title()));
        } else if (list.isEmpty() || list.getFirst().tick() > 0) {
            list.addFirst(new HowToScene.Chapter(0, title));
        }
        // Na de laatste cue nog een seconde rust.
        return new HowToScene(id, title, sizeX, sizeZ, Math.max(maxY + 1, minHeight), yaw, pitch, end + 20, List.copyOf(sorted),
                List.copyOf(list));
    }

    /** Wanneer een cue klaar is. */
    static int end(HowToCue cue) {
        return switch (cue) {
            case HowToCue.Place p -> p.tick() + p.drop();
            case HowToCue.Remove r -> r.tick();
            case HowToCue.Text x -> x.tick() + x.duration();
            case HowToCue.Click c -> c.tick() + c.duration();
            case HowToCue.Outline o -> o.tick() + o.duration();
            case HowToCue.Cable c -> c.tick() + c.grow();
            case HowToCue.Picture p -> p.tick() + p.duration();
            case HowToCue.Meter m -> m.tick() + m.duration();
            case HowToCue.Signal s -> s.tick() + s.duration();
            case HowToCue.Actor a -> a.tick() + Math.max(0, a.duration());
            case HowToCue.Beam b -> b.tick() + b.duration();
            case HowToCue.BlockData d -> d.tick();
            case HowToCue.Sound s -> s.tick();
        };
    }
}
