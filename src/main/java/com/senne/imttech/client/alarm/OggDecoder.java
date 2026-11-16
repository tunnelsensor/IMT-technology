package com.senne.imttech.client.alarm;

import com.senne.imttech.alarm.audio.AlarmAudio;
import com.senne.imttech.alarm.audio.AlarmAudioException;
import com.senne.imttech.alarm.audio.Pcm;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.JOrbisAudioStream;

/**
 * Een .ogg inlezen (hoofdstuk 126), met de ogg-lezer die Minecraft zelf voor zijn geluiden gebruikt (JOrbis). Die zit
 * alleen in je spel, niet op een server; daarom zet AlarmClient.init hem bij het opstarten in AlarmAudio.
 *
 * Alleen "Ogg Vorbis" (zoals de geluiden van Minecraft); een ogg met Opus erin kan deze lezer niet.
 */
final class OggDecoder implements AlarmAudio.Decoder {
    @Override
    public Pcm decode(Path file, double maxSeconds) throws IOException, AlarmAudioException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 65536); JOrbisAudioStream ogg = open(in)) {
            AudioFormat format = ogg.getFormat();
            Pcm pcm = new Pcm(Math.round(format.getSampleRate()), format.getChannels(), maxSeconds);
            // De lezer geeft per stukje getallen tussen -1 en 1, de kanalen om en om: precies wat Pcm wil.
            while (!pcm.full() && ogg.readChunk(value -> pcm.add(value))) {
                // doorgaan tot het bestand op is, of tot we genoeg hebben
            }
            return pcm;
        }
    }

    private static JOrbisAudioStream open(InputStream in) throws AlarmAudioException {
        try {
            return new JOrbisAudioStream(in);
        } catch (IOException | RuntimeException notVorbis) {
            throw new AlarmAudioException("bad_ogg", notVorbis);
        }
    }
}
