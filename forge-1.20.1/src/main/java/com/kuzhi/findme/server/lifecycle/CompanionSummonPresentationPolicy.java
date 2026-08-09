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
        if (tacticalDeploy) {
            // Protect/guard deployment is an intentional tactical action. It
            // must keep its arrival presentation even when ordinary companion
            // summons are configured to appear instantly.
            return true;
        }
        if (purpose == CompanionAnimationPurpose.RESCUE) {
            return true;
        }
        if (kind == CompanionKind.COMPANION) {
            return false;
        }
        return data.uiSettings().mountSummonAnimations();
    }

}
