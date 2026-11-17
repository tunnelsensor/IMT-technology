package com.senne.imttech.client.alarm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

/**
 * Een alarmgeluid dat je zelf uploadde, als "stroom" voor de geluidsmotor van Minecraft (hoofdstuk 126).
 *
 * Minecraft speelt lange geluiden (muziek, platen) niet in één keer af, maar vraagt steeds een stukje van ongeveer een
 * seconde ({@link #read}). Normaal komen die stukjes uit een .ogg-bestand; hier komen ze uit het geluid dat de server ons
 * stuurde (mono, 16 bit, AlarmAudio.RATE per seconde). Zo doet het geluid van de speaker alles wat een gewoon geluid
 * doet: zachter als je verder weg staat, uit je linker- of rechteroor, de schuif "Blocks" in je instellingen, stil in het
 * pauzemenu.
 *
 * Hoofdstuk 136: een geluid speelt één keer ({@code playsound}); kom je later in de buurt, dan begint hij halverwege
 * ({@code start}), zodat iedereen hetzelfde moment hoort. Met {@code looping} begint hij aan het einde weer vooraan.
 * De geluidsmotor vraagt de stukjes op zijn eigen draad; de monsters zelf veranderen nooit meer, dus dat is veilig.
 */
final class PcmAudioStream implements AudioStream {
    private final short[] samples;
    private final AudioFormat format;
    private final boolean looping;
    private int position;

    PcmAudioStream(short[] samples, int rate, boolean looping) {
        this(samples, rate, looping, 0);
    }

    /** @param start bij welk monster hij begint (0 = vooraan) */
    PcmAudioStream(short[] samples, int rate, boolean looping, int start) {
        this.samples = samples;
        this.format = new AudioFormat(rate, 16, 1, true, false);
        this.looping = looping && samples.length > 0;
        this.position = Math.max(0, Math.min(samples.length, start));
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    /**
     * Het volgende stukje, hooguit {@code size} bytes (twee per monster). Null aan het einde (zonder lus): dan stopt het
     * geluid zodra wat er al klaarstaat, op is.
     */
    @Override
    public ByteBuffer read(int size) {
        int wanted = Math.max(1, size / 2);
        if (!looping) {
            wanted = Math.min(wanted, samples.length - position);
        }
        if (wanted <= 0) {
            return null;
        }
        // OpenAL wil een "direct" geheugenblok (buiten Java), met de bytes in de volgorde van deze computer.
        ByteBuffer buffer = BufferUtils.createByteBuffer(wanted * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < wanted; i++) {
            if (position >= samples.length) {
                position = 0; // de lus: weer vooraan
            }
            buffer.putShort(samples[position++]);
        }
        buffer.flip();
        return buffer;
    }

    @Override
    public void close() {
        // Niets te sluiten: de monsters zitten gewoon in het geheugen.
    }
}
