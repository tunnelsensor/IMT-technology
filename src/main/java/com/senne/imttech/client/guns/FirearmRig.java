package com.senne.imttech.client.guns;

import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunPart;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Hoe een vuurwapen in je eigen beeld hangt en beweegt (hoofdstuk 52), in de maten van het model (zie
 * tools/generate_gun_models.py: 16 = één blok, de loop wijst naar -z en ligt op x = 8). Zoals GunRig voor de
 * lasergeweren: de greep (je rechterhand), de steun (je linkerhand), het vizier (voor je oog als je richt), de loop,
 * waar het magazijn uit het wapen komt, en een paar getallen voor de animaties.
 *
 * Hoofdstuk 103: elk wapen zijn eigen maten ({@link #of}). Een lang wapen (de MP5...) houd je met je linkerhand vooraan
 * vast, en je laadt het door met een hendel ({@link #handle}): de spanhendel, de grendel of de pomp.
 */
public final class FirearmRig {
    /** Hoe groot het wapen in beeld is (1 = 16 pixels van het model per blok). */
    public final float scale;
    public final Vector3f grip;
    public final Vector3f support;
    public final Vector3f sight;
    public final Vector3f muzzle;
    /** Waar het magazijn in het wapen zit (zijn bovenkant), en de richting waarin het eruit schuift. */
    public final Vector3f magazine;
    public final Vector3f magazineAxis;
    /** Waar de uitwerpopening zit (daar steekt een klemzittende huls uit). */
    public final Vector3f port;
    /** Waar de greep in je beeld zit als je vanuit de heup schiet (blokken: rechts, omhoog, naar achteren). */
    public final Vector3f hip;
    /** Zo ver voor je oog zit het vizier als je richt (blokken). */
    public final float sightDistance;
    /** Zo ver schuift de slede (of de grendel) terug bij een schot (pixels van het model), en zo lang duurt dat (ticks). */
    public final float cycle;
    public final float cycleTicks;
    /** Zo ver springt het wapen naar achteren en omhoog bij een schot. */
    public final float kickBack;
    public final float kickUp;
    /**
     * Waar je linkerhand het wapen doorlaadt (hoofdstuk 103): achter op de slede van een pistool, de spanhendel van de
     * MP5, de grendel, de pomp. En hoe ver die hendel naar achteren gaat (pixels).
     */
    public final Vector3f handle;
    public final float handleTravel;
    /** Het midden van de onderkant van het magazijn zoals het in het wapen zit, en waar zijn bovenste patroon ligt. */
    public final Vector3f magazineFoot;
    public final Vector3f magazineTop;
    /** Hoe schuin het magazijn in het wapen staat (radialen): zoveel draaien we terug om het rechtop te zetten. */
    public final float magazineAngle;
    /** Zo groot hangt het wapen op je rug of voor je borst (aan zijn sling), en waar het dan om draait (het midden). */
    public final float slingScale;
    public final Vector3f slingPivot;
    /**
     * Een grendel (de AWP): zoveel graden draait de grendel met zijn knop omhoog voor je hem naar achteren trekt, en weer
     * omlaag als hij voorin zit. 0 = geen grendel. De grendel draait om de as van de loop ({@link #muzzle}).
     */
    public float handleLift;
    /**
     * Hoofdstuk 103, stap 5: waar de voet van een kijker (de Red Dot Sight of de Sniper Scope) op dit wapen staat: het
     * midden van de bovenkant van de behuizing (model-pixels). {@link #sightRiser}: een verhoging eronder (de MP5: zo
     * kijk je over zijn eigen vizier heen). {@link #sightOnSlide}: de kijker staat op de slede en gaat bij elk schot mee
     * naar achteren (de Glock).
     */
    public Vector3f sightMount = new Vector3f(8, 16, 0);
    public float sightRiser;
    public boolean sightOnSlide;

    private FirearmRig(float scale, Vector3f grip, Vector3f support, Vector3f sight, Vector3f muzzle, Vector3f magazine,
                       Vector3f magazineAxis, Vector3f port, Vector3f hip, float sightDistance, float cycle, float cycleTicks,
                       float kickBack, float kickUp, Vector3f handle, float handleTravel, Vector3f magazineFoot,
                       Vector3f magazineTop, float magazineAngle, float slingScale, Vector3f slingPivot) {
        this.scale = scale;
        this.grip = grip;
        this.support = support;
        this.sight = sight;
        this.muzzle = muzzle;
        this.magazine = magazine;
        this.magazineAxis = magazineAxis;
        this.port = port;
        this.hip = hip;
        this.sightDistance = sightDistance;
        this.cycle = cycle;
        this.cycleTicks = cycleTicks;
        this.kickBack = kickBack;
        this.kickUp = kickUp;
        this.handle = handle;
        this.handleTravel = handleTravel;
        this.magazineFoot = magazineFoot;
        this.magazineTop = magazineTop;
        this.magazineAngle = magazineAngle;
        this.slingScale = slingScale;
        this.slingPivot = slingPivot;
    }

    /** Hoe schuin de greep (en dus het magazijn) van de Glock naar achteren helt: 22,5 graden, de onderkant naar +z. */
    public static final float GRIP_ANGLE = (float) Math.toRadians(22.5);

    /**
     * De maten van het model van hoofdstuk 74 (tools/glock19_model.py, nagemeten van een foto van een echte Glock 19):
     * je rechterhand op de greep, je linkerhand ertegen, de vizierlijn (de bodem van de witte U op de keep, het witte
     * stipje van de korrel zit daar net in), de loop, de onderkant van het magazijn, en de uitwerpopening.
     */
    private static final FirearmRig GLOCK_19 = new FirearmRig(0.44f,
            new Vector3f(8, 7.6f, 11.6f), new Vector3f(7.0f, 6.4f, 10.8f), new Vector3f(8, 13.21f, 13.4f),
            new Vector3f(8, 12.14f, 0.49f), new Vector3f(8, 4.0f, 11.9f),
            new Vector3f(0, -(float) Math.cos(GRIP_ANGLE), (float) Math.sin(GRIP_ANGLE)),
            new Vector3f(9.2f, 12.6f, 7.4f),
            new Vector3f(0.21f, -0.21f, -0.42f), 0.46f, 2.6f, 2, 0.055f, 6.5f,
            new Vector3f(8.2f, 13.6f, 12.4f), 2.6f,
            new Vector3f(8, 3.82f, 11.94f), new Vector3f(8.13f, 11.23f, 9.2f), GRIP_ANGLE,
            0.5f, new Vector3f(8, 9, 8)).optic(13.33f, 10.5f, 0, true);

    /**
     * De MP5 (hoofdstuk 103, tools/mp5_model.py): je rechterhand op de greep, je linkerhand onder de handbeschermer, het
     * venster van het achterste vizier voor je oog, de spanhendel links vooraan op de spanbuis, en het gebogen magazijn
     * dat recht naar beneden uit de schacht komt.
     */
    private static final FirearmRig MP5 = new FirearmRig(0.34f,
            new Vector3f(8, 7.1f, 13.2f), new Vector3f(8, 13.0f, -8.2f), new Vector3f(8, 19.22f, 12.78f),
            new Vector3f(8, 15.11f, -18.84f), new Vector3f(8, 10.9f, 1.2f), new Vector3f(0, -1, 0),
            new Vector3f(9.5f, 16.8f, 3.3f),
            new Vector3f(0.19f, -0.335f, -0.36f), 0.36f, 3.0f, 2, 0.03f, 2.6f,
            new Vector3f(6.2f, 17.7f, -11.4f), 4.6f,
            new Vector3f(8, -1.2f, -3.1f), new Vector3f(8, 13.36f, 1.4f), 0,
            0.36f, new Vector3f(8, 12.5f, 8.5f)).optic(18.38f, 3.0f, 0.9f, false);

    /**
     * De AK-47 (hoofdstuk 103, stap 2, tools/ak47_model.py): je rechterhand op de houten greep, je linkerhand onder de
     * handbeschermer, de keep van het achterste vizier voor je oog (dat zit bij een AK ver naar voren, voor de kap), de
     * spanhendel <b>rechts</b> op de grendeldrager (hij beweegt bij elk schot mee, dus even ver als de grendel), en het
     * gebogen magazijn dat schuin naar voren uit de behuizing kantelt.
     */
    private static final FirearmRig AK_47 = new FirearmRig(0.34f,
            new Vector3f(8, 5.9f, 12.6f), new Vector3f(8, 8.9f, -11.6f), new Vector3f(8, 15.88f, -3.75f),
            new Vector3f(8, 11.88f, -34.06f), new Vector3f(8, 10.62f, 1.08f), new Vector3f(0, -0.94f, -0.34f),
            new Vector3f(9.28f, 13.79f, 0.96f),
            new Vector3f(0.20f, -0.32f, -0.40f), 0.45f, 5.0f, 2, 0.05f, 4.5f,
            new Vector3f(10.3f, 13.5f, 1.1f), 5.0f,
            new Vector3f(8, -1.83f, -6.0f), new Vector3f(8, 10.98f, 1.04f), -0.515f,
            0.36f, new Vector3f(8, 10.5f, 4.0f)).optic(15.34f, 4.5f, 0, false);

    /**
     * De AWP (hoofdstuk 103, stap 3, tools/awp_model.py): je rechterhand op de greep achter het duimgat, je linkerhand
     * onder de handbeschermer, de keep van het ijzeren vizier achteraan op de rail voor je oog (je wang ligt voor het
     * wangstuk, dus dicht bij het vizier), de grendelknop rechts (optillen, 7 pixels naar achteren, terug en omlaag), en
     * het rechte magazijn dat recht naar beneden uit de kolf komt.
     */
    private static final FirearmRig AWP = new FirearmRig(0.34f,
            new Vector3f(8, 5.9f, 14.0f), new Vector3f(8, 10.2f, -16.0f), new Vector3f(8, 16.58f, 7.7f),
            new Vector3f(8, 14.56f, -61.5f), new Vector3f(8, 13.03f, 0.2f), new Vector3f(0, -1, 0),
            new Vector3f(9.0f, 14.7f, 0.66f),
            new Vector3f(0.20f, -0.34f, -0.42f), 0.215f, 7.0f, 2, 0.085f, 7.0f,
            new Vector3f(11.5f, 12.66f, 7.22f), 7.0f,
            new Vector3f(8, 6.23f, 0.24f), new Vector3f(8, 13.45f, 0.2f), 0,
            0.34f, new Vector3f(8, 11.5f, -10.0f)).lift(58).optic(16.1f, -1.0f, 0, false);

    private FirearmRig lift(float degrees) {
        this.handleLift = degrees;
        return this;
    }

    /** De montage van een kijker: de hoogte (de bovenkant van de behuizing) en waar langs het wapen, een verhoging, en of hij op de slede staat. */
    private FirearmRig optic(float y, float z, float riser, boolean onSlide) {
        this.sightMount = new Vector3f(8, y, z);
        this.sightRiser = riser;
        this.sightOnSlide = onSlide;
        return this;
    }

    /**
     * De Remington 870 (hoofdstuk 103, stap 4, tools/r870_model.py): je rechterhand om de hals van de kolf, je linkerhand
     * om het voorhout: dat is ook de hendel, die je 7 pixels naar achteren trekt en weer naar voren duwt (de pomp). Je
     * richt over de rug van de behuizing op de kraal vooraan op de loop, met je oog vlak achter de behuizing (verder naar
     * achteren vulde de hals van de kolf het halve beeld) en een centimeter erboven (lager vulde de behuizing zelf het
     * halve beeld; de kraal staat zo net onder je richtpunt, zoals je over een hagelgeweer kijkt). "Het magazijn" is hier de laadopening onder de behuizing: daar
     * duw je de patronen een voor een de buis in.
     */
    private static final FirearmRig REMINGTON_870 = new FirearmRig(0.34f,
            new Vector3f(8, 7.95f, 12.5f), new Vector3f(8, 8.8f, -19.0f), new Vector3f(8, 15.2f, 6.8f),
            new Vector3f(8, 13.44f, -42.96f), new Vector3f(8, 9.9f, -0.5f), new Vector3f(0, -1, 0),
            new Vector3f(9.5f, 13.3f, -0.7f),
            new Vector3f(0.20f, -0.31f, -0.40f), 0.13f, 7.0f, 2, 0.09f, 7.5f,
            new Vector3f(8, 8.8f, -19.0f), 7.0f,
            new Vector3f(8, 6.0f, -0.5f), new Vector3f(8, 9.9f, -0.5f), 0,
            0.36f, new Vector3f(8, 10.5f, -3.0f)).optic(14.28f, 1.5f, 0, false);

    public static FirearmRig of(FirearmType type) {
        return switch (type) {
            case GLOCK_19 -> GLOCK_19;
            case MP5 -> MP5;
            case AK_47 -> AK_47;
            case AWP -> AWP;
            case REMINGTON_870 -> REMINGTON_870;
        };
    }

    /**
     * Waar de hendel nu zit (model-pixels): {@code lift} van 0 (omlaag) tot 1 (de grendelknop helemaal omhoog gedraaid
     * om de as van de loop), en {@code back} pixels naar achteren. Voor een wapen zonder grendel gewoon de hendel, naar
     * achteren geschoven.
     */
    public Vector3f handleAt(float lift, float back) {
        Vector3f at = new Vector3f(handle);
        if (handleLift != 0) {
            double a = Math.toRadians(handleLift * lift);
            float dx = handle.x - 8;
            float dy = handle.y - muzzle.y;
            at.x = 8 + (float) (dx * Math.cos(a) - dy * Math.sin(a));
            at.y = muzzle.y + (float) (dx * Math.sin(a) + dy * Math.cos(a));
        }
        at.z += back;
        return at;
    }

    /**
     * Zit de hendel rechts op het wapen (de AK-47)? Dan laadt je <b>rechterhand</b> door (ze laat de greep even los en
     * pakt de hendel, vlak boven de greep), terwijl je linkerhand het wapen vooraan vasthoudt; het wapen kantelt een
     * beetje, zodat je de hendel ziet.
     */
    public boolean handleRight() {
        return handle.x > 8.6f;
    }

    // ================================================================ Het magazijn los (in je hand, in de pouch)

    /**
     * Zet het magazijn van de Glock rechtop (in het model staat het schuin, zoals in de greep), met het midden van zijn
     * bodemplaat op (0, 0, 0) en de lipjes naar +y. Voor een magazijn in je hand en in de magazijnpouch. Het magazijn is
     * ongeveer 7,7 pixels hoog.
     */
    public static Matrix4f magazineUpright() {
        return magazineUpright(FirearmType.GLOCK_19);
    }

    /** Hetzelfde voor het magazijn van dit wapen (hoofdstuk 103). */
    public static Matrix4f magazineUpright(FirearmType type) {
        FirearmRig rig = of(type);
        return new Matrix4f().rotateX(rig.magazineAngle).translate(-rig.magazineFoot.x, -rig.magazineFoot.y, -rig.magazineFoot.z);
    }

    /** Zo hoog is het magazijn van dit wapen ongeveer (pixels van het model), van zijn bodemplaat tot zijn bovenste patroon. */
    public static float magazineHeight(FirearmType type) {
        FirearmRig rig = of(type);
        return rig.magazineTop.distance(rig.magazineFoot);
    }

    /**
     * Hoeveel kleiner het magazijn van dit wapen getekend wordt waar een Glock-magazijn precies past (je hand, de
     * magazijnpouch): het lange magazijn van een MP5 zou er anders ver bovenuit steken.
     */
    public static float magazineFit(FirearmType type) {
        return Math.min(1.0f, 9.5f / Math.max(1.0f, magazineHeight(type)));
    }

    /** Waar de bovenste patroon in het magazijn van de Glock ligt (model-pixels, zoals in de greep). */
    public static final Vector3f MAGAZINE_TOP = new Vector3f(8.13f, 11.23f, 9.2f);

    // ================================================================ De Gunsmith Table

    /** Het vak van dit onderdeel van de Glock (model-pixels). */
    public static AABB bounds(GunPart part) {
        return bounds(FirearmType.GLOCK_19, part);
    }

    private static final Map<String, AABB> BOUNDS = new HashMap<>();

    /**
     * Waar elk onderdeel in het model zit (voor de Gunsmith Table: waar je het neerlegt). Voor de Glock de dozen die
     * tools/generate_gun_models.py uitprint (hoofdstuk 74); voor de wapens van hoofdstuk 103 rekenen we ze uit het
     * model zelf (de hoeken van al zijn vlakjes), zodat ze altijd kloppen.
     */
    public static AABB bounds(FirearmType type, GunPart part) {
        if (type == FirearmType.GLOCK_19) {
            return switch (part) {
                case FRAME -> new AABB(6.66, 4.0, 0.68, 9.22, 11.32, 15.26);
                case SLIDE -> new AABB(6.98, 11.32, 0.7, 9.04, 13.33, 14.27);
                case BARREL -> new AABB(7.25, 10.95, 0.49, 8.96, 12.97, 8.7);
                case SPRINGS -> new AABB(7.76, 11.24, 0.69, 8.24, 11.72, 6.69);
                case STRIKER -> new AABB(7.84, 11.55, 8.75, 8.16, 12.3, 13.3);
                case TRIGGER -> new AABB(7.72, 8.14, 7.74, 8.28, 9.93, 8.24);
            };
        }
        return BOUNDS.computeIfAbsent(type.id + "/" + part.id, key -> {
            AABB box = measure(FirearmModels.part(type, part, GunMaterial.STEEL));
            for (String extra : FirearmModels.extras(type, part)) {
                box = box.minmax(measure(FirearmModels.extra(type, extra, GunMaterial.STEEL)));
            }
            return box;
        });
    }

    /** Het vak van de sling van dit wapen (alleen de lange wapens). */
    public static AABB slingBounds(FirearmType type) {
        return BOUNDS.computeIfAbsent(type.id + "/sling", key -> measure(FirearmModels.sling(type)));
    }

    /** De doos om het hele wapen (voor het midden van de tafel en hoe ver hij uitzoomt). */
    public static AABB whole(FirearmType type) {
        AABB box = null;
        for (GunPart part : GunPart.values()) {
            AABB one = bounds(type, part);
            box = box == null ? one : box.minmax(one);
        }
        return box == null ? new AABB(0, 0, 0, 16, 16, 16) : box;
    }

    /** De kleinste doos om alle vlakjes van een model (model-pixels). */
    static AABB measure(ModelResourceLocation location) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(location);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        boolean any = false;
        RandomSource random = RandomSource.create(0);
        Direction[] sides = new Direction[Direction.values().length + 1];
        System.arraycopy(Direction.values(), 0, sides, 0, Direction.values().length);
        for (Direction side : sides) {
            for (BakedQuad quad : model.getQuads(null, side, random)) {
                int[] data = quad.getVertices();
                int stride = data.length / 4;
                for (int v = 0; v < 4; v++) {
                    float x = Float.intBitsToFloat(data[v * stride]) * 16;
                    float y = Float.intBitsToFloat(data[v * stride + 1]) * 16;
                    float z = Float.intBitsToFloat(data[v * stride + 2]) * 16;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                    any = true;
                }
            }
        }
        return any ? new AABB(minX, minY, minZ, maxX, maxY, maxZ) : new AABB(7, 7, 7, 9, 9, 9);
    }

    /** Hoe ver een onderdeel van de Glock uit elkaar geschoven ligt op de Gunsmith Table (model-pixels). */
    public static Vector3f exploded(GunPart part) {
        return exploded(FirearmType.GLOCK_19, part);
    }

    private static final Map<GunPart, Vector3f> LONG_EXPLODED = new EnumMap<>(GunPart.class);

    static {
        // Een lang wapen (je ziet het van opzij): het frame blijft liggen; de grendel erboven, de veer erboven en naar
        // achteren, de slagpin hoog erboven, de loop recht eronder, de trekker eronder en naar voren (voor het magazijn).
        LONG_EXPLODED.put(GunPart.FRAME, new Vector3f(0, 0, 0));
        LONG_EXPLODED.put(GunPart.SLIDE, new Vector3f(0, 9.0f, 0));
        LONG_EXPLODED.put(GunPart.BARREL, new Vector3f(0, -8.0f, 0));
        LONG_EXPLODED.put(GunPart.SPRINGS, new Vector3f(0, 9.0f, 7.0f));
        LONG_EXPLODED.put(GunPart.STRIKER, new Vector3f(0, 16.0f, 0));
        LONG_EXPLODED.put(GunPart.TRIGGER, new Vector3f(0, -5.0f, -12.0f));
    }

    /**
     * Hoe ver een onderdeel uit elkaar geschoven ligt op de Gunsmith Table als het er niet in zit (model-pixels). Sinds
     * hoofdstuk 54 (jouw notitie: "je kunt niet goed aan elk onderdeel") liggen ze verder uit elkaar, elk in zijn eigen
     * hoek: de slede erboven, de loop en de veren ervoor, de slagpin erachter, de trekker eronder.
     */
    public static Vector3f exploded(FirearmType type, GunPart part) {
        if (type == FirearmType.AWP && part == GunPart.SPRINGS) {
            // De grendel van de AWP is lang (met zijn kap en zijn knop): de veer past er niet achter. Hij ligt ervoor,
            // boven de loop.
            return new Vector3f(0, 9.0f, -24.0f).mul(explodedScale(type));
        }
        if (type == FirearmType.REMINGTON_870 && part == GunPart.SPRINGS) {
            // De veer van de 870 zit in de buis, onder het voorhout: naast het voorhout is geen plek. Hij ligt erboven,
            // vooraan, naast de slagpin.
            return new Vector3f(0, 16.0f, -11.5f).mul(explodedScale(type));
        }
        if (type == FirearmType.REMINGTON_870 && part == GunPart.STRIKER) {
            // ...en de slagpin een eindje naar achteren, zodat hun namen niet tegen elkaar staan.
            return new Vector3f(0, 16.0f, 4.0f).mul(explodedScale(type));
        }
        if (type != FirearmType.GLOCK_19) {
            return new Vector3f(LONG_EXPLODED.get(part)).mul(explodedScale(type));
        }
        return switch (part) {
            case FRAME -> new Vector3f(0, 0, 0);
            case SLIDE -> new Vector3f(0, 6.0f, 0);
            case BARREL -> new Vector3f(0, 2.6f, -5.5f);
            case SPRINGS -> new Vector3f(0, -2.2f, -7.0f);
            case STRIKER -> new Vector3f(0, 4.0f, 4.5f);
            case TRIGGER -> new Vector3f(0, -8.0f, -3.0f);
        };
    }

    /** Waar de sling ligt als hij er nog niet aan hangt: helemaal onder het wapen. */
    public static Vector3f slingExploded(FirearmType type) {
        return new Vector3f(0, -13.0f, 0).mul(explodedScale(type));
    }

    /**
     * Waar de kijker ligt als hij er nog niet op zit (hoofdstuk 103, stap 5): boven het wapen, achter de slagpin (bij een
     * lang wapen), of een eindje boven het midden van de slede (een pistool: de plek is er pas als de slede erop zit).
     */
    public static Vector3f sightExploded(FirearmType type) {
        if (type == FirearmType.GLOCK_19) {
            return new Vector3f(0, 2.6f, -3.0f);
        }
        return new Vector3f(0, 13.0f, 10.0f).mul(explodedScale(type));
    }

    /** Zo lang is de MP5 (pixels): daarvoor zijn de afstanden van {@link #LONG_EXPLODED} gekozen. */
    private static final float EXPLODED_LENGTH = 55.0f;

    /**
     * Hoeveel verder de losse onderdelen van dit wapen uit elkaar liggen dan bij de MP5. Een langer wapen (de AK-47)
     * staat verder uitgezoomd op de tafel; zonder dit zouden de namen boven de vakken over elkaar heen staan.
     */
    public static float explodedScale(FirearmType type) {
        if (type == FirearmType.GLOCK_19) {
            return 1.0f;
        }
        return Math.max(1.0f, (float) whole(type).getZsize() / EXPLODED_LENGTH);
    }
}
