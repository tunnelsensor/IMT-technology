package com.senne.starktech.client.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** Hoe gevaarlijk is een wezen? Een simpele schatting op basis van soort en levenspunten. */
public enum ThreatLevel {
    NONE("none", HudStyle.GREEN),
    NEUTRAL("neutral", 0xFFE14A),
    LOW("low", HudStyle.ORANGE),
    MEDIUM("medium", 0xFF7A3F),
    HIGH("high", HudStyle.RED),
    EXTREME("extreme", 0xD02BFF),
    UNKNOWN("unknown", HudStyle.WHITE);

    private final String id;
    public final int color;

    ThreatLevel(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public Component label() {
        return Component.translatable("hud.starktech.threat." + id);
    }

    public static ThreatLevel of(LivingEntity entity) {
        if (entity instanceof Player) {
            return UNKNOWN;
        }
        if (entity instanceof Enemy) {
            float max = entity.getMaxHealth();
            if (max >= 100) {
                return EXTREME;
            }
            if (max >= 40) {
                return HIGH;
            }
            return max >= 20 ? MEDIUM : LOW;
        }
        return entity instanceof NeutralMob ? NEUTRAL : NONE;
    }
}
