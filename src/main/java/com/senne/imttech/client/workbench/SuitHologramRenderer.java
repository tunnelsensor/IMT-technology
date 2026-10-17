package com.senne.imttech.client.workbench;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.senne.imttech.ImtTech;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.client.suit.TechSuitModel;
import com.senne.imttech.suit.RocketPackItem;
import com.senne.imttech.workbench.HoloPiece;
import com.senne.imttech.workbench.SuitWorkbenchBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Tekent het harnas-hologram boven de werkbank.
 *
 * Het is het echte 3D-model van het tech-harnas (TechSuitModel), op ware grootte en langzaam
 * ronddraaiend, zoals het harnas in Bibie's lab:
 * - een stuk dat er nog niet is: doorschijnend blauw, met het plaatje van het harnas erin;
 * - een echt stuk dat je erop legde: gewoon zoals het is, in de kleuren van het harnas;
 * - een stuk in aanbouw: plaatje voor plaatje verschijnt het (eerst de romp, dan de
 *   details), met een scanring die op en neer gaat;
 * - een upgrade die erin gaat: het upgrade-item draait om het stuk en krimpt erin weg, met een
 *   ring die volloopt;
 * - het stuk waar je naar kijkt, gloeit op.
 * Een klaar (of gepakt) stuk vliegt in een boog naar de speler en wordt onderweg even groot als
 * zijn eigen harnas, zodat het precies om hem heen valt.
 */
public class SuitHologramRenderer implements BlockEntityRenderer<SuitWorkbenchBlockEntity> {
    private static final ResourceLocation OUTER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/models/armor/tech_layer_1.png");
    private static final ResourceLocation INNER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/models/armor/tech_layer_2.png");
    /**
     * Dezelfde plaatjes, maar in grijstinten met lijntjes (tools/generate_textures.py,
     * suit_hologram): die kleuren we hier blauw. Met het echte plaatje werd het een doorzichtig
     * rood harnas in plaats van een hologram.
     */
    private static final ResourceLocation HOLO_OUTER =
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/entity/suit_hologram_1.png");
    private static final ResourceLocation HOLO_INNER =
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "textures/entity/suit_hologram_2.png");
    /** De kleur van het hologram (iets lichter dan de HUD, anders wordt het plaatje te donker). */
    private static final int HOLO = 0x7FE6FF;
    /** Zo snel draait het hologram (graden per tick: één rondje in ruim 16 seconden). */
    private static final float SPIN_SPEED = 1.1f;

    public SuitHologramRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SuitWorkbenchBlockEntity bench, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = bench.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = bench.getBlockPos();
        float time = level.getGameTime() + partialTick;
        float spin = (time * SPIN_SPEED) % 360.0f;
        // Het blok zelf is dicht (daar is het donker): het licht van erboven.
        int worldLight = LevelRenderer.getLightColor(level, pos.above());
        WorkbenchHologram.Target hover = WorkbenchHologram.hovered();
        HoloPiece hovered = hover != null && hover.pos().equals(pos) ? hover.piece() : null;
        // Zonder stroom flikkert het hologram en is het zwakker.
        float power = bench.powered() ? 1.0f : 0.45f + 0.2f * (float) Math.abs(Math.sin(time * 0.9) * Math.sin(time * 0.23));
        int job = bench.job();
        float fraction = bench.total() == 0 ? 0 : Mth.clamp((bench.progress() + (bench.powered() ? partialTick : 0))
                / bench.total(), 0, 1);

        // Let op: de lijntjes-buffer telkens opnieuw ophalen. Tekenen we tussendoor een model (een
        // andere soort buffer), dan is de oude lijntjes-buffer al afgesloten.
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        base(pose, buffers.getBuffer(RenderType.lines()), time, power);
        pose.popPose();

        for (HoloPiece piece : HoloPiece.values()) {
            ItemStack onIt = bench.laid(piece);
            boolean glow = piece == hovered;
            boolean building = job == SuitWorkbenchBlockEntity.JOB_BUILD && bench.jobPiece() == piece;
            boolean installing = job == SuitWorkbenchBlockEntity.JOB_INSTALL && bench.jobPiece() == piece;
            pose.pushPose();
            holoPose(pose, spin, HoloPiece.SCALE);
            if (!onIt.isEmpty()) {
                real(pose, buffers, piece, onIt, worldLight);
                if (glow || installing) {
                    pose.pushPose();
                    pose.scale(1.03f, 1.03f, 1.03f);
                    holo(pose, buffers, piece, onIt, alpha(glow ? 0.35f : 0.2f), HudStyle.WHITE, 1.0f);
                    pose.popPose();
                }
            } else if (building) {
                // Eerst het hele stuk heel zwak, dan wat al af is fel eroverheen.
                holo(pose, buffers, piece, ItemStack.EMPTY, alpha(0.14f * power), HOLO, 1.0f);
                holo(pose, buffers, piece, ItemStack.EMPTY, alpha((glow ? 0.95f : 0.8f) * power),
                        lighten(HOLO, 0.4f), fraction);
            } else {
                holo(pose, buffers, piece, ItemStack.EMPTY, alpha((glow ? 0.6f : 0.3f) * power),
                        glow ? lighten(HOLO, 0.5f) : HOLO, 1.0f);
            }
            pose.popPose();
            if (building) {
                scanRing(pose, buffers.getBuffer(RenderType.lines()), piece, time, power);
            }
            if (installing) {
                installEffect(pose, buffers, bench, piece, time, fraction, worldLight);
            }
            if (glow) {
                corners(pose, buffers.getBuffer(RenderType.lines()), piece.box(), HudStyle.CYAN, 200);
            }
        }

        if (!bench.flying().isEmpty()) {
            flight(bench, pose, buffers, level, spin, partialTick);
        }
    }

    // ================================================================ Het harnas zelf

    /** Naar de plek en de stand van het hologram: de voeten op FEET, draaiend om het midden. */
    private static void holoPose(PoseStack pose, float spin, float scale) {
        // Het model heeft zijn nulpunt in de nek, 24 pixels (1,5 blok) boven de voeten, en y
        // wijst naar beneden: daarom omhoog schuiven en dan omklappen (net als bij een mob).
        pose.translate(0.5, HoloPiece.FEET + 1.5f * scale, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.scale(-scale, -scale, scale);
    }

    /** Een echt stuk: het gewone plaatje, met het licht van de wereld. */
    private static void real(PoseStack pose, MultiBufferSource buffers, HoloPiece piece, ItemStack stack, int light) {
        HumanoidModel<LivingEntity> model = prepare(piece, stack);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(texture(piece))), light,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }

    /**
     * Een hologram-stuk: het plaatje doorschijnend, lichtgevend en in de kleur {@code rgb}.
     * Met {@code built} onder 1 tekenen we alleen de eerste plaatjes (het stuk in aanbouw).
     */
    private static void holo(PoseStack pose, MultiBufferSource buffers, HoloPiece piece, ItemStack stack, int alpha,
                             int rgb, float built) {
        if (alpha <= 3) {
            return;
        }
        HumanoidModel<LivingEntity> model = prepare(piece, stack);
        List<ModelPart> parts = parts(model, piece);
        int shown = Mth.ceil(built * parts.size());
        for (int i = shown; i < parts.size(); i++) {
            parts.get(i).skipDraw = true; // de kinderen (details) worden nog wel getekend
        }
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(
                        piece == HoloPiece.LEGS ? HOLO_INNER : HOLO_OUTER)),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, (alpha << 24) | rgb);
        for (ModelPart part : parts) {
            part.skipDraw = false;
        }
    }

    /**
     * Zet het model klaar voor dit stuk: rechtop, zonder hoofd, alleen de delen van dit stuk.
     * (Het model wordt gedeeld met het harnas dat spelers dragen; dat zet alles weer terug.)
     */
    private static HumanoidModel<LivingEntity> prepare(HoloPiece piece, ItemStack stack) {
        HumanoidModel<LivingEntity> model = TechSuitModel.forSlot(piece.slot());
        for (ModelPart part : List.of(model.head, model.hat, model.body, model.rightArm, model.leftArm,
                model.rightLeg, model.leftLeg)) {
            part.resetPose();
            part.visible = false;
        }
        model.young = false;
        model.crouching = false;
        model.riding = false;
        for (ModelPart part : limbs(model, piece)) {
            part.visible = true;
        }
        if (piece == HoloPiece.CHEST) {
            // De armen iets van het lichaam af, zoals een harnas op een standaard.
            model.rightArm.zRot = 0.12f;
            model.leftArm.zRot = -0.12f;
            boolean pods = RocketPackItem.installedOn(stack);
            model.body.getChild("rocket_pod_right").visible = pods;
            model.body.getChild("rocket_pod_left").visible = pods;
        }
        return model;
    }

    /** Welke lichaamsdelen bij dit stuk horen (zoals Minecraft dat ook doet voor een harnas). */
    private static List<ModelPart> limbs(HumanoidModel<LivingEntity> model, HoloPiece piece) {
        return switch (piece) {
            case CHEST -> List.of(model.body, model.rightArm, model.leftArm);
            case LEGS -> List.of(model.body, model.rightLeg, model.leftLeg);
            case BOOTS -> List.of(model.rightLeg, model.leftLeg);
        };
    }

    /** Alle plaatjes van een stuk, in de volgorde waarin ze gebouwd worden: eerst de romp. */
    private static List<ModelPart> parts(HumanoidModel<LivingEntity> model, HoloPiece piece) {
        List<ModelPart> limbs = limbs(model, piece);
        List<ModelPart> parts = new ArrayList<>(limbs);
        for (ModelPart limb : limbs) {
            limb.getAllParts().skip(1).forEach(parts::add);
        }
        return parts;
    }

    private static ResourceLocation texture(HoloPiece piece) {
        return piece == HoloPiece.LEGS ? INNER_TEXTURE : OUTER_TEXTURE;
    }

    // ================================================================ Het vliegende stuk

    /**
     * Het stuk vliegt in een boog van het hologram naar de speler. Onderweg draait het mee met
     * zijn lichaam en groeit het naar ware grootte, zodat het aan het eind precies om hem heen zit.
     */
    private static void flight(SuitWorkbenchBlockEntity bench, PoseStack pose, MultiBufferSource buffers, Level level,
                               float spin, float partialTick) {
        Player player = bench.flyTo() == null ? null : level.getPlayerByUUID(bench.flyTo());
        if (player == null) {
            return;
        }
        float t = Mth.clamp((level.getGameTime() - bench.flyStart() + partialTick) / SuitWorkbenchBlockEntity.FLY_TICKS,
                0, 1);
        float e = t * t * (3 - 2 * t);
        BlockPos pos = bench.getBlockPos();
        Vec3 start = new Vec3(0.5, HoloPiece.FEET + 1.5f * HoloPiece.SCALE, 0.5);
        Vec3 end = player.getPosition(partialTick).subtract(pos.getX(), pos.getY(), pos.getZ()).add(0, 1.501, 0);
        Vec3 at = start.lerp(end, e).add(0, Mth.sin(t * Mth.PI) * 0.8, 0);
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float angle = spin + Mth.wrapDegrees(180.0f - bodyYaw - spin) * e;
        float scale = Mth.lerp(e, HoloPiece.SCALE, 1.0f);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(at.add(pos.getX(), pos.getY(), pos.getZ())));
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.scale(-scale, -scale, scale);
        real(pose, buffers, bench.flyFrom(), bench.flying(), light);
        // Een blauwe gloed eromheen die verdwijnt als het aankomt.
        pose.scale(1.03f, 1.03f, 1.03f);
        holo(pose, buffers, bench.flyFrom(), bench.flying(), alpha(0.4f * (1 - e)), HOLO, 1.0f);
        pose.popPose();
    }

    // ================================================================ Lijntjes

    /** De ring op de werkbank waar het hologram uit komt, en zwakke lichtstraaltjes omhoog. */
    private static void base(PoseStack pose, VertexConsumer lines, float time, float power) {
        int alpha = (int) (130 * power);
        ring(pose, lines, 0.46f, 1.01f, HudStyle.CYAN, alpha, 48, 0);
        ring(pose, lines, 0.3f, 1.01f, HudStyle.CYAN, alpha / 2, 24, time * 2.0f);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + time * 0.01;
            float x = (float) Math.cos(a) * 0.46f;
            float z = (float) Math.sin(a) * 0.46f;
            line(pose, lines, x, 1.01f, z, x * 0.7f, 1.35f, z * 0.7f, HudStyle.CYAN, alpha / 3);
        }
    }

    /** Een ring die op en neer over het stuk in aanbouw gaat, zoals een 3D-printer. */
    private static void scanRing(PoseStack pose, VertexConsumer lines, HoloPiece piece, float time, float power) {
        AABB box = piece.box();
        float y = (float) (box.minY + (box.maxY - box.minY) * (0.5 + 0.5 * Math.sin(time * 0.15)));
        float radius = (float) (box.maxX - box.minX) * 0.5f;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        ring(pose, lines, radius, y, HudStyle.WHITE, (int) (200 * power), 32, 0);
        pose.popPose();
    }

    /**
     * Een upgrade gaat erin: het item draait om het stuk en krimpt erin weg, en een ring rond
     * het stuk loopt vol.
     */
    private static void installEffect(PoseStack pose, MultiBufferSource buffers, SuitWorkbenchBlockEntity bench, HoloPiece piece, float time, float fraction,
                                      int light) {
        AABB box = piece.box();
        float middle = (float) (box.minY + box.maxY) * 0.5f;
        float radius = (float) (box.maxX - box.minX) * 0.5f + 0.08f;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        ring(pose, lines, radius, middle, HudStyle.CYAN, 70, 48, 0);
        arc(pose, lines, radius, middle, fraction, HudStyle.WHITE, 230);
        ItemStack module = bench.module();
        if (!module.isEmpty() && bench.getLevel() != null) {
            float orbit = radius * (1.0f - 0.6f * fraction);
            double a = time * 0.2;
            pose.translate(Math.cos(a) * orbit, middle, Math.sin(a) * orbit);
            pose.mulPose(Axis.YP.rotationDegrees(time * 6));
            float size = 0.45f * (1.0f - 0.7f * fraction);
            pose.scale(size, size, size);
            Minecraft.getInstance().getItemRenderer().renderStatic(module, ItemDisplayContext.FIXED, light,
                    OverlayTexture.NO_OVERLAY, pose, buffers, bench.getLevel(), 0);
        }
        pose.popPose();
    }

    /** Hoekjes om het vak van het stuk waar je naar kijkt (zoals de HUD doelwitten markeert). */
    private static void corners(PoseStack pose, VertexConsumer lines, AABB box, int rgb, int alpha) {
        float len = 0.12f;
        float[] xs = {(float) box.minX, (float) box.maxX};
        float[] ys = {(float) box.minY, (float) box.maxY};
        float[] zs = {(float) box.minZ, (float) box.maxZ};
        for (float x : xs) {
            for (float y : ys) {
                for (float z : zs) {
                    float dx = x == xs[0] ? len : -len;
                    float dy = y == ys[0] ? len : -len;
                    float dz = z == zs[0] ? len : -len;
                    line(pose, lines, x, y, z, x + dx, y, z, rgb, alpha);
                    line(pose, lines, x, y, z, x, y + dy, z, rgb, alpha);
                    line(pose, lines, x, y, z, x, y, z + dz, rgb, alpha);
                }
            }
        }
    }

    private static void ring(PoseStack pose, VertexConsumer lines, float radius, float y, int rgb, int alpha, int steps,
                             float turnDegrees) {
        double turn = Math.toRadians(turnDegrees);
        for (int i = 0; i < steps; i++) {
            // Bij een draaiende ring laten we elk tweede stukje weg: dan zie je hem draaien.
            if (turnDegrees != 0 && i % 2 == 1) {
                continue;
            }
            double a0 = turn + i * (Math.PI * 2 / steps);
            double a1 = turn + (i + 1) * (Math.PI * 2 / steps);
            line(pose, lines, (float) Math.cos(a0) * radius, y, (float) Math.sin(a0) * radius,
                    (float) Math.cos(a1) * radius, y, (float) Math.sin(a1) * radius, rgb, alpha);
        }
    }

    /** Een deel van een ring: {@code fraction} 0.5 = een halve cirkel. */
    private static void arc(PoseStack pose, VertexConsumer lines, float radius, float y, float fraction, int rgb,
                            int alpha) {
        int steps = Math.max(1, (int) (48 * fraction));
        double end = Math.PI * 2 * fraction;
        for (int i = 0; i < steps; i++) {
            double a0 = end * i / steps - Math.PI / 2;
            double a1 = end * (i + 1) / steps - Math.PI / 2;
            line(pose, lines, (float) Math.cos(a0) * radius, y, (float) Math.sin(a0) * radius,
                    (float) Math.cos(a1) * radius, y, (float) Math.sin(a1) * radius, rgb, alpha);
        }
    }

    /** Eén lijnstuk (RenderType.lines wil een normaal langs de lijn). */
    private static void line(PoseStack pose, VertexConsumer lines, float x0, float y0, float z0, float x1, float y1,
                             float z1, int rgb, int alpha) {
        float nx = x1 - x0;
        float ny = y1 - y0;
        float nz = z1 - z0;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-5f || alpha <= 0) {
            return;
        }
        PoseStack.Pose last = pose.last();
        Matrix4f matrix = last.pose();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        lines.addVertex(matrix, x0, y0, z0).setColor(r, g, b, alpha).setNormal(last, nx / length, ny / length, nz / length);
        lines.addVertex(matrix, x1, y1, z1).setColor(r, g, b, alpha).setNormal(last, nx / length, ny / length, nz / length);
    }

    private static int alpha(float fraction) {
        return Mth.clamp((int) (fraction * 255), 0, 255);
    }

    /** Een kleur lichter maken ({@code amount} 0 = zoals hij is, 1 = wit). */
    private static int lighten(int rgb, float amount) {
        int r = (int) Mth.lerp(amount, (rgb >> 16) & 0xFF, 255);
        int g = (int) Mth.lerp(amount, (rgb >> 8) & 0xFF, 255);
        int b = (int) Mth.lerp(amount, rgb & 0xFF, 255);
        return (r << 16) | (g << 8) | b;
    }

    // ================================================================ Wanneer tekenen

    /** Normaal het hologram boven de werkbank; vliegt er een stuk, dan ook de weg ernaartoe. */
    @Override
    public AABB getRenderBoundingBox(SuitWorkbenchBlockEntity bench) {
        AABB box = new AABB(bench.getBlockPos()).inflate(0.5, 0, 0.5).expandTowards(0, 2.5, 0);
        return bench.flying().isEmpty() ? box : box.inflate(26);
    }

    @Override
    public boolean shouldRenderOffScreen(SuitWorkbenchBlockEntity bench) {
        return !bench.flying().isEmpty();
    }
}
