package com.kuzhi.findme;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.ModBlocks;
import com.kuzhi.findme.common.ModBlockEntities;
import com.kuzhi.findme.common.ModItems;
import com.kuzhi.findme.common.ModParticles;
import com.kuzhi.findme.common.ModCreativeTabs;
import com.kuzhi.findme.client.ClientEvents;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.core.CompanionEvents;
import com.kuzhi.findme.server.integration.SalvationCompatibilityService;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(value="find_me")
public class FindMeMod {
    public static final String MODID = "find_me";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FindMeMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModItems::addCreative);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModParticles.register(modEventBus);
        Config.register(modEventBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEvents.register(modEventBus);
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);
        MinecraftForge.EVENT_BUS.register(new CompanionEvents());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetwork.register();
            SalvationCompatibilityService.bootstrap();
        });
        LOGGER.info("Find me core loaded");
    }
}
