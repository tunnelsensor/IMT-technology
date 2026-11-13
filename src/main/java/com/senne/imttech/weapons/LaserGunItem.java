package com.senne.imttech.weapons;

import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.registry.ModDataComponents;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Een lasergeweer (hoofdstuk 35): het pistool, het geweer, de sniper of de shotgun (zie {@link GunType}). Jouw keuze:
 * bediening zoals TACZ. Linkerklik schiet, rechterklik richt door het vizier, R herlaadt, H bekijkt het geweer; dat
 * regelt je spel (zie client/weapons/GunControl), de server beslist (zie {@link LaserGuns}).
 *
 * In het geweer zit een energiecel (of niet): het data component GUN_CELL is hoe vol die cel is (0 tot 100), en
 * zonder component zit er geen cel in. Een nieuw geweer is leeg: druk R en hij pakt de volste cel uit je inventaris.
 *
 * Sinds hoofdstuk 55 (jouw keuze: "al de bestaande laserguns wil ik ook dat ze gecraft worden in een nieuwe soort
 * tafel op dezelfde manier als de Glock") bestaat een geweer uit <b>onderdelen</b> (het data component LASER_PARTS, zie
 * {@link LaserParts}): zes onderdelen die slijten, een Nether Star als kern, en bij de lange geweren een sling. Je zet
 * ze in elkaar op de Laser Workbench. Een geweer uit een oude wereld (van voor hoofdstuk 55) heeft nog geen onderdelen:
 * zodra het in iemands inventaris zit, krijgt het complete titanium onderdelen en een kern (het was al van jou).
 */
public class LaserGunItem extends Item {
    private final GunType type;

    public LaserGunItem(Properties properties, GunType type) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    public GunType type() {
        return type;
    }

    /** Het geweer in deze hand, of null. */
    public static @Nullable GunType typeOf(ItemStack stack) {
        return stack.getItem() instanceof LaserGunItem gun ? gun.type : null;
    }

    // ================================================================ De cel

    /** Hoe vol de cel in dit geweer is (0 tot 100), of -1 als er geen cel in zit. */
    public static float cell(ItemStack stack) {
        Float charge = stack.get(ModDataComponents.GUN_CELL.get());
        return charge == null ? -1 : charge;
    }

    /** Zet een cel met deze lading in het geweer (of haal hem eruit met -1). */
    public static void setCell(ItemStack stack, float charge) {
        if (charge < 0) {
            stack.remove(ModDataComponents.GUN_CELL.get());
        } else {
            stack.set(ModDataComponents.GUN_CELL.get(), Math.min(EnergyCellItem.FULL, charge));
        }
    }

    /** Zoveel schoten zitten er nog in (met de capacitor zoals hij nu is). */
    public static int shotsLeft(ItemStack stack) {
        GunType type = typeOf(stack);
        float charge = cell(stack);
        return type == null || charge <= 0 ? 0 : (int) Math.floor(charge / Math.max(0.01f, LaserGuns.shotCost(stack)) + 1.0E-4);
    }

    // ================================================================ De onderdelen (hoofdstuk 55)

    public static LaserParts parts(ItemStack stack) {
        LaserParts parts = stack.get(ModDataComponents.LASER_PARTS.get());
        return parts == null ? LaserParts.EMPTY : parts;
    }

    public static void setParts(ItemStack stack, LaserParts parts) {
        if (parts.parts().isEmpty()) {
            stack.remove(ModDataComponents.LASER_PARTS.get());
        } else {
            stack.set(ModDataComponents.LASER_PARTS.get(), parts);
        }
    }

    /** Zitten de zes onderdelen en de kern erin, en is er geen kapot? */
    public static boolean works(ItemStack stack) {
        LaserParts parts = parts(stack);
        return parts.complete() && parts.broken().isEmpty();
    }

    /** Hangt er een sling aan (dan past het in een sling-vakje)? */
    public static boolean hasSling(ItemStack stack) {
        return parts(stack).hasSling();
    }

    /** Een compleet geweer (zes onderdelen van dit materiaal en een kern), zonder cel: zo komt het van de tafel. */
    public static ItemStack complete(GunType type, GunMaterial material) {
        return withParts(type, LaserParts.complete(material));
    }

    /** Een compleet titanium geweer met een volle cel (voor creative, de showcase en de tests). */
    public static ItemStack loaded(GunType type) {
        ItemStack stack = complete(type, GunMaterial.TITANIUM);
        setCell(stack, EnergyCellItem.FULL);
        return stack;
    }

    /** Een geweer met alleen deze onderdelen, zonder cel (zo ontstaat het op de Laser Workbench). */
    public static ItemStack withParts(GunType type, LaserParts parts) {
        ItemStack stack = new ItemStack(com.senne.imttech.registry.ModItems.laserGun(type));
        setParts(stack, parts);
        return stack;
    }

    /**
     * Een geweer uit een oude wereld (van voor hoofdstuk 55) heeft nog geen onderdelen: zodra het in een inventaris zit,
     * krijgt het complete titanium onderdelen en een kern, zodat het blijft werken.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && !stack.has(ModDataComponents.LASER_PARTS.get())) {
            setParts(stack, LaserParts.complete(GunMaterial.TITANIUM));
        }
    }

    // ================================================================ Geen gewoon gereedschap

    /** Met een geweer in je hand breek je geen blokken (linkerklik is schieten). */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    /** En je zwaait er niet mee. */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, net.minecraft.world.InteractionHand hand) {
        return true;
    }

    /** Een schot (de cel wordt leger, een onderdeel slijt) laat het geweer niet opnieuw in je hand komen. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    // ================================================================ Het streepje en de uitleg

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return cell(stack) >= 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13 * Math.max(0, cell(stack)) / EnergyCellItem.FULL);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3FD8FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech." + type.id).withStyle(ChatFormatting.GRAY));
        LaserParts parts = parts(stack);
        if (!parts.complete()) {
            List<LaserPart> missing = parts.missing();
            tooltip.add((missing.size() == 1 && missing.getFirst() == LaserPart.CORE
                    ? Component.translatable("tooltip.imttech.laser_gun.no_core")
                    : Component.translatable("tooltip.imttech.laser_gun.incomplete", missing.size())).withStyle(ChatFormatting.RED));
        }
        for (Map.Entry<LaserPart, PartState> entry : parts.parts().entrySet()) {
            LaserPart part = entry.getKey();
            PartState state = entry.getValue();
            if (!part.wears()) {
                continue;
            }
            ChatFormatting colour = state.broken() ? ChatFormatting.RED : state.worn() ? ChatFormatting.GOLD : ChatFormatting.DARK_AQUA;
            tooltip.add(Component.literal("  ").append(Component.translatable("tooltip.imttech.firearm.part",
                    state.material().text(), part.text(), state.condition())).withStyle(colour));
        }
        if (parts.hasCore()) {
            tooltip.add(Component.literal("  ").append(Component.translatable("tooltip.imttech.laser_gun.core")).withStyle(ChatFormatting.YELLOW));
        }
        if (parts.hasSling()) {
            tooltip.add(Component.literal("  ").append(Component.translatable("tooltip.imttech.laser_gun.sling")).withStyle(ChatFormatting.GOLD));
        }
        // Hoofdstuk 103, stap 7: wat erop zit (een kijker, een greep).
        for (com.senne.imttech.guns.Attachment attachment : com.senne.imttech.guns.FirearmItem.attachments(stack).list()) {
            tooltip.add(Component.literal("  ").append(Component.translatable("tooltip.imttech.firearm.attachment",
                    attachment.slot.text(), attachment.text())).withStyle(ChatFormatting.AQUA));
        }
        String damage = type.rays() > 1 ? type.rays() + " x " + fmt(type.damage()) : fmt(type.damage());
        tooltip.add(Component.translatable("tooltip.imttech.laser_gun.stats", damage,
                fmt(20f / type.interval()), type.shotsPerCell()).withStyle(ChatFormatting.DARK_AQUA));
        float cell = cell(stack);
        tooltip.add(cell < 0
                ? Component.translatable("tooltip.imttech.laser_gun.no_cell").withStyle(ChatFormatting.RED)
                : Component.translatable("tooltip.imttech.laser_gun.cell", shotsLeft(stack), type.shotsPerCell())
                        .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.imttech.laser_gun.keys").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String fmt(float value) {
        return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
