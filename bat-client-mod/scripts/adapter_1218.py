"""Minecraft 1.21.8 screen and 2D matrix changes, layered over 1.21.5."""
import re
from pathlib import Path


def adapt(name, source):
    if name.endswith('/ForgeClientResources.java'):
        source = source.replace('net.minecraftforge.eventbus.api.IEventBus', 'net.minecraftforge.eventbus.api.bus.BusGroup')
        source = source.replace('IEventBus', 'BusGroup')
        source = source.replace('modBus.addListener((net.minecraftforge.client.event.EntityRenderersEvent.AddLayers event)->',
                                'net.minecraftforge.client.event.EntityRenderersEvent.AddLayers.getBus(modBus).addListener(event->')
        source = source.replace('modBus.addListener((RegisterClientReloadListenersEvent event)->',
                                'RegisterClientReloadListenersEvent.getBus(modBus).addListener(event->')
    if name.endswith('/ForgeClientHooks.java'):
        source = source.replace('MinecraftForge.EVENT_BUS.addListener(this::firstTick)',
                                'TickEvent.ClientTickEvent.Post.BUS.addListener(this::firstTick)')
        source = source.replace('MinecraftForge.EVENT_BUS.addListener(this::highlight)',
                                'RenderHighlightEvent.Block.BUS.addListener(this::highlight)')
        for event in ('TickEvent.ClientTickEvent.Post', 'ScreenEvent.Init.Pre',
                      'net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut',
                      'net.minecraftforge.event.entity.player.ItemTooltipEvent'):
            source = re.sub(r'MinecraftForge\.EVENT_BUS\.addListener\(\(' + re.escape(event) + r' (\w+)\)->',
                            lambda match: event + '.BUS.addListener(' + match[1] + '->', source)
        source = source.replace('private void highlight(RenderHighlightEvent.Block event){\n        if(outlines.isEmpty())return;',
                                'private boolean highlight(RenderHighlightEvent.Block event){\n        if(outlines.isEmpty())return false;')
        source = source.replace('if(!vanilla)event.setCanceled(true);', 'return !vanilla;')
    if name.endswith('/GlintColorMixin.java'):
        return (Path(__file__).parent / 'templates-1218/GlintColorMixin.java').read_text(encoding='utf-8')
    if name.endswith('/VisualModules.java'):
        start = source.index('    public static ShaderProgram glint(')
        source = source[:start] + '''    public static org.joml.Vector4fc glintTint(org.joml.Vector4fc vanilla){
        var mod=settings("glintcolor");
        if(!HudConfig.enabled(mod))return vanilla;
        int color=HudRenderer.parseColor(HudConfig.str(mod,"color","#8B5CFFFF"),0xFF8B5CFF);
        float intensity=((color>>>24)&255)/255f*number(mod,"strength",1,0,2);
        return new org.joml.Vector4f(vanilla.x()*((color>>16)&255)/255f*intensity,
                vanilla.y()*((color>>8)&255)/255f*intensity,
                vanilla.z()*(color&255)/255f*intensity, vanilla.w());
    }
}
'''
    if name.endswith('/PipelinePrograms.java'):
        start = source.index('    private static final RenderPipeline GLINT=')
        end = source.index('    public static ShaderProgram program', start)
        source = source[:start] + '    private static final RenderPipeline GLINT=RenderPipelines.GLINT;\n' + source[end:]
    if name.endswith('/GlFramebuffer.java'):
        source = source.replace('getFramebufferManager()', 'getBufferManager()')
    if name.endswith('/SmoothChunkRebuildMixin.java'):
        source = source.replace('chunk.scheduleRebuild(builder,region)', 'chunk.scheduleRebuild(region)')
    if name.endswith('/VisualLayers.java'):
        source = source.replace('@Override public Framebuffer getTarget()', 'public Framebuffer getTarget()')
        source = source.replace('@Override public RenderPipeline getPipeline()', 'public RenderPipeline getPipeline()')
        start = source.index('   if(!(device.precompilePipeline')
        end = source.index('    pass.setVertexBuffer', start)
        source = source[:start] + '''   var transforms=RenderSystem.getDynamicUniforms().write(RenderSystem.getModelViewMatrix(),
       new org.joml.Vector4f(1,1,1,1),RenderSystem.getModelOffset(),RenderSystem.getTextureMatrix(),width);
   var fb=client.getFramebuffer();
   try(var pass=device.createCommandEncoder().createRenderPass(()->"Bat world overlays",
       fb.getColorAttachmentView(),OptionalInt.empty(),fb.getDepthAttachmentView(),OptionalDouble.empty())){
    pass.setPipeline(pipeline);
    var scissor=RenderSystem.getScissorStateForRenderTypeDraws();
    if(scissor.method_72091())pass.enableScissor(scissor.method_72092(),scissor.method_72093(),
       scissor.method_72094(),scissor.method_72095());
    RenderSystem.bindDefaultUniforms(pass);
    pass.setUniform("DynamicTransforms",transforms);
''' + source[end:]
        source = source.replace('pass.drawIndexed(0,params.indexCount())', 'pass.drawIndexed(0,0,params.indexCount(),1)')
        source = source.replace('  float oldWidth=RenderSystem.getShaderLineWidth();\n', '')
        source = source.replace('  }finally{RenderSystem.lineWidth(oldWidth);}', '  }')
    if name.endswith('/MenuRegressionChecks.java'):
        source = source.replace('                ctx.draw();\n', '')
        source = source.replace('var ctx = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());',
                                'var state = new net.minecraft.client.gui.render.state.GuiRenderState();\n            var ctx = new DrawContext(client, state);')
        source = source.replace('check(GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM) == BatClientShaders.bgraPositionTextureShader.getGlRef(), "native web pipeline did not bind its program");',
                                '''var queued = new java.util.concurrent.atomic.AtomicBoolean();
            state.forEachSimpleElement((element, depth) -> {
                if (element.pipeline() == com.batclient.platform.mc1215.GpuWebQuad.PIPELINE) queued.set(true);
            }, net.minecraft.client.gui.render.state.GuiRenderState.LayerFilter.ALL);
            check(queued.get(), "web pipeline was not queued");''')
    if name.endswith('/GpuWebQuad.java'):
        return (Path(__file__).parent / 'templates-1218/GpuWebQuad.java').read_text(encoding='utf-8')
    if name.endswith('/WebWindow.java'):
        source = source.replace('    private com.mojang.blaze3d.textures.GpuTexture gpuTexture;\n', '')
        start = source.index('        if(gpuTexture!=null)gpuTexture.close();')
        end = source.index('\n', source.index('        texture=gl.getGlId();', start))
        source = source[:start] + '        texture=gpuQuad.resize(width,height);' + source[end:]
        source = source.replace('gpuQuad.draw(context,gpuTexture,', 'gpuQuad.draw(context,')
        source = source.replace('gpuQuad.close();if(gpuTexture!=null){gpuTexture.close();gpuTexture=null;}', 'gpuQuad.close();')
        source = source.replace('GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,', 'org.lwjgl.opengl.GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE,')
    if name.endswith('/WebScreen.java'):
        start = source.index('\t\t\tcase "savequit"')
        end = source.index('\t\t\tcase "serverlist"', start)
        source = source[:start] + '''			case "savequit" -> client.execute(() -> {
				if (client.world != null) {
					net.minecraft.client.gui.screen.GameMenuScreen.disconnect(client,
						net.minecraft.client.world.ClientWorld.QUITTING_MULTIPLAYER_TEXT);
				}
			});
''' + source[end:]
        blur = '''			if (mode != Mode.MENU && client != null && client.world != null) {
				applyBlur();
			}
'''
        if source.count(blur) != 1:
            raise ValueError('Expected one inline WebScreen blur')
        source = source.replace(blur, '')
        anchor = '\t@Override\n\tpublic void render(DrawContext context,'
        source = source.replace(anchor, '''	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		if (mode != Mode.MENU && client != null && client.world != null && client.getOverlay() == null) {
			applyBlur(context);
		}
	}

''' + anchor)
    if name.endswith('/GuiCompat.java'):
        source = source.replace('import net.minecraft.client.render.RenderLayer;',
                                'import net.minecraft.client.gl.RenderPipelines;')
        source = source.replace('RenderLayer::getGuiTextured', 'RenderPipelines.GUI_TEXTURED')
        source = source.replace('id,x,y,w,h,u,v,rw,rh,tw,th,', 'id,x,y,u,v,w,h,rw,rh,tw,th,')
        source = source.replace('RenderSystem.getShaderColor()', 'getShaderColor()')
        source = source.replace(' private GuiCompat(){}', ''' private static final float[] COLOR = {1, 1, 1, 1};
 private GuiCompat(){}
 public static float[] getShaderColor(){return COLOR;}
 public static void setShaderColor(float r,float g,float b,float a){COLOR[0]=r;COLOR[1]=g;COLOR[2]=b;COLOR[3]=a;}''')
        source = re.sub(r'c\.getMatrices\(\)\.push\(\);try\{c\.getMatrices\(\)\.translate\(0,0,z\);(c\.drawSpriteStretched[^;]+;)\}finally\{c\.getMatrices\(\)\.pop\(\);\}',
                        r'if(z>0)c.createNewRootLayer();\1', source)
    if name.endswith(('/AdventureHud.java', '/HudRenderer.java', '/MinimapHud.java', '/NativeChat.java')):
        source = re.sub(r'(?:com\.mojang\.blaze3d\.systems\.)?RenderSystem\.((?:get|set)ShaderColor)\(',
                        r'com.batclient.platform.mc1214.GuiCompat.\1(', source)
    if name.endswith('/MinimapHud.java'):
        source = source.replace('ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees((client.player==null?0:client.player.getYaw())+180));',
                                'ctx.getMatrices().rotate((float)Math.toRadians((client.player==null?0:client.player.getYaw())+180));')
    if name.endswith('/HudRenderer.java'):
        source = source.replace('ctx.draw();', '')
        source = source.replace('com.batclient.platform.mc1214.GuiCompat.drawSprite(ctx,0, y + (rowH - 18) / 2, 0, 18, 18, c.getStatusEffectSpriteManager().getSprite(effect.getEffectType()));',
                                'com.batclient.platform.mc1214.GuiCompat.drawGuiTexture(ctx, net.minecraft.client.gui.hud.InGameHud.getEffectTexture(effect.getEffectType()), 0, y + (rowH - 18) / 2, 18, 18);')
    if name.endswith('/LoadingBrand.java'):
        source = source.replace('context.fill(RenderLayer.getGuiOverlay(),', 'context.fill(')
    if name.endswith('/LoadingBrandMixin.java'):
        source = source.replace('Ljava/util/function/Function;', 'Lcom/mojang/blaze3d/pipeline/RenderPipeline;')
        source = source.replace('java.util.function.Function<Identifier,net.minecraft.client.render.RenderLayer>',
                                'com.mojang.blaze3d.pipeline.RenderPipeline')
    if name.endswith('/MenuBranding.java'):
        source = source.replace('ctx.draw();', '')
    if name.endswith('/HitColorMixin.java'):
        source = source.replace('setupOverlayColor(bat$hitTexture.getGlTexture())',
                                'setupOverlayColor(bat$hitTexture.getGlTextureView())')
    if name.endswith('/CustomCrosshairMixin.java'):
        source = source.replace('Ljava/util/function/Function;', 'Lcom/mojang/blaze3d/pipeline/RenderPipeline;')
        source = source.replace('java.util.function.Function<Identifier,net.minecraft.client.render.RenderLayer>',
                                'com.mojang.blaze3d.pipeline.RenderPipeline')
        start = source.index('        int srcRgb')
        source = source[:start] + '        HudRenderer.drawCustomCrosshair(ctx);\n    }\n}\n'
    if name.endswith('/InventoryNametagMixin.java'):
        start = source.index('    @Unique private static float bat$portraitWidth;')
        body = source.index('        MinecraftClient client =', start)
        source = source[:start] + '''    @Inject(method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIFLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/entity/LivingEntity;)V",
            at = @At("RETURN"))
    private static void bat$portraitName(DrawContext ctx, int left, int top, int right, int bottom,
                                         float size, Vector3f offset, Quaternionf rotation,
                                         Quaternionf cameraRotation, LivingEntity entity, CallbackInfo ci) {
''' + source[body:]
        start = source.index('        float scale =')
        end = source.index('        if (HudConfig.bool', start)
        source = source[:start] + '''        float scale = Math.min(Math.max(.5f, HudConfig.setNumber(NativeNametags.settings(), "scale", 1)) * .025f * size,
                Math.max(1, right - left - 2) / (float)Math.max(1, width));
        var head = rotation.transform(new Vector3f(0, entity.getHeight() + .12f, 0)).add(offset);
        var matrices = ctx.getMatrices();
        ctx.createNewRootLayer();
        matrices.pushMatrix();
        try {
            matrices.translate((left + right) / 2f + head.x * size - width * scale / 2f,
                (top + bottom) / 2f + head.y * size - height * scale);
            matrices.scale(scale, scale);
''' + source[end:]
        source = source.replace('        ctx.draw();\n        ctx.getMatrices().pop();',
                                '        } finally { matrices.popMatrix(); }')
    if name.endswith('/NativeChat.java'):
        source = source.replace('if(line==null)return ctx.drawTextWithShadow(font,text,x,y,color);',
                                'if(line==null){ctx.drawTextWithShadow(font,text,x,y,color);return x+font.getWidth(text);}')
        source = source.replace('return ctx.drawTextWithShadow(font,text,x,y,animatedColor);',
                                'ctx.drawTextWithShadow(font,text,x,y,animatedColor);return x+font.getWidth(text);')
    if name.endswith('/ChatHudMixin.java'):
        source = source.replace('@Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow',
                                '@Redirect(method="method_71991",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow')
        source = source.replace('Lnet/minecraft/text/OrderedText;III)I', 'Lnet/minecraft/text/OrderedText;III)V')
        source = source.replace('private int bat$draw(', 'private void bat$draw(')
        source = source.replace('return bat$state().draw(', 'bat$state().draw(')
    if name.endswith('/ServerEntryExtrasMixin.java'):
        source = source.replace('Ljava/lang/String;III)I', 'Ljava/lang/String;III)V')
    if name.endswith('/NativeNametagStyleMixin.java'):
        source = source.replace('Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I',
                                'Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V')
    if name.endswith(('/ChatHudMixin.java', '/ChatScreenAnimationMixin.java',
                      '/InventoryOpeningMixin.java', '/WorldEntryFavoriteMixin.java',
                      '/ServerEntryExtrasMixin.java', '/ResourcePackHud.java',
                      '/HudRenderer.java', '/MinimapHud.java', '/NativeChat.java',
                      '/MenuBranding.java')):
        source = source.replace('ctx.getMatrices().push()', 'ctx.getMatrices().pushMatrix()')
        source = source.replace('ctx.getMatrices().pop()', 'ctx.getMatrices().popMatrix()')
        source = re.sub(r'(ctx\.getMatrices\(\)\.translate\([^;\n]+),\s*0\);', r'\1);', source)
        source = re.sub(r'(ctx\.getMatrices\(\)\.translate\([^;\n]+),\s*(?:10|200)\);',
                        r'ctx.createNewRootLayer();\1);', source)
        source = re.sub(r'(ctx\.getMatrices\(\)\.scale\([^;\n]+),\s*1f?\);', r'\1);', source)
    return source
