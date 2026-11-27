"""Real Yarn 1.18.2+build.4 API adapters layered on top of 1.19.2."""


def adapt(path: str, value: str) -> str:
    value = value.replace("net.minecraft.util.Identifier.of(", "new net.minecraft.util.Identifier(")
    value = value.replace("Identifier.of(", "new Identifier(")
    value = value.replace("net.minecraft.text.Text.literal(", "new net.minecraft.text.LiteralText(")
    value = value.replace("net.minecraft.text.Text.translatable(", "new net.minecraft.text.TranslatableText(")
    value = value.replace("net.minecraft.text.Text.empty()", 'new net.minecraft.text.LiteralText("")')
    value = value.replace("Text.literal(", "new net.minecraft.text.LiteralText(")
    value = value.replace("Text.translatable(", "new net.minecraft.text.TranslatableText(")
    value = value.replace("Text.empty()", 'new net.minecraft.text.LiteralText("")')
    value = value.replace("AbstractHorseEntity", "HorseBaseEntity")
    value = value.replace("Items.RECOVERY_COMPASS", "Items.COMPASS")
    value = value.replace(".getUuidOrNull()", ".getProfile().getId()")

    # GameOptions fields directly in 1.18.2 (pre-SimpleOption).
    value = value.replace(".getMaxFps().getValue()", ".maxFps")
    value = value.replace(".getMaxFps().setValue(", ".maxFps = (")
    value = value.replace(".getEnableVsync().getValue()", ".enableVsync")
    value = value.replace(".getEnableVsync().setValue(", ".enableVsync = (")
    value = value.replace(".getRawMouseInput().getValue()", ".rawMouseInput")
    value = value.replace(".getRawMouseInput().setValue(", ".rawMouseInput = (")
    value = value.replace(".getViewDistance().getValue()", ".viewDistance")
    value = value.replace(".getViewDistance().setValue(", ".viewDistance = (")
    value = value.replace(".getGamma().getValue()", ".gamma")
    value = value.replace(".getGamma().setValue(", ".gamma = (")

    # ServerList.add(ServerInfo) takes 1 argument in 1.18.2.
    value = value.replace(".getServerList().add(server, false)", ".getServerList().add(server)")
    value = value.replace(
        '.getServerList().add(new ServerInfo("zz-bat-menu-probe", "127.0.0.1:25565", false), false)',
        '.getServerList().add(new ServerInfo("zz-bat-menu-probe", "127.0.0.1:25565", false))',
    )

    # Forge 40.3.0 (1.18.2) event and HUD names.
    value = value.replace("net.minecraftforge.client.event.RenderHighlightEvent.Block", "net.minecraftforge.client.event.DrawSelectionEvent.HighlightBlock")
    value = value.replace("net.minecraftforge.client.event.RenderHighlightEvent", "net.minecraftforge.client.event.DrawSelectionEvent")
    value = value.replace("RenderHighlightEvent.Block", "DrawSelectionEvent.HighlightBlock")
    value = value.replace("ScreenEvent.Init.Pre", "ScreenEvent.InitScreenEvent.Pre")
    value = value.replace("ClientPlayerNetworkEvent.LoggingOut", "ClientPlayerNetworkEvent.LoggedOutEvent")
    value = value.replace("net.minecraftforge.client.gui.overlay.ForgeGui", "net.minecraftforge.client.gui.ForgeIngameGui")

    if path.endswith("ForgeWorldFrame.java"):
        value = value.replace("camera=event.getCamera();delta=event.getPartialTick();", "camera=event.getCamera();delta=event.getPartialTicks();")

    if path.endswith("com/batclient/web/WebWindow.java"):
        value = value.replace("BufferRenderer.drawWithShader(builder.end());", "builder.end(); BufferRenderer.draw(builder);")

    if path.endswith("DrawContext.java"):
        value = value.replace(
            "DrawableHelper.enableScissor(x1, y1, x2, y2);",
            "var win = MinecraftClient.getInstance().getWindow(); double scale = win.getScaleFactor(); "
            "com.mojang.blaze3d.systems.RenderSystem.enableScissor("
            "(int) (x1 * scale), (int) (win.getFramebufferHeight() - y2 * scale), "
            "Math.max(0, (int) ((x2 - x1) * scale)), Math.max(0, (int) ((y2 - y1) * scale)));",
        )
        value = value.replace("DrawableHelper.disableScissor();", "com.mojang.blaze3d.systems.RenderSystem.disableScissor();")

    if path.endswith("com/batclient/web/WebScreen.java"):
        value = value.replace("import net.minecraft.client.gui.screen.MessageScreen;", "import net.minecraft.client.gui.screen.SaveLevelScreen;")
        value = value.replace("new MessageScreen(", "new SaveLevelScreen(")
        value = value.replace("CreateWorldScreen.create(client, this);", "CreateWorldScreen.create(this);")

    if path.endswith("com/batclient/accounts/AccountHeads.java"):
        value = value.replace(
            "if(resource.isPresent())try(var stream=resource.get().getInputStream()){cached.png=face(stream.readNBytes(LIMIT+1));}",
            "if(resource!=null)try(var stream=resource.getInputStream()){cached.png=face(stream.readNBytes(LIMIT+1));}",
        )

    if path.endswith("com/batclient/HudRenderer.java"):
        value = value.replace(
            "{com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,player.getSkinTexture());net.minecraft.client.gui.PlayerSkinDrawer.draw(ctx.getMatrices(),0,0,24);}",
            "{com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,player.getSkinTexture());"
            "net.minecraft.client.gui.DrawableHelper.drawTexture(ctx.getMatrices(),0,0,24,24,8.0f,8.0f,8,8,64,64);"
            "net.minecraft.client.gui.DrawableHelper.drawTexture(ctx.getMatrices(),0,0,24,24,40.0f,8.0f,8,8,64,64);}",
        )

    if path.endswith("com/batclient/mixin/ClientAccountMixin.java"):
        value = value.replace("import net.minecraft.client.realms.RealmsPeriodicCheckers;\n", "")
        value = value.replace("import net.minecraft.client.util.ProfileKeys;\n", "")
        value = value.replace("import net.minecraft.client.report.AbuseReportContext;\n", "")
        value = value.replace("import net.minecraft.client.report.ReporterEnvironment;\n", "")
        value = value.replace("    @Shadow @Final @Mutable private ProfileKeys profileKeys;\n", "")
        value = value.replace("    @Shadow @Final @Mutable private RealmsPeriodicCheckers realmsPeriodicCheckers;\n", "")
        value = value.replace("    @Shadow private AbuseReportContext abuseReportContext;\n", "")
        value = value.replace("        profileKeys=new ProfileKeys(userApiService,session.getProfile().getId(),client.runDirectory.toPath());\n", "")
        value = value.replace("        abuseReportContext=AbuseReportContext.create(ReporterEnvironment.ofIntegratedServer(),userApiService);\n", "")
        value = value.replace("        realmsPeriodicCheckers=new RealmsPeriodicCheckers(RealmsClient.createRealmsClient(client));\n", "")

    if path.endswith("com/batclient/mixin/InventoryScissorAnimationMixin.java"):
        value = (
            "package com.batclient.mixin;\n"
            "import net.minecraft.client.gui.DrawableHelper;\n"
            "import org.spongepowered.asm.mixin.Mixin;\n"
            "@Mixin(DrawableHelper.class)\n"
            "public class InventoryScissorAnimationMixin {\n"
            "}\n"
        )

    if path.endswith("com/batclient/mixin/WorldListFavoritesMixin.java"):
        value = (
            "package com.batclient.mixin;\n"
            "import com.batclient.WorldFavorites;\n"
            "import net.minecraft.client.gui.screen.world.WorldListWidget;\n"
            "import net.minecraft.world.level.storage.LevelSummary;\n"
            "import org.spongepowered.asm.mixin.Mixin;\n"
            "import org.spongepowered.asm.mixin.Shadow;\n"
            "import org.spongepowered.asm.mixin.injection.At;\n"
            "import org.spongepowered.asm.mixin.injection.Inject;\n"
            "import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;\n"
            "import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;\n"
            "import java.util.Comparator;\n"
            "import java.util.List;\n"
            "import java.util.function.Supplier;\n"
            "@Mixin(WorldListWidget.class)\n"
            "public abstract class WorldListFavoritesMixin {\n"
            "    @Shadow private List<LevelSummary> levels;\n"
            '    @Inject(method = "getRowWidth", at = @At("RETURN"), cancellable = true)\n'
            "    private void bat$favoriteGutter(CallbackInfoReturnable<Integer> cir) {\n"
            "        cir.setReturnValue(cir.getReturnValue() + 20);\n"
            "    }\n"
            '    @Inject(method = "filter", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))\n'
            "    private void bat$favoritesFirst(Supplier<String> search, boolean load, CallbackInfo ci) {\n"
            "        if (levels != null) levels = levels.stream().sorted(Comparator.comparing(level -> !WorldFavorites.contains(level.getName()))).toList();\n"
            "    }\n"
            "}\n"
        )

    if path.endswith("com/batclient/mixin/WorldListFavoritesAccessor.java"):
        value = (
            "package com.batclient.mixin;\n"
            "import net.minecraft.client.gui.screen.world.WorldListWidget;\n"
            "import net.minecraft.world.level.storage.LevelSummary;\n"
            "import org.spongepowered.asm.mixin.Mixin;\n"
            "import org.spongepowered.asm.mixin.gen.Accessor;\n"
            "import org.spongepowered.asm.mixin.gen.Invoker;\n"
            "import java.util.List;\n"
            "import java.util.function.Supplier;\n"
            "@Mixin(WorldListWidget.class)\n"
            "public interface WorldListFavoritesAccessor {\n"
            '    @Accessor("levels") List<LevelSummary> bat$getLevels();\n'
            '    @Invoker("filter") void bat$refresh(Supplier<String> search, boolean load);\n'
            "}\n"
        )

    if path.endswith("com/batclient/mixin/WorldEntryFavoriteMixin.java"):
        value = value.replace("@Mixin(WorldListWidget.WorldEntry.class)", "@Mixin(WorldListWidget.Entry.class)")
        value = value.replace("selected instanceof WorldFavorites.Entry entry", "(Object) selected instanceof WorldFavorites.Entry entry")
        value = value.replace("list.bat$refresh(list.bat$getSearch(), list.bat$getLevels());", 'list.bat$refresh(() -> "", false);')
        value = value.replace(
            "if (entry instanceof WorldListWidget.WorldEntry world && entry instanceof WorldFavorites.Entry favorite\n"
            "                            && folder.equals(favorite.bat$worldFolder())) field_19135.setSelected(world);",
            "if ((Object) entry instanceof WorldFavorites.Entry favorite\n"
            "                            && folder.equals(favorite.bat$worldFolder())) field_19135.setSelected(entry);",
        )

    if path.endswith("com/batclient/mixin/ChatSenderMixin.java"):
        value = (
            "package com.batclient.mixin;\n"
            "import net.minecraft.client.network.ClientPlayNetworkHandler;\n"
            "import org.spongepowered.asm.mixin.Mixin;\n"
            "@Mixin(ClientPlayNetworkHandler.class)\n"
            "public class ChatSenderMixin {\n"
            "}\n"
        )

    if path.endswith("com/batclient/mixin/ChatHudMixin.java"):
        value = value.replace("List<ChatHudLine.Visible> visibleMessages;", "List<ChatHudLine<OrderedText>> visibleMessages;")
        value = value.replace("List<ChatHudLine> messages;", "List<ChatHudLine<net.minecraft.text.Text>> messages;")
        value = value.replace("visibleMessages.get(index).content()", "visibleMessages.get(index).getText()")
        value = value.replace("@Shadow protected abstract void refresh();", "@Shadow public abstract void reset();")
        value = value.replace("if(bat$state().configure())refresh();", "if(bat$state().configure())reset();")
        value = value.replace('@Inject(method="refresh",at=@At("HEAD"))', '@Inject(method="reset",at=@At("HEAD"))')
        value = value.replace('@Inject(method="refresh",at=@At("RETURN"))', '@Inject(method="reset",at=@At("RETURN"))')
        value = value.replace("    @Shadow private double toChatLineX(double x){throw new AssertionError();}\n", "")
        value = value.replace("    @Shadow private double toChatLineY(double y){throw new AssertionError();}\n", "")
        value = value.replace("    @Shadow private int getMessageIndex(double y){throw new AssertionError();}\n", "")
        value = value.replace(
            "int index=getMessageIndex(toChatLineY(mouseY-com.batclient.ChatOpeningAnimation.offset()));",
            "double scale=getChatScale();int lh=(int)(9.0*(client.options.chatLineSpacing+1.0));"
            "double cy=(client.getWindow().getScaledHeight()-40-(mouseY-com.batclient.ChatOpeningAnimation.offset()))/scale;"
            "int index=(int)Math.floor(cy/lh)+scrolledLines;",
        )
        value = value.replace(
            '@Inject(method="addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",at=@At("HEAD"),cancellable=true)\n'
            '    private void bat$visible(net.minecraft.text.Text text,net.minecraft.network.message.MessageSignatureData signature,int tick,net.minecraft.client.gui.hud.MessageIndicator indicator,boolean refresh,CallbackInfo ci) {\n'
            '        ChatHudLine message=new ChatHudLine(tick,text,signature,indicator);',
            '@Inject(method="addMessage(Lnet/minecraft/text/Text;IIZ)V",at=@At("HEAD"),cancellable=true)\n'
            '    private void bat$visible(net.minecraft.text.Text text,int messageId,int tick,boolean refresh,CallbackInfo ci) {\n'
            '        ChatHudLine<net.minecraft.text.Text> message=new ChatHudLine<>(tick,text,messageId);',
        )

    if path.endswith("com/batclient/NativeChat.java"):
        value = value.replace("import net.minecraft.client.gui.PlayerSkinDrawer;\n", "")
        value = value.replace("import net.minecraft.network.message.MessageSignatureData;\n", "")
        value = value.replace("Map<MessageSignatureData,UUID> SENDERS", "Map<Object,UUID> SENDERS")
        value = value.replace("public static void remember(MessageSignatureData signature,UUID sender)", "public static void remember(Object signature,UUID sender)")
        value = value.replace(
            "text=line.content();this.sender=sender;indicator=line.indicator();tick=line.creationTick();",
            "text=line.getText();this.sender=sender;indicator=null;tick=line.getCreationTick();",
        )
        value = value.replace("Group(ChatHudLine line,", "Group(ChatHudLine<Text> line,")
        value = value.replace("private UUID sender(MinecraftClient c,ChatHudLine line) {\n        UUID known=line.headerSignature()==null?null:SENDERS.get(line.headerSignature());\n        if(known!=null)return known;", "private UUID sender(MinecraftClient c,ChatHudLine<Text> line) {")
        value = value.replace("line.content().getString()", "line.getText().getString()")
        value = value.replace(
            "public boolean append(MinecraftClient c,ChatHudLine message,List<ChatHudLine.Visible> visible,",
            "public boolean append(MinecraftClient c,ChatHudLine<Text> message,List<ChatHudLine<OrderedText>> visible,",
        )
        value = value.replace(
            "boolean merge=last!=null && Objects.equals(last.indicator,message.indicator()) &&\n"
            "            ChatFlow.repeats(message.content(),last.text,sender,last.sender,message.creationTick(),last.tick,HudConfig.setNumber(repeats,\"window\",10));",
            "boolean merge=last!=null &&\n"
            "            ChatFlow.repeats(message.getText(),last.text,sender,last.sender,message.getCreationTick(),last.tick,HudConfig.setNumber(repeats,\"window\",10));",
        )
        value = value.replace("visible.removeIf(line->{Line metadata=rendered.get(line.content());", "visible.removeIf(line->{Line metadata=rendered.get(line.getText());")
        value = value.replace("Text text=message.content();", "Text text=message.getText();")
        value = value.replace("var icon=(message.indicator()==null?null:message.indicator().icon());\n        int available=width-(icon==null?0:icon.width+6);", "int available=width;")
        value = value.replace("visible.add(0,new ChatHudLine.Visible(message.creationTick(),content,message.indicator(),i==lines.size()-1));", "visible.add(0,new ChatHudLine<>(message.getCreationTick(),content,message.getId()));")
        value = value.replace("for(var line:visible)active.add(line.content());", "for(var line:visible)active.add(line.getText());")
        value = value.replace(
            "PlayerSkinDrawer.draw(ctx.getMatrices(),x,y,8,HudConfig.bool(heads,\"hat\",true),false);",
            "net.minecraft.client.gui.DrawableHelper.drawTexture(ctx.getMatrices(),x,y,8,8,8.0f,8.0f,8,8,64,64);"
            "if(HudConfig.bool(heads,\"hat\",true))net.minecraft.client.gui.DrawableHelper.drawTexture(ctx.getMatrices(),x,y,8,8,40.0f,8.0f,8,8,64,64);",
        )

    if path.endswith("VisualParitySmoke.java"):
        import re
        value = value.replace("if (client.currentScreen instanceof net.minecraft.client.gui.screen.AccessibilityOnboardingScreen) {\n                    client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());\n                    ticks = 0;\n                    return;\n                }", "")
        value = value.replace("net.minecraft.resource.DataConfiguration.SAFE_MODE", "net.minecraft.resource.DataPackSettings.SAFE_MODE")
        value = re.sub(
            r'client\.createIntegratedServerLoader\(\)\.createAndStart\([^;]+\);',
            'var drm = net.minecraft.util.registry.DynamicRegistryManager.createAndLoad();\n'
            '        client.createWorld("VisualParity-" + System.currentTimeMillis(), info, drm,\n'
            '            net.minecraft.world.gen.GeneratorOptions.getDefaultOptions(drm));',
            value,
            flags=re.DOTALL,
        )

    return value
