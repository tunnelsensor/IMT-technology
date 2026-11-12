package com.senne.imttech.guns;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
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
            // Hoofdstuk 103: doorladen (grendel, pomp) hoort bij herladen, een patroon in de buis bij magazijnen vullen.
            case CYCLE, SELECT_FIRE -> GunSkill.RELOAD;
            case LOAD_TUBE, UNLOAD_TUBE -> GunSkill.MAG_HANDLING;
        };
    }

    /** Maal dit duurt een handeling voor deze speler (1 = zoals de config). */
    public static float timeFactor(ServerPlayer player, Firearms.ActionKind kind, @Nullable ItemStack gun) {
        return GunSkillsServer.of(player).factor(skillFor(kind), gun == null ? null : scopeOf(gun));
    }

    // ================================================================ De scopes (hoofdstuk 55: elk wapen zijn eigen rij)

    /** De scope van dit wapen ("glock_19", "laser_rifle"...), of null als het geen wapen met skills is. */
    public static @Nullable String scopeOf(ItemStack stack) {
        FirearmType firearm = FirearmItem.typeOf(stack);
        if (firearm != null) {
            return firearm.id;
        }
        GunType laser = LaserGunItem.typeOf(stack);
        return laser == null ? null : laser.id;
    }

    /** Alle wapens met een eigen rij in de boom, in de volgorde van de tabbladen: de Glock, dan de lasergeweren. */
    public static List<String> weaponScopes() {
        List<String> scopes = new ArrayList<>();
        for (FirearmType type : FirearmType.values()) {
            scopes.add(type.id);
        }
        for (GunType type : GunType.values()) {
            scopes.add(type.id);
        }
        return scopes;
    }

    /** Is dit een scope die bestaat ("global" of een wapen)? */
    public static boolean validScope(String scope) {
        return GunSkills.GLOBAL.equals(scope) || weaponScopes().contains(scope);
    }

    /** De naam van een wapen (of "Global") voor op het scherm. */
    public static Component scopeText(String scope) {
        return GunSkills.GLOBAL.equals(scope) ? Component.translatable("screen.imttech.skills.global")
                : Component.translatable("item.imttech." + scope);
    }

    /** Een korte naam voor een tabblad: "Glock 19", "Pistol", "Rifle", "Sniper", "Shotgun". */
    public static Component shortName(String scope) {
        for (GunType type : GunType.values()) {
            if (type.id.equals(scope)) {
                return Component.translatable("screen.imttech.skills.tab." + type.id);
            }
        }
        return scopeText(scope);
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
