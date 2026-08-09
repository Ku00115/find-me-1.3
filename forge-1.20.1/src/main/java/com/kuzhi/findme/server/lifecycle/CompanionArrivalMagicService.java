package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.data.CompanionDataService;


import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.RescueMagicPacket;
import com.kuzhi.findme.server.animation.CompanionEnderEffectService;
import com.kuzhi.findme.server.animation.CompanionMagicAudioService;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public final class CompanionArrivalMagicService {
    private static final double MAGIC_PACKET_RADIUS = 96.0;

    private CompanionArrivalMagicService() {
    }

    public static void send(ServerPlayer player, LivingEntity living, Vec3 focus, int durationTicks) {
        send(player, living, focus, durationTicks, RescueMagicPacket.Style.VERTICAL_PORTAL, RescueMagicPacket.Purpose.SUMMON);
    }

    public static void sendGround(ServerPlayer player, LivingEntity living, Vec3 focus, int durationTicks) {
        send(player, living, focus, durationTicks, RescueMagicPacket.Style.GROUND_CIRCLE, RescueMagicPacket.Purpose.SUMMON);
    }

    public static void sendGround(ServerPlayer player, LivingEntity living, Vec3 focus, int durationTicks, RescueMagicPacket.Purpose purpose) {
        send(player, living, focus, durationTicks, RescueMagicPacket.Style.GROUND_CIRCLE, purpose);
    }

    public static void beginAt(ServerPlayer player, LivingEntity living, BlockPos anchor, Vec3 focus, int durationTicks, RescueMagicPacket.Style style) {
        beginAt(player, living, Vec3.atBottomCenterOf(anchor), focus, durationTicks, style, RescueMagicPacket.Purpose.SUMMON);
    }

    public static void beginAt(ServerPlayer player, LivingEntity living, BlockPos anchor, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        beginAt(player, living, Vec3.atBottomCenterOf(anchor), focus, durationTicks, style, purpose);
    }

    public static void beginAt(ServerPlayer player, LivingEntity living, Vec3 anchor, Vec3 focus, int durationTicks, RescueMagicPacket.Style style) {
        beginAt(player, living, anchor, focus, durationTicks, style, RescueMagicPacket.Purpose.SUMMON);
    }

    public static void beginAt(ServerPlayer player, LivingEntity living, Vec3 anchor, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        sendAt(player, effectLevel(player, living), living, anchor, focus, durationTicks, style, purpose);
        CompanionArrivalSequenceService.startWithDelay(player, living, focus == null ? player.position() : focus, purpose, revealDelayTicks(player, living, durationTicks, style, purpose));
    }

    public static void openAt(ServerPlayer player, LivingEntity living, BlockPos anchor, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        openAt(player, living, Vec3.atBottomCenterOf(anchor), focus, durationTicks, style, purpose);
    }

    public static void openAt(ServerPlayer player, LivingEntity living, Vec3 anchor, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        sendAt(player, effectLevel(player, living), living, anchor, focus, durationTicks, style, purpose, null);
    }

    public static void openEffectAt(ServerPlayer player, LivingEntity living, Vec3 anchor, Vec3 focus,
                                    int durationTicks, RescueMagicPacket.Style style,
                                    RescueMagicPacket.Purpose purpose) {
        sendAt(player, effectLevel(player, living), living, anchor, focus, durationTicks, style, purpose,
                null, animationPurpose(purpose), false);
    }

    public static void openAt(ServerPlayer player, LivingEntity living, Vec3 anchor, Vec3 focus, int durationTicks,
                              RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose,
                              CompanionEntityVisualBoundsService.VisualDimensions storedDimensions) {
        sendAt(player, effectLevel(player, living), living, anchor, focus, durationTicks, style, purpose, storedDimensions);
    }

    public static void openAt(ServerPlayer player, LivingEntity living, Vec3 anchor, Vec3 focus, int durationTicks,
                              RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose,
                              CompanionEntityVisualBoundsService.VisualDimensions storedDimensions,
                              CompanionAnimationPurpose animationPurpose) {
        sendAt(player, effectLevel(player, living), living, anchor, focus, durationTicks, style, purpose,
                storedDimensions, animationPurpose);
    }

    public static void openAt(ServerLevel level, ServerPlayer player, LivingEntity living, Vec3 anchor, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        sendAt(player, level, living, anchor, focus, durationTicks, style, purpose, null);
    }

    public static void openAt(ServerLevel level, ServerPlayer player, LivingEntity living, Vec3 anchor, Vec3 focus,
                              int durationTicks, RescueMagicPacket.Style style,
                              RescueMagicPacket.Purpose purpose, CompanionAnimationPurpose animationPurpose) {
        sendAt(player, level, living, anchor, focus, durationTicks, style, purpose, null, animationPurpose);
    }

    public static void openPulse(ServerPlayer player, Vec3 anchor, float radius, int durationTicks, RescueMagicPacket.Purpose purpose) {
        float yaw = yawToward(anchor, player.position());
        RescueMagicPacket packet = new RescueMagicPacket(anchor.x, anchor.y, anchor.z, radius, 1.6f, yaw, durationTicks, RescueMagicPacket.Style.GROUND_CIRCLE, purpose, false);
        ModNetwork.sendToPlayersNear(player.serverLevel(), anchor, MAGIC_PACKET_RADIUS, packet);
        CompanionMagicAudioService.playCircleOpen(player, anchor, radius, purpose == RescueMagicPacket.Purpose.RESCUE);
    }

    /** Opens a summon presentation from stored metadata before the real entity exists in the world. */
    public static boolean openStoredAt(ServerLevel level, ServerPlayer player, UUID companionUuid, String entityType,
                                       Vec3 anchor, Vec3 focus, int durationTicks, RescueMagicPacket.Style style,
                                       RescueMagicPacket.Purpose purpose,
                                       CompanionEntityVisualBoundsService.VisualDimensions storedDimensions,
                                       CompanionAnimationPurpose animationPurpose) {
        CompanionEntityVisualBoundsService.VisualDimensions dimensions = storedDimensions == null
                ? new CompanionEntityVisualBoundsService.VisualDimensions(1.0, 1.0, 1.0)
                : storedDimensions;
        CompanionEffectPurpose effectPurpose = purpose == RescueMagicPacket.Purpose.RESCUE
                ? CompanionEffectPurpose.RESCUE : CompanionEffectPurpose.SUMMON;
        CompanionEffectStyle selected = animationPurpose == CompanionAnimationPurpose.SWITCH
                ? CompanionEffectStyle.NONE
                : CompanionDataService.data(player).effectStyle(companionUuid, effectPurpose, entityType);
        CompanionAnimationStyle animation = CompanionDataService.data(player)
                .animationStyle(companionUuid, animationPurpose, entityType);
        boolean sent = false;
        if (animation == CompanionAnimationStyle.GROUND_EMERGE) {
            float radius = CompanionEntityVisualBoundsService.summonCircleRadius(entityType, dimensions);
            float height = CompanionEntityVisualBoundsService.portalHeight(dimensions);
            RescueMagicPacket packet = new RescueMagicPacket(anchor.x, anchor.y, anchor.z, radius, height,
                    yawToward(anchor, focus == null ? player.position() : focus), burrowDurationTicks(durationTicks, purpose),
                    RescueMagicPacket.Style.GROUND_CIRCLE, purpose, RescueMagicPacket.Visual.GROUND_EMERGE);
            ModNetwork.sendToPlayersNear(level, anchor, MAGIC_PACKET_RADIUS, packet);
            sent = true;
        }
        if (selected == CompanionEffectStyle.NONE) {
            return sent;
        }
        if (selected == CompanionEffectStyle.ENDER) {
            CompanionEnderEffectService.play(player,
                    CompanionEntityVisualBoundsService.effectBoundsAt(anchor, dimensions));
            return true;
        }
        float radius = selected == CompanionEffectStyle.VELOCITY_BURST
                ? CompanionEntityVisualBoundsService.impactRingRadius(entityType, dimensions)
                : style == RescueMagicPacket.Style.GROUND_CIRCLE
                ? CompanionEntityVisualBoundsService.summonCircleRadius(entityType, dimensions)
                : CompanionEntityVisualBoundsService.portalRadius(entityType, dimensions);
        float height = CompanionEntityVisualBoundsService.portalHeight(dimensions);
        RescueMagicPacket.Visual visual = selected == CompanionEffectStyle.CUSTOM_MAGIC_CIRCLE
                ? RescueMagicPacket.Visual.CUSTOM_TEXTURE
                : selected == CompanionEffectStyle.VELOCITY_BURST
                ? RescueMagicPacket.Visual.VELOCITY_BURST : RescueMagicPacket.Visual.DEFAULT;
        ModNetwork.sendToPlayersNear(level, anchor, MAGIC_PACKET_RADIUS,
                new RescueMagicPacket(anchor.x, anchor.y, anchor.z, radius, height,
                        yawToward(anchor, focus == null ? player.position() : focus), durationTicks, style, purpose, visual));
        if (visual != RescueMagicPacket.Visual.VELOCITY_BURST) {
            CompanionMagicAudioService.playCircleOpen(player, anchor, radius, purpose == RescueMagicPacket.Purpose.RESCUE);
        }
        FindMeDebugLogger.info("arrival-presentation",
                "entity={} purpose={} effect={} animation={} duration={} anchor={} source=stored",
                companionUuid + "/" + entityType, purpose, selected, animation, durationTicks, anchor);
        return true;
    }

    public static void send(ServerPlayer player, LivingEntity living, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        send(player, living, focus, durationTicks, style, purpose, animationPurpose(purpose));
    }

    public static void send(ServerPlayer player, LivingEntity living, Vec3 focus, int durationTicks,
                            RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose,
                            CompanionAnimationPurpose animationPurpose) {
        Vec3 anchor = living.position();
        sendAt(player, effectLevel(player, living), living, anchor, focus, durationTicks, style, purpose, null, animationPurpose);
        CompanionArrivalSequenceService.startWithDelay(player, living, focus == null ? player.position() : focus,
                purpose, revealDelayTicks(player, living, durationTicks, style, purpose, animationPurpose));
    }

    public static int revealDelayTicks(int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        boolean rescue = purpose == RescueMagicPacket.Purpose.RESCUE;
        float progress;
        if (rescue) {
            progress = style == RescueMagicPacket.Style.VERTICAL_PORTAL ? 0.24f : 0.22f;
        } else {
            // Release in the opening phase. The entity must begin its approach as
            // soon as it becomes visible; leaving it at the anchor until the
            // circle's closing half makes summon and switch feel frozen.
            progress = 0.28f;
        }
        return Mth.clamp(Math.round(durationTicks * progress), 1, 24);
    }

    public static int revealDelayTicks(int durationTicks, RescueMagicPacket.Style style,
                                       RescueMagicPacket.Purpose purpose, CompanionAnimationStyle animation) {
        if (animation == CompanionAnimationStyle.NONE) return 0;
        if (animation == CompanionAnimationStyle.GROUND_EMERGE) {
            int burrowDuration = burrowDurationTicks(durationTicks, purpose);
            float revealProgress = purpose == RescueMagicPacket.Purpose.RESCUE ? 0.66f : 0.68f;
            return Mth.clamp(Math.round(burrowDuration * revealProgress), 1, 64);
        }
        return revealDelayTicks(durationTicks, style, purpose);
    }

    private static void sendAt(ServerPlayer player, ServerLevel level, LivingEntity living, Vec3 anchor, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        sendAt(player, level, living, anchor, focus, durationTicks, style, purpose, null);
    }

    private static void sendAt(ServerPlayer player, ServerLevel level, LivingEntity living, Vec3 anchor, Vec3 focus, int durationTicks,
                               RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose,
                               CompanionEntityVisualBoundsService.VisualDimensions storedDimensions) {
        sendAt(player, level, living, anchor, focus, durationTicks, style, purpose, storedDimensions, animationPurpose(purpose));
    }

    private static void sendAt(ServerPlayer player, ServerLevel level, LivingEntity living, Vec3 anchor, Vec3 focus,
                               int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose,
                               CompanionEntityVisualBoundsService.VisualDimensions storedDimensions,
                               CompanionAnimationPurpose animationPurpose) {
        sendAt(player, level, living, anchor, focus, durationTicks, style, purpose, storedDimensions,
                animationPurpose, true);
    }

    private static void sendAt(ServerPlayer player, ServerLevel level, LivingEntity living, Vec3 anchor, Vec3 focus,
                               int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose,
                               CompanionEntityVisualBoundsService.VisualDimensions storedDimensions,
                               CompanionAnimationPurpose animationPurpose, boolean includeEntityAnimation) {
        CompanionEffectPurpose effectPurpose = purpose == RescueMagicPacket.Purpose.RESCUE ? CompanionEffectPurpose.RESCUE : CompanionEffectPurpose.SUMMON;
        String entityType = living == null ? "" : EntityType.getKey(living.getType()).toString();
        CompanionEffectStyle selected = animationPurpose == CompanionAnimationPurpose.SWITCH
                ? CompanionEffectStyle.NONE
                : CompanionDataService.data(player).effectStyle(living.getUUID(), effectPurpose, entityType);
        CompanionEntityVisualBoundsService.VisualDimensions dimensions = storedDimensions == null
                ? CompanionEntityVisualBoundsService.effectDimensions(living) : storedDimensions;
        AABB liveBounds = CompanionEntityVisualBoundsService.effectBounds(living);
        CompanionAnimationStyle animation = includeEntityAnimation
                ? CompanionDataService.data(player).animationStyle(living.getUUID(), animationPurpose, entityType)
                : CompanionAnimationStyle.NONE;
        FindMeDebugLogger.info("arrival-presentation",
                "entity={} purpose={} effect={} animation={} duration={} anchor={}",
                FindMeDebugLogger.entity(living), purpose, selected, animation, durationTicks, anchor);
        if (includeEntityAnimation) {
            sendAnimationAt(player, level, living, anchor, focus, durationTicks, purpose, dimensions, animation);
        }
        if (selected == CompanionEffectStyle.NONE) return;
        if (selected == CompanionEffectStyle.ENDER) {
            CompanionEnderEffectService.play(player, CompanionEntityVisualBoundsService.effectBounds(living, dimensions));
            FindMeDebugLogger.info("effect-sizing", "side=server entity={} purpose={} style={} source={} envelope={} live={} dimensions={}x{}x{}",
                    FindMeDebugLogger.entity(living), purpose, selected, storedDimensions == null ? "live" : "stored",
                    CompanionEntityVisualBoundsService.effectEnvelopeSource(living), FindMeDebugLogger.box(liveBounds), dimensions.width(), dimensions.height(), dimensions.depth());
            return;
        }
        RescueMagicPacket.Style packetStyle = style;
        float radius = selected == CompanionEffectStyle.VELOCITY_BURST
                ? CompanionEntityVisualBoundsService.impactRingRadius(living, dimensions)
                : packetStyle == RescueMagicPacket.Style.GROUND_CIRCLE
                ? CompanionEntityVisualBoundsService.summonCircleRadius(living, dimensions)
                : CompanionEntityVisualBoundsService.portalRadius(living, dimensions);
        float height = CompanionEntityVisualBoundsService.portalHeight(living, dimensions);
        float yaw = yawToward(anchor, focus == null ? player.position() : focus);
        RescueMagicPacket.Visual visual = selected == CompanionEffectStyle.CUSTOM_MAGIC_CIRCLE
                ? RescueMagicPacket.Visual.CUSTOM_TEXTURE
                : selected == CompanionEffectStyle.VELOCITY_BURST
                ? RescueMagicPacket.Visual.VELOCITY_BURST
                : RescueMagicPacket.Visual.DEFAULT;
        RescueMagicPacket packet = new RescueMagicPacket(living.getId(), anchor.x, anchor.y, anchor.z, radius, height, yaw, durationTicks, packetStyle, purpose, visual);
        FindMeDebugLogger.info("effect-sizing", "side=server entity={} purpose={} style={} visual={} source={} envelope={} live={} dimensions={}x{}x{} packetRadius={} packetHeight={}",
                FindMeDebugLogger.entity(living), purpose, selected, visual, storedDimensions == null ? "live" : "stored",
                CompanionEntityVisualBoundsService.effectEnvelopeSource(living), FindMeDebugLogger.box(liveBounds), dimensions.width(), dimensions.height(), dimensions.depth(), radius, height);
        ModNetwork.sendToPlayersNear(level == null ? player.serverLevel() : level, anchor, MAGIC_PACKET_RADIUS, packet);
        if (visual != RescueMagicPacket.Visual.VELOCITY_BURST) {
            CompanionMagicAudioService.playCircleOpen(player, anchor, radius, purpose == RescueMagicPacket.Purpose.RESCUE);
        }
    }
    public static int revealDelayTicks(ServerPlayer player, LivingEntity living, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        return revealDelayTicks(player, living, durationTicks, style, purpose, animationPurpose(purpose));
    }

    public static int revealDelayTicks(ServerPlayer player, LivingEntity living, int durationTicks,
                                       RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose,
                                       CompanionAnimationPurpose animationPurpose) {
        String entityType = living == null ? "" : EntityType.getKey(living.getType()).toString();
        CompanionAnimationStyle selected = CompanionDataService.data(player).animationStyle(living.getUUID(), animationPurpose, entityType);
        return revealDelayTicks(durationTicks, style, purpose, selected);
    }

    private static void sendAnimationAt(ServerPlayer player, ServerLevel level, LivingEntity living, Vec3 anchor,
                                        Vec3 focus, int durationTicks, RescueMagicPacket.Purpose purpose,
                                        CompanionEntityVisualBoundsService.VisualDimensions dimensions,
                                        CompanionAnimationStyle animation) {
        if (animation != CompanionAnimationStyle.GROUND_EMERGE) return;
        float radius = CompanionEntityVisualBoundsService.summonCircleRadius(living, dimensions);
        float height = CompanionEntityVisualBoundsService.portalHeight(living, dimensions);
        float yaw = yawToward(anchor, focus == null ? player.position() : focus);
        int animationDuration = burrowDurationTicks(durationTicks, purpose);
        RescueMagicPacket packet = new RescueMagicPacket(living.getId(), anchor.x, anchor.y, anchor.z, radius, height,
                yaw, animationDuration, RescueMagicPacket.Style.GROUND_CIRCLE, purpose,
                RescueMagicPacket.Visual.GROUND_EMERGE);
        ModNetwork.sendToPlayersNear(level == null ? player.serverLevel() : level, anchor, MAGIC_PACKET_RADIUS, packet);
    }

    private static CompanionAnimationPurpose animationPurpose(RescueMagicPacket.Purpose purpose) {
        return purpose == RescueMagicPacket.Purpose.RESCUE
                ? CompanionAnimationPurpose.RESCUE : CompanionAnimationPurpose.SUMMON;
    }

    private static int burrowDurationTicks(int durationTicks, RescueMagicPacket.Purpose purpose) {
        return purpose == RescueMagicPacket.Purpose.RESCUE ? Math.max(durationTicks, 32) : 17;
    }

    private static ServerLevel effectLevel(ServerPlayer player, LivingEntity living) {
        if (living != null && living.level() instanceof ServerLevel level) {
            return level;
        }
        return player.serverLevel();
    }

    private static float yawToward(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        if (dx * dx + dz * dz < 1.0E-4) {
            return 0.0f;
        }
        return Mth.wrapDegrees((float)(Mth.atan2(dz, dx) * 57.2957763671875) - 90.0f);
    }
}
