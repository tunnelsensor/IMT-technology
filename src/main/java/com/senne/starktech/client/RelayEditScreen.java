package com.senne.starktech.client;

import com.senne.starktech.client.hud.HudStyle;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.network.RelayActionPayload;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.redstone.EdithRelays;
import com.senne.starktech.redstone.Relay;
import com.senne.starktech.redstone.RelayBlock;
import com.senne.starktech.registry.ModAttachments;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Eén relais een naam en een groep geven, en het eventueel omkeren.
 *
 * Dit scherm opent als je rechtsklikt op een E.D.I.T.H.-relais (ook zonder bril, sinds 13 oktober), of
 * via "rename" in het redstone-scherm.
 *
 * <ul>
 *   <li><b>Group</b> (14 oktober, in plaats van tags): de knop toont waar het relais nu in zit ("base ›
 *       restricted › security room", of "No group"). Klik erop en je kiest een groep in het groepen-scherm
 *       (daar maak je ook nieuwe groepen en zet je groepen in groepen). Bewaren doe je hier met Save.</li>
 *   <li><b>Invert</b>: het relais geeft redstone als het UIT staat (alleen bij een echt relais; een koepel
 *       of torentje geeft geen redstone).</li>
 * </ul>
 */
public class RelayEditScreen extends Screen {
    private static final int PANEL_W = 260;
    private static final int PANEL_H = 160;

    private final int index;
    private EditBox nameBox;
    /** Wat je in het naamvak typte (blijft staan terwijl je in het groepen-scherm een groep kiest). */
    private @Nullable String typedName;
    /** De gekozen groep ("" = geen groep), nog niet bewaard; null = nog zoals het relais hem heeft. */
    private @Nullable String group;
    private @Nullable Button invertButton;
    private int left;
    private int top;

    public RelayEditScreen(int index) {
        super(Component.translatable("screen.starktech.relay_edit"));
        this.index = index;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static EdithRelays data() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? EdithRelays.EMPTY : player.getData(ModAttachments.RELAYS);
    }

    /** Het blok van dit relais zoals jouw spel het ziet, of null (te ver weg, of geen echt relais). */
    private @Nullable BlockState relayBlock() {
        Relay relay = data().get(index);
        Minecraft mc = Minecraft.getInstance();
        if (relay == null || relay.channel() >= 0 || mc.level == null || mc.level.dimension() != relay.dimension()
                || !mc.level.hasChunkAt(relay.pos())) {
            return null;
        }
        BlockState state = mc.level.getBlockState(relay.pos());
        return state.getBlock() instanceof RelayBlock ? state : null;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(8, (height - PANEL_H) / 2);
        Relay relay = data().get(index);

        nameBox = new EditBox(font, left + 10, top + 34, PANEL_W - 20, 18, Component.translatable("screen.starktech.relay_edit.name"));
        nameBox.setMaxLength(Math.round(StarkConfig.Relays.MAX_NAME));
        nameBox.setValue(typedName != null ? typedName : relay == null ? "" : relay.name());
        nameBox.setHint(Component.translatable("screen.starktech.relay_edit.name_hint"));
        nameBox.setResponder(text -> typedName = text);
        addRenderableWidget(nameBox);

        // De groep: een knop die het groepen-scherm opent om er een te kiezen.
        if (group == null) {
            group = relay == null ? "" : relay.group();
        }
        addRenderableWidget(Button.builder(groupLabel(), button -> Minecraft.getInstance().setScreen(new RelayGroupsScreen(this, group)))
                .bounds(left + 10, top + 68, PANEL_W - 20, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.starktech.relay_edit.group.tip")))
                .build());

        // Omkeren: alleen bij een echt relais dat jouw spel kan zien.
        invertButton = null;
        if (relayBlock() != null) {
            invertButton = Button.builder(invertLabel(), button -> PacketDistributor.sendToServer(
                            new RelayActionPayload(RelayActionPayload.Op.INVERT, EdithRedstone.Op.TOGGLE, index, "", "")))
                    .bounds(left + 10, top + 106, PANEL_W - 20, 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.starktech.relay_edit.invert.tip")))
                    .build();
            addRenderableWidget(invertButton);
        }

        int buttons = top + 132;
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relay_edit.save"), button -> save())
                .bounds(left + 10, buttons, 100, 20).build());
        // Even testen of het werkt: een korte puls (zonder bril alleen als je ernaast staat).
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relays.pulse"),
                        button -> PacketDistributor.sendToServer(RelayActionPayload.switchOne(index, EdithRedstone.Op.PULSE)))
                .bounds(left + 116, buttons, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.relay_edit.forget"), button -> forget())
                .bounds(left + 180, buttons, 70, 20).build());
        setInitialFocus(nameBox);
    }

    /** De knop van de groep: "base › restricted › security room", of "No group". */
    private Component groupLabel() {
        String chosen = group == null ? "" : group;
        if (chosen.isEmpty()) {
            return Component.translatable("screen.starktech.relay_edit.no_group");
        }
        List<String> path = data().pathOf(chosen);
        String text = path.isEmpty() ? chosen : String.join(" › ", path);
        return Component.literal(font.plainSubstrByWidth(text, PANEL_W - 30));
    }

    /** Het groepen-scherm koos een groep ("" = geen groep). Bewaard wordt pas met Save. */
    public void chooseGroup(String chosen) {
        group = chosen;
    }

    /** De naam van dit relais (zoals je hem nu typt), voor de kop van het groepen-scherm. */
    public String relayName() {
        Relay relay = data().get(index);
        return typedName != null && !typedName.isBlank() ? typedName : relay == null ? "" : relay.name();
    }

    /** "Invert: OFF" of "Invert: ON", zoals het blok nu staat. */
    private Component invertLabel() {
        BlockState state = relayBlock();
        boolean inverted = state != null && state.getValue(RelayBlock.INVERTED);
        return Component.translatable(inverted ? "screen.starktech.relay_edit.invert.on" : "screen.starktech.relay_edit.invert.off");
    }

    /** Het blok veranderde (je drukte op "invert"): de knop laat de nieuwe stand zien. */
    @Override
    public void tick() {
        super.tick();
        if (invertButton != null) {
            invertButton.setMessage(invertLabel());
        }
    }

    private void save() {
        PacketDistributor.sendToServer(RelayActionPayload.rename(index, nameBox.getValue(), group == null ? "" : group));
        onClose();
    }

    private void forget() {
        PacketDistributor.sendToServer(new RelayActionPayload(RelayActionPayload.Op.REMOVE, EdithRedstone.Op.TOGGLE,
                index, "", ""));
        onClose();
    }

    /**
     * Het paneel hoort bij de achtergrond. Minecraft tekent in render() eerst een wazige
     * achtergrond; wat daarvóór getekend was, werd dus mee vervaagd en verduisterd.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
        HudStyle.dim(g, width, height);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, HudStyle.argb(210, 0x08121A));
        HudStyle.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H, 8, HudStyle.argb(220, HudStyle.CYAN));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);

        g.drawString(font, title, left + 10, top + 8, HudStyle.argb(255, HudStyle.CYAN), true);
        Relay relay = data().get(index);
        if (relay != null) {
            String where = relay.pos().getX() + " " + relay.pos().getY() + " " + relay.pos().getZ();
            g.drawString(font, where, left + PANEL_W - 10 - font.width(where), top + 8, HudStyle.argb(160, HudStyle.WHITE), true);
        }
        g.drawString(font, Component.translatable("screen.starktech.relay_edit.name"), left + 10, top + 24,
                HudStyle.argb(200, HudStyle.WHITE), false);
        g.drawString(font, Component.translatable("screen.starktech.relay_edit.group"), left + 10, top + 58,
                HudStyle.argb(200, HudStyle.WHITE), false);
    }
}
