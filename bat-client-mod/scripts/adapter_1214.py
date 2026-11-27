"""Actual 1.21.4 API adaptation; compilation/native gates remain mandatory."""
import re

def adapt(name,value):
    if name.endswith('ServerEntryExtrasMixin.java'):
        value=value.replace('drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)I',
            'drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)I')
    value=value.replace('EntityAttributes.GENERIC_','EntityAttributes.')
    value=value.replace('.isFallFlying()', '.isGliding()')
    value=value.replace('applyBlur(delta)', 'applyBlur()')
    value=value.replace('CreateWorldScreen.create(client, this)', 'CreateWorldScreen.show(client, this)')
    value=value.replace('RenderSystem.setShader(() -> shader)', 'RenderSystem.setShader(shader)').replace('RenderSystem.setShader(() -> previousShader)', 'RenderSystem.setShader(previousShader)')
    value=value.replace('.drawItemInSlot(', '.drawStackOverlay(')
    value=value.replace('WorldRenderer.drawBox(', 'net.minecraft.client.render.VertexRendering.drawBox(')
    value=value.replace('ctx.world().getTopY()', '(ctx.world().getTopYInclusive()+1)')
    value=value.replace('getCooldownProgress(Items.SHIELD,', 'getCooldownProgress(Items.SHIELD.getDefaultStack(),')
    value=value.replace('image::getColor', 'image::getColorArgb')
    value=re.sub(r'\b(ctx|context)\.drawTexture\(', r'com.batclient.platform.mc1214.GuiCompat.drawTexture(\1,',value)
    value=re.sub(r'\b(ctx|context)\.drawGuiTexture\(', r'com.batclient.platform.mc1214.GuiCompat.drawGuiTexture(\1,',value)
    value=value.replace('ctx.drawSprite(', 'com.batclient.platform.mc1214.GuiCompat.drawSprite(ctx,')
    if name.endswith('/HudRenderer.java'):
        # GUI layers own blending. Tint each corner once with the plate color.
        start = value.index('\t\tctx.setShaderColor(', value.index('static void fillRounded('))
        end = value.index('\n\t}\n', start)
        corners = re.findall(r'\t\t\t(com\.batclient\.platform\.mc1214\.GuiCompat\.drawTexture\(ctx,roundedMask,[^\n]+)\);', value[start:end])
        if len(corners) != 4:
            raise ValueError('Expected four HUD background corner draws')
        value = value[:start] + '\n'.join('\t\t' + corner + ', color);' for corner in corners) + value[end:]
    value=value.replace('ctx.setShaderColor(', 'com.mojang.blaze3d.systems.RenderSystem.setShaderColor(')
    value=re.sub(r'([A-Za-z_$][\w$]*(?:\.[\w$]+(?:\(\))?)*)\.registerDynamicTexture\(',r'com.batclient.platform.mc1214.GuiCompat.registerTexture(\1,',value)
    value=value.replace('image.setColor(', 'com.batclient.platform.mc1214.GuiCompat.setAbgr(image,')
    value=value.replace('liveTexture.getImage().setColor(', 'com.batclient.platform.mc1214.GuiCompat.setAbgr(liveTexture.getImage(),')
    value=value.replace('previewTexture.getImage().setColor(', 'com.batclient.platform.mc1214.GuiCompat.setAbgr(previewTexture.getImage(),')
    value=value.replace('original.getColor(', 'com.batclient.platform.mc1214.GuiCompat.getAbgr(original,')
    if name.endswith('ItemPreviews.java'):
        value=value.replace('a.getSlotType()', 'stack.get(net.minecraft.component.DataComponentTypes.EQUIPPABLE).slot()').replace('armor.getSlotType()', 'stack.get(net.minecraft.component.DataComponentTypes.EQUIPPABLE).slot()')
    if name.endswith('VisualLayers.java'):
        value=value.replace('new ShaderProgram(GameRenderer::getPositionColorProgram)', 'new ShaderProgram(net.minecraft.client.gl.ShaderProgramKeys.POSITION_COLOR)')
    value=value.replace('.getScrollAmount()', '.getScrollY()').replace('.setScrollAmount(', '.setScrollY(')
    if name.endswith('ItemPreviews.java'):
        value=value.replace('public int getHeight()', 'public int getHeight(TextRenderer font)')
        value=value.replace('instanceof ArmorItem a)', 'instanceof ArmorItem a&&stack.contains(net.minecraft.component.DataComponentTypes.EQUIPPABLE))')
        value=value.replace('instanceof ArmorItem armor)', 'instanceof ArmorItem armor&&stack.contains(net.minecraft.component.DataComponentTypes.EQUIPPABLE))')
    if name.endswith('NativeChat.java'):
        value=value.replace('HudConfig.bool(heads,"hat",true),false)', 'HudConfig.bool(heads,"hat",true),false,com.batclient.platform.mc1214.GuiCompat.tint())')
    if name.endswith('HitColorMixin.java'):
        value=value.replace('image.upload(0,0,0,0,0,16,16,false,true,false,false)', 'image.upload(0,0,0,0,0,16,16,false)')
    if name.endswith('SkinLayerPixelsMixin.java'):
        value=value.replace('PlayerSkinTexture','NativeImageBackedTexture').replace('method="uploadTexture"','method="<init>(Lnet/minecraft/client/texture/NativeImage;)V"').replace('at=@At("HEAD")','at=@At("RETURN")')
    if name.endswith('ClientResources.java'):
        value=value.replace('FeatureRenderer<net.minecraft.client.network.AbstractClientPlayerEntity,', 'FeatureRenderer<net.minecraft.client.render.entity.state.PlayerEntityRenderState,')
        value=value.replace('PlayerEntityModel<net.minecraft.client.network.AbstractClientPlayerEntity>', 'PlayerEntityModel')
    if name.endswith('FabricClientResources.java'):
        value=value.replace('import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;', '')
        value=value.replace('CoreShaderRegistrationCallback.EVENT.register(context->callback.register(context::register));', 'com.batclient.platform.mc1214.ShaderRegistry.register(callback);')
    if name.endswith('SkinLayers3D.java'):
        value=value.replace('FeatureRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>>', 'FeatureRenderer<net.minecraft.client.render.entity.state.PlayerEntityRenderState,PlayerEntityModel>')
        value=value.replace('AbstractClientPlayerEntity player,float limbAngle,float limbDistance,float delta,float animation,float yaw,float pitch)', 'net.minecraft.client.render.entity.state.PlayerEntityRenderState state,float limbAngle,float limbDistance)')
        value=value.replace('var mod=VisualModules.settings("skinlayers3d");var c=MinecraftClient.getInstance();', 'var mod=VisualModules.settings("skinlayers3d");var c=MinecraftClient.getInstance();if(c.world==null||!(c.world.getEntityById(state.id) instanceof AbstractClientPlayerEntity player))return;')
        value=value.replace('getContextModel().child', 'state.baby').replace('LivingEntityRenderer.getOverlay(player,0)', 'LivingEntityRenderer.getOverlay(state,0)')
    if name.endswith(('Fabric1214Smoke.java','ForgeFullSmoke.java')):
        value=value.replace('new net.minecraft.world.GameRules()', 'new net.minecraft.world.GameRules(net.minecraft.resource.DataConfiguration.SAFE_MODE.enabledFeatures())')
        value=value.replace('registry.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).get(net.minecraft.world.gen.WorldPresets.FLAT)', 'registry.getOrThrow(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(net.minecraft.world.gen.WorldPresets.FLAT).value()')
    if name.endswith('HealthIndicatorMixin.java'):
        value=value.replace('@Shadow protected abstract boolean hasLabel(Entity entity);', '')
        value=value.replace('@Inject(method="render",', '@Inject(method="render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",')
        value=value.replace('Entity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci)', 'net.minecraft.client.render.entity.state.EntityRenderState state,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci)')
        value=value.replace('if(entity instanceof LivingEntity living)HealthIndicator.render(living,hasLabel(entity),delta,', 'var entity=com.batclient.platform.mc1214.EntityStateAccess.entity(state);if(entity instanceof LivingEntity living)HealthIndicator.render(living,state.displayName!=null,((com.batclient.platform.mc1214.EntityStateAccess)state).bat$tickDelta(),')
    if name.endswith('OwnNametagMixin.java'):
        value=value.replace('@Mixin(PlayerEntityRenderer.class)', '@Mixin(net.minecraft.client.render.entity.EntityRenderer.class)')
        value=value.replace('render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V', 'render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V')
        value=value.replace('AbstractClientPlayerEntity player, float yaw, float delta, MatrixStack matrices,', 'net.minecraft.client.render.entity.state.EntityRenderState state, MatrixStack matrices,')
        value=value.replace('MinecraftClient client = MinecraftClient.getInstance();', 'MinecraftClient client = MinecraftClient.getInstance();if(!(com.batclient.platform.mc1214.EntityStateAccess.entity(state) instanceof AbstractClientPlayerEntity player))return;float delta=((com.batclient.platform.mc1214.EntityStateAccess)state).bat$tickDelta();')
    if name.endswith('PlayerNametagMixin.java'):
        value=value.replace('renderLabelIfPresent(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IF)V', 'renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V')
        value=value.replace('AbstractClientPlayerEntity player, Text original', 'net.minecraft.client.render.entity.state.PlayerEntityRenderState state, Text original').replace('int light, float delta)', 'int light)')
        value=value.replace('return NativeNametags.enabled()', 'var player=com.batclient.platform.mc1214.EntityStateAccess.entity(state);return player!=null && NativeNametags.enabled()')
    if name.endswith('NativeNametagStyleMixin.java'):
        value=value.replace('method = "renderLabelIfPresent"', 'method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"')
        value=value.replace('Entity player, Text text', 'net.minecraft.client.render.entity.state.EntityRenderState state, Text text').replace('int light, float delta)', 'int light)')
        value=value.replace('if (!(player instanceof PlayerEntity)', 'if (!(com.batclient.platform.mc1214.EntityStateAccess.entity(state) instanceof PlayerEntity)')
    if name.endswith('TntCountdownMixin.java'):
        value=value.replace('render(Lnet/minecraft/entity/TntEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V', 'render(Lnet/minecraft/client/render/entity/state/TntEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V')
        value=value.replace('TntEntity entity,float yaw,float delta,MatrixStack matrices', 'net.minecraft.client.render.entity.state.TntEntityRenderState state,MatrixStack matrices')
        value=value.replace('{TntCountdown.render(entity,delta,matrices,vertices);}', '{if(com.batclient.platform.mc1214.EntityStateAccess.entity(state) instanceof TntEntity entity)TntCountdown.render(entity,((com.batclient.platform.mc1214.EntityStateAccess)state).bat$tickDelta(),matrices,vertices);}')
    if name.endswith('GlintColorMixin.java'):
        value='package com.batclient.mixin;\nimport com.batclient.VisualModules;\nimport net.minecraft.client.gl.*;\nimport org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.injection.*;\nimport org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;\n@Mixin(ShaderLoader.class)\npublic class GlintColorMixin {\n @Inject(method="getOrCreateProgram",at=@At("RETURN"),cancellable=true)\n private void bat$glint(ShaderProgramKey key,CallbackInfoReturnable<ShaderProgram> ci){String path=key.configId().getPath();if(path.startsWith("core/rendertype_")&&path.contains("glint"))ci.setReturnValue(VisualModules.glint(ci.getReturnValue()));}\n}\n'
    if name.endswith('CustomCrosshairMixin.java'):
        value=value.replace('drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V', 'drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V')
        value=value.replace('DrawContext ctx, Identifier sprite,', 'DrawContext ctx, java.util.function.Function<Identifier,net.minecraft.client.render.RenderLayer> layer, Identifier sprite,')
        value=value.replace('com.batclient.platform.mc1214.GuiCompat.drawGuiTexture(ctx,sprite, x, y, width, height)', 'ctx.drawGuiTexture(layer,sprite,x,y,width,height)')
    if name.endswith('ForgeClientResources.java'):
        value=value.replace('import net.minecraftforge.client.event.RegisterShadersEvent;', '')
        start=value.index('    @Override public void shaders(');end=value.index('    @Override public void reload(',start)
        value=value[:start]+'    @Override public void shaders(ShaderRegistration callback){com.batclient.platform.mc1214.ShaderRegistry.register(callback); }\n'+value[end:]
    if name.endswith('ChunkMeshExecutorMixin.java'):
        value=value.replace('private Executor executor;', 'private net.minecraft.util.thread.NameableExecutor executor;')
        value=value.replace('new MeshWorkExecutor(executor)', 'new MeshWorkExecutor(executor.named("bat-chunk-mesh"))')
    if name.endswith('ItemPhysicsMixin.java'):
        value=value.replace('render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V', 'render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V')
        value=value.replace('ItemEntity entity,float yaw,float delta,MatrixStack original', 'net.minecraft.client.render.entity.state.ItemEntityRenderState state,MatrixStack original')
        value=value.replace('{matrices.translate(x,VisualModules.itemHeight(entity,y),z);}', '{var entity=com.batclient.platform.mc1214.EntityStateAccess.entity(state);matrices.translate(x,entity instanceof ItemEntity item?VisualModules.itemHeight(item,y):y,z);}')
        value=value.replace('{VisualModules.rotateItem(matrices,rotation,entity,delta);}', '{var entity=com.batclient.platform.mc1214.EntityStateAccess.entity(state);if(entity instanceof ItemEntity item)VisualModules.rotateItem(matrices,rotation,item,((com.batclient.platform.mc1214.EntityStateAccess)state).bat$tickDelta());else matrices.multiply(rotation);}')
    if name.endswith('RainCustomizerMixin.java'):
        value='package com.batclient.mixin;\nimport com.batclient.*;\nimport org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.injection.*;\n@Mixin(net.minecraft.client.render.WeatherRendering.class)\npublic abstract class RainCustomizerMixin {\n @ModifyArg(method="renderPrecipitation(Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/util/math/Vec3d;IFLjava/util/List;Ljava/util/List;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WeatherRendering;renderPieces(Lnet/minecraft/client/render/VertexConsumer;Ljava/util/List;Lnet/minecraft/util/math/Vec3d;FIF)V",ordinal=0),index=5)\n private float bat$rain(float alpha){var mod=VisualModules.settings("raincustomizer");return HudConfig.enabled(mod)?alpha*VisualModules.number(mod,"opacity",40,0,100)/100:alpha;}\n @ModifyArg(method="renderPrecipitation(Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/util/math/Vec3d;IFLjava/util/List;Ljava/util/List;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WeatherRendering;renderPieces(Lnet/minecraft/client/render/VertexConsumer;Ljava/util/List;Lnet/minecraft/util/math/Vec3d;FIF)V",ordinal=1),index=5)\n private float bat$snow(float alpha){var mod=VisualModules.settings("raincustomizer");return HudConfig.enabled(mod)&&HudConfig.bool(mod,"snow",false)?alpha*VisualModules.number(mod,"opacity",40,0,100)/100:alpha;}\n}\n'
    if name.endswith('ForgeClientHooks.java'):
        value=value.replace('import net.minecraftforge.client.event.RenderLevelStageEvent;', '')
        value=value.replace('        MinecraftForge.EVENT_BUS.addListener(this::worldStage);', '')
        start=value.index('    private void worldStage(');end=value.index('    private void highlight(',start)
        value=value[:start]+'    public static void fireWorldEntities(net.minecraft.client.util.math.MatrixStack matrices,net.minecraft.client.render.VertexConsumerProvider buffers,net.minecraft.client.render.Camera camera,float delta,net.minecraft.client.render.Frustum frustum,org.joml.Matrix4f projection){\n        var hooks=active;if(hooks==null||hooks.stopping||hooks.entities.isEmpty())return;\n        hooks.worldFrame.stage(matrices,buffers,camera,delta,frustum,projection);for(var callback:hooks.entities)callback.accept(hooks.worldFrame);\n    }\n'+value[end:]
    if name.endswith('ForgeWorldFrame.java'):
        start=value.index('    void stage(');end=value.index('    void highlight(',start)
        value=value[:start]+'    void stage(MatrixStack matrices,VertexConsumerProvider buffers,Camera camera,float delta,Frustum frustum,Matrix4f projection){\n        this.matrices=matrices;this.buffers=buffers;this.camera=camera;this.delta=delta;this.frustum=frustum;this.projection=projection;\n    }\n'+value[end:]
    if name.endswith('GameRendererZoomMixin.java'):
        value=value.replace('CallbackInfoReturnable<Double>', 'CallbackInfoReturnable<Float>')
        value=value.replace('cir.setReturnValue(ClientMods.zoomedFov(cir.getReturnValue()))', 'cir.setReturnValue((float)ClientMods.zoomedFov(cir.getReturnValue()))')
    if name.endswith('LoadingBrandMixin.java'):
        value=value.replace('bat$init(MinecraftClient client,CallbackInfo ci){LoadingBrand.init(client);}', 'bat$init(net.minecraft.client.texture.TextureManager manager,CallbackInfo ci){LoadingBrand.init(manager);}')
        value=value.replace('drawTexture(Lnet/minecraft/util/Identifier;IIIIFFIIII)V', 'drawTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V')
        value=value.replace('DrawContext context,Identifier vanilla,int x,int y,int width,int height,float u,float v,int regionWidth,int regionHeight,int textureWidth,int textureHeight)', 'DrawContext context,java.util.function.Function<Identifier,net.minecraft.client.render.RenderLayer> layer,Identifier vanilla,int x,int y,float u,float v,int width,int height,int regionWidth,int regionHeight,int textureWidth,int textureHeight,int color)')
        value=value.replace('LoadingBrand.draw(context,x,y,width,height)', 'LoadingBrand.draw(context,x,y,width,height,color)')
    if name.endswith('LoadingBrand.java'):
        value=value.replace('init(MinecraftClient client)', 'init(net.minecraft.client.texture.TextureManager manager)')
        value=value.replace('client.getTextureManager().registerTexture(TEXTURE,texture)', 'manager.registerTexture(TEXTURE,texture)')
        value=value.replace('init(MinecraftClient.getInstance())', 'init(MinecraftClient.getInstance().getTextureManager())')
        value=value.replace('int halfWidth,int originalHeight)', 'int halfWidth,int originalHeight,int color)')
        value=value.replace('textureWidth,textureHeight,textureWidth,textureHeight);', 'textureWidth,textureHeight,textureWidth,textureHeight,color);')
    return value
