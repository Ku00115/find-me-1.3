package com.kuzhi.findme.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.element.MinecraftElement;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;
import com.kuzhi.findme.server.profile.CompanionMountContactService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

/** A bounded, shared entity preview used by the AUI management cards. */
public final class FindMePreviewElement extends MinecraftElement {
    public static final String TAG_NAME = "findme-preview";
    private static int frameBudget = 8;
    private static long budgetFrame = Long.MIN_VALUE;
    private static final List<CardOverlay> CARD_OVERLAYS = new ArrayList<>();

    public FindMePreviewElement(Document document) {
        super(document, TAG_NAME);
    }

    public static void beginFrame(long frame) {
        budgetFrame = frame;
        frameBudget = 8;
    }

    public static void beginOverlayPass() {
        CARD_OVERLAYS.clear();
    }

    public static void renderQueuedOverlays(GuiGraphics graphics) {
        if (graphics == null || CARD_OVERLAYS.isEmpty()) return;
        RenderSystem.disableDepthTest();
        for (CardOverlay overlay : CARD_OVERLAYS) {
            if ("expanded".equals(overlay.mode())) {
                renderExpandedOverlay(graphics, overlay.name(), overlay.type(), overlay.team(), overlay.x(), overlay.y(),
                        overlay.width(), overlay.height(), overlay.number(), overlay.status(), overlay.statusTone());
            } else if ("profile".equals(overlay.mode())) {
                renderProfileOverlay(graphics, overlay.name(), overlay.type(), overlay.team(), overlay.x(), overlay.y(),
                        overlay.width(), overlay.height());
            } else {
                renderWarehouseOverlay(graphics, overlay.name(), overlay.team(), overlay.x(), overlay.y(),
                        overlay.width(), overlay.height(), overlay.number(), overlay.status(), overlay.statusTone());
            }
        }
        graphics.flush();
        CARD_OVERLAYS.clear();
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        super.drawPhase(poseStack, phase);
        if (phase != Base.RenderPhase.BODY) {
            return;
        }
        String rawUuid = getAttribute("data-uuid");
        String entityType = getAttribute("data-entity-type");
        UUID uuid = parseUuid(rawUuid);
        CompanionListPacketEntry entry = uuid == null
                ? CompanionListPacketEntry.fromType(entityType, getAttribute("data-preview-nbt"))
                : CompanionListPacketEntry.find(uuid);
        if (entry == null) {
            var resident = ClientHouseState.find(uuid);
            entry = resident == null ? CompanionListPacketEntry.fromType(entityType, getAttribute("data-preview-nbt"))
                    : CompanionListPacketEntry.fromHouse(resident);
            if (entry == null) return;
        }
        String overlayMode = getAttribute("data-preview-overlay");
        // Metadata must use the same rectangle as the entity renderer. It is intentionally
        // not derived from the outer card: legacy AUI can place the first flex row differently
        // from its card background while the preview renderer still uses this element's rect.
        Rect rect = Rect.of(this);
        Position position = rect.getBodyRectPosition();
        Size size = rect.getBodyRectSize();
        int width = Math.max(1, (int) Math.round(size.width()));
        int height = Math.max(1, (int) Math.round(size.height()));
        float previewScale = parsePreviewScale(getAttribute("data-preview-scale"));
        Minecraft minecraft = Minecraft.getInstance();
        if (!isVisible(minecraft, position.x, position.y, width, height)) {
            return;
        }
        if (!allowPreview(minecraft)) return;
        GuiGraphics graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        CompanionDetailPreviewRenderer renderer = new CompanionDetailPreviewRenderer();
        com.kuzhi.findme.network.CompanionListPacket.Entry previewEntry = entry.vehicle() != null
                ? entry.vehicle().asPreviewEntry() : entry.companion();
        String fallbackType = entry.vehicle() != null ? "minecraft:boat"
                : previewEntry.moveType() == com.kuzhi.findme.common.CompanionMoveType.FLY ? "minecraft:parrot" : previewEntry.entityType();
        // Do not leave orphaned metadata when the preview entity cannot be created. This is
        // especially important for dead/recovery records whose saved preview tag is optional.
        if (renderer.previewEntityForBounds(previewEntry, fallbackType) == null) return;
        String interactionId = getAttribute("data-interaction-id");
        FindMePreviewInteractionState.View view = FindMePreviewInteractionState.view(interactionId);
        if (interactionId == null || interactionId.isBlank()) {
            renderer.renderCard(graphics, previewEntry, (int) Math.round(position.x), (int) Math.round(position.y),
                    width, height, fallbackType, previewScale);
        } else {
            renderer.render(graphics, previewEntry, (int)Math.round(position.x), (int)Math.round(position.y), width, height,
                    view.yaw(), view.pitch(), view.zoom() * previewScale, view.offsetX(), view.offsetY(), fallbackType);
        }
        if ("warehouse".equals(overlayMode) || "expanded".equals(overlayMode) || "profile".equals(overlayMode)) {
            String name = getAttribute("data-preview-name");
            if (name == null || name.isBlank()) {
                name = entry.vehicle() != null ? entry.vehicle().name() : entry.companion().name();
            }
            CARD_OVERLAYS.add(new CardOverlay(overlayMode, name, getAttribute("data-preview-type"), getAttribute("data-preview-team"),
                    (int) Math.round(position.x), (int) Math.round(position.y), width, height,
                    getAttribute("data-preview-number"), getAttribute("data-preview-status"),
                    getAttribute("data-preview-status-tone")));
        }
        if ("true".equals(getAttribute("data-show-bounds"))) {
            renderBoundsOverlay(graphics, renderer.previewEntityForBounds(previewEntry, fallbackType), previewEntry,
                    (int)Math.round(position.x), (int)Math.round(position.y), width, height, previewScale,
                    parseBoundsScale(getAttribute("data-bounds-scale")), parseBoundsScale(getAttribute("data-effect-scale")),
                    interactionId == null || interactionId.isBlank() ? FindMePreviewInteractionState.view("") : view);
            graphics.flush();
        }
    }

    private static boolean isVisible(Minecraft minecraft, double x, double y, int width, int height) {
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        return x + width > 0 && y + height > 0 && x < guiWidth && y < guiHeight;
    }

    private static boolean allowPreview(Minecraft minecraft) {
        long frame = minecraft.getFrameTimeNs();
        if (frame != budgetFrame) {
            budgetFrame = frame;
            frameBudget = 8;
        }
        return frameBudget-- > 0;
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static float parsePreviewScale(String value) {
        try {
            if (value == null || value.isBlank()) return 1.0f;
            return Math.max(0.25f, Math.min(1.25f, Float.parseFloat(value)));
        } catch (NumberFormatException ignored) {
            return 1.0f;
        }
    }

    private static float parseBoundsScale(String value) {
        try {
            if (value == null || value.isBlank()) return 1.0f;
            return Math.max(0.25f, Math.min(4.0f, Float.parseFloat(value)));
        } catch (NumberFormatException ignored) {
            return 1.0f;
        }
    }

    private static void renderBoundsOverlay(GuiGraphics graphics, Entity entity,
                                            com.kuzhi.findme.network.CompanionListPacket.Entry entry,
                                            int x, int y, int width, int height, float previewScale, float boundsScale,
                                            float effectScale, FindMePreviewInteractionState.View view) {
        if (entity == null) return;
        AABB contact = CompanionMountContactService.previewContactBox(entity, boundsScale);
        AABB effect = scaleAroundCenter(CompanionEntityVisualBoundsService.effectBounds(entity), effectScale);
        float modelScale = CompanionPreviewScaler.detailScale(entry, entity, 78.0f, 28.0f)
                * view.zoom() * Math.max(0.25f, Math.min(1.25f, previewScale));
        int centerX = x + width / 2 + Math.round(view.offsetX());
        int bottomY = y + height - 12 + Math.round(view.offsetY());
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0.0f, 0.0f, 260.0f);
            drawProjectedBox(graphics, entity, effect, centerX, bottomY, modelScale, view.yaw(), view.pitch(), 0xC7E6B447);
            drawProjectedBox(graphics, entity, contact, centerX, bottomY, modelScale, view.yaw(), view.pitch(), 0xE52AC6E8);
            graphics.drawString(Minecraft.getInstance().font,
                    String.format(java.util.Locale.ROOT, "CONTACT %.2fx", boundsScale), x + 5, y + 5, 0xFFBCECF5, true);
            graphics.drawString(Minecraft.getInstance().font,
                    String.format(java.util.Locale.ROOT, "EFFECT %.2fx", effectScale), x + 5, y + 15, 0xFFFFD67A, true);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static AABB scaleAroundCenter(AABB box, double scale) {
        double safe = Double.isFinite(scale) ? Math.max(0.25, Math.min(4.0, scale)) : 1.0;
        var center = box.getCenter();
        double halfX = box.getXsize() * safe * 0.5;
        double halfY = box.getYsize() * safe * 0.5;
        double halfZ = box.getZsize() * safe * 0.5;
        return new AABB(center.x - halfX, center.y - halfY, center.z - halfZ,
                center.x + halfX, center.y + halfY, center.z + halfZ);
    }

    private static void drawProjectedBox(GuiGraphics graphics, Entity entity, AABB box, int centerX, int bottomY,
                                         float scale, float yaw, float pitch, int color) {
        int[][] points = new int[8][2];
        double[] xs = {box.minX - entity.getX(), box.maxX - entity.getX()};
        double[] ys = {box.minY - entity.getY(), box.maxY - entity.getY()};
        double[] zs = {box.minZ - entity.getZ(), box.maxZ - entity.getZ()};
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        int index = 0;
        for (double localY : ys) {
            for (double localZ : zs) {
                for (double localX : xs) {
                    double rotatedX = localX * Math.cos(yawRadians) - localZ * Math.sin(yawRadians);
                    double rotatedZ = localX * Math.sin(yawRadians) + localZ * Math.cos(yawRadians);
                    double rotatedY = localY * Math.cos(pitchRadians) - rotatedZ * Math.sin(pitchRadians);
                    double depthY = rotatedZ * Math.cos(pitchRadians) + localY * Math.sin(pitchRadians);
                    points[index][0] = centerX + (int)Math.round(rotatedX * scale);
                    points[index][1] = bottomY - (int)Math.round((rotatedY + depthY * 0.18) * scale);
                    index++;
                }
            }
        }
        int[][] edges = {{0,1},{0,2},{0,4},{1,3},{1,5},{2,3},{2,6},{3,7},{4,5},{4,6},{5,7},{6,7}};
        for (int[] edge : edges) {
            drawLine(graphics, points[edge[0]][0], points[edge[0]][1], points[edge[1]][0], points[edge[1]][1], color);
        }
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) return;
            int twice = error * 2;
            if (twice >= dy) {
                error += dy;
                x0 += sx;
            }
            if (twice <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    private static void renderWarehouseOverlay(GuiGraphics graphics, String name, String team, int x, int y, int width, int height,
                                               String number, String status, String statusTone) {
        Minecraft minecraft = Minecraft.getInstance();
        float nameScale = 10.0f / 9.0f;
        float teamScale = ("death".equals(statusTone) || "recovery".equals(statusTone))
                ? 7.0f / 9.0f : 6.0f / 9.0f;
        int availableWidth = Math.max(1, width - 11);
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            drawScaledString(graphics, minecraft.font, fitText(minecraft.font, name, availableWidth, nameScale),
                    x + 6, y + height - 31, nameScale, 0xFFFFFFFF);
            drawScaledString(graphics, minecraft.font, fitText(minecraft.font, team, availableWidth, teamScale),
                    x + 6, y + height - 12, teamScale, 0xFFD0D6D8);
            drawScaledString(graphics, minecraft.font, number, x + 6, y + 5, 7.0f / 9.0f, 0xDEFFFFFF);
            if (status != null && !status.isBlank()) {
                float statusScale = 7.0f / 9.0f;
                int statusWidth = Math.max(22, Math.round(minecraft.font.width(status) * statusScale) + 8);
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(0.0f, 0.0f, 295.0f);
                    int statusColor = "recovery".equals(statusTone) ? 0xE6D3A83D : 0xE6A94F4F;
                    graphics.fill(x + width - statusWidth, y + 4, x + width, y + 15, statusColor);
                } finally {
                    graphics.pose().popPose();
                }
                drawScaledString(graphics, minecraft.font, status, x + width - statusWidth + 4, y + 6,
                        statusScale, 0xFFFFFFFF, false);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private static void renderExpandedOverlay(GuiGraphics graphics, String name, String type, String state,
                                              int x, int y, int width, int height, String number, String status,
                                              String statusTone) {
        Minecraft minecraft = Minecraft.getInstance();
        int left = x + Math.max(6, width / 18);
        int availableWidth = Math.max(1, width - (left - x) - Math.max(7, width / 24));
        int infoTop = y + Math.max(34, height - Math.max(49, height / 3));
        float typeScale = clamp(width / 180.0f, 7.0f / 9.0f, 1.0f);
        float nameScale = clamp(width / 115.0f, 10.0f / 9.0f, 14.0f / 9.0f);
        float stateScale = clamp(width / 160.0f, 7.0f / 9.0f, 10.0f / 9.0f);
        drawScaledString(graphics, minecraft.font, fitText(minecraft.font, type, availableWidth, typeScale),
                left, infoTop, typeScale, 0xFFAAB4B8);
        int nameTop = infoTop + Math.max(8, Math.round(9 * typeScale));
        drawScaledString(graphics, minecraft.font, fitText(minecraft.font, name, availableWidth, nameScale),
                left, nameTop, nameScale, 0xFFFFFFFF);
        int stateTop = nameTop + Math.max(11, Math.round(10 * nameScale));
        drawScaledString(graphics, minecraft.font, fitText(minecraft.font, state, availableWidth, stateScale),
                left, stateTop, stateScale, 0xFFD0D6D8);
        drawScaledString(graphics, minecraft.font, number, x + 7, y + 7, 8.0f / 9.0f, 0xDEFFFFFF);
        if (status != null && !status.isBlank()) {
            float statusScale = 6.0f / 9.0f;
            int statusWidth = Math.max(25, Math.round(minecraft.font.width(status) * statusScale) + 10);
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0.0f, 0.0f, 295.0f);
                graphics.fill(x + width - statusWidth, y + 7, x + width, y + 18, 0xFFF1C94D);
            } finally {
                graphics.pose().popPose();
            }
            drawScaledString(graphics, minecraft.font, status, x + width - statusWidth + 5, y + 9,
                    statusScale, 0xFF172027);
        }
    }

    private static void renderProfileOverlay(GuiGraphics graphics, String name, String type, String state,
                                             int x, int y, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        int left = x + 10;
        int bottom = y + height - 10;
        int availableWidth = Math.max(1, width - 20);
        float typeScale = 7.0f / 9.0f;
        float nameScale = 14.0f / 9.0f;
        float stateScale = 8.0f / 9.0f;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0.0f, 0.0f, 294.0f);
            graphics.fill(x, bottom - 55, x + width, y + height, 0xA80A1115);
        } finally {
            graphics.pose().popPose();
        }
        drawScaledString(graphics, minecraft.font, fitText(minecraft.font, type, availableWidth, typeScale),
                left, bottom - 48, typeScale, 0xFF9EAAAF);
        drawScaledString(graphics, minecraft.font, fitText(minecraft.font, name, availableWidth, nameScale),
                left, bottom - 35, nameScale, 0xFFFFFFFF);
        drawScaledString(graphics, minecraft.font, fitText(minecraft.font, state, availableWidth, stateScale),
                left, bottom - 13, stateScale, 0xFFC9D1D4);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String fitText(Font font, String text, int availableWidth, float scale) {
        if (text == null || text.isBlank()) return "";
        int logicalWidth = Math.max(1, (int) Math.floor(availableWidth / Math.max(0.1f, scale)));
        if (font.width(text) <= logicalWidth) return text;
        int ellipsisWidth = font.width("...");
        return font.plainSubstrByWidth(text, Math.max(1, logicalWidth - ellipsisWidth)) + "...";
    }

    private static void drawScaledString(GuiGraphics graphics, Font font, String text, int x, int y, float scale, int color) {
        drawScaledString(graphics, font, text, x, y, scale, color, true);
    }

    private static void drawScaledString(GuiGraphics graphics, Font font, String text, int x, int y, float scale,
                                         int color, boolean dropShadow) {
        if (text == null || text.isBlank()) return;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 300.0f);
            graphics.pose().scale(scale, scale, 1.0f);
            graphics.drawString(font, text, 0, 0, color, dropShadow);
        } finally {
            graphics.pose().popPose();
        }
    }

    static void register() {
        Element.register(TAG_NAME, (document, ignored) -> new FindMePreviewElement(document));
    }

    private record CardOverlay(String mode, String name, String type, String team, int x, int y, int width, int height,
                               String number, String status, String statusTone) {
    }

    record CompanionListPacketEntry(java.util.UUID uuid, com.kuzhi.findme.network.CompanionListPacket.Entry companion,
                                    com.kuzhi.findme.network.VehicleListPacket.Entry vehicle) {
        static CompanionListPacketEntry find(UUID uuid) {
            for (var entry : ClientCompanionState.allEntries(com.kuzhi.findme.common.CompanionKind.MOUNT)) {
                if (entry.uuid().equals(uuid)) return new CompanionListPacketEntry(uuid, entry, null);
            }
            for (var entry : ClientCompanionState.allEntries(com.kuzhi.findme.common.CompanionKind.COMPANION)) {
                if (entry.uuid().equals(uuid)) return new CompanionListPacketEntry(uuid, entry, null);
            }
            for (var entry : ClientVehicleState.allEntries()) {
                if (entry.uuid().equals(uuid)) return new CompanionListPacketEntry(uuid, null, entry);
            }
            for (var entry : ClientCompanionState.deadEntries()) {
                if (entry.uuid().equals(uuid)) return fromDead(entry);
            }
            for (var entry : ClientCompanionState.recoveryEntries()) {
                if (entry.uuid().equals(uuid)) return fromRecovery(entry);
            }
            return null;
        }

        static CompanionListPacketEntry fromDead(com.kuzhi.findme.network.DeadCompanionListPacket.Entry dead) {
            var entry = new com.kuzhi.findme.network.CompanionListPacket.Entry(
                    dead.uuid(), -1, dead.entityType(), dead.name(), false, false, false, false, false, false,
                    null, dead.health(), dead.maxHealth(), dead.armor(), dead.moveType(),
                    com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD, com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD,
                    com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD, com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD,
                    com.kuzhi.findme.common.CompanionEffectStyle.NONE, com.kuzhi.findme.common.CompanionEffectStyle.NONE,
                    com.kuzhi.findme.common.CompanionEffectStyle.NONE, dead.previewTag());
            return new CompanionListPacketEntry(dead.uuid(), entry, null);
        }

        static CompanionListPacketEntry fromRecovery(com.kuzhi.findme.network.RecoveryCompanionListPacket.Entry recovery) {
            var tag = recovery.previewTag();
            float health = tag == null ? 0.0f : tag.getFloat("CompanionRescueHealth");
            float maxHealth = tag == null ? 0.0f : tag.getFloat("CompanionRescueMaxHealth");
            float armor = tag == null ? 0.0f : tag.getInt("CompanionRescueArmor");
            var entry = new com.kuzhi.findme.network.CompanionListPacket.Entry(
                    recovery.uuid(), -1, recovery.entityType(), recovery.name(), false, false, false, false,
                    false, false, null, health, maxHealth, armor, recovery.moveType(),
                    com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD,
                    com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD,
                    com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD,
                    com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD,
                    com.kuzhi.findme.common.CompanionEffectStyle.NONE,
                    com.kuzhi.findme.common.CompanionEffectStyle.NONE,
                    com.kuzhi.findme.common.CompanionEffectStyle.NONE, tag);
            return new CompanionListPacketEntry(recovery.uuid(), entry, null);
        }

        static CompanionListPacketEntry fromHouse(com.kuzhi.findme.network.HousePagePacket.Resident resident) {
            var entry = new com.kuzhi.findme.network.CompanionListPacket.Entry(
                    resident.uuid(), -1, resident.entityType(), resident.name(), false, !resident.dead(), resident.active(), false,
                    true, resident.active(), null, 0.0f, 0.0f, 0.0f, com.kuzhi.findme.common.CompanionMoveType.WALK,
                    com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD, com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD,
                    com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD, com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD,
                    com.kuzhi.findme.common.CompanionEffectStyle.NONE, com.kuzhi.findme.common.CompanionEffectStyle.NONE,
                    com.kuzhi.findme.common.CompanionEffectStyle.NONE, resident.previewTag());
            return new CompanionListPacketEntry(resident.uuid(), entry, null);
        }

        static CompanionListPacketEntry fromType(String entityType, String previewNbt) {
            if (entityType == null || entityType.isBlank()) return null;
            UUID uuid = UUID.nameUUIDFromBytes(("findme-preview:" + entityType).getBytes(StandardCharsets.UTF_8));
            CompoundTag tag = new CompoundTag();
            if (previewNbt != null && !previewNbt.isBlank()) {
                try {
                    tag = CompanionPreviewTags.sanitized(TagParser.parseTag(previewNbt), entityType);
                } catch (Exception ignored) {
                    tag = new CompoundTag();
                }
            }
            var entry = new com.kuzhi.findme.network.CompanionListPacket.Entry(
                    uuid, -1, entityType, entityType, false, true, false, false, true, false,
                    null, 1.0f, 1.0f, 0.0f, com.kuzhi.findme.common.CompanionMoveType.WALK,
                    com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD, com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD,
                    com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD, com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD,
                    com.kuzhi.findme.common.CompanionEffectStyle.NONE, com.kuzhi.findme.common.CompanionEffectStyle.NONE,
                    com.kuzhi.findme.common.CompanionEffectStyle.NONE, tag);
            return new CompanionListPacketEntry(uuid, entry, null);
        }
    }
}
