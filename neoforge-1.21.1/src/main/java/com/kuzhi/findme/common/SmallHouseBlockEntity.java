package com.kuzhi.findme.common;

import com.kuzhi.findme.server.data.FindMeWorldSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class SmallHouseBlockEntity extends BlockEntity {
    private UUID houseId = UUID.randomUUID();
    private UUID owner;
    private String displayName = "";

    public SmallHouseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMALL_HOUSE.get(), pos, state);
    }

    public UUID houseId() {
        return houseId;
    }

    public UUID owner() {
        return owner;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isOwner(ServerPlayer player) {
        return player != null && owner != null && owner.equals(player.getUUID());
    }

    public void initializeOwner(ServerPlayer player) {
        if (owner != null || player == null) {
            return;
        }
        owner = player.getUUID();
        setChanged();
        registerWorldRecord();
    }

    public void setDisplayName(String name) {
        displayName = name == null ? "" : name.trim();
        if (displayName.length() > 64) {
            displayName = displayName.substring(0, 64);
        }
        setChanged();
        registerWorldRecord();
    }

    public void refreshWorldRecord() {
        registerWorldRecord();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            registerWorldRecord();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("houseId", houseId);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        if (!displayName.isBlank()) {
            tag.putString("displayName", displayName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("houseId")) {
            houseId = tag.getUUID("houseId");
        }
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        displayName = tag.getString("displayName");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    private void registerWorldRecord() {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getServer() == null) {
            return;
        }
        FindMeWorldSavedData world = FindMeWorldSavedData.get(serverLevel.getServer());
        SavedPosition position = SavedPosition.of(serverLevel, worldPosition.getX(), worldPosition.getY(),
                worldPosition.getZ(), 0.0f, 0.0f);
        if (!world.registerHouse(houseId, owner, position, displayName)) {
            houseId = UUID.randomUUID();
            setChanged();
            world.registerHouse(houseId, owner, position, displayName);
        }
    }
}
