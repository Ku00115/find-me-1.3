package com.kuzhi.findme.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;

/** Extracts a stable card color from the texture selected by the entity renderer. */
final class CompanionCardColorResolver {
    private static final int FALLBACK = 0x465257;
    private static final Map<UUID, Integer> COLORS = new HashMap<>();

    private CompanionCardColorResolver() {
    }

    static int resolve(UUID uuid, Entity entity) {
        if (uuid == null || entity == null) return FALLBACK;
        Integer cached = COLORS.get(uuid);
        if (cached != null) return cached;
        int color = sample(entity);
        COLORS.put(uuid, color);
        return color;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int sample(Entity entity) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            EntityRenderer renderer = minecraft.getEntityRenderDispatcher().getRenderer(entity);
            ResourceLocation texture = renderer.getTextureLocation(entity);
            Resource resource = minecraft.getResourceManager().getResource(texture).orElse(null);
            if (resource == null) return FALLBACK;
            try (InputStream stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
                return dominantColor(image);
            }
        } catch (RuntimeException | java.io.IOException ignored) {
            return FALLBACK;
        }
    }

    private static int dominantColor(NativeImage image) {
        Map<Integer, Bucket> buckets = new HashMap<>();
        int stepX = Math.max(1, image.getWidth() / 48);
        int stepY = Math.max(1, image.getHeight() / 48);
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                int abgr = image.getPixelRGBA(x, y);
                int alpha = abgr >>> 24 & 0xFF;
                if (alpha < 96) continue;
                int red = abgr & 0xFF;
                int green = abgr >>> 8 & 0xFF;
                int blue = abgr >>> 16 & 0xFF;
                int brightness = Math.max(red, Math.max(green, blue));
                if (brightness < 18 || red + green + blue > 735) continue;
                int key = (red >> 5) << 6 | (green >> 5) << 3 | blue >> 5;
                buckets.computeIfAbsent(key, ignored -> new Bucket()).add(red, green, blue);
            }
        }
        Bucket best = null;
        for (Bucket bucket : buckets.values()) {
            if (best == null || bucket.score() > best.score()) best = bucket;
        }
        if (best == null) return FALLBACK;
        int red = tone(best.red / best.count);
        int green = tone(best.green / best.count);
        int blue = tone(best.blue / best.count);
        return red << 16 | green << 8 | blue;
    }

    private static int tone(int channel) {
        return Math.max(34, Math.min(112, Math.round(channel * 0.58f)));
    }

    private static final class Bucket {
        private int red;
        private int green;
        private int blue;
        private int count;

        private void add(int red, int green, int blue) {
            this.red += red;
            this.green += green;
            this.blue += blue;
            this.count++;
        }

        private int score() {
            if (count == 0) return 0;
            int averageRed = red / count;
            int averageGreen = green / count;
            int averageBlue = blue / count;
            int saturation = Math.max(averageRed, Math.max(averageGreen, averageBlue))
                    - Math.min(averageRed, Math.min(averageGreen, averageBlue));
            return count * (32 + saturation);
        }
    }
}
