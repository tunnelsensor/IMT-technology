package com.senne.imttech.client.guns;

import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunSkill;
import com.senne.imttech.guns.GunSkills;
import com.senne.imttech.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * De kant van jouw spel van de skills (hoofdstuk 52, stap 3): wat je geleerd hebt (de attachment komt van de server),
 * voor de terugslag en het kruisje.
 */
public final class GunSkillsClient {
    private GunSkillsClient() {}

    private static GunSkills skills() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? GunSkills.EMPTY : player.getData(ModAttachments.GUN_SKILLS);
    }

    /** De terugslag, maal dit (1 = gewoon). */
    public static float recoilFactor(FirearmType type) {
        return skills().factor(GunSkill.RECOIL, type);
    }

    /** De spreiding van het kruisje, maal dit. */
    public static float spreadFactor(FirearmType type) {
        return skills().factor(GunSkill.AIM, type);
    }
}
