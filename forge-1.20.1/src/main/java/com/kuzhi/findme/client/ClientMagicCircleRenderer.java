package com.kuzhi.findme.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class ClientMagicCircleRenderer {
    private ClientMagicCircleRenderer() {
    }

    static void renderGroundCircle(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double radius, Color color, float alpha, float progress, float age, double offset) {
        double y = center.y + 0.095;
        double spin = age * 0.01 + offset;
        ring(buffer, matrix, center, y, radius, 128, spin, 0.115, color, alpha);
        ring(buffer, matrix, center, y + 0.008, radius * 0.84, 128, -spin * 0.8, 0.072, color, alpha * 0.72f);
        ring(buffer, matrix, center, y + 0.014, radius * 0.46, 96, spin * 1.35, 0.065, color, alpha * 0.82f);
        runeTicks(buffer, matrix, center, y + 0.019, radius * 0.92, 24, spin * 0.45, radius * 0.075, 0.048, color, alpha * 0.88f);
        hexagram(buffer, matrix, center, y + 0.026, radius * 0.68, spin * 0.22 - Math.PI / 2.0, 0.105, color, alpha * 1.12f);
        polygon(buffer, matrix, center, y + 0.032, radius * 0.36, 6, -spin * 0.7, 0.055, color, alpha * 0.78f);
        spokes(buffer, matrix, center, y + 0.038, radius * 0.62, 6, spin * 0.22 - Math.PI / 2.0, 0.052, color, alpha * 0.70f);
        starNodes(buffer, matrix, center, y + 0.044, radius * 0.68, spin * 0.22 - Math.PI / 2.0, radius * 0.04, color, alpha);
        double bloomRadius = radius * (0.92 + Math.sin(progress * Math.PI * 2.0 + offset) * 0.025);
        ring(buffer, matrix, center, y + 0.034, bloomRadius, 64, -spin * 0.7, 0.04, color, alpha * 0.48f);
    }

    static void renderSealDisk(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double y, double radius, double spin, Color color, float alpha, boolean upper) {
        double direction = upper ? -1.0 : 1.0;
        ring(buffer, matrix, center, y, radius, 96, spin, 0.064, color, alpha);
        ring(buffer, matrix, center, y + 0.006 * direction, radius * 0.72, 72, -spin * 0.85, 0.044, color, alpha * 0.72f);
        hexagram(buffer, matrix, center, y + 0.014 * direction, radius * 0.60, spin * 0.35 - Math.PI / 2.0, 0.068, color, alpha * 0.98f);
        polygon(buffer, matrix, center, y + 0.021 * direction, radius * 0.34, 6, -spin * 0.7, 0.036, color, alpha * 0.62f);
        runeTicks(buffer, matrix, center, y + 0.028 * direction, radius * 0.88, 18, spin * 0.52, radius * 0.060, 0.030, color, alpha * 0.70f);
    }

    static void renderVerticalPortal(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double radius, float yaw, Color color, Color coreColor, float alpha, float progress, float age) {
        double yawRad = yaw * Math.PI / 180.0;
        Vec3 normal = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        if (normal.lengthSqr() < 1.0E-5) {
            normal = new Vec3(0.0, 0.0, 1.0);
        } else {
            normal = normal.normalize();
        }
        Vec3 right = new Vec3(normal.z, 0.0, -normal.x).normalize();
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        double spin = age * 0.018;
        double pulse = 1.0 + Math.sin(progress * Math.PI * 2.0 + age * 0.08) * 0.025;
        planeRing(buffer, matrix, center, right, up, normal, radius * pulse, 128, spin, 0.130, color, alpha);
        planeRing(buffer, matrix, center.add(normal.scale(0.012)), right, up, normal, radius * 0.84, 128, -spin * 0.76, 0.076, coreColor, alpha * 0.78f);
        planeRing(buffer, matrix, center.add(normal.scale(0.020)), right, up, normal, radius * 0.48, 96, spin * 1.18, 0.066, color, alpha * 0.82f);
        planeRuneTicks(buffer, matrix, center.add(normal.scale(0.030)), right, up, normal, radius * 0.92, 30, -spin * 0.38, radius * 0.082, 0.045, coreColor, alpha * 0.82f);
        planeHexagram(buffer, matrix, center.add(normal.scale(0.042)), right, up, normal, radius * 0.66, spin * 0.26 + Math.PI / 2.0, 0.098, color, alpha);
        planePolygon(buffer, matrix, center.add(normal.scale(0.052)), right, up, normal, radius * 0.34, 6, -spin * 0.72, 0.048, coreColor, alpha * 0.66f);
        planeSpokes(buffer, matrix, center.add(normal.scale(0.062)), right, up, normal, radius * 0.60, 6, spin * 0.26 + Math.PI / 2.0, 0.046, coreColor, alpha * 0.62f);
        planeStarNodes(buffer, matrix, center.add(normal.scale(0.074)), right, up, normal, radius * 0.66, spin * 0.26 + Math.PI / 2.0, radius * 0.040, color, alpha * 0.92f);
    }

    static void ring(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double y, double radius, int segments, double spin, double width, Color color, float alpha) {
        for (int i = 0; i < segments; i++) {
            double a0 = spin + Math.PI * 2.0 * i / segments;
            double a1 = spin + Math.PI * 2.0 * (i + 1) / segments;
            Vec3 p0 = new Vec3(center.x + Math.cos(a0) * radius, y, center.z + Math.sin(a0) * radius);
            Vec3 p1 = new Vec3(center.x + Math.cos(a1) * radius, y, center.z + Math.sin(a1) * radius);
            line(buffer, matrix, p0, p1, width, color, alpha);
        }
    }

    static void spokes(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double y, double radius, int points, double spin, double width, Color color, float alpha) {
        for (int i = 0; i < points; i++) {
            double angle = spin + Math.PI * 2.0 * i / points;
            Vec3 start = new Vec3(center.x + Math.cos(angle) * radius * 0.24, y, center.z + Math.sin(angle) * radius * 0.24);
            Vec3 end = new Vec3(center.x + Math.cos(angle) * radius, y, center.z + Math.sin(angle) * radius);
            line(buffer, matrix, start, end, width, color, alpha);
        }
    }

    static void line(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b, double width, Color color, float alpha) {
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

    static void planeLine(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 normal, double width, Color color, float alpha) {
        Vec3 direction = b.subtract(a);
        Vec3 side = direction.cross(normal);
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

    static void quad(BufferBuilder buffer, Matrix4f matrix, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, Color color, float alpha) {
        vertex(buffer, matrix, p0, color, alpha);
        vertex(buffer, matrix, p1, color, alpha);
        vertex(buffer, matrix, p2, color, alpha);
        vertex(buffer, matrix, p3, color, alpha);
    }

    private static void polygon(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double y, double radius, int points, double spin, double width, Color color, float alpha) {
        Vec3[] vertices = new Vec3[points];
        for (int i = 0; i < points; i++) {
            double angle = spin + Math.PI * 2.0 * i / points;
            vertices[i] = new Vec3(center.x + Math.cos(angle) * radius, y, center.z + Math.sin(angle) * radius);
        }
        for (int i = 0; i < points; i++) {
            line(buffer, matrix, vertices[i], vertices[(i + 1) % points], width, color, alpha);
        }
    }

    private static void hexagram(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double y, double radius, double spin, double width, Color color, float alpha) {
        polygon(buffer, matrix, center, y, radius, 3, spin, width, color, alpha);
        polygon(buffer, matrix, center, y + 0.006, radius, 3, spin + Math.PI, width, color, alpha * 0.92f);
    }

    private static void runeTicks(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double y, double radius, int points, double spin, double length, double width, Color color, float alpha) {
        for (int i = 0; i < points; i++) {
            double angle = spin + Math.PI * 2.0 * i / points;
            Vec3 tangent = new Vec3(-Math.sin(angle), 0.0, Math.cos(angle)).scale(length * (i % 3 == 0 ? 1.25 : 0.75));
            Vec3 middle = new Vec3(center.x + Math.cos(angle) * radius, y, center.z + Math.sin(angle) * radius);
            line(buffer, matrix, middle.subtract(tangent.scale(0.5)), middle.add(tangent.scale(0.5)), width, color, alpha);
        }
    }

    private static void starNodes(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double y, double radius, double spin, double size, Color color, float alpha) {
        for (int i = 0; i < 6; i++) {
            double angle = spin + Math.PI * 2.0 * i / 6.0;
            Vec3 middle = new Vec3(center.x + Math.cos(angle) * radius, y, center.z + Math.sin(angle) * radius);
            Vec3 radial = new Vec3(Math.cos(angle), 0.0, Math.sin(angle)).scale(size);
            Vec3 tangent = new Vec3(-Math.sin(angle), 0.0, Math.cos(angle)).scale(size * 0.72);
            quad(buffer, matrix, middle.add(radial), middle.add(tangent), middle.subtract(radial), middle.subtract(tangent), color, alpha);
        }
    }

    private static void planeRing(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up, Vec3 normal, double radius, int segments, double spin, double width, Color color, float alpha) {
        for (int i = 0; i < segments; i++) {
            double a0 = spin + Math.PI * 2.0 * i / segments;
            double a1 = spin + Math.PI * 2.0 * (i + 1) / segments;
            Vec3 p0 = planePoint(center, right, up, Math.cos(a0) * radius, Math.sin(a0) * radius);
            Vec3 p1 = planePoint(center, right, up, Math.cos(a1) * radius, Math.sin(a1) * radius);
            planeLine(buffer, matrix, p0, p1, normal, width, color, alpha);
        }
    }

    private static void planeSpokes(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up, Vec3 normal, double radius, int points, double spin, double width, Color color, float alpha) {
        for (int i = 0; i < points; i++) {
            double angle = spin + Math.PI * 2.0 * i / points;
            Vec3 start = planePoint(center, right, up, Math.cos(angle) * radius * 0.24, Math.sin(angle) * radius * 0.24);
            Vec3 end = planePoint(center, right, up, Math.cos(angle) * radius, Math.sin(angle) * radius);
            planeLine(buffer, matrix, start, end, normal, width, color, alpha);
        }
    }

    private static void planePolygon(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up, Vec3 normal, double radius, int points, double spin, double width, Color color, float alpha) {
        Vec3[] vertices = new Vec3[points];
        for (int i = 0; i < points; i++) {
            double angle = spin + Math.PI * 2.0 * i / points;
            vertices[i] = planePoint(center, right, up, Math.cos(angle) * radius, Math.sin(angle) * radius);
        }
        for (int i = 0; i < points; i++) {
            planeLine(buffer, matrix, vertices[i], vertices[(i + 1) % points], normal, width, color, alpha);
        }
    }

    private static void planeHexagram(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up, Vec3 normal, double radius, double spin, double width, Color color, float alpha) {
        planePolygon(buffer, matrix, center, right, up, normal, radius, 3, spin, width, color, alpha);
        planePolygon(buffer, matrix, center.add(normal.scale(0.012)), right, up, normal, radius, 3, spin + Math.PI, width, color, alpha * 0.92f);
    }

    private static void planeRuneTicks(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up, Vec3 normal, double radius, int points, double spin, double length, double width, Color color, float alpha) {
        for (int i = 0; i < points; i++) {
            double angle = spin + Math.PI * 2.0 * i / points;
            Vec3 radial = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 tangent = right.scale(-Math.sin(angle)).add(up.scale(Math.cos(angle))).scale(length * (i % 3 == 0 ? 1.25 : 0.72));
            Vec3 middle = center.add(radial.scale(radius));
            planeLine(buffer, matrix, middle.subtract(tangent.scale(0.5)), middle.add(tangent.scale(0.5)), normal, width, color, alpha);
        }
    }

    private static void planeStarNodes(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up, Vec3 normal, double radius, double spin, double size, Color color, float alpha) {
        for (int i = 0; i < 6; i++) {
            double angle = spin + Math.PI * 2.0 * i / 6.0;
            Vec3 radial = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 tangent = right.scale(-Math.sin(angle)).add(up.scale(Math.cos(angle)));
            Vec3 middle = center.add(radial.scale(radius));
            quad(buffer, matrix, middle.add(radial.scale(size)), middle.add(tangent.scale(size * 0.72)), middle.subtract(radial.scale(size)), middle.subtract(tangent.scale(size * 0.72)), color, alpha);
        }
    }

    private static Vec3 planePoint(Vec3 center, Vec3 right, Vec3 up, double x, double y) {
        return center.add(right.scale(x)).add(up.scale(y));
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 pos, Color color, float alpha) {
        buffer.vertex(matrix, (float)pos.x, (float)pos.y, (float)pos.z)
                .color(color.red, color.green, color.blue, Mth.clamp(alpha, 0.0f, 1.0f)).endVertex();
    }

    record Color(float red, float green, float blue) {
    }
}
