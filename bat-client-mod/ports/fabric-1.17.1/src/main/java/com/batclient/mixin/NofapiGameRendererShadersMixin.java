package com.batclient.mixin;

import com.batclient.platform.fabric.FabricClientResources;
import com.mojang.datafixers.util.Pair;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Shader;
import net.minecraft.resource.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class NofapiGameRendererShadersMixin {
    @Shadow @Final private Map<String, Shader> shaders;

    @Inject(method = "loadShaders", at = @At("TAIL"))
    private void bat$nofapiShaders(ResourceManager manager, CallbackInfo ci) {
        try {
            List<Pair<Shader, Consumer<Shader>>> list = new ArrayList<>();
            FabricClientResources.onRegisterShaders(manager, list);
            for (Pair<Shader, Consumer<Shader>> pair : list) {
                Shader shader = pair.getFirst();
                this.shaders.put(shader.getName(), shader);
                pair.getSecond().accept(shader);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Bat Client shaders", e);
        }
    }
}
