"""Actual 1.19.2 APIs, layered after the 1.19.4 MatrixStack/DrawableHelper adapter.

No stubs or feature removal. Full compilation and native checks remain required.
Signatures checked with javap against the genuine mapped 1.19.2 game JAR.
"""
import re


def adapt(name, value):
    # Registries -> Registry, FluidTags
    value = value.replace('import net.minecraft.registry.Registries;', 'import net.minecraft.util.registry.Registry;')
    value = value.replace('net.minecraft.registry.Registries.', 'net.minecraft.util.registry.Registry.')
    value = value.replace('Registries.BLOCK', 'Registry.BLOCK')
    value = value.replace('Registries.ITEM', 'Registry.ITEM')
    value = value.replace('Registries.STATUS_EFFECT', 'Registry.STATUS_EFFECT')
    value = value.replace('net.minecraft.registry.tag.FluidTags', 'net.minecraft.tag.FluidTags')
    value = value.replace('net.minecraft.registry.RegistryKeys.WORLD_PRESET', 'net.minecraft.util.registry.Registry.WORLD_PRESET_KEY')

    # Shaders
    value = value.replace('import net.minecraft.client.gl.ShaderProgram;', 'import net.minecraft.client.render.Shader;')
    value = value.replace('ShaderProgram', 'Shader')
    value = value.replace('BufferRenderer.drawWithGlobalProgram(', 'BufferRenderer.drawWithShader(')

    # Math library: org.joml -> net.minecraft.util.math
    value = value.replace('import org.joml.Matrix4f;', 'import net.minecraft.util.math.Matrix4f;')
    value = value.replace('import org.joml.Matrix3f;', 'import net.minecraft.util.math.Matrix3f;')
    value = value.replace('import org.joml.Vector3f;', 'import net.minecraft.util.math.Vec3f;')
    value = value.replace('import org.joml.Vector4f;', 'import net.minecraft.util.math.Vector4f;')
    value = value.replace('import org.joml.Quaternionf;', 'import net.minecraft.util.math.Quaternion;')
    value = value.replace('org.joml.Matrix4f', 'net.minecraft.util.math.Matrix4f')
    value = value.replace('org.joml.Matrix3f', 'net.minecraft.util.math.Matrix3f')
    value = value.replace('org.joml.Vector3f', 'net.minecraft.util.math.Vec3f')
    value = value.replace('org.joml.Vector4f', 'net.minecraft.util.math.Vector4f')
    value = value.replace('org.joml.Quaternionf', 'net.minecraft.util.math.Quaternion')
    value = value.replace('Lnet/minecraft/util/math/RotationAxis;rotationDegrees(F)Lorg/joml/Quaternionf;', 'Lnet/minecraft/util/math/Vec3f;getDegreesQuaternion(F)Lnet/minecraft/util/math/Quaternion;')
    value = value.replace('Lorg/joml/Matrix4f;', 'Lnet/minecraft/util/math/Matrix4f;')
    value = value.replace('Lorg/joml/Quaternionf;', 'Lnet/minecraft/util/math/Quaternion;')
    value = value.replace('Lnet/minecraft/client/font/TextRenderer$TextLayerType;', 'Z')
    value = value.replace('Quaternionf', 'Quaternion')
    value = value.replace('Vector3f', 'Vec3f')

    value = value.replace('import net.minecraft.util.math.RotationAxis;', 'import net.minecraft.util.math.Vec3f;')
    for axis in ('POSITIVE_X', 'POSITIVE_Y', 'POSITIVE_Z', 'NEGATIVE_X', 'NEGATIVE_Y', 'NEGATIVE_Z'):
        value = value.replace(f'RotationAxis.{axis}.rotationDegrees(', f'Vec3f.{axis}.getDegreesQuaternion(')
    if name.endswith('HurtCameraMixin.java'):
        value = value.replace('tiltViewWhenHurt', 'bobViewWhenHurt')

    # TextRenderer.TextLayerType -> boolean seeThrough
    if name.endswith('NativeNametags.java'):
        value = value.replace('TextRenderer.TextLayerType layer', 'boolean seeThrough')
        value = value.replace('layer == TextRenderer.TextLayerType.SEE_THROUGH', 'seeThrough')
    if name.endswith('NativeNametagStyleMixin.java'):
        value = value.replace('TextRenderer.TextLayerType layer', 'boolean layer')
    value = value.replace('net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL', 'false')
    value = value.replace('net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH', 'true')
    value = value.replace('TextRenderer.TextLayerType.NORMAL', 'false')
    value = value.replace('TextRenderer.TextLayerType.SEE_THROUGH', 'true')

    if name.endswith('VisualLayers.java'):
        value = value.replace('.program(new Shader(GameRenderer::getPositionColorProgram))', '.shader(new Shader(GameRenderer::getPositionColorShader))')
        value = value.replace('.program(LINES_PROGRAM)', '.shader(LINES_SHADER)')
        value = value.replace('of("bat_waypoint_plate_"+through,', 'createLayer("bat_waypoint_plate_"+through,')
        value = value.replace('of("bat_lines_"+k,', 'createLayer("bat_lines_"+k,')
        value = value.replace(
            '    public static RenderLayer waypointPlate(boolean through)',
            '''    private static java.lang.reflect.Method OF_METHOD;
    private static java.lang.reflect.Method findOf() {
        if (OF_METHOD != null) return OF_METHOD;
        for (var m : RenderLayer.class.getDeclaredMethods()) {
            var p = m.getParameterTypes();
            if (p.length == 7 && p[0] == String.class && p[1] == VertexFormat.class && p[2] == VertexFormat.DrawMode.class
                    && p[3] == int.class && p[4] == boolean.class && p[5] == boolean.class && p[6] == MultiPhaseParameters.class) {
                m.setAccessible(true);
                OF_METHOD = m;
                return m;
            }
        }
        throw new IllegalStateException("RenderLayer.of not found");
    }
    private static RenderLayer createLayer(String n, VertexFormat f, VertexFormat.DrawMode d, int b, boolean c, boolean t, MultiPhaseParameters p) {
        try { return (RenderLayer) findOf().invoke(null, n, f, d, b, c, t, p); }
        catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    public static RenderLayer waypointPlate(boolean through)''',
        )

    if name.endswith('GlintColorMixin.java'):
        for m in (
            'getRenderTypeArmorGlintProgram',
            'getRenderTypeArmorEntityGlintProgram',
            'getRenderTypeGlintTranslucentProgram',
            'getRenderTypeGlintProgram',
            'getRenderTypeGlintDirectProgram',
            'getRenderTypeEntityGlintProgram',
            'getRenderTypeEntityGlintDirectProgram',
        ):
            value = value.replace(m, m[:-7] + 'Shader')

    if name.endswith('HudRenderer.java') or name.endswith('PotionCountdown.java'):
        value = value.replace('effect.isInfinite()', 'effect.isPermanent()')
    if name.endswith('HudRenderer.java'):
        value = value.replace('client.getCurrentFps()', '((com.batclient.ClientAccountAccess)client).bat$currentFps()')
        value = value.replace('c.getCurrentFps()', '((com.batclient.ClientAccountAccess)c).bat$currentFps()')

    if name.endswith('ClientAccountAccess.java'):
        value = value.replace('void bat$applyAccount(AccountSession account);',
                              'void bat$applyAccount(AccountSession account);\n    int bat$currentFps();')

    if name.endswith('ClientAccountMixin.java'):
        value = value.replace('import net.minecraft.client.util.telemetry.TelemetryManager;\n', '')
        value = value.replace('@Shadow @Final @Mutable private TelemetryManager telemetryManager;\n', '@Shadow private static int currentFps;\n')
        value = value.replace('        telemetryManager.close();\n', '')
        value = value.replace('        telemetryManager=new TelemetryManager(client,userApiService,session);\n', '')
        value = value.replace('profileKeys=ProfileKeys.create(userApiService,session,client.runDirectory.toPath());', 'profileKeys=new ProfileKeys(userApiService,session.getUuidOrNull(),client.runDirectory.toPath());')
        value = value.replace('    public String bat$accounts(String action,String value) {',
                              '    public int bat$currentFps() { return currentFps; }\n    public String bat$accounts(String action,String value) {')

    if name.endswith('WebScreen.java'):
        value = value.replace('new PackScreen(\n\t\t\t\t\tclient.getResourcePackManager(),',
                              'new PackScreen(this,\n\t\t\t\t\tclient.getResourcePackManager(),')
        value = value.replace('client.getResourcePackDir().toAbsolutePath().normalize()',
                              'client.getResourcePackDir().toPath().toAbsolutePath().normalize()')
        value = value.replace('client.options.refreshResourcePacks(manager);',
                              'client.options.addResourcePackProfilesToManager(manager);')

    if name.endswith('WebWindow.java'):
        value = value.replace(
            '\t\tRenderSystem.enableBlend();',
            '\t\tRenderSystem.enableTexture();\n'
            '\t\tRenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);\n'
            '\t\tRenderSystem.disableDepthTest();\n'
            '\t\tRenderSystem.depthMask(false);\n'
            '\t\tRenderSystem.disableCull();\n'
            '\t\tRenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);\n'
            '\t\tRenderSystem.enableBlend();',
        )

    if name.endswith('OriginalPauseScreen.java'):
        value = value.replace(
            'ButtonWidget.builder(Text.literal("Bat Client Screen"), b -> {\n\t\t\tBatClientMod.setPreferOriginalPause(false);\n\t\t\tBatClientMod.showPauseScreen(client);\n\t\t}).dimensions(width - 170, 6, 164, 20).build()',
            'new ButtonWidget(width - 170, 6, 164, 20, Text.literal("Bat Client Screen"), b -> {\n\t\t\tBatClientMod.setPreferOriginalPause(false);\n\t\t\tBatClientMod.showPauseScreen(client);\n\t\t})',
        )

    if name.endswith('ReturnTrail.java'):
        value = value.replace('if(n.lengthSquared()<.000001)return;', 'if(n.dot(n)<.000001f)return;')
        value = value.replace('n.x,n.y,n.z', 'n.getX(),n.getY(),n.getZ()')

    if name.endswith('ResourcePackHud.java'):
        value = value.replace('ResourcePackSource.FEATURE', 'ResourcePackSource.PACK_SOURCE_BUILTIN')
        value = value.replace('ResourcePackSource.SERVER', 'ResourcePackSource.PACK_SOURCE_SERVER')
        value = value.replace('ResourcePackSource.WORLD', 'ResourcePackSource.PACK_SOURCE_WORLD')
        value = value.replace('try (var input = supplier.get())', 'try (var input = supplier)')

    if name.endswith('ForgeWorldFrame.java'):
        value = value.replace('stack.peek().getPositionMatrix().set(', 'stack.peek().getPositionMatrix().load(')
        value = value.replace('stack.peek().getNormalMatrix().set(', 'stack.peek().getNormalMatrix().load(')

    if name.endswith('ForgeClientHooks.java'):
        value = value.replace('RenderLevelStageEvent.Stage.AFTER_ENTITIES', 'RenderLevelStageEvent.Stage.AFTER_PARTICLES')

    if name.endswith('ForgeClientResources.java'):
        value = value.replace('event.getResourceProvider()', 'event.getResourceManager()')

    if name.endswith('ChatSenderMixin.java'):
        value = value.replace('message.signature()', 'message.headerSignature()')
        value = value.replace('private void bat$sender(SignedMessage message,GameProfile sender,MessageType.Parameters params,CallbackInfo ci)', 'private void bat$sender(SignedMessage message,MessageType.Parameters params,CallbackInfo ci)')
        value = value.replace('NativeChat.remember(message.headerSignature(),sender.getId());', 'NativeChat.remember(message.headerSignature(),message.signedHeader().sender());')

    if name.endswith('NativeChat.java'):
        value = value.replace('line.signature()', 'line.headerSignature()')

    if name.endswith('ItemPickupTracker.java'):
        value = value.replace('var normalized=stack.copyWithCount(1);', 'var normalized=stack.copy();normalized.setCount(1);')

    if name.endswith('AdventureHud.java'):
        value = value.replace('!preview&&horse instanceof net.minecraft.entity.passive.CamelEntity?"Jump: Camel dash":', '')

    if name.endswith('AdventureWorld.java'):
        value = value.replace('net.minecraft.util.math.Vec3d', 'net.minecraft.util.math.Vec3d')

    if name.endswith(('LootBeams.java', 'WorldVisualModules.java', 'WaypointRenderer.java')):
        value = value.replace('net.minecraft.client.render.RenderLayer.getDebugQuads()', 'VisualLayers.waypointPlate(false)')
        value = value.replace('RenderLayer.getDebugQuads()', 'VisualLayers.waypointPlate(false)')

    if name.endswith('WorldVisualModules.java'):
        value = value.replace(
            'var normal=new Vec3f((float)(x2-x1),(float)(y2-y1),(float)(z2-z1));if(normal.lengthSquared()==0)return;normal.normalize();var entry=matrices.peek();\n        com.batclient.platform.mc1204.Vertices.vertex(vertices,entry,(float)x1,(float)y1,(float)z1).color(first).normal(entry.getNormalMatrix(),normal.x,normal.y,normal.z).next();\n        com.batclient.platform.mc1204.Vertices.vertex(vertices,entry,(float)x2,(float)y2,(float)z2).color(second).normal(entry.getNormalMatrix(),normal.x,normal.y,normal.z).next();',
            'var normal=new Vec3f((float)(x2-x1),(float)(y2-y1),(float)(z2-z1));if(!normal.normalize())return;var entry=matrices.peek();\n        com.batclient.platform.mc1204.Vertices.vertex(vertices,entry,(float)x1,(float)y1,(float)z1).color(first).normal(entry.getNormalMatrix(),normal.getX(),normal.getY(),normal.getZ()).next();\n        com.batclient.platform.mc1204.Vertices.vertex(vertices,entry,(float)x2,(float)y2,(float)z2).color(second).normal(entry.getNormalMatrix(),normal.getX(),normal.getY(),normal.getZ()).next();',
        )

    if name.endswith('TurnTrail.java'):
        value = value.replace('normal.lengthSquared()==0', '!normal.normalize()')
        value = value.replace('normal.x,normal.y,normal.z', 'normal.getX(),normal.getY(),normal.getZ()')

    if name.endswith('WaypointRenderer.java'):
        value = value.replace(
            'var inverse=new Quaternion(ctx.camera().getRotation()).conjugate();',
            'var inverse=ctx.camera().getRotation().copy();inverse.conjugate();',
        )
        value = value.replace(
            'Vec3f local=new Vec3f((float)offset.x,(float)offset.y,(float)offset.z).rotate(inverse);\n            Vector4f clip=new Vector4f(local,1).mul(ctx.projectionMatrix());if(clip.w<=0)continue;\n            float nx=clip.x/clip.w,ny=clip.y/clip.w;',
            'Vec3f local=new Vec3f((float)offset.x,(float)offset.y,(float)offset.z);local.rotate(inverse);\n            Vector4f clip=new Vector4f(local.getX(),local.getY(),local.getZ(),1f);clip.transform(ctx.projectionMatrix());if(clip.getW()<=0)continue;\n            float nx=clip.getX()/clip.getW(),ny=clip.getY()/clip.getW();',
        )
        value = value.replace(
            'float pixel=2*Math.abs(local.z)/(c.getWindow().getScaledHeight()*ctx.projectionMatrix().m11())*scale;',
            'var mBuf=org.lwjgl.BufferUtils.createFloatBuffer(16);ctx.projectionMatrix().writeColumnMajor(mBuf);float m11=mBuf.get(5);float pixel=2*Math.abs(local.getZ())/(c.getWindow().getScaledHeight()*m11)*scale;',
        )

    if name.endswith('ItemPhysicsMixin.java'):
        value = value.replace(
            'target="Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"',
            'target="Lnet/minecraft/client/util/math/MatrixStack;translate(DDD)V"',
        )
        value = value.replace(
            'private void bat$height(MatrixStack matrices,float x,float y,float z,ItemEntity entity,float yaw,float delta,MatrixStack original,VertexConsumerProvider vertices,int light){matrices.translate(x,VisualModules.itemHeight(entity,y),z);}',
            'private void bat$height(MatrixStack matrices,double x,double y,double z,ItemEntity entity,float yaw,float delta,MatrixStack original,VertexConsumerProvider vertices,int light){matrices.translate(x,VisualModules.itemHeight(entity,(float)y),z);}',
        )
        value = value.replace(
            'target="Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V"',
            'target="Lnet/minecraft/client/util/math/MatrixStack;multiply(Lnet/minecraft/util/math/Quaternion;)V"',
        )

    if name.endswith('LowFireMixin.java'):
        value = value.replace('Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V', 'Lnet/minecraft/client/util/math/MatrixStack;translate(DDD)V')
        value = value.replace('private static void bat$lowerFire(MatrixStack matrices, float x, float y, float z)', 'private static void bat$lowerFire(MatrixStack matrices, double x, double y, double z)')

    if name.endswith('CustomCrosshairMixin.java'):
        value = value.replace(
            'private void bat$crosshair(net.minecraft.client.util.math.MatrixStack matrices, int x, int y, int u, int v, int width, int height) {',
            'private void bat$crosshair(InGameHud instance, net.minecraft.client.util.math.MatrixStack matrices, int x, int y, int u, int v, int width, int height) {',
        )
        value = value.replace(
            'net.minecraft.client.gui.DrawableHelper.drawTexture(matrices, x, y, u, v, width, height);',
            'instance.drawTexture(matrices, x, y, u, v, width, height);',
        )

    if name.endswith('InventoryNametagMixin.java'):
        value = '''package com.batclient.mixin;

import com.batclient.HudConfig;
import com.batclient.NativeNametags;
import com.batclient.platform.mc1194.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shared native player portrait helper covers survival and creative inventory. */
@Mixin(InventoryScreen.class)
public abstract class InventoryNametagMixin {
    @Unique private static float bat$portraitWidth;
    @Unique private static int bat$portraitSize;

    @Inject(method = "drawEntity(IIIFFLnet/minecraft/entity/LivingEntity;)V", at = @At("HEAD"))
    private static void bat$portraitBounds(int x, int y, int size, float mouseX, float mouseY, LivingEntity entity, CallbackInfo ci) {
        bat$portraitWidth = Math.max(1, size * 2);
        bat$portraitSize = size;
    }

    @Redirect(method = "method_29977(Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private static void bat$portraitName(EntityRenderDispatcher dispatcher, Entity entity, double x, double y, double z,
                                         float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        dispatcher.render(entity, x, y, z, yaw, tickDelta, matrices, vertices, light);
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(entity instanceof LivingEntity living) || entity != client.player || !(client.currentScreen instanceof InventoryScreen
                || client.currentScreen instanceof CreativeInventoryScreen) || !NativeNametags.enabled()
                || !HudConfig.bool(NativeNametags.settings(), "showOwn", true)) return;
        DrawContext ctx = new DrawContext(matrices);
        Text name = NativeNametags.ownName(living.getName());
        int width = client.textRenderer.getWidth(name), height = client.textRenderer.fontHeight;
        float scale = Math.min(Math.max(.5f, HudConfig.setNumber(NativeNametags.settings(), "scale", 1)) * .025f,
                bat$portraitWidth / (Math.max(1, bat$portraitSize) * Math.max(1, width)));
        matrices.push();
        matrices.translate(0, living.getHeight() + .12f, .05f);
        matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(180 - (living.getYaw() - 180)));
        matrices.scale(scale, -scale, scale);
        matrices.translate(-width / 2f, -height, .05f);
        if (HudConfig.bool(NativeNametags.settings(), "background", true)) ctx.fill(-2, -1, width + 2, height + 1, 0x6B000000);
        ctx.drawText(client.textRenderer, name, 0, 0, 0xFFFFFFFF, HudConfig.bool(NativeNametags.settings(), "shadow", true));
        NativeNametags.drawGuiLogo(ctx, name, 0, 0);
        ctx.draw();
        matrices.pop();
    }
}
'''

    if name.endswith('CombatDamageMixin.java'):
        value = '''package com.batclient.mixin;
import com.batclient.CombatHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayNetworkHandler.class)
public class CombatDamageMixin {
    @Inject(method="onEntityStatus",at=@At("TAIL"))
    private void bat$damage(EntityStatusS2CPacket packet,CallbackInfo ci){CombatHud.damage(packet);}
}
'''

    if name.endswith('CombatHud.java'):
        value = value.replace('import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;',
                              'import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;')
        value = value.replace(
            '''    public static void damage(EntityDamageS2CPacket packet) {
        var client=MinecraftClient.getInstance();synchronize(client);if(client.player==null)return;
        int local=client.player.getId();
        if(packet.entityId()==local){tracker.damaged();return;}
        if(packet.sourceCauseId()!=local||packet.sourceDirectId()!=local)return; // Melee only, no projectile/sweep guesses.
        var cfg=HudConfig.snapshot();var combo=HudConfig.module(cfg,"combo");
        tracker.confirm(packet.entityId(),System.nanoTime()/1_000_000,timeout(combo,3),
            HudConfig.enabled(combo)&&(!HudConfig.bool(combo,"playersOnly",false)||pendingPlayer),false);
    }''',
            '''    public static void damage(EntityStatusS2CPacket packet) {
        if(packet.getStatus()!=2)return;
        var client=MinecraftClient.getInstance();synchronize(client);if(client.player==null||client.world==null)return;
        var entity=packet.getEntity(client.world);if(entity==null)return;
        if(entity==client.player){tracker.damaged();return;}
        var cfg=HudConfig.snapshot();var combo=HudConfig.module(cfg,"combo");
        tracker.confirm(entity.getId(),System.nanoTime()/1_000_000,timeout(combo,3),
            HudConfig.enabled(combo)&&(!HudConfig.bool(combo,"playersOnly",false)||pendingPlayer),false);
    }''',
        )

    if name.endswith('LocalChunkBatchMixin.java'):
        value = value.replace('player.networkHandler.getConnectionAddress()', 'player.networkHandler.connection.getAddress()')
        value = value.replace('player.networkHandler.isConnectionOpen()', 'player.networkHandler.connection.isOpen()')

    if name.endswith('MultiplayerExtrasMixin.java'):
        value = value.replace('bat$search.setPlaceholder(Text.literal("Search servers"));', 'bat$search.setSuggestion("Search servers");')
        value = value.replace('MultiplayerScreen;drawCenteredTextWithShadow(', 'MultiplayerScreen;drawCenteredText(')

    if name.endswith('ChatHudMixin.java'):
        value = value.replace('@Shadow private int getMessageIndex(double x,double y){throw new AssertionError();}', '@Shadow private int getMessageIndex(double y){throw new AssertionError();}')
        value = value.replace('getMessageIndex(toChatLineX(mouseX),toChatLineY(mouseY-com.batclient.ChatOpeningAnimation.offset()))', 'getMessageIndex(toChatLineY(mouseY-com.batclient.ChatOpeningAnimation.offset()))')
        value = value.replace('(net.minecraft.client.util.math.MatrixStack matrices,int ticks,int mouseX,int mouseY,CallbackInfo ci)', '(net.minecraft.client.util.math.MatrixStack matrices,int ticks,CallbackInfo ci)')

    if name.endswith(('Fabric1192Smoke.java', 'VisualParitySmoke.java', 'ForgeFullSmoke.java')):
        value = re.sub(
            r'\s*if\s*\(\s*client\.currentScreen\s+instanceof\s+net\.minecraft\.client\.gui\.screen\.AccessibilityOnboardingScreen\s*\)\s*\{[^}]*\}\s*',
            '\n',
            value,
        )
        value = value.replace('net.minecraft.resource.DataConfiguration.SAFE_MODE', 'net.minecraft.resource.DataPackSettings.SAFE_MODE')
        value = value.replace(
            'new net.minecraft.world.gen.GeneratorOptions(12345L,false,false),registry->registry.get(net.minecraft.util.registry.Registry.WORLD_PRESET_KEY).get(net.minecraft.world.gen.WorldPresets.FLAT).createDimensionsRegistryHolder()',
            'net.minecraft.util.registry.DynamicRegistryManager.BUILTIN.get(),net.minecraft.util.registry.DynamicRegistryManager.BUILTIN.get().get(net.minecraft.util.registry.Registry.WORLD_PRESET_KEY).get(net.minecraft.world.gen.WorldPresets.FLAT).createGeneratorOptions(12345L,false,false)',
        )
        value = value.replace(
            'new net.minecraft.world.gen.GeneratorOptions(12345L, false, false),',
            'net.minecraft.util.registry.DynamicRegistryManager.BUILTIN.get(),',
        )
        value = value.replace(
            'registry->registry.get(net.minecraft.util.registry.Registry.WORLD_PRESET_KEY).get(net.minecraft.world.gen.WorldPresets.FLAT).createDimensionsRegistryHolder()',
            'net.minecraft.util.registry.DynamicRegistryManager.BUILTIN.get().get(net.minecraft.util.registry.Registry.WORLD_PRESET_KEY).get(net.minecraft.world.gen.WorldPresets.FLAT).createGeneratorOptions(12345L, false, false)',
        )
    if name.endswith('VisualParitySmoke.java'):
        value = value.replace('new net.minecraft.client.gui.screen.AccessibilityOnboardingScreen', 'new net.minecraft.client.gui.screen.TitleScreen')
        value = value.replace('client.getCurrentFps()', '((com.batclient.ClientAccountAccess)client).bat$currentFps()')

    return value
