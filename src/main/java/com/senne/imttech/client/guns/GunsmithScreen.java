package com.senne.imttech.client.guns;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunPartItem;
import com.senne.imttech.guns.GunParts;
import com.senne.imttech.guns.GunsmithMenu;
import com.senne.imttech.guns.PartState;
import java.util.List;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Het scherm van de Gunsmith Table (hoofdstuk 52): de Glock 19 uit zijn zes onderdelen. Het slepen, de vakken en de
 * lijsten staan in {@link PartsTableScreen} (sinds hoofdstuk 55 gedeeld met de Laser Workbench); hier staat alleen wat
 * de Glock bijzonder maakt.
 *
 * Hoofdstuk 54 (jouw notitie: "je kunt niet goed aan elk onderdeel"): de loop, de veren en de slagpin zitten ONDER de
 * slede. Zit de slede erop, dan kun je er niet bij (niet slepen, geen [x]): eerst de slede eraf, zoals bij een echte
 * Glock. De losse onderdelen liggen verder uit elkaar, elk in zijn eigen vak, en hun naam valt nooit buiten de tafel.
 */
public class GunsmithScreen extends PartsTableScreen<GunPart> {
    /** Zo groot is het pistool: pixels op je scherm per pixel van het model (sinds hoofdstuk 54 iets kleiner: de losse onderdelen liggen verder uit elkaar). */
    private static final float ZOOM = 6.6f;

    public GunsmithScreen(GunsmithMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GunPart.values());
    }

    @Override
    protected GunPart base() {
        return GunPart.FRAME;
    }

    @Override
    protected @Nullable GunPart partOf(ItemStack stack) {
        return GunPartItem.partOf(stack);
    }

    @Override
    protected boolean isGun(ItemStack stack) {
        return FirearmItem.typeOf(stack) != null;
    }

    @Override
    protected @Nullable PartState state(ItemStack gun, GunPart part) {
        return FirearmItem.parts(gun).get(part);
    }

    @Override
    protected boolean hasMaterial(GunPart part) {
        return true;
    }

    @Override
    protected Component partName(GunPart part) {
        return part.text();
    }

    @Override
    protected boolean allowed(GunPart part, ItemStack gun) {
        return true;
    }

    /**
     * Zit dit onderdeel onder de slede (de loop, de veren en de slagpin) terwijl de slede erop zit? Dan kun je er niet bij:
     * niet om het eruit te halen, en niet om het erin te leggen. (Sleep je de slede net weg, dan wel.)
     */
    @Override
    protected boolean reachable(GunPart part, ItemStack gun) {
        GunParts parts = FirearmItem.parts(gun);
        boolean under = (part == GunPart.BARREL || part == GunPart.SPRINGS || part == GunPart.STRIKER)
                && parts.has(GunPart.SLIDE) && dragging() != GunPart.SLIDE;
        return !under;
    }

    @Override
    protected boolean removable(GunPart part, ItemStack gun) {
        return part != GunPart.FRAME || FirearmItem.parts(gun).parts().size() == 1;
    }

    @Override
    protected boolean fits(ItemStack stack, ItemStack gun) {
        return true;
    }

    @Override
    protected AABB bounds(ItemStack gun, GunPart part) {
        return FirearmRig.bounds(part);
    }

    @Override
    protected Vector3f exploded(ItemStack gun, GunPart part) {
        return FirearmRig.exploded(part);
    }

    @Override
    protected Vector3f centre(ItemStack gun) {
        return new Vector3f(8, 8, 7.6f);
    }

    @Override
    protected float zoom(ItemStack gun) {
        return ZOOM;
    }

    @Override
    protected void renderInstalled(PoseStack pose, MultiBufferSource buffers, ItemStack gun, @Nullable GunPart skip) {
        FirearmType type = FirearmItem.typeOf(gun);
        if (type == null) {
            return;
        }
        GunParts parts = FirearmItem.parts(gun);
        for (GunPart part : GunPart.values()) {
            PartState state = parts.get(part);
            if (state == null || part == skip) {
                continue;
            }
            pose.pushPose();
            FirearmModels.render(pose, buffers, FirearmModels.part(type, part, state.material()), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        if (FirearmItem.hasMagazine(gun)) {
            FirearmModels.render(pose, buffers, FirearmModels.magazine(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    @Override
    protected ItemStack partStack(ItemStack gun, GunPart part) {
        PartState state = FirearmItem.parts(gun).get(part);
        return state == null ? ItemStack.EMPTY : GunPartItem.of(part, state.material(), state.condition());
    }

    @Override
    protected Component gunTitle(ItemStack gun) {
        FirearmType type = FirearmItem.typeOf(gun);
        return type == null ? Component.translatable("screen.imttech.gunsmith.empty") : type.text();
    }

    @Override
    protected String rowName(Row row) {
        return row.gun() ? row.stack().getHoverName().getString() : GunPartItem.materialOf(row.stack()).text().getString();
    }

    @Override
    protected String rowSub(Row row) {
        if (row.gun()) {
            return FirearmItem.parts(row.stack()).complete() ? "complete" : "incomplete";
        }
        GunPart part = GunPartItem.partOf(row.stack());
        int condition = GunPartItem.conditionOf(row.stack());
        return (part == null ? "" : part.text().getString()) + (condition < PartState.NEW ? " " + condition + "%" : "");
    }

    @Override
    protected String readyText(ItemStack gun) {
        FirearmType type = FirearmItem.typeOf(gun);
        GunParts parts = FirearmItem.parts(gun);
        if (type == null) {
            return "Drag a frame onto the table to start.";
        }
        if (!parts.complete()) {
            return "Missing: " + parts.missing().size() + " part(s).";
        }
        if (!parts.broken().isEmpty()) {
            return "Broken: " + parts.broken().getFirst().text().getString() + ". Replace it.";
        }
        return FirearmItem.hasMagazine(gun) ? "Ready, with a magazine." : "Ready. Needs a magazine.";
    }

    @Override
    protected int readyColour(ItemStack gun) {
        FirearmType type = FirearmItem.typeOf(gun);
        GunParts parts = FirearmItem.parts(gun);
        if (type == null) {
            return com.senne.imttech.client.hud.HudStyle.WHITE;
        }
        if (!parts.complete()) {
            return com.senne.imttech.client.hud.HudStyle.ORANGE;
        }
        return parts.broken().isEmpty() ? com.senne.imttech.client.hud.HudStyle.GREEN : com.senne.imttech.client.hud.HudStyle.RED;
    }

    @Override
    protected Component takeText() {
        return Component.translatable("screen.imttech.gunsmith.take");
    }

    @Override
    protected String[] emptyTray() {
        return new String[]{"No parts or pistols", "in your inventory."};
    }

    @Override
    protected String hint() {
        return "turn: scroll on the table   drag parts onto the pistol, or off it";
    }

    @Override
    protected @Nullable Component coveredTooltip(GunPart part, ItemStack gun) {
        return Component.translatable("screen.imttech.gunsmith.under_slide");
    }

    @Override
    protected List<Component> partTooltip(ItemStack gun, GunPart part, PartState state) {
        return List.of(
                Component.translatable("item.imttech.gun_part", state.material().text(), part.text()),
                Component.translatable("tooltip.imttech.gun_part.condition", state.condition()),
                Component.translatable("screen.imttech.gunsmith.drag_off"));
    }
}
