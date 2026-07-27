package com.kuzhi.findme.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record SavedPosition(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot) {
    public static SavedPosition of(Level level, double x, double y, double z, float yRot, float xRot) {
        return new SavedPosition((ResourceKey<Level>)level.dimension(), x, y, z, yRot, xRot);
    }

    public BlockPos blockPos() {
        return BlockPos.containing((double)this.x, (double)this.y, (double)this.z);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", this.dimension.location().toString());
        tag.putDouble("x", this.x);
        tag.putDouble("y", this.y);
        tag.putDouble("z", this.z);
        tag.putFloat("yRot", this.yRot);
        tag.putFloat("xRot", this.xRot);
        return tag;
    }

    public static SavedPosition load(CompoundTag tag) {
        ResourceKey dimension = ResourceKey.create((ResourceKey)Registries.DIMENSION, (ResourceLocation)ResourceLocation.parse(tag.getString("dimension")));
        return new SavedPosition((ResourceKey<Level>)dimension, tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getFloat("yRot"), tag.getFloat("xRot"));
    }
}
