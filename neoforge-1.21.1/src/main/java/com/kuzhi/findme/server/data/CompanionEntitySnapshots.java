package com.kuzhi.findme.server.data;


import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

public final class CompanionEntitySnapshots {
    private CompanionEntitySnapshots() {
    }

    public static CompoundTag previewEntityTag(Entity entity, String entityType) {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        tag.putString("id", entityType);
        writePreviewBounds(entity, tag);
        tag.remove("UUID");
        tag.remove("Pos");
        tag.remove("Motion");
        tag.remove("Rotation");
        tag.remove("Passengers");
        tag.remove("Leash");
        tag.remove("NoAI");
        tag.remove("NoGravity");
        tag.remove("Invisible");
        return tag;
    }

    public static void writePreviewBounds(Entity entity, CompoundTag tag) {
        CompanionEntityVisualBoundsService.VisualDimensions dimensions = CompanionEntityVisualBoundsService.previewDimensions(entity);
        CompanionEntityVisualBoundsService.VisualDimensions bodyDimensions = CompanionEntityVisualBoundsService.bodyPreviewDimensions(entity);
        CompanionEntityVisualBoundsService.VisualDimensions effectDimensions = CompanionEntityVisualBoundsService.effectDimensions(entity);
        tag.putFloat("CompanionPreviewWidth", (float)dimensions.width());
        tag.putFloat("CompanionPreviewHeight", (float)dimensions.height());
        tag.putFloat("CompanionPreviewDepth", (float)dimensions.depth());
        tag.putFloat("CompanionPreviewBodyWidth", (float)bodyDimensions.width());
        tag.putFloat("CompanionPreviewBodyHeight", (float)bodyDimensions.height());
        tag.putFloat("CompanionPreviewBodyDepth", (float)bodyDimensions.depth());
        tag.putFloat("CompanionEffectWidth", (float)effectDimensions.width());
        tag.putFloat("CompanionEffectHeight", (float)effectDimensions.height());
        tag.putFloat("CompanionEffectDepth", (float)effectDimensions.depth());
        tag.putInt("CompanionEffectBoundsVersion", CompanionEntityVisualBoundsService.EFFECT_BOUNDS_VERSION);
    }

    public static String storedEntityName(CompoundTag tag, UUID uuid) {
        String id;
        block6: {
            String name;
            if (tag.contains("CompanionRescueName") && !(name = tag.getString("CompanionRescueName")).isBlank()) {
                return name;
            }
            if (tag.contains("CustomName")) {
                String customName = tag.getString("CustomName");
                if (!customName.isBlank()) {
                    return customName;
                }
            }
        }
        if (!(id = tag.getString("id")).isBlank()) {
            int separator = id.indexOf(58);
            return separator >= 0 && separator + 1 < id.length() ? id.substring(separator + 1) : id;
        }
        return uuid.toString().substring(0, 8);
    }

    public static String storedEntityType(CompoundTag tag) {
        String type;
        if (tag.contains("CompanionRescueType") && !(type = tag.getString("CompanionRescueType")).isBlank()) {
            return type;
        }
        return tag.getString("id");
    }
}
