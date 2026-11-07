package com.senne.imttech.client.weapons;

import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserPart;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Waar elk onderdeel van een lasergeweer in het model zit (hoofdstuk 55), voor de Laser Workbench: zijn vak (om een
 * onderdeel op te laten vallen) en hoe ver het uit elkaar geschoven ligt als het er nog niet in zit ("exploded view").
 * Zoals FirearmRig.bounds en exploded voor de Glock, maar de vakken rekenen we hier uit het model zelf (de hoeken van
 * al zijn vlakjes), zodat ze altijd kloppen met tools/generate_weapon_models.py. Hoe het geweer in je hand hangt en
 * beweegt, staat in GunRig.
 */
public final class LaserRig {
    private LaserRig() {}

    /** Welke onderdelen een bewegend deel hebben (..._moving.json; moet kloppen met de generator). */
    public static boolean moves(GunType type, LaserPart part) {
        return switch (type) {
            case PISTOL -> part == LaserPart.RECEIVER || part == LaserPart.OPTICS;
            case RIFLE, SNIPER -> part == LaserPart.RECEIVER;
            case SHOTGUN -> part == LaserPart.EMITTER;
        };
    }

    private static final Map<ModelResourceLocation, AABB> BOUNDS = new java.util.HashMap<>();

    /** Het vak van dit onderdeel (model-pixels): de kleinste doos om alle vlakjes van zijn model(len). */
    public static AABB bounds(GunType type, LaserPart part) {
        ModelResourceLocation location = switch (part) {
            case CORE -> WeaponModels.location(type.id, "core");
            case SLING -> WeaponModels.location(type.id, "sling");
            default -> WeaponModels.part(type, part, GunMaterial.TITANIUM);
        };
        AABB box = BOUNDS.computeIfAbsent(location, LaserRig::measure);
        if (part.wears() && moves(type, part)) {
            box = box.minmax(BOUNDS.computeIfAbsent(WeaponModels.moving(type, part, GunMaterial.TITANIUM), LaserRig::measure));
        }
        return box;
    }

    private static AABB measure(ModelResourceLocation location) {
        BakedModel model = net.minecraft.client.Minecraft.getInstance().getModelManager().getModel(location);
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

    /** Alles bij elkaar: de doos om het hele geweer, met alle onderdelen erin (voor het midden van de tafel). */
    public static AABB whole(GunType type) {
        AABB box = null;
        for (LaserPart part : LaserPart.values()) {
            if (!part.fitsOn(type)) {
                continue;
            }
            AABB one = bounds(type, part);
            box = box == null ? one : box.minmax(one);
        }
        return box == null ? new AABB(0, 0, 0, 16, 16, 16) : box;
    }

    private static final Map<LaserPart, Vector3f> EXPLODED = new EnumMap<>(LaserPart.class);

    static {
        // Het frame is de basis (blijft liggen); de rest ligt eromheen: de receiver erboven, de emitter ervoor, de optics
        // hoog erboven, de capacitor en de trekker eronder, de kern erachter boven, de sling helemaal onderaan.
        EXPLODED.put(LaserPart.FRAME, new Vector3f(0, 0, 0));
        EXPLODED.put(LaserPart.RECEIVER, new Vector3f(0, 6.5f, 0));
        EXPLODED.put(LaserPart.EMITTER, new Vector3f(0, 2.0f, -8.0f));
        EXPLODED.put(LaserPart.OPTICS, new Vector3f(0, 11.5f, 0));
        EXPLODED.put(LaserPart.CAPACITOR, new Vector3f(0, -7.0f, -1.5f));
        EXPLODED.put(LaserPart.TRIGGER, new Vector3f(0, -6.0f, 1.0f));
        EXPLODED.put(LaserPart.CORE, new Vector3f(0, 9.0f, 5.0f));
        EXPLODED.put(LaserPart.SLING, new Vector3f(0, -10.5f, 0));
    }

    /** Hoe ver een onderdeel uit elkaar geschoven ligt op de Laser Workbench als het er niet in zit (model-pixels). */
    public static Vector3f exploded(GunType type, LaserPart part) {
        Vector3f offset = new Vector3f(EXPLODED.getOrDefault(part, new Vector3f()));
        if (type == GunType.PISTOL) {
            // Een pistool is klein: iets dichter bij elkaar.
            offset.mul(0.75f);
        }
        return offset;
    }
}
