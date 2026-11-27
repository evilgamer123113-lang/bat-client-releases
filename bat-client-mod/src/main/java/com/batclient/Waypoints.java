package com.batclient;

import com.google.gson.*;
import com.batclient.platform.ClientHookServices;
import com.batclient.platform.Platforms;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Client-thread controller. No marker data is sent to a server. */
public final class Waypoints {
    private static final WaypointStore STORE=new WaypointStore(Platforms.get().getConfigDir().resolve("batclient-waypoints.json"));
    private static String scope="",dimension="",label="",arrived="";
    private static Object world;
    private static List<WaypointStore.Entry> current=List.of();
    private static boolean dirty=true,pendingOpen,pendingNew;
    private static int age,openTicks;
    private static boolean wasDead;
    private static JsonObject draft;
    private static final ServerWaypointStore SERVER=new ServerWaypointStore();
    private Waypoints(){}
    public static void init(){
        BatKeybinds.register("waypoint_add",ClientHookServices.get().registerKeyBinding(new KeyBinding("key.batclient.waypoint_add",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_N,"category.batclient.title")));
        BatKeybinds.register("waypoint_manager",ClientHookServices.get().registerKeyBinding(new KeyBinding("key.batclient.waypoint_manager",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_M,"category.batclient.title")));
        WaypointRenderer.init();WaypointNetwork.init();
    }
    private static void refresh(MinecraftClient c){
        if(c.world!=world){world=c.world;dirty=true;arrived="";SERVER.clear();wasDead=false;
            if(c.world==null||c.player==null){scope=dimension=label="";current=List.of();world=null;return;}
            dimension=c.world.getRegistryKey().getValue().toString();
            if(c.getServer()!=null){scope="save:"+c.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize();label=c.getServer().getSaveProperties().getLevelName();}
            else if(c.getCurrentServerEntry()!=null){label=ServerProfiles.normalize(c.getCurrentServerEntry().address);scope="server:"+label;}
            else{var network=c.getNetworkHandler();label=network==null?"":String.valueOf(network.getConnection().getAddress());scope=label.isEmpty()?"":"server:"+label;}
        }
        if(dirty){current=STORE.list(scope);dirty=false;}
    }
    public static List<WaypointStore.Entry> current(){var list=new ArrayList<>(current.stream().filter(e->!WaypointStore.expired(e)).toList());if(HudConfig.bool(VisualModules.settings("waypoints"),"serverMarkers",true))list.addAll(SERVER.list());return list;}
    public static void receive(JsonObject message,MinecraftClient c){refresh(c);if(c.world==null||scope.isEmpty())return;SERVER.accept(message,scope,dimension);}
    public static void chatDraft(int x,int y,int z){var c=MinecraftClient.getInstance();if(!VisualModules.enabled("waypoints")||!HudConfig.bool(VisualModules.settings("waypoints"),"chat",true)||c.player==null)return;draft=new JsonObject();draft.addProperty("x",x);draft.addProperty("y",y);draft.addProperty("z",z);BatClientMod.openWaypoints(c);request(true);}
    private static void death(MinecraftClient c){boolean dead=c.player.isDead();if(dead&&!wasDead&&VisualModules.enabled("waypoints")&&HudConfig.bool(VisualModules.settings("waypoints"),"death",true)){var data=new JsonObject();data.addProperty("name","Death "+java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));data.addProperty("group","Deaths");data.addProperty("x",c.player.getBlockX());data.addProperty("y",c.player.getBlockY());data.addProperty("z",c.player.getBlockZ());data.addProperty("color","#FF6666");data.addProperty("expiresAt",System.currentTimeMillis()+86400000L);try{STORE.save(scope,data,dimension);dirty=true;}catch(Exception e){BatClientMod.LOGGER.warn("[waypoints] Could not save death location: {}",e.toString());}}wasDead=dead;}
    public static String dimension(){return dimension;}
    public static void request(boolean create){pendingOpen=true;pendingNew=create;openTicks=0;}
    public static void tick(MinecraftClient c){
        refresh(c);age++;
        boolean add=BatKeybinds.pressed(c,"waypoint_add"),manager=BatKeybinds.pressed(c,"waypoint_manager");
        if(c.player==null||c.world==null){pendingOpen=false;return;}
        death(c);
        if(c.currentScreen==null&&(add||manager)){BatClientMod.openWaypoints(c);request(add);}
        if(pendingOpen&&++openTicks<120&&c.currentScreen instanceof com.batclient.web.WebScreen screen&&screen.window()!=null){
            try{String result=screen.window().view().evaluateScript("typeof openWaypointManager==='function'?(openWaypointManager("+pendingNew+"),'ready'):'waiting'");if("ready".equals(result)){pendingOpen=false;if(draft!=null){screen.window().view().evaluateScript("wpOpenDraft("+draft+")");draft=null;}}}catch(RuntimeException ignored){}
        }else if(openTicks>=120)pendingOpen=false;
        if(age%5!=0||!VisualModules.enabled("waypoints"))return;
        var mod=VisualModules.settings("waypoints");
        for(var e:current())if(e.tracked()&&e.visible()&&e.dimension().equals(dimension)){
            double distance=c.player.getPos().distanceTo(new net.minecraft.util.math.Vec3d(e.x()+.5,e.y(),e.z()+.5));double radius=VisualModules.number(mod,"arrivalRadius",5,1,32);
            if(distance<=radius&&!arrived.equals(e.id())){arrived=e.id();if(HudConfig.bool(mod,"arrival",true))c.inGameHud.setOverlayMessage(Text.literal("Arrived at "+e.name()),false);}
            else if(distance>radius*2&&arrived.equals(e.id()))arrived="";
        }
    }
    public static String action(String action,String raw){
        var c=MinecraftClient.getInstance();refresh(c);String error="",created="";
        try{
            if(!action.equals("list")){
                if(scope.isEmpty())throw new IllegalArgumentException("Join a world or server first.");
                if(raw!=null&&raw.length()>8192)throw new IllegalArgumentException("Waypoint request is too large.");
                JsonObject data=raw==null||raw.isBlank()?new JsonObject():JsonParser.parseString(raw).getAsJsonObject();
                String id=data.has("id")?data.get("id").getAsString():"";
                boolean remote=SERVER.contains(id);
                if(remote&&!Set.of("visible","tracked","copy").contains(action))throw new IllegalArgumentException("Server waypoints are managed by the server. You can hide or track them.");
                switch(action){
                    case "save" -> created=STORE.save(scope,data,dimension).id();
                    case "delete" -> STORE.remove(scope,id);
                    case "copy" -> {var point=current().stream().filter(e->e.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Waypoint not found."));c.keyboard.setClipboard(point.name()+": "+point.x()+", "+point.y()+", "+point.z()+" ("+point.dimension()+")");}
                    case "visible","favorite","tracked" -> {boolean value=data.has("value")&&data.get("value").getAsBoolean();
                        if(action.equals("tracked")&&value){SERVER.stopTracking();for(var e:STORE.list(scope))if(e.tracked())STORE.flag(scope,e.id(),"tracked",false);}
                        if(remote){if(action.equals("visible"))SERVER.visible(id,value);else SERVER.track(id,value);}else STORE.flag(scope,id,action,value);
                    }
                    case "group" -> {String group=WaypointStore.label(data.get("group").getAsString(),24,"Group");boolean value=data.get("value").getAsBoolean();STORE.group(scope,group,value);for(var e:SERVER.list())if(e.group().equals(group))SERVER.visible(e.id(),value);}
                    default -> throw new IllegalArgumentException("Unknown waypoint action.");
                }
                dirty=true;refresh(c);
            }
        }catch(Exception failure){error=failure instanceof java.io.IOException?"Could not save waypoints. Your previous locations are intact.":failure.getMessage()==null?"Waypoint request failed.":failure.getMessage();}
        var result=new JsonObject();result.addProperty("error",error);result.addProperty("notice",STORE.notice());result.addProperty("readOnly",STORE.readOnly());result.addProperty("world",label);result.addProperty("dimension",dimension);result.addProperty("createdId",created);result.addProperty("moduleEnabled",VisualModules.enabled("waypoints"));
        var list=new JsonArray();for(var e:current()){var item=e.json();item.addProperty("server",SERVER.contains(e.id()));item.remove("scope");if(c.player!=null&&e.dimension().equals(dimension))item.addProperty("distance",c.player.getPos().distanceTo(new net.minecraft.util.math.Vec3d(e.x()+.5,e.y(),e.z()+.5)));list.add(item);}result.add("waypoints",list);
        if(c.player!=null){var position=new JsonObject();position.addProperty("x",c.player.getBlockX());position.addProperty("y",c.player.getBlockY());position.addProperty("z",c.player.getBlockZ());result.add("position",position);}return result.toString();
    }
}
