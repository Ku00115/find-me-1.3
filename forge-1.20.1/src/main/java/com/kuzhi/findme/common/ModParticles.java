package com.kuzhi.findme.common;

import com.kuzhi.findme.FindMeMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, FindMeMod.MODID);

    public static final RegistryObject<SimpleParticleType> CONTRACT_GLYPH = PARTICLES.register("contract_glyph", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> CONTRACT_GLYPH_RESPONSE = PARTICLES.register("contract_glyph_response", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> CONTRACT_GLYPH_VOW = PARTICLES.register("contract_glyph_vow", () -> new SimpleParticleType(false));

    private ModParticles() {
    }

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}
