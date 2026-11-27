package com.batclient.platform.forge.mixin;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import java.util.List;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;
/** Forge54 removed RenderLevelStageEvent. Hook the actual active entity render pass. */
@Mixin(WorldRenderer.class)
public abstract class ForgeWorldStageMixin {
 @Shadow private Frustum frustum;
 @Unique private Matrix4f bat$projection;
 @Inject(method="render",at=@At("HEAD"))
 private void bat$captureProjection(net.minecraft.client.util.ObjectAllocator allocator,RenderTickCounter counter,
     boolean outline,Camera camera,Matrix4f position,Matrix4f projection,
     com.mojang.blaze3d.buffers.GpuBufferSlice fog,org.joml.Vector4f fogColor,boolean renderSky,CallbackInfo ci){
  bat$projection=projection;
 }
 @Inject(method="renderEntities",at=@At("RETURN"))
 private void bat$entities(MatrixStack matrices,VertexConsumerProvider.Immediate buffers,Camera camera,RenderTickCounter counter,List<Entity> entities,CallbackInfo ci){
  if(bat$projection!=null)com.batclient.platform.forge.ForgeClientHooks.fireWorldEntities(
      matrices,buffers,camera,counter.getTickDelta(false),frustum,bat$projection);
 }
}
