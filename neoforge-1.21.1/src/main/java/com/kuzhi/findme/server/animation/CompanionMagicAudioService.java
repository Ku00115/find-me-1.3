package com.kuzhi.findme.server.animation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class CompanionMagicAudioService {
    private CompanionMagicAudioService() {
    }

    public static void playCircleOpen(ServerPlayer player, Vec3 position, float radius, boolean urgent) {
        float size = Math.max(1.0f, Math.min(radius, 10.0f));
        playNearOwner(player, position, SoundEvents.ENCHANTMENT_TABLE_USE, 0.56f + size * 0.026f, urgent ? 0.84f : 1.04f);
        playNearOwner(player, position, SoundEvents.AMETHYST_BLOCK_CHIME, urgent ? 0.46f : 0.32f, urgent ? 1.34f : 1.18f);
        if (urgent) {
            playNearOwner(player, position, SoundEvents.BEACON_POWER_SELECT, 0.24f + size * 0.010f, 1.28f);
        }
    }

    public static void playStorageOpen(ServerPlayer player, Vec3 position, float radius) {
        float size = Math.max(1.0f, Math.min(radius, 12.0f));
        playNearOwner(player, position, SoundEvents.ENCHANTMENT_TABLE_USE, 0.48f + size * 0.022f, 0.92f);
        playNearOwner(player, position, SoundEvents.AMETHYST_BLOCK_CHIME, 0.20f + size * 0.012f, 1.42f);
    }

    public static void playStorageWhoosh(ServerPlayer player, Vec3 position, int stage) {
        float pitch = switch (stage) {
            case 0 -> 1.24f;
            case 1 -> 1.06f;
            default -> 0.88f;
        };
        float volume = switch (stage) {
            case 0 -> 0.20f;
            case 1 -> 0.26f;
            default -> 0.34f;
        };
        SoundEvent sound = stage == 1 ? SoundEvents.EVOKER_CAST_SPELL : SoundEvents.ILLUSIONER_CAST_SPELL;
        playNearOwner(player, position, sound, volume, pitch);
    }

    public static void playStorageComplete(ServerPlayer player, Vec3 position) {
        playNearOwner(player, position, SoundEvents.ENDERMAN_TELEPORT, 0.30f, 1.42f);
        playNearOwner(player, position, SoundEvents.AMETHYST_BLOCK_CHIME, 0.30f, 1.55f);
    }

    private static void playNearOwner(ServerPlayer player, Vec3 position, SoundEvent sound, float volume, float pitch) {
        if (player == null || position == null) {
            return;
        }
        volume = scaleVolume(volume);
        if (volume <= 0.0f) {
            return;
        }
        Vec3 soundPos = position;
        Vec3 delta = position.subtract(player.position());
        if (delta.lengthSqr() > 24.0 * 24.0) {
            soundPos = player.position().add(delta.normalize().scale(8.0));
        }
        player.level().playSound(null, soundPos.x, soundPos.y, soundPos.z, sound, SoundSource.NEUTRAL, volume, pitch);
    }

    private static float scaleVolume(float volume) {
        return volume;
    }
}
