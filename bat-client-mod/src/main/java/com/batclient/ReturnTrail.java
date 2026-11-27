package com.batclient;

import com.batclient.hud.TrailHistory;
import com.google.gson.JsonObject;
import com.batclient.platform.ClientHookServices;
import com.batclient.platform.WorldFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import org.joml.Vector3f;
import java.util.*;

/** Breadcrumbs of observed movement. No route calculation, chunk loads, files or network messages. */
public final class ReturnTrail {
    private static final TrailHistory HISTORY=new TrailHistory();
    private static List<TrailHistory.Point> points=List.of(),route=List.of();
    private static String key="";private static Object world,player;private static int ticks,cursor=-1;private static boolean wasDead;
    private ReturnTrail(){}
    public static void init(){
        BatKeybinds.register("trail_retrace",ClientHookServices.get().registerKeyBinding(new KeyBinding("key.batclient.trail_retrace",InputUtil.Type.KEYSYM,-1,"category.batclient.title")));
        ClientHookServices.get().afterEntities(ReturnTrail::render);ClientHookServices.get().hud(ReturnTrail::guide);
    }
    private static long now(){return System.nanoTime()/1_000_000;}
    private static String identity(MinecraftClient c){String scope=c.getServer()!=null?"save:"+c.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize():c.getCurrentServerEntry()!=null?"server:"+ServerProfiles.normalize(c.getCurrentServerEntry().address):"connection:"+c.getNetworkHandler().getConnection().getAddress();return scope+"|"+c.world.getRegistryKey().getValue();}
    private static void stop(){cursor=-1;route=List.of();HISTORY.interrupt();}
    public static void tick(MinecraftClient c){
        boolean retrace=BatKeybinds.pressed(c,"trail_retrace");
        if(c.world==null||c.player==null){if(world!=null)HISTORY.clearAll();world=player=null;key="";points=List.of();stop();return;}
        if(world!=c.world||player!=c.player){world=c.world;player=c.player;key=identity(c);HISTORY.context(key);HISTORY.interrupt();stop();wasDead=false;}
        var mod=VisualModules.settings("returntrail");if(!HudConfig.enabled(mod)){points=List.of();stop();return;}
        long time=now(),lifetime=(long)(VisualModules.number(mod,"minutes",10,1,30)*60000);int cap=(int)VisualModules.number(mod,"points",512,64,1024);
        if(c.player.isDead()){if(!wasDead&&HudConfig.bool(mod,"clearOnDeath",true))HISTORY.clear(key);wasDead=true;stop();points=HISTORY.points(key,time,lifetime,cap);return;}wasDead=false;
        if(retrace&&c.currentScreen==null)action(cursor>=0?"resume":"retrace");
        if(++ticks%5!=0)return;
        points=HISTORY.points(key,time,lifetime,cap);
        if(cursor>=0){
            if(route.isEmpty()||time-route.get(cursor).time()>=lifetime){stop();return;}
            while(cursor>=0&&route.get(cursor).distanceSquared(c.player.getX(),c.player.getY(),c.player.getZ())<=2.25)cursor--;
            if(cursor<0){stop();c.inGameHud.setOverlayMessage(Text.literal("Back at the start of your trail"),false);}return;
        }
        if(c.isPaused())return;
        if(c.player.hasVehicle()||c.player.isFallFlying()||c.player.getAbilities().flying||HudConfig.bool(mod,"groundOnly",false)&&!c.player.isOnGround()){HISTORY.interrupt();return;}
        HISTORY.sample(key,c.player.getX(),c.player.getY(),c.player.getZ(),time,lifetime,cap,VisualModules.number(mod,"spacing",1,.5f,4));points=HISTORY.points(key,time,lifetime,cap);
    }
    public static String action(String action){
        var c=MinecraftClient.getInstance();String error="";var mod=VisualModules.settings("returntrail");
        if(action.equals("clear")){HISTORY.clear(key);points=List.of();stop();}
        else if(action.equals("resume"))stop();
        else if(action.equals("retrace")){
            if(!HudConfig.enabled(mod)||c.world==null||c.player==null||c.world!=world||c.player!=player)error="Enable Return Trail and join a world first.";
            else{var latest=TrailHistory.latestRoute(points);if(latest.size()<2)error="Walk a little farther to record a route first.";else{route=latest;cursor=route.size()-2;}}
        }else if(!action.equals("status"))error="Unknown trail action.";
        var result=new JsonObject();result.addProperty("error",error);result.addProperty("points",points.size());result.addProperty("returning",cursor>=0);result.addProperty("remaining",Math.max(0,cursor+1));result.addProperty("recording",HudConfig.enabled(mod)&&c.player!=null&&c.world==world&&cursor<0);return result.toString();
    }
    private static void line(WorldFrame ctx,VertexConsumer v,Vec3d a,Vec3d b,int color){var m=ctx.matrixStack().peek();var n=new Vector3f((float)(b.x-a.x),(float)(b.y-a.y),(float)(b.z-a.z));if(n.lengthSquared()<.000001)return;n.normalize();v.vertex(m,(float)a.x,(float)a.y,(float)a.z).color(color).normal(m,n.x,n.y,n.z);v.vertex(m,(float)b.x,(float)b.y,(float)b.z).color(color).normal(m,n.x,n.y,n.z);}
    private static void render(WorldFrame ctx){
        var c=MinecraftClient.getInstance();var mod=VisualModules.settings("returntrail");if(!HudConfig.enabled(mod)||c.player==null||c.world!=world||c.currentScreen!=null||!MinecraftClient.isHudEnabled()||ctx.matrixStack()==null||ctx.consumers()==null)return;
        var visible=cursor>=0?route:points;var camera=ctx.camera().getPos();double range=VisualModules.number(mod,"range",48,8,128);long age=now(),ttl=(long)(VisualModules.number(mod,"minutes",10,1,30)*60000);
        int base=HudRenderer.parseColor(HudConfig.str(mod,"color","#75CFFFFF"),0xFF75CFFF);float opacity=VisualModules.number(mod,"opacity",80,10,100)/100;var vertices=ctx.consumers().getBuffer(VisualLayers.lines(VisualModules.number(mod,"width",2,1,4)));String style=HudConfig.str(mod,"style","Both");int drawn=0;
        for(int i=visible.size()-1;i>0&&drawn<256;i--){var p=visible.get(i);var previous=visible.get(i-1);if(p.breakBefore()||age-p.time()>=ttl||p.distanceSquared(c.player.getX(),c.player.getY(),c.player.getZ())>range*range||cursor>=0&&i>cursor+1)continue;
            Vec3d a=new Vec3d(p.x(),p.y()+.045,p.z()).subtract(camera),b=new Vec3d(previous.x(),previous.y()+.045,previous.z()).subtract(camera);float fade=(float)Math.clamp(1-(double)(age-p.time())/ttl,.15,1);int color=(Math.round((base>>>24)*opacity*fade)<<24)|(base&0xFFFFFF);
            if(!style.equals("Arrows"))line(ctx,vertices,a,b,color);
            if(!style.equals("Line")){var direction=new Vec3d(b.x-a.x,0,b.z-a.z);if(direction.lengthSquared()>.0001){direction=direction.normalize();var side=new Vec3d(-direction.z,0,direction.x);var tip=a.add(direction.multiply(.18));var back=a.subtract(direction.multiply(.12));line(ctx,vertices,tip,back.add(side.multiply(.12)),color);line(ctx,vertices,tip,back.subtract(side.multiply(.12)),color);}}
            drawn++;
        }
    }
    private static void guide(DrawContext ctx){var c=MinecraftClient.getInstance();var mod=VisualModules.settings("returntrail");if(cursor<0||!HudConfig.enabled(mod)||!HudConfig.bool(mod,"guide",true)||c.player==null||c.currentScreen!=null||!MinecraftClient.isHudEnabled())return;var target=route.get(cursor);double dx=target.x()-c.player.getX(),dz=target.z()-c.player.getZ(),yaw=net.minecraft.util.math.MathHelper.wrapDegrees((float)(Math.toDegrees(Math.atan2(-dx,dz))-c.player.getYaw()));String arrow=Math.abs(yaw)<20?"^":Math.abs(yaw)>160?"v":yaw>0?">":"<";double distance=Math.sqrt(target.distanceSquared(c.player.getX(),c.player.getY(),c.player.getZ()));for(int i=cursor;i>0;i--)distance+=Math.sqrt(route.get(i).distanceSquared(route.get(i-1).x(),route.get(i-1).y(),route.get(i-1).z()));String text=arrow+" Trail back · "+Math.round(distance)+"m";int width=c.textRenderer.getWidth(text),x=(c.getWindow().getScaledWidth()-width)/2,y=Math.round(c.getWindow().getScaledHeight()*.21f);HudRenderer.fillRounded(ctx,x-7,y-4,width+14,17,4,0x99000000);ctx.drawText(c.textRenderer,text,x,y,0xFFFFFFFF,true);}
}
