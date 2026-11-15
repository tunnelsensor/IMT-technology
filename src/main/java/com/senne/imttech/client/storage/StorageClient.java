package com.senne.imttech.client.storage;

import com.senne.imttech.storage.StorageMenu;
import com.senne.imttech.storage.StorageRegistry;
import com.senne.imttech.storage.StorageStatePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Het opslagsysteem in jouw spel (hoofdstuk 113): het scherm van de Storage Interface, en het bericht van de server met
 * wat dat scherm toont.
 */
public final class StorageClient {
    private StorageClient() {}

    /** Bij het opstarten (ImtTechClient). */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterMenuScreensEvent event) ->
                event.register(StorageRegistry.STORAGE_MENU.get(), StorageScreen::new));
    }

    /** De server stuurt wat het scherm toont: heb je dat scherm open, dan onthoudt je menu het. */
    public static void onState(StorageStatePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof StorageMenu menu) {
            menu.setState(payload);
        }
    }
}
