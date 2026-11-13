package com.senne.imttech.client.guns;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunPart;
import com.senne.imttech.guns.GunPartItem;
import com.senne.imttech.guns.GunParts;
import com.senne.imttech.guns.GunSlot;
import com.senne.imttech.guns.GunsmithMenu;
import com.senne.imttech.guns.GunsmithTableBlockEntity;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.registry.ModItems;
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
 * Het scherm van de Gunsmith Table (hoofdstuk 52): een vuurwapen uit zijn zes onderdelen. Het slepen, de vakken en de
 * lijsten staan in {@link PartsTableScreen} (sinds hoofdstuk 55 gedeeld met de Laser Workbench); hier staat alleen wat
 * de vuurwapens bijzonder maakt.
 *
 * Hoofdstuk 54 (jouw notitie: "je kunt niet goed aan elk onderdeel"): de loop, de veren en de slagpin zitten ONDER de
 * slede. Zit de slede erop, dan kun je er niet bij (niet slepen, geen [x]): eerst de slede eraf, zoals bij een echte
 * Glock. De losse onderdelen liggen verder uit elkaar, elk in zijn eigen vak, en hun naam valt nooit buiten de tafel.
 *
 * Hoofdstuk 103: elk wapen van {@link FirearmType} (de Glock, de MP5...). Het frame dat je op de lege tafel legt, zegt
 * welk wapen het wordt; een onderdeel van een ander wapen past niet. De plekken zijn die van {@link GunSlot}: de zes
 * onderdelen, en bij een lang wapen de <b>sling</b> (dan past het in een sling-vakje). De tafel zoomt uit voor een lang
 * wapen, en de namen van de onderdelen zijn die van het wapen dat er ligt.
 */
public class GunsmithScreen extends PartsTableScreen<GunSlot> {
    /** Zo groot is de Glock: pixels op je scherm per pixel van het model (sinds hoofdstuk 54 iets kleiner: de losse onderdelen liggen verder uit elkaar). */
    private static final float ZOOM = 6.6f;

    public GunsmithScreen(GunsmithMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GunSlot.values());
    }

    /** Het wapen dat op de tafel ligt, of de Glock als er niets ligt (voor de namen en de maten van een lege tafel). */
    private static FirearmType typeOf(ItemStack gun) {
        FirearmType type = FirearmItem.typeOf(gun);
        return type == null ? FirearmType.GLOCK_19 : type;
    }

    @Override
    protected GunSlot base() {
        return GunSlot.FRAME;
    }

    @Override
    protected @Nullable GunSlot partOf(ItemStack stack) {
        return GunsmithTableBlockEntity.slotOf(stack);
    }

    @Override
    protected boolean isGun(ItemStack stack) {
        return FirearmItem.typeOf(stack) != null;
    }

    @Override
    protected @Nullable PartState state(ItemStack gun, GunSlot slot) {
        if (slot.attachment != null) {
            // Een attachment slijt niet: het zit erop of niet.
            return FirearmItem.attachment(gun, slot.attachment) != null ? PartState.fresh(GunMaterial.STEEL) : null;
        }
        if (slot.part == null) {
            // De sling slijt niet en heeft geen materiaal: hij hangt eraan of niet.
            return FirearmItem.hasSling(gun) ? PartState.fresh(GunMaterial.STEEL) : null;
        }
        return FirearmItem.parts(gun).get(slot.part);
    }

    @Override
    protected boolean hasMaterial(GunSlot slot) {
        return slot.part != null;
    }

    /** Bij een attachment: wat erop zit ("Red Dot"), in plaats van "in". */
    @Override
    protected String installedText(ItemStack gun, GunSlot slot) {
        if (slot.attachment != null) {
            com.senne.imttech.guns.Attachment on = FirearmItem.attachment(gun, slot.attachment);
            return on == null ? "in" : on.shortText().getString();
        }
        return "in";
    }

    @Override
    protected Component partName(GunSlot slot) {
        return slot.text(typeOf(menu.gun()));
    }

    /**
     * Een pistool heeft geen plek voor een sling. De plek van een attachment is er pas als het onderdeel waar het op
     * staat in het wapen zit (de kijker van een pistool staat op de slede).
     */
    @Override
    protected boolean allowed(GunSlot slot, ItemStack gun) {
        FirearmType type = typeOf(gun);
        if (!slot.fitsOn(type)) {
            return false;
        }
        // Stap 6: de plekken van de attachments staan er pas als het wapen compleet is. Zo liggen ze nooit door de
        // gestippelde vakken van ontbrekende onderdelen heen (er zijn nu vier plekken), en het onderdeel waar een
        // attachment op staat zit er dan zeker in.
        return slot.attachment == null || FirearmItem.typeOf(gun) == null || FirearmItem.parts(gun).complete();
    }

    /** In de lijst rechts staan alleen de plekken van dit wapen: bij een pistool (of een lege tafel) geen sling. */
    @Override
    protected boolean listed(GunSlot slot, ItemStack gun) {
        return slot.fitsOn(typeOf(gun));
    }

    /** Een lang wapen zie je van opzij (een pistool schuin van achteren, zoals altijd). */
    @Override
    protected float startYaw(ItemStack gun) {
        return typeOf(gun).longGun ? 72 : 28;
    }

    /** Hoofdstuk 78: hier maak je alle onderdelen (jouw keuze), op het tabblad "Make parts" (zie GunsmithMakePanel). */
    @Override
    protected boolean makesParts() {
        return true;
    }

    /**
     * Zit dit onderdeel onder de slede (de loop, de veren en de slagpin) terwijl de slede erop zit? Dan kun je er niet bij:
     * niet om het eruit te halen, en niet om het erin te leggen. (Sleep je de slede net weg, dan wel.)
     */
    @Override
    protected boolean reachable(GunSlot slot, ItemStack gun) {
        if (slot.part == null) {
            return true;
        }
        GunParts parts = FirearmItem.parts(gun);
        boolean under = slot.part.underSlide(typeOf(gun)) && parts.has(GunPart.SLIDE) && dragging() != GunSlot.SLIDE;
        // Hoofdstuk 103, stap 5: staat er een attachment op dit onderdeel, dan moet dat er eerst af.
        return !under && GunsmithTableBlockEntity.sittingOn(gun, slot.part) == null;
    }

    @Override
    protected boolean removable(GunSlot slot, ItemStack gun) {
        return slot != GunSlot.FRAME || FirearmItem.parts(gun).parts().size() == 1;
    }

    /** Een los onderdeel hoort bij één wapen; de sling past op elk lang wapen; een attachment zegt zelf waar het op past. */
    @Override
    protected boolean fits(ItemStack stack, ItemStack gun) {
        FirearmType type = FirearmItem.typeOf(gun);
        if (type == null) {
            return false;
        }
        com.senne.imttech.guns.Attachment attachment = com.senne.imttech.guns.AttachmentItem.of(stack);
        if (attachment != null) {
            return attachment.fits(type);
        }
        FirearmType partType = GunPartItem.typeOf(stack);
        return partType == null ? type.longGun : partType == type;
    }

    @Override
    protected AABB bounds(ItemStack gun, GunSlot slot) {
        FirearmType type = typeOf(gun);
        if (slot.attachment != null) {
            // De plek van een attachment: om wat erop zit, of (leeg) om wat er meestal op komt.
            return AddOns.bounds(type, slot.attachment, FirearmItem.attachment(gun, slot.attachment));
        }
        if (slot.part == null) {
            return type.longGun ? FirearmRig.slingBounds(type) : new AABB(7, 7, 7, 9, 9, 9);
        }
        return FirearmRig.bounds(type, slot.part);
    }

    @Override
    protected Vector3f exploded(ItemStack gun, GunSlot slot) {
        if (slot.attachment != null) {
            return FirearmRig.attachmentExploded(typeOf(gun), slot.attachment);
        }
        return slot.part == null ? FirearmRig.slingExploded(typeOf(gun)) : FirearmRig.exploded(typeOf(gun), slot.part);
    }

    @Override
    protected Vector3f centre(ItemStack gun) {
        FirearmType type = FirearmItem.typeOf(gun);
        if (type == null || type == FirearmType.GLOCK_19) {
            return new Vector3f(8, 8, 7.6f);
        }
        // Stap 6: met de plaats voor een demper erbij (zie FirearmRig.wholeWithMuzzle).
        AABB box = FirearmRig.wholeWithMuzzle(type);
        return new Vector3f((float) box.getCenter().x, (float) FirearmRig.whole(type).getCenter().y, (float) box.getCenter().z);
    }

    /** De Glock zoals altijd; een lang wapen zo ver uitgezoomd dat het met zijn losse onderdelen op de tafel past. */
    @Override
    protected float zoom(ItemStack gun) {
        FirearmType type = FirearmItem.typeOf(gun);
        if (type == null || type == FirearmType.GLOCK_19) {
            return ZOOM;
        }
        AABB box = FirearmRig.wholeWithMuzzle(type);
        // 27: zo ver steken de losse onderdelen boven en onder het wapen uit (bij een langer wapen iets meer).
        return (float) Math.min(ZOOM, Math.min((VIEW_W - 16) / box.getZsize(),
                (VIEW_H - 44) / (box.getYsize() + 27 * FirearmRig.explodedScale(type))));
    }

    @Override
    protected void renderInstalled(PoseStack pose, MultiBufferSource buffers, ItemStack gun, @Nullable GunSlot skip) {
        FirearmType type = FirearmItem.typeOf(gun);
        if (type == null) {
            return;
        }
        GunParts parts = FirearmItem.parts(gun);
        for (GunPart part : GunPart.values()) {
            PartState state = parts.get(part);
            if (state == null || (skip != null && skip.part == part)) {
                continue;
            }
            FirearmModels.render(pose, buffers, FirearmModels.part(type, part, state.material()), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY);
            for (String extra : FirearmModels.extras(type, part)) {
                FirearmModels.render(pose, buffers, FirearmModels.extra(type, extra, state.material()), LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY);
            }
        }
        if (!type.tube() && FirearmItem.hasMagazine(gun)) {
            FirearmModels.render(pose, buffers, FirearmModels.magazine(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        if (type.longGun && FirearmItem.hasSling(gun) && skip != GunSlot.SLING) {
            FirearmModels.render(pose, buffers, FirearmModels.sling(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        FirearmModels.renderAttachments(pose, buffers, gun, LightTexture.FULL_BRIGHT, skip == null ? null : skip.attachment, 0);
    }

    @Override
    protected ItemStack partStack(ItemStack gun, GunSlot slot) {
        if (slot.attachment != null) {
            com.senne.imttech.guns.Attachment on = FirearmItem.attachment(gun, slot.attachment);
            return on == null ? ItemStack.EMPTY : new ItemStack(ModItems.attachment(on));
        }
        if (slot.part == null) {
            return FirearmItem.hasSling(gun) ? new ItemStack(ModItems.GUN_SLING.get()) : ItemStack.EMPTY;
        }
        PartState state = FirearmItem.parts(gun).get(slot.part);
        return state == null ? ItemStack.EMPTY : GunPartItem.of(typeOf(gun), slot.part, state.material(), state.condition());
    }

    @Override
    protected Component gunTitle(ItemStack gun) {
        FirearmType type = FirearmItem.typeOf(gun);
        return type == null ? Component.translatable("screen.imttech.gunsmith.empty") : type.text();
    }

    /**
     * De bovenste regel van een rij links. Bij de Glock het materiaal (zoals altijd); bij de andere wapens het onderdeel
     * zelf ("Bolt Carrier"), want het wapen en het materiaal staan eronder en samen passen ze niet op één regel.
     */
    @Override
    protected String rowName(Row row) {
        GunPart part = GunPartItem.partOf(row.stack());
        FirearmType type = GunPartItem.typeOf(row.stack());
        com.senne.imttech.guns.Attachment can = com.senne.imttech.guns.AttachmentItem.of(row.stack());
        if (can != null && can.suppresses()) {
            // "Shotgun Suppressor" past niet op een regel van de lijst: de soort staat eronder (zie rowSub).
            return "Suppressor";
        }
        if (row.gun() || part == null || type == null) {
            return row.stack().getHoverName().getString();
        }
        return type == FirearmType.GLOCK_19 ? GunPartItem.materialOf(row.stack()).text().getString() : part.text(type).getString();
    }

    @Override
    protected String rowSub(Row row) {
        if (row.gun()) {
            return FirearmItem.parts(row.stack()).complete() ? "complete" : "incomplete";
        }
        GunPart part = GunPartItem.partOf(row.stack());
        FirearmType type = GunPartItem.typeOf(row.stack());
        com.senne.imttech.guns.Attachment attachment = com.senne.imttech.guns.AttachmentItem.of(row.stack());
        if (attachment != null) {
            // Een attachment: op welke plek het komt, of dat het niet op het wapen op de tafel past.
            FirearmType on = FirearmItem.typeOf(menu.gun());
            String where = on != null && !attachment.fits(on) ? "does not fit"
                    : attachment.slot.text().getString().toLowerCase(java.util.Locale.ROOT);
            // Een demper: voor welk kaliber hij is.
            String kind = switch (attachment) {
                case SUPPRESSOR_9MM -> "9mm, ";
                case SUPPRESSOR_RIFLE -> "rifle, ";
                case SUPPRESSOR_SHOTGUN -> "shotgun, ";
                default -> "";
            };
            return kind + where;
        }
        if (part == null || type == null) {
            return "for a long gun"; // de Gun Sling
        }
        int condition = GunPartItem.conditionOf(row.stack());
        String worn = condition < PartState.NEW ? " " + condition + "%" : "";
        if (type == FirearmType.GLOCK_19) {
            return part.text().getString() + worn;
        }
        // Het wapen en het materiaal ("MP5, steel"); is het versleten, dan zijn conditie in plaats van het materiaal.
        return type.text().getString() + (worn.isEmpty() ? ", " + GunPartItem.materialOf(row.stack()).text().getString().toLowerCase(java.util.Locale.ROOT)
                : "," + worn);
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
            return "Broken: " + parts.broken().getFirst().text(type).getString() + ". Replace it.";
        }
        String sling = !type.longGun ? "" : FirearmItem.hasSling(gun) ? " Has a sling." : " A sling fits too.";
        if (type.tube()) {
            return "Ready." + sling;
        }
        return (FirearmItem.hasMagazine(gun) ? "Ready, with a magazine." : "Ready. Needs a magazine.") + sling;
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
        return new String[]{"No parts or guns", "in your inventory."};
    }

    @Override
    protected String hint() {
        return "turn: scroll on the table   drag parts onto the gun, or off it";
    }

    @Override
    protected @Nullable Component coveredTooltip(GunSlot slot, ItemStack gun) {
        com.senne.imttech.guns.Attachment on = slot.part == null ? null : GunsmithTableBlockEntity.sittingOn(gun, slot.part);
        if (on != null) {
            return Component.translatable("gunsmith.imttech.attachment_first", on.text());
        }
        return Component.translatable("screen.imttech.gunsmith.under_slide");
    }

    @Override
    protected List<Component> partTooltip(ItemStack gun, GunSlot slot, PartState state) {
        if (slot.attachment != null) {
            com.senne.imttech.guns.Attachment on = FirearmItem.attachment(gun, slot.attachment);
            return List.of(on == null ? slot.attachment.text() : on.text(), Component.translatable("screen.imttech.gunsmith.drag_off"));
        }
        if (slot.part == null) {
            return List.of(Component.translatable("item.imttech.gun_sling"), Component.translatable("screen.imttech.gunsmith.drag_off"));
        }
        FirearmType type = typeOf(gun);
        return List.of(
                type == FirearmType.GLOCK_19 ? Component.translatable("item.imttech.gun_part", state.material().text(), slot.part.text())
                        : Component.translatable("item.imttech.firearm_part", state.material().text(), type.text(), slot.part.text(type)),
                Component.translatable("tooltip.imttech.gun_part.condition", state.condition()),
                Component.translatable("screen.imttech.gunsmith.drag_off"));
    }
}
