package com.kuzhi.findme.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompanionMagicStateTest {
    @Test
    void clampsManaToValidRange() {
        assertEquals(0.0F, new CompanionMagicState(-4.0F, 100.0F).mana());
        assertEquals(100.0F, new CompanionMagicState(140.0F, 100.0F).mana());
        assertEquals(0.0F, new CompanionMagicState(40.0F, -1.0F).maxMana());
    }

    @Test
    void availabilityRequiresPositiveCapacity() {
        assertFalse(CompanionMagicState.EMPTY.available());
        assertTrue(new CompanionMagicState(0.0F, 100.0F).available());
    }

    @Test
    void increasingCapacityAddsTheSameAmountToCurrentMana() {
        FindMeApi.SharedManaProjection projection = FindMeApi.projectedCompanionMana(
                20.0F, 100L, 100.0F, 200.0F, 100L, 0.0F);

        assertEquals(120.0F, projection.mana());
        assertTrue(projection.capacityChanged());
    }

    @Test
    void decreasingCapacityOnlyClampsCurrentMana() {
        FindMeApi.SharedManaProjection projection = FindMeApi.projectedCompanionMana(
                180.0F, 100L, 200.0F, 100.0F, 100L, 0.0F);

        assertEquals(100.0F, projection.mana());
        assertTrue(projection.capacityChanged());
    }

    @Test
    void legacyPoolWithoutCapacityBaselineReceivesOneCorrectionRefill() {
        FindMeApi.SharedManaProjection projection = FindMeApi.projectedCompanionMana(
                28.0F, 100L, -1.0F, 400.0F, 100L, 0.0F);

        assertEquals(400.0F, projection.mana());
        assertTrue(projection.capacityChanged());
    }

    @Test
    void regenerationScalesWithEveryContributingCreature() {
        float perTick = FindMeApi.sharedCompanionRegenPerTick(6, 0.05F);
        FindMeApi.SharedManaProjection projection = FindMeApi.projectedCompanionMana(
                100.0F, 100L, 600.0F, 600.0F, 120L, perTick);

        assertEquals(0.3F, perTick, 0.0001F);
        assertEquals(106.0F, projection.mana(), 0.0001F);
    }
}
