package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.ui.CompanionMessageService;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.BindingAnimationPolicy;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.profile.PackAnimationPresetService;
import com.kuzhi.findme.network.ContractCameraPacket;
import com.kuzhi.findme.network.ModNetwork;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CompanionContractService {
    private static final int DURATION_TICKS = 120;
    private static final int ENDING_TICKS = 36;
    private static final double MIN_WARD_RADIUS = 7.5;
    private static final Map<UUID, PendingContract> CONTRACTS = new HashMap<>();
    private static final Map<UUID, UUID> TARGET_TO_PLAYER = new HashMap<>();

    private CompanionContractService() {
    }

    public static boolean start(ServerPlayer player, LivingEntity target) {
        if (target == player || !target.isAlive() || player.level() != target.level()) {
            CompanionMessageService.tell(player, "message.find_me.contract_invalid_target", ChatFormatting.RED);
            return false;
        }
        if (CONTRACTS.containsKey(player.getUUID()) || TARGET_TO_PLAYER.containsKey(target.getUUID())) {
            CompanionMessageService.tell(player, "message.find_me.contract_busy", ChatFormatting.RED);
            return false;
        }
        CompanionBindingService.BindingCheck binding = CompanionBindingService.checkManualBinding(player, target);
        if (!binding.allowed()) {
            CompanionMessageService.tell(player, binding.messageKey(), binding.color(), binding.args());
            return false;
        }
        CompanionKind kind = binding.kind();
        if (!shouldPlayAnimation(player, target)) {
            return bindImmediately(player, target, kind);
        }
        float targetRadius = visualRadius(target);
        ContractPose pose = arrangePose(player, target, kind, targetRadius);
        PendingContract contract = PendingContract.create(player, target, kind, pose, targetRadius);
        CONTRACTS.put(player.getUUID(), contract);
        TARGET_TO_PLAYER.put(target.getUUID(), player.getUUID());
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setTarget(null);
        }
        applyOpeningPose(player, target, contract);
        ModNetwork.sendToPlayer(player, new ContractCameraPacket(true, target.getId(), DURATION_TICKS, pose.playerYaw, 8.0f, pose.playerPos.x, pose.playerPos.y, pose.playerPos.z, pose.targetPos.x, pose.targetPos.y, pose.targetPos.z, 1.55f, targetRadius, target.getDisplayName().getString(), ContractCameraPacket.Mode.START, Config.enableContractCinematicCamera));
        Vec3 center = player.position().add(target.position()).scale(0.5);
        playCeremonySound(player, center, SoundEvents.ENCHANTMENT_TABLE_USE, 0.82f, 0.85f);
        playCeremonySound(player, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.46f, 1.25f);
        return true;
    }

    public static void tick(MinecraftServer server) {
        Iterator<PendingContract> iterator = CONTRACTS.values().iterator();
        while (iterator.hasNext()) {
            PendingContract contract = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(contract.playerUuid);
            Optional<Entity> located = CompanionEntityLookup.findEntity(server, contract.targetUuid);
            if (located.isEmpty() || !(located.get() instanceof LivingEntity target)) {
                if (player != null) {
                    ModNetwork.sendToPlayer(player, endingPacket(contract, -1, ContractCameraPacket.Mode.CANCEL));
                }
                iterator.remove();
                TARGET_TO_PLAYER.remove(contract.targetUuid);
                continue;
            }
            if (player == null) {
                restoreTargetAi(target, contract);
                iterator.remove();
                TARGET_TO_PLAYER.remove(contract.targetUuid);
                continue;
            }
            FindMeModule module = contract.kind == CompanionKind.MOUNT
                    ? FindMeModule.RIDING : FindMeModule.COMPANIONS;
            if (!FindMeModuleService.enabled(module)) {
                cancel(iterator, player, target, contract);
                continue;
            }
            if (!canContinue(player, target, contract)) {
                cancel(iterator, player, target, contract);
                continue;
            }
            contract.ticks++;
            holdStill(player, target, contract);
            repelOthers(player.serverLevel(), player, target, contract);
            spawnAtmosphereParticles(player.serverLevel(), contract, target);
            playStageSounds(player, contract);
            if (contract.ticks >= DURATION_TICKS) {
                complete(iterator, player, target, contract);
            }
        }
    }

    private static boolean canContinue(ServerPlayer player, LivingEntity target, PendingContract contract) {
        return target.isAlive()
                && player.isAlive()
                && player.level().dimension().equals(contract.dimension)
                && target.level().dimension().equals(contract.dimension);
    }

    private static void cancel(Iterator<PendingContract> iterator, ServerPlayer player, LivingEntity target, PendingContract contract) {
        restoreTargetAi(target, contract);
        ModNetwork.sendToPlayer(player, endingPacket(contract, target.getId(), ContractCameraPacket.Mode.CANCEL));
        TARGET_TO_PLAYER.remove(contract.targetUuid);
        iterator.remove();
    }

    private static boolean bindImmediately(ServerPlayer player, LivingEntity target, CompanionKind kind) {
        if (!CompanionBindingService.bindAndStore(player, target, kind)) {
            return false;
        }
        markBindingTypeSeen(player, target);
        return true;
    }

    private static void complete(Iterator<PendingContract> iterator, ServerPlayer player, LivingEntity target, PendingContract contract) {
        restoreTargetAi(target, contract);
        if (CompanionBindingService.bindAndStore(player, target, contract.kind)) {
            markBindingTypeSeen(player, target);
            ModNetwork.sendToPlayer(player, endingPacket(contract, target.getId(), ContractCameraPacket.Mode.COMPLETE));
            playCeremonySound(player, contract.playerPos.add(contract.targetPos).scale(0.5), SoundEvents.ENDERMAN_TELEPORT, 0.44f, 1.55f);
            playCeremonySound(player, contract.playerPos.add(contract.targetPos).scale(0.5), SoundEvents.BEACON_POWER_SELECT, 0.36f, 1.48f);
            spawnCompletionBurst(player.serverLevel(), player, target);
        } else {
            ModNetwork.sendToPlayer(player, endingPacket(contract, target.getId(), ContractCameraPacket.Mode.CANCEL));
        }
        TARGET_TO_PLAYER.remove(contract.targetUuid);
        iterator.remove();
    }

    private static void playStageSounds(ServerPlayer player, PendingContract contract) {
        Vec3 center = contract.playerPos.add(contract.targetPos).scale(0.5);
        if (contract.ticks == 8) {
            playCeremonySound(player, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.52f, 1.32f);
        } else if (contract.ticks == 18) {
            playCeremonySound(player, center, SoundEvents.ENCHANTMENT_TABLE_USE, 0.92f, 1.12f);
            playCeremonySound(player, center, SoundEvents.AMETHYST_BLOCK_CHIME, 0.58f, 1.26f);
        } else if (contract.ticks == 36) {
            playCeremonySound(player, center, SoundEvents.ILLUSIONER_CAST_SPELL, 0.44f, 1.18f);
        } else if (contract.ticks == 54) {
            playCeremonySound(player, center, SoundEvents.AMETHYST_BLOCK_CHIME, 1.02f, 0.92f);
            playCeremonySound(player, center, SoundEvents.EVOKER_CAST_SPELL, 0.40f, 0.96f);
        } else if (contract.ticks == 74) {
            playCeremonySound(player, center, SoundEvents.ENCHANTMENT_TABLE_USE, 0.70f, 1.34f);
        } else if (contract.ticks == 92) {
            playCeremonySound(player, center, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.42f);
            playCeremonySound(player, center, SoundEvents.AMETHYST_BLOCK_CHIME, 0.96f, 1.35f);
        } else if (contract.ticks == 110) {
            playCeremonySound(player, center, SoundEvents.BEACON_POWER_SELECT, 0.58f, 1.62f);
        }
    }

    private static boolean shouldPlayAnimation(ServerPlayer player, LivingEntity target) {
        if (!Config.enableContractAnimation) {
            return false;
        }
        String entityType = EntityType.getKey(target.getType()).toString();
        PlayerCompanionData data = CompanionDataService.data(player);
        BindingAnimationPolicy global = data.uiSettings().bindingAnimationPolicy();
        BindingAnimationPolicy policy = PackAnimationPresetService.bindingAnimationPolicy(entityType)
                .effective(global);
        return policy == BindingAnimationPolicy.ALWAYS
                || policy == BindingAnimationPolicy.FIRST_TYPE
                && !data.hasSeenBindingCinematic(entityType);
    }

    private static void markBindingTypeSeen(ServerPlayer player, LivingEntity target) {
        PlayerCompanionData data = CompanionDataService.data(player);
        if (data.markBindingCinematicSeen(EntityType.getKey(target.getType()).toString())) {
            CompanionDataService.save(player, data);
        }
    }

    private static void playCeremonySound(ServerPlayer player, Vec3 pos, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (volume <= 0.0f) {
            return;
        }
        ServerLevel level = player.serverLevel();
        level.playSound(null, pos.x, pos.y + 1.0, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
        Vec3 playerPos = player.position();
        level.playSound(null, playerPos.x, playerPos.y + player.getEyeHeight() * 0.75, playerPos.z, sound, SoundSource.PLAYERS, volume * 0.55f, pitch);
    }

    private static ContractCameraPacket endingPacket(PendingContract contract, int targetEntityId, ContractCameraPacket.Mode mode) {
        return new ContractCameraPacket(false, targetEntityId, ENDING_TICKS, contract.playerYaw, 8.0f, contract.playerPos.x, contract.playerPos.y, contract.playerPos.z, contract.targetPos.x, contract.targetPos.y, contract.targetPos.z, 1.55f, contract.targetRadius, contract.targetName, mode, false);
    }

    private static void holdStill(ServerPlayer player, LivingEntity target, PendingContract contract) {
        player.setDeltaMovement(Vec3.ZERO);
        target.setDeltaMovement(Vec3.ZERO);
        player.connection.teleport(contract.playerPos.x, contract.playerPos.y, contract.playerPos.z, contract.playerYaw, 8.0f);
        target.moveTo(contract.targetEntityPos.x, contract.targetEntityPos.y, contract.targetEntityPos.z, contract.targetYaw, target.getXRot());
        target.setNoGravity(contract.targetShouldFly || contract.targetHadNoGravity);
        if (contract.targetShouldFly) {
            CompanionAnimationHelper.forceFlyingAnimationPose(target);
        }
        faceParticipants(player, target, contract);
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setTarget(null);
        }
    }

    private static void faceParticipants(ServerPlayer player, LivingEntity target, PendingContract contract) {
        float playerYaw = yawToward(player.position(), target.position());
        float targetYaw = yawToward(target.position(), player.position());
        player.setYRot(playerYaw);
        player.setYHeadRot(playerYaw);
        player.yRotO = playerYaw;
        player.yHeadRot = playerYaw;
        player.yHeadRotO = playerYaw;
        CompanionCinematicOrientationHelper.faceYaw(target, targetYaw);
        Vec3 targetEye = target.position().add(0.0, target.getBbHeight() * 0.62, 0.0);
        Vec3 playerEye = player.position().add(0.0, player.getEyeHeight(), 0.0);
        float pitch = CompanionCinematicOrientationHelper.pitchToward(playerEye.subtract(targetEye));
        target.setXRot(pitch);
        target.xRotO = pitch;
    }

    private static void repelOthers(ServerLevel level, ServerPlayer player, LivingEntity target, PendingContract contract) {
        Vec3 center = contract.playerPos.add(contract.targetPos).scale(0.5);
        AABB ward = new AABB(center, center).inflate(contract.wardRadius, 4.2, contract.wardRadius);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, ward, entity -> entity != player && entity != target && entity.isAlive())) {
            Vec3 away = living.position().subtract(center);
            if (away.lengthSqr() < 0.01) {
                away = new Vec3(level.random.nextDouble() - 0.5, 0.0, level.random.nextDouble() - 0.5);
            }
            Vec3 push = away.normalize().scale(0.55).add(0.0, 0.06, 0.0);
            living.setDeltaMovement(living.getDeltaMovement().add(push));
            living.hurtMarked = true;
        }
    }

    private static void spawnCompletionBurst(ServerLevel level, ServerPlayer player, LivingEntity target) {
        AABB visualBox = CompanionEntityVisualBoundsService.effectBounds(target);
        double radius = CompanionEntityVisualBoundsService.contractCircleRadius(target);
        double height = Math.max(0.25, visualBox.getYsize());
        Vec3 center = target.position().add(player.position()).scale(0.5);
        int targetCount = Mth.clamp((int)Math.round(24.0 + radius * height * 18.0), 24, 192);
        level.sendParticles((ParticleOptions)ParticleTypes.ENCHANT, visualBox.getCenter().x, visualBox.minY + height * 0.42, visualBox.getCenter().z, targetCount, radius * 0.42, height * 0.20, radius * 0.42, 0.36);
        level.sendParticles((ParticleOptions)ParticleTypes.ENCHANT, center.x, center.y + 0.9, center.z, 42, radius * 0.35, 0.28, radius * 0.35, 0.28);
        level.sendParticles((ParticleOptions)ParticleTypes.END_ROD, visualBox.getCenter().x, visualBox.minY + height * 0.25, visualBox.getCenter().z, Mth.clamp((int)Math.round(4.0 + radius * 3.0), 4, 24), radius * 0.24, height * 0.05, radius * 0.24, 0.03);
        level.sendParticles((ParticleOptions)ParticleTypes.REVERSE_PORTAL, visualBox.getCenter().x, visualBox.minY + height * 0.35, visualBox.getCenter().z, Mth.clamp((int)Math.round(4.0 + radius * 3.0), 4, 24), radius * 0.20, height * 0.08, radius * 0.20, 0.08);
    }

    private static void spawnAtmosphereParticles(ServerLevel level, PendingContract contract, LivingEntity target) {
        if (contract.ticks % 12 != 0) {
            return;
        }
        Vec3 center = contract.playerPos.add(contract.targetPos).scale(0.5);
        double radius = Math.max(2.0, contract.targetRadius);
        level.sendParticles((ParticleOptions)ParticleTypes.ENCHANT, contract.playerPos.x, contract.playerPos.y + 0.12, contract.playerPos.z, 3, 0.35, 0.02, 0.35, 0.01);
        level.sendParticles((ParticleOptions)ParticleTypes.ENCHANT, contract.targetPos.x, contract.targetPos.y + 0.12, contract.targetPos.z, 3, radius * 0.18, 0.02, radius * 0.18, 0.01);
        if (contract.ticks >= 90) {
            level.sendParticles((ParticleOptions)ParticleTypes.END_ROD, center.x, center.y + 0.22, center.z, 2, radius * 0.18, 0.05, radius * 0.18, 0.02);
        }
    }

    private static ContractPose arrangePose(ServerPlayer player, LivingEntity target, CompanionKind kind, float targetRadius) {
        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position();
        Vec3 direction = horizontal(targetPos.subtract(playerPos));
        if (direction.lengthSqr() < 0.01) {
            direction = horizontal(player.getLookAngle());
        }
        if (direction.lengthSqr() < 0.01) {
            direction = new Vec3(1.0, 0.0, 0.0);
        }
        direction = direction.normalize();
        Vec3 midpoint = bestRitualCenter(player.serverLevel(), player, target, playerPos.add(targetPos).scale(0.5), direction, targetRadius);
        double separation = Mth.clamp(targetRadius * 1.62 + 6.2, 9.5, 28.0);
        Vec3 arrangedPlayer = new Vec3(midpoint.x - direction.x * separation * 0.5, player.getY(), midpoint.z - direction.z * separation * 0.5);
        Vec3 arrangedTarget = new Vec3(midpoint.x + direction.x * separation * 0.5, player.getY(), midpoint.z + direction.z * separation * 0.5);
        Vec3 arrangedTargetEntity = flyingBindingTarget(player.serverLevel(), target, kind, arrangedTarget, targetRadius);
        float playerYaw = yawToward(arrangedPlayer, arrangedTargetEntity);
        float targetYaw = yawToward(arrangedTargetEntity, arrangedPlayer);
        double wardRadius = Mth.clamp(separation * 0.5 + targetRadius * 1.25 + 4.0, MIN_WARD_RADIUS, 28.0);
        return new ContractPose(arrangedPlayer, arrangedTarget, arrangedTargetEntity, playerYaw, targetYaw, wardRadius, arrangedTargetEntity.y > arrangedTarget.y + 0.15);
    }

    private static Vec3 flyingBindingTarget(ServerLevel level, LivingEntity target, CompanionKind kind, Vec3 circlePos, float targetRadius) {
        if (CompanionEntityClassifier.summonMoveType(target, kind) != com.kuzhi.findme.common.CompanionMoveType.FLY) {
            return circlePos;
        }
        double maxLift = Mth.clamp(targetRadius * 0.45 + target.getBbHeight() * 0.30 + 1.3, 1.6, 5.2);
        for (double lift = maxLift; lift >= 1.0; lift -= 0.5) {
            Vec3 candidate = circlePos.add(0.0, lift, 0.0);
            if (CompanionPlacementFinder.hasOpenBox(level, entityBoxAt(target, candidate, targetRadius))) {
                return candidate;
            }
        }
        return circlePos;
    }

    private static Vec3 bestRitualCenter(ServerLevel level, ServerPlayer player, LivingEntity target, Vec3 midpoint, Vec3 direction, float targetRadius) {
        Vec3 side = new Vec3(-direction.z, 0.0, direction.x);
        double separation = Mth.clamp(targetRadius * 1.62 + 6.2, 9.5, 28.0);
        Vec3 best = midpoint;
        double bestScore = ritualSpaceScore(level, player, target, midpoint, direction, separation, targetRadius);
        for (int r = 2; r <= 12; r += 2) {
            Vec3[] candidates = {
                    midpoint.add(side.scale(r)),
                    midpoint.add(side.scale(-r)),
                    midpoint.add(direction.scale(r)),
                    midpoint.add(direction.scale(-r)),
                    midpoint.add(side.add(direction).normalize().scale(r)),
                    midpoint.add(side.subtract(direction).normalize().scale(r)),
                    midpoint.add(side.scale(-1.0).add(direction).normalize().scale(r)),
                    midpoint.add(side.scale(-1.0).subtract(direction).normalize().scale(r))
            };
            for (Vec3 candidate : candidates) {
                double score = ritualSpaceScore(level, player, target, candidate, direction, separation, targetRadius);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                    if (score >= 7.0) {
                        return best;
                    }
                }
            }
        }
        return best;
    }

    private static double ritualSpaceScore(ServerLevel level, ServerPlayer player, LivingEntity target, Vec3 center, Vec3 direction, double separation, float targetRadius) {
        Vec3 playerPos = new Vec3(center.x - direction.x * separation * 0.5, player.getY(), center.z - direction.z * separation * 0.5);
        Vec3 targetPos = new Vec3(center.x + direction.x * separation * 0.5, player.getY(), center.z + direction.z * separation * 0.5);
        double score = 0.0;
        score += openBoxScore(level, entityBoxAt(player, playerPos, 1.15f));
        score += openBoxScore(level, entityBoxAt(target, targetPos, targetRadius));
        double wardRadius = Mth.clamp(separation * 0.5 + targetRadius * 1.25 + 4.0, MIN_WARD_RADIUS, 28.0);
        score += openBoxScore(level, new AABB(center.x - wardRadius, center.y + 0.35, center.z - wardRadius, center.x + wardRadius, center.y + 3.2, center.z + wardRadius));
        score += distanceToWallScore(level, playerPos, 1.8);
        score += distanceToWallScore(level, targetPos, Math.min(5.5, Math.max(2.2, targetRadius * 0.55)));
        return score;
    }

    private static double openBoxScore(ServerLevel level, AABB box) {
        return CompanionPlacementFinder.hasOpenBox(level, box) ? 2.0 : 0.0;
    }

    private static AABB entityBoxAt(LivingEntity entity, Vec3 pos, float radius) {
        AABB box = entity == null ? new AABB(pos.x - radius, pos.y, pos.z - radius, pos.x + radius, pos.y + 2.0, pos.z + radius) : CompanionEntityVisualBoundsService.visualBounds(entity);
        Vec3 current = entity == null ? box.getCenter() : entity.position();
        return box.move(pos.subtract(current)).inflate(0.35, 0.15, 0.35);
    }

    private static double distanceToWallScore(ServerLevel level, Vec3 pos, double radius) {
        int clear = 0;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0;
            Vec3 sample = pos.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            AABB box = new AABB(sample.x - 0.35, sample.y + 0.1, sample.z - 0.35, sample.x + 0.35, sample.y + 1.9, sample.z + 0.35);
            if (CompanionPlacementFinder.hasOpenBox(level, box)) {
                clear++;
            }
        }
        return clear / 8.0;
    }

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0.0, vector.z);
    }

    private static void applyOpeningPose(ServerPlayer player, LivingEntity target, PendingContract contract) {
        player.setDeltaMovement(Vec3.ZERO);
        target.setDeltaMovement(Vec3.ZERO);
        player.connection.teleport(contract.playerPos.x, contract.playerPos.y, contract.playerPos.z, contract.playerYaw, 8.0f);
        target.moveTo(contract.targetEntityPos.x, contract.targetEntityPos.y, contract.targetEntityPos.z, contract.targetYaw, target.getXRot());
        target.setNoGravity(contract.targetShouldFly || contract.targetHadNoGravity);
        if (contract.targetShouldFly) {
            CompanionAnimationHelper.forceFlyingAnimationPose(target);
        }
        faceParticipants(player, target, contract);
    }

    private static float yawToward(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return Mth.wrapDegrees((float)(Mth.atan2(dz, dx) * 57.2957763671875) - 90.0f);
    }

    private static float visualRadius(LivingEntity entity) {
        return CompanionEntityVisualBoundsService.contractCircleRadius(entity);
    }

    private static void restoreTargetAi(LivingEntity target, PendingContract contract) {
        if (contract.targetShouldFly) {
            CompanionAnimationHelper.restoreAnimationControl(target);
        }
        if (target instanceof Mob mob) {
            mob.setNoAi(contract.targetHadNoAi);
        }
        target.setNoGravity(contract.targetHadNoGravity);
        target.noPhysics = contract.targetHadNoPhysics;
    }

    private static void chat(ServerPlayer player, String key, ChatFormatting color, Object ... args) {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(color));
    }

    private static final class PendingContract {
        final UUID playerUuid;
        final UUID targetUuid;
        final net.minecraft.resources.ResourceKey<Level> dimension;
        final Vec3 startPlayerPos;
        final Vec3 startTargetPos;
        final Vec3 playerPos;
        final Vec3 targetPos;
        final Vec3 targetEntityPos;
        final float playerYaw;
        final float targetYaw;
        final boolean targetHadNoAi;
        final boolean targetHadNoGravity;
        final boolean targetShouldFly;
        final boolean targetHadNoPhysics;
        final CompanionKind kind;
        final String targetName;
        final float targetRadius;
        final double wardRadius;
        int ticks;

        private PendingContract(ServerPlayer player, LivingEntity target, CompanionKind kind, boolean targetHadNoAi, ContractPose pose, float targetRadius) {
            this.playerUuid = player.getUUID();
            this.targetUuid = target.getUUID();
            this.dimension = player.level().dimension();
            this.startPlayerPos = player.position();
            this.startTargetPos = target.position();
            this.playerPos = pose.playerPos;
            this.targetPos = pose.targetPos;
            this.targetEntityPos = pose.targetEntityPos;
            this.playerYaw = pose.playerYaw;
            this.targetYaw = pose.targetYaw;
            this.kind = kind;
            this.targetHadNoAi = targetHadNoAi;
            this.targetHadNoGravity = target.isNoGravity();
            this.targetHadNoPhysics = target.noPhysics;
            this.targetShouldFly = pose.targetShouldHover || isControlledFlight(target, kind);
            this.targetName = target.getDisplayName().getString();
            this.targetRadius = targetRadius;
            this.wardRadius = pose.wardRadius;
        }

        static PendingContract create(ServerPlayer player, LivingEntity target, CompanionKind kind, ContractPose pose, float targetRadius) {
            boolean targetHadNoAi = target instanceof Mob mob && mob.isNoAi();
            return new PendingContract(player, target, kind, targetHadNoAi, pose, targetRadius);
        }

        private static boolean isControlledFlight(LivingEntity target, CompanionKind kind) {
            return CompanionEntityClassifier.summonMoveType(target, kind)
                    == com.kuzhi.findme.common.CompanionMoveType.FLY
                    && (!target.onGround() || target.isNoGravity() || target.noPhysics
                    || target.getPose() == Pose.FALL_FLYING);
        }
    }

    private record ContractPose(Vec3 playerPos, Vec3 targetPos, Vec3 targetEntityPos, float playerYaw, float targetYaw, double wardRadius, boolean targetShouldHover) {
    }
}
