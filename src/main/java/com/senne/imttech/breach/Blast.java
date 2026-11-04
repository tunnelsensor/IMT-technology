package com.senne.imttech.breach;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.weapons.RemoteChargeBlock;
import com.senne.imttech.weapons.RemoteChargeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * De klap van een explosief tegen blokken (hoofdstuk 10, jouw keuze: "sterke muren kosten meer"). Vroeger was dit de
 * klap van C4; sinds 26 oktober (jouw wens: "haal de C4 weg, ik wil dat er nog enkel granaten bestaan") zijn het de
 * frag-granaten en de Remote Frag (hoofdstuk 35), allemaal even sterk (zie {@link #frag}).
 *
 * Elk blok binnen de straal krijgt breekpunten: veel vlak bij de lading, aflopend tot 0 aan de rand. Heeft een blok
 * genoeg (zie {@link #strength}), dan breekt het en valt het eruit zoals bij TNT (een kist ook, met zijn inhoud). Zo
 * niet, dan krijgt het barsten die je ziet, en de volgende klap telt erbij op. Die barsten blijven tot je ze herstelt
 * (zie {@link Cracks}, hoofdstuk 11).
 *
 * Een zone houdt deze klap niet tegen: dat is juist de bedoeling (zie security/zone/Zones). Een andere Remote Frag in de
 * klap gaat ook af (een kettingreactie), TNT wordt aangestoken. Wezens krijgen een klap zoals van TNT, iets zwakker.
 */
public final class Blast {
    private Blast() {}

    /** Een Remote Frag in de klap gaat zoveel ticks later zelf af (een kettingreactie). */
    private static final int CHAIN_DELAY = 3;

    /**
     * Een frag gaat af (jouw keuze: de Remote Frag even sterk als een Sticky Frag): een klap rond {@code centre} met de
     * sterktes uit [weapons]. {@code at} is waar de lading lag (voor het logboek van een zone), {@code source} wie of wat
     * de klap veroorzaakte (een granaat: dan telt wie hem gooide als dader), {@code alerted} of de zone al gewaarschuwd
     * was (dan gaat de markering weg), {@code what} de naam in het logboek. Geeft hoeveel blokken er kapot gingen.
     */
    public static int frag(ServerLevel level, Vec3 centre, BlockPos at,
                           @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity source,
                           boolean alerted, String what) {
        float radius = ImtConfig.Weapons.GRENADE_BREACH_RADIUS;
        com.senne.imttech.security.zone.ZoneData.Zone zone = com.senne.imttech.security.zone.Zones.activeNear(level, at, radius);
        // Eerst de klap voor wezens (met het geluid en de rook van een explosie), zonder blokken: die doen we hieronder
        // zelf. Zo vernielt de klap ook niet de spullen die straks uit de blokken vallen.
        level.explode(source, centre.x, centre.y, centre.z, ImtConfig.Weapons.GRENADE_ENTITY_POWER, false,
                Level.ExplosionInteraction.NONE);
        Breach result = breach(level, centre, ImtConfig.Weapons.GRENADE_BREACH, radius, here -> true, zone);
        BlastAlarm.blasted(level, at, zone, result.inZone(), alerted, what);
        return result.broken();
    }

    /** Wat een klap deed: zoveel blokken kapot, waarvan zoveel in de zone. */
    public record Breach(int broken, int inZone) {}

    /**
     * Breekpunten uitdelen rond {@code centre}: {@code power} vlakbij, aflopend tot 0 op {@code radius} blokken, alleen
     * aan blokken waar {@code affects} ja op zegt. Wat genoeg heeft, breekt (zoals bij TNT); de rest krijgt barsten. Een
     * Remote Frag in de klap gaat ook af. Frags gebruiken dit voor alles in hun klap; TNT en satellietraketten alleen voor
     * de blokken van een zone (zie Zones.onExplosion).
     */
    public static Breach breach(ServerLevel level, Vec3 centre, float power, float radius,
                                java.util.function.Predicate<BlockPos> affects,
                                com.senne.imttech.security.zone.ZoneData.@org.jetbrains.annotations.Nullable Zone zone) {
        int reach = Mth.ceil(radius);
        int broken = 0;
        int inZone = 0;
        BlockPos middle = BlockPos.containing(centre);
        for (BlockPos at : BlockPos.betweenClosed(middle.offset(-reach, -reach, -reach), middle.offset(reach, reach, reach))) {
            double distance = centre.distanceTo(at.getCenter());
            if (distance > radius) {
                continue;
            }
            BlockState target = level.getBlockState(at);
            if (target.isAir() || target.getBlock() instanceof LiquidBlock) {
                continue;
            }
            BlockPos here = at.immutable();
            if (!affects.test(here)) {
                continue;
            }
            // Een andere Remote Frag: gaat zelf ook af (een beetje later, dat klinkt als een kettingreactie).
            if (target.getBlock() instanceof RemoteChargeBlock charge && charge.kind() == RemoteChargeBlock.Kind.FRAG) {
                if (level.getBlockEntity(here) instanceof RemoteChargeBlockEntity other) {
                    other.goOffIn(CHAIN_DELAY);
                }
                continue;
            }
            float hp = strength(level, here, target);
            if (hp < 0) {
                continue; // onbreekbaar (bedrock, barrières)
            }
            float total = (float) (power * (1.0 - distance / radius)) + Cracks.damage(level, here);
            if (total >= hp) {
                if (zone != null && zone.contains(here)) {
                    inZone++;
                }
                // Zijn barsten vergeten we vanzelf (zie Cracks.blockChanged): er staat straks iets anders.
                breakBlock(level, here, target);
                broken++;
            } else {
                Cracks.crack(level, here, target.getBlock(), total, hp);
            }
        }
        return new Breach(broken, inZone);
    }

    /** Een blok breken zoals TNT dat doet (alles valt eruit); TNT zelf wordt aangestoken. */
    private static void breakBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof TntBlock) {
            level.removeBlock(pos, false);
            PrimedTnt tnt = new PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, null);
            tnt.setFuse(10 + level.random.nextInt(20));
            level.addFreshEntity(tnt);
            return;
        }
        level.destroyBlock(pos, true);
    }

    /**
     * Hoeveel breekpunten dit blok kan hebben voor het breekt (-1 = nooit). Dat hangt af van hoe goed het in Minecraft
     * tegen explosies kan: steen en baksteen zijn "gewoon", obsidiaan "sterk". De beveiligingskern is het sterkst
     * (hacken is de andere weg naar binnen). Is het blok geplamuurd, dan is het zwakker (hoofdstuk 11: elke keer 15%,
     * zie {@link Cracks#factor}).
     */
    public static float strength(ServerLevel level, BlockPos pos, BlockState state) {
        float strength = baseStrength(level, pos, state);
        return strength < 0 ? strength : strength * Cracks.factor(Cracks.patches(level, pos));
    }

    /** Zoals {@link #strength}, maar voor een nieuw blok (nooit geplamuurd). */
    public static float baseStrength(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.SECURITY_CORE.get())) {
            return ImtConfig.Breach.CORE;
        }
        if (state.getDestroySpeed(level, pos) < 0) {
            return -1;
        }
        float resistance = state.getBlock().getExplosionResistance();
        float strength = resistance >= ImtConfig.Breach.STRONG_RESISTANCE ? ImtConfig.Breach.STRONG
                : resistance >= ImtConfig.Breach.NORMAL_RESISTANCE ? ImtConfig.Breach.NORMAL : ImtConfig.Breach.WEAK;
        // Hoofdstuk 44 (jouw keuze: "zo sterk als obsidiaan"): een verstevigd blok telt minstens als een sterk blok, hoe
        // mooi of zwak het zelf ook is (kwarts, hout, glas...).
        if (Cracks.isReinforced(level, pos)) {
            strength = Math.max(strength, ImtConfig.Breach.REINFORCED);
        }
        return strength;
    }
}
