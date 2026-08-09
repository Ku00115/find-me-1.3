package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.ui.CompanionMessageService;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.BindingAnimationPolicy;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.ContractCeremonyTimeline;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.common.ModItems;
import com.kuzhi.findme.common.ModParticles;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.profile.PackAnimationPresetService;
import com.kuzhi.findme.network.ContractCameraPacket;
import com.kuzhi.findme.network.ModNetwork;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CompanionContractService {
    private static final Map<UUID, PendingContract> CONTRACTS = new HashMap<>();
    private static final Map<UUID, UUID> TARGET_TO_PLAYER = new HashMap<>();

    private CompanionContractService() {
    }

    public static StartResult start(ServerPlayer player, LivingEntity target, InteractionHand hand) {
        if (target == player || !target.isAlive() || player.level() != target.level()) {
            CompanionMessageService.tell(player, "message.find_me.contract_invalid_target", ChatFormatting.RED);
            return StartResult.REJECTED;
        }
        if (CONTRACTS.containsKey(player.getUUID()) || TARGET_TO_PLAYER.containsKey(target.getUUID())) {
            CompanionMessageService.tell(player, "message.find_me.contract_busy", ChatFormatting.RED);
            return StartResult.REJECTED;
        }
        CompanionBindingService.BindingCheck binding = CompanionBindingService.checkManualBinding(player, target);
        if (!binding.allowed()) {
            CompanionMessageService.tell(player, binding.messageKey(), binding.color(), binding.args());
            return StartResult.REJECTED;
        }
        CompanionKind kind = binding.kind();
        if (!shouldPlayAnimation(player, target)) {
            return bindImmediately(player, target, kind) ? StartResult.COMPLETED : StartResult.REJECTED;
        }
        float targetRadius = visualRadius(target);
        ContractPose pose = arrangePose(player, target);
        PendingContract contract = PendingContract.create(player, target, kind, hand, pose, targetRadius);
        CONTRACTS.put(player.getUUID(), contract);
        TARGET_TO_PLAYER.put(target.getUUID(), player.getUUID());
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setTarget(null);
        }
        applyOpeningPose(player, target, contract);
        ModNetwork.sendToPlayer(player, new ContractCameraPacket(true, target.getId(), ContractCeremonyTimeline.DURATION_TICKS, pose.playerYaw, 8.0f, pose.playerPos.x, pose.playerPos.y, pose.playerPos.z, pose.targetPos.x, pose.targetPos.y, pose.targetPos.z, 1.55f, targetRadius, target.getDisplayName().getString(), ContractCameraPacket.Mode.START, Config.enableContractCinematicCamera));
        Vec3 center = player.position().add(target.position()).scale(0.5);
        playCeremonySound(player, center, SoundEvents.BOOK_PAGE_TURN, 0.72f, 0.92f);
        playCeremonySound(player, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.32f, 1.18f);
        return StartResult.STARTED;
    }

    public static void tick(MinecraftServer server) {
        Iterator<PendingContract> iterator = CONTRACTS.values().iterator();
        while (iterator.hasNext()) {
            PendingContract contract = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(contract.playerUuid);
            ServerLevel contractLevel = server.getLevel(contract.dimension);
            Entity located = contractLevel == null ? null : contractLevel.getEntity(contract.targetUuid);
            if (!(located instanceof LivingEntity target)) {
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
            spawnAtmosphereParticles(player.serverLevel(), contract, target);
            playStageSounds(player, contract);
            if (contract.ticks >= ContractCeremonyTimeline.DURATION_TICKS) {
                complete(iterator, player, target, contract);
            }
        }
    }

    private static boolean canContinue(ServerPlayer player, LivingEntity target, PendingContract contract) {
        return target.isAlive()
                && player.isAlive()
                && player.level().dimension().equals(contract.dimension)
                && target.level().dimension().equals(contract.dimension)
                && player.position().distanceToSqr(contract.startPlayerPos) <= 2.25
                && target.position().distanceToSqr(contract.startTargetPos) <= 6.25
                && player.getHealth() + 0.01f >= contract.playerStartHealth
                && target.getHealth() + 0.01f >= contract.targetStartHealth;
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
        if (!hasNamePaper(player, contract.hand)) {
            ModNetwork.sendToPlayer(player, endingPacket(contract, target.getId(), ContractCameraPacket.Mode.CANCEL));
            CompanionMessageService.tell(player, "message.find_me.contract_cancelled", ChatFormatting.YELLOW, target.getDisplayName());
        } else if (CompanionBindingService.bindAndStore(player, target, contract.kind)) {
            consumeNamePaper(player, contract.hand);
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
        if (contract.ticks == ContractCeremonyTimeline.FOCUS_END_TICK) {
            playCeremonySound(player, center, SoundEvents.ENCHANTMENT_TABLE_USE, 0.68f, 1.16f);
            playCeremonySound(player, center, SoundEvents.AMETHYST_BLOCK_CHIME, 0.42f, 1.34f);
        } else if (contract.ticks == ContractCeremonyTimeline.NAME_END_TICK) {
            playCeremonySound(player, center, SoundEvents.ILLUSIONER_CAST_SPELL, 0.40f, 1.22f);
        } else if (contract.ticks == ContractCeremonyTimeline.RESPONSE_END_TICK) {
            playCeremonySound(player, center, SoundEvents.AMETHYST_BLOCK_CHIME, 0.82f, 1.02f);
            playCeremonySound(player, center, SoundEvents.EVOKER_CAST_SPELL, 0.34f, 1.08f);
        } else if (contract.ticks == 68) {
            playCeremonySound(player, center, SoundEvents.BEACON_ACTIVATE, 0.74f, 1.42f);
            playCeremonySound(player, center, SoundEvents.BEACON_POWER_SELECT, 0.48f, 1.58f);
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
        return new ContractCameraPacket(false, targetEntityId, ContractCeremonyTimeline.ENDING_TICKS, contract.playerYaw, 8.0f, contract.playerPos.x, contract.playerPos.y, contract.playerPos.z, contract.targetPos.x, contract.targetPos.y, contract.targetPos.z, 1.55f, contract.targetRadius, contract.targetName, mode, false);
    }

    private static void holdStill(ServerPlayer player, LivingEntity target, PendingContract contract) {
        player.setDeltaMovement(Vec3.ZERO);
        target.setDeltaMovement(Vec3.ZERO);
        target.setNoGravity(contract.targetShouldFly || contract.targetHadNoGravity);
        if (contract.targetShouldFly) {
            CompanionAnimationHelper.forceFlyingAnimationPose(target);
        }
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setTarget(null);
        }
    }

    private static void faceParticipants(ServerPlayer player, LivingEntity target, PendingContract contract) {
        float targetYaw = yawToward(target.position(), player.position());
        CompanionCinematicOrientationHelper.faceYaw(target, targetYaw);
        Vec3 targetEye = target.position().add(0.0, target.getBbHeight() * 0.62, 0.0);
        Vec3 playerEye = player.position().add(0.0, player.getEyeHeight(), 0.0);
        float pitch = CompanionCinematicOrientationHelper.pitchToward(playerEye.subtract(targetEye));
        target.setXRot(pitch);
        target.xRotO = pitch;
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
        if (contract.ticks % 4 != 0) {
            return;
        }
        Vec3 center = contract.playerPos.add(contract.targetPos).scale(0.5);
        double radius = Math.max(2.0, contract.targetRadius);
        ContractCeremonyTimeline.Stage stage = ContractCeremonyTimeline.stage(contract.ticks);
        if (stage == ContractCeremonyTimeline.Stage.FOCUS || stage == ContractCeremonyTimeline.Stage.NAME) {
            level.sendParticles(ModParticles.CONTRACT_GLYPH.get(), contract.playerPos.x, contract.playerPos.y + 1.2, contract.playerPos.z, 1, 0.18, 0.16, 0.18, 0.0);
        } else if (stage == ContractCeremonyTimeline.Stage.RESPONSE) {
            level.sendParticles(ModParticles.CONTRACT_GLYPH_RESPONSE.get(), contract.targetPos.x, contract.targetPos.y + Math.max(0.8, target.getBbHeight() * 0.55), contract.targetPos.z, 2, radius * 0.18, target.getBbHeight() * 0.12, radius * 0.18, 0.0);
        } else {
            level.sendParticles(ModParticles.CONTRACT_GLYPH_VOW.get(), center.x, center.y + 0.85, center.z, 2, radius * 0.14, 0.16, radius * 0.14, 0.0);
            level.sendParticles((ParticleOptions)ParticleTypes.END_ROD, center.x, center.y + 0.25, center.z, 1, radius * 0.12, 0.03, radius * 0.12, 0.01);
        }
    }

    private static ContractPose arrangePose(ServerPlayer player, LivingEntity target) {
        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position();
        return new ContractPose(playerPos, targetPos, yawToward(playerPos, targetPos));
    }

    private static void applyOpeningPose(ServerPlayer player, LivingEntity target, PendingContract contract) {
        player.setDeltaMovement(Vec3.ZERO);
        target.setDeltaMovement(Vec3.ZERO);
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
        CompanionCinematicOrientationHelper.faceYaw(target, contract.targetStartYaw);
        target.setXRot(contract.targetStartPitch);
        target.xRotO = contract.targetStartPitch;
        target.setNoGravity(contract.targetHadNoGravity);
        target.noPhysics = contract.targetHadNoPhysics;
    }

    private static boolean hasNamePaper(ServerPlayer player, InteractionHand hand) {
        return player.getAbilities().instabuild || !findNamePaper(player, hand).isEmpty();
    }

    private static void consumeNamePaper(ServerPlayer player, InteractionHand hand) {
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack paper = findNamePaper(player, hand);
        if (!paper.isEmpty()) {
            paper.shrink(1);
        }
    }

    private static ItemStack findNamePaper(ServerPlayer player, InteractionHand hand) {
        ItemStack preferred = player.getItemInHand(hand);
        if (preferred.is(ModItems.NAME_PAPER.get())) {
            return preferred;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.is(ModItems.NAME_PAPER.get())) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
    }

    public static void resetServerState(MinecraftServer server) {
        for (PendingContract contract : CONTRACTS.values()) {
            ServerLevel level = server.getLevel(contract.dimension);
            Entity entity = level == null ? null : level.getEntity(contract.targetUuid);
            if (entity instanceof LivingEntity target) {
                restoreTargetAi(target, contract);
            }
        }
        CONTRACTS.clear();
        TARGET_TO_PLAYER.clear();
    }

    private static final class PendingContract {
        final UUID playerUuid;
        final UUID targetUuid;
        final net.minecraft.resources.ResourceKey<Level> dimension;
        final Vec3 startPlayerPos;
        final Vec3 startTargetPos;
        final Vec3 playerPos;
        final Vec3 targetPos;
        final float playerYaw;
        final boolean targetHadNoAi;
        final boolean targetHadNoGravity;
        final boolean targetShouldFly;
        final boolean targetHadNoPhysics;
        final float targetStartYaw;
        final float targetStartPitch;
        final float playerStartHealth;
        final float targetStartHealth;
        final CompanionKind kind;
        final InteractionHand hand;
        final String targetName;
        final float targetRadius;
        int ticks;

        private PendingContract(ServerPlayer player, LivingEntity target, CompanionKind kind, InteractionHand hand, boolean targetHadNoAi, ContractPose pose, float targetRadius) {
            this.playerUuid = player.getUUID();
            this.targetUuid = target.getUUID();
            this.dimension = player.level().dimension();
            this.startPlayerPos = player.position();
            this.startTargetPos = target.position();
            this.playerPos = pose.playerPos;
            this.targetPos = pose.targetPos;
            this.playerYaw = pose.playerYaw;
            this.kind = kind;
            this.hand = hand;
            this.targetHadNoAi = targetHadNoAi;
            this.targetHadNoGravity = target.isNoGravity();
            this.targetHadNoPhysics = target.noPhysics;
            this.targetStartYaw = target.getYRot();
            this.targetStartPitch = target.getXRot();
            this.playerStartHealth = player.getHealth();
            this.targetStartHealth = target.getHealth();
            this.targetShouldFly = isControlledFlight(target, kind);
            this.targetName = target.getDisplayName().getString();
            this.targetRadius = targetRadius;
        }

        static PendingContract create(ServerPlayer player, LivingEntity target, CompanionKind kind, InteractionHand hand, ContractPose pose, float targetRadius) {
            boolean targetHadNoAi = target instanceof Mob mob && mob.isNoAi();
            return new PendingContract(player, target, kind, hand, targetHadNoAi, pose, targetRadius);
        }

        private static boolean isControlledFlight(LivingEntity target, CompanionKind kind) {
            return CompanionEntityClassifier.summonMoveType(target, kind)
                    == com.kuzhi.findme.common.CompanionMoveType.FLY
                    && (!target.onGround() || target.isNoGravity() || target.noPhysics
                    || target.getPose() == Pose.FALL_FLYING);
        }
    }

    private record ContractPose(Vec3 playerPos, Vec3 targetPos, float playerYaw) {
    }

    public enum StartResult {
        REJECTED(false, false),
        STARTED(true, false),
        COMPLETED(true, true);

        private final boolean accepted;
        private final boolean consumePaperNow;

        StartResult(boolean accepted, boolean consumePaperNow) {
            this.accepted = accepted;
            this.consumePaperNow = consumePaperNow;
        }

        public boolean accepted() {
            return accepted;
        }

        public boolean consumePaperNow() {
            return consumePaperNow;
        }
    }
}
