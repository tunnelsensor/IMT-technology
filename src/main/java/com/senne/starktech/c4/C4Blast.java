package com.senne.starktech.c4;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.registry.ModBlocks;
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
 * De klap van C4 (hoofdstuk 10, jouw keuze: "sterke muren kosten meer C4").
 *
 * Het midden van de klap ligt op het blok waar de lading tegenaan plakt. Elk blok binnen
 * {@link StarkConfig.C4#RADIUS} krijgt breekpunten: {@link StarkConfig.C4#POWER} vlak bij de lading, aflopend tot
 * 0 aan de rand. Heeft een blok genoeg (zie {@link #strength}), dan breekt het en valt het eruit zoals
 * bij TNT (een kist ook, met zijn inhoud). Zo niet, dan krijgt het barsten die je ziet, en de volgende
 * C4 telt erbij op. Die barsten blijven tot je ze herstelt (zie {@link Cracks}, hoofdstuk 11).
 *
 * Een zone houdt C4 niet tegen: dat is juist de bedoeling (zie security/zone/Zones). Ander C4 in de
 * klap gaat ook af (een kettingreactie), TNT wordt aangestoken. Wezens krijgen een klap zoals van TNT.
 */
public final class C4Blast {
    private C4Blast() {}

    /** Zo ver van het midden van het blok ligt het midden van de klap: op de kant waar de lading plakt. */
    private static final double ON_FACE = 0.45;

    /**
     * Laat de C4 op {@code pos} ontploffen. Geeft terug hoeveel blokken er kapot gingen (voor het
     * logboek van een zone, zie stap 7).
     */
    public static int detonate(ServerLevel level, BlockPos pos, BlockState state) {
        Direction3 out = Direction3.of(state);
        Vec3 centre = pos.getCenter().subtract(out.x * ON_FACE, out.y * ON_FACE, out.z * ON_FACE);
        // Welke zone raakt deze klap (voor het alarm en het logboek)?
        com.senne.starktech.security.zone.ZoneData.Zone zone =
                com.senne.starktech.security.zone.Zones.activeNear(level, pos, StarkConfig.C4.RADIUS);
        boolean alerted = level.getBlockEntity(pos) instanceof C4BlockEntity self && self.alerted();
        level.removeBlock(pos, false);
        // Eerst de klap voor wezens (met het geluid en de rook van een explosie), zonder blokken: die
        // doen we hieronder zelf. Zo vernielt de klap ook niet de spullen die straks uit de blokken vallen.
        level.explode(null, centre.x, centre.y, centre.z, StarkConfig.C4.ENTITY_POWER, false, Level.ExplosionInteraction.NONE);
        Breach result = breach(level, centre, StarkConfig.C4.POWER, StarkConfig.C4.RADIUS, here -> true, zone);
        C4Alarm.blasted(level, pos, zone, result.inZone(), alerted, "C4");
        return result.broken();
    }

    /** Wat een klap deed: zoveel blokken kapot, waarvan zoveel in de zone. */
    public record Breach(int broken, int inZone) {}

    /**
     * Breekpunten uitdelen rond {@code centre}: {@code power} vlakbij, aflopend tot 0 op {@code radius}
     * blokken, alleen aan blokken waar {@code affects} ja op zegt. Wat genoeg heeft, breekt (zoals bij
     * TNT); de rest krijgt barsten. Ander C4 in de klap gaat ook af. C4 gebruikt dit voor alles in zijn
     * klap; TNT en satellietraketten alleen voor de blokken van een zone (zie Zones.onExplosion).
     */
    public static Breach breach(ServerLevel level, Vec3 centre, float power, float radius,
                                java.util.function.Predicate<BlockPos> affects,
                                com.senne.starktech.security.zone.ZoneData.@org.jetbrains.annotations.Nullable Zone zone) {
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
            // Ander C4: gaat zelf ook af (een beetje later, dat klinkt als een kettingreactie).
            if (target.getBlock() instanceof C4Block) {
                if (level.getBlockEntity(here) instanceof C4BlockEntity other) {
                    other.detonateIn(StarkConfig.C4.CHAIN_DELAY);
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
     * Hoeveel breekpunten dit blok kan hebben voor het breekt (-1 = nooit). Dat hangt af van hoe goed
     * het in Minecraft tegen explosies kan: steen en baksteen zijn "gewoon", obsidiaan "sterk". De
     * beveiligingskern is het sterkst (hacken is de andere weg naar binnen). Is het blok geplamuurd,
     * dan is het zwakker (hoofdstuk 11: elke keer 15%, zie {@link Cracks#factor}).
     */
    public static float strength(ServerLevel level, BlockPos pos, BlockState state) {
        float strength = baseStrength(level, pos, state);
        return strength < 0 ? strength : strength * Cracks.factor(Cracks.patches(level, pos));
    }

    /** Zoals {@link #strength}, maar voor een nieuw blok (nooit geplamuurd). */
    public static float baseStrength(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.SECURITY_CORE.get())) {
            return StarkConfig.C4.CORE;
        }
        if (state.getDestroySpeed(level, pos) < 0) {
            return -1;
        }
        float resistance = state.getBlock().getExplosionResistance();
        if (resistance >= StarkConfig.C4.STRONG_RESISTANCE) {
            return StarkConfig.C4.STRONG;
        }
        return resistance >= StarkConfig.C4.NORMAL_RESISTANCE ? StarkConfig.C4.NORMAL : StarkConfig.C4.WEAK;
    }

    /** De kant waar een lading van af wijst, als drie getallen (zie C4Block.outward). */
    private record Direction3(int x, int y, int z) {
        static Direction3 of(BlockState state) {
            if (!(state.getBlock() instanceof C4Block)) {
                return new Direction3(0, 0, 0);
            }
            net.minecraft.core.Direction out = C4Block.outward(state);
            return new Direction3(out.getStepX(), out.getStepY(), out.getStepZ());
        }
    }
}
