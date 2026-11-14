package com.senne.imttech.client.guns;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.network.GearClickPayload;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.LaserGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * De eigen vakjes in je inventaris (hoofdstuk 52, stap 2, jouw wens: "als je die mod (Curios) niet hebt, in een slot
 * dat mijn mod toevoegt"): rechts van je poppetje, onder elkaar: de holster, de granaatpouch en de magazijnpouch; en
 * sinds hoofdstuk 55 de twee <b>sling-vakjes</b> (een geweer met sling op je rug, een voor je borst) in een rij onder
 * het craftraster, naast het vakje van je andere hand, en daarnaast (hoofdstuk 72) het vakje van je <b>rugzak</b> en
 * (hoofdstuk 100) dat van je <b>nek</b>, voor een keycard. Hoofdstuk 109: je <b>vest</b> (de Plate Carrier) links naast je
 * borstplaat, net buiten het scherm (die rij is vol), met een eigen randje. Ze zijn geen echte vakjes van het inventarisscherm (dat is van
 * Minecraft), maar we tekenen ze erbij en vangen de klik: wat je aan je muis hebt gaat erin, wat erin zat komt aan je
 * muis (zie Gear.click). Met Curios erbij zie je ze niet: dan zitten ze in de Curios-slots.
 */
public final class GearSlotsOverlay {
    private GearSlotsOverlay() {}

    private static final int X = 77;
    private static final int Y = 8;
    private static final int STEP = 18;
    /** De sling-vakjes: onder het 2x2-craftraster, rechts van het vakje van je andere hand. */
    private static final int SLING_X = 98;
    private static final int SLING_Y = 62;
    /** Hoofdstuk 109: het vest-vakje, links naast het vakje van je borstplaat (dat staat op x 8, y 26). */
    private static final int VEST_X = -22;
    private static final int VEST_Y = 25;

    private static boolean shown(Screen screen) {
        return screen instanceof InventoryScreen && !Gear.curios();
    }

    /** De linkerbovenhoek van vakje {@code index} (0-2 naast je harnas, 3-4 de slings, 5 je rugzak, 6 je nek), op het scherm. */
    private static int[] at(InventoryScreen screen, int index) {
        if (index == Gear.VEST) {
            // Hoofdstuk 109: je vest, links naast je borstplaat (net buiten het scherm: de rij onder het craftraster is vol). Staat
            // het receptenboek open (dat ligt links), dan rechts naast het scherm.
            if (screen.getRecipeBookComponent().isVisible()) {
                return new int[]{screen.getGuiLeft() + 176 + 4, screen.getGuiTop() + VEST_Y};
            }
            return new int[]{screen.getGuiLeft() + VEST_X, screen.getGuiTop() + VEST_Y};
        }
        if (index == Gear.NECK) {
            // Hoofdstuk 100: je nek, rechts naast je rugzak (het laatste plekje van die rij).
            return new int[]{screen.getGuiLeft() + SLING_X + (Gear.SLINGS + 1) * STEP, screen.getGuiTop() + SLING_Y};
        }
        if (index == Gear.BACK) {
            // Hoofdstuk 72: je rugzak, rechts naast de twee slings.
            return new int[]{screen.getGuiLeft() + SLING_X + Gear.SLINGS * STEP, screen.getGuiTop() + SLING_Y};
        }
        if (index < GearItem.Kind.values().length) {
            return new int[]{screen.getGuiLeft() + X, screen.getGuiTop() + Y + index * STEP};
        }
        int sling = index - GearItem.Kind.values().length;
        return new int[]{screen.getGuiLeft() + SLING_X + sling * STEP, screen.getGuiTop() + SLING_Y};
    }

    private static boolean over(int[] at, double mouseX, double mouseY) {
        return mouseX >= at[0] && mouseX < at[0] + 18 && mouseY >= at[1] && mouseY < at[1] + 18;
    }

    /** Wat er in dit vakje hoort, als vaag plaatje in een leeg vakje. */
    private static ItemStack ghost(int index) {
        if (index == Gear.VEST) {
            return new ItemStack(com.senne.imttech.armor.ArmorRegistry.PLATE_CARRIER.get());
        }
        if (index == Gear.NECK) {
            return new ItemStack(ModItems.KEYCARD.get());
        }
        if (index == Gear.BACK) {
            return new ItemStack(com.senne.imttech.backpack.BackpackRegistry.ASSAULT_PACK.get());
        }
        if (index >= GearItem.Kind.values().length) {
            return new ItemStack(ModItems.LASER_RIFLE.get());
        }
        return new ItemStack(switch (GearItem.Kind.values()[index]) {
            case HOLSTER -> ModItems.HOLSTER.get();
            case GRENADE_POUCH -> ModItems.GRENADE_POUCH.get();
            case MAG_POUCH -> ModItems.MAG_POUCH.get();
        });
    }

    private static ItemStack worn(LocalPlayer player, int index) {
        if (index == Gear.VEST) {
            return Gear.vest(player);
        }
        if (index == Gear.NECK) {
            return Gear.neck(player);
        }
        if (index == Gear.BACK) {
            return Gear.back(player);
        }
        if (index >= GearItem.Kind.values().length) {
            return Gear.sling(player, index - GearItem.Kind.values().length);
        }
        return Gear.get(player, GearItem.Kind.values()[index]);
    }

    private static Component slotName(int index) {
        if (index == Gear.VEST) {
            return Component.translatable("screen.imttech.gear_slot.vest");
        }
        if (index == Gear.NECK) {
            return Component.translatable("screen.imttech.gear_slot.neck");
        }
        if (index == Gear.BACK) {
            return Component.translatable("screen.imttech.gear_slot.back");
        }
        if (index >= GearItem.Kind.values().length) {
            return Component.translatable("screen.imttech.gear_slot." + (index == GearItem.Kind.values().length ? "sling_back" : "sling_chest"));
        }
        return Component.translatable("screen.imttech.gear_slot." + GearItem.Kind.values()[index].id);
    }

    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || !shown(screen)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        ItemStack hoveredStack = ItemStack.EMPTY;
        int hoveredIndex = -1;
        for (int index = 0; index < Gear.SLOTS; index++) {
            int[] at = at(screen, index);
            int x0 = at[0];
            int y = at[1];
            ItemStack worn = worn(player, index);
            boolean hover = over(at, event.getMouseX(), event.getMouseY());
            if (index == Gear.VEST) {
                // Buiten het scherm: een eigen randje eromheen, zodat het niet los in de lucht hangt.
                g.fill(x0 - 3, y - 3, x0 + 21, y + 21, HudStyle.argb(235, 0x10141A));
                g.renderOutline(x0 - 3, y - 3, 24, 24, HudStyle.argb(255, 0x465460));
            }
            g.fill(x0, y, x0 + 18, y + 18, HudStyle.argb(255, 0x1E2128));
            g.renderOutline(x0, y, 18, 18, HudStyle.argb(255, hover ? HudStyle.CYAN : 0x465460));
            if (worn.isEmpty()) {
                // Een vaag plaatje van wat erin hoort.
                g.pose().pushPose();
                g.pose().translate(x0 + 1, y + 1, 0);
                g.renderItem(ghost(index), 0, 0);
                g.fill(0, 0, 16, 16, HudStyle.argb(170, 0x1E2128));
                g.pose().popPose();
            } else {
                g.renderItem(worn, x0 + 1, y + 1);
                int filled = GearItem.kindOf(worn) != null ? GearItem.filled(worn)
                        : com.senne.imttech.backpack.BackpackItem.isBackpack(worn) ? com.senne.imttech.backpack.BackpackItem.filled(worn)
                        : com.senne.imttech.armor.VestItem.isVest(worn) ? com.senne.imttech.armor.VestItem.data(worn).filledMagazines() : 0;
                if (filled > 0) {
                    g.pose().pushPose();
                    g.pose().translate(x0 + 12, y + 11, 200);
                    g.pose().scale(0.6f, 0.6f, 1);
                    g.drawString(Minecraft.getInstance().font, String.valueOf(filled), 0, 0, HudStyle.argb(255, 0xD6AC58), true);
                    g.pose().popPose();
                } else if (LaserGunItem.typeOf(worn) != null) {
                    g.renderItemDecorations(Minecraft.getInstance().font, worn, x0 + 1, y + 1);
                }
            }
            if (hover) {
                hoveredStack = worn;
                hoveredIndex = index;
            }
        }
        if (hoveredIndex >= 0 && screen.getMenu().getCarried().isEmpty()) {
            if (hoveredStack.isEmpty()) {
                g.renderTooltip(Minecraft.getInstance().font, slotName(hoveredIndex), event.getMouseX(), event.getMouseY());
            } else {
                g.renderTooltip(Minecraft.getInstance().font, Screen.getTooltipFromItem(Minecraft.getInstance(), hoveredStack),
                        hoveredStack.getTooltipImage(), event.getMouseX(), event.getMouseY());
            }
        }
    }

    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || !shown(screen)) {
            return;
        }
        for (int index = 0; index < Gear.SLOTS; index++) {
            if (over(at(screen, index), event.getMouseX(), event.getMouseY())) {
                if (event.getButton() == 0) {
                    PacketDistributor.sendToServer(new GearClickPayload(index));
                }
                event.setCanceled(true);
                return;
            }
        }
    }

    /**
     * Hoofdstuk 109: ook het loslaten van de muis boven een eigen vakje houden we tegen. Het vest-vakje ligt buiten het venster
     * van je inventaris, en Minecraft gooit bij loslaten "buiten het venster" weg wat je aan je muis hebt.
     */
    public static void onRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || !shown(screen)) {
            return;
        }
        for (int index = 0; index < Gear.SLOTS; index++) {
            if (over(at(screen, index), event.getMouseX(), event.getMouseY())) {
                event.setCanceled(true);
                return;
            }
        }
    }
}
