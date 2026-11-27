package com.batclient.mixin;

import com.batclient.platform.mc1194.DrawContext;
import com.batclient.platform.mc189.MatrixStack;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SelectWorldScreen.class)
public abstract class WorldScreenBrandMixin {
    @Inject(method = "render(IIF)V", at = @At("TAIL"))
    private void bat$brand(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        com.batclient.web.MenuBranding.draw(new DrawContext(new MatrixStack()));
    }
}
