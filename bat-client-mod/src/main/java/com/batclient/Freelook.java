package com.batclient;

import com.batclient.platform.ClientHookServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/** Camera-only orbit. Player yaw/pitch, movement and outgoing look remain vanilla. */
public final class Freelook {
    private static boolean active,lastHeld,blocked;
    private static Object world,player;
    private static Perspective previous;
    private static float yaw,pitch;
    private Freelook(){}
    public static void init(){BatKeybinds.register("freelook",ClientHookServices.get().registerKeyBinding(new KeyBinding("key.batclient.freelook",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_LEFT_ALT,"category.batclient.title")));}
    public static boolean active(){return active;}
    public static float yaw(){return yaw;}
    public static float pitch(){return pitch;}
    public static void tick(MinecraftClient c){
        boolean held=BatKeybinds.held(c,"freelook"),edge=held&&!lastHeld;lastHeld=held;if(!held)blocked=false;
        var mod=VisualModules.settings("freelook");
        boolean usable=HudConfig.enabled(mod)&&c.world!=null&&c.player!=null&&c.player.isAlive()&&c.getCameraEntity()==c.player&&c.currentScreen==null&&c.isWindowFocused();
        if(!usable||active&&(world!=c.world||player!=c.player)){stop(c,true);blocked=held;return;}
        if(active&&c.options.getPerspective()!=Perspective.THIRD_PERSON_BACK){stop(c,false);blocked=held;return;}
        boolean toggle="Toggle".equals(HudConfig.str(mod,"mode","Hold"));
        if(active&&(toggle?edge:!held)){stop(c,true);return;}
        if(!active&&!blocked&&(toggle?edge:held))start(c);
    }
    private static void start(MinecraftClient c){previous=c.options.getPerspective();yaw=c.player.getYaw()+(previous==Perspective.THIRD_PERSON_FRONT?180:0);pitch=previous==Perspective.THIRD_PERSON_FRONT?-c.player.getPitch():c.player.getPitch();world=c.world;player=c.player;active=true;c.options.setPerspective(Perspective.THIRD_PERSON_BACK);}
    private static void stop(MinecraftClient c,boolean restore){if(active&&restore&&previous!=null&&c.options.getPerspective()==Perspective.THIRD_PERSON_BACK)c.options.setPerspective(previous);active=false;previous=null;world=player=null;}
    public static boolean mouse(double x,double y){
        var c=MinecraftClient.getInstance();if(!active)return false;
        if(c.currentScreen!=null||c.world!=world||c.player!=player||!c.isWindowFocused()||!VisualModules.enabled("freelook")){stop(c,true);return false;}
        float speed=VisualModules.number(VisualModules.settings("freelook"),"sensitivity",100,10,200)/100;
        yaw=MathHelper.wrapDegrees(yaw+(float)x*.15f*speed);pitch=Math.clamp(pitch+(float)y*.15f*speed,-90,90);return true;
    }
}
