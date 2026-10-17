package com.senne.imttech.client.suit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.client.SuitFlight;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * De vlieghouding van het harnas: wie vliegt, loopt niet.
 *
 * Zonder dit deed Minecraft gewoon zijn loopanimatie als je met het harnas vooruit vloog (je
 * beweegt immers), en kwamen de straalvlammen onder lopende benen uit. Nu:
 * - hel je voorover naarmate je harder vooruit vliegt, tot bijna liggend (zoals Iron Man);
 *   stilhangen en stijgen doe je rechtop, en achteruit hel je een beetje achterover;
 * - staan je benen recht en tegen elkaar, en hangen je armen langs je lijf;
 * - kantelen de straalvlammen mee (zie SuitEffects): ze komen uit je zolen en wijzen langs je
 *   lichaam naar achteren.
 *
 * Dat geldt voor iedereen die vliegt: jijzelf (in derde persoon) en andere spelers.
 */
public final class FlightPose {
    private FlightPose() {}

    /** Hoe ver je voorover helt bij volle vaart (graden). */
    private static final float MAX_LEAN = 70;
    /** Hoe ver je achterover helt als je achteruit vliegt. */
    private static final float BACK_LEAN = 15;
    /** Vanaf deze snelheid vooruit (blokken per tick) hel je helemaal voorover. */
    private static final float FULL_LEAN_SPEED = 0.9f;
    /** Hoe snel de houding meedraait (deel per tick): lager = rustiger. */
    private static final float SMOOTH = 0.18f;
    /** Waar je lichaam om kantelt: ongeveer je buik. */
    public static final float PIVOT = 0.9f;

    /** Per speler hoe ver hij voorover helt: vorige tick en nu (om er vloeiend tussenin te tekenen). */
    private static final Map<Integer, float[]> LEAN = new HashMap<>();
    /** Spelers bij wie we het tekenen gekanteld hebben (dat moet na het tekenen weer terug). */
    private static final Set<Integer> TILTED = new HashSet<>();

    /** Vliegt deze speler met het harnas? */
    public static boolean isFlying(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) {
            return SuitFlight.isEngaged() && !player.onGround();
        }
        return SuitEffects.isThrusting(player.getId());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            LEAN.clear();
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (Player player : mc.level.players()) {
            seen.add(player.getId());
            float[] lean = LEAN.computeIfAbsent(player.getId(), id -> new float[2]);
            lean[0] = lean[1];
            float target = 0;
            if (isFlying(player)) {
                // Hoe hard gaat hij vooruit, gezien vanuit zijn lichaam?
                float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
                double forward = (player.getX() - player.xo) * -Mth.sin(yaw) + (player.getZ() - player.zo) * Mth.cos(yaw);
                float amount = (float) Mth.clamp(forward / FULL_LEAN_SPEED, -1, 1);
                target = amount >= 0 ? amount * MAX_LEAN : amount * BACK_LEAN;
            }
            lean[1] += (target - lean[1]) * SMOOTH;
        }
        LEAN.keySet().retainAll(seen);
    }

    /** Hoe ver deze speler nu voorover helt (graden; negatief = achterover). */
    public static float lean(Player player, float partialTick) {
        float[] lean = LEAN.get(player.getId());
        return lean == null ? 0 : Mth.lerp(partialTick, lean[0], lean[1]);
    }

    /**
     * De kanteling als draaiing: om de as dwars door je lichaam (van schouder naar schouder),
     * zodat je hoofd naar voren gaat en je voeten naar achteren.
     */
    public static Quaternionf rotation(Player player, float partialTick) {
        float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vector3f across = new Vector3f(Mth.cos(yaw), 0, Mth.sin(yaw));
        return new Quaternionf().rotationAxis(lean(player, partialTick) * Mth.DEG_TO_RAD, across);
    }

    /** Een punt aan het lichaam van deze speler, meegekanteld (voor de straalvlammen). */
    public static Vec3 tilt(Player player, float partialTick, Vec3 point) {
        Vec3 feet = player.getPosition(partialTick);
        Vector3f local = new Vector3f((float) (point.x - feet.x), (float) (point.y - feet.y - PIVOT),
                (float) (point.z - feet.z));
        rotation(player, partialTick).transform(local);
        return new Vec3(feet.x + local.x, feet.y + PIVOT + local.y, feet.z + local.z);
    }

    /** Een richting, meegekanteld (bijvoorbeeld "recht naar beneden" onder je voeten). */
    public static Vec3 tiltDirection(Player player, float partialTick, Vec3 direction) {
        Vector3f dir = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        rotation(player, partialTick).transform(dir);
        return new Vec3(dir.x, dir.y, dir.z);
    }

    /** Vlak voor het tekenen van een speler die vliegt: benen stil, en het hele lichaam kantelen. */
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        float partialTick = event.getPartialTick();
        float lean = lean(player, partialTick);
        if (!isFlying(player) && Math.abs(lean) < 0.5f) {
            return;
        }
        // Geen loopbeweging: tweemaal "niet bewogen", dan is ook de vorige stand 0 en staan de
        // benen meteen recht (de volgende tick rekent Minecraft het zelf weer uit).
        player.walkAnimation.update(0, 1);
        player.walkAnimation.update(0, 1);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.rotateAround(rotation(player, partialTick), 0, PIVOT, 0);
        TILTED.add(player.getId());
    }

    /** Na het tekenen: de kanteling er weer af, anders kantelt alles wat daarna komt ook. */
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (TILTED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }
}
