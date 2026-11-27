package com.batclient.platform.fabric;

import com.batclient.platform.ClientResources;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public final class FabricClientResources implements ClientResources {
    @Override public void playerLayers(PlayerLayers callback){
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type,renderer,helper,context)->{
            if(renderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer player)callback.register(player,helper::register);
        });
    }
    @Override public void shaders(ShaderRegistration callback){
        CoreShaderRegistrationCallback.EVENT.register(context->callback.register(context::register));
    }
    @Override public void reload(Identifier id,Runnable callback){
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){
            @Override public Identifier getFabricId(){return id;}
            @Override public void reload(ResourceManager manager){callback.run();}
        });
    }
}
