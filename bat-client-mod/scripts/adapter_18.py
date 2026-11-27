"""Adapt 1.8.9 Legacy Fabric Yarn Java/Mixin sources to 1.8.0 Legacy Fabric Yarn."""
import re


def _convert_buffer_calls(text):
    # Tessellator.draw() -> method_9927()
    text = re.sub(r'\b(Tessellator\.getInstance\(\)|tess)\.draw\(\)', r'\1.method_9927()', text)
    # BufferBuilder.begin(mode, VertexFormats.*) -> method_9737(mode)
    text = re.sub(r'(\b\w+)\.begin\(([^,]+),\s*VertexFormats\.[A-Z_]+\)', r'\1.method_9737(\2)', text)
    text = text.replace('import net.minecraft.client.render.VertexFormats;\n', '')

    # Fluent vertex chains:
    # 1) buf.vertex(x, y, z).texture(u, v).color(r, g, b, a).next()
    text = re.sub(
        r'(\b\w+)\.vertex\(([^;]+?)\)\.texture\(([^;]+?)\)\.color\(([^;]+?)\)\.next\(\)',
        r'\1.method_21805(\4); \1.method_21799(\3); \1.method_9742(\2)',
        text,
    )
    # 2) buf.vertex(x, y, z).color(r, g, b, a).next()
    text = re.sub(
        r'(\b\w+)\.vertex\(([^;]+?)\)\.color\(([^;]+?)\)\.next\(\)',
        r'\1.method_21805(\3); \1.method_9742(\2)',
        text,
    )
    # 3) buf.vertex(x, y, z).texture(u, v).next()
    text = re.sub(
        r'(\b\w+)\.vertex\(([^;]+?)\)\.texture\(([^;]+?)\)\.next\(\)',
        r'\1.method_21799(\3); \1.method_9742(\2)',
        text,
    )
    # 4) Vertices.color(v.vertex(x, y, z), color).next()
    text = re.sub(
        r'Vertices\.color\((\w+)\.vertex\(([^;]+?)\),\s*([^)]+)\)\.next\(\)',
        r'Vertices.color(\1, \3); \1.method_9742(\2)',
        text,
    )
    # 5) Vertices.normal(buf.vertex(x, y, z).texture(u, v), nx, ny, nz).next()
    text = re.sub(
        r'Vertices\.normal\((\w+)\.vertex\(([^;]+?)\)\.texture\(([^;]+?)\),\s*([^)]+)\)\.next\(\)',
        r'Vertices.normal(\1, \4); \1.method_21799(\3); \1.method_9742(\2)',
        text,
    )
    # 6) buffer.vertex(x, y, z)\n .texture(u, v)\n .normal(nx, ny, nz)\n .next()
    text = re.sub(
        r'(\w+)\.vertex\(([^;]+?)\)\s*\.texture\(([^;]+?)\)\s*\.normal\(([^;]+?)\)\s*\.next\(\)',
        r'\1.method_21808(\4); \1.method_21799(\3); \1.method_9742(\2)',
        text,
    )
    return text


def adapt(rel, text):
    # Window constructor: new Window(client) -> new Window(client, (client).width, (client).height)
    text = re.sub(
        r'new\s+((?:net\.minecraft\.client\.util\.)?Window)\(((?:[^,()]|\(\))+)\)',
        r'new \1(\2, (\2).width, (\2).height)',
        text,
    )

    # Non-generic LivingEntityRenderer / FeatureRenderer in 1.8.0
    text = text.replace('LivingEntityRenderer<AbstractClientPlayerEntity>', 'LivingEntityRenderer')
    text = text.replace('FeatureRenderer<AbstractClientPlayerEntity>', 'FeatureRenderer')

    # ResourcePackLoader.Entry -> net.minecraft.client.class_1659
    if 'ResourcePackLoader.Entry' in text:
        text = text.replace('ResourcePackLoader.Entry', 'net.minecraft.client.class_1659')
        text = text.replace('.getSelectedResourcePacks()', '.getSelectedResourcePacks()')
        text = text.replace('.getResourcePack()', '.method_5913()')
        text = text.replace('.getName()', '.method_5914()')
        text = text.replace('.getDescription()', '.method_5915()')

    # BlockHitResult.Type -> HitResultType, hit.type -> hit.hitResult
    text = text.replace('BlockHitResult.Type', 'net.minecraft.util.hit.HitResultType')
    text = re.sub(r'\bhit\.type\b', 'hit.hitResult', text)

    # ClientPlayerInteractionManager.isBreakingBlock() -> BlockBreakingAccessor
    if 'BlockIndicator.java' in rel:
        text = text.replace('client.interactionManager.isBreakingBlock()', '((com.batclient.mixin.BlockBreakingAccessor) client.interactionManager).bat$getBreakingProgress() > 0f')

    # MinecraftClient.getSessionProperties() -> getTwitchPropertyMap()
    text = text.replace('.getSessionProperties()', '.getTwitchPropertyMap()')

    # LevelInfo.GameMode -> net.minecraft.world.GameMode
    text = text.replace('net.minecraft.world.level.LevelInfo.GameMode', 'net.minecraft.world.GameMode')
    text = text.replace('LevelInfo.GameMode', 'net.minecraft.world.GameMode')

    # Raw List loops in 1.8.0
    if 'ClientPresence.java' in rel:
        text = text.replace('client.world.playerEntities.stream()', '((java.util.List<net.minecraft.entity.player.PlayerEntity>) client.world.playerEntities).stream()')
    if 'LootBeams.java' in rel:
        text = text.replace('for (Entity entity : c.world.getLoadedEntities()) {', 'for (Object _entity : c.world.getLoadedEntities()) { Entity entity = (Entity) _entity;')
    if 'WorldVisualModules.java' in rel:
        text = text.replace('for (Entity entity : context.world().getLoadedEntities()) {', 'for (Object _entity : context.world().getLoadedEntities()) { Entity entity = (Entity) _entity;')
        text = re.sub(
            r'WorldRenderer\.drawBox\(([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^)]+)\)',
            r'WorldRenderer.method_9896(\1, (((\5) & 255) << 24) | (((\2) & 255) << 16) | (((\3) & 255) << 8) | ((\4) & 255))',
            text,
        )
    if 'NativeChat.java' in rel:
        text = text.replace('for (PlayerListEntry player : c.getNetworkHandler().getPlayerList()) {', 'for (Object _player : c.getNetworkHandler().getPlayerList()) { PlayerListEntry player = (PlayerListEntry) _player;')
    if 'HudRenderer.java' in rel:
        text = text.replace(
            'board.getAllPlayerScores(objective)',
            '((java.util.Collection<net.minecraft.scoreboard.ScoreboardPlayerScore>) board.getAllPlayerScores(objective))',
        )

    # SkinLayers3D FeatureRenderer signature
    if 'SkinLayers3D.java' in rel:
        text = re.sub(
            r'public void render\(AbstractClientPlayerEntity (\w+),([^)]+)\)\s*\{',
            r'public void render(net.minecraft.entity.LivingEntity _entity,\2) {\n            if (!(_entity instanceof AbstractClientPlayerEntity)) return;\n            AbstractClientPlayerEntity \1 = (AbstractClientPlayerEntity) _entity;',
            text,
        )

    # NativeNametagStyleMixin: BufferBuilder.method_9731(FFFF)V returns void
    if 'NativeNametagStyleMixin.java' in rel:
        text = text.replace(
            'target = "Lnet/minecraft/client/render/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/BufferBuilder;"',
            'target = "Lnet/minecraft/client/render/BufferBuilder;method_9731(FFFF)V"',
        )
        text = text.replace(
            'private BufferBuilder bat$bgColor(BufferBuilder buf, float r, float g, float b, float a) {',
            'private void bat$bgColor(BufferBuilder buf, float r, float g, float b, float a) {',
        )
        text = re.sub(r'return buf\.color\(([^;]+)\);', r'buf.method_9731(\1);', text)

    # Vertices.java helpers
    if 'Vertices.java' in rel:
        text = text.replace('return v.vertex(x, y, z);', 'v.method_9742(x, y, z); return v;')
        text = text.replace('return v.normal(x, y, z);', 'v.method_21808(x, y, z); return v;')
        text = text.replace(
            'return v.color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);',
            'v.method_21805((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF); return v;',
        )

    if 'MotionBlurMixin.java' in rel:
        text = text.replace('@Inject(method = "render", at = @At("HEAD"))', '@Inject(method = "method_9775", at = @At("HEAD"))')
        text = text.replace('private void bat$prepareBlur(float tickDelta, long nanoTime, CallbackInfo ci)', 'private void bat$prepareBlur(float tickDelta, CallbackInfo ci)')

    if 'ServerEntryExtrasMixin.java' in rel:
        text = text.replace('render(IIIIIIIZ)V', 'method_6700(IIIIIIIZ)V')
        text = text.replace('mouseClicked(IIIIII)Z', 'method_6699(IIIIII)Z')

    if 'RainCustomizerMixin.java' in rel:
        text = text.replace(
            'Lnet/minecraft/client/render/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/BufferBuilder;',
            'Lnet/minecraft/client/render/BufferBuilder;method_9731(FFFF)V',
        )

    text = _convert_buffer_calls(text)
    return text
