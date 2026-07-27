package com.kuzhi.findme.server.profile;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.api.FindMeApi;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.PackEntityCategoryOverride;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;

public final class CompanionEntityClassifier {
    private static final Map<Class<?>, List<OwnershipMethod>> OWNERSHIP_METHODS = new HashMap<>();

    private CompanionEntityClassifier() {
    }

    public static Optional<CompanionKind> classify(ServerPlayer player, Entity entity) {
        ResourceLocation id = EntityType.getKey(entity.getType());
        String key = id.toString();
        Optional<PackEntityCategoryOverride> packCategory = PackAnimationPresetService.categoryOverride(key);
        if (packCategory.isPresent() && (packCategory.get() == PackEntityCategoryOverride.DISABLED || packCategory.get() == PackEntityCategoryOverride.VEHICLE)) {
            return Optional.empty();
        }
        if (isKnownRideableMountKey(key)) {
            return Optional.of(CompanionKind.MOUNT);
        }
        if (packCategory.isEmpty() && PackAnimationPresetService.movementOverride(key).isPresent()) {
            return Optional.of(CompanionKind.MOUNT);
        }
        if (!isOwnedBy(player, entity)) {
            return Optional.empty();
        }
        if (packCategory.isPresent()) {
            return switch (packCategory.get()) {
                case MOUNT -> Optional.of(CompanionKind.MOUNT);
                case COMPANION -> Optional.of(CompanionKind.COMPANION);
                case AUTO, VEHICLE, DISABLED -> Optional.empty();
            };
        }
        if (entity instanceof AbstractHorse || isKnownRideableMountKey(key)) {
            return Optional.of(CompanionKind.MOUNT);
        }
        return Optional.of(CompanionKind.COMPANION);
    }

    public static boolean isManualBindingMountKey(String key) {
        if (key == null) {
            return false;
        }
        Optional<PackEntityCategoryOverride> packCategory = PackAnimationPresetService.categoryOverride(key);
        if (packCategory.isPresent()) {
            return packCategory.get() == PackEntityCategoryOverride.MOUNT;
        }
        if (PackAnimationPresetService.movementOverride(key).isPresent()) {
            return true;
        }
        return isExplicitCompatibleMountKey(key);
    }

    public static boolean isLikelyMountKey(String key) {
        return key != null && isKnownRideableMountKey(key);
    }

    public static boolean isIceAndFireDragonKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.equals("iceandfire:fire_dragon") || normalized.equals("iceandfire:ice_dragon") || normalized.equals("iceandfire:lightning_dragon");
    }

    public static CompanionMoveType moveType(Entity entity, CompanionKind kind) {
        if (kind == CompanionKind.COMPANION) {
            return CompanionMoveType.COMPANION;
        }
        if (entity == null) {
            return CompanionMoveType.WALK;
        }
        if (entity instanceof LivingEntity living) {
            Optional<CompanionMoveType> external = FindMeApi.externalMovement(
                    living.level().getServer(), EntityType.getKey(entity.getType()));
            if (external.isPresent()) return external.get();
        }
        String key = EntityType.getKey(entity.getType()).toString();
        Optional<CompanionMoveType> configured = configuredMoveType(key);
        if (configured.isPresent()) {
            return configured.get();
        }
        if (isExplicitWalkingMoveKey(key)) {
            return CompanionMoveType.WALK;
        }
        if (isKnownSwimmingMountKey(key) || entity instanceof WaterAnimal || entity.getType().getCategory() == MobCategory.WATER_CREATURE) {
            return CompanionMoveType.SWIM;
        }
        if (isAutoFlying(entity, key, false)) {
            return CompanionMoveType.FLY;
        }
        return CompanionMoveType.WALK;
    }

    public static CompanionMoveType summonMoveType(Entity entity, CompanionKind kind) {
        if (kind == CompanionKind.MOUNT) {
            return moveType(entity, kind);
        }
        String key = EntityType.getKey(entity.getType()).toString();
        if (entity instanceof LivingEntity living) {
            Optional<CompanionMoveType> external = FindMeApi.externalMovement(living);
            if (external.isPresent()) return external.get();
        }
        Optional<CompanionMoveType> configured = configuredMoveType(key);
        if (configured.isPresent()) {
            return configured.get();
        }
        if (isExplicitWalkingMoveKey(key)) {
            return CompanionMoveType.WALK;
        }
        if (isKnownSwimmingMountKey(key) || entity instanceof WaterAnimal || entity.getType().getCategory() == MobCategory.WATER_CREATURE) {
            return CompanionMoveType.SWIM;
        }
        if (isAutoFlying(entity, key, true)) {
            return CompanionMoveType.FLY;
        }
        return CompanionMoveType.WALK;
    }

    public static CompanionMoveType summonMoveType(String entityType, CompanionKind kind) {
        return summonMoveType(null, entityType, kind);
    }

    public static CompanionMoveType summonMoveType(net.minecraft.server.MinecraftServer server,
                                                   String entityType, CompanionKind kind) {
        if (kind == CompanionKind.MOUNT) {
            return moveType(server, entityType, kind);
        }
        if (entityType == null || entityType.isBlank()) {
            return CompanionMoveType.WALK;
        }
        Optional<CompanionMoveType> configured = configuredMoveType(entityType);
        if (configured.isPresent()) {
            return configured.get();
        }
        if (isExplicitWalkingMoveKey(entityType)) {
            return CompanionMoveType.WALK;
        }
        if (isKnownSwimmingMountKey(entityType)) {
            return CompanionMoveType.SWIM;
        }
        if (isKnownFlyingMountKey(entityType) || isKnownFlyingCompanionKey(entityType)) {
            return CompanionMoveType.FLY;
        }
        return CompanionMoveType.WALK;
    }

    public static CompanionMoveType moveType(String entityType, CompanionKind kind) {
        return moveType(null, entityType, kind);
    }

    public static CompanionMoveType moveType(net.minecraft.server.MinecraftServer server,
                                             String entityType, CompanionKind kind) {
        if (kind == CompanionKind.COMPANION) {
            return CompanionMoveType.COMPANION;
        }
        if (entityType == null || entityType.isBlank()) {
            return CompanionMoveType.WALK;
        }
        ResourceLocation externalType = ResourceLocation.tryParse(entityType);
        if (externalType != null) {
            Optional<CompanionMoveType> external = FindMeApi.externalMovement(server, externalType);
            if (external.isPresent()) return external.get();
        }
        Optional<CompanionMoveType> configured = configuredMoveType(entityType);
        if (configured.isPresent()) {
            return configured.get();
        }
        if (isExplicitWalkingMoveKey(entityType)) {
            return CompanionMoveType.WALK;
        }
        if (isKnownSwimmingMountKey(entityType)) {
            return CompanionMoveType.SWIM;
        }
        if (isKnownFlyingMountKey(entityType)) {
            return CompanionMoveType.FLY;
        }
        return CompanionMoveType.WALK;
    }

    private static boolean isKnownRideableMountKey(String key) {
        if (isExplicitCompatibleMountKey(key)) {
            return true;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("dragon") || normalized.contains("horse") || normalized.contains("donkey") || normalized.contains("mule") || normalized.contains("llama") || normalized.contains("camel") || normalized.contains("pig") || normalized.contains("strider") || normalized.contains("ravager") || normalized.contains("wyrm") || normalized.contains("wyvern") || normalized.contains("drake") || normalized.contains("griffin") || normalized.contains("gryphon") || normalized.contains("hippogryph") || normalized.contains("hippocampus") || normalized.contains("pegasus") || normalized.contains("manticore") || normalized.contains("cockatrice") || normalized.contains("maid");
    }

    private static boolean isIceAndFireMountKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("iceandfire:")) {
            return false;
        }
        String path = normalized.substring("iceandfire:".length());
        return path.equals("fire_dragon") || path.equals("ice_dragon") || path.equals("lightning_dragon") || path.equals("hippogryph") || path.equals("hippocampus") || path.equals("amphithere") || path.equals("deathworm") || path.equals("cockatrice") || path.equals("dread_horse");
    }

    public static boolean isDragonMountsLegacyKey(String key) {
        return key.toLowerCase(Locale.ROOT).equals("dragonmounts:dragon");
    }

    private static boolean isErsMountKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("ers:")) {
            return false;
        }
        String path = normalized.substring("ers:".length());
        return path.equals("dentisaurus_longirostris") || path.equals("terridensaurus_saevus");
    }

    private static boolean isSaintsDragonsMountKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("saintsdragons:")) {
            return false;
        }
        String path = normalized.substring("saintsdragons:".length());
        return path.equals("raevyx") || path.equals("stegonaut") || path.equals("cindervane") || path.equals("varasuchus") || path.equals("ignivorus") || path.equals("volitans") || path.equals("nulljaw");
    }

    private static boolean isExplicitCompatibleMountKey(String key) {
        return isIceAndFireMountKey(key) || isErsMountKey(key) || isSaintsDragonsMountKey(key);
    }

    private static boolean isKnownFlyingCompanionKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.equals("minecraft:parrot") || normalized.equals("minecraft:allay") || normalized.equals("minecraft:bat") || normalized.contains("bird") || normalized.contains("parrot") || normalized.contains("raven") || normalized.contains("crow") || normalized.contains("owl") || normalized.contains("eagle") || normalized.contains("falcon");
    }

    private static boolean isKnownFlyingMountKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.contains("happy_ghast")) {
            return true;
        }
        if (normalized.startsWith("ers:")) {
            return false;
        }
        if (normalized.startsWith("iceandfire:")) {
            String path = normalized.substring("iceandfire:".length());
            return path.equals("fire_dragon") || path.equals("ice_dragon") || path.equals("lightning_dragon") || path.equals("hippogryph") || path.equals("amphithere");
        }
        if (normalized.startsWith("saintsdragons:")) {
            String path = normalized.substring("saintsdragons:".length());
            return path.equals("raevyx") || path.equals("cindervane") || path.equals("ignivorus") || path.equals("volitans") || path.equals("nulljaw");
        }
        return normalized.contains("dragon") || normalized.contains("wyvern") || normalized.contains("griffin") || normalized.contains("gryphon") || normalized.contains("hippogryph") || normalized.contains("pegasus");
    }

    private static boolean isKnownSwimmingMountKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.equals("iceandfire:hippocampus") || normalized.equals("ers:dentisaurus_longirostris") || normalized.equals("saintsdragons:varasuchus");
    }

    private static boolean isExplicitWalkingMoveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("roost2:")) {
            String path = normalized.substring("roost2:".length());
            return path.equals("roost") || path.equals("roost_rider");
        }
        return normalized.equals("roost2:roost") || normalized.equals("roost2:roost_rider");
    }

    private static boolean isAutoFlying(Entity entity, String key, boolean includeCompanionKeys) {
        if (entity == null) {
            return includeCompanionKeys ? isKnownFlyingMountKey(key) || isKnownFlyingCompanionKey(key) : isKnownFlyingMountKey(key);
        }
        int score = flyingScore(entity, key, includeCompanionKeys);
        boolean flying = score >= 4;
        if (Config.enableDiagnosticLogging && flying) {
            FindMeMod.LOGGER.info("[FindMe] Auto movement classified {} as flying, score={}", key, score);
        }
        return flying;
    }

    private static int flyingScore(Entity entity, String key, boolean includeCompanionKeys) {
        int score = 0;
        if (isKnownFlyingMountKey(key)) {
            score += 5;
        }
        if (includeCompanionKeys && isKnownFlyingCompanionKey(key)) {
            score += 4;
        }
        if (entity instanceof FlyingMob) {
            score += 5;
        }
        if (entity instanceof FlyingAnimal) {
            score += 4;
        }
        if (entity instanceof Mob mob) {
            if (mob.getNavigation() instanceof FlyingPathNavigation) {
                score += 5;
            }
            if (mob.getMoveControl() instanceof FlyingMoveControl) {
                score += 5;
            }
        }
        if (hasFlyingSpeedAttribute(entity)) {
            score += 2;
        }
        if (entity.isNoGravity()) {
            score += 1;
        }
        score += flyingNameScore(key, entity.getClass());
        return score;
    }

    private static boolean hasFlyingSpeedAttribute(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        try {
            return living.getAttribute(Attributes.FLYING_SPEED) != null && living.getAttributeValue(Attributes.FLYING_SPEED) > 0.0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static int flyingNameScore(String key, Class<?> type) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        String className = type == null ? "" : type.getName().toLowerCase(Locale.ROOT);
        String combined = normalized + " " + className;
        if (containsAny(combined, "flying", "flyer", "aerial", "airborne", "winged")) {
            return 2;
        }
        if (containsAny(combined, "dragon", "wyvern", "griffin", "gryphon", "hippogryph", "pegasus", "phantom", "ghast")) {
            return 2;
        }
        if (containsAny(combined, "bird", "parrot", "raven", "crow", "owl", "eagle", "falcon", "bat", "allay")) {
            return 1;
        }
        return 0;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<CompanionMoveType> configuredMoveType(String entityType) {
        Optional<CompanionMoveType> packMovement = PackAnimationPresetService.movementOverride(entityType);
        if (packMovement.isPresent()) {
            return packMovement;
        }
        return Optional.empty();
    }

    public static boolean isOwnedBy(ServerPlayer player, Entity entity) {
        if (entity instanceof LivingEntity living
                && FindMeApi.externalOwner(living).filter(player.getUUID()::equals).isPresent()) {
            return true;
        }
        if (entity instanceof OwnableEntity ownable) {
            return player.getUUID().equals(ownable.getOwnerUUID());
        }
        if (entity instanceof TamableAnimal tamable) {
            return tamable.isTame() && player.getUUID().equals(tamable.getOwnerUUID());
        }
        if (entity instanceof AbstractHorse horse) {
            return horse.isTamed() && player.getUUID().equals(horse.getOwnerUUID());
        }
        return isOwnedByReflectedMethod(player, entity) || isOwnedBySavedTag(player, entity);
    }

    public static boolean hasOwnershipSignal(Entity entity) {
        if (entity instanceof LivingEntity living && FindMeApi.externalOwner(living).isPresent()) {
            return true;
        }
        if (entity instanceof OwnableEntity ownable) {
            return ownable.getOwnerUUID() != null;
        }
        if (entity instanceof TamableAnimal tamable) {
            return tamable.isTame() && tamable.getOwnerUUID() != null;
        }
        if (entity instanceof AbstractHorse horse) {
            return horse.isTamed() && horse.getOwnerUUID() != null;
        }
        if (!OWNERSHIP_METHODS.computeIfAbsent(entity.getClass(), CompanionEntityClassifier::findOwnershipMethods).isEmpty()) {
            return true;
        }
        ResourceLocation entityKey = EntityType.getKey(entity.getType());
        if ("minecraft".equals(entityKey.getNamespace())) {
            return false;
        }
        CompoundTag tag = new CompoundTag();
        try {
            entity.saveWithoutId(tag);
        } catch (RuntimeException ignored) {
            return false;
        }
        String[] uuidKeys = {"Owner", "OwnerUUID", "owner", "ownerUUID", "TameOwner", "TameOwnerUUID", "MasterUUID", "masterUUID", "MaidMasterUUID"};
        for (String key : uuidKeys) {
            if (tag.hasUUID(key) || !tag.getString(key).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOwnedByReflectedMethod(ServerPlayer player, Entity entity) {
        for (OwnershipMethod ownershipMethod : OWNERSHIP_METHODS.computeIfAbsent(entity.getClass(), CompanionEntityClassifier::findOwnershipMethods)) {
            try {
                Object value = ownershipMethod.method().invoke(entity);
                if (ownerValueMatches(player, value)) {
                    return true;
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
            }
        }
        return false;
    }

    private static List<OwnershipMethod> findOwnershipMethods(Class<?> type) {
        ArrayList<OwnershipMethod> methods = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!name.equals("getowneruuid") && !name.equals("getownerid") && !name.equals("getowner") && !name.equals("gettameowneruuid") && !name.equals("getmasteruuid") && !name.equals("getmaster") && !name.equals("getmaidmasteruuid")) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (!UUID.class.isAssignableFrom(returnType) && !Entity.class.isAssignableFrom(returnType) && !Player.class.isAssignableFrom(returnType) && !String.class.isAssignableFrom(returnType)) {
                continue;
            }
            methods.add(new OwnershipMethod(method));
        }
        return List.copyOf(methods);
    }

    private static boolean ownerValueMatches(ServerPlayer player, Object value) {
        if (value instanceof UUID uuid) {
            return player.getUUID().equals(uuid);
        }
        if (value instanceof Entity entity) {
            return player.getUUID().equals(entity.getUUID());
        }
        if (value instanceof String text) {
            try {
                return player.getUUID().equals(UUID.fromString(text));
            } catch (IllegalArgumentException ignored) {
                return player.getScoreboardName().equals(text) || player.getGameProfile().getName().equals(text);
            }
        }
        return false;
    }

    private static boolean isOwnedBySavedTag(ServerPlayer player, Entity entity) {
        ResourceLocation entityKey = EntityType.getKey(entity.getType());
        if ("minecraft".equals(entityKey.getNamespace())) {
            return false;
        }
        CompoundTag tag = new CompoundTag();
        try {
            entity.saveWithoutId(tag);
        } catch (RuntimeException ignored) {
            return false;
        }
        String[] uuidKeys = {"Owner", "OwnerUUID", "owner", "ownerUUID", "TameOwner", "TameOwnerUUID", "MasterUUID", "masterUUID", "MaidMasterUUID"};
        for (String key : uuidKeys) {
            if (tag.hasUUID(key) && player.getUUID().equals(tag.getUUID(key))) {
                return true;
            }
            String value = tag.getString(key);
            if (!value.isBlank() && ownerValueMatches(player, value)) {
                return true;
            }
        }
        return false;
    }

    private record OwnershipMethod(Method method) {
    }
}
