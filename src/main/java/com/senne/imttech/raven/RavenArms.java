package com.senne.imttech.raven;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.Attachment;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.EnergyCellItem;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import com.senne.imttech.weapons.LaserPart;
import com.senne.imttech.weapons.LaserParts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * De wapens van een Raven-soldaat (hoofdstuk 103, stap 8; jouw keuze: "Raven-soldaten met de nieuwe wapens").
 *
 * Vroeger droeg hij een Glock 19 of een lasergeweer. Nu ook een <b>MP5</b>, een <b>AK-47</b>, soms een <b>AWP</b> (een
 * sluipschutter) en soms een <b>Remington 870</b>. Hoe vaak hij wat draagt, staat in {@code [raven] weaponWeights}.
 * Elk wapen vecht anders:
 *
 * <ul>
 *   <li>de <b>Glock 19</b>: losse schoten, zoals altijd;</li>
 *   <li>de <b>MP5</b>: een salvo van drie kleine kogels;</li>
 *   <li>de <b>AK-47</b>: een salvo van twee zwaardere kogels, en iets verder;</li>
 *   <li>de <b>AWP</b>: een sluipschutter. Hij schiet van veel verder en veel preciezer (er zit altijd een Sniper Scope
 *       op), maar hij legt eerst een seconde aan en moet na elk schot doorladen: je hoort de grendel;</li>
 *   <li>de <b>Remington 870</b>: acht korrels hagel per schot, alleen van dichtbij gevaarlijk; hij komt dus naar je toe.</li>
 * </ul>
 *
 * De getallen (schade, salvo, tijd tussen twee schoten, bereik, spreiding) staan per wapen in {@code [raven]}. Wat op
 * zijn wapen zit, werkt ook voor hem: een kijker maakt hem preciezer, een demper maakt zijn schot stil (en een tikje
 * zwakker). Laat hij zijn wapen vallen, dan zit het er nog op.
 *
 * Deze klasse weet alleen wat hij draagt en wat het kan; het schieten zelf staat in RavenSoldierEntity en het vechten in
 * RavenCombatGoal.
 */
public final class RavenArms {
    private RavenArms() {}

    /** Zo lang (ticks) moet hij je zien voor hij schiet; een sluipschutter legt een seconde aan. */
    private static final int AIM_TICKS = 5;
    private static final int SNIPER_AIM_TICKS = 20;

    /** Wat een soldaat kan dragen, in de volgorde van {@code [raven] weaponWeights}. */
    public enum Kind {
        GLOCK_19(FirearmType.GLOCK_19, null),
        MP5(FirearmType.MP5, null),
        AK_47(FirearmType.AK_47, null),
        AWP(FirearmType.AWP, null),
        REMINGTON_870(FirearmType.REMINGTON_870, null),
        LASER_RIFLE(null, GunType.RIFLE),
        LASER_PISTOL(null, GunType.PISTOL);

        /** Het vuurwapen, of null bij een lasergeweer. */
        public final @Nullable FirearmType firearm;
        /** Het lasergeweer, of null bij een vuurwapen. */
        public final @Nullable GunType laser;

        Kind(@Nullable FirearmType firearm, @Nullable GunType laser) {
            this.firearm = firearm;
            this.laser = laser;
        }

        /** Hoe zwaar deze soort weegt bij het kiezen ({@code [raven] weaponWeights}). */
        public float weight() {
            float[] weights = ImtConfig.Raven.WEAPON_WEIGHTS;
            return ordinal() < weights.length ? Math.max(0.0f, weights[ordinal()]) : 0.0f;
        }
    }

    // ================================================================ Wat hij draagt

    /** Kiest een soort wapen: hoe zwaarder het weegt, hoe vaker. */
    public static Kind pick(RandomSource random) {
        float total = 0.0f;
        for (Kind kind : Kind.values()) {
            total += kind.weight();
        }
        if (total <= 0.0f) {
            return Kind.GLOCK_19; // alles op 0 gezet: dan toch een Glock, een soldaat zonder wapen doet niets
        }
        float roll = random.nextFloat() * total;
        for (Kind kind : Kind.values()) {
            roll -= kind.weight();
            if (roll < 0.0f) {
                return kind;
            }
        }
        return Kind.GLOCK_19;
    }

    /** Een wapen voor een nieuwe soldaat: een soort kiezen en het klaarmaken. */
    public static ItemStack random(RandomSource random) {
        return weapon(pick(random), random);
    }

    /**
     * Het wapen zelf: compleet en geladen. Een vuurwapen is van staal, soms met een attachment erop (de AWP altijd met
     * zijn kijker); een lasergeweer is van titanium, zonder Nether Star (die haal je niet bij een soldaat: wie het opraapt
     * moet er zelf een insteken op de Laser Workbench; de soldaat zelf schiet ook zonder).
     */
    public static ItemStack weapon(Kind kind, RandomSource random) {
        if (kind.laser != null) {
            ItemStack gun = LaserGunItem.withParts(kind.laser, LaserParts.complete(GunMaterial.TITANIUM).without(LaserPart.CORE));
            LaserGunItem.setCell(gun, EnergyCellItem.FULL);
            return gun;
        }
        FirearmType type = kind.firearm == null ? FirearmType.GLOCK_19 : kind.firearm;
        ItemStack gun = FirearmItem.loaded(type, GunMaterial.STEEL);
        dress(gun, type, random);
        return gun;
    }

    /** Zet soms iets op zijn vuurwapen: meestal een red dot, soms een greep, af en toe een demper. */
    private static void dress(ItemStack gun, FirearmType type, RandomSource random) {
        if (type == FirearmType.AWP) {
            FirearmItem.setAttachment(gun, Attachment.SNIPER_SCOPE); // een sluipschutter zonder kijker is er geen
        }
        if (random.nextFloat() >= ImtConfig.Raven.ATTACHMENT_CHANCE) {
            return;
        }
        List<Attachment> choices = new ArrayList<>();
        for (Attachment attachment : Attachment.values()) {
            if (!attachment.fits(type) || FirearmItem.attachment(gun, attachment.slot) != null) {
                continue; // past niet, of op die plek zit al iets (de kijker van de AWP)
            }
            if (type == FirearmType.AWP && attachment.slot == com.senne.imttech.guns.AttachmentSlot.MUZZLE) {
                continue; // geen demper voor de sluipschutter: een schot van 40 blokken ver dat je niet hoort, is niet eerlijk
            }
            for (int i = 0; i < onGunWeight(attachment); i++) {
                choices.add(attachment);
            }
        }
        if (!choices.isEmpty()) {
            FirearmItem.setAttachment(gun, choices.get(random.nextInt(choices.size())));
        }
    }

    /** Hoe vaak dit attachment op het wapen van een soldaat zit (0 = nooit). */
    private static int onGunWeight(Attachment attachment) {
        return switch (attachment) {
            case RED_DOT -> 4;
            case VERTICAL_GRIP, ANGLED_GRIP, BIPOD, TUBE_EXTENSION -> 2;
            case SUPPRESSOR_9MM, SUPPRESSOR_RIFLE, SUPPRESSOR_SHOTGUN -> 1;
            // De Sniper Scope draagt alleen de sluipschutter (die heeft hem altijd).
            case SNIPER_SCOPE -> 0;
        };
    }

    /**
     * Een los attachment als buit (in de rugzak van een gevallen soldaat): meestal een red dot of een greep, soms een
     * demper, zelden de Sniper Scope, de tweepoot of de verlengbuis.
     */
    public static ItemStack lootAttachment(RandomSource random) {
        List<Attachment> choices = new ArrayList<>();
        for (Attachment attachment : Attachment.values()) {
            int weight = switch (attachment) {
                case RED_DOT -> 5;
                case VERTICAL_GRIP, ANGLED_GRIP -> 3;
                case SUPPRESSOR_9MM, SUPPRESSOR_RIFLE -> 2;
                case SUPPRESSOR_SHOTGUN, SNIPER_SCOPE, BIPOD, TUBE_EXTENSION -> 1;
            };
            for (int i = 0; i < weight; i++) {
                choices.add(attachment);
            }
        }
        return new ItemStack(ModItems.attachment(choices.get(random.nextInt(choices.size()))));
    }

    // ================================================================ Wat het kan (config [raven])

    /** Het getal van dit vuurwapen uit een rijtje van {@code [raven]} (is het rijtje te kort, dan het laatste getal). */
    private static float per(float[] list, FirearmType type) {
        return list.length == 0 ? 0.0f : list[Math.min(type.ordinal(), list.length - 1)];
    }

    /** Schade van één kogel (of één korrel hagel) van dichtbij. Een demper remt hem een tikje af, zoals bij jou. */
    public static float damage(ItemStack weapon) {
        FirearmType type = FirearmItem.typeOf(weapon);
        return type == null ? ImtConfig.Raven.LASER_DAMAGE
                : per(ImtConfig.Raven.GUN_DAMAGE, type) * FirearmItem.damageFactor(weapon);
    }

    /** Hoeveel schoten in één salvo (de MP5 drie, de AK-47 twee, de rest één). */
    public static int burst(ItemStack weapon) {
        FirearmType type = FirearmItem.typeOf(weapon);
        return type == null ? 1 : Math.max(1, Math.round(per(ImtConfig.Raven.GUN_BURST, type)));
    }

    /** Ticks tussen twee schoten van één salvo: zo snel als het wapen zelf kan, maar niet sneller dan elke twee ticks. */
    public static int burstGap(ItemStack weapon) {
        FirearmType type = FirearmItem.typeOf(weapon);
        return type == null ? 2 : Math.max(2, type.interval());
    }

    /** Ticks tussen twee schoten, of tussen twee salvo's. */
    public static int interval(ItemStack weapon) {
        FirearmType type = FirearmItem.typeOf(weapon);
        return Math.max(1, Math.round(type == null ? ImtConfig.Raven.FIRE_INTERVAL : per(ImtConfig.Raven.GUN_INTERVAL, type)));
    }

    /** Tot hoe ver hij ermee schiet (blokken). */
    public static double range(ItemStack weapon) {
        FirearmType type = FirearmItem.typeOf(weapon);
        return Math.max(1.0, type == null ? ImtConfig.Raven.ATTACK_RANGE : per(ImtConfig.Raven.GUN_RANGE, type));
    }

    /** Hoe ver hij ernaast kan schieten (graden). Met een kijker op zijn wapen richt hij beter, zoals jij. */
    public static double spread(ItemStack weapon) {
        FirearmType type = FirearmItem.typeOf(weapon);
        return type == null ? ImtConfig.Raven.SPREAD
                : per(ImtConfig.Raven.GUN_SPREAD, type) * FirearmItem.aimSpreadFactor(weapon);
    }

    /** Zo lang (ticks) moet hij je zien voor hij schiet: een sluipschutter met een grendelgeweer legt eerst een seconde aan. */
    public static int aimTicks(ItemStack weapon) {
        FirearmType type = FirearmItem.typeOf(weapon);
        return type != null && type.action == FirearmType.Action.BOLT ? SNIPER_AIM_TICKS : AIM_TICKS;
    }
}
