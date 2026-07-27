package com.kuzhi.findme.server.profile;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class CompanionEntityVisualBoundsService {
    public static final int EFFECT_BOUNDS_VERSION = 2;
    private static final String EFFECT_BOUNDS_VERSION_TAG = "CompanionEffectBoundsVersion";
    private static final double ICE_AND_FIRE_DRAGON_RENDER_WIDTH = 0.90;
    private static final double ICE_AND_FIRE_DRAGON_RENDER_HEIGHT = 0.66;
    private static final double ICE_AND_FIRE_DRAGON_FALLBACK_WIDTH_SCALE = 3.25;
    private static final double ICE_AND_FIRE_DRAGON_FALLBACK_HEIGHT_SCALE = 1.55;
    private static final Map<Class<?>, Optional<Method>> DRAGON_RENDER_SIZE_METHODS = new HashMap<>();

    private CompanionEntityVisualBoundsService() {
    }

    public static AABB visualBounds(Entity entity) {
        AABB box = CompanionMountContactService.entityPhysicalCollisionBox(entity);
        VisualDimensions minimum = minimumVisualDimensions(entity);
        if (minimum.isEmpty()) {
            return box;
        }
        return withMinimumDimensions(box, minimum.width(), minimum.height(), minimum.depth());
    }

    /** Render-envelope bounds used by world effects. Ordinary entities still use their physical collision envelope. */
    public static AABB effectBounds(Entity entity) {
        return boundsForDimensions(entity, effectDimensions(entity));
    }

    public static AABB effectBounds(Entity entity, VisualDimensions dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return effectBounds(entity);
        }
        return boundsForDimensions(entity, dimensions);
    }

    public static VisualDimensions previewDimensions(Entity entity) {
        AABB box = visualBounds(entity);
        double width = Math.max(box.getXsize(), box.getZsize());
        double height = box.getYsize();
        double depth = Math.max(0.0, Math.min(box.getXsize(), box.getZsize()));
        return new VisualDimensions(width, height, depth);
    }

    public static VisualDimensions bodyPreviewDimensions(Entity entity) {
        AABB box = entity.getBoundingBox();
        double width = Math.max(Math.max(box.getXsize(), box.getZsize()), entity.getBbWidth());
        double height = Math.max(box.getYsize(), entity.getBbHeight());
        double depth = Math.max(0.0, Math.min(Math.max(box.getXsize(), entity.getBbWidth()), Math.max(box.getZsize(), entity.getBbWidth())));
        return new VisualDimensions(width, height, depth);
    }

    public static VisualDimensions effectDimensions(Entity entity) {
        AABB collision = CompanionMountContactService.entityPhysicalCollisionBox(entity);
        if (!isUsable(collision)) {
            collision = entity.getBoundingBox();
        }
        VisualDimensions physical = dimensions(collision);
        VisualDimensions envelope = isIceAndFireDragon(entity)
                ? iceAndFireDragonEffectDimensions(entity, physical)
                : physical;
        return envelope;
    }

    public static Optional<VisualDimensions> storedEffectDimensions(CompoundTag tag) {
        Optional<VisualDimensions> exact = readDimensions(tag, "CompanionEffectWidth", "CompanionEffectHeight", "CompanionEffectDepth");
        if (exact.isPresent()) {
            if (tag.getInt(EFFECT_BOUNDS_VERSION_TAG) >= EFFECT_BOUNDS_VERSION || !isIceAndFireDragon(tag)) {
                return exact;
            }
            return Optional.of(upgradeLegacyDragonEffectDimensions(exact.get()));
        }
        Optional<VisualDimensions> fallback = readDimensions(tag, "CompanionPreviewBodyWidth", "CompanionPreviewBodyHeight", "CompanionPreviewBodyDepth");
        if (fallback.isPresent() && isIceAndFireDragon(tag)) {
            return Optional.of(upgradeLegacyDragonEffectDimensions(fallback.get()));
        }
        return fallback;
    }

    public static String effectEnvelopeSource(Entity entity) {
        return isIceAndFireDragon(entity) ? "iceandfire_model" : "physical";
    }

    public static float contractCircleRadius(LivingEntity entity) {
        return contractCircleRadius(entity, effectDimensions(entity));
    }

    public static float contractCircleRadius(LivingEntity entity, VisualDimensions dimensions) {
        double radius = horizontalEnclosingRadius(dimensions);
        return (float)Mth.clamp((radius * 1.15 + 0.12) * circleScale(entity), 0.35, 128.0);
    }

    public static float summonCircleRadius(Entity entity) {
        return summonCircleRadius(entity, effectDimensions(entity));
    }

    public static float summonCircleRadius(Entity entity, VisualDimensions dimensions) {
        double radius = horizontalEnclosingRadius(dimensions);
        return (float)Mth.clamp((radius * 1.28 + 0.16) * circleScale(entity), 0.45, 128.0);
    }

    public static float summonCircleRadius(String entityType, VisualDimensions dimensions) {
        double radius = horizontalEnclosingRadius(dimensions);
        return (float)Mth.clamp((radius * 1.28 + 0.16) * PackAnimationPresetService.circleScale(entityType), 0.45, 128.0);
    }

    public static float portalRadius(LivingEntity entity) {
        return portalRadius(entity, effectDimensions(entity));
    }

    public static float portalRadius(LivingEntity entity, VisualDimensions dimensions) {
        double halfWidth = Math.max(dimensions.width(), dimensions.depth()) * 0.5;
        double halfHeight = dimensions.height() * 0.5;
        double radius = Math.sqrt(halfWidth * halfWidth + halfHeight * halfHeight);
        return (float)Mth.clamp((radius * 1.15 + 0.15) * circleScale(entity), 0.55, 128.0);
    }

    public static float portalRadius(String entityType, VisualDimensions dimensions) {
        double halfWidth = Math.max(dimensions.width(), dimensions.depth()) * 0.5;
        double halfHeight = dimensions.height() * 0.5;
        double radius = Math.sqrt(halfWidth * halfWidth + halfHeight * halfHeight);
        return (float)Mth.clamp((radius * 1.15 + 0.15) * PackAnimationPresetService.circleScale(entityType), 0.55, 128.0);
    }

    public static float portalHeight(LivingEntity entity) {
        return portalHeight(entity, effectDimensions(entity));
    }

    public static float portalHeight(LivingEntity entity, VisualDimensions dimensions) {
        return (float)Mth.clamp(dimensions.height(), 0.25, 128.0);
    }

    public static float portalHeight(VisualDimensions dimensions) {
        return (float)Mth.clamp(dimensions.height(), 0.25, 128.0);
    }

    public static float impactRingRadius(Entity entity) {
        return impactRingRadius(entity, effectDimensions(entity));
    }

    public static float impactRingRadius(Entity entity, VisualDimensions dimensions) {
        double halfWidth = Math.max(dimensions.width(), dimensions.depth()) * 0.5;
        double halfHeight = dimensions.height() * 0.5;
        double enclosingRadius = Math.sqrt(halfWidth * halfWidth + halfHeight * halfHeight);
        return (float)Mth.clamp(enclosingRadius * 0.14 * circleScale(entity), 0.12, 32.0);
    }

    public static float impactRingRadius(String entityType, VisualDimensions dimensions) {
        double halfWidth = Math.max(dimensions.width(), dimensions.depth()) * 0.5;
        double halfHeight = dimensions.height() * 0.5;
        double enclosingRadius = Math.sqrt(halfWidth * halfWidth + halfHeight * halfHeight);
        return (float)Mth.clamp(enclosingRadius * 0.14 * PackAnimationPresetService.circleScale(entityType), 0.12, 32.0);
    }

    public static AABB effectBoundsAt(Vec3 anchor, VisualDimensions dimensions) {
        double halfWidth = dimensions.width() * 0.5;
        double halfDepth = dimensions.depth() * 0.5;
        return new AABB(anchor.x - halfWidth, anchor.y, anchor.z - halfDepth,
                anchor.x + halfWidth, anchor.y + dimensions.height(), anchor.z + halfDepth);
    }

    private static double horizontalEnclosingRadius(AABB box) {
        double halfX = box.getXsize() * 0.5;
        double halfZ = box.getZsize() * 0.5;
        return Math.sqrt(halfX * halfX + halfZ * halfZ);
    }

    private static double horizontalEnclosingRadius(VisualDimensions dimensions) {
        double halfX = dimensions.width() * 0.5;
        double halfZ = dimensions.depth() * 0.5;
        return Math.sqrt(halfX * halfX + halfZ * halfZ);
    }

    private static Optional<VisualDimensions> readDimensions(CompoundTag tag, String widthKey, String heightKey, String depthKey) {
        if (tag == null || !tag.contains(widthKey) || !tag.contains(heightKey) || !tag.contains(depthKey)) {
            return Optional.empty();
        }
        double width = tag.getFloat(widthKey);
        double height = tag.getFloat(heightKey);
        double depth = tag.getFloat(depthKey);
        if (!Double.isFinite(width) || !Double.isFinite(height) || !Double.isFinite(depth)
                || width <= 0.0 || height <= 0.0 || depth <= 0.0) {
            return Optional.empty();
        }
        return Optional.of(new VisualDimensions(width, height, depth));
    }

    private static AABB boundsForDimensions(Entity entity, VisualDimensions dimensions) {
        AABB live = entity.getBoundingBox();
        Vec3 center = live.getCenter();
        return new AABB(center.x - dimensions.width() * 0.5, live.minY, center.z - dimensions.depth() * 0.5,
                center.x + dimensions.width() * 0.5, live.minY + dimensions.height(), center.z + dimensions.depth() * 0.5);
    }

    private static VisualDimensions dimensions(AABB box) {
        return new VisualDimensions(box.getXsize(), box.getYsize(), box.getZsize());
    }

    private static VisualDimensions iceAndFireDragonEffectDimensions(Entity entity, VisualDimensions physical) {
        Optional<Double> renderSize = dragonRenderSize(entity);
        if (renderSize.isPresent()) {
            double size = renderSize.get();
            return new VisualDimensions(
                    Math.max(physical.width(), size * ICE_AND_FIRE_DRAGON_RENDER_WIDTH),
                    Math.max(physical.height(), size * ICE_AND_FIRE_DRAGON_RENDER_HEIGHT),
                    Math.max(physical.depth(), size * ICE_AND_FIRE_DRAGON_RENDER_WIDTH));
        }
        return upgradeLegacyDragonEffectDimensions(physical);
    }

    private static VisualDimensions upgradeLegacyDragonEffectDimensions(VisualDimensions physical) {
        return new VisualDimensions(
                physical.width() * ICE_AND_FIRE_DRAGON_FALLBACK_WIDTH_SCALE,
                physical.height() * ICE_AND_FIRE_DRAGON_FALLBACK_HEIGHT_SCALE,
                physical.depth() * ICE_AND_FIRE_DRAGON_FALLBACK_WIDTH_SCALE);
    }

    private static Optional<Double> dragonRenderSize(Entity entity) {
        Optional<Method> method = DRAGON_RENDER_SIZE_METHODS.computeIfAbsent(entity.getClass(), CompanionEntityVisualBoundsService::findDragonRenderSizeMethod);
        if (method.isEmpty()) {
            return Optional.empty();
        }
        try {
            Object value = method.get().invoke(entity);
            if (value instanceof Number number) {
                double size = number.doubleValue();
                if (Double.isFinite(size) && size > 0.0) {
                    return Optional.of(size);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return Optional.empty();
    }

    private static Optional<Method> findDragonRenderSizeMethod(Class<?> type) {
        try {
            Method method = type.getMethod("getRenderSize");
            if (method.getParameterCount() == 0
                    && (method.getReturnType() == Float.TYPE || method.getReturnType() == Double.TYPE
                    || Number.class.isAssignableFrom(method.getReturnType()))) {
                return Optional.of(method);
            }
        } catch (NoSuchMethodException | SecurityException ignored) {
        }
        return Optional.empty();
    }

    private static boolean isIceAndFireDragon(Entity entity) {
        return CompanionEntityClassifier.isIceAndFireDragonKey(entityKey(entity));
    }

    private static boolean isIceAndFireDragon(CompoundTag tag) {
        String type = tag.contains("CompanionRescueType") ? tag.getString("CompanionRescueType") : tag.getString("id");
        return CompanionEntityClassifier.isIceAndFireDragonKey(type);
    }

    private static boolean isUsable(AABB box) {
        return box != null
                && Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ)
                && box.getXsize() > 0.0 && box.getYsize() > 0.0 && box.getZsize() > 0.0;
    }

    private static AABB withMinimumDimensions(AABB box, double width, double height, double depth) {
        double xSize = Math.max(box.getXsize(), width);
        double ySize = Math.max(box.getYsize(), height);
        double zSize = Math.max(box.getZsize(), depth);
        Vec3 center = box.getCenter();
        return new AABB(center.x - xSize * 0.5, box.minY, center.z - zSize * 0.5, center.x + xSize * 0.5, box.minY + ySize, center.z + zSize * 0.5);
    }

    private static double circleScale(Entity entity) {
        return PackAnimationPresetService.circleScale(entityKey(entity));
    }

    private static String entityKey(Entity entity) {
        ResourceLocation id = EntityType.getKey(entity.getType());
        return id == null ? "" : id.toString();
    }

    private static VisualDimensions minimumVisualDimensions(Entity entity) {
        ResourceLocation id = EntityType.getKey(entity.getType());
        if (CompanionEntityClassifier.isIceAndFireDragonKey(id.toString())) {
            return new VisualDimensions(7.5, 5.5, 7.5);
        }
        return VisualDimensions.EMPTY;
    }

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0.0, vector.z);
    }

    public record VisualDimensions(double width, double height, double depth) {
        private static final VisualDimensions EMPTY = new VisualDimensions(0.0, 0.0, 0.0);

        boolean isEmpty() {
            return width <= 0.0 && height <= 0.0 && depth <= 0.0;
        }
    }
}
