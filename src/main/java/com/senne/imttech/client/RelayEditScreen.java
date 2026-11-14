package com.senne.imttech.client;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.RelayActionPayload;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.redstone.ImtRelays;
import com.senne.imttech.redstone.Relay;
import com.senne.imttech.redstone.RelayBlock;
import com.senne.imttech.registry.ModAttachments;
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
 * Eén relais een naam geven, en het eventueel omkeren.
 *
 * Dit scherm opent als je rechtsklikt op een I.M.T-relais (ook zonder bril, sinds 13 oktober), of
 * via "rename" in het redstone-scherm.
 *
 * <ul>
 *   <li><b>Group</b> (14 oktober, in plaats van tags): waar het relais nu in zit ("base › restricted ›
 *       security room", of "No group"). Alleen om te lezen: een groep kiezen doe je sinds 16 oktober in de app
 *       Groups op de Security OS-desktop (jouw notitie: "dit kan enkel nog op de desktop").</li>
 *   <li><b>Invert</b>: het relais geeft redstone als het UIT staat (alleen bij een echt relais; een koepel
 *       of torentje geeft geen redstone).</li>
 *   <li><b>Network</b> (hoofdstuk 112): hangt het relais aan een netwerk (een netwerkkabel of een Router)? Zonder
 *       netwerk schakelt alleen de knop Pulse hier het nog. Alleen als je het scherm bij het relais zelf opende (de
 *       server zegt het dan); een relais van vóór de Router heeft geen netwerk nodig, en dan staat er niets.</li>
 * </ul>
 */
public class RelayEditScreen extends Screen {
    private static final int PANEL_W = 260;
    private static final int PANEL_H = 174;

    private final int index;
    /** Hoofdstuk 112: wat de server over het netwerk van dit relais zei (zie RelayEditPayload), of UNKNOWN. */
    private final int network;
    private EditBox nameBox;
    private @Nullable Button invertButton;
    private int left;
    private int top;

    public RelayEditScreen(int index) {
        this(index, com.senne.imttech.network.RelayEditPayload.UNKNOWN);
    }

    public RelayEditScreen(int index, int network) {
        super(Component.translatable("screen.imttech.relay_edit"));
        this.index = index;
        this.network = network;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static ImtRelays data() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? ImtRelays.EMPTY : player.getData(ModAttachments.RELAYS);
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

        String typed = nameBox == null ? null : nameBox.getValue(); // blijft staan als het venster van grootte verandert
        nameBox = new EditBox(font, left + 10, top + 34, PANEL_W - 20, 18, Component.translatable("screen.imttech.relay_edit.name"));
        nameBox.setMaxLength(Math.round(ImtConfig.Relays.MAX_NAME));
        nameBox.setValue(typed != null ? typed : relay == null ? "" : relay.name());
        nameBox.setHint(Component.translatable("screen.imttech.relay_edit.name_hint"));
        addRenderableWidget(nameBox);

        // Omkeren: alleen bij een echt relais dat jouw spel kan zien.
        invertButton = null;
        if (relayBlock() != null) {
            invertButton = Button.builder(invertLabel(), button -> PacketDistributor.sendToServer(
                            new RelayActionPayload(RelayActionPayload.Op.INVERT, ImtRedstone.Op.TOGGLE, index, "", "")))
                    .bounds(left + 10, top + 120, PANEL_W - 20, 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.imttech.relay_edit.invert.tip")))
                    .build();
            addRenderableWidget(invertButton);
        }

        int buttons = top + 146;
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.relay_edit.save"), button -> save())
                .bounds(left + 10, buttons, 100, 20).build());
        // Even testen of het werkt: een korte puls (zonder bril alleen als je ernaast staat).
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.relays.pulse"),
                        button -> PacketDistributor.sendToServer(RelayActionPayload.switchOne(index, ImtRedstone.Op.PULSE)))
                .bounds(left + 116, buttons, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.imttech.relay_edit.forget"), button -> forget())
                .bounds(left + 180, buttons, 70, 20).build());
        setInitialFocus(nameBox);
    }

    /** De groep van dit relais: "base › restricted › security room", of "No group". */
    private String groupText() {
        Relay relay = data().get(index);
        String group = relay == null ? "" : relay.group();
        if (group.isEmpty()) {
            return Component.translatable("screen.imttech.relay_edit.no_group").getString();
        }
        List<String> path = data().pathOf(group);
        return path.isEmpty() ? group : String.join(" › ", path);
    }

    /** "Invert: OFF" of "Invert: ON", zoals het blok nu staat. */
    private Component invertLabel() {
        BlockState state = relayBlock();
        boolean inverted = state != null && state.getValue(RelayBlock.INVERTED);
        return Component.translatable(inverted ? "screen.imttech.relay_edit.invert.on" : "screen.imttech.relay_edit.invert.off");
    }

    /** Het blok veranderde (je drukte op "invert"): de knop laat de nieuwe stand zien. */
    @Override
    public void tick() {
        super.tick();
        if (invertButton != null) {
            invertButton.setMessage(invertLabel());
        }
    }

    /** Bewaren: de nieuwe naam, en de groep zoals hij was (die verander je in de app Groups). */
    private void save() {
        Relay relay = data().get(index);
        PacketDistributor.sendToServer(RelayActionPayload.rename(index, nameBox.getValue(), relay == null ? "" : relay.group()));
        onClose();
    }

    private void forget() {
        PacketDistributor.sendToServer(new RelayActionPayload(RelayActionPayload.Op.REMOVE, ImtRedstone.Op.TOGGLE,
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
        g.drawString(font, Component.translatable("screen.imttech.relay_edit.name"), left + 10, top + 24,
                HudStyle.argb(200, HudStyle.WHITE), false);
        g.drawString(font, Component.translatable("screen.imttech.relay_edit.group"), left + 10, top + 58,
                HudStyle.argb(200, HudStyle.WHITE), false);
        // De groep alleen lezen; kiezen doe je op de desktop (16 oktober).
        g.drawString(font, font.plainSubstrByWidth(groupText(), PANEL_W - 20), left + 10, top + 71,
                HudStyle.argb(255, HudStyle.CYAN), true);
        HudStyle.wrappedCentered(g, font, Component.translatable("screen.imttech.relay_edit.group_hint"), left + PANEL_W / 2,
                top + 84, PANEL_W - 20, HudStyle.argb(140, HudStyle.WHITE));
        // Hoofdstuk 112: zijn netwerk (alleen als de server het erbij zei, en het relais er een nodig heeft).
        if (network == com.senne.imttech.network.RelayEditPayload.ONLINE) {
            g.drawString(font, Component.translatable("screen.imttech.relay_edit.network_on"), left + 10, top + 107,
                    HudStyle.argb(230, HudStyle.GREEN), false);
        } else if (network == com.senne.imttech.network.RelayEditPayload.OFFLINE) {
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.imttech.relay_edit.network_off").getString(),
                    PANEL_W - 20), left + 10, top + 107, HudStyle.argb(240, HudStyle.ORANGE), false);
        }
    }
}
