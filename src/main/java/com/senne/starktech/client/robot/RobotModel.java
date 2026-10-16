package com.senne.starktech.client.robot;

import com.senne.starktech.StarkTech;
import com.senne.starktech.robot.GuardRobotEntity;
import com.senne.starktech.robot.RobotParts;
import com.senne.starktech.robot.RobotSlot;
import com.senne.starktech.robot.RobotStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Het 3D-model van een guard robot (hoofdstuk 21).
 *
 * Een robot heeft dezelfde botten als een mens (hoofd, lijf, twee armen, twee benen), zodat hij gratis
 * loopt en met zijn armen zwaait zoals elk mensachtig wezen. Aan elk bot hangen vier versies: één per
 * stijl (Commando, Trainer, Battle, Super Battle). Per robot zetten we alleen de gekozen stijl van elk
 * onderdeel zichtbaar (zie {@link #showParts}), zo kun je ze mengen.
 *
 * Omdat een Super Battle-lijf veel breder is dan een Battle-lijf, hangen de armen aan de schouders van het
 * gekozen LIJF (zie {@link #SHOULDER}) en het hoofd op zijn nek ({@link #NECK_Y}); de benen bepalen zelf
 * hoe ver ze uit elkaar staan ({@link #HIP}).
 *
 * De textuur (128 x 128, zie robot_textures() in tools/generate_textures.py) heeft voor elke stijl een
 * vierkant van 64 x 64 met vier "verven": de hoofdkleur, een donkere kleur voor gewrichten, lichtjes en
 * details (ogen, sleuven). Elk blokje kiest een verf; blokjes met dezelfde verf delen dezelfde plek op de
 * textuur. Dat mag: het zijn effen vlakken metaal. Zo hoeft de textuur niet precies op elk blokje te
 * passen, en kun je hier een blokje toevoegen zonder het plaatje opnieuw te tekenen.
 *
 * Maten in pixels (een blok is 16). Voor een arm en een been staat hier de RECHTER; de linker is het
 * spiegelbeeld. De binnenkant van een arm ligt op x = +1 (naast het lijf), net als bij een speler.
 */
public class RobotModel extends HumanoidModel<GuardRobotEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "guard_robot"), "main");

    /** Hoe breed elk lijf (de helft) bij de schouders is: daar hangen de armen. Volgorde van RobotStyle. */
    private static final float[] SHOULDER = {3.5f, 4.0f, 3.0f, 6.0f};
    /** Hoe ver het hoofd lager (+) staat, naar het lijf (de Super Battle heeft zijn hoofd in zijn borst). */
    private static final float[] NECK_Y = {0.0f, 0.0f, 0.0f, 1.0f};
    /** Hoe ver het hoofd naar voren (-) staat, naar het lijf. */
    private static final float[] NECK_Z = {0.0f, 0.0f, 0.0f, -1.5f};
    /** Hoe ver elk been van het midden staat, naar de stijl van de benen. */
    private static final float[] HIP = {2.0f, 2.0f, 1.5f, 2.5f};

    /** Per onderdeel de vier versies (op volgorde van RobotStyle). */
    private final Map<RobotSlot, ModelPart[]> versions = new EnumMap<>(RobotSlot.class);
    private final ModelPart[] leftArms = new ModelPart[4];
    private final ModelPart[] leftLegs = new ModelPart[4];

    public RobotModel(ModelPart root) {
        super(root);
        for (RobotSlot slot : RobotSlot.values()) {
            versions.put(slot, new ModelPart[4]);
        }
        for (RobotStyle style : RobotStyle.values()) {
            int i = style.ordinal();
            versions.get(RobotSlot.HEAD)[i] = head.getChild(style.id());
            versions.get(RobotSlot.BODY)[i] = body.getChild(style.id());
            versions.get(RobotSlot.ARMS)[i] = rightArm.getChild(style.id());
            versions.get(RobotSlot.LEGS)[i] = rightLeg.getChild(style.id());
            leftArms[i] = leftArm.getChild(style.id());
            leftLegs[i] = leftLeg.getChild(style.id());
        }
    }

    @Override
    public void setupAnim(GuardRobotEntity robot, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(robot, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        showParts(robot.parts());
    }

    /**
     * Zet van elk onderdeel de gekozen stijl zichtbaar, en hang armen, hoofd en benen op de juiste plek.
     * (Na de gewone houding: die zet de armen op 5 pixels van het midden, zoals bij een speler.)
     */
    public void showParts(RobotParts parts) {
        for (RobotSlot slot : RobotSlot.values()) {
            ModelPart[] parts4 = versions.get(slot);
            for (int i = 0; i < 4; i++) {
                parts4[i].visible = i == parts.get(slot).ordinal();
            }
        }
        for (int i = 0; i < 4; i++) {
            leftArms[i].visible = i == parts.arms().ordinal();
            leftLegs[i].visible = i == parts.legs().ordinal();
        }
        int body = parts.body().ordinal();
        // Bij een slag draait de arm in een cirkel van 5 pixels om het midden: even groot maken als dit lijf.
        float shoulder = (SHOULDER[body] + 1.0f) / 5.0f;
        rightArm.x *= shoulder;
        rightArm.z *= shoulder;
        leftArm.x *= shoulder;
        leftArm.z *= shoulder;
        head.y += NECK_Y[body];
        head.z = NECK_Z[body];
        rightLeg.x = -HIP[parts.legs().ordinal()];
        leftLeg.x = HIP[parts.legs().ordinal()];
        hat.copyFrom(head);
    }

    // ================================================================ De vormen

    /** De vier verven op de textuur van een stijl, met waar ze in zijn vierkant beginnen. */
    private enum Paint {
        /** De hoofdkleur (64 x 32). */
        MAIN(0, 0),
        /** Donker: gewrichten, nek, handen (64 x 16). */
        DARK(0, 32),
        /** Lichtjes; bij de Commando en de Trainer gloeien die ook in het donker (32 x 16). */
        LIGHT(0, 48),
        /** Bijna zwart: ogen, sleuven, lopen (32 x 16). */
        DETAIL(32, 48);

        final int u;
        final int v;

        Paint(int u, int v) {
            this.u = u;
            this.v = v;
        }
    }

    /** Eén blokje: verf, hoek (x, y, z) en maat (breed, hoog, diep). */
    private record Box(Paint paint, float x, float y, float z, int w, int h, int d) {
        Box mirrored() {
            return new Box(paint, -x - w, y, z, w, h, d);
        }
    }

    /** Een groepje blokjes dat samen kantelt (zoals de snuit van de Battle); de meeste stukken hebben er één. */
    private record Piece(PartPose pose, List<Box> boxes) {
        Piece mirrored() {
            return new Piece(PartPose.offsetAndRotation(-pose.x, pose.y, pose.z, pose.xRot, -pose.yRot, -pose.zRot),
                    boxes.stream().map(Box::mirrored).toList());
        }
    }

    /** Een onderdeel in één stijl: een paar stukken. */
    private static final class Shape {
        final List<Piece> pieces = new ArrayList<>();
        private final List<Box> straight = new ArrayList<>();

        Shape() {
            pieces.add(new Piece(PartPose.ZERO, straight));
        }

        Shape box(Paint paint, float x, float y, float z, int w, int h, int d) {
            straight.add(new Box(paint, x, y, z, w, h, d));
            return this;
        }

        Shape piece(PartPose pose, Box... boxes) {
            pieces.add(new Piece(pose, List.of(boxes)));
            return this;
        }
    }

    private static Box box(Paint paint, float x, float y, float z, int w, int h, int d) {
        return new Box(paint, x, y, z, w, h, d);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5, 2, 0));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5, 2, 0));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-2, 12, 0));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(2, 12, 0));
        for (RobotStyle style : RobotStyle.values()) {
            add(head, style, head(style), false);
            add(body, style, body(style), false);
            add(rightArm, style, arm(style), false);
            add(leftArm, style, arm(style), true);
            add(rightLeg, style, leg(style), false);
            add(leftLeg, style, leg(style), true);
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Hang een onderdeel in één stijl aan zijn bot (het eerste stuk zelf, de gekantelde als kinderen). */
    private static void add(PartDefinition bone, RobotStyle style, Shape shape, boolean mirror) {
        List<Piece> pieces = shape.pieces.stream().map(p -> mirror ? p.mirrored() : p).toList();
        PartDefinition group = bone.addOrReplaceChild(style.id(), cubes(style, pieces.get(0).boxes()), PartPose.ZERO);
        for (int i = 1; i < pieces.size(); i++) {
            group.addOrReplaceChild("piece_" + i, cubes(style, pieces.get(i).boxes()), pieces.get(i).pose());
        }
    }

    private static CubeListBuilder cubes(RobotStyle style, List<Box> boxes) {
        int u0 = style.ordinal() % 2 * 64;
        int v0 = style.ordinal() / 2 * 64;
        CubeListBuilder builder = CubeListBuilder.create();
        for (Box b : boxes) {
            builder.texOffs(u0 + b.paint().u, v0 + b.paint().v).addBox(b.x(), b.y(), b.z(), b.w(), b.h(), b.d());
        }
        return builder;
    }

    /** Het hoofd: zijn nek begint op y = 0 (boven op het lijf), hij steekt omhoog (min). Voorkant = -z. */
    private static Shape head(RobotStyle style) {
        return switch (style) {
            // Rond en gepantserd, met één rond oog in een donkere kom.
            case COMMANDO -> new Shape()
                    .box(Paint.DARK, -1, -1, -1, 2, 1, 2)
                    .box(Paint.MAIN, -3, -6, -3, 6, 5, 6)
                    .box(Paint.MAIN, -2, -7, -2, 4, 1, 4)
                    .box(Paint.DETAIL, -1.5f, -4.5f, -3.3f, 3, 3, 1)
                    .box(Paint.LIGHT, -1, -4, -3.6f, 2, 2, 1);
            // Een gladde witte helm (rond door drie doosjes in elkaar), een blauwe vizierstreep en oorlichtjes.
            case TRAINER -> new Shape()
                    .box(Paint.DARK, -1, -1, -1, 2, 1, 2)
                    .box(Paint.MAIN, -3, -7, -3, 6, 6, 6)
                    .box(Paint.MAIN, -3.5f, -6, -2.5f, 7, 4, 5)
                    .box(Paint.MAIN, -2.5f, -6, -3.5f, 5, 4, 7)
                    .box(Paint.MAIN, -2, -7.5f, -2, 4, 1, 4)
                    .box(Paint.LIGHT, -2.5f, -5, -3.8f, 5, 1, 1)
                    .box(Paint.LIGHT, -3.8f, -4.5f, -0.5f, 1, 1, 1)
                    .box(Paint.LIGHT, 2.8f, -4.5f, -0.5f, 1, 1, 1);
            // Een dunne nek, een smalle schedel en een lange snuit die een beetje naar beneden wijst.
            case BATTLE -> new Shape()
                    .box(Paint.DARK, -0.5f, -3, -0.5f, 1, 3, 1)
                    .box(Paint.MAIN, -2, -6, -1, 4, 3, 4)
                    .box(Paint.MAIN, -1.5f, -6.5f, 0, 3, 1, 3)
                    .piece(PartPose.offsetAndRotation(0, -4.5f, -1, 0.2f, 0, 0),
                            box(Paint.MAIN, -1.5f, -1, -6, 3, 2, 6),
                            box(Paint.DETAIL, -2, -0.5f, -5, 1, 1, 1),
                            box(Paint.DETAIL, 1, -0.5f, -5, 1, 1, 1));
            // Klein en laag, met een donkere kijksleuf: hij zit half in de borst van zijn lijf.
            case SUPER_BATTLE -> new Shape()
                    .box(Paint.MAIN, -2.5f, -4, -3, 5, 4, 5)
                    .box(Paint.DETAIL, -2, -3, -3.4f, 4, 1, 1)
                    .box(Paint.DARK, -1.5f, -4.5f, -2, 3, 1, 3);
        };
    }

    /** Het lijf: van de schouders (y = 0) tot de heupen (y = 12). */
    private static Shape body(RobotStyle style) {
        return switch (style) {
            // Gepantserde borst met platen voor en achter, een donkere buik en een bekken.
            case COMMANDO -> new Shape()
                    .box(Paint.MAIN, -3.5f, 0, -2, 7, 6, 4)
                    .box(Paint.MAIN, -3, 0.5f, -2.5f, 6, 4, 1)
                    .box(Paint.MAIN, -3, 0.5f, 1.5f, 6, 5, 1)
                    .box(Paint.DARK, -2, 6, -1.5f, 4, 3, 3)
                    .box(Paint.MAIN, -3, 9, -2, 6, 3, 4);
            // Rond en wit, met een blauw lampje op de borst en een blauwe streep op de riem.
            case TRAINER -> new Shape()
                    .box(Paint.MAIN, -4, 0, -2.5f, 8, 6, 5)
                    .box(Paint.MAIN, -3.5f, 0.5f, -3, 7, 5, 6)
                    .box(Paint.LIGHT, -1, 1.5f, -3.3f, 2, 2, 1)
                    .box(Paint.DARK, -2.5f, 6, -2, 5, 3, 4)
                    .box(Paint.MAIN, -3, 9, -2, 6, 3, 4)
                    .box(Paint.LIGHT, -2, 9.5f, -2.3f, 4, 1, 1);
            // Een smalle borst, een rugzak, een dunne ruggengraat en een bekken.
            case BATTLE -> new Shape()
                    .box(Paint.MAIN, -3, 0, -1.5f, 6, 5, 3)
                    .box(Paint.MAIN, -2.5f, 0.5f, -2, 5, 4, 1)
                    .box(Paint.MAIN, -2.5f, 0, 1.5f, 5, 6, 2)
                    .box(Paint.DARK, -0.5f, 5, -0.5f, 1, 4, 1)
                    .box(Paint.MAIN, -2.5f, 9, -1.5f, 5, 3, 3);
            // Een enorme borst met een bult achter het hoofd, een smal middel en een bekken.
            case SUPER_BATTLE -> new Shape()
                    .box(Paint.MAIN, -6, -1, -3.5f, 12, 8, 7)
                    .box(Paint.MAIN, -4.5f, -3, -0.5f, 9, 2, 5)
                    .box(Paint.MAIN, -5, 0, -4.5f, 10, 6, 1)
                    .box(Paint.DARK, -1.5f, 7, -1.5f, 3, 3, 3)
                    .box(Paint.MAIN, -3, 10, -2, 6, 2, 4);
        };
    }

    /** De rechterarm: van de schouder (y = -2) tot de hand (y = 10 en iets verder). */
    private static Shape arm(RobotStyle style) {
        return switch (style) {
            case COMMANDO -> new Shape()
                    .box(Paint.MAIN, -2, -2.5f, -2, 3, 3, 4)
                    .box(Paint.DARK, -1, 0.5f, -1, 2, 4, 2)
                    .box(Paint.MAIN, -1.5f, 4.5f, -1.5f, 3, 5, 3)
                    .box(Paint.DARK, -1, 9.5f, -1, 2, 2, 2);
            case TRAINER -> new Shape()
                    .box(Paint.MAIN, -2.5f, -2.5f, -2, 4, 3, 4)
                    .box(Paint.MAIN, -1.5f, 0.5f, -1.5f, 3, 4, 3)
                    .box(Paint.DARK, -1, 4.5f, -1, 2, 1, 2)
                    .box(Paint.MAIN, -1.5f, 5.5f, -1.5f, 3, 4, 3)
                    .box(Paint.LIGHT, -2, 8, -2, 4, 1, 4)
                    .box(Paint.DARK, -1, 9.5f, -1, 2, 2, 2);
            // Dunne staafjes met gewrichtjes.
            case BATTLE -> new Shape()
                    .box(Paint.DARK, -1, -2, -1, 2, 2, 2)
                    .box(Paint.MAIN, -1, 0, -1, 2, 4, 2)
                    .box(Paint.DARK, -1, 4, -1, 2, 1, 2)
                    .box(Paint.MAIN, -1, 5, -1, 2, 4, 2)
                    .box(Paint.DARK, -1, 9, -1.5f, 2, 2, 3);
            // Een schouderstuk, een dikke onderarm met een blaster in de pols, en een grote hand.
            case SUPER_BATTLE -> new Shape()
                    .box(Paint.MAIN, -3, -3, -2.5f, 4, 3, 5)
                    .box(Paint.DARK, -2, 0, -1.5f, 3, 3, 3)
                    .box(Paint.MAIN, -3, 3, -2.5f, 4, 6, 5)
                    .box(Paint.DETAIL, -2.5f, 6, -3.5f, 2, 2, 1)
                    .box(Paint.DARK, -2.5f, 9, -1.5f, 3, 2, 3);
        };
    }

    /** Het rechterbeen: van de heup (y = 0) tot de grond (y = 12). */
    private static Shape leg(RobotStyle style) {
        return switch (style) {
            case COMMANDO -> new Shape()
                    .box(Paint.MAIN, -1.5f, 0, -1.5f, 3, 5, 3)
                    .box(Paint.DARK, -1, 5, -1, 2, 1, 2)
                    .box(Paint.MAIN, -1.5f, 4.5f, -2, 3, 2, 1)
                    .box(Paint.MAIN, -1.5f, 6, -1.5f, 3, 5, 3)
                    .box(Paint.DARK, -1.5f, 11, -2.5f, 3, 1, 4);
            case TRAINER -> new Shape()
                    .box(Paint.MAIN, -2, 0, -2, 4, 5, 4)
                    .box(Paint.DARK, -1.5f, 5, -1.5f, 3, 1, 3)
                    .box(Paint.LIGHT, -1, 5, -2.3f, 2, 1, 1)
                    .box(Paint.MAIN, -2, 6, -2, 4, 5, 4)
                    .box(Paint.MAIN, -2, 11, -3, 4, 1, 5);
            case BATTLE -> new Shape()
                    .box(Paint.MAIN, -1, 0, -1, 2, 5, 2)
                    .box(Paint.DARK, -1, 5, -1, 2, 1, 2)
                    .box(Paint.MAIN, -1, 6, -1, 2, 5, 2)
                    .box(Paint.DARK, -1, 11, -3, 2, 1, 4);
            // Dikke onderbenen en brede voeten.
            case SUPER_BATTLE -> new Shape()
                    .box(Paint.DARK, -1.5f, 0, -1.5f, 3, 5, 3)
                    .box(Paint.MAIN, -2, 5, -2, 4, 6, 4)
                    .box(Paint.DARK, -2, 11, -3, 4, 1, 5);
        };
    }
}
