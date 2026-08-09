package com.kuzhi.findme.common;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.api.FindMeCreativeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FindMeMod.MODID);

    public static final DeferredItem<Item> VEHICLE_BINDER = ITEMS.register("vehicle_binder", () -> new VehicleBinderItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> VEHICLE_SEAT_CUSHION = ITEMS.register("vehicle_seat_cushion", () -> new VehicleSeatCushionItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> NAME_PAPER = ITEMS.register("name_paper", () -> new NamePaperItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<SmallHouseItem> SMALL_HOUSE = ITEMS.register("small_house",
            () -> new SmallHouseItem(ModBlocks.SMALL_HOUSE.get(), new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(FindMeCreativeTabs.MAIN)) {
            if (Config.moduleConfigured(FindMeModule.RIDING)) {
                event.accept(VEHICLE_BINDER.get());
            }
            if (Config.moduleConfigured(FindMeModule.RIDING)
                    && Config.moduleConfigured(FindMeModule.SABLE_INTEGRATION)) {
                event.accept(VEHICLE_SEAT_CUSHION.get());
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
