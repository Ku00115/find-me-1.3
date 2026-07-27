package com.kuzhi.findme.common;

import com.kuzhi.findme.FindMeMod;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, FindMeMod.MODID);

    public static final RegistryObject<BlockEntityType<SmallHouseBlockEntity>> SMALL_HOUSE =
            BLOCK_ENTITIES.register("small_house", () -> BlockEntityType.Builder.of(
                    SmallHouseBlockEntity::new, ModBlocks.SMALL_HOUSE.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
