package com.kuzhi.findme.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class ClientContractGlyphParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float baseSize;
    private final float red;
    private final float green;
    private final float blue;

    private ClientContractGlyphParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, float red, float green, float blue) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.lifetime = 26 + this.random.nextInt(12);
        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.xd = 0.0;
        this.yd = 0.006 + this.random.nextDouble() * 0.012;
        this.zd = 0.0;
        this.baseSize = 0.34f + this.random.nextFloat() * 0.22f;
        this.quadSize = this.baseSize;
        this.alpha = 0.0f;
        this.rCol = colorJitter(red);
        this.gCol = colorJitter(green);
        this.bCol = colorJitter(blue);
        this.pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        float progress = (float)this.age / (float)this.lifetime;
        float fadeIn = Math.min(1.0f, progress * 5.0f);
        float fadeOut = Math.min(1.0f, (1.0f - progress) * 3.0f);
        this.alpha = Math.max(0.0f, Math.min(fadeIn, fadeOut)) * 0.88f;
        this.quadSize = this.baseSize * (0.85f + progress * 0.55f);
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    private float colorJitter(float value) {
        return Math.min(1.0f, value + this.random.nextFloat() * 0.08f);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final float red;
        private final float green;
        private final float blue;

        public Provider(SpriteSet sprites, float red, float green, float blue) {
            this.sprites = sprites;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        @Override
        public ClientContractGlyphParticle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new ClientContractGlyphParticle(level, x, y, z, this.sprites, this.red, this.green, this.blue);
        }
    }
}
