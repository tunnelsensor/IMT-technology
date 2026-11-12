package com.senne.imttech.client.armory;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.senne.imttech.ImtTech;
import com.senne.imttech.armory.Pegboard;
import com.senne.imttech.armory.PegboardBlock;
import com.senne.imttech.armory.PegboardBlockEntity;
import com.senne.imttech.client.guns.FirearmModels;
import com.senne.imttech.client.weapons.LaserGunModels;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunParts;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.registry.ModDataComponents;
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
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Tekent wat aan een Pegboard hangt (hoofdstuk 67): elk wapen in zijn eigen rechthoek van gaatjes, plat tegen de wand
 * met zijn zijkant naar jou, aan één of twee stalen haakjes. De wapens zijn de echte: een Glock uit zijn onderdelen
 * (in hun materiaal, met het magazijn als het erin zit), een lasergeweer uit zijn onderdelen (met de Nether Star, de
 * sling en de cel). De rest (granaten, pouches...) is het gewone 3D-model van het item.
 *
 * Elk wapen wordt zo groot getekend als in zijn rechthoek past: we meten het model (eenmalig per stand) en schalen het.
 * De Remote EMP en Remote Frag krijgen de grootte en de plek van hun sticky (zie {@link #sizedLike}).
 *
 * Houd je iets vast dat erop mag en kijk je naar het paneel, dan zie je een kader waar het komt (rood: geen plek).
 * Met een lege hand krijgt het wapen waar je naar kijkt een wit kader. Alleen van dichtbij (hoofdstuk 71: maximaal 2
 * blokken ervan, zie Pegboard.inReach); verder weg zie je geen kader, want dan kun je er ook niets mee.
 *
 * Hoofdstuk 70: in een hoek hangen twee panelen in een blok; elk krijgt zijn eigen wapens. Hoofdstuk 71 (jouw wens: geen
 * slotje maar een grijze omtrek op het bord): van een vastgezet wapen is de omtrek op het paneel geverfd, zoals op een
 * echt gereedschapsbord. Elke soort granaat heeft zijn eigen kleur grijs (zie {@link #paintColour}), zodat je ziet wat
 * waar hoort. Hoofdstuk 95: die omtrek is een dun lijntje in een kleur dicht bij die van het bord, ook als je er schuin
 * naar kijkt (zie {@link #paint}). Pak je het wapen, dan blijft alleen dat lijntje (de schaduw), niet opgevuld.
 */
public class PegboardRenderer implements BlockEntityRenderer<PegboardBlockEntity> {
    /** Het haakje (tools/generate_armory_models.py: models/armory/peg.json). */
    public static final ModelResourceLocation PEG = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "armory/peg"));
    /**
     * Hoe breed het geverfde lijntje rond een vastgezet wapen op je scherm is (pixels van je scherm). Hoofdstuk 95 (jouw
     * wensen: "heel kleine smalle dunne lijn", daarna "nog net iets dunner"): het was een randje van een halve pixel van
     * het paneel. Nu rekenen we elke keer uit hoe breed het op het paneel moet zijn om op je scherm zo'n fijn lijntje te
     * zijn (zie {@link #outlineWidth}): van dichtbij smaller, van verder weg breder, maar altijd even dun om te zien.
     * Smaller dan één schermpixel valt het lijntje in stukjes uiteen (dat zag ik op de screenshots, bij een tiende pixel
     * van het paneel).
     */
    private static final float LINE_PX = 1.25f;
    /** Grenzen voor de breedte op het paneel (pixels van het paneel), voor als de berekening iets vreemds geeft. */
    private static final float OUTLINE_MIN = 0.02f, OUTLINE_MAX = 1.0f;
    /** Zo dik is de platte verf (en het masker): een blok van het model wordt zoveel pixels van het paneel. */
    private static final float FLAT = 0.002f;
    /** Hoe ver de verf voor het paneel ligt (pixels). */
    private static final float PAINT_Z = 0.12f;
    /**
     * Hoe ver het masker (zie {@link #paint}) voor de verf ligt (pixels): meer dan de platte verf zelf dik is, en zo
     * weinig dat het van opzij niet verschuift ten opzichte van het lijntje (anders eet het van dichtbij en schuin aan
     * één kant het lijntje op).
     */
    private static final float MASK_Z = 0.008f;
    /**
     * Het masker: tekent geen kleur, alleen diepte. Wat daarna achter het masker getekend wordt, valt weg. Het schuift in
     * de diepte een klein beetje naar je toe (POLYGON_OFFSET_LAYERING), zodat het ook van ver weg voor de verf blijft.
     */
    private static final RenderType MASK = RenderType.create("imttech_pegboard_mask", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_TEXT_BACKGROUND_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setWriteMaskState(RenderStateShard.DEPTH_WRITE)
                    .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                    .createCompositeState(false));
    /**
     * Hetzelfde masker voor een plat plaatje (de Ion Blaster): met zijn plaatje, zodat de doorzichtige pixels geen diepte
     * schrijven (zijn verf heeft daar ook geen kleur).
     */
    private static final RenderType MASK_FLAT = RenderType.create("imttech_pegboard_mask_flat", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.DEPTH_WRITE)
                    .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                    .createCompositeState(false));
    /**
     * De omtrek tekenen we 8 keer, telkens een randje opgeschoven: zo wordt de vorm overal even veel groter, ook bij een
     * dunne loop. Het masker haalt de vorm zelf er weer uit.
     */
    private static final float[][] SPREAD = {{1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {0.7f, 0.7f}, {-0.7f, 0.7f}, {0.7f, -0.7f}, {-0.7f, -0.7f}};
    /**
     * De kleuren van de verf (rood, groen, blauw): grijs, en de granaten elk hun eigen kleur grijs. Het paneel is
     * middengrijs met donkere gaatjes; de verf is lichter, zodat een zwarte Glock niet op zijn eigen omtrek lijkt.
     * Hoofdstuk 95: we verven er niet meer zo fel mee, maar mengen ze met het grijs van het bord (zie {@link #boardPaint}).
     */
    private static final int PAINT_GREY = 0xB4B6BC, PAINT_EMP = 0xD49C9C, PAINT_FRAG = 0xA6B88A, PAINT_SMOKE = 0x8C9098,
            PAINT_FLASH = 0xEEEADC;
    /** Het grijs van het bord tussen de gaatjes (textures/block/pegboard.png). */
    private static final int BOARD = 0x6B717B;
    /**
     * Hoeveel van de eigen kleur van de verf overblijft; de rest is het grijs van het bord. Hoofdstuk 95 (jouw wens: "een
     * andere kleur die iets meer blend met de boards"): het lijntje is zo net iets lichter dan het bord, met een tintje
     * van zijn soort, in plaats van bijna wit.
     */
    private static final float BLEND = 0.45f;
    /**
     * Een plat plaatje (de Ion Blaster) heeft doorzichtige pixels: dat tekenen we met zijn eigen plaatje, de kleur op
     * zwart en de "witte flits" die een wezen krijgt als het geraakt wordt (OverlayTexture, rij 10). De kolom zegt
     * hoeveel wit: kolom 15 is 75 %, kolom 0 niets. We nemen de kolom die even licht is als de verf (zie flashColumn).
     */
    private static final int FLASH_OVERLAY_V = 10;

    /** De gemeten doos van elk model, per stand (in blokken van het model, na het draaien). */
    private static final Map<BakedModel, AABB[]> BOUNDS = new IdentityHashMap<>();
    /** De doos van elk model zonder te draaien (zie {@link #raw}). */
    private static final Map<BakedModel, AABB> RAW = new IdentityHashMap<>();

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
        renderWall(board, board.facing(), pose, buffers, light);
        // Hoofdstuk 70: in een hoek ook het tweede paneel.
        Direction side = Pegboard.sideFacing(board.getBlockState());
        if (side != null) {
            renderWall(board, side, pose, buffers, light);
        }
    }

    /** Alles wat aan het paneel tegen muur {@code wall} hangt, en het kader. */
    private static void renderWall(PegboardBlockEntity board, Direction wall, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        // Naar het vlak van het paneel: x naar rechts (zoals jij ervoor staat), y omhoog, z van de muur af, in pixels.
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-wall.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        for (int i = 0; i < board.hung().size(); i++) {
            if (board.wall(i) != wall) {
                continue;
            }
            PegboardBlockEntity.Hung hung = board.hung().get(i);
            int[] size = Pegboard.size(hung.stack(), hung.turn());
            renderHung(pose, buffers, light, board.getLevel(), wall, hung, hung.x() * 2f, hung.y() * 2f, size[0] * 2f, size[1] * 2f);
        }
        renderFrame(board, wall, pose, buffers);
        pose.popPose();
    }

    // ================================================================ Eén wapen

    /**
     * Tekent één wapen in de rechthoek (x0, y0, w, h) in pixels van dit paneel, met zijn haakjes. {@code wall}: de kant
     * waar het paneel naar kijkt (voor de verf, zie {@link #boardPaint}).
     */
    static void renderHung(PoseStack pose, MultiBufferSource buffers, int light, Level level, Direction wall,
                           PegboardBlockEntity.Hung hung, float x0, float y0, float w, float h) {
        ItemStack stack = hung.stack();
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(stack, level, null, 0);
        // Waar en hoe groot: gemeten aan het item zelf, of (de Remote EMP en Remote Frag) aan hun sticky. Zo'n remote hangt
        // dan precies waar zijn sticky zou hangen: de bol even groot en zijn onderkant even hoog, alleen de antenne steekt
        // er bovenuit.
        ItemStack like = sizedLike(stack);
        BakedModel likeModel = like == stack ? model : mc.getItemRenderer().getModel(like, level, null, 0);
        Quaternionf turn = rotation(like, likeModel, hung.turn());
        AABB box = bounds(likeModel, turn, hung.turn(), like);
        double sx = box.getXsize() * 16, sy = box.getYsize() * 16;
        float scale = (float) Math.min(1.3, Math.min(w / Math.max(0.01, sx), h / Math.max(0.01, sy)) * 0.9);
        float depth = (float) (box.getZsize() * 16 * scale);

        // Hoofdstuk 71 en 95: van een vastgezet wapen is de omtrek op het paneel geverfd, een dun lijntje. Pakte iemand het,
        // dan blijft alleen dat lijntje (de schaduw), zonder haakjes.
        if (hung.locked()) {
            float shade = level == null ? 1.0f : level.getShade(wall, true);
            paint(pose, buffers, light, level, stack, model, like, likeModel, box, turn, scale, x0 + w / 2, y0 + h / 2,
                    boardPaint(stack, shade));
        }
        if (hung.shadow()) {
            return;
        }

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
        placeModel(pose, box, turn, stack, like, model, likeModel);
        draw(pose, buffers, light, level, stack, model);
        pose.popPose();
    }

    /** Van het midden van de rechthoek naar het model: het midden van zijn (gedraaide) doos hier, en gedraaid. */
    private static void placeModel(PoseStack pose, AABB box, Quaternionf turn, ItemStack stack, ItemStack like, BakedModel model,
                                   BakedModel likeModel) {
        Vector3f centre = box.getCenter().toVector3f();
        pose.translate(-centre.x, -centre.y, -centre.z);
        pose.mulPose(turn);
        if (like != stack) {
            // Gemeten aan een ander item (zie sizedLike): we schuiven het model naar de plek van dat andere. Bij de
            // remote ligt de bol in zijn eigen model lager dan die van de sticky (als blok staat hij op de vloer,
            // y = 0; de sticky op y = 5). De onderkant (het kleefkussen) is in beide modellen het laagste en het
            // breedste, dus de hoek linksonder vooraan klopt. Bij de flashbang is het dezelfde bol als de frag.
            AABB own = raw(model), theirs = raw(likeModel);
            pose.translate(theirs.minX - own.minX, theirs.minY - own.minY, theirs.minZ - own.minZ);
        }
    }

    /**
     * De geverfde omtrek van een vastgezet wapen (hoofdstuk 71): hetzelfde model, plat net voor het paneel, in kleur
     * {@code rgb}, en 8 keer een randje opgeschoven, zodat de vorm overal even veel groter is dan het wapen (zie
     * {@link #outlineWidth}).
     *
     * Eerst tekenen we het wapen zelf, plat en onzichtbaar, als masker ({@link #MASK}) net voor de verf: binnen de vorm
     * van het wapen valt de verf weg en blijft alleen het lijntje eromheen over. Hoofdstuk 95: zonder masker zag je van
     * opzij een brede vlek naast het wapen (het wapen hangt een stukje voor het paneel, dus van schuin zie je de verf
     * achter het wapen), en een schaduw was een opgevulde vorm. Nu is het altijd een lijntje, of het wapen er hangt of niet
     * (jouw wens: "als je het gun er afhaalt moet het een outline blijven en niet opgevuld worden").
     */
    private static void paint(PoseStack pose, MultiBufferSource buffers, int light, Level level, ItemStack stack, BakedModel model,
                              ItemStack like, BakedModel likeModel, AABB box, Quaternionf turn, float scale, float cx, float cy,
                              int rgb) {
        boolean flat = stack.is(ModItems.ION_BLASTER.get());
        float outline = outlineWidth(pose, cx, cy, Pegboard.THICKNESS + PAINT_Z);
        pose.pushPose();
        pose.translate(cx, cy, Pegboard.THICKNESS + PAINT_Z + MASK_Z);
        pose.scale(16 * scale, 16 * scale, FLAT);
        placeModel(pose, box, turn, stack, like, model, likeModel);
        draw(pose, masked(buffers, flat), light, level, stack, model);
        pose.popPose();
        // Het masker meteen tekenen: het moet in de diepte staan voor de verf getekend wordt.
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(flat ? MASK_FLAT : MASK);
        }
        MultiBufferSource paint = painted(buffers, rgb, flat);
        for (float[] spread : SPREAD) {
            pose.pushPose();
            pose.translate(cx + spread[0] * outline, cy + spread[1] * outline, Pegboard.THICKNESS + PAINT_Z);
            pose.scale(16 * scale, 16 * scale, FLAT);
            placeModel(pose, box, turn, stack, like, model, likeModel);
            draw(pose, paint, light, level, stack, model);
            pose.popPose();
        }
    }

    /**
     * Hoe breed het lijntje op het paneel moet zijn (pixels van het paneel) om op je scherm {@link #LINE_PX} breed te zijn.
     * We zetten het punt ({@code cx}, {@code cy}) en een punt één pixel ernaast en erboven om naar je scherm, met dezelfde
     * matrices waarmee straks getekend wordt (dus ook in How it works, waar het paneel in een venster staat), en kijken
     * hoe ver ze uit elkaar liggen.
     */
    private static float outlineWidth(PoseStack pose, float cx, float cy, float z) {
        Matrix4f toScreen = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix())
                .mul(pose.last().pose());
        Vector4f here = toScreen.transform(new Vector4f(cx, cy, z, 1));
        Vector4f right = toScreen.transform(new Vector4f(cx + 1, cy, z, 1));
        Vector4f up = toScreen.transform(new Vector4f(cx, cy + 1, z, 1));
        if (here.w <= 0 || right.w <= 0 || up.w <= 0) {
            return OUTLINE_MIN; // achter je: wordt toch niet getekend
        }
        Window window = Minecraft.getInstance().getWindow();
        float halfWidth = window.getWidth() / 2f, halfHeight = window.getHeight() / 2f;
        double alongX = Math.hypot((right.x / right.w - here.x / here.w) * halfWidth, (right.y / right.w - here.y / here.w) * halfHeight);
        double alongY = Math.hypot((up.x / up.w - here.x / here.w) * halfWidth, (up.y / up.w - here.y / here.w) * halfHeight);
        // Hoe groot één pixel van het paneel op je scherm is (van schuin is hij in één richting korter: het gemiddelde).
        double onScreen = Math.sqrt(alongX * alongY);
        if (!(onScreen > 0.001)) {
            return OUTLINE_MAX;
        }
        return (float) Math.max(OUTLINE_MIN, Math.min(OUTLINE_MAX, LINE_PX / onScreen));
    }

    /**
     * De kleur van de verf (hoofdstuk 71, jouw wens: "elke bom er net iets anders uitziet van outline, zodat je makkelijk
     * weet wat waar moet"). Grijs, maar elke soort granaat zijn eigen kleur grijs: EMP roodachtig, frag olijfgroen,
     * smokebom donkerder grijs (of een beetje in zijn rookkleur), flashbang bijna wit. De sticky's, de remotes en de
     * triggers krijgen de kleur van hun soort (en ze verschillen van vorm: een bol, een halve bol, een antenne).
     */
    static int paintColour(ItemStack stack) {
        if (stack.is(ModItems.EMP_GRENADE.get()) || stack.is(ModItems.STICKY_EMP_GRENADE.get()) || stack.is(ModItems.REMOTE_EMP.get())
                || stack.is(ModItems.EMP_TRIGGER.get())) {
            return PAINT_EMP;
        }
        if (stack.is(ModItems.FRAG_GRENADE.get()) || stack.is(ModItems.STICKY_FRAG_GRENADE.get())
                || stack.is(ModItems.REMOTE_FRAG.get()) || stack.is(ModItems.FRAG_TRIGGER.get())) {
            return PAINT_FRAG;
        }
        if (stack.is(ModItems.FLASHBANG.get())) {
            return PAINT_FLASH;
        }
        if (stack.is(ModItems.SMOKE_GRENADE.get())) {
            DyeColor dye = stack.get(ModDataComponents.SMOKE_COLOR.get());
            return dye == null ? PAINT_SMOKE : mix(PAINT_GREY, dye.getTextureDiffuseColor(), 0.45f);
        }
        return PAINT_GREY;
    }

    /** Twee kleuren gemengd: {@code amount} van {@code b}, de rest van {@code a}. */
    private static int mix(int a, int b, float amount) {
        int r = Math.round(((a >> 16) & 255) * (1 - amount) + ((b >> 16) & 255) * amount);
        int g = Math.round(((a >> 8) & 255) * (1 - amount) + ((b >> 8) & 255) * amount);
        int bl = Math.round((a & 255) * (1 - amount) + (b & 255) * amount);
        return r << 16 | g << 8 | bl;
    }

    /**
     * De kleur waarmee we echt verven (hoofdstuk 95, jouw wens: "een andere kleur die iets meer blend met de boards"): de
     * kleur van {@link #paintColour}, gemengd met het grijs van het bord ({@link #BLEND}), en even donker als het bord aan
     * die kant. Minecraft tekent een paneel dat naar het oosten of westen kijkt donkerder dan een dat naar het noorden of
     * zuiden kijkt ({@code shade}); de verf krijgt dat niet vanzelf, dus doen we het hier.
     */
    static int boardPaint(ItemStack stack, float shade) {
        int rgb = mix(BOARD, paintColour(stack), BLEND);
        int r = Math.round(((rgb >> 16) & 255) * shade);
        int g = Math.round(((rgb >> 8) & 255) * shade);
        int b = Math.round((rgb & 255) * shade);
        return r << 16 | g << 8 | b;
    }

    /** Voor een plat plaatje: de kolom van de "witte flits" (zie FLASH_OVERLAY_V) die even licht is als {@code rgb}. */
    private static int flashColumn(int rgb) {
        float grey = (((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255)) / (3 * 255f);
        return Math.max(0, Math.min(15, Math.round(grey / 0.75f * 15)));
    }

    /**
     * Alles wat hierin getekend wordt, krijgt de verf: een 3D-model zonder zijn plaatje, alleen in kleur {@code rgb} (met
     * het licht van de plek); een plat plaatje ({@code flat}) met zijn eigen plaatje, in een grijs dat even licht is.
     */
    private static MultiBufferSource painted(MultiBufferSource buffers, int rgb, boolean flat) {
        if (flat) {
            // Niet het rendertype van het item zelf: dat van een plat item tekent de flits niet, en dan werd de verf
            // zwart (zo was het sinds hoofdstuk 71, gezien in hoofdstuk 95). Dit rendertype tekent hem wel, zoals bij
            // een wezen dat geraakt wordt, en laat de doorzichtige pixels weg.
            int column = flashColumn(rgb);
            return type -> new Painted(buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)), 0, column);
        }
        return type -> new Painted(buffers.getBuffer(RenderType.textBackground()), rgb, -1);
    }

    /** Alles wat hierin getekend wordt, komt in het masker (zie {@link #paint}): alleen diepte, geen kleur. */
    private static MultiBufferSource masked(MultiBufferSource buffers, boolean flat) {
        if (flat) {
            return type -> buffers.getBuffer(MASK_FLAT);
        }
        return type -> new Painted(buffers.getBuffer(MASK), 0, -1);
    }

    /**
     * Geeft alles door, behalve de kleur (die wordt {@code rgb}) en, bij een plat plaatje, de "flits" (kolom
     * {@code flash} van de witte rij; -1 = geen flits). De doorzichtigheid blijft, en het licht ook (in het donker is de
     * verf ook donker). Een rendertype zonder plaatje slaat de rest over.
     */
    private record Painted(VertexConsumer inner, int rgb, int flash) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            inner.setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            if (flash >= 0) {
                inner.setUv1(flash, FLASH_OVERLAY_V);
            } else {
                inner.setUv1(u, v);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            inner.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
            return this;
        }
    }

    /** De doos om het model zoals het in zijn json staat, zonder te draaien (eenmalig gemeten). */
    private static AABB raw(BakedModel model) {
        return RAW.computeIfAbsent(model, m -> measure(m, new Quaternionf()));
    }

    /** Tekent het item in de ruimte van zijn model (1 = 16 pixels van het model, de hoek (0, 0, 0) hier). */
    private static void draw(PoseStack pose, MultiBufferSource buffers, int light, Level level, ItemStack stack, BakedModel model) {
        FirearmType firearm = FirearmItem.typeOf(stack);
        if (firearm != null) {
            // Het vuurwapen uit zijn onderdelen (zoals in de holster), met het magazijn als het erin zit en (hoofdstuk 103)
            // zijn losse bewegende stukken en zijn sling.
            FirearmModels.renderParts(pose, buffers, stack, light, true, null);
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
        // Hoofdstuk 72, stap 5: een rugzak met zijn MOLLE-pouches erop.
        if (com.senne.imttech.backpack.BackpackItem.isBackpack(stack)) {
            com.senne.imttech.client.backpack.BackpackLayer.renderPouches(pose, buffers, light, OverlayTexture.NO_OVERLAY, stack, level);
        }
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
        // Hoofdstuk 72, stap 5: een rugzak of MOLLE-pouch hangt met zijn voorkant (de MOLLE-banden) naar je toe; in zijn
        // model kijkt die naar -z, dus een halve draai.
        if (com.senne.imttech.backpack.BackpackItem.isBackpack(stack) || stack.getItem() instanceof com.senne.imttech.backpack.MolleItem) {
            q.rotateY((float) Math.PI);
            return q;
        }
        q.rotateY((float) Math.toRadians(turn == Pegboard.Turn.RIGHT ? -90 : 90));
        if (MagazineItem.typeOf(stack) != null) {
            // Hoofdstuk 101 (jouw melding: "de glock magazijn op de pegboards zijn mini"): het magazijn uit de foto
            // (hoofdstuk 74) leunt in zijn model de andere kant op dan het oude blokjesmodel. Met -22,5 graden leunde het
            // daardoor dubbel zo schuin (7,7 pixels breed), en zo klein als in zijn 1 x 3 gaatjes past werd het een
            // dwerg. Met +22,5 staat het rechtop (2,8 breed, 8,9 hoog) en hangt het even groot als een Energy Cell.
            // Hoofdstuk 103: elk magazijn zo ver als het in zijn eigen wapen schuin staat (dat van de MP5 staat al recht).
            q.rotateX(com.senne.imttech.client.guns.FirearmRig.of(MagazineItem.typeOf(stack)).magazineAngle);
        } else if (stack.getItem() instanceof GunSlingItem) {
            q.rotateZ((float) Math.toRadians(90));
        }
        return q;
    }

    /**
     * Het item waarvan dit item de grootte en de plek krijgt (meestal zichzelf). Jouw wens (9 november): de Remote EMP
     * en Remote Frag even groot als de Sticky EMP en de Sticky Frag, en hun onderkant even hoog ("de antenne moet hoger
     * uitsteken, niet de bodem lager"). Ze hebben dezelfde halve bol, maar hun antenne maakt ze hoger: zo groot als in hun
     * 2 x 2 gaatjes past werden ze zo'n 11 % kleiner, en in het midden van hun rechthoek hing de bol lager. Nu hangt de
     * bol precies waar die van de sticky hangt, en steekt de antenne zo'n pixel boven de rechthoek uit.
     */
    private static ItemStack sizedLike(ItemStack stack) {
        if (stack.getItem() instanceof RemoteChargeItem remote) {
            return remote.kind() == RemoteChargeBlock.Kind.FRAG ? STICKY_FRAG.get() : STICKY_EMP.get();
        }
        // Hoofdstuk 70 (jouw wens: "flashbangs even groot als de rest, ze zijn nu net iets kleiner"): de flashbang is
        // dezelfde bol als de andere granaten, maar zijn gouden pin steekt erboven uit. Zo groot als in zijn 2 x 2 gaatjes
        // past werd hij daardoor zo'n 15 % kleiner. Nu krijgt hij de grootte van de Frag Grenade; de pin steekt een
        // klein beetje boven zijn rechthoek uit, net als de antenne van een remote.
        if (stack.is(ModItems.FLASHBANG.get())) {
            return FRAG.get();
        }
        // Hoofdstuk 101: een leeg magazijn heeft zijn eigen model, zonder de patronen die bovenuit steken. Zo groot als in
        // zijn gaatjes past werd het daardoor een paar procent groter dan een vol. Nu krijgt het de grootte en de plek van
        // een vol magazijn (de onderkant ligt in beide modellen even laag), zodat ze naast elkaar even groot hangen.
        FirearmType magazine = MagazineItem.typeOf(stack);
        if (magazine != null && MagazineItem.rounds(stack) == 0) {
            return FULL_MAGAZINES.computeIfAbsent(magazine, type -> MagazineItem.of(type, type.capacity()));
        }
        return stack;
    }

    /** Een vol magazijn per pistool om de grootte van te meten (zie {@link #sizedLike}). */
    private static final Map<FirearmType, ItemStack> FULL_MAGAZINES = new java.util.EnumMap<>(FirearmType.class);

    /** Een Sticky EMP en een Sticky Frag om de grootte van te meten (één keer gemaakt, niet elk beeld een nieuwe). */
    private static final Supplier<ItemStack> STICKY_EMP = Suppliers.memoize(() -> new ItemStack(ModItems.STICKY_EMP_GRENADE.get()));
    private static final Supplier<ItemStack> STICKY_FRAG = Suppliers.memoize(() -> new ItemStack(ModItems.STICKY_FRAG_GRENADE.get()));
    private static final Supplier<ItemStack> FRAG = Suppliers.memoize(() -> new ItemStack(ModItems.FRAG_GRENADE.get()));

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

    private static void renderFrame(PegboardBlockEntity board, Direction wall, PoseStack pose, MultiBufferSource buffers) {
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
        // Alleen het paneel waar je naar kijkt (in een hoek hangen er twee), en alleen van dichtbij (hoofdstuk 70).
        if (PegboardBlock.wallOf(board.getBlockState(), hit) != wall || !Pegboard.inReach(player, hit.getLocation())) {
            return;
        }
        Direction facing = wall;
        ItemStack held = player.getMainHandItem();
        int baseU = Pegboard.column(pos, facing) * Pegboard.CELLS;
        int baseV = pos.getY() * Pegboard.CELLS;
        float r, g, b;
        int u, v, w, h;
        if (Pegboard.hangable(held)) {
            double[] cell = Pegboard.aim(player, pos, facing, hit.getLocation(), Pegboard.BACK);
            // Hoofdstuk 70: op de schaduw van hetzelfde soort komt het precies daar.
            Pegboard.Spot shadow = Pegboard.shadowFor(board.getLevel(), pos, facing, cell[0], cell[1], held);
            int[] size = shadow != null ? new int[]{shadow.w(), shadow.h()} : Pegboard.size(held, Pegboard.Turn.LEFT);
            int[] at = shadow != null ? new int[]{shadow.u(), shadow.v()}
                    : Pegboard.place(board.getLevel(), pos, facing, cell[0], cell[1], size[0], size[1], null);
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
            // Een schaduw (hoofdstuk 70) krijgt een grijs kader: pakken kan niet, sneak + linksklik veegt hem weg.
            r = g = b = spot.shadow() ? 0.55f : 1.0f;
        } else {
            return;
        }
        float x0 = (u - baseU) * 2f, y0 = (v - baseV) * 2f;
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()),
                new AABB(x0, y0, Pegboard.THICKNESS, x0 + w * 2f, y0 + h * 2f, Pegboard.FRONT), r, g, b, 0.8f);
    }

    /** Voor de tests in het spel: de gemeten dozen vergeten (na het herladen van de modellen). */
    public static void forget() {
        BOUNDS.clear();
        RAW.clear();
    }
}
