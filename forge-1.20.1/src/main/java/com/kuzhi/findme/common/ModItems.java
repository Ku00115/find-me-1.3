package com.kuzhi.findme.common;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.FindMeMod;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FindMeMod.MODID);

    public static final RegistryObject<Item> VEHICLE_BINDER = ITEMS.register("vehicle_binder", () -> new VehicleBinderItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> NAME_PAPER = ITEMS.register("name_paper", () -> new NamePaperItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<SmallHouseItem> SMALL_HOUSE = ITEMS.register("small_house",
            () -> new SmallHouseItem(ModBlocks.SMALL_HOUSE.get(), new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            if (Config.moduleConfigured(FindMeModule.RIDING)) {
                event.accept(VEHICLE_BINDER.get());
            }
            if (Config.moduleConfigured(FindMeModule.RIDING)
                    || Config.moduleConfigured(FindMeModule.COMPANIONS)) {
                event.accept(NAME_PAPER.get());
            }
            if (Config.moduleConfigured(FindMeModule.HOUSES)
                    && Config.moduleConfigured(FindMeModule.COMPANIONS)) {
                event.accept(SMALL_HOUSE.get());
            }
        }
    }
}
