package com.batclient.platform.forge;

import com.batclient.platform.ClientResources;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Install on the mod bus in the mod constructor, before Minecraft's initial reload. */
public final class ForgeClientResources implements ClientResources {
    private static IEventBus modBus;
    public static void bootstrap(IEventBus bus){
        if(modBus!=null&&modBus!=bus)throw new IllegalStateException("Bat resource bus changed");
        modBus=bus;
    }
    public ForgeClientResources(){if(modBus==null)throw new IllegalStateException("Bootstrap Bat resources during mod construction");}
    @Override public void playerLayers(PlayerLayers callback){
        modBus.addListener((net.minecraftforge.client.event.EntityRenderersEvent.AddLayers event)->{
            for(var skin:event.getSkins()){
                var renderer=event.getPlayerSkin(skin);
                if(renderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer player)callback.register(player,player::addFeature);
            }
        });
    }
    @Override public void shaders(ShaderRegistration callback){
        modBus.addListener((RegisterShadersEvent event)->{
            try{callback.register((id,format,loaded)->event.registerShader(new ShaderProgram(event.getResourceProvider(),id,format),loaded));}
            catch(java.io.IOException error){throw new IllegalStateException("Bat shader registration failed",error);}
        });
    }
    @Override public void reload(Identifier id,Runnable callback){
        modBus.addListener((RegisterClientReloadListenersEvent event)->
            event.registerReloadListener((SynchronousResourceReloader)manager->callback.run()));
    }
}
