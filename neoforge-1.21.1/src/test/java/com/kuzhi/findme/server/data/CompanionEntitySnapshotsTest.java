package com.kuzhi.findme.server.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class CompanionEntitySnapshotsTest {
    @Test
    void storedPreviewRemovesWorldIdentityButPreservesModData() {
        CompoundTag source = new CompoundTag();
        source.putUUID("UUID", UUID.randomUUID());
        source.put("Pos", new ListTag());
        source.put("Motion", new ListTag());
        source.put("Passengers", new ListTag());
        source.putString("id", "example:dragon");
        source.putInt("Variant", 7);

        CompoundTag preview = CompanionEntitySnapshots.previewStoredEntityTag(source);

        assertFalse(preview.contains("UUID"));
        assertFalse(preview.contains("Pos"));
        assertFalse(preview.contains("Motion"));
        assertFalse(preview.contains("Passengers"));
        assertEquals("example:dragon", preview.getString("id"));
        assertEquals(7, preview.getInt("Variant"));
        assertTrue(source.contains("UUID"));
    }
}
