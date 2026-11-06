package com.senne.imttech.client.guns;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunSkill;
import com.senne.imttech.guns.GunSkills;
import com.senne.imttech.network.SkillLearnPayload;
import com.senne.imttech.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * De skilltree van de vuurwapens (hoofdstuk 52, stap 3, jouw wens: "dit kun je dan ook zien in een soort GUI
 * skilltree"). Links de globale skills (voor elk vuurwapen), rechts die van het pistool zelf (de Glock 19); ze tellen
 * bij elkaar op. Bovenaan je punten en de balk naar het volgende punt. Elke skill: wat hij doet, zijn niveau (vijf
 * bolletjes), wat hij je nu oplevert, en de knop om een niveau te leren met de punten die het kost (jouw keuze: punten
 * verdelen, globaal is het dubbele want het geldt voor alles).
 */
public class SkillTreeScreen extends Screen {
    private static final int COL_W = 190;
    private static final int ROW_H = 24;
    private static final int TOP = 40;

    private final List<Button> buttons = new ArrayList<>();
    private final FirearmType weapon = FirearmType.GLOCK_19;

    public SkillTreeScreen() {
        super(Component.translatable("screen.imttech.skills"));
    }

    private GunSkills skills() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? GunSkills.EMPTY : player.getData(ModAttachments.GUN_SKILLS);
    }

    private int left(int column) {
        return width / 2 - COL_W - 8 + column * (COL_W + 16);
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        for (int column = 0; column < 2; column++) {
            String scope = column == 0 ? GunSkills.GLOBAL : weapon.id;
            for (GunSkill skill : GunSkill.values()) {
                int y = TOP + 18 + skill.ordinal() * ROW_H;
                Button button = addRenderableWidget(Button.builder(Component.literal("+"),
                                b -> PacketDistributor.sendToServer(new SkillLearnPayload(scope, skill.ordinal())))
                        .bounds(left(column) + COL_W - 24, y + 1, 22, 16).build());
                buttons.add(button);
            }
        }
        int bottom = TOP + 18 + GunSkill.values().length * ROW_H + 22;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(width / 2 - 30, bottom - 2, 60, 16).build());
    }

    @Override
    public void tick() {
        super.tick();
        GunSkills skills = skills();
        int index = 0;
        for (int column = 0; column < 2; column++) {
            String scope = column == 0 ? GunSkills.GLOBAL : weapon.id;
            for (GunSkill skill : GunSkill.values()) {
                Button button = buttons.get(index++);
                boolean maxed = skills.level(scope, skill) >= GunSkills.maxLevel();
                button.active = skills.canLearn(scope, skill);
                button.setMessage(Component.literal(maxed ? "MAX" : "+" + skills.cost(scope, skill)));
            }
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        HudStyle.dim(g, width, height);
        GunSkills skills = skills();
        int x0 = left(0) - 8;
        int x1 = left(1) + COL_W + 8;
        int bottom = TOP + 18 + GunSkill.values().length * ROW_H + 22;
        g.fill(x0, 10, x1, bottom, HudStyle.argb(235, 0x0E1116));
        g.renderOutline(x0, 10, x1 - x0, bottom - 10, HudStyle.argb(255, 0x465460));
        HudStyle.brackets(g, x0 + 2, 12, x1 - 3, bottom - 3, 8, HudStyle.argb(255, HudStyle.ORANGE));
        g.drawCenteredString(font, title, width / 2, 14, HudStyle.argb(255, HudStyle.ORANGE));
        // Punten en de balk naar het volgende punt.
        Component points = Component.translatable("screen.imttech.skills.points", skills.points());
        g.drawString(font, points, left(0), 26, HudStyle.argb(255, skills.points() > 0 ? HudStyle.GREEN : HudStyle.WHITE), false);
        int barX = left(1);
        int barW = COL_W - 60;
        HudStyle.bar(g, barX, 28, barW, 5, skills.xp() / (float) Math.max(1, skills.nextPointXp()), HudStyle.CYAN);
        g.pose().pushPose();
        g.pose().translate(barX + barW + 4, 26, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, skills.xp() + " / " + skills.nextPointXp() + " xp", 0, 0, HudStyle.argb(200, HudStyle.WHITE), false);
        g.pose().popPose();
        for (int column = 0; column < 2; column++) {
            String scope = column == 0 ? GunSkills.GLOBAL : weapon.id;
            int x = left(column);
            Component head = column == 0 ? Component.translatable("screen.imttech.skills.global") : weapon.text();
            g.drawString(font, head, x, TOP + 2, HudStyle.argb(255, HudStyle.CYAN), false);
            g.pose().pushPose();
            g.pose().translate(x + font.width(head) + 6, TOP + 3, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            int percent = Math.round((column == 0 ? ImtConfig.Guns.SKILL_GLOBAL_STEP : ImtConfig.Guns.SKILL_WEAPON_STEP) * 100);
            g.drawString(font, Component.translatable(column == 0 ? "screen.imttech.skills.global_what" : "screen.imttech.skills.weapon_what", percent),
                    0, 0, HudStyle.argb(160, HudStyle.WHITE), false);
            g.pose().popPose();
            // De stam van de boom.
            g.fill(x + 3, TOP + 14, x + 4, TOP + 18 + GunSkill.values().length * ROW_H - 10, HudStyle.argb(120, HudStyle.ORANGE));
            int textW = (int) ((COL_W - 28 - 14 - GunSkills.maxLevel() * 9 - 8) / 0.7f);
            for (GunSkill skill : GunSkill.values()) {
                int y = TOP + 18 + skill.ordinal() * ROW_H;
                int level = skills.level(scope, skill);
                g.fill(x + 3, y + 8, x + 12, y + 9, HudStyle.argb(120, HudStyle.ORANGE));
                g.drawString(font, skill.text(), x + 14, y + 1, HudStyle.argb(255, level > 0 ? HudStyle.GREEN : HudStyle.WHITE), false);
                // De bolletjes, en ernaast wat de skill nu oplevert en waar hij voor is (afgekapt als het niet past).
                for (int i = 0; i < GunSkills.maxLevel(); i++) {
                    int px = x + 14 + i * 9;
                    g.fill(px, y + 12, px + 6, y + 17, HudStyle.argb(i < level ? 255 : 70, i < level ? HudStyle.GREEN : HudStyle.WHITE));
                }
                float step = column == 0 ? ImtConfig.Guns.SKILL_GLOBAL_STEP : ImtConfig.Guns.SKILL_WEAPON_STEP;
                String now = "-" + Math.round(level * step * 100) + "%  " + skill.description().getString();
                g.pose().pushPose();
                g.pose().translate(x + 14 + GunSkills.maxLevel() * 9 + 6, y + 12, 0);
                g.pose().scale(0.7f, 0.7f, 1);
                g.drawString(font, font.plainSubstrByWidth(now, textW), 0, 0, HudStyle.argb(190, HudStyle.WHITE), false);
                g.pose().popPose();
            }
        }
        // Wat ze samen doen, onderaan.
        int y = TOP + 18 + GunSkill.values().length * ROW_H + 4;
        List<String> parts = new ArrayList<>();
        for (GunSkill skill : GunSkill.values()) {
            int percent = Math.round((1 - skills.factor(skill, weapon)) * 100);
            if (percent > 0) {
                parts.add(skill.text().getString() + " -" + percent + "%");
            }
        }
        String total = parts.isEmpty() ? Component.translatable("screen.imttech.skills.nothing_yet").getString()
                : Component.translatable("screen.imttech.skills.total", weapon.text().getString()).getString() + " " + String.join(", ", parts);
        g.pose().pushPose();
        g.pose().translate(left(0), y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, total, 0, 0, HudStyle.argb(200, HudStyle.GREEN), false);
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);
    }
}
