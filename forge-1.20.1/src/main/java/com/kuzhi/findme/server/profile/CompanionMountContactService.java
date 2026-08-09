package com.kuzhi.findme.server.profile;

import com.kuzhi.findme.server.core.FindMeDebugLogger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

public final class CompanionMountContactService {
    private static final Map<Class<?>, Optional<Method>> CUSTOM_CONTACT_SEGMENT_METHODS = new HashMap<>();
    private static final Map<Class<?>, Optional<Field>> CUSTOM_CONTACT_SEGMENT_FIELDS = new HashMap<>();
    private static final Map<Class<?>, List<Method>> CUSTOM_PART_METHODS = new HashMap<>();
    private static final Map<Class<?>, List<Field>> CUSTOM_PART_FIELDS = new HashMap<>();
    private static final Map<ContactBoxCacheKey, List<AABB>> CONTACT_BOX_CACHE = new HashMap<>();
    private static long contactBoxCacheTick = Long.MIN_VALUE;

    private CompanionMountContactService() {
    }

    public static boolean canMountByNormalUse(ServerPlayer player, LivingEntity mount) {
        if (mount instanceof AbstractHorse horse) {
            return horse.isTamed() && horse.isSaddled() && CompanionEntityClassifier.isOwnedBy(player, horse);
        }
        Boolean saddled = booleanMethodValue(mount, "isSaddled");
        if (saddled != null && !saddled.booleanValue()) {
            return false;
        }
        Boolean tame = booleanMethodValue(mount, "isTame");
        if (tame == null) {
            tame = booleanMethodValue(mount, "isTamed");
        }
        return tame == null || !tame.booleanValue() || CompanionEntityClassifier.isOwnedBy(player, mount);
    }

    public static boolean intersectsMountContact(LivingEntity mount, AABB otherBox, double inflate) {
        for (AABB box : contactBoxes(mount, inflate)) {
            if (otherBox.intersects(box)) {
                logIntersectionSample(mount, "intersects:true", "intersectsMountContact mount={} other={} inflate={} result=true hit={}",
                        FindMeDebugLogger.entity(mount), FindMeDebugLogger.box(otherBox), inflate, FindMeDebugLogger.box(box));
                return true;
            }
        }
        logIntersectionSample(mount, "intersects:false", "intersectsMountContact mount={} other={} inflate={} result=false",
                FindMeDebugLogger.entity(mount), FindMeDebugLogger.box(otherBox), inflate);
        return false;
    }

    public static boolean contactsIntersect(Entity first, Entity second, double inflate) {
        List<AABB> firstBoxes = contactBoxes(first, inflate);
        List<AABB> secondBoxes = contactBoxes(second, inflate);
        for (AABB firstBox : firstBoxes) {
            for (AABB secondBox : secondBoxes) {
                if (firstBox.intersects(secondBox)) {
                    logIntersectionSample(first, "pairwise:" + second.getId(), "contactsIntersect first={} second={} inflate={} result=true method=pairwise firstBox={} secondBox={}",
                            FindMeDebugLogger.entity(first), FindMeDebugLogger.entity(second), inflate, FindMeDebugLogger.box(firstBox), FindMeDebugLogger.box(secondBox));
                    return true;
                }
            }
        }
        if (sweptContact(first, firstBoxes, second, secondBoxes)) {
            logIntersectionSample(first, "swept:" + second.getId(), "contactsIntersect first={} second={} inflate={} result=true method=swept firstMotion={} secondMotion={}",
                    FindMeDebugLogger.entity(first), FindMeDebugLogger.entity(second), inflate, first.getDeltaMovement(), second.getDeltaMovement());
            return true;
        }
        boolean broad = broadMultipartContact(first, firstBoxes, second, secondBoxes, inflate);
        logIntersectionSample(first, "broad:" + second.getId() + ':' + broad, "contactsIntersect first={} second={} inflate={} result={} method=broad-or-none",
                FindMeDebugLogger.entity(first), FindMeDebugLogger.entity(second), inflate, broad);
        return broad;
    }

    private static void logIntersectionSample(Entity entity, String key, String message, Object... args) {
        long tick = entity.level() == null ? Long.MIN_VALUE : entity.level().getGameTime();
        if (FindMeDebugLogger.shouldLogSample("contact-result", entity.getId() + ":" + key, tick, 20)) {
            FindMeDebugLogger.info("contact-result", message, args);
        }
    }

    public static AABB mountContactBox(LivingEntity mount) {
        AABB box = union(contactBoxes(mount, 0.0));
        double boxWidth = Math.max(box.getXsize(), box.getZsize());
        double boxHeight = box.getYsize();
        double horizontal = Math.max(0.35, Math.min(3.25, boxWidth * 0.22));
        double vertical = Math.max(0.35, Math.min(2.25, boxHeight * 0.18));
        return box.inflate(horizontal, vertical, horizontal);
    }

    public static AABB entityContactBox(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return mountContactBox(living);
        }
        return union(contactBoxes(entity, 0.0));
    }

    public static AABB entityCollisionBox(Entity entity) {
        return union(contactBoxes(entity, 0.0));
    }

    public static AABB entityPhysicalCollisionBox(Entity entity) {
        return union(collectContactBoxes(entity));
    }

    public static AABB previewContactBox(Entity entity, double scale) {
        return union(scaleContactBoxes(collectContactBoxes(entity), scale));
    }

    private static Boolean booleanMethodValue(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            if (method.getReturnType() != Boolean.TYPE && method.getReturnType() != Boolean.class) {
                return null;
            }
            Object value = method.invoke(target);
            return value instanceof Boolean result ? result : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static List<AABB> contactBoxes(Entity entity, double inflate) {
        Level level = entity.level();
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (tick != contactBoxCacheTick) {
            CONTACT_BOX_CACHE.clear();
            contactBoxCacheTick = tick;
        }
        double scale = PackAnimationPresetService.boundsScale(EntityType.getKey(entity.getType()).toString());
        ContactBoxCacheKey key = new ContactBoxCacheKey(level == null ? "" : level.dimension().location().toString(), entity.getId(),
                Double.doubleToLongBits(inflate), Double.doubleToLongBits(scale));
        List<AABB> cached = CONTACT_BOX_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        List<AABB> boxes = scaleContactBoxes(collectContactBoxes(entity), scale);
        if (inflate != 0.0) {
            boxes = boxes.stream().map(box -> box.inflate(inflate)).toList();
        }
        List<AABB> cachedBoxes = List.copyOf(boxes);
        CONTACT_BOX_CACHE.put(key, cachedBoxes);
        return cachedBoxes;
    }

    private static List<AABB> collectContactBoxes(Entity entity) {
        List<AABB> boxes = new ArrayList<>();
        RelatedParts officialParts;
        int reflectedParts = 0;
        int customSegments = 0;
        boxes.add(entity.getBoundingBox());
        officialParts = relatedParts(entity);
        for (PartEntity<?> part : officialParts.parts()) {
            if (part != null) {
                boxes.add(part.getBoundingBox());
            }
        }
        if (officialParts.parts().isEmpty()) {
            List<Entity> reflected = reflectedPartEntities(entity);
            reflectedParts = reflected.size();
            for (Entity part : reflected) {
                if (part != null && part != entity) {
                    boxes.add(part.getBoundingBox());
                }
            }
            customSegments = addCustomContactSegmentBoxes(entity, boxes, 0.0);
        }
        FindMeDebugLogger.contactBoxes(entity, 0.0, boxes, officialParts.parts().size(), officialParts.found(), officialParts.ignored(), officialParts.parts().isEmpty() && reflectedParts > 0, reflectedParts, customSegments > 0);
        return boxes;
    }

    private static List<AABB> scaleContactBoxes(List<AABB> boxes, double scale) {
        double safeScale = Double.isFinite(scale) ? Math.max(0.25, Math.min(4.0, scale)) : 1.0;
        if (Math.abs(safeScale - 1.0) < 0.0001) return List.copyOf(boxes);
        ArrayList<AABB> scaled = new ArrayList<>(boxes.size());
        for (AABB box : boxes) {
            Vec3 center = box.getCenter();
            double halfX = box.getXsize() * safeScale * 0.5;
            double halfY = box.getYsize() * safeScale * 0.5;
            double halfZ = box.getZsize() * safeScale * 0.5;
            scaled.add(new AABB(center.x - halfX, center.y - halfY, center.z - halfZ,
                    center.x + halfX, center.y + halfY, center.z + halfZ));
        }
        return List.copyOf(scaled);
    }

    private static AABB union(List<AABB> boxes) {
        AABB result = boxes.isEmpty() ? new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0) : boxes.get(0);
        for (int i = 1; i < boxes.size(); ++i) {
            result = result.minmax(boxes.get(i));
        }
        return result;
    }

    private static RelatedParts relatedParts(Entity entity) {
        List<PartEntity<?>> result = new ArrayList<>();
        Set<PartEntity<?>> seen = new HashSet<>();
        PartCounter counter = new PartCounter();
        if (entity.isMultipartEntity()) {
            addFilteredParts(entity, java.util.Arrays.asList(entity.getParts()), result, seen, counter);
        }
        Level level = entity.level();
        if (level instanceof ServerLevel serverLevel) {
            addFilteredParts(entity, serverLevel.getPartEntities(), result, seen, counter);
        }
        return new RelatedParts(result, counter.found, counter.ignored);
    }

    private static void addFilteredParts(Entity entity, Iterable<PartEntity<?>> parts, List<PartEntity<?>> result, Set<PartEntity<?>> seen, PartCounter counter) {
        for (PartEntity<?> part : parts) {
            if (part == null || !isPartOf(entity, part) || !seen.add(part)) {
                continue;
            }
            counter.found++;
            if (isUsableRelatedPart(entity, part)) {
                result.add(part);
            } else {
                counter.ignored++;
            }
        }
    }

    private static boolean broadMultipartContact(Entity first, List<AABB> firstBoxes, Entity second, List<AABB> secondBoxes, double inflate) {
        double firstPadding = broadContactPadding(first, firstBoxes, inflate);
        double secondPadding = broadContactPadding(second, secondBoxes, inflate);
        if (firstPadding <= 0.0 && secondPadding <= 0.0) {
            return false;
        }
        AABB firstUnion = union(firstBoxes).inflate(firstPadding);
        AABB secondUnion = union(secondBoxes).inflate(secondPadding);
        return firstUnion.intersects(secondUnion);
    }

    private static boolean sweptContact(Entity first, List<AABB> firstBoxes, Entity second, List<AABB> secondBoxes) {
        Vec3 firstSweep = boundedSweep(first.getDeltaMovement());
        Vec3 secondSweep = boundedSweep(second.getDeltaMovement());
        if (firstSweep.lengthSqr() < 0.0025 && secondSweep.lengthSqr() < 0.0025) {
            return false;
        }
        for (AABB firstBox : firstBoxes) {
            AABB sweptFirst = sweepBox(firstBox, firstSweep);
            for (AABB secondBox : secondBoxes) {
                AABB sweptSecond = sweepBox(secondBox, secondSweep);
                if (sweptFirst.intersects(sweptSecond)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static AABB sweepBox(AABB box, Vec3 motion) {
        AABB swept = box.expandTowards(motion).expandTowards(motion.scale(-1.0));
        return swept.inflate(0.18);
    }

    private static Vec3 boundedSweep(Vec3 motion) {
        if (motion == null || !Double.isFinite(motion.x) || !Double.isFinite(motion.y) || !Double.isFinite(motion.z)) {
            return Vec3.ZERO;
        }
        double length = motion.length();
        if (length <= 3.0) {
            return motion;
        }
        return motion.scale(3.0 / length);
    }

    private static double broadContactPadding(Entity entity, List<AABB> boxes, double inflate) {
        if (boxes.isEmpty()) {
            return 0.0;
        }
        AABB box = union(boxes);
        double width = Math.max(box.getXsize(), box.getZsize());
        double size = Math.max(width, box.getYsize());
        boolean multipartOrLarge = boxes.size() > 1 || entity.isMultipartEntity() || Math.max(entity.getBbWidth(), entity.getBbHeight()) >= 3.0f || size >= 4.0;
        if (!multipartOrLarge) {
            return 0.0;
        }
        return Math.max(0.45, Math.min(2.25, size * 0.10 + inflate * 0.5));
    }

    private static int addCustomContactSegmentBoxes(Entity entity, List<AABB> boxes, double inflate) {
        Vec3[] positions = customContactSegmentPositions(entity);
        if (positions == null || positions.length == 0) {
            return 0;
        }
        double radius = Math.max(1.0, Math.min(4.0, Math.max(entity.getBbWidth(), entity.getBbHeight()) * 0.25)) + inflate;
        double vertical = Math.max(0.75, Math.min(3.0, entity.getBbHeight() * 0.18)) + inflate;
        int added = 0;
        for (Vec3 position : positions) {
            if (position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
                continue;
            }
            boxes.add(new AABB(position.x - radius, position.y - vertical, position.z - radius, position.x + radius, position.y + vertical, position.z + radius));
            added++;
        }
        return added;
    }

    private static Vec3[] customContactSegmentPositions(Entity entity) {
        Optional<Method> method = CUSTOM_CONTACT_SEGMENT_METHODS.computeIfAbsent(entity.getClass(), CompanionMountContactService::findCustomContactSegmentMethod);
        if (method.isPresent()) {
            try {
                Object result = method.get().invoke(entity);
                if (result instanceof Vec3[] positions) {
                    return positions;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        Optional<Field> field = CUSTOM_CONTACT_SEGMENT_FIELDS.computeIfAbsent(entity.getClass(), CompanionMountContactService::findCustomContactSegmentField);
        if (field.isPresent()) {
            try {
                Object result = field.get().get(entity);
                if (result instanceof Vec3[] positions) {
                    return positions;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static Optional<Method> findCustomContactSegmentMethod(Class<?> type) {
        try {
            Method method = type.getMethod("getPosArray");
            if (method.getReturnType().isArray() && method.getReturnType().getComponentType() == Vec3.class && method.getParameterCount() == 0) {
                method.setAccessible(true);
                return Optional.of(method);
            }
        } catch (NoSuchMethodException | SecurityException ignored) {
        }
        return Optional.empty();
    }

    private static Optional<Field> findCustomContactSegmentField(Class<?> type) {
        try {
            Field field = type.getField("posArray");
            if (field.getType().isArray() && field.getType().getComponentType() == Vec3.class) {
                field.setAccessible(true);
                return Optional.of(field);
            }
        } catch (NoSuchFieldException | SecurityException ignored) {
        }
        return Optional.empty();
    }

    private static List<Entity> reflectedPartEntities(Entity entity) {
        List<Entity> parts = new ArrayList<>();
        Set<Entity> seen = new HashSet<>();
        for (Method method : CUSTOM_PART_METHODS.computeIfAbsent(entity.getClass(), CompanionMountContactService::findCustomPartMethods)) {
            try {
                collectEntities(method.invoke(entity), entity, parts, seen);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        for (Field field : CUSTOM_PART_FIELDS.computeIfAbsent(entity.getClass(), CompanionMountContactService::findCustomPartFields)) {
            try {
                collectEntities(field.get(entity), entity, parts, seen);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return parts;
    }

    private static void collectEntities(Object value, Entity owner, List<Entity> parts, Set<Entity> seen) {
        if (value == null) {
            return;
        }
        if (value instanceof Entity entity) {
            addReflectedPart(owner, entity, parts, seen);
            return;
        }
        if (value instanceof Entity[] entities) {
            for (Entity entity : entities) {
                addReflectedPart(owner, entity, parts, seen);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry instanceof Entity entity) {
                    addReflectedPart(owner, entity, parts, seen);
                }
            }
        }
    }

    private static void addReflectedPart(Entity owner, Entity part, List<Entity> parts, Set<Entity> seen) {
        if (part == null || part == owner || !seen.add(part)) {
            return;
        }
        if (part instanceof PartEntity<?> partEntity && !isPartOf(owner, partEntity)) {
            return;
        }
        if (!isUsableRelatedPart(owner, part)) {
            return;
        }
        parts.add(part);
    }

    private static boolean isUsableRelatedPart(Entity owner, Entity part) {
        if (part.level() != owner.level()) {
            return false;
        }
        AABB ownerBox = owner.getBoundingBox();
        AABB partBox = part.getBoundingBox();
        if (!isFiniteBox(partBox) || partBox.getXsize() < 0.001 && partBox.getYsize() < 0.001 && partBox.getZsize() < 0.001) {
            return false;
        }
        double ownerWidth = Math.max(ownerBox.getXsize(), ownerBox.getZsize());
        double partWidth = Math.max(partBox.getXsize(), partBox.getZsize());
        double horizontal = Math.max(4.0, Math.min(28.0, ownerWidth * 2.4 + partWidth));
        double vertical = Math.max(3.0, Math.min(18.0, ownerBox.getYsize() * 0.85 + partBox.getYsize()));
        return ownerBox.inflate(horizontal, vertical, horizontal).intersects(partBox);
    }

    private static boolean isFiniteBox(AABB box) {
        return Double.isFinite(box.minX)
                && Double.isFinite(box.minY)
                && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX)
                && Double.isFinite(box.maxY)
                && Double.isFinite(box.maxZ);
    }

    private static boolean isPartOf(Entity owner, PartEntity<?> part) {
        if (part.getParent() == owner) {
            return true;
        }
        try {
            return part.is(owner);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static List<Method> findCustomPartMethods(Class<?> type) {
        List<Method> result = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() != 0 || !looksLikePartMember(method.getName())) {
                    continue;
                }
                Class<?> returnType = method.getReturnType();
                if (Entity.class.isAssignableFrom(returnType)
                        || returnType.isArray() && Entity.class.isAssignableFrom(returnType.getComponentType())
                        || Iterable.class.isAssignableFrom(returnType)) {
                    try {
                        method.setAccessible(true);
                        result.add(method);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }
        return result;
    }

    private static List<Field> findCustomPartFields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!looksLikePartMember(field.getName())) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                if (Entity.class.isAssignableFrom(fieldType)
                        || fieldType.isArray() && Entity.class.isAssignableFrom(fieldType.getComponentType())
                        || Iterable.class.isAssignableFrom(fieldType)) {
                    try {
                        field.setAccessible(true);
                        result.add(field);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }
        return result;
    }

    private static boolean looksLikePartMember(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("part")
                || lower.contains("segment")
                || lower.contains("hitbox")
                || lower.contains("subentity")
                || lower.contains("sub_entity");
    }

    private record RelatedParts(List<PartEntity<?>> parts, int found, int ignored) {
    }

    private record ContactBoxCacheKey(String dimension, int entityId, long inflateBits, long scaleBits) {
    }

    private static final class PartCounter {
        private int found;
        private int ignored;
    }
}
