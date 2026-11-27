"""Actual 1.19.4 APIs, layered after the 1.20.1 adapters.

No stubs or feature removal. Full compilation and native checks remain required.
Signatures checked with javap against the genuine mapped 1.19.4 game JAR.
"""


def adapt(name, value):
    value = value.replace('net.minecraft.client.gui.DrawContext', 'com.batclient.platform.mc1194.DrawContext')
    if name.endswith('VisualLayers.java'):
        value = value.replace('ITEM_ENTITY_TARGET', 'ITEM_TARGET')
    if name.endswith('LoadingBrand.java'):
        value = value.replace('context.fill(RenderLayer.getGuiOverlay(),', 'context.fill(')
    if name.endswith('HudRenderer.java'):
        value = value.replace(
            'net.minecraft.client.gui.PlayerSkinDrawer.draw(ctx,player.getSkinTexture(),0,0,24);',
            '{com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,player.getSkinTexture());net.minecraft.client.gui.PlayerSkinDrawer.draw(ctx.getMatrices(),0,0,24);}',
        )
        value = value.replace(
            '((BossBarHudAccessor)c.inGameHud.getBossBarHud()).bat$drawBar(ctx, (width - 182) / 2, y + (names ? fr.fontHeight + 3 : 2), bar);',
            '{com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, new Identifier("textures/gui/bars.png")); ((BossBarHudAccessor)c.inGameHud.getBossBarHud()).bat$drawBar(ctx.getMatrices(), (width - 182) / 2, y + (names ? fr.fontHeight + 3 : 2), bar);}',
        )
    if name.endswith('NativeChat.java'):
        value = value.replace(
            'PlayerSkinDrawer.draw(ctx,entry.getSkinTexture(),x,y,8,HudConfig.bool(heads,"hat",true),false);',
            '{RenderSystem.setShaderTexture(0,entry.getSkinTexture());PlayerSkinDrawer.draw(ctx.getMatrices(),x,y,8,HudConfig.bool(heads,"hat",true),false);}',
        )
    if name.endswith('ItemPreviews.java'):
        value = value.replace(
            'public void drawItems(TextRenderer font,int x,int y,DrawContext ctx){',
            'public void drawItems(TextRenderer font,int x,int y,net.minecraft.client.util.math.MatrixStack matrices,net.minecraft.client.render.item.ItemRenderer itemRenderer){drawItems(font,x,y,new DrawContext(matrices));}\n        public void drawItems(TextRenderer font,int x,int y,DrawContext ctx){',
        )
    if name.endswith('WebScreen.java'):
        value = value.replace(
            'public void render(DrawContext context, int mouseX, int mouseY, float delta) {',
            'public void render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {\n\t\tDrawContext context = new DrawContext(matrices);',
        )
    if name.endswith('HudEditorScreen.java'):
        value = value.replace(
            'public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {',
            'public void render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {\n\t\tDrawContext ctx = new DrawContext(matrices);',
        )
        value = value.replace('super.render(ctx, mouseX, mouseY, delta);', 'super.render(matrices, mouseX, mouseY, delta);')
    if name.endswith('OriginalPauseScreen.java'):
        value = value.replace(
            'public void render(DrawContext context, int mouseX, int mouseY, float delta) {',
            'public void render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {',
        )
        value = value.replace('delegate.render(context, mouseX, mouseY, delta);', 'delegate.render(matrices, mouseX, mouseY, delta);')
        value = value.replace('super.render(context, mouseX, mouseY, delta);', 'super.render(matrices, mouseX, mouseY, delta);')
        value = value.replace(
            'public void renderBackground(DrawContext context) {',
            'public void renderBackground(net.minecraft.client.util.math.MatrixStack matrices) {',
        )
    if name.endswith('MenuRegressionChecks.java'):
        value = value.replace('multiplayer.render(ctx, 0, 0, 0);', 'multiplayer.render(ctx.getMatrices(), 0, 0, 0);')
    if name.endswith('FabricClientHooks.java'):
        value = value.replace(
            'HudRenderCallback.EVENT.register((context,ticks)->callback.draw(context));',
            'HudRenderCallback.EVENT.register((matrices,ticks)->callback.draw(new com.batclient.platform.mc1194.DrawContext(matrices)));',
        )
    if name.endswith('ForgeHudMixin.java'):
        value = value.replace(
            'private void bat$hud(DrawContext context,float tickCounter,CallbackInfo info){ForgeClientHooks.fireHud(context);}',
            'private void bat$hud(net.minecraft.client.util.math.MatrixStack matrices,float tickCounter,CallbackInfo info){ForgeClientHooks.fireHud(new com.batclient.platform.mc1194.DrawContext(matrices));}',
        )
    if name.endswith('BossBarHudAccessor.java'):
        value = value.replace(
            '@Invoker("renderBossBar") void bat$drawBar(DrawContext context, int x, int y, BossBar bar);',
            '@Invoker("renderBossBar") void bat$drawBar(net.minecraft.client.util.math.MatrixStack matrices, int x, int y, BossBar bar);',
        )
    if name.endswith('BossBarHudMixin.java'):
        value = value.replace(
            'private void bat$bars(DrawContext context, CallbackInfo ci)',
            'private void bat$bars(net.minecraft.client.util.math.MatrixStack matrices, CallbackInfo ci)',
        )
    if name.endswith('ChatHudMixin.java'):
        value = value.replace('getMessageLineIndex', 'getMessageIndex')
        value = value.replace(
            'private void bat$configuration(DrawContext ctx,int ticks,int mouseX,int mouseY,CallbackInfo ci) {',
            'private void bat$configuration(net.minecraft.client.util.math.MatrixStack matrices,int ticks,int mouseX,int mouseY,CallbackInfo ci) {\n        DrawContext ctx=new DrawContext(matrices);',
        )
        value = value.replace(
            'private void bat$endOpening(DrawContext ctx,int ticks,int mouseX,int mouseY,CallbackInfo ci){ctx.getMatrices().pop();}',
            'private void bat$endOpening(net.minecraft.client.util.math.MatrixStack matrices,int ticks,int mouseX,int mouseY,CallbackInfo ci){matrices.pop();}',
        )
        value = value.replace(
            '@Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"))\n    private int bat$draw(DrawContext ctx,TextRenderer font,OrderedText text,int x,int y,int color) {\n        return bat$state().draw(ctx,font,text,x,y,color,isChatFocused(),scrolledLines>0);\n    }',
            '@Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/font/TextRenderer;drawWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/text/OrderedText;FFI)I"))\n    private int bat$draw(TextRenderer font,net.minecraft.client.util.math.MatrixStack matrices,OrderedText text,float x,float y,int color) {\n        return bat$state().draw(new DrawContext(matrices),font,text,Math.round(x),Math.round(y),color,isChatFocused(),scrolledLines>0);\n    }',
        )
    if name.endswith('ChatScreenAnimationMixin.java'):
        value = value.replace(
            'private void bat$entrance(DrawContext ctx,int mouseX,int mouseY,float delta,CallbackInfo ci){ctx.getMatrices().push();ctx.getMatrices().translate(0,ChatOpeningAnimation.offset(),0);}',
            'private void bat$entrance(net.minecraft.client.util.math.MatrixStack matrices,int mouseX,int mouseY,float delta,CallbackInfo ci){matrices.push();matrices.translate(0,ChatOpeningAnimation.offset(),0);}',
        )
        value = value.replace(
            'private void bat$end(DrawContext ctx,int mouseX,int mouseY,float delta,CallbackInfo ci){ctx.getMatrices().pop();}',
            'private void bat$end(net.minecraft.client.util.math.MatrixStack matrices,int mouseX,int mouseY,float delta,CallbackInfo ci){matrices.pop();}',
        )
    if name.endswith('CustomCrosshairMixin.java'):
        value = value.replace(
            'target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"',
            'target = "Lnet/minecraft/client/gui/hud/InGameHud;drawTexture(Lnet/minecraft/client/util/math/MatrixStack;IIIIII)V"',
        )
        value = value.replace(
            'private void bat$crosshair(DrawContext ctx, Identifier sprite, int x, int y, int u, int v, int width, int height) {',
            'private void bat$crosshair(net.minecraft.client.util.math.MatrixStack matrices, int x, int y, int u, int v, int width, int height) {\n        DrawContext ctx = new DrawContext(matrices);',
        )
        value = value.replace('ctx.drawTexture(sprite, x, y, u, v, width, height);', 'net.minecraft.client.gui.DrawableHelper.drawTexture(matrices, x, y, u, v, width, height);')
    if name.endswith('InventoryNametagMixin.java'):
        value = value.replace(
            'drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIFFLnet/minecraft/entity/LivingEntity;)V',
            'drawEntity(Lnet/minecraft/client/util/math/MatrixStack;IIIFFLnet/minecraft/entity/LivingEntity;)V',
        )
        value = value.replace(
            'private static void bat$portraitBounds(DrawContext ctx,',
            'private static void bat$portraitBounds(net.minecraft.client.util.math.MatrixStack matrices,',
        )
        value = value.replace(
            'drawEntity(Lnet/minecraft/client/gui/DrawContext;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/entity/LivingEntity;)V',
            'drawEntity(Lnet/minecraft/client/util/math/MatrixStack;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/entity/LivingEntity;)V',
        )
        value = value.replace(
            'private static void bat$portraitName(DrawContext ctx, int x, int y, int size,\n                                         Quaternionf rotation, Quaternionf cameraRotation, LivingEntity entity, CallbackInfo ci) {',
            'private static void bat$portraitName(net.minecraft.client.util.math.MatrixStack matrices, int x, int y, int size,\n                                         Quaternionf rotation, Quaternionf cameraRotation, LivingEntity entity, CallbackInfo ci) {\n        DrawContext ctx = new DrawContext(matrices);',
        )
    if name.endswith('InventoryOpeningMixin.java'):
        value = value.replace(
            'private void bat$render(DrawContext ctx,int mouseX,int mouseY,float delta,CallbackInfo ci){\n        InventoryOpeningAnimation.drawing((Screen)(Object)this);\n        ctx.getMatrices().push();ctx.getMatrices().translate(0,InventoryOpeningAnimation.drawingOffset(),0);\n    }',
            'private void bat$render(net.minecraft.client.util.math.MatrixStack matrices,int mouseX,int mouseY,float delta,CallbackInfo ci){\n        InventoryOpeningAnimation.drawing((Screen)(Object)this);\n        matrices.push();matrices.translate(0,InventoryOpeningAnimation.drawingOffset(),0);\n    }',
        )
        value = value.replace(
            'private void bat$renderEnd(DrawContext ctx,int mouseX,int mouseY,float delta,CallbackInfo ci){\n        ctx.getMatrices().pop();InventoryOpeningAnimation.finished();\n    }',
            'private void bat$renderEnd(net.minecraft.client.util.math.MatrixStack matrices,int mouseX,int mouseY,float delta,CallbackInfo ci){\n        matrices.pop();InventoryOpeningAnimation.finished();\n    }',
        )
    if name.endswith('InventoryScissorAnimationMixin.java'):
        value = value.replace('import com.batclient.platform.mc1194.DrawContext;', 'import net.minecraft.client.gui.DrawableHelper;')
        value = value.replace('@Mixin(DrawContext.class)', '@Mixin(DrawableHelper.class)')
        value = value.replace('private int bat$clip', 'private static int bat$clip')
    if name.endswith('LoadingBrandMixin.java'):
        value = value.replace(
            '@Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIFFIIII)V"),require=0)\n    private void bat$brand(DrawContext context,Identifier vanilla,int x,int y,int width,int height,float u,float v,int regionWidth,int regionHeight,int textureWidth,int textureHeight){',
            '@Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screen/SplashOverlay;drawTexture(Lnet/minecraft/client/util/math/MatrixStack;IIIIFFIIII)V"),require=0)\n    private void bat$brand(net.minecraft.client.util.math.MatrixStack matrices,int x,int y,int width,int height,float u,float v,int regionWidth,int regionHeight,int textureWidth,int textureHeight){\n        DrawContext context=new DrawContext(matrices);',
        )
        value = value.replace(
            'private void bat$progress(DrawContext context,int minX,int minY,int maxX,int maxY,float opacity,CallbackInfo ci){LoadingBrand.progress(context,progress,opacity);ci.cancel();}',
            'private void bat$progress(net.minecraft.client.util.math.MatrixStack matrices,int minX,int minY,int maxX,int maxY,float opacity,CallbackInfo ci){LoadingBrand.progress(new DrawContext(matrices),progress,opacity);ci.cancel();}',
        )
    if name.endswith('MultiplayerExtrasMixin.java'):
        value = value.replace(
            'private void bat$brand(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) { MenuBranding.draw(ctx); }',
            'private void bat$brand(net.minecraft.client.util.math.MatrixStack matrices, int mx, int my, float delta, CallbackInfo ci) { MenuBranding.draw(new DrawContext(matrices)); }',
        )
        value = value.replace(
            'Lnet/minecraft/client/gui/DrawContext;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V',
            'Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerScreen;drawCenteredTextWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V',
        )
        value = value.replace('args.set(3, 8);', 'args.set(4, 8);')
        value = value.replace('index=3)', 'index=4)')
    if name.endswith('ServerEntryExtrasMixin.java'):
        value = value.replace(
            'private void bat$before(DrawContext ctx, int index, int y, int x, int width, int height, int mx, int my, boolean hovered, float delta, CallbackInfo ci) {\n        bat$x = x; bat$y = y;\n        ctx.getMatrices().push(); ctx.getMatrices().translate(20, 0, 0);\n    }',
            'private void bat$before(net.minecraft.client.util.math.MatrixStack matrices, int index, int y, int x, int width, int height, int mx, int my, boolean hovered, float delta, CallbackInfo ci) {\n        bat$x = x; bat$y = y;\n        matrices.push(); matrices.translate(20, 0, 0);\n    }',
        )
        value = value.replace(
            'value = "INVOKE", target = "Lnet/minecraft/client/network/ServerInfo;getStatus()Lnet/minecraft/client/network/ServerInfo$Status;", ordinal = 0',
            'value = "FIELD", target = "Lnet/minecraft/client/network/ServerInfo;online:Z", ordinal = 0',
        )
        value = value.replace(
            'Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)I',
            'Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/client/util/math/MatrixStack;Ljava/lang/String;FFI)I',
        )
        value = value.replace('args.set(2, (int)args.get(2) + 18);', 'args.set(2, (float)args.get(2) + 18f);')
        value = value.replace('private int bat$nameSpace(int x) { return x+18; }', 'private float bat$nameSpace(float x) { return x+18f; }')
        value = value.replace(
            'private void bat$after(DrawContext ctx, int index, int y, int x, int width, int height, int mx, int my, boolean hovered, float delta, CallbackInfo ci) {',
            'private void bat$after(net.minecraft.client.util.math.MatrixStack matrices, int index, int y, int x, int width, int height, int mx, int my, boolean hovered, float delta, CallbackInfo ci) {\n        DrawContext ctx = new DrawContext(matrices);',
        )
    if name.endswith('ServerHudMixin.java'):
        value = value.replace(
            'renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V',
            'renderScoreboardSidebar(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/scoreboard/ScoreboardObjective;)V',
        )
        value = value.replace('private void bat$sidebar(DrawContext context,', 'private void bat$sidebar(net.minecraft.client.util.math.MatrixStack matrices,')
    if name.endswith('ShulkerInspectMixin.java'):
        value = value.replace(
            'private void bat$inspect(DrawContext ctx,int x,int y,CallbackInfo ci){if(ItemPreviews.inspect(ctx,focusedSlot,x,y))ci.cancel();}',
            'private void bat$inspect(net.minecraft.client.util.math.MatrixStack matrices,int x,int y,CallbackInfo ci){if(ItemPreviews.inspect(new DrawContext(matrices),focusedSlot,x,y))ci.cancel();}',
        )
    if name.endswith('WorldEntryFavoriteMixin.java'):
        value = value.replace(
            'private void bat$star(DrawContext ctx, int index, int y, int x, int width, int height,\n                          int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo ci) {',
            'private void bat$star(net.minecraft.client.util.math.MatrixStack matrices, int index, int y, int x, int width, int height,\n                          int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo ci) {\n        DrawContext ctx = new DrawContext(matrices);',
        )
        value = value.replace(
            'private void bat$finishRow(DrawContext ctx, int index, int y, int x, int width, int height,\n                              int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo ci) {',
            'private void bat$finishRow(net.minecraft.client.util.math.MatrixStack matrices, int index, int y, int x, int width, int height,\n                              int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo ci) {\n        DrawContext ctx = new DrawContext(matrices);',
        )
        value = value.replace(
            'Lnet/minecraft/world/level/storage/LevelSummary;isSelectable()Z',
            'Lnet/minecraft/world/level/storage/LevelSummary;isUnavailable()Z',
        )
    if name.endswith('ForgeClientResources.java'):
        value = value.replace(
            'var renderer=event.getPlayerSkin(skin);',
            'net.minecraft.client.render.entity.LivingEntityRenderer<?, ?> renderer=event.getSkin(skin);',
        )
    if name.endswith('WorldScreenBrandMixin.java'):
        value = value.replace(
            'private void bat$brand(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {\n        com.batclient.web.MenuBranding.draw(ctx);\n    }',
            'private void bat$brand(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {\n        com.batclient.web.MenuBranding.draw(new DrawContext(matrices));\n    }',
        )
    return value
