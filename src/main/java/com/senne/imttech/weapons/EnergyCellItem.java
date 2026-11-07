package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;

/**
 * De energiecel (hoofdstuk 35): het magazijn van de lasergeweren. Jouw keuze: cellen stapelen niet, want elke cel heeft
 * zijn eigen lading (het data component CELL_CHARGE, 0 tot 100%). Een nieuwe cel is leeg; je laadt hem op in het Charging
 * Station (in het vak van de bril), en een volle cel kost CELL_CHARGE_ENERGY stroom. Het streepje onder het item laat
 * zien hoe vol hij is.
 *
 * Sinds hoofdstuk 57 werkt hij als een magazijn van de Glock: <b>rechtsklik</b> met de cel in je hand steekt hem in het
 * lasergeweer in je werkbalk dat er geen heeft (zie LaserGuns.insertCell); met het geweer in je hand haalt R (zonder
 * vollere cel in je pouch) of U hem er weer uit.
 */
public class EnergyCellItem extends Item {
    /** Een volle cel: 100 (procent). */
    public static final float FULL = 100.0f;

    public EnergyCellItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Hoe vol deze cel is (0 tot 100). */
    public static float charge(ItemStack stack) {
        Float charge = stack.get(ModDataComponents.CELL_CHARGE.get());
        return charge == null ? 0 : charge;
    }

    public static void setCharge(ItemStack stack, float charge) {
        stack.set(ModDataComponents.CELL_CHARGE.get(), Mth.clamp(charge, 0, FULL));
    }

    /** Een nieuwe cel met deze lading. */
    public static ItemStack withCharge(float charge) {
        ItemStack stack = new ItemStack(com.senne.imttech.registry.ModItems.ENERGY_CELL.get());
        setCharge(stack, charge);
        return stack;
    }

    /** Zoveel stroom is er nog nodig om deze cel helemaal vol te maken. */
    public static double missingEnergy(ItemStack stack) {
        return (FULL - charge(stack)) / FULL * ImtConfig.Weapons.CELL_CHARGE_ENERGY;
    }

    /**
     * Laadt de cel op met hooguit {@code energy} stroom (van het Charging Station). Geeft hoeveel stroom hij echt
     * gebruikte (minder als hij al bijna vol was).
     */
    public static double addEnergy(ItemStack stack, double energy) {
        double used = Math.min(energy, missingEnergy(stack));
        if (used <= 0) {
            return 0;
        }
        setCharge(stack, (float) (charge(stack) + used / ImtConfig.Weapons.CELL_CHARGE_ENERGY * FULL));
        return used;
    }

    /** Rechtsklik (hoofdstuk 57): in het lasergeweer in je werkbalk dat geen cel heeft; de server doet het, na even. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            LaserGuns.insertCell(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13 * charge(stack) / FULL);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3FD8FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.energy_cell.charge", Math.round(charge(stack)))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.imttech.energy_cell").withStyle(ChatFormatting.GRAY));
    }
}
