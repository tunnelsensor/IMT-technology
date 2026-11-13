package com.senne.imttech.guns;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * Een attachment als item (hoofdstuk 103): de Red Dot Sight, de Sniper Scope. Je maakt het op de Gunsmith Table (het
 * tabblad "Make parts", onder Attachments) en sleept het daar op een wapen; eraf slepen geeft het terug. In je hand doet
 * het niets.
 */
public class AttachmentItem extends Item {
    private final Attachment attachment;

    public AttachmentItem(Properties properties, Attachment attachment) {
        super(properties.stacksTo(1));
        this.attachment = attachment;
    }

    public Attachment attachment() {
        return attachment;
    }

    /** Welk attachment dit item is, of null als het er geen is. */
    public static @Nullable Attachment of(ItemStack stack) {
        return stack.getItem() instanceof AttachmentItem item ? item.attachment : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech." + attachment.id).withStyle(ChatFormatting.GRAY));
        // Op welke wapens het past.
        List<String> guns = new ArrayList<>();
        for (FirearmType type : FirearmType.values()) {
            if (attachment.fits(type)) {
                guns.add(type.text().getString());
            }
        }
        // Hoofdstuk 103, stap 7: en op welke lasergeweren (op de Laser Workbench).
        for (com.senne.imttech.weapons.GunType laser : com.senne.imttech.weapons.GunType.values()) {
            if (attachment.fits(laser)) {
                guns.add(Component.translatable("item.imttech." + laser.id).getString());
            }
        }
        tooltip.add(Component.translatable("tooltip.imttech.attachment.fits", String.join(", ", guns))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.imttech.attachment.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
