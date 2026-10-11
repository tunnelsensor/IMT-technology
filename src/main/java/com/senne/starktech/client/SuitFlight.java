package com.senne.starktech.client;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.network.SuitFlightPayload;
import com.senne.starktech.suit.StarkSuitItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Vliegen met het harnas, met natuurkunde in plaats van creative-vliegen.
 *
 * - **Dubbel op spatie**: stuwraketten aan (of weer uit). Op de grond duwen ze je omhoog.
 * - **W/S**: stuwkracht in de richting waarin je kijkt, dus ook omhoog of omlaag. Kijk naar
 *   de lucht en druk W, en je schiet omhoog.
 * - **A/D**: opzij.
 * - **Spatie / Shift**: stijgen / dalen.
 * - **Sprint (Ctrl)**: de naverbrander. Veel sneller, maar kost meer energie.
 *
 * Wat dit anders maakt dan creative: je hebt vaart. Snelheid bouwt op terwijl je stuwt en
 * loopt langzaam uit als je loslaat, de zwaartekracht trekt aan je (de stuwraketten houden je
 * op hoogte) en het beeld helt over in de bochten.
 *
 * Bewegen doet jouw eigen spel (dat doet Minecraft voor spelers altijd), dus de natuurkunde
 * staat hier. De server betaalt de energie, vangt je val op en laat de vlammen zien; zie
 * EdithSuit.
 */
public final class SuitFlight {
    private SuitFlight() {}

    private static boolean engaged;
    /** Hoe lang je al op de grond staat met de stuwraketten aan; na even landen ze vanzelf. */
    private static int groundTicks;
    /** Hoe hard de stuwraketten werken, 0 tot 1 (voor het geluid en de HUD). */
    private static float thrust;
    private static boolean boosting;
    /** Hoe ver het beeld overhelt (graden), nu en een tick geleden (voor vloeiend tekenen). */
    private static float bank;
    private static float previousBank;
    private static float lastYaw;
    /** Hoeveel breder het beeld wordt door de snelheid (0.1 = 10%). */
    private static float fovBoost;
    private static float previousFovBoost;
    private static @Nullable ThrusterSound sound;

    public static boolean isEngaged() {
        return engaged;
    }

    public static float thrust() {
        return thrust;
    }

    public static boolean isBoosting() {
        return engaged && boosting;
    }

    // ================================================================ Elke tick

    /** Vóór de beweging van deze tick: stuwkracht, vaart en zwaartekracht. */
    public static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player) {
            return;
        }
        previousBank = bank;
        previousFovBoost = fovBoost;
        if (!engaged) {
            bank *= 0.8f;
            fovBoost *= 0.85f;
            thrust = 0;
            return;
        }
        if (!allowed(player) || player.isInWater() || player.isInLava() || player.isPassenger()
                || player.isFallFlying()) {
            stop(true);
            return;
        }
        Input input = player.input;
        if (player.onGround() && !input.jumping) {
            // Op de grond: gewoon lopen. Na even staan landen de stuwraketten (zie Post).
            thrust = 0.1f;
            bank *= 0.8f;
            fovBoost *= 0.85f;
            return;
        }
        fly(player, input);
    }

    private static void fly(LocalPlayer player, Input input) {
        float forward = input.forwardImpulse;
        float strafe = input.leftImpulse;
        boolean up = input.jumping;
        boolean down = input.shiftKeyDown;
        boosting = Minecraft.getInstance().options.keySprint.isDown() && forward > 0;
        // Stuwraket-upgrades in de laarzen (werkbank): meer stuwkracht en een hogere topsnelheid.
        double upgrade = 1 + StarkConfig.Workbench.THRUSTER_SPEED
                * com.senne.starktech.workbench.SuitUpgrades.thrustersOf(player);
        double power = StarkConfig.Suit.THRUST * (boosting ? StarkConfig.Suit.BOOST : 1.0) * upgrade;

        // Vooruit is waar je kijkt, ook omhoog of omlaag. Opzij is altijd plat.
        Vec3 look = player.getLookAngle();
        double yaw = Math.toRadians(player.getYRot());
        Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 push = look.scale(forward * power).add(left.scale(strafe * power * 0.6));
        double lift = (up ? StarkConfig.Suit.THRUST_UP : 0) - (down ? StarkConfig.Suit.THRUST_DOWN : 0);

        // Na deze methode doet Minecraft zelf nog luchtweerstand (x0,91 opzij en x0,98 in de
        // hoogte) en trekt het de zwaartekracht eraf, maar pas NA de beweging. Daarom rekenen we
        // hier terug: we zetten de vaart van vorige tick terug (inclusief de zwaartekracht die
        // Minecraft er toen afhaalde) en houden daar KEEP_SPEED van over.
        Vec3 v = player.getDeltaMovement();
        double keep = StarkConfig.Suit.KEEP_SPEED;
        double carriedY = v.y * (keep / 0.98) + keep * player.getGravity();
        boolean steeringUpOrDown = up || down || Math.abs(forward * look.y) > 0.05;
        if (!steeringUpOrDown) {
            // Niet aan het stijgen of dalen: het harnas houdt je op hoogte.
            carriedY *= StarkConfig.Suit.HOVER_DAMPING;
        }
        Vec3 next = new Vec3(v.x * (keep / 0.91) + push.x, carriedY + push.y + lift, v.z * (keep / 0.91) + push.z);
        double max = (boosting ? StarkConfig.Suit.MAX_BOOST_SPEED : StarkConfig.Suit.MAX_SPEED) * upgrade;
        if (next.length() > max) {
            next = next.normalize().scale(max);
        }
        // Vlak boven de grond remt het harnas zelf af: je landt, je stort niet neer.
        if (next.y < -StarkConfig.Suit.LANDING_SPEED
                && heightAboveGround(player) < StarkConfig.Suit.LANDING_HEIGHT) {
            next = new Vec3(next.x, Math.max(next.y * 0.7, -StarkConfig.Suit.LANDING_SPEED), next.z);
        }
        player.setDeltaMovement(next);
        player.resetFallDistance();

        // Voor het geluid en de HUD: hoe hard werken de stuwraketten?
        float wanted = 0.25f + 0.45f * Math.abs(forward) + (up ? 0.3f : 0) + (boosting ? 0.3f : 0);
        thrust += (Mth.clamp(wanted, 0, 1) - thrust) * 0.25f;

        // Overhellen in de bochten: opzij sturen en snel draaien laten het beeld kantelen.
        float turn = Mth.wrapDegrees(player.getYRot() - lastYaw);
        lastYaw = player.getYRot();
        float speed = (float) next.horizontalDistance();
        float wantedBank = Mth.clamp(-strafe * 10.0f + turn * 0.7f, -25.0f, 25.0f) * Mth.clamp(speed / 0.6f, 0.3f, 1.0f);
        bank += (wantedBank - bank) * 0.2f;
        // Hoe sneller, hoe breder het beeld: dan vóel je de snelheid.
        float wantedFov = Mth.clamp(((float) next.length() - 0.3f) * 0.12f, 0.0f, 0.18f);
        fovBoost += (wantedFov - fovBoost) * 0.15f;
    }

    /**
     * Na de beweging: heeft Minecraft net "vliegen" aangezet? Dat gebeurt bij dubbel op spatie
     * omdat je harnas je laat vliegen (zie EdithSuit). Dat creative-vliegen zetten we meteen
     * weer uit, en we gebruiken het als teken: stuwraketten aan (of uit).
     */
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            if (engaged) {
                stop(false);
            }
            return;
        }
        Abilities abilities = player.getAbilities();
        if (abilities.flying) {
            abilities.flying = false;
            player.onUpdateAbilities();
            if (engaged) {
                stop(true);
            } else if (allowed(player)) {
                start(player);
            }
        }
        if (engaged) {
            if (player.onGround() && !player.input.jumping) {
                if (++groundTicks > 8) {
                    stop(true); // geland
                }
            } else {
                groundTicks = 0;
            }
        }
    }

    /** Hoe ver de grond (of water) onder je voeten is, tot LANDING_HEIGHT blokken ver. */
    private static double heightAboveGround(LocalPlayer player) {
        Vec3 feet = player.position();
        Vec3 below = feet.add(0, -StarkConfig.Suit.LANDING_HEIGHT, 0);
        HitResult hit = player.level().clip(new ClipContext(feet, below, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, player));
        return hit.getType() == HitResult.Type.MISS ? StarkConfig.Suit.LANDING_HEIGHT : feet.y - hit.getLocation().y;
    }

    /** Mag je nu vliegen? Het hele harnas, en de server heeft het vliegen vrijgegeven. */
    private static boolean allowed(LocalPlayer player) {
        return player.getAbilities().mayfly && StarkSuitItem.fullSet(player) && EdithClientState.operational;
    }

    private static void start(LocalPlayer player) {
        engaged = true;
        groundTicks = 0;
        lastYaw = player.getYRot();
        thrust = 0.6f;
        PacketDistributor.sendToServer(new SuitFlightPayload(true));
        if (sound == null || sound.isStopped()) {
            sound = new ThrusterSound(player);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }

    private static void stop(boolean tellServer) {
        engaged = false;
        boosting = false;
        if (tellServer) {
            PacketDistributor.sendToServer(new SuitFlightPayload(false));
        }
        // Het geluid dooft zelf uit (zie ThrusterSound).
    }

    /** De server zette de stuwraketten uit (energie op, harnas uit...). */
    public static void serverStopped() {
        stop(false);
    }

    /** Bij uitloggen: alles terug naar het begin. */
    public static void reset() {
        stop(false);
        bank = previousBank = 0;
        fovBoost = previousFovBoost = 0;
        thrust = 0;
        sound = null;
    }

    // ================================================================ Beeld

    /** Het beeld helt over in de bochten. */
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float roll = Mth.lerp((float) event.getPartialTick(), previousBank, bank);
        if (Math.abs(roll) > 0.01f) {
            event.setRoll(event.getRoll() + roll);
        }
    }

    /** Hoeveel breder het beeld wordt door de snelheid, voor ComputeFov. */
    public static float fovFactor(float partialTick) {
        return 1.0f + Mth.lerp(partialTick, previousFovBoost, fovBoost);
    }
}
