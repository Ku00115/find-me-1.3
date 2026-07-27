package com.kuzhi.findme.client;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.network.RescueMagicPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ClientRescueMagicRenderState {
    private static final List<Effect> EFFECTS = new ArrayList<>();

    private ClientRescueMagicRenderState() {
    }

    public static void start(RescueMagicPacket packet) {
        if (Config.enableDiagnosticLogging) {
            FindMeMod.LOGGER.info("[FindMe debug/effect-sizing] side=client kind=arrival entityId={} purpose={} visual={} radius={} height={} anchor={},{},{}",
                    packet.entityId(), packet.purpose(), packet.visual(), packet.radius(), packet.height(), packet.x(), packet.y(), packet.z());
        }
        if (packet.groundEmerge()) {
            ClientBurrowEffectState.startEmerging(packet.entityId(), new Vec3(packet.x(), packet.y(), packet.z()), packet.radius(), packet.height(), Math.max(1, packet.durationTicks()), packet.purpose());
            return;
        }
        if (packet.entityId() >= 0) {
            EFFECTS.removeIf(effect -> effect.entityId == packet.entityId() && effect.purpose == packet.purpose());
        }
        addEffect(new Effect(packet.entityId(), new Vec3(packet.x(), packet.y(), packet.z()), packet.radius(), packet.height(), packet.yaw(), Math.max(1, packet.durationTicks()), packet.style(), packet.purpose(), packet.customTexture(), packet.groundEmerge(), packet.velocityBurst(), false));
    }

    static void triggerVelocityBurst(Entity entity, float intensity, boolean impact, boolean threeDimensional,
                                     boolean playImpactSound) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == null || minecraft.level == null || entity.level() != minecraft.level) {
            return;
        }
        Vec3 direction = threeDimensional
                ? entity.getDeltaMovement() : entity.getDeltaMovement().multiply(1.0, 0.0, 1.0);
        if (direction.lengthSqr() < 1.0E-5) {
            double yawRadians = Math.toRadians(entity.getYRot());
            direction = new Vec3(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
        } else {
            direction = direction.normalize();
        }
        Vec3 center = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
        float radius = Math.max(0.28f, Math.max(entity.getBbWidth() * 0.56f, entity.getBbHeight() * 0.30f));
        Effect effect = new Effect(entity.getId(), center, radius, entity.getBbHeight(), entity.getYRot(), 1,
                RescueMagicPacket.Style.VERTICAL_PORTAL, RescueMagicPacket.Purpose.SUMMON,
                false, false, true, threeDimensional);
        effect.burstScale = Mth.clamp(intensity, 0.35f, 1.35f);
        effect.burstImpact = impact;
        effect.burstSound = playImpactSound;
        effect.trackedCenter = center;
        effect.motionDirection = direction;
        effect.beginBurst(minecraft, center, direction);
        addEffect(effect);
    }

    private static void addEffect(Effect effect) {
        EFFECTS.add(effect);
        while (EFFECTS.size() > 8) {
            EFFECTS.remove(0);
        }
    }

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            EFFECTS.clear();
            return;
        }
        Iterator<Effect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            effect.track(minecraft.level.getEntity(effect.entityId));
            effect.age++;
            if (effect.burstAge >= 0) {
                effect.burstAge++;
            }
            boolean burstFinished = effect.burstAge < 0 || effect.burstAge > Effect.BURST_LIFETIME_TICKS;
            if (effect.age >= effect.durationTicks && burstFinished) {
                iterator.remove();
            }
        }
    }

    static List<Effect> effects() {
        return EFFECTS;
    }

    static void clear() {
        EFFECTS.clear();
    }

    static final class Effect {
        private static final int BURST_LIFETIME_TICKS = 30;
        private static final double HIGH_SPEED_THRESHOLD = 0.30;
        private static final double MAX_TRACKED_STEP = 12.0;

        final int entityId;
        final Vec3 center;
        final float radius;
        final float height;
        final float yaw;
        final int durationTicks;
        final RescueMagicPacket.Style style;
        final RescueMagicPacket.Purpose purpose;
        final boolean customTexture;
        final boolean groundEmerge;
        final boolean velocityBurst;
        final boolean burstThreeDimensional;
        int age;
        Vec3 trackedCenter;
        Vec3 motionDirection;
        double smoothedSpeed;
        Vec3 burstCenter;
        Vec3 burstDirection;
        int burstAge = -1;
        float burstScale = 1.0f;
        boolean burstImpact = true;
        boolean burstSound = true;

        Effect(int entityId, Vec3 center, float radius, float height, float yaw, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose, boolean customTexture, boolean groundEmerge, boolean velocityBurst, boolean burstThreeDimensional) {
            this.entityId = entityId;
            this.center = center;
            this.radius = radius;
            this.height = height;
            this.yaw = yaw;
            this.durationTicks = durationTicks;
            this.style = style;
            this.purpose = purpose;
            this.customTexture = customTexture;
            this.groundEmerge = groundEmerge;
            this.velocityBurst = velocityBurst;
            this.burstThreeDimensional = burstThreeDimensional;
        }

        private void track(Entity entity) {
            if (!velocityBurst || entity == null) {
                smoothedSpeed *= 0.72;
                return;
            }
            Vec3 current = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
            if (trackedCenter == null) {
                trackedCenter = current;
                return;
            }
            Vec3 delta = current.subtract(trackedCenter);
            if (!burstThreeDimensional) {
                delta = delta.multiply(1.0, 0.0, 1.0);
            }
            double step = delta.length();
            trackedCenter = current;
            if (step < 1.0E-4 || step > MAX_TRACKED_STEP) {
                smoothedSpeed *= 0.72;
                return;
            }
            Vec3 instantaneousDirection = delta.scale(1.0 / step);
            if (motionDirection == null || motionDirection.dot(instantaneousDirection) < -0.25) {
                motionDirection = instantaneousDirection;
            } else {
                Vec3 blended = motionDirection.scale(0.62).add(instantaneousDirection.scale(0.38));
                motionDirection = blended.lengthSqr() < 1.0E-5 ? instantaneousDirection : blended.normalize();
            }
            smoothedSpeed = smoothedSpeed * 0.42 + step * 0.58;
            if (burstAge < 0 && age >= 2 && smoothedSpeed >= HIGH_SPEED_THRESHOLD) {
                beginBurst(Minecraft.getInstance(), current, motionDirection);
            } else if (burstAge < 0 && age >= 8) {
                double yawRadians = Math.toRadians(yaw);
                Vec3 fallbackDirection = motionDirection == null
                        ? new Vec3(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians)).normalize()
                        : motionDirection;
                beginBurst(Minecraft.getInstance(), current, fallbackDirection);
            }
        }

        private void beginBurst(Minecraft minecraft, Vec3 current, Vec3 direction) {
            burstDirection = direction;
            burstCenter = current.subtract(direction.scale(Math.max(0.04, radius * 0.28)));
            burstAge = 0;
            if (minecraft.level == null || !burstImpact || !burstSound
                    || !ClientWheelPresentationState.operationSounds()) {
                return;
            }
            minecraft.level.playLocalSound(current.x, current.y, current.z, SoundEvents.GENERIC_EXPLODE,
                    SoundSource.NEUTRAL, 0.52f, 1.28f, false);
            minecraft.level.playLocalSound(current.x, current.y, current.z, SoundEvents.FIREWORK_ROCKET_BLAST,
                    SoundSource.NEUTRAL, 0.72f, 0.76f, false);
            minecraft.level.playLocalSound(current.x, current.y, current.z, SoundEvents.ENDER_DRAGON_FLAP,
                    SoundSource.NEUTRAL, 0.62f, 1.32f, false);
        }
    }
}
