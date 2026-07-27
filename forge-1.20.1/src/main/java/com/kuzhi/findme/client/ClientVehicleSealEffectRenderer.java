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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

final class ClientVehicleSealEffectRenderer {
    private static final Color BLUE = new Color(0.56f, 0.88f, 1.0f);
    private static final Color WHITE = new Color(0.98f, 1.0f, 1.0f);

    private ClientVehicleSealEffectRenderer() {
    }

    static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ClientVehicleSealEffectState.effects().isEmpty()) {
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
        for (ClientVehicleSealEffectState.Effect effect : ClientVehicleSealEffectState.effects()) {
            renderEffect(buffer, matrix, effect, event.getPartialTick());
        }
        BufferUploader.drawWithShader(buffer.end());
        for (ClientVehicleSealEffectState.Effect effect : ClientVehicleSealEffectState.effects()) {
            if (effect.customTexture) renderCustomDisks(matrix, effect, event.getPartialTick());
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void renderEffect(BufferBuilder buffer, Matrix4f matrix, ClientVehicleSealEffectState.Effect effect, float partialTick) {
        float age = effect.age + partialTick;
        float progress = Mth.clamp(age / (float)effect.durationTicks, 0.0f, 1.0f);
        float open = smooth(Mth.clamp(progress / 0.20f, 0.0f, 1.0f));
        float close = smooth(Mth.clamp((progress - 0.24f) / 0.58f, 0.0f, 1.0f));
        float fade = 1.0f - smooth(Mth.clamp((progress - 0.82f) / 0.18f, 0.0f, 1.0f));
        if (open * fade <= 0.01f) {
            return;
        }
        double baseY = effect.base.y + 0.18;
        double topY = effect.base.y + Math.max(0.25, effect.height) + 0.18;
        double centerY = (baseY + topY) * 0.5;
        double lowerY = Mth.lerp(close, baseY, centerY - 0.05);
        double upperY = Mth.lerp(close, topY, centerY + 0.05);
        double radius = effect.radius * (0.35 + 0.65 * open) * (1.0 - 0.28 * close);
        float alpha = 0.66f * open * fade;
        float spin = age * (0.055f + 0.035f * close) + (float)Math.sin(age * 0.08f) * 0.7f;
        Vec3 center = new Vec3(effect.base.x, effect.base.y, effect.base.z);
        if (!effect.customTexture) {
            ClientMagicCircleRenderer.renderSealDisk(buffer, matrix, center, lowerY, radius, spin, BLUE, alpha, false);
            ClientMagicCircleRenderer.renderSealDisk(buffer, matrix, center, upperY, radius * 0.98, -spin * 1.08, WHITE, alpha * 0.82f, true);
        }
        renderSealLines(buffer, matrix, center, lowerY, upperY, radius, close, fade, age);
        float flash = smooth(Mth.clamp(1.0f - Math.abs(progress - 0.80f) / 0.12f, 0.0f, 1.0f));
        if (flash > 0.02f) {
            ClientMagicCircleRenderer.ring(buffer, matrix, center, centerY, Math.max(0.16, radius * (0.42 - 0.22 * close)), 72, spin * 1.6, 0.050 + radius * 0.006, WHITE, flash * 0.48f);
        }
    }

    private static void renderCustomDisks(Matrix4f matrix, ClientVehicleSealEffectState.Effect effect, float partialTick) {
        float age = effect.age + partialTick;
        float progress = Mth.clamp(age / (float)effect.durationTicks, 0.0f, 1.0f);
        float open = smooth(Mth.clamp(progress / 0.20f, 0.0f, 1.0f));
        float close = smooth(Mth.clamp((progress - 0.24f) / 0.58f, 0.0f, 1.0f));
        float fade = 1.0f - smooth(Mth.clamp((progress - 0.82f) / 0.18f, 0.0f, 1.0f));
        if (open * fade <= 0.01f) return;
        double baseY = effect.base.y + 0.18;
        double topY = effect.base.y + Math.max(0.25, effect.height) + 0.18;
        double centerY = (baseY + topY) * 0.5;
        double lowerY = Mth.lerp(close, baseY, centerY - 0.05);
        double upperY = Mth.lerp(close, topY, centerY + 0.05);
        double radius = effect.radius * (0.35 + 0.65 * open) * (1.0 - 0.28 * close);
        float spin = age * (0.055f + 0.035f * close);
        float alpha = 0.66f * open * fade;
        ClientCustomMagicCircleRenderer.ground(matrix, effect.base, lowerY, radius, spin, alpha);
        ClientCustomMagicCircleRenderer.ground(matrix, effect.base, upperY, radius * 0.98, -spin * 1.08, alpha * 0.82f);
    }

    private static void renderSealLines(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double lowerY, double upperY, double radius, float close, float fade, float age) {
        if (close <= 0.02f) {
            return;
        }
        int strands = 8;
        float alpha = fade * close * 0.36f;
        for (int i = 0; i < strands; i++) {
            double angle = Math.PI * 2.0 * i / strands + age * 0.028 + Math.sin(age * 0.05 + i) * 0.08;
            double strandRadius = radius * (0.76 + 0.10 * Math.sin(age * 0.06 + i));
            Vec3 bottom = new Vec3(center.x + Math.cos(angle) * strandRadius, lowerY, center.z + Math.sin(angle) * strandRadius);
            Vec3 top = new Vec3(center.x + Math.cos(angle + close * 0.55) * strandRadius * 0.92, upperY, center.z + Math.sin(angle + close * 0.55) * strandRadius * 0.92);
            ClientMagicCircleRenderer.line(buffer, matrix, bottom, top, 0.030 + radius * 0.004, i % 2 == 0 ? BLUE : WHITE, alpha);
        }
    }

    private static float smooth(float t) {
        float clamped = Mth.clamp(t, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }
}
