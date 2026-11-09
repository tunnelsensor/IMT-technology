package com.senne.imttech.client.armory;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.senne.imttech.ImtTech;
import com.senne.imttech.armory.Pegboard;
import com.senne.imttech.armory.PegboardBlockEntity;
import com.senne.imttech.client.guns.FirearmModels;
import com.senne.imttech.client.weapons.LaserGunModels;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunParts;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.GunSlingItem;
import com.senne.imttech.weapons.LaserGunItem;
import com.senne.imttech.weapons.RemoteChargeBlock;
import com.senne.imttech.weapons.RemoteChargeItem;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Tekent wat aan een Pegboard hangt (hoofdstuk 67): elk wapen in zijn eigen rechthoek van gaatjes, plat tegen de wand
 * met zijn zijkant naar jou, aan één of twee stalen haakjes. De wapens zijn de echte: een Glock uit zijn onderdelen
 * (in hun materiaal, met het magazijn als het erin zit), een lasergeweer uit zijn onderdelen (met de Nether Star, de
 * sling en de cel). De rest (granaten, pouches...) is het gewone 3D-model van het item.
 *
 * Elk wapen wordt zo groot getekend als in zijn rechthoek past: we meten het model (eenmalig per stand) en schalen het.
 * De Remote EMP en Remote Frag krijgen de grootte van hun sticky (zie {@link #sizedLike}).
 *
 * Houd je iets vast dat erop mag en kijk je naar het paneel, dan zie je een kader waar het komt (rood: geen plek).
 * Met een lege hand krijgt het wapen waar je naar kijkt een wit kader.
 */
public class PegboardRenderer implements BlockEntityRenderer<PegboardBlockEntity> {
    /** Het haakje (tools/generate_armory_models.py: models/armory/peg.json). */
    public static final ModelResourceLocation PEG = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "armory/peg"));

    /** De gemeten doos van elk model, per stand (in blokken van het model, na het draaien). */
    private static final Map<BakedModel, AABB[]> BOUNDS = new IdentityHashMap<>();

    public PegboardRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void register(ModelEvent.RegisterAdditional event) {
        event.register(PEG);
    }

    @Override
    public AABB getRenderBoundingBox(PegboardBlockEntity board) {
        // Een wapen mag tot 2 panelen naar rechts en naar boven uitsteken.
        return new AABB(board.getBlockPos()).inflate(Pegboard.REACH + 1);
    }

    @Override
    public void render(PegboardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
                       int overlay) {
        Direction facing = board.facing();
        pose.pushPose();
        // Naar het vlak van het paneel: x naar rechts (zoals jij ervoor staat), y omhoog, z van de muur af, in pixels.
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        for (PegboardBlockEntity.Hung hung : board.hung()) {
            int[] size = Pegboard.size(hung.stack(), hung.turn());
            renderHung(pose, buffers, light, board.getLevel(), hung, hung.x() * 2f, hung.y() * 2f, size[0] * 2f, size[1] * 2f);
        }
        renderFrame(board, pose, buffers);
        pose.popPose();
    }

    // ================================================================ Eén wapen

    /** Tekent één wapen in de rechthoek (x0, y0, w, h) in pixels van dit paneel, met zijn haakjes. */
    static void renderHung(PoseStack pose, MultiBufferSource buffers, int light, Level level, PegboardBlockEntity.Hung hung,
                           float x0, float y0, float w, float h) {
        ItemStack stack = hung.stack();
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(stack, level, null, 0);
        Quaternionf turn = rotation(stack, model, hung.turn());
        AABB box = bounds(model, turn, hung.turn(), stack);
        // Hoe groot: zo groot als in de rechthoek past, of (de Remote EMP en Remote Frag) zo groot als hun sticky.
        ItemStack like = sizedLike(stack);
        AABB fit = box;
        if (like != stack) {
            BakedModel likeModel = mc.getItemRenderer().getModel(like, level, null, 0);
            fit = bounds(likeModel, rotation(like, likeModel, hung.turn()), hung.turn(), like);
        }
        double fx = fit.getXsize() * 16, fy = fit.getYsize() * 16;
        float scale = (float) Math.min(1.3, Math.min(w / Math.max(0.01, fx), h / Math.max(0.01, fy)) * 0.9);
        float depth = (float) (box.getZsize() * 16 * scale);

        // De haakjes: van het paneel tot net achter het wapen.
        float pegLength = Pegboard.BACK - Pegboard.THICKNESS + Math.min(depth * 0.5f, 1.2f);
        if (!hung.turn().vertical() && w >= 6) {
            peg(pose, buffers, light, x0 + w * 0.3f, y0 + h * 0.55f, pegLength);
            peg(pose, buffers, light, x0 + w * 0.7f, y0 + h * 0.55f, pegLength);
        } else {
            peg(pose, buffers, light, x0 + w * 0.5f, y0 + h * 0.62f, pegLength);
        }

        pose.pushPose();
        pose.translate(x0 + w / 2, y0 + h / 2, Pegboard.BACK + depth / 2);
        pose.scale(16 * scale, 16 * scale, 16 * scale);
        Vector3f centre = box.getCenter().toVector3f();
        pose.translate(-centre.x, -centre.y, -centre.z);
        pose.mulPose(turn);
        draw(pose, buffers, light, level, stack, model);
        pose.popPose();
    }

    /** Tekent het item in de ruimte van zijn model (1 = 16 pixels van het model, de hoek (0, 0, 0) hier). */
    private static void draw(PoseStack pose, MultiBufferSource buffers, int light, Level level, ItemStack stack, BakedModel model) {
        FirearmType firearm = FirearmItem.typeOf(stack);
        if (firearm != null) {
            // De Glock uit zijn onderdelen (zoals in de holster), met het magazijn als het erin zit.
            GunParts parts = FirearmItem.parts(stack);
            for (GunPart part : GunPart.values()) {
                PartState state = parts.get(part);
                if (state != null) {
                    FirearmModels.render(pose, buffers, FirearmModels.part(firearm, part, state.material()), light, OverlayTexture.NO_OVERLAY);
                }
            }
            if (FirearmItem.hasMagazine(stack)) {
                FirearmModels.render(pose, buffers, FirearmModels.magazine(firearm), light, OverlayTexture.NO_OVERLAY);
            }
            return;
        }
        if (LaserGunItem.typeOf(stack) != null) {
            LaserGunModels.render(pose, buffers, stack, light, true, null);
            return;
        }
        // Het gewone model: ItemRenderer schuift het zelf een half blok terug, dat zetten we hier recht.
        pose.translate(0.5, 0.5, 0.5);
        Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.NONE, false, pose, buffers, light,
                OverlayTexture.NO_OVERLAY, model);
    }

    /**
     * Hoe het model draait zodat het goed hangt. Een 3D-model (de loop wijst in het model naar -z) draaien we zoals in
     * een item frame: zijn linkerkant naar jou, de loop naar links. Een plat plaatje (de Ion Blaster) staat al naar jou.
     * Een magazijn staat in zijn model schuin (zoals in de greep): dat zetten we eerst rechtop. Een opgerolde sling ligt
     * in zijn model plat: die zetten we eerst op zijn kant.
     */
    private static Quaternionf rotation(ItemStack stack, BakedModel model, Pegboard.Turn turn) {
        boolean flat = !model.isGui3d() && FirearmItem.typeOf(stack) == null && LaserGunItem.typeOf(stack) == null;
        Quaternionf q = new Quaternionf();
        switch (turn) {
            case DOWN -> q.rotateZ((float) Math.toRadians(90));
            case UP -> q.rotateZ((float) Math.toRadians(-90));
            default -> {
            }
        }
        if (flat) {
            if (turn == Pegboard.Turn.RIGHT) {
                q.rotateY((float) Math.PI);
            }
            return q;
        }
        q.rotateY((float) Math.toRadians(turn == Pegboard.Turn.RIGHT ? -90 : 90));
        if (MagazineItem.typeOf(stack) != null) {
            q.rotateX((float) Math.toRadians(-22.5));
        } else if (stack.getItem() instanceof GunSlingItem) {
            q.rotateZ((float) Math.toRadians(90));
        }
        return q;
    }

    /**
     * Het item waarvan dit item de grootte krijgt (meestal zichzelf). Jouw wens (9 november): de Remote EMP en Remote
     * Frag even groot als de Sticky EMP en de Sticky Frag. Ze hebben dezelfde halve bol, maar hun antenne maakt ze hoger,
     * en zo groot als in hun 2 x 2 gaatjes past werden ze zo'n 11 % kleiner dan de sticky ernaast. Met de schaal van de
     * sticky is de bol even groot, en de antenne past dan net in dezelfde rechthoek (4,05 van de 4 pixels).
     */
    private static ItemStack sizedLike(ItemStack stack) {
        if (stack.getItem() instanceof RemoteChargeItem remote) {
            return remote.kind() == RemoteChargeBlock.Kind.FRAG ? STICKY_FRAG.get() : STICKY_EMP.get();
        }
        return stack;
    }

    /** Een Sticky EMP en een Sticky Frag om de grootte van te meten (één keer gemaakt, niet elk beeld een nieuwe). */
    private static final Supplier<ItemStack> STICKY_EMP = Suppliers.memoize(() -> new ItemStack(ModItems.STICKY_EMP_GRENADE.get()));
    private static final Supplier<ItemStack> STICKY_FRAG = Suppliers.memoize(() -> new ItemStack(ModItems.STICKY_FRAG_GRENADE.get()));

    /** De doos om het model na het draaien (eenmalig gemeten per model en stand). */
    private static AABB bounds(BakedModel model, Quaternionf turn, Pegboard.Turn which, ItemStack stack) {
        AABB[] cached = BOUNDS.computeIfAbsent(model, m -> new AABB[Pegboard.Turn.values().length * 2]);
        int key = which.ordinal() * 2 + (MagazineItem.typeOf(stack) != null || stack.getItem() instanceof GunSlingItem ? 1 : 0);
        if (cached[key] == null) {
            cached[key] = measure(model, turn);
        }
        return cached[key];
    }

    private static AABB measure(BakedModel model, Quaternionf turn) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        boolean any = false;
        RandomSource random = RandomSource.create(0);
        Vector3f v = new Vector3f();
        Direction[] sides = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction side : sides) {
            for (BakedQuad quad : model.getQuads(null, side, random)) {
                int[] data = quad.getVertices();
                int stride = data.length / 4;
                for (int i = 0; i < 4; i++) {
                    v.set(Float.intBitsToFloat(data[i * stride]), Float.intBitsToFloat(data[i * stride + 1]),
                            Float.intBitsToFloat(data[i * stride + 2]));
                    turn.transform(v);
                    minX = Math.min(minX, v.x);
                    minY = Math.min(minY, v.y);
                    minZ = Math.min(minZ, v.z);
                    maxX = Math.max(maxX, v.x);
                    maxY = Math.max(maxY, v.y);
                    maxZ = Math.max(maxZ, v.z);
                    any = true;
                }
            }
        }
        return any ? new AABB(minX, minY, minZ, maxX, maxY, maxZ) : new AABB(-0.1, -0.1, -0.1, 0.1, 0.1, 0.1);
    }

    /** Eén haakje: een stalen pinnetje uit het paneel, {@code length} pixels lang. */
    private static void peg(PoseStack pose, MultiBufferSource buffers, int light, float x, float y, float length) {
        pose.pushPose();
        pose.translate(x - 0.5f, y - 0.5f, Pegboard.THICKNESS);
        // Het model is 1 x 1 pixel en 16 lang (langs z): naar blokken en naar de juiste lengte.
        pose.scale(16, 16, length);
        pose.translate(-7.5f / 16, -7.5f / 16, 0);
        BakedModel peg = Minecraft.getInstance().getModelManager().getModel(PEG);
        Minecraft.getInstance().getItemRenderer().renderModelLists(peg, ItemStack.EMPTY, light, OverlayTexture.NO_OVERLAY, pose,
                buffers.getBuffer(Sheets.cutoutBlockSheet()));
        pose.popPose();
    }

    // ================================================================ Het kader: waar het komt, of wat je pakt

    private static void renderFrame(PegboardBlockEntity board, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        // Alleen voor een echt paneel in de wereld (niet in een animatie van How it works).
        if (mc.level == null || mc.level.getBlockEntity(board.getBlockPos()) != board) {
            return;
        }
        if (player == null || mc.options.hideGui || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(board.getBlockPos())) {
            return;
        }
        BlockPos pos = board.getBlockPos();
        Direction facing = board.facing();
        ItemStack held = player.getMainHandItem();
        int baseU = Pegboard.column(pos, facing) * Pegboard.CELLS;
        int baseV = pos.getY() * Pegboard.CELLS;
        float r, g, b;
        int u, v, w, h;
        if (Pegboard.hangable(held)) {
            double[] cell = Pegboard.aim(player, pos, facing, hit.getLocation(), Pegboard.BACK);
            int[] size = Pegboard.size(held, Pegboard.Turn.LEFT);
            int[] at = Pegboard.place(board.getLevel(), pos, facing, cell[0], cell[1], size[0], size[1], null);
            w = size[0];
            h = size[1];
            if (at != null) {
                u = at[0];
                v = at[1];
                r = 0.35f;
                g = 0.9f;
                b = 1.0f;
            } else {
                u = (int) Math.round(cell[0] - w / 2.0);
                v = (int) Math.round(cell[1] - h / 2.0);
                r = 1.0f;
                g = 0.25f;
                b = 0.2f;
            }
        } else if (held.isEmpty()) {
            double[] cell = Pegboard.aim(player, pos, facing, hit.getLocation(), Pegboard.BACK + 1.5);
            Pegboard.Spot spot = Pegboard.itemAt(board.getLevel(), pos, facing, cell[0], cell[1]);
            if (spot == null) {
                return;
            }
            u = spot.u();
            v = spot.v();
            w = spot.w();
            h = spot.h();
            r = g = b = 1.0f;
        } else {
            return;
        }
        float x0 = (u - baseU) * 2f, y0 = (v - baseV) * 2f;
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()),
                new AABB(x0, y0, Pegboard.THICKNESS, x0 + w * 2f, y0 + h * 2f, Pegboard.BACK + 3.5), r, g, b, 0.8f);
    }

    /** Voor de tests in het spel: de gemeten dozen vergeten (na het herladen van de modellen). */
    public static void forget() {
        BOUNDS.clear();
    }
}
