package com.kuzhi.findme.api;

import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.PackAnimationPresetCategory;
import com.kuzhi.findme.common.PackEntityBindingRequirement;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.lifecycle.CompanionBindingService;
import com.kuzhi.findme.server.lifecycle.CompanionArrivalSequenceService;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.lifecycle.CompanionPreSpawnPresentationService;
import com.kuzhi.findme.server.lifecycle.CompanionTacticalOrderService;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.server.profile.CompanionOwnershipGuardService;
import com.kuzhi.findme.server.profile.PackAnimationPresetService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/** Stable integration surface for addons that hand creatures into FindMe. */
public final class FindMeApi {
    private static final List<MovementProfileProvider> MOVEMENT_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<ServerMovementProfileProvider> SERVER_MOVEMENT_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<OwnershipProvider> OWNERSHIP_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<MountRideProvider> MOUNT_RIDE_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<StoragePresentationProvider> STORAGE_PRESENTATION_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<FindMeTemporaryActionController> TEMPORARY_ACTION_CONTROLLERS = new CopyOnWriteArrayList<>();

    private FindMeApi() {
    }

    public static void registerMovementProfileProvider(MovementProfileProvider provider) {
        if (provider != null && !MOVEMENT_PROVIDERS.contains(provider)) {
            MOVEMENT_PROVIDERS.add(provider);
        }
    }

    public static void registerServerMovementProfileProvider(ServerMovementProfileProvider provider) {
        if (provider != null && !SERVER_MOVEMENT_PROVIDERS.contains(provider)) {
            SERVER_MOVEMENT_PROVIDERS.add(provider);
        }
    }

    public static void registerOwnershipProvider(OwnershipProvider provider) {
        if (provider != null && !OWNERSHIP_PROVIDERS.contains(provider)) {
            OWNERSHIP_PROVIDERS.add(provider);
        }
    }

    public static void registerMountRideProvider(MountRideProvider provider) {
        if (provider != null && !MOUNT_RIDE_PROVIDERS.contains(provider)) {
            MOUNT_RIDE_PROVIDERS.add(provider);
        }
    }

    public static void registerStoragePresentationProvider(StoragePresentationProvider provider) {
        if (provider != null && !STORAGE_PRESENTATION_PROVIDERS.contains(provider)) {
            STORAGE_PRESENTATION_PROVIDERS.add(provider);
        }
    }

    public static void registerTemporaryActionController(FindMeTemporaryActionController controller) {
        if (controller != null && !TEMPORARY_ACTION_CONTROLLERS.contains(controller)) {
            TEMPORARY_ACTION_CONTROLLERS.add(controller);
        }
    }

    public static void tickTemporaryActions(MinecraftServer server) {
        for (FindMeTemporaryActionController controller : TEMPORARY_ACTION_CONTROLLERS) {
            controller.tick(server);
            controller.cleanupTemporaryPerformers(server);
        }
    }

    public static int cancelTemporaryActionsForCompanion(MinecraftServer server, UUID companionUuid, String reason) {
        int cancelled = 0;
        for (FindMeTemporaryActionController controller : TEMPORARY_ACTION_CONTROLLERS) {
            cancelled += controller.cancelForCompanion(server, companionUuid, reason);
        }
        return cancelled;
    }

    public static int cancelTemporaryActionsForPlayer(MinecraftServer server, UUID playerUuid, String reason) {
        int cancelled = 0;
        for (FindMeTemporaryActionController controller : TEMPORARY_ACTION_CONTROLLERS) {
            cancelled += controller.cancelForPlayer(server, playerUuid, reason);
        }
        return cancelled;
    }

    public static int cancelAllTemporaryActions(MinecraftServer server, String reason) {
        int cancelled = 0;
        for (FindMeTemporaryActionController controller : TEMPORARY_ACTION_CONTROLLERS) {
            cancelled += controller.cancelAll(server, reason);
        }
        return cancelled;
    }

    public static boolean isTemporaryActionPerformer(Entity entity) {
        if (entity == null) return false;
        for (FindMeTemporaryActionController controller : TEMPORARY_ACTION_CONTROLLERS) {
            if (controller.isTemporaryPerformer(entity)) return true;
        }
        return false;
    }

    public static Optional<CompanionMoveType> externalMovement(LivingEntity entity) {
        if (entity == null) return Optional.empty();
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return externalMovement(type, entity);
    }

    /** Resolves an optional movement override when only the saved entity type is available. */
    public static Optional<CompanionMoveType> externalMovement(ResourceLocation type) {
        return externalMovement(type, null);
    }

    public static Optional<CompanionMoveType> externalMovement(MinecraftServer server, ResourceLocation type) {
        if (server != null && type != null) {
            for (ServerMovementProfileProvider provider : SERVER_MOVEMENT_PROVIDERS) {
                try {
                    Optional<CompanionMoveType> resolved = provider.resolve(server, type);
                    if (resolved != null && resolved.isPresent()) return resolved;
                } catch (RuntimeException ignored) {
                    // A broken optional provider must not prevent FindMe from using its own profile.
                }
            }
        }
        return externalMovement(type);
    }

    private static Optional<CompanionMoveType> externalMovement(ResourceLocation type, LivingEntity entity) {
        if (type == null) return Optional.empty();
        for (MovementProfileProvider provider : MOVEMENT_PROVIDERS) {
            try {
                Optional<CompanionMoveType> resolved = provider.resolve(type, entity);
                if (resolved != null && resolved.isPresent()) return resolved;
            } catch (RuntimeException ignored) {
                // A broken optional provider must not prevent FindMe from using its own profile.
            }
        }
        return Optional.empty();
    }

    public static Optional<UUID> externalOwner(LivingEntity entity) {
        if (entity == null) return Optional.empty();
        for (OwnershipProvider provider : OWNERSHIP_PROVIDERS) {
            try {
                Optional<UUID> resolved = provider.resolveOwner(entity);
                if (resolved != null && resolved.isPresent()) return resolved;
            } catch (RuntimeException ignored) {
                // A broken optional provider must not broaden binding permission.
            }
        }
        return Optional.empty();
    }

    public static CompanionMoveType resolveMovement(LivingEntity entity, CompanionKind kind) {
        return CompanionEntityClassifier.moveType(entity, kind);
    }

    public static boolean isRegistered(ServerPlayer player, LivingEntity entity) {
        return player != null && entity != null && CompanionDataService.data(player).contains(entity.getUUID());
    }

    public static boolean isRegistered(ServerPlayer player, LivingEntity entity, CompanionKind kind) {
        return player != null && entity != null && kind != null
                && CompanionDataService.data(player).contains(kind, entity.getUUID());
    }

    /** Returns whether FindMe has temporarily isolated this entity as a home resident. */
    public static boolean isHomeResident(LivingEntity entity) {
        return CompanionHomeResidentService.isHomeResident(entity);
    }

    /** True while FindMe owns a stationary home or guard post for this entity. */
    public static boolean isFixedPost(LivingEntity entity) {
        return entity != null && (CompanionHomeResidentService.isHomeResident(entity)
                || CompanionTacticalOrderService.isGuardingPost(entity.getUUID()));
    }

    /**
     * True while external AI must yield movement and target ownership to FindMe.
     * This is derived from FindMe's authoritative runtime state and requires no addon-side lease cleanup.
     */
    public static boolean isMovementControlled(LivingEntity entity) {
        if (entity == null) return false;
        UUID uuid = entity.getUUID();
        return isFixedPost(entity)
                || CompanionTacticalOrderService.controls(uuid)
                || CompanionOperationLockService.get(uuid) != null
                || CompanionPreSpawnPresentationService.isPending(uuid)
                || CompanionStorageService.isStoragePending(entity)
                || CompanionArrivalSequenceService.isPending(entity);
    }

    /** Exact target currently owned by a FindMe tactical order. */
    public static Optional<UUID> tacticalTargetUuid(LivingEntity entity) {
        return entity == null ? Optional.empty()
                : CompanionTacticalOrderService.currentTargetUuid(entity.getUUID());
    }

    public static Optional<UUID> registeredOwnerUuid(MinecraftServer server, LivingEntity entity) {
        if (server == null || entity == null) return Optional.empty();
        return CompanionOwnershipGuardService.registeredOwner(server, entity.getUUID())
                .map(ServerPlayer::getUUID);
    }

    public static EntityProfile entityProfile(LivingEntity entity) {
        if (entity == null) {
            return new EntityProfile(PackAnimationPresetCategory.DISABLED, CompanionMoveType.WALK,
                    PackEntityBindingRequirement.AUTO);
        }
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        PackAnimationPresetCategory category = PackAnimationPresetService.effectiveCategory(type.toString());
        CompanionKind kind = category == PackAnimationPresetCategory.MOUNT
                ? CompanionKind.MOUNT : CompanionKind.COMPANION;
        return new EntityProfile(category, CompanionEntityClassifier.moveType(entity, kind),
                PackAnimationPresetService.bindingRequirement(type.toString()));
    }

    public static EntityProfile entityProfile(ResourceLocation entityType) {
        if (entityType == null) {
            return new EntityProfile(PackAnimationPresetCategory.DISABLED, CompanionMoveType.WALK,
                    PackEntityBindingRequirement.AUTO);
        }
        PackAnimationPresetCategory category = PackAnimationPresetService.effectiveCategory(entityType.toString());
        CompanionKind kind = category == PackAnimationPresetCategory.MOUNT
                ? CompanionKind.MOUNT : CompanionKind.COMPANION;
        return new EntityProfile(category, CompanionEntityClassifier.moveType(entityType.toString(), kind),
                PackAnimationPresetService.bindingRequirement(entityType.toString()));
    }

    public static boolean canForceMount(ServerPlayer player, LivingEntity entity) {
        if (player == null || entity == null || !entity.isAlive() || entity.level() != player.level()
                || !isRegistered(player, entity) || isOwnedByAnotherPlayer(player, entity)) {
            return false;
        }
        for (MountRideProvider provider : MOUNT_RIDE_PROVIDERS) {
            try {
                if (provider.canForceMount(player, entity)) return true;
            } catch (RuntimeException ignored) {
                // Optional integrations fail closed so they cannot broaden riding permission.
            }
        }
        return false;
    }

    /**
     * True only after FindMe has committed this mount as deployed and no summon/storage transaction
     * still owns it. Addons may use this to start their normal riding controller after handoff.
     */
    public static boolean isRideControlReady(ServerPlayer player, LivingEntity entity) {
        if (player == null || entity == null || !entity.isAlive() || entity.level() != player.level()
                || !isRegistered(player, entity, CompanionKind.MOUNT)) {
            return false;
        }
        var data = CompanionDataService.data(player);
        return data.lifecycleState(entity.getUUID()) == com.kuzhi.findme.common.CompanionLifecycleState.DEPLOYED
                && CompanionOperationLockService.get(entity.getUUID()) == null
                && !CompanionStorageService.isStoragePending(entity)
                && !CompanionArrivalSequenceService.isPending(entity);
    }

    public static boolean hasExternalStoragePresentation(LivingEntity entity) {
        if (entity == null) return false;
        for (StoragePresentationProvider provider : STORAGE_PRESENTATION_PROVIDERS) {
            try {
                if (provider.supports(entity)) return true;
            } catch (RuntimeException ignored) {
                // Fall back to FindMe's presentation when an optional provider fails.
            }
        }
        return false;
    }

    public static boolean beginExternalStoragePresentation(MinecraftServer server, UUID ownerUuid,
                                                           LivingEntity entity, int durationTicks) {
        if (server == null || entity == null) return false;
        for (StoragePresentationProvider provider : STORAGE_PRESENTATION_PROVIDERS) {
            try {
                if (!provider.supports(entity)) continue;
                provider.begin(server, ownerUuid, entity, durationTicks);
                return true;
            } catch (RuntimeException ignored) {
                // Try the next provider, then fall back to FindMe's native effect.
            }
        }
        return false;
    }

    public static boolean isOwnedByAnotherPlayer(ServerPlayer player, LivingEntity entity) {
        if (player == null || entity == null) return true;
        if (CompanionOwnershipGuardService.registeredOwner(player.getServer(), entity.getUUID())
                .filter(owner -> !owner.getUUID().equals(player.getUUID())).isPresent()) return true;
        return CompanionEntityClassifier.hasOwnershipSignal(entity)
                && !CompanionEntityClassifier.isOwnedBy(player, entity);
    }

    public static boolean bindAndStore(ServerPlayer player, LivingEntity entity, CompanionKind kind) {
        if (player == null || entity == null || kind == null) {
            FindMeDebugLogger.info("api", "bindAndStore rejected reason=invalid_arguments");
            return false;
        }
        if (entity == player || !entity.isAlive()) {
            FindMeDebugLogger.info("api", "bindAndStore rejected player={} entity={} reason=invalid_entity_state",
                    player.getUUID(), entity.getUUID());
            return false;
        }
        if (entity.level() != player.level()) {
            FindMeDebugLogger.info("api", "bindAndStore rejected player={} entity={} reason=wrong_level",
                    player.getUUID(), entity.getUUID());
            return false;
        }
        if (isRegistered(player, entity)) {
            FindMeDebugLogger.info("api", "bindAndStore rejected player={} entity={} reason=already_registered",
                    player.getUUID(), entity.getUUID());
            return false;
        }
        if (isOwnedByAnotherPlayer(player, entity)) {
            FindMeDebugLogger.info("api", "bindAndStore rejected player={} entity={} reason=owned_by_another",
                    player.getUUID(), entity.getUUID());
            return false;
        }
        if (entity instanceof TamableAnimal tamable) {
            tamable.tame(player);
            player.serverLevel().broadcastEntityEvent(tamable, (byte) 7);
        } else if (entity instanceof AbstractHorse horse) {
            horse.tameWithName(player);
        }
        boolean bound = CompanionBindingService.forceBindAndStore(player, entity, kind);
        if (!bound) {
            FindMeDebugLogger.info("api", "bindAndStore rejected player={} entity={} kind={} reason=registration_or_storage_failed",
                    player.getUUID(), entity.getUUID(), kind);
        }
        return bound;
    }

    @FunctionalInterface
    public interface MovementProfileProvider {
        Optional<CompanionMoveType> resolve(ResourceLocation entityType, LivingEntity entity);
    }

    @FunctionalInterface
    public interface ServerMovementProfileProvider {
        Optional<CompanionMoveType> resolve(MinecraftServer server, ResourceLocation entityType);
    }

    @FunctionalInterface
    public interface OwnershipProvider {
        Optional<UUID> resolveOwner(LivingEntity entity);
    }

    @FunctionalInterface
    public interface MountRideProvider {
        boolean canForceMount(ServerPlayer player, LivingEntity entity);
    }

    public interface StoragePresentationProvider {
        boolean supports(LivingEntity entity);

        void begin(MinecraftServer server, UUID ownerUuid, LivingEntity entity, int durationTicks);
    }

    public record EntityProfile(PackAnimationPresetCategory category, CompanionMoveType movement,
                                PackEntityBindingRequirement bindingRequirement) {
        public boolean isMount() {
            return category == PackAnimationPresetCategory.MOUNT;
        }

        public Optional<CompanionKind> companionKind() {
            return switch (category) {
                case MOUNT -> Optional.of(CompanionKind.MOUNT);
                case COMPANION -> Optional.of(CompanionKind.COMPANION);
                case ALL, VEHICLE, DISABLED -> Optional.empty();
            };
        }
    }
}
