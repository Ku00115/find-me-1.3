package com.kuzhi.findme.common;

import com.kuzhi.findme.FindMeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, FindMeMod.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CONTRACT_GLYPH = PARTICLES.register("contract_glyph", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CONTRACT_GLYPH_RESPONSE = PARTICLES.register("contract_glyph_response", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CONTRACT_GLYPH_VOW = PARTICLES.register("contract_glyph_vow", () -> new SimpleParticleType(false));

    private ModParticles() {
    }

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}
