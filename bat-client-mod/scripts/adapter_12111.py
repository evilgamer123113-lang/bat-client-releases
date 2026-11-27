"""1.21.11-specific adaptations after the shared queued-GUI port."""
import re
from pathlib import Path


def adapt(name, source):
    if name.endswith(('/PlayerNametagMixin.java', '/OwnNametagMixin.java')):
        signature = '''    @org.spongepowered.asm.mixin.injection.Inject(
        method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
        at = @At("RETURN"))
    private void bat$updateNametag(net.minecraft.entity.PlayerLikeEntity player,
            net.minecraft.client.render.entity.state.PlayerEntityRenderState state,
            float delta, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
'''
        if name.endswith('/PlayerNametagMixin.java'):
            body = '''        if (player == net.minecraft.client.MinecraftClient.getInstance().player || state.displayName == null) return;
        if (NativeNametags.enabled() && HudConfig.bool(NativeNametags.settings(), "logo", true)
                && ClientPresence.isBatPlayer(player.getUuid())) {
            state.displayName = NativeNametags.withLogo(state.displayName);
        }
'''
        else:
            source = source.replace('@Mixin(net.minecraft.client.render.entity.EntityRenderer.class)',
                                    '@Mixin(PlayerEntityRenderer.class)')
            body = '''        var client = MinecraftClient.getInstance();
        if (player != client.player || client.options.getPerspective().isFirstPerson()
                || !MinecraftClient.isHudEnabled() || player.isInvisible() || client.currentScreen != null
                || !NativeNametags.enabled() || !HudConfig.bool(NativeNametags.settings(), "showOwn", true)) return;
        var attachment = player.getAttachments().getPointNullable(EntityAttachmentType.NAME_TAG, 0, player.getYaw(delta));
        if (attachment != null) {
            state.nameLabelPos = attachment;
            state.displayName = NativeNametags.ownName(player.getName());
        }
'''
        start = source.index('    @', source.index('public abstract class'))
        source = source[:start] + signature + body + '    }\n}\n'
    if name.endswith('/WorldScreenBrandMixin.java'):
        start = source.index('public abstract class WorldScreenBrandMixin')
        source = source[:start] + '''public abstract class WorldScreenBrandMixin extends net.minecraft.client.gui.screen.Screen {
    protected WorldScreenBrandMixin(net.minecraft.text.Text title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void bat$brand(CallbackInfo ci) {
        addDrawable((context, mouseX, mouseY, delta) -> com.batclient.web.MenuBranding.draw(context));
    }
}
'''
    if name.endswith('/MultiplayerExtrasMixin.java'):
        start = source.index('    @Inject(method = "render"')
        source = source[:start] + '}\n'
        source = source.replace('        addDrawableChild(bat$search);', '''        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.widget.TextWidget label
                    && label.getMessage().equals(title)) label.setY(8);
        }
        addDrawableChild(bat$search);
        addDrawable((context, mouseX, mouseY, delta) -> MenuBranding.draw(context));''')
    if name.endswith('/InventoryNametagMixin.java'):
        start = source.index('    @Inject(method = "drawEntity')
        body = source.index('        MinecraftClient client =', start)
        source = source[:start] + '''    @org.spongepowered.asm.mixin.injection.Redirect(
        method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;addEntity(Lnet/minecraft/client/render/entity/state/EntityRenderState;FLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;IIII)V"))
    private static void bat$portraitName(DrawContext ctx,
            net.minecraft.client.render.entity.state.EntityRenderState state, float size,
            Vector3f offset, Quaternionf rotation, Quaternionf cameraRotation,
            int left, int top, int right, int bottom,
            DrawContext originalContext, int originalLeft, int originalTop, int originalRight,
            int originalBottom, int originalSize, float originalOffset, float mouseX, float mouseY,
            LivingEntity entity) {
        ctx.addEntity(state, size, offset, rotation, cameraRotation, left, top, right, bottom);
''' + source[body:]
    if name.endswith('/ChatCopyMixin.java'):
        source = source.replace('bat$copy(double x,double y,int button,',
                                'bat$copy(net.minecraft.client.gui.Click click,boolean doubleClick,')
        source = source.replace('        if(button!=1', '        double x=click.x(), y=click.y(); int button=click.button();\n        if(button!=1')
    if name.endswith('/InventoryOpeningMixin.java'):
        source = source.replace('method={"mouseClicked","mouseReleased"},at=@At("HEAD"),argsOnly=true,ordinal=1',
                                'method={"mouseClicked","mouseReleased","mouseDragged"},at=@At("HEAD"),argsOnly=true,require=0')
        source = source.replace('private double bat$inputY(double y){return y-InventoryOpeningAnimation.inputOffset();}',
                                '''private net.minecraft.client.gui.Click bat$inputY(net.minecraft.client.gui.Click click){
        return new net.minecraft.client.gui.Click(click.x(),click.y()-InventoryOpeningAnimation.inputOffset(),click.buttonInfo());
    }''')
        source = source.replace('method={"mouseDragged","mouseScrolled"}', 'method="mouseScrolled"')
    if name.endswith('/InventoryDragAnimationMixin.java'):
        source = source.replace('argsOnly=true,ordinal=1', 'argsOnly=true')
        source = source.replace('private double bat$survivalDragY(double y){return (Object)this instanceof InventoryScreen?y-InventoryOpeningAnimation.inputOffset():y;}',
                                '''private net.minecraft.client.gui.Click bat$survivalDragY(net.minecraft.client.gui.Click click){
        if(!((Object)this instanceof InventoryScreen))return click;
        return new net.minecraft.client.gui.Click(click.x(),click.y()-InventoryOpeningAnimation.inputOffset(),click.buttonInfo());
    }''')
    if name.endswith('/ServerEntryExtrasMixin.java'):
        source = source.replace('DrawContext ctx, int index, int y, int x, int width, int height, int mx, int my,',
                                'DrawContext ctx, int mx, int my,')
        source = source.replace('        bat$x = x; bat$y = y;',
                                '''        var entry = (MultiplayerServerListWidget.ServerEntry)(Object)this;
        bat$x = entry.getContentX(); bat$y = entry.getContentY();''')
        source = re.sub(r'    @ModifyVariable\(method = "render", at = @At\(value = "INVOKE"[^\n]+\n    private int bat\$contentWidth[^\n]+\n', '', source)
        source = re.sub(r'    @ModifyVariable\(method = "render"[^\n]+\n    private int bat\$hoverOffset',
                        '    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0, order = 1100)\n    private int bat$hoverOffset', source)
        source = source.replace('renderer.wrapLines(text, width)', 'renderer.wrapLines(text, Math.max(1, width - 20))')
        source = source.replace('        var info = ServerLocation.get(server.address);',
                                '        int x = bat$x, y = bat$y;\n        var info = ServerLocation.get(server.address);')
        source = source.replace('bat$click(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {',
                                'bat$click(net.minecraft.client.gui.Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {\n        double x=click.x(), y=click.y(); int button=click.button();')
        source = source.replace('argsOnly = true, ordinal = 0, order = 1100)\n    private double bat$clickOffset(double x) { return x - 20; }',
                                '''argsOnly = true, order = 1100)
    private net.minecraft.client.gui.Click bat$clickOffset(net.minecraft.client.gui.Click click) {
        return new net.minecraft.client.gui.Click(click.x() - 20, click.y(), click.buttonInfo());
    }''')
    if name.endswith('/ServerListExtrasMixin.java'):
        source = source.replace('import org.spongepowered.asm.mixin.Mixin;',
                                'import org.spongepowered.asm.mixin.Final;\nimport org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.Shadow;')
        source = source.replace('@Unique private String bat$query = "";',
                                '@Shadow @Final private List<MultiplayerServerListWidget.ServerEntry> servers;\n    @Unique private String bat$query = "";')
        source = source.replace(
            '''    @Redirect(method = "updateEntries", at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V", ordinal = 0))
    private void bat$filter(List<MultiplayerServerListWidget.ServerEntry> entries, Consumer<MultiplayerServerListWidget.ServerEntry> consumer) {
        // Vanilla can call updateEntries from its constructor, before Mixin field
        // initializers have executed. A null query must mean no filtering.
        String query = bat$query == null ? "" : bat$query;
        entries.stream().filter(entry -> query.isEmpty() || (entry.getServer().name + " " + entry.getServer().address).toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(entry -> !ServerPreferences.flag(entry.getServer().address, "favorite"))).forEach(consumer);
    }''',
            '''    @Redirect(
        method = "updateEntries",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerServerListWidget;servers:Ljava/util/List;"))
    private List<MultiplayerServerListWidget.ServerEntry> bat$filter(MultiplayerServerListWidget self) {
        String query = bat$query == null ? "" : bat$query;
        return servers.stream()
            .filter(entry -> query.isEmpty() || (entry.getServer().name + " " + entry.getServer().address).toLowerCase(Locale.ROOT).contains(query))
            .sorted(Comparator.comparing(entry -> !ServerPreferences.flag(entry.getServer().address, "favorite")))
            .toList();
    }''')
    if name.endswith('/RainCustomizerMixin.java'):
        source = source.replace('Lnet/minecraft/util/math/Vec3d;IFLjava/util/List;Ljava/util/List;)V',
                                'Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/state/WeatherRenderState;)V')
    if name.endswith(('/ItemPhysicsMixin.java', '/TntCountdownMixin.java')):
        source = source.replace('Lnet/minecraft/client/render/VertexConsumerProvider;I)V',
                                'Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V')
        source = source.replace('VertexConsumerProvider vertices,int light',
                                'net.minecraft.client.render.command.OrderedRenderCommandQueue queue,net.minecraft.client.render.state.CameraRenderState camera')
        if name.endswith('/TntCountdownMixin.java'):
            source = source.replace('matrices,vertices);', 'matrices,queue);')
    if name.endswith('/TntCountdown.java'):
        source = source.replace('float delta,MatrixStack matrices,VertexConsumerProvider vertices)',
                                'float delta,MatrixStack matrices,net.minecraft.client.render.command.OrderedRenderCommandQueue queue)')
        source = source.replace('draw(matrices,vertices,text(fuse,HudConfig.bool(mod,"ticks",false)),color,HudConfig.bool(mod,"shadow",true),HudConfig.bool(mod,"background",true),HudConfig.bool(mod,"throughWalls",false));',
                                '''String value=text(fuse,HudConfig.bool(mod,"ticks",false));
            float x=-c.textRenderer.getWidth(value)/2f;
            var label=Text.literal(value).asOrderedText();
            int light=LightmapTextureManager.MAX_LIGHT_COORDINATE;
            var layer=HudConfig.bool(mod,"throughWalls",false)?TextRenderer.TextLayerType.SEE_THROUGH:TextRenderer.TextLayerType.NORMAL;
            queue.submitText(matrices,x,0,label,false,layer,light,0x40FFFFFF,
                HudConfig.bool(mod,"background",true)?0x70000000:0,0);
            matrices.translate(0,0,.05);
            queue.submitText(matrices,x,0,label,HudConfig.bool(mod,"shadow",true),
                TextRenderer.TextLayerType.NORMAL,light,color,0,0);''')
    if name.endswith('/WorldEntryFavoriteMixin.java'):
        source = source.replace('field_19135', 'parent')
        source = re.sub(r'(private void bat\$(?:star|finishRow)\(DrawContext ctx,) int index, int y, int x, int width, int height,\s+',
                        r'\1 ', source)
        source = source.replace('        bat$starX = x;', '''        var entry = (WorldListWidget.WorldEntry)(Object)this;
        int x = entry.getContentX(), y = entry.getContentY(), height = entry.getContentHeight();
        bat$starX = x;''')
        source = re.sub(r'    @ModifyVariable\(method = "render"[^\n]+',
                        '    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0, order = 1100)', source)
        source = source.replace('bat$toggle(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {',
                                'bat$toggle(net.minecraft.client.gui.Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {\n        double x=click.x(), y=click.y(); int button=click.button();')
        source = source.replace('at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/LevelSummary;isSelectable()Z")',
                                'at = @At("HEAD")')
        source = source.replace('at = @At("HEAD"), argsOnly = true, ordinal = 0)', 'at = @At("HEAD"), argsOnly = true, ordinal = 0, order = 1100)')
        source = source.replace('private double bat$shiftIconHitbox(double x) { return x - 20; }',
                                '''private net.minecraft.client.gui.Click bat$shiftIconHitbox(net.minecraft.client.gui.Click click) {
        return new net.minecraft.client.gui.Click(click.x() - 20, click.y(), click.buttonInfo());
    }''')
    if name.endswith('/GlintColorMixin.java'):
        source = source.replace('@Mixin(RenderLayer.MultiPhase.class)', '@Mixin(RenderLayer.class)')
        source = source.replace('@Shadow @Final private RenderPipeline pipeline;', '@Shadow public abstract RenderPipeline getRenderPipeline();')
        source = source.replace('var id = pipeline.getLocation();', 'var id = getRenderPipeline().getLocation();')
        source = source.replace('Lorg/joml/Matrix4fc;F)', 'Lorg/joml/Matrix4fc;)')
    if name.endswith('/HitColorMixin.java'):
        source = source.replace('import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;',
                                'import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;\nimport org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;')
        source = source.replace('method="setupOverlayColor"', 'method="getTextureView"')
        source = source.replace('bat$hitColor(CallbackInfo ci)', 'bat$hitColor(CallbackInfoReturnable<com.mojang.blaze3d.textures.GpuTextureView> ci)')
        source = source.replace('RenderSystem.setupOverlayColor(bat$hitTexture.getGlTextureView());ci.cancel();',
                                'ci.setReturnValue(bat$hitTexture.getGlTextureView());')
    if name.endswith('/SkinLayers3D.java'):
        source = source.replace('render(MatrixStack matrices,VertexConsumerProvider vertices,int light,',
                                'render(MatrixStack matrices,net.minecraft.client.render.command.OrderedRenderCommandQueue queue,int light,')
        source = source.replace('player.getSkinTextures()', 'player.getSkin()').replace('textures.texture()', 'textures.body().texturePath()')
        source = source.replace('net.minecraft.client.util.SkinTextures.Model.SLIM', 'net.minecraft.entity.player.PlayerSkinType.SLIM')
        source = source.replace('var model=getContextModel();', 'var model=getContextModel();\n            model.setAngles(state);')
        source = source.replace('var buffer=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(skin));',
                                'var layer=net.minecraft.client.render.RenderLayers.entityCutoutNoCull(skin, false);')
        source = source.replace('if(!part.visible||!player.isPartVisible(FLAGS[i])', '''boolean visible=switch(i){
                    case 0->state.hatVisible;
                    case 1->state.jacketVisible;
                    case 2->state.rightSleeveVisible;
                    case 3->state.leftSleeveVisible;
                    case 4->state.rightPantsLegVisible;
                    default->state.leftPantsLegVisible;
                };
                if(!part.visible||!visible''')
        source = source.replace('matrices.push();try{part.applyTransform(matrices);for(var q:mesh.parts.get(i))draw(matrices,buffer,q,light,overlay);}finally{matrices.pop();}',
                                '''var quads=mesh.parts.get(i);
                matrices.push();
                try {
                    part.applyTransform(matrices);
                    queue.submitCustom(matrices,layer,(entry,buffer)->{
                        for(var quad:quads)draw(entry,buffer,quad,light,overlay);
                    });
                } finally {matrices.pop();}''')
        source = source.replace('void draw(MatrixStack matrices,VertexConsumer buffer,Quad quad',
                                'void draw(MatrixStack.Entry entry,VertexConsumer buffer,Quad quad')
        source = source.replace('buffer.vertex(matrices.peek(),quad.', 'buffer.vertex(entry,quad.')
        source = source.replace('.normal(matrices.peek(),quad.', '.normal(entry,quad.')
    if name.endswith('/FabricClientHooks.java'):
        source = source.replace('rendering.v1.WorldRender', 'rendering.v1.world.WorldRender')
        source = source.replace('WorldRenderEvents.START.', 'WorldRenderEvents.START_MAIN.')
        source = source.replace('WorldRenderEvents.END.', 'WorldRenderEvents.END_MAIN.')
        source = source.replace('WorldRenderEvents.BLOCK_OUTLINE.', 'WorldRenderEvents.BEFORE_BLOCK_OUTLINE.')
        source = source.replace('source.matrixStack()', 'source.matrices()')
        source = source.replace('source.camera()', 'source.gameRenderer().getCamera()')
        source = source.replace('source.frustum()', '((WorldFrameAccess)source.worldRenderer()).bat$frustum()')
        source = source.replace('source.projectionMatrix()', '((WorldFrameAccess)source.worldRenderer()).bat$projection()')
        source = source.replace('source.world()', 'MinecraftClient.getInstance().world')
        source = source.replace('source.tickCounter().getTickProgress(false)', 'MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false)')
        source = source.replace('WorldRenderContext.BlockOutlineContext', 'net.minecraft.client.render.state.OutlineRenderState')
        source = source.replace('source.blockState()', 'MinecraftClient.getInstance().world.getBlockState(source.pos())')
        source = source.replace('source.blockPos()', 'source.pos()')
        source = source.replace('source.entity()', 'MinecraftClient.getInstance().gameRenderer.getCamera().getFocusedEntity()')
    if name.endswith('/VisualLayers.java'):
        return (Path(__file__).parent / 'templates-12111/VisualLayers.java').read_text(encoding='utf-8')
    if name.endswith('/WorldVisualModules.java'):
        source = source.replace('RenderLayer.getDebugQuads()', 'net.minecraft.client.render.RenderLayers.debugQuads()')
        source = re.sub(r'var buffer=context.consumers\(\).getBuffer\(VisualLayers.lines\((VisualModules.number\([^;]+?\))\)\);',
                        r'float width=\1;\n        var buffer=context.consumers().getBuffer(VisualLayers.lines());', source)
        source = source.replace('->line(matrices,buffer,', '->line(matrices,buffer,width,')
        source = source.replace('VertexConsumer vertices,double x1', 'VertexConsumer vertices,float width,double x1')
        source = source.replace('.normal(entry,normal.x,normal.y,normal.z);', '.normal(entry,normal.x,normal.y,normal.z).lineWidth(width);')
        source = re.sub(r'net\.minecraft\.client\.render\.VertexRendering\.drawBox\([^;]+;', '''net.minecraft.client.render.VertexRendering.drawOutline(context.matrixStack(),buffer,
                net.minecraft.util.shape.VoxelShapes.cuboid(box),-camera.x,-camera.y,-camera.z,tint,width);''', source)
    if name.endswith('/ReturnTrail.java'):
        source = source.replace('var vertices=ctx.consumers().getBuffer(VisualLayers.lines(VisualModules.number(mod,"width",2,1,4)));',
                                'float width=VisualModules.number(mod,"width",2,1,4);var vertices=ctx.consumers().getBuffer(VisualLayers.lines());')
        source = source.replace('Vec3d a,Vec3d b,int color)', 'Vec3d a,Vec3d b,int color,float width)')
        source = source.replace('.normal(m,n.x,n.y,n.z);', '.normal(m,n.x,n.y,n.z).lineWidth(width);')
        source = re.sub(r'(line\(ctx,vertices,[^;]+,color)\);', r'\1,width);', source)
    if name.endswith('/MenuRegressionChecks.java'):
        source = source.replace('new DrawContext(client, state)', 'new DrawContext(client, state, 0, 0)')
        source = source.replace('(element, depth) ->', 'element ->')
        source = re.sub(r'(?m)^.*RenderSystem.getShaderTexture\(0\).*\n', '', source)
    if name.endswith('/TargetHud.java'):
        source = source.replace('entity.getWorld()', 'entity.getEntityWorld()')
    if name.endswith('/AccountHeads.java'):
        # Session no longer records an account type. Keep the existing offline
        # UUID and token checks used by the launcher bridge.
        source = source.replace('session.getAccountType()!=Session.AccountType.LEGACY && ', '')
        source = source.replace('DefaultSkinHelper.getSkinTextures(fallback).texture()', 'DefaultSkinHelper.getSkinTextures(fallback).body().texturePath()')
    if name.endswith('/ClientAccounts.java'):
        source = source.replace(',Session.AccountType.LEGACY', '').replace(',Session.AccountType.MSA', '')
        source = source.replace('UserApiService api=new YggdrasilAuthenticationService(Proxy.NO_PROXY).createUserApiService(identity.token());',
                                'var auth=new YggdrasilAuthenticationService(Proxy.NO_PROXY);\n            UserApiService api=auth.createUserApiService(identity.token());')
        source = source.replace('client.getSessionService().fetchProfile', 'auth.createMinecraftSessionService().fetchProfile')
    if name.endswith('/NativeChat.java'):
        source = source.replace('getProfile().getName()', 'getProfile().name()').replace('getProfile().getId()', 'getProfile().id()')
        source = source.replace('message.getIcon()', 'message.indicator()==null ? null : message.indicator().icon()')
        source = source.replace('getSkinTextures().texture()', 'getSkinTextures().body().texturePath()')
        helper = '''    public net.minecraft.client.gui.hud.ChatHud.Backend wrapBackend(
            net.minecraft.client.gui.hud.ChatHud.Backend delegate,
            DrawContext ctx,
            TextRenderer font,
            boolean focused,
            boolean scrolled) {
        if (ctx == null || font == null) return delegate;
        return new net.minecraft.client.gui.hud.ChatHud.Backend() {
            @Override
            public void updatePose(java.util.function.Consumer<org.joml.Matrix3x2f> transformer) {
                delegate.updatePose(transformer);
            }
            @Override
            public void fill(int x1, int y1, int x2, int y2, int color) {
                delegate.fill(x1, y1, x2, y2, color);
            }
            @Override
            public boolean text(int y, float opacity, OrderedText text) {
                int color = net.minecraft.util.math.ColorHelper.getWhite(opacity);
                draw(ctx, font, text, 0, y, color, focused, scrolled);
                return delegate.text(y, 0f, text);
            }
            @Override
            public void indicator(int x1, int y1, int x2, int y2, float opacity, net.minecraft.client.gui.hud.MessageIndicator indicator) {
                delegate.indicator(x1, y1, x2, y2, opacity, indicator);
            }
            @Override
            public void indicatorIcon(int left, int bottom, boolean forceDraw, net.minecraft.client.gui.hud.MessageIndicator indicator, net.minecraft.client.gui.hud.MessageIndicator.Icon icon) {
                delegate.indicatorIcon(left, bottom, forceDraw, indicator, icon);
            }
        };
    }
}'''
        source = source[:source.rfind('}')] + helper
    if name.endswith('/ChatHudMixin.java'):
        return '''package com.batclient.mixin;
import com.batclient.ChatCopyAccess;
import com.batclient.NativeChat;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin implements ChatCopyAccess {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;
    @Shadow private int scrolledLines;
    @Shadow public abstract int getWidth();
    @Shadow public abstract boolean isChatFocused();
    @Shadow public abstract void scroll(int scroll);
    @Shadow private double getChatScale() { return 1.0; }
    @Shadow private int getLineHeight() { return 9; }
    @Shadow public abstract int getVisibleLineCount();
    @Unique private NativeChat bat$chat;
    @Unique private boolean bat$pushed;
    @Unique private DrawContext bat$context;

    @Unique
    private NativeChat bat$state() {
        if (bat$chat == null) bat$chat = new NativeChat();
        return bat$chat;
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("HEAD"))
    private void bat$configuration(DrawContext context, TextRenderer textRenderer, int currentTick, int mouseX, int mouseY, boolean focused, boolean insert, CallbackInfo ci) {
        bat$context = context;
        bat$pushed = false;
        if (bat$state().configure()) ((ChatHud)(Object)this).reset();
        float offset = com.batclient.ChatOpeningAnimation.offset();
        if (offset > 0.05f) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0, offset);
            bat$pushed = true;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("RETURN"))
    private void bat$endOpening(DrawContext context, TextRenderer textRenderer, int currentTick, int mouseX, int mouseY, boolean focused, boolean insert, CallbackInfo ci) {
        if (bat$pushed) {
            context.getMatrices().popMatrix();
            bat$pushed = false;
        }
        bat$context = null;
    }

    @ModifyVariable(
        method = "render(Lnet/minecraft/client/gui/hud/ChatHud$Backend;IIZ)V",
        at = @At("HEAD"),
        argsOnly = true)
    private ChatHud.Backend bat$wrapBackend(ChatHud.Backend delegate) {
        return bat$state().wrapBackend(delegate, bat$context, client.textRenderer, isChatFocused(), scrolledLines > 0);
    }

    @Inject(method = "refresh", at = @At("HEAD"))
    private void bat$refreshBegin(CallbackInfo ci) { bat$state().beginRefresh(); }

    @Inject(method = "refresh", at = @At("RETURN"))
    private void bat$refreshEnd(CallbackInfo ci) { bat$state().endRefresh(); }

    @Inject(method = "clear", at = @At("HEAD"))
    private void bat$clear(boolean clearHistory, CallbackInfo ci) { bat$state().clear(); }

    @Inject(method = "addVisibleMessage", at = @At("HEAD"), cancellable = true)
    private void bat$visible(ChatHudLine message, CallbackInfo ci) {
        bat$state().configure();
        bat$state().append(client, message, visibleMessages, getWidth(), isChatFocused(), scrolledLines, this::scroll);
        ci.cancel();
    }

    @Override
    public String bat$copyAt(double mouseX, double mouseY) {
        double scale = getChatScale();
        if (scale <= 0.0) scale = 1.0;
        double chatX = mouseX / scale - 4.0;
        int maxWidth = MathHelper.floor((double)getWidth() / scale);
        if (chatX < -4.0 || chatX > maxWidth + 4.0) return "";
        int bottomY = MathHelper.floor((double)(client.getWindow().getScaledHeight() - 40) / scale);
        double dy = bottomY - (mouseY / scale);
        int lineHeight = Math.max(1, getLineHeight());
        if (dy < 0.0) return "";
        int row = MathHelper.floor(dy / lineHeight);
        if (row < 0 || row >= getVisibleLineCount()) return "";
        int index = row + scrolledLines;
        if (index < 0 || index >= visibleMessages.size()) return "";
        return bat$state().copyText(visibleMessages.get(index).content());
    }
}
'''
    if name.endswith('/NativeNametags.java'):
        nametag_helper = '''    public static void submitLabel(
            net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
            net.minecraft.client.util.math.MatrixStack matrices,
            net.minecraft.util.math.Vec3d pos, int y, Text text,
            boolean notSneaking, int light, double squaredDistanceToCamera,
            net.minecraft.client.render.state.CameraRenderState camera) {
        if (!enabled()) {
            queue.submitLabel(matrices, pos, y, text, notSneaking, light, squaredDistanceToCamera, camera);
            return;
        }
        if (pos == null || text == null) return;
        var client = net.minecraft.client.MinecraftClient.getInstance();
        var sMap = settings();
        matrices.push();
        matrices.translate(pos.x, pos.y + 0.5, pos.z);
        matrices.multiply(camera.orientation);
        float s = 0.025f * Math.max(0.5f, Math.min(2f, HudConfig.setNumber(sMap, "scale", 1)));
        matrices.scale(s, -s, s);
        float x = -client.textRenderer.getWidth(text) / 2.0f;
        int bg = HudConfig.bool(sMap, "background", true)
            ? ((int)(client.options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24)
            : 0;
        boolean dropShadow = HudConfig.bool(sMap, "shadow", true);
        var ordered = text.asOrderedText();
        if (notSneaking) {
            queue.submitText(matrices, x, (float)y, ordered, dropShadow, TextRenderer.TextLayerType.SEE_THROUGH, light, -2130706433, bg, 0);
            drawLogo(text, x, (float)y, -2130706433, matrices, queue, TextRenderer.TextLayerType.SEE_THROUGH, light);
            int lit = net.minecraft.client.render.LightmapTextureManager.applyEmission(light, 2);
            queue.submitText(matrices, x, (float)y, ordered, dropShadow, TextRenderer.TextLayerType.NORMAL, lit, -1, 0, 0);
            drawLogo(text, x, (float)y, -1, matrices, queue, TextRenderer.TextLayerType.NORMAL, lit);
        } else {
            queue.submitText(matrices, x, (float)y, ordered, dropShadow, TextRenderer.TextLayerType.NORMAL, light, -2130706433, bg, 0);
            drawLogo(text, x, (float)y, -2130706433, matrices, queue, TextRenderer.TextLayerType.NORMAL, light);
        }
        matrices.pop();
    }
    public static void drawLogo(
            Text text, float x, float y, int color,
            net.minecraft.client.util.math.MatrixStack matrices,
            net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
            TextRenderer.TextLayerType layer, int light) {
        if (!hasLogo(text)) return;
        var renderLayer = layer == TextRenderer.TextLayerType.SEE_THROUGH
            ? net.minecraft.client.render.RenderLayers.textSeeThrough(LOGO)
            : net.minecraft.client.render.RenderLayers.text(LOGO);
        int a = ((color >>> 24) & 255);
        if (a == 0) a = 255;
        if (layer == TextRenderer.TextLayerType.SEE_THROUGH) a = Math.min(a, 110);
        int alpha = (a << 24) | 0x00FFFFFF;
        float w = 16f, h = 8f, bx = x + 1f, by = y, z = 0.05f;
        float u0 = 0.10f, v0 = 0.31f, u1 = 0.90f, v1 = 0.69f;
        queue.submitCustom(matrices, renderLayer, (entry, buffer) -> {
            Matrix4f matrix = entry.getPositionMatrix();
            buffer.vertex(matrix, bx, by + h, z).color(alpha).texture(u0, v1).light(light);
            buffer.vertex(matrix, bx + w, by + h, z).color(alpha).texture(u1, v1).light(light);
            buffer.vertex(matrix, bx + w, by, z).color(alpha).texture(u1, v0).light(light);
            buffer.vertex(matrix, bx, by, z).color(alpha).texture(u0, v0).light(light);
        });
    }
}'''
        source = source[:source.rfind('}')] + nametag_helper
    if name.endswith('/NativeNametagStyleMixin.java'):
        return '''package com.batclient.mixin;

import com.batclient.NativeNametags;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerEntityRenderer.class)
public abstract class NativeNametagStyleMixin {
    @Redirect(
        method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitLabel(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/math/Vec3d;ILnet/minecraft/text/Text;ZIDLnet/minecraft/client/render/state/CameraRenderState;)V"))
    private void bat$submitLabel(
            OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d pos, int y,
            Text text, boolean notSneaking, int light, double squaredDistanceToCamera,
            CameraRenderState camera) {
        NativeNametags.submitLabel(queue, matrices, pos, y, text, notSneaking, light, squaredDistanceToCamera, camera);
    }
}
'''
    if name.endswith('/ChatSenderMixin.java'):
        source = source.replace('sender.getId()', 'sender.id()')
    if name.endswith('/HudRenderer.java'):
        source = source.replace('player.getSkinTextures()', 'player.getSkin()')
    if name.endswith('/HealthIndicator.java'):
        source = source.replace('entity.getWorld()', 'entity.getEntityWorld()')
        source = source.replace('getGuiAtlasManager().getSprite(id)', 'getAtlasManager().getAtlasTexture(net.minecraft.util.Atlases.GUI).getSprite(id)')
        source = source.replace('VertexConsumerProvider vertices', 'net.minecraft.client.render.command.OrderedRenderCommandQueue queue')
        source = source.replace('matrices,vertices', 'matrices,queue')
        source = source.replace('renderer.draw(text,x,y,(color&0xFFFFFF)|0x40000000,false,matrices.peek().getPositionMatrix(),vertices,',
                                'queue.submitText(matrices,x,y,text.asOrderedText(),false,')
        source = source.replace('background,LightmapTextureManager.MAX_LIGHT_COORDINATE);',
                                'LightmapTextureManager.MAX_LIGHT_COORDINATE,(color&0xFFFFFF)|0x40000000,background,0);')
        source = source.replace('renderer.draw(text,x,y,color,HudConfig.bool(mod,"shadow",true),matrices.peek().getPositionMatrix(),vertices,TextRenderer.TextLayerType.NORMAL,0,LightmapTextureManager.MAX_LIGHT_COORDINATE);',
                                'queue.submitText(matrices,x,y,text.asOrderedText(),HudConfig.bool(mod,"shadow",true),TextRenderer.TextLayerType.NORMAL,LightmapTextureManager.MAX_LIGHT_COORDINATE,color,0,0);')
        source = source.replace('var buffer=vertices.getBuffer(through?RenderLayer.getTextSeeThrough(sprite.getAtlasId()):RenderLayer.getText(sprite.getAtlasId()));',
                                'var layer=through?RenderLayer.getTextSeeThrough(sprite.getAtlasId()):RenderLayer.getText(sprite.getAtlasId());\n        queue.submitCustom(matrices,layer,(entry,buffer)->{')
        source = source.replace('var matrix=matrices.peek().getPositionMatrix();int light=', 'var matrix=entry.getPositionMatrix();int light=')
        source = source.replace('sprite.getMaxU(),sprite.getMinV()).light(light);', 'sprite.getMaxU(),sprite.getMinV()).light(light);\n        });')
    if name.endswith('/HealthIndicatorMixin.java'):
        source = source.replace('Lnet/minecraft/client/render/VertexConsumerProvider;I)V',
                                'Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V')
        source = source.replace('VertexConsumerProvider vertices,int light,CallbackInfo ci',
                                'net.minecraft.client.render.command.OrderedRenderCommandQueue queue,net.minecraft.client.render.state.CameraRenderState camera,CallbackInfo ci')
        source = source.replace('matrices,vertices,light);', 'matrices,queue,state.light);')
    if name.endswith(('/HealthIndicator.java', '/OwnNametagMixin.java', '/TntCountdown.java')):
        source = source.replace('.getEntityRenderDispatcher().getRotation()', '.gameRenderer.getCamera().getRotation()')
    if name.endswith(('/HealthIndicator.java', '/NativeNametags.java', '/LootBeams.java', '/WaypointRenderer.java')):
        source = re.sub(r'(?:net\.minecraft\.client\.render\.)?RenderLayer\.get(TextSeeThrough|Text|DebugQuads)\(',
                        lambda match: 'net.minecraft.client.render.RenderLayers.' + match[1][0].lower() + match[1][1:] + '(', source)
    source = re.sub(r'\b((?:client|c)\.player|entity)\.getPos\(\)', r'\1.getEntityPos()', source)
    source = source.replace('.camera().getPos()', '.camera().getCameraPos()')
    source = source.replace('ctx.drawBorder(', 'ctx.drawStrokedRectangle(')
    if name.endswith('/AdventureWorld.java'):
        source = source.replace('c.getEntityRenderDispatcher().getRotation()', 'ctx.camera().getRotation()')
    if name.endswith('/WebScreen.java'):
        source = source.replace('CreateWorldScreen.show(client, this)', 'CreateWorldScreen.show(client, () -> client.setScreen(this))')
        source = source.replace('net.minecraft.client.gui.screen.GameMenuScreen.disconnect(client,',
                                'client.disconnect(')
        source = source.replace('net.minecraft.SharedConstants.VERSION_NAME', 'net.minecraft.SharedConstants.getGameVersion().name()')
    if name.endswith(('Fabric12111Smoke.java', 'VisualParitySmoke.java', 'ForgeFullSmoke.java')):
        source = source.replace('net.minecraft.world.GameRules', 'net.minecraft.world.rule.GameRules')
    if name.endswith('/ForgeWorldStageMixin.java'):
        return '''package com.batclient.platform.forge.mixin;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;
@Mixin(WorldRenderer.class)
public abstract class ForgeWorldStageMixin {
    @Shadow @Final private BufferBuilderStorage bufferBuilders;
    @Unique private Camera bat$camera;
    @Unique private RenderTickCounter bat$counter;
    @Unique private Matrix4f bat$projection;
    @Unique private Frustum bat$frustum;
    @Inject(method="render",at=@At("HEAD"))
    private void bat$captureProjection(net.minecraft.client.util.memory.ObjectAllocator allocator,RenderTickCounter counter,
            boolean outline,Camera camera,Matrix4f position,Matrix4f basicProjection,Matrix4f projection,
            com.mojang.blaze3d.buffers.GpuBufferSlice fog,org.joml.Vector4f fogColor,boolean renderSky,CallbackInfo ci){
        bat$counter=counter;bat$camera=camera;bat$projection=projection;bat$frustum=null;
    }
    @Inject(method="renderMain",at=@At("HEAD"))
    private void bat$captureFrustum(FrameGraphBuilder graph,Frustum frustum,Matrix4f position,
            com.mojang.blaze3d.buffers.GpuBufferSlice fog,boolean outline,net.minecraft.client.render.state.WorldRenderState state,
            RenderTickCounter ticks,net.minecraft.util.profiler.Profiler profiler,CallbackInfo ci){
        bat$frustum=frustum;
    }
    @Inject(method="pushEntityRenders",at=@At("RETURN"))
    private void bat$entities(MatrixStack matrices,net.minecraft.client.render.state.WorldRenderState state,
            net.minecraft.client.render.command.OrderedRenderCommandQueue queue,CallbackInfo ci){
        if(bat$projection!=null&&bat$camera!=null&&bat$counter!=null){
            com.batclient.platform.forge.ForgeClientHooks.fireWorldEntities(
                matrices,bufferBuilders.getEntityVertexConsumers(),bat$camera,bat$counter.getTickProgress(false),bat$frustum,bat$projection);
        }
    }
    @Inject(method="renderTargetBlockOutline",at=@At("HEAD"),cancellable=true)
    private void bat$blockOutline(VertexConsumerProvider.Immediate consumers,MatrixStack matrices,
            boolean translucent,net.minecraft.client.render.state.WorldRenderState state,CallbackInfo ci){
        var outline=state.outlineRenderState;
        if(outline!=null&&outline.isTranslucent()==translucent&&bat$camera!=null
                &&!com.batclient.platform.forge.ForgeClientHooks.fireBlockOutline(
                    matrices,consumers,bat$camera,bat$counter!=null?bat$counter.getTickProgress(false):0f,outline.pos())){
            ci.cancel();
        }
    }
}
'''
    if name.endswith('/ForgeWorldFrame.java'):
        source = source.replace(
            '''    void highlight(net.minecraftforge.client.event.RenderHighlightEvent.Block event){
        matrices=event.getPoseStack();camera=event.getCamera();delta=event.getPartialTick();
        buffers=event.getMultiBufferSource();frustum=null;
        // Block-outline renderers do not consume a projection matrix.
        projection=null;
    }''',
            '''    void highlight(net.minecraftforge.client.event.RenderHighlightEvent.Block event){
        camera=event.getCamera();delta=MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
        frustum=null;projection=null;
    }''')
    if name.endswith('/ForgeClientHooks.java'):
        source = source.replace(
            '''    private boolean highlight(RenderHighlightEvent.Block event){
        if(outlines.isEmpty())return false;
        worldFrame.highlight(event);target.event=event;
        boolean vanilla=true;for(BlockOutline callback:outlines)vanilla&=callback.draw(worldFrame,target);
        return !vanilla;
    }''',
            '''    private boolean highlight(RenderHighlightEvent.Block event){
        target.event=event;target.pos=null;
        return false;
    }
    public static boolean fireBlockOutline(net.minecraft.client.util.math.MatrixStack matrices,net.minecraft.client.render.VertexConsumerProvider buffers,net.minecraft.client.render.Camera camera,float delta,net.minecraft.util.math.BlockPos pos){
        var hooks=active;if(hooks==null||hooks.stopping||hooks.outlines.isEmpty()||pos==null||MinecraftClient.getInstance().world==null)return true;
        hooks.worldFrame.stage(matrices,buffers,camera,delta,null,null);
        hooks.target.pos=pos;hooks.target.camera=camera;
        boolean vanilla=true;for(BlockOutline callback:hooks.outlines)vanilla&=callback.draw(hooks.worldFrame,hooks.target);
        return vanilla;
    }''')
        source = source.replace(
            '''    private static final class TargetView implements BlockTarget {
        private RenderHighlightEvent.Block event;
        @Override public net.minecraft.util.math.BlockPos blockPos(){return event.getTarget().getBlockPos();}
        @Override public net.minecraft.block.BlockState blockState(){return MinecraftClient.getInstance().world.getBlockState(blockPos());}
        @Override public net.minecraft.entity.Entity entity(){return event.getCamera().getFocusedEntity();}
    }''',
            '''    private static final class TargetView implements BlockTarget {
        private RenderHighlightEvent.Block event;
        private net.minecraft.util.math.BlockPos pos;
        private net.minecraft.client.render.Camera camera;
        @Override public net.minecraft.util.math.BlockPos blockPos(){return pos!=null?pos:event.getTarget().getBlockPos();}
        @Override public net.minecraft.block.BlockState blockState(){return MinecraftClient.getInstance().world.getBlockState(blockPos());}
        @Override public net.minecraft.entity.Entity entity(){return camera!=null?camera.getFocusedEntity():event.getCamera().getFocusedEntity();}
    }''')
    if name.endswith('/ForgeClientResources.java'):
        source = source.replace('for(var skin:event.getSkins()){', 'for(var skin:event.getModelTypes()){')
        source = source.replace('var renderer=event.getPlayerSkin(skin);',
                                'var renderer=event.getPlayerRenderer(skin);')
    if name.endswith('/GpuWebQuad.java'):
        source = source.replace('            glTextureView = view;', '''            glTextureView = view;
            sampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);''')
        source = source.replace('            texture.setAddressMode(AddressMode.CLAMP_TO_EDGE);\n', '')
        source = source.replace('            texture.setTextureFilter(FilterMode.LINEAR, false);\n', '')
    if name.endswith(('/HudRenderer.java', '/LoadingBrand.java')):
        source = source.replace('new NativeImageBackedTexture(()->"Bat Client texture",image);',
                                '''new NativeImageBackedTexture(()->"Bat Client texture",image) {
                    { sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                        .get(com.mojang.blaze3d.textures.FilterMode.LINEAR); }
                };''')
        source = re.sub(r'texture\.setFilter\(true,\s*false\);', '', source)
    if name.endswith('/MinimapHud.java'):
        source = source.replace('texture.setFilter(false,false);', '')
    if name.endswith(('/BatClientMod.java', '/ClientMods.java', '/Freelook.java',
                      '/ReturnTrail.java', '/Waypoints.java')):
        source = source.replace('"category.batclient.title"', 'com.batclient.BatKeybinds.CATEGORY')
    if name.endswith('/BatKeybinds.java'):
        source = source.replace('public final class BatKeybinds {', '''public final class BatKeybinds {
    public static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(net.minecraft.util.Identifier.of("batclient", "controls"));''')
        source = source.replace('InputUtil.isKeyPressed(client.getWindow().getHandle(),', 'InputUtil.isKeyPressed(client.getWindow(),')
        source = source.replace('binding.getTranslationKey()', 'binding.getId()')
    if name.endswith(('/BatClientMod.java', '/OriginalPauseScreen.java')):
        source = source.replace('.init(client, ', '.init(')
    if name.endswith(('/WebScreen.java', '/OriginalPauseScreen.java', '/HudEditorScreen.java')):
        source = re.sub(
            r'public boolean (mouseClicked|mouseReleased|mouseDragged)\(double mouseX, double mouseY, int button(,\s*double deltaX, double deltaY)?\) \{',
            lambda match: 'public boolean ' + match[1] + '(net.minecraft.client.gui.Click click'
            + (', boolean doubleClick' if match[1] == 'mouseClicked' else '')
            + (', double deltaX, double deltaY' if match[2] else '') + ') {'
            + ('' if name.endswith('/OriginalPauseScreen.java') else
               '\n\t\tdouble mouseX = click.x(), mouseY = click.y();\n\t\tint button = click.button();'),
            source)
        source = source.replace('mouseClicked(mouseX, mouseY, button)', 'mouseClicked(click, doubleClick)')
        source = source.replace('mouseReleased(mouseX, mouseY, button)', 'mouseReleased(click)')
        source = source.replace('mouseDragged(mouseX, mouseY, button, deltaX, deltaY)', 'mouseDragged(click, deltaX, deltaY)')
        source = re.sub(
            r'public boolean (keyPressed|keyReleased)\(int keyCode, int scanCode, int modifiers\) \{',
            lambda match: 'public boolean ' + match[1] + '(net.minecraft.client.input.KeyInput input) {'
            + ('\n\t\tint keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();'
               if name.endswith('/WebScreen.java') else
               ('\n\t\tint keyCode = input.key();' if match[1] == 'keyPressed' else '')),
            source)
        source = source.replace('keyPressed(keyCode, scanCode, modifiers)', 'keyPressed(input)')
        source = source.replace('keyReleased(keyCode, scanCode, modifiers)', 'keyReleased(input)')
        source = re.sub(r'fromKeyCode\(keyCode,\s*scanCode\)', 'fromKeyCode(input)', source)
        source = source.replace('charTyped(char chr, int modifiers)', 'charTyped(net.minecraft.client.input.CharInput input)')
        source = source.replace('charTyped(chr, modifiers)', 'charTyped(input)')
        source = source.replace('window.onCharMods(chr, modifiers)', 'window.onCharMods(input.codepoint(), input.modifiers())')
    if name.endswith('/ItemPreviews.java'):
        source = source.replace('Screen.hasShiftDown()', 'MinecraftClient.getInstance().isShiftPressed()')
    if name.endswith('/HudEditorScreen.java'):
        source = source.replace('!hasShiftDown()', '!client.isShiftPressed()')
    return source
