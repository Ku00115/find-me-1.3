package com.kuzhi.findme.common;

public enum CompanionTeamCommandAction {
    FOLLOW(CompanionTacticalAction.FOLLOW, true),
    GUARD_HERE(CompanionTacticalAction.GUARD_HERE, true),
    PROTECT_OWNER(CompanionTacticalAction.PROTECT_OWNER, true),
    HEAL_OWNER(CompanionTacticalAction.HEAL_OWNER, true),
    MAGIC_PROTECT(CompanionTacticalAction.MAGIC_PROTECT, true),
    MAGIC_SUPPORT(CompanionTacticalAction.MAGIC_SUPPORT, true),
    PAUSE_RESUME(null, false),
    ATTACK_TARGET(CompanionTacticalAction.ATTACK_TARGET, true),
    MAGIC_ATTACK(CompanionTacticalAction.MAGIC_ATTACK, true),
    LAND(CompanionTacticalAction.LAND, false),
    RECALL_ALL(null, false),
    CANCEL_PROTECT(CompanionTacticalAction.PROTECT_OWNER, false),
    CANCEL_MAGIC_PROTECT(CompanionTacticalAction.MAGIC_PROTECT, false),
    CANCEL_MAGIC_SUPPORT(CompanionTacticalAction.MAGIC_SUPPORT, false),
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
        return this == CANCEL_PROTECT || this == CANCEL_MAGIC_PROTECT
                || this == CANCEL_MAGIC_SUPPORT || this == CANCEL_GUARD;
    }
}
