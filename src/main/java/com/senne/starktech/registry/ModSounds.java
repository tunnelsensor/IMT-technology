package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Geluiden van E.D.I.T.H. Welk geluid er echt afspeelt staat in
 * assets/starktech/sounds.json. Nu verwijzen die naar bestaande Minecraft-geluiden;
 * je kunt daar later je eigen .ogg-bestanden neerzetten.
 */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, StarkTech.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_BOOT = register("edith.boot");
    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_NOTIFY = register("edith.notify");
    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_ALERT = register("edith.alert");
    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_DENIED = register("edith.denied");
    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_SCAN = register("edith.scan");
    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_MENU_OPEN = register("edith.menu_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_SELECT = register("edith.select");
    public static final DeferredHolder<SoundEvent, SoundEvent> STATION_UPGRADE = register("station.upgrade");
    // Fase 2
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_DEPLOY = register("drone.deploy");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_LASER = register("drone.laser");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_RECALL = register("drone.recall");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_HURT = register("drone.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_DESTROYED = register("drone.destroyed");
    public static final DeferredHolder<SoundEvent, SoundEvent> SATELLITE_LOCK = register("satellite.lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> SATELLITE_CHARGE = register("satellite.charge");
    /** Fase 8: het inslaan van de satelliet. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SATELLITE_FIRE = register("satellite.fire");
    // Fase 3
    public static final DeferredHolder<SoundEvent, SoundEvent> COMMS_MESSAGE = register("comms.message");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMMS_PING = register("comms.ping");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, name)));
    }
}
