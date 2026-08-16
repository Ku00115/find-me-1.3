package com.kuzhi.findme.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CompanionPreviewScalerTest {
    @Test
    void prefersBodyDimensionsOverVisualEffectEnvelope() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("CompanionPreviewBodyWidth", 8.0f);
        tag.putFloat("CompanionPreviewWidth", 30.611f);

        assertEquals(8.0, CompanionPreviewScaler.previewDimension(tag,
                "CompanionPreviewBodyWidth", "CompanionPreviewWidth", 1.0, 0.0));
    }

    @Test
    void fallsBackToLegacyVisualDimension() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("CompanionPreviewWidth", 4.5f);

        assertEquals(4.5, CompanionPreviewScaler.previewDimension(tag,
                "CompanionPreviewBodyWidth", "CompanionPreviewWidth", 1.0, 0.0));
    }
}
