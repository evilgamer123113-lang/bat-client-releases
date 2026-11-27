package com.batclient;

import com.batclient.hud.ChatFlow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import java.util.Map;
import java.util.WeakHashMap;

/** Optional native inventory entrance. Input uses the last painted position. */
public final class InventoryOpeningAnimation {
    private static final Map<Screen,State> screens=new WeakHashMap<>();
    private static float drawingOffset;
    private static final class State { final long start=System.nanoTime(); float painted; }
    private InventoryOpeningAnimation() { }
    public static void begin(Screen screen){screens.computeIfAbsent(screen,key->new State());}
    public static float offset(Screen screen){
        var module=HudConfig.module(HudConfig.snapshot(),"inventoryanimations");
        if(module!=null&&!HudConfig.enabled(module))return 0;
        State state=screens.computeIfAbsent(screen,key->new State());
        return (1-ChatFlow.progress((System.nanoTime()-state.start)/1_000_000L,180))*10;
    }
    public static void drawing(Screen screen){
        drawingOffset=offset(screen);screens.get(screen).painted=drawingOffset;
    }
    public static void finished(){drawingOffset=0;}
    public static float drawingOffset(){return drawingOffset;}
    public static float inputOffset(){
        Screen screen=MinecraftClient.getInstance().currentScreen;
        if(!(screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen))return 0;
        State state=screens.get(screen);return state==null?0:state.painted;
    }
}
