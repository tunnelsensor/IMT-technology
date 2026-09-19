package com.senne.starktech.client;

import com.senne.starktech.client.music.EdithMusic;
import com.senne.starktech.client.music.SpotifyAuth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Het muziekscherm: hier koppel je E.D.I.T.H. aan jouw Spotify, en zie je wat er speelt.
 *
 * Stap voor stap (dat staat ook op het scherm):
 * 1. Ga naar developer.spotify.com/dashboard en maak een gratis app ("Create app").
 * 2. Vul bij Redirect URI exact in: http://127.0.0.1:8899/starktech
 * 3. Kopieer het Client ID van die app en plak het hieronder.
 * 4. Klik op "Koppelen": je browser opent, je logt in bij Spotify en klaar.
 *
 * Daarna kun je zeggen: "E.D.I.T.H., speel Thunderstruck van AC/DC".
 * Let op: Spotify moet ergens open staan (pc, telefoon of speaker), want daar speelt hij op.
 */
public class MusicScreen extends Screen {
    private static final int PANEL_W = 340;

    private EditBox clientIdBox;
    private int left;
    private int top;
    /** Laatste melding over het koppelen ("Gelukt!" of een fout). */
    private Component status = Component.empty();

    public MusicScreen() {
        super(Component.translatable("screen.starktech.music"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(8, height / 2 - 95);

        clientIdBox = new EditBox(font, left + 10, top + 108, PANEL_W - 20, 18,
                Component.translatable("screen.starktech.music.client_id"));
        clientIdBox.setMaxLength(64);
        clientIdBox.setValue(EdithClientConfig.SPEC.isLoaded() ? EdithClientConfig.SPOTIFY_CLIENT_ID.get() : "");
        clientIdBox.setHint(Component.translatable("screen.starktech.music.client_id_hint"));
        clientIdBox.setResponder(value -> {
            if (EdithClientConfig.SPEC.isLoaded()) {
                EdithClientConfig.SPOTIFY_CLIENT_ID.set(value.trim());
                EdithClientConfig.SPEC.save();
            }
        });
        addRenderableWidget(clientIdBox);

        // De knop naar het Spotify-dashboard.
        addRenderableWidget(Button.builder(Component.translatable("screen.starktech.music.open_dashboard"),
                        button -> net.minecraft.Util.getPlatform().openUri("https://developer.spotify.com/dashboard"))
                .bounds(left + 10, top + 132, 150, 20).build());

        boolean linked = SpotifyAuth.isLinked();
        addRenderableWidget(Button.builder(Component.translatable(linked
                        ? "screen.starktech.music.relink"
                        : "screen.starktech.music.link"), button -> link())
                .bounds(left + 166, top + 132, 80, 20).build());
        if (linked) {
            addRenderableWidget(Button.builder(Component.translatable("screen.starktech.music.unlink"), button -> {
                SpotifyAuth.unlink();
                status = Component.translatable("screen.starktech.music.unlinked");
                rebuildWidgets();
            }).bounds(left + 252, top + 132, 78, 20).build());

            // Snelknoppen om te testen.
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> EdithMusic.resume())
                    .bounds(left + 10, top + 156, 30, 20).build());
            addRenderableWidget(Button.builder(Component.literal("⏸"), button -> EdithMusic.pause())
                    .bounds(left + 44, top + 156, 30, 20).build());
            addRenderableWidget(Button.builder(Component.literal("⏮"), button -> EdithMusic.previous())
                    .bounds(left + 78, top + 156, 30, 20).build());
            addRenderableWidget(Button.builder(Component.literal("⏭"), button -> EdithMusic.next())
                    .bounds(left + 112, top + 156, 30, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.starktech.music.what"),
                            button -> EdithMusic.whatIsPlaying())
                    .bounds(left + 146, top + 156, 100, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + PANEL_W - 88, top + 180, 80, 20).build());
    }

    private void link() {
        if (!SpotifyAuth.hasClientId()) {
            status = Component.translatable("screen.starktech.music.need_client_id");
            return;
        }
        status = Component.translatable("screen.starktech.music.linking");
        SpotifyAuth.link(error -> Minecraft.getInstance().execute(() -> {
            status = error == null
                    ? Component.translatable("screen.starktech.music.linked")
                    : Component.translatable("screen.starktech.music.failed", error);
            rebuildWidgets();
        }));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        int panelH = 212;
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + panelH, EdithHud.argb(210, 0x08121A));
        EdithHud.brackets(g, left - 4, top - 4, left + PANEL_W + 4, top + panelH, 8, EdithHud.argb(220, EdithHud.CYAN));
        super.render(g, mouseX, mouseY, a);

        g.drawString(font, title, left + 10, top + 8, EdithHud.argb(255, EdithHud.CYAN), true);
        // De stappen.
        int y = top + 24;
        for (int step = 1; step <= 4; step++) {
            g.drawString(font, Component.translatable("screen.starktech.music.step" + step), left + 10, y,
                    EdithHud.argb(210, EdithHud.WHITE), false);
            y += 11;
        }
        // De redirect-URI, zodat je hem kunt overtypen.
        g.drawString(font, SpotifyAuth.REDIRECT_URI, left + 10, y + 4, EdithHud.argb(255, EdithHud.GREEN), false);

        g.drawString(font, Component.translatable("screen.starktech.music.client_id"), left + 10, top + 96,
                EdithHud.argb(220, EdithHud.WHITE), false);

        // Status: gekoppeld of niet, en wat er speelt.
        boolean linked = SpotifyAuth.isLinked();
        g.drawString(font, Component.translatable(linked
                        ? "screen.starktech.music.state_linked"
                        : "screen.starktech.music.state_not_linked"),
                left + 10, top + 184, EdithHud.argb(255, linked ? EdithHud.GREEN : EdithHud.ORANGE), false);
        if (!status.getString().isEmpty()) {
            g.drawString(font, status, left + 10, top + 196, EdithHud.argb(230, EdithHud.CYAN), false);
        }
        String playing = EdithMusic.nowPlaying();
        if (!playing.isEmpty()) {
            g.drawString(font, Component.translatable("screen.starktech.music.now_playing", playing),
                    left + 10, top + 170, EdithHud.argb(220, EdithHud.WHITE), false);
        }
    }
}
