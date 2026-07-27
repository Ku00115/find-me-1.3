package com.kuzhi.findme.client;

import com.kuzhi.findme.client.ClientMagicCircleRenderer.Color;
import com.kuzhi.findme.network.RescueMagicPacket;
import com.mojang.blaze3d.platform.GlStateManager;
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

final class ClientRescueMagicRenderer {
    private static final Color BLUE = new Color(0.58f, 0.92f, 1.0f);
    private static final Color WHITE = new Color(0.96f, 1.0f, 1.0f);
    private static final Color VEIL = new Color(0.32f, 0.64f, 0.78f);

    private ClientRescueMagicRenderer() {
    }

    static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ClientRescueMagicRenderState.effects().isEmpty()) {
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
        for (ClientRescueMagicRenderState.Effect effect : ClientRescueMagicRenderState.effects()) {
            renderEffect(buffer, matrix, effect, event.getPartialTick());
        }
        BufferUploader.drawWithShader(buffer.end());
        for (ClientRescueMagicRenderState.Effect effect : ClientRescueMagicRenderState.effects()) {
            if (effect.customTexture) {
                renderCustomEffect(matrix, effect, event.getPartialTick());
            }
        }
        renderKineticEffects(matrix, event.getPartialTick());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void renderKineticEffects(Matrix4f matrix, float partialTick) {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        BufferBuilder ringBuffer = Tesselator.getInstance().getBuilder();
        ringBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (ClientRescueMagicRenderState.Effect effect : ClientRescueMagicRenderState.effects()) {
            renderBurstRings(ringBuffer, matrix, effect, partialTick);
        }
        draw(ringBuffer);
        RenderSystem.defaultBlendFunc();
    }

    private static void renderBurstRings(BufferBuilder buffer, Matrix4f matrix, ClientRescueMagicRenderState.Effect effect, float partialTick) {
        if (!effect.velocityBurst || effect.burstAge < 0 || effect.burstCenter == null || effect.burstDirection == null) {
            return;
        }
        Vec3 normal = effect.burstThreeDimensional
                ? effect.burstDirection : effect.burstDirection.multiply(1.0, 0.0, 1.0);
        if (normal.lengthSqr() < 1.0E-5) {
            double yawRadians = Math.toRadians(effect.yaw);
            normal = new Vec3(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
        } else {
            normal = normal.normalize();
        }
        Vec3 reference = Math.abs(normal.y) < 0.92 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 right = reference.cross(normal).normalize();
        Basis basis = new Basis(right, normal.cross(right).normalize());
        double baseRadius = Math.max(0.12, effect.radius) * effect.burstScale;
        int ringCount = effect.burstImpact ? 3 : 1;
        float lifetime = effect.burstImpact ? 24.0f : 14.0f;
        double expansionDistance = effect.burstImpact ? 8.2 : 3.1;
        for (int i = 0; i < ringCount; i++) {
            float localAge = effect.burstAge + partialTick - i * 2.8f;
            if (localAge < 0.0f || localAge > lifetime) {
                continue;
            }
            float progress = Mth.clamp(localAge / lifetime, 0.0f, 1.0f);
            float expansion = 1.0f - (1.0f - progress) * (1.0f - progress) * (1.0f - progress);
            int sizeOrder = effect.burstImpact ? ringCount - 1 - i : 0;
            double radius = baseRadius * (0.48 + expansion * (expansionDistance + sizeOrder * 0.55));
            double width = Math.max(0.012, baseRadius * ((effect.burstImpact ? 0.145 : 0.090)
                    * (1.0 - progress) + 0.022));
            float alpha = (1.0f - smooth(progress))
                    * (effect.burstImpact ? 0.92f - i * 0.16f : 0.46f);
            Vec3 center = effect.burstCenter.subtract(normal.scale(i * baseRadius * 0.72));
            planeRing(buffer, matrix, center, basis, normal, radius, 96, width, WHITE, alpha);
            planeRing(buffer, matrix, center.add(normal.scale(0.025)), basis, normal,
                    radius * 0.965, 96, Math.max(0.018, width * 0.34), BLUE, alpha * 0.44f);
        }
    }

    private static void planeRing(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Basis basis, Vec3 normal,
                                  double radius, int segments, double width, Color color, float alpha) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            Vec3 p0 = center.add(basis.right.scale(Math.cos(a0) * radius)).add(basis.up.scale(Math.sin(a0) * radius));
            Vec3 p1 = center.add(basis.right.scale(Math.cos(a1) * radius)).add(basis.up.scale(Math.sin(a1) * radius));
            ClientMagicCircleRenderer.planeLine(buffer, matrix, p0, p1, normal, width, color, alpha);
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 position, Color color, float alpha) {
        buffer.vertex(matrix, (float)position.x, (float)position.y, (float)position.z)
                .color(color.red(), color.green(), color.blue(), Mth.clamp(alpha, 0.0f, 1.0f)).endVertex();
    }

    private static void draw(BufferBuilder buffer) {
        BufferUploader.drawWithShader(buffer.end());
    }

    private record Basis(Vec3 right, Vec3 up) {
    }

    private static void renderEffect(BufferBuilder buffer, Matrix4f matrix, ClientRescueMagicRenderState.Effect effect, float partialTick) {
        if (effect.customTexture || effect.velocityBurst) {
            return;
        }
        float age = effect.age + partialTick;
        float progress = Mth.clamp(age / (float)effect.durationTicks, 0.0f, 1.0f);
        boolean rescue = effect.purpose == RescueMagicPacket.Purpose.RESCUE;
        float open = smooth(Mth.clamp(progress / (rescue ? 0.22f : 0.42f), 0.0f, 1.0f));
        float fade = 1.0f - smooth(Mth.clamp((progress - (rescue ? 0.72f : 0.82f)) / (rescue ? 0.18f : 0.18f), 0.0f, 1.0f));
        float alpha = open * fade * (rescue ? 0.92f : 0.66f);
        if (alpha <= 0.01f) {
            return;
        }
        if (effect.style == com.kuzhi.findme.network.RescueMagicPacket.Style.GROUND_CIRCLE) {
            renderGroundEffect(buffer, matrix, effect, progress, open, alpha, age);
            return;
        }
        renderVerticalEffect(buffer, matrix, effect, progress, open, alpha, age);
    }

    private static void renderCustomEffect(Matrix4f matrix, ClientRescueMagicRenderState.Effect effect, float partialTick) {
        float age = effect.age + partialTick;
        float progress = Mth.clamp(age / (float)effect.durationTicks, 0.0f, 1.0f);
        boolean rescue = effect.purpose == RescueMagicPacket.Purpose.RESCUE;
        float open = smooth(Mth.clamp(progress / (rescue ? 0.22f : 0.42f), 0.0f, 1.0f));
        float fade = 1.0f - smooth(Mth.clamp((progress - (rescue ? 0.72f : 0.82f)) / 0.18f, 0.0f, 1.0f));
        float alpha = open * fade * (rescue ? 0.86f : 0.64f);
        if (alpha <= 0.01f) return;
        double rotation = age * (rescue ? 0.055 : 0.032);
        if (effect.style == RescueMagicPacket.Style.GROUND_CIRCLE) {
            double baseRadius = Math.max(0.35, effect.radius * (rescue ? 0.98 : 0.90));
            double radius = baseRadius * ((rescue ? 0.06 : 0.12) + (rescue ? 0.94 : 0.88) * open);
            ClientCustomMagicCircleRenderer.ground(matrix, effect.center, effect.center.y + 0.105, radius, rotation, alpha);
        } else {
            double baseRadius = Math.max(0.45, Math.max(effect.radius, effect.height * 0.52f)) * (rescue ? 1.12 : 1.0);
            double radius = baseRadius * ((rescue ? 0.04 : 0.10) + (rescue ? 0.96 : 0.90) * open);
            Vec3 center = effect.center.add(0.0, Math.max(effect.height * 0.52f, Math.min(radius * 0.62, effect.radius * 0.8)), 0.0);
            ClientCustomMagicCircleRenderer.vertical(matrix, center, radius, effect.yaw, rotation, alpha);
        }
    }

    private static void renderVerticalEffect(BufferBuilder buffer, Matrix4f matrix, ClientRescueMagicRenderState.Effect effect, float progress, float open, float alpha, float age) {
        boolean rescue = effect.purpose == RescueMagicPacket.Purpose.RESCUE;
        double baseRadius = Math.max(0.45, Math.max(effect.radius, effect.height * 0.52f)) * (rescue ? 1.12 : 1.0);
        double growth = (rescue ? 0.04 : 0.10) + (rescue ? 0.96 : 0.90) * open;
        double radius = baseRadius * growth;
        Vec3 center = effect.center.add(0.0, Math.max(effect.height * 0.52f, Math.min(radius * 0.62, effect.radius * 0.8)) + Math.sin(age * 0.11) * 0.025, 0.0);
        float renderAge = rescue ? age * 1.38f + (float)Math.sin(age * 0.17f) * 2.0f : age * 0.82f + (float)Math.sin(age * 0.07f) * 0.7f;
        ClientMagicCircleRenderer.renderVerticalPortal(buffer, matrix, center, radius, effect.yaw, BLUE, WHITE, (rescue ? 0.82f : 0.56f) * alpha, progress, renderAge);

        float flash = smooth(Mth.clamp(1.0f - Math.abs(progress - (rescue ? 0.24f : 0.38f)) / (rescue ? 0.11f : 0.14f), 0.0f, 1.0f));
        if (flash > 0.02f) {
            ClientMagicCircleRenderer.renderVerticalPortal(buffer, matrix, center, radius * (1.0 + flash * (rescue ? 0.11 : 0.04)), effect.yaw, WHITE, BLUE, flash * (rescue ? 0.42f : 0.18f), progress, renderAge + 9.0f);
        }
    }

    private static void renderGroundEffect(BufferBuilder buffer, Matrix4f matrix, ClientRescueMagicRenderState.Effect effect, float progress, float open, float alpha, float age) {
        boolean rescue = effect.purpose == RescueMagicPacket.Purpose.RESCUE;
        double baseRadius = Math.max(0.35, effect.radius * (rescue ? 0.98 : 0.90));
        double radius = baseRadius * ((rescue ? 0.06 : 0.12) + (rescue ? 0.94 : 0.88) * open);
        float renderAge = rescue ? age * 1.45f + (float)Math.sin(age * 0.18f) * 2.0f : age * 0.78f + (float)Math.sin(age * 0.06f) * 0.6f;
        ClientMagicCircleRenderer.renderGroundCircle(buffer, matrix, effect.center, radius, BLUE, (rescue ? 0.70f : 0.44f) * alpha, progress, renderAge, rescue ? 0.44 : 0.22);
        if (effect.groundEmerge) {
            renderGroundEmergence(buffer, matrix, effect, progress, open, alpha, age, radius);
        }

        if (!rescue) {
            float detailOpen = smooth(Mth.clamp((progress - 0.32f) / 0.16f, 0.0f, 1.0f));
            if (detailOpen > 0.01f) {
                ClientMagicCircleRenderer.renderGroundCircle(buffer, matrix, effect.center, radius * 0.985, WHITE, 0.15f * alpha * detailOpen, progress, renderAge + 4.0f, 0.58);
            }
            return;
        }
        float flash = smooth(Mth.clamp(1.0f - Math.abs(progress - (rescue ? 0.22f : 0.40f)) / (rescue ? 0.11f : 0.16f), 0.0f, 1.0f));
        if (flash > 0.02f) {
            ClientMagicCircleRenderer.renderGroundCircle(buffer, matrix, effect.center, radius * (1.0 + flash * (rescue ? 0.22 : 0.10)), WHITE, flash * (rescue ? 0.40f : 0.16f), progress, renderAge + 7.0f, rescue ? 0.72 : 0.58);
        }
    }

    private static void renderGroundEmergence(BufferBuilder buffer, Matrix4f matrix, ClientRescueMagicRenderState.Effect effect, float progress, float open, float alpha, float age, double circleRadius) {
        boolean rescue = effect.purpose == RescueMagicPacket.Purpose.RESCUE;
        float emerge = smooth(Mth.clamp((progress - (rescue ? 0.08f : 0.16f)) / (rescue ? 0.42f : 0.52f), 0.0f, 1.0f));
        float fade = 1.0f - smooth(Mth.clamp((progress - 0.76f) / 0.20f, 0.0f, 1.0f));
        float veilAlpha = alpha * fade;
        if (veilAlpha <= 0.01f) {
            return;
        }
        double radius = Math.max(0.30, circleRadius * (0.86 + 0.08 * Math.sin(age * 0.09)));
        double height = Math.max(0.25, effect.height);
        double top = effect.center.y + 0.16 + height * (0.22 + 0.78 * emerge);
        double spin = age * (rescue ? 0.085 : 0.052) + Math.sin(age * 0.041) * 0.35;

        renderGroundMist(buffer, matrix, effect.center, radius, veilAlpha, age, emerge);
        renderEmergenceVeil(buffer, matrix, effect.center, radius, top, spin, veilAlpha, emerge);
        renderRisingStreaks(buffer, matrix, effect.center, radius, height, spin, veilAlpha, emerge, age);

        float flash = smooth(Mth.clamp(1.0f - Math.abs(progress - (rescue ? 0.28f : 0.42f)) / 0.10f, 0.0f, 1.0f));
        if (flash > 0.02f) {
            ClientMagicCircleRenderer.ring(buffer, matrix, effect.center, effect.center.y + 0.20 + height * 0.18 * emerge, radius * (0.28 + 0.42 * emerge), 80, -spin * 1.3, 0.065 + radius * 0.010, WHITE, flash * veilAlpha * 0.70f);
        }
    }

    private static void renderGroundMist(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double radius, float alpha, float age, float emerge) {
        double baseY = center.y + 0.075;
        for (int i = 0; i < 5; i++) {
            double t = i / 4.0;
            double localRadius = radius * (0.24 + t * 0.70 + Math.sin(age * 0.055 + i) * 0.018);
            double width = radius * (0.045 + 0.014 * i);
            float localAlpha = alpha * (0.16f + 0.10f * (float)(1.0 - t)) * (1.0f - emerge * 0.28f);
            ClientMagicCircleRenderer.ring(buffer, matrix, center, baseY + i * 0.018, localRadius, 96, age * (0.015 + i * 0.004), width, i % 2 == 0 ? VEIL : BLUE, localAlpha);
        }
    }

    private static void renderEmergenceVeil(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double radius, double top, double spin, float alpha, float emerge) {
        int segments = 28;
        double baseY = center.y + 0.07;
        for (int i = 0; i < segments; i++) {
            double a0 = spin + Math.PI * 2.0 * i / segments;
            double a1 = spin + Math.PI * 2.0 * (i + 0.42) / segments;
            double wave = Math.sin(spin * 1.7 + i * 0.73) * 0.08;
            double localRadius = radius * (0.58 + wave);
            double localTop = top + Math.sin(spin * 2.2 + i) * 0.18;
            Vec3 p0 = new Vec3(center.x + Math.cos(a0) * localRadius, baseY, center.z + Math.sin(a0) * localRadius);
            Vec3 p1 = new Vec3(center.x + Math.cos(a1) * localRadius, baseY + 0.05, center.z + Math.sin(a1) * localRadius);
            Vec3 p2 = new Vec3(center.x + Math.cos(a1) * localRadius * (0.78 + 0.18 * emerge), localTop, center.z + Math.sin(a1) * localRadius * (0.78 + 0.18 * emerge));
            Vec3 p3 = new Vec3(center.x + Math.cos(a0) * localRadius * (0.78 + 0.18 * emerge), localTop - 0.08, center.z + Math.sin(a0) * localRadius * (0.78 + 0.18 * emerge));
            float strandAlpha = alpha * (0.075f + 0.060f * (float)Math.sin(i * 1.37 + spin));
            ClientMagicCircleRenderer.quad(buffer, matrix, p0, p1, p2, p3, i % 3 == 0 ? WHITE : VEIL, Math.max(0.025f, strandAlpha));
        }
    }

    private static void renderRisingStreaks(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double radius, double height, double spin, float alpha, float emerge, float age) {
        int strands = 16;
        double baseY = center.y + 0.14;
        for (int i = 0; i < strands; i++) {
            double angle = spin * (i % 2 == 0 ? 1.0 : -0.72) + Math.PI * 2.0 * i / strands;
            double localRadius = radius * (0.18 + 0.64 * ((i * 37 % 100) / 100.0));
            double rise = height * (0.16 + 0.72 * emerge) * (0.72 + 0.28 * Math.sin(age * 0.08 + i));
            Vec3 start = new Vec3(center.x + Math.cos(angle) * localRadius, baseY, center.z + Math.sin(angle) * localRadius);
            Vec3 end = new Vec3(center.x + Math.cos(angle + 0.18) * localRadius * 0.72, baseY + rise, center.z + Math.sin(angle + 0.18) * localRadius * 0.72);
            float streakAlpha = alpha * (0.18f + 0.22f * emerge) * (i % 2 == 0 ? 1.0f : 0.65f);
            ClientMagicCircleRenderer.line(buffer, matrix, start, end, 0.026 + radius * 0.004, i % 4 == 0 ? WHITE : BLUE, streakAlpha);
        }
    }

    private static float smooth(float t) {
        float clamped = Mth.clamp(t, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }
}
