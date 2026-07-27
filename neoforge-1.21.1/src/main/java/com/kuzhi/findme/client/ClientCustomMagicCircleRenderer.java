package com.kuzhi.findme.client;

import com.kuzhi.findme.FindMeMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class ClientCustomMagicCircleRenderer {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "textures/effect/custom_magic_circle.png");

    private ClientCustomMagicCircleRenderer() {
    }

    static void ground(Matrix4f matrix, Vec3 center, double y, double radius, double rotation, float alpha) {
        Vec3 right = new Vec3(Math.cos(rotation), 0.0, Math.sin(rotation)).scale(radius);
        Vec3 forward = new Vec3(-Math.sin(rotation), 0.0, Math.cos(rotation)).scale(radius);
        quad(matrix, center.add(0.0, y - center.y, 0.0), right, forward, alpha);
    }

    static void vertical(Matrix4f matrix, Vec3 center, double radius, float yaw, double rotation, float alpha) {
        double yawRad = Math.toRadians(yaw);
        Vec3 rightBase = new Vec3(Math.cos(yawRad), 0.0, Math.sin(yawRad));
        Vec3 upBase = new Vec3(0.0, 1.0, 0.0);
        Vec3 right = rightBase.scale(Math.cos(rotation)).add(upBase.scale(Math.sin(rotation))).scale(radius);
        Vec3 up = upBase.scale(Math.cos(rotation)).subtract(rightBase.scale(Math.sin(rotation))).scale(radius);
        quad(matrix, center, right, up, alpha);
    }

    private static void quad(Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up, float alpha) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        vertex(buffer, matrix, center.subtract(right).subtract(up), 0.0f, 1.0f, alpha);
        vertex(buffer, matrix, center.add(right).subtract(up), 1.0f, 1.0f, alpha);
        vertex(buffer, matrix, center.add(right).add(up), 1.0f, 0.0f, alpha);
        vertex(buffer, matrix, center.subtract(right).add(up), 0.0f, 0.0f, alpha);
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 point, float u, float v, float alpha) {
        buffer.addVertex(matrix, (float)point.x, (float)point.y, (float)point.z)
                .setUv(u, v)
                .setColor(0.72f, 0.94f, 1.0f, Mth.clamp(alpha, 0.0f, 1.0f));
    }
}
