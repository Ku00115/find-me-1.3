package com.kuzhi.findme;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.ModBlocks;
import com.kuzhi.findme.common.ModBlockEntities;
import com.kuzhi.findme.common.ModItems;
import com.kuzhi.findme.common.ModParticles;
import com.kuzhi.findme.client.ClientEvents;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.core.CompanionEvents;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(value="find_me")
public class FindMeMod {
    public static final String MODID = "find_me";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FindMeMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModItems::addCreative);
        modEventBus.addListener(this::registerPayloads);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModParticles.register(modEventBus);
        Config.register(modEventBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEvents.register(modEventBus);
        }
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);
        NeoForge.EVENT_BUS.register((Object)new CompanionEvents());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        VehicleManager.bootstrap();
        LOGGER.info("Find me core loaded");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        ModNetwork.register(event);
    }
}
