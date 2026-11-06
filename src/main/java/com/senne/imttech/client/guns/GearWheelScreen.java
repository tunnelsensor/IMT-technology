package com.senne.imttech.client.guns;

import com.senne.imttech.client.ImtKeys;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.network.GearActionPayload;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.weapons.GrenadeItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het wiel om snel te trekken (hoofdstuk 52, stap 2, jouw wens: "één groot wiel zoals het G menu of een emote wheel").
 * Houd <b>linker Alt</b> ingedrukt: het wiel komt in het midden van je scherm, met je pistool uit de holster, elke soort
 * granaat uit je pouch (met hoeveel), en "Put away" (wat je vasthoudt wegsteken). Wijs er een aan en laat Alt los (of
 * klik). Wat je in je hand had, valt op de grond (jouw keuze); het pistool of de granaat komt in dat vakje.
 */
public class GearWheelScreen extends Screen {
    private static final int RADIUS = 72;
    private static final int BOX_W = 92;
    private static final int BOX_H = 30;
    private static final int DEAD_ZONE = 22;

    /** Eén punt op het wiel. */
    private record Option(ItemStack icon, Component label, Component sub, boolean enabled, int action, int arg) {}

    private final List<Option> options = new ArrayList<>();
    private boolean holdMode;
    private long openedAt;

    public GearWheelScreen() {
        super(Component.translatable("screen.imttech.wheel"));
    }

    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();
        holdMode = ImtKeys.GEAR_WHEEL.isDown();
        rebuild();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(ModSounds.IMT_MENU_OPEN.get(), 0.5f, 1.2f);
        }
    }

    /** De opties: het pistool, elke granaatsoort, en wegsteken. */
    private void rebuild() {
        options.clear();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        List<ItemStack> holster = Gear.contents(player, GearItem.Kind.HOLSTER);
        ItemStack gun = holster.isEmpty() ? ItemStack.EMPTY : holster.getFirst();
        boolean hasHolster = !Gear.get(player, GearItem.Kind.HOLSTER).isEmpty();
        options.add(new Option(gun.isEmpty() ? new ItemStack(com.senne.imttech.registry.ModItems.HOLSTER.get()) : gun,
                gun.isEmpty() ? Component.translatable(hasHolster ? "screen.imttech.wheel.holster_empty" : "screen.imttech.wheel.no_holster")
                        : gun.getHoverName(),
                gun.isEmpty() ? Component.empty() : Component.translatable("screen.imttech.wheel.rounds",
                        FirearmItem.roundsInMagazine(gun) + (FirearmItem.chambered(gun) ? "+1" : "")),
                !gun.isEmpty(), Gear.ACTION_DRAW_GUN, 0));
        Map<GrenadeItem.Kind, Integer> counts = new EnumMap<>(GrenadeItem.Kind.class);
        Map<GrenadeItem.Kind, ItemStack> icons = new EnumMap<>(GrenadeItem.Kind.class);
        for (ItemStack stack : Gear.contents(player, GearItem.Kind.GRENADE_POUCH)) {
            if (stack.getItem() instanceof GrenadeItem grenade) {
                counts.merge(grenade.kind(), stack.getCount(), Integer::sum);
                icons.putIfAbsent(grenade.kind(), stack);
            }
        }
        for (Map.Entry<GrenadeItem.Kind, Integer> entry : counts.entrySet()) {
            ItemStack icon = icons.get(entry.getKey());
            options.add(new Option(icon, icon.getHoverName(), Component.translatable("screen.imttech.wheel.count", entry.getValue()),
                    true, Gear.ACTION_DRAW_GRENADE, entry.getKey().ordinal()));
        }
        if (counts.isEmpty()) {
            boolean hasPouch = !Gear.get(player, GearItem.Kind.GRENADE_POUCH).isEmpty();
            options.add(new Option(new ItemStack(com.senne.imttech.registry.ModItems.GRENADE_POUCH.get()),
                    Component.translatable(hasPouch ? "screen.imttech.wheel.pouch_empty" : "screen.imttech.wheel.no_pouch"),
                    Component.empty(), false, Gear.ACTION_DRAW_GRENADE, 0));
        }
        ItemStack held = player.getMainHandItem();
        GearItem.Kind room = Gear.roomFor(player, held);
        options.add(new Option(held.isEmpty() ? new ItemStack(com.senne.imttech.registry.ModItems.MAG_POUCH.get()) : held,
                Component.translatable("screen.imttech.wheel.put_away"),
                room == null ? Component.translatable("screen.imttech.wheel.nothing") : Component.translatable("screen.imttech.wheel.into", room.text()),
                room != null, Gear.ACTION_HOLSTER, 0));
    }

    /** Geen wazige achtergrond van Minecraft: alleen onze eigen lichte verduistering (zie HudStyle.dim). */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        rebuild();
        // Vasthouden en loslaten = kiezen (na een kort moment, anders kiest een tik meteen).
        if (holdMode && !ImtKeys.GEAR_WHEEL.isDown()) {
            if (System.currentTimeMillis() - openedAt > 200) {
                choose(hovered(lastMouseX, lastMouseY));
            }
            onClose();
        }
    }

    private double lastMouseX;
    private double lastMouseY;

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        HudStyle.dim(g, width, height);
        int cx = width / 2;
        int cy = height / 2;
        int hovered = hovered(mouseX, mouseY);
        // De ring.
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            int x = (int) Math.round(cx + Math.cos(a) * (RADIUS - 10));
            int y = (int) Math.round(cy + Math.sin(a) * (RADIUS - 10));
            g.fill(x, y, x + 1, y + 1, HudStyle.argb(90, HudStyle.ORANGE));
        }
        g.drawCenteredString(font, Component.translatable("screen.imttech.wheel"), cx, cy - 4, HudStyle.argb(200, HudStyle.ORANGE));
        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            double angle = angleOf(i);
            int bx = (int) Math.round(cx + Math.cos(angle) * RADIUS) - BOX_W / 2;
            int by = (int) Math.round(cy + Math.sin(angle) * RADIUS) - BOX_H / 2;
            boolean hot = i == hovered && option.enabled;
            int frame = option.enabled ? (hot ? HudStyle.argb(255, HudStyle.CYAN) : HudStyle.argb(200, HudStyle.ORANGE))
                    : HudStyle.argb(120, 0x8A9BA0);
            g.fill(bx, by, bx + BOX_W, by + BOX_H, HudStyle.argb(hot ? 230 : 200, 0x0E1116));
            g.renderOutline(bx, by, BOX_W, BOX_H, frame);
            g.renderItem(option.icon, bx + 6, by + 7);
            int textColour = option.enabled ? HudStyle.argb(255, hot ? HudStyle.CYAN : HudStyle.WHITE) : HudStyle.argb(150, 0x8A9BA0);
            g.pose().pushPose();
            g.pose().translate(bx + 26, by + 5, 0);
            g.pose().scale(0.8f, 0.8f, 1);
            g.drawString(font, option.label, 0, 0, textColour, false);
            g.drawString(font, option.sub, 0, 11, HudStyle.argb(200, 0x8A9BA0), false);
            g.pose().popPose();
        }
        g.drawCenteredString(font, Component.translatable(holdMode ? "screen.imttech.wheel.release" : "screen.imttech.wheel.click"),
                cx, cy + RADIUS + 26, HudStyle.argb(150, HudStyle.WHITE));
        super.render(g, mouseX, mouseY, partialTick);
    }

    private double angleOf(int index) {
        return -Math.PI / 2 + index * Math.PI * 2 / Math.max(1, options.size());
    }

    /** Welke optie je aanwijst (de richting vanuit het midden), of -1. */
    private int hovered(double mouseX, double mouseY) {
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        if (options.isEmpty() || Math.sqrt(dx * dx + dy * dy) < DEAD_ZONE) {
            return -1;
        }
        double angle = Math.atan2(dy, dx);
        int best = -1;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < options.size(); i++) {
            double diff = Math.abs(Mth.wrapDegrees(Math.toDegrees(angle - angleOf(i))));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    private void choose(int index) {
        if (index < 0 || index >= options.size()) {
            return;
        }
        Option option = options.get(index);
        if (!option.enabled) {
            return;
        }
        PacketDistributor.sendToServer(new GearActionPayload(option.action, option.arg));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.level().playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), ModSounds.IMT_SELECT.get(),
                    SoundSource.PLAYERS, 0.6f, 1.0f, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = hovered(mouseX, mouseY);
            if (index >= 0) {
                choose(index);
                onClose();
                return true;
            }
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ImtKeys.GEAR_WHEEL.matches(keyCode, scanCode) && !holdMode) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Voor de showcase en de screenshots: deze optie kiezen alsof je hem aanwees. */
    public void chooseIndex(int index) {
        choose(index);
        onClose();
    }

    /** Is het wiel open? */
    public static boolean isOpen() {
        return Minecraft.getInstance().screen instanceof GearWheelScreen;
    }

    public static @Nullable GearWheelScreen current() {
        return Minecraft.getInstance().screen instanceof GearWheelScreen wheel ? wheel : null;
    }
}
