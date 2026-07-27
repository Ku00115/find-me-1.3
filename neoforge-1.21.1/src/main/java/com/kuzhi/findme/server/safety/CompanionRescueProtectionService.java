package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.Config;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class CompanionRescueProtectionService {
    private static final int COMPANION_PROTECTION_TICKS = 120;
    private static final int PLAYER_PROTECTION_TICKS = 45;

    private CompanionRescueProtectionService() {
    }

    public static void protectPlayer(Player player) {
        if (player == null) {
            return;
        }
        player.fallDistance = 0.0f;
        player.clearFire();
        player.invulnerableTime = Math.max(player.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, PLAYER_PROTECTION_TICKS, 0, false, false, true));
    }

    public static void protectCompanion(LivingEntity companion) {
        if (companion == null) {
            return;
        }
        companion.fallDistance = 0.0f;
        companion.clearFire();
        companion.setAirSupply(companion.getMaxAirSupply());
        companion.invulnerableTime = Math.max(companion.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        companion.removeEffect(MobEffects.POISON);
        companion.removeEffect(MobEffects.WITHER);
        addIfUseful(companion, MobEffects.DAMAGE_RESISTANCE, COMPANION_PROTECTION_TICKS, 1);
        addIfUseful(companion, MobEffects.FIRE_RESISTANCE, COMPANION_PROTECTION_TICKS, 0);
        addIfUseful(companion, MobEffects.MOVEMENT_SPEED, COMPANION_PROTECTION_TICKS, 1);
        addIfUseful(companion, MobEffects.DAMAGE_BOOST, COMPANION_PROTECTION_TICKS, 0);
    }

    private static void addIfUseful(LivingEntity living, Holder<MobEffect> effect, int duration, int amplifier) {
        MobEffectInstance current = living.getEffect(effect);
        if (current != null && current.getDuration() >= duration && current.getAmplifier() >= amplifier) {
            return;
        }
        living.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, true));
    }
}
