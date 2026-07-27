package com.kuzhi.findme.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Keeps optional compatibility mixins out of the transform pipeline when their mod is absent. */
public final class FindMeMixinConfigPlugin implements IMixinConfigPlugin {
    private static final String SALVATION_MIXIN =
            "com.kuzhi.findme.mixin.SalvationRitualServiceMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals(SALVATION_MIXIN)) {
            if (net.minecraftforge.fml.loading.FMLLoader.getLoadingModList() == null
                    || net.minecraftforge.fml.loading.FMLLoader.getLoadingModList().getModFileById("findme_salvation") == null) {
                return false;
            }
            String resource = targetClassName.replace('.', '/') + ".class";
            return FindMeMixinConfigPlugin.class.getClassLoader().getResource(resource) != null;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {
    }
}
