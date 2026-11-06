package com.senne.imttech.guns;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.imttech.config.ImtConfig;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Wat een speler geleerd heeft (hoofdstuk 52, stap 3): hoeveel ervaring hij spaart voor zijn volgende skillpunt
 * ({@code xp}), hoeveel punten hij nog kan verdelen ({@code points}), hoeveel hij er ooit kreeg ({@code earned}, want
 * elk volgend punt kost meer ervaring), en per skill zijn niveau ({@code levels}: "global:reload" of "glock_19:aim").
 * Onveranderlijk; zit als attachment aan de speler, blijft na je dood, en wordt naar jezelf gestuurd (voor de boom, de
 * terugslag en het kruisje).
 */
public record GunSkills(int xp, int points, int earned, Map<String, Integer> levels) {
    public static final String GLOBAL = "global";
    public static final GunSkills EMPTY = new GunSkills(0, 0, 0, Map.of());

    public static final Codec<GunSkills> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("xp", 0).forGetter(GunSkills::xp),
            Codec.INT.optionalFieldOf("points", 0).forGetter(GunSkills::points),
            Codec.INT.optionalFieldOf("earned", 0).forGetter(GunSkills::earned),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("levels", Map.of()).forGetter(GunSkills::levels)
    ).apply(instance, GunSkills::new));

    public static final StreamCodec<ByteBuf, GunSkills> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GunSkills::xp,
            ByteBufCodecs.VAR_INT, GunSkills::points,
            ByteBufCodecs.VAR_INT, GunSkills::earned,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), GunSkills::levels,
            GunSkills::new);

    public GunSkills {
        levels = Map.copyOf(levels);
    }

    public static String key(String scope, GunSkill skill) {
        return scope + ":" + skill.id;
    }

    /** Het niveau van deze skill in deze scope ("global" of de naam van het pistool). */
    public int level(String scope, GunSkill skill) {
        return levels.getOrDefault(key(scope, skill), 0);
    }

    public static int maxLevel() {
        return Math.max(1, Math.round(ImtConfig.Guns.SKILL_MAX_LEVEL));
    }

    /** Zoveel punten kost het volgende niveau: globaal het dubbele (het geldt voor alles). */
    public int cost(String scope, GunSkill skill) {
        int next = level(scope, skill) + 1;
        return GLOBAL.equals(scope) ? next * 2 : next;
    }

    public boolean canLearn(String scope, GunSkill skill) {
        return level(scope, skill) < maxLevel() && points >= cost(scope, skill);
    }

    /** Dit niveau erbij (als dat kan), anders dezelfde skills. */
    public GunSkills learn(String scope, GunSkill skill) {
        if (!canLearn(scope, skill)) {
            return this;
        }
        Map<String, Integer> copy = new HashMap<>(levels);
        copy.put(key(scope, skill), level(scope, skill) + 1);
        return new GunSkills(xp, points - cost(scope, skill), earned, copy);
    }

    /**
     * Maal dit duurt (of groot is) wat deze skill verbetert, voor dit pistool: 1 = niets geleerd; elk globaal niveau
     * haalt er {@code skillGlobalStep} af, elk niveau van het pistool zelf {@code skillWeaponStep}, tot
     * {@code skillMinFactor}.
     */
    public float factor(GunSkill skill, @Nullable FirearmType type) {
        float less = level(GLOBAL, skill) * ImtConfig.Guns.SKILL_GLOBAL_STEP;
        if (type != null) {
            less += level(type.id, skill) * ImtConfig.Guns.SKILL_WEAPON_STEP;
        }
        return Mth.clamp(1 - less, Math.max(0.05f, ImtConfig.Guns.SKILL_MIN_FACTOR), 1);
    }

    /** Zoveel ervaring kost het {@code earned + 1}-de punt. */
    public int nextPointXp() {
        return Math.max(1, Math.round(ImtConfig.Guns.XP_FIRST_POINT * (float) Math.pow(ImtConfig.Guns.XP_GROWTH, earned)));
    }

    /** Ervaring erbij; elke keer dat de teller vol is, een punt. Geeft de nieuwe skills (en zie {@link #points}). */
    public GunSkills withXp(int amount) {
        int xp = this.xp + Math.max(0, amount);
        int points = this.points;
        int earned = this.earned;
        int next = nextPointXpFor(earned);
        while (xp >= next) {
            xp -= next;
            points++;
            earned++;
            next = nextPointXpFor(earned);
        }
        return new GunSkills(xp, points, earned, levels);
    }

    private static int nextPointXpFor(int earned) {
        return Math.max(1, Math.round(ImtConfig.Guns.XP_FIRST_POINT * (float) Math.pow(ImtConfig.Guns.XP_GROWTH, earned)));
    }

    /** Zoveel punten gaf je al uit. */
    public int spent() {
        int spent = 0;
        for (Map.Entry<String, Integer> entry : levels.entrySet()) {
            boolean global = entry.getKey().startsWith(GLOBAL + ":");
            for (int level = 1; level <= entry.getValue(); level++) {
                spent += global ? level * 2 : level;
            }
        }
        return spent;
    }
}
