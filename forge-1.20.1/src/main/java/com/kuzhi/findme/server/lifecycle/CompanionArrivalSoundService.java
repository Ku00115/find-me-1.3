package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.Config;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.kuzhi.findme.server.profile.PackAnimationPresetService;

public final class CompanionArrivalSoundService {
    private CompanionArrivalSoundService() {
    }

    public static void playCommandVoice(ServerPlayer player, LivingEntity living) {
        if ((player != null
                && !com.kuzhi.findme.server.data.CompanionDataService.data(player).creatureArrivalVoice())
                || living == null || living.isSilent()) {
            return;
        }
        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString();
        SoundEvent voice = PackAnimationPresetService.arrivalSound(entityType)
                .map(BuiltInRegistries.SOUND_EVENT::get)
                .orElse(null);
        boolean specified = voice != null;
        if (voice == null) voice = resolveVoice(living);
        if (voice != null) {
            float volume = (float)Math.max(0.0, Math.min(2.0, Config.creatureArrivalVoiceVolume));
            if (specified) volume *= PackAnimationPresetService.arrivalVolume(entityType);
            if (volume > 0.0f) {
                float pitch = specified ? PackAnimationPresetService.arrivalPitch(entityType) : voicePitch(living);
                playNearPlayer(player, living, voice, volume, pitch);
            }
        }
    }

    private static SoundEvent resolveVoice(LivingEntity living) {
        SoundEvent voice = reflectedEntitySound(living);
        if (voice == null) {
            voice = specificAmbientSound(living);
        }
        if (voice == null) {
            voice = registryAmbientSound(living);
        }
        return voice;
    }

    private static void playNearPlayer(ServerPlayer player, LivingEntity living, SoundEvent sound, float volume, float pitch) {
        Vec3 soundPos = living.position();
        Level soundLevel = living.level();
        if (player != null && player.level() == living.level()) {
            Vec3 direction = living.position().subtract(player.position());
            soundPos = player.position().add(direction.lengthSqr() > 0.001 ? direction.normalize().scale(2.5) : player.getLookAngle().scale(2.5));
        } else if (player != null) {
            soundLevel = player.level();
            soundPos = player.position().add(player.getLookAngle().scale(2.5));
        }
        soundLevel.playSound(null, soundPos.x, soundPos.y, soundPos.z, sound, SoundSource.NEUTRAL, volume, pitch);
    }

    private static SoundEvent reflectedEntitySound(LivingEntity living) {
        Class<?> type = living.getClass();
        while (type != null && LivingEntity.class.isAssignableFrom(type)) {
            SoundEvent sound = reflectedNoArgSound(living, type, "getAmbientSound");
            if (sound != null) {
                return sound;
            }
            sound = reflectedNoArgSound(living, type, "getRoarSound");
            if (sound != null) {
                return sound;
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static SoundEvent reflectedNoArgSound(LivingEntity living, Class<?> type, String methodName) {
        try {
            Method method = type.getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(living);
            return value instanceof SoundEvent sound ? sound : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static SoundEvent registryAmbientSound(LivingEntity living) {
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
        if (typeId == null) {
            return null;
        }
        String namespace = typeId.getNamespace();
        String path = typeId.getPath();
        String compactPath = path.replace("_", "");
        String ageHint = ageHint(living);
        List<ResourceLocation> candidates = List.of(
                id(namespace, "entity." + path + ".ambient"),
                id(namespace, "entity." + path + ".idle"),
                id(namespace, "entity." + path + ".say"),
                id(namespace, "entity." + path + ".roar"),
                id(namespace, path + ".ambient"),
                id(namespace, path + ".idle"),
                id(namespace, path + "_idle"),
                id(namespace, path + "_ambient"),
                id(namespace, path + "_roar"),
                id(namespace, path + "_adult_idle"),
                id(namespace, path + "_teen_idle"),
                id(namespace, path + "_child_idle"),
                id(namespace, compactPath + "_idle"),
                id(namespace, compactPath + "_ambient"),
                id(namespace, compactPath + "_roar"),
                id(namespace, compactPath + "_adult_idle"),
                id(namespace, compactPath + "_teen_idle"),
                id(namespace, compactPath + "_child_idle"),
                id(namespace, "mob." + path + ".say")
        );
        for (ResourceLocation candidate : candidates) {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(candidate);
            if (sound != null) {
                return sound;
            }
        }
        return scanRelatedSound(typeId, ageHint);
    }

    private static ResourceLocation id(String namespace, String path) {
        ResourceLocation location = ResourceLocation.tryParse(namespace + ":" + path);
        return location == null ? ResourceLocation.tryParse("minecraft:empty") : location;
    }

    private static SoundEvent scanRelatedSound(ResourceLocation entityTypeId, String ageHint) {
        String namespace = entityTypeId.getNamespace();
        String entityPath = entityTypeId.getPath();
        String compactEntityPath = compact(entityPath);
        int bestScore = 0;
        SoundEvent best = null;
        for (ResourceLocation soundId : BuiltInRegistries.SOUND_EVENT.keySet()) {
            if (!namespace.equals(soundId.getNamespace())) {
                continue;
            }
            int score = soundScore(soundId.getPath(), entityPath, compactEntityPath, ageHint);
            if (score > bestScore) {
                bestScore = score;
                best = BuiltInRegistries.SOUND_EVENT.get(soundId);
            }
        }
        return bestScore >= 40 ? best : null;
    }

    private static int soundScore(String soundPath, String entityPath, String compactEntityPath, String ageHint) {
        String normalized = soundPath.replace('.', '_').replace('-', '_').toLowerCase();
        String compactSoundPath = compact(normalized);
        int score = 0;
        if (normalized.contains(entityPath)) {
            score += 40;
        }
        if (compactSoundPath.contains(compactEntityPath)) {
            score += 42;
        }
        for (String token : entityPath.split("_")) {
            if (token.length() >= 4 && normalized.contains(token)) {
                score += 8;
            }
        }
        if (normalized.contains("idle")) score += 34;
        if (normalized.contains("ambient")) score += 30;
        if (normalized.contains("say")) score += 22;
        if (normalized.contains("roar")) score += 18;
        if (normalized.contains("growl")) score += 12;
        if (normalized.contains("hurt")) score -= 18;
        if (normalized.contains("death")) score -= 28;
        if (normalized.contains("step")) score -= 20;
        if (ageHint != null && normalized.contains(ageHint)) {
            score += 16;
        } else if (ageHint == null && normalized.contains("adult")) {
            score += 4;
        }
        return score;
    }

    private static String compact(String value) {
        return value.replace("_", "").replace(".", "").replace("-", "").toLowerCase();
    }

    private static String ageHint(LivingEntity living) {
        if (reflectedBoolean(living, "isBaby")) {
            return "child";
        }
        if (reflectedBoolean(living, "isTeen")) {
            return "teen";
        }
        if (reflectedBoolean(living, "isMature")) {
            return "adult";
        }
        return null;
    }

    private static boolean reflectedBoolean(LivingEntity living, String methodName) {
        Class<?> type = living.getClass();
        while (type != null && LivingEntity.class.isAssignableFrom(type)) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(living);
                return value instanceof Boolean result && result;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                type = type.getSuperclass();
            }
        }
        return false;
    }

    private static float voicePitch(LivingEntity living) {
        return living instanceof TamableAnimal ? 1.0f + (living.getRandom().nextFloat() - living.getRandom().nextFloat()) * 0.2f : 1.0f;
    }

    private static SoundEvent specificAmbientSound(LivingEntity living) {
        EntityType<?> type = living.getType();
        if (type == EntityType.WOLF) return SoundEvents.WOLF_AMBIENT;
        if (type == EntityType.CAT) return SoundEvents.CAT_AMBIENT;
        if (type == EntityType.HORSE) return SoundEvents.HORSE_AMBIENT;
        if (type == EntityType.DONKEY) return SoundEvents.DONKEY_AMBIENT;
        if (type == EntityType.MULE) return SoundEvents.MULE_AMBIENT;
        if (type == EntityType.LLAMA || type == EntityType.TRADER_LLAMA) return SoundEvents.LLAMA_AMBIENT;
        if (type == EntityType.CAMEL) return SoundEvents.CAMEL_AMBIENT;
        if (type == EntityType.PIG) return SoundEvents.PIG_AMBIENT;
        if (type == EntityType.STRIDER) return SoundEvents.STRIDER_AMBIENT;
        if (type == EntityType.FOX) return SoundEvents.FOX_AMBIENT;
        if (type == EntityType.PARROT) return SoundEvents.PARROT_AMBIENT;
        if (type == EntityType.FROG) return SoundEvents.FROG_AMBIENT;
        if (type == EntityType.ALLAY) return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
        if (type == EntityType.AXOLOTL) return SoundEvents.AXOLOTL_IDLE_WATER;
        if (type == EntityType.TURTLE) return SoundEvents.TURTLE_AMBIENT_LAND;
        if (type == EntityType.RAVAGER) return SoundEvents.RAVAGER_AMBIENT;
        return null;
    }
}
