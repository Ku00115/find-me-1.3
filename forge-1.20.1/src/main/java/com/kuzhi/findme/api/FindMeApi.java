package com.kuzhi.findme.api;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.FindMeMod;
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
import com.kuzhi.findme.server.ui.CompanionSyncService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

/** Stable integration surface for addons that hand creatures into FindMe. */
public final class FindMeApi {
    private static final List<MovementProfileProvider> MOVEMENT_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<ServerMovementProfileProvider> SERVER_MOVEMENT_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<OwnershipProvider> OWNERSHIP_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<MountRideProvider> MOUNT_RIDE_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<MountedMovementOwner> MOUNTED_MOVEMENT_OWNERS = new CopyOnWriteArrayList<>();
    private static final List<StoragePresentationProvider> STORAGE_PRESENTATION_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<FindMeTemporaryActionController> TEMPORARY_ACTION_CONTROLLERS = new CopyOnWriteArrayList<>();
    private static final List<SpellProviderRegistration> COMPANION_SPELL_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final Set<Class<?>> FAILED_TEMPORARY_CONTROLLERS = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> FAILED_SPELL_PROVIDERS = ConcurrentHashMap.newKeySet();

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

    public static void registerMountedMovementOwner(MountedMovementOwner owner) {
        if (owner != null && !MOUNTED_MOVEMENT_OWNERS.contains(owner)) {
            MOUNTED_MOVEMENT_OWNERS.add(owner);
        }
    }

    /** True while an addon-owned, server-authoritative mounted action must ignore rider vehicle packets. */
    public static boolean ownsExternalMountedMovement(ServerPlayer player) {
        if (player == null || player.getVehicle() == null) return false;
        for (MountedMovementOwner owner : MOUNTED_MOVEMENT_OWNERS) {
            try {
                if (owner.owns(player)) return true;
            } catch (RuntimeException ignored) {
                // Optional movement owners fail closed and cannot capture unrelated riding.
            }
        }
        return false;
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

    public static void registerCompanionSpellProvider(FindMeCompanionSpellProvider provider) {
        if (provider == null) return;
        try {
            ResourceLocation id = provider.id();
            if (id == null) throw new IllegalArgumentException("Companion spell providers require a stable id");
            COMPANION_SPELL_PROVIDERS.removeIf(registration -> registration.id().equals(id));
            COMPANION_SPELL_PROVIDERS.add(new SpellProviderRegistration(id, provider));
        } catch (RuntimeException exception) {
            logSpellProviderFailure(provider, "register", exception);
        }
    }

    public static boolean hasCompanionSpellProviders() {
        return !COMPANION_SPELL_PROVIDERS.isEmpty();
    }

    public static Optional<net.minecraftforge.items.IItemHandlerModifiable> companionSpellItems(
            ServerPlayer owner, UUID companionUuid) {
        if (owner == null || companionUuid == null || !hasCompanionSpellProviders()) return Optional.empty();
        com.kuzhi.findme.server.data.PlayerCompanionData data =
                com.kuzhi.findme.server.data.CompanionDataService.data(owner);
        if (data.kindOf(companionUuid).isEmpty() || data.deadList().contains(companionUuid)
                || data.containsVehicle(companionUuid)) return Optional.empty();
        return Optional.of(new CompanionSpellItemHandler(owner, companionUuid));
    }

    public static Optional<CompanionSpellBinding> createCompanionSpellBinding(ServerPlayer player,
                                                                              UUID companionUuid,
                                                                              ItemStack stack) {
        if (player == null || companionUuid == null || stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        for (SpellProviderRegistration registration : COMPANION_SPELL_PROVIDERS) {
            try {
                FindMeCompanionSpellProvider provider = registration.provider();
                Optional<CompanionSpellBinding> binding = provider.createBinding(player, companionUuid, stack);
                if (binding != null && binding.isPresent()) {
                    if (registration.id().equals(binding.get().providerId())) return binding;
                    logSpellProviderFailure(provider, "createBinding",
                            new IllegalStateException("Binding provider id " + binding.get().providerId()
                                    + " does not match registered id " + registration.id()));
                }
            } catch (RuntimeException exception) {
                logSpellProviderFailure(registration.provider(), "createBinding", exception);
            }
        }
        return Optional.empty();
    }

    public static boolean tryCastCompanionSpell(ServerPlayer owner, LivingEntity companion,
                                                CompanionSpellBinding binding,
                                                CompanionSpellIntent intent, LivingEntity target) {
        if (owner == null || companion == null || binding == null || intent == null || !companion.isAlive()) {
            return false;
        }
        for (SpellProviderRegistration registration : COMPANION_SPELL_PROVIDERS) {
            if (!registration.id().equals(binding.providerId())) continue;
            try {
                return registration.provider().tryCast(owner, companion, binding, intent, target);
            } catch (RuntimeException exception) {
                logSpellProviderFailure(registration.provider(), "tryCast", exception);
                return false;
            }
        }
        return false;
    }

    public static boolean isCastingCompanionSpell(LivingEntity companion) {
        if (companion == null || !companion.isAlive()) return false;
        for (SpellProviderRegistration registration : COMPANION_SPELL_PROVIDERS) {
            try {
                if (registration.provider().isCasting(companion)) return true;
            } catch (RuntimeException exception) {
                logSpellProviderFailure(registration.provider(), "isCasting", exception);
            }
        }
        return false;
    }

    public static CompanionMagicState companionMagicState(ServerPlayer owner, UUID companionUuid,
                                                           LivingEntity companion, CompoundTag storedEntity,
                                                           List<CompanionSpellBinding> bindings) {
        if (owner == null || companionUuid == null || bindings == null || bindings.isEmpty()) {
            return CompanionMagicState.EMPTY;
        }
        for (CompanionSpellBinding binding : bindings) {
            if (binding == null) continue;
            for (SpellProviderRegistration registration : COMPANION_SPELL_PROVIDERS) {
                if (!registration.id().equals(binding.providerId())) continue;
                try {
                    CompanionMagicState state = registration.provider().magicState(owner, companionUuid,
                            companion, storedEntity);
                    if (state != null && state.available()) return state;
                } catch (RuntimeException exception) {
                    logSpellProviderFailure(registration.provider(), "magicState", exception);
                }
            }
        }
        return CompanionMagicState.EMPTY;
    }

    public static void syncCompanionMagicState(ServerPlayer owner) {
        if (owner == null) return;
        CompanionSyncService.syncToClient(owner, CompanionKind.COMPANION);
        CompanionSyncService.syncToClient(owner, CompanionKind.MOUNT);
    }

    /** Returns the owner-wide companion mana pool after applying projected regeneration. */
    public static CompanionMagicState sharedCompanionMagicState(ServerPlayer owner, float regenPerTick) {
        if (owner == null) return CompanionMagicState.EMPTY;
        if (!Float.isFinite(regenPerTick)) regenPerTick = 0.0F;
        var data = CompanionDataService.data(owner);
        int contributors = data.companionMagicContributorCount(Config.companionMagicContributionLimit);
        float maxMana = contributors * 100.0F;
        if (maxMana <= 0.0F) return CompanionMagicState.EMPTY;
        long now = owner.serverLevel().getGameTime();
        SharedManaProjection projection = projectedCompanionMana(data.companionMagicMana(),
                data.companionMagicManaTick(), data.companionMagicCapacity(), maxMana, now,
                sharedCompanionRegenPerTick(contributors, regenPerTick));
        if (projection.capacityChanged()) {
            data.setCompanionMagicMana(projection.mana(), now, maxMana);
            CompanionDataService.save(owner, data);
        }
        return new CompanionMagicState(projection.mana(), maxMana);
    }

    /** Atomically regenerates and spends mana from the owner's shared companion pool. */
    public static boolean tryConsumeSharedCompanionMana(ServerPlayer owner, float amount, float regenPerTick) {
        if (owner == null || !Float.isFinite(amount) || amount < 0.0F
                || !Float.isFinite(regenPerTick)) return false;
        var data = CompanionDataService.data(owner);
        int contributors = data.companionMagicContributorCount(Config.companionMagicContributionLimit);
        float maxMana = contributors * 100.0F;
        if (maxMana <= 0.0F) return false;
        long now = owner.serverLevel().getGameTime();
        SharedManaProjection projection = projectedCompanionMana(data.companionMagicMana(),
                data.companionMagicManaTick(), data.companionMagicCapacity(), maxMana, now,
                sharedCompanionRegenPerTick(contributors, regenPerTick));
        if (projection.mana() + 0.001F < amount) return false;
        data.setCompanionMagicMana(Math.max(0.0F, projection.mana() - amount), now, maxMana);
        CompanionDataService.save(owner, data);
        return true;
    }

    static SharedManaProjection projectedCompanionMana(float storedMana, long storedTick, float storedCapacity,
                                                         float maxMana, long now, float regenPerTick) {
        if (!Float.isFinite(maxMana) || maxMana < 0.0F) maxMana = 0.0F;
        if (!Float.isFinite(regenPerTick) || regenPerTick < 0.0F) regenPerTick = 0.0F;
        if (!Float.isFinite(storedMana) || !Float.isFinite(storedCapacity)
                || storedMana < 0.0F || storedCapacity < 0.0F) {
            return new SharedManaProjection(maxMana, true);
        }
        float oldCapacity = Math.max(0.0F, storedCapacity);
        float mana = Math.min(oldCapacity, storedMana);
        long lastTick = storedTick <= 0L ? now : storedTick;
        long elapsed = Math.max(0L, Math.min(24000L, now - lastTick));
        mana = Math.min(oldCapacity, mana + elapsed * Math.max(0.0F, regenPerTick));
        if (maxMana > oldCapacity) mana += maxMana - oldCapacity;
        return new SharedManaProjection(Math.min(maxMana, mana), maxMana != oldCapacity);
    }

    static float sharedCompanionRegenPerTick(int contributors, float regenPerContributorTick) {
        if (!Float.isFinite(regenPerContributorTick)) return 0.0F;
        return Math.max(0, contributors) * Math.max(0.0F, regenPerContributorTick);
    }

    record SharedManaProjection(float mana, boolean capacityChanged) {
    }

    public static void tickTemporaryActions(MinecraftServer server) {
        for (FindMeTemporaryActionController controller : TEMPORARY_ACTION_CONTROLLERS) {
            try {
                controller.tick(server);
            } catch (RuntimeException exception) {
                logTemporaryControllerFailure(controller, "tick", exception);
            }
            try {
                controller.cleanupTemporaryPerformers(server);
            } catch (RuntimeException exception) {
                logTemporaryControllerFailure(controller, "cleanup", exception);
            }
        }
    }

    public static int cancelTemporaryActionsForCompanion(MinecraftServer server, UUID companionUuid, String reason) {
        int cancelled = 0;
        for (FindMeTemporaryActionController controller : TEMPORARY_ACTION_CONTROLLERS) {
            try {
                cancelled += Math.max(0, controller.cancelForCompanion(server, companionUuid, reason));
            } catch (RuntimeException exception) {
                logTemporaryControllerFailure(controller, "cancelForCompanion", exception);
            }
        }
        return cancelled;
    }

    public static int cancelTemporaryActionsForPlayer(MinecraftServer server, UUID playerUuid, String reason) {
        int cancelled = 0;
        for (FindMeTemporaryActionController controller : TEMPORARY_ACTION_CONTROLLERS) {
            try {
                cancelled += Math.max(0, controller.cancelForPlayer(server, playerUuid, reason));
            } catch (RuntimeException exception) {
                logTemporaryControllerFailure(controller, "cancelForPlayer", exception);
            }
        }
        return cancelled;
    }

    public static int cancelAllTemporaryActions(MinecraftServer server, String reason) {
        int cancelled = 0;
        for (FindMeTemporaryActionController controller : TEMPORARY_ACTION_CONTROLLERS) {
            try {
                cancelled += Math.max(0, controller.cancelAll(server, reason));
            } catch (RuntimeException exception) {
                logTemporaryControllerFailure(controller, "cancelAll", exception);
            }
        }
        return cancelled;
    }

    public static boolean isTemporaryActionPerformer(Entity entity) {
        if (entity == null) return false;
        for (FindMeTemporaryActionController controller : TEMPORARY_ACTION_CONTROLLERS) {
            try {
                if (controller.isTemporaryPerformer(entity)) return true;
            } catch (RuntimeException exception) {
                logTemporaryControllerFailure(controller, "isTemporaryPerformer", exception);
            }
        }
        return false;
    }

    public static boolean ownsTacticalCombat(Entity entity) {
        if (entity == null) return false;
        for (FindMeTemporaryActionController controller : TEMPORARY_ACTION_CONTROLLERS) {
            try {
                if (controller.ownsTacticalCombat(entity)) return true;
            } catch (RuntimeException exception) {
                logTemporaryControllerFailure(controller, "ownsTacticalCombat", exception);
            }
        }
        return false;
    }

    private static void logTemporaryControllerFailure(FindMeTemporaryActionController controller, String operation,
                                                       RuntimeException exception) {
        TEMPORARY_ACTION_CONTROLLERS.remove(controller);
        if (controller != null && FAILED_TEMPORARY_CONTROLLERS.add(controller.getClass())) {
            FindMeMod.LOGGER.error("FindMe disabled failing temporary action controller callback type={} operation={}",
                    controller.getClass().getName(), operation, exception);
        }
    }

    private static void logSpellProviderFailure(FindMeCompanionSpellProvider provider, String operation,
                                                RuntimeException exception) {
        COMPANION_SPELL_PROVIDERS.removeIf(registration -> registration.provider() == provider);
        if (provider != null && FAILED_SPELL_PROVIDERS.add(provider.getClass())) {
            FindMeMod.LOGGER.error("FindMe rejected failing companion spell provider type={} operation={}",
                    provider.getClass().getName(), operation, exception);
        }
    }

    private record SpellProviderRegistration(ResourceLocation id, FindMeCompanionSpellProvider provider) {
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
                || CompanionOperationLockService.isFindMeOwned(uuid)
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

    @FunctionalInterface
    public interface MountedMovementOwner {
        boolean owns(ServerPlayer player);
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
