package com.senne.imttech.client.raven;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;

/**
 * De animatie van een sluipaanval (8 november, jouw wens: "een leuke animatie zodat het geen instakill is maar een beetje
 * tijd kost"). De server zegt alleen hoe ver het is (0 tot 1, zie RavenSoldierEntity.takedownProgress); hier wordt dat
 * een houding van het mensmodel, in vier stukken die in elkaar overvloeien:
 * <ol>
 *   <li><b>worstelen</b> (tot ruim de helft): zijn handen gaan omhoog naar je arm om zijn nek en maaien heen en weer, zijn
 *       hoofd gaat achterover en schudt, hij trapt met zijn benen, en zijn hele lijf trilt;</li>
 *   <li><b>slap</b>: zijn armen zakken langs zijn lijf en zijn hoofd valt voorover;</li>
 *   <li><b>zakken</b>: hij gaat door zijn knieën tot hij zit (de benen naar voren);</li>
 *   <li><b>vallen</b>: hij valt achterover, tegen je aan. Daarna ligt zijn lichaam er op zijn rug (RavenBodyRenderer), net
 *       zoals de animatie eindigt.</li>
 * </ol>
 * Het kantelen en zakken van zijn hele lijf gebeurt in RavenSoldierRenderer.setupRotations; de armen, benen en het hoofd
 * hier.
 */
final class TakedownPose {
    private TakedownPose() {}

    /** Hoe hoog zijn heupen zitten (blokken): daar draait hij om als hij achterover valt. */
    static final float HIP = 0.75f;
    /** Zo ver zakt hij als hij zit (zijn heupen komen vlak boven de grond). */
    static final float SINK = 0.62f;
    /** Zo ver valt hij achterover voor hij neer is (graden). */
    static final float FALL_DEGREES = 70.0f;
    /** Zo ver leunt hij achterover terwijl hij worstelt (graden). */
    static final float LEAN_DEGREES = 9.0f;

    /** De vier stukken, elk van 0 (nog niet) tot 1 (helemaal). */
    record Phases(float struggle, float limp, float sink, float fall) {}

    static Phases phases(float progress) {
        float in = smooth(0.0f, 0.08f, progress);
        float out = 1.0f - smooth(0.48f, 0.66f, progress);
        return new Phases(in * out, smooth(0.5f, 0.74f, progress), smooth(0.58f, 0.84f, progress), smooth(0.8f, 1.0f, progress));
    }

    /** Zacht van 0 naar 1 tussen {@code from} en {@code to} (geen schok aan het begin of het eind). */
    static float smooth(float from, float to, float x) {
        float t = Mth.clamp((x - from) / (to - from), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** Zet het model in de houding van dit moment ({@code age}: zijn tickCount plus de partial tick, voor het heen en weer). */
    static void apply(HumanoidModel<?> model, float progress, float age) {
        Phases p = phases(progress);
        float struggle = p.struggle();
        float sink = p.sink();
        float fall = p.fall();
        float flail = Mth.sin(age * 1.9f);
        float flail2 = Mth.cos(age * 2.4f);

        // Het hoofd: achterover (de arm om zijn nek) en schudden; slap valt het voorover en een beetje opzij.
        model.head.xRot = Mth.lerp(p.limp(), -0.45f * struggle + Mth.sin(age * 2.1f) * 0.12f * struggle, 0.65f);
        model.head.yRot = Mth.sin(age * 2.7f) * 0.4f * struggle;
        model.head.zRot = 0.3f * p.limp();
        model.hat.copyFrom(model.head);

        // De armen: omhoog naar je arm, wild heen en weer (bij een opgeheven arm is zRot naar binnen); slap hangen ze langs
        // zijn lijf, en als hij zit een beetje naar voren.
        float hang = -0.35f * sink;
        model.rightArm.xRot = Mth.lerp(struggle, hang, -2.3f + flail * 0.45f);
        model.leftArm.xRot = Mth.lerp(struggle, hang, -2.3f - flail2 * 0.45f);
        model.rightArm.yRot = 0.0f;
        model.leftArm.yRot = 0.0f;
        model.rightArm.zRot = Mth.lerp(struggle, 0.12f, 0.45f + flail2 * 0.2f);
        model.leftArm.zRot = Mth.lerp(struggle, -0.12f, -0.45f - flail * 0.2f);

        // De benen: trappen tijdens het worstelen; dan zitten (naar voren), en terwijl hij achterover valt blijven ze op de
        // grond liggen (het kantelen van zijn lijf wordt hier weer teruggedraaid).
        float kick = Mth.sin(age * 1.6f) * 0.45f * struggle;
        float sit = -1.41f * sink + (float) Math.toRadians(FALL_DEGREES) * fall;
        model.rightLeg.xRot = kick + sit;
        model.leftLeg.xRot = -kick + sit;
        model.rightLeg.yRot = 0.3f * sink;
        model.leftLeg.yRot = -0.3f * sink;
        model.rightLeg.zRot = 0.08f * sink;
        model.leftLeg.zRot = -0.08f * sink;
    }
}
