package com.kuzhi.findme.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

/** Stable creative-tab key shared by FindMe addons. */
public final class FindMeCreativeTabs {
    public static final ResourceKey<CreativeModeTab> MAIN = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath("find_me", "main"));

    private FindMeCreativeTabs() {
    }
}
