package com.batclient.platform.mc1204;

import com.batclient.platform.ClientHookServices;
import com.batclient.platform.ClientResourceServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderEffect;
import net.minecraft.util.Identifier;

/** Own the vanilla blur chain without replacing a player's active postprocessor. */
public final class ScreenBlur {
    private static ShaderEffect effect;
    private static boolean registered,failed;
    private static int width,height;
    private ScreenBlur() {}
    private static void close(){if(effect!=null)effect.close();effect=null;failed=false;width=height=0;}
    public static void apply(MinecraftClient client,float delta){
        if(!registered){
            registered=true;
            ClientResourceServices.get().reload(new Identifier("batclient","screen_blur"),ScreenBlur::close);
            ClientHookServices.get().stopping(c->close());
        }
        if(failed)return;
        try{
            if(effect==null)effect=new ShaderEffect(client.getTextureManager(),client.getResourceManager(),client.getFramebuffer(),new Identifier("minecraft","shaders/post/blur.json"));
            int w=client.getWindow().getFramebufferWidth(),h=client.getWindow().getFramebufferHeight();
            if(w!=width||h!=height){width=w;height=h;effect.setupDimensions(w,h);}
            effect.render(delta);
        }catch(Exception error){
            close();failed=true;
            com.batclient.BatClientMod.LOGGER.warn("[1.19.2] Screen blur unavailable",error);
        }finally{client.getFramebuffer().beginWrite(true);}
    }
}
