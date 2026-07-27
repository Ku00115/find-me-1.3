package com.kuzhi.findme.compat.waystones;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/** Class-loading barrier around the optional Waystones API. */
public final class WaystonesIntegration {
    private WaystonesIntegration() {
    }

    public static boolean available() {
        return ModList.get().isLoaded("waystones");
    }

    public static List<WaystoneDestination> destinations(ServerPlayer player) {
        return available() ? WaystonesApiBridge.destinations(player) : List.of();
    }

    public static boolean isActivated(ServerPlayer player, UUID waystoneUuid) {
        return available() && WaystonesApiBridge.isActivated(player, waystoneUuid);
    }

    public static void teleport(ServerPlayer player, LivingEntity mount, UUID waystoneUuid,
                                Consumer<List<net.minecraft.world.entity.Entity>> success,
                                Consumer<Component> failure) {
        if (!available()) {
            failure.accept(Component.translatable("message.find_me.waystones_unavailable"));
            return;
        }
        WaystonesApiBridge.teleport(player, mount, waystoneUuid, success, failure);
    }
}
