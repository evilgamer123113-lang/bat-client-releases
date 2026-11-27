package com.batclient.performance;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Renderer owners keep their own rebuild/upload schedulers. No optional-class mixin targets. */
public final class PerformanceMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName){
        if(mixinClassName.endsWith(".LoadingBrandMixin")){
            FabricLoader loader=FabricLoader.getInstance();
            return !loader.isModLoaded("fastclient-hud")
                &&!loader.isModLoaded("dark-loading-screen")
                &&!loader.isModLoaded("drippyloadingscreen")
                &&!loader.isModLoaded("fancymenu");
        }
        if(mixinClassName.endsWith(".ChunkMeshExecutorMixin")){
            FabricLoader loader=FabricLoader.getInstance();
            return RendererCompatibility.useVanillaChunkHooks(loader::isModLoaded)&&!loader.isModLoaded("c2me")&&!loader.isModLoaded("vmp");
        }
        if(mixinClassName.endsWith(".LocalChunkBatchMixin")){
            FabricLoader loader=FabricLoader.getInstance();
            return !loader.isModLoaded("c2me")&&!loader.isModLoaded("servercore")&&!loader.isModLoaded("vmp");
        }
        if(mixinClassName.endsWith(".CubicColorAllocationMixin")){
            FabricLoader loader=FabricLoader.getInstance();
            return !loader.isModLoaded("modernfix")&&RendererCompatibility.useVanillaChunkHooks(loader::isModLoaded);
        }
        if(!mixinClassName.endsWith(".SmoothChunkRebuildMixin")&&!mixinClassName.endsWith(".SmoothChunkUploadMixin")&&!mixinClassName.endsWith(".GameLoopYieldMixin"))return true;
        FabricLoader loader=FabricLoader.getInstance();
        return RendererCompatibility.useVanillaChunkHooks(loader::isModLoaded);
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others){ }
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){ }
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){ }
}
