package com.senne.imttech.guns;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Waar de skills van hoofdstuk 52 (stap 3) aan de vuurwapens hangen: hoe lang een handeling duurt, en wat je leert
 * door te doen. Zolang de skills er nog niet zijn, doet dit niets (alles duurt gewoon zo lang als de config zegt).
 */
public final class GunSkillHooks {
    private GunSkillHooks() {}

    /** Maal dit duurt een handeling voor deze speler (1 = zoals de config). */
    public static float timeFactor(ServerPlayer player, Firearms.ActionKind kind, @Nullable ItemStack gun) {
        return 1;
    }

    /** Je herlaadde (een magazijn erin). */
    public static void reloaded(ServerPlayer player, ItemStack gun) {
    }

    /** Je verhielp een storing (tap-rack). */
    public static void cleared(ServerPlayer player, ItemStack gun) {
    }

    /** Je duwde een patroon in een magazijn. */
    public static void roundLoaded(ServerPlayer player) {
    }

    /** Je trok je pistool uit de holster. */
    public static void drawn(ServerPlayer player, ItemStack gun) {
    }
}
