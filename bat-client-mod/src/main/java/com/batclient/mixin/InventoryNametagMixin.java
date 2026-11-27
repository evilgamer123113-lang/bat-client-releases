package com.batclient.mixin;

import com.batclient.HudConfig;
import com.batclient.NativeNametags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shared native player portrait helper covers survival and creative inventory. */
@Mixin(InventoryScreen.class)
public abstract class InventoryNametagMixin {
    @Unique private static float bat$portraitWidth;

    @Inject(method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V", at = @At("HEAD"))
    private static void bat$portraitBounds(DrawContext ctx, int left, int top, int right, int bottom, int size,
                                         float offset, float mouseX, float mouseY, LivingEntity entity, CallbackInfo ci) {
        bat$portraitWidth = Math.max(1, right - left - 2);
    }

    // Draw while the native portrait's scale, offset and mouse-driven rotation are active.
    @Inject(method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;FFFLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/entity/LivingEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"))
    private static void bat$portraitName(DrawContext ctx, float x, float y, float size, Vector3f offset,
                                         Quaternionf rotation, Quaternionf cameraRotation, LivingEntity entity, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (entity != client.player || !(client.currentScreen instanceof InventoryScreen
                || client.currentScreen instanceof CreativeInventoryScreen) || !NativeNametags.enabled()
                || !HudConfig.bool(NativeNametags.settings(), "showOwn", true)) return;
        Text name = NativeNametags.ownName(entity.getName());
        int width = client.textRenderer.getWidth(name), height = client.textRenderer.fontHeight;
        float scale = Math.min(Math.max(.5f, HudConfig.setNumber(NativeNametags.settings(), "scale", 1)) * .025f,
                bat$portraitWidth / (Math.max(1, size) * Math.max(1, width)));
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, entity.getHeight() + .12f, .05f);
        // Undo the portrait's horizontal flip, then follow the player's actual look angle.
        ctx.getMatrices().multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - (entity.getYaw() - 180)));
        ctx.getMatrices().scale(scale, -scale, scale);
        ctx.getMatrices().translate(-width / 2f, -height, .05f);
        if (HudConfig.bool(NativeNametags.settings(), "background", true)) ctx.fill(-2, -1, width + 2, height + 1, 0x6B000000);
        ctx.drawText(client.textRenderer, name, 0, 0, 0xFFFFFFFF, HudConfig.bool(NativeNametags.settings(), "shadow", true));
        NativeNametags.drawGuiLogo(ctx, name, 0, 0);
        ctx.draw();
        ctx.getMatrices().pop();
    }
}
