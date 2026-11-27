package com.batclient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.batclient.platform.Platforms;
import net.minecraft.client.MinecraftClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Last death per save/server. Writes only on a new death, never in a render call. */
public final class DeathCoordinates {
    public record Death(int x,int y,int z,String dimension) { }
    private static final Gson GSON=new Gson();
    private static final Map<String,Death> DEATHS=new LinkedHashMap<>();
    private static final java.nio.file.Path FILE=Platforms.get().getConfigDir().resolve("batclient-deaths.json");
    private static Object world;private static boolean dead,loaded;private static String active="";
    private DeathCoordinates() { }
    private static void load(){if(loaded)return;loaded=true;try{if(Files.isRegularFile(FILE)){Map<String,Death> data=GSON.fromJson(Files.readString(FILE,StandardCharsets.UTF_8),new TypeToken<Map<String,Death>>(){}.getType());if(data!=null)data.forEach((key,value)->{if(value!=null&&value.dimension()!=null&&DEATHS.size()<128)DEATHS.put(key,value);});}}catch(Exception e){BatClientMod.LOGGER.warn("[death] Could not read saved death locations: {}",e.toString());}}
    private static String key(MinecraftClient c){if(c.getServer()!=null)return "save:"+c.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize();var server=c.getCurrentServerEntry();return server==null?"":"server:"+server.address.toLowerCase(java.util.Locale.ROOT);}
    public static void tick(MinecraftClient c){
        load();if(c.world==null||c.player==null){world=null;active="";dead=false;return;}
        if(world!=c.world){world=c.world;active=key(c);dead=false;}
        boolean now=c.player.isDead();
        if(VisualModules.enabled("deathcoords")&&now&&!dead&&!active.isEmpty()){
            var pos=c.player.getBlockPos();DEATHS.remove(active);DEATHS.put(active,new Death(pos.getX(),pos.getY(),pos.getZ(),c.world.getRegistryKey().getValue().toString()));
            while(DEATHS.size()>128)DEATHS.remove(DEATHS.keySet().iterator().next());
            try{Files.createDirectories(FILE.getParent());var temp=FILE.resolveSibling(FILE.getFileName()+".tmp");Files.writeString(temp,GSON.toJson(DEATHS),StandardCharsets.UTF_8);Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){BatClientMod.LOGGER.warn("[death] Could not save death location: {}",e.toString());}
        }dead=now;
    }
    public static Death last(MinecraftClient c,boolean preview){return preview?new Death(120,64,-240,"minecraft:overworld"):DEATHS.get(active);}
}
