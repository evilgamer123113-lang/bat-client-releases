package com.batclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SelectWorldScreen.class)
public abstract class WorldScreenBrandMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void bat$brand(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        com.batclient.web.MenuBranding.draw(ctx);
    }
}
