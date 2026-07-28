package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.CompanionTacticalAction;
import com.kuzhi.findme.common.CompanionTeamCommandAction;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.api.client.CompanionCommandTarget;
import com.kuzhi.findme.network.CompanionCommandPacket;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.CompanionTacticalCommandPacket;
import com.kuzhi.findme.network.CompanionTeamTacticalCommandPacket;
import com.kuzhi.findme.network.CompanionWheelIntentPacket;
import com.kuzhi.findme.network.ModNetwork;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

final class ClientCompanionCommandTarget {
    private static CompanionKind rememberedKind;
    private static UUID rememberedUuid;

    private ClientCompanionCommandTarget() {
    }

    static CompanionCommandTarget resolve() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }

        CompanionCommandTarget target = find(minecraft.player.getVehicle());
        if (target != null) {
            return target;
        }
        if (minecraft.hitResult instanceof EntityHitResult entityHit) {
            target = find(entityHit.getEntity());
            if (target != null) {
                return target;
            }
        }
        target = find(rememberedKind, rememberedUuid);
        if (target != null) {
            return target;
        }
        target = firstDeployed(CompanionKind.COMPANION);
        if (target != null) {
            return target;
        }
        target = firstDeployed(CompanionKind.MOUNT);
        if (target != null) {
            return target;
        }
        return activeMount();
    }

    static void remember(CompanionKind kind, UUID uuid) {
        if (kind != null && uuid != null) {
            rememberedKind = kind;
            rememberedUuid = uuid;
        }
    }

    static boolean rideHome(CompanionCommandTarget target) {
        if (!canRideHome(target)) {
            showUnavailable();
            return false;
        }
        remember(target.kind(), target.entry().uuid());
        ClientCompanionWheelController.sendIntent(target.kind(), target.entry().uuid(), CompanionAction.RIDE_HOME);
        return true;
    }

    static boolean openWaystones(CompanionCommandTarget target) {
        if (!canUseWaystones(target)) {
            showUnavailable();
            return false;
        }
        remember(target.kind(), target.entry().uuid());
        ModNetwork.sendToServer(new com.kuzhi.findme.network.WaystoneDestinationRequestPacket(target.entry().uuid()));
        return true;
    }

    static boolean summonOrRecall(CompanionCommandTarget target) {
        if (target == null || target.entry() == null || !target.entry().alive()) {
            showUnavailable();
            return false;
        }
        remember(target.kind(), target.entry().uuid());
        CompanionAction action = target.entry().deployed() || target.entry().ridden()
                ? CompanionAction.RECALL : CompanionAction.SELECT_SUMMON;
        ClientCompanionWheelController.sendIntent(target.kind(), target.entry().uuid(), action);
        return true;
    }

    static boolean toggleEscort(CompanionCommandTarget target) {
        if (target == null || target.kind() != CompanionKind.COMPANION || !target.entry().alive()) {
            showUnavailable();
            return false;
        }
        remember(target.kind(), target.entry().uuid());
        ClientCompanionWheelController.sendIntent(target.kind(), target.entry().uuid(), CompanionAction.ESCORT);
        return true;
    }

    static boolean goHome(CompanionCommandTarget target) {
        if (target == null || !ClientFindMeModuleState.enabled(FindMeModule.HOUSES)
                || !target.entry().alive()) {
            showUnavailable();
            return false;
        }
        remember(target.kind(), target.entry().uuid());
        ClientCompanionWheelController.sendIntent(target.kind(), target.entry().uuid(), CompanionAction.GO_HOME);
        return true;
    }

    static boolean canIssueTacticalOrder(CompanionCommandTarget target) {
        return target != null && target.entry() != null && target.entry().alive();
    }

    static boolean canMoveToCrosshair(CompanionCommandTarget target, net.minecraft.core.BlockPos targetPos) {
        return canIssueTacticalOrder(target) && targetPos != null;
    }

    static boolean canAttackCrosshair(CompanionCommandTarget target, int targetEntityId) {
        return canIssueTacticalOrder(target) && targetEntityId >= 0;
    }

    static boolean canLand(CompanionCommandTarget target) {
        return canIssueTacticalOrder(target) && target.entry().moveType() == CompanionMoveType.FLY
                && (target.entry().deployed() || target.entry().ridden());
    }

    static boolean follow(CompanionCommandTarget target) {
        return sendTactical(target, CompanionTacticalAction.FOLLOW, null, -1);
    }

    static boolean hold(CompanionCommandTarget target) {
        return sendTactical(target, CompanionTacticalAction.HOLD, null, -1);
    }

    static boolean moveToCrosshair(CompanionCommandTarget target, net.minecraft.core.BlockPos targetPos) {
        if (targetPos == null) {
            showUnavailable();
            return false;
        }
        return sendTactical(target, CompanionTacticalAction.MOVE_TO, targetPos, -1);
    }

    static boolean attackCrosshair(CompanionCommandTarget target, int targetEntityId) {
        if (targetEntityId < 0) {
            showUnavailable();
            return false;
        }
        return sendTactical(target, CompanionTacticalAction.ATTACK_TARGET, null, targetEntityId);
    }

    static boolean land(CompanionCommandTarget target) {
        return sendTactical(target, CompanionTacticalAction.LAND, null, -1);
    }

    static boolean stopCurrent(CompanionCommandTarget target) {
        return sendTactical(target, CompanionTacticalAction.STOP_CURRENT, null, -1);
    }

    static boolean guardHere(CompanionCommandTarget target) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        return sendTactical(target, CompanionTacticalAction.GUARD_HERE,
                minecraft.player.blockPosition(), -1);
    }

    static boolean protectOwner(CompanionCommandTarget target) {
        return sendTactical(target, CompanionTacticalAction.PROTECT_OWNER, null, -1);
    }

    static boolean hasAction(CompanionCommandTarget target, CompanionTacticalAction action) {
        return target != null && target.entry() != null && target.entry().tacticalAction() == action;
    }
    static boolean sendTeamCommand(CompanionTeamTarget target, int teamIndex, CompanionTeamCommandAction action,
                                    net.minecraft.core.BlockPos targetPos, int targetEntityId) {
        if (target == null || teamIndex < 0 || action == null || ClientCompanionTeamState.currentMembers(target).isEmpty()) {
            showUnavailable(); return false;
        }
        ModNetwork.sendToServer(new CompanionTeamTacticalCommandPacket(target, teamIndex, action, targetPos, targetEntityId));
        return true;
    }

    private static boolean sendTactical(CompanionCommandTarget target, CompanionTacticalAction action,
                                        net.minecraft.core.BlockPos targetPos, int targetEntityId) {
        if (!canIssueTacticalOrder(target)) {
            showUnavailable();
            return false;
        }
        remember(target.kind(), target.entry().uuid());
        ModNetwork.sendToServer(new CompanionTacticalCommandPacket(target.kind(), target.entry().uuid(), action,
                targetPos, targetEntityId));
        return true;
    }

    static CompanionCommandTarget fromEntry(CompanionKind kind, CompanionListPacket.Entry entry) {
        if (kind == null || entry == null) return null;
        int wheelIndex = ClientCompanionState.serverWheelIndex(kind, entry.uuid());
        remember(kind, entry.uuid());
        return new CompanionCommandTarget(kind, wheelIndex, entry);
    }

    static void sync() {
        if (ModNetwork.channel == null) {
            return;
        }
        if (ClientFindMeModuleState.enabled(FindMeModule.RIDING)) {
            ModNetwork.sendToServer(new CompanionCommandPacket(CompanionKind.MOUNT, CompanionAction.SYNC, -1));
        }
        if (ClientFindMeModuleState.enabled(FindMeModule.COMPANIONS)) {
            ModNetwork.sendToServer(new CompanionCommandPacket(CompanionKind.COMPANION, CompanionAction.SYNC, -1));
        }
    }

    static void showNoTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("message.find_me.command_no_target"), true);
        }
    }

    static void showUnavailable() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("message.find_me.command_unavailable"), true);
        }
    }

    private static CompanionCommandTarget find(Entity entity) {
        if (entity == null) {
            return null;
        }
        CompanionCommandTarget target = find(CompanionKind.COMPANION, entity.getUUID());
        return target != null ? target : find(CompanionKind.MOUNT, entity.getUUID());
    }

    private static CompanionCommandTarget find(CompanionKind kind, UUID uuid) {
        if (kind == null || uuid == null) {
            return null;
        }
        FindMeModule module = kind == CompanionKind.MOUNT ? FindMeModule.RIDING : FindMeModule.COMPANIONS;
        if (!ClientFindMeModuleState.enabled(module)) {
            return null;
        }
        for (CompanionListPacket.Entry entry : ClientCompanionState.entries(kind)) {
            if (uuid.equals(entry.uuid()) && entry.alive()) {
                int wheelIndex = ClientCompanionState.serverWheelIndex(kind, uuid);
                return new CompanionCommandTarget(kind, wheelIndex, entry);
            }
        }
        return null;
    }

    private static CompanionCommandTarget firstDeployed(CompanionKind kind) {
        for (CompanionListPacket.Entry entry : ClientCompanionState.entries(kind)) {
            if (entry.alive() && (entry.ridden() || entry.deployed())) {
                CompanionCommandTarget target = find(kind, entry.uuid());
                if (target != null) {
                    return target;
                }
            }
        }
        return null;
    }

    private static CompanionCommandTarget activeMount() {
        List<CompanionListPacket.Entry> entries = ClientCompanionState.entries(CompanionKind.MOUNT);
        int active = ClientCompanionState.activeIndex(CompanionKind.MOUNT);
        return active >= 0 && active < entries.size() ? find(CompanionKind.MOUNT, entries.get(active).uuid()) : null;
    }

    static boolean canRideHome(CompanionCommandTarget target) {
        return target != null && ClientFindMeModuleState.enabled(FindMeModule.HOUSES)
                && target.kind() == CompanionKind.MOUNT && target.entry().alive()
                && target.entry().hasHome();
    }

    static boolean canUseWaystones(CompanionCommandTarget target) {
        return target != null && target.kind() == CompanionKind.MOUNT && target.entry().alive()
                && ClientFindMeModuleState.enabled(FindMeModule.RIDING)
                && net.neoforged.fml.ModList.get().isLoaded("waystones");
    }

}
