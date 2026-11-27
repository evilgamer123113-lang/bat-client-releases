"""Actual pre-components 1.20.4 APIs. Applied after the shared 1.20.6 backport.

Generated code is still subject to compilation, Mixin and native game gates.
"""
import re


def adapt(name, value):
    if name.endswith('ForgeHudMixin.java'):
        value = value.replace(
            '@Mixin(InGameHud.class)',
            '@Mixin({InGameHud.class, net.minecraftforge.client.gui.overlay.ForgeGui.class})',
        )
    if name.endswith('ForgeWorldFrame.java'):
        # Forge 49 supplies both position and normal matrices in its PoseStack.
        value = value.replace('set(event.getPoseStack());', 'set(event.getPoseStack().peek().getPositionMatrix());')
        value = value.replace('set(event.getPoseStack()).invert().transpose();', 'set(event.getPoseStack().peek().getNormalMatrix());')
    if name.endswith('ServerHudMixin.java'):
        value = value.replace('DrawContext;F)V', 'DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V')
        value = value.replace('DrawContext context, float ticks, CallbackInfo ci', 'DrawContext context, net.minecraft.scoreboard.ScoreboardObjective objective, CallbackInfo ci')
    if name.endswith('ChatHudMixin.java'):
        value = value.replace('@Shadow @Final private List<ChatHudLine.Visible> visibleMessages;',
                              '@Shadow @Final private List<ChatHudLine.Visible> visibleMessages;\n    @Shadow @Final private List<ChatHudLine> messages;')
        value = value.replace('int mouseY,boolean focused,CallbackInfo ci)', 'int mouseY,CallbackInfo ci)')
        value = value.replace('@Inject(method="addVisibleMessage",at=@At("HEAD"),cancellable=true)',
            '@Inject(method="addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",at=@At("HEAD"),cancellable=true)')
        value = value.replace('private void bat$visible(ChatHudLine message,CallbackInfo ci) {',
            '''private void bat$visible(net.minecraft.text.Text text,net.minecraft.network.message.MessageSignatureData signature,int tick,net.minecraft.client.gui.hud.MessageIndicator indicator,boolean refresh,CallbackInfo ci) {
        ChatHudLine message=new ChatHudLine(tick,text,signature,indicator);
        if(!refresh){messages.add(0,message);while(messages.size()>100)messages.remove(messages.size()-1);}''')
    if name.endswith('MotionBlurMixin.java'):
        value = value.replace('bat$blurWorld(float counter, long frameTime, CallbackInfo ci)',
                              'bat$blurWorld(float counter, long frameTime, net.minecraft.client.util.math.MatrixStack matrices, CallbackInfo ci)')
    if name.endswith('InventoryNametagMixin.java'):
        value = value.replace('DrawContext;FFFLorg/joml/Vector3f;', 'DrawContext;FFILorg/joml/Vector3f;')
        value = value.replace('float x, float y, float size, Vector3f offset', 'float x, float y, int size, Vector3f offset')
    if name.endswith(('PlayerNametagMixin.java', 'NativeNametagStyleMixin.java')):
        value = value.replace('VertexConsumerProvider;IF)V', 'VertexConsumerProvider;I)V')
        value = re.sub(r',\s*float delta\)', ')', value)
    if name.endswith(('HealthIndicator.java', 'OwnNametagMixin.java')):
        value = value.replace('import net.minecraft.entity.EntityAttachmentType;', '')
        value = re.sub(r'(entity|player)\.getAttachments\(\)\.getPointNullable\(EntityAttachmentType.NAME_TAG,\s*0,\s*\1.getYaw\(delta\)\)',
                       r'new net.minecraft.util.math.Vec3d(0,\1.getHeight(),0)', value)
    if name.endswith('LocalChunkBatchMixin.java'):
        value = value.replace('ServerChunkLoadingManager', 'ThreadedAnvilChunkStorage')
    if name.endswith('AdventureHud.java'):
        value = value.replace('EntityAttributes.GENERIC_JUMP_STRENGTH', 'EntityAttributes.HORSE_JUMP_STRENGTH')
    if name.endswith('ResourcePackHud.java'):
        value = value.replace('.getId()', '.getName()').replace('.isRequired()', '.isAlwaysEnabled()')
    if name.endswith('NativeChat.java'):
        value = value.replace('message.getIcon()', '(message.indicator()==null?null:message.indicator().icon())')
    if name.endswith('WebScreen.java'):
        value = value.replace('new AdvancementsScreen(client.player.networkHandler.getAdvancementHandler(), this)',
                              'new AdvancementsScreen(client.player.networkHandler.getAdvancementHandler())')
        value = value.replace('client.gameRenderer.applyBlur(delta)', 'com.batclient.platform.mc1204.ScreenBlur.apply(client,delta)')
        value = value.replace('applyBlur(delta);', 'com.batclient.platform.mc1204.ScreenBlur.apply(client,delta);')
    if name.endswith('ItemPreviews.java'):
        value = value.replace('net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute>',
                              'net.minecraft.entity.attribute.EntityAttribute')
        value = value.replace('stack.applyAttributeModifiers(slot,', 'stack.getAttributeModifiers(slot).forEach(')
        value = value.replace('m.operation()', 'm.getOperation()').replace('m.value()', 'm.getValue()')
        value = value.replace('case ADD_VALUE', 'case ADDITION').replace('case ADD_MULTIPLIED_BASE', 'case MULTIPLY_BASE').replace('case ADD_MULTIPLIED_TOTAL', 'case MULTIPLY_TOTAL')
    # Only MatrixStack.Entry parameters/locals need conversion, not Matrix4f.
    entries = set(re.findall(r'MatrixStack\.Entry\s+(\w+)', value))
    entries.update(re.findall(r'var\s+(\w+)\s*=[^;\n]*?\.peek\(\)', value))
    for entry in entries:
        value = re.sub(r'(\b\w+)\.vertex\('+entry+r',', r'com.batclient.platform.mc1204.Vertices.vertex(\1,'+entry+',', value)
        value = value.replace('.normal('+entry+',', '.normal('+entry+'.getNormalMatrix(),')
    value = re.sub(r'(\b\w+)\.vertex\((\w+\.peek\(\)),', r'com.batclient.platform.mc1204.Vertices.vertex(\1,\2,', value)
    value = re.sub(r'\.normal\((\w+\.peek\(\)),', r'.normal(\1.getNormalMatrix(),', value)
    if name.endswith('/ClientHooks.java'):
        value, count = re.subn(r'<T extends net\.minecraft\.network\.packet\.CustomPayload> void playPayload\(.*?receiver\);',
            'void playPayload(net.minecraft.util.Identifier id,int maxBytes,java.util.function.BiConsumer<String,MinecraftClient> receiver);', value, flags=re.S)
        if count != 1:
            raise ValueError('1.20.4 payload contract source changed')
    if name.endswith('FabricClientHooks.java'):
        start = value.index('    @Override public <T extends net.minecraft.network.packet.CustomPayload>')
        end = value.index('    @Override public void disconnected', start)
        value = value[:start] + '''    @Override public void playPayload(net.minecraft.util.Identifier id,int maxBytes,java.util.function.BiConsumer<String,MinecraftClient> receiver){
        if(maxBytes<1||maxBytes>1_048_576)throw new IllegalArgumentException("Invalid payload limit");
        var queued=new java.util.concurrent.atomic.AtomicInteger();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(id,(client,handler,buffer,sender)->{
            if(buffer.readableBytes()>maxBytes)return;
            final String json;
            try{json=buffer.readString(8192);}catch(RuntimeException invalid){return;}
            if(queued.incrementAndGet()>256){queued.decrementAndGet();return;}
            client.execute(()->{try{if(client.getNetworkHandler()==handler)receiver.accept(json,client);}finally{queued.decrementAndGet();}});
        });
    }
''' + value[end:]
        # Pre-1.20.5 tooltip callback lacks the registry lookup argument.
        value = value.replace('(stack,context,type,lines)->', '(stack,context,lines)->')
    if name.endswith('ForgeClientHooks.java'):
        start = value.index('    @Override public <T extends net.minecraft.network.packet.CustomPayload>')
        end = value.index('    public ForgeClientHooks()', start)
        original = value[start:end]
        original = re.sub(r'@Override public <T extends .*?receiver\)\{',
            '@Override public void playPayload(net.minecraft.util.Identifier id,int maxBytes,java.util.function.BiConsumer<String,MinecraftClient> receiver){', original, count=1, flags=re.S)
        original = original.replace('ChannelBuilder.named(id.id())', 'ChannelBuilder.named(id)')
        original = original.replace('new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bytes),handler.getRegistryManager())',
                                    'new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bytes))')
        original = original.replace('codec.decode(buffer)', 'buffer.readString(8192)')
        value = value[:start]+original+value[end:]
    if name.endswith('WaypointNetwork.java'):
        value = '''package com.batclient;
import com.google.gson.JsonParser;
import com.batclient.platform.ClientHookServices;
import net.minecraft.util.Identifier;
/** Same bounded connected-server JSON protocol over 1.20.4 plugin messages. */
public final class WaypointNetwork {
    private static long window;private static int count;
    private WaypointNetwork(){}
    public static void init(){
        ClientHookServices.get().playPayload(new Identifier("batclient","waypoints"),32773,(json,client)->{
            long now=System.nanoTime();if(now-window>1_000_000_000L){window=now;count=0;}if(++count>256)return;
            try{Waypoints.receive(JsonParser.parseString(json).getAsJsonObject(),client);}catch(RuntimeException ignored){}
        });
    }
}
'''
    return value
