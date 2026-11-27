package com.batclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve the limiter and background fairness; avoid an extra uncapped gameplay yield. */
@Mixin(MinecraftClient.class)
public abstract class GameLoopYieldMixin {
    @Shadow public ClientWorld world;
    @Shadow public Screen currentScreen;
    @Shadow public GameOptions options;

    @Redirect(method="render",at=@At(value="INVOKE",target="Ljava/lang/Thread;yield()V"),require=0)
    private void bat$gameplayYield() {
        MinecraftClient client=(MinecraftClient)(Object)this;
        if(world!=null&&currentScreen==null&&client.isWindowFocused()
                &&options.getMaxFps().getValue()>=260&&!options.getEnableVsync().getValue())return;
        Thread.yield();
    }
}
