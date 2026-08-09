package com.kuzhi.findme.api;

import java.util.UUID;

/** Immutable status for an idempotent server-side deploy or store request. */
public record CompanionActionRequest(
        UUID requestId,
        UUID ownerUuid,
        UUID companionUuid,
        Action action,
        State state,
        Reason reason,
        long submittedAt,
        long expiresAt,
        long completedAt) {

    public boolean terminal() {
        return state != State.PENDING;
    }

    public enum Action {
        DEPLOY,
        STORE
    }

    public enum State {
        PENDING,
        SUCCEEDED,
        REJECTED,
        TIMED_OUT
    }

    public enum Reason {
        NONE,
        ALREADY_SATISFIED,
        INVALID_REQUEST,
        NOT_REGISTERED,
        DEAD,
        BUSY,
        UNAVAILABLE,
        OWNER_OFFLINE,
        RELEASED,
        TIMEOUT
    }
}
