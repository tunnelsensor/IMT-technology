package com.senne.imttech.guns;

import com.senne.imttech.config.ImtConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Waar de skills (hoofdstuk 52, stap 3) aan de vuurwapens hangen: hoe lang een handeling duurt, hoe ver een schot
 * strooit, en wat je leert door te doen (zie {@link GunSkillsServer}).
 */
public final class GunSkillHooks {
    private GunSkillHooks() {}

    /** Welke skill bij deze handeling hoort. */
    public static GunSkill skillFor(Firearms.ActionKind kind) {
        return switch (kind) {
            case EJECT_MAG, INSERT_MAG, SLIDE_RELEASE, RELOAD_POUCH, CHAMBER_ROUND -> GunSkill.RELOAD;
            case TAP_RACK -> GunSkill.CLEARING;
            case LOAD_ROUND, UNLOAD_ROUND -> GunSkill.MAG_HANDLING;
            case UNLOAD_CHAMBER -> GunSkill.RELOAD;
            case DRAW -> GunSkill.QUICKDRAW;
        };
    }

    /** Maal dit duurt een handeling voor deze speler (1 = zoals de config). */
    public static float timeFactor(ServerPlayer player, Firearms.ActionKind kind, @Nullable ItemStack gun) {
        return GunSkillsServer.of(player).factor(skillFor(kind), gun == null ? null : FirearmItem.typeOf(gun));
    }

    /** De spreiding van een schot, maal dit. */
    public static float spreadFactor(ServerPlayer player, FirearmType type) {
        return GunSkillsServer.of(player).factor(GunSkill.AIM, type);
    }

    /** Je schoot. */
    public static void fired(ServerPlayer player) {
        GunSkillsServer.award(player, ImtConfig.Guns.XP_SHOT);
    }

    /** Je herlaadde (een magazijn erin). */
    public static void reloaded(ServerPlayer player, ItemStack gun) {
        GunSkillsServer.award(player, ImtConfig.Guns.XP_RELOAD);
    }

    /** Je verhielp een storing (tap-rack). */
    public static void cleared(ServerPlayer player, ItemStack gun) {
        GunSkillsServer.award(player, ImtConfig.Guns.XP_TAP_RACK);
    }

    /** Je duwde een patroon in een magazijn. */
    public static void roundLoaded(ServerPlayer player) {
        GunSkillsServer.award(player, ImtConfig.Guns.XP_ROUND_LOADED);
    }

    /** Je trok je pistool uit de holster. */
    public static void drawn(ServerPlayer player, ItemStack gun) {
        GunSkillsServer.award(player, ImtConfig.Guns.XP_DRAW);
    }
}
