package com.batclient;
import net.minecraft.client.MinecraftClient;
import com.google.gson.JsonObject;
/** Applies a local profile once per connection. Manual choices remain in force for that session. */
public final class ServerProfiles {
    private ServerProfiles(){}
    private static String last="",override="";
    private static boolean automatic;
    public static String key(MinecraftClient c){
        if(c==null||c.world==null)return "";
        if(c.isInSingleplayer()&&c.getServer()!=null)return "World: "+c.getServer().getSaveProperties().getLevelName();
        return c.getCurrentServerEntry()==null?"":normalize(c.getCurrentServerEntry().address);
    }
    public static String normalize(String value){String s=value.trim().toLowerCase(java.util.Locale.ROOT);if(s.endsWith(":25565"))s=s.substring(0,s.length()-6);return s;}
    public static void tick(MinecraftClient c){String key=key(c);if(key.equals(last))return;last=key;override="";if(!key.isEmpty())apply(c,key);}
    private static void apply(MinecraftClient c,String key){var cfg=HudConfig.snapshot();if(!cfg.has("serverProfiles")||!cfg.get("serverProfiles").isJsonObject())return;var map=cfg.getAsJsonObject("serverProfiles");if(!map.has(key)||!map.get(key).isJsonPrimitive())return;String name=map.get(key).getAsString();if(cfg.has("activeProfile")&&name.equals(cfg.get("activeProfile").getAsString()))return;automatic=true;try{String error=HudConfig.profileAction("load",name);if(error.isEmpty()){BatKeybinds.suppressHeld(c);BatClientMod.LOGGER.info("[profiles] Applied {} for {}",name,key);}}finally{automatic=false;}}
    public static void manual(){if(!automatic)override=key(MinecraftClient.getInstance());}
    public static String action(String action,String name){
        var c=MinecraftClient.getInstance();String key=key(c),error="";
        if(action.equals("resume")){override="";if(!key.isEmpty())apply(c,key);}
        else if(action.equals("override"))override=key;
        else if(!action.equals("list"))error=HudConfig.serverBinding(action,key,name);
        var result=new JsonObject();result.addProperty("key",key);result.addProperty("override",!key.isEmpty()&&key.equals(override));result.addProperty("error",error);result.addProperty("config",HudConfig.json());return result.toString();
    }
}
