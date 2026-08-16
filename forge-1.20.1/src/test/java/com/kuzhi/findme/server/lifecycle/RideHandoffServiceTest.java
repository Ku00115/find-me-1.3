package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionMoveType;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RideHandoffServiceTest {
    @Test
    void airborneVehicleDoesNotDependOnCreatureClassification() {
        assertTrue(RideHandoffService.airborneFor(RideHandoffService.SourceType.ENTITY_VEHICLE,
                CompanionMoveType.WALK, 24.0));
        assertFalse(RideHandoffService.airborneFor(RideHandoffService.SourceType.ENTITY_VEHICLE,
                CompanionMoveType.FLY, 4.0));
    }

    @Test
    void biologicalMountStillRequiresFlyingMovementType() {
        assertFalse(RideHandoffService.airborneFor(RideHandoffService.SourceType.FINDME_MOUNT,
                CompanionMoveType.WALK, 24.0));
        assertTrue(RideHandoffService.airborneFor(RideHandoffService.SourceType.FINDME_MOUNT,
                CompanionMoveType.FLY, 24.0));
    }

    @Test
    void vehicleMotionCanFeedAnySupportedMountMovementType() {
        RideHandoffService.MotionSnapshot motion = new RideHandoffService.MotionSnapshot(
                RideHandoffService.SourceType.ENTITY_VEHICLE, UUID.randomUUID(),
                CompanionMoveType.WALK, new Vec3(1.2, 0.4, 0.3), 30.0f, 0.0f, true);

        assertTrue(motion.compatibleWith(CompanionMoveType.WALK));
        assertTrue(motion.compatibleWith(CompanionMoveType.FLY));
        assertTrue(motion.velocityFor(CompanionMoveType.WALK).horizontalDistance() > 1.0);
        assertTrue(motion.velocityFor(CompanionMoveType.FLY).y > 0.0);
    }
}
