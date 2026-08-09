package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionKind;
import org.junit.jupiter.api.Test;

class CompanionSummonPresentationPolicyTest {
    @Test
    void ordinaryCompanionSummonsNeverUseArrivalPresentation() {
        var settings = com.kuzhi.findme.common.FindMeUiSettings.defaults()
                .withCompanionSummonAnimations(true);
        org.junit.jupiter.api.Assertions.assertFalse(settings.companionSummonAnimations());
        assertFalse(CompanionSummonPresentationPolicy.enabled(
                new com.kuzhi.findme.server.data.PlayerCompanionData(), CompanionKind.COMPANION,
                CompanionAnimationPurpose.SUMMON, false));
    }

    @Test
    void tacticalCompanionDeploymentKeepsArrivalPresentation() {
        assertTrue(CompanionSummonPresentationPolicy.enabled(
                new com.kuzhi.findme.server.data.PlayerCompanionData(), CompanionKind.COMPANION,
                CompanionAnimationPurpose.SUMMON, true));
        assertTrue(CompanionSummonPresentationPolicy.enabled(
                new com.kuzhi.findme.server.data.PlayerCompanionData(), CompanionKind.MOUNT,
                CompanionAnimationPurpose.SUMMON, true));
    }
}
