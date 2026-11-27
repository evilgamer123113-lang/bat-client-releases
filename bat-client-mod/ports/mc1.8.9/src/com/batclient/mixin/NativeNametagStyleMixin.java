package com.batclient.mixin;

import com.batclient.HudConfig;
import com.batclient.HudRenderer;
import com.batclient.VisualModules;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class NativeNametagStyleMixin<T extends Entity> {
    @Redirect(
        method = "renderLabelIfPresent(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/BufferBuilder;")
    )
    private BufferBuilder bat$bgColor(BufferBuilder buf, float r, float g, float b, float a) {
        if (VisualModules.enabled("nametags")) {
            com.google.gson.JsonObject mod = VisualModules.settings("nametags");
            int bg = HudRenderer.parseColor(HudConfig.str(mod, "bgColor", "#000000"), 0xFF000000);
            float op = VisualModules.number(mod, "bgOpacity", 0.25f, 0f, 1f);
            return buf.color(((bg >>> 16) & 255) / 255f, ((bg >>> 8) & 255) / 255f, (bg & 255) / 255f, op);
        }
        return buf.color(r, g, b, a);
    }
}
