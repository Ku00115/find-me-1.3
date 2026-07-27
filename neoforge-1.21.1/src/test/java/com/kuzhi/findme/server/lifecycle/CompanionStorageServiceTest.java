package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

        CompoundTag sanitized = CompanionStorageService.sanitizedStoredTag(source);

        assertFalse(sanitized.getBoolean("Flying"));
        assertFalse(sanitized.getBoolean("Hovering"));
        assertFalse(sanitized.getBoolean("Tackle"));
        assertTrue(sanitized.getBoolean("TamedDragon"));
    }
}
