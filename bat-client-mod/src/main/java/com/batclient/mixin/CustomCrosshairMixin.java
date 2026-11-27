package com.batclient.mixin;

import com.batclient.HudConfig;
import com.batclient.HudRenderer;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InGameHud.class)
public class CustomCrosshairMixin {
    // Replace only the first sprite. Vanilla still owns first-person/spectator/F3
    // visibility checks and the attack cooldown indicator below the crosshair.
    @Redirect(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V", ordinal = 0))
    private void bat$crosshair(DrawContext ctx, Identifier sprite, int x, int y, int width, int height) {
        var mod = HudConfig.module(HudConfig.snapshot(), "crosshair");
        if (!HudConfig.enabled(mod)) {
            ctx.drawGuiTexture(sprite, x, y, width, height);
            return;
        }
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        try {
            ctx.draw();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            HudRenderer.drawCustomCrosshair(ctx);
            ctx.draw();
        } finally {
            GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }
}
