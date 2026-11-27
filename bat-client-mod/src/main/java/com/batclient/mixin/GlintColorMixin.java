package com.batclient.mixin;
import com.batclient.VisualModules;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GlintColorMixin {
    @Inject(method={"getRenderTypeArmorGlintProgram","getRenderTypeArmorEntityGlintProgram","getRenderTypeGlintTranslucentProgram","getRenderTypeGlintProgram","getRenderTypeGlintDirectProgram","getRenderTypeEntityGlintProgram","getRenderTypeEntityGlintDirectProgram"},at=@At("RETURN"),cancellable=true)
    private static void bat$glint(CallbackInfoReturnable<ShaderProgram> ci){ci.setReturnValue(VisualModules.glint(ci.getReturnValue()));}
}
