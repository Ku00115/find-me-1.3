package com.kuzhi.findme.server.profile;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.PackEntityBindingRequirement;
import com.kuzhi.findme.common.PackEntityCategoryOverride;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;

/** Resolves editor-authored binding intent and applies its optional native ownership adapter. */
public final class CompanionBindingProfileService {
    private CompanionBindingProfileService() {
    }

    public static Optional<CompanionKind> manualBindingKind(ServerPlayer player, LivingEntity target) {
        if (!valid(player, target)) return Optional.empty();
        String key = key(target);
        Optional<PackEntityCategoryOverride> category = PackAnimationPresetService.categoryOverride(key);
        if (category.filter(value -> value == PackEntityCategoryOverride.DISABLED
                || value == PackEntityCategoryOverride.VEHICLE).isPresent()) {
            FindMeDebugLogger.info("binding-profile", "name paper rejected entity={} category={}", key,
                    category.orElse(PackEntityCategoryOverride.AUTO));
            return Optional.empty();
        }
        PackEntityBindingRequirement requirement = PackAnimationPresetService.bindingRequirement(key);
        Optional<CompanionKind> configuredKind = configuredKind(key, category);
        if (!bindingAllowed(player, target, requirement)) {
            traceDecision(key, requirement, category, Optional.empty(), "requirement-rejected");
            return Optional.empty();
        }
        Optional<CompanionKind> result = configuredKind.or(() -> Optional.of(
                CompanionEntityClassifier.isManualBindingMountKey(key) ? CompanionKind.MOUNT : CompanionKind.COMPANION));
        traceDecision(key, requirement, category, result, "requirement-accepted");
        return result;
    }

    public static boolean allowsCreativeBinding(LivingEntity target, CompanionKind requestedKind) {
        if (target == null || requestedKind == null) return false;
        String key = key(target);
        Optional<PackEntityCategoryOverride> category = PackAnimationPresetService.categoryOverride(key);
        if (category.filter(value -> value == PackEntityCategoryOverride.DISABLED
                || value == PackEntityCategoryOverride.VEHICLE).isPresent()) return false;
        return configuredKind(key, category).map(kind -> kind == requestedKind).orElse(true);
    }

    public static boolean allowsVehicleBinding(String entityType, boolean creative) {
        if (entityType == null || entityType.isBlank()) return true;
        Optional<PackEntityCategoryOverride> category = PackAnimationPresetService.categoryOverride(entityType);
        boolean allowed = category.map(value -> value == PackEntityCategoryOverride.AUTO
                || value == PackEntityCategoryOverride.VEHICLE).orElse(true);
        FindMeDebugLogger.info("binding-profile", "vehicle binding entity={} category={} creative={} result={}",
                entityType, category.orElse(PackEntityCategoryOverride.AUTO), creative, allowed);
        return allowed;
    }

    public static boolean applyForBinding(ServerPlayer player, LivingEntity target) {
        String key = key(target);
        PackEntityBindingRequirement requirement = PackAnimationPresetService.bindingRequirement(key);
        boolean result = bindingAllowed(player, target, requirement);
        FindMeDebugLogger.info("binding-profile", "apply entity={} requirement={} result={}", key, requirement, result);
        return result;
    }

    public static boolean forceTame(ServerPlayer player, LivingEntity target) {
        if (!valid(player, target)) return false;
        return applyNativeOwner(player, target);
    }

    private static Optional<CompanionKind> configuredKind(String key,
                                                           Optional<PackEntityCategoryOverride> category) {
        if (category.isPresent()) {
            if (category.get() == PackEntityCategoryOverride.MOUNT) return Optional.of(CompanionKind.MOUNT);
            if (category.get() == PackEntityCategoryOverride.COMPANION) return Optional.of(CompanionKind.COMPANION);
        }
        if (PackAnimationPresetService.movementOverride(key).isPresent()) {
            return Optional.of(CompanionKind.MOUNT);
        }
        return Optional.empty();
    }

    private static boolean applyNativeOwner(ServerPlayer player, LivingEntity target) {
        if (target instanceof TamableAnimal tamable) {
            tamable.tame(player);
            player.serverLevel().broadcastEntityEvent(tamable, (byte) 7);
            return true;
        }
        if (target instanceof AbstractHorse horse) {
            return horse.tameWithName(player);
        }
        if (target instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID())) {
            return true;
        }
        return invokeOwnerSetter(player, target);
    }

    private static boolean invokeOwnerSetter(ServerPlayer player, LivingEntity target) {
        String[] names = {"setOwnerUUID", "setOwnerId", "setTameOwnerUUID", "setMasterUUID", "setMaidMasterUUID"};
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name, UUID.class);
                method.invoke(target, player.getUUID());
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        String[] playerNames = {"tame", "setTamedBy", "setOwner", "setMaster"};
        for (String name : playerNames) {
            for (Class<?> parameter : new Class<?>[]{Player.class, ServerPlayer.class}) {
                try {
                    Method method = target.getClass().getMethod(name, parameter);
                    method.invoke(target, player);
                    return true;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
        }
        return false;
    }

    private static boolean bindingAllowed(ServerPlayer player, LivingEntity target,
                                          PackEntityBindingRequirement requirement) {
        return switch (requirement) {
            case IGNORE -> true;
            case OWNER -> CompanionEntityClassifier.isOwnedBy(player, target);
            case TAMED -> isTamed(target);
            case AUTO -> {
                if (CompanionEntityClassifier.isOwnedBy(player, target)) yield true;
                if (CompanionEntityClassifier.hasOwnershipSignal(target)) yield false;
                yield hasTamingCapability(target) && isTamed(target);
            }
        };
    }

    private static boolean hasTamingCapability(LivingEntity target) {
        if (target instanceof TamableAnimal || target instanceof AbstractHorse || target instanceof OwnableEntity) {
            return true;
        }
        for (String name : new String[]{"isTame", "isTamed", "isDomesticated"}) {
            try {
                Method method = target.getClass().getMethod(name);
                if (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class) return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return false;
    }

    private static boolean isTamed(LivingEntity target) {
        if (target instanceof TamableAnimal tamable) return tamable.isTame();
        if (target instanceof AbstractHorse horse) return horse.isTamed();
        for (String name : new String[]{"isTame", "isTamed", "isDomesticated"}) {
            try {
                Method method = target.getClass().getMethod(name);
                Object value = method.invoke(target);
                if (value instanceof Boolean tamed) return tamed;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        CompoundTag tag = new CompoundTag();
        try {
            target.saveWithoutId(tag);
        } catch (RuntimeException ignored) {
            return false;
        }
        for (String key : new String[]{"Tame", "Tamed", "tame", "tamed", "Domesticated"}) {
            if (tag.contains(key) && tag.getBoolean(key)) return true;
        }
        return false;
    }

    private static boolean valid(ServerPlayer player, LivingEntity target) {
        return player != null && target != null && target != player && target.isAlive() && player.level() == target.level();
    }

    private static String key(LivingEntity target) {
        return EntityType.getKey(target.getType()).toString();
    }

    private static void traceDecision(String key, PackEntityBindingRequirement requirement,
                                       Optional<PackEntityCategoryOverride> category,
                                       Optional<CompanionKind> result, String source) {
        FindMeDebugLogger.info("binding-profile",
                "resolve entity={} requirement={} category={} movement={} result={} source={}",
                key, requirement, category.orElse(PackEntityCategoryOverride.AUTO),
                PackAnimationPresetService.movementOverride(key).map(Enum::name).orElse("AUTO"),
                result.map(Enum::name).orElse("REJECT"), source);
    }
}
