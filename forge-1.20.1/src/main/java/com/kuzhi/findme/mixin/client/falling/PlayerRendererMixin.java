package com.kuzhi.findme.mixin.client.falling;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.kuzhi.findme.client.ClientWheelPresentationState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    private static final double FALLING$MIN_DROP_HEIGHT = 10.0D;
    private static final float FALLING$FLIP_DURATION_TICKS = 14.0F;
    private static final double FALLING$MIN_FALL_SPEED = 0.01D;
    private static final Map<UUID, Float> FALLING$FLIP_START_TICKS = new HashMap<>();

    @Inject(
            method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At("TAIL")
    )
    private void falling$tiltPlayerDuringHighFall(AbstractClientPlayer player, PoseStack poseStack, float bob,
                                                   float bodyYaw, float partialTick, CallbackInfo callback) {
        if (!ClientWheelPresentationState.fallingAnimation() || !falling$shouldUseDivePose(player)) {
            FALLING$FLIP_START_TICKS.remove(player.getUUID());
            return;
        }

        UUID playerId = player.getUUID();
        float currentTicks = player.tickCount + partialTick;
        float startTicks = FALLING$FLIP_START_TICKS.computeIfAbsent(playerId, ignored -> currentTicks);
        float progress = Mth.clamp((currentTicks - startTicks) / FALLING$FLIP_DURATION_TICKS,
                0.0F, 1.0F);
        progress = progress * progress * (3.0F - 2.0F * progress);

        poseStack.translate(0.0F, player.getBbHeight() * progress, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(progress, 0.0F, 180.0F)));
    }

    private static boolean falling$shouldUseDivePose(AbstractClientPlayer player) {
        return falling$hasLongDropBelow(player)
                && player.getDeltaMovement().y < -FALLING$MIN_FALL_SPEED
                && !player.onGround()
                && !player.isFallFlying()
                && !player.isAutoSpinAttack()
                && !player.isPassenger()
                && !player.isInWater()
                && !player.isInLava()
                && !player.isSleeping()
                && !player.isSpectator();
    }

    private static boolean falling$hasLongDropBelow(AbstractClientPlayer player) {
        double minDropHeight = FALLING$MIN_DROP_HEIGHT;
        if (minDropHeight <= 0.0D) {
            return true;
        }

        Level level = player.level();
        AABB box = player.getBoundingBox();
        int minX = Mth.floor(box.minX + 0.001D);
        int maxX = Mth.floor(box.maxX - 0.001D);
        int minZ = Mth.floor(box.minZ + 0.001D);
        int maxZ = Mth.floor(box.maxZ - 0.001D);

        if (minX > maxX) {
            minX = maxX = Mth.floor(player.getX());
        }
        if (minZ > maxZ) {
            minZ = maxZ = Mth.floor(player.getZ());
        }

        int startY = Mth.floor(box.minY) - 1;
        int endY = Math.max(level.getMinBuildHeight(), Mth.floor(box.minY - minDropHeight));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        CollisionContext collisionContext = CollisionContext.of(player);

        for (int y = startY; y >= endY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.getCollisionShape(level, pos, collisionContext).isEmpty()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}
