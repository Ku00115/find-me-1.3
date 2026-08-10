package com.kuzhi.findme.server.lifecycle;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class CompanionCinematicOrientationHelper {
    private CompanionCinematicOrientationHelper() {
    }

    public static float yawToward(Vec3 direction) {
        if (!isFinite(direction)) {
            return 0.0f;
        }
        return (float)(Mth.atan2(direction.z, direction.x) * 180.0 / Math.PI) - 90.0f;
    }

    public static float yawTowardStable(LivingEntity living, Vec3 direction) {
        Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
        if (!isFinite(horizontal)) {
            return finiteOr(living.getYRot(), 0.0f);
        }
        if (horizontal.lengthSqr() < 0.0025) {
            return finiteOr(living.getYRot(), 0.0f);
        }
        return yawToward(horizontal.normalize());
    }

    public static float pitchToward(Vec3 direction) {
        return pitchToward(direction, 35.0f);
    }

    public static float pitchToward(Vec3 direction, float maxAngle) {
        if (!isFinite(direction)) {
            return 0.0f;
        }
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float limit = Mth.clamp(maxAngle, 1.0f, 89.0f);
        return Mth.clamp((float)(-(Mth.atan2(direction.y, horizontal) * 180.0 / Math.PI)), -limit, limit);
    }

    public static void faceYaw(LivingEntity living, float yaw) {
        yaw = finiteOr(yaw, finiteOr(living.getYRot(), 0.0f));
        living.setYRot(yaw);
        living.setYHeadRot(yaw);
        living.yRotO = yaw;
        living.yBodyRot = yaw;
        living.yBodyRotO = yaw;
        living.yHeadRot = yaw;
        living.yHeadRotO = yaw;
    }

    private static boolean isFinite(Vec3 direction) {
        return direction != null && Double.isFinite(direction.x)
                && Double.isFinite(direction.y) && Double.isFinite(direction.z);
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
