package com.senne.imttech.client.weapons;

import com.senne.imttech.weapons.SmokeCloudOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/**
 * Een dikke pluk rook van de smokebom (hoofdstuk 35): de grote rookplaatjes van een kampvuur, tot 3 à 4 blokken groot,
 * in de kleur van de granaat, en bijna niet doorzichtig. Hij komt zacht op, drijft heel langzaam (een beetje omhoog)
 * en trekt aan het eind weer weg. Met honderden tegelijk wordt het een muur van rook.
 *
 * Jouw wens: de rook gaat niet door blokken. Daarom is een pluk nooit groter dan de ruimte tot het dichtstbijzijnde blok
 * (zie SmokeView.room): midden in een kamer groot, tegen een muur, vloer of plafond klein, zodat hij er niet aan de
 * andere kant uitsteekt. Drijft hij naar een plek waar de wolk niet kan komen, dan verdwijnt hij snel.
 */
public class SmokeCloudParticle extends TextureSheetParticle {
    /** Zo ondoorzichtig is een pluk op zijn dikst (1 = helemaal). */
    private static final float THICK = 0.92f;
    /** Zo lang (ticks) komt een pluk op, en zo lang trekt hij weg. */
    private static final int FADE_IN = 12;
    private static final int FADE_OUT = 30;
    /** Zo ver mag een pluk voorbij de ruimte tot een blok steken: net in de muur, nooit erdoorheen. */
    private static final float INTO_WALL = 0.1f;

    /** Zo groot wil deze pluk zijn (de helft van zijn breedte, in blokken), als er ruimte genoeg is. */
    private final float wanted;
    /** Hij dreef uit de wolk: snel weg. */
    private boolean leaving;
    private float fade = 1;

    protected SmokeCloudParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                                 SmokeCloudOptions options, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.friction = 0.94f;
        this.gravity = -0.0015f; // warme rook stijgt een heel klein beetje
        this.lifetime = 90 + random.nextInt(50);
        this.wanted = 1.5f + random.nextFloat() * 0.6f;
        float room = SmokeView.room(x, y, z);
        this.quadSize = room < 0 ? 0.3f : Math.min(wanted, room + INTO_WALL);
        float shade = 0.85f + random.nextFloat() * 0.15f;
        this.rCol = ((options.rgb() >> 16) & 0xFF) / 255f * shade;
        this.gCol = ((options.rgb() >> 8) & 0xFF) / 255f * shade;
        this.bCol = (options.rgb() & 0xFF) / 255f * shade;
        this.alpha = 0;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        this.hasPhysics = true;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        roll += 0.004f;
        // Zo groot als de ruimte hier toelaat (rustig groeien of krimpen, zodat het niet verspringt).
        float room = SmokeView.room(x, y, z);
        if (room < 0) {
            leaving = true;
        } else {
            float size = Math.min(wanted, room + INTO_WALL);
            quadSize += (size - quadSize) * (size < quadSize ? 0.5f : 0.1f);
        }
        if (leaving) {
            fade *= 0.7f;
            if (fade < 0.03f) {
                remove();
            }
        }
        float in = Math.min(1, age / (float) FADE_IN);
        float out = Math.min(1, (lifetime - age) / (float) FADE_OUT);
        alpha = THICK * Math.min(in, out) * fade;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Maakt de plukken (aangemeld in ImtTechClient). */
    public static class Provider implements ParticleProvider<SmokeCloudOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SmokeCloudOptions options, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new SmokeCloudParticle(level, x, y, z, dx, dy, dz, options, sprites);
        }
    }
}
