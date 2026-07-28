package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.data.PlayerCompanionData;

/** One policy boundary for optional summon presentation. Safety-critical rescue remains active. */
final class CompanionSummonPresentationPolicy {
    private CompanionSummonPresentationPolicy() {
    }

    static boolean enabled(PlayerCompanionData data, CompanionKind kind,
                           CompanionAnimationPurpose purpose, boolean tacticalDeploy) {
        if (data == null || kind == null || purpose == null) {
            return true;
        }
        if (purpose == CompanionAnimationPurpose.RESCUE) {
            return true;
        }
        return kind == CompanionKind.MOUNT
                ? data.uiSettings().mountSummonAnimations()
                : data.uiSettings().companionSummonAnimations();
    }

    static boolean ordinaryCompanionApproach(PlayerCompanionData data, CompanionKind kind,
                                               CompanionAnimationPurpose purpose,
                                               boolean tacticalDeploy) {
        return kind == CompanionKind.COMPANION
                && purpose == CompanionAnimationPurpose.SUMMON
                && !tacticalDeploy
                && enabled(data, kind, purpose, false);
    }
}
