package com.batclient;

import com.batclient.hud.ChatFlow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;

/** Shared entrance for chat history and the input screen. No movement after opening. */
public final class ChatOpeningAnimation {
    private static Screen screen;
    private static long started;
    private ChatOpeningAnimation() { }
    public static void begin(Screen next) {
        if(screen!=next){screen=next;started=System.nanoTime();}
    }
    public static float progress() {
        var client=MinecraftClient.getInstance();
        if(!(client.currentScreen instanceof ChatScreen))return 1;
        begin(client.currentScreen);
        var mod=HudConfig.module(HudConfig.snapshot(),"chatanimations");
        return ChatFlow.progress((System.nanoTime()-started)/1_000_000L,HudConfig.setNumber(mod,"duration",200));
    }
    public static float offset() { return (1-progress())*12; }
}
