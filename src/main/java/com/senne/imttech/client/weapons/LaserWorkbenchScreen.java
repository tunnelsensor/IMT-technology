package com.senne.imttech.client.weapons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senne.imttech.client.guns.PartsTableScreen;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.GunsmithMenu;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.GunType;
import com.senne.imttech.weapons.LaserGunItem;
import com.senne.imttech.weapons.LaserPart;
import com.senne.imttech.weapons.LaserPartItem;
import com.senne.imttech.weapons.LaserParts;
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
 */
public class LaserWorkbenchScreen extends PartsTableScreen<LaserPart> {
    public LaserWorkbenchScreen(GunsmithMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, LaserPart.values());
    }

    /** Het geweer op de tafel, of de rifle als er nog geen ligt (voor de maat van het frame-vak). */
    private static GunType typeOr(ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        return type == null ? GunType.RIFLE : type;
    }

    @Override
    protected LaserPart base() {
        return LaserPart.FRAME;
    }

    @Override
    protected @Nullable LaserPart partOf(ItemStack stack) {
        return LaserWorkbenchBlockEntity.partOf(stack);
    }

    @Override
    protected boolean isGun(ItemStack stack) {
        return LaserGunItem.typeOf(stack) != null;
    }

    @Override
    protected @Nullable PartState state(ItemStack gun, LaserPart part) {
        return LaserGunItem.parts(gun).get(part);
    }

    @Override
    protected boolean hasMaterial(LaserPart part) {
        return part.wears();
    }

    @Override
    protected Component partName(LaserPart part) {
        return part.text();
    }

    @Override
    protected boolean allowed(LaserPart part, ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        return type == null || part.fitsOn(type);
    }

    @Override
    protected boolean reachable(LaserPart part, ItemStack gun) {
        return true;
    }

    @Override
    protected boolean removable(LaserPart part, ItemStack gun) {
        return part != LaserPart.FRAME || LaserGunItem.parts(gun).parts().size() == 1;
    }

    /** Een los onderdeel hoort bij één geweer; de Nether Star en de sling passen overal (de sling niet op een pistool). */
    @Override
    protected boolean fits(ItemStack stack, ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        GunType partGun = LaserPartItem.gunOf(stack);
        LaserPart part = partOf(stack);
        return type != null && part != null && (partGun == null || partGun == type) && part.fitsOn(type);
    }

    @Override
    protected AABB bounds(ItemStack gun, LaserPart part) {
        return LaserRig.bounds(typeOr(gun), part);
    }

    @Override
    protected Vector3f exploded(ItemStack gun, LaserPart part) {
        return LaserRig.exploded(typeOr(gun), part);
    }

    @Override
    protected Vector3f centre(ItemStack gun) {
        AABB whole = LaserRig.whole(typeOr(gun));
        return new Vector3f((float) (whole.minX + whole.maxX) / 2, (float) (whole.minY + whole.maxY) / 2 + 1,
                (float) (whole.minZ + whole.maxZ) / 2);
    }

    /** Zo groot als er past: een pistool groot, een sniper (veertig pixels lang) kleiner. */
    @Override
    protected float zoom(ItemStack gun) {
        AABB whole = LaserRig.whole(typeOr(gun));
        float length = (float) whole.getZsize() + 6;
        float height = (float) whole.getYsize() + 20;
        return Math.min(6.6f, Math.min((VIEW_W - 16) / length, (VIEW_H - 16) / height));
    }

    @Override
    protected void renderInstalled(PoseStack pose, MultiBufferSource buffers, ItemStack gun, @Nullable LaserPart skip) {
        LaserGunModels.render(pose, buffers, gun, LightTexture.FULL_BRIGHT, true, skip);
    }

    @Override
    protected ItemStack partStack(ItemStack gun, LaserPart part) {
        GunType type = LaserGunItem.typeOf(gun);
        PartState state = LaserGunItem.parts(gun).get(part);
        if (type == null || state == null) {
            return ItemStack.EMPTY;
        }
        return switch (part) {
            case CORE -> new ItemStack(Items.NETHER_STAR);
            case SLING -> new ItemStack(ModItems.GUN_SLING.get());
            default -> LaserPartItem.of(type, part, state.material(), state.condition());
        };
    }

    @Override
    protected Component gunTitle(ItemStack gun) {
        GunType type = LaserGunItem.typeOf(gun);
        return type == null ? Component.translatable("screen.imttech.laserbench.empty") : Component.translatable("item.imttech." + type.id);
    }

    @Override
    protected String rowName(Row row) {
        if (row.gun()) {
            return row.stack().getHoverName().getString();
        }
        LaserPart part = partOf(row.stack());
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
        LaserPart part = partOf(row.stack());
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
    protected @Nullable Component coveredTooltip(LaserPart part, ItemStack gun) {
        return null;
    }

    @Override
    protected List<Component> partTooltip(ItemStack gun, LaserPart part, PartState state) {
        if (!part.wears()) {
            return List.of(part.text(), Component.translatable("screen.imttech.laserbench.drag_off"));
        }
        return List.of(
                Component.translatable("item.imttech.laser_part", state.material().text(),
                        Component.translatable("item.imttech." + typeOr(gun).id), part.text()),
                Component.translatable("tooltip.imttech.gun_part.condition", state.condition()),
                Component.translatable("screen.imttech.laserbench.drag_off"));
    }
}
