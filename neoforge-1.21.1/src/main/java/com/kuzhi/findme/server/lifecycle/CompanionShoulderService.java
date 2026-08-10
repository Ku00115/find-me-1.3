package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class CompanionShoulderService {
    private static final String SHOULDER_COMPANION_UUID = "FindMeCompanionUUID";

    private CompanionShoulderService() {
    }

    public static Optional<CompoundTag> shoulderEntityTag(ServerPlayer player, UUID uuid) {
        CompoundTag left = player.getShoulderEntityLeft();
        if (matchesShoulderEntity(left, uuid)) {
            return Optional.of(left.copy());
        }
        CompoundTag right = player.getShoulderEntityRight();
        if (matchesShoulderEntity(right, uuid)) {
            return Optional.of(right.copy());
        }
        return Optional.empty();
    }

    public static boolean clearPlayerShoulderEntity(ServerPlayer player, UUID uuid) {
        if (matchesShoulderEntity(player.getShoulderEntityLeft(), uuid)) {
            return setPlayerShoulderEntity(player, true, new CompoundTag());
        }
        if (matchesShoulderEntity(player.getShoulderEntityRight(), uuid)) {
            return setPlayerShoulderEntity(player, false, new CompoundTag());
        }
        return false;
    }

    public static Optional<LivingEntity> releaseShoulderCompanion(ServerPlayer player, UUID uuid, CompoundTag source) {
        CompoundTag tag = CompanionEntitySnapshots.prepareForLoad(source,
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        if (!tag.contains("id")) {
            return Optional.empty();
        }
        tag.putUUID("UUID", uuid);
        Entity released = EntityType.loadEntityRecursive(tag, (Level)player.serverLevel(), entity -> {
            entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            return entity;
        });
        if (!(released instanceof LivingEntity living)) {
            return Optional.empty();
        }
        if (!clearPlayerShoulderEntity(player, uuid)) {
            return Optional.empty();
        }
        player.serverLevel().addFreshEntity(released);
        CompanionAnimationHelper.restoreAnimationControl(living);
        return Optional.of(living);
    }

    private static boolean matchesShoulderEntity(CompoundTag tag, UUID uuid) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }
        if (tag.hasUUID(SHOULDER_COMPANION_UUID) && uuid.equals(tag.getUUID(SHOULDER_COMPANION_UUID))) {
            return true;
        }
        return tag.hasUUID("UUID") && uuid.equals(tag.getUUID("UUID"));
    }

    private static boolean setPlayerShoulderEntity(ServerPlayer player, boolean left, CompoundTag tag) {
        String[] methodNames = left
                ? new String[]{"setShoulderEntityLeft", "m_36362_"}
                : new String[]{"setShoulderEntityRight", "m_36364_"};
        for (String name : methodNames) {
            try {
                Method method = Player.class.getDeclaredMethod(name, CompoundTag.class);
                method.setAccessible(true);
                method.invoke(player, tag);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return false;
    }
}
