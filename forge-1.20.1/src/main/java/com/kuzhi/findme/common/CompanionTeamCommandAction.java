package com.kuzhi.findme.common;

public enum CompanionTeamCommandAction {
    FOLLOW(CompanionTacticalAction.FOLLOW, true),
    GUARD_HERE(CompanionTacticalAction.GUARD_HERE, true),
    PROTECT_OWNER(CompanionTacticalAction.PROTECT_OWNER, true),
    PAUSE_RESUME(null, false),
    ATTACK_TARGET(CompanionTacticalAction.ATTACK_TARGET, true),
    LAND(CompanionTacticalAction.LAND, false),
    RECALL_ALL(null, false),
    CANCEL_PROTECT(CompanionTacticalAction.PROTECT_OWNER, false),
    CANCEL_GUARD(CompanionTacticalAction.GUARD_HERE, false);

    private final CompanionTacticalAction tacticalAction;
    private final boolean deploysTeam;

    CompanionTeamCommandAction(CompanionTacticalAction tacticalAction, boolean deploysTeam) {
        this.tacticalAction = tacticalAction;
        this.deploysTeam = deploysTeam;
    }

    public CompanionTacticalAction tacticalAction() {
        return this.tacticalAction;
    }

    public boolean deploysTeam() {
        return this.deploysTeam;
    }

    public boolean cancelsTacticalAction() {
        return this == CANCEL_PROTECT || this == CANCEL_GUARD;
    }
}
