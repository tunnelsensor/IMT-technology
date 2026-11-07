package com.senne.imttech.client.guns;

import com.senne.imttech.client.ImtKeys;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.guns.FirearmItem;
import com.senne.imttech.guns.Gear;
import com.senne.imttech.guns.GearItem;
import com.senne.imttech.network.GearActionPayload;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.weapons.GrenadeItem;
import com.senne.imttech.weapons.LaserGunItem;
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
 * Houd <b>linker Alt</b> ingedrukt: het wiel komt in het midden van je scherm, met je pistool uit de holster (de Glock
 * of, sinds hoofdstuk 55, het laserpistool), het geweer op je rug en dat voor je borst (met hoeveel schoten erin), elke
 * soort granaat uit je pouch (met hoeveel), en "Put away" (wat je vasthoudt wegsteken). Wijs er een aan en laat Alt los
 * (of klik). Wat je in je hand had, valt op de grond (jouw keuze), tenzij het zelf in je holster, een sling-vakje of een
 * pouch past: dan gaat het daarheen (hoofdstuk 55); het pistool, het geweer of de granaat komt in dat vakje.
 *
 * Sinds hoofdstuk 54 (jouw notities van 6 november) staan er ook op: elke pouch die je draagt openen (om er magazijnen of
 * granaten in te stoppen of uit te halen, zie GearPouchMenu) en de skilltree van je vuurwapens (die stond in het G-menu en
 * hoort daar niet).
 */
public class GearWheelScreen extends Screen {
    /** De ring is een ovaal dat meegroeit met het aantal opties (hoofdstuk 55: er staan er nu tot tien op). */
    private static final int MIN_RX = 72;
    private static final int MIN_RY = 72;
    private static final int BOX_W = 92;
    private static final int BOX_H = 30;
    private static final int DEAD_ZONE = 22;

    /** Eén punt op het wiel. */
    private record Option(ItemStack icon, Component label, Component sub, boolean enabled, int action, int arg) {}

    /** Een keuze die het wiel zelf afhandelt (niet de server): de skilltree openen. */
    private static final int LOCAL_SKILLS = 100;

    private final List<Option> options = new ArrayList<>();
    private boolean holdMode;
    private long openedAt;
    /** Wat er moet gebeuren zodra het wiel dicht is (een ander scherm openen). */
    private @Nullable Runnable after;

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

    /** Wat er in een wapen zit, kort: "13 rounds" (de Glock) of "23 shots" (een lasergeweer). */
    private static Component ammo(ItemStack gun) {
        if (FirearmItem.typeOf(gun) != null) {
            return Component.translatable("screen.imttech.wheel.rounds",
                    FirearmItem.roundsInMagazine(gun) + (FirearmItem.chambered(gun) ? "+1" : ""));
        }
        if (LaserGunItem.typeOf(gun) != null) {
            return LaserGunItem.cell(gun) < 0 ? Component.translatable("screen.imttech.wheel.no_cell")
                    : Component.translatable("screen.imttech.wheel.shots", LaserGunItem.shotsLeft(gun));
        }
        return Component.empty();
    }

    /** De opties: het pistool, de geweren op je slings, elke granaatsoort, wegsteken, de pouches openen, de skills. */
    private void rebuild() {
        options.clear();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        List<ItemStack> holster = Gear.contents(player, GearItem.Kind.HOLSTER);
        ItemStack gun = holster.isEmpty() ? ItemStack.EMPTY : holster.getFirst();
        boolean hasHolster = !Gear.get(player, GearItem.Kind.HOLSTER).isEmpty();
        options.add(new Option(gun.isEmpty() ? new ItemStack(ModItems.HOLSTER.get()) : gun,
                gun.isEmpty() ? Component.translatable(hasHolster ? "screen.imttech.wheel.holster_empty" : "screen.imttech.wheel.no_holster")
                        : gun.getHoverName(),
                gun.isEmpty() ? Component.empty() : ammo(gun),
                !gun.isEmpty(), Gear.ACTION_DRAW_GUN, 0));
        // De slings (hoofdstuk 55): het geweer op je rug en dat voor je borst.
        boolean anySling = false;
        for (int i = 0; i < Gear.SLINGS; i++) {
            ItemStack slung = Gear.sling(player, i);
            if (!slung.isEmpty()) {
                anySling = true;
                options.add(new Option(slung, slung.getHoverName(), Component.translatable(i == 0
                        ? "screen.imttech.wheel.on_back" : "screen.imttech.wheel.on_chest", ammo(slung)), true, Gear.ACTION_DRAW_SLING, i));
            }
        }
        if (!anySling) {
            options.add(new Option(new ItemStack(ModItems.LASER_RIFLE.get()), Component.translatable("screen.imttech.wheel.sling_empty"),
                    Component.translatable("screen.imttech.wheel.sling_hint"), false, Gear.ACTION_DRAW_SLING, 0));
        }
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
            options.add(new Option(new ItemStack(ModItems.GRENADE_POUCH.get()),
                    Component.translatable(hasPouch ? "screen.imttech.wheel.pouch_empty" : "screen.imttech.wheel.no_pouch"),
                    Component.empty(), false, Gear.ACTION_DRAW_GRENADE, 0));
        }
        ItemStack held = player.getMainHandItem();
        Component target = Gear.stowTarget(player, held);
        options.add(new Option(held.isEmpty() ? new ItemStack(ModItems.MAG_POUCH.get()) : held,
                Component.translatable("screen.imttech.wheel.put_away"),
                target == null ? Component.translatable("screen.imttech.wheel.nothing") : target,
                target != null, Gear.ACTION_HOLSTER, 0));
        // Elke pouch die je draagt openen: magazijnen of granaten erin stoppen of eruit halen (hoofdstuk 54).
        for (GearItem.Kind kind : new GearItem.Kind[] {GearItem.Kind.MAG_POUCH, GearItem.Kind.GRENADE_POUCH}) {
            ItemStack worn = Gear.get(player, kind);
            if (!worn.isEmpty()) {
                options.add(new Option(worn, Component.translatable("screen.imttech.wheel.open", kind.text()),
                        Component.translatable("screen.imttech.wheel.filled", GearItem.filled(worn), kind.size()), true,
                        Gear.ACTION_OPEN, kind.ordinal()));
            }
        }
        // De skilltree (jouw keuze: niet in het G-menu).
        int points = player.getData(ModAttachments.GUN_SKILLS).points();
        options.add(new Option(new ItemStack(ModItems.GLOCK_19.get()), Component.translatable("screen.imttech.menu.gun_skills"),
                Component.translatable("screen.imttech.wheel.skills_sub", points), true, LOCAL_SKILLS, 0));
    }

    /** Dicht, en dan wat er nog moest gebeuren (de skilltree openen). */
    @Override
    public void onClose() {
        super.onClose();
        if (after != null) {
            Runnable run = after;
            after = null;
            run.run();
        }
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
        int rx = rx();
        int ry = ry();
        int hovered = hovered(mouseX, mouseY);
        // De ring.
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            int x = (int) Math.round(cx + Math.cos(a) * (rx - 10));
            int y = (int) Math.round(cy + Math.sin(a) * (ry - 10));
            g.fill(x, y, x + 1, y + 1, HudStyle.argb(90, HudStyle.ORANGE));
        }
        g.drawCenteredString(font, Component.translatable("screen.imttech.wheel"), cx, cy - 4, HudStyle.argb(200, HudStyle.ORANGE));
        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            double angle = angleOf(i);
            int bx = (int) Math.round(cx + Math.cos(angle) * rx) - BOX_W / 2;
            int by = (int) Math.round(cy + Math.sin(angle) * ry) - BOX_H / 2;
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
                cx, Math.max(4, cy - ry - BOX_H / 2 - 12), HudStyle.argb(150, HudStyle.WHITE));
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** De halve breedte en hoogte van het ovaal: groter met meer opties, maar altijd binnen het scherm. */
    private int rx() {
        int n = Math.max(1, options.size());
        return Math.max(MIN_RX, Math.min(width / 2 - BOX_W / 2 - 6, 40 + 16 * n));
    }

    private int ry() {
        int n = Math.max(1, options.size());
        return Math.max(MIN_RY, Math.min(height / 2 - BOX_H / 2 - 42, 50 + 5 * n));
    }

    private double angleOf(int index) {
        return -Math.PI / 2 + index * Math.PI * 2 / Math.max(1, options.size());
    }

    /** Welke optie je aanwijst (de richting vanuit het midden, op het ovaal), of -1. */
    private int hovered(double mouseX, double mouseY) {
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        if (options.isEmpty() || Math.sqrt(dx * dx + dy * dy) < DEAD_ZONE) {
            return -1;
        }
        double angle = Math.atan2(dy / ry(), dx / rx());
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
        if (option.action == LOCAL_SKILLS) {
            after = () -> Minecraft.getInstance().setScreen(new SkillTreeScreen());
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

    /** Voor de showcase en de screenshots: de optie met deze actie (Gear.ACTION_...) kiezen. */
    public void chooseAction(int action) {
        chooseAction(action, -1);
    }

    /** Hetzelfde, met het extra getal erbij (welk sling-vakje, welke granaatsoort); -1 = het eerste dat past. */
    public void chooseAction(int action, int arg) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).action == action && (arg < 0 || options.get(i).arg == arg)) {
                chooseIndex(i);
                return;
            }
        }
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
