package com.senne.starktech.client;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * Soorten erts die de scanner herkent, met hun kleur in de HUD.
 * We gebruiken "tags" (zoals c:ores/diamond), zodat ertsen uit andere mods ook gevonden worden.
 * De volgorde bepaalt wat het belangrijkst is (bovenaan = zeldzaamst).
 */
public enum OreType {
    ANCIENT_DEBRIS("ancient_debris", Tags.Blocks.ORES_NETHERITE_SCRAP, 0xFFB5715B),
    DIAMOND("diamond", Tags.Blocks.ORES_DIAMOND, 0xFF5CE1E6),
    EMERALD("emerald", Tags.Blocks.ORES_EMERALD, 0xFF17DD62),
    GOLD("gold", Tags.Blocks.ORES_GOLD, 0xFFFCEE4B),
    LAPIS("lapis", Tags.Blocks.ORES_LAPIS, 0xFF4169FF),
    REDSTONE("redstone", Tags.Blocks.ORES_REDSTONE, 0xFFFF3030),
    IRON("iron", Tags.Blocks.ORES_IRON, 0xFFD8AF93),
    COPPER("copper", Tags.Blocks.ORES_COPPER, 0xFFE77C56),
    QUARTZ("quartz", Tags.Blocks.ORES_QUARTZ, 0xFFEFE9E1),
    COAL("coal", Tags.Blocks.ORES_COAL, 0xFF9A9A9A),
    OTHER("other", Tags.Blocks.ORES, 0xFFB0B0FF);

    private final String id;
    private final TagKey<Block> tag;
    private final int color;

    OreType(String id, TagKey<Block> tag, int color) {
        this.id = id;
        this.tag = tag;
        this.color = color;
    }

    public int color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("edith.starktech.ore." + id);
    }

    /** Welk soort erts is dit blok? Null als het geen erts is. */
    public static @Nullable OreType of(BlockState state) {
        if (!state.is(Tags.Blocks.ORES)) {
            return null;
        }
        for (OreType type : values()) {
            if (state.is(type.tag)) {
                return type;
            }
        }
        return OTHER;
    }
}
