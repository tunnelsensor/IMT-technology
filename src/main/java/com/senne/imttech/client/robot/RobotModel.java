package com.senne.imttech.client.robot;

import com.senne.imttech.ImtTech;
import com.senne.imttech.bot.BotModule;
import com.senne.imttech.robot.GuardRobotEntity;
import com.senne.imttech.robot.RobotParts;
import com.senne.imttech.robot.RobotSlot;
import com.senne.imttech.robot.RobotStyle;
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
 *
 * Hoofdstuk 22: wat er op zijn printplaat gesoldeerd is, zie je ook op de robot (zie {@link #moduleShapes}):
 * een schoteltje op zijn hoofd (Scan), een camera opzij (Camera), een cyaan lensstrook (Face Recognition), een
 * sensor op zijn borst (Distance), een sprietantenne (Pathfinding) en een zonnepaneel op zijn rug, een laser op
 * zijn rechterarm en een geweer op zijn linker. Die blokjes hangen in elke stijl op een eigen plek, en hebben
 * hun eigen verven onderaan de textuur (die is daarom 128 x 160).
 */
public class RobotModel extends HumanoidModel<GuardRobotEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "guard_robot"), "main");

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
    /** Per module de blokjes die hem laten zien (in elke stijl en elk bot waar hij hangt). */
    private final Map<BotModule, List<ModelPart>> modules = new EnumMap<>(BotModule.class);

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
            for (ModelPart group : new ModelPart[] {head.getChild(style.id()), body.getChild(style.id()),
                    rightArm.getChild(style.id()), leftArm.getChild(style.id())}) {
                for (BotModule module : BotModule.values()) {
                    if (group.hasChild("mod_" + module.id())) {
                        modules.computeIfAbsent(module, m -> new ArrayList<>()).add(group.getChild("mod_" + module.id()));
                    }
                }
            }
        }
    }

    @Override
    public void setupAnim(GuardRobotEntity robot, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(robot, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        showParts(robot.parts());
        // De modules die op zijn printplaat zitten (hoofdstuk 22).
        int soldered = robot.board().modules();
        for (Map.Entry<BotModule, List<ModelPart>> entry : modules.entrySet()) {
            boolean on = (soldered & 1 << entry.getKey().ordinal()) != 0;
            for (ModelPart part : entry.getValue()) {
                part.visible = on;
            }
        }
        // In het voorbeeld van de Robot Assembler: wat er nog niet ligt, tekenen we niet.
        for (RobotSlot slot : RobotSlot.values()) {
            if (!robot.shows(slot)) {
                versions.get(slot)[robot.parts().get(slot).ordinal()].visible = false;
                if (slot == RobotSlot.ARMS) {
                    leftArms[robot.parts().arms().ordinal()].visible = false;
                }
                if (slot == RobotSlot.LEGS) {
                    leftLegs[robot.parts().legs().ordinal()].visible = false;
                }
            }
        }
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

    /**
     * De verven op de textuur: vier per stijl (met waar ze in het vierkant van die stijl beginnen), en de verven van
     * de modules (onderaan de textuur, voor alle stijlen hetzelfde; elk 32 x 16).
     */
    private enum Paint {
        /** De hoofdkleur (64 x 32). */
        MAIN(0, 0, false),
        /** Donker: gewrichten, nek, handen (64 x 16). */
        DARK(0, 32, false),
        /** Lichtjes; bij de Commando en de Trainer gloeien die ook in het donker (32 x 16). */
        LIGHT(0, 48, false),
        /** Bijna zwart: ogen, sleuven, lopen (32 x 16). */
        DETAIL(32, 48, false),
        /** Modules: lichtgrijs staal. */
        STEEL(0, 128, true),
        /** Modules: donker geweermetaal. */
        GUNMETAL(32, 128, true),
        /** Modules: een cyaan lens (gloeit). */
        GLASS(64, 128, true),
        /** Modules: een rood lampje of de punt van de laser (gloeit). */
        RED(96, 128, true),
        /** Modules: blauwe zonnecellen. */
        SOLAR(0, 144, true),
        /** Modules: een witte antenne. */
        ANTENNA(32, 144, true);

        final int u;
        final int v;
        /** Een moduleverf: niet verschoven naar het vierkant van een stijl. */
        final boolean shared;

        Paint(int u, int v, boolean shared) {
            this.u = u;
            this.v = v;
            this.shared = shared;
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
            // De modules (hoofdstuk 22), aan de versie van deze stijl van elk bot.
            addModules(head.getChild(style.id()), style, "head", false);
            addModules(body.getChild(style.id()), style, "body", false);
            addModules(rightArm.getChild(style.id()), style, "right_arm", false);
            addModules(leftArm.getChild(style.id()), style, "left_arm", true);
        }
        return LayerDefinition.create(mesh, 128, 160);
    }

    private static void addModules(PartDefinition group, RobotStyle style, String bone, boolean mirror) {
        for (Map.Entry<BotModule, List<Box>> entry : moduleShapes(style, bone).entrySet()) {
            List<Box> boxes = mirror ? entry.getValue().stream().map(Box::mirrored).toList() : entry.getValue();
            group.addOrReplaceChild("mod_" + entry.getKey().id(), cubes(style, boxes), PartPose.ZERO);
        }
    }

    /** Waar het hoofd van elke stijl bovenaan ophoudt (y), waar zijn voorkant is (z) en hoe breed (de helft). */
    private static final float[] HEAD_TOP = {-7.0f, -7.5f, -6.5f, -4.5f};
    private static final float[] HEAD_FRONT = {-3.0f, -3.5f, -1.0f, -3.0f};
    private static final float[] HEAD_SIDE = {3.0f, 3.5f, 2.0f, 2.5f};
    /** De voor- en achterkant van elk lijf (z). */
    private static final float[] BODY_FRONT = {-2.5f, -3.0f, -2.0f, -4.5f};
    private static final float[] BODY_BACK = {2.5f, 3.0f, 3.5f, 4.5f};
    /** De buitenkant (x, rechterarm) en het midden van de onderarm (y) van elke stijl armen. */
    private static final float[] ARM_OUT = {-1.5f, -1.5f, -1.0f, -3.0f};
    private static final float[] FORE_Y = {6.5f, 7.0f, 6.5f, 5.0f};

    /**
     * De blokjes van de modules aan dit bot ("head", "body", "right_arm" of "left_arm") in deze stijl. Voor de
     * linkerarm staan ze hier als op de rechterarm; {@link #addModules} spiegelt ze.
     */
    private static Map<BotModule, List<Box>> moduleShapes(RobotStyle style, String bone) {
        int s = style.ordinal();
        Map<BotModule, List<Box>> map = new EnumMap<>(BotModule.class);
        switch (bone) {
            case "head" -> {
                float top = HEAD_TOP[s];
                float side = HEAD_SIDE[s];
                float front = HEAD_FRONT[s];
                // Scan: een paaltje met een schoteltje erop, links bovenop.
                map.put(BotModule.SCAN, List.of(box(Paint.STEEL, 0.5f, top - 3, -0.5f, 1, 3, 1),
                        box(Paint.ANTENNA, -0.5f, top - 4, -1.5f, 3, 1, 3)));
                // Camera: een kastje rechts opzij met een lens naar voren.
                map.put(BotModule.CAMERA, List.of(box(Paint.GUNMETAL, -side - 1.5f, top + 1, -1.5f, 2, 2, 3),
                        box(Paint.GLASS, -side - 1.0f, top + 1.5f, -2.2f, 1, 1, 1)));
                // Face Recognition: een cyaan lensstrook bovenaan de voorkant.
                int w = Math.max(2, Math.round(2 * side - 2));
                map.put(BotModule.FACE_RECOGNITION, List.of(box(Paint.GLASS, -w / 2.0f, top + 1, front - 0.4f, w, 1, 1)));
            }
            case "body" -> {
                float front = BODY_FRONT[s];
                float back = BODY_BACK[s];
                // Distance: een sensortje op de borst met een rood lampje.
                map.put(BotModule.DISTANCE, List.of(box(Paint.GUNMETAL, 1.0f, 2.0f, front - 1, 2, 2, 1),
                        box(Paint.RED, 1.5f, 2.5f, front - 1.3f, 1, 1, 1)));
                // Pathfinding: een sprietantenne op de rug, met een rood lampje bovenaan.
                map.put(BotModule.PATHFINDING, List.of(box(Paint.ANTENNA, -2.5f, -6, back - 1, 1, 7, 1),
                        box(Paint.RED, -2.5f, -7, back - 1, 1, 1, 1)));
                // Solar Panel: een plat paneel op de rug.
                map.put(BotModule.SOLAR_PANEL, List.of(box(Paint.SOLAR, -3, 0.5f, back, 6, 6, 1)));
            }
            case "right_arm" -> {
                // Laser: een buisje aan de buitenkant van de onderarm, met een rode punt.
                float out = ARM_OUT[s];
                float y = FORE_Y[s];
                map.put(BotModule.LASER, List.of(box(Paint.GUNMETAL, out - 1, y, -2.5f, 1, 1, 4),
                        box(Paint.RED, out - 1, y, -3.5f, 1, 1, 1)));
            }
            case "left_arm" -> {
                // Gun: een dikkere loop aan de buitenkant van de onderarm (de linkerarm, dus gespiegeld).
                float out = ARM_OUT[s];
                float y = FORE_Y[s];
                map.put(BotModule.GUN, List.of(box(Paint.GUNMETAL, out - 2, y - 0.5f, -3, 2, 2, 5),
                        box(Paint.STEEL, out - 2, y - 1.5f, -1, 2, 1, 2)));
            }
            default -> { }
        }
        return map;
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
            int u = b.paint().shared ? b.paint().u : u0 + b.paint().u;
            int v = b.paint().shared ? b.paint().v : v0 + b.paint().v;
            builder.texOffs(u, v).addBox(b.x(), b.y(), b.z(), b.w(), b.h(), b.d());
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
