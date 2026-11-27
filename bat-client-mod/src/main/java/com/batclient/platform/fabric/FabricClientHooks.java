package com.batclient.platform.fabric;

import com.batclient.platform.ClientHooks;
import com.batclient.platform.WorldFrame;
import com.batclient.platform.BlockTarget;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class FabricClientHooks implements ClientHooks {
    private final WorldView frame=new WorldView();
    private final TargetView target=new TargetView();
    @Override public <T extends net.minecraft.network.packet.CustomPayload> void playPayload(net.minecraft.network.packet.CustomPayload.Id<T> id,
        net.minecraft.network.codec.PacketCodec<net.minecraft.network.RegistryByteBuf,T> codec,int maxBytes,java.util.function.BiConsumer<T,MinecraftClient> receiver){
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(id,codec);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(id,(payload,context)->receiver.accept(payload,context.client()));
    }
    @Override public void disconnected(Runnable callback){net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->callback.run());}
    @Override public void itemTooltip(java.util.function.BiConsumer<net.minecraft.item.ItemStack,java.util.List<net.minecraft.text.Text>> callback){
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack,context,type,lines)->callback.accept(stack,lines));
    }
    @Override public void tooltipComponent(java.util.function.Function<net.minecraft.item.tooltip.TooltipData,net.minecraft.client.gui.tooltip.TooltipComponent> factory){
        net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback.EVENT.register(factory::apply);
    }
    @Override public KeyBinding registerKeyBinding(KeyBinding binding){return KeyBindingHelper.registerKeyBinding(binding);}
    @Override public InputUtil.Key getBoundKey(KeyBinding binding){return KeyBindingHelper.getBoundKeyOf(binding);}
    @Override public void endTick(Consumer<MinecraftClient> callback){ClientTickEvents.END_CLIENT_TICK.register(callback::accept);}
    @Override public void started(Consumer<MinecraftClient> callback){ClientLifecycleEvents.CLIENT_STARTED.register(callback::accept);}
    @Override public void stopping(Consumer<MinecraftClient> callback){ClientLifecycleEvents.CLIENT_STOPPING.register(callback::accept);}
    @Override public void beforeScreen(ScreenInit callback){ScreenEvents.BEFORE_INIT.register(callback::before);}
    @Override public void hud(HudDraw callback){HudRenderCallback.EVENT.register((context,ticks)->callback.draw(context));}
    @Override public void worldStart(Runnable callback){WorldRenderEvents.START.register(context->callback.run());}
    @Override public void worldEnd(Runnable callback){WorldRenderEvents.END.register(context->callback.run());}
    @Override public void afterEntities(Consumer<WorldFrame> callback){
        WorldRenderEvents.AFTER_ENTITIES.register(context->{
            WorldRenderContext previous=frame.source;frame.source=context;
            try{callback.accept(frame);}finally{frame.source=previous;}
        });
    }
    @Override public void blockOutline(BlockOutline callback){
        WorldRenderEvents.BLOCK_OUTLINE.register((context,selection)->{
            WorldRenderContext oldFrame=frame.source;var oldTarget=target.source;
            frame.source=context;target.source=selection;
            try{return callback.draw(frame,target);}finally{frame.source=oldFrame;target.source=oldTarget;}
        });
    }
    private static final class WorldView implements WorldFrame {
        private WorldRenderContext source;
        @Override public net.minecraft.client.util.math.MatrixStack matrixStack(){return source.matrixStack();}
        @Override public net.minecraft.client.render.Camera camera(){return source.camera();}
        @Override public net.minecraft.client.render.VertexConsumerProvider consumers(){return source.consumers();}
        @Override public net.minecraft.client.render.Frustum frustum(){return source.frustum();}
        @Override public net.minecraft.client.world.ClientWorld world(){return source.world();}
        @Override public org.joml.Matrix4f projectionMatrix(){return source.projectionMatrix();}
        @Override public float tickDelta(){return source.tickCounter().getTickDelta(false);}
    }
    private static final class TargetView implements BlockTarget {
        private WorldRenderContext.BlockOutlineContext source;
        @Override public net.minecraft.block.BlockState blockState(){return source.blockState();}
        @Override public net.minecraft.util.math.BlockPos blockPos(){return source.blockPos();}
        @Override public net.minecraft.entity.Entity entity(){return source.entity();}
    }
}
