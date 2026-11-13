package com.senne.imttech.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.senne.imttech.ImtTech;
import com.senne.imttech.guns.FirearmType;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Hoofdstuk 105, jouw melding: "de verhouding van de guns is niet super, de glock zeker met barrel is veel te groot, maak
 * die kleiner, de ander guns zijn redelijk klein maak die groter". Sindsdien wordt elk vuurwapen op dezelfde maat
 * getekend (FirearmType.DRAWN_SCALE): elk model is op ware grootte, dus zo kloppen de verhoudingen vanzelf. Deze test
 * kijkt in de modellen (models/item/*.json, gemaakt door tools/generate_gun_models.py en generate_weapon_models.py) of
 * dat zo blijft: past iemand later één wapen aan, dan zie je het hier.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class GunScaleGameTests {
    private GunScaleGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** De houdingen ("display") van het model van dit item. */
    private static JsonObject display(String item) {
        String path = "/assets/" + ImtTech.MODID + "/models/item/" + item + ".json";
        try (InputStream in = GunScaleGameTests.class.getResourceAsStream(path)) {
            check(in != null, "Het model " + path + " is er niet");
            JsonObject model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            check(model.has("display"), "Het model van " + item + " heeft geen houdingen (display)");
            return model.getAsJsonObject("display");
        } catch (java.io.IOException e) {
            throw new GameTestAssertException("Het model van " + item + " kon niet gelezen worden: " + e.getMessage());
        }
    }

    /** Hoe groot het item in deze houding is (de drie getallen van "scale" moeten gelijk zijn). */
    private static float scale(JsonObject display, String item, String context) {
        check(display.has(context), "Het model van " + item + " heeft geen houding " + context);
        var scale = display.getAsJsonObject(context).getAsJsonArray("scale");
        float x = scale.get(0).getAsFloat();
        check(scale.get(1).getAsFloat() == x && scale.get(2).getAsFloat() == x,
                "De " + item + " is in " + context + " niet in alle richtingen even groot: " + scale);
        return x;
    }

    /**
     * Elk vuurwapen in de hand (derde persoon, links en rechts) op FirearmType.DRAWN_SCALE (0,37), en op de grond 0,8 keer
     * zo groot. Vroeger was de Glock 0,75 (met een demper even lang als een MP5) en de lange wapens 0,30 tot 0,36. De
     * lasergeweren (geen modellen op ware grootte): het laserpistool 0,5 (ongeveer zo lang als de Glock), de drie lange
     * 0,75.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "gunscale")
    public static void every_firearm_is_drawn_on_one_ruler(GameTestHelper helper) {
        for (FirearmType type : FirearmType.values()) {
            JsonObject display = display(type.id);
            for (String hand : new String[]{"thirdperson_righthand", "thirdperson_lefthand"}) {
                float drawn = scale(display, type.id, hand);
                check(Math.abs(drawn - FirearmType.DRAWN_SCALE) < 1.0e-4f, "De " + type.id + " is in " + hand + " " + drawn
                        + " groot in plaats van " + FirearmType.DRAWN_SCALE + " (zoals elk vuurwapen)");
            }
            float ground = scale(display, type.id, "ground");
            check(Math.abs(ground - FirearmType.DRAWN_SCALE * 0.8f) < 2.0e-3f, "De " + type.id + " is op de grond " + ground
                    + " groot in plaats van " + FirearmType.DRAWN_SCALE * 0.8f);
        }
        String[] lasers = {"laser_pistol", "laser_rifle", "laser_sniper", "laser_shotgun"};
        float[] sizes = {0.5f, 0.75f, 0.75f, 0.75f};
        for (int i = 0; i < lasers.length; i++) {
            float drawn = scale(display(lasers[i]), lasers[i], "thirdperson_righthand");
            check(Math.abs(drawn - sizes[i]) < 1.0e-4f, "De " + lasers[i] + " is in je hand " + drawn + " groot in plaats van "
                    + sizes[i]);
        }
        helper.succeed();
    }
}
