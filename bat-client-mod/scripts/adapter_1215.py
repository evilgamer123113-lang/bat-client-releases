"""Actual 1.21.5 API delta; compilation and native acceptance mandatory."""
import re
from pathlib import Path
def adapt(name,value):
    if name.endswith("VisualLayers.java"):
        return (Path(__file__).parent/"templates-1215/VisualLayers.java").read_text(encoding="utf-8")
    if name.endswith('ShaderRegistry.java'):
        value=value.replace('var key=new ShaderProgramKey(id.withPrefixedPath("core/"),format,Defines.EMPTY);\n    consumer.accept(Objects.requireNonNull(loader.getOrCreateProgram(key),"Bat shader missing: "+id));', 'consumer.accept(com.batclient.platform.mc1215.PipelinePrograms.program(id));')
    if name.endswith('VisualModules.java'):
        value=value.replace('glintShader.getUniformOrDefault("BatTint").set(', 'glintShader.getUniformOrDefault("BatTint").set(new float[]{').replace('number(mod,"strength",1,0,2));','number(mod,"strength",1,0,2)});')
        value=value.replace('Quaternionf','Quaternionfc')
    if name.endswith('ItemPhysicsMixin.java'):
        value=value.replace('Quaternionf','Quaternionfc')
    if name.endswith('GlintColorMixin.java'):
        return '''package com.batclient.mixin;
import com.batclient.VisualModules;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(CompiledShaderPipeline.class)
public class GlintColorMixin {
 @Shadow @Final private RenderPipeline info;
 @Inject(method="program",at=@At("RETURN"),cancellable=true)
 private void bat$glint(CallbackInfoReturnable<ShaderProgram> ci){if(info.getLocation().getNamespace().equals("minecraft")&&info.getLocation().getPath().contains("glint"))ci.setReturnValue(VisualModules.glint(ci.getReturnValue()));}
}
'''
    if name.endswith(('Fabric1215Smoke.java','Fabric1218Smoke.java','Fabric12111Smoke.java','VisualParitySmoke.java')):
        value=value.replace('new net.minecraft.world.GameRules()', 'new net.minecraft.world.GameRules(net.minecraft.resource.DataConfiguration.SAFE_MODE.enabledFeatures())')
        value=value.replace('registry.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).get(net.minecraft.world.gen.WorldPresets.FLAT)', 'registry.getOrThrow(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(net.minecraft.world.gen.WorldPresets.FLAT).value()')
    value=value.replace('net.minecraft.client.render.VertexFormat;', 'com.mojang.blaze3d.vertex.VertexFormat;')
    value=re.sub(r'new NativeImageBackedTexture\((image|RES,RES,false)\)',r'new NativeImageBackedTexture(()->"Bat Client texture",\1)',value)
    value=value.replace('.getTickDelta(', '.getTickProgress(').replace('horse.prevX','horse.lastX').replace('horse.prevZ','horse.lastZ')
    value=value.replace('.getHorizontalFacing().getName()', '.getHorizontalFacing().asString()')
    value=value.replace('.getInventory().main', '.getInventory().getMainStacks()')
    value=re.sub(r'([a-zA-Z_$][\w$]*(?:\.[a-zA-Z_$][\w$]*)*)\.getInventory\(\)\.armor',r'java.util.List.of(\1.getInventory().getStack(36),\1.getInventory().getStack(37),\1.getInventory().getStack(38),\1.getInventory().getStack(39))',value)
    value=re.sub(r'([a-zA-Z_$][\w$]*(?:\.[a-zA-Z_$][\w$]*)*)\.getInventory\(\)\.offHand',r'java.util.List.of(\1.getInventory().getStack(40))',value)
    if name.endswith('SkinLayers3D.java'):
        value=value.replace('part.rotate(matrices)', 'part.applyTransform(matrices)')
        value=value.replace('texture.getGlId()', '((net.minecraft.client.texture.GlTexture)texture.getGlTexture()).getGlId()')
        value=value.replace('texture.bindTexture()', 'com.mojang.blaze3d.opengl.GlStateManager._bindTexture(gl)')
        value=value.replace('image.loadFromTextureImage(0,false);pixels=new SkinAlphaMask(image::getColorArgb);remember(texture,pixels);', 'pixels=com.batclient.platform.mc1215.SkinReadback.readBound64();remember(texture,pixels);')
    if name.endswith('SkinLayerPixelsMixin.java'):
        value=value.replace('<init>(Lnet/minecraft/client/texture/NativeImage;)V', '<init>(Ljava/util/function/Supplier;Lnet/minecraft/client/texture/NativeImage;)V')
        value=value.replace('bat$skinPixels(NativeImage image,CallbackInfo callback)', 'bat$skinPixels(java.util.function.Supplier<String> label,NativeImage image,CallbackInfo callback)')
    if name.endswith('MenuRegressionChecks.java'):
        value=value.replace('var shader = com.mojang.blaze3d.systems.RenderSystem.getShader();', '')
        value=value.replace('int shaderTexture =', 'var shaderTexture =')
        value=value.replace('boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);', '')
        value=value.replace('check(com.mojang.blaze3d.systems.RenderSystem.getShader() == shader, "draw changed shader");',
            'check(GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM) == BatClientShaders.bgraPositionTextureShader.getGlRef(), "native web pipeline did not bind its program");')
        value=value.replace('check(GL11.glIsEnabled(GL11.GL_BLEND) == blend, "draw changed blend enable");', '')
    if name.endswith('MotionBlur.java'):
        value=value.replace('target.fbo', 'com.batclient.platform.mc1215.GlFramebuffer.id(target)')
    if name.endswith('ItemPreviews.java'):
        value=re.sub(r'stack.getItem\(\) instanceof ArmorItem (?:a|armor)&&stack.contains\(net.minecraft.component.DataComponentTypes.EQUIPPABLE\)', 'stack.contains(net.minecraft.component.DataComponentTypes.EQUIPPABLE)&&stack.get(net.minecraft.component.DataComponentTypes.EQUIPPABLE).slot().isArmorSlot()',value)
        value=value.replace('stack.getItem() instanceof SwordItem||stack.getItem() instanceof MiningToolItem', 'stack.isIn(net.minecraft.registry.tag.ItemTags.SWORDS)||stack.isIn(net.minecraft.registry.tag.ItemTags.AXES)||stack.isIn(net.minecraft.registry.tag.ItemTags.PICKAXES)||stack.isIn(net.minecraft.registry.tag.ItemTags.SHOVELS)||stack.isIn(net.minecraft.registry.tag.ItemTags.HOES)')
    value=value.replace('com.mojang.blaze3d.platform.GlStateManager','com.mojang.blaze3d.opengl.GlStateManager')
    for old,new in [('enableBlend()', '_enableBlend()'),('disableBlend()', '_disableBlend()'),('activeTexture(', '_activeTexture('),('bindTexture(', '_bindTexture(')]:
        value=value.replace('RenderSystem.'+old,'com.mojang.blaze3d.opengl.GlStateManager.'+new)
    value=value.replace('RenderSystem.defaultBlendFunc()', 'com.mojang.blaze3d.opengl.GlStateManager._blendFuncSeparate(770,771,1,0)')
    if name.endswith('HitColorMixin.java'):
        value=value.replace('new NativeImageBackedTexture(16,16,false)', 'new NativeImageBackedTexture(()->"Bat hit overlay",16,16,false)')
        value=value.replace('com.mojang.blaze3d.opengl.GlStateManager._activeTexture(GL13.GL_TEXTURE1);', '')
        value=value.replace('bat$hitTexture.bindTexture();image.upload(0,0,0,0,0,16,16,false);', 'bat$hitTexture.upload();')
        value=value.replace('com.mojang.blaze3d.opengl.GlStateManager._activeTexture(GL13.GL_TEXTURE0);', '')
        value=value.replace('RenderSystem.setupOverlayColor(bat$hitTexture.getGlId(),16)', 'RenderSystem.setupOverlayColor(bat$hitTexture.getGlTexture())')
    if name.endswith('WebWindow.java'):
        value=value.replace('import net.minecraft.client.render.BufferRenderer;', '')
        value=value.replace('private int texture;', 'private int texture;\n    private com.mojang.blaze3d.textures.GpuTexture gpuTexture;\n    private final com.batclient.platform.mc1215.GpuWebQuad gpuQuad=new com.batclient.platform.mc1215.GpuWebQuad();')
        value=value.replace('this.texture = GL11.glGenTextures();', '')
        start=value.index('\t\tShaderProgram shader =',value.index('public void drawRegion('));end=value.index('\n\t// ---- input forwarding',start)
        value=value[:start]+'\t\tgpuQuad.draw(context,gpuTexture,left,top,width,height,u1,v1,u2,v2);\n\t}\n'+value[end:]
        start=value.index('\t\tint previousTexture =',value.index('private void resizeTexture('));end=value.index('\n\t\tif (pbo == 0)',start)
        value=value[:start]+'''        if(gpuTexture!=null)gpuTexture.close();
        gpuTexture=RenderSystem.getDevice().createTexture("Bat web surface",com.mojang.blaze3d.textures.TextureFormat.RGBA8,width,height,1);
        gpuTexture.setAddressMode(com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE);
        gpuTexture.setTextureFilter(com.mojang.blaze3d.textures.FilterMode.LINEAR,false);
        if(!(gpuTexture instanceof net.minecraft.client.texture.GlTexture gl))throw new IllegalStateException("Ultralight PBO upload requires the OpenGL backend");
        texture=gl.getGlId();
'''+value[end:]
        value=value.replace('GlStateManager._deleteTexture(texture);', 'gpuQuad.close();if(gpuTexture!=null){gpuTexture.close();gpuTexture=null;}')
    return value
