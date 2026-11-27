package com.batclient.platform.forge;

import com.batclient.performance.RendererCompatibility;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import net.minecraftforge.fml.loading.FMLLoader;

/** Uses loading metadata: ModList and client services are not initialized during Mixin selection. */
public final class ForgePerformanceMixinPlugin implements IMixinConfigPlugin {
    private boolean loaded(String id){return FMLLoader.getLoadingModList().getModFileById(id)!=null;}
    public void onLoad(String mixinPackage){}
    public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){
        if(mixin.endsWith(".CubicColorAllocationMixin"))return !loaded("modernfix")&&RendererCompatibility.useVanillaChunkHooks(this::loaded);
        if(mixin.endsWith(".SmoothChunkRebuildMixin")||mixin.endsWith(".SmoothChunkUploadMixin"))return RendererCompatibility.useVanillaChunkHooks(this::loaded);
        return true;
    }
    public void acceptTargets(Set<String> mine,Set<String> others){}
    public List<String> getMixins(){return null;}
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
