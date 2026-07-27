package com.kuzhi.findme.client;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.network.SableVehiclePreviewCapturePacket;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class SableVehicleScreenshotPreviewCache {
    private static final int PREVIEW_SIZE = 160;
    private static final int MAX_PENDING_ATTEMPTS = 6;
    private static final Map<UUID, DynamicTexture> TEXTURES = new HashMap<>();
    private static final Map<UUID, ResourceLocation> LOCATIONS = new HashMap<>();
    private static final Map<UUID, PendingCapture> PENDING = new HashMap<>();
    private static LastView lastView;
    private static long lastScreenVisibleAt;

    private SableVehicleScreenshotPreviewCache() {
    }

    public static void capture(SableVehiclePreviewCapturePacket packet) {
        if (packet == null || packet.uuid() == null) {
            return;
        }
        AABB box = new AABB(packet.minX(), packet.minY(), packet.minZ(), packet.maxX(), packet.maxY(), packet.maxZ());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && !recentlyHadScreen() && captureNow(packet.uuid(), box)) {
            return;
        }
        PENDING.put(packet.uuid(), new PendingCapture(packet.uuid(), box, 0));
    }

    public static void delete(UUID uuid) {
        if (uuid == null) {
            return;
        }
        PENDING.remove(uuid);
        DynamicTexture texture = TEXTURES.remove(uuid);
        LOCATIONS.remove(uuid);
        if (texture != null) {
            texture.close();
        }
        try {
            Files.deleteIfExists(cachePath(uuid));
        } catch (IOException exception) {
            FindMeMod.LOGGER.warn("FindMe could not delete Sable preview cache {}", uuid, exception);
        }
    }

    public static void transfer(UUID fromUuid, UUID toUuid) {
        if (fromUuid == null || toUuid == null || fromUuid.equals(toUuid)) {
            return;
        }
        PendingCapture pending = PENDING.remove(fromUuid);
        if (pending != null) {
            PENDING.put(toUuid, new PendingCapture(toUuid, pending.box, pending.attempts));
        }
        DynamicTexture oldNewTexture = TEXTURES.remove(toUuid);
        LOCATIONS.remove(toUuid);
        if (oldNewTexture != null) {
            oldNewTexture.close();
        }
        DynamicTexture texture = TEXTURES.remove(fromUuid);
        ResourceLocation location = LOCATIONS.remove(fromUuid);
        if (texture != null && location != null) {
            TEXTURES.put(toUuid, texture);
            LOCATIONS.put(toUuid, location);
        }
        try {
            Files.createDirectories(cacheDir());
            Path from = cachePath(fromUuid);
            Path to = cachePath(toUuid);
            if (Files.isRegularFile(from)) {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            FindMeMod.LOGGER.warn("FindMe could not transfer Sable preview cache {} -> {}", fromUuid, toUuid, exception);
        }
    }

    static void tick() {
        if (Minecraft.getInstance().screen != null) {
            lastScreenVisibleAt = System.currentTimeMillis();
        }
    }

    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Camera camera = event.getCamera();
        if (camera != null) {
            lastView = new LastView(camera.getPosition(), new Matrix4f(event.getModelViewMatrix()), new Matrix4f(event.getProjectionMatrix()));
        }
        if (PENDING.isEmpty() || Minecraft.getInstance().screen != null) {
            return;
        }
        Iterator<PendingCapture> iterator = PENDING.values().iterator();
        while (iterator.hasNext()) {
            PendingCapture pending = iterator.next();
            if (captureNow(pending.uuid, pending.box)) {
                iterator.remove();
                continue;
            }
            pending.attempts++;
            if (pending.attempts >= MAX_PENDING_ATTEMPTS) {
                iterator.remove();
            }
        }
    }

    static boolean render(GuiGraphics graphics, UUID uuid, int x, int y, int width, int height) {
        ResourceLocation texture = texture(uuid);
        if (texture == null) {
            return false;
        }
        graphics.blit(texture, x, y, 0.0f, 0.0f, width, height, PREVIEW_SIZE, PREVIEW_SIZE);
        return true;
    }

    private static boolean captureNow(UUID uuid, AABB box) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null || minecraft.level == null || uuid == null || box == null || lastView == null) {
            return false;
        }
        int frameWidth = minecraft.getMainRenderTarget().width;
        int frameHeight = minecraft.getMainRenderTarget().height;
        if (frameWidth <= 0 || frameHeight <= 0) {
            return false;
        }
        Crop crop = projectedCrop(box, lastView, frameWidth, frameHeight);
        try {
            Files.createDirectories(cacheDir());
            NativeImage frame = new NativeImage(frameWidth, frameHeight, false);
            NativeImage preview = new NativeImage(PREVIEW_SIZE, PREVIEW_SIZE, false);
            try {
                RenderSystem.bindTexture(minecraft.getMainRenderTarget().getColorTextureId());
                frame.downloadTexture(0, false);
                frame.flipY();
                frame.resizeSubRectTo(crop.x, crop.y, crop.size, crop.size, preview);
                preview.writeToFile(cachePath(uuid));
                installTexture(uuid, preview);
                preview = null;
                return true;
            } finally {
                frame.close();
                if (preview != null) {
                    preview.close();
                }
            }
        } catch (Throwable throwable) {
            FindMeMod.LOGGER.warn("FindMe could not capture Sable vehicle preview {}", uuid, throwable);
            return false;
        }
    }

    private static Crop projectedCrop(AABB box, LastView view, int frameWidth, int frameHeight) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        int visible = 0;
        double[] xs = new double[]{box.minX, box.maxX};
        double[] ys = new double[]{box.minY, box.maxY};
        double[] zs = new double[]{box.minZ, box.maxZ};
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    Vector4f clip = new Vector4f((float)(x - view.camera.x), (float)(y - view.camera.y), (float)(z - view.camera.z), 1.0f);
                    clip.mul(view.modelView);
                    clip.mul(view.projection);
                    if (clip.w() <= 0.02f) {
                        continue;
                    }
                    float invW = 1.0f / clip.w();
                    float ndcX = clip.x() * invW;
                    float ndcY = clip.y() * invW;
                    if (ndcX < -1.7f || ndcX > 1.7f || ndcY < -1.7f || ndcY > 1.7f) {
                        continue;
                    }
                    double screenX = (ndcX * 0.5 + 0.5) * frameWidth;
                    double screenY = (0.5 - ndcY * 0.5) * frameHeight;
                    minX = Math.min(minX, screenX);
                    minY = Math.min(minY, screenY);
                    maxX = Math.max(maxX, screenX);
                    maxY = Math.max(maxY, screenY);
                    visible++;
                }
            }
        }
        if (visible < 2 || !Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(maxX) || !Double.isFinite(maxY)) {
            int fallback = Math.max(64, Math.min(frameWidth, frameHeight) / 2);
            return squareCrop(frameWidth / 2.0, frameHeight / 2.0, fallback, frameWidth, frameHeight);
        }
        double width = Math.max(48.0, maxX - minX);
        double height = Math.max(48.0, maxY - minY);
        double size = Math.max(width, height) * 1.18;
        return squareCrop((minX + maxX) * 0.5, (minY + maxY) * 0.5, size, frameWidth, frameHeight);
    }

    private static Crop squareCrop(double centerX, double centerY, double wantedSize, int frameWidth, int frameHeight) {
        int maxSize = Math.max(1, Math.min(frameWidth, frameHeight));
        int size = Mth.clamp((int)Math.ceil(wantedSize), 48, maxSize);
        int x = Mth.clamp((int)Math.round(centerX - size * 0.5), 0, Math.max(0, frameWidth - size));
        int y = Mth.clamp((int)Math.round(centerY - size * 0.5), 0, Math.max(0, frameHeight - size));
        return new Crop(x, y, size);
    }

    private static ResourceLocation texture(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        ResourceLocation cached = LOCATIONS.get(uuid);
        if (cached != null) {
            return cached;
        }
        Path path = cachePath(uuid);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (InputStream stream = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(stream);
            return installTexture(uuid, image);
        } catch (IOException exception) {
            FindMeMod.LOGGER.warn("FindMe could not load Sable preview cache {}", uuid, exception);
            return null;
        }
    }

    private static ResourceLocation installTexture(UUID uuid, NativeImage image) {
        DynamicTexture old = TEXTURES.remove(uuid);
        if (old != null) {
            old.close();
        }
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "sable_preview/" + uuid.toString().replace("-", "_"));
        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(location, texture);
        TEXTURES.put(uuid, texture);
        LOCATIONS.put(uuid, location);
        return location;
    }

    private static Path cacheDir() {
        return FMLPaths.CONFIGDIR.get().resolve("find_me").resolve("preview_cache").resolve("sable");
    }

    private static Path cachePath(UUID uuid) {
        return cacheDir().resolve(uuid.toString() + ".png");
    }

    private static boolean recentlyHadScreen() {
        return System.currentTimeMillis() - lastScreenVisibleAt < 450L;
    }

    private static final class PendingCapture {
        private final UUID uuid;
        private final AABB box;
        private int attempts;

        private PendingCapture(UUID uuid, AABB box, int attempts) {
            this.uuid = uuid;
            this.box = box;
            this.attempts = attempts;
        }
    }

    private record LastView(Vec3 camera, Matrix4f modelView, Matrix4f projection) {
    }

    private record Crop(int x, int y, int size) {
    }
}
