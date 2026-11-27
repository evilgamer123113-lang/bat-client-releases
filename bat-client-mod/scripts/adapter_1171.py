"""Generate explicit 1.17.1 API deltas on top of 1.18.2 adaptation."""
import re


def adapt(name: str, value: str) -> str:
    # Authlib 2.x (1.17.1): SocialInteractionsService replaces UserApiService; 4-arg Session.
    if name.endswith(('AccountSession.java', 'AccountSwitch.java', 'ClientAccountMixin.java', 'ClientAccounts.java')):
        value = value.replace('com.mojang.authlib.minecraft.UserApiService', 'com.mojang.authlib.minecraft.SocialInteractionsService')
        value = value.replace('UserApiService.OFFLINE', 'new com.mojang.authlib.minecraft.OfflineSocialInteractions()')
        value = value.replace('UserApiService', 'SocialInteractionsService')
        value = value.replace('api.properties();\n', '')
        value = value.replace('yggdrasil.createSocialInteractionsService(accessToken)', 'yggdrasil.createSocialInteractionsService(accessToken)')
        value = value.replace(
            'new Session(username,uuid,accessToken,Optional.empty(),Optional.empty(),online?Session.AccountType.MSA:Session.AccountType.LEGACY)',
            'new Session(username,uuid,accessToken,online?"mojang":"legacy")',
        )
        value = value.replace(
            'new Session(name,uuid.toString(),"0",Optional.empty(),Optional.empty(),Session.AccountType.LEGACY)',
            'new Session(name,uuid.toString(),"0","legacy")',
        )
        value = value.replace(
            'new Session(identity.name(),identity.uuid().toString(),identity.token(),identity.xuid().isEmpty()?Optional.empty():Optional.of(identity.xuid()),Optional.empty(),Session.AccountType.MSA)',
            'new Session(identity.name(),identity.uuid().toString(),identity.token(),"mojang")',
        )
    if name.endswith('ClientAccountMixin.java'):
        value = value.replace('@Shadow @Final @Mutable private SocialInteractionsService userApiService;', '@Shadow @Final @Mutable private SocialInteractionsService socialInteractionsService;')
        value = value.replace('userApiService', 'socialInteractionsService')
        value = value.replace('socialInteractionsService=com.mojang.authlib.minecraft.SocialInteractionsService.OFFLINE;\n', '')

    if name.endswith('ChatHudMixin.java'):
        value = value.replace('@Shadow public abstract void scroll(int lines);', '@Shadow public abstract void scroll(double lines);')

    if name.endswith('LocalChunkBatchMixin.java'):
        value = value.replace('List<ServerPlayerEntity> getPlayersWatchingChunk(ChunkPos pos,boolean edge);', 'java.util.stream.Stream<ServerPlayerEntity> getPlayersWatchingChunk(ChunkPos pos,boolean edge);')
        value = value.replace('getPlayersWatchingChunk(chunk.getPos(),false).contains(player)', 'getPlayersWatchingChunk(chunk.getPos(),false).anyMatch(p->p==player)')
        value = value.replace('MutableObject<ChunkDataS2CPacket>', 'net.minecraft.network.Packet<?>[]')
        value = value.replace('new MutableObject<>()', 'new net.minecraft.network.Packet<?>[2]')

    # Gson 2.8.0 (1.17.1): no parseString, no public deepCopy, no JsonObject.keySet.
    value = value.replace('com.google.gson.JsonParser.parseString(', 'com.batclient.platform.mc1171.GsonCompat.parse(')
    value = value.replace('JsonParser.parseString(', 'com.batclient.platform.mc1171.GsonCompat.parse(')
    value = re.sub(r'([A-Za-z0-9_$.()"\[\]]+?)\.deepCopy\(\)', r'com.batclient.platform.mc1171.GsonCompat.copy(\1)', value)
    value = value.replace('.getAsJsonObject("bindings").keySet()', '.getAsJsonObject("bindings").entrySet().stream().map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet())')
    value = value.replace('map.keySet()', 'com.batclient.platform.mc1171.GsonCompat.keys(map)')

    # GameOptions keybindings (Yarn 1.17.1).
    for old_k, new_k in (
        ('.allKeys', '.keysAll'),
        ('.forwardKey', '.keyForward'),
        ('.leftKey', '.keyLeft'),
        ('.backKey', '.keyBack'),
        ('.rightKey', '.keyRight'),
        ('.jumpKey', '.keyJump'),
        ('.sneakKey', '.keySneak'),
        ('.sprintKey', '.keySprint'),
        ('.attackKey', '.keyAttack'),
        ('.useKey', '.keyUse'),
    ):
        value = value.replace(old_k, new_k)

    # MatrixStack.Entry & Entity world.
    value = value.replace('.getPositionMatrix()', '.getModel()')
    value = value.replace('.getNormalMatrix()', '.getNormal()')
    value = value.replace('.getWorld()', '.world')

    # Screen.shouldPause() -> isPauseScreen(), Screen.close() -> onClose().
    if name.endswith(('HudEditorScreen.java', 'WebScreen.java', 'OriginalPauseScreen.java')):
        value = value.replace('public boolean shouldPause()', 'public boolean isPauseScreen()')
        value = value.replace('public void close()', 'public void onClose()')
        value = value.replace('super.close();', 'super.onClose();')
        value = value.replace('this.close();', 'this.onClose();')
    if name.endswith('MenuRegressionChecks.java'):
        value = value.replace('multiplayer.close();', 'multiplayer.onClose();')

    # DynamicRegistryManager / GeneratorOptions in VisualParitySmoke.
    if name.endswith('VisualParitySmoke.java'):
        value = value.replace('net.minecraft.util.registry.DynamicRegistryManager.createAndLoad()', 'net.minecraft.util.registry.DynamicRegistryManager.create()')
        value = value.replace(
            'net.minecraft.world.gen.GeneratorOptions.getDefaultOptions(drm)',
            'net.minecraft.world.gen.GeneratorOptions.getDefaultOptions(drm.get(net.minecraft.util.registry.Registry.DIMENSION_TYPE_KEY), drm.get(net.minecraft.util.registry.Registry.BIOME_KEY), drm.get(net.minecraft.util.registry.Registry.CHUNK_GENERATOR_SETTINGS_KEY))',
        )

    # MinimapHud / HudRenderer biome lookup in 1.17.1.
    if name.endswith('MinimapHud.java'):
        value = value.replace(
            'client.world.getBiome(client.player.getBlockPos()).getKey().map(k->k.getValue().getPath().replace(\'_\',\' \')).orElse("Unknown")',
            'java.util.Optional.ofNullable(client.world.getRegistryManager().get(net.minecraft.util.registry.Registry.BIOME_KEY).getId(client.world.getBiome(client.player.getBlockPos()))).map(id->id.getPath().replace(\'_\',\' \')).orElse("Unknown")',
        )
    if name.endswith('HudRenderer.java'):
        value = value.replace(
            'client.world.getBiome(blockPos).getKey()',
            'client.world.getRegistryManager().get(net.minecraft.util.registry.Registry.BIOME_KEY).getKey(client.world.getBiome(blockPos))',
        )

    # Vertices.java: add color(VertexConsumer, int argb) helper.
    if name.endswith('Vertices.java'):
        value = value.replace(
            'public static VertexConsumer normal(VertexConsumer v,Matrix3f m,float x,float y,float z){return v.normal(m,x,y,z);}',
            'public static VertexConsumer normal(VertexConsumer v,Matrix3f m,float x,float y,float z){return v.normal(m,x,y,z);}\n    public static VertexConsumer color(VertexConsumer v,int argb){return v.color((argb>>16)&0xFF,(argb>>8)&0xFF,argb&0xFF,(argb>>>24)&0xFF);}',
        )

    # VertexConsumer.color(int) -> color(r, g, b, a) via Vertices.color helper.
    if name.endswith(('HealthIndicator.java', 'LootBeams.java', 'NativeNametags.java', 'ReturnTrail.java', 'SkinLayers3D.java', 'WaypointRenderer.java', 'WorldVisualModules.java')):
        value = re.sub(
            r'\.color\(([^;()]+(?:\([^()]*\)[^;()]*)*)\)',
            lambda m: m.group(0) if ',' in m.group(1) else f'.color((({m.group(1)})>>16)&0xFF,(({m.group(1)})>>8)&0xFF,({m.group(1)})&0xFF,((({m.group(1)})>>>24)&0xFF))',
            value,
        )

    # SmoothChunkRebuildMixin: 1.17.1 updateChunks(long) and ChunkBuilder.rebuild(BuiltChunk).
    if name.endswith('SmoothChunkRebuildMixin.java'):
        value = value.replace('import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;\n', '')
        value = value.replace('private void bat$begin(Camera camera,CallbackInfo ci){', 'private void bat$begin(long limitTime,CallbackInfo ci){')
        value = value.replace(
            'target="Lnet/minecraft/client/render/chunk/ChunkBuilder;rebuild(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;Lnet/minecraft/client/render/chunk/ChunkRendererRegionBuilder;)V"',
            'target="Lnet/minecraft/client/render/chunk/ChunkBuilder;rebuild(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;)V"',
        )
        value = value.replace(
            'private void bat$rebuild(ChunkBuilder builder,ChunkBuilder.BuiltChunk chunk,ChunkRendererRegionBuilder region){',
            'private void bat$rebuild(ChunkBuilder builder,ChunkBuilder.BuiltChunk chunk){',
        )
        value = value.replace('builder.rebuild(chunk,region);', 'builder.rebuild(chunk);')
        value = value.replace('chunk.scheduleRebuild(builder,region);', 'chunk.scheduleRebuild(builder);')

    # SmoothChunkUploadMixin: 1.17.1 ChunkBuilder.upload() returns boolean.
    if name.endswith('SmoothChunkUploadMixin.java'):
        value = value.replace('import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;', 'import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;\nimport org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;')
        value = value.replace('private void bat$begin(CallbackInfo ci){', 'private void bat$begin(CallbackInfoReturnable<Boolean> ci){')
        value = value.replace('private void bat$end(CallbackInfo ci){', 'private void bat$end(CallbackInfoReturnable<Boolean> ci){')

    # Forge 37.1.1 (1.17.1): GuiScreenEvent.InitGuiEvent.Pre + RenderWorldLastEvent + DrawSelectionEvent + fmllegacy.network.
    if name.endswith('ForgeClientHooks.java'):
        value = value.replace('import net.minecraftforge.client.event.ScreenEvent;', 'import net.minecraftforge.client.event.GuiScreenEvent;')
        value = value.replace('import net.minecraftforge.client.event.RenderLevelStageEvent;', 'import net.minecraftforge.client.event.RenderWorldLastEvent;')
        value = value.replace('ScreenEvent.InitScreenEvent.Pre', 'GuiScreenEvent.InitGuiEvent.Pre')
        value = value.replace('event.getScreen()', 'event.getGui()')
        value = value.replace('net.minecraftforge.network.', 'net.minecraftforge.fmllegacy.network.')
        value = value.replace('event.getCamera().getFocusedEntity()', 'event.getInfo().getFocusedEntity()')
        value = value.replace(
            'MinecraftForge.EVENT_BUS.addListener(this::firstTick);\n        MinecraftForge.EVENT_BUS.addListener(this::worldStage);\n        MinecraftForge.EVENT_BUS.addListener(this::highlight);',
            'MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,TickEvent.ClientTickEvent.class,this::firstTick);\n        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,RenderWorldLastEvent.class,this::worldStage);\n        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,net.minecraftforge.client.event.DrawSelectionEvent.HighlightBlock.class,this::highlight);',
        )
        value = value.replace(
            'MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e)->{if(e.phase==TickEvent.Phase.END)callback.accept(MinecraftClient.getInstance());});',
            'MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,TickEvent.ClientTickEvent.class,e->{if(e.phase==TickEvent.Phase.END)callback.accept(MinecraftClient.getInstance());});',
        )
        value = value.replace(
            'MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.InitGuiEvent.Pre event)->{',
            'MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,GuiScreenEvent.InitGuiEvent.Pre.class,event->{',
        )
        value = value.replace(
            'MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggedOutEvent event)->callback.run());',
            'MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggedOutEvent.class,event->callback.run());',
        )
        value = value.replace(
            'MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.ItemTooltipEvent event)->callback.accept(event.getItemStack(),event.getToolTip()));',
            'MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,net.minecraftforge.event.entity.player.ItemTooltipEvent.class,event->callback.accept(event.getItemStack(),event.getToolTip()));',
        )
        value = value.replace(
            'private void worldStage(RenderLevelStageEvent event){\n        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||entities.isEmpty())return;',
            'private void worldStage(RenderWorldLastEvent event){\n        if(entities.isEmpty())return;',
        )
    if name.endswith('WebController.java'):
        value = value.replace('LOGGER.error("Failed to load Ultralight natives", t);', 'LOGGER.error("Failed to load Ultralight natives", t); t.printStackTrace();')
        value = value.replace('LOGGER.error("Failed to create Ultralight renderer", t);', 'LOGGER.error("Failed to create Ultralight renderer", t); t.printStackTrace();')
    if name.endswith('ForgeClientResources.java'):
        value = value.replace(
            'modBus.addListener((net.minecraftforge.client.event.EntityRenderersEvent.AddLayers event)->{',
            'modBus.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,net.minecraftforge.client.event.EntityRenderersEvent.AddLayers.class,event->{',
        )
        value = value.replace(
            'modBus.addListener((RegisterShadersEvent event)->{',
            'modBus.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,RegisterShadersEvent.class,event->{',
        )
        value = value.replace(
            'event.registerShader(new Shader(event.getResourceManager(),id,format),loaded)',
            '''{ net.minecraft.resource.ResourceFactory wrapped = loc -> {
                if ("minecraft".equals(loc.getNamespace()) && loc.getPath().contains(id.getPath())) {
                    String path = "/assets/" + id.getNamespace() + "/" + loc.getPath();
                    java.io.InputStream stream = ForgeClientResources.class.getResourceAsStream(path);
                    if (stream == null) stream = ForgeClientResources.class.getResourceAsStream(path.replace("shaders/program/", "shaders/core/"));
                    if (stream != null) return new net.minecraft.resource.ResourceImpl(id.getNamespace(), new Identifier(id.getNamespace(), loc.getPath()), stream, null);
                }
                return event.getResourceManager().getResource(loc);
            };
            event.registerShader(new Shader(wrapped,id.getPath(),format),loaded); }''',
        )
        value = value.replace(
            'modBus.addListener((RegisterClientReloadListenersEvent event)->',
            'modBus.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,RegisterClientReloadListenersEvent.class,event->',
        )
    if name.endswith('ForgeWorldFrame.java'):
        value = value.replace('net.minecraftforge.client.event.RenderLevelStageEvent', 'net.minecraftforge.client.event.RenderWorldLastEvent')
        value = value.replace('event.getPoseStack()', 'event.getMatrixStack()')
        value = value.replace('event.getPartialTick()', 'event.getPartialTicks()')
        value = value.replace(
            'matrices=stack;camera=event.getCamera();frustum=event.getFrustum();\n        projection=event.getProjectionMatrix();delta=event.getPartialTicks();',
            'matrices=stack;camera=MinecraftClient.getInstance().gameRenderer.getCamera();frustum=null;\n        projection=com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix();delta=event.getPartialTicks();',
        )
        value = value.replace(
            'matrices=event.getMatrixStack();camera=event.getCamera();delta=event.getPartialTicks();\n        buffers=event.getMultiBufferSource();frustum=null;',
            'matrices=event.getMatrix();camera=event.getInfo();delta=event.getPartialTicks();\n        buffers=event.getBuffers();frustum=null;',
        )

    return value
