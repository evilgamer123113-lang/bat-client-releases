import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import com.google.gson.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Protected-jar integration test in a disposable save, not any user save. */
public final class WaypointRuntimeSmoke {
    private boolean started,done,managerChecked;
    private int wait,ticks,checks;
    private long start;
    private Method action,update;
    public void start(){ClientTickEvents.END_CLIENT_TICK.register(c->{
        if(done)return;
        try{
            if(!started){if(c.currentScreen==null||c.getOverlay()!=null||++wait<40)return;started=true;start=System.nanoTime();
                c.options.pauseOnLostFocus=false;c.options.getViewDistance().setValue(5);
                var info=new net.minecraft.world.level.LevelInfo("Waypoint Rendering Test",net.minecraft.world.GameMode.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.GameRules(),net.minecraft.resource.DataConfiguration.SAFE_MODE);
                c.createIntegratedServerLoader().createAndStart("WaypointTest-"+System.currentTimeMillis(),info,new net.minecraft.world.gen.GeneratorOptions(12345L,false,false),r->r.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).get(net.minecraft.world.gen.WorldPresets.FLAT).createDimensionsRegistryHolder(),c.currentScreen);return;
            }
            if(c.world==null||c.player==null){if(System.nanoTime()-start>180_000_000_000L)finish(c,"FAILED: waypoint world timed out");return;}
            ticks++;if(ticks<115&&c.currentScreen!=null)c.setScreen(null);
            if(ticks==5){
                Class<?> config=ChatRuntimeSmoke.protectedClass("com.batclient.HudConfig");update=Arrays.stream(config.getDeclaredMethods()).filter(m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{String.class,String.class,boolean.class})).findFirst().orElseThrow();update.setAccessible(true);
                update.invoke(null,"waypoints","{\"en\":true,\"set\":{\"navigation\":false,\"throughWalls\":true,\"markers\":12}}",false);
                Class<?> waypoints=ChatRuntimeSmoke.protectedClass("com.batclient.Waypoints");action=Arrays.stream(waypoints.getDeclaredMethods()).filter(m->m.getReturnType()==String.class&&Arrays.equals(m.getParameterTypes(),new Class<?>[]{String.class,String.class})).findFirst().orElseThrow();action.setAccessible(true);
                c.player.setYaw(0);c.player.setPitch(0);int x=c.player.getBlockX(),y=c.player.getBlockY(),z=c.player.getBlockZ();
                boolean demo=Boolean.getBoolean("bat.waypoint.visualDemo");save(demo?"Spawn":"Home",x,y,z+(demo?7:30),demo?"#35FF35":"#FFCC55","minecraft:overworld",true);save("Far base",x,y,z+90,"#FFCC55","minecraft:overworld",false);save("Camp",x+12,y,z+40,"#85E6A3","minecraft:overworld",false);save("Hidden dimension",x,y,z+20,"#FF00FF","minecraft:the_nether",false);
                var list=request("list",new JsonObject());check(list.getAsJsonArray("waypoints").size()==4,"live bridge stores current-world waypoints");check(list.get("dimension").getAsString().equals("minecraft:overworld"),"live dimension identity");
                check(!request("save",new JsonObject()).get("error").getAsString().isEmpty(),"invalid request cannot overwrite a waypoint");
            }
            if(ticks==70){update.invoke(null,"waypoints","{\"en\":true,\"set\":{\"beam\":true,\"ring\":false}}",false);c.player.setPitch(-50);}
            if(ticks==90){
                try(var image=net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
                    image.writeTo(Path.of(System.getProperty("bat.chat.smoke.out")).resolveSibling("waypoints-beam-offscreen-base.png"));int beamPixels=0;
                    for(int y=image.getHeight()/5;y<image.getHeight()*4/5;y++)for(int x=image.getWidth()/4;x<image.getWidth()*3/4;x++){int abgr=image.getColor(x,y),r=abgr&255,g=abgr>>8&255,b=abgr>>16&255;if(r>150&&r>b+25&&g>b+15)beamPixels++;}
                    check(beamPixels>30,"beam remains rendered while pitched up with base label below viewport");
                }
                c.player.setPitch(0);update.invoke(null,"waypoints","{\"en\":true,\"set\":{\"beam\":false,\"ring\":true}}",false);
            }
            if(ticks==100){
                try(var image=net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
                    Path out=Path.of(System.getProperty("bat.chat.smoke.out")).resolveSibling("waypoints-world.png");image.writeTo(out);int golden=0,white=0,magenta=0;
                    for(int y=image.getHeight()/4;y<image.getHeight()*3/4;y++)for(int x=image.getWidth()/5;x<image.getWidth()*4/5;x++){int abgr=image.getColor(x,y),r=abgr&255,g=abgr>>8&255,b=abgr>>16&255;if((r>225&&g>170&&g<225&&b>40&&b<115)||(g>200&&r<130&&b<130))golden++;if(r>225&&g>225&&b>225)white++;if(r>230&&b>230&&g<20)magenta++;}
                    check(golden>20&&white>20,"rounded colored waypoint outlines and white text visible in framebuffer");check(magenta<5,"other-dimension waypoint does not render");
                }
                if(Boolean.getBoolean("bat.waypoint.visualDemo")){done=true;Files.writeString(Path.of(System.getProperty("bat.chat.smoke.out")).resolveSibling("waypoint-result.txt"),"WAYPOINT_VISUAL_DEMO_OK: "+checks+" checks; gameplay left open");System.out.println("WAYPOINT_VISUAL_DEMO_OK: gameplay left open");return;}
                update.invoke(null,"waypoints","{\"en\":true,\"set\":{\"navigation\":true}}",false);
            }
            if(ticks==105){
                Class<?> payload=ChatRuntimeSmoke.protectedClass("com.batclient.WaypointNetwork$Payload");var constructor=payload.getDeclaredConstructor(String.class);constructor.setAccessible(true);String world=UUID.randomUUID().toString(),id=UUID.randomUUID().toString();
                var select=new JsonObject();select.addProperty("version",1);select.addProperty("op","world");select.addProperty("worldId",world);
                var marker=select.deepCopy();marker.addProperty("op","upsert");marker.addProperty("id",id);marker.addProperty("durationSeconds",60);var data=new JsonObject();data.addProperty("name","Server spawn");data.addProperty("x",c.player.getBlockX()+20);data.addProperty("y",c.player.getBlockY());data.addProperty("z",c.player.getBlockZ()+20);data.addProperty("dimension","minecraft:overworld");data.addProperty("color","#75CFFF");data.addProperty("chroma",true);data.addProperty("opacity",80);marker.add("waypoint",data);
                var first=(net.minecraft.network.packet.CustomPayload)constructor.newInstance(select.toString());var second=(net.minecraft.network.packet.CustomPayload)constructor.newInstance(marker.toString());UUID playerId=c.player.getUuid();c.getServer().execute(()->{var player=c.getServer().getPlayerManager().getPlayer(playerId);net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,first);net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,second);});
            }
            if(ticks==115){
                var list=request("list",new JsonObject()).getAsJsonArray("waypoints");check(list.size()==5,"actual server payload reaches protected receiver");var remote=list.asList().stream().map(JsonElement::getAsJsonObject).filter(e->e.get("server").getAsBoolean()).findFirst().orElseThrow();check(remote.get("chroma").getAsBoolean()&&remote.get("expiresAt").getAsLong()>System.currentTimeMillis(),"server chroma and TTL preserved");var id=new JsonObject();id.addProperty("id",remote.get("id").getAsString());check(!request("delete",id).get("error").getAsString().isEmpty(),"UI cannot delete a server marker");check(request("list",new JsonObject()).getAsJsonArray("waypoints").size()==5,"rejected server deletion preserves personal markers");
                Class<?> parser=ChatRuntimeSmoke.protectedClass("com.batclient.WaypointChat");var parse=Arrays.stream(parser.getDeclaredMethods()).filter(m->m.getReturnType()==int[].class).findFirst().orElseThrow();parse.setAccessible(true);check(Arrays.equals((int[])parse.invoke(null,"Meet at X: -120 Y: 64 Z: 80"),new int[]{-120,64,80}),"chat coordinates parsed");check(parse.invoke(null,"X: 30000000 Y: 64 Z: 0")==null,"invalid chat coordinates rejected");
                Class<?> mod=Class.forName("com.batclient.BatClientMod");mod.getMethod("openWaypoints",MinecraftClient.class).invoke(null,c);
            }
            if(!managerChecked&&ticks>=140&&c.currentScreen instanceof net.minecraft.client.gui.screen.Screen){
                // Avoid compile-time references to obfuscated screen/window classes.
                Class<?> nativeWindowClass=ChatRuntimeSmoke.protectedClass("com.batclient.web.WebWindow");Object screen=c.currentScreen;Method window=Arrays.stream(screen.getClass().getDeclaredMethods()).filter(m->m.getParameterCount()==0&&m.getReturnType()==nativeWindowClass).findFirst().orElseThrow();window.setAccessible(true);Object nativeWindow=window.invoke(screen);
                Method view=Arrays.stream(nativeWindow.getClass().getDeclaredMethods()).filter(m->m.getParameterCount()==0&&m.getReturnType()==io.github.nilsen84.ultralight.UltralightView.class).findFirst().orElseThrow();view.setAccessible(true);var web=(io.github.nilsen84.ultralight.UltralightView)view.invoke(nativeWindow);
                if(ticks==140)web.evaluateScript("openWaypointManager(false)");
                if(ticks>=160){String cards=web.evaluateScript("String(document.querySelectorAll('.wp-card').length)");check("4".equals(cards),"protected encrypted UI manager lists personal and server markers in current dimension");check("1".equals(web.evaluateScript("String(document.querySelectorAll('.wp-card [class=wp-badge]').length>=1?1:0)")),"server marker badge in protected manager");
                    try(var image=net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(Path.of(System.getProperty("bat.chat.smoke.out")).resolveSibling("waypoints-manager-native.png"));}
                    managerChecked=true;c.setScreen(null);c.getServer().execute(()->c.getServer().getPlayerManager().getPlayer(c.player.getUuid()).kill());}
            }
            if(managerChecked&&ticks>=185){var list=request("list",new JsonObject()).getAsJsonArray("waypoints");check(list.asList().stream().map(JsonElement::getAsJsonObject).anyMatch(e->e.get("group").getAsString().equals("Deaths")&&e.get("expiresAt").getAsLong()>System.currentTimeMillis()),"actual player death creates expiring personal waypoint");finish(c,"WAYPOINT_NATIVE_OK: "+checks+" checks");}
        }catch(Throwable error){error.printStackTrace();finish(c,"FAILED: "+error);}
    });}
    private void save(String name,int x,int y,int z,String color,String dimension,boolean track)throws Exception{var data=new JsonObject();data.addProperty("name",name);data.addProperty("group","Tests");data.addProperty("x",x);data.addProperty("y",y);data.addProperty("z",z);data.addProperty("color",color);data.addProperty("dimension",dimension);data.addProperty("tracked",track);data.addProperty("beam",true);check(request("save",data).get("error").getAsString().isEmpty(),"native save "+name);}
    private JsonObject request(String command,JsonObject value)throws Exception{return JsonParser.parseString((String)action.invoke(null,command,value.toString())).getAsJsonObject();}
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;}
    private void finish(MinecraftClient c,String message){done=true;try{Files.writeString(Path.of(System.getProperty("bat.chat.smoke.out")).resolveSibling("waypoint-result.txt"),message);}catch(Exception ignored){}System.out.println(message);c.scheduleStop();}
}
