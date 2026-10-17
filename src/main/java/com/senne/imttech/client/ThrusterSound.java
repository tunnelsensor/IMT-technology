package com.senne.imttech.client;

import com.senne.imttech.registry.ModSounds;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * Het gebrul van de stuwraketten. Het speelt als lus en past zich elke tick aan: harder en
 * hoger naarmate de stuwraketten harder werken. Gaan ze uit, dan dooft het uit in plaats van
 * ineens te stoppen.
 */
final class ThrusterSound extends AbstractTickableSoundInstance {
    private final LocalPlayer player;

    ThrusterSound(LocalPlayer player) {
        super(ModSounds.SUIT_THRUSTER.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.05f;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    @Override
    public void tick() {
        if (player.isRemoved()) {
            stop();
            return;
        }
        x = player.getX();
        y = player.getY();
        z = player.getZ();
        if (!SuitFlight.isEngaged()) {
            volume *= 0.7f;
            if (volume < 0.02f) {
                stop();
            }
            return;
        }
        float power = SuitFlight.thrust();
        volume += ((0.25f + 0.6f * power) - volume) * 0.3f;
        pitch = 0.75f + 0.45f * power;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
