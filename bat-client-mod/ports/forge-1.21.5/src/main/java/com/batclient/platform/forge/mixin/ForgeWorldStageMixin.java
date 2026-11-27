package com.batclient.platform.forge.mixin;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import java.util.List;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Forge54 removed RenderLevelStageEvent. Hook the actual active entity render pass. */
@Mixin(WorldRenderer.class)
public abstract class ForgeWorldStageMixin {
 @Shadow private Frustum frustum;
 @Inject(method="renderEntities",at=@At("RETURN"))
 private void bat$entities(MatrixStack matrices,VertexConsumerProvider.Immediate buffers,Camera camera,RenderTickCounter counter,List<Entity> entities,CallbackInfo ci){
  com.batclient.platform.forge.ForgeClientHooks.fireWorldEntities(matrices,buffers,camera,counter.getTickDelta(false),frustum,com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
 }
}
