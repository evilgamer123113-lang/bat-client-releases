package com.batclient.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class ServerHudMixin {
    @Inject(method = "renderScoreboardObjective", at = @At("HEAD"), cancellable = true)
    private void bat$sidebar(ScoreboardObjective objective, Window window, CallbackInfo ci) {
        ci.cancel();
    }
}
