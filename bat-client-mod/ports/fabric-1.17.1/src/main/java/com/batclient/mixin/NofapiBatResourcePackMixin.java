package com.batclient.mixin;

import com.batclient.platform.fabric.BatClasspathResourcePack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.resource.ResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ReloadableResourceManagerImpl.class)
public abstract class NofapiBatResourcePackMixin {
    @ModifyVariable(method = "reload", at = @At("HEAD"), argsOnly = true)
    private List<ResourcePack> bat$addClasspathPack(List<ResourcePack> packs) {
        if (packs == null) {
            return Collections.singletonList(BatClasspathResourcePack.INSTANCE);
        }
        if (packs.contains(BatClasspathResourcePack.INSTANCE)) {
            return packs;
        }
        List<ResourcePack> updated = new ArrayList<>(packs.size() + 1);
        updated.addAll(packs);
        updated.add(BatClasspathResourcePack.INSTANCE);
        return updated;
    }
}
