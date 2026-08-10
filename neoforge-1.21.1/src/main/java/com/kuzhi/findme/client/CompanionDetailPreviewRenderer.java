package com.kuzhi.findme.client;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.compat.cobblemon.CobblemonCompat;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.network.CompanionListPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;

final class CompanionDetailPreviewRenderer {
    static final float DEFAULT_PREVIEW_YAW = 180.0f;
    private static final int MAX_CACHED_PREVIEWS = 64;
    private static final Map<UUID, Entity> PREVIEW_ENTITIES = new HashMap<UUID, Entity>();
    private static final Map<UUID, String> PREVIEW_KEYS = new HashMap<UUID, String>();
    private static final Map<UUID, CompoundTag> LAST_PREVIEW_TAGS = new HashMap<UUID, CompoundTag>();
    private static final Map<UUID, CompoundTag> LAST_PREVIEW_SOURCES = new HashMap<UUID, CompoundTag>();
    private static final Map<UUID, String> LAST_PREVIEW_TYPES = new HashMap<UUID, String>();
    private static final Map<UUID, String> LAST_PREVIEW_KEYS = new HashMap<UUID, String>();
    private static final Map<UUID, String> FAILED_PREVIEW_KEYS = new HashMap<UUID, String>();
    private static final ArrayDeque<UUID> CACHE_ORDER = new ArrayDeque<UUID>();
    private static final Set<Entity> ANIMATED_THIS_FRAME = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Entity, Long> LAST_ENTITY_ANIMATION_TICKS = new IdentityHashMap<>();
    private static long lastPreviewAnimationFrame = Long.MIN_VALUE;
    private static final ArrayDeque<WarmEntry> WARM_QUEUE = new ArrayDeque<WarmEntry>();
    private static final Map<UUID, String> PENDING_KEYS = new HashMap<UUID, String>();
    private static final Map<UUID, Long> LIVE_MIRROR_SUPPRESSION = new HashMap<UUID, Long>();

    void render(GuiGraphics graphics, CompanionListPacket.Entry entry, int x, int y, int w, int h, float yaw, float pitch, float zoom, float cameraOffsetX, float cameraOffsetY, String fallbackType) {
        this.render(graphics, entry, x, y, w, h, yaw, pitch, zoom, cameraOffsetX, cameraOffsetY, fallbackType, 78.0f, 28.0f);
    }

    void render(GuiGraphics graphics, CompanionListPacket.Entry entry, int x, int y, int w, int h, float yaw, float pitch, float zoom, float cameraOffsetX, float cameraOffsetY, String fallbackType, float baseSize, float padding) {
        if (SableVehicleScreenshotPreviewCache.render(graphics, entry.uuid(), x + 2, y + 2, w - 4, h - 4)) {
            return;
        }
        Entity entity = this.previewEntity(entry, fallbackType, true);
        if (entity == null) {
            return;
        }
        stabilizeStoredPreviewPose(entity, yaw, pitch);
        float scale = CompanionPreviewScaler.detailScale(entry, entity, baseSize, padding) * zoom;
        graphics.enableScissor(x + 2, y + 2, x + w - 2, y + h - 2);
        try {
            renderPreviewEntitySafely(graphics, entry, entity, x + w / 2 + Math.round(cameraOffsetX),
                    y + h - 12 + Math.round(cameraOffsetY), scale, yaw, pitch);
        } finally {
            graphics.disableScissor();
        }
    }

    void renderWheel(GuiGraphics graphics, CompanionListPacket.Entry entry, int x, int y, String fallbackType) {
        this.renderWheel(graphics, entry, x, y, fallbackType, CompanionWheelLayout.SLOT_RADIUS);
    }

    void renderWheel(GuiGraphics graphics, CompanionListPacket.Entry entry, int x, int y, String fallbackType, int radius) {
        int clipRadius = Math.max(10, radius - 3);
        if (SableVehicleScreenshotPreviewCache.render(graphics, entry.uuid(), x - radius, y - radius - 12, radius * 2, radius * 2 + 20)) {
            return;
        }
        Entity entity = this.previewEntity(entry, fallbackType, true);
        if (entity == null) {
            enqueueWarm(entry, fallbackType);
            return;
        }
        float scale = CompanionPreviewScaler.wheelScale(entry, entity, Math.max(clipRadius, radius + 4)) * 0.75f;
        int bottomY = y + CompanionPreviewScaler.wheelBottomOffset(entry, entity, scale, clipRadius);
        renderPreviewEntitySafely(graphics, entry, entity, x, bottomY, scale, DEFAULT_PREVIEW_YAW, 0.0f);
    }

    void renderCard(GuiGraphics graphics, CompanionListPacket.Entry entry, int x, int y, int w, int h, String fallbackType) {
        this.renderCard(graphics, entry, x, y, w, h, fallbackType, 1.0f);
    }

    void renderCard(GuiGraphics graphics, CompanionListPacket.Entry entry, int x, int y, int w, int h, String fallbackType, float visualScale) {
        if (w <= 4 || h <= 4) {
            return;
        }
        int pad = 2;
        float clampedVisualScale = Math.max(0.25f, Math.min(1.25f, visualScale));
        int previewWidth = Math.max(1, Math.round((w - pad * 2) * clampedVisualScale));
        int previewHeight = Math.max(1, Math.round((h - pad * 2) * clampedVisualScale));
        int previewX = x + (w - previewWidth) / 2;
        int previewY = y + h - pad - previewHeight;
        if (SableVehicleScreenshotPreviewCache.render(graphics, entry.uuid(), previewX, previewY, previewWidth, previewHeight)) {
            return;
        }
        Entity entity = this.previewEntity(entry, fallbackType, true);
        if (entity == null) {
            enqueueWarm(entry, fallbackType);
            return;
        }
        float radius = Math.max(12.0f, Math.min(w, h) * 0.5f + 4.0f);
        float scale = CompanionPreviewScaler.wheelScale(entry, entity, radius) * clampedVisualScale;
        int bottomY = y + h - 5;
        float yaw = ClientWheelPresentationState.rotateModels()
                ? (System.currentTimeMillis() % 12000L) * 0.03f : DEFAULT_PREVIEW_YAW;
        renderPreviewEntitySafely(graphics, entry, entity, x + w / 2, bottomY, scale, yaw, 0.0f);
    }

    void renderFitted(GuiGraphics graphics, CompanionListPacket.Entry entry, int x, int y, int w, int h,
                      String fallbackType) {
        if (w <= 2 || h <= 2) {
            return;
        }
        if (SableVehicleScreenshotPreviewCache.render(graphics, entry.uuid(), x, y, w, h)) {
            return;
        }
        Entity entity = this.previewEntity(entry, fallbackType, true);
        if (entity == null) {
            enqueueWarm(entry, fallbackType);
            return;
        }
        stabilizeStoredPreviewPose(entity, DEFAULT_PREVIEW_YAW, 0.0f);
        float scale = CompanionPreviewScaler.fittedScale(entry, entity, w - 2.0f, h - 2.0f);
        renderPreviewEntitySafely(graphics, entry, entity, x + w / 2, y + h - 1,
                scale, DEFAULT_PREVIEW_YAW, 0.0f);
    }

    private Entity previewEntity(CompanionListPacket.Entry entry, String fallbackType) {
        return this.previewEntity(entry, fallbackType, true);
    }

    Entity previewEntityForBounds(CompanionListPacket.Entry entry, String fallbackType) {
        return this.previewEntity(entry, fallbackType, true);
    }

    private Entity previewEntity(CompanionListPacket.Entry entry, String fallbackType, boolean createIfMissing) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        if (hasNoPreview(entry)) {
            discardPreview(entry.uuid());
            return null;
        }
        String key = previewKey(entry);
        if (key.equals(FAILED_PREVIEW_KEYS.get(entry.uuid()))) {
            return null;
        }
        // CE dragons own multipart and GeckoLib state on their live world instance.
        // Rendering that same instance in AUI can alternate between the world pass and
        // the card pass, producing a full black card. Always use the detached snapshot.
        boolean liveMirror = !IceAndFirePreviewCompatibility.isDragon(entry.entityType()) && isLiveMirror(entry);
        Entity live = null;
        Entity cached = PREVIEW_ENTITIES.get(entry.uuid());
        if (liveMirror) {
            live = minecraft.level.getEntity(entry.entityId());
            if (!matchesLiveEntity(entry, live)) {
                liveMirror = false;
            }
        }
        if (liveMirror) {
            removeLiveMirror(entry.uuid());
            return live;
        }
        // The static and live presentations are different objects. Retiring a live
        // clone must never leave its mutable multipart state in the static path.
        if (!liveMirror) {
            removeLiveMirror(entry.uuid());
        }
        if (cached != null && key.equals(PREVIEW_KEYS.get(entry.uuid()))) {
            stabilizeStoredPreviewPose(cached, DEFAULT_PREVIEW_YAW, 0.0f);
            touchCache(entry.uuid());
            return cached;
        }
        if (!createIfMissing) {
            return null;
        }
        Entity created = null;
        CompoundTag renderTag = sanitizedPreviewTag(entry);
        if (renderTag != null && !renderTag.isEmpty()) {
            try {
                if (CobblemonCompat.available() && "cobblemon:pokemon".equals(entry.entityType())) {
                    created = CobblemonCompat.createPreview(minecraft.level, renderTag, entry.uuid());
                } else {
                    CompoundTag loadTag = CompanionEntitySnapshots.prepareForLoad(renderTag,
                            0.0, 0.0, 0.0, 0.0f, 0.0f);
                    created = EntityType.loadEntityRecursive(loadTag, (Level)minecraft.level, entity -> {
                        entity.moveTo(0.0, 0.0, 0.0, 0.0f, 0.0f);
                        return entity;
                    });
                }
            } catch (RuntimeException ignored) {
                created = null;
            }
        }
        // A data-less PokemonEntity is not a valid fallback: Cobblemon will render
        // whichever default species state it currently owns, which can look like a
        // different party member. Keep the slot empty until its own NBT can load.
        if (created == null && !"cobblemon:pokemon".equals(entry.entityType())) {
            String type = !entry.entityType().isBlank() ? entry.entityType() : fallbackType;
            created = EntityType.byString((String)type).map(entityType -> entityType.create((Level)minecraft.level)).orElse(null);
            IceAndFirePreviewCompatibility.initializeSyntheticPreview(created, type);
        }
        if (created != null) {
            GeckoLibPreviewCompatibility.initializeDetachedTree(created);
            Entity previous = PREVIEW_ENTITIES.put(entry.uuid(), created);
            if (previous != null && previous != created) {
                releasePreviewEntity(previous);
            }
            CompanionPreviewScaler.remember(created, entry, renderTag);
            PREVIEW_KEYS.put(entry.uuid(), key);
            FAILED_PREVIEW_KEYS.remove(entry.uuid());
            PENDING_KEYS.remove(entry.uuid());
            touchCache(entry.uuid());
        } else {
            PREVIEW_ENTITIES.remove(entry.uuid());
            PREVIEW_KEYS.remove(entry.uuid());
            FAILED_PREVIEW_KEYS.put(entry.uuid(), key);
            PENDING_KEYS.remove(entry.uuid());
        }
        if (created != null) {
            stabilizeStoredPreviewPose(created, DEFAULT_PREVIEW_YAW, 0.0f);
        }
        return created;
    }

    static void enqueueWarm(List<CompanionListPacket.Entry> entries, String fallbackType) {
        for (CompanionListPacket.Entry entry : entries) {
            enqueueWarm(entry, fallbackType);
        }
    }

    static void enqueueWarm(CompanionListPacket.Entry entry, String fallbackType) {
        if (hasNoPreview(entry)) {
            discardPreview(entry.uuid());
            return;
        }
        rememberPreviewTag(entry);
        String key = previewKey(entry);
        if (key.equals(PREVIEW_KEYS.get(entry.uuid())) || key.equals(PENDING_KEYS.get(entry.uuid()))
                || key.equals(FAILED_PREVIEW_KEYS.get(entry.uuid()))) {
            return;
        }
        PENDING_KEYS.put(entry.uuid(), key);
        WARM_QUEUE.addLast(new WarmEntry(entry, fallbackType));
    }

    static void tickWarmCache() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            WARM_QUEUE.clear();
            PENDING_KEYS.clear();
            return;
        }
        if (WARM_QUEUE.isEmpty()) {
            return;
        }
        CompanionDetailPreviewRenderer warmer = new CompanionDetailPreviewRenderer();
        int budget = minecraft.screen instanceof CompanionWheelScreen ? 3 : 1;
        while (budget-- > 0 && !WARM_QUEUE.isEmpty()) {
            WarmEntry warmEntry = WARM_QUEUE.removeFirst();
            String key = previewKey(warmEntry.entry);
            if (key.equals(PREVIEW_KEYS.get(warmEntry.entry.uuid()))) {
                PENDING_KEYS.remove(warmEntry.entry.uuid());
                continue;
            }
            warmer.previewEntity(warmEntry.entry, warmEntry.fallbackType, true);
        }
    }

    static void tickLiveMirrors() {
        // Kept as a lifecycle hook for callers compiled against the old mirror path.
    }

    static void reset() {
        for (Entity entity : PREVIEW_ENTITIES.values()) {
            releasePreviewEntity(entity);
        }
        PREVIEW_ENTITIES.clear();
        PREVIEW_KEYS.clear();
        LAST_PREVIEW_TAGS.clear();
        LAST_PREVIEW_SOURCES.clear();
        LAST_PREVIEW_TYPES.clear();
        LAST_PREVIEW_KEYS.clear();
        FAILED_PREVIEW_KEYS.clear();
        CACHE_ORDER.clear();
        WARM_QUEUE.clear();
        PENDING_KEYS.clear();
        LIVE_MIRROR_SUPPRESSION.clear();
    }

    static void renderPreviewEntity(GuiGraphics graphics, Entity entity, int x, int bottomY, float scale, float yaw, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean liveWorldEntity = isLiveWorldEntity(entity);
        advancePreviewAnimation(minecraft, entity);
        if (!liveWorldEntity) {
            stabilizePreviewPose(entity, yaw, pitch);
            IceAndFirePreviewCompatibility.stabilizeDetachedPreview(entity);
        }
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        Quaternionf rotation = Axis.ZP.rotationDegrees(180.0f);
        Quaternionf pitchRotation = Axis.XP.rotationDegrees(pitch);
        PreviewOrientation original = PreviewOrientation.capture(entity);
        if (!liveWorldEntity) {
            applyPreviewOrientation(entity, yaw, pitch);
        }
        float renderYaw = entity.getYRot();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate((double)x, (double)bottomY, 120.0);
            graphics.pose().mulPose(rotation);
            graphics.pose().mulPose(pitchRotation);
            if (liveWorldEntity) {
                graphics.pose().mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(yaw - DEFAULT_PREVIEW_YAW)));
            }
            graphics.pose().scale(scale, scale, -scale);
            RenderSystem.enableDepthTest();
            RenderSystem.runAsFancy(() -> {
                dispatcher.setRenderShadow(false);
                ClientEntityPreviewRenderGuard.enter();
                try {
                    dispatcher.render(entity, 0.0, 0.0, 0.0, renderYaw, 1.0f, graphics.pose(), (MultiBufferSource)graphics.bufferSource(), 0xF000F0);
                    graphics.flush();
                } finally {
                    ClientEntityPreviewRenderGuard.exit();
                    dispatcher.setRenderShadow(true);
                }
            });
        } finally {
            graphics.pose().popPose();
            original.restore(entity);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableDepthTest();
        }
    }

    private static void renderPreviewEntitySafely(GuiGraphics graphics, CompanionListPacket.Entry entry,
                                                   Entity entity, int x, int bottomY, float scale,
                                                   float yaw, float pitch) {
        try {
            renderPreviewEntity(graphics, entity, x, bottomY, scale, yaw, pitch);
        } catch (RuntimeException exception) {
            quarantineFailedPreview(entry, entity, exception);
        }
    }

    private static void quarantineFailedPreview(CompanionListPacket.Entry entry, Entity entity,
                                                RuntimeException exception) {
        UUID uuid = entry.uuid();
        String key = previewKey(entry);
        boolean firstFailure = !key.equals(FAILED_PREVIEW_KEYS.get(uuid));
        Entity cached = PREVIEW_ENTITIES.remove(uuid);
        PREVIEW_KEYS.remove(uuid);
        PENDING_KEYS.remove(uuid);
        WARM_QUEUE.removeIf(warmEntry -> uuid.equals(warmEntry.entry().uuid()));
        CACHE_ORDER.remove(uuid);
        FAILED_PREVIEW_KEYS.put(uuid, key);
        if (cached != null) {
            releasePreviewEntity(cached);
        } else if (!isLiveWorldEntity(entity)) {
            releasePreviewEntity(entity);
        }
        if (firstFailure) {
            FindMeMod.LOGGER.warn("Disabled preview for {} ({}) after its renderer failed",
                    uuid, entry.entityType(), exception);
        }
    }

    private static void applyPreviewOrientation(Entity entity, float yaw, float pitch) {
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.yRotO = yaw;
        entity.xRotO = pitch;
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = yaw;
            living.yBodyRotO = yaw;
            living.yHeadRot = yaw;
            living.yHeadRotO = yaw;
        }
    }

    static void stabilizePreviewPose(Entity entity, float yaw, float pitch) {
        if (CobblemonCompat.isPokemonPreview(entity)) {
            return;
        }
        entity.tickCount = 1;
        if (entity instanceof LivingEntity living) {
            living.walkAnimation.setSpeed(0.0f);
            living.walkAnimation.update(0.0f, 1.0f);
            living.hurtTime = 0;
            living.deathTime = 0;
            living.swingTime = 0;
            living.attackAnim = 0.0f;
            living.oAttackAnim = 0.0f;
            living.yBodyRot = yaw;
            living.yBodyRotO = yaw;
            living.yHeadRot = yaw;
            living.yHeadRotO = yaw;
        }
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.xRotO = pitch;
        entity.yRotO = yaw;
    }

    static void stabilizeStoredPreviewPose(Entity entity, float yaw, float pitch) {
        if (!isLiveWorldEntity(entity)) {
            stabilizePreviewPose(entity, yaw, pitch);
        }
    }

    private static void advancePreviewAnimation(Minecraft minecraft, Entity entity) {
        if (minecraft.level == null) {
            return;
        }
        long frame = minecraft.getFrameTimeNs();
        if (frame != lastPreviewAnimationFrame) {
            lastPreviewAnimationFrame = frame;
            ANIMATED_THIS_FRAME.clear();
        }
        if (!ANIMATED_THIS_FRAME.add(entity)) {
            return;
        }
        if (isLiveWorldEntity(entity)) {
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        Long previousTick = LAST_ENTITY_ANIMATION_TICKS.put(entity, gameTime);
        boolean advanceAge = previousTick == null || previousTick.longValue() != gameTime;
        float partialTicks = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        CobblemonCompat.tickPreview(entity, partialTicks, advanceAge);
    }

    private static String previewKey(CompanionListPacket.Entry entry) {
        if (hasNoPreview(entry)) {
            return "no_preview";
        }
        if (entry == null) {
            return "";
        }
        if (entry.previewTag() != null && !entry.previewTag().isEmpty()) {
            rememberPreviewTag(entry, entry.previewTag());
        }
        String rememberedType = LAST_PREVIEW_TYPES.get(entry.uuid());
        String rememberedKey = LAST_PREVIEW_KEYS.get(entry.uuid());
        if (rememberedKey != null && (rememberedType == null || rememberedType.equals(entry.entityType()))) {
            return rememberedKey;
        }
        if (rememberedType != null && !rememberedType.equals(entry.entityType())) {
            return entry.entityType() + ":0";
        }
        if (effectivePreviewTag(entry) == null) {
            String cached = PREVIEW_KEYS.get(entry.uuid());
            if (cached != null) {
                return cached;
            }
            String pending = PENDING_KEYS.get(entry.uuid());
            if (pending != null) {
                return pending;
            }
            return entry.entityType() + ":0";
        }
        rememberedKey = LAST_PREVIEW_KEYS.get(entry.uuid());
        return rememberedKey != null ? rememberedKey : entry.entityType() + ":0";
    }

    private static boolean isLiveWorldEntity(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return entity != null && minecraft.level != null && entity.level() == minecraft.level
                && entity.getId() >= 0 && minecraft.level.getEntity(entity.getId()) == entity;
    }

    private static String previewFingerprint(CompoundTag tag) {
        return Integer.toHexString(tag.hashCode());
    }

    private static CompoundTag sanitizedPreviewTag(CompanionListPacket.Entry entry) {
        CompoundTag tag = effectivePreviewTag(entry);
        return tag == null ? null : CompanionPreviewTags.sanitized(tag, entry.entityType());
    }

    private static CompoundTag effectivePreviewTag(CompanionListPacket.Entry entry) {
        if (entry == null || hasNoPreview(entry)) {
            return null;
        }
        CompoundTag tag = entry.previewTag();
        if (tag != null && !tag.isEmpty()) {
            rememberPreviewTag(entry, tag);
            return tag;
        }
        String rememberedType = LAST_PREVIEW_TYPES.get(entry.uuid());
        if (rememberedType != null && !rememberedType.equals(entry.entityType())) {
            return null;
        }
        CompoundTag remembered = LAST_PREVIEW_TAGS.get(entry.uuid());
        return remembered == null ? null : remembered.copy();
    }

    private static void rememberPreviewTag(CompanionListPacket.Entry entry) {
        if (entry != null && entry.previewTag() != null && !entry.previewTag().isEmpty()) {
            rememberPreviewTag(entry, entry.previewTag());
        }
    }

    private static void rememberPreviewTag(CompanionListPacket.Entry entry, CompoundTag tag) {
        CompoundTag previousSource = LAST_PREVIEW_SOURCES.get(entry.uuid());
        String previousType = LAST_PREVIEW_TYPES.get(entry.uuid());
        if (previousSource == tag && entry.entityType().equals(previousType)) {
            touchCache(entry.uuid());
            return;
        }
        CompoundTag sanitized = CompanionPreviewTags.sanitized(tag, entry.entityType());
        LAST_PREVIEW_TAGS.put(entry.uuid(), tag.copy());
        LAST_PREVIEW_SOURCES.put(entry.uuid(), tag);
        LAST_PREVIEW_TYPES.put(entry.uuid(), entry.entityType());
        LAST_PREVIEW_KEYS.put(entry.uuid(), entry.entityType() + ":" + previewFingerprint(sanitized));
        touchCache(entry.uuid());
    }

    private static void touchCache(UUID uuid) {
        if (uuid == null) {
            return;
        }
        CACHE_ORDER.remove(uuid);
        CACHE_ORDER.addLast(uuid);
        while (CACHE_ORDER.size() > MAX_CACHED_PREVIEWS) {
            discardPreview(CACHE_ORDER.removeFirst(), false);
        }
    }

    private static void discardPreview(UUID uuid) {
        discardPreview(uuid, true);
    }

    private static void discardRenderedPreview(UUID uuid) {
        Entity removed = PREVIEW_ENTITIES.remove(uuid);
        PREVIEW_KEYS.remove(uuid);
        FAILED_PREVIEW_KEYS.remove(uuid);
        PENDING_KEYS.remove(uuid);
        WARM_QUEUE.removeIf(entry -> uuid.equals(entry.entry().uuid()));
        if (removed != null) {
            releasePreviewEntity(removed);
        }
    }

    private static void discardPreview(UUID uuid, boolean removeFromOrder) {
        Entity removed = PREVIEW_ENTITIES.remove(uuid);
        PREVIEW_KEYS.remove(uuid);
        LAST_PREVIEW_TAGS.remove(uuid);
        LAST_PREVIEW_SOURCES.remove(uuid);
        LAST_PREVIEW_TYPES.remove(uuid);
        LAST_PREVIEW_KEYS.remove(uuid);
        FAILED_PREVIEW_KEYS.remove(uuid);
        PENDING_KEYS.remove(uuid);
        WARM_QUEUE.removeIf(entry -> uuid.equals(entry.entry().uuid()));
        if (removeFromOrder) {
            CACHE_ORDER.remove(uuid);
        }
        if (removed != null) {
            releasePreviewEntity(removed);
        }
    }

    private static void releasePreviewEntity(Entity entity) {
        ANIMATED_THIS_FRAME.remove(entity);
        LAST_ENTITY_ANIMATION_TICKS.remove(entity);
        CompanionPreviewScaler.forget(entity);
    }

    private static boolean hasNoPreview(CompanionListPacket.Entry entry) {
        return entry != null && entry.previewTag() == null && entry.entityType().isBlank();
    }

    private static boolean isLiveMirror(CompanionListPacket.Entry entry) {
        if (entry == null || !entry.loaded() || !entry.alive() || entry.entityId() < 0
                || !entry.deployed() && !entry.homeResident() || liveMirrorSuppressed(entry.uuid())) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        Entity live = minecraft.level.getEntity(entry.entityId());
        return matchesLiveEntity(entry, live);
    }

    static void suppressLiveMirror(UUID uuid) {
        if (uuid == null) return;
        LIVE_MIRROR_SUPPRESSION.put(uuid, System.nanoTime() + 8_000_000_000L);
        removeLiveMirror(uuid);
    }

    private static void removeLiveMirror(UUID uuid) {
        // Live entities are no longer cloned or owned by the preview cache.
    }

    private static boolean liveMirrorSuppressed(UUID uuid) {
        Long expiresAt = LIVE_MIRROR_SUPPRESSION.get(uuid);
        if (expiresAt == null) return false;
        if (System.nanoTime() < expiresAt) return true;
        LIVE_MIRROR_SUPPRESSION.remove(uuid);
        return false;
    }

    private static boolean matchesLiveEntity(CompanionListPacket.Entry entry, Entity live) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && live != null && live.level() == minecraft.level
                && !live.isRemoved() && minecraft.level.hasChunkAt(live.blockPosition())
                && (minecraft.player == null || !live.isInvisibleTo(minecraft.player))
                && entry.uuid().equals(live.getUUID())
                && entry.entityType().equals(EntityType.getKey(live.getType()).toString());
    }

    private record WarmEntry(CompanionListPacket.Entry entry, String fallbackType) {
    }

    private record PreviewOrientation(float yRot, float yRotO, float xRot, float xRotO, float bodyRot, float bodyRotO, float headRot, float headRotO) {
        static PreviewOrientation capture(Entity entity) {
            if (entity instanceof LivingEntity living) {
                return new PreviewOrientation(entity.getYRot(), entity.yRotO, entity.getXRot(), entity.xRotO, living.yBodyRot, living.yBodyRotO, living.yHeadRot, living.yHeadRotO);
            }
            return new PreviewOrientation(entity.getYRot(), entity.yRotO, entity.getXRot(), entity.xRotO, 0.0f, 0.0f, 0.0f, 0.0f);
        }

        void restore(Entity entity) {
            entity.setYRot(this.yRot);
            entity.yRotO = this.yRotO;
            entity.setXRot(this.xRot);
            entity.xRotO = this.xRotO;
            if (entity instanceof LivingEntity living) {
                living.yBodyRot = this.bodyRot;
                living.yBodyRotO = this.bodyRotO;
                living.yHeadRot = this.headRot;
                living.yHeadRotO = this.headRotO;
            }
        }
    }
}
