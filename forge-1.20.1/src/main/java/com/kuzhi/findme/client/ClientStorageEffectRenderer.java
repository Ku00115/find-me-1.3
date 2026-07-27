package com.kuzhi.findme.client;

import com.kuzhi.findme.client.ClientMagicCircleRenderer.Color;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

final class ClientStorageEffectRenderer {
    private static final Color BLUE = new Color(0.54f, 0.88f, 1.0f);
    private static final Color WHITE = new Color(0.96f, 1.0f, 1.0f);
    private static final Color VEIL = new Color(0.30f, 0.62f, 0.80f);

    private ClientStorageEffectRenderer() {
    }

    static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ClientStorageEffectState.effects().isEmpty()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        pose.pushPose();
        pose.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (ClientStorageEffectState.Effect effect : ClientStorageEffectState.effects()) {
            renderEffect(buffer, matrix, effect, event.getPartialTick());
        }
        BufferUploader.drawWithShader(buffer.end());
        for (ClientStorageEffectState.Effect effect : ClientStorageEffectState.effects()) {
            if (effect.customTexture) renderCustomCircle(matrix, effect, event.getPartialTick());
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void renderEffect(BufferBuilder buffer, Matrix4f matrix, ClientStorageEffectState.Effect effect, float partialTick) {
        float age = effect.age + partialTick;
        float progress = Mth.clamp(age / (float)effect.durationTicks, 0.0f, 1.0f);
        float open = smooth(Mth.clamp(progress / 0.24f, 0.0f, 1.0f));
        float wrap = wrapProgress(progress);
        float fade = 1.0f - smooth(Mth.clamp((progress - 0.78f) / 0.22f, 0.0f, 1.0f));
        if (fade <= 0.01f) {
            return;
        }
        Bounds bounds = bounds(effect);
        double radius = Math.max(0.30, bounds.radius) * (0.18 + 0.82 * open);
        float alpha = 0.62f * open * fade;
        if (!effect.customTexture) {
            ClientMagicCircleRenderer.renderGroundCircle(buffer, matrix, bounds.base, radius, BLUE, alpha, progress, age * 0.86f + wobble(age) * 2.5f, 0.36);
        }
        if (effect.groundSink) {
            renderGroundSink(buffer, matrix, bounds, radius, progress, open, fade, age);
            return;
        }
        renderWrap(buffer, matrix, bounds, radius, wrap, fade, age);
        renderClosingRings(buffer, matrix, bounds, radius, wrap, fade, age);
    }

    private static void renderCustomCircle(Matrix4f matrix, ClientStorageEffectState.Effect effect, float partialTick) {
        float age = effect.age + partialTick;
        float progress = Mth.clamp(age / (float)effect.durationTicks, 0.0f, 1.0f);
        float open = smooth(Mth.clamp(progress / 0.24f, 0.0f, 1.0f));
        float fade = 1.0f - smooth(Mth.clamp((progress - 0.78f) / 0.22f, 0.0f, 1.0f));
        if (open * fade <= 0.01f) return;
        Bounds bounds = bounds(effect);
        double radius = Math.max(0.30, bounds.radius) * (0.18 + 0.82 * open);
        ClientCustomMagicCircleRenderer.ground(matrix, bounds.base, bounds.base.y + 0.105, radius, age * 0.045, 0.62f * open * fade);
    }

    private static void renderWrap(BufferBuilder buffer, Matrix4f matrix, Bounds bounds, double radius, float wrap, float fade, float age) {
        if (wrap <= 0.01f) {
            return;
        }
        int strands = 5;
        int segments = 72;
        double height = bounds.height;
        double maxT = Mth.clamp(wrap, 0.0f, 1.0f);
        for (int strand = 0; strand < strands; strand++) {
            double phase = strand * Math.PI * 2.0 / strands;
            double speed = 0.052 + strand * 0.007;
            double spin = age * speed + Math.sin(age * 0.055 + strand) * 0.42;
            Vec3 previous = null;
            for (int i = 0; i <= segments; i++) {
                double t = maxT * i / segments;
                double easedT = smooth((float)t);
                double localRadius = radius * (0.98 - 0.34 * easedT + Math.sin(age * 0.10 + i * 0.35 + strand) * 0.018);
                double angle = phase + spin + easedT * Math.PI * 5.1 + Math.sin(easedT * Math.PI * 2.0 + age * 0.045) * 0.24;
                double y = bounds.base.y + 0.18 + height * easedT;
                Vec3 point = new Vec3(bounds.base.x + Math.cos(angle) * localRadius, y, bounds.base.z + Math.sin(angle) * localRadius);
                if (previous != null) {
                    float segmentAlpha = fade * (0.18f + 0.54f * (float)easedT) * (1.0f - Math.max(0.0f, (float)t - 0.86f) * 2.2f);
                    ClientMagicCircleRenderer.line(buffer, matrix, previous, point, 0.035 + radius * 0.010, strand % 2 == 0 ? WHITE : BLUE, segmentAlpha);
                }
                previous = point;
            }
        }
    }

    private static void renderClosingRings(BufferBuilder buffer, Matrix4f matrix, Bounds bounds, double radius, float wrap, float fade, float age) {
        if (wrap <= 0.04f) {
            return;
        }
        double top = bounds.base.y + 0.18 + bounds.height * smooth(wrap);
        double pulse = Math.sin(age * 0.23) * 0.04;
        double bandRadius = radius * (0.94 - 0.46 * smooth(wrap) + pulse);
        float alpha = fade * (0.22f + 0.45f * wrap);
        ClientMagicCircleRenderer.ring(buffer, matrix, bounds.base, top, Math.max(0.16, bandRadius), 72, age * -0.035, 0.040 + radius * 0.006, WHITE, alpha);
        if (wrap > 0.68f) {
            double lowRadius = radius * (1.02 - 0.72 * smooth((wrap - 0.68f) / 0.32f));
            ClientMagicCircleRenderer.ring(buffer, matrix, bounds.base, bounds.base.y + 0.24, Math.max(0.12, lowRadius), 72, age * 0.055, 0.032 + radius * 0.005, BLUE, alpha * 0.72f);
        }
    }

    private static void renderGroundSink(BufferBuilder buffer, Matrix4f matrix, Bounds bounds, double radius, float progress, float open, float fade, float age) {
        float pull = smooth(Mth.clamp((progress - 0.10f) / 0.68f, 0.0f, 1.0f));
        float alpha = open * fade;
        double sinkRadius = Math.max(0.26, radius * (1.02 - 0.72 * pull));
        double baseY = bounds.base.y + 0.11;
        double height = Math.max(0.25, bounds.height);
        renderGroundMaw(buffer, matrix, bounds.base, sinkRadius, radius, pull, alpha, age);
        renderDownwardVeil(buffer, matrix, bounds.base, radius, height, pull, alpha, age);
        renderSinkBands(buffer, matrix, bounds.base, radius, height, pull, alpha, age);
        float flash = smooth(Mth.clamp(1.0f - Math.abs(progress - 0.76f) / 0.12f, 0.0f, 1.0f));
        if (flash > 0.02f) {
            ClientMagicCircleRenderer.ring(buffer, matrix, bounds.base, baseY + 0.08, Math.max(0.14, sinkRadius * 0.56), 80, age * 0.11, 0.052 + radius * 0.006, WHITE, flash * alpha * 0.58f);
        }
    }

    private static void renderGroundMaw(BufferBuilder buffer, Matrix4f matrix, Vec3 base, double sinkRadius, double originalRadius, float pull, float alpha, float age) {
        double y = base.y + 0.075;
        for (int i = 0; i < 5; i++) {
            double t = i / 4.0;
            double localRadius = Math.max(0.12, Mth.lerp(t, sinkRadius * 0.42, originalRadius * (0.82 - 0.34 * pull)));
            float localAlpha = alpha * (0.12f + 0.16f * (float)(1.0 - t)) * (0.72f + 0.28f * pull);
            ClientMagicCircleRenderer.ring(buffer, matrix, base, y + i * 0.018, localRadius, 96, age * (0.05 + i * 0.01), 0.040 + originalRadius * 0.006, i % 2 == 0 ? BLUE : VEIL, localAlpha);
        }
    }

    private static void renderDownwardVeil(BufferBuilder buffer, Matrix4f matrix, Vec3 base, double radius, double height, float pull, float alpha, float age) {
        int segments = 24;
        double topY = base.y + 0.22 + height * (1.0 - 0.78 * pull);
        double bottomY = base.y + 0.08;
        double spin = age * (0.075 + 0.050 * pull);
        for (int i = 0; i < segments; i++) {
            double a0 = spin + Math.PI * 2.0 * i / segments;
            double a1 = spin + Math.PI * 2.0 * (i + 0.46) / segments;
            double topRadius = radius * (0.72 - 0.38 * pull + Math.sin(age * 0.07 + i) * 0.030);
            double bottomRadius = Math.max(0.18, radius * (0.24 + 0.18 * (1.0 - pull)));
            Vec3 p0 = new Vec3(base.x + Math.cos(a0) * topRadius, topY + Math.sin(age * 0.10 + i) * 0.09, base.z + Math.sin(a0) * topRadius);
            Vec3 p1 = new Vec3(base.x + Math.cos(a1) * topRadius, topY - 0.05, base.z + Math.sin(a1) * topRadius);
            Vec3 p2 = new Vec3(base.x + Math.cos(a1 + pull * 0.48) * bottomRadius, bottomY, base.z + Math.sin(a1 + pull * 0.48) * bottomRadius);
            Vec3 p3 = new Vec3(base.x + Math.cos(a0 + pull * 0.48) * bottomRadius, bottomY + 0.04, base.z + Math.sin(a0 + pull * 0.48) * bottomRadius);
            ClientMagicCircleRenderer.quad(buffer, matrix, p0, p1, p2, p3, i % 3 == 0 ? WHITE : VEIL, alpha * (0.040f + 0.070f * pull));
        }
    }

    private static void renderSinkBands(BufferBuilder buffer, Matrix4f matrix, Vec3 base, double radius, double height, float pull, float alpha, float age) {
        int strands = 18;
        for (int i = 0; i < strands; i++) {
            double angle = Math.PI * 2.0 * i / strands + age * (i % 2 == 0 ? 0.070 : -0.046);
            double startRadius = radius * (0.34 + 0.46 * ((i * 29 % 100) / 100.0));
            double endRadius = radius * (0.10 + 0.16 * (1.0 - pull));
            double startY = base.y + 0.22 + height * (0.30 + 0.58 * (1.0 - pull));
            Vec3 start = new Vec3(base.x + Math.cos(angle) * startRadius, startY, base.z + Math.sin(angle) * startRadius);
            Vec3 end = new Vec3(base.x + Math.cos(angle + pull * 0.70) * endRadius, base.y + 0.12, base.z + Math.sin(angle + pull * 0.70) * endRadius);
            float strandAlpha = alpha * (0.20f + 0.28f * pull) * (i % 2 == 0 ? 1.0f : 0.72f);
            ClientMagicCircleRenderer.line(buffer, matrix, start, end, 0.026 + radius * 0.004, i % 4 == 0 ? WHITE : BLUE, strandAlpha);
        }
    }

    private static Bounds bounds(ClientStorageEffectState.Effect effect) {
        Entity entity = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(effect.entityId);
        if (entity != null) {
            AABB box = entity.getBoundingBox();
            double halfX = box.getXsize() * 0.5;
            double halfZ = box.getZsize() * 0.5;
            double radius = Math.max(effect.radius, Math.sqrt(halfX * halfX + halfZ * halfZ) * 1.15 + 0.12);
            double height = Math.max(effect.height, box.getYsize());
            return new Bounds(new Vec3(box.getCenter().x, box.minY, box.getCenter().z), radius, height);
        }
        return new Bounds(effect.fallbackBase, effect.radius, effect.height);
    }

    private static float wrapProgress(float progress) {
        float staged = Mth.clamp((progress - 0.10f) / 0.70f, 0.0f, 1.0f);
        float accelerated = staged < 0.55f
                ? 0.55f * smooth(staged / 0.55f) * 0.62f
                : 0.34f + 0.66f * smooth((staged - 0.55f) / 0.45f);
        return Mth.clamp(accelerated, 0.0f, 1.0f);
    }

    private static float smooth(float t) {
        float clamped = Mth.clamp(t, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }

    private static float wobble(float age) {
        return (float)Math.sin(age * 0.11f) + (float)Math.sin(age * 0.041f) * 0.55f;
    }

    private record Bounds(Vec3 base, double radius, double height) {
    }
}
