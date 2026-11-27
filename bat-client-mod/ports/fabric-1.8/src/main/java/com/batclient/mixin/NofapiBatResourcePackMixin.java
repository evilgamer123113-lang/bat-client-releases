package com.batclient.mixin;

import com.batclient.platform.fabric.BatClasspathResourcePack;
import java.util.List;
import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.resource.ResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReloadableResourceManagerImpl.class)
public abstract class NofapiBatResourcePackMixin {
    @Shadow
    public abstract void add(ResourcePack pack);

    @Inject(method = "reload", at = @At("HEAD"))
    private void bat$addClasspathPack(List<ResourcePack> packs, CallbackInfo ci) {
        if (packs != null && !packs.contains(BatClasspathResourcePack.INSTANCE)) {
            try {
                packs.add(BatClasspathResourcePack.INSTANCE);
            } catch (UnsupportedOperationException ignored) {
                this.add(BatClasspathResourcePack.INSTANCE);
            }
        }
    }
}
