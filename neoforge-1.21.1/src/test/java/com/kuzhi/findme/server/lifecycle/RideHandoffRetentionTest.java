package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionMoveType;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RideHandoffRetentionTest {
    @AfterEach
    void reset() {
        RideHandoffService.resetServerState();
    }

    @Test
    void retainedSourceSurvivesUnrelatedRelease() {
        UUID player = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        RideHandoffService.Source rideSource = new RideHandoffService.Source(
                RideHandoffService.SourceType.COBBLEMON, source, CompanionMoveType.FLY, null, true);

        RideHandoffService.retainSource(player, rideSource, destination);
        RideHandoffService.releaseSourceRetention(player, UUID.randomUUID());

        assertTrue(RideHandoffService.isRetainedSource(player, source));
        RideHandoffService.releaseSourceRetention(player, destination);
        assertFalse(RideHandoffService.isRetainedSource(player, source));
    }

    @Test
    void newerHandoffReplacesOlderRetentionForThePlayer() {
        UUID player = UUID.randomUUID();
        UUID oldSource = UUID.randomUUID();
        UUID newSource = UUID.randomUUID();
        RideHandoffService.retainSource(player, new RideHandoffService.Source(
                RideHandoffService.SourceType.FINDME_MOUNT, oldSource,
                CompanionMoveType.WALK, null, false), UUID.randomUUID());
        RideHandoffService.retainSource(player, new RideHandoffService.Source(
                RideHandoffService.SourceType.SABLE, newSource,
                CompanionMoveType.FLY, null, true), UUID.randomUUID());

        assertFalse(RideHandoffService.isRetainedSource(player, oldSource));
        assertTrue(RideHandoffService.isRetainedSource(player, newSource));
    }
}
