"""Generate explicit 1.16.5 API deltas on top of 1.17.1 adaptation."""
import re


def adapt(name: str, value: str) -> str:
    # 1.16.5 uses log4j instead of slf4j.
    value = value.replace('import org.slf4j.Logger;', 'import org.apache.logging.log4j.Logger;')
    value = value.replace('import org.slf4j.LoggerFactory;', 'import org.apache.logging.log4j.LogManager;')
    value = value.replace('LoggerFactory.getLogger(', 'LogManager.getLogger(')

    # 1.16.5 SplashScreen & ScreenshotUtils & Registry & LightmapTextureManager.
    value = value.replace('net.minecraft.client.gui.screen.SplashOverlay', 'net.minecraft.client.gui.screen.SplashScreen')
    value = value.replace('SplashOverlay', 'SplashScreen')
    value = value.replace('net.minecraft.client.util.ScreenshotRecorder', 'net.minecraft.client.util.ScreenshotUtils')
    value = value.replace('ScreenshotRecorder', 'ScreenshotUtils')
    value = value.replace('LightmapTextureManager.MAX_LIGHT_COORDINATE', 'LightmapTextureManager.pack(15, 15)')
    value = value.replace('CHUNK_GENERATOR_SETTINGS_KEY', 'NOISE_SETTINGS_WORLDGEN')
    value = value.replace('SharedConstants.getGameVersion().getName()', 'SharedConstants.getGameVersion().getName()')
    value = value.replace('net.minecraft.SharedConstants.VERSION_NAME', '"1.16.5"')

    # RenderSystem / immediate quads (no core Shaders in 1.16.5).
    value = value.replace('com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,', 'net.minecraft.client.MinecraftClient.getInstance().getTextureManager().bindTexture(')
    value = value.replace('RenderSystem.setShaderTexture(0,', 'net.minecraft.client.MinecraftClient.getInstance().getTextureManager().bindTexture(')
    value = value.replace('com.mojang.blaze3d.systems.RenderSystem.setShaderColor(', 'com.mojang.blaze3d.systems.RenderSystem.color4f(')
    value = value.replace('RenderSystem.setShaderColor(', 'RenderSystem.color4f(')
    value = value.replace('GlStateManager._deleteTexture(', 'RenderSystem.deleteTexture(')
    value = value.replace('GlStateManager._blendFuncSeparate(', 'RenderSystem.blendFuncSeparate(')
    value = value.replace('VertexFormat.DrawMode.QUADS', '7')
    value = value.replace('net.minecraft.client.render.VertexFormat.DrawMode.QUADS', '7')
    value = value.replace('VertexFormat.DrawMode.LINES', '1')
    value = value.replace('VertexFormat.DrawMode.class', 'int.class')
    value = value.replace('VertexFormat.DrawMode ', 'int ')
    value = value.replace('VertexFormats.LINES', 'VertexFormats.POSITION_COLOR')
    value = value.replace('writeColumnMajor(', 'writeRowFirst(')
    value = value.replace('.getSelectedOrNull()', '.getSelected()')
    value = value.replace('.getMapColor(', '.getTopMaterialColor(')
    value = value.replace('entity.getId()', 'entity.getEntityId()')

    # MinecraftClient.openScreen, Screen.addButton, PlayerEntity fields, Entity methods.
    value = value.replace('.setScreen(', '.openScreen(')
    value = value.replace('addDrawableChild(', 'addButton(')
    value = value.replace('.getInventory()', '.inventory')
    value = value.replace('.getAbilities()', '.abilities')
    value = value.replace('.isRemoved()', '.removed')
    value = value.replace('.getBlockX()', '.getBlockPos().getX()')
    value = value.replace('.getBlockY()', '.getBlockPos().getY()')
    value = value.replace('.getBlockZ()', '.getBlockPos().getZ()')
    value = value.replace('.getEyePos()', '.getCameraPosVec(1.0F)')
    value = re.sub(r'\b([A-Za-z0-9_$.()]+)\.getChunkPos\(\)', r'new net.minecraft.util.math.ChunkPos(\1.getBlockPos())', value)
    value = re.sub(r'\b([A-Za-z0-9_$.()]+)\.getYaw\(\)', r'\1.getYaw(1.0F)', value)
    value = re.sub(r'\b([A-Za-z0-9_$.()]+)\.getPitch\(\)', r'\1.getPitch(1.0F)', value)
    value = re.sub(r'\b([A-Za-z0-9_$.()]+)\.getLerpedPos\(([^)]+)\)', r'\1.getPos()', value)
    value = re.sub(r'\b([A-Za-z0-9_$.()]+)\.getBottomY\(\)', '0', value)
    value = re.sub(r'\b([A-Za-z0-9_$.()]+)\.getTopY\(\)', r'\1.getHeight()', value)

    # ItemStack & NativeImage.
    value = re.sub(r'!([A-Za-z0-9_$.()]+)\.isOf\(([^)]+)\)', r'\1.getItem() != \2', value)
    value = re.sub(r'\.isOf\(([^)]+)\)', r'.getItem() == \1', value)
    value = value.replace('.getNbt()', '.getTag()')
    value = value.replace('.getTag()', '.getTag()')
    value = value.replace('.getSubNbt(', '.getSubTag(')
    value = value.replace('.setSubNbt(', '.setSubTag(')
    if name.endswith(('AccountHeads.java', 'SkinLayers3D.java', 'TerrainMapRaster.java', 'HudRenderer.java', 'MinimapHud.java', 'HitColorMixin.java')):
        value = value.replace('::getColor', '::getPixelColor')
        value = value.replace('image.getColor(', 'image.getPixelColor(')
        value = value.replace('original.getColor(', 'original.getPixelColor(')
        value = value.replace('image.setColor(', 'image.setPixelColor(')
        value = value.replace('getImage().setColor(', 'getImage().setPixelColor(')
    value = value.replace('.writeTo(', '.writeFile(')

    # WebWindow: BGRA upload + fixed-function quad without Shader.
    if name.endswith('WebWindow.java'):
        value = value.replace('import net.minecraft.client.render.Shader;', '')
        value = value.replace('GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,', 'org.lwjgl.opengl.GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE,')
        value = value.replace('Shader shader = BatClientShaders.bgraPositionTextureShader;\n\t\tif (shader == null) {\n\t\t\treturn;\n\t\t}\n', '')
        value = value.replace('Shader previousShader = RenderSystem.getShader();', '')
        value = value.replace('int previousShaderTexture = RenderSystem.getShaderTexture(0);', 'int previousShaderTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);')
        value = value.replace('net.minecraft.client.MinecraftClient.getInstance().getTextureManager().bindTexture( texture);', 'RenderSystem.bindTexture(texture);')
        value = value.replace('net.minecraft.client.MinecraftClient.getInstance().getTextureManager().bindTexture( previousShaderTexture);', 'RenderSystem.bindTexture(previousShaderTexture);')
        value = value.replace('RenderSystem.setShader(() -> shader);', 'RenderSystem.enableTexture(); RenderSystem.disableAlphaTest(); RenderSystem.color4f(1f, 1f, 1f, 1f);')
        value = value.replace('RenderSystem.setShader(() -> previousShader);', 'RenderSystem.enableAlphaTest();')

    if name.endswith('BatClientShaders.java'):
        value = value.replace('import net.minecraft.client.render.Shader;', '')
        value = value.replace('public static Shader bgraPositionTextureShader;', 'public static Object bgraPositionTextureShader = new Object();')
        value = value.replace('context.register(BGRA_POSITION_TEX, VertexFormats.POSITION_TEXTURE, program -> {\n\t\t\t\tbgraPositionTextureShader = program;\n\t\t\t});', 'bgraPositionTextureShader = new Object();')
        value = value.replace('context.register(Identifier.of("batclient","tinted_glint"), VertexFormats.POSITION_TEXTURE, program -> com.batclient.VisualModules.glintShader=program);', '')

    if name.endswith('GlintColorMixin.java'):
        return '''package com.batclient.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MinecraftClient.class)
public final class GlintColorMixin {
}
'''

    if name.endswith('VisualModules.java'):
        value = value.replace('import net.minecraft.client.render.Shader;', '')
        value = value.replace('public static Shader glintShader;', 'public static Object glintShader;')
        value = value.replace('public static Shader glint(Shader vanilla){', 'public static Object glint(Object vanilla){')
        value = re.sub(r'glintShader\.getUniformOrDefault\([^;]+;', '', value)

    if name.endswith('DrawContext.java'):
        value = value.replace(
            'MatrixStack mv = RenderSystem.getModelViewStack();\n        mv.push();\n        mv.peek().getModel().multiply(matrices.peek().getModel());\n        RenderSystem.applyModelViewMatrix();',
            'RenderSystem.pushMatrix();\n        RenderSystem.multMatrix(matrices.peek().getModel());',
        )
        value = value.replace(
            'mv.pop();\n        RenderSystem.applyModelViewMatrix();',
            'RenderSystem.popMatrix();',
        )

    if name.endswith('VisualLayers.java'):
        value = value.replace('import net.minecraft.client.render.Shader;', '')
        value = value.replace('.shader(new Shader(GameRenderer::getPositionColorShader))', '')
        value = value.replace('.shader(LINES_SHADER)', '')

    if name.endswith('ClientHooks.java'):
        value = value.replace(
            'void tooltipComponent(java.util.function.Function<net.minecraft.client.item.TooltipData,net.minecraft.client.gui.tooltip.TooltipComponent> factory);',
            'default void tooltipComponent(java.util.function.Function<Object,Object> factory) {}',
        )

    if name.endswith('ClientResources.java'):
        value = value.replace('import net.minecraft.client.render.Shader;', '')
        value = value.replace('Consumer<Shader> loaded', 'Consumer<Object> loaded')

    if name.endswith('ItemPreviews.java'):
        value = value.replace('import net.minecraft.client.gui.tooltip.TooltipComponent;\n', '')
        value = value.replace('import net.minecraft.client.item.TooltipData;\n', '')
        value = value + '\ninterface TooltipComponent {}\ninterface TooltipData {}\n'

    if name.endswith('ServerLocation.java'):
        value = value.replace('import net.minecraft.client.network.AllowedAddressResolver;\n', '')
        value = value.replace(
            'var resolved = AllowedAddressResolver.DEFAULT.resolve(ServerAddress.parse(address)).orElseThrow();',
            'var resolved = java.net.InetAddress.getByName(ServerAddress.parse(address).getAddress());',
        )
        value = value.replace('resolved.getSocketAddress().getAddress().getHostAddress()', 'resolved.getHostAddress()')
        value = value.replace('resolved.getInetSocketAddress().getAddress()', 'resolved')

    if name.endswith('ShulkerPreviewMixin.java'):
        return '''package com.batclient.mixin;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemStack.class)
public final class ShulkerPreviewMixin {
}
'''

    if name.endswith('AccountHeads.java'):
        value = value.replace('session.getAccountType()!=Session.AccountType.LEGACY', 'true')

    if name.endswith('DrawContext.java'):
        value = value.replace('RenderSystem.bindTexture(texture);', 'client.getTextureManager().bindTexture(texture);')
        value = value.replace('RenderSystem.bindTexture(sprite.getAtlas().getId());', 'client.getTextureManager().bindTexture(sprite.getAtlas().getId());')
        value = value.replace('client.currentScreen.renderTooltip(matrices, client.currentScreen.getTooltipFromItem(stack), stack.getTooltipData(), x, y);', 'client.currentScreen.renderTooltip(matrices, client.currentScreen.getTooltipFromItem(stack), x, y);')

    if name.endswith('HudRenderer.java'):
        value = value.replace('float[] oldColor = com.mojang.blaze3d.systems.RenderSystem.getShaderColor().clone();', 'float[] oldColor = new float[]{1f, 1f, 1f, 1f};')
        value = value.replace(
            'new ClientBossBar(new java.util.UUID(0, 0), new net.minecraft.text.LiteralText("Bat Client"), .8f, BossBar.Color.PURPLE, BossBar.Style.PROGRESS, false, false, false)',
            'new ClientBossBar(new net.minecraft.network.packet.s2c.play.BossBarS2CPacket(net.minecraft.network.packet.s2c.play.BossBarS2CPacket.Type.ADD, new net.minecraft.entity.boss.BossBar(new java.util.UUID(0, 0), new net.minecraft.text.LiteralText("Bat Client"), BossBar.Color.PURPLE, BossBar.Style.PROGRESS) {})) {{ setPercent(.8f); }}',
        )

    if name.endswith('MenuRegressionChecks.java'):
        value = value.replace('var shader = com.mojang.blaze3d.systems.RenderSystem.getShader();', 'Object shader = null;')
        value = value.replace('int shaderTexture = com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(0);', 'int shaderTexture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);')
        value = value.replace('check(com.mojang.blaze3d.systems.RenderSystem.getShader() == shader, "draw changed shader");', '')
        value = value.replace('check(com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(0) == shaderTexture, "draw changed shader texture");', 'check(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D) == shaderTexture, "draw changed shader texture");')

    if name.endswith('VisualParitySmoke.java'):
        value = value.replace(
            'ScreenshotUtils.takeScreenshot(client.getFramebuffer())',
            'ScreenshotUtils.takeScreenshot(client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), client.getFramebuffer())',
        )

    if name.endswith('ResourcePackHud.java'):
        value = value.replace('style.withColor(color&0xFFFFFF)', 'style.withColor(net.minecraft.text.TextColor.fromRgb(color&0xFFFFFF))')

    if name.endswith('ClientAccountMixin.java'):
        value = value.replace('socialInteractionsService', 'field_26902')

    if name.endswith('WorldEntryFavoriteMixin.java'):
        value = value.replace('((WorldFavorites.Entry) (selected))', '((WorldFavorites.Entry) (Object) (selected))')
        value = value.replace('LevelSummary;isUnavailable()Z', 'LevelSummary;isLocked()Z')

    if name.endswith('LoadingBrandMixin.java'):
        value = value.replace(
            '@Redirect(method="render",at=@At(value="INVOKE",target="Ljava/util/function/IntSupplier;getAsInt()I"),require=0)\n'
            '    private int bat$black(IntSupplier original){return 0xFF000000;}',
            '@Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screen/SplashScreen;fill(Lnet/minecraft/client/util/math/MatrixStack;IIIII)V"),require=0)\n'
            '    private void bat$black(net.minecraft.client.util.math.MatrixStack matrices,int x1,int y1,int x2,int y2,int color){net.minecraft.client.gui.DrawableHelper.fill(matrices,x1,y1,x2,y2,(color&0xFF000000));}',
        )

    if name.endswith('RainCustomizerMixin.java'):
        value = value.replace(
            'target="Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/util/Identifier;)V"),index=1',
            'target="Lnet/minecraft/client/texture/TextureManager;bindTexture(Lnet/minecraft/util/Identifier;)V"),index=0',
        )

    if name.endswith('ForgeClientHooks.java'):
        value = value.replace('net.minecraftforge.fmllegacy.network.', 'net.minecraftforge.fml.network.')
        value = value.replace('DrawSelectionEvent', 'DrawHighlightEvent')
        value = value.replace(
            '    private final List<java.util.function.Function<net.minecraft.client.item.TooltipData,net.minecraft.client.gui.tooltip.TooltipComponent>> tooltips=new ArrayList<>();\n',
            '',
        )
        value = value.replace(
            '    @Override public void tooltipComponent(java.util.function.Function<net.minecraft.client.item.TooltipData,net.minecraft.client.gui.tooltip.TooltipComponent> factory){tooltips.add(factory);}\n'
            '    public static net.minecraft.client.gui.tooltip.TooltipComponent createTooltip(net.minecraft.client.item.TooltipData data){\n'
            '        ForgeClientHooks hooks=active;if(hooks==null)return null;\n'
            '        for(var factory:hooks.tooltips){var component=factory.apply(data);if(component!=null)return component;}\n'
            '        return null;\n'
            '    }',
            '',
        )

    if name.endswith('ForgeWorldFrame.java'):
        value = value.replace('DrawSelectionEvent', 'DrawHighlightEvent')
        value = value.replace(
            '        stack.loadIdentity();\n'
            '        stack.peek().getModel().load(event.getMatrixStack().peek().getModel());\n'
            '        stack.peek().getNormal().load(event.getMatrixStack().peek().getNormal());\n'
            '        matrices=stack;camera=MinecraftClient.getInstance().gameRenderer.getCamera();frustum=null;\n'
            '        projection=com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix();delta=event.getPartialTicks();',
            '        matrices=event.getMatrixStack();camera=MinecraftClient.getInstance().gameRenderer.getCamera();frustum=null;\n'
            '        projection=event.getProjectionMatrix();delta=event.getPartialTicks();',
        )

    if name.endswith('ForgeClientResources.java'):
        value = (
            'package com.batclient.platform.forge;\n\n'
            'import com.batclient.platform.ClientResources;\n'
            'import net.minecraft.client.MinecraftClient;\n'
            'import net.minecraft.resource.ReloadableResourceManager;\n'
            'import net.minecraft.resource.SynchronousResourceReloader;\n'
            'import net.minecraft.util.Identifier;\n'
            'import net.minecraftforge.eventbus.api.EventPriority;\n'
            'import net.minecraftforge.eventbus.api.IEventBus;\n'
            'import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;\n\n'
            'public final class ForgeClientResources implements ClientResources {\n'
            '    private static IEventBus modBus;\n'
            '    public static void bootstrap(IEventBus bus){\n'
            '        if(modBus!=null&&modBus!=bus)throw new IllegalStateException("Bat resource bus changed");\n'
            '        modBus=bus;\n'
            '    }\n'
            '    public ForgeClientResources(){if(modBus==null)throw new IllegalStateException("Bootstrap Bat resources during mod construction");}\n'
            '    @Override public void playerLayers(PlayerLayers callback){\n'
            '        modBus.addListener(EventPriority.NORMAL,false,FMLClientSetupEvent.class,event->event.enqueueWork(()->{\n'
            '            for(net.minecraft.client.render.entity.PlayerEntityRenderer player:MinecraftClient.getInstance().getEntityRenderDispatcher().getSkinMap().values()){\n'
            '                callback.register(player,player::addFeature);\n'
            '            }\n'
            '        }));\n'
            '    }\n'
            '    @Override public void shaders(ShaderRegistration callback){}\n'
            '    @Override public void reload(Identifier id,Runnable callback){\n'
            '        modBus.addListener(EventPriority.NORMAL,false,FMLClientSetupEvent.class,event->event.enqueueWork(()->{\n'
            '            ((ReloadableResourceManager)MinecraftClient.getInstance().getResourceManager()).registerReloader((SynchronousResourceReloader)manager->callback.run());\n'
            '            callback.run();\n'
            '        }));\n'
            '    }\n'
            '}\n'
        )

    if name.endswith('ForgeFullSmoke.java'):
        value = value.replace(
            '            var preview=new com.batclient.ItemPreviews.Contents(net.minecraft.util.collection.DefaultedList.ofSize(27,net.minecraft.item.ItemStack.EMPTY),"Fixture",0xFF9973BC);\n'
            '            var tooltip=net.minecraft.client.gui.tooltip.TooltipComponent.of(preview);\n'
            '            if(tooltip==null||tooltip.getWidth(client.textRenderer)<=0)throw new AssertionError("Forge native tooltip factory not registered");\n'
            '            System.out.println("BAT_FORGE_NATIVE_TOOLTIP_FACTORY_OK: actual Forge registry and custom shulker component");\n',
            '',
        )

    return value
