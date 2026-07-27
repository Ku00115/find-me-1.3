package com.kuzhi.findme.server.lifecycle;

import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

/** Transfers an entity between dimensions without invoking portal placement. */
public final class ExactEntityTeleporter {
    private ExactEntityTeleporter() {
    }

    public static Entity transfer(Entity entity, ServerLevel destination, double x, double y, double z,
                                  float yRot, float xRot) {
        if (entity == null || destination == null) return null;
        if (entity.level() == destination) return entity;
        Vec3 target = new Vec3(x, y, z);
        Vec3 velocity = entity.getDeltaMovement();
        return entity.changeDimension(destination, new ITeleporter() {
            @Override
            public PortalInfo getPortalInfo(Entity source, ServerLevel targetLevel,
                                            Function<ServerLevel, PortalInfo> defaultPortalInfo) {
                return new PortalInfo(target, velocity, yRot, xRot);
            }

            @Override
            public Entity placeEntity(Entity source, ServerLevel currentLevel, ServerLevel targetLevel,
                                      float ignoredYaw, Function<Boolean, Entity> repositionEntity) {
                Entity moved = repositionEntity.apply(false);
                if (moved != null) {
                    moved.moveTo(x, y, z, yRot, xRot);
                    moved.setDeltaMovement(velocity);
                }
                return moved;
            }

            @Override
            public boolean playTeleportSound(net.minecraft.server.level.ServerPlayer player,
                                             ServerLevel sourceLevel, ServerLevel targetLevel) {
                return false;
            }
        });
    }
}
