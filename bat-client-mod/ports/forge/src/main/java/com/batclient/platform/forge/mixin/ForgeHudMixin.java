package com.batclient.platform.forge.mixin;

import com.batclient.platform.forge.ForgeClientHooks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Post-HUD hook works with Forge 52's layered HUD without removed RenderGuiEvent APIs. */
@Mixin(InGameHud.class)
public final class ForgeHudMixin {
    @Inject(method="render",at=@At("RETURN"))
    private void bat$hud(DrawContext context,RenderTickCounter tickCounter,CallbackInfo info){ForgeClientHooks.fireHud(context);}
}
