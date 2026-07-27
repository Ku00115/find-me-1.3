package com.kuzhi.findme.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.api.riding.behaviour.ActiveRidingContext;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CobblemonCommandAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.network.CobblemonPartyPacket;
import com.kuzhi.findme.network.ExternalRideHandoffPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.RescueMagicPacket;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.lifecycle.CompanionArrivalMagicService;
import com.kuzhi.findme.server.lifecycle.CompanionCinematicLandingService;
import com.kuzhi.findme.server.lifecycle.CompanionMountCinematicFlowService;
import com.kuzhi.findme.server.lifecycle.CompanionSpawnPlacementService;
import com.kuzhi.findme.server.lifecycle.MountCinematicMode;
import com.kuzhi.findme.server.lifecycle.RideHandoffService;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.common.MountRosterAction;
import com.kuzhi.findme.server.module.FindMeModuleService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

final class CobblemonHooks {
    private static final String POKEMON_ENTITY_ID = "cobblemon:pokemon";
    private static final Map<UUID, Integer> SELECTED_SLOTS = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING_SEND_OUTS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, UUID> PENDING_PLAYER_SEND_OUTS = new ConcurrentHashMap<>();
    private static final Map<UUID, PokemonRideHandoff> PENDING_RIDE_HANDOFFS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SYNC_REVISIONS = new ConcurrentHashMap<>();

    private CobblemonHooks() {
    }

    static void handleCommand(ServerPlayer player, CobblemonCommandAction action, int slot, UUID targetUuid) {
        if (action == CobblemonCommandAction.SYNC) {
            syncParty(player);
            return;
        }
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int resolvedSlot = slotForUuid(party, targetUuid);
        if (resolvedSlot < 0 || resolvedSlot != slot) {
            syncParty(player);
            return;
        }
        if (action == CobblemonCommandAction.SELECT) {
            select(player, resolvedSlot);
            return;
        }
        activate(player, resolvedSlot);
    }

    static boolean handleRosterAction(ServerPlayer player, MountRosterAction action, int slot, UUID targetUuid) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int resolvedSlot = slotForUuid(party, targetUuid);
        if (action == null || resolvedSlot < 0 || resolvedSlot != slot) {
            syncParty(player);
            return false;
        }
        return switch (action) {
            case SELECT -> {
                SELECTED_SLOTS.put(player.getUUID(), resolvedSlot);
                syncParty(player);
                yield true;
            }
            case ACTIVATE -> activate(player, resolvedSlot);
            case RECALL -> recall(player, party.get(resolvedSlot));
        };
    }

    static boolean contains(ServerPlayer player, int slot, UUID targetUuid) {
        if (player == null || targetUuid == null) return false;
        return slotForUuid(Cobblemon.INSTANCE.getStorage().getParty(player), targetUuid) == slot;
    }

    static CompanionMoveType moveType(ServerPlayer player, int slot, UUID targetUuid) {
        if (player == null || targetUuid == null) return CompanionMoveType.WALK;
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int resolvedSlot = slotForUuid(party, targetUuid);
        Pokemon pokemon = resolvedSlot == slot ? party.get(slot) : null;
        return pokemon == null ? CompanionMoveType.WALK : moveType(pokemon);
    }

    static boolean isRideReady(ServerPlayer player, UUID targetUuid) {
        if (player == null || targetUuid == null || !(player.getVehicle() instanceof PokemonEntity entity)
                || !targetUuid.equals(entity.getPokemon().getUuid())) return false;
        ActiveRidingContext context = entity.getRidingController().getContext();
        return context != null && context.getStyle() != null;
    }

    static boolean isDeployed(ServerPlayer player, UUID targetUuid) {
        if (player == null || targetUuid == null) return false;
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int slot = slotForUuid(party, targetUuid);
        Pokemon pokemon = slot < 0 ? null : party.get(slot);
        PokemonEntity entity = pokemon == null ? null : pokemon.getEntity();
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    static void cancelRosterActivation(ServerPlayer player, UUID targetUuid) {
        if (player == null) return;
        UUID pending = PENDING_PLAYER_SEND_OUTS.get(player.getUUID());
        if (pending != null && (targetUuid == null || targetUuid.equals(pending))) {
            PENDING_PLAYER_SEND_OUTS.remove(player.getUUID(), pending);
            PENDING_SEND_OUTS.remove(pending);
            PENDING_RIDE_HANDOFFS.remove(pending);
        }
        if (targetUuid == null || isRideReady(player, targetUuid)) {
            return;
        }
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int slot = slotForUuid(party, targetUuid);
        Pokemon pokemon = slot < 0 ? null : party.get(slot);
        PokemonEntity entity = pokemon == null ? null : pokemon.getEntity();
        if (entity != null && entity.isAlive() && !entity.isRemoved()) {
            entity.recallWithAnimation().whenComplete((ignored, error) -> execute(player, () -> syncParty(player)));
        }
    }

    private static boolean recall(ServerPlayer player, Pokemon pokemon) {
        PokemonEntity entity = pokemon == null ? null : pokemon.getEntity();
        if (entity == null || entity.isRemoved()) {
            syncParty(player);
            return true;
        }
        entity.recallWithAnimation().whenComplete((ignored, error) -> execute(player, () -> syncParty(player)));
        return true;
    }

    private static void select(ServerPlayer player, int requestedSlot) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int slot = normalizedSlot(party, requestedSlot);
        if (slot >= 0) {
            SELECTED_SLOTS.put(player.getUUID(), slot);
        }
        syncParty(player);
    }

    static void syncParty(ServerPlayer player) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int selectedSlot = selectedSlot(player, party);
        List<CobblemonPartyPacket.Entry> entries = new ArrayList<>(6);
        for (int slot = 0; slot < 6; slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon == null) {
                continue;
            }
            PokemonEntity entity = pokemon.getEntity();
            boolean deployed = entity != null && entity.isAlive() && !entity.isRemoved();
            boolean ridden = deployed && player.getVehicle() == entity;
            entries.add(new CobblemonPartyPacket.Entry(
                    slot,
                    pokemon.getUuid(),
                    deployed ? entity.getId() : -1,
                    pokemon.getDisplayName(false).getString(),
                    deployed,
                    ridden,
                    pokemon.isFainted(),
                    isRideable(pokemon),
                    moveType(pokemon),
                    pokemon.getCurrentHealth(),
                    pokemon.getMaxHealth(),
                    previewTag(player, pokemon)
            ));
        }
        long revision = SYNC_REVISIONS.merge(player.getUUID(), 1L, Long::sum);
        ModNetwork.sendToPlayer(player, new CobblemonPartyPacket(revision, selectedSlot, entries));
    }

    static long syncRevision(ServerPlayer player) {
        return player == null ? 0L : SYNC_REVISIONS.getOrDefault(player.getUUID(), 0L);
    }

    static boolean isPokemon(Entity entity) {
        return entity instanceof PokemonEntity;
    }

    static Vec3 rideVelocity(Entity entity) {
        if (!(entity instanceof PokemonEntity pokemonEntity)) {
            return null;
        }
        ActiveRidingContext context = pokemonEntity.getRidingController().getContext();
        Vec3 rideVelocity = context == null ? null : context.getState().getRideVelocity().get();
        Vec3 entityVelocity = pokemonEntity.getDeltaMovement();
        if (rideVelocity == null) {
            return entityVelocity == null ? Vec3.ZERO : entityVelocity;
        }
        return entityVelocity != null && entityVelocity.lengthSqr() > rideVelocity.lengthSqr()
                ? entityVelocity
                : rideVelocity;
    }

    static void prepareRideHandoff(Entity target, RideHandoffService.MotionSnapshot snapshot) {
        if (target instanceof PokemonEntity pokemonEntity && snapshot != null && snapshot.captured()) {
            PENDING_RIDE_HANDOFFS.put(pokemonEntity.getPokemon().getUuid(), PokemonRideHandoff.capture(snapshot));
        }
    }

    static boolean tryRide(ServerPlayer player, LivingEntity entity) {
        if (!(entity instanceof PokemonEntity pokemonEntity)) {
            return false;
        }
        PokemonRideHandoff handoff = PENDING_RIDE_HANDOFFS.remove(pokemonEntity.getPokemon().getUuid());
        if (handoff == null) {
            handoff = PokemonRideHandoff.capture(player.getVehicle());
        } else {
            handoff = handoff.refresh(player.getVehicle());
        }
        boolean mounted = pokemonEntity.tryRidingPokemon(player) && player.getVehicle() == pokemonEntity;
        if (mounted) {
            RideApplyResult result = handoff.applyTo(pokemonEntity);
            if (result.controllerReady() && handoff.clientBridgeRequired() && handoff.previousStyle() != null) {
                Vec3 velocity = handoff.velocityFor(handoff.previousStyle());
                ModNetwork.sendToPlayer(player, new ExternalRideHandoffPacket(
                        pokemonEntity.getPokemon().getUuid(), handoff.sourceMoveType(),
                        moveType(result.targetStyle()),
                        velocity.x, velocity.y, velocity.z, handoff.yRot(), handoff.xRot()));
            }
        }
        return mounted;
    }

    static boolean recallIfPokemon(Entity entity) {
        if (!(entity instanceof PokemonEntity pokemonEntity)) {
            return false;
        }
        pokemonEntity.recallWithAnimation();
        return true;
    }

    static void recallDeployed(ServerPlayer player) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (int slot = 0; slot < 6; slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon == null) {
                continue;
            }
            PENDING_SEND_OUTS.remove(pokemon.getUuid());
            PENDING_RIDE_HANDOFFS.remove(pokemon.getUuid());
            PokemonEntity entity = pokemon.getEntity();
            if (entity != null && entity.isAlive() && !entity.isRemoved()) {
                entity.recallWithAnimation();
            }
        }
        PENDING_PLAYER_SEND_OUTS.remove(player.getUUID());
        syncParty(player);
    }

    static void forgetPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerUuid = player.getUUID();
        UUID pendingPokemon = PENDING_PLAYER_SEND_OUTS.remove(playerUuid);
        if (pendingPokemon != null) {
            PENDING_SEND_OUTS.remove(pendingPokemon);
            PENDING_RIDE_HANDOFFS.remove(pendingPokemon);
        }
        SELECTED_SLOTS.remove(playerUuid);
        SYNC_REVISIONS.remove(playerUuid);
    }

    static int deployedCount(ServerPlayer player) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int count = 0;
        for (int slot = 0; slot < 6; slot++) {
            Pokemon pokemon = party.get(slot);
            PokemonEntity entity = pokemon == null ? null : pokemon.getEntity();
            if (entity != null && entity.isAlive() && !entity.isRemoved()) {
                count++;
            }
        }
        return count;
    }

    static void clearRideHandoff(Entity entity) {
        if (entity instanceof PokemonEntity pokemonEntity) {
            PENDING_RIDE_HANDOFFS.remove(pokemonEntity.getPokemon().getUuid());
        }
    }

    private static boolean activate(ServerPlayer player, int requestedSlot) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int slot = normalizedSlot(party, requestedSlot);
        Pokemon pokemon = slot < 0 ? null : party.get(slot);
        if (pokemon == null) {
            syncParty(player);
            return false;
        }
        SELECTED_SLOTS.put(player.getUUID(), slot);
        if (pokemon.isFainted()) {
            player.displayClientMessage(Component.translatable("message.find_me.cobblemon_fainted", pokemon.getDisplayName(false)).withStyle(ChatFormatting.RED), true);
            syncParty(player);
            return false;
        }

        PokemonEntity active = pokemon.getEntity();
        if (active != null && !active.isRemoved()) {
            active.recallWithAnimation().whenComplete((ignored, error) -> execute(player, () -> syncParty(player)));
            return true;
        }
        UUID playerUuid = player.getUUID();
        UUID pendingPokemon = PENDING_PLAYER_SEND_OUTS.putIfAbsent(playerUuid, pokemon.getUuid());
        if (pendingPokemon != null) {
            player.displayClientMessage(Component.translatable("message.find_me.busy", pokemon.getDisplayName(false))
                    .withStyle(ChatFormatting.YELLOW), true);
            FindMeDebugLogger.info("cobblemon",
                    "send-out blocked player={} requested={} pending={} reason=player_transaction_busy",
                    playerUuid, pokemon.getUuid(), pendingPokemon);
            syncParty(player);
            return false;
        }
        if (!PENDING_SEND_OUTS.add(pokemon.getUuid())) {
            PENDING_PLAYER_SEND_OUTS.remove(playerUuid, pokemon.getUuid());
            return false;
        }

        boolean rideable = isRideable(pokemon);
        CompanionMoveType moveType = moveType(pokemon);
        RideHandoffService.Source rideSource = rideable
                ? RideHandoffService.resolveSource(player, com.kuzhi.findme.server.data.CompanionDataService.data(player))
                : RideHandoffService.Source.none();
        boolean fallingRescue = rideable && !rideSource.present() && isFallingRescue(player);
        if (fallingRescue && moveType != CompanionMoveType.FLY
                && !CompanionCinematicLandingService.hasReliableLandingBelow(player.serverLevel(), player)) {
            releasePendingSendOut(playerUuid, pokemon.getUuid());
            player.displayClientMessage(Component.translatable("message.find_me.cobblemon_rescue_requires_flight").withStyle(ChatFormatting.RED), true);
            syncParty(player);
            return false;
        }
        boolean airToGroundSwitch = rideSource.airborne() && moveType != CompanionMoveType.FLY;
        boolean rescuePresentation = fallingRescue || airToGroundSwitch;
        MountCinematicMode mode = RideHandoffService.modeForMountTarget(rideSource, moveType, fallingRescue);
        BlockPos spawn = rideable
                ? CompanionSpawnPlacementService.findArrivalSpawn(player, moveType, rescuePresentation, POKEMON_ENTITY_ID)
                : CompanionSpawnPlacementService.findSummonSpot(player, CompanionKind.COMPANION, moveType);
        if (rescuePresentation) {
            CompanionCinematicLandingService.preloadChunkArea(player.serverLevel(), spawn, 1);
            CompanionMountCinematicFlowService.stabilizeFallingPlayer(player);
        }
        Vec3 position = Vec3.atBottomCenterOf(spawn);
        RescueMagicPacket.Purpose purpose = rescuePresentation ? RescueMagicPacket.Purpose.RESCUE : RescueMagicPacket.Purpose.SUMMON;
        RescueMagicPacket.Style style = rideable ? RescueMagicPacket.Style.VERTICAL_PORTAL : RescueMagicPacket.Style.GROUND_CIRCLE;

        Runnable sendOut = () -> {
            PokemonEntity sentEntity = pokemon.sendOut(player.serverLevel(), position, null, entity -> {
                if (!PENDING_SEND_OUTS.contains(pokemon.getUuid())) {
                    entity.recallWithAnimation();
                    return Unit.INSTANCE;
                }
                if (!FindMeModuleService.enabled(FindMeModule.COBBLEMON_INTEGRATION)) {
                    entity.recallWithAnimation();
                    return Unit.INSTANCE;
                }
                int duration = rescuePresentation ? 48 : 42;
                CompanionArrivalMagicService.beginAt(player, entity, position, player.position(), duration, style, purpose);
                if (rideable) {
                    CompanionMountCinematicFlowService.scheduleExternalMountCinematic(player, entity, moveType, mode);
                }
                return Unit.INSTANCE;
            });
            execute(player, () -> {
                releasePendingSendOut(playerUuid, pokemon.getUuid());
                if (sentEntity == null) {
                    PENDING_RIDE_HANDOFFS.remove(pokemon.getUuid());
                    player.displayClientMessage(Component.translatable("message.find_me.cobblemon_send_failed", pokemon.getDisplayName(false)).withStyle(ChatFormatting.RED), true);
                    FindMeDebugLogger.info("cobblemon", "send-out failed player={} pokemon={} error=null_entity",
                            player.getUUID(), pokemon.getUuid());
                }
                syncParty(player);
            });
        };

        try {
            sendOut.run();
            return true;
        } catch (RuntimeException error) {
            releasePendingSendOut(playerUuid, pokemon.getUuid());
            PENDING_RIDE_HANDOFFS.remove(pokemon.getUuid());
            FindMeDebugLogger.info("cobblemon", "send-out threw player={} pokemon={} error={}",
                    playerUuid, pokemon.getUuid(), error.toString());
            syncParty(player);
            return false;
        }
    }

    private static void releasePendingSendOut(UUID playerUuid, UUID pokemonUuid) {
        PENDING_SEND_OUTS.remove(pokemonUuid);
        PENDING_PLAYER_SEND_OUTS.remove(playerUuid, pokemonUuid);
    }

    private static int selectedSlot(ServerPlayer player, PlayerPartyStore party) {
        PokemonEntity ridden = player.getVehicle() instanceof PokemonEntity entity ? entity : null;
        if (ridden != null) {
            for (int slot = 0; slot < 6; slot++) {
                Pokemon pokemon = party.get(slot);
                if (pokemon != null && pokemon.getUuid().equals(ridden.getPokemon().getUuid())) {
                    SELECTED_SLOTS.put(player.getUUID(), slot);
                    return slot;
                }
            }
        }
        return normalizedSlot(party, SELECTED_SLOTS.getOrDefault(player.getUUID(), -1));
    }

    private static int normalizedSlot(PlayerPartyStore party, int requested) {
        if (requested >= 0 && requested < 6 && party.get(requested) != null) {
            return requested;
        }
        for (int slot = 0; slot < 6; slot++) {
            if (party.get(slot) != null) {
                return slot;
            }
        }
        return -1;
    }

    private static int slotForUuid(PlayerPartyStore party, UUID uuid) {
        if (uuid == null) {
            return -1;
        }
        for (int slot = 0; slot < 6; slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon != null && uuid.equals(pokemon.getUuid())) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isRideable(Pokemon pokemon) {
        return pokemon.getRiding().getBehaviours() != null && !pokemon.getRiding().getBehaviours().isEmpty();
    }

    private static CompanionMoveType moveType(Pokemon pokemon) {
        Map<RidingStyle, ?> behaviours = pokemon.getRiding().getBehaviours();
        if (behaviours != null && behaviours.containsKey(RidingStyle.AIR)) {
            return CompanionMoveType.FLY;
        }
        if (behaviours != null && behaviours.containsKey(RidingStyle.LIQUID)) {
            return CompanionMoveType.SWIM;
        }
        if (behaviours != null && behaviours.containsKey(RidingStyle.LAND)) {
            return CompanionMoveType.WALK;
        }
        // A non-empty ride profile should normally contain one of the three styles.
        // Keep a conservative fallback for malformed or integration-provided data.
        if (pokemon.getForm().getBehaviour().getMoving().getFly().getCanFly()) {
            return CompanionMoveType.FLY;
        }
        if (!pokemon.getForm().getBehaviour().getMoving().getWalk().getCanWalk()
                && pokemon.getForm().getBehaviour().getMoving().getSwim().getCanSwimInWater()) {
            return CompanionMoveType.SWIM;
        }
        return CompanionMoveType.WALK;
    }

    static CompanionMoveType moveType(Entity entity) {
        return entity instanceof PokemonEntity pokemonEntity
                ? moveType(pokemonEntity.getPokemon())
                : CompanionMoveType.WALK;
    }

    private static boolean isFallingRescue(ServerPlayer player) {
        return player.getVehicle() == null
                && !player.onGround()
                && player.getDeltaMovement().y < -0.01
                && (player.fallDistance >= Config.rescueMinFallDistance
                || CompanionCinematicLandingService.distanceToGround(player) > 6.0);
    }

    private static CompoundTag previewTag(ServerPlayer player, Pokemon pokemon) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", POKEMON_ENTITY_ID);
        tag.put("Pokemon", pokemon.saveToNBT(player.registryAccess(), new CompoundTag()));
        return tag;
    }

    private record PokemonRideHandoff(RidingStyle previousStyle, Vec3 velocity, float yRot, float xRot,
                                      boolean clientBridgeRequired) {
        private static PokemonRideHandoff capture(RideHandoffService.MotionSnapshot snapshot) {
            RidingStyle sourceStyle = switch (snapshot.sourceMoveType()) {
                case FLY -> RidingStyle.AIR;
                case SWIM -> RidingStyle.LIQUID;
                default -> RidingStyle.LAND;
            };
            return new PokemonRideHandoff(sourceStyle, snapshot.velocityFor(snapshot.sourceMoveType()),
                    snapshot.yRot(), snapshot.xRot(),
                    snapshot.sourceType() != RideHandoffService.SourceType.COBBLEMON);
        }

        private static PokemonRideHandoff capture(Entity previousVehicle) {
            return capture(previousVehicle, null,
                    previousVehicle != null && !(previousVehicle instanceof PokemonEntity));
        }

        private static PokemonRideHandoff capture(Entity previousVehicle, RidingStyle fallbackStyle,
                                                   boolean clientBridgeRequired) {
            if (!(previousVehicle instanceof PokemonEntity previousPokemon)) {
                Vec3 velocity = previousVehicle == null || previousVehicle.getDeltaMovement() == null
                        ? Vec3.ZERO
                        : previousVehicle.getDeltaMovement();
                return new PokemonRideHandoff(fallbackStyle, velocity,
                        previousVehicle == null ? 0.0f : previousVehicle.getYRot(),
                        previousVehicle == null ? 0.0f : previousVehicle.getXRot(), clientBridgeRequired);
            }
            ActiveRidingContext context = previousPokemon.getRidingController().getContext();
            RidingStyle previousStyle = context == null ? fallbackStyle : context.getStyle();
            Vec3 rideVelocity = context == null ? Vec3.ZERO : context.getState().getRideVelocity().get();
            Vec3 entityVelocity = previousPokemon.getDeltaMovement();
            if (entityVelocity == null) {
                entityVelocity = Vec3.ZERO;
            }
            Vec3 velocity = rideVelocity != null && rideVelocity.lengthSqr() >= entityVelocity.lengthSqr()
                    ? rideVelocity
                    : entityVelocity;
            return new PokemonRideHandoff(previousStyle, velocity == null ? Vec3.ZERO : velocity,
                    previousPokemon.getYRot(), previousPokemon.getXRot(), clientBridgeRequired);
        }

        private PokemonRideHandoff refresh(Entity currentVehicle) {
            if (currentVehicle == null || currentVehicle.isRemoved()) {
                return this;
            }
            return capture(currentVehicle, this.previousStyle,
                    this.clientBridgeRequired || !(currentVehicle instanceof PokemonEntity));
        }

        private RideApplyResult applyTo(PokemonEntity pokemonEntity) {
            Map<RidingStyle, RidingBehaviourSettings> behaviours = pokemonEntity.getPokemon().getRiding().getBehaviours();
            RidingStyle targetStyle = serverRidingStyle(pokemonEntity, behaviours);
            RidingBehaviourSettings settings = targetStyle == null || behaviours == null ? null : behaviours.get(targetStyle);
            if (settings == null) {
                return new RideApplyResult(null, false, false);
            }
            pokemonEntity.getRidingController().changeBehaviour(settings.getKey());
            ActiveRidingContext context = pokemonEntity.getRidingController().getContext();
            boolean controllerReady = context != null && context.getStyle() == targetStyle;
            boolean inheritVelocity = controllerReady && this.previousStyle == targetStyle;
            Vec3 inheritedVelocity = inheritVelocity ? velocityFor(targetStyle) : Vec3.ZERO;
            if (inheritVelocity) {
                context.getState().getRideVelocity().set(inheritedVelocity, true);
                pokemonEntity.setDeltaMovement(inheritedVelocity);
                pokemonEntity.setYRot(this.yRot);
                pokemonEntity.setXRot(this.xRot);
                pokemonEntity.yBodyRot = this.yRot;
                pokemonEntity.yHeadRot = this.yRot;
                pokemonEntity.hurtMarked = true;
            }
            FindMeDebugLogger.info("cobblemon", "server riding controller initialized pokemon={} previousStyle={} requestedStyle={} activeStyle={} behaviours={} inheritedVelocity={} velocity={} onGround={} noPhysics={}",
                    pokemonEntity.getPokemon().getUuid(), this.previousStyle, targetStyle,
                    context == null ? "null" : context.getStyle(), behaviours.keySet(), inheritVelocity,
                    inheritedVelocity, pokemonEntity.onGround(), pokemonEntity.noPhysics);
            return new RideApplyResult(targetStyle, controllerReady, inheritVelocity);
        }

        private CompanionMoveType sourceMoveType() {
            return moveType(this.previousStyle);
        }

        private Vec3 velocityFor(RidingStyle style) {
            if (style == null || this.velocity == null) {
                return Vec3.ZERO;
            }
            Vec3 adjusted = style == RidingStyle.LAND
                    ? new Vec3(this.velocity.x, 0.0, this.velocity.z)
                    : this.velocity;
            double maximum = switch (style) {
                case LAND -> 1.5;
                case LIQUID -> 2.0;
                case AIR -> 4.0;
            };
            double lengthSqr = adjusted.lengthSqr();
            return lengthSqr > maximum * maximum
                    ? adjusted.scale(maximum / Math.sqrt(lengthSqr))
                    : adjusted;
        }

        private static RidingStyle serverRidingStyle(PokemonEntity pokemonEntity,
                                                      Map<RidingStyle, RidingBehaviourSettings> behaviours) {
            if (behaviours == null || behaviours.isEmpty()) {
                return null;
            }
            if ((pokemonEntity.isInWater() || pokemonEntity.isUnderWater()) && behaviours.containsKey(RidingStyle.LIQUID)) {
                return RidingStyle.LIQUID;
            }
            if (behaviours.containsKey(RidingStyle.AIR)) {
                return RidingStyle.AIR;
            }
            if (behaviours.containsKey(RidingStyle.LAND)) {
                return RidingStyle.LAND;
            }
            if (behaviours.containsKey(RidingStyle.LIQUID)) {
                return RidingStyle.LIQUID;
            }
            return behaviours.keySet().iterator().next();
        }
    }

    private static CompanionMoveType moveType(RidingStyle style) {
        if (style == null) {
            return CompanionMoveType.WALK;
        }
        return switch (style) {
            case AIR -> CompanionMoveType.FLY;
            case LIQUID -> CompanionMoveType.SWIM;
            case LAND -> CompanionMoveType.WALK;
        };
    }

    private record RideApplyResult(RidingStyle targetStyle, boolean controllerReady,
                                   boolean inheritedVelocity) {
    }

    private static void execute(ServerPlayer player, Runnable action) {
        if (player.getServer() != null) {
            player.getServer().execute(() -> {
                if (!player.isRemoved()) {
                    action.run();
                }
            });
        }
    }
}
