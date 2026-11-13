package com.senne.imttech.client.weapons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.client.guns.PartsTableScreen;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.Attachment;
import com.senne.imttech.guns.AttachmentItem;
import com.senne.imttech.guns.AttachmentSlot;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.GunsmithMenu;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import com.senne.imttech.weapons.LaserPart;
import com.senne.imttech.weapons.LaserPartItem;
import com.senne.imttech.weapons.LaserParts;
import com.senne.imttech.weapons.LaserSlot;
import com.senne.imttech.weapons.LaserWorkbenchBlockEntity;
import java.util.List;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Het scherm van de Laser Workbench (hoofdstuk 55): een lasergeweer uit zijn zes onderdelen, de Nether Star (de kern)
 * en de sling. Het slepen, de vakken en de lijsten staan in {@link PartsTableScreen} (gedeeld met de Gunsmith Table);
 * hier staat alleen wat de lasergeweren bijzonder maakt: welk geweer het is bepaalt het frame, een onderdeel van een
 * ander geweer past niet, een pistool heeft geen vak voor de sling, en de tafel zoomt uit voor een lange sniper.
 *
 * Hoofdstuk 103, stap 7: twee plekken erbij voor attachments ({@link LaserSlot}): de kijker (een Red Dot Sight, of op de
 * Laser Sniper de Sniper Scope; hij komt in de plaats van het eigen vizier van het geweer) en onder de loop (een greep).
 * Ze staan er zodra het geweer compleet is.
 */
public class LaserWorkbenchScreen extends PartsTableScreen<LaserSlot> {
    public LaserWorkbenchScreen(GunsmithMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, LaserSlot.values());
    }

    /** Het geweer op de tafel, of de rifle als er nog geen ligt (voor de maat van het frame-vak). */
    private static GunType typeOr(ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        return type == null ? GunType.RIFLE : type;
    }

    @Override
    protected LaserSlot base() {
        return LaserSlot.FRAME;
    }

    @Override
    protected @Nullable LaserSlot partOf(ItemStack stack) {
        Attachment attachment = AttachmentItem.of(stack);
        if (attachment != null) {
            return LaserSlot.of(attachment.slot);
        }
        LaserPart part = LaserWorkbenchBlockEntity.partOf(stack);
        return part == null ? null : LaserSlot.of(part);
    }

    @Override
    protected boolean isGun(ItemStack stack) {
        return LaserGunItem.typeOf(stack) != null;
    }

    @Override
    protected @Nullable PartState state(ItemStack gun, LaserSlot slot) {
        if (slot.attachment != null) {
            // Een attachment slijt niet: het zit erop of niet.
            return FirearmItem.attachment(gun, slot.attachment) != null ? PartState.fresh(GunMaterial.TITANIUM) : null;
        }
        return LaserGunItem.parts(gun).get(slot.part);
    }

    @Override
    protected boolean hasMaterial(LaserSlot slot) {
        return slot.part != null && slot.part.wears();
    }

    /** Bij een attachment: wat erop zit ("Red Dot"), in plaats van "in". */
    @Override
    protected String installedText(ItemStack gun, LaserSlot slot) {
        if (slot.attachment != null) {
            Attachment on = FirearmItem.attachment(gun, slot.attachment);
            return on == null ? "in" : on.shortText().getString();
        }
        return "in";
    }

    @Override
    protected Component partName(LaserSlot slot) {
        return slot.text();
    }

    /**
     * Een pistool heeft geen plek voor een sling. De plekken van de attachments staan er pas als het geweer compleet is
     * (zo liggen ze nooit door de gestippelde vakken van ontbrekende onderdelen heen).
     */
    @Override
    protected boolean allowed(LaserSlot slot, ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        if (type == null) {
            return slot.attachment == null;
        }
        return slot.fitsOn(type) && (slot.attachment == null || LaserGunItem.parts(gun).complete());
    }

    /** In de lijst rechts staan alleen de plekken van dit geweer (een pistool heeft geen sling en geen plek onder de loop). */
    @Override
    protected boolean listed(LaserSlot slot, ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        return type == null ? slot.attachment == null : slot.attachment == null || slot.fitsOn(type);
    }

    @Override
    protected boolean reachable(LaserSlot slot, ItemStack gun) {
        return true;
    }

    @Override
    protected boolean removable(LaserSlot slot, ItemStack gun) {
        return slot != LaserSlot.FRAME || LaserGunItem.parts(gun).parts().size() == 1;
    }

    /**
     * Een los onderdeel hoort bij één geweer; de Nether Star en de sling passen overal (de sling niet op een pistool);
     * een attachment zegt zelf op welk lasergeweer het past.
     */
    @Override
    protected boolean fits(ItemStack stack, ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        Attachment attachment = AttachmentItem.of(stack);
        if (attachment != null) {
            return type != null && attachment.fits(type);
        }
        GunType partGun = LaserPartItem.gunOf(stack);
        LaserPart part = LaserWorkbenchBlockEntity.partOf(stack);
        return type != null && part != null && (partGun == null || partGun == type) && part.fitsOn(type);
    }

    @Override
    protected AABB bounds(ItemStack gun, LaserSlot slot) {
        GunType type = typeOr(gun);
        GunRig rig = GunRig.of(type);
        if (slot.attachment == AttachmentSlot.SIGHT) {
            // De plek van de kijker: een vak van de grootte van een red dot, op zijn montage.
            Vector3f mount = rig.sightMount;
            return new AABB(mount.x - 1.2, mount.y, mount.z - 1.9, mount.x + 1.2, mount.y + 2.5, mount.z + 1.9);
        }
        if (slot.attachment != null) {
            // Onder de loop: een vak van de grootte van een rechte greep.
            Vector3f mount = rig.underMount == null ? rig.support : rig.underMount;
            return new AABB(mount.x - 0.98, mount.y - 6.1, mount.z - 1.35, mount.x + 0.98, mount.y, mount.z + 1.35);
        }
        return LaserRig.bounds(type, slot.part);
    }

    @Override
    protected Vector3f exploded(ItemStack gun, LaserSlot slot) {
        if (slot.attachment == AttachmentSlot.SIGHT) {
            return new Vector3f(0, 6.5f, 0);
        }
        if (slot.attachment != null) {
            return new Vector3f(0, -3.0f, 0);
        }
        return LaserRig.exploded(typeOr(gun), slot.part);
    }

    @Override
    protected Vector3f centre(ItemStack gun) {
        AABB whole = LaserRig.whole(typeOr(gun));
        return new Vector3f((float) (whole.minX + whole.maxX) / 2, (float) (whole.minY + whole.maxY) / 2 + 1,
                (float) (whole.minZ + whole.maxZ) / 2);
    }

    /**
     * Zo groot als er past: een pistool groot, een sniper (veertig pixels lang) kleiner. Onder het geweer is er plaats
     * voor de sling (die het verst uitsteekt: 24 pixels onder en boven samen).
     */
    @Override
    protected float zoom(ItemStack gun) {
        AABB whole = LaserRig.whole(typeOr(gun));
        float length = (float) whole.getZsize() + 6;
        float height = (float) whole.getYsize() + 24;
        return Math.min(6.6f, Math.min((VIEW_W - 16) / length, (VIEW_H - 16) / height));
    }

    @Override
    protected void renderInstalled(PoseStack pose, MultiBufferSource buffers, ItemStack gun, @Nullable LaserSlot skip) {
        LaserGunModels.render(pose, buffers, gun, LightTexture.FULL_BRIGHT, true, skip == null ? null : skip.part,
                skip == null ? null : skip.attachment);
    }

    @Override
    protected ItemStack partStack(ItemStack gun, LaserSlot slot) {
        if (slot.attachment != null) {
            Attachment on = FirearmItem.attachment(gun, slot.attachment);
            return on == null ? ItemStack.EMPTY : new ItemStack(ModItems.attachment(on));
        }
        GunType type = LaserGunItem.typeOf(gun);
        PartState state = LaserGunItem.parts(gun).get(slot.part);
        if (type == null || state == null) {
            return ItemStack.EMPTY;
        }
        return switch (slot.part) {
            case CORE -> new ItemStack(Items.NETHER_STAR);
            case SLING -> new ItemStack(ModItems.GUN_SLING.get());
            default -> LaserPartItem.of(type, slot.part, state.material(), state.condition());
        };
    }

    @Override
    protected Component gunTitle(ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        return type == null ? Component.translatable("screen.imttech.laserbench.empty") : Component.translatable("item.imttech." + type.id);
    }

    @Override
    protected String rowName(Row row) {
        if (row.gun() || AttachmentItem.of(row.stack()) != null) {
            return row.stack().getHoverName().getString();
        }
        LaserPart part = LaserWorkbenchBlockEntity.partOf(row.stack());
        if (part == LaserPart.CORE || part == LaserPart.SLING) {
            return row.stack().getHoverName().getString();
        }
        return LaserPartItem.materialOf(row.stack()).text().getString();
    }

    @Override
    protected String rowSub(Row row) {
        if (row.gun()) {
            return LaserGunItem.parts(row.stack()).complete() ? "complete" : "incomplete";
        }
        Attachment attachment = AttachmentItem.of(row.stack());
        if (attachment != null) {
            // Een attachment: op welke plek het komt, of dat het niet op het geweer op de tafel past.
            GunType on = LaserGunItem.typeOf(menu.gun());
            return on != null && !attachment.fits(on) ? "does not fit" : attachment.slot.text().getString().toLowerCase(java.util.Locale.ROOT);
        }
        LaserPart part = LaserWorkbenchBlockEntity.partOf(row.stack());
        GunType gun = LaserPartItem.gunOf(row.stack());
        if (part == null || gun == null) {
            return part == null ? "" : part.text().getString();
        }
        int condition = LaserPartItem.conditionOf(row.stack());
        return part.text().getString() + " (" + shortName(gun) + ")" + (condition < PartState.NEW ? " " + condition + "%" : "");
    }

    /** "Laser Rifle" wordt "rifle": de lijst links is smal. */
    private static String shortName(GunType type) {
        return switch (type) {
            case PISTOL -> "pistol";
            case RIFLE -> "rifle";
            case SNIPER -> "sniper";
            case SHOTGUN -> "shotgun";
        };
    }

    @Override
    protected String readyText(ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        LaserParts parts = LaserGunItem.parts(gun);
        if (type == null) {
            return "Drag a frame onto the bench to start: it decides which gun you build.";
        }
        if (!parts.complete()) {
            List<LaserPart> missing = parts.missing();
            if (missing.size() == 1 && missing.getFirst() == LaserPart.CORE) {
                return "Needs a Nether Star as its core.";
            }
            return "Missing: " + missing.size() + " part(s)" + (parts.hasCore() ? "." : ", and a Nether Star.");
        }
        if (!parts.broken().isEmpty()) {
            return "Broken: " + parts.broken().getFirst().text().getString() + ". Replace it.";
        }
        String cell = LaserGunItem.cell(gun) >= 0 ? "Ready, with a cell" : "Ready. Needs an energy cell (R)";
        return cell + (parts.hasSling() ? ", with a sling." : LaserPart.SLING.fitsOn(type) ? "; a sling fits too." : ".");
    }

    @Override
    protected int readyColour(ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        LaserParts parts = LaserGunItem.parts(gun);
        if (type == null) {
            return HudStyle.WHITE;
        }
        if (!parts.complete()) {
            return HudStyle.ORANGE;
        }
        return parts.broken().isEmpty() ? HudStyle.GREEN : HudStyle.RED;
    }

    @Override
    protected Component takeText() {
        return Component.translatable("screen.imttech.laserbench.take");
    }

    @Override
    protected String[] emptyTray() {
        return new String[]{"No laser parts, Nether", "Stars, slings or laser", "guns in your inventory."};
    }

    @Override
    protected String hint() {
        return "turn: scroll on the bench   drag parts onto the gun, or off it";
    }

    @Override
    protected @Nullable Component coveredTooltip(LaserSlot slot, ItemStack gun) {
        return null;
    }

    @Override
    protected List<Component> partTooltip(ItemStack gun, LaserSlot slot, PartState state) {
        if (slot.attachment != null) {
            Attachment on = FirearmItem.attachment(gun, slot.attachment);
            return List.of(on == null ? slot.attachment.text() : on.text(), Component.translatable("screen.imttech.laserbench.drag_off"));
        }
        if (!slot.part.wears()) {
            return List.of(slot.part.text(), Component.translatable("screen.imttech.laserbench.drag_off"));
        }
        return List.of(
                Component.translatable("item.imttech.laser_part", state.material().text(),
                        Component.translatable("item.imttech." + typeOr(gun).id), slot.part.text()),
                Component.translatable("tooltip.imttech.gun_part.condition", state.condition()),
                Component.translatable("screen.imttech.laserbench.drag_off"));
    }
}
