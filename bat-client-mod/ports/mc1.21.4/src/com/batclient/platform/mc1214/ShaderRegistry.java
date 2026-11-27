package com.batclient.platform.mc1214;
import com.batclient.platform.ClientResources;
import net.minecraft.client.gl.*;
import java.util.*;
/** Rebind owned shader programs after the vanilla 1.21.4 loader applies its new definitions. */
public final class ShaderRegistry {
 private static final List<ClientResources.ShaderRegistration> CALLBACKS=new ArrayList<>();
 private ShaderRegistry(){}
 public static void register(ClientResources.ShaderRegistration callback){CALLBACKS.add(callback);}
 public static void loaded(ShaderLoader loader){
  for(var callback:List.copyOf(CALLBACKS))try{
   callback.register((id,format,consumer)->{
    var key=new ShaderProgramKey(id.withPrefixedPath("core/"),format,Defines.EMPTY);
    consumer.accept(Objects.requireNonNull(loader.getOrCreateProgram(key),"Bat shader missing: "+id));
   });
  }catch(java.io.IOException error){throw new IllegalStateException("Could not load Bat shaders",error);}
 }
}
