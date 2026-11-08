package com.senne.imttech.client.howto;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Het kleine wereldje van een "How it works"-animatie (hoofdstuk 63): alleen blokken, zonder chunks, licht of wezens.
 * Minecraft tekent een blok met dezelfde code als in de echte wereld als je het zo'n "BlockAndTintGetter" geeft: dan
 * kloppen de schaduw in de hoekjes (ambient occlusion), de verborgen kanten tussen twee blokken, en de kleur van gras en
 * bladeren (van een plains-bioom).
 *
 * Overal is het volle daglicht; de kanten krijgen dezelfde schaduw als in de wereld (boven het lichtst, onder het donkerst).
 */
final class SceneWorld implements BlockAndTintGetter {
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    /** Een lichtmotor zonder licht: er wordt nooit iets aan gevraagd, want getBrightness zegt zelf "vol licht". */
    private final LevelLightEngine light;
    private final @Nullable Holder<Biome> biome;
    /** Blokken waarvan updateShape niet werkt zonder echte wereld (die laten we zoals ze zijn). */
    private static final Set<Block> NO_SHAPE_UPDATE = new HashSet<>();

    SceneWorld() {
        light = new LevelLightEngine(new LightChunkGetter() {
            @Override
            public @Nullable LightChunk getChunkForLighting(int x, int z) {
                return null;
            }

            @Override
            public BlockGetter getLevel() {
                return SceneWorld.this;
            }
        }, false, false);
        ClientLevel level = Minecraft.getInstance().level;
        biome = level == null ? null : level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(Biomes.PLAINS).orElse(null);
    }

    void clear() {
        blocks.clear();
    }

    void set(BlockPos pos, BlockState state) {
        if (state.isAir()) {
            blocks.remove(pos);
        } else {
            blocks.put(pos.immutable(), state);
        }
    }

    void remove(BlockPos pos) {
        blocks.remove(pos);
    }

    Map<BlockPos, BlockState> blocks() {
        return blocks;
    }

    /**
     * Laat elk blok kijken naar zijn buren, zoals in de echte wereld na het neerzetten: zo hangt Battery Glass aan elkaar
     * als één raam. Een blok dat daarvoor een echte wereld nodig heeft, blijft zoals het is.
     */
    void connect() {
        Map<BlockPos, BlockState> updated = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            if (NO_SHAPE_UPDATE.contains(state.getBlock())) {
                continue;
            }
            BlockState now = state;
            try {
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.relative(direction);
                    // Geen echte wereld: blokken die hem nodig hebben gooien een fout en blijven zoals ze zijn.
                    now = now.updateShape(direction, getBlockState(next), null, pos, next);
                }
            } catch (RuntimeException e) {
                NO_SHAPE_UPDATE.add(state.getBlock());
                continue;
            }
            if (now != state && !now.isAir()) {
                updated.put(pos, now);
            }
        }
        blocks.putAll(updated);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return 128;
    }

    @Override
    public int getMinBuildHeight() {
        return -32;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        if (!shade) {
            return 1.0f;
        }
        return switch (direction) {
            case DOWN -> 0.5f;
            case UP -> 1.0f;
            case NORTH, SOUTH -> 0.8f;
            case WEST, EAST -> 0.6f;
        };
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return light;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return 15;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return biome == null ? 0xFFFFFF : resolver.getColor(biome.value(), pos.getX(), pos.getZ());
    }
}
