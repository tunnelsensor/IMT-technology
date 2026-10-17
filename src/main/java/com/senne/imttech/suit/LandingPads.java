package com.senne.imttech.suit;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModBlocks;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Jouw landingsplatforms: waar ze liggen, zodat I.M.T je tijdens het vliegen de weg naar
 * huis kan wijzen, ook naar een platform dat ver weg ligt (en dus niet geladen is).
 *
 * De lijst hangt aan de speler (net als je waypoints) en gaat mee naar je eigen spel, want de
 * HUD tekent het dichtstbijzijnde platform. Een platform zet zichzelf erop zodra hij van jou is
 * (neergezet, of de toren gehackt), en haalt zich eraf als hij wordt afgebroken.
 */
public record LandingPads(List<GlobalPos> pads) {
    public static final LandingPads EMPTY = new LandingPads(List.of());

    /** Zoveel platforms onthouden we hooguit per speler. */
    private static final int MAX_PADS = 64;

    public static final MapCodec<LandingPads> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            GlobalPos.CODEC.listOf().optionalFieldOf("pads", List.of()).forGetter(LandingPads::pads)
    ).apply(instance, LandingPads::new));

    public static final StreamCodec<ByteBuf, LandingPads> STREAM_CODEC = StreamCodec.composite(
            GlobalPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PADS)), LandingPads::pads,
            LandingPads::new);

    public LandingPads {
        pads = List.copyOf(pads.size() > MAX_PADS ? pads.subList(pads.size() - MAX_PADS, pads.size()) : pads);
    }

    public LandingPads with(GlobalPos pad) {
        if (pads.contains(pad)) {
            return this;
        }
        List<GlobalPos> list = new ArrayList<>(pads);
        list.add(pad);
        return new LandingPads(list);
    }

    public LandingPads without(GlobalPos pad) {
        if (!pads.contains(pad)) {
            return this;
        }
        List<GlobalPos> list = new ArrayList<>(pads);
        list.remove(pad);
        return new LandingPads(list);
    }

    /** Het dichtstbijzijnde platform in deze wereld, of null als je er hier geen hebt. */
    public @Nullable GlobalPos nearest(ResourceKey<Level> dimension, Vec3 from) {
        GlobalPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (GlobalPos pad : pads) {
            if (pad.dimension() != dimension) {
                continue;
            }
            double distance = Vec3.atCenterOf(pad.pos()).distanceToSqr(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pad;
            }
        }
        return best;
    }

    // ================================================================ Op de server

    public static LandingPads of(ServerPlayer player) {
        return player.getData(ModAttachments.LANDING_PADS);
    }

    public static void add(ServerPlayer player, GlobalPos pad) {
        LandingPads pads = of(player);
        LandingPads more = pads.with(pad);
        if (more != pads) {
            player.setData(ModAttachments.LANDING_PADS, more);
        }
    }

    public static void remove(ServerPlayer player, GlobalPos pad) {
        LandingPads pads = of(player);
        LandingPads fewer = pads.without(pad);
        if (fewer != pads) {
            player.setData(ModAttachments.LANDING_PADS, fewer);
        }
    }

    /**
     * Platforms uit de lijst halen die er niet meer zijn: afgebroken terwijl je offline was. Dat
     * zien we alleen als die plek geladen is, dus we kijken telkens als je in de buurt vliegt.
     */
    public static void forgetMissing(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (GlobalPos pad : of(player).pads()) {
            if (pad.dimension() == level.dimension() && level.isLoaded(pad.pos())
                    && !level.getBlockState(pad.pos()).is(ModBlocks.LANDING_PAD.get())) {
                remove(player, pad);
            }
        }
    }
}
