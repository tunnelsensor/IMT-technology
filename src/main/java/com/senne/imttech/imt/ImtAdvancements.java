package com.senne.imttech.imt;

import com.senne.imttech.ImtTech;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * De prestaties (advancements) die de mod zelf toekent.
 *
 * De meeste prestaties kan Minecraft zelf bijhouden ("heb je dit voorwerp gehad?"). Maar voor
 * dingen als "je hebt je eerste drone gelanceerd" of "je hebt met het harnas gevlogen" bestaat
 * geen kant-en-klare voorwaarde. Die prestaties hebben daarom een voorwaarde die nooit vanzelf
 * uitkomt (minecraft:impossible), en kennen we hier met de hand toe op het moment dat het echt
 * gebeurt.
 *
 * De namen hieronder horen bij de bestanden in data/imttech/advancement/.
 */
public final class ImtAdvancements {
    private ImtAdvancements() {}

    public static final String GLASSES = "glasses";
    public static final String FIRST_DRONE = "first_drone";
    public static final String SWARM = "swarm";
    public static final String DRONE_DOWN = "drone_down";
    public static final String SATELLITE = "satellite";
    public static final String RELAY = "relay";
    public static final String ILLUSION = "illusion";
    public static final String SUIT = "suit";
    public static final String FLY = "fly";
    public static final String REPULSOR = "repulsor";

    /**
     * Kent een prestatie toe, als de speler hem nog niet had.
     *
     * Bestaat de prestatie niet (bijvoorbeeld omdat een datapack hem weghaalt), dan gebeurt er
     * gewoon niets; dat mag nooit het spel stukmaken.
     */
    public static void award(ServerPlayer player, String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, name);
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        if (holder == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) {
            return;
        }
        // Alle voorwaarden die nog openstaan afvinken; bij ons is dat er altijd precies één.
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}
