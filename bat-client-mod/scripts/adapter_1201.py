from pathlib import Path
"""Actual 1.20.1 APIs, layered after the pre-components 1.20.4 adapters.

No stubs or feature removal. Full compilation and native checks remain required.
Signatures checked with javap against the genuine mapped 1.20.1 game JAR.
"""
def adapt(name, value):
    for newer, older in (
        ('net.minecraft.client.session.Session','net.minecraft.client.util.Session'),
        ('net.minecraft.client.session.ProfileKeys','net.minecraft.client.util.ProfileKeys'),
        ('net.minecraft.client.session.report.','net.minecraft.client.report.'),
        ('net.minecraft.client.session.telemetry.','net.minecraft.client.util.telemetry.'),
    ):
        value=value.replace(newer,older)
    value=value.replace('entry.getSkinTextures().texture()', 'entry.getSkinTexture()')
    value=value.replace('player.getSkinTextures()', 'player.getSkinTexture()')
    value=value.replace('DefaultSkinHelper.getSkinTextures(fallback).texture()', 'DefaultSkinHelper.getTexture(fallback)')
    if name.endswith('SkinLayers3D.java'):
        value=value.replace('var textures=player.getSkinTexture();Identifier skin=textures.texture();boolean slim=textures.model()==net.minecraft.client.util.SkinTextures.Model.SLIM;',
                            'Identifier skin=player.getSkinTexture();boolean slim="slim".equals(player.getModel());')
    if name.endswith('ClientAccounts.java'):
        value=value.replace('new Session(name,uuid,', 'new Session(name,uuid.toString(),')
        value=value.replace('new Session(identity.name(),identity.uuid(),', 'new Session(identity.name(),identity.uuid().toString(),')
    if name.endswith('BatClientMod.java'):
        value=value.replace('net.minecraft.client.network.ServerInfo.ServerType.OTHER','false')
    if name.endswith('WebScreen.java'):
        value=value.replace('double horizontalAmount, double verticalAmount)', 'double verticalAmount)')
        value=value.replace('window.onScroll(horizontalAmount, verticalAmount)', 'window.onScroll(0, verticalAmount)')
    if name.endswith('HudEditorScreen.java'):
        value=value.replace('double horizontal, double vertical)', 'double vertical)')
        value=value.replace('super.mouseScrolled(mouseX, mouseY, horizontal, vertical)', 'super.mouseScrolled(mouseX, mouseY, vertical)')
    if name.endswith('MultiplayerExtrasMixin.java'):
        value=value.replace('serverListWidget.setDimensionsAndPosition(width, Math.max(36, height - 64 - 50), 0, 50);',
                            'serverListWidget.updateSize(width, height, 50, Math.max(86, height - 64));serverListWidget.setLeftPos(0);')
    value=value.replace('ServerInfo.ServerType.OTHER','false')
    value=value.replace('serverInfo.isRealm()', 'client.isConnectedToRealms()')
    if name.endswith('OriginalPauseScreen.java'):
        value=value.replace('double horizontalAmount, double verticalAmount)', 'double verticalAmount)')
    value=value.replace('mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)', 'mouseScrolled(mouseX, mouseY, verticalAmount)')
    value=value.replace('renderBackground(DrawContext context, int mouseX, int mouseY, float delta)', 'renderBackground(DrawContext context)')
    value=value.replace('renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks)', 'renderBackground(DrawContext context)')
    if name.endswith(('Fabric1201Smoke.java','Fabric1194Smoke.java','Fabric1192Smoke.java','ForgeFullSmoke.java','VisualParitySmoke.java')):
        value=value.replace('createDimensionsRegistryHolder(),client.currentScreen)', 'createDimensionsRegistryHolder())')
        value=value.replace('createDimensionsRegistryHolder(), title)', 'createDimensionsRegistryHolder())')
    if name.endswith(('HudRenderer.java','HealthIndicator.java')):
        value=value.replace('import net.minecraft.scoreboard.ScoreboardDisplaySlot;','')
        value=value.replace('ScoreboardDisplaySlot.BELOW_NAME','2').replace('ScoreboardDisplaySlot.SIDEBAR','1')
    if name.endswith('HudRenderer.java'):
        value=value.replace('import net.minecraft.scoreboard.ScoreboardEntry;', 'import net.minecraft.scoreboard.ScoreboardPlayerScore;')
        value=value.replace('import net.minecraft.scoreboard.number.StyledNumberFormat;', '')
        value=value.replace('var slot = ScoreboardDisplaySlot.fromFormatting(c.player.getScoreboardTeam().getColor());', 'int slot = c.player.getScoreboardTeam().getColor().getColorIndex();')
        value=value.replace('slot == null ? null : board.getObjectiveForSlot(slot)', 'slot < 0 ? null : board.getObjectiveForSlot(3+slot)')
        value=value.replace('var format = objective.getNumberFormatOr(StyledNumberFormat.RED);','')
        value=value.replace('board.getScoreboardEntries(objective).stream().filter(entry -> !entry.hidden())','board.getAllPlayerScores(objective).stream().filter(entry -> !entry.getPlayerName().startsWith("#"))')
        value=value.replace('ScoreboardEntry::value','ScoreboardPlayerScore::getScore').replace('ScoreboardEntry::owner','ScoreboardPlayerScore::getPlayerName')
        value=value.replace('board.getScoreHolderTeam(entry.owner()), entry.name()', 'board.getPlayerTeam(entry.getPlayerName()), Text.literal(entry.getPlayerName())')
        value=value.replace('entry.formatted(format)', 'Text.literal(Integer.toString(entry.getScore())).formatted(net.minecraft.util.Formatting.RED)')
    if name.endswith('AccountSession.java'):
        value="""package com.batclient.accounts;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.util.Session;
/** Account-owned native profile properties, never sent to the web UI. */
public record AccountSession(Session session,UserApiService api,GameProfile profile) {
    public AccountSession {
        GameProfile copy=new GameProfile(profile.getId(),profile.getName());
        copy.getProperties().putAll(profile.getProperties());profile=copy;
    }
}
"""
    if name.endswith('ClientAccounts.java'):
        value=value.replace('import com.mojang.authlib.yggdrasil.ProfileResult;','')
        value=value.replace('CompletableFuture.completedFuture(new ProfileResult(new GameProfile(uuid,name))),CompletableFuture.completedFuture(UserApiService.OFFLINE_PROPERTIES)', 'new GameProfile(uuid,name)')
        value=value.replace('UserApiService.UserProperties properties=api.fetchProperties();', 'api.properties();')
        value=value.replace('ProfileResult result=client.getSessionService().fetchProfile(identity.uuid(),true);', 'GameProfile result=client.getSessionService().fillProfileProperties(new GameProfile(identity.uuid(),identity.name()),true);')
        value=value.replace('CompletableFuture.completedFuture(result),CompletableFuture.completedFuture(properties)', 'result')
    if name.endswith('ClientAccountMixin.java'):
        value=value.replace('import com.mojang.authlib.yggdrasil.ProfileResult;', 'import com.mojang.authlib.GameProfile;\nimport com.mojang.authlib.properties.PropertyMap;')
        value=value.replace('@Shadow @Final @Mutable private CompletableFuture<ProfileResult> gameProfileFuture;', '@Shadow @Final private PropertyMap sessionPropertyMap;')
        value=value.replace('    @Shadow @Final @Mutable private CompletableFuture<UserApiService.UserProperties> userPropertiesFuture;','')
        value=value.replace('return new AccountSession(session,userApiService,gameProfileFuture,userPropertiesFuture);', 'GameProfile profile=session.getProfile();profile.getProperties().putAll(sessionPropertyMap);return new AccountSession(session,userApiService,profile);')
        value=value.replace('gameProfileFuture=account.profile();userPropertiesFuture=account.properties();', 'sessionPropertyMap.clear();sessionPropertyMap.putAll(account.profile().getProperties());')
    if name.endswith('LocalChunkBatchMixin.java'):
        value=(Path(__file__).parent/'templates-1201/LocalChunkBatchMixin.java').read_text(encoding='utf-8')
    if name.endswith(('AdventureHud.java','HealthIndicator.java')):
        value=value.replace('ctx.drawGuiTexture(', 'com.batclient.platform.mc1201.GuiSprites.draw(ctx,')
        value=value.replace('MinecraftClient.getInstance().getGuiAtlasManager().getSprite(id)', 'com.batclient.platform.mc1201.GuiSprites.region(id)')
    if name.endswith('CustomCrosshairMixin.java'):
        value=value.replace('drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V', 'drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V')
        value=value.replace('Identifier sprite, int x, int y, int width, int height)', 'Identifier sprite, int x, int y, int u, int v, int width, int height)')
        value=value.replace('ctx.drawGuiTexture(sprite, x, y, width, height)', 'ctx.drawTexture(sprite, x, y, u, v, width, height)')
    if name.endswith('InventoryNametagMixin.java'):
        value=value.replace('drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V', 'drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIFFLnet/minecraft/entity/LivingEntity;)V')
        value=value.replace('int left, int top, int right, int bottom, int size,\n                                         float offset, float mouseX, float mouseY,', 'int x, int y, int size,\n                                         float mouseX, float mouseY,')
        value=value.replace('Math.max(1, right - left - 2)', 'Math.max(1, size * 2)')
        value=value.replace('drawEntity(Lnet/minecraft/client/gui/DrawContext;FFILorg/joml/Vector3f;Lorg/joml/Quaternionf;', 'drawEntity(Lnet/minecraft/client/gui/DrawContext;IIILorg/joml/Quaternionf;')
        value=value.replace('float x, float y, int size, Vector3f offset,', 'int x, int y, int size,')
    if name.endswith('SmoothChunkUploadMixin.java'):
        value=value.replace('@Shadow private boolean stopped;', '@Unique private boolean bat$stopped;')
        value=value.replace('&&!stopped', '&&!bat$stopped')
        value=value.replace('    @Inject(method="upload",at=@At("HEAD"))', '    @Inject(method="stop",at=@At("HEAD"))\n    private void bat$stopping(CallbackInfo ci){bat$stopped=true;}\n    @Inject(method="upload",at=@At("HEAD"))')
    if name.endswith('ForgeClientHooks.java'):
        value=value.replace('net.minecraftforge.network.ChannelBuilder.named(id).optional().eventNetworkChannel()', 'net.minecraftforge.network.NetworkRegistry.ChannelBuilder.named(id).networkProtocolVersion(()->"1").clientAcceptedVersions(version->true).serverAcceptedVersions(version->true).eventNetworkChannel()')
        value=value.replace('net.minecraftforge.event.network.CustomPayloadEvent event', 'net.minecraftforge.network.NetworkEvent.ClientCustomPayloadEvent event')
        value=value.replace('event.getSource()', 'event.getSource().get()')
        value=value.replace('if(!source.isClientSide())return;', 'if(source.getDirection()!=net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)return;')
        value=value.replace('source.getConnection()', 'source.getNetworkManager()')
        value=value.replace('TickEvent.ClientTickEvent.Post', 'TickEvent.ClientTickEvent')
        value=value.replace('if(started)return;started=true;', 'if(event.phase!=TickEvent.Phase.END||started)return;started=true;')
        value=value.replace('(TickEvent.ClientTickEvent e)->callback.accept(MinecraftClient.getInstance())', '(TickEvent.ClientTickEvent e)->{if(e.phase==TickEvent.Phase.END)callback.accept(MinecraftClient.getInstance());}')
    if name.endswith('WorldEntryFavoriteMixin.java'):
        value=value.replace('LevelSummary;isSelectable()Z', 'LevelSummary;isUnavailable()Z')
    if name.endswith('ServerEntryExtrasMixin.java'):
        value=value.replace('value = "INVOKE", target = "Lnet/minecraft/client/network/ServerInfo;getStatus()Lnet/minecraft/client/network/ServerInfo$Status;", ordinal = 0', 'value = "FIELD", target = "Lnet/minecraft/client/network/ServerInfo;online:Z", ordinal = 0')
    return value
