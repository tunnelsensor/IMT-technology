package com.senne.imttech.client.pickpocket;

import com.mojang.math.Axis;
import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.network.PocketPayload;
import com.senne.imttech.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * De zak van een andere speler (hoofdstuk 12, jouw keuze: een zak-minigame).
 * <pre>
 *  POCKET &gt; LOTTE                               NOTICED
 *  + - - - - - - - - - - - - - - - - +          [   ]
 *  |   pull a card up here           |          [   ]
 *  + - - - - - - - - - - - - - - - - +          [###]
 *  |  (paper)   [card]  (string)     |          [###]
 *  |     (coin)    (flint) [card]    |          [###]
 *  +---------------------------------+
 * </pre>
 * Zijn keycards liggen omgekeerd tussen rommel (alleen plaatjes: een touwtje, een bonnetje, een muntje).
 * Sleep met de muis: rommel opzij, en een kaart naar boven uit de zak. Elke beweging vult de meter
 * (snel slepen veel meer dan rustig); hou je even stil, dan zakt hij weer. Vol = hij merkt het.
 *
 * Welke kaart er achter een achterkant zit, weet je spel niet: dat zegt de server pas als je hem eruit
 * hebt (zie Pickpocket). Draait hij zich om, of loopt hij weg, dan stopt de server het ook.
 */
public class PocketScreen extends Screen {
    private enum Phase { SEARCH, WAIT, DONE }

    private static final int W = 300;
    private static final int H = 236;
    private static final int POCKET_W = 220;
    private static final int POCKET_H = 112;
    /** Zo hoog is het vak boven de zak, waar je een kaart naartoe trekt. */
    private static final int HAND_H = 50;
    private static final int CARD_W = 40;
    private static final int CARD_H = 26;
    private static final int JUNK_SIZE = 34;
    /** Zo groot tekenen we een stukje rommel (een item is 16 pixels). */
    private static final float JUNK_SCALE = 2.1f;

    /** Hoeveel de meter vol loopt per pixel slepen: rustig, of snel (meer dan FAST pixels per tick). */
    private static final float SLOW_RATE = 0.0025f;
    private static final float FAST_RATE = 0.006f;
    private static final float FAST = 8f;
    /** Iets oppakken voelt hij ook een beetje. */
    private static final float PICK_UP = 0.02f;
    /** Zoveel zakt de meter per tick als je even stilhoudt (na CALM_AFTER ticks). */
    private static final float CALM = 0.003f;
    private static final int CALM_AFTER = 15;
    private static final int RESULT_TICKS = 70;

    /** De rommel: alleen plaatjes (een touwtje, een bonnetje, een muntje, een vuursteen...). */
    private static final Item[] JUNK = {Items.STRING, Items.PAPER, Items.GOLD_NUGGET, Items.FLINT, Items.STICK,
            Items.FEATHER, Items.IRON_NUGGET, Items.CLAY_BALL};

    /** Voor de showcase: het scherm speelt zichzelf (rommel opzij, dan een kaart eruit). */
    public static boolean autoPlay;

    /** Eén ding in de zak: een kaart (achterkant boven) of rommel. */
    private static final class Thing {
        /** Welke kaart (0, 1, ...), of -1 voor rommel. */
        final int card;
        final ItemStack junk;
        /** Het midden, vanaf de linkerbovenhoek van de zak. */
        float x;
        float y;
        final int w;
        final int h;
        final float angle;
        /** Een kaart die je eruit hebt, ligt open: zijn naam en niveau. */
        String label = "";
        int level;
        boolean open;

        Thing(int card, ItemStack junk, float x, float y, int w, int h, float angle) {
            this.card = card;
            this.junk = junk;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.angle = angle;
        }
    }

    private final int victimId;
    private final String victimName;
    private final List<Thing> things = new ArrayList<>();
    private Phase phase = Phase.SEARCH;
    private float notice;
    private float moved;
    private int idle;
    private @Nullable Thing held;
    private float grabX;
    private float grabY;
    private @Nullable Thing pulled;
    private String result = "";
    private int resultTicks;
    private boolean finished;
    private int ticks;

    public PocketScreen(int victimId, String victimName, int cards, long seed) {
        super(Component.literal(victimName));
        this.victimId = victimId;
        this.victimName = victimName;
        Random random = new Random(seed);
        // Eerst de kaarten, onderin de zak; dan de rommel, voor een groot deel bovenop de kaarten.
        for (int i = 0; i < cards; i++) {
            things.add(new Thing(i, ItemStack.EMPTY, 30 + random.nextFloat() * (POCKET_W - 60),
                    POCKET_H * 0.45f + random.nextFloat() * (POCKET_H * 0.55f - CARD_H), CARD_W, CARD_H,
                    random.nextFloat() * 50 - 25));
        }
        int junk = 7 + random.nextInt(4);
        for (int j = 0; j < junk; j++) {
            float x;
            float y;
            if (cards > 0 && j < junk * 2 / 3) {
                Thing card = things.get(random.nextInt(cards));
                x = card.x + random.nextFloat() * 30 - 15;
                y = card.y + random.nextFloat() * 20 - 10;
            } else {
                x = random.nextFloat() * POCKET_W;
                y = random.nextFloat() * POCKET_H;
            }
            Thing thing = new Thing(-1, new ItemStack(JUNK[random.nextInt(JUNK.length)]), x, y, JUNK_SIZE, JUNK_SIZE,
                    random.nextFloat() * 70 - 35);
            clamp(thing);
            things.add(thing);
        }
    }

    public int victim() {
        return victimId;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        HudStyle.dim(g, width, height);
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int pocketX() {
        return left() + 14;
    }

    private int pocketY() {
        return top() + 24 + HAND_H + 6;
    }

    /** Rommel blijft in de zak; een kaart mag er boven uit, het vak in. */
    private static void clamp(Thing thing) {
        thing.x = Math.max(thing.w / 2f, Math.min(POCKET_W - thing.w / 2f, thing.x));
        float topLimit = thing.card >= 0 ? -HAND_H + thing.h / 2f : thing.h / 2f;
        thing.y = Math.max(topLimit, Math.min(POCKET_H - thing.h / 2f, thing.y));
    }

    /** Is deze kaart helemaal uit de zak (boven de rand)? */
    private static boolean outOfPocket(Thing thing) {
        return thing.card >= 0 && thing.y + thing.h / 2f < -2;
    }

    private @Nullable Thing thingAt(double mouseX, double mouseY) {
        double x = mouseX - pocketX();
        double y = mouseY - pocketY();
        for (int i = things.size() - 1; i >= 0; i--) {
            Thing thing = things.get(i);
            if (Math.abs(x - thing.x) <= thing.w / 2.0 && Math.abs(y - thing.y) <= thing.h / 2.0) {
                return thing;
            }
        }
        return null;
    }

    // ================================================================ Tekenen

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        int frame = phase != Phase.DONE ? HudStyle.CYAN : result.equals("taken") ? HudStyle.GREEN : HudStyle.RED;
        g.fill(x, y, x + W, y + H, HudStyle.argb(240, 0x0D1318));
        HudStyle.brackets(g, x, y, x + W, y + H, 8, HudStyle.argb(255, frame));
        g.drawString(font, "POCKET > " + victimName.toUpperCase(Locale.ROOT), x + 10, y + 9, HudStyle.argb(255, HudStyle.CYAN), false);

        // Het vak boven de zak: hier trek je een kaart naartoe.
        int px = pocketX();
        int py = pocketY();
        int hy = py - HAND_H - 4;
        dashed(g, px, hy, POCKET_W, HAND_H, 0x33505F);
        if (phase == Phase.SEARCH) {
            String hint = "pull a card up here";
            g.drawString(font, hint, px + (POCKET_W - font.width(hint)) / 2, hy + HAND_H / 2 - 4, HudStyle.argb(255, 0x3A5462), false);
        }

        // De zak: donkere stof met een stiksel, en een lichtere rand bovenaan (de opening).
        g.fill(px, py, px + POCKET_W, py + POCKET_H, HudStyle.argb(255, 0x34291F));
        g.fill(px, py, px + POCKET_W, py + 3, HudStyle.argb(255, 0x5A4838));
        g.fill(px, py + 3, px + POCKET_W, py + 7, HudStyle.argb(255, 0x231B14));
        for (int sx = px + 3; sx < px + POCKET_W - 3; sx += 6) {
            g.fill(sx, py + POCKET_H - 4, sx + 3, py + POCKET_H - 3, HudStyle.argb(255, 0x6B5642));
        }
        for (int sy = py + 9; sy < py + POCKET_H - 4; sy += 6) {
            g.fill(px + 3, sy, px + 4, sy + 3, HudStyle.argb(255, 0x6B5642));
            g.fill(px + POCKET_W - 4, sy, px + POCKET_W - 3, sy + 3, HudStyle.argb(255, 0x6B5642));
        }

        // Wat erin zit (de laatste bovenop).
        for (Thing thing : things) {
            drawThing(g, thing, px + thing.x, py + thing.y);
        }

        // De meter: hoe dicht hij erbij is het te merken.
        int mx = x + W - 50;
        g.drawString(font, "NOTICED", mx + 8 - font.width("NOTICED") / 2 + 10, py - 14, HudStyle.argb(255, HudStyle.WHITE), false);
        g.fill(mx + 6, py, mx + 26, py + POCKET_H, HudStyle.argb(255, 0x0A1014));
        g.renderOutline(mx + 6, py, 20, POCKET_H, HudStyle.argb(255, 0x33505F));
        int filled = Math.round(POCKET_H * Math.min(1, notice));
        int meterColour = notice < 0.5f ? HudStyle.GREEN : notice < 0.8f ? HudStyle.ORANGE : HudStyle.RED;
        boolean blink = notice >= 0.8f && (System.currentTimeMillis() / 120) % 2 == 0;
        g.fill(mx + 8, py + POCKET_H - filled, mx + 24, py + POCKET_H - 1, HudStyle.argb(blink ? 140 : 255, meterColour));
        String percent = Math.round(Math.min(1, notice) * 100) + "%";
        g.drawString(font, percent, mx + 16 - font.width(percent) / 2, py + POCKET_H + 4, HudStyle.argb(255, meterColour), false);

        // Onderaan: wat je doet, of hoe het afliep.
        int ty = y + H - 26;
        int cx = x + W / 2;
        String[] lines = footer();
        int colour = phase != Phase.DONE ? HudStyle.WHITE : result.equals("taken") ? HudStyle.GREEN : HudStyle.RED;
        g.drawString(font, lines[0], cx - font.width(lines[0]) / 2, ty, HudStyle.argb(255, colour), false);
        g.drawString(font, lines[1], cx - font.width(lines[1]) / 2, ty + 11, HudStyle.argb(255, 0x7F93A0), false);
    }

    private String[] footer() {
        if (phase == Phase.WAIT) {
            return new String[] {"Easing it out...", ""};
        }
        if (phase == Phase.SEARCH) {
            return new String[] {"Drag things aside and pull a card up out of the pocket.",
                    "Slow and steady: every move can be felt.    Esc: stop"};
        }
        return switch (result) {
            case "taken" -> new String[] {"You took: " + (pulled == null ? "a keycard" : pulled.label),
                    victimName + " didn't notice a thing."};
            case "caught" -> new String[] {"CAUGHT! " + victimName + " noticed you.", "You glow for a while: they can find you."};
            case "slipped" -> new String[] {"That card isn't there any more.", victimName + " must have moved it."};
            default -> new String[] {victimName + " walked away.", "Get behind them again to try once more."};
        };
    }

    /** Een kaart (achterkant, of open als je hem hebt) of een stukje rommel, schuin. */
    private void drawThing(GuiGraphics g, Thing thing, float cx, float cy) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(thing.open ? 0 : thing.angle));
        int hw = thing.w / 2;
        int hh = thing.h / 2;
        if (thing.card < 0) {
            g.pose().scale(JUNK_SCALE, JUNK_SCALE, 1);
            g.renderItem(thing.junk, -8, -8);
        } else if (!thing.open) {
            // De achterkant van een keycard: donkerblauw met een lichte streep, voor iedereen hetzelfde.
            g.fill(-hw, -hh, hw, hh, HudStyle.argb(255, 0x0B1A2C));
            g.fill(-hw + 1, -hh + 1, hw - 1, hh - 1, HudStyle.argb(255, 0x1D3D66));
            g.fill(-hw + 1, -hh + 6, hw - 1, -hh + 10, HudStyle.argb(255, 0x3FA9FF));
            g.fill(hw - 12, hh - 9, hw - 4, hh - 4, HudStyle.argb(255, 0xC9A03A));
        } else {
            // Open, groot in je hand: de kleur van zijn niveau, zijn naam en zijn niveau.
            int bw = 66;
            int bh = 38;
            int colour = levelColour(thing.level);
            g.fill(-bw - 1, -bh / 2 - 1, bw + 1, bh / 2 + 1, HudStyle.argb(255, 0x0B1A2C));
            g.fill(-bw, -bh / 2, bw, bh / 2, HudStyle.argb(255, colour));
            g.fill(-bw, -bh / 2 + 7, bw, -bh / 2 + 11, HudStyle.argb(255, 0x0B1A2C));
            String name = font.plainSubstrByWidth(thing.label, bw * 2 - 8);
            g.drawString(font, name, -bw + 4, -1, HudStyle.argb(255, 0x0B1A2C), false);
            String level = thing.level > 0 ? "LEVEL " + thing.level : "BLANK";
            g.drawString(font, level, -bw + 4, bh / 2 - 10, HudStyle.argb(255, 0x0B1A2C), false);
        }
        g.pose().popPose();
    }

    private static int levelColour(int level) {
        return switch (level) {
            case 1 -> 0xE8EEF2;
            case 2 -> 0x5CFF9D;
            case 3 -> 0x3FA9FF;
            case 4 -> 0xC27BFF;
            case 5 -> 0xFFD447;
            default -> 0x9AA4AC;
        };
    }

    /** Een stippellijn rond een vak. */
    private static void dashed(GuiGraphics g, int x, int y, int w, int h, int rgb) {
        int colour = HudStyle.argb(255, rgb);
        for (int i = 0; i < w; i += 6) {
            g.fill(x + i, y, x + Math.min(w, i + 3), y + 1, colour);
            g.fill(x + i, y + h - 1, x + Math.min(w, i + 3), y + h, colour);
        }
        for (int i = 0; i < h; i += 6) {
            g.fill(x, y + i, x + 1, y + Math.min(h, i + 3), colour);
            g.fill(x + w - 1, y + i, x + w, y + Math.min(h, i + 3), colour);
        }
    }

    // ================================================================ Slepen

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && phase == Phase.SEARCH) {
            Thing thing = thingAt(mouseX, mouseY);
            if (thing != null) {
                pickUp(thing, (float) (mouseX - pocketX()), (float) (mouseY - pocketY()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void pickUp(Thing thing, float mouseX, float mouseY) {
        held = thing;
        things.remove(thing);
        things.add(thing); // bovenop
        grabX = thing.x - mouseX;
        grabY = thing.y - mouseY;
        notice += PICK_UP;
        idle = 0;
        play(SoundEvents.BUNDLE_REMOVE_ONE, 1.2f + (float) Math.random() * 0.3f, 0.25f);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (held != null && phase == Phase.SEARCH) {
            move(held, (float) (mouseX - pocketX()) + grabX, (float) (mouseY - pocketY()) + grabY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** Verschuift iets naar deze plek; de afstand telt voor de meter. */
    private void move(Thing thing, float x, float y) {
        float oldX = thing.x;
        float oldY = thing.y;
        thing.x = x;
        thing.y = y;
        clamp(thing);
        moved += (float) Math.hypot(thing.x - oldX, thing.y - oldY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && held != null) {
            Thing thing = held;
            held = null;
            if (phase == Phase.SEARCH && outOfPocket(thing)) {
                pull(thing);
            } else {
                play(SoundEvents.BUNDLE_INSERT, 1.3f, 0.2f);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Een kaart is uit de zak: de server zegt welke het is (en geeft hem jou). */
    private void pull(Thing card) {
        pulled = card;
        phase = Phase.WAIT;
        resultTicks = 0;
        CompoundTag args = new CompoundTag();
        args.putInt("index", card.card);
        PacketDistributor.sendToServer(new PocketPayload(victimId, "take", args));
    }

    // ================================================================ Elke tick

    @Override
    public void tick() {
        super.tick();
        ticks++;
        switch (phase) {
            case SEARCH -> {
                if (moved > 0) {
                    notice += moved * (moved > FAST ? FAST_RATE : SLOW_RATE);
                    idle = 0;
                } else if (++idle > CALM_AFTER) {
                    notice = Math.max(0, notice - CALM);
                }
                moved = 0;
                if (notice >= 0.6f && ticks % 16 == 0) {
                    play(SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.15f + notice * 0.2f);
                }
                if (notice >= 1) {
                    notice = 1;
                    PacketDistributor.sendToServer(PocketPayload.of(victimId, "caught"));
                    finish("caught");
                    return;
                }
                if (autoPlay) {
                    if (ticks == 40) {
                        tourUncover();
                    } else if (ticks == 90) {
                        tourPull();
                    }
                }
            }
            case WAIT -> {
                if (++resultTicks > 100) {
                    onClose(); // geen antwoord van de server
                }
            }
            case DONE -> {
                if (++resultTicks > RESULT_TICKS) {
                    onClose();
                }
            }
        }
    }

    private void finish(String how) {
        finished = true;
        result = how;
        phase = Phase.DONE;
        resultTicks = 0;
        if (how.equals("taken")) {
            play(ModSounds.IMT_CONFIRM.get(), 1.4f, 0.5f);
        } else {
            play(ModSounds.IMT_DENIED.get(), 0.9f, 0.8f);
        }
    }

    /** Het antwoord van de server: je hebt hem (welke), of het liep anders af. */
    public void onResult(CompoundTag args) {
        if (phase == Phase.DONE) {
            return;
        }
        String how = args.getString("result");
        if (how.equals("taken") && pulled != null) {
            pulled.open = true;
            pulled.label = args.getString("name");
            pulled.level = args.getInt("level");
            pulled.x = POCKET_W / 2f;
            pulled.y = -HAND_H / 2f;
        }
        finish(how);
    }

    @Override
    public void removed() {
        super.removed();
        if (!finished) {
            PacketDistributor.sendToServer(PocketPayload.of(victimId, "cancel"));
        }
    }

    // ================================================================ Voor de rondleiding en de showcase

    /** Schuift de rommel die op de eerste kaart ligt rustig opzij (de meter loopt een beetje op). */
    public void tourUncover() {
        Thing card = things.stream().filter(thing -> thing.card == 0).findFirst().orElse(null);
        if (card == null) {
            return;
        }
        for (Thing thing : new ArrayList<>(things)) {
            if (thing.card < 0 && Math.abs(thing.x - card.x) < (thing.w + card.w) / 2f
                    && Math.abs(thing.y - card.y) < (thing.h + card.h) / 2f) {
                pickUp(thing, thing.x, thing.y);
                float away = thing.x < card.x ? -1 : 1;
                float target = card.x + away * (card.w + thing.w) * 0.75f;
                // In stapjes van 5 pixels, zoals een rustige hand (snel slepen zou de meter laten vollopen).
                for (int step = 0; step < 20 && Math.abs(thing.x - target) > 1; step++) {
                    move(thing, thing.x + Math.max(-5, Math.min(5, target - thing.x)), thing.y);
                    tick();
                }
                held = null;
            }
        }
    }

    /** Trekt de eerste kaart rustig naar boven, uit de zak. */
    public void tourPull() {
        for (Thing thing : new ArrayList<>(things)) {
            if (thing.card == 0) {
                pickUp(thing, thing.x, thing.y);
                for (int step = 0; step < 30 && !outOfPocket(thing); step++) {
                    move(thing, thing.x, thing.y - 6);
                    tick();
                }
                held = null;
                if (outOfPocket(thing) && phase == Phase.SEARCH) {
                    pull(thing);
                }
                return;
            }
        }
    }

    /** De meter op deze stand zetten (voor een screenshot). */
    public void tourNotice(float value) {
        notice = value;
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
