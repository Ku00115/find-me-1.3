package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.WheelIntentPhase;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.DeadCompanionListPacket;
import com.kuzhi.findme.network.CompanionWheelIntentPacket;
import com.kuzhi.findme.network.CompanionWheelIntentResultPacket;
import com.kuzhi.findme.network.ModNetwork;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client presentation state only. Gameplay truth still comes from server sync. */
public final class ClientCompanionWheelController {
    private static final long LEGACY_SWITCH_TIMEOUT_MS = 8_000L;
    private static final long DEATH_GHOST_MS = 1_200L;
    private static final Map<CompanionKind, UUID> PENDING = new EnumMap<>(CompanionKind.class);
    private static final Map<CompanionKind, Transition> SWITCHING = new EnumMap<>(CompanionKind.class);
    private static final Map<CompanionKind, Map<UUID, CompanionListPacket.Entry>> LAST_KNOWN =
            new EnumMap<>(CompanionKind.class);
    private static final Map<CompanionKind, List<UUID>> LAST_ORDER = new EnumMap<>(CompanionKind.class);
    private static final Map<CompanionKind, Map<UUID, DeathGhost>> DEATH_GHOSTS =
            new EnumMap<>(CompanionKind.class);
    private static Set<UUID> knownDead = Set.of();

    private ClientCompanionWheelController() {
    }

    public static void rememberPending(CompanionKind kind, UUID uuid) {
        if (kind != null && uuid != null) {
            PENDING.put(kind, uuid);
        }
    }

    public static UUID pendingUuid(CompanionKind kind) {
        return kind == null ? null : PENDING.get(kind);
    }

    public static boolean switchInProgress(CompanionKind kind, UUID uuid) {
        Transition transition = currentTransition(kind);
        return transition != null && (uuid == null || transition.uuid.equals(uuid));
    }

    public static void requestSwitch(CompanionKind kind, UUID uuid, CompanionAction action) {
        if (kind != null && uuid != null) {
            SWITCHING.put(kind, new Transition(UUID.randomUUID(), uuid, action, false,
                    false, System.currentTimeMillis()));
        }
    }

    public static UUID sendIntent(CompanionKind kind, UUID uuid, CompanionAction action) {
        if (kind == null || uuid == null || action == null || ModNetwork.channel == null) return null;
        UUID requestId = UUID.randomUUID();
        if (action == CompanionAction.SELECT) {
            rememberPending(kind, uuid);
        } else if (action == CompanionAction.SELECT_SUMMON || action == CompanionAction.RECALL
                || action == CompanionAction.RIDE_HOME) {
            if (action == CompanionAction.RECALL) {
                CompanionDetailPreviewRenderer.suppressLiveMirror(uuid);
            } else if (action == CompanionAction.SELECT_SUMMON) {
                ClientCompanionState.allEntries(kind).stream()
                        .filter(entry -> !uuid.equals(entry.uuid()) && (entry.deployed() || entry.ridden()))
                        .map(CompanionListPacket.Entry::uuid)
                        .forEach(CompanionDetailPreviewRenderer::suppressLiveMirror);
            }
            SWITCHING.put(kind, new Transition(requestId, uuid, action, false,
                    true, System.currentTimeMillis()));
        }
        ModNetwork.sendToServer(new CompanionWheelIntentPacket(requestId, kind, action, uuid,
                ClientCompanionState.serverRevision(kind)));
        return requestId;
    }

    public static void acceptResult(CompanionWheelIntentResultPacket result) {
        if (result == null) return;
        CompanionKind kind = result.kind();
        if (result.action() == CompanionAction.SELECT) {
            if (result.phase() == WheelIntentPhase.REJECTED || result.phase() == WheelIntentPhase.FAILED
                    || result.phase() == WheelIntentPhase.CANCELLED || result.phase() == WheelIntentPhase.TIMED_OUT) {
                PENDING.remove(kind, result.targetUuid());
            }
            return;
        }
        Transition transition = SWITCHING.get(kind);
        if (transition == null || !transition.requestId.equals(result.requestId())
                || !transition.uuid.equals(result.targetUuid()) || transition.action != result.action()) {
            return;
        }
        if (result.phase() == WheelIntentPhase.STARTED) {
            SWITCHING.put(kind, new Transition(transition.requestId, transition.uuid,
                    transition.action, true, transition.serverOwned, transition.startedAt));
            return;
        }
        if (result.phase().terminal()) {
            SWITCHING.remove(kind, transition);
            PENDING.put(kind, transition.uuid);
        }
    }

    public static void acknowledgeSwitch(CompanionKind kind, UUID uuid, boolean accepted) {
        Transition transition = SWITCHING.get(kind);
        if (transition != null && transition.uuid.equals(uuid)) {
            if (accepted) {
                SWITCHING.put(kind, new Transition(transition.requestId, uuid, transition.action, true,
                        transition.serverOwned, transition.startedAt));
            } else {
                SWITCHING.remove(kind, transition);
                PENDING.put(kind, uuid);
            }
        }
    }

    public static void observeRoster(CompanionKind kind, List<CompanionListPacket.Entry> entries) {
        Map<UUID, CompanionListPacket.Entry> snapshots = LAST_KNOWN.computeIfAbsent(kind, ignored -> new HashMap<>());
        for (CompanionListPacket.Entry entry : entries) {
            snapshots.put(entry.uuid(), entry);
        }
        LAST_ORDER.put(kind, entries.stream().map(CompanionListPacket.Entry::uuid).toList());
    }

    public static void observeDead(List<DeadCompanionListPacket.Entry> entries) {
        long now = System.currentTimeMillis();
        Set<UUID> incoming = new HashSet<>();
        for (DeadCompanionListPacket.Entry dead : entries) {
            incoming.add(dead.uuid());
            if (knownDead.contains(dead.uuid())) {
                continue;
            }
            CompanionListPacket.Entry snapshot = LAST_KNOWN
                    .getOrDefault(dead.kind(), Map.of()).get(dead.uuid());
            if (snapshot != null) {
                DEATH_GHOSTS.computeIfAbsent(dead.kind(), ignored -> new HashMap<>())
                        .put(dead.uuid(), new DeathGhost(snapshot,
                                Math.max(0, LAST_ORDER.getOrDefault(dead.kind(), List.of()).indexOf(dead.uuid())),
                                now + DEATH_GHOST_MS));
            }
            PENDING.remove(dead.kind(), dead.uuid());
            Transition transition = SWITCHING.get(dead.kind());
            if (transition != null && transition.uuid.equals(dead.uuid())) {
                SWITCHING.remove(dead.kind());
            }
        }
        knownDead = Set.copyOf(incoming);
    }

    public static List<CompanionListPacket.Entry> deathGhosts(CompanionKind kind) {
        long now = System.currentTimeMillis();
        Map<UUID, DeathGhost> ghosts = DEATH_GHOSTS.get(kind);
        if (ghosts == null || ghosts.isEmpty()) {
            return List.of();
        }
        ghosts.values().removeIf(ghost -> ghost.expiresAt <= now);
        return ghosts.values().stream().map(DeathGhost::entry).toList();
    }

    public static List<CompanionListPacket.Entry> mergeDeathGhosts(CompanionKind kind,
                                                                    List<CompanionListPacket.Entry> entries) {
        long now = System.currentTimeMillis();
        Map<UUID, DeathGhost> ghosts = DEATH_GHOSTS.get(kind);
        if (ghosts == null || ghosts.isEmpty()) {
            return entries;
        }
        ghosts.values().removeIf(ghost -> ghost.expiresAt <= now);
        if (ghosts.isEmpty()) {
            return entries;
        }
        java.util.ArrayList<CompanionListPacket.Entry> merged = new java.util.ArrayList<>(entries);
        ghosts.values().stream().sorted(java.util.Comparator.comparingInt(DeathGhost::index)).forEach(ghost -> {
            if (merged.stream().noneMatch(entry -> entry.uuid().equals(ghost.entry.uuid()))) {
                merged.add(Math.min(ghost.index, merged.size()), ghost.entry);
            }
        });
        return List.copyOf(merged);
    }

    public static CompanionWheelVisualState state(CompanionKind kind, UUID uuid,
                                                   boolean alive, boolean deployed, boolean ridden,
                                                   UUID serverActiveUuid) {
        if (!alive || isDeathGhost(kind, uuid)) {
            return CompanionWheelVisualState.DEAD;
        }
        Transition transition = currentTransition(kind);
        if (transition != null && transition.confirmed && transition.uuid.equals(uuid)) {
            return CompanionWheelVisualState.SWITCHING;
        }
        if (deployed || ridden) {
            return CompanionWheelVisualState.DEPLOYED;
        }
        UUID pending = PENDING.get(kind);
        if (pending != null ? uuid.equals(pending) : uuid.equals(serverActiveUuid)) {
            return CompanionWheelVisualState.PENDING;
        }
        return CompanionWheelVisualState.AVAILABLE;
    }

    private static boolean isDeathGhost(CompanionKind kind, UUID uuid) {
        Map<UUID, DeathGhost> ghosts = DEATH_GHOSTS.get(kind);
        return ghosts != null && ghosts.containsKey(uuid);
    }

    private static Transition currentTransition(CompanionKind kind) {
        Transition transition = SWITCHING.get(kind);
        if (transition != null && !transition.serverOwned
                && System.currentTimeMillis() - transition.startedAt > LEGACY_SWITCH_TIMEOUT_MS) {
            SWITCHING.remove(kind, transition);
            PENDING.put(kind, transition.uuid);
            return null;
        }
        return transition;
    }

    public static void reset() {
        PENDING.clear();
        SWITCHING.clear();
        LAST_KNOWN.clear();
        LAST_ORDER.clear();
        DEATH_GHOSTS.clear();
        knownDead = Set.of();
    }

    private record Transition(UUID requestId, UUID uuid, CompanionAction action, boolean confirmed,
                              boolean serverOwned, long startedAt) {
    }

    private record DeathGhost(CompanionListPacket.Entry entry, int index, long expiresAt) {
    }
}
