package com.kuzhi.findme.compat.waystones;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record WaystoneDestination(UUID uuid, String name, ResourceKey<Level> dimension, BlockPos position) {
}
