package com.kuzhi.findme.client;

import com.kuzhi.findme.common.MountRosterAction;
import com.kuzhi.findme.common.MountRosterSource;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.WheelIntentPhase;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.MountRosterIntentPacket;
import com.kuzhi.findme.network.MountRosterIntentResultPacket;
import java.util.UUID;
import net.minecraft.client.Minecraft;

public final class ClientMountRosterTransactionState {
    private static Pending pending;

    private ClientMountRosterTransactionState() {
    }

    public static boolean send(MountRosterAction action, MountRosterSource source, UUID targetUuid,
                               int sourceSlot, int teamIndex) {
        if (action == null || source == null || targetUuid == null || ModNetwork.channel == null) return false;
        if (pending != null && !pending.terminal() && pending.source == source
                && pending.targetUuid.equals(targetUuid) && pending.action == action) {
            return false;
        }
        UUID requestId = UUID.randomUUID();
        if (action == MountRosterAction.ACTIVATE) {
            var player = Minecraft.getInstance().player;
            if (player != null && player.getVehicle() != null
                    && !targetUuid.equals(player.getVehicle().getUUID())) {
                CompanionDetailPreviewRenderer.suppressLiveMirror(player.getVehicle().getUUID());
            }
        } else if (action == MountRosterAction.RECALL) {
            CompanionDetailPreviewRenderer.suppressLiveMirror(targetUuid);
        }
        pending = new Pending(requestId, action, source, targetUuid, false);
        if (action == MountRosterAction.ACTIVATE) ClientCameraLock.arm();
        ModNetwork.sendToServer(new MountRosterIntentPacket(requestId, action, source, targetUuid,
                sourceSlot, teamIndex, expectedRevision(source)));
        return true;
    }

    public static void acceptResult(MountRosterIntentResultPacket packet) {
        if (packet == null || pending == null || !matches(pending.requestId, pending.action,
                pending.source, pending.targetUuid, packet)) return;
        if (packet.phase().terminal()) {
            pending = new Pending(packet.requestId(), packet.action(), packet.destinationSource(),
                    packet.targetUuid(), true);
            if (packet.phase() != WheelIntentPhase.COMPLETED) {
                ClientMountRosterState.reset();
            }
        }
    }

    static boolean matches(UUID requestId, MountRosterAction action, MountRosterSource source,
                           UUID targetUuid, MountRosterIntentResultPacket packet) {
        return packet != null && requestId != null && requestId.equals(packet.requestId())
                && action == packet.action() && source == packet.destinationSource()
                && targetUuid != null && targetUuid.equals(packet.targetUuid());
    }

    public static boolean switching(MountRosterSource source, UUID targetUuid) {
        return pending != null && !pending.terminal && pending.action == MountRosterAction.ACTIVATE
                && pending.source == source && pending.targetUuid.equals(targetUuid);
    }

    public static boolean inProgress(MountRosterSource source, UUID targetUuid) {
        return pending != null && !pending.terminal && pending.source == source
                && pending.targetUuid.equals(targetUuid);
    }

    public static void reset() {
        pending = null;
    }

    private static long expectedRevision(MountRosterSource source) {
        return switch (source) {
            case FIND_ME -> ClientCompanionState.serverRevision(CompanionKind.MOUNT);
            case VEHICLE -> ClientVehicleState.serverRevision();
        };
    }

    private record Pending(UUID requestId, MountRosterAction action, MountRosterSource source,
                           UUID targetUuid, boolean terminal) {
    }
}
