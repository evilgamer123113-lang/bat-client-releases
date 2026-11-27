package com.batclient.platform.forge;

import com.batclient.platform.ClientHooks;
import com.batclient.platform.WorldFrame;
import com.batclient.platform.BlockTarget;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Real Forge events for the shared 1.21.1 lifecycle/key/HUD contract. */
public final class ForgeClientHooks implements ClientHooks {
    private static ForgeClientHooks active;
    private final List<Consumer<MinecraftClient>> start=new ArrayList<>(),stop=new ArrayList<>();
    private final List<HudDraw> overlays=new ArrayList<>();
    private final List<Runnable> worldStarts=new ArrayList<>(),worldEnds=new ArrayList<>();
    private final List<Consumer<WorldFrame>> entities=new ArrayList<>();
    private final List<BlockOutline> outlines=new ArrayList<>();
    private final List<java.util.function.Function<net.minecraft.item.tooltip.TooltipData,net.minecraft.client.gui.tooltip.TooltipComponent>> tooltips=new ArrayList<>();
    private final ForgeWorldFrame worldFrame=new ForgeWorldFrame();
    private final TargetView target=new TargetView();
    private boolean started,stopping;
    @Override public <T extends net.minecraft.network.packet.CustomPayload> void playPayload(net.minecraft.network.packet.CustomPayload.Id<T> id,
        net.minecraft.network.codec.PacketCodec<net.minecraft.network.RegistryByteBuf,T> codec,int maxBytes,java.util.function.BiConsumer<T,MinecraftClient> receiver){
        if(maxBytes<1||maxBytes>1_048_576)throw new IllegalArgumentException("Invalid Bat payload size");
        var queued=new java.util.concurrent.atomic.AtomicInteger();
        net.minecraftforge.network.ChannelBuilder.named(id.id()).optional().eventNetworkChannel().addListener((net.minecraftforge.event.network.CustomPayloadEvent event)->{
            var source=event.getSource();
            if(!source.isClientSide())return;
            source.setPacketHandled(true);
            var payload=event.getPayload();
            if(payload==null||payload.readableBytes()>maxBytes)return;
            if(queued.incrementAndGet()>256){queued.decrementAndGet();return;}
            byte[] bytes=new byte[payload.readableBytes()];payload.getBytes(payload.readerIndex(),bytes);
            source.enqueueWork(()->{
                var client=MinecraftClient.getInstance();
                try{
                    var handler=client.getNetworkHandler();
                    if(handler==null||handler.getConnection()!=source.getConnection())return;
                    var buffer=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bytes),handler.getRegistryManager());
                    try{receiver.accept(codec.decode(buffer),client);}finally{buffer.release();}
                }catch(RuntimeException ignored){/* Invalid or stale connected-server messages are ignored. */}
                finally{queued.decrementAndGet();}
            });
        });
    }
    public ForgeClientHooks(){
        if(active!=null)throw new IllegalStateException("Forge client hooks already registered");
        active=this;
        MinecraftForge.EVENT_BUS.addListener(this::firstTick);
        MinecraftForge.EVENT_BUS.addListener(this::worldStage);
        MinecraftForge.EVENT_BUS.addListener(this::highlight);
    }
    private void firstTick(TickEvent.ClientTickEvent.Post event){
        if(started)return;started=true;
        MinecraftClient client=MinecraftClient.getInstance();
        for(Consumer<MinecraftClient> listener:List.copyOf(start))listener.accept(client);
        start.clear();
    }
    @Override public KeyBinding registerKeyBinding(KeyBinding binding){
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.options==null||!client.isOnThread())throw new IllegalStateException("Register Bat keys during enqueued client setup");
        // Forge deliberately makes allKeys mutable. The constructor registers key lookup;
        // append here because RegisterKeyMappingsEvent precedes client setup.
        if(Arrays.stream(client.options.allKeys).noneMatch(key->key==binding)){
            KeyBinding[] keys=Arrays.copyOf(client.options.allKeys,client.options.allKeys.length+1);
            keys[keys.length-1]=binding;client.options.allKeys=keys;KeyBinding.updateKeysByCode();
        }
        return binding;
    }
    @Override public InputUtil.Key getBoundKey(KeyBinding binding){return binding.getKey();}
    @Override public void endTick(Consumer<MinecraftClient> callback){MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post e)->callback.accept(MinecraftClient.getInstance()));}
    @Override public void started(Consumer<MinecraftClient> callback){
        if(started){MinecraftClient client=MinecraftClient.getInstance();client.execute(()->callback.accept(client));}
        else start.add(callback);
    }
    @Override public void stopping(Consumer<MinecraftClient> callback){stop.add(callback);}
    @Override public void beforeScreen(ScreenInit callback){
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Pre event)->{
            MinecraftClient client=MinecraftClient.getInstance();
            callback.before(client,event.getScreen(),client.getWindow().getScaledWidth(),client.getWindow().getScaledHeight());
        });
    }
    @Override public void hud(HudDraw callback){overlays.add(callback);}
    @Override public void disconnected(Runnable callback){MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event)->callback.run());}
    @Override public void itemTooltip(java.util.function.BiConsumer<net.minecraft.item.ItemStack,java.util.List<net.minecraft.text.Text>> callback){
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.ItemTooltipEvent event)->callback.accept(event.getItemStack(),event.getToolTip()));
    }
    @Override public void tooltipComponent(java.util.function.Function<net.minecraft.item.tooltip.TooltipData,net.minecraft.client.gui.tooltip.TooltipComponent> factory){tooltips.add(factory);}
    public static net.minecraft.client.gui.tooltip.TooltipComponent createTooltip(net.minecraft.item.tooltip.TooltipData data){
        ForgeClientHooks hooks=active;if(hooks==null)return null;
        for(var factory:hooks.tooltips){var component=factory.apply(data);if(component!=null)return component;}
        return null;
    }
    @Override public void worldStart(Runnable callback){worldStarts.add(callback);}
    @Override public void worldEnd(Runnable callback){worldEnds.add(callback);}
    @Override public void afterEntities(Consumer<WorldFrame> callback){entities.add(callback);}
    @Override public void blockOutline(BlockOutline callback){outlines.add(callback);}
    private void worldStage(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||entities.isEmpty())return;
        worldFrame.stage(event);for(Consumer<WorldFrame> callback:entities)callback.accept(worldFrame);
    }
    private void highlight(RenderHighlightEvent.Block event){
        if(outlines.isEmpty())return;
        worldFrame.highlight(event);target.event=event;
        boolean vanilla=true;for(BlockOutline callback:outlines)vanilla&=callback.draw(worldFrame,target);
        if(!vanilla)event.setCanceled(true);
    }
    public static void fireWorldStart(){ForgeClientHooks hooks=active;if(hooks!=null&&!hooks.stopping)for(Runnable callback:hooks.worldStarts)callback.run();}
    public static void fireWorldEnd(){ForgeClientHooks hooks=active;if(hooks!=null&&!hooks.stopping)for(Runnable callback:hooks.worldEnds)callback.run();}
    public static void fireHud(net.minecraft.client.gui.DrawContext context){
        ForgeClientHooks hooks=active;if(hooks==null||hooks.stopping)return;
        for(HudDraw listener:hooks.overlays)listener.draw(context);
    }
    public static void fireStopping(MinecraftClient client){
        ForgeClientHooks hooks=active;if(hooks==null||hooks.stopping)return;hooks.stopping=true;
        for(Consumer<MinecraftClient> listener:List.copyOf(hooks.stop))listener.accept(client);
        hooks.stop.clear();
    }
    private static final class TargetView implements BlockTarget {
        private RenderHighlightEvent.Block event;
        @Override public net.minecraft.util.math.BlockPos blockPos(){return event.getTarget().getBlockPos();}
        @Override public net.minecraft.block.BlockState blockState(){return MinecraftClient.getInstance().world.getBlockState(blockPos());}
        @Override public net.minecraft.entity.Entity entity(){return event.getCamera().getFocusedEntity();}
    }
}
