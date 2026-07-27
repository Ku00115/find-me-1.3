package com.kuzhi.findme.client;

import com.kuzhi.findme.client.ClientMagicCircleRenderer.Color;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

final class ClientContractCeremonyRenderer {
    private ClientContractCeremonyRenderer() {
    }

    static void renderWorld(RenderLevelStageEvent event) {
        if (!ClientContractRenderState.active() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        float progress = ClientContractRenderState.progress(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        float age = ClientContractRenderState.age(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        Vec3 playerPos = ClientContractRenderState.playerCircle();
        Vec3 targetPos = ClientContractRenderState.targetCircle();
        ClientContractRenderState.Phase phase = ClientContractRenderState.phase();
        ClientContractRenderState.Outcome outcome = ClientContractRenderState.outcome();
        float ending = ClientContractRenderState.endingProgress(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        Color color = phaseColor(phase);
        float intensity = phaseIntensity(phase, age);

        pose.pushPose();
        pose.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        renderWard(buffer, matrix, playerPos, targetPos, ClientContractRenderState.playerRadius(), ClientContractRenderState.targetRadius(), progress, age, outcome, ending);
        renderCircle(buffer, matrix, playerPos, ClientContractRenderState.playerRadius(), color, intensity, progress, age, 0.0, false, outcome, ending);
        renderCircle(buffer, matrix, targetPos, ClientContractRenderState.targetRadius(), color, intensity, progress, age, 0.7, true, outcome, ending);
        renderPaperGhost(buffer, matrix, playerPos, targetPos, color, intensity, age, outcome, ending);
        if (age >= 92.0f) {
            renderContractLine(buffer, matrix, playerPos, targetPos, color, Math.min(1.0f, (age - 92.0f) / 18.0f));
        }
        if (outcome == ClientContractRenderState.Outcome.CANCEL) {
            renderShatter(buffer, matrix, playerPos, targetPos, color, ending, age);
        }
        MeshData meshData = buffer.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    static void renderGui(RenderGuiEvent.Post event) {
        if (!ClientContractRenderState.active()) {
            return;
        }
        Component subtitle = ClientContractRenderState.subtitle();
        if (subtitle.getString().isBlank()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int y = Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() * 0.72f);
        int textWidth = font.width(subtitle);
        int x = (width - textWidth) / 2;
        int color = switch (ClientContractRenderState.phase()) {
            case NAME -> 0xA7F7FF;
            case RESPONSE -> 0xDDFBFF;
            case VOW -> 0xF7FEFF;
        };
        graphics.drawString(font, subtitle, x + 1, y + 1, 0x70000000, false);
        graphics.drawString(font, subtitle, x, y, color, false);
    }

    private static void renderCircle(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double radius, Color color, float intensity, float progress, float age, double offset, boolean targetCircle, ClientContractRenderState.Outcome outcome, float ending) {
        float alpha = intensity * (float)(0.52 + 0.18 * Math.sin(age * 0.09 + offset));
        if (outcome == ClientContractRenderState.Outcome.COMPLETE && targetCircle) {
            float hide = smooth(Mth.clamp(ending / 0.22f, 0.0f, 1.0f));
            if (hide >= 0.98f) {
                return;
            }
            radius *= 1.0 - ending * 0.65;
            alpha *= 1.0f - hide;
        } else if (outcome == ClientContractRenderState.Outcome.CANCEL) {
            radius *= 1.0 + ending * 0.24;
            alpha *= 1.0f - ending * 0.88f;
        }
        ClientMagicCircleRenderer.renderGroundCircle(buffer, matrix, center, radius, color, alpha, progress, age, offset);
    }

    private static void renderWard(BufferBuilder buffer, Matrix4f matrix, Vec3 playerPos, Vec3 targetPos, float playerRadius, float targetRadius, float progress, float age, ClientContractRenderState.Outcome outcome, float ending) {
        Vec3 center = playerPos.add(targetPos).scale(0.5);
        double distance = horizontalDistance(playerPos, targetPos);
        double radius = distance * 0.5 + Math.max(playerRadius, targetRadius) * 1.45 + 4.0;
        double height = Mth.clamp(radius * 0.20 + 1.35, 2.6, 5.8);
        float pulse = 0.64f + 0.16f * (float)Math.sin(age * 0.08);
        if (outcome == ClientContractRenderState.Outcome.COMPLETE) {
            pulse *= 1.0f - ending * 0.35f;
        } else if (outcome == ClientContractRenderState.Outcome.CANCEL) {
            pulse *= 1.0f - ending * 0.82f;
            radius += ending * 1.2;
        }
        Color shield = new Color(0.42f, 0.88f, 1.0f);
        int segments = 96;
        double y0 = Math.min(playerPos.y, targetPos.y) + 0.05;
        double y1 = y0 + height;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            double wave0 = 0.035 * Math.sin(age * 0.05 + i * 0.42);
            double wave1 = 0.035 * Math.sin(age * 0.05 + (i + 1) * 0.42);
            Vec3 p0 = new Vec3(center.x + Math.cos(a0) * (radius + wave0), y0, center.z + Math.sin(a0) * (radius + wave0));
            Vec3 p1 = new Vec3(center.x + Math.cos(a1) * (radius + wave1), y0, center.z + Math.sin(a1) * (radius + wave1));
            Vec3 p2 = new Vec3(p1.x, y1, p1.z);
            Vec3 p3 = new Vec3(p0.x, y1, p0.z);
            quad(buffer, matrix, p0, p1, p2, p3, shield, 0.038f * pulse);
        }
        ClientMagicCircleRenderer.ring(buffer, matrix, center, y0 + 0.05, radius, 128, age * 0.01, 0.075, shield, 0.46f * pulse);
        ClientMagicCircleRenderer.ring(buffer, matrix, center, y0 + height * 0.5, radius * 0.92, 96, age * 0.006, 0.035, shield, 0.14f * pulse);
        ClientMagicCircleRenderer.ring(buffer, matrix, center, y1, radius * 0.72, 96, -age * 0.015, 0.042, shield, 0.22f * pulse);
    }

    private static void renderContractLine(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b, Color color, float alpha) {
        Vec3 start = new Vec3(a.x, a.y + 0.16, a.z);
        Vec3 end = new Vec3(b.x, b.y + 0.16, b.z);
        line(buffer, matrix, start, end, 0.11, color, 0.58f * alpha);
        line(buffer, matrix, start.add(0.0, 0.055, 0.0), end.add(0.0, 0.055, 0.0), 0.045, new Color(0.94f, 1.0f, 1.0f), 0.82f * alpha);
    }

    private static void renderPaperGhost(BufferBuilder buffer, Matrix4f matrix, Vec3 playerPos, Vec3 targetPos, Color color, float intensity, float age, ClientContractRenderState.Outcome outcome, float ending) {
        float appear = smooth(Mth.clamp((age - 8.0f) / 24.0f, 0.0f, 1.0f));
        if (appear <= 0.0f) {
            return;
        }
        Vec3 axis = horizontalDirection(playerPos, targetPos);
        Vec3 side = new Vec3(-axis.z, 0.0, axis.x);
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 center = playerPos.add(axis.scale(0.56)).add(0.0, 1.30 + 0.035 * Math.sin(age * 0.07), 0.0);
        double width = 0.48 + 0.025 * Math.sin(age * 0.09);
        double height = 0.70;
        float alpha = appear * intensity;
        if (outcome == ClientContractRenderState.Outcome.COMPLETE) {
            alpha *= 1.0f - ending * 0.22f;
            width *= 1.0 - ending * 0.12;
            height *= 1.0 - ending * 0.12;
        } else if (outcome == ClientContractRenderState.Outcome.CANCEL) {
            alpha *= 1.0f - ending;
        }
        Vec3 topLeft = center.add(side.scale(-width * 0.5)).add(up.scale(height * 0.5));
        Vec3 topRight = center.add(side.scale(width * 0.5)).add(up.scale(height * 0.5));
        Vec3 bottomRight = center.add(side.scale(width * 0.5)).add(up.scale(-height * 0.5));
        Vec3 bottomLeft = center.add(side.scale(-width * 0.5)).add(up.scale(-height * 0.5));
        quad(buffer, matrix, topLeft, topRight, bottomRight, bottomLeft, new Color(0.82f, 0.98f, 1.0f), 0.035f * alpha);
        line(buffer, matrix, topLeft, topRight, 0.020, color, 0.42f * alpha);
        line(buffer, matrix, topRight, bottomRight, 0.020, color, 0.32f * alpha);
        line(buffer, matrix, bottomRight, bottomLeft, 0.020, color, 0.32f * alpha);
        line(buffer, matrix, bottomLeft, topLeft, 0.020, color, 0.42f * alpha);

        float write = smooth(Mth.clamp((age - 46.0f) / 34.0f, 0.0f, 1.0f));
        int strokes = Math.max(0, Math.min(7, (int)Math.ceil(write * 7.0f)));
        for (int i = 0; i < strokes; i++) {
            double row = 0.20 - i * 0.068;
            double wobble = Math.sin(age * 0.11 + i * 1.4) * 0.018;
            double length = (0.28 + (i % 3) * 0.052) * Math.min(1.0, write * 1.25);
            Vec3 start = center.add(up.scale(row)).add(side.scale(-length * 0.5 + wobble));
            Vec3 end = center.add(up.scale(row + 0.018 * Math.sin(i))).add(side.scale(length * 0.5 + wobble));
            line(buffer, matrix, start, end, 0.018, new Color(0.93f, 1.0f, 1.0f), alpha * (0.36f + i * 0.030f));
        }
        float seal = smooth(Mth.clamp((age - 88.0f) / 18.0f, 0.0f, 1.0f));
        if (seal > 0.0f) {
            Vec3 sealCenter = center.add(up.scale(-0.16));
            double sealRadius = 0.115 + 0.018 * Math.sin(age * 0.16);
            paperRing(buffer, matrix, sealCenter, side, up, sealRadius, 24, age * 0.02, 0.014, color, alpha * seal * 0.74f);
            paperPolygon(buffer, matrix, sealCenter, side, up, sealRadius * 0.74, 3, -Math.PI / 2.0, 0.014, color, alpha * seal * 0.86f);
            paperPolygon(buffer, matrix, sealCenter, side, up, sealRadius * 0.74, 3, Math.PI / 2.0, 0.014, color, alpha * seal * 0.78f);
        }
    }

    private static void renderShatter(BufferBuilder buffer, Matrix4f matrix, Vec3 playerPos, Vec3 targetPos, Color color, float ending, float age) {
        if (ending <= 0.0f) {
            return;
        }
        Vec3 center = playerPos.add(targetPos).scale(0.5);
        double radius = horizontalDistance(playerPos, targetPos) * 0.5 + Math.max(ClientContractRenderState.playerRadius(), ClientContractRenderState.targetRadius()) + 0.9;
        float alpha = (1.0f - ending) * 0.52f;
        for (int i = 0; i < 18; i++) {
            double angle = Math.PI * 2.0 * i / 18.0 + age * 0.018;
            double drift = ending * (0.45 + (i % 4) * 0.12);
            Vec3 middle = new Vec3(center.x + Math.cos(angle) * (radius + drift), center.y + 0.18 + ending * 0.35, center.z + Math.sin(angle) * (radius + drift));
            Vec3 tangent = new Vec3(-Math.sin(angle), 0.0, Math.cos(angle)).scale(0.28 + (i % 3) * 0.08);
            line(buffer, matrix, middle.subtract(tangent), middle.add(tangent), 0.04, color, alpha);
        }
    }

    private static void line(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b, double width, Color color, float alpha) {
        Vec3 direction = b.subtract(a);
        Vec3 side = new Vec3(-direction.z, 0.0, direction.x);
        if (side.lengthSqr() < 1.0E-5) {
            side = new Vec3(width, 0.0, 0.0);
        } else {
            side = side.normalize().scale(width * 0.5);
        }
        Vec3 p0 = a.add(side);
        Vec3 p1 = a.subtract(side);
        Vec3 p2 = b.subtract(side);
        Vec3 p3 = b.add(side);
        quad(buffer, matrix, p0, p1, p2, p3, color, alpha);
    }

    private static void paperRing(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Vec3 side, Vec3 up, double radius, int segments, double spin, double width, Color color, float alpha) {
        for (int i = 0; i < segments; i++) {
            double a0 = spin + Math.PI * 2.0 * i / segments;
            double a1 = spin + Math.PI * 2.0 * (i + 1) / segments;
            Vec3 p0 = center.add(side.scale(Math.cos(a0) * radius)).add(up.scale(Math.sin(a0) * radius));
            Vec3 p1 = center.add(side.scale(Math.cos(a1) * radius)).add(up.scale(Math.sin(a1) * radius));
            line(buffer, matrix, p0, p1, width, color, alpha);
        }
    }

    private static void paperPolygon(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Vec3 side, Vec3 up, double radius, int points, double spin, double width, Color color, float alpha) {
        Vec3[] vertices = new Vec3[points];
        for (int i = 0; i < points; i++) {
            double angle = spin + Math.PI * 2.0 * i / points;
            vertices[i] = center.add(side.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
        }
        for (int i = 0; i < points; i++) {
            line(buffer, matrix, vertices[i], vertices[(i + 1) % points], width, color, alpha);
        }
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, Color color, float alpha) {
        vertex(buffer, matrix, p0, color, alpha);
        vertex(buffer, matrix, p1, color, alpha);
        vertex(buffer, matrix, p2, color, alpha);
        vertex(buffer, matrix, p3, color, alpha);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 pos, Color color, float alpha) {
        buffer.addVertex(matrix, (float)pos.x, (float)pos.y, (float)pos.z).setColor(color.red(), color.green(), color.blue(), alpha);
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Vec3 horizontalDirection(Vec3 from, Vec3 to) {
        Vec3 direction = new Vec3(to.x - from.x, 0.0, to.z - from.z);
        if (direction.lengthSqr() < 0.01) {
            return new Vec3(1.0, 0.0, 0.0);
        }
        return direction.normalize();
    }

    private static float smooth(float t) {
        float clamped = Mth.clamp(t, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }

    private static Color phaseColor(ClientContractRenderState.Phase phase) {
        return switch (phase) {
            case NAME -> new Color(0.58f, 0.92f, 1.0f);
            case RESPONSE -> new Color(0.78f, 0.97f, 1.0f);
            case VOW -> new Color(0.94f, 1.0f, 1.0f);
        };
    }

    private static float phaseIntensity(ClientContractRenderState.Phase phase, float age) {
        float base = switch (phase) {
            case NAME -> 0.72f;
            case RESPONSE -> 1.00f;
            case VOW -> 1.18f;
        };
        return base + 0.12f * (float)Math.sin(age * 0.12);
    }

}
