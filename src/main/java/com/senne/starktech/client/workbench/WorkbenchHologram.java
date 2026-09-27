package com.senne.starktech.client.workbench;

import com.senne.starktech.client.DroneCamera;
import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.network.WorkbenchClickPayload;
import com.senne.starktech.suit.StarkSuitItem;
import com.senne.starktech.workbench.Blueprint;
import com.senne.starktech.workbench.HoloPiece;
import com.senne.starktech.workbench.SuitWorkbenchBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Het hologram boven de harnas-werkbank, aan jouw kant: waar kijk je naar, en wat gebeurt er
 * als je klikt.
 *
 * - Elke tick kijken we of je vizier op een stuk van een hologram staat (een straal vanaf je
 *   ogen door de klikvakken van {@link HoloPiece}). Staat er een muur of een mob tussen, dan
 *   telt het niet. Met shift ingedrukt ook niet: dan doet rechtsklikken weer het gewone.
 * - Klik je met rechts terwijl je naar een stuk kijkt, dan houden we de gewone klik tegen en
 *   sturen we de server "klik op dit stuk". Blijf je de knop vasthouden, dan telt dat maar als
 *   één klik (anders pakte je een stuk en begon je meteen opnieuw te bouwen).
 * - Naast je vizier staat een kaartje met wat een klik zou doen: bouwen (met wat het kost en
 *   wat je bij je hebt), pakken, neerleggen, of een upgrade inbouwen.
 */
public final class WorkbenchHologram {
    private WorkbenchHologram() {}

    /** Een stuk van een hologram, en hoe ver het van je ogen is. */
    public record Target(BlockPos pos, HoloPiece piece, double distance) {}

    private static @Nullable Target hovered;
    /** Is de rechtermuisknop losgelaten sinds de laatste klik op een hologram? */
    private static boolean released = true;

    /** Het stuk waar je nu naar kijkt (of null). Gebruikt door de tekenaar voor de gloed. */
    public static @Nullable Target hovered() {
        return hovered;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyUse.isDown()) {
            released = true;
        }
        hovered = pick(mc);
    }

    /** Rechtsklik: kijk je naar het hologram, dan is de klik voor de werkbank. */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Target target = pick(mc);
        if (target == null || mc.player == null) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!released) {
            return;
        }
        released = false;
        PacketDistributor.sendToServer(new WorkbenchClickPayload(target.pos(), target.piece().ordinal()));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /** Naar welk stuk van welk hologram kijk je? */
    private static @Nullable Target pick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null || player.isSpectator()
                || player.isShiftKeyDown() || DroneCamera.isActive()) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(reach));
        Target best = null;
        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        // De werkbanken in de 9 stukken wereld om je heen (verder reik je toch niet).
        for (int cx = chunkX - 1; cx <= chunkX + 1; cx++) {
            for (int cz = chunkZ - 1; cz <= chunkZ + 1; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof SuitWorkbenchBlockEntity)) {
                        continue;
                    }
                    BlockPos pos = be.getBlockPos();
                    if (pos.getCenter().distanceToSqr(eye) > (reach + 3) * (reach + 3)) {
                        continue;
                    }
                    for (HoloPiece piece : HoloPiece.values()) {
                        Optional<Vec3> hit = piece.box().move(pos).clip(eye, end);
                        if (hit.isPresent()) {
                            double distance = hit.get().distanceTo(eye);
                            if (best == null || distance < best.distance()) {
                                best = new Target(pos, piece, distance);
                            }
                        }
                    }
                }
            }
        }
        if (best == null) {
            return null;
        }
        // Staat er iets tussen (een muur, een mob)? Dan kijk je daarnaar, niet naar het hologram.
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() != HitResult.Type.MISS
                && hit.getLocation().distanceTo(eye) < best.distance() - 1.0e-3) {
            return null;
        }
        return best;
    }

    // ================================================================ Het kaartje bij je vizier

    /** Een regel op het kaartje. */
    private record Line(Component text, int rgb) {}

    /** Een GUI-laag boven het vizier: wat doet een klik op dit stuk? */
    public static void renderHint(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Target target = hovered;
        if (target == null || mc.options.hideGui || mc.player == null || mc.level == null
                || !(mc.level.getBlockEntity(target.pos()) instanceof SuitWorkbenchBlockEntity bench)) {
            return;
        }
        List<Line> lines = new ArrayList<>();
        float progress = -1;
        HoloPiece piece = target.piece();
        ItemStack onIt = bench.laid(piece);
        Component name = Component.translatable(piece.langKey());
        lines.add(new Line(onIt.isEmpty()
                ? Component.translatable("workbench.starktech.title.hologram", name)
                : Component.translatable("workbench.starktech.title.laid", name), HudStyle.CYAN));

        Component click = Component.literal("[").append(mc.options.keyUse.getTranslatedKeyMessage()).append("] ");
        ItemStack hand = mc.player.getMainHandItem();
        boolean working = bench.job() != SuitWorkbenchBlockEntity.JOB_NONE;
        boolean busyHere = working && bench.jobPiece() == piece;
        if (busyHere) {
            boolean install = bench.job() == SuitWorkbenchBlockEntity.JOB_INSTALL;
            progress = bench.total() == 0 ? 0 : bench.progress() / (float) bench.total();
            lines.add(new Line(Component.translatable(install ? "workbench.starktech.installing" : "workbench.starktech.building",
                    install ? bench.module().getHoverName() : name, Math.round(progress * 100)), HudStyle.WHITE));
            if (!bench.powered()) {
                lines.add(new Line(Component.translatable("workbench.starktech.no_power"), HudStyle.RED));
            }
        } else if (!bench.flying().isEmpty()) {
            lines.add(new Line(Component.translatable("workbench.starktech.busy"), HudStyle.ORANGE));
        } else if (hand.getItem() instanceof StarkSuitItem suit) {
            HoloPiece own = HoloPiece.of(suit.getType());
            boolean busy = own != null && working && bench.jobPiece() == own;
            if (busy) {
                lines.add(new Line(Component.translatable("workbench.starktech.busy"), HudStyle.ORANGE));
            } else {
                boolean swap = own != null && !bench.laid(own).isEmpty();
                lines.add(new Line(click.copy().append(Component.translatable(
                        swap ? "workbench.starktech.action.swap" : "workbench.starktech.action.lay")), HudStyle.WHITE));
            }
        } else if (SuitWorkbenchBlockEntity.isUpgrade(hand)) {
            if (working) {
                lines.add(new Line(Component.translatable("workbench.starktech.busy"), HudStyle.ORANGE));
            } else if (onIt.isEmpty()) {
                lines.add(new Line(Component.translatable("workbench.starktech.need_piece", name), HudStyle.RED));
            } else if (SuitWorkbenchBlockEntity.fits(hand, onIt)) {
                lines.add(new Line(click.copy().append(Component.translatable("workbench.starktech.action.install",
                        hand.getHoverName())), HudStyle.WHITE));
            } else {
                lines.add(new Line(Component.translatable("workbench.starktech.no_fit", hand.getHoverName(), name),
                        HudStyle.RED));
            }
        } else if (!onIt.isEmpty()) {
            lines.add(new Line(click.copy().append(Component.translatable("workbench.starktech.action.take")), HudStyle.WHITE));
            upgradeLines(mc, onIt, lines);
        } else if (working) {
            lines.add(new Line(Component.translatable("workbench.starktech.busy"), HudStyle.ORANGE));
        } else {
            lines.add(new Line(click.copy().append(Component.translatable("workbench.starktech.action.build")), HudStyle.WHITE));
            costLines(mc.player, piece.blueprint(), lines);
        }
        draw(g, mc.font, lines, progress);
    }

    /** Per materiaal: hoeveel nodig, en hoeveel je bij je hebt (groen genoeg, rood te weinig). */
    private static void costLines(LocalPlayer player, Blueprint blueprint, List<Line> lines) {
        if (player.getAbilities().instabuild) {
            lines.add(new Line(Component.translatable("workbench.starktech.creative"), HudStyle.GREEN));
            return;
        }
        for (Blueprint.Cost cost : blueprint.costs()) {
            int have = Blueprint.count(player.getInventory(), cost);
            ItemStack[] examples = cost.ingredient().getItems();
            Component item = examples.length > 0 ? examples[0].getHoverName() : Component.literal("?");
            lines.add(new Line(Component.translatable("workbench.starktech.cost", cost.count(), item, have),
                    have >= cost.count() ? HudStyle.GREEN : HudStyle.RED));
        }
    }

    /** Wat er al in een stuk zit (dezelfde regels als in de tooltip). */
    private static void upgradeLines(Minecraft mc, ItemStack piece, List<Line> lines) {
        List<Component> tooltip = new ArrayList<>();
        piece.getItem().appendHoverText(piece, Item.TooltipContext.of(mc.level), tooltip, TooltipFlag.NORMAL);
        // De eerste twee regels zijn de uitleg van het harnas; daarna komen de upgrades.
        List<Component> upgrades = tooltip.size() > 2 ? tooltip.subList(2, tooltip.size()) : List.of();
        if (upgrades.isEmpty()) {
            lines.add(new Line(Component.translatable("workbench.starktech.no_upgrades"), 0x8A9BA0));
        }
        for (Component line : upgrades) {
            lines.add(new Line(line.copy().withStyle(ChatFormatting.RESET), HudStyle.ORANGE));
        }
    }

    /** Het kaartje zelf: rechts naast het vizier, in de stijl van de HUD. */
    private static void draw(GuiGraphics g, Font font, List<Line> lines, float progress) {
        int width = 0;
        for (Line line : lines) {
            width = Math.max(width, font.width(line.text()));
        }
        int x0 = g.guiWidth() / 2 + 14;
        int y0 = g.guiHeight() / 2 - 8;
        int x1 = x0 + width + 12;
        int y1 = y0 + lines.size() * 11 + 6 + (progress >= 0 ? 6 : 0);
        if (x1 > g.guiWidth() - 4) {
            // Past het niet rechts? Dan links van het vizier, en anders zo ver naar links als kan.
            x1 = g.guiWidth() / 2 - 14;
            x0 = x1 - width - 12;
            if (x0 < 4) {
                x0 = 4;
                x1 = x0 + width + 12;
                y0 = g.guiHeight() / 2 + 14; // dan onder het vizier, zodat het er niet overheen valt
                y1 = y0 + lines.size() * 11 + 6 + (progress >= 0 ? 6 : 0);
            }
        }
        g.fill(x0, y0, x1, y1, HudStyle.argb(170, 0x0B1A22));
        HudStyle.brackets(g, x0, y0, x1, y1, 4, HudStyle.argb(220, HudStyle.CYAN));
        int y = y0 + 4;
        for (Line line : lines) {
            g.drawString(font, line.text(), x0 + 6, y, HudStyle.argb(240, line.rgb()), true);
            y += 11;
        }
        if (progress >= 0) {
            HudStyle.bar(g, x0 + 6, y + 1, width, 3, progress, HudStyle.CYAN);
        }
    }
}
