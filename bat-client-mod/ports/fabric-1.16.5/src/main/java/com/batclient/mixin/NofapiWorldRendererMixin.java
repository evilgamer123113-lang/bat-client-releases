package com.batclient.mixin;

import com.batclient.platform.fabric.FabricClientHooks;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class NofapiWorldRendererMixin {
    @Shadow private ClientWorld world;
    @Shadow private Frustum capturedFrustum;
    @Shadow private net.minecraft.client.render.BufferBuilderStorage bufferBuilders;
    @Unique private MatrixStack bat$matrices;
    @Unique private Camera bat$camera;
    @Unique private Matrix4f bat$projection;
    @Unique private float bat$tickDelta;

    @Inject(method = "render", at = @At("HEAD"))
    private void bat$nofapiWorldStart(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline,
                                      Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager,
                                      Matrix4f matrix4f, CallbackInfo ci) {
        bat$matrices = matrices;
        bat$camera = camera;
        bat$projection = matrix4f;
        bat$tickDelta = tickDelta;
        FabricClientHooks.onWorldStart();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;checkEmpty(Lnet/minecraft/client/util/math/MatrixStack;)V", ordinal = 0))
    private void bat$nofapiAfterEntities(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline,
                                         Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager,
                                         Matrix4f matrix4f, CallbackInfo ci) {
        VertexConsumerProvider consumers = bufferBuilders.getEntityVertexConsumers();
        FabricClientHooks.onAfterEntities(matrices, camera, consumers, capturedFrustum, world, matrix4f, tickDelta);
    }

    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void bat$nofapiBlockOutline(MatrixStack matrices, VertexConsumer vertexConsumer, Entity entity,
                                        double cameraX, double cameraY, double cameraZ, BlockPos blockPos,
                                        BlockState blockState, CallbackInfo ci) {
        VertexConsumerProvider consumers = bufferBuilders.getEntityVertexConsumers();
        if (!FabricClientHooks.onBlockOutline(matrices, bat$camera, consumers, capturedFrustum, world, bat$projection, bat$tickDelta, blockState, blockPos, entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void bat$nofapiWorldEnd(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline,
                                    Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager,
                                    Matrix4f matrix4f, CallbackInfo ci) {
        FabricClientHooks.onWorldEnd();
    }
}
