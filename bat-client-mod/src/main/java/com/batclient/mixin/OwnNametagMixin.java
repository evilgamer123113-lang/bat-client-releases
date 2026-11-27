package com.batclient.mixin;

import com.batclient.HudConfig;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.text.Text;
import com.batclient.NativeNametags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Own labels use vanilla's camera-facing transform and resource-pack font. */
@Mixin(PlayerEntityRenderer.class)
public abstract class OwnNametagMixin {
    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("TAIL"))
    private void bat$ownName(AbstractClientPlayerEntity player, float yaw, float delta, MatrixStack matrices,
                              VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        JsonObject mod = HudConfig.module(HudConfig.snapshot(), "nametags");
        if ((mod != null && !HudConfig.enabled(mod)) || !HudConfig.bool(mod, "showOwn", true)
                || player != client.player || client.options.getPerspective().isFirstPerson()
                || !MinecraftClient.isHudEnabled() || player.isInvisible() || client.currentScreen != null) return;
        var attachment = player.getAttachments().getPointNullable(EntityAttachmentType.NAME_TAG, 0, player.getYaw(delta));
        if (attachment == null) return;
        Text label = NativeNametags.ownName(player.getName());
        float scale = Math.max(.5f, Math.min(2, HudConfig.setNumber(mod, "scale", 1))) * .025f;
        matrices.push();
        matrices.translate(attachment.x, attachment.y + .5, attachment.z);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        matrices.scale(scale, -scale, scale);
        int background = HudConfig.bool(mod, "background", true)
                ? (int)(client.options.getTextBackgroundOpacity(.25f) * 255) << 24 : 0;
        client.textRenderer.draw(label, -client.textRenderer.getWidth(label) / 2f, 0, 0xFFFFFFFF,
                HudConfig.bool(mod, "shadow", true), matrices.peek().getPositionMatrix(), vertices,
                TextRenderer.TextLayerType.NORMAL, background, light);
        NativeNametags.drawLogo(label, -client.textRenderer.getWidth(label) / 2f, 0, 0xFFFFFFFF,
                matrices.peek().getPositionMatrix(), vertices, TextRenderer.TextLayerType.NORMAL, light);
        matrices.pop();
    }
}
