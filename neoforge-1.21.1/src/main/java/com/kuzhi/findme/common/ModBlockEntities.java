package com.kuzhi.findme.common;

import com.kuzhi.findme.FindMeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FindMeMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmallHouseBlockEntity>> SMALL_HOUSE =
            BLOCK_ENTITIES.register("small_house", () -> BlockEntityType.Builder.of(
                    SmallHouseBlockEntity::new, ModBlocks.SMALL_HOUSE.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
