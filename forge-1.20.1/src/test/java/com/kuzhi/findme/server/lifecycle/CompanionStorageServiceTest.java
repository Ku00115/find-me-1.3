package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CompanionStorageServiceTest {
    @Test
    void sanitizedSnapshotClearsTransientFlightControllerState() {
        CompoundTag source = new CompoundTag();
        source.putBoolean("Flying", true);
        source.putBoolean("Hovering", true);
        source.putBoolean("Tackle", true);
        source.putBoolean("TamedDragon", true);
        source.putBoolean("Invulnerable", true);

        CompoundTag sanitized = CompanionStorageService.sanitizedStoredTag(source);

        assertFalse(sanitized.getBoolean("Flying"));
        assertFalse(sanitized.getBoolean("Hovering"));
        assertFalse(sanitized.getBoolean("Tackle"));
        assertFalse(sanitized.getBoolean("Invulnerable"));
        assertTrue(sanitized.getBoolean("TamedDragon"));
    }

    @Test
    void normalStoredCompanionRegeneratesAndKeepsNativeHealthAligned() {
        CompoundTag source = storedHealth(5.0F, 20.0F, 100L);

        CompoundTag healed = CompanionStorageService.storedEntityAfterElapsed(source, 140L, false);

        assertEquals(7.0F, healed.getFloat("CompanionRescueHealth"));
        assertEquals(7.0F, healed.getFloat("Health"));
        assertEquals(140L, healed.getLong("CompanionRescueStoredAt"));
        assertFalse(healed.getBoolean("CompanionRescueCritical"));
    }

    @Test
    void criticalStoredCompanionNeverRegenerates() {
        CompoundTag source = storedHealth(1.0F, 20.0F, 100L);

        CompoundTag healed = CompanionStorageService.storedEntityAfterElapsed(source, 20_000L, true);

        assertEquals(1.0F, healed.getFloat("CompanionRescueHealth"));
        assertEquals(1.0F, healed.getFloat("Health"));
        assertTrue(healed.getBoolean("CompanionRescueCritical"));
    }

    @Test
    void criticalStoredCompanionRepairsAnInconsistentFullHealthSnapshot() {
        CompoundTag source = storedHealth(20.0F, 20.0F, 100L);

        CompoundTag healed = CompanionStorageService.storedEntityAfterElapsed(source, 20_000L, true);

        assertEquals(1.0F, healed.getFloat("CompanionRescueHealth"));
        assertEquals(1.0F, healed.getFloat("Health"));
    }

    private static CompoundTag storedHealth(float health, float maxHealth, long storedAt) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("CompanionRescueHealth", health);
        tag.putFloat("CompanionRescueMaxHealth", maxHealth);
        tag.putFloat("Health", health);
        tag.putLong("CompanionRescueStoredAt", storedAt);
        return tag;
    }
}
