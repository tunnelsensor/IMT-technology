package com.senne.starktech.c4;

import com.senne.starktech.registry.ModBlocks;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * De klap van C4 (hoofdstuk 10, jouw keuze: "sterke muren kosten meer C4").
 *
 * Het midden van de klap ligt op het blok waar de lading tegenaan plakt. Elk blok binnen
 * {@link C4Balance#RADIUS} krijgt breekpunten: {@link C4Balance#POWER} vlak bij de lading, aflopend tot
 * 0 aan de rand. Heeft een blok genoeg (zie {@link #strength}), dan breekt het en valt het eruit zoals
 * bij TNT (een kist ook, met zijn inhoud). Zo niet, dan krijgt het barsten die je ziet, en de volgende
 * C4 telt erbij op. Barsten genezen na {@link C4Balance#CRACK_HEAL_TICKS} (en als de server opnieuw
 * start: ze staan niet in de wereld).
 *
 * Een zone houdt C4 niet tegen: dat is juist de bedoeling (zie security/zone/Zones). Ander C4 in de
 * klap gaat ook af (een kettingreactie), TNT wordt aangestoken. Wezens krijgen een klap zoals van TNT.
 */
public final class C4Blast {
    private C4Blast() {}

    /** De barsten in één blok: hoeveel breekpunten het al kreeg, wanneer, en welk blok het toen was. */
    private record Crack(float damage, long time, Block block) {}

    private static final Map<ResourceKey<Level>, Map<BlockPos, Crack>> CRACKS = new HashMap<>();

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
                com.senne.starktech.security.zone.Zones.activeNear(level, pos, C4Balance.RADIUS);
        boolean alerted = level.getBlockEntity(pos) instanceof C4BlockEntity self && self.alerted();
        level.removeBlock(pos, false);
        // Eerst de klap voor wezens (met het geluid en de rook van een explosie), zonder blokken: die
        // doen we hieronder zelf. Zo vernielt de klap ook niet de spullen die straks uit de blokken vallen.
        level.explode(null, centre.x, centre.y, centre.z, C4Balance.ENTITY_POWER, false, Level.ExplosionInteraction.NONE);
        Breach result = breach(level, centre, C4Balance.POWER, C4Balance.RADIUS, here -> true, zone);
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
        Map<BlockPos, Crack> cracks = CRACKS.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        long now = level.getGameTime();
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
                    other.detonateIn(C4Balance.CHAIN_DELAY);
                }
                continue;
            }
            float hp = strength(level, here, target);
            if (hp < 0) {
                continue; // onbreekbaar (bedrock, barrières)
            }
            float damage = (float) (power * (1.0 - distance / radius));
            Crack old = cracks.get(here);
            float total = damage + (old != null && old.block() == target.getBlock() ? old.damage() : 0);
            if (total >= hp) {
                cracks.remove(here);
                level.destroyBlockProgress(crackId(here), here, -1);
                if (zone != null && zone.contains(here)) {
                    inZone++;
                }
                breakBlock(level, here, target);
                broken++;
            } else {
                cracks.put(here, new Crack(total, now, target.getBlock()));
                showCrack(level, here, total / hp);
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
     * beveiligingskern is het sterkst (hacken is de andere weg naar binnen).
     */
    public static float strength(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.SECURITY_CORE.get())) {
            return C4Balance.CORE;
        }
        if (state.getDestroySpeed(level, pos) < 0) {
            return -1;
        }
        float resistance = state.getBlock().getExplosionResistance();
        if (resistance >= C4Balance.STRONG_RESISTANCE) {
            return C4Balance.STRONG;
        }
        return resistance >= C4Balance.NORMAL_RESISTANCE ? C4Balance.NORMAL : C4Balance.WEAK;
    }

    /** Hoeveel breekpunten dit blok nu al heeft (0 = geen barsten). */
    public static float cracks(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Crack> cracks = CRACKS.get(level.dimension());
        Crack crack = cracks == null ? null : cracks.get(pos);
        return crack == null || crack.block() != level.getBlockState(pos).getBlock() ? 0 : crack.damage();
    }

    // ================================================================ De barsten

    /**
     * Een eigen nummer per blok voor de barsten (Minecraft onthoudt per nummer maar één blok). Negatief,
     * zodat het nooit het nummer van een speler of wezen is.
     */
    private static int crackId(BlockPos pos) {
        return -1 - (Long.hashCode(pos.asLong()) & 0x3FFFFFFF);
    }

    /** Barsten laten zien, van 0 (net een beetje) tot 9 (bijna kapot). */
    private static void showCrack(ServerLevel level, BlockPos pos, float fraction) {
        level.destroyBlockProgress(crackId(pos), pos, Mth.clamp((int) (fraction * 10), 0, 9));
    }

    /**
     * Om de seconde: barsten die genezen zijn (of in een blok dat er niet meer is) weghalen, en om de
     * tien seconden de rest opnieuw sturen (anders laat je spel ze na twintig seconden vallen).
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        int tick = event.getServer().getTickCount();
        if (tick % 20 != 0 || CRACKS.isEmpty()) {
            return;
        }
        for (Map.Entry<ResourceKey<Level>, Map<BlockPos, Crack>> perLevel : CRACKS.entrySet()) {
            ServerLevel level = event.getServer().getLevel(perLevel.getKey());
            if (level == null) {
                continue;
            }
            long now = level.getGameTime();
            for (Iterator<Map.Entry<BlockPos, Crack>> it = perLevel.getValue().entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<BlockPos, Crack> entry = it.next();
                BlockPos pos = entry.getKey();
                Crack crack = entry.getValue();
                boolean loaded = level.isLoaded(pos);
                if (now - crack.time() > C4Balance.CRACK_HEAL_TICKS
                        || loaded && level.getBlockState(pos).getBlock() != crack.block()) {
                    it.remove();
                    level.destroyBlockProgress(crackId(pos), pos, -1);
                } else if (loaded && tick % 200 == 0) {
                    showCrack(level, pos, crack.damage() / strength(level, pos, level.getBlockState(pos)));
                }
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        CRACKS.clear();
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
